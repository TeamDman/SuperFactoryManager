package ca.teamdman.sfm.client.handler;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.client.registry.SFMKeyMappings;
import ca.teamdman.sfm.common.net.ServerboundNetworkToolToggleOverlayPacket;
import ca.teamdman.sfm.common.registry.registration.SFMItems;
import ca.teamdman.sfm.common.registry.registration.SFMPackets;
import ca.teamdman.sfm.common.util.SFMHandUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.util.EnumHand;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;

@Mod.EventBusSubscriber(modid = SFM.MOD_ID, value = Side.CLIENT)
public class NetworkToolKeyMappingHandler {
    private static ToggleKeyState toggleKeyState = ToggleKeyState.Idle;

    public static void setExternalDebounce() {
        toggleKeyState = ToggleKeyState.PressCancelledExternally;
    }

    @SuppressWarnings("DuplicatedCode")
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.world == null) return;
        EntityPlayerSP player = minecraft.player;
        if (player == null) return;
        handleAltKeyLogic();
    }

    private static void handleAltKeyLogic() {
        Minecraft minecraft = Minecraft.getMinecraft();

        // don't do anything if a screen is open
        if (minecraft.currentScreen != null) return;

        // only do something if the key was pressed
        boolean alt_down = SFMKeyMappings.isKeyDown(SFMKeyMappings.TOGGLE_NETWORK_TOOL_OVERLAY_KEY);
        switch (toggleKeyState) {
            case Idle: {
                if (alt_down) {
                    toggleKeyState = ToggleKeyState.Pressed;
                }
                break;
            }
            case Pressed: {
                if (!alt_down) {
                    toggleKeyState = ToggleKeyState.Idle;
                    assert minecraft.player != null;
                    EnumHand hand = SFMHandUtils.getHandHoldingItem(
                            minecraft.player,
                            SFMItems.NETWORK_TOOL
                    );
                    if (hand == null) return;
                    // send packet to server to toggle mode
                    SFMPackets.sendToServer(new ServerboundNetworkToolToggleOverlayPacket(hand));
                }
                break;
            }
            case PressCancelledExternally: {
                if (!alt_down) {
                    toggleKeyState = ToggleKeyState.Idle;
                }
                break;
            }
        }
    }

    private enum ToggleKeyState {
        Idle,
        Pressed,
        PressCancelledExternally,
    }
}