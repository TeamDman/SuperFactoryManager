package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.client.theme.preview.*;
import ca.teamdman.sfm.client.screen.SFMActionChoice;
import ca.teamdman.sfm.common.localization.*;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.builder.*;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.*;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import java.util.Objects;
import java.util.function.*;

/** User-mediated clipboard handoff only: no model, source reads, item enumeration or theme writes. */
public final class SFMItemstackPreviewRulePromptAction implements SFMClientAction<SFMClientActionContext> {
    public static final ResourceLocation ID=new ResourceLocation("sfm:explorer/itemstack_preview_rule/prompt/copy");
    @SFMLocalizationDatagen public static final LocalizationEntry TITLE=new LocalizationEntry("gui.sfm.preview_rule.prompt.copy","Copy prompt for soliciting a new ItemStack preview rule");
    @SFMLocalizationDatagen public static final LocalizationEntry DESCRIPTION=new LocalizationEntry("gui.sfm.preview_rule.prompt.description","Copy captured entry paths/metadata and the available rule grammar; no source contents or item catalogue");
    @SFMLocalizationDatagen public static final LocalizationEntry COPIED=new LocalizationEntry("gui.sfm.preview_rule.prompt.copied","Copied rule-authoring prompt; add your desired icon and scope before sharing");
    private final Consumer<String> clipboard;
    private final Supplier<SFMItemstackPreviewRegistry.Snapshot> registry;
    public SFMItemstackPreviewRulePromptAction() { this(value->Minecraft.getInstance().keyboardHandler.setClipboard(value),SFMItemstackPreviewRegistry::snapshot); }
    public SFMItemstackPreviewRulePromptAction(Consumer<String> clipboard,Supplier<SFMItemstackPreviewRegistry.Snapshot> registry) {
        this.clipboard=Objects.requireNonNull(clipboard);this.registry=Objects.requireNonNull(registry);
    }
    public static SFMActionChoice choice(long id) { return SFMActionChoice.invoke(ID,Long.toString(id),TITLE.getComponent().getString()); }
    @Override public Component title() { return TITLE.getComponent(); }
    @Override public Component description() { return DESCRIPTION.getComponent(); }
    @Override public SFMClientActionRequirement<SFMClientActionContext> requirement() {
        return context->context.originatingHostIsCurrent().getAsBoolean() ? SFMClientActionAvailability.available(context)
                : SFMClientActionAvailability.unavailable(SFMClientActionContext.ORIGINATING_HOST_CHANGED.getComponent());
    }
    @Override public void configureCommandNode(LiteralArgumentBuilder<SFMClientActionSource> node) {
        node.then(RequiredArgumentBuilder.<SFMClientActionSource,Long>argument("icon_capture",LongArgumentType.longArg(1)).executes(this::invoke));
    }
    @Override public int execute(SFMClientActionContext target,CommandContext<SFMClientActionSource> command) throws CommandSyntaxException {
        return copyCapture(LongArgumentType.getLong(command,"icon_capture"),command.getSource()::sendFeedback);
    }
    public int copyCapture(long id,Consumer<Component> feedback) throws CommandSyntaxException {
        try {
            var capture=SFMItemstackPreviewCaptures.find(id).orElseThrow(()->new IllegalArgumentException("Icon capture expired; inspect the entry again"));
            if(!capture.context().originatingHostIsCurrent().getAsBoolean()) throw new IllegalArgumentException("Captured icon host is no longer current");
            String payload=SFMItemstackPreviewRulePrompt.generate(capture.inspection(),registry.get(),SFMItemstackPreviewRuleAction.promptContract());
            clipboard.accept(payload);feedback.accept(COPIED.getComponent());return 1;
        } catch(RuntimeException failure) {
            throw new SimpleCommandExceptionType(Component.literal("Rule prompt not copied: "+failure.getMessage())).create();
        }
    }
}
