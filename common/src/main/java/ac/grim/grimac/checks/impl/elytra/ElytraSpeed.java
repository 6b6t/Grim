package ac.grim.grimac.checks.impl.elytra;

import ac.grim.grimac.api.config.ConfigManager;
import ac.grim.grimac.api.storage.verbose.Verbose;
import ac.grim.grimac.checks.Check;
import ac.grim.grimac.checks.CheckData;
import ac.grim.grimac.checks.type.PostPredictionCheck;
import ac.grim.grimac.player.GrimPlayer;
import ac.grim.grimac.utils.anticheat.update.PredictionComplete;

@CheckData(name = "ElytraSpeed", stableKey = "grim.elytra.speed", description = "Moved too quickly while gliding", setback = 0, decay = 0.1)
public class ElytraSpeed extends Check implements PostPredictionCheck {
    private static final Verbose V = Verbose.of("horizontal={f64}, vertical={f64}");

    private double maxHorizontalSpeed;
    private double maxAscendingSpeed;

    public ElytraSpeed(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onPredictionComplete(PredictionComplete predictionComplete) {
        if (!player.isGliding) {
            reward();
            return;
        }

        double horizontalSpeed = Math.hypot(player.actualMovement.getX(), player.actualMovement.getZ());
        double ascendingSpeed = Math.max(0, player.actualMovement.getY());
        if (horizontalSpeed <= maxHorizontalSpeed && ascendingSpeed <= maxAscendingSpeed) {
            reward();
            return;
        }

        flagWithSetback(V.write(verbose()).f64(horizontalSpeed).f64(ascendingSpeed));
    }

    @Override
    public void onReload(ConfigManager config) {
        maxHorizontalSpeed = config.getDoubleElse(getConfigName() + ".max-horizontal-speed", 3.25);
        maxAscendingSpeed = config.getDoubleElse(getConfigName() + ".max-ascending-speed", 2.5);
    }
}
