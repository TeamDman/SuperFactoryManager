package ca.teamdman.sfm.client.screen;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.client.screen.text_editor.ISFMTextEditScreen;
import ca.teamdman.sfm.client.screen.text_editor.SFMTextEditScreenV1;
import ca.teamdman.sfm.client.text_editor.ISFMTextEditScreenOpenContext;
import ca.teamdman.sfm.client.text_editor.ISFMTextEditorRegistration;
import ca.teamdman.sfm.client.text_editor.SFMTextEditScreenDiskOpenContext;
import ca.teamdman.sfm.client.text_editor.SFMTextEditScreenExampleProgramOpenContext;
import ca.teamdman.sfm.common.config.SFMClientTextEditorConfig;
import ca.teamdman.sfm.common.containermenu.ManagerContainerMenu;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfm.common.localization.LocalizationKeys;
import ca.teamdman.sfm.common.net.ServerboundManagerLogDesireUpdatePacket;
import ca.teamdman.sfm.common.registry.SFMPackets;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.EnumHand;
import net.minecraft.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class SFMScreenChangeHelpers {
    public static void setOrPushScreen(GuiScreen screen) {
        if (Minecraft.getMinecraft().currentScreen == null) {
            Minecraft
                    .getMinecraft()
                    .displayGuiScreen(screen);
        } else {
            Minecraft
                    .getMinecraft()
                    .screen(screen);
        }
    }

    public static void popScreen() {
        Minecraft.getInstance().popGuiLayer();
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

        ISFMTextEditorRegistration textEditorRegistration = SFMClientTextEditorConfig.getPreferredTextEditor();
        return textEditorRegistration.createScreen(openContext);
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
            case Push -> setOrPushScreen(screen.asScreen());
            case Replace -> setScreen(screen.asScreen());
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
                menu.containerId,
                menu.MANAGER_POSITION,
                true
        ));
    }

    // TODO: copy item id, not just NBT
    // TODO: replace with showing a screen with the data
    public static void showItemInspectorScreen(ItemStack stack) {
        NBTTagCompound tag = stack.getTag();
        if (tag != null) {
            String content = tag.toString();
            Minecraft minecraft = Minecraft.getInstance();
            minecraft.keyboardHandler.setClipboard(content);
            SFM.LOGGER.info("Copied {} characters to clipboard", content.length());
            assert minecraft.player != null;
            minecraft.player.sendSystemMessage(
                    LocalizationKeys.ITEM_INSPECTOR_COPIED_TO_CLIPBOARD.getComponent(
                            Component.literal(String.valueOf(content.length())).withStyle(ChatFormatting.AQUA)
                    )
            );
        }
    }

    public static void showChangelog() {
        String changelog = null;
        var irm = Minecraft.getInstance().getResourceManager();
        Map<ResourceLocation, Resource> found = irm.listResources(
                "template_programs",
                (path) -> path.getPath().endsWith(".sfml") || path.getPath().endsWith(".sfm")
        );
        for (var entry : found.entrySet()) {
            if (entry.getKey().getPath().equals("template_programs/changelog.sfml")) {
                try (var reader = entry.getValue().openAsReader()) {
                    changelog = reader.lines().collect(Collectors.joining("\n"));
                    break;
                } catch (Exception e) {
                    SFM.LOGGER.error("Failed to read changelog", e);
                }
            }
        }
        if (changelog == null) {
            SFM.LOGGER.error("Failed to find changelog");
            return;
        }
        SFMTextEditScreenV1 screen = new SFMTextEditScreenV1(new SFMTextEditScreenExampleProgramOpenContext(
                changelog,
                changelog,
                Map.of("changelog.sfml", changelog),
                LabelPositionHolder.empty(),
                newContent -> {
                }
        ));
        setOrPushScreen(screen);
        screen.scrollToTop();
    }

    public static @Nullable Screen getCurrentScreen() {
        return Minecraft.getInstance().screen;
    }

    public static void setScreen(@Nullable Screen screen) {
        Minecraft.getInstance().setScreen(screen);
    }
}
