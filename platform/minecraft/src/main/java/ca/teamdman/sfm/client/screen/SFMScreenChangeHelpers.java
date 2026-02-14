package ca.teamdman.sfm.client.screen;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.client.examples.SFMExampleProgram;
import ca.teamdman.sfm.client.screen.text_editor.ISFMTextEditScreen;
import ca.teamdman.sfm.client.screen.text_editor.SFMTextEditScreenV1;
import ca.teamdman.sfm.client.text_editor.ISFMTextEditScreenOpenContext;
import ca.teamdman.sfm.client.text_editor.SFMTextEditScreenDiskOpenContext;
import ca.teamdman.sfm.client.text_editor.SFMTextEditScreenExampleProgramOpenContext;
import ca.teamdman.sfm.common.containermenu.ManagerContainerMenu;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfm.common.localization.LocalizationKeys;
import ca.teamdman.sfm.common.net.ServerboundManagerLogDesireUpdatePacket;
import ca.teamdman.sfm.common.registry.SFMPackets;
import ca.teamdman.sfm.common.util.CollectionUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.IResource;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class SFMScreenChangeHelpers {

    public static void setOrPushScreen(GuiScreen screen) {
        var current = Minecraft.getMinecraft().currentScreen;
        if (current == null) {
            Minecraft
                    .getMinecraft()
                    .displayGuiScreen(screen);
        } else {
            if (screen instanceof IStackableScreen stackableScreen) {
                stackableScreen.setParent(current);
            }
            Minecraft
                    .getMinecraft()
                    .displayGuiScreen(screen);
        }
    }

    public static void popScreen() {

        Minecraft mc = Minecraft.getMinecraft();
        GuiScreen currentScreen = mc.currentScreen;
        if (currentScreen instanceof IStackableScreen) {
            IStackableScreen stackableScreen = (IStackableScreen) currentScreen;
            if (stackableScreen.getParent() != null) {
                Minecraft.getMinecraft().displayGuiScreen(stackableScreen.getParent());
                return;
            }
        }
        Minecraft.getMinecraft().displayGuiScreen(null);
    }

    public static void showLabelGunScreen(
            ItemStack stack,
            EnumHand hand
    ) {

        setOrPushScreen(new LabelGunScreen(stack, hand));
    }

    public static ISFMTextEditScreen createProgramEditScreen(
            ISFMTextEditScreenOpenContext openContext
    ) {

        return new SFMTextEditScreenV1(openContext);
    }

    public static void showProgramEditScreen(
            ISFMTextEditScreenOpenContext context
    ) {

        showTextEditScreen(createProgramEditScreen(context));
    }

    public static void showTextEditScreen(
            ISFMTextEditScreen screen
    ) {

        switch (screen.openBehaviour()) {
            case Push -> setOrPushScreen((GuiScreen) screen);
            case Replace -> setScreen((GuiScreen) screen);
        }
    }

    public static void showTomlEditScreen(
            TomlEditScreenOpenContext context
    ) {

        SFMTextEditScreenV1 screen = new TomlEditScreen(context);
        setOrPushScreen(screen);
        screen.scrollToTop();
    }

    public static void showProgramEditScreen(String initialContent) {

        ISFMTextEditScreenOpenContext openContext = new SFMTextEditScreenDiskOpenContext(
                initialContent,
                LabelPositionHolder.empty(),
                (x) -> {
                }
        );
        showProgramEditScreen(openContext);
    }

    public static void showExampleListScreen(
            String diskProgramString,
            LabelPositionHolder labelPositionHolder,
            Consumer<String> saveCallback
    ) {

        setOrPushScreen(new ExamplesScreen((chosenExample, templates) -> {
            SFMTextEditScreenV1 screen = new SFMTextEditScreenV1(new SFMTextEditScreenExampleProgramOpenContext(
                    chosenExample,
                    diskProgramString,
                    templates,
                    labelPositionHolder,
                    saveCallback
            ));
            setOrPushScreen(screen);
            screen.scrollToTop();
        }));
    }

    public static void showLogsScreen(ManagerContainerMenu menu) {

        LogsScreen screen = new LogsScreen(menu);
        setOrPushScreen(screen);
        screen.scrollToBottom();
        SFMPackets.sendToServer(new ServerboundManagerLogDesireUpdatePacket(
                menu.windowId,
                menu.MANAGER_POSITION,
                true
        ));
    }

    // TODO: copy item id, not just NBT
    // TODO: replace with showing a screen with the data
    public static void showItemInspectorScreen(ItemStack stack) {

        NBTTagCompound tag = stack.getTagCompound();
        if (tag != null) {
            String content = tag.toString();
            Minecraft minecraft = Minecraft.getMinecraft();
            GuiScreen.setClipboardString(content);
            SFM.LOGGER.info("Copied {} characters to clipboard", content.length());
            assert minecraft.player != null;
            minecraft.player.sendMessage(
                    LocalizationKeys.ITEM_INSPECTOR_COPIED_TO_CLIPBOARD.getComponent(
                            new TextComponentString(String.valueOf(content.length())).setStyle(new net.minecraft.util.text.Style().setColor(TextFormatting.AQUA))
                    )
            );
        }
    }

    public static void showChangelog() {

        SFMExampleProgram changelogExampleProgram = SFMExampleProgram.getChangelog();
        SFMTextEditScreenV1 screen = new SFMTextEditScreenV1(new SFMTextEditScreenExampleProgramOpenContext(
                changelogExampleProgram.programString(),
                changelogExampleProgram.programString(),
                Arrays.asList(changelogExampleProgram),
                LabelPositionHolder.empty(),
                newContent -> {
                }
        ));
        setOrPushScreen(screen);
        screen.scrollToTop();
    }

    public static @Nullable GuiScreen getCurrentScreen() {

        return Minecraft.getMinecraft().currentScreen;
    }

    public static void setScreen(@Nullable GuiScreen screen) {

        Minecraft.getMinecraft().displayGuiScreen(screen);
    }

}