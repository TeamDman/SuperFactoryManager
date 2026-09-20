package ca.teamdman.sfm.client.terminal;

import ca.teamdman.sfm.client.screen.workspace.*;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.*;

/** Explicit local controls. Merely opening this panel starts no process and grants no permission. */
public final class TouchDisplayTerminalMountsPanel implements SFMScreenPanel {
    public enum Control { PREVIOUS, NEXT, MODE, SESSION, START_SERVER, CREATE, CONNECT, INPUT, DISCONNECT, CLOSE_SESSION }
    private final SFMPanelWidgetHost widgets = new SFMPanelWidgetHost();
    private final Map<Control, SFMPanelActionButton> buttons = new EnumMap<>(Control.class);
    private final Map<Control, SFMScreenPanelBounds> rectangles = new EnumMap<>(Control.class);
    private SFMWorkspacePanelContext context;
    private Minecraft minecraft;
    private List<TouchDisplayTerminalBinding> declarations = List.of();
    private int selected, sessionIndex, ticks, scroll;
    private TouchDisplayTerminalServiceTransport.Mode mode = TouchDisplayTerminalServiceTransport.Mode.INTERACTIVE;
    private String message = "Choose a declared display. Approve its exact capabilities in Client program consents.";

    public TouchDisplayTerminalMountsPanel() {
        List<SFMPanelActionButton> children = new ArrayList<>();
        for (Control control : Control.values()) {
            String operation = control.name().toLowerCase(Locale.ROOT);
            String command = "sfm action invoke sfm:terminal/mounts/control " + operation;
            var button = new SFMPanelActionButton(new ResourceLocation("sfm", "terminal/mounts/" + operation),
                    new ResourceLocation("sfm", "default"), Component.literal(label(control)), () -> Component.literal(label(control)), () -> command,
                    () -> { if (context != null) SFMPanelActionExecution.execute(context, minecraft, command, feedback -> message = feedback.getString()); });
            buttons.put(control, button); children.add(button);
        }
        widgets.setChildren(children);
    }
    @Override public Component title() { return Component.literal("Touch Display terminal mounts"); }
    @Override public Optional<SFMPanelWidgetHost> widgetHost() { return Optional.of(widgets); }
    @Override public void opened(Minecraft minecraft, SFMScreenPanelBounds bounds, SFMWorkspacePanelContext context) {
        this.minecraft = minecraft; this.context = context; declarations = TouchDisplayTerminalRuntime.declarations();
    }
    @Override public void closed() { minecraft = null; context = null; rectangles.clear(); }
    @Override public void tick() { if (++ticks % 20 == 0) declarations = TouchDisplayTerminalRuntime.declarations(); }
    public Optional<SFMScreenPanelBounds> controlBoundsForAutomation(Control control) { return Optional.ofNullable(rectangles.get(control)); }
    public Optional<TouchDisplayTerminalBinding> selectedBinding() {
        return declarations.isEmpty() ? Optional.empty() : Optional.of(declarations.get(Math.floorMod(selected, declarations.size())));
    }
    private List<TouchDisplayTerminalRuntime.SessionView> existing(TouchDisplayTerminalBinding binding) {
        return TouchDisplayTerminalRuntime.sessions().stream().filter(session -> session.owner().equals(binding.owner())).toList();
    }
    public boolean activate(Control control) {
        if (minecraft == null) return false;
        if (control == Control.PREVIOUS || control == Control.NEXT) { selected += control == Control.NEXT ? 1 : -1; scroll = 0; return true; }
        if (control == Control.MODE) { mode = mode == TouchDisplayTerminalServiceTransport.Mode.INTERACTIVE
                ? TouchDisplayTerminalServiceTransport.Mode.STRUCTURED_WORKER : TouchDisplayTerminalServiceTransport.Mode.INTERACTIVE; return true; }
        if (control == Control.SESSION) { sessionIndex++; return true; }
        var selected = selectedBinding();
        if (selected.isEmpty()) { message = "No static terminal/display declarations found in loaded Client Managers."; return false; }
        var binding = selected.orElseThrow();
        var sessions = existing(binding);
        var session = sessions.isEmpty() ? null : sessions.get(Math.floorMod(sessionIndex, sessions.size()));
        switch (control) {
            case START_SERVER -> { if (!TouchDisplayTerminalRuntime.requestStartServer(binding, text -> message = text)) message = "Start rejected: review session consent or wait for pending preparation."; }
            case CREATE -> { if (!TouchDisplayTerminalRuntime.requestCreate(binding, mode, text -> message = text)) message = "Create rejected: review exact consent and current declaration."; }
            case CONNECT -> message = session != null && TouchDisplayTerminalRuntime.connect(binding, session.id())
                    ? "Connected to existing SFM-owned session. Input remains disabled." : "Connect rejected: select a compatible SFM-owned session and an unmounted display.";
            case INPUT -> message = TouchDisplayTerminalRuntime.enableInput(binding)
                    ? "Input enabled for future matching touch packets only." : "Input unavailable: approve the separate input/inbox capabilities and ensure no other input writer exists.";
            case DISCONNECT -> { TouchDisplayTerminalRuntime.disconnect(binding); message = "Disconnected this display. An input-owner disconnect closes its session."; }
            case CLOSE_SESSION -> { if (session != null) TouchDisplayTerminalRuntime.closeSession(session.id()); message = "Selected SFM-owned session closed."; }
            default -> { return false; }
        }
        return true;
    }
    @Override public void render(PoseStack pose, Minecraft minecraft, SFMScreenPanelBounds bounds, int mouseX, int mouseY, float partialTick, boolean focused) {
        GuiComponent.fill(pose, bounds.x(), bounds.y(), bounds.x() + bounds.width(), bounds.y() + bounds.height(), 0xF0101520);
        int x = bounds.x() + 6, y = bounds.y() + 6, width = Math.max(20, bounds.width() - 12);
        minecraft.font.draw(pose, "Local sessions; no automatic creation or input", x, y, 0xFFE8EDF2); y += 14;
        int columns = Math.max(2, Math.min(4, width / 120)), buttonWidth = Math.max(20, (width - (columns - 1) * 3) / columns);
        int index = 0; rectangles.clear();
        for (Control control : Control.values()) {
            var button = buttons.get(control);
            var rectangle = new SFMScreenPanelBounds(x + (index % columns) * (buttonWidth + 3), y + (index / columns) * 22, buttonWidth, 20);
            button.setPanelBounds(rectangle); button.setMessage(Component.literal(label(control)));
            button.visible = rectangle.y() + 20 <= bounds.y() + bounds.height();
            button.active = selectedBinding().isPresent() || control == Control.PREVIOUS || control == Control.NEXT || control == Control.MODE;
            if (button.visible) rectangles.put(control, rectangle); index++;
        }
        y += ((index + columns - 1) / columns) * 22 + 4;
        List<String> lines = new ArrayList<>(); lines.add(message);
        selectedBinding().ifPresent(binding -> {
            lines.add("Manager: " + binding.owner().managerPosition().toShortString() + " | display: " + binding.display().toShortString());
            lines.add("Channel: " + binding.channel() + " | input declared: " + binding.input());
            var sessions = existing(binding);
            lines.add("Existing SFM-owned session: " + (sessions.isEmpty() ? "none" : sessions.get(Math.floorMod(sessionIndex, sessions.size())).id()));
            lines.add(ca.teamdman.sfm.common.value.SFMValueSchema.canonicalActionJson(TouchDisplayTerminalRuntime.status(binding)));
        });
        lines.add("Interactive keeps the normal configured shell. Structured worker (test) requires an explicit local executable setting.");
        lines.add("Closing this panel keeps mounts active. World changes, revocation and target removal release sessions. Start server controls a shared local helper.");
        var wrapped = lines.stream().flatMap(line -> minecraft.font.split(Component.literal(line), width).stream()).toList();
        int visible = Math.max(1, (bounds.y() + bounds.height() - y) / 10);
        scroll = Math.min(scroll, Math.max(0, wrapped.size() - visible));
        for (int i = scroll; i < wrapped.size() && y + 9 <= bounds.y() + bounds.height(); i++, y += 10) minecraft.font.draw(pose, wrapped.get(i), x, y, 0xFFD4DFE8);
        widgets.render(pose, mouseX, mouseY, partialTick);
    }
    @Override public boolean mouseScrolled(double x, double y, double delta) { scroll = Math.max(0, scroll - (int) Math.signum(delta) * 3); return true; }
    private String label(Control control) {
        return switch (control) {
            case PREVIOUS -> "Previous display"; case NEXT -> "Next display"; case MODE -> mode == TouchDisplayTerminalServiceTransport.Mode.INTERACTIVE ? "Mode: interactive" : "Worker (test)";
            case SESSION -> "Next SFM session"; case START_SERVER -> "Start local server"; case CREATE -> "Create session"; case CONNECT -> "Connect selected";
            case INPUT -> "Enable input"; case DISCONNECT -> "Disconnect display"; case CLOSE_SESSION -> "Close SFM session";
        };
    }
}
