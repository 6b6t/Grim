package ac.grim.grimac.checks.impl.movement;

import ac.grim.grimac.api.config.ConfigManager;
import ac.grim.grimac.api.storage.verbose.Verbose;
import ac.grim.grimac.checks.Check;
import ac.grim.grimac.checks.CheckData;
import ac.grim.grimac.checks.type.PacketCheck;
import ac.grim.grimac.manager.SetbackTeleportUtil;
import ac.grim.grimac.player.GrimPlayer;
import ac.grim.grimac.utils.math.Vector3dm;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.player.GameMode;
import com.github.retrooper.packetevents.protocol.world.Location;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerFlying;

@CheckData(name = "MovementSpeed", stableKey = "grim.movement.speed", description = "Moved too quickly horizontally", setback = 0, decay = 0.1)
public class MovementSpeed extends Check implements PacketCheck {
    private static final Verbose V = Verbose.of("horizontal={f64}, limit={f64}");
    private static final long MAX_INTERVAL_NANOS = 50_000_000L;
    private static final double EPSILON = 1.0E-3;

    private double maxHorizontalBlocksPerSecond;
    private double horizontalBudget;
    private long lastPositionNanos;

    public MovementSpeed(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (maxHorizontalBlocksPerSecond <= 0
                || player.isGliding
                || player.inVehicle()
                || player.isFlying
                || player.gamemode == GameMode.SPECTATOR
                || player.packetStateData.lastPacketWasTeleport
                || !WrapperPlayClientPlayerFlying.isFlying(event.getPacketType())) {
            resetBudget();
            reward();
            return;
        }

        if (maxHorizontalBlocksPerSecond <= 0) {
            reward();
            return;
        }

        WrapperPlayClientPlayerFlying packet = new WrapperPlayClientPlayerFlying(event);
        if (!packet.hasPositionChanged()) {
            reward();
            return;
        }

        Location location = packet.getLocation();
        double deltaX = location.getX() - player.x;
        double deltaZ = location.getZ() - player.z;
        double horizontalDistance = Math.hypot(deltaX, deltaZ);
        double maxHorizontalDistance = availableHorizontalDistance();

        if (horizontalDistance <= maxHorizontalDistance + EPSILON) {
            horizontalBudget = Math.max(0, horizontalBudget - horizontalDistance);
            reward();
            return;
        }

        horizontalBudget = 0;
        if (shouldModifyPackets()) {
            applyCappedSetback(event, location, deltaX, deltaZ, maxHorizontalDistance, horizontalDistance);
        }
        flag(V.write(verbose()).f64(horizontalDistance).f64(maxHorizontalDistance));
    }

    private double availableHorizontalDistance() {
        double maxPerTick = maxHorizontalBlocksPerSecond / 20.0;
        long now = System.nanoTime();
        if (lastPositionNanos == 0) {
            lastPositionNanos = now;
            horizontalBudget = maxPerTick;
            return horizontalBudget;
        }

        long elapsed = Math.max(0, Math.min(MAX_INTERVAL_NANOS, now - lastPositionNanos));
        lastPositionNanos = now;
        horizontalBudget = Math.min(maxPerTick, horizontalBudget + maxHorizontalBlocksPerSecond * (elapsed / 1_000_000_000.0));
        return horizontalBudget;
    }

    private void resetBudget() {
        lastPositionNanos = 0;
        horizontalBudget = maxHorizontalBlocksPerSecond / 20.0;
    }

    private void applyCappedSetback(PacketReceiveEvent event, Location location, double deltaX, double deltaZ, double maxHorizontalDistance, double horizontalDistance) {
        double scale = maxHorizontalDistance / horizontalDistance;
        Vector3d cappedPosition = new Vector3d(
                player.x + deltaX * scale,
                location.getY(),
                player.z + deltaZ * scale
        );

        player.getSetbackTeleportUtil().lastKnownGoodPosition = new SetbackTeleportUtil.SetbackPosWithVector(cappedPosition, new Vector3dm());
        player.getSetbackTeleportUtil().executeNonSimulatingSetback();
        event.setCancelled(true);
        player.onPacketCancel();
    }

    @Override
    public void onReload(ConfigManager config) {
        maxHorizontalBlocksPerSecond = config.getDoubleElse(getConfigName() + ".max-horizontal-blocks-per-second", -1);
        resetBudget();
    }
}
