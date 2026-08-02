package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.client.presentation.SFMItemIcon;
import ca.teamdman.sfm.client.screen.SFMScreenChangeHelpers;
import ca.teamdman.sfm.client.screen.SFMTitleScreenDevScreen;
import ca.teamdman.sfm.client.screen.file_explorer.SFMFilePresentationRegistry;
import ca.teamdman.sfm.common.localization.LocalizationEntry;
import ca.teamdman.sfm.common.localization.SFMLocalizationDatagen;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;

import java.util.Objects;
import java.util.Optional;

public final class OpenTitleScreenDevScreenAction implements SFMClientAction<TitleScreen> {
    private static final SFMItemIcon FILE_EXPLORER_ICON = SFMFilePresentationRegistry
            .createDefault()
            .directoryPresentation()
            .itemIcon();

    @SFMLocalizationDatagen
    public static final LocalizationEntry TEXT_EDITOR_TITLE = new LocalizationEntry(
            "gui.sfm.client_action.developer.text_editor.title",
            "Text Editor"
    );

    @SFMLocalizationDatagen
    public static final LocalizationEntry TEXT_EDITOR_DESCRIPTION = new LocalizationEntry(
            "gui.sfm.client_action.developer.text_editor.description",
            "Open an empty program in the preferred SFM text editor"
    );

    @SFMLocalizationDatagen
    public static final LocalizationEntry INPUT_DIAGNOSTICS_TITLE = new LocalizationEntry(
            "gui.sfm.client_action.developer.input_diagnostics.title",
            "Input Diagnostics"
    );

    @SFMLocalizationDatagen
    public static final LocalizationEntry INPUT_DIAGNOSTICS_DESCRIPTION = new LocalizationEntry(
            "gui.sfm.client_action.developer.input_diagnostics.description",
            "Open the SFM input diagnostics screen"
    );

    @SFMLocalizationDatagen
    public static final LocalizationEntry FILE_EXPLORER_TITLE = new LocalizationEntry(
            "gui.sfm.client_action.developer.file_explorer.title",
            "File Explorer"
    );

    @SFMLocalizationDatagen
    public static final LocalizationEntry FILE_EXPLORER_DESCRIPTION = new LocalizationEntry(
            "gui.sfm.client_action.developer.file_explorer.description",
            "Open the read-only SFM file explorer experiment"
    );

    @SFMLocalizationDatagen
    public static final LocalizationEntry INSTANCE_FILE_EXPLORER_TITLE = new LocalizationEntry(
            "gui.sfm.client_action.developer.instance_file_explorer.title",
            "Instance File Explorer"
    );

    @SFMLocalizationDatagen
    public static final LocalizationEntry INSTANCE_FILE_EXPLORER_DESCRIPTION = new LocalizationEntry(
            "gui.sfm.client_action.developer.instance_file_explorer.description",
            "Browse the isolated Minecraft instance read-only"
    );

    @SFMLocalizationDatagen
    public static final LocalizationEntry ITEM_ICON_PICKER_TITLE = new LocalizationEntry(
            "gui.sfm.client_action.developer.item_icon_picker.title",
            "Item Icon Picker"
    );

    @SFMLocalizationDatagen
    public static final LocalizationEntry ITEM_ICON_PICKER_DESCRIPTION = new LocalizationEntry(
            "gui.sfm.client_action.developer.item_icon_picker.description",
            "Search the Minecraft item registry and choose a typed icon"
    );

    private final SFMTitleScreenDevScreen devScreen;

    public OpenTitleScreenDevScreenAction(SFMTitleScreenDevScreen devScreen) {
        this.devScreen = Objects.requireNonNull(devScreen);
    }

    @Override
    public Component title() {
        return switch (devScreen) {
            case TEXT_EDITOR -> TEXT_EDITOR_TITLE.getComponent();
            case INPUT_DIAG -> INPUT_DIAGNOSTICS_TITLE.getComponent();
            case FILE_EXPLORER -> FILE_EXPLORER_TITLE.getComponent();
            case INSTANCE_FILE_EXPLORER -> INSTANCE_FILE_EXPLORER_TITLE.getComponent();
            case ITEM_ICON_PICKER -> ITEM_ICON_PICKER_TITLE.getComponent();
        };
    }

    @Override
    public Component description() {
        return switch (devScreen) {
            case TEXT_EDITOR -> TEXT_EDITOR_DESCRIPTION.getComponent();
            case INPUT_DIAG -> INPUT_DIAGNOSTICS_DESCRIPTION.getComponent();
            case FILE_EXPLORER -> FILE_EXPLORER_DESCRIPTION.getComponent();
            case INSTANCE_FILE_EXPLORER -> INSTANCE_FILE_EXPLORER_DESCRIPTION.getComponent();
            case ITEM_ICON_PICKER -> ITEM_ICON_PICKER_DESCRIPTION.getComponent();
        };
    }

    @Override
    public Optional<SFMItemIcon> itemIcon(SFMClientActionContext context) {
        if (devScreen == SFMTitleScreenDevScreen.FILE_EXPLORER
                || devScreen == SFMTitleScreenDevScreen.INSTANCE_FILE_EXPLORER) {
            return Optional.of(FILE_EXPLORER_ICON);
        }
        if (devScreen == SFMTitleScreenDevScreen.ITEM_ICON_PICKER) {
            return Optional.of(SFMItemIcon.vanilla("compass", "Item icon picker"));
        }
        return Optional.empty();
    }

    @Override
    public SFMClientActionRequirement<TitleScreen> requirement() {
        return SFMDeveloperActionRequirement::resolve;
    }

    @Override
    public int execute(
            TitleScreen target,
            CommandContext<SFMClientActionSource> context
    ) {
        SFMScreenChangeHelpers.setScreen(devScreen.create(target));
        return 1;
    }
}
