package ca.teamdman.sfm.client.screen;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.client.examples.SFMExampleProgram;
import ca.teamdman.sfm.client.screen.text_editor.ISFMTextEditScreen;
import ca.teamdman.sfm.client.screen.text_editor.SFMTextEditScreenV1;
import ca.teamdman.sfm.client.text_editor.ISFMTextEditScreenOpenContext;
import ca.teamdman.sfm.client.text_editor.ISFMTextEditorRegistration;
import ca.teamdman.sfm.client.text_editor.SFMTextEditScreenDiskOpenContext;
import ca.teamdman.sfm.client.text_editor.SFMTextEditScreenExampleProgramOpenContext;
import ca.teamdman.sfm.common.config.SFMClientTextEditorConfig;
import ca.teamdman.sfm.common.containermenu.ManagerContainerMenu;
import ca.teamdman.sfm.common.item.DiskItem;
import ca.teamdman.sfm.common.item.LabelGunItem;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfm.common.localization.LocalizationKeys;
import ca.teamdman.sfm.common.net.ServerboundManagerLogDesireUpdatePacket;
import ca.teamdman.sfm.common.registry.registration.SFMPackets;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

public class SFMScreenChangeHelpers {
    public static void setOrPushScreen(Screen screen) {

        if (Minecraft.getInstance().screen == null) {
            Minecraft
                    .getInstance()
                    .setScreen(screen);
        } else {
            Minecraft
                    .getInstance()
                    .pushGuiLayer(screen);
        }
    }

    public static void popScreen() {

        Minecraft.getInstance().popGuiLayer();
    }

    public static void showLabelGunScreen(
            ItemStack stack,
            InteractionHand hand
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

    public static void showItemInspectorScreen(ItemStack stack) {

        String content = getItemInspectorContent(stack);
        showTomlEditScreen(new TomlEditScreenOpenContext(content, x -> {
        }));
    }

    public static String getItemInspectorContent(ItemStack stack) {

        if (stack.getItem() instanceof DiskItem) {
            return getDiskItemInspectorContent(stack);
        } else if (stack.getItem() instanceof LabelGunItem) {
            return getLabelGunItemInspectorContent(stack);
        }

        CompoundTag stackTag = stack.save(new CompoundTag());
        return stackTag.toString();
    }

    private static String getDiskItemInspectorContent(ItemStack stack) {

        String programString = DiskItem.getProgramString(stack);
        LabelPositionHolder labelPositionHolder = LabelPositionHolder.from(stack);

        StringBuilder content = new StringBuilder();
        content.append("# item\n");
        content.append("id = \"")
            .append(ForgeRegistries.ITEMS.getKey(stack.getItem()))
            .append("\"\n");
        content.append("count = ")
                .append(stack.getCount())
                .append("\n\n");
        content.append("# disk program\n");
        content.append("program = '''\n")
                .append(programString)
                .append("\n'''\n\n");
        appendPrettyLabelPositionHolder(content, labelPositionHolder);
        return content.toString();
        }

        private static String getLabelGunItemInspectorContent(ItemStack stack) {

        String activeLabel = LabelGunItem.getActiveLabel(stack);
        LabelPositionHolder labelPositionHolder = LabelPositionHolder.from(stack);

        StringBuilder content = new StringBuilder();
        content.append("# item\n");
        content.append("id = \"")
            .append(ForgeRegistries.ITEMS.getKey(stack.getItem()))
            .append("\"\n");
        content.append("count = ")
            .append(stack.getCount())
            .append("\n");
        content.append("active_label = \"")
            .append(activeLabel)
            .append("\"\n");
        content.append("view_mode = \"")
            .append(LabelGunItem.getViewMode(stack).name())
            .append("\"\n\n");
        appendPrettyLabelPositionHolder(content, labelPositionHolder);
        return content.toString();
        }

        private static void appendPrettyLabelPositionHolder(
            StringBuilder content,
            LabelPositionHolder labelPositionHolder
        ) {

        content.append("# labels\n");
        if (labelPositionHolder.labels().isEmpty()) {
            content.append("(none)\n");
            return;
        }

        labelPositionHolder.labels().entrySet().stream()
            .sorted(Comparator.comparing(entry -> entry.getKey().toLowerCase()))
            .forEach(entry -> {
                content.append("[\"")
                    .append(entry.getKey())
                    .append("\"] ")
                    .append(entry.getValue().size())
                    .append(" positions\n");

                entry.getValue().blockPosIterator().forEachRemaining(pos ->
                    content.append("- ")
                        .append(pos.getX())
                        .append(", ")
                        .append(pos.getY())
                        .append(", ")
                        .append(pos.getZ())
                        .append("\n")
                );
                content.append("\n");
            });
    }

    public static void showChangelog() {

        SFMExampleProgram changelogExampleProgram = SFMExampleProgram.getChangelog();
        SFMTextEditScreenV1 screen = new SFMTextEditScreenV1(new SFMTextEditScreenExampleProgramOpenContext(
                changelogExampleProgram.programString(),
                changelogExampleProgram.programString(),
                List.of(changelogExampleProgram),
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
