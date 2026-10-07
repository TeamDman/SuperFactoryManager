package ca.teamdman.sfm.client.screen;

import ca.teamdman.sfm.client.screen.text_editor.ISFMTextEditScreen;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% else %}
{% if features.legacy_file_explorer %}
import ca.teamdman.sfm.client.screen.file_explorer.SFMFileExplorerScreen;
{% endif %}
{% if features.legacy_file_explorer %}
import ca.teamdman.sfm.client.screen.file_explorer.SFMFileExplorerFixtureSource;
{% endif %}
{% if features.legacy_file_explorer %}
import ca.teamdman.sfm.client.screen.file_explorer.SFMFileExplorerWorkspace;
{% endif %}
{% if features.legacy_file_explorer %}
import ca.teamdman.sfm.client.screen.file_explorer.SFMPathFileExplorerSource;
{% endif %}
{% if features.item_picker %}
import ca.teamdman.sfm.client.presentation.SFMItemIcon;
{% endif %}
{% if features.item_picker %}
import ca.teamdman.sfm.client.screen.item_picker.SFMItemPickerScreen;
{% endif %}
{% if features.legacy_source_review_ui %}
import ca.teamdman.sfm.client.screen.review.SFMSourceComparisonWorkspace;
{% endif %}
{% if features.legacy_comment_review_ui %}
import ca.teamdman.sfm.client.screen.review.comment.SFMReviewCommentWorkspace;
{% endif %}
{% endcase %}
import ca.teamdman.sfm.client.text_editor.ISFMTextEditScreenOpenContext;
import ca.teamdman.sfm.client.text_editor.SFMTextEditScreenTitleScreenOpenContext;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
{% if features.legacy_file_explorer %}
import net.minecraft.client.Minecraft;
{% endif %}
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
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% if features.input_diagnostics_screen %}
    },
{% else %}
    };
{% endif %}
{% else %}
{% if features.input_diagnostics_screen or features.canvas_text_editor or features.legacy_file_explorer or features.item_picker or features.legacy_source_review_ui or features.legacy_comment_review_ui %}
    },
{% else %}
    };
{% endif %}
{% endcase %}
{% if features.input_diagnostics_screen %}
    INPUT_DIAG("input-diag", Component.literal("Input Diagnostics")) {
        @Override
        public Screen create(TitleScreen titleScreen) {
            return new SFMInputDiagnosticsScreen(titleScreen);
        }
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
    };
{% else %}
{% if features.canvas_text_editor or features.legacy_file_explorer or features.item_picker or features.legacy_source_review_ui or features.legacy_comment_review_ui %}
    },
{% else %}
    };
{% endif %}
{% endcase %}
{% endif %}
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% else %}
{% if features.canvas_text_editor %}
    DRAW_CANVAS("draw-canvas", Component.literal("Draw Canvas")) {
        @Override
        public Screen create(TitleScreen titleScreen) {
            return new SFMDrawCanvasScreen(titleScreen);
        }
{% if features.legacy_file_explorer or features.item_picker or features.legacy_source_review_ui or features.legacy_comment_review_ui %}
    },
{% else %}
    };
{% endif %}
{% endif %}
{% if features.legacy_file_explorer %}
    FILE_EXPLORER("file-explorer", Component.literal("File Explorer")) {
        @Override
        public Screen create(TitleScreen titleScreen) {
            return SFMFileExplorerWorkspace.create(titleScreen, new SFMFileExplorerFixtureSource());
        }
{% if features.legacy_file_explorer or features.item_picker or features.legacy_source_review_ui or features.legacy_comment_review_ui %}
    },
{% else %}
    };
{% endif %}
{% endif %}
{% if features.legacy_file_explorer %}
    INSTANCE_FILE_EXPLORER("instance-file-explorer", Component.literal("Instance File Explorer")) {
        @Override
        public Screen create(TitleScreen titleScreen) {
            return SFMFileExplorerWorkspace.create(
                    titleScreen,
                    new SFMPathFileExplorerSource(Minecraft.getInstance().gameDirectory.toPath())
            );
        }
{% if features.item_picker or features.legacy_source_review_ui or features.legacy_comment_review_ui %}
    },
{% else %}
    };
{% endif %}
{% endif %}
{% if features.item_picker %}
    ITEM_ICON_PICKER("item-icon-picker", Component.literal("Item Icon Picker")) {
        @Override
        public Screen create(TitleScreen titleScreen) {
            return new SFMItemPickerScreen(
                    titleScreen,
                    SFMItemIcon.vanilla("chest", "Chest"),
                    ignored -> {}
            );
        }
{% if features.legacy_source_review_ui or features.legacy_comment_review_ui %}
    },
{% else %}
    };
{% endif %}
{% endif %}
{% if features.legacy_source_review_ui %}
    SOURCE_REVIEW("source-review", Component.literal("Source Review Ledger")) {
        @Override
        public Screen create(TitleScreen titleScreen) {
            return SFMSourceComparisonWorkspace.create(titleScreen);
        }
{% if features.legacy_comment_review_ui %}
    },
{% else %}
    };
{% endif %}
{% endif %}
{% if features.legacy_comment_review_ui %}
    COMMENT_REVIEW("comment-review", Component.literal("Review Comments")) {
        @Override
        public Screen create(TitleScreen titleScreen) {
            return SFMReviewCommentWorkspace.create(titleScreen);
        }
    };
{% endif %}
{% endcase %}

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
