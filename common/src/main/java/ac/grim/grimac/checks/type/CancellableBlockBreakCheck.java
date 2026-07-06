package ac.grim.grimac.checks.type;

import ac.grim.grimac.api.config.ConfigManager;
import ac.grim.grimac.checks.Check;
import ac.grim.grimac.player.GrimPlayer;

public abstract class CancellableBlockBreakCheck extends Check implements BlockBreakCheck {
    protected int cancelVL;

    public CancellableBlockBreakCheck(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onReload(ConfigManager config) {
        cancelVL = config.getIntElse(getConfigName() + ".cancelvl",
                config.getIntElse(getConfigName() + ".cancelVL", defaultCancelVL()));
    }

    protected int defaultCancelVL() {
        return 0;
    }

    protected boolean shouldCancelBlockBreak() {
        return shouldModifyPackets() && cancelVL >= 0 && violations >= cancelVL;
    }
}
