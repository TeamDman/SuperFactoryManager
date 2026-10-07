package ca.teamdman.sfm.gametest.puppet.action;

import ca.teamdman.sfm.client.action.*;
import ca.teamdman.sfm.client.presentation.SFMItemIconRenderer;
import ca.teamdman.sfm.client.screen.SFMCommandPaletteScreen;
import ca.teamdman.sfm.client.screen.explorer.*;
import ca.teamdman.sfm.client.screen.item_picker.SFMItemPickerPanel;
import ca.teamdman.sfm.client.screen.theme_settings.SFMItemstackPreviewDraftPanel;
import ca.teamdman.sfm.client.screen.workspace.*;
import ca.teamdman.sfm.client.theme.SFMClientThemeService;
import ca.teamdman.sfm.client.theme.preview.*;
import ca.teamdman.sfm.gametest.puppet.*;
import com.google.gson.*;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;

import java.nio.file.*;
import java.util.*;
import java.util.function.*;

/** Real palette/Explorer/picker inputs against a new disposable theme; never moves the OS cursor. */
public final class ItemstackPreviewRulesPuppetAction implements SFMPuppetAction {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String SUFFIX = "sfm:bool/and sfm:entry/is_file sfm:entry/has_suffix \".json\"";
    private static final String EXACT = "sfm:bool/and " + SUFFIX + " sfm:string/equals sfm:entry/name \"abc.json\"";
    private final boolean resume;
    private final ArrayDeque<SFMPuppetAction> steps = new ArrayDeque<>();
    private int ticks, stepTicks;
    private String current = "initialize", continuation, copiedDetails, initialTheme;
    private Path directory, theme;
    private JsonObject marker;
    private SFMWorkspacePanelId explorerId;
    private boolean initialized, expansionRequested;

    public ItemstackPreviewRulesPuppetAction(boolean resume) { this.resume = resume; }
    @Override public String description() { return "ItemStack rule " + (resume ? "resume: " : "authoring: ") + current; }
    @Override public boolean tick(ISFMGamePuppetRuntime runtime) {
        if (++ticks > 20 * 60 * 12) throw new IllegalStateException("Rule journey timed out: " + current);
        if (!initialized) { initialize(); initialized = true; }
        if (steps.isEmpty()) return true;
        if (!current.equals(steps.peek().description())) {
            current = steps.peek().description(); stepTicks = 0;
            ca.teamdman.sfm.SFM.LOGGER.info("SFM_ITEMSTACK_RULE_PUPPET stage={} resume={}", current, resume);
        }
        if (++stepTicks > 20 * 90) throw new IllegalStateException("Rule journey step timed out: " + current + "; screen=" + runtime.currentScreenName());
        if (steps.peek().tick(runtime)) steps.remove();
        return steps.isEmpty();
    }

    private void initialize() {
        step("prepare isolated authority", runtime -> { prepare(); return true; });
        command(() -> "sfm action invoke sfm:panel/open sfm:explorer " + directory.toUri());
        step("expand staged directory", this::waitForEntries);
        if (resume) {
            step("verify fresh JVM and persisted icons", runtime -> {
                require(marker.get("process_id").getAsLong() != ProcessHandle.current().pid(), "Resume requires a new JVM");
                require(hash(theme).equals(marker.get("theme_sha256").getAsString()), "Persisted theme bytes changed");
                require(SFMClientThemeService.active().previewRules().size() == 1, "One persisted rule expected");
                assertIcon("abc.json", "minecraft:bell"); assertIcon("abd.json", "minecraft:bell");
                assertSources(); return true;
            });
            capture("rules-resumed", "Fresh JVM: the saved JSON rule survives; source files are unchanged.");
        } else {
            capture("rules-baseline", "Disposable files and theme. No saved preview rule yet; directory chest renders without a world.");
            menu();
            capture("rules-icon-menu", "The icon has flat actions: entry details, rule prompt, rendering, rule explanation, bounds and authoring.");
            step("copy exact entry evidence", runtime -> {
                var palette = palette(); if (palette == null) return false;
                copiedDetails = SFMItemstackPreviewCaptures.forContext(palette.actionContextForAutomation()).orElseThrow().inspection().detailsPayload();
                return choose(runtime, value -> value.startsWith("sfm action invoke sfm:explorer/row/details/copy "));
            });
            step("read back entry clipboard", runtime -> {
                require(Minecraft.getInstance().keyboardHandler.getClipboard().equals(copiedDetails), "Entry clipboard differs from captured payload");
                artifact(runtime, "entry-details", JSON.toJson(Map.of("captured_entry_details", copiedDetails))); return true;
            });
            menu();
            step("copy registry-derived prompt", runtime -> {
                var palette = palette(); if (palette == null) return false;
                copiedDetails = SFMItemstackPreviewCaptures.forContext(palette.actionContextForAutomation()).orElseThrow().inspection().detailsPayload();
                return choose(runtime, value -> value.startsWith("sfm action invoke sfm:explorer/itemstack_preview_rule/prompt/copy "));
            });
            step("read back prompt without executing it", runtime -> {
                String prompt = Minecraft.getInstance().keyboardHandler.getClipboard();
                JsonObject object = JsonParser.parseString(prompt).getAsJsonObject();
                require(prompt.contains("sfm.itemstack-preview-rule-prompt/1"), "Prompt schema missing");
                require(object.get("untrusted_captured_entry_details").getAsString().equals(copiedDetails), "Prompt does not embed the exact captured entry");
                require(hash(theme).equals(initialTheme), "Copying a prompt changed the theme");
                artifact(runtime, "rule-prompt", prompt); return true;
            });
            menu();
            step("accept incomplete suffix continuation with Tab", runtime -> {
                var palette = palette(); if (palette == null || !palette.suggestionsMatchCurrentInputForAutomation()) return false;
                var candidates = palette.candidateSnapshotsForAutomation();
                var candidate = candidates.stream().filter(value -> value.replacementText().startsWith(SFMItemstackPreviewRuleAction.ID + " ")
                        && value.replacementText().endsWith("\".json\"")).findFirst().orElseThrow();
                continuation = "sfm action invoke " + candidate.replacementText();
                for (int index = 0; index < candidates.indexOf(candidate); index++) runtime.pressScreenKey(GLFW.GLFW_KEY_DOWN, 0);
                runtime.pressScreenKey(GLFW.GLFW_KEY_TAB, 0); return true;
            });
            step("verify live continuation ownership", runtime -> {
                var palette = palette(); if (palette == null) return false;
                require(palette.inputForAutomation().equals(continuation + " "), "Continuation did not open its canonical incomplete command: " + palette.inputForAutomation());
                require(palette.actionContextForAutomation().originatingHostIsCurrent().getAsBoolean(), "Continuation lost its originating panel");
                require(hash(theme).equals(initialTheme), "Incomplete completion wrote a theme"); return true;
            });
            capture("rules-continuation", "Tab builds a scoped command and asks for the ItemStack. Completion alone does not save anything.");
            closePalette();
            menu();
            step("choose shared ItemStack picker", runtime -> choose(runtime, value -> value.startsWith(SFMItemstackPreviewRuleToolsAction.Kind.PICK.prefix() + " ")
                    && value.contains("sfm:entry/has_suffix \".json\"")));
            step("search bell using character inputs", runtime -> {
                if (!(workspace().focusedPanelInstance() instanceof SFMItemPickerPanel)) return false;
                for (char ch : "minecraft:bell".toCharArray()) runtime.typeScreenCharacter(ch, 0); return true;
            });
            capture("rules-picker", "The existing searchable ItemStack picker supplies the item argument; this is still only a draft.");
            step("confirm picker", runtime -> { runtime.pressScreenKey(GLFW.GLFW_KEY_ENTER, 0); return true; });
            step("wait for local preview", runtime -> {
                if (draft() == null) return false;
                require(draft().draft().request().item().toString().equals("minecraft:bell"), "Picker chose the wrong icon");
                require(hash(theme).equals(initialTheme), "Draft preview changed persisted bytes"); return true;
            });
            capture("rules-draft", "Review scope, exact theme destination and revision before pressing Save rule.");
            step("click Save rule", runtime -> { clickDraft(0); return true; });
            step("await committed rule", runtime -> SFMClientThemeService.active().previewRules().size() == 1 && draft().status().contains("Saved"));
            capture("rules-saved", "Save completed: the canonical rule is persisted in the explicitly selected disposable theme.");
            step("close draft", runtime -> { runtime.pressScreenKey(GLFW.GLFW_KEY_ESCAPE, 0); return true; });
            step("verify both JSON siblings changed", runtime -> { assertIcon("abc.json", "minecraft:bell"); assertIcon("abd.json", "minecraft:bell"); assertSources(); return true; });
            capture("rules-json-scope", "Both JSON files use bells; the text file and directory keep their inherited icons.");
            menu();
            step("inspect winning rule", runtime -> choose(runtime, value -> value.startsWith("sfm action invoke sfm:explorer/icon/rule/explain ")));
            capture("rules-explanation", "Winning-rule evidence is an ordinary readable document, separate from rendering and geometry explanations.");
            step("close explanation document", runtime -> {
                runtime.pressScreenKey(GLFW.GLFW_KEY_ESCAPE, 0);
                require(workspace() != null && workspace().focusedPanelInstance() instanceof SFMExplorerPanel,
                        "Read-only explanation did not return to Explorer: " + runtime.currentScreenName());
                return true;
            });
            command(() -> SFMItemstackPreviewRuleToolsAction.Kind.PREVIEW.prefix() + " " + authority().argument() + " " + EXACT + " minecraft:diamond");
            step("cancel unsaved narrower preview", runtime -> {
                require(draft() != null, "Preview panel absent"); runtime.pressScreenKey(GLFW.GLFW_KEY_ESCAPE, 0);
                require(SFMClientThemeService.active().previewRules().size() == 1, "Cancelled preview saved a rule"); return true;
            });
            step("prepare complete command with earlier literal to edit", runtime -> {
                if (!runtime.openCommandPalette()) return false;
                runtime.setCommandPaletteInput(add(EXACT.replace("abc.json", "not-yet.json"), "minecraft:diamond")); return true;
            });
            step("edit earlier parameter and preserve item suffix", runtime -> {
                var palette = palette(); if (palette == null || !palette.suggestionsMatchCurrentInputForAutomation()) return false;
                String command = palette.inputForAutomation(); int start = command.indexOf("not-yet.json");
                require(start >= 0, "Editable literal absent");
                runtime.pressScreenKey(GLFW.GLFW_KEY_HOME, 0);
                for (int index = 0; index < start + "not-yet.json".length(); index++) runtime.pressScreenKey(GLFW.GLFW_KEY_RIGHT, 0);
                for (int index = 0; index < "not-yet.json".length(); index++) runtime.pressScreenKey(GLFW.GLFW_KEY_BACKSPACE, 0);
                for (char ch : "abc.json".toCharArray()) runtime.typeScreenCharacter(ch, 0);
                require(palette.inputForAutomation().equals(add(EXACT, "minecraft:diamond")), "Editing predicate lost trailing arguments: " + palette.inputForAutomation()); return true;
            });
            capture("rules-edit-parameter", "Editing an earlier predicate literal preserves the later ItemStack argument.");
            step("execute narrower exact-name rule", runtime -> { runtime.submitCommandPalette(); return true; });
            step("await exact-name commit", runtime -> SFMClientThemeService.active().previewRules().size() == 2);
            closePalette();
            step("verify conservative specificity", runtime -> { assertIcon("abc.json", "minecraft:diamond"); assertIcon("abd.json", "minecraft:bell"); return true; });
            capture("rules-specificity", "The exact-name rule narrows the JSON rule: abc gets a diamond; abd retains its bell.");
            menu();
            step("reset only the narrower authored rule", runtime -> {
                String id = expressionRule(EXACT, "minecraft:diamond").id();
                return choose(runtime, value -> value.startsWith(SFMItemstackPreviewRuleToolsAction.Kind.RESET.prefix() + " ") && value.endsWith(" " + id));
            });
            step("await inherited JSON icon", runtime -> {
                if (SFMClientThemeService.active().previewRules().size() != 1) return false;
                assertIcon("abc.json", "minecraft:bell"); assertSources(); return true;
            });
            capture("rules-reset", "Reset removes only the authored exact-name override and reveals the broader JSON rule again.");
            step("save restart witnesses", runtime -> {
                marker.addProperty("process_id", ProcessHandle.current().pid()); marker.addProperty("theme_sha256", hash(theme));
                marker.addProperty("rule_id", SFMClientThemeService.active().previewRules().get(0).id());
                write(directory.resolve("witness.json"), JSON.toJson(marker)); artifact(runtime, "rule-stage-witness", JSON.toJson(marker)); return true;
            });
        }
        command(() -> "sfm action invoke sfm:panel/open/right sfm:explorer " + directory.toUri());
        step("expand second split", runtime -> { explorerId = null; return waitForEntries(runtime); });
        step("verify split rendering", runtime -> { assertIcon("abc.json", "minecraft:bell"); assertIcon("minecraft", "minecraft:chest"); return true; });
        capture(resume ? "rules-resumed-split" : "rules-split", "The persisted rule is shared by two independent Explorer panels, including title-screen ItemStack rendering.");
        if (resume) step("write fresh-process proof", runtime -> {
            marker.addProperty("resume_process_id", ProcessHandle.current().pid());
            artifact(runtime, "rule-resume-witness", JSON.toJson(marker)); write(directory.resolve("resume-witness.json"), JSON.toJson(marker)); return true;
        });
    }

    private void prepare() {
        Path base = Minecraft.getInstance().gameDirectory.toPath().resolve("sfm-puppet/itemstack-preview-rule-journey").toAbsolutePath().normalize();
        try {
            Files.createDirectories(base);
            if (resume) {
                String id = UUID.fromString(Files.readString(base.resolve("latest.txt")).strip()).toString();
                directory = base.resolve(id); marker = JsonParser.parseString(Files.readString(directory.resolve("witness.json"))).getAsJsonObject();
            } else {
                String id = UUID.randomUUID().toString(); directory = base.resolve(id); Files.createDirectory(directory);
                Files.createDirectory(directory.resolve("minecraft"));
                write(directory.resolve("abc.json"), "{\"value\":1}\n"); write(directory.resolve("abd.json"), "{\"value\":2}\n");
                write(directory.resolve("notes.txt"), "This disposable source must not change.\n");
                write(directory.resolve("theme.toml"), SFMClientThemeService.DEFAULT_TOML + "\n[unrelated]\nkeep = \"retained\"\n");
                marker = new JsonObject(); marker.addProperty("directory", directory.toString());
                for (String name : List.of("abc.json", "abd.json", "notes.txt")) marker.addProperty(name, hash(directory.resolve(name)));
                write(base.resolve("latest.txt"), id);
            }
            theme = directory.resolve("theme.toml"); initialTheme = hash(theme);
            var loaded = SFMClientThemeService.reload(theme);
            require(loaded.valid(), "Invalid staged theme: " + loaded.diagnostics());
            require(authority().path().equals(theme.toRealPath()), "Disposable theme was not activated");
        } catch (Exception failure) { throw new IllegalStateException("Could not prepare isolated rule fixture", failure); }
    }
    private boolean waitForEntries(ISFMGamePuppetRuntime runtime) {
        var workspace = workspace(); if (workspace == null) return false;
        if (explorerId == null) explorerId = workspace.panelIds().stream().filter(id -> workspace.panelInstance(id) instanceof SFMExplorerPanel).reduce((a,b)->b).orElse(null);
        if (explorerId == null) return false;
        var state = explorer().model().state(workspace.panelContentBounds(explorerId));
        if (state.viewport().cells().stream().anyMatch(cell -> cell.row().entry().label().equals("abc.json"))) { expansionRequested = false; return true; }
        if (!expansionRequested) for (var cell : state.viewport().cells()) if (cell.row().entry().expandable() && !cell.row().expanded()) {
            click(cell.chevron(), GLFW.GLFW_MOUSE_BUTTON_LEFT); expansionRequested = true; break;
        }
        return false;
    }
    private void menu() {
        closePalette();
        step("right-click abc.json icon", runtime -> {
            var state = explorer().model().state(workspace().panelContentBounds(explorerId));
            var cell = state.viewport().cells().stream().filter(value -> value.row().entry().label().equals("abc.json")).findFirst().orElseThrow();
            click(SFMExplorerPanelViewport.iconBounds(cell, state.session().settings().view()), GLFW.GLFW_MOUSE_BUTTON_RIGHT); return true;
        });
    }
    private boolean choose(ISFMGamePuppetRuntime runtime, Predicate<String> predicate) {
        var palette = palette(); if (palette == null) return false;
        var command = palette.choiceCommandsForAutomation().stream().filter(predicate).findFirst();
        if (command.isEmpty()) throw new IllegalStateException("Required contextual action absent: " + palette.choiceCommandsForAutomation());
        if (!palette.choiceReadyForPointerAutomation(command.orElseThrow())) return false;
        runtime.clickActionChoice(command.orElseThrow()); return true;
    }
    private void assertIcon(String name, String expected) {
        var state = explorer().model().state(workspace().panelContentBounds(explorerId));
        var row = state.projection().rows().stream().filter(value -> value.entry().label().equals(name)).findFirst().orElseThrow();
        var presentation = SFMExplorerPresentationRegistry.minecraftDefaults().resolve(row).presentation();
        require(presentation.icon() instanceof SFMExplorerPresentation.ItemIcon, "Expected resolved ItemStack for " + name + ": " + presentation.icon());
        var icon = ((SFMExplorerPresentation.ItemIcon)presentation.icon()).item();
        require(icon.requestedItem().toString().equals(expected), "Wrong icon for " + name + ": " + icon);
        require(!SFMItemIconRenderer.inspect(Minecraft.getInstance(), icon).resolved().usedFallback(), "Expected title-screen rendering for " + name);
        require(row.path().equals(ca.teamdman.sfm.client.explorer.SFMPath.fromNative(directory.resolve(name))), "Rule changed path identity");
    }
    private void assertSources() { for (String name : List.of("abc.json", "abd.json", "notes.txt")) require(hash(directory.resolve(name)).equals(marker.get(name).getAsString()), "Source modified: " + name); }
    private SFMItemstackPreviewThemeTarget authority() { return SFMClientThemeService.activeAuthority().orElseThrow(); }
    private SFMItemstackPreviewRules.Rule expressionRule(String predicate, String item) {
        var parsed = SFMItemstackPreviewExpression.parse(predicate, 0, SFMItemstackPreviewOperators.Type.BOOLEAN, SFMItemstackPreviewRegistry.snapshot().operators());
        return new SFMItemstackPreviewRuleAction.Request(authority(), parsed.expression(), new ResourceLocation(item)).rule();
    }
    private String add(String predicate, String item) { return SFMItemstackPreviewRuleAction.PREFIX + " " + authority().argument() + " " + predicate + " " + item; }
    private SFMExplorerPanel explorer() { return (SFMExplorerPanel) workspace().panelInstance(explorerId); }
    private static SFMScreenMultiplexer workspace() { return Minecraft.getInstance().screen instanceof SFMScreenMultiplexer value ? value : null; }
    private static SFMCommandPaletteScreen palette() { return Minecraft.getInstance().screen instanceof SFMCommandPaletteScreen value ? value : null; }
    private static SFMItemstackPreviewDraftPanel draft() { return workspace() != null && workspace().focusedPanelInstance() instanceof SFMItemstackPreviewDraftPanel value ? value : null; }
    private void clickDraft(int index) { click(SFMItemstackPreviewDraftPanel.controls(workspace().panelContentBounds(workspace().focusedPanelId())), GLFW.GLFW_MOUSE_BUTTON_LEFT, index); }
    private void click(List<SFMExplorerPanelViewport.Rect> rectangles, int button, int index) { click(rectangles.get(index), button); }
    private void click(SFMExplorerPanelViewport.Rect rectangle, int button) { SFMGamePuppetPointer.clickVirtual(workspace(), rectangle.x() + rectangle.width()/2D, rectangle.y() + rectangle.height()/2D, button, 0); }
    private void closePalette() { step("dismiss palette if present", runtime -> {
        if (palette() == null) return true;
        runtime.pressScreenKey(GLFW.GLFW_KEY_ESCAPE, 0);
        ca.teamdman.sfm.SFM.LOGGER.info("SFM_ITEMSTACK_RULE_PUPPET palette_dismissed screen={}", runtime.currentScreenName());
        return palette() == null;
    }); }
    private void command(Supplier<String> supplier) {
        steps.add(new SFMPuppetAction() {
            private ExecuteCommandPalettePuppetAction delegate;
            public String description() { return "execute canonical command"; }
            public boolean tick(ISFMGamePuppetRuntime runtime) { if (delegate == null) delegate = new ExecuteCommandPalettePuppetAction(supplier.get()); return delegate.tick(runtime); }
        });
        closePalette();
    }
    private void step(String name, Predicate<ISFMGamePuppetRuntime> action) { steps.add(new SFMPuppetAction() { public String description() { return name; } public boolean tick(ISFMGamePuppetRuntime runtime) { return action.test(runtime); } }); }
    private void capture(String name, String caption) {
        steps.add(new SFMPuppetAction() { int settle; public String description() { return "capture " + name; } public boolean tick(ISFMGamePuppetRuntime runtime) { return ++settle > 8 && runtime.captureWithHud(name, Component.literal(caption)); } });
    }
    private static void artifact(ISFMGamePuppetRuntime runtime, String name, String text) { runtime.writeArtifact(name, SFMGamePuppetArtifactFormat.JSON, text); }
    private static void write(Path path, String text) { try { Files.writeString(path, text); } catch (Exception failure) { throw new IllegalStateException(failure); } }
    private static String hash(Path path) { try { return SFMItemstackPreviewRules.sha256(Files.readString(path)); } catch (Exception failure) { throw new IllegalStateException(failure); } }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
