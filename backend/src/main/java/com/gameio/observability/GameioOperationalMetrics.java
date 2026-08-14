package com.gameio.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

@Component
public class GameioOperationalMetrics {
    private final AtomicInteger websocketConnections = new AtomicInteger();
    private final AtomicInteger activeMatches = new AtomicInteger();
    private final Counter matchesStarted;
    private final Counter matchesCompleted;
    private final Counter inputsAccepted;
    private final Counter restoreSucceeded;
    private final Counter restoreMissed;
    private final Counter restoreFailed;
    private final Counter checkpointSaved;
    private final Counter checkpointSaveFailed;
    private final Counter checkpointDeleted;
    private final Counter checkpointDeleteFailed;
    private final Counter websocketSendFailed;
    private final Timer matchDuration;

    public GameioOperationalMetrics(MeterRegistry registry) {
        Gauge.builder("gameio.realtime.websocket.connections", websocketConnections, AtomicInteger::get)
                .description("Open authenticated Gameio WebSocket connections")
                .register(registry);
        Gauge.builder("gameio.realtime.matches.active", activeMatches, AtomicInteger::get)
                .description("Authoritative matches currently owned by this backend process")
                .register(registry);
        matchesStarted = counter(registry, "gameio.realtime.matches", "started");
        matchesCompleted = counter(registry, "gameio.realtime.matches", "completed");
        inputsAccepted = Counter.builder("gameio.realtime.inputs.accepted")
                .description("Player inputs accepted by an authoritative game engine")
                .register(registry);
        restoreSucceeded = restoreCounter(registry, "success");
        restoreMissed = restoreCounter(registry, "missing");
        restoreFailed = restoreCounter(registry, "invalid");
        checkpointSaved = checkpointCounter(registry, "save", "success");
        checkpointSaveFailed = checkpointCounter(registry, "save", "failure");
        checkpointDeleted = checkpointCounter(registry, "delete", "success");
        checkpointDeleteFailed = checkpointCounter(registry, "delete", "failure");
        websocketSendFailed = Counter.builder("gameio.realtime.websocket.send.failures")
                .description("WebSocket messages that could not be serialized or delivered")
                .register(registry);
        matchDuration = Timer.builder("gameio.realtime.matches.duration")
                .description("Completed authoritative match duration")
                .publishPercentileHistogram()
                .register(registry);
    }

    public void websocketConnections(int count) {
        websocketConnections.set(Math.max(0, count));
    }

    public void activeMatches(int count) {
        activeMatches.set(Math.max(0, count));
    }

    public void matchStarted() {
        matchesStarted.increment();
    }

    public void matchCompleted(Duration duration) {
        matchesCompleted.increment();
        matchDuration.record(duration);
    }

    public void inputAccepted() {
        inputsAccepted.increment();
    }

    public void restoreSucceeded() {
        restoreSucceeded.increment();
    }

    public void restoreMissed() {
        restoreMissed.increment();
    }

    public void restoreFailed() {
        restoreFailed.increment();
    }

    public void checkpointSaved() {
        checkpointSaved.increment();
    }

    public void checkpointSaveFailed() {
        checkpointSaveFailed.increment();
    }

    public void checkpointDeleted() {
        checkpointDeleted.increment();
    }

    public void checkpointDeleteFailed() {
        checkpointDeleteFailed.increment();
    }

    public void websocketSendFailed() {
        websocketSendFailed.increment();
    }

    private Counter counter(MeterRegistry registry, String name, String state) {
        return Counter.builder(name)
                .description("Authoritative realtime match lifecycle transitions")
                .tag("state", state)
                .register(registry);
    }

    private Counter restoreCounter(MeterRegistry registry, String outcome) {
        return Counter.builder("gameio.realtime.match.restores")
                .description("Attempts to restore an authoritative match from Redis")
                .tag("outcome", outcome)
                .register(registry);
    }

    private Counter checkpointCounter(MeterRegistry registry, String operation, String outcome) {
        return Counter.builder("gameio.realtime.checkpoints")
                .description("Redis active-match checkpoint operations")
                .tag("operation", operation)
                .tag("outcome", outcome)
                .register(registry);
    }
}
