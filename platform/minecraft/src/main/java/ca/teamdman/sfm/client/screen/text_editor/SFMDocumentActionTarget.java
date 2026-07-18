package ca.teamdman.sfm.client.screen.text_editor;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.client.action.SFMClientAction;
import ca.teamdman.sfm.client.action.SFMClientActionAvailability;
import ca.teamdman.sfm.client.action.SFMClientActionRequirement;
import ca.teamdman.sfm.client.action.SFMClientActionSource;
import ca.teamdman.sfm.client.registry.SFMClientActions;
import ca.teamdman.sfm.common.localization.LocalizationEntry;
import ca.teamdman.sfm.common.localization.SFMLocalizationDatagen;
import ca.teamdman.sfm.common.registry.SFMDeferredRegister;
import ca.teamdman.sfm.common.registry.SFMRegistryObject;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraftforge.eventbus.api.IEventBus;

public interface SFMDocumentActionTarget {
    @SFMLocalizationDatagen
    LocalizationEntry SAVE_AND_CLOSE_TITLE = new LocalizationEntry(
            "gui.sfm.client_action.document.save_and_close.title",
            "Save and close"
    );
    @SFMLocalizationDatagen
    LocalizationEntry SAVE_AND_CLOSE_DESCRIPTION = new LocalizationEntry(
            "gui.sfm.client_action.document.save_and_close.description",
            "Save the current document and close its editor"
    );
    @SFMLocalizationDatagen
    LocalizationEntry SAVE_TITLE = new LocalizationEntry(
            "gui.sfm.client_action.document.save.title",
            "Save"
    );
    @SFMLocalizationDatagen
    LocalizationEntry SAVE_DESCRIPTION = new LocalizationEntry(
            "gui.sfm.client_action.document.save.description",
            "Save the current document and keep editing"
    );
    @SFMLocalizationDatagen
    LocalizationEntry CLOSE_WITHOUT_SAVING_TITLE = new LocalizationEntry(
            "gui.sfm.client_action.document.close_without_saving.title",
            "Close without saving"
    );
    @SFMLocalizationDatagen
    LocalizationEntry CLOSE_WITHOUT_SAVING_DESCRIPTION = new LocalizationEntry(
            "gui.sfm.client_action.document.close_without_saving.description",
            "Close the editor and discard its changes"
    );
    @SFMLocalizationDatagen
    LocalizationEntry REQUIRES_DOCUMENT_EDITOR = new LocalizationEntry(
            "gui.sfm.client_action.document.requires_editor",
            "This action requires an active SFM document editor"
    );
    @SFMLocalizationDatagen
    LocalizationEntry REQUIRES_WRITABLE_DOCUMENT = new LocalizationEntry(
            "gui.sfm.client_action.document.requires_writable",
            "The active document cannot be saved"
    );

    boolean canSaveDocument();

    void saveDocument();

    void saveDocumentAndClose();

    void closeDocumentWithoutSaving();

    static SFMClientActionAvailability<SFMDocumentActionTarget> resolveWritableDocument(
            ca.teamdman.sfm.client.action.SFMClientActionContext context
    ) {
        SFMClientActionAvailability<SFMDocumentActionTarget> target = context.requireOriginatingHost(
                SFMDocumentActionTarget.class,
                REQUIRES_DOCUMENT_EDITOR.getComponent().withStyle(ChatFormatting.RED)
        );
        if (!target.isAvailable()) return target;
        if (!target.target().canSaveDocument()) {
            return SFMClientActionAvailability.unavailable(
                    REQUIRES_WRITABLE_DOCUMENT.getComponent().withStyle(ChatFormatting.RED)
            );
        }
        return target;
    }

    final class Actions {
        private static final SFMDeferredRegister<SFMClientAction<?>> REGISTERER =
                SFMClientActions.createContributor(SFM.MOD_ID);

        public static final SFMRegistryObject<SFMClientAction<?>, SaveAndCloseAction> SAVE_AND_CLOSE = REGISTERER.register(
                "document/save_and_close",
                SaveAndCloseAction::new
        );

        public static final SFMRegistryObject<SFMClientAction<?>, SaveAction> SAVE = REGISTERER.register(
                "document/save",
                SaveAction::new
        );

        public static final SFMRegistryObject<SFMClientAction<?>, CloseWithoutSavingAction> CLOSE_WITHOUT_SAVING = REGISTERER.register(
                "document/close_without_saving",
                CloseWithoutSavingAction::new
        );

        private Actions() {
        }

        public static void register(IEventBus bus) {
            REGISTERER.register(bus);
        }

    }

    final class SaveAndCloseAction implements SFMClientAction<SFMDocumentActionTarget> {
        @Override
        public Component title() {
            return SAVE_AND_CLOSE_TITLE.getComponent();
        }

        @Override
        public Component description() {
            return SAVE_AND_CLOSE_DESCRIPTION.getComponent();
        }

        @Override
        public SFMClientActionRequirement<SFMDocumentActionTarget> requirement() {
            return context -> {
                SFMClientActionAvailability<SFMDocumentActionTarget> target = context.requireOriginatingHost(
                        SFMDocumentActionTarget.class,
                        REQUIRES_DOCUMENT_EDITOR.getComponent().withStyle(ChatFormatting.RED)
                );
                if (!target.isAvailable()) {
                    return target;
                }
                if (!target.target().canSaveDocument()) {
                    return SFMClientActionAvailability.unavailable(
                            REQUIRES_WRITABLE_DOCUMENT.getComponent().withStyle(ChatFormatting.RED)
                    );
                }
                return target;
            };
        }

        @Override
        public boolean isPinnable() {
            return true;
        }

        @Override
        public int execute(
                SFMDocumentActionTarget target,
                CommandContext<SFMClientActionSource> context
        ) {
            target.saveDocumentAndClose();
            return 1;
        }
    }

    final class SaveAction implements SFMClientAction<SFMDocumentActionTarget> {
        @Override
        public Component title() {
            return SAVE_TITLE.getComponent();
        }

        @Override
        public Component description() {
            return SAVE_DESCRIPTION.getComponent();
        }

        @Override
        public SFMClientActionRequirement<SFMDocumentActionTarget> requirement() {
            return SFMDocumentActionTarget::resolveWritableDocument;
        }

        @Override
        public boolean isPinnable() {
            return true;
        }

        @Override
        public int execute(
                SFMDocumentActionTarget target,
                CommandContext<SFMClientActionSource> context
        ) {
            target.saveDocument();
            return 1;
        }
    }

    final class CloseWithoutSavingAction implements SFMClientAction<SFMDocumentActionTarget> {
        @Override
        public Component title() {
            return CLOSE_WITHOUT_SAVING_TITLE.getComponent();
        }

        @Override
        public Component description() {
            return CLOSE_WITHOUT_SAVING_DESCRIPTION.getComponent();
        }

        @Override
        public SFMClientActionRequirement<SFMDocumentActionTarget> requirement() {
            return context -> context.requireOriginatingHost(
                    SFMDocumentActionTarget.class,
                    REQUIRES_DOCUMENT_EDITOR.getComponent().withStyle(ChatFormatting.RED)
            );
        }

        @Override
        public boolean isPinnable() {
            return true;
        }

        @Override
        public int execute(
                SFMDocumentActionTarget target,
                CommandContext<SFMClientActionSource> context
        ) {
            target.closeDocumentWithoutSaving();
            return 1;
        }
    }

}
