#Requires -Version 5.1

[CmdletBinding()]
param(
    [Parameter()]
    [ValidateNotNull()]
    [uri] $BaseUrl = "http://localhost:8080",

    [Parameter()]
    [ValidateNotNull()]
    [uri] $WsUrl = "ws://localhost:8080/ws",

    [Parameter()]
    [ValidateNotNull()]
    [uri] $Origin = "http://localhost:3000",

    [Parameter()]
    [ValidateRange(10, 600)]
    [int] $StartupTimeoutSeconds = 120,

    [Parameter()]
    [ValidateRange(2, 60)]
    [int] $EventTimeoutSeconds = 15,

    [Parameter()]
    [switch] $SkipDockerServices,

    [Parameter()]
    [ValidateNotNullOrEmpty()]
    [string[]] $ExpectedServices = @("postgres", "redis")
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"
$ProgressPreference = "SilentlyContinue"

if (-not $BaseUrl.IsAbsoluteUri -or $BaseUrl.Scheme -notin @("http", "https")) {
    throw "BaseUrl must be an absolute HTTP or HTTPS URL."
}
if (-not $WsUrl.IsAbsoluteUri -or $WsUrl.Scheme -notin @("ws", "wss")) {
    throw "WsUrl must be an absolute WS or WSS URL."
}
if (-not $Origin.IsAbsoluteUri -or $Origin.Scheme -notin @("http", "https")) {
    throw "Origin must be an absolute HTTP or HTTPS URL."
}
if ($BaseUrl.Scheme -eq "https" -and $WsUrl.Scheme -ne "wss") {
    throw "An HTTPS API deployment must be tested through WSS."
}
if (-not [string]::IsNullOrEmpty($BaseUrl.UserInfo) -or
    $BaseUrl.AbsolutePath -ne "/" -or
    -not [string]::IsNullOrEmpty($BaseUrl.Query) -or
    -not [string]::IsNullOrEmpty($BaseUrl.Fragment)) {
    throw "BaseUrl must be a plain origin without credentials, path, query, or fragment."
}
if (-not [string]::IsNullOrEmpty($Origin.UserInfo) -or
    $Origin.AbsolutePath -ne "/" -or
    -not [string]::IsNullOrEmpty($Origin.Query) -or
    -not [string]::IsNullOrEmpty($Origin.Fragment)) {
    throw "Origin must be a plain origin without credentials, path, query, or fragment."
}
if (-not [string]::IsNullOrEmpty($WsUrl.UserInfo) -or
    $WsUrl.AbsolutePath -ne "/ws" -or
    -not [string]::IsNullOrEmpty($WsUrl.Query) -or
    -not [string]::IsNullOrEmpty($WsUrl.Fragment)) {
    throw "WsUrl must be the credential-free /ws endpoint without query or fragment."
}

$repoRoot = Split-Path -Parent $PSScriptRoot
$composeFile = Join-Path $repoRoot "docker-compose.yml"
$normalizedBaseUrl = $BaseUrl.GetLeftPart([System.UriPartial]::Authority).TrimEnd("/")
$originValue = $Origin.GetLeftPart([System.UriPartial]::Authority).TrimEnd("/")

function Write-SmokeStep {
    param([Parameter(Mandatory)][string] $Message)
    Write-Host "[realtime-smoke] $Message"
}

function ConvertFrom-ComposeJson {
    param([Parameter(Mandatory)][string[]] $Lines)

    $jsonText = ($Lines -join "`n").Trim()
    if ([string]::IsNullOrWhiteSpace($jsonText)) {
        return @()
    }
    if ($jsonText.StartsWith("[")) {
        return @($jsonText | ConvertFrom-Json)
    }
    return @($Lines | Where-Object { -not [string]::IsNullOrWhiteSpace($_) } | ForEach-Object {
        $_ | ConvertFrom-Json
    })
}

function Get-ComposeContainers {
    $outputLines = @(& docker compose --project-directory $repoRoot --file $composeFile ps --all --format json)
    if ($LASTEXITCODE -ne 0) {
        throw "docker compose ps failed with exit code $LASTEXITCODE."
    }
    return @(ConvertFrom-ComposeJson -Lines $outputLines)
}

function Test-ComposeServicesReady {
    param([Parameter(Mandatory)][object[]] $Containers)

    foreach ($service in $ExpectedServices) {
        $matches = @($Containers | Where-Object {
            $_.PSObject.Properties["Service"] -and [string] $_.Service -eq $service
        })
        if ($matches.Count -ne 1) {
            return $false
        }
        $state = if ($matches[0].PSObject.Properties["State"]) { [string] $matches[0].State } else { "" }
        $health = if ($matches[0].PSObject.Properties["Health"]) { [string] $matches[0].Health } else { "" }
        if ($state -ne "running" -or
            (-not [string]::IsNullOrWhiteSpace($health) -and $health -ne "healthy")) {
            return $false
        }
    }
    return $true
}

function Wait-ForComposeServices {
    if (-not (Test-Path -LiteralPath $composeFile -PathType Leaf)) {
        throw "Docker Compose file was not found at the repository root."
    }
    if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
        throw "Docker CLI was not found. Use -SkipDockerServices only for a remote deployment."
    }
    $null = & docker compose version
    if ($LASTEXITCODE -ne 0) {
        throw "Docker Compose v2 is unavailable."
    }

    $deadline = [DateTimeOffset]::UtcNow.AddSeconds($StartupTimeoutSeconds)
    do {
        try {
            $containers = @(Get-ComposeContainers)
            if (Test-ComposeServicesReady -Containers $containers) {
                return
            }
        }
        catch {
            # Retry transient Docker errors until the bounded deadline.
        }
        Start-Sleep -Seconds 2
    } while ([DateTimeOffset]::UtcNow -lt $deadline)

    throw "Docker dependencies did not reach running/healthy state within $StartupTimeoutSeconds seconds."
}

Add-Type -AssemblyName System.Net.Http

function New-ApiSession {
    $cookies = [System.Net.CookieContainer]::new()
    $handler = [System.Net.Http.HttpClientHandler]::new()
    $handler.UseCookies = $true
    $handler.CookieContainer = $cookies
    $handler.AllowAutoRedirect = $false
    $client = [System.Net.Http.HttpClient]::new($handler)
    $client.Timeout = [TimeSpan]::FromSeconds(15)
    $client.DefaultRequestHeaders.UserAgent.ParseAdd("gameio-realtime-smoke/1.0")
    return [pscustomobject]@{
        Client = $client
        Cookies = $cookies
    }
}

function Invoke-ApiRequest {
    param(
        [Parameter(Mandatory)][object] $Session,
        [Parameter(Mandatory)][ValidateSet("GET", "POST", "DELETE")][string] $Method,
        [Parameter(Mandatory)][ValidatePattern("^/")][string] $Path,
        [Parameter()][object] $Body,
        [Parameter()][int[]] $ExpectedStatus = @(200),
        [Parameter()][string] $BearerToken
    )

    $request = [System.Net.Http.HttpRequestMessage]::new(
        [System.Net.Http.HttpMethod]::new($Method),
        "$normalizedBaseUrl$Path"
    )
    $response = $null
    try {
        $request.Headers.Accept.ParseAdd("application/json")
        $null = $request.Headers.TryAddWithoutValidation("Origin", $originValue)
        if ($Method -eq "POST") {
            $null = $request.Headers.TryAddWithoutValidation("X-Gameio-CSRF", "1")
        }
        if (-not [string]::IsNullOrWhiteSpace($BearerToken)) {
            $request.Headers.Authorization = [System.Net.Http.Headers.AuthenticationHeaderValue]::new(
                "Bearer", $BearerToken
            )
        }
        if ($PSBoundParameters.ContainsKey("Body")) {
            $json = $Body | ConvertTo-Json -Depth 12 -Compress
            $request.Content = [System.Net.Http.StringContent]::new(
                $json, [System.Text.Encoding]::UTF8, "application/json"
            )
        }

        $response = $Session.Client.SendAsync($request).GetAwaiter().GetResult()
        $status = [int] $response.StatusCode
        if ($status -notin $ExpectedStatus) {
            throw "Request $Method $Path failed with HTTP $status."
        }
        $corsOrigins = if ($response.Headers.Contains("Access-Control-Allow-Origin")) {
            @($response.Headers.GetValues("Access-Control-Allow-Origin"))
        } else { @() }
        $corsCredentials = if ($response.Headers.Contains("Access-Control-Allow-Credentials")) {
            @($response.Headers.GetValues("Access-Control-Allow-Credentials"))
        } else { @() }
        if ($corsOrigins -notcontains $originValue -or $corsCredentials -notcontains "true") {
            throw "Request $Method $Path did not allow credentialed CORS for the configured Origin."
        }

        $content = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
        if ([string]::IsNullOrWhiteSpace($content)) {
            return $null
        }
        try {
            return $content | ConvertFrom-Json
        }
        catch {
            throw "Request $Method $Path returned invalid JSON."
        }
    }
    finally {
        if ($null -ne $response) {
            $response.Dispose()
        }
        $request.Dispose()
    }
}

function Get-RequiredProperty {
    param(
        [Parameter(Mandatory)][object] $Object,
        [Parameter(Mandatory)][string] $Name,
        [Parameter(Mandatory)][string] $Context
    )

    $property = $Object.PSObject.Properties[$Name]
    if ($null -eq $property -or $null -eq $property.Value -or
        ($property.Value -is [string] -and [string]::IsNullOrWhiteSpace([string] $property.Value))) {
        throw "$Context response is missing $Name."
    }
    return $property.Value
}

function Wait-ForApiHealth {
    param([Parameter(Mandatory)][object] $Session)

    $deadline = [DateTimeOffset]::UtcNow.AddSeconds($StartupTimeoutSeconds)
    do {
        try {
            $health = Invoke-ApiRequest -Session $Session -Method GET -Path "/actuator/health"
            if ($null -ne $health -and [string] (Get-RequiredProperty $health "status" "Health") -eq "UP") {
                return
            }
        }
        catch {
            # Retry startup and transient network failures until the deadline.
        }
        Start-Sleep -Seconds 2
    } while ([DateTimeOffset]::UtcNow -lt $deadline)
    throw "Backend health did not become UP within $StartupTimeoutSeconds seconds."
}

function Register-SmokePlayer {
    param(
        [Parameter(Mandatory)][object] $Session,
        [Parameter(Mandatory)][string] $Label
    )

    $suffix = [guid]::NewGuid().ToString("N").Substring(0, 12)
    $registration = Invoke-ApiRequest -Session $Session -Method POST -Path "/api/auth/register" `
        -ExpectedStatus 201 -Body @{
            username = "rt_${Label}_$suffix"
            email = "rt_${Label}+$suffix@gameio.invalid"
            password = "Rt!9$([guid]::NewGuid().ToString('N'))"
        }
    return [pscustomobject]@{
        AccessToken = [string] (Get-RequiredProperty $registration "accessToken" "$Label registration")
        User = Get-RequiredProperty $registration "user" "$Label registration"
    }
}

function New-GameSocket {
    param(
        [Parameter(Mandatory)][string] $AccessToken,
        [Parameter(Mandatory)][string] $Label
    )

    $socket = [System.Net.WebSockets.ClientWebSocket]::new()
    try {
        $socket.Options.AddSubProtocol("gameio.v1")
        $socket.Options.AddSubProtocol("gameio.jwt.$AccessToken")
        $socket.Options.SetRequestHeader("Origin", $originValue)
        $cts = [System.Threading.CancellationTokenSource]::new()
        try {
            $cts.CancelAfter([TimeSpan]::FromSeconds($EventTimeoutSeconds))
            $socket.ConnectAsync($WsUrl, $cts.Token).GetAwaiter().GetResult() | Out-Null
        }
        finally {
            $cts.Dispose()
        }
        if ($socket.State -ne [System.Net.WebSockets.WebSocketState]::Open -or
            $socket.SubProtocol -ne "gameio.v1") {
            throw "negotiation failed"
        }
        return [pscustomobject]@{
            Label = $Label
            Socket = $socket
            Pending = [System.Collections.Generic.List[object]]::new()
        }
    }
    catch {
        $socket.Dispose()
        throw "$Label WebSocket connection or subprotocol negotiation failed."
    }
}

function Send-WsEnvelope {
    param(
        [Parameter(Mandatory)][object] $Connection,
        [Parameter(Mandatory)][ValidatePattern("^[A-Z_]{3,40}$")][string] $Type,
        [Parameter()][string] $RoomId,
        [Parameter()][object] $Payload
    )

    $requestId = [guid]::NewGuid().ToString("N")
    $envelope = [ordered]@{
        type = $Type
        requestId = $requestId
        sentAt = [DateTimeOffset]::UtcNow.ToString("o")
    }
    if (-not [string]::IsNullOrWhiteSpace($RoomId)) {
        $envelope.roomId = $RoomId
    }
    if ($PSBoundParameters.ContainsKey("Payload")) {
        $envelope.payload = $Payload
    }
    $bytes = [System.Text.Encoding]::UTF8.GetBytes(($envelope | ConvertTo-Json -Depth 12 -Compress))
    $segment = [ArraySegment[byte]]::new($bytes)
    $cts = [System.Threading.CancellationTokenSource]::new()
    try {
        $cts.CancelAfter([TimeSpan]::FromSeconds($EventTimeoutSeconds))
        $Connection.Socket.SendAsync(
            $segment, [System.Net.WebSockets.WebSocketMessageType]::Text, $true, $cts.Token
        ).GetAwaiter().GetResult() | Out-Null
    }
    catch {
        throw "$($Connection.Label) could not send WebSocket event $Type."
    }
    finally {
        $cts.Dispose()
    }
    return $requestId
}

function Receive-WsEnvelope {
    param([Parameter(Mandatory)][object] $Connection)

    $buffer = New-Object byte[] 65536
    $stream = [System.IO.MemoryStream]::new()
    $cts = [System.Threading.CancellationTokenSource]::new()
    try {
        $cts.CancelAfter([TimeSpan]::FromSeconds($EventTimeoutSeconds))
        do {
            $result = $Connection.Socket.ReceiveAsync(
                [ArraySegment[byte]]::new($buffer), $cts.Token
            ).GetAwaiter().GetResult()
            if ($result.MessageType -eq [System.Net.WebSockets.WebSocketMessageType]::Close) {
                throw "socket closed"
            }
            if ($result.MessageType -ne [System.Net.WebSockets.WebSocketMessageType]::Text) {
                throw "non-text frame"
            }
            if ($stream.Length + $result.Count -gt 1MB) {
                throw "message too large"
            }
            $stream.Write($buffer, 0, $result.Count)
        } while (-not $result.EndOfMessage)

        $json = [System.Text.Encoding]::UTF8.GetString($stream.ToArray())
        try {
            return $json | ConvertFrom-Json
        }
        catch {
            throw "invalid JSON"
        }
    }
    catch {
        throw "$($Connection.Label) did not receive a valid WebSocket event within $EventTimeoutSeconds seconds: $($_.Exception.Message)"
    }
    finally {
        $cts.Dispose()
        $stream.Dispose()
    }
}

function Test-WsEnvelopeMatch {
    param(
        [Parameter(Mandatory)][object] $Envelope,
        [Parameter(Mandatory)][string] $Type,
        [Parameter()][AllowNull()][string] $RequestId
    )

    if (-not $Envelope.PSObject.Properties["type"] -or [string] $Envelope.type -ne $Type) {
        return $false
    }
    if ($PSBoundParameters.ContainsKey("RequestId")) {
        $actual = if ($Envelope.PSObject.Properties["requestId"]) { [string] $Envelope.requestId } else { $null }
        return $actual -eq $RequestId
    }
    return $true
}

function Wait-ForWsEvent {
    param(
        [Parameter(Mandatory)][object] $Connection,
        [Parameter(Mandatory)][string] $Type,
        [Parameter()][AllowNull()][string] $RequestId
    )

    for ($index = 0; $index -lt $Connection.Pending.Count; $index++) {
        $pending = $Connection.Pending[$index]
        $matches = if ($PSBoundParameters.ContainsKey("RequestId")) {
            Test-WsEnvelopeMatch -Envelope $pending -Type $Type -RequestId $RequestId
        } else {
            Test-WsEnvelopeMatch -Envelope $pending -Type $Type
        }
        if ($matches) {
            $Connection.Pending.RemoveAt($index)
            return $pending
        }
    }

    $deadline = [DateTimeOffset]::UtcNow.AddSeconds($EventTimeoutSeconds)
    while ([DateTimeOffset]::UtcNow -lt $deadline) {
        $event = Receive-WsEnvelope -Connection $Connection
        if ([string] $event.type -eq "ERROR") {
            $code = if ($event.PSObject.Properties["payload"] -and
                $event.payload.PSObject.Properties["code"]) { [string] $event.payload.code } else { "UNKNOWN" }
            throw "$($Connection.Label) received WebSocket error code $code."
        }
        $matches = if ($PSBoundParameters.ContainsKey("RequestId")) {
            Test-WsEnvelopeMatch -Envelope $event -Type $Type -RequestId $RequestId
        } else {
            Test-WsEnvelopeMatch -Envelope $event -Type $Type
        }
        if ($matches) {
            return $event
        }
        $Connection.Pending.Add($event)
    }
    throw "$($Connection.Label) did not receive WebSocket event $Type within $EventTimeoutSeconds seconds."
}

function Close-GameSocket {
    param([Parameter()][AllowNull()][object] $Connection)

    if ($null -eq $Connection -or $null -eq $Connection.PSObject.Properties["Socket"]) {
        return
    }
    try {
        if ($Connection.Socket.State -eq [System.Net.WebSockets.WebSocketState]::Open) {
            $cts = [System.Threading.CancellationTokenSource]::new()
            try {
                $cts.CancelAfter([TimeSpan]::FromSeconds(2))
                $Connection.Socket.CloseAsync(
                    [System.Net.WebSockets.WebSocketCloseStatus]::NormalClosure,
                    "Smoke complete", $cts.Token
                ).GetAwaiter().GetResult() | Out-Null
            }
            finally {
                $cts.Dispose()
            }
        }
    }
    catch {
        # Best-effort close; disposal below always releases the client socket.
    }
    finally {
        $Connection.Socket.Dispose()
    }
}

function Get-HistoryOutcome {
    param(
        [Parameter(Mandatory)][object] $History,
        [Parameter(Mandatory)][string] $MatchId
    )

    $content = @(Get-RequiredProperty $History "content" "History")
    $match = @($content | Where-Object {
        $_.PSObject.Properties["matchId"] -and [string] $_.matchId -eq $MatchId
    })
    if ($match.Count -ne 1) {
        throw "History does not contain exactly one result for the completed match."
    }
    return [string] (Get-RequiredProperty $match[0] "result" "History result")
}

$ownerSession = New-ApiSession
$guestSession = New-ApiSession
$ownerSocket = $null
$guestSocket = $null
$flowCompleted = $false

try {
    if (-not $SkipDockerServices) {
        Write-SmokeStep "Checking PostgreSQL and Redis Docker dependencies"
        Wait-ForComposeServices
    }

    Write-SmokeStep "Waiting for backend health"
    Wait-ForApiHealth -Session $ownerSession

    Write-SmokeStep "Registering two isolated test accounts"
    $owner = Register-SmokePlayer -Session $ownerSession -Label "owner"
    $guest = Register-SmokePlayer -Session $guestSession -Label "guest"

    Write-SmokeStep "Resolving the seeded Tic Tac Toe catalog entry"
    $catalog = Invoke-ApiRequest -Session $ownerSession -Method GET -Path "/api/games?search=Tic%20Tac%20Toe&page=0&size=20" `
        -BearerToken $owner.AccessToken
    $games = @(Get-RequiredProperty $catalog "content" "Catalog")
    $ticTacToe = @($games | Where-Object { [string] $_.slug -eq "tic-tac-toe" })
    if ($ticTacToe.Count -ne 1) {
        throw "Catalog did not contain exactly one enabled Tic Tac Toe entry."
    }
    $gameId = [string] (Get-RequiredProperty $ticTacToe[0] "id" "Tic Tac Toe")

    Write-SmokeStep "Creating and joining a private two-player room"
    $room = Invoke-ApiRequest -Session $ownerSession -Method POST -Path "/api/rooms" `
        -ExpectedStatus 201 -BearerToken $owner.AccessToken -Body @{
            gameId = $gameId
            maxPlayers = 2
            privateRoom = $true
        }
    $roomId = [string] (Get-RequiredProperty $room "roomId" "Room creation")
    $roomCode = [string] (Get-RequiredProperty $room "roomCode" "Room creation")
    $joined = Invoke-ApiRequest -Session $guestSession -Method POST -Path "/api/rooms/join" `
        -BearerToken $guest.AccessToken -Body @{ roomCode = $roomCode }
    if ([string] (Get-RequiredProperty $joined "roomId" "Room join") -ne $roomId) {
        throw "Guest joined a different room than the owner created."
    }

    Write-SmokeStep "Opening authenticated WebSockets with token-free URLs"
    $ownerSocket = New-GameSocket -AccessToken $owner.AccessToken -Label "Owner"
    $guestSocket = New-GameSocket -AccessToken $guest.AccessToken -Label "Guest"

    $ownerJoinId = Send-WsEnvelope -Connection $ownerSocket -Type "ROOM_JOIN" -RoomId $roomId
    $guestJoinId = Send-WsEnvelope -Connection $guestSocket -Type "ROOM_JOIN" -RoomId $roomId
    $null = Wait-ForWsEvent -Connection $ownerSocket -Type "ROOM_STATE" -RequestId $ownerJoinId
    $null = Wait-ForWsEvent -Connection $guestSocket -Type "ROOM_STATE" -RequestId $guestJoinId
    $null = Wait-ForWsEvent -Connection $ownerSocket -Type "CONNECTED"
    $null = Wait-ForWsEvent -Connection $guestSocket -Type "CONNECTED"

    Write-SmokeStep "Ready-up and explicit owner start"
    $ownerReadyId = Send-WsEnvelope -Connection $ownerSocket -Type "ROOM_READY" -RoomId $roomId
    $null = Wait-ForWsEvent -Connection $ownerSocket -Type "ROOM_STATE" -RequestId $ownerReadyId
    $guestReadyId = Send-WsEnvelope -Connection $guestSocket -Type "ROOM_READY" -RoomId $roomId
    $null = Wait-ForWsEvent -Connection $guestSocket -Type "ROOM_STATE" -RequestId $guestReadyId
    $startId = Send-WsEnvelope -Connection $ownerSocket -Type "ROOM_START" -RoomId $roomId
    $null = Wait-ForWsEvent -Connection $ownerSocket -Type "ROOM_STATE" -RequestId $startId
    $ownerStart = Wait-ForWsEvent -Connection $ownerSocket -Type "GAME_START"
    $guestStart = Wait-ForWsEvent -Connection $guestSocket -Type "GAME_START"
    $matchId = [string] (Get-RequiredProperty $ownerStart.payload "matchId" "GAME_START")
    if ([string] (Get-RequiredProperty $guestStart.payload "matchId" "GAME_START") -ne $matchId) {
        throw "Players received different match identifiers."
    }

    Write-SmokeStep "Playing a deterministic server-authoritative Tic Tac Toe win"
    $moves = @(
        [pscustomobject]@{ Connection = $ownerSocket; Row = 0; Column = 0 },
        [pscustomobject]@{ Connection = $guestSocket; Row = 1; Column = 0 },
        [pscustomobject]@{ Connection = $ownerSocket; Row = 0; Column = 1 },
        [pscustomobject]@{ Connection = $guestSocket; Row = 1; Column = 1 },
        [pscustomobject]@{ Connection = $ownerSocket; Row = 0; Column = 2 }
    )
    foreach ($move in $moves) {
        $inputId = Send-WsEnvelope -Connection $move.Connection -Type "GAME_INPUT" -RoomId $roomId -Payload @{
            action = "PLACE_PIECE"
            row = $move.Row
            column = $move.Column
        }
        $null = Wait-ForWsEvent -Connection $move.Connection -Type "GAME_STATE" -RequestId $inputId
    }

    $ownerOver = Wait-ForWsEvent -Connection $ownerSocket -Type "GAME_OVER"
    $guestOver = Wait-ForWsEvent -Connection $guestSocket -Type "GAME_OVER"
    if ([string] (Get-RequiredProperty $ownerOver.payload "matchId" "Owner GAME_OVER") -ne $matchId -or
        [string] (Get-RequiredProperty $guestOver.payload "matchId" "Guest GAME_OVER") -ne $matchId) {
        throw "GAME_OVER did not identify the started match for both players."
    }
    $winnerId = [string] (Get-RequiredProperty $ownerOver.payload.finalState "winnerId" "Final state")
    $ownerId = [string] (Get-RequiredProperty $owner.User "id" "Owner")
    if ($winnerId -ne $ownerId) {
        throw "The deterministic match did not declare the expected owner win."
    }

    Write-SmokeStep "Confirming durable WIN and LOSS history records"
    $ownerHistory = Invoke-ApiRequest -Session $ownerSession -Method GET -Path "/api/game-results/me?page=0&size=20" `
        -BearerToken $owner.AccessToken
    $guestHistory = Invoke-ApiRequest -Session $guestSession -Method GET -Path "/api/game-results/me?page=0&size=20" `
        -BearerToken $guest.AccessToken
    if ((Get-HistoryOutcome -History $ownerHistory -MatchId $matchId) -ne "WIN") {
        throw "Owner history did not persist a WIN for the match."
    }
    if ((Get-HistoryOutcome -History $guestHistory -MatchId $matchId) -ne "LOSS") {
        throw "Guest history did not persist a LOSS for the match."
    }

    $flowCompleted = $true
}
finally {
    Close-GameSocket -Connection $ownerSocket
    Close-GameSocket -Connection $guestSocket
    foreach ($cleanup in @(
        [pscustomobject]@{ Session = $ownerSession },
        [pscustomobject]@{ Session = $guestSession }
    )) {
        try {
            $null = Invoke-ApiRequest -Session $cleanup.Session -Method POST -Path "/api/auth/logout" `
                -ExpectedStatus 204
        }
        catch {
            Write-Warning "A best-effort test-session logout request failed."
        }
        $cleanup.Session.Client.Dispose()
    }
}

if ($flowCompleted) {
    Write-SmokeStep "WebSocket auth, room flow, authoritative match, and persisted outcomes passed"
}
