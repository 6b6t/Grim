package ac.grim.grimac.checks.impl.flight;

import ac.grim.grimac.api.config.ConfigManager;
import ac.grim.grimac.api.storage.verbose.Verbose;
import ac.grim.grimac.checks.Check;
import ac.grim.grimac.checks.CheckData;
import ac.grim.grimac.checks.type.PostPredictionCheck;
import ac.grim.grimac.player.GrimPlayer;
import ac.grim.grimac.utils.anticheat.update.PredictionComplete;
import com.github.retrooper.packetevents.protocol.player.GameMode;

@CheckData(name = "Flight", stableKey = "grim.movement.flight", description = "Maintained unsupported vertical movement", setback = 0, decay = 0.05)
public class FlightA extends Check implements PostPredictionCheck {
    private static final Verbose V = Verbose.of("vertical={f64}, predicted={f64}, advantage={f64}, air={uint}");

    private double verticalAdvantageThreshold;
    private double hoverVerticalSpeed;
    private int minAirTicks;
    private int maxHoverTicks;
    private int airTicks;
    private int hoverTicks;

    public FlightA(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onPredictionComplete(PredictionComplete predictionComplete) {
        if (isExempt(predictionComplete)) {
            reset();
            reward();
            return;
        }

        if (player.onGround || player.lastOnGround) {
            reset();
            reward();
            return;
        }

        airTicks++;

        double vertical = player.actualMovement.getY();
        double predicted = player.predictedVelocity.vector.getY();
        double advantage = vertical - predicted;
        boolean hovering = Math.abs(vertical) <= hoverVerticalSpeed && predicted < -hoverVerticalSpeed;

        hoverTicks = hovering ? hoverTicks + 1 : 0;

        if (airTicks >= minAirTicks && (advantage > verticalAdvantageThreshold || hoverTicks >= maxHoverTicks)) {
            if (flag(V.write(verbose()).f64(vertical).f64(predicted).f64(advantage).uint(airTicks))) {
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
                || player.isClimbing
                || player.wasTouchingWater
                || player.wasTouchingLava
                || player.isSwimming
                || player.wasSwimming
                || player.compensatedEntities.getSlowFallingAmplifier().isPresent()
                || player.riptideSpinAttackTicks > 0
                || player.predictedVelocity.isKnockback()
                || player.predictedVelocity.isExplosion()
                || player.predictedVelocity.isTrident()
                || player.uncertaintyHandler.lastTeleportTicks.hasOccurredSince(2)
                || player.uncertaintyHandler.lastFlyingStatusChange.hasOccurredSince(5);
    }

    private void reset() {
        airTicks = 0;
        hoverTicks = 0;
    }

    @Override
    public void onReload(ConfigManager config) {
        verticalAdvantageThreshold = config.getDoubleElse(getConfigName() + ".vertical-advantage-threshold", 0.08);
        hoverVerticalSpeed = config.getDoubleElse(getConfigName() + ".hover-vertical-speed", 0.04);
        minAirTicks = config.getIntElse(getConfigName() + ".min-air-ticks", 6);
        maxHoverTicks = config.getIntElse(getConfigName() + ".max-hover-ticks", 4);
        reset();
    }
}
