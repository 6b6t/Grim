package ac.grim.grimac.checks.impl.flight;

import ac.grim.grimac.checks.Check;
import ac.grim.grimac.checks.CheckData;
import ac.grim.grimac.checks.type.PacketCheck;
import ac.grim.grimac.player.GrimPlayer;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerFlying;

@CheckData(name = "Flight", stableKey = "grim.movement.flight", description = "Legacy flying packet placeholder signal", setback = 0, decay = 0.05)
public class FlightA extends Check implements PacketCheck {
    public FlightA(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (WrapperPlayClientPlayerFlying.isFlying(event.getPacketType()) && !player.isFlying) {
            flag();
        }
    }
}
