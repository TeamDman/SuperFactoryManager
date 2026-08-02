package ca.teamdman.sfm.client.screen;

import ca.teamdman.sfm.client.screen.text_editor.ISFMTextEditScreen;
import ca.teamdman.sfm.client.screen.file_explorer.SFMFileExplorerScreen;
import ca.teamdman.sfm.client.screen.file_explorer.SFMFileExplorerFixtureSource;
import ca.teamdman.sfm.client.screen.file_explorer.SFMFileExplorerWorkspace;
import ca.teamdman.sfm.client.screen.file_explorer.SFMPathFileExplorerSource;
import ca.teamdman.sfm.client.presentation.SFMItemIcon;
import ca.teamdman.sfm.client.screen.item_picker.SFMItemPickerScreen;
import ca.teamdman.sfm.client.text_editor.ISFMTextEditScreenOpenContext;
import ca.teamdman.sfm.client.text_editor.SFMTextEditScreenTitleScreenOpenContext;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.Arrays;
import java.util.Optional;

public enum SFMTitleScreenDevScreen {
    TEXT_EDITOR("text-editor", Component.literal("Text Editor")) {
        @Override
        public Screen create(TitleScreen titleScreen) {
            ISFMTextEditScreenOpenContext ctx = new SFMTextEditScreenTitleScreenOpenContext(
                    "",
                    LabelPositionHolder.empty(),
                    s -> {},
                    titleScreen
            );
            ISFMTextEditScreen screen = SFMScreenChangeHelpers.createProgramEditScreen(ctx);
            return screen.asScreen();
        }
    },
    INPUT_DIAG("input-diag", Component.literal("Input Diagnostics")) {
        @Override
        public Screen create(TitleScreen titleScreen) {
            return new SFMInputDiagnosticsScreen(titleScreen);
        }
    },
    FILE_EXPLORER("file-explorer", Component.literal("File Explorer")) {
        @Override
        public Screen create(TitleScreen titleScreen) {
            return SFMFileExplorerWorkspace.create(titleScreen, new SFMFileExplorerFixtureSource());
        }
    },
    INSTANCE_FILE_EXPLORER("instance-file-explorer", Component.literal("Instance File Explorer")) {
        @Override
        public Screen create(TitleScreen titleScreen) {
            return SFMFileExplorerWorkspace.create(
                    titleScreen,
                    new SFMPathFileExplorerSource(Minecraft.getInstance().gameDirectory.toPath())
            );
        }
    },
    ITEM_ICON_PICKER("item-icon-picker", Component.literal("Item Icon Picker")) {
        @Override
        public Screen create(TitleScreen titleScreen) {
            return new SFMItemPickerScreen(
                    titleScreen,
                    SFMItemIcon.vanilla("chest", "Chest"),
                    ignored -> {}
            );
        }
    };

    private final String id;
    private final Component displayName;

    SFMTitleScreenDevScreen(
            String id,
            Component displayName
    ) {
        this.id = id;
        this.displayName = displayName;
    }

    public String id() {
        return id;
    }

    public Component displayName() {
        return displayName;
    }

    public abstract Screen create(TitleScreen titleScreen);

    public static Optional<SFMTitleScreenDevScreen> byId(String id) {
        String normalized = id.trim();
        return Arrays.stream(values())
                .filter(screen -> screen.id().equals(normalized))
                .findFirst();
    }
}
