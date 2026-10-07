package ca.teamdman.sfm.client.handler;

{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% if features.single_line_input or features.document_history %}
import ca.teamdman.sfm.client.history.SFMDocumentHistoryInputRouting;
import ca.teamdman.sfm.client.history.document.SFMDocumentHistoryContract;
{% endif %}
{% endcase %}
import ca.teamdman.sfm.client.keybinding.SFMKeyBindingEngine;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
import ca.teamdman.sfm.client.keybinding.SFMKeyBindingMatchResult;
{% endcase %}
import ca.teamdman.sfm.client.keybinding.SFMKeyBindingService;
import ca.teamdman.sfm.client.keybinding.SFMKeyInputEvent;
import ca.teamdman.sfm.client.keybinding.SFMKeyModifier;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
import ca.teamdman.sfm.client.keybinding.SFMKeyboardUsageContextSnapshot;
{% endcase %}
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
import ca.teamdman.sfm.client.keybinding.SFMKeyboardUsageContextProvider;
{% endcase %}
import ca.teamdman.sfm.common.event_bus.SFMSubscribeEvent;
import ca.teamdman.sfm.common.util.SFMDist;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
{% endcase %}
import net.minecraft.client.Minecraft;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
import net.minecraft.client.gui.screens.Screen;
{% endcase %}
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1" %}
import net.minecraftforge.client.event.InputEvent;
{% when "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
import net.neoforged.neoforge.client.event.InputEvent;
{% endcase %}
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
import net.minecraftforge.client.event.ScreenEvent;
{% endcase %}
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1" %}
import net.minecraftforge.event.TickEvent;
{% when "1.20.2", "1.20.3", "1.20.4" %}
import net.neoforged.neoforge.event.TickEvent;
{% when "1.21", "1.21.1", "26.1.2" %}
import net.neoforged.neoforge.client.event.ClientTickEvent;
{% endcase %}
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
import org.jetbrains.annotations.Nullable;
{% endcase %}
import org.lwjgl.glfw.GLFW;

import java.util.EnumSet;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
import java.util.HashSet;
{% endcase %}
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% if features.single_line_input or features.document_history %}
import java.util.Optional;
{% endif %}
{% endcase %}
import java.util.Set;

{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
/**
 * Matches dynamic bindings at Forge's cancellable pre-screen boundary.
 * Raw input remains a compatibility path for explicit-global relationships
 * while no GUI is open.
 */
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
/**
 * Adapts Forge's ordered raw keyboard callback to the replayable SFM matcher.
 * Unlike polling a {@code KeyMapping} once per tick, this seam preserves
 * press/release order and multiple transitions occurring during one tick.
 */
{% endcase %}
public final class SFMDynamicKeyBindingHandler {
{% case minecraft_version %}
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
    private static long eventSequence;
{% endcase %}
    private static long clientTick;
    private static boolean windowFocused = true;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
    private static final Set<Integer> PRESSED_KEYS = new HashSet<>();
    private static final Set<Integer> CONSUMED_KEYS = new HashSet<>();
    private static final Set<Integer> CHARACTER_SUPPRESSION_KEYS = new HashSet<>();
    private static final ThreadLocal<ScreenEventMarker> SCREEN_EVENT = new ThreadLocal<>();
    private static @Nullable Screen activeInputScreen;
{% endcase %}

    private SFMDynamicKeyBindingHandler() {
    }

{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
    @SFMSubscribeEvent(value = SFMDist.CLIENT)
    @MCVersionDependentBehaviour
    public static void onScreenKeyPressed(ScreenEvent.KeyPressed.Pre event) {
        markScreenEvent(event.getKeyCode(), event.getScanCode(), false, event.getModifiers());
        if (acceptScreenPress(
                event.getScreen(),
                event.getKeyCode(),
                event.getScanCode(),
                event.getModifiers())) {
            event.setCanceled(true);
        }
    }

    /**
     * Mixin ingress for Vanilla's screenshot/fullscreen press paths, which
     * return before Forge emits ScreenEvent/InputEvent on Minecraft 1.19.2.
     */
    @MCVersionDependentBehaviour
    public static boolean onPreVanillaReservedKeyPressed(
            @Nullable Screen screen,
            int keyCode,
            int scanCode,
            int modifiers
    ) {
        return acceptScreenPress(screen, keyCode, scanCode, modifiers);
    }

    @SFMSubscribeEvent(value = SFMDist.CLIENT, receiveCanceled = true)
    @MCVersionDependentBehaviour
    public static void onScreenKeyReleased(ScreenEvent.KeyReleased.Pre event) {
        markScreenEvent(event.getKeyCode(), event.getScanCode(), true, event.getModifiers());
        synchronizeScreen(event.getScreen());
        boolean consumed = CONSUMED_KEYS.contains(event.getKeyCode());
{% if features.single_line_input or features.document_history %}
        recordDocumentInput(
                event.getScreen(),
                SFMDocumentHistoryContract.RawEventKind.KEY_UP,
                "forge-screen-key",
                journalKeyCode(event.getKeyCode(), event.getScanCode()),
                Optional.empty(),
                event.getModifiers(),
                consumed,
                !consumed
        );
{% endif %}
        PRESSED_KEYS.remove(event.getKeyCode());
        CHARACTER_SUPPRESSION_KEYS.remove(event.getKeyCode());
        if (CONSUMED_KEYS.remove(event.getKeyCode())) event.setCanceled(true);
    }

    @SFMSubscribeEvent(value = SFMDist.CLIENT, receiveCanceled = true)
    @MCVersionDependentBehaviour
    public static void onScreenCharacterTyped(ScreenEvent.CharacterTyped.Pre event) {
        synchronizeScreen(event.getScreen());
        boolean consumed = !CHARACTER_SUPPRESSION_KEYS.isEmpty();
{% if features.single_line_input or features.document_history %}
        String text = Character.toString(event.getCodePoint());
{% endif %}
{% if features.single_line_input or features.document_history %}
        recordDocumentInput(
                event.getScreen(),
                SFMDocumentHistoryContract.RawEventKind.CHARACTER,
                "forge-screen-character",
                String.format("U+%04X", (int) event.getCodePoint()),
                Optional.of(text),
                event.getModifiers(),
                consumed,
                !consumed
        );
{% endif %}
        if (consumed) event.setCanceled(true);
    }

{% endcase %}
    @SFMSubscribeEvent(value = SFMDist.CLIENT)
{% case minecraft_version %}
{% when "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
    @ca.teamdman.sfm.common.util.MCVersionDependentBehaviour
{% endcase %}
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
    public static void onKey(InputEvent.Key event) {
        if (consumeScreenEvent(
                event.getKey(),
                event.getScanCode(),
                event.getAction() == GLFW.GLFW_RELEASE,
                event.getModifiers())) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen != null) return;
        synchronizeScreen(null);
        SFMKeyInputEvent.Type type = switch (event.getAction()) {
            case GLFW.GLFW_PRESS -> SFMKeyInputEvent.Type.PRESS;
            case GLFW.GLFW_RELEASE -> SFMKeyInputEvent.Type.RELEASE;
            case GLFW.GLFW_REPEAT -> SFMKeyInputEvent.Type.REPEAT;
            default -> null;
        };
        if (type == null) return;
        if (type == SFMKeyInputEvent.Type.PRESS && !PRESSED_KEYS.add(event.getKey())) return;
        if (type == SFMKeyInputEvent.Type.RELEASE) {
            PRESSED_KEYS.remove(event.getKey());
            CONSUMED_KEYS.remove(event.getKey());
            CHARACTER_SUPPRESSION_KEYS.remove(event.getKey());
        }
        SFMKeyBindingMatchResult match = SFMKeyBindingService.INSTANCE.acceptKey(
                event.getKey(),
                type,
                modifiers(event.getModifiers()),
                SFMKeyboardUsageContextSnapshot.global(
                        null,
                        () -> Minecraft.getInstance().screen == null));
        if (type == SFMKeyInputEvent.Type.PRESS && match.consumed()) {
            CONSUMED_KEYS.add(event.getKey());
            CHARACTER_SUPPRESSION_KEYS.add(event.getKey());
        }
    }

{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
    public static void onKey(InputEvent.Key event) {
        SFMKeyInputEvent.Type type = switch (event.getAction()) {
            case GLFW.GLFW_PRESS -> SFMKeyInputEvent.Type.PRESS;
            case GLFW.GLFW_RELEASE -> SFMKeyInputEvent.Type.RELEASE;
            case GLFW.GLFW_REPEAT -> SFMKeyInputEvent.Type.REPEAT;
            default -> null;
        };
        if (type == null) return;
        SFMKeyBindingService.INSTANCE.accept(new SFMKeyInputEvent(
                ++eventSequence,
                clientTick,
                event.getKey(),
                type,
                modifiers(event.getModifiers())
        ));
    }

{% endcase %}
    @SFMSubscribeEvent(value = SFMDist.CLIENT)
{% case minecraft_version %}
{% when "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
    @ca.teamdman.sfm.common.util.MCVersionDependentBehaviour
{% endcase %}
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4" %}
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
{% when "1.21", "1.21.1", "26.1.2" %}
    public static void onClientTick(ClientTickEvent.Post event) {
{% endcase %}
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
        long window = Minecraft.getInstance().getWindow().getWindow();
{% when "26.1.2" %}
        long window = Minecraft.getInstance().getWindow().handle();
{% endcase %}
        boolean focused = GLFW.glfwGetWindowAttrib(window, GLFW.GLFW_FOCUSED) == GLFW.GLFW_TRUE;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% if features.single_line_input or features.document_history %}
        if (windowFocused != focused) {
            recordDocumentInput(
                    Minecraft.getInstance().screen,
                    SFMDocumentHistoryContract.RawEventKind.FOCUS,
                    "glfw-window-focus",
                    focused ? "gain" : "loss",
                    Optional.empty(),
                    0,
                    false,
                    true
            );
        }
{% endif %}
{% endcase %}
        if (windowFocused && !focused) resetForFocusLoss();
        windowFocused = focused;
        SFMKeyBindingService.INSTANCE.advanceTime(++clientTick);
    }

    public static void resetForFocusLoss() {
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
        PRESSED_KEYS.clear();
        CONSUMED_KEYS.clear();
        CHARACTER_SUPPRESSION_KEYS.clear();
{% endcase %}
        SFMKeyBindingService.INSTANCE.reset(SFMKeyBindingEngine.ResetReason.FOCUS_LOST);
    }

{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
    static SFMKeyboardUsageContextSnapshot contextFor(@Nullable Screen screen) {
        if (screen instanceof SFMKeyboardUsageContextProvider provider) {
            return provider.keyboardUsageContextSnapshot();
        }
        return SFMKeyboardUsageContextSnapshot.global(
                screen,
                () -> Minecraft.getInstance().screen == screen);
    }

    private static boolean acceptScreenPress(
            @Nullable Screen screen,
            int keyCode,
            int scanCode,
            int modifierMask
    ) {
        synchronizeScreen(screen);
        boolean firstPress = PRESSED_KEYS.add(keyCode);
        if (CONSUMED_KEYS.contains(keyCode)) {
{% if features.single_line_input or features.document_history %}
            recordDocumentInput(
                    screen,
                    SFMDocumentHistoryContract.RawEventKind.KEY_REPEAT,
                    "forge-screen-key",
                    journalKeyCode(keyCode, scanCode),
                    Optional.empty(),
                    modifierMask,
                    true,
                    false
            );
{% endif %}
            return true;
        }
        if (!firstPress) {
{% if features.single_line_input or features.document_history %}
            recordDocumentInput(
                    screen,
                    SFMDocumentHistoryContract.RawEventKind.KEY_REPEAT,
                    "forge-screen-key",
                    journalKeyCode(keyCode, scanCode),
                    Optional.empty(),
                    modifierMask,
                    false,
                    true
            );
{% endif %}
            return false;
        }
{% if features.single_line_input or features.document_history %}
        final SFMKeyBindingMatchResult[] observed = new SFMKeyBindingMatchResult[1];
        SFMKeyBindingMatchResult match = SFMKeyBindingService.INSTANCE.acceptKey(
                keyCode,
                SFMKeyInputEvent.Type.PRESS,
                modifiers(modifierMask),
                contextFor(screen),
                result -> {
                    observed[0] = result;
                    recordDocumentInput(
                            screen,
                            SFMDocumentHistoryContract.RawEventKind.KEY_DOWN,
                            "forge-screen-key",
                            journalKeyCode(keyCode, scanCode),
                            Optional.empty(),
                            modifierMask,
                            result.consumed(),
                            !result.consumed()
                    );
                });
        if (observed[0] == null) {
            // Dispatch can be suspended during keybinding capture. The raw
            // event still belongs to the document journal.
            recordDocumentInput(
                    screen,
                    SFMDocumentHistoryContract.RawEventKind.KEY_DOWN,
                    "forge-screen-key",
                    journalKeyCode(keyCode, scanCode),
                    Optional.empty(),
                    modifierMask,
                    false,
                    true
            );
        }
{% else %}
        SFMKeyBindingMatchResult match = SFMKeyBindingService.INSTANCE.acceptKey(
                keyCode,
                SFMKeyInputEvent.Type.PRESS,
                modifiers(modifierMask),
                contextFor(screen));
{% endif %}
        if (!match.consumed()) return false;
        CONSUMED_KEYS.add(keyCode);
        CHARACTER_SUPPRESSION_KEYS.add(keyCode);
        return true;
    }

    private static void synchronizeScreen(@Nullable Screen screen) {
        if (activeInputScreen == screen) return;
        activeInputScreen = screen;
        SFMKeyBindingService.INSTANCE.reset(SFMKeyBindingEngine.ResetReason.CONTEXT_CHANGED);
    }

    private static void markScreenEvent(int keyCode, int scanCode, boolean release, int modifiers) {
        SCREEN_EVENT.set(new ScreenEventMarker(keyCode, scanCode, release, modifiers));
    }

    private static boolean consumeScreenEvent(int keyCode, int scanCode, boolean release, int modifiers) {
        ScreenEventMarker marker = SCREEN_EVENT.get();
        SCREEN_EVENT.remove();
        return marker != null
                && marker.keyCode == keyCode
                && marker.scanCode == scanCode
                && marker.release == release
                && marker.modifiers == modifiers;
    }

{% endcase %}
    private static Set<SFMKeyModifier> modifiers(int mask) {
        EnumSet<SFMKeyModifier> result = EnumSet.noneOf(SFMKeyModifier.class);
        if ((mask & GLFW.GLFW_MOD_CONTROL) != 0) result.add(SFMKeyModifier.CONTROL);
        if ((mask & GLFW.GLFW_MOD_ALT) != 0) result.add(SFMKeyModifier.ALT);
        if ((mask & GLFW.GLFW_MOD_SHIFT) != 0) result.add(SFMKeyModifier.SHIFT);
        if ((mask & GLFW.GLFW_MOD_SUPER) != 0) result.add(SFMKeyModifier.SUPER);
        return result;
    }
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}

{% if features.single_line_input or features.document_history %}
    private static void recordDocumentInput(
            @Nullable Screen screen,
            SFMDocumentHistoryContract.RawEventKind kind,
            String source,
            String code,
            Optional<String> text,
            int modifiers,
            boolean consumed,
            boolean delivered
    ) {
        if (screen == null) return;
        SFMDocumentHistoryInputRouting.resolve(screen).ifPresent(target -> target.recordDocumentRawInput(
                clientTick,
                kind,
                source,
                code,
                text,
                modifiers,
                consumed,
                delivered
        ));
    }

{% endif %}
    static String journalKeyCode(int keyCode, int scanCode) {
        return "key=" + keyCode + ",scan=" + scanCode;
    }

    private record ScreenEventMarker(int keyCode, int scanCode, boolean release, int modifiers) {
    }
{% endcase %}
}
