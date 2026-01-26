package ca.teamdman.sfm.client.handler;

import ca.teamdman.sfm.client.registry.SFMKeyMappings;
import ca.teamdman.sfm.common.event_bus.SFMSubscribeEvent;
import ca.teamdman.sfm.common.net.ServerboundNetworkToolToggleOverlayPacket;
import ca.teamdman.sfm.common.registry.SFMItems;
import ca.teamdman.sfm.common.registry.SFMPackets;
import ca.teamdman.sfm.common.util.SFMDist;
import ca.teamdman.sfm.common.util.SFMHandUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;


public class NetworkToolKeyMappingHandler {
    private static ToggleKeyState toggleKeyState = ToggleKeyState.Idle;

    public static void setExternalDebounce() {
        toggleKeyState = ToggleKeyState.PressCancelledExternally;
    }

    @SuppressWarnings("DuplicatedCode")
    @SFMSubscribeEvent(value = SFMDist.CLIENT)
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return;
        Player player = minecraft.player;
        if (player == null) return;
        handleAltKeyLogic();
    }

    private static void handleAltKeyLogic() {
        Minecraft minecraft = Minecraft.getInstance();

        // don't do anything if a screen is open
        if (minecraft.screen != null) return;

        // only do something if the key was pressed
        boolean alt_down = SFMKeyMappings.isKeyDown(SFMKeyMappings.TOGGLE_NETWORK_TOOL_OVERLAY_KEY);
        switch (toggleKeyState) {
            case Idle -> {
                if (alt_down) {
                    toggleKeyState = ToggleKeyState.Pressed;
                }
            }
            case Pressed -> {
                if (!alt_down) {
                    toggleKeyState = ToggleKeyState.Idle;
                    assert minecraft.player != null;
                    InteractionHand hand = SFMHandUtils.getHandHoldingItem(
                            minecraft.player,
                            SFMItems.NETWORK_TOOL_ITEM.get()
                    );
                    if (hand == null) return;
                    // send packet to server to toggle mode
                    SFMPackets.sendToServer(new ServerboundNetworkToolToggleOverlayPacket(hand));
                }
            }
            case PressCancelledExternally -> {
                if (!alt_down) {
                    toggleKeyState = ToggleKeyState.Idle;
                }
            }
        }
    }

    private enum ToggleKeyState {
        Idle,
        Pressed,
        PressCancelledExternally,
    }
}
