package qa.sfm;

import ca.teamdman.sfm.client.registry.SFMTextEditors;
import ca.teamdman.sfm.client.screen.SFMScreenChangeHelpers;
import ca.teamdman.sfm.client.screen.SFMDrawCanvasScreen;
import ca.teamdman.sfm.client.screen.text_editor.ISFMTextEditScreen;
import ca.teamdman.sfm.client.screen.text_editor.SFMTextEditScreenV1;
import ca.teamdman.sfm.client.screen.text_editor.SFMTextEditScreenV2;
import ca.teamdman.sfm.client.text_editor.ISFMTextEditorRegistration;
import ca.teamdman.sfm.client.text_editor.SFMTextEditScreenDiskOpenContext;
import ca.teamdman.sfm.client.text_editor.TextEditContext;
import ca.teamdman.sfm.common.config.SFMConfig;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
import com.google.gson.Gson;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.DeferredRegister;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** Local test fixture only. Normal Forge registration; picker/editor implementations are untouched. */
@Mod(PickerFixture.ID)
public final class PickerFixture {
    public static final String ID = "sfm_picker_fixture";
    private static final Gson JSON = new Gson();
    private static final Path ROOT = FMLPaths.GAMEDIR.get().resolve("picker-fixture");
    private static final DeferredRegister<ISFMTextEditorRegistration> EDITORS =
            DeferredRegister.create(SFMTextEditors.REGISTRY_ID, ID);
    private ISFMTextEditScreen activeEditor;
    private String previous = "";
    private int factories;
    private int records;

    public PickerFixture() {
        for (String name : List.of("external", "external_alt")) {
            EDITORS.register(name, () -> context -> {
                factories++;
                record(Map.of("event", "external_factory", "id", ID + ":" + name,
                        "initialText", context.initialValue(), "initialSha256", sha(context.initialValue()),
                        "factoryCalls", factories));
                return new SFMTextEditScreenV1(context);
            });
        }
        EDITORS.register(FMLJavaModLoadingContext.get().getModEventBus());
        MinecraftForge.EVENT_BUS.addListener(this::key);
        MinecraftForge.EVENT_BUS.addListener(this::tick);
    }

    private void key(InputEvent.Key event) {
        if (event.getAction() != GLFW.GLFW_PRESS || event.getKey() != GLFW.GLFW_KEY_F8
                || !(Minecraft.getInstance().screen instanceof TitleScreen)) return;
        try {
            String saved = Files.readString(ROOT.resolve("program.sfml"));
            record(Map.of("event", "open_request", "preferred", SFMConfig.CLIENT_TEXT_EDITOR_CONFIG.preferredEditor.get(),
                    "savedText", saved, "savedSha256", sha(saved)));
            SFMScreenChangeHelpers.showProgramEditScreen(new SFMTextEditScreenDiskOpenContext(saved,
                    LabelPositionHolder.empty(), text -> {
                try {
                    Files.writeString(ROOT.resolve("program.sfml"), text, StandardCharsets.UTF_8);
                    record(Map.of("event", "save", "text", text, "sha256", sha(text)));
                } catch (Exception failure) { throw new RuntimeException(failure); }
            }));
        } catch (Exception failure) { record(Map.of("event", "fixture_error", "detail", failure.toString())); }
    }

    private void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || records >= 10000) return;
        var screen = Minecraft.getInstance().screen;
        if (screen instanceof ISFMTextEditScreen editor) activeEditor = editor;
        if (activeEditor == null) return;
        try {
            var state = new LinkedHashMap<String, Object>();
            state.put("event", "state");
            state.put("screen", screen == null ? "null" : screen.getClass().getName());
            state.put("screenIdentity", System.identityHashCode(screen));
            state.put("editor", activeEditor.getClass().getName());
            state.put("editorIdentity", System.identityHashCode(activeEditor));
            state.put("preferred", SFMConfig.CLIENT_TEXT_EDITOR_CONFIG.preferredEditor.get());
            state.put("factoryCalls", factories);
            state.put("savedSha256", sha(Files.readString(ROOT.resolve("program.sfml"))));
            if (activeEditor instanceof SFMTextEditScreenV1) {
                Object widget = field(activeEditor, "textarea");
                if (widget != null) {
                    String text = ((MultiLineEditBox) widget).getValue();
                    state.put("text", text); state.put("textSha256", sha(text));
                    state.put("widgetIdentity", System.identityHashCode(widget));
                    state.put("cursor", method(widget, "getCursorPosition"));
                    state.put("selectionCursor", method(widget, "getSelectionCursorPosition"));
                }
            } else if (activeEditor instanceof SFMTextEditScreenV2) {
                TextEditContext context = (TextEditContext) field(activeEditor, "textEditContext");
                state.put("text", context.getContent()); state.put("textSha256", sha(context.getContent()));
                state.put("contextIdentity", System.identityHashCode(context));
                state.put("cursors", context.multiCursor().toString());
            } else if (activeEditor instanceof SFMDrawCanvasScreen canvas
                    && Boolean.TRUE.equals(field(canvas, "initialContentLoaded"))) {
                // Observe only after normal screen initialization has loaded the document.
                String text = canvas.currentDocumentText();
                state.put("text", text); state.put("textSha256", sha(text));
            }
            if (screen != null && screen.getClass().getSimpleName().equals("SFMTextEditorSelectionScreen")) {
                Object selected = field(screen, "selection");
                state.put("pickerSelected", method(selected, "selectedId"));
            }
            String serialized = JSON.toJson(state);
            if (!serialized.equals(previous)) { previous = serialized; record(state); }
        } catch (Exception failure) {
            String detail = failure.toString();
            if (!detail.equals(previous)) { previous = detail; record(Map.of("event", "observer_error", "detail", detail)); }
        }
    }

    private static Object field(Object value, String name) throws Exception {
        for (Class<?> type = value.getClass(); type != null; type = type.getSuperclass()) {
            try { Field field = type.getDeclaredField(name); field.setAccessible(true); return field.get(value); }
            catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(name);
    }
    private static Object method(Object value, String name) throws Exception {
        Method method = value.getClass().getMethod(name); method.setAccessible(true); return method.invoke(value);
    }
    private void record(Map<String, ?> value) {
        try {
            Files.createDirectories(ROOT);
            var record = new LinkedHashMap<String, Object>(value); record.put("timeMillis", System.currentTimeMillis());
            Files.writeString(ROOT.resolve("events.jsonl"), JSON.toJson(record) + "\n", StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            records++;
        } catch (Exception failure) { throw new IllegalStateException("Fixture evidence write failed", failure); }
    }
    private static String sha(String text) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception failure) { throw new IllegalStateException(failure); }
    }
}
