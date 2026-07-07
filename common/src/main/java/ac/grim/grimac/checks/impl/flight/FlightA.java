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
    private static final Verbose V = Verbose.of("vertical={f64}, predicted={f64}, advantage={f64}, buffer={f64}, air={uint}");

    private double verticalAdvantageThreshold;
    private double maxCumulativeAdvantage;
    private double advantageDecay;
    private double hoverVerticalSpeed;
    private double advantageBuffer;
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
        double positiveAdvantage = Math.max(0, advantage);
        boolean hovering = Math.abs(vertical) <= hoverVerticalSpeed && predicted < -hoverVerticalSpeed;

        if (positiveAdvantage > 0) {
            advantageBuffer = Math.min(maxCumulativeAdvantage * 2, advantageBuffer + positiveAdvantage);
        } else {
            advantageBuffer = Math.max(0, advantageBuffer - advantageDecay);
        }
        hoverTicks = hovering ? hoverTicks + 1 : 0;

        if (airTicks >= minAirTicks && (advantage > verticalAdvantageThreshold || advantageBuffer > maxCumulativeAdvantage || hoverTicks >= maxHoverTicks)) {
            if (flag(V.write(verbose()).f64(vertical).f64(predicted).f64(advantage).f64(advantageBuffer).uint(airTicks))) {
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
        advantageBuffer = 0;
    }

    @Override
    public void onReload(ConfigManager config) {
        verticalAdvantageThreshold = config.getDoubleElse(getConfigName() + ".vertical-advantage-threshold", 0.08);
        maxCumulativeAdvantage = config.getDoubleElse(getConfigName() + ".max-cumulative-advantage", 0.24);
        advantageDecay = config.getDoubleElse(getConfigName() + ".advantage-decay", 0.03);
        hoverVerticalSpeed = config.getDoubleElse(getConfigName() + ".hover-vertical-speed", 0.04);
        minAirTicks = config.getIntElse(getConfigName() + ".min-air-ticks", 6);
        maxHoverTicks = config.getIntElse(getConfigName() + ".max-hover-ticks", 4);
        reset();
    }
}
