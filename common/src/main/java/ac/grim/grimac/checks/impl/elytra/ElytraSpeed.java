package ac.grim.grimac.checks.impl.elytra;

import ac.grim.grimac.api.config.ConfigManager;
import ac.grim.grimac.api.storage.verbose.Verbose;
import ac.grim.grimac.checks.Check;
import ac.grim.grimac.checks.CheckData;
import ac.grim.grimac.checks.type.PacketCheck;
import ac.grim.grimac.player.GrimPlayer;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.world.Location;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerFlying;

@CheckData(name = "ElytraSpeed", stableKey = "grim.elytra.speed", description = "Moved too quickly while gliding", setback = 0, decay = 0.1)
public class ElytraSpeed extends Check implements PacketCheck {
    private static final Verbose V = Verbose.of("horizontal={f64}, vertical={f64}");

    private double maxHorizontalSpeed;
    private double maxAscendingSpeed;

    public ElytraSpeed(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (!player.isGliding || player.packetStateData.lastPacketWasTeleport || !WrapperPlayClientPlayerFlying.isFlying(event.getPacketType())) {
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
        double deltaY = location.getY() - player.y;
        double deltaZ = location.getZ() - player.z;
        double horizontalSpeed = Math.hypot(deltaX, deltaZ);
        double ascendingSpeed = Math.max(0, deltaY);
        if (horizontalSpeed <= maxHorizontalSpeed && ascendingSpeed <= maxAscendingSpeed) {
            reward();
            return;
        }

        if (flag(V.write(verbose()).f64(horizontalSpeed).f64(ascendingSpeed)) && shouldModifyPackets()) {
            packet.setLocation(clampLocation(location, deltaX, deltaY, deltaZ, horizontalSpeed));
            event.markForReEncode(true);
            player.fallDistance = 0;
        }
    }

    private Location clampLocation(Location location, double deltaX, double deltaY, double deltaZ, double horizontalSpeed) {
        double horizontalScale = horizontalSpeed > 0 ? Math.min(1, maxHorizontalSpeed / horizontalSpeed) : 0;
        double y = deltaY > maxAscendingSpeed ? player.y + maxAscendingSpeed : location.getY();
        return new Location(
                player.x + deltaX * horizontalScale,
                y,
                player.z + deltaZ * horizontalScale,
                location.getYaw(),
                location.getPitch()
        );
    }

    @Override
    public void onReload(ConfigManager config) {
        maxHorizontalSpeed = config.getDoubleElse(getConfigName() + ".max-horizontal-speed", 3.25);
        maxAscendingSpeed = config.getDoubleElse(getConfigName() + ".max-ascending-speed", 2.5);
    }
}
