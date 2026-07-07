package ac.grim.grimac.checks.impl.prediction;

import ac.grim.grimac.api.config.ConfigManager;
import ac.grim.grimac.checks.Check;
import ac.grim.grimac.checks.CheckData;
import ac.grim.grimac.checks.type.PostPredictionCheck;
import ac.grim.grimac.player.GrimPlayer;
import ac.grim.grimac.utils.anticheat.update.PredictionComplete;
import ac.grim.grimac.utils.collisions.datatypes.SimpleCollisionBox;
import ac.grim.grimac.utils.nmsutil.Collisions;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState;
import com.github.retrooper.packetevents.protocol.world.states.defaulttags.BlockTags;
import com.github.retrooper.packetevents.protocol.world.states.type.StateTypes;

import java.util.ArrayList;
import java.util.List;

@CheckData(name = "Phase", stableKey = "grim.prediction.phase", description = "Moved into a solid block during movement prediction", setback = 1, decay = 0.005)
public class Phase extends Check implements PostPredictionCheck {
    private SimpleCollisionBox oldBB;
    private boolean flagPersistentCollisions;

    public Phase(GrimPlayer player) {
        super(player);
        oldBB = player.boundingBox;
    }

    @Override
    public void onPredictionComplete(final PredictionComplete predictionComplete) {
        if (!player.getSetbackTeleportUtil().blockOffsets && !predictionComplete.getData().isTeleport() && predictionComplete.isChecked()) { // Not falling through world
            SimpleCollisionBox newBB = player.boundingBox;

            List<SimpleCollisionBox> boxes = new ArrayList<>();
            Collisions.getCollisionBoxes(player, newBB, boxes, false);

            for (SimpleCollisionBox box : boxes) {
                if (!newBB.isIntersected(box)) continue;
                if (!flagPersistentCollisions && oldBB.isIntersected(box)) continue;

                if (isIgnoredLegacyCollision(box)) {
                    continue;
                }

                flagWithSetback();
                return;
            }
        }

        oldBB = player.boundingBox;
        reward();
    }

    private boolean isIgnoredLegacyCollision(SimpleCollisionBox box) {
        if (player.getClientVersion().isNewerThan(ClientVersion.V_1_8)) return false;

        // A bit of a hacky way to get the block state, but this is much faster to use the tuinity method for grabbing collision boxes
        WrappedBlockState state = player.compensatedWorld.getBlock((box.minX + box.maxX) / 2, (box.minY + box.maxY) / 2, (box.minZ + box.maxZ) / 2);
        return BlockTags.ANVIL.contains(state.getType()) || state.getType() == StateTypes.CHEST || state.getType() == StateTypes.TRAPPED_CHEST;
    }

    @Override
    public void onReload(ConfigManager config) {
        flagPersistentCollisions = config.getBooleanElse(getConfigName() + ".flag-persistent-collisions", false);
    }
}
