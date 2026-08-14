package com.gameio.observability;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class GameioOperationalMetricsTest {
    @Test
    void recordsLowCardinalityRealtimeOperationalSignals() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        GameioOperationalMetrics metrics = new GameioOperationalMetrics(registry);

        metrics.websocketConnections(3);
        metrics.activeMatches(2);
        metrics.matchStarted();
        metrics.inputAccepted();
        metrics.restoreSucceeded();
        metrics.checkpointSaved();
        metrics.matchCompleted(Duration.ofSeconds(42));

        assertThat(registry.get("gameio.realtime.websocket.connections").gauge().value()).isEqualTo(3);
        assertThat(registry.get("gameio.realtime.matches.active").gauge().value()).isEqualTo(2);
        assertThat(registry.get("gameio.realtime.matches").tag("state", "started").counter().count())
                .isEqualTo(1);
        assertThat(registry.get("gameio.realtime.matches").tag("state", "completed").counter().count())
                .isEqualTo(1);
        assertThat(registry.get("gameio.realtime.inputs.accepted").counter().count()).isEqualTo(1);
        assertThat(registry.get("gameio.realtime.match.restores").tag("outcome", "success").counter().count())
                .isEqualTo(1);
        assertThat(registry.get("gameio.realtime.checkpoints")
                .tag("operation", "save").tag("outcome", "success").counter().count()).isEqualTo(1);
        assertThat(registry.get("gameio.realtime.matches.duration").timer().totalTime(
                java.util.concurrent.TimeUnit.SECONDS)).isEqualTo(42);
    }
}
