package ca.teamdman.sfm.client.handler;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.client.registry.SFMKeyMappings;
import ca.teamdman.sfm.common.item.LabelGunItem;
import ca.teamdman.sfm.common.net.ServerboundLabelGunCycleViewModePacket;
import ca.teamdman.sfm.common.net.ServerboundLabelGunSetActiveLabelPacket;
import ca.teamdman.sfm.common.registry.registration.SFMItems;
import ca.teamdman.sfm.common.registry.registration.SFMPackets;
import ca.teamdman.sfm.common.util.SFMHandUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumHand;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.ClientTickEvent;
import net.minecraftforge.fml.relauncher.Side;

@Mod.EventBusSubscriber(modid = SFM.MOD_ID, value = Side.CLIENT)
public class LabelGunKeyMappingHandler {
    private static final KeyState cycleViewKeyState = KeyState.idle();
    private static final KeyState nextLabelKeyState = KeyState.idle();
    private static final KeyState prevLabelKeyState = KeyState.idle();
    private static boolean labelSwitchKeyDown = false;

    public static void setExternalDebounce() {
        cycleViewKeyState.debounce();
        nextLabelKeyState.debounce();
        prevLabelKeyState.debounce();
    }

    @SuppressWarnings("DuplicatedCode")
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.world == null) return;
        EntityPlayerSP player = minecraft.player;
        if (player == null) return;
        handleAltKeyLogic(minecraft, player);
        handleLabelSwitchKeyLogic(minecraft, player);
    }

    private static void handleLabelSwitchKeyLogic(Minecraft minecraft, EntityPlayer player) {
        boolean nextDown = SFMKeyMappings.isKeyDown(SFMKeyMappings.LABEL_GUN_NEXT_LABEL_KEY);
        boolean nextPress = nextLabelKeyState.handleKey(nextDown);
        boolean prevDown = SFMKeyMappings.isKeyDown(SFMKeyMappings.LABEL_GUN_PREVIOUS_LABEL_KEY);
        boolean prevPress = prevLabelKeyState.handleKey(prevDown);
        if (nextPress || prevPress) {
            // don't do anything if a screen is open
            if (minecraft.currentScreen != null) return;
            var labelGun = SFMHandUtils.getItemAndHand(player, SFMItems.LABEL_GUN);
            if (labelGun == null) return;
            var nextLabel = LabelGunItem.getNextLabel(labelGun.stack(), prevPress ? -1 : 1);
            SFMPackets.sendToServer(new ServerboundLabelGunSetActiveLabelPacket(nextLabel, labelGun.hand()));
        }
    }

    private static void handleAltKeyLogic(Minecraft minecraft, EntityPlayer player) {
        // only do something if the key was pressed
        boolean keyDown = SFMKeyMappings.isKeyDown(SFMKeyMappings.CYCLE_LABEL_VIEW_KEY);
        boolean keyPress = cycleViewKeyState.handleKey(keyDown);
        if (keyPress) {
            // don't do anything if a screen is open
            if (minecraft.currentScreen != null) return;
            EnumHand hand = SFMHandUtils.getHandHoldingItem(
                    player,
                    SFMItems.LABEL_GUN
            );if (hand == null) return;
            // send packet to server to toggle mode
            SFMPackets.sendToServer(new ServerboundLabelGunCycleViewModePacket(hand));
        }
    }

    private static class KeyState {

        private KeyStateEnum state;

        private KeyState(KeyStateEnum state) {
            this.state = state;
        }

        public static KeyState idle() {
            return new KeyState(KeyStateEnum.Idle);
        }

        public boolean handleKey(boolean isDown) {
            switch (state) {
                case Idle -> {
                    if (isDown) {
                        state = KeyStateEnum.Pressed;
                    }
                }
                case Pressed -> {
                    if (!isDown) {
                        state = KeyStateEnum.Idle;
                        return true;
                    }
                }
                case PressCancelledExternally -> {
                    if (!isDown) {
                        state = KeyStateEnum.Idle;
                    }
                }
            }
            return false;
        }

        public void debounce() {
            if (state == KeyStateEnum.Pressed) {
                state = KeyStateEnum.PressCancelledExternally;
            }
        }

        private enum KeyStateEnum {
            Idle,
            Pressed,
            PressCancelledExternally,
        }
    }
}
