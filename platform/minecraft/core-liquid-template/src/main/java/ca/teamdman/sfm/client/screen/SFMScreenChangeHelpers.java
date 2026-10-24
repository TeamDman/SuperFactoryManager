package ca.teamdman.sfm.client.screen;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.client.examples.SFMExampleProgram;
import ca.teamdman.sfm.client.screen.text_editor.ISFMTextEditScreen;
import ca.teamdman.sfm.client.screen.text_editor.SFMTextEditScreenV1;
import ca.teamdman.sfm.client.text_editor.ISFMTextEditScreenOpenContext;
import ca.teamdman.sfm.client.text_editor.ISFMTextEditorRegistration;
import ca.teamdman.sfm.client.text_editor.SFMTextEditScreenDiskOpenContext;
import ca.teamdman.sfm.client.text_editor.SFMTextEditScreenExampleProgramOpenContext;
{% if features.dynamic_editor_picker %}
import ca.teamdman.sfm.client.text_editor.SFMTextEditorOpenAttempt;
{% endif %}
import ca.teamdman.sfm.common.config.SFMClientTextEditorConfig;
{% if features.dynamic_editor_picker %}
import ca.teamdman.sfm.common.config.SFMConfig;
{% endif %}
import ca.teamdman.sfm.common.containermenu.ManagerContainerMenu;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfm.common.localization.LocalizationEntry;
import ca.teamdman.sfm.common.localization.SFMLocalizationDatagen;
import ca.teamdman.sfm.common.net.ServerboundManagerLogDesireUpdatePacket;
import ca.teamdman.sfm.common.registry.registration.SFMPackets;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
{% when '1.21', '1.21.1', '26.1.2' %}
import net.minecraft.client.multiplayer.ClientLevel;
{% endcase %}
import net.minecraft.nbt.CompoundTag;
{% if features.dynamic_editor_picker %}
import net.minecraft.network.chat.CommonComponents;
{% endif %}
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;
{% if features.dynamic_editor_picker %}
import java.util.Objects;
{% endif %}
import java.util.function.Consumer;

public class SFMScreenChangeHelpers {
{% if features.dynamic_editor_picker %}
    @SFMLocalizationDatagen
    public static final LocalizationEntry EDITOR_OPEN_FAILED = new LocalizationEntry(
            "gui.sfm.editor_selection.open_failed", "Could not open editor");
    @SFMLocalizationDatagen
    public static final LocalizationEntry EDITOR_OPEN_FAILED_MESSAGE = new LocalizationEntry(
            "gui.sfm.editor_selection.open_failed_message",
            "Editor {} could not be created: {}. Choose another editor and try again.");

    private record PreparedEditor(ISFMTextEditScreen.OpenBehaviour behaviour, Screen screen) { }

{% endif %}
    @SFMLocalizationDatagen
    public static final LocalizationEntry ITEM_INSPECTOR_COPIED_TO_CLIPBOARD = new LocalizationEntry(
            "gui.sfm.item_inspector.copied_to_clipboard",
            "Copied {} characters to clipboard!"
    );

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
{% when '26.1.2' %}
/*    public static boolean isTopScreen() {
        return Minecraft.getInstance().screen;
    }*/

{% endcase %}
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

{% if features.dynamic_editor_picker %}
        SFMTextEditorOpenAttempt.open(() -> {
            ISFMTextEditScreen editor = Objects.requireNonNull(createProgramEditScreen(context),
                    "Editor factory returned no screen");
            return new PreparedEditor(Objects.requireNonNull(editor.openBehaviour()),
                    Objects.requireNonNull(editor.asScreen()));
        }, prepared -> {
            switch (prepared.behaviour()) {
                case Push -> setOrPushScreen(prepared.screen());
                case Replace -> setScreen(prepared.screen());
            }
        }, failure -> {
            String preferredId = SFMConfig.CLIENT_TEXT_EDITOR_CONFIG.preferredEditor.get();
            SFM.LOGGER.error("Could not create preferred editor {}", preferredId, failure);
            setOrPushScreen(new SFMConfirmationScreen(
                    () -> setOrPushScreen(new SFMTextEditorSelectionScreen(SFMConfig.CLIENT_TEXT_EDITOR_CONFIG)),
                    EDITOR_OPEN_FAILED.getComponent(),
                    EDITOR_OPEN_FAILED_MESSAGE.getComponent(Component.literal(preferredId),
                            Component.literal(String.valueOf(failure.getMessage()))),
                    SFMTextEditorSelectionScreen.CHOOSE_EDITOR.getComponent(),
                    CommonComponents.GUI_CANCEL.copy(), 0));
        });
{% else %}
        showTextEditScreen(createProgramEditScreen(context));
{% endif %}
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

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
        CompoundTag tag = stack.getTag();
        if (tag != null) {
{% when '1.21', '1.21.1' %}
//        CompoundTag tag = stack.getTag();
        CompoundTag tag = new CompoundTag();
        ClientLevel level = Minecraft.getInstance().level;
        assert level != null;
        // when we add our own nbt editor would be neat to also add a button to switch between ours and ftb library's nbt editor
        // https://github.com/FTBTeam/FTB-Library/blob/09712e7ac26bda48c32f372ae23fe06d100eabc4/common/src/main/java/dev/ftb/mods/ftblibrary/FTBLibraryCommands.java#L155
        stack.save(level.registryAccess(), tag);
        if (!tag.isEmpty()) {
{% when '26.1.2' %}
//        CompoundTag tag = stack.getTag();
        CompoundTag tag = new CompoundTag();
        ClientLevel level = Minecraft.getInstance().level;
        assert level != null;
        // when we add our own nbt editor would be neat to also add a button to switch between ours and ftb library's nbt editor
        // https://github.com/FTBTeam/FTB-Library/blob/09712e7ac26bda48c32f372ae23fe06d100eabc4/common/src/main/java/dev/ftb/mods/ftblibrary/FTBLibraryCommands.java#L155
        tag.merge((CompoundTag)ItemStack.CODEC.encodeStart(net.minecraft.resources.RegistryOps.create(net.minecraft.nbt.NbtOps.INSTANCE, level.registryAccess()), stack).getOrThrow());
        if (!tag.isEmpty()) {
{% endcase %}
            String content = tag.toString();
            Minecraft minecraft = Minecraft.getInstance();
            minecraft.keyboardHandler.setClipboard(content);
            SFM.LOGGER.info("Copied {} characters to clipboard", content.length());
            assert minecraft.player != null;
            minecraft.player.sendSystemMessage(
                    ITEM_INSPECTOR_COPIED_TO_CLIPBOARD.getComponent(
                            Component.literal(String.valueOf(content.length())).withStyle(ChatFormatting.AQUA)
                    )
            );
        }
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
