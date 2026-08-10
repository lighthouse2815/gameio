#Requires -Version 5.1

[CmdletBinding()]
param(
    [Parameter()]
    [ValidateNotNull()]
    [uri] $BaseUrl = "http://localhost:8080",

    [Parameter()]
    [ValidateNotNull()]
    [uri] $Origin = "http://localhost:3000",

    [Parameter()]
    [ValidateRange(10, 600)]
    [int] $StartupTimeoutSeconds = 120,

    [Parameter()]
    [switch] $SkipDockerServices,

    [Parameter()]
    [ValidateNotNullOrEmpty()]
    [string[]] $ExpectedServices = @("postgres", "redis", "backend", "frontend"),

    [Parameter()]
    [ValidatePattern("^[A-Za-z0-9_-]+$")]
    [string] $RefreshCookieName = "gameio_refresh",

    [Parameter()]
    [ValidateSet("Lax", "Strict", "None")]
    [string] $ExpectedSameSite = "Lax"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"
$ProgressPreference = "SilentlyContinue"

if (-not $BaseUrl.IsAbsoluteUri -or $BaseUrl.Scheme -notin @("http", "https")) {
    throw "BaseUrl must be an absolute HTTP or HTTPS URL."
}
if (-not $Origin.IsAbsoluteUri -or $Origin.Scheme -notin @("http", "https")) {
    throw "Origin must be an absolute HTTP or HTTPS URL."
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

$repoRoot = Split-Path -Parent $PSScriptRoot
$composeFile = Join-Path $repoRoot "docker-compose.yml"
$normalizedBaseUrl = $BaseUrl.GetLeftPart([System.UriPartial]::Authority).TrimEnd("/")
$originValue = $Origin.GetLeftPart([System.UriPartial]::Authority).TrimEnd("/")
$authCookieUri = [uri] "$normalizedBaseUrl/api/auth"

function Write-SmokeStep {
    param([Parameter(Mandatory)][string] $Message)
    Write-Host "[smoke] $Message"
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

    $records = foreach ($line in $Lines) {
        if (-not [string]::IsNullOrWhiteSpace($line)) {
            $line | ConvertFrom-Json
        }
    }
    return @($records)
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

        $container = $matches[0]
        $state = if ($container.PSObject.Properties["State"]) { [string] $container.State } else { "" }
        if ($state -ne "running") {
            return $false
        }

        $health = if ($container.PSObject.Properties["Health"]) { [string] $container.Health } else { "" }
        if (-not [string]::IsNullOrWhiteSpace($health) -and $health -ne "healthy") {
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
        throw "Docker CLI was not found. Use -SkipDockerServices only when checking a remote deployment."
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
            # The deadline below produces a stable, secret-free error message.
        }
        Start-Sleep -Seconds 2
    } while ([DateTimeOffset]::UtcNow -lt $deadline)

    throw "Docker services did not all reach running/healthy state within $StartupTimeoutSeconds seconds."
}

Add-Type -AssemblyName System.Net.Http
$cookieContainer = [System.Net.CookieContainer]::new()
$httpHandler = [System.Net.Http.HttpClientHandler]::new()
$httpHandler.UseCookies = $true
$httpHandler.CookieContainer = $cookieContainer
$httpHandler.AllowAutoRedirect = $false
$httpClient = [System.Net.Http.HttpClient]::new($httpHandler)
$httpClient.Timeout = [TimeSpan]::FromSeconds(15)
$httpClient.DefaultRequestHeaders.UserAgent.ParseAdd("gameio-smoke/1.0")

function Invoke-ApiRequest {
    param(
        [Parameter(Mandatory)]
        [ValidateSet("GET", "POST")]
        [string] $Method,

        [Parameter(Mandatory)]
        [ValidatePattern("^/")]
        [string] $Path,

        [Parameter()]
        [object] $Body,

        [Parameter()]
        [int[]] $ExpectedStatus = @(200),

        [Parameter()]
        [string] $BearerToken
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
                "Bearer",
                $BearerToken
            )
        }
        if ($PSBoundParameters.ContainsKey("Body")) {
            $json = $Body | ConvertTo-Json -Depth 8 -Compress
            $request.Content = [System.Net.Http.StringContent]::new(
                $json,
                [System.Text.Encoding]::UTF8,
                "application/json"
            )
        }

        $response = $httpClient.SendAsync($request).GetAwaiter().GetResult()
        $status = [int] $response.StatusCode
        $content = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
        if ($status -notin $ExpectedStatus) {
            throw "Request $Method $Path failed with HTTP $status."
        }

        $setCookieHeaders = @()
        if ($response.Headers.Contains("Set-Cookie")) {
            $setCookieHeaders = @($response.Headers.GetValues("Set-Cookie"))
        }
        $setsRefreshCookie = $false
        $clearsRefreshCookie = $false
        $refreshCookieSameSite = $null
        $refreshCookieHasDomain = $false
        foreach ($header in $setCookieHeaders) {
            if ($header.StartsWith("$RefreshCookieName=", [System.StringComparison]::OrdinalIgnoreCase)) {
                $setsRefreshCookie = $true
                $attributes = @($header.Split(";") | ForEach-Object { $_.Trim() })
                if ($attributes -contains "Max-Age=0") {
                    $clearsRefreshCookie = $true
                }
                $sameSiteAttribute = @($attributes | Where-Object {
                    $_.StartsWith("SameSite=", [System.StringComparison]::OrdinalIgnoreCase)
                })
                if ($sameSiteAttribute.Count -eq 1) {
                    $refreshCookieSameSite = $sameSiteAttribute[0].Substring("SameSite=".Length)
                }
                $refreshCookieHasDomain = @($attributes | Where-Object {
                    $_.StartsWith("Domain=", [System.StringComparison]::OrdinalIgnoreCase)
                }).Count -gt 0
            }
        }

        $corsOrigins = @()
        if ($response.Headers.Contains("Access-Control-Allow-Origin")) {
            $corsOrigins = @($response.Headers.GetValues("Access-Control-Allow-Origin"))
        }
        $corsCredentials = @()
        if ($response.Headers.Contains("Access-Control-Allow-Credentials")) {
            $corsCredentials = @($response.Headers.GetValues("Access-Control-Allow-Credentials"))
        }

        $data = $null
        if (-not [string]::IsNullOrWhiteSpace($content)) {
            try {
                $data = $content | ConvertFrom-Json
            }
            catch {
                throw "Request $Method $Path returned invalid JSON."
            }
        }
        return [pscustomobject]@{
            Status = $status
            Data = $data
            SetsRefreshCookie = $setsRefreshCookie
            ClearsRefreshCookie = $clearsRefreshCookie
            RefreshCookieSameSite = $refreshCookieSameSite
            RefreshCookieHasDomain = $refreshCookieHasDomain
            CorsOriginMatches = $corsOrigins -contains $originValue
            CorsCredentialsAllowed = $corsCredentials -contains "true"
        }
    }
    finally {
        if ($null -ne $response) {
            $response.Dispose()
        }
        $request.Dispose()
    }
}

function Assert-RefreshCookieResponsePolicy {
    param(
        [Parameter(Mandatory)][object] $Response,
        [Parameter(Mandatory)][string] $Context
    )

    if (-not $Response.SetsRefreshCookie) {
        throw "$Context response did not set the refresh cookie."
    }
    if ([string] $Response.RefreshCookieSameSite -ne $ExpectedSameSite) {
        throw "$Context refresh cookie did not use expected SameSite=$ExpectedSameSite."
    }
    if ($Response.RefreshCookieHasDomain) {
        throw "$Context refresh cookie was not host-only."
    }
}

function Assert-BrowserCors {
    param(
        [Parameter(Mandatory)][object] $Response,
        [Parameter(Mandatory)][string] $Context
    )

    if (-not $Response.CorsOriginMatches -or -not $Response.CorsCredentialsAllowed) {
        throw "$Context response does not allow credentialed CORS for the configured Origin."
    }
}

function Get-RequiredStringProperty {
    param(
        [Parameter(Mandatory)][object] $Object,
        [Parameter(Mandatory)][string] $Name,
        [Parameter(Mandatory)][string] $Context
    )

    $property = $Object.PSObject.Properties[$Name]
    if ($null -eq $property -or [string]::IsNullOrWhiteSpace([string] $property.Value)) {
        throw "$Context response is missing $Name."
    }
    return [string] $property.Value
}

function Get-RefreshCookie {
    $matches = @($cookieContainer.GetCookies($authCookieUri) | Where-Object {
        $_.Name -eq $RefreshCookieName
    })
    if ($matches.Count -gt 1) {
        throw "More than one refresh cookie is active for the API origin."
    }
    if ($matches.Count -eq 0) {
        return $null
    }
    return $matches[0]
}

function Assert-ActiveRefreshCookie {
    param([Parameter(Mandatory)][string] $Context)

    $cookie = Get-RefreshCookie
    if ($null -eq $cookie -or [string]::IsNullOrWhiteSpace($cookie.Value)) {
        throw "$Context did not establish a refresh cookie."
    }
    if (-not $cookie.HttpOnly) {
        throw "$Context refresh cookie is not HttpOnly."
    }
    if ($cookie.Path -ne "/api/auth") {
        throw "$Context refresh cookie has an unexpected Path."
    }
    if ($BaseUrl.Scheme -eq "https" -and -not $cookie.Secure) {
        throw "$Context refresh cookie is not Secure over HTTPS."
    }
    return $cookie
}

function Get-CookieFingerprint {
    param([Parameter(Mandatory)][System.Net.Cookie] $Cookie)

    $algorithm = [System.Security.Cryptography.SHA256]::Create()
    try {
        $bytes = [System.Text.Encoding]::UTF8.GetBytes($Cookie.Value)
        return [Convert]::ToBase64String($algorithm.ComputeHash($bytes))
    }
    finally {
        $algorithm.Dispose()
    }
}

function Wait-ForApiHealth {
    $deadline = [DateTimeOffset]::UtcNow.AddSeconds($StartupTimeoutSeconds)
    do {
        try {
            $health = Invoke-ApiRequest -Method GET -Path "/actuator/health"
            if ($null -ne $health.Data -and
                $health.Data.PSObject.Properties["status"] -and
                [string] $health.Data.status -eq "UP") {
                return
            }
        }
        catch {
            # Startup and transient network failures are retried until the deadline.
        }
        Start-Sleep -Seconds 2
    } while ([DateTimeOffset]::UtcNow -lt $deadline)

    throw "Backend health did not become UP within $StartupTimeoutSeconds seconds."
}

$flowCompleted = $false

try {
    if (-not $SkipDockerServices) {
        Write-SmokeStep "Checking Docker service state"
        Wait-ForComposeServices
    }

    Write-SmokeStep "Waiting for backend health"
    Wait-ForApiHealth

    $suffix = [guid]::NewGuid().ToString("N").Substring(0, 12)
    $username = "smoke_$suffix"
    $email = "smoke+$suffix@gameio.invalid"
    $password = "Sm0ke!$([guid]::NewGuid().ToString('N'))"

    Write-SmokeStep "Registering a unique test account"
    $registration = Invoke-ApiRequest -Method POST -Path "/api/auth/register" -ExpectedStatus 201 -Body @{
        username = $username
        email = $email
        password = $password
    }
    Assert-BrowserCors -Response $registration -Context "Registration"
    Assert-RefreshCookieResponsePolicy -Response $registration -Context "Registration"
    $null = Get-RequiredStringProperty -Object $registration.Data -Name "accessToken" -Context "Registration"
    $null = Assert-ActiveRefreshCookie -Context "Registration"

    Write-SmokeStep "Closing the registration bootstrap session"
    $registrationLogout = Invoke-ApiRequest -Method POST -Path "/api/auth/logout" -ExpectedStatus 204
    Assert-BrowserCors -Response $registrationLogout -Context "Registration logout"
    if (-not $registrationLogout.ClearsRefreshCookie -or $null -ne (Get-RefreshCookie)) {
        throw "Registration logout did not clear the refresh cookie."
    }

    Write-SmokeStep "Logging in with the new account"
    $login = Invoke-ApiRequest -Method POST -Path "/api/auth/login" -Body @{
        login = $username
        password = $password
    }
    Assert-BrowserCors -Response $login -Context "Login"
    Assert-RefreshCookieResponsePolicy -Response $login -Context "Login"
    $accessToken = Get-RequiredStringProperty -Object $login.Data -Name "accessToken" -Context "Login"
    $loginCookie = Assert-ActiveRefreshCookie -Context "Login"
    $loginCookieFingerprint = Get-CookieFingerprint -Cookie $loginCookie

    Write-SmokeStep "Reading the authenticated user profile"
    $me = Invoke-ApiRequest -Method GET -Path "/api/users/me" -BearerToken $accessToken
    Assert-BrowserCors -Response $me -Context "Authenticated profile"
    $profileUsername = Get-RequiredStringProperty -Object $me.Data -Name "username" -Context "Authenticated profile"
    if ($profileUsername -ne $username) {
        throw "Authenticated profile did not belong to the logged-in account."
    }

    Write-SmokeStep "Reading the game catalog"
    $catalog = Invoke-ApiRequest -Method GET -Path "/api/games?page=0&size=20" -BearerToken $accessToken
    Assert-BrowserCors -Response $catalog -Context "Catalog"
    $contentProperty = $catalog.Data.PSObject.Properties["content"]
    if ($null -eq $contentProperty -or @($contentProperty.Value).Count -lt 1) {
        throw "Catalog response did not contain any games."
    }

    Write-SmokeStep "Rotating the HttpOnly refresh cookie"
    $refresh = Invoke-ApiRequest -Method POST -Path "/api/auth/refresh"
    Assert-BrowserCors -Response $refresh -Context "Refresh"
    Assert-RefreshCookieResponsePolicy -Response $refresh -Context "Refresh"
    $refreshedAccessToken = Get-RequiredStringProperty -Object $refresh.Data -Name "accessToken" -Context "Refresh"
    $rotatedCookie = Assert-ActiveRefreshCookie -Context "Refresh"
    $rotatedCookieFingerprint = Get-CookieFingerprint -Cookie $rotatedCookie
    if ($rotatedCookieFingerprint -eq $loginCookieFingerprint) {
        throw "Refresh cookie was not rotated."
    }

    Write-SmokeStep "Using the refreshed access token"
    $refreshedMe = Invoke-ApiRequest -Method GET -Path "/api/users/me" -BearerToken $refreshedAccessToken
    Assert-BrowserCors -Response $refreshedMe -Context "Refreshed profile"
    if ((Get-RequiredStringProperty -Object $refreshedMe.Data -Name "username" -Context "Refreshed profile") -ne $username) {
        throw "Refreshed access token did not resolve the expected account."
    }

    Write-SmokeStep "Logging out the refreshed session"
    $logout = Invoke-ApiRequest -Method POST -Path "/api/auth/logout" -ExpectedStatus 204
    Assert-BrowserCors -Response $logout -Context "Logout"
    if (-not $logout.ClearsRefreshCookie -or $null -ne (Get-RefreshCookie)) {
        throw "Logout did not clear the refresh cookie."
    }

    Write-SmokeStep "Confirming refresh is rejected after logout"
    $rejectedRefresh = Invoke-ApiRequest -Method POST -Path "/api/auth/refresh" -ExpectedStatus 401
    Assert-BrowserCors -Response $rejectedRefresh -Context "Rejected refresh"

    $flowCompleted = $true
}
finally {
    if ($null -ne (Get-RefreshCookie)) {
        try {
            $null = Invoke-ApiRequest -Method POST -Path "/api/auth/logout" -ExpectedStatus 204
        }
        catch {
            Write-Warning "A best-effort test-session cleanup request failed."
        }
    }
    $httpClient.Dispose()
}

if ($flowCompleted) {
    Write-SmokeStep "All Docker, CORS, cookie, and API smoke checks passed"
}
