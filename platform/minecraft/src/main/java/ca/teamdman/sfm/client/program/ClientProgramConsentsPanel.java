package ca.teamdman.sfm.client.program;

import ca.teamdman.sfm.client.screen.SFMConfirmationScreen;
import ca.teamdman.sfm.client.screen.SFMScreenChangeHelpers;
import ca.teamdman.sfm.client.screen.workspace.*;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

import java.util.*;

/** Explicit review only: discovery and program requests never open this panel or its confirmation. */
public final class ClientProgramConsentsPanel implements SFMScreenPanel {
    public enum Control { PREVIOUS, NEXT, CAPABILITY, VIEW, LIFETIME, RETRY, REVIEW, DENY, REVOKE, FORGET, STOP_ALL, RESUME, SAVE, SIGNER, TRUST_SIGNER, UNTRUST_SIGNER, SIGN_REVIEW }
    private static final ResourceLocation USAGE = new ResourceLocation("sfm", "default");
    private static final long DAY = 86_400_000L;
    private static final Long[] LIFETIMES = {DAY, 365 * DAY, null};
    private static final Long[] RETRIES = {null, 60_000L, DAY};
    private final ClientProgramConsentService service;
    private final ClientProgramSignerTrustService signers;
    private final SFMPanelWidgetHost widgets = new SFMPanelWidgetHost();
    private final Map<Control, SFMPanelActionButton> controls = new EnumMap<>(Control.class);
    private final Map<Control, SFMScreenPanelBounds> controlBounds = new EnumMap<>(Control.class);
    private SFMWorkspacePanelContext context;
    private Minecraft minecraft;
    private ClientProgramIdentity selected;
    private int capabilityIndex;
    private int lifetimeIndex = 1;
    private int retryIndex;
    private ClientProgramIdentity signerIdentity;
    private List<String> signerChoices = List.of();
    private int signerIndex;
    private ClientProgramConsentReview.View view = ClientProgramConsentReview.View.SCOPE;
    private int scroll;
    private int visibleLines = 1;
    private String message = "Select a program and capability. Review never opens automatically.";
    private List<String> rawLines = List.of();
    private List<FormattedCharSequence> wrappedLines = List.of();
    private int wrappedWidth = -1;

    public ClientProgramConsentsPanel() { this(ClientProgramConsentRuntime.service(), ClientProgramSignerTrustRuntime.service()); }
    public ClientProgramConsentsPanel(ClientProgramConsentService service) {
        this(service, null);
    }
    public ClientProgramConsentsPanel(ClientProgramConsentService service, ClientProgramSignerTrustService signers) {
        this.service = Objects.requireNonNull(service);
        this.signers = signers;
        List<SFMPanelActionButton> ordered = new ArrayList<>();
        for (Control control : Control.values()) {
            String operation = control.name().toLowerCase(Locale.ROOT);
            String command = "sfm action invoke sfm:client_program/consents/control " + operation;
            var button = new SFMPanelActionButton(new ResourceLocation("sfm", "client_program/consents/" + operation),
                    USAGE, Component.literal(label(control)), () -> Component.literal(label(control)), () -> command,
                    () -> {
                        if (context != null) SFMPanelActionExecution.execute(context, minecraft, command,
                                feedback -> message = feedback.getString());
                    });
            controls.put(control, button);
            ordered.add(button);
        }
        widgets.setChildren(ordered);
    }

    @Override public Component title() { return Component.literal("Client program consents"); }
    @Override public Optional<SFMPanelWidgetHost> widgetHost() { return Optional.of(widgets); }
    @Override public void opened(Minecraft minecraft, SFMScreenPanelBounds bounds, SFMWorkspacePanelContext context) {
        this.minecraft = minecraft;
        this.context = context;
    }
    @Override public void closed() { context = null; minecraft = null; controlBounds.clear(); }

    public Optional<ClientProgramIdentity> selectedIdentity() { return snapshot().map(ClientProgramConsentStore.Snapshot::identity); }
    public String statusMessage() { return message; }
    public Optional<SFMScreenPanelBounds> controlBoundsForAutomation(Control control) {
        return Optional.ofNullable(controlBounds.get(control));
    }

    public boolean activate(Control control) {
        if (control == Control.PREVIOUS || control == Control.NEXT) {
            var records = service.store().snapshots();
            if (!records.isEmpty()) {
                int current = 0;
                for (int i = 0; i < records.size(); i++) if (records.get(i).identity().equals(selected)) current = i;
                selected = records.get(Math.floorMod(current + (control == Control.NEXT ? 1 : -1), records.size())).identity();
                capabilityIndex = 0;
                scroll = 0;
            }
            return true;
        }
        if (control == Control.VIEW) {
            view = ClientProgramConsentReview.View.values()[(view.ordinal() + 1) % ClientProgramConsentReview.View.values().length];
            scroll = 0;
            return true;
        }
        if (control == Control.LIFETIME) { lifetimeIndex = (lifetimeIndex + 1) % LIFETIMES.length; return true; }
        if (control == Control.RETRY) { retryIndex = (retryIndex + 1) % RETRIES.length; return true; }
        if (control == Control.STOP_ALL) { result(service.stopAll()); return true; }
        if (control == Control.SAVE) { result(service.retrySave()); return true; }
        if (control == Control.RESUME) {
            confirm("Resume client program review?", "No previous approvals will be restored.", () -> result(service.resume()));
            return true;
        }
        var record = snapshot();
        if (record.isEmpty()) { message = "No locally observed programs are available."; return false; }
        var identity = record.orElseThrow().identity();
        List<ResourceLocation> capabilities = capabilities(identity);
        if (control == Control.CAPABILITY) { capabilityIndex = (capabilityIndex + 1) % capabilities.size(); return true; }
        ResourceLocation capability = capabilities.get(Math.floorMod(capabilityIndex, capabilities.size()));
        switch (control) {
            case SIGN_REVIEW -> message = ca.teamdman.sfm.client.program.signing.ClientProgramSigningRuntime.open(identity,
                    ClientProgramConsentReview.previous(service.store(), record.orElseThrow())
                            .flatMap(ClientProgramConsentStore.Snapshot::evidence)
                            .map(ClientProgramConsentStore.Evidence::source).orElse(""));
            case SIGNER -> selectSigner(identity);
            case TRUST_SIGNER -> trustSigner(identity);
            case UNTRUST_SIGNER -> untrustSigner(identity);
            case REVIEW -> review(identity, capability);
            case DENY -> {
                if (service.gate().state(identity, capability) == ClientProgramConsentGate.ConsentState.APPROVED) {
                    var revoked = service.revoke(identity, capability);
                    if (!revoked.successful()) { result(revoked); return true; }
                }
                var opened = service.reopen(identity, capability);
                if (!opened.successful()) result(opened);
                else result(service.decide(identity, capability, ClientProgramConsentGate.Decision.DENY,
                        null, future(RETRIES[retryIndex])));
            }
            case REVOKE -> result(service.revoke(identity, capability));
            case FORGET -> confirm("Forget this exact program?",
                    "This revokes all its approvals and removes its remembered source/history.", () -> {
                        result(service.forget(identity)); selected = null; scroll = 0;
                    });
            default -> { return false; }
        }
        return true;
    }

    private void selectSigner(ClientProgramIdentity identity) {
        if (signers == null) { message = "Signer review is unavailable in this panel."; return; }
        try {
            var choices = new TreeSet<>(signers.currentSigners(identity));
            signers.snapshot().stream().filter(grant -> sameScope(grant, identity))
                    .map(ClientProgramSignerTrustGrant::fingerprint).forEach(choices::add);
            var next = List.copyOf(choices);
            signerIndex = identity.equals(signerIdentity) && next.equals(signerChoices) && !next.isEmpty()
                    ? (signerIndex + 1) % next.size() : 0;
            signerIdentity = identity;
            signerChoices = next;
            message = next.isEmpty() ? "No current signatures or stored signer rules at this scope." : "Review the full fingerprint and scope below.";
        } catch (RuntimeException invalid) { message = "Current signature evidence is unavailable."; }
    }

    private Optional<String> selectedSigner(ClientProgramIdentity identity) {
        return identity.equals(signerIdentity) && !signerChoices.isEmpty()
                ? Optional.of(signerChoices.get(Math.floorMod(signerIndex, signerChoices.size()))) : Optional.empty();
    }

    private void trustSigner(ClientProgramIdentity identity) {
        var selectedSigner = selectedSigner(identity);
        if (signers == null || selectedSigner.isEmpty()) { message = "Use Signer to select a current author first."; return; }
        String fingerprint = selectedSigner.orElseThrow();
        Long duration = LIFETIMES[lifetimeIndex];
        String details = fingerprint + ". Only at " + identity.dimension() + " " + identity.managerPosition().toShortString()
                + ", this world, runtime and exact label bindings. Capability ceiling: " + capabilities(identity)
                + ". This also permits FUTURE source revisions signed by this author within that ceiling. "
                + (duration == null ? "No expiry. " : "Lifetime: " + duration / DAY + " day(s). ")
                + "Existing exact denials, revocations and policy blocks still apply.";
        confirm("Trust this author at this exact scope?", details, () -> {
            try {
                signers.trustCurrent(identity, fingerprint, future(duration));
                message = "Scoped signer rule saved. Exact decisions still take precedence.";
            } catch (Exception failure) {
                message = "Signer rule not granted: " + (failure.getMessage() == null ? "review is stale" : failure.getMessage());
            }
        });
    }

    private void untrustSigner(ClientProgramIdentity identity) {
        var selectedSigner = selectedSigner(identity);
        if (signers == null || selectedSigner.isEmpty()) { message = "Select the signer rule to remove first."; return; }
        try {
            for (var grant : signers.snapshot()) {
                if (sameScope(grant, identity) && grant.fingerprint().equals(selectedSigner.orElseThrow())) signers.untrust(grant);
            }
            message = "Signer rule removed. Separate exact-program approvals are unchanged.";
        } catch (Exception failure) {
            service.stopAll();
            message = "Signer removal was not saved. Client programs stopped; retry saving before restarting.";
        }
    }

    private static boolean sameScope(ClientProgramSignerTrustGrant grant, ClientProgramIdentity identity) {
        return grant.world().equals(identity.world()) && grant.dimension().equals(identity.dimension())
                && grant.managerPosition().equals(identity.managerPosition()) && grant.runtime().equals(identity.runtimeRevision())
                && grant.bindingSha256().equals(identity.bindingSha256());
    }

    private void review(ClientProgramIdentity identity, ResourceLocation capability) {
        var opened = service.reopen(identity, capability);
        if (!opened.successful()) { result(opened); return; }
        if (service.gate().state(identity, capability) == ClientProgramConsentGate.ConsentState.APPROVED) {
            message = "Already approved. Revoke before choosing a different lifetime.";
            return;
        }
        Long lifetime = LIFETIMES[lifetimeIndex];
        String scope = capability + " at " + identity.dimension() + " " + identity.managerPosition().toShortString()
                + ". Exact program " + identity.sourceSha256().substring(0, 12)
                + ". " + (lifetime == null ? "No expiry." : "Lifetime: " + lifetime / DAY + " day(s).")
                + " Review full source, bindings and world identity in the panel before approving.";
        confirm("Approve this exact capability?", scope,
                () -> result(service.decide(identity, capability, ClientProgramConsentGate.Decision.APPROVE,
                        future(lifetime), null)));
    }

    private void confirm(String title, String details, Runnable accepted) {
        if (minecraft == null) { message = "A live explicit review panel is required."; return; }
        SFMScreenChangeHelpers.setOrPushScreen(new SFMConfirmationScreen(accepted,
                () -> message = "Review cancelled; no approval granted.", Component.literal(title), Component.literal(details),
                Component.literal("Confirm"), Component.literal("Cancel"), 40));
    }

    private Long future(Long duration) { return duration == null ? null : Math.addExact(service.now(), duration); }
    private void result(ClientProgramConsentService.Result result) { message = result.message(); }
    private static List<ResourceLocation> capabilities(ClientProgramIdentity identity) {
        return identity.requestedCapabilities().stream().sorted().toList();
    }

    private Optional<ClientProgramConsentStore.Snapshot> snapshot() {
        var records = service.store().snapshots();
        var result = records.stream().filter(s -> s.identity().equals(selected)).findFirst();
        if (result.isEmpty() && !records.isEmpty()) {
            result = Optional.of(records.get(records.size() - 1));
            selected = result.orElseThrow().identity();
        }
        return result;
    }

    @Override public void render(PoseStack pose, Minecraft minecraft, SFMScreenPanelBounds bounds,
                                 int mouseX, int mouseY, float partialTick, boolean focused) {
        GuiComponent.fill(pose, bounds.x(), bounds.y(), bounds.x() + bounds.width(), bounds.y() + bounds.height(), 0xF0101520);
        int x = bounds.x() + 6, y = bounds.y() + 6, width = Math.max(20, bounds.width() - 12);
        var record = snapshot();
        String state = service.store().stoppedAll() ? "STOPPED - no program may execute" : "Local decisions; exact source and location";
        minecraft.font.draw(pose, minecraft.font.plainSubstrByWidth(state, width), x, y, 0xFFE8EDF2);
        y += 13;
        int columns = Math.max(2, Math.min(5, width / 90));
        int buttonWidth = Math.max(20, (width - (columns - 1) * 3) / columns);
        controlBounds.clear();
        int index = 0;
        for (Control control : Control.values()) {
            var button = controls.get(control);
            button.setMessage(Component.literal(label(control)));
            var rectangle = new SFMScreenPanelBounds(x + (index % columns) * (buttonWidth + 3), y + (index / columns) * 22,
                    buttonWidth, 20);
            button.setPanelBounds(rectangle);
            button.visible = rectangle.y() + 20 <= bounds.y() + bounds.height();
            button.active = record.isPresent() || control == Control.STOP_ALL || control == Control.RESUME || control == Control.SAVE;
            if (button.visible) controlBounds.put(control, rectangle);
            index++;
        }
        y += ((index + columns - 1) / columns) * 22 + 3;
        List<String> nextLines = new ArrayList<>();
        nextLines.add(message);
        if (!service.diagnostic().isBlank()) nextLines.add("Storage: " + service.diagnostic());
        if (record.isPresent()) {
            var snapshot = record.orElseThrow();
            var identity = snapshot.identity();
            var capabilities = capabilities(identity);
            var capability = capabilities.get(Math.floorMod(capabilityIndex, capabilities.size()));
            var evaluation = service.gate().evaluate(identity, capability, (id, cap) -> runtimeBlockers(id));
            nextLines.add("Capability: " + capability);
            nextLines.add("Consent: " + evaluation.consent() + " / effective: " + evaluation.effective());
            nextLines.add("Authority: " + evaluation.authority());
            selectedSigner(identity).ifPresent(fingerprint -> {
                nextLines.add("Selected signer: " + fingerprint);
                nextLines.add("Signer rule scope: exact world, location, runtime and label bindings.");
                nextLines.add("Capability ceiling: " + capabilities(identity));
                nextLines.add("Trust can authorize future revisions by this author; exact denials still win.");
                if (signers != null) signers.snapshot().stream().filter(grant -> sameScope(grant, identity)
                        && grant.fingerprint().equals(fingerprint)).forEach(grant -> {
                    nextLines.add("Stored ceiling: " + grant.allowedCapabilities().stream().sorted().toList());
                    nextLines.add("Stored expiry: " + (grant.expiresAt() == null ? "none" : java.time.Instant.ofEpochMilli(grant.expiresAt())));
                });
            });
            if (signers != null && !signers.diagnostic().isBlank()) nextLines.add("Signer storage: " + signers.diagnostic());
            if (!evaluation.policyBlockers().isEmpty()) nextLines.add("Blockers: " + evaluation.policyBlockers());
            nextLines.add("Manager: " + identity.dimension() + " " + identity.managerPosition().toShortString());
            nextLines.add("View: " + view + " | scroll to read all text");
            nextLines.addAll(ClientProgramConsentReview.lines(snapshot, ClientProgramConsentReview.previous(service.store(), snapshot), view));
        } else nextLines.add("No programs observed. Approach a labelled Client Manager display in a private world.");
        if (!rawLines.equals(nextLines) || wrappedWidth != width) {
            rawLines = List.copyOf(nextLines);
            wrappedLines = rawLines.stream().flatMap(line -> minecraft.font.split(Component.literal(line), width).stream()).toList();
            wrappedWidth = width;
        }
        visibleLines = Math.max(1, (bounds.y() + bounds.height() - y - 3) / 10);
        scroll = Math.min(scroll, Math.max(0, wrappedLines.size() - visibleLines));
        for (int i = scroll; i < Math.min(wrappedLines.size(), scroll + visibleLines); i++) {
            if (y + 9 <= bounds.y() + bounds.height()) minecraft.font.draw(pose, wrappedLines.get(i), x, y, 0xFFD4DFE8);
            y += 10;
        }
        widgets.render(pose, mouseX, mouseY, partialTick);
    }

    private static List<String> runtimeBlockers(ClientProgramIdentity identity) {
        var minecraft = Minecraft.getInstance();
        var server = minecraft.getSingleplayerServer();
        return server == null || !server.isSingleplayer() || server.isPublished()
                || !identity.world().serverEndpoint().equals("integrated")
                ? List.of("private_integrated_world_required") : List.of();
    }

    @Override public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        scroll = Math.max(0, Math.min(Math.max(0, wrappedLines.size() - visibleLines), scroll - (int) Math.signum(delta) * 3));
        return true;
    }

    private String label(Control control) {
        return switch (control) {
            case PREVIOUS -> "Previous"; case NEXT -> "Next program"; case CAPABILITY -> "Capability";
            case VIEW -> "View: " + view.name().toLowerCase(Locale.ROOT);
            case LIFETIME -> switch (lifetimeIndex) { case 0 -> "For 1 day"; case 1 -> "For 1 year"; default -> "No expiry"; };
            case RETRY -> switch (retryIndex) { case 0 -> "Deny: never ask"; case 1 -> "Deny: 1 minute"; default -> "Deny: 1 day"; };
            case REVIEW -> "Review approval"; case DENY -> "Deny"; case REVOKE -> "Revoke";
            case FORGET -> "Forget program"; case STOP_ALL -> "Stop all"; case RESUME -> "Resume review"; case SAVE -> "Retry save";
            case SIGNER -> "Next signer"; case TRUST_SIGNER -> "Trust signer"; case UNTRUST_SIGNER -> "Remove signer";
            case SIGN_REVIEW -> "Review signing";
        };
    }
}
