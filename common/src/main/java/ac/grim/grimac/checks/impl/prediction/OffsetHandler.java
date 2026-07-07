package ac.grim.grimac.checks.impl.prediction;

import ac.grim.grimac.GrimAPI;
import ac.grim.grimac.api.config.ConfigManager;
import ac.grim.grimac.api.event.events.CompletePredictionEvent;
import ac.grim.grimac.api.storage.verbose.Verbose;
import ac.grim.grimac.checks.Check;
import ac.grim.grimac.checks.CheckData;
import ac.grim.grimac.checks.type.PostPredictionCheck;
import ac.grim.grimac.player.GrimPlayer;
import ac.grim.grimac.utils.anticheat.update.PredictionComplete;

import java.util.concurrent.atomic.AtomicInteger;

@CheckData(name = "Simulation", stableKey = "grim.prediction.simulation", description = "Moved differently than predicted movement simulation", decay = 0.02)
public class OffsetHandler extends Check implements PostPredictionCheck {
    private static final Verbose V = Verbose.of("{offset}");
    private static final double NORMAL_THRESHOLD = 0.001;
    private static final double NORMAL_IMMEDIATE_SETBACK_THRESHOLD = 0.1;
    private static final double NORMAL_MAX_ADVANTAGE = 1;
    private static final double NORMAL_SETBACK_VIOLATION_THRESHOLD = 1;

    private static final AtomicInteger flags = new AtomicInteger(0);
    // Config
    private double setbackDecayMultiplier;
    private double threshold;
    private double immediateSetbackThreshold;
    private double maxAdvantage;
    private double maxCeiling;
    private double setbackViolationThreshold;
    private boolean useNormalFluidThresholds;
    // Current advantage gained
    private double advantageGained = 0;
    private static final CompletePredictionEvent.Channel COMPLETE_CHANNEL = GrimAPI.INSTANCE.getEventBus().get(CompletePredictionEvent.class);

    public OffsetHandler(GrimPlayer player) {
        super(player);
    }

    public void onPredictionComplete(final PredictionComplete predictionComplete) {
        if (!predictionComplete.isChecked()) return;

        double offset = predictionComplete.getOffset();

        if (COMPLETE_CHANNEL.fire(player, this, offset)) return;

        boolean normalFluidSimulation = isNormalFluidSimulation();
        double activeThreshold = normalFluidSimulation ? NORMAL_THRESHOLD : threshold;
        double activeImmediateSetbackThreshold = normalFluidSimulation ? NORMAL_IMMEDIATE_SETBACK_THRESHOLD : immediateSetbackThreshold;
        double activeMaxAdvantage = normalFluidSimulation ? NORMAL_MAX_ADVANTAGE : maxAdvantage;
        double activeSetbackViolationThreshold = normalFluidSimulation ? NORMAL_SETBACK_VIOLATION_THRESHOLD : setbackViolationThreshold;

        if ((offset >= activeThreshold || offset >= activeImmediateSetbackThreshold)) {
            advantageGained += offset;
            giveOffsetLenienceNextTick(offset);

            synchronized (flags) {
                int flagId = (flags.get() & 255) + 1; // 1-256 as possible values

                if (flag(V.write(verbose()).f64(offset), () -> humanFormattedOffset(offset) + " /gl " + flagId)) {
                    flags.incrementAndGet();
                    predictionComplete.setIdentifier(flagId);

                    if ((advantageGained >= activeMaxAdvantage || offset >= activeImmediateSetbackThreshold)
                            && shouldUseSetbacks()
                            && violations >= activeSetbackViolationThreshold) {
                        executeViolationSetback();
                    }
                }
            }

            advantageGained = Math.min(advantageGained, maxCeiling);
        } else {
            advantageGained *= setbackDecayMultiplier;
        }

        removeOffsetLenience();
    }

    public static String humanFormattedOffset(double offset) {
        String humanFormattedOffset;
        if (offset < 0.001) { // 1.129E-3
            humanFormattedOffset = String.format("%.4E", offset);
            // Squeeze out an extra digit here by E-03 to E-3
            humanFormattedOffset = humanFormattedOffset.replace("E-0", "E-");
        } else {
            // 0.00112945678 -> .001129
            humanFormattedOffset = String.format("%6f", offset);
            // I like the leading zero, but removing it lets us add another digit to the end
            humanFormattedOffset = humanFormattedOffset.replace("0.", ".");
        }
        return humanFormattedOffset;
    }

    private void giveOffsetLenienceNextTick(double offset) {
        // Don't let players carry more than 1 offset into the next tick
        // (I was seeing cheats try to carry 1,000,000,000 offset into the next tick!)
        //
        // This value so that setting back with high ping doesn't allow players to gather high client velocity
        double minimizedOffset = Math.min(offset, 1);

        // Normalize offsets
        player.uncertaintyHandler.lastHorizontalOffset = minimizedOffset;
        player.uncertaintyHandler.lastVerticalOffset = minimizedOffset;
    }

    private void removeOffsetLenience() {
        player.uncertaintyHandler.lastHorizontalOffset = 0;
        player.uncertaintyHandler.lastVerticalOffset = 0;
    }

    private boolean isNormalFluidSimulation() {
        return useNormalFluidThresholds
                && !player.inVehicle()
                && !player.isFlying
                && !player.isGliding
                && (player.wasTouchingWater
                || player.wasTouchingLava
                || player.compensatedWorld.containsLiquid(player.boundingBox.copy().expand(0.1, 0.1, 0.1)));
    }

    @Override
    public void onReload(ConfigManager config) {
        setbackDecayMultiplier = config.getDoubleElse("Simulation.setback-decay-multiplier", 0.999);
        threshold = config.getDoubleElse("Simulation.threshold", 0.001);
        immediateSetbackThreshold = config.getDoubleElse("Simulation.immediate-setback-threshold", 0.1);
        maxAdvantage = config.getDoubleElse("Simulation.max-advantage", 1);
        maxCeiling = config.getDoubleElse("Simulation.max-ceiling", 4);
        setbackViolationThreshold = config.getDoubleElse("Simulation.setback-violation-threshold", 1);
        useNormalFluidThresholds = config.getBooleanElse("Simulation.use-normal-fluid-thresholds", true);
        if (maxAdvantage == -1) maxAdvantage = Double.MAX_VALUE;
        if (immediateSetbackThreshold == -1) immediateSetbackThreshold = Double.MAX_VALUE;
    }

    public boolean doesOffsetFlag(double offset) {
        return offset >= (isNormalFluidSimulation() ? NORMAL_THRESHOLD : threshold);
    }
}
