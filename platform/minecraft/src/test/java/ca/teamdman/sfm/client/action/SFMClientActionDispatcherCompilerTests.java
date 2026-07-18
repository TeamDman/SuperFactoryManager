package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.client.screen.text_editor.SFMDocumentActionTarget;
import com.mojang.brigadier.ParseResults;
import net.minecraft.resources.ResourceLocation;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SFMClientActionDispatcherCompilerTests {
    private static final ResourceLocation SAVE_AND_CLOSE_ID = new ResourceLocation(
            SFM.MOD_ID,
            "document/save_and_close"
    );
    private static final String SAVE_AND_CLOSE_COMMAND =
            "sfm action invoke " + SAVE_AND_CLOSE_ID;
    private static final ResourceLocation SAVE_ID = new ResourceLocation(
            SFM.MOD_ID,
            "document/save"
    );
    private static final ResourceLocation CLOSE_WITHOUT_SAVING_ID = new ResourceLocation(
            SFM.MOD_ID,
            "document/close_without_saving"
    );

    @Test
    public void availableDocumentActionExecutesAgainstItsResolvedTarget() throws CommandSyntaxException {
        FakeDocumentTarget target = new FakeDocumentTarget(true);
        AtomicBoolean originCurrent = new AtomicBoolean(true);
        SFMClientActionSource source = source(target, originCurrent);
        SFMClientActionCommandTree dispatcher = dispatcher();

        int result = dispatcher.execute(SAVE_AND_CLOSE_COMMAND, source);
        var suggestions = dispatcher
                .getCompletionSuggestions(dispatcher.parse("sfm action invoke ", source))
                .join()
                .getList();

        assertEquals(1, result);
        assertEquals(1, target.saveAndCloseCount.get());
        assertEquals(
                List.of(SAVE_AND_CLOSE_ID.toString()),
                suggestions.stream().map(suggestion -> suggestion.getText()).toList()
        );
        assertThrows(
                CommandSyntaxException.class,
                () -> dispatcher.execute("sfm action invoke save_and_close", source)
        );
    }

    @Test
    public void incompatibleOriginCannotInvokeDocumentAction() {
        SFMClientActionSource source = source(new Object(), new AtomicBoolean(true));
        SFMClientActionCommandTree dispatcher = dispatcher();

        assertThrows(
                CommandSyntaxException.class,
                () -> dispatcher.execute(SAVE_AND_CLOSE_COMMAND, source)
        );
        assertTrue(dispatcher
                           .getCompletionSuggestions(dispatcher.parse("sfm action invoke ", source))
                           .join()
                           .isEmpty());
    }

    @Test
    public void unavailableDocumentActionIsAbsentFromSuggestions() {
        FakeDocumentTarget readOnlyTarget = new FakeDocumentTarget(false);
        SFMClientActionSource source = source(readOnlyTarget, new AtomicBoolean(true));
        SFMClientActionCommandTree dispatcher = dispatcher();

        var suggestions = dispatcher
                .getCompletionSuggestions(dispatcher.parse("sfm action invoke ", source))
                .join()
                .getList();

        assertTrue(suggestions.isEmpty());
        assertThrows(
                CommandSyntaxException.class,
                () -> dispatcher.execute(SAVE_AND_CLOSE_COMMAND, source)
        );
        assertEquals(0, readOnlyTarget.saveAndCloseCount.get());
    }

    @Test
    public void executionRechecksContextAfterParsing() {
        FakeDocumentTarget target = new FakeDocumentTarget(true);
        AtomicBoolean originCurrent = new AtomicBoolean(true);
        SFMClientActionSource source = source(target, originCurrent);
        SFMClientActionCommandTree dispatcher = dispatcher();
        ParseResults<SFMClientActionSource> parsed = dispatcher.parse(SAVE_AND_CLOSE_COMMAND, source);
        assertFalse(parsed.getReader().canRead());

        originCurrent.set(false);

        assertTrue(dispatcher.getCompletionSuggestions(parsed).join().isEmpty());
        assertEquals(0, target.saveAndCloseCount.get());
    }

    @Test
    public void compilerRejectsDuplicateActionIds() {
        SFMDocumentActionTarget.SaveAndCloseAction first = new SFMDocumentActionTarget.SaveAndCloseAction();
        SFMDocumentActionTarget.SaveAndCloseAction second = new SFMDocumentActionTarget.SaveAndCloseAction();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> SFMClientActionDispatcherCompiler.compile(List.of(
                        Map.entry(SAVE_AND_CLOSE_ID, first),
                        Map.entry(SAVE_AND_CLOSE_ID, second)
                ))
        );

        assertTrue(exception.getMessage().contains(SAVE_AND_CLOSE_ID.toString()));
    }

    @Test
    public void listAndHelpWriteStructuredFeedback() throws CommandSyntaxException {
        FakeDocumentTarget target = new FakeDocumentTarget(true);
        List<net.minecraft.network.chat.Component> feedback = new ArrayList<>();
        SFMClientActionCommandTree dispatcher = dispatcher();
        SFMClientActionSource source = source(target, new AtomicBoolean(true), feedback::add);

        assertEquals(1, dispatcher.execute("sfm action list all", source));
        assertEquals(1, dispatcher.execute("sfm action help " + SAVE_AND_CLOSE_ID, source));
        assertEquals(4, feedback.size());
        assertTrue(feedback.get(0).toString().contains(SAVE_AND_CLOSE_ID.toString()));
        assertTrue(feedback.get(1).toString().contains("save_and_close"));
    }

    @Test
    public void saveAndCloseActionsUseDistinctTargetOperations() throws CommandSyntaxException {
        FakeDocumentTarget target = new FakeDocumentTarget(true);
        SFMClientActionSource source = source(target, new AtomicBoolean(true));
        SFMClientActionCommandTree dispatcher = fullDispatcher();

        assertEquals(1, dispatcher.execute("sfm action invoke " + SAVE_ID, source));
        assertEquals(1, dispatcher.execute("sfm action invoke " + CLOSE_WITHOUT_SAVING_ID, source));
        assertEquals(1, target.saveCount.get());
        assertEquals(1, target.closeWithoutSavingCount.get());
        assertEquals(0, target.saveAndCloseCount.get());
    }

    private static SFMClientActionCommandTree dispatcher() {
        return SFMClientActionDispatcherCompiler.compileCommandTree(List.of(
                Map.entry(SAVE_AND_CLOSE_ID, new SFMDocumentActionTarget.SaveAndCloseAction())
        ));
    }

    private static SFMClientActionCommandTree fullDispatcher() {
        return SFMClientActionDispatcherCompiler.compileCommandTree(List.of(
                Map.entry(SAVE_AND_CLOSE_ID, new SFMDocumentActionTarget.SaveAndCloseAction()),
                Map.entry(SAVE_ID, new SFMDocumentActionTarget.SaveAction()),
                Map.entry(CLOSE_WITHOUT_SAVING_ID, new SFMDocumentActionTarget.CloseWithoutSavingAction())
        ));
    }

    private static SFMClientActionSource source(
            Object target,
            AtomicBoolean originCurrent
    ) {
        return source(target, originCurrent, ignored -> {
        });
    }

    private static SFMClientActionSource source(
            Object target,
            AtomicBoolean originCurrent,
            java.util.function.Consumer<net.minecraft.network.chat.Component> feedback
    ) {
        return new SFMClientActionSource(SFMClientActionContext.create(target, originCurrent::get), feedback);
    }

    private static final class FakeDocumentTarget implements SFMDocumentActionTarget {
        private final boolean writable;
        private final AtomicInteger saveCount = new AtomicInteger();
        private final AtomicInteger saveAndCloseCount = new AtomicInteger();
        private final AtomicInteger closeWithoutSavingCount = new AtomicInteger();

        private FakeDocumentTarget(boolean writable) {
            this.writable = writable;
        }

        @Override
        public boolean canSaveDocument() {
            return writable;
        }

        @Override
        public void saveDocument() {
            saveCount.incrementAndGet();
        }

        @Override
        public void saveDocumentAndClose() {
            saveAndCloseCount.incrementAndGet();
        }

        @Override
        public void closeDocumentWithoutSaving() {
            closeWithoutSavingCount.incrementAndGet();
        }
    }
}
