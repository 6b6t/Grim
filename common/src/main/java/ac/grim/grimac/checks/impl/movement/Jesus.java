package ac.grim.grimac.checks.impl.movement;

import ac.grim.grimac.api.config.ConfigManager;
import ac.grim.grimac.api.storage.verbose.Verbose;
import ac.grim.grimac.checks.Check;
import ac.grim.grimac.checks.CheckData;
import ac.grim.grimac.checks.type.PostPredictionCheck;
import ac.grim.grimac.player.GrimPlayer;
import ac.grim.grimac.utils.anticheat.update.PredictionComplete;
import ac.grim.grimac.utils.collisions.datatypes.SimpleCollisionBox;
import ac.grim.grimac.utils.math.GrimMath;
import com.github.retrooper.packetevents.protocol.player.GameMode;

@CheckData(name = "Jesus", stableKey = "grim.movement.jesus", description = "Moved on top of fluid without swimming", setback = 0, decay = 0.05)
public class Jesus extends Check implements PostPredictionCheck {
    private static final Verbose V = Verbose.of("horizontal={f64}, vertical={f64}, surface={f64}, ticks={uint}");

    private double surfaceTolerance;
    private double maxVerticalSpeed;
    private double minHorizontalDistance;
    private int minSurfaceTicks;
    private int surfaceTicks;

    public Jesus(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onPredictionComplete(PredictionComplete predictionComplete) {
        if (isExempt(predictionComplete)) {
            reset();
            reward();
            return;
        }

        double surfaceDistance = fluidSurfaceDistance();
        double horizontal = Math.hypot(player.actualMovement.getX(), player.actualMovement.getZ());
        double vertical = player.actualMovement.getY();
        boolean walkingOnFluid = surfaceDistance <= surfaceTolerance
                && Math.abs(vertical) <= maxVerticalSpeed
                && horizontal >= minHorizontalDistance;

        surfaceTicks = walkingOnFluid ? surfaceTicks + 1 : 0;

        if (surfaceTicks >= minSurfaceTicks) {
            if (flag(V.write(verbose()).f64(horizontal).f64(vertical).f64(surfaceDistance).uint(surfaceTicks))) {
                executeViolationSetback();
            }
            return;
        }

        reward();
    }

    private boolean isExempt(PredictionComplete predictionComplete) {
        return !predictionComplete.isChecked()
                || predictionComplete.getData().isTeleport()
                || player.getSetbackTeleportUtil().blockOffsets
                || player.inVehicle()
                || player.isFlying
                || player.canFly
                || player.isGliding
                || player.gamemode == GameMode.CREATIVE
                || player.gamemode == GameMode.SPECTATOR
                || player.onGround
                || player.lastOnGround
                || player.wasTouchingWater
                || player.wasTouchingLava
                || player.isSwimming
                || player.wasSwimming
                || player.isClimbing
                || player.riptideSpinAttackTicks > 0;
    }

    private double fluidSurfaceDistance() {
        SimpleCollisionBox box = player.boundingBox;
        int minX = GrimMath.floor(box.minX + 0.001);
        int maxX = GrimMath.floor(box.maxX - 0.001);
        int minZ = GrimMath.floor(box.minZ + 0.001);
        int maxZ = GrimMath.floor(box.maxZ - 0.001);
        int y = GrimMath.floor(player.y - 0.03);
        double closest = Double.MAX_VALUE;

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                double fluidHeight = Math.max(
                        player.compensatedWorld.getWaterFluidLevelAt(x, y, z),
                        player.compensatedWorld.getLavaFluidLevelAt(x, y, z)
                );
                if (fluidHeight <= 0) continue;

                double surfaceY = y + fluidHeight;
                double distance = Math.abs(player.y - surfaceY);
                closest = Math.min(closest, distance);
            }
        }

        return closest;
    }

    private void reset() {
        surfaceTicks = 0;
    }

    @Override
    public void onReload(ConfigManager config) {
        surfaceTolerance = config.getDoubleElse(getConfigName() + ".surface-tolerance", 0.08);
        maxVerticalSpeed = config.getDoubleElse(getConfigName() + ".max-vertical-speed", 0.05);
        minHorizontalDistance = config.getDoubleElse(getConfigName() + ".min-horizontal-distance", 0.03);
        minSurfaceTicks = config.getIntElse(getConfigName() + ".min-surface-ticks", 4);
        reset();
    }
}
