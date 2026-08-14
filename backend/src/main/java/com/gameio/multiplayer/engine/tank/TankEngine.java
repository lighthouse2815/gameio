package com.gameio.multiplayer.engine.tank;

import com.gameio.common.error.InvalidGameActionException;
import com.gameio.gameresult.GameResultType;
import com.gameio.multiplayer.engine.AuthoritativeEngine;
import com.gameio.multiplayer.engine.EngineOutcome;
import com.gameio.multiplayer.engine.EngineUpdate;
import com.gameio.multiplayer.engine.GameInput;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class TankEngine implements AuthoritativeEngine {
    public static final double WIDTH = 100;
    public static final double HEIGHT = 100;
    private static final double TANK_RADIUS = 2;
    private static final double TANK_SPEED = 20;
    private static final double BULLET_SPEED = 45;
    private static final double BULLET_RADIUS = 0.6;
    private static final int BULLET_DAMAGE = 25;
    private static final Duration SHOT_COOLDOWN = Duration.ofMillis(500);

    private final Map<UUID, MutableTank> tanks = new LinkedHashMap<>();
    private final List<MutableBullet> bullets = new ArrayList<>();
    private long sequence;
    private Instant lastTick;
    private UUID winner;
    private boolean draw;

    public TankEngine(List<UUID> playerIds) {
        if (playerIds.size() < 2 || playerIds.size() > 4 || playerIds.stream().distinct().count() != playerIds.size()) {
            throw new IllegalArgumentException("Tank Battle requires two to four distinct players");
        }
        double[][] spawns = {{10, 10}, {90, 90}, {90, 10}, {10, 90}};
        for (int index = 0; index < playerIds.size(); index++) {
            tanks.put(playerIds.get(index), new MutableTank(playerIds.get(index), spawns[index][0], spawns[index][1]));
        }
    }

    TankEngine(List<UUID> playerIds, TankCheckpoint checkpoint) {
        this(playerIds);
        if (checkpoint.sequence() < 0 || checkpoint.tanks().size() != playerIds.size()
                || checkpoint.winnerId() != null && !playerIds.contains(checkpoint.winnerId())) {
            throw new IllegalArgumentException("Tank Battle checkpoint is invalid");
        }
        tanks.clear();
        for (TankCheckpoint.TankState state : checkpoint.tanks()) {
            if (!playerIds.contains(state.userId()) || tanks.containsKey(state.userId())
                    || !finite(state.x(), state.y(), state.rotation(), state.dx(), state.dy())
                    || state.x() < TANK_RADIUS || state.x() > WIDTH - TANK_RADIUS
                    || state.y() < TANK_RADIUS || state.y() > HEIGHT - TANK_RADIUS
                    || !validRotation(state.rotation())
                    || state.hp() < 0 || state.hp() > 100 || state.hp() % BULLET_DAMAGE != 0
                    || state.kills() < 0 || state.kills() >= playerIds.size()
                    || state.lastInputSequence() < -1 || !validDirection(state.dx(), state.dy())
                    || state.alive() != (state.hp() > 0)) {
                throw new IllegalArgumentException("Tank Battle tank checkpoint is invalid");
            }
            MutableTank tank = new MutableTank(state.userId(), state.x(), state.y());
            tank.rotation = state.rotation();
            tank.hp = state.hp();
            tank.alive = state.alive();
            tank.kills = state.kills();
            tank.dx = state.dx();
            tank.dy = state.dy();
            tank.lastInputSequence = state.lastInputSequence();
            tank.lastShotAt = state.lastShotAt();
            tanks.put(state.userId(), tank);
        }
        java.util.Set<UUID> bulletIds = new java.util.HashSet<>();
        for (TankCheckpoint.BulletState state : checkpoint.bullets()) {
            if (state.id() == null || !bulletIds.add(state.id()) || !tanks.containsKey(state.ownerId())
                    || !finite(state.x(), state.y(), state.dx(), state.dy())
                    || state.x() < 0 || state.x() > WIDTH || state.y() < 0 || state.y() > HEIGHT
                    || Math.abs(Math.hypot(state.dx(), state.dy()) - 1) > 0.000_001) {
                throw new IllegalArgumentException("Tank Battle bullet checkpoint is invalid");
            }
            bullets.add(new MutableBullet(state.id(), state.ownerId(), state.x(), state.y(), state.dx(), state.dy()));
        }
        sequence = checkpoint.sequence();
        lastTick = checkpoint.lastTick();
        winner = checkpoint.winnerId();
        draw = checkpoint.draw();
        validateOutcome();
    }

    @Override
    public boolean requiresServerTick() {
        return true;
    }

    @Override
    public TankSnapshot snapshot() {
        List<TankView> tankViews = tanks.values().stream().map(MutableTank::view).toList();
        List<BulletView> bulletViews = bullets.stream().map(MutableBullet::view).toList();
        return new TankSnapshot(sequence, WIDTH, HEIGHT, tankViews, bulletViews, winner, draw);
    }

    @Override
    public TankCheckpoint checkpoint() {
        List<TankCheckpoint.TankState> tankStates = tanks.values().stream().map(tank ->
                new TankCheckpoint.TankState(tank.userId, tank.x, tank.y, tank.rotation, tank.hp, tank.alive,
                        tank.kills, tank.dx, tank.dy, tank.lastInputSequence, tank.lastShotAt)).toList();
        List<TankCheckpoint.BulletState> bulletStates = bullets.stream().map(bullet ->
                new TankCheckpoint.BulletState(bullet.id, bullet.ownerId, bullet.x, bullet.y,
                        bullet.dx, bullet.dy)).toList();
        return new TankCheckpoint(TankCheckpoint.CURRENT_VERSION, sequence, lastTick, winner, draw,
                tankStates, bulletStates);
    }

    @Override
    public EngineUpdate input(UUID userId, GameInput input, Instant now) {
        if (terminal()) {
            throw new InvalidGameActionException("Game is already over");
        }
        MutableTank tank = tanks.get(userId);
        if (tank == null) {
            throw new InvalidGameActionException("Player is not part of this Tank Battle");
        }
        if (!tank.alive) {
            throw new InvalidGameActionException("Destroyed tanks cannot submit input");
        }
        long inputSequence = input.sequence() == null ? -1 : input.sequence();
        if (inputSequence <= tank.lastInputSequence) {
            throw new InvalidGameActionException("Tank input sequence must increase monotonically");
        }

        switch (input.action()) {
            case "MOVE_UP" -> tank.move(0, -1, 270);
            case "MOVE_DOWN" -> tank.move(0, 1, 90);
            case "MOVE_LEFT" -> tank.move(-1, 0, 180);
            case "MOVE_RIGHT" -> tank.move(1, 0, 0);
            case "STOP" -> tank.move(0, 0, tank.rotation);
            case "SHOOT" -> shoot(tank, now);
            default -> throw new InvalidGameActionException("Tank Battle accepts movement, STOP, or SHOOT input only");
        }
        tank.lastInputSequence = inputSequence;
        sequence++;
        return EngineUpdate.state(snapshot());
    }

    @Override
    public EngineUpdate tick(Instant now) {
        if (terminal()) {
            return new EngineUpdate(false, snapshot(), true, outcomes());
        }
        double deltaSeconds = lastTick == null ? 0.05
                : Math.clamp(Duration.between(lastTick, now).toNanos() / 1_000_000_000.0, 0.01, 0.1);
        lastTick = now;
        boolean changed = moveTanks(deltaSeconds);
        changed |= moveBullets(deltaSeconds);
        boolean terminal = terminal();
        if (changed || terminal) sequence++;
        return new EngineUpdate(changed || terminal, snapshot(), terminal, terminal ? outcomes() : List.of());
    }

    private boolean moveTanks(double deltaSeconds) {
        boolean changed = false;
        for (MutableTank tank : tanks.values()) {
            if (!tank.alive || tank.dx == 0 && tank.dy == 0) continue;
            double proposedX = Math.clamp(tank.x + tank.dx * TANK_SPEED * deltaSeconds,
                    TANK_RADIUS, WIDTH - TANK_RADIUS);
            double proposedY = Math.clamp(tank.y + tank.dy * TANK_SPEED * deltaSeconds,
                    TANK_RADIUS, HEIGHT - TANK_RADIUS);
            boolean collides = tanks.values().stream().anyMatch(other -> other != tank && other.alive
                    && distanceSquared(proposedX, proposedY, other.x, other.y) < 4 * TANK_RADIUS * TANK_RADIUS);
            if (!collides && (proposedX != tank.x || proposedY != tank.y)) {
                tank.x = proposedX;
                tank.y = proposedY;
                changed = true;
            }
        }
        return changed;
    }

    private boolean moveBullets(double deltaSeconds) {
        boolean changed = !bullets.isEmpty();
        Iterator<MutableBullet> iterator = bullets.iterator();
        while (iterator.hasNext()) {
            MutableBullet bullet = iterator.next();
            bullet.x += bullet.dx * BULLET_SPEED * deltaSeconds;
            bullet.y += bullet.dy * BULLET_SPEED * deltaSeconds;
            if (bullet.x < 0 || bullet.x > WIDTH || bullet.y < 0 || bullet.y > HEIGHT) {
                iterator.remove();
                continue;
            }
            MutableTank hit = tanks.values().stream()
                    .filter(tank -> tank.alive && !tank.userId.equals(bullet.ownerId))
                    .filter(tank -> distanceSquared(bullet.x, bullet.y, tank.x, tank.y)
                            <= (TANK_RADIUS + BULLET_RADIUS) * (TANK_RADIUS + BULLET_RADIUS))
                    .findFirst().orElse(null);
            if (hit != null) {
                hit.hp = Math.max(0, hit.hp - BULLET_DAMAGE);
                if (hit.hp == 0) {
                    hit.alive = false;
                    hit.dx = 0;
                    hit.dy = 0;
                    MutableTank owner = tanks.get(bullet.ownerId);
                    if (owner != null) owner.kills++;
                }
                iterator.remove();
            }
        }
        evaluateTerminal();
        return changed;
    }

    private void shoot(MutableTank tank, Instant now) {
        if (tank.lastShotAt != null && now.isBefore(tank.lastShotAt.plus(SHOT_COOLDOWN))) {
            throw new InvalidGameActionException("Tank weapon is cooling down");
        }
        double radians = Math.toRadians(tank.rotation);
        double dx = Math.cos(radians);
        double dy = Math.sin(radians);
        bullets.add(new MutableBullet(UUID.randomUUID(), tank.userId,
                tank.x + dx * (TANK_RADIUS + 1), tank.y + dy * (TANK_RADIUS + 1), dx, dy));
        tank.lastShotAt = now;
    }

    private void evaluateTerminal() {
        List<MutableTank> alive = tanks.values().stream().filter(tank -> tank.alive).toList();
        if (alive.size() == 1) winner = alive.getFirst().userId;
        if (alive.isEmpty()) draw = true;
    }

    @Override
    public boolean terminal() {
        return winner != null || draw;
    }

    @Override
    public List<EngineOutcome> outcomes() {
        return tanks.values().stream().map(tank -> new EngineOutcome(tank.userId,
                draw ? GameResultType.DRAW : tank.userId.equals(winner) ? GameResultType.WIN : GameResultType.LOSS,
                tank.kills)).toList();
    }

    private static double distanceSquared(double x1, double y1, double x2, double y2) {
        double dx = x1 - x2;
        double dy = y1 - y2;
        return dx * dx + dy * dy;
    }

    private static boolean finite(double... values) {
        for (double value : values) {
            if (!Double.isFinite(value)) return false;
        }
        return true;
    }

    private static boolean validRotation(double rotation) {
        return rotation == 0 || rotation == 90 || rotation == 180 || rotation == 270;
    }

    private static boolean validDirection(double dx, double dy) {
        return dx == Math.rint(dx) && dy == Math.rint(dy)
                && Math.abs(dx) <= 1 && Math.abs(dy) <= 1 && Math.abs(dx) + Math.abs(dy) <= 1;
    }

    private void validateOutcome() {
        List<MutableTank> alive = tanks.values().stream().filter(tank -> tank.alive).toList();
        int totalKills = tanks.values().stream().mapToInt(tank -> tank.kills).sum();
        int destroyed = tanks.size() - alive.size();
        if (winner != null && draw
                || winner != null && (alive.size() != 1 || !alive.getFirst().userId.equals(winner))
                || draw && !alive.isEmpty()
                || winner == null && !draw && alive.size() < 2
                || totalKills != destroyed) {
            throw new IllegalArgumentException("Tank Battle checkpoint outcome is invalid");
        }
    }

    private static final class MutableTank {
        private final UUID userId;
        private double x;
        private double y;
        private double rotation;
        private int hp = 100;
        private boolean alive = true;
        private int kills;
        private double dx;
        private double dy;
        private long lastInputSequence = -1;
        private Instant lastShotAt;

        private MutableTank(UUID userId, double x, double y) {
            this.userId = userId;
            this.x = x;
            this.y = y;
        }

        private void move(double dx, double dy, double rotation) {
            this.dx = dx;
            this.dy = dy;
            this.rotation = rotation;
        }

        private TankView view() {
            return new TankView(userId, x, y, rotation, hp, alive, kills, lastInputSequence);
        }
    }

    private static final class MutableBullet {
        private final UUID id;
        private final UUID ownerId;
        private double x;
        private double y;
        private final double dx;
        private final double dy;

        private MutableBullet(UUID id, UUID ownerId, double x, double y, double dx, double dy) {
            this.id = id;
            this.ownerId = ownerId;
            this.x = x;
            this.y = y;
            this.dx = dx;
            this.dy = dy;
        }

        private BulletView view() {
            return new BulletView(id, ownerId, x, y);
        }
    }
}
