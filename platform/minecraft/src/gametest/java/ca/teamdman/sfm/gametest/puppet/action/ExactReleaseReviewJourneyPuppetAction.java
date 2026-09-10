package ca.teamdman.sfm.gametest.puppet.action;

import ca.teamdman.sfm.client.review.release_review.*;
import ca.teamdman.sfm.client.screen.SFMCommandPaletteScreen;
import ca.teamdman.sfm.client.screen.explorer.SFMExplorerPanel;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import ca.teamdman.sfm.gametest.puppet.*;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.List;
import java.util.function.Predicate;

/** Ordinary input proof: real pinned source -> small approval -> exact remaining -> fresh JVM. */
public final class ExactReleaseReviewJourneyPuppetAction implements SFMPuppetAction {
    private static final String NOTE = "RCOV acceptance: this note and its exact target must survive a full game restart.";
    private static final String QUERY = "remaining intersect 1.19.2 HEAD";
    private final boolean resume;
    private final ArrayDeque<SFMPuppetAction> steps = new ArrayDeque<>();
    private JsonObject marker;
    private int ticks;
    private boolean initialized;
    private String description = "initialize";
    private String createdApproval;
    private int initialComments;
    private int noteCharacter;
    private boolean noteFocused;
    private RuntimeException noteInputFailure;
    private boolean selectedText;
    private SFMPuppetAction selectCommand;
    private SFMReleaseReviewCoverage.UnitCoverage expectedCoverage;
    private SFMReleaseReviewV1.Utf8Range openedRemaining;
    private String openedRemainingRevision;
    private ca.teamdman.sfm.client.explorer.SFMPath requestedExpansion;
    private int expansionTicks;
    private int remainingNavigationTicks;

    public ExactReleaseReviewJourneyPuppetAction(boolean resume) { this.resume = resume; }

    @Override public String description() { return "exact real release-review " + (resume ? "resume: " : "stage: ") + description; }

    @Override public boolean tick(ISFMGamePuppetRuntime runtime) {
        if (++ticks > 20 * 60 * 15) throw new IllegalStateException("Exact review timed out: " + description);
        if (!initialized) { initialize(); initialized = true; }
        if (steps.isEmpty()) return true;
        if (!description.equals(steps.peek().description())) {
            description = steps.peek().description();
            ca.teamdman.sfm.SFM.LOGGER.info("SFM_EXACT_REVIEW_CHECKPOINT stage={} resume={}", description, resume);
        }
        if (steps.peek().tick(runtime)) steps.remove();
        return steps.isEmpty();
    }

    private void initialize() {
        if (resume) {
            step("verify separate process and exact file", runtime -> {
                marker = RealReleaseReviewJourneyPuppetAction.readMarker();
                require(marker.get("stage_process_id").getAsLong() != ProcessHandle.current().pid(), "Resume must use a fresh JVM");
                require(RealReleaseReviewJourneyPuppetAction.hash(RealReleaseReviewJourneyPuppetAction.stagedPath())
                        .equals(marker.get("exact_staged_sha256").getAsString()), "Stage bytes changed between JVMs");
                RealReleaseReviewJourneyPuppetAction.requireCanonicalUnchanged(marker);
                SFMReleaseReviewRuntime.get().discardAndClose();
                var local = SFMReleaseReviewStore.machineLocalState(RealReleaseReviewJourneyPuppetAction.stagedPath());
                try { java.nio.file.Files.deleteIfExists(local.recovery()); java.nio.file.Files.deleteIfExists(local.writerLease()); }
                catch (java.io.IOException failure) { throw new IllegalStateException(failure); }
                return true;
            });
        } else {
            steps.add(new RealReleaseReviewJourneyPuppetAction(RealReleaseReviewJourneyPuppetAction.Operation.PREPARE_EXACT_STAGE));
            step("remember pinned real target", runtime -> { marker = RealReleaseReviewJourneyPuppetAction.readMarker(); return true; });
        }
        command("sfm:review/session/open " + RealReleaseReviewJourneyPuppetAction.stagedPath());
        step("wait for portable review", runtime -> {
            if (SFMReleaseReviewRuntime.get().document().isEmpty()) return false;
            initialComments = review().reviewSession().comments().size(); return true;
        });
        if (!resume) {
            command("sfm:review/session/query/activate " + QUERY);
            step("select real work unit", runtime -> {
                if (selectCommand == null) selectCommand = new ExecuteCommandPalettePuppetAction("sfm action invoke sfm:review/session/work/select "
                        + com.mojang.brigadier.arguments.StringArgumentType.escapeIfRequired(marker.get("target_unit_id").getAsString()));
                return selectCommand.tick(runtime);
            });
            steps.add(new ExecuteCommandPalettePuppetAction("sfm action invoke sfm:palette/close"));
        }
        command("sfm:panel/open sfm:explorer/release_review/query " + QUERY);
        step("filter the work queue", runtime -> {
            var workspace = workspace();
            if (workspace == null || !(workspace.focusedPanelInstance() instanceof SFMExplorerPanel)) return false;
            runtime.pressScreenKey(GLFW.GLFW_KEY_F, GLFW.GLFW_MOD_CONTROL);
            for (char ch : "SFM.java".toCharArray()) runtime.typeScreenCharacter(ch, 0);
            runtime.pressScreenKey(GLFW.GLFW_KEY_ENTER, 0);
            return true;
        });
        if (resume) {
            step("compare exact persisted coverage", this::assertResumed);
            step("open an exact remaining range", runtime -> openLeaf(runtime, true));
            step("verify uncovered-range navigation", this::assertRemainingOpened);
            capture("exact-review-resumed", "Resumed from one portable file: partial approval and exact remaining SFM.java surface.");
        } else {
            step("open real candidate source", runtime -> openLeaf(runtime, false));
            step("select a small source region and open actions", this::selectAndOpen);
            step("choose exact comment target", this::chooseComment);
            step("apply partial approval", runtime -> choose(runtime, command -> command.startsWith(
                    "sfm action invoke sfm:review/comment/choice/apply ") && command.contains("#approved")));
            step("verify partial approval remains incomplete", this::assertPartial);
            capture("exact-review-partial-approval", "A small source region approved; the rest of this real before/after change remains unreviewed.");
            step("open another comment", runtime -> {
                if (workspace() == null) return false;
                runtime.pressScreenKey(GLFW.GLFW_KEY_ENTER, GLFW.GLFW_MOD_ALT); return true;
            });
            step("choose same exact target for note", this::chooseComment);
            step("choose arbitrary note", runtime -> choose(runtime, command -> command.startsWith(
                    "sfm action invoke sfm:review/comment/choice/other ")));
            step("type and save note", this::typeNote);
            step("persist restart witnesses", this::assertStaged);
            step("open remaining range", runtime -> openLeaf(runtime, true));
            step("verify uncovered-range navigation", this::assertRemainingOpened);
            capture("exact-review-remaining", "Remaining ranges are source links, separate from the full before/after previews.");
        }
        step("release staged writer without deleting evidence", runtime -> {
            SFMReleaseReviewRuntime.get().discardAndClose(); return true;
        });
    }

    private void command(String suffix) {
        steps.add(new ExecuteCommandPalettePuppetAction("sfm action invoke " + suffix));
        steps.add(new ExecuteCommandPalettePuppetAction("sfm action invoke sfm:palette/close"));
    }

    private void step(String name, Predicate<ISFMGamePuppetRuntime> action) {
        steps.add(new SFMPuppetAction() {
            public String description() { return name; }
            public boolean tick(ISFMGamePuppetRuntime runtime) { return action.test(runtime); }
        });
    }

    private void capture(String name, String caption) {
        step("capture " + name, runtime -> {
            var workspace = workspace();
            if (workspace != null) {
                var divider = workspace.dividerDescriptions().stream()
                        .filter(value -> value.lineBounds().height() > value.lineBounds().width()).findFirst();
                if (divider.isPresent()) {
                    var line = divider.orElseThrow().lineBounds();
                    double target = workspace.width * 0.42;
                    if (line.x() > target + 5) {
                        double x = line.x() + line.width() / 2D, y = line.y() + line.height() / 2D;
                        SFMGamePuppetPointer.moveVirtual(workspace, x, y);
                        require(workspace.mouseClicked(x, y, GLFW.GLFW_MOUSE_BUTTON_LEFT), "Evidence divider press was not handled");
                        SFMGamePuppetPointer.moveVirtual(workspace, target, y);
                        workspace.mouseDragged(target, y, GLFW.GLFW_MOUSE_BUTTON_LEFT, target - x, 0);
                        workspace.mouseReleased(target, y, GLFW.GLFW_MOUSE_BUTTON_LEFT);
                        return false;
                    }
                }
            }
            return runtime.capture(name, Component.literal(caption));
        });
    }

    private boolean chooseComment(ISFMGamePuppetRuntime runtime) {
        return choose(runtime, command -> command.startsWith("sfm action invoke sfm:review/comment/choice/open "));
    }

    private boolean choose(ISFMGamePuppetRuntime runtime, Predicate<String> predicate) {
        if (!(Minecraft.getInstance().screen instanceof SFMCommandPaletteScreen palette)) return false;
        var command = palette.choiceCommandsForAutomation().stream().filter(predicate).findFirst();
        if (command.isEmpty()) return false;
        if (!palette.choiceReadyForPointerAutomation(command.orElseThrow())) return false;
        runtime.clickActionChoice(command.orElseThrow()); return true;
    }

    private boolean openLeaf(ISFMGamePuppetRuntime runtime, boolean remaining) {
        var workspace = workspace();
        if (workspace == null) return false;
        for (var id : workspace.panelIds()) {
            if (!(workspace.panelInstance(id) instanceof SFMExplorerPanel panel)) continue;
            var bounds = workspace.panelContentBounds(id);
            var state = panel.model().state(bounds);
            var loader = ca.teamdman.sfm.client.explorer.SFMExplorerRuntime.get().repository()
                    .find(state.session().id()).orElseThrow().loader();
            // The view deliberately retains its last rows while a save refreshes
            // the index. Those old rows do not authorize current-generation
            // actions. Wait for the same publication and child loads as the UI.
            if (!loader.activeParents().isEmpty() || loader.filterProjection(
                    state.session().roots(), state.session().settings().filterQuery(),
                    state.session().expanded()).isEmpty()) return false;
            var rows = state.projection().rows();
            if (requestedExpansion != null) {
                if (state.session().expanded().contains(requestedExpansion)
                        || rows.stream().anyMatch(row -> row.path().equals(requestedExpansion) && row.expanded())) {
                    requestedExpansion = null;
                } else {
                    if (++expansionTicks == 80) {
                        runtime.capture("exact-review-expansion-diagnostic", Component.literal("Expansion diagnostic: waiting for children"));
                        runtime.writeArtifact("exact-review-expansion-diagnostic", SFMGamePuppetArtifactFormat.JSON,
                                json(java.util.Map.of("requested", requestedExpansion.canonical(), "rows", rows.stream().limit(30)
                                        .map(row -> row.entry().label() + " | " + row.path().canonical()).toList(),
                                        "expanded", state.session().expanded().stream().map(value -> value.canonical()).toList())));
                    }
                    require(expansionTicks < 120, "An Explorer expansion did not publish after one click: " + requestedExpansion);
                    return false;
                }
            }
            for (var row : rows) {
                var target = SFMReleaseReviewExplorerRuntime.get().documentTarget(row.path());
                if (target.isEmpty()) continue;
                var leaf = target.orElseThrow().leaf();
                boolean matches = remaining ? leaf.title().startsWith("Unreviewed")
                        && leaf.targetRange().map(range -> expectedCoverage.remaining().stream().anyMatch(gap ->
                                leaf.documentRevisionId().orElse("").equals(gap.documentRevisionId())
                                        && range.startByte() == gap.startByte() && range.endByte() == gap.endByte())).orElse(false)
                        : leaf.title().startsWith("after") && leaf.documentRevisionId().orElse("").equals(marker.get("target_document_revision_id").getAsString())
                        && leaf.targetRange().map(range -> range.startByte() == marker.get("target_start_byte").getAsInt()).orElse(false);
                if (!matches) continue;
                if (remaining) {
                    openedRemaining = leaf.targetRange().orElseThrow();
                    openedRemainingRevision = leaf.documentRevisionId().orElseThrow();
                }
                return clickRow(workspace, id, panel, row.path(), false);
            }
            for (var row : rows) {
                if (!row.entry().expandable() || row.expanded() || state.session().expanded().contains(row.path())) continue;
                if (!remaining && !row.entry().label().contains(marker.get("target_path").getAsString())) continue;
                if (clickRow(workspace, id, panel, row.path(), true)) {
                    requestedExpansion = row.path();
                    expansionTicks = 0;
                }
                return false;
            }
        }
        return false;
    }

    private boolean clickRow(SFMScreenMultiplexer workspace, ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelId id,
            SFMExplorerPanel panel, ca.teamdman.sfm.client.explorer.SFMPath path, boolean chevron) {
        var local = workspace.panelContentBounds(id);
        var viewport = panel.model().state(local).viewport();
        var cell = viewport.cells().stream().filter(value -> value.row().path().equals(path)).findFirst();
        var physical = workspace.panelBounds(id).inset(1);
        if (cell.isEmpty()) {
            var rows = panel.model().state(local).projection().rows();
            int index = java.util.stream.IntStream.range(0, rows.size()).filter(i -> rows.get(i).path().equals(path)).findFirst().orElse(0);
            SFMGamePuppetPointer.moveVirtual(workspace, physical.x() + 20, physical.y() + physical.height() / 2D);
            SFMGamePuppetPointer.scrollVirtual(0, index < viewport.firstVisibleIndex() ? 3 : -3);
            return false;
        }
        var rect = chevron ? cell.orElseThrow().chevron() : cell.orElseThrow().bounds();
        double x = physical.x() + (rect.x() + (chevron ? rect.width() / 2D : Math.min(100, rect.width() / 2D))) * physical.width() / Math.max(1D, local.width());
        double y = physical.y() + (rect.y() + rect.height() / 2D) * physical.height() / Math.max(1D, local.height());
        if (chevron) {
            SFMGamePuppetPointer.clickVirtual(workspace, x, y, GLFW.GLFW_MOUSE_BUTTON_LEFT, 0);
        } else {
            // Select then Enter: deterministic ordinary keyboard activation does
            // not depend on a double-click deadline during a cold review load.
            require(workspace.mouseClicked(x, y, GLFW.GLFW_MOUSE_BUTTON_LEFT), "Review row selection was not handled");
            workspace.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0);
        }
        return true;
    }

    private SFMSourcePuppetProbe.EditorHandle editor() {
        var workspace = workspace();
        if (workspace == null) return null;
        return SFMSourcePuppetProbe.editor(workspace, workspace.focusedPanelId()).orElse(null);
    }

    private boolean selectAndOpen(ISFMGamePuppetRuntime runtime) {
        var editor = editor();
        if (editor == null || editor.resolvedPanel().isEmpty()) return false;
        if (!selectedText) {
            var snapshot = editor.state().documentSnapshot().orElseThrow();
            String text = snapshot.text();
            int offset = new String(text.getBytes(java.nio.charset.StandardCharsets.UTF_8), 0,
                    marker.get("target_start_byte").getAsInt(), java.nio.charset.StandardCharsets.UTF_8).length();
            while (offset < text.length() && Character.isWhitespace(text.charAt(offset))) offset++;
            var start = C11SourceNavigationPuppetProbe.pointer(workspace(), editor,
                    ca.teamdman.sfm.client.context.SFMContextTextCoordinates.rangeAtUtf16Offsets(text, offset, offset + 1));
            var end = C11SourceNavigationPuppetProbe.pointer(workspace(), editor,
                    ca.teamdman.sfm.client.context.SFMContextTextCoordinates.rangeAtUtf16Offsets(text, offset + 1, offset + 2));
            SFMGamePuppetPointer.moveVirtual(workspace(), start.globalX(), start.globalY());
            SFMGamePuppetPointer.buttonVirtual(GLFW.GLFW_MOUSE_BUTTON_LEFT, GLFW.GLFW_PRESS, 0);
            SFMGamePuppetPointer.moveVirtual(workspace(), end.globalX(), end.globalY());
            SFMGamePuppetPointer.buttonVirtual(GLFW.GLFW_MOUSE_BUTTON_LEFT, GLFW.GLFW_RELEASE, 0);
            selectedText = true; return false;
        }
        // Ask the same capture adapter used by Comment, not the special exact
        // jump-range publication (ordinary cursor motion deliberately retires it).
        var workspace = workspace();
        var actionContext = new ca.teamdman.sfm.client.action.SFMClientActionContext(
                workspace, () -> true, workspace.focusedPanelId());
        SFMReleaseReviewEditorCapture.Capture capture;
        try { capture = SFMReleaseReviewEditorCapture.capture(actionContext, workspace.contextSnapshot(), review()); }
        catch (IllegalArgumentException notReady) { return false; }
        var selections = capture.adapted().pinnedSelection().ranges();
        require(selections.size() == 1, "Expected one source selection");
        var selected = selections.get(0);
        require(selected.startByte() >= marker.get("target_start_byte").getAsInt()
                && selected.endByte() > selected.startByte()
                && selected.endByte() < marker.get("target_end_byte").getAsInt(), "Selection escaped or covered the whole hunk: " + selected);
        marker.addProperty("partial_start", selected.startByte());
        marker.addProperty("partial_end", selected.endByte());
        runtime.pressScreenKey(GLFW.GLFW_KEY_ENTER, GLFW.GLFW_MOD_ALT);
        return true;
    }

    private boolean assertPartial(ISFMGamePuppetRuntime runtime) {
        if (review().reviewSession().comments().size() <= initialComments || SFMReleaseReviewRuntime.get().dirty()) return false;
        var comment = review().reviewSession().comments().get(review().reviewSession().comments().size() - 1);
        require(comment.text().startsWith("#approved"), "Approval choice was not persisted");
        createdApproval = comment.id(); marker.addProperty("approval_id", createdApproval);
        expectedCoverage = coverage();
        require(!expectedCoverage.fullyApproved() && !expectedCoverage.remaining().isEmpty(), "Partial approval hid remaining work");
        require(expectedCoverage.approved().stream().mapToInt(range -> range.endByte() - range.startByte()).sum()
                == marker.get("partial_end").getAsInt() - marker.get("partial_start").getAsInt(), "Approval covered unselected source");
        require(SFMReleaseReviewKernel.query(review(), "#approved").reviewUnitIds().contains(marker.get("target_unit_id").getAsString()), "Raw approval query lost the unit");
        var editor = editor();
        if (editor == null || editor.resolvedPanel().isEmpty()) return false;
        var decorations = editor.resolvedPanel().orElseThrow().documentDecorations();
        if (decorations.stream().noneMatch(value -> value.interactiveObject().map(object -> object.id().equals(createdApproval)).orElse(false))) return false;
        writeEvidence(runtime, "exact-review-partial", expectedCoverage);
        return true;
    }

    private boolean typeNote(ISFMGamePuppetRuntime runtime) {
        var editor = editor();
        if (editor == null || editor.state().documentSnapshot().map(value -> value.readOnly()).orElse(true)) return false;
        if (noteInputFailure != null) {
            if (!runtime.capture("exact-review-note-input-failure", Component.literal("Preferred note editor input diagnostic"))) return false;
            throw noteInputFailure;
        }
        if (!noteFocused) {
            var workspace = workspace();
            var bounds = workspace.panelBounds(editor.panelId()).inset(1);
            SFMGamePuppetPointer.clickVirtual(workspace, bounds.x() + bounds.width() / 2D,
                    bounds.y() + bounds.height() / 2D, GLFW.GLFW_MOUSE_BUTTON_LEFT, 0);
            noteFocused = true;
            return false;
        }
        if (noteCharacter < NOTE.length()) {
            try {
                runtime.typeScreenCharacter(NOTE.charAt(noteCharacter), 0);
                noteCharacter++;
            } catch (RuntimeException failure) {
                noteInputFailure = failure;
                try {
                    var field = ca.teamdman.sfm.client.screen.text_editor.SFMTextEditorPanel.class.getDeclaredField("screen");
                    field.setAccessible(true);
                    var screen = (net.minecraft.client.gui.screens.Screen) field.get(editor.resolvedPanel().orElseThrow());
                    runtime.writeArtifact("exact-review-note-input-failure", SFMGamePuppetArtifactFormat.JSON,
                            json(java.util.Map.of("editor_id", editor.resolvedPanel().orElseThrow().editorId(),
                                    "screen", screen.getClass().getName(), "screen_size", screen.width + "x" + screen.height,
                                    "focused", String.valueOf(screen.getFocused()),
                                    "focused_child_current", screen.children().contains(screen.getFocused()),
                                    "typed_characters", noteCharacter,
                                    "children", screen.children().stream().map(child -> child.getClass().getName()
                                            + " focused=" + (child instanceof net.minecraft.client.gui.components.AbstractWidget widget
                                            && widget.isFocused())).toList())));
                } catch (ReflectiveOperationException diagnosticFailure) { failure.addSuppressed(diagnosticFailure); }
            }
            return false;
        }
        if (editor.resolvedPanel().orElseThrow().editorId().equals("sfm:v1")) {
            runtime.pressScreenKey(GLFW.GLFW_KEY_ENTER, GLFW.GLFW_MOD_SHIFT);
        } else {
            runtime.pressScreenKey(GLFW.GLFW_KEY_S, GLFW.GLFW_MOD_CONTROL);
        }
        return true;
    }

    private int stagedWaitTicks;

    private boolean assertStaged(ISFMGamePuppetRuntime runtime) {
        if (review().reviewSession().comments().size() < initialComments + 2 || SFMReleaseReviewRuntime.get().dirty()) {
            if (++stagedWaitTicks < 100) return false;
            if (!runtime.capture("exact-review-save-failure", Component.literal("Review note save diagnostic"))) return false;
            throw new IllegalStateException("Review note did not persist after Save/Done; inspect the captured editor diagnostic");
        }
        require(review().reviewSession().comments().stream().anyMatch(value -> value.text().equals(NOTE)), "Typed note missing");
        require(coverage().equals(expectedCoverage), "A plain note changed approval coverage");
        marker.addProperty("exact_staged_sha256", RealReleaseReviewJourneyPuppetAction.hash(RealReleaseReviewJourneyPuppetAction.stagedPath()));
        marker.addProperty("exact_semantic_hash", SFMReleaseReviewKernel.completion(review()).reviewSemanticStateHash());
        marker.addProperty("exact_coverage_json", json(coverage()));
        marker.addProperty("exact_resume_unit", review().resumeState().currentUnitId().orElseThrow());
        RealReleaseReviewJourneyPuppetAction.requireCanonicalUnchanged(marker);
        RealReleaseReviewJourneyPuppetAction.writeMarker(marker);
        writeEvidence(runtime, "exact-review-staged", coverage()); return true;
    }

    private boolean assertResumed(ISFMGamePuppetRuntime runtime) {
        if (SFMReleaseReviewRuntime.get().document().isEmpty()) return false;
        require(review().reviewSession().comments().stream().anyMatch(value -> value.id().equals(marker.get("approval_id").getAsString())
                && value.text().startsWith("#approved")), "Approval lost after restart");
        require(review().reviewSession().comments().stream().anyMatch(value -> value.text().equals(NOTE)), "Note lost after restart");
        expectedCoverage = coverage();
        require(json(expectedCoverage).equals(marker.get("exact_coverage_json").getAsString()), "Exact coverage changed after restart");
        require(review().resumeState().currentUnitId().orElse("").equals(marker.get("exact_resume_unit").getAsString()), "Work cursor lost after restart");
        require(SFMReleaseReviewKernel.completion(review()).reviewSemanticStateHash().equals(marker.get("exact_semantic_hash").getAsString()), "Semantic state changed after restart");
        require(RealReleaseReviewJourneyPuppetAction.hash(RealReleaseReviewJourneyPuppetAction.stagedPath()).equals(marker.get("exact_staged_sha256").getAsString()), "Reopen changed portable bytes");
        require(review().completionAttestations().isEmpty(), "Puppet must not attest a release");
        RealReleaseReviewJourneyPuppetAction.requireCanonicalUnchanged(marker);
        writeEvidence(runtime, "exact-review-resumed", coverage()); return true;
    }

    private boolean assertRemainingOpened(ISFMGamePuppetRuntime runtime) {
        var editor = editor();
        if (editor == null || editor.resolvedPanel().isEmpty()) return false;
        var selected = C11SourceNavigationPuppetProbe.exactDocumentSelections(editor).orElse(List.of());
        var document = editor.state().documentSnapshot().orElseThrow();
        boolean exactRevision = document.path().map(path -> path.scheme().equals("review")
                && path.authority().equals("document") && path.segments().contains(openedRemainingRevision)).orElse(false);
        boolean ready = exactRevision && selected.size() == 1 && selected.get(0).orderedRange().start().byteOffset() == openedRemaining.startByte()
                && selected.get(0).orderedRange().end().byteOffset() == openedRemaining.endByte();
        require(ready || ++remainingNavigationTicks < 400,
                "Remaining link did not select the exact gap: expected=" + openedRemaining + " actual=" + selected);
        if (ready) {
            JsonObject navigation = new JsonObject();
            navigation.addProperty("schema", "sfm.release-review-exact-navigation/1");
            navigation.addProperty("document_revision_id", openedRemainingRevision);
            navigation.addProperty("document_path", document.path().orElseThrow().canonical());
            navigation.addProperty("start_byte", openedRemaining.startByte());
            navigation.addProperty("end_byte", openedRemaining.endByte());
            navigation.addProperty("selected_exactly", true);
            navigation.addProperty("current_process_id", ProcessHandle.current().pid());
            runtime.writeArtifact("exact-review-navigation", SFMGamePuppetArtifactFormat.JSON, json(navigation));
        }
        return ready;
    }

    private void writeEvidence(ISFMGamePuppetRuntime runtime, String name, SFMReleaseReviewCoverage.UnitCoverage coverage) {
        JsonObject evidence = marker.deepCopy();
        evidence.addProperty("schema", "sfm.release-review-exact-coverage-acceptance/1");
        evidence.addProperty("current_process_id", ProcessHandle.current().pid());
        evidence.addProperty("os_pointer_injection", false);
        evidence.add("coverage", new GsonBuilder().create().toJsonTree(coverage));
        runtime.writeArtifact(name, SFMGamePuppetArtifactFormat.JSON, json(evidence));
    }
    private SFMReleaseReviewCoverage.UnitCoverage coverage() {
        return SFMReleaseReviewKernel.completion(review()).surfaceCoverage().stream()
                .filter(value -> value.reviewUnitId().equals(marker.get("target_unit_id").getAsString())).findFirst().orElseThrow();
    }
    private static SFMReleaseReviewV1 review() { return SFMReleaseReviewRuntime.get().document().orElseThrow(); }
    private static SFMScreenMultiplexer workspace() { return Minecraft.getInstance().screen instanceof SFMScreenMultiplexer value ? value : null; }
    private static String json(Object value) { return new GsonBuilder().setPrettyPrinting().create().toJson(value); }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
