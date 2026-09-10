package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.client.theme.preview.*;
import ca.teamdman.sfm.client.registry.SFMTextEditors;
import ca.teamdman.sfm.client.text_editor.*;
import ca.teamdman.sfm.client.screen.SFMActionChoice;
import ca.teamdman.sfm.common.localization.*;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.builder.*;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.*;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import java.util.List;

/** Flat semantic aspects of a captured icon. Geometry is not buried in its rendering explanation. */
public final class SFMItemstackPreviewInspectionAction implements SFMClientAction<SFMClientActionContext> {
    @SFMLocalizationDatagen public static final LocalizationEntry RULE=new LocalizationEntry("gui.sfm.preview_rule.explain","Explain matching icon rules");
    @SFMLocalizationDatagen public static final LocalizationEntry RENDER=new LocalizationEntry("gui.sfm.preview_rule.rendering","Explain title-screen ItemStack rendering");
    @SFMLocalizationDatagen public static final LocalizationEntry IDS=new LocalizationEntry("gui.sfm.preview_rule.ids","Copy icon, rule and provider IDs");
    @SFMLocalizationDatagen public static final LocalizationEntry BOUNDS=new LocalizationEntry("gui.sfm.preview_rule.bounds","Inspect icon geometry");
    @SFMLocalizationDatagen public static final LocalizationEntry CACHE=new LocalizationEntry("gui.sfm.preview_rule.cache","Inspect icon cache provenance");
    public enum Kind {
        RULE("explorer/icon/rule/explain",SFMItemstackPreviewInspectionAction.RULE),
        RENDER("explain/itemstack_rendering_in_the_title_screen",SFMItemstackPreviewInspectionAction.RENDER),
        IDS("explorer/icon/ids/copy",SFMItemstackPreviewInspectionAction.IDS),
        BOUNDS("explorer/icon/bounds/show",SFMItemstackPreviewInspectionAction.BOUNDS),
        CACHE("explorer/icon/cache/show",SFMItemstackPreviewInspectionAction.CACHE);
        public final String path; public final LocalizationEntry title;
        Kind(String path,LocalizationEntry title) { this.path=path;this.title=title; }
        public ResourceLocation id() { return new ResourceLocation("sfm",path); }
    }
    private final Kind kind;
    public SFMItemstackPreviewInspectionAction(Kind kind) { this.kind=kind; }
    public static List<SFMActionChoice> choices(long capture) {
        return java.util.Arrays.stream(Kind.values()).map(kind->SFMActionChoice.invoke(kind.id(),Long.toString(capture),kind.title.getComponent().getString())).toList();
    }
    @Override public Component title() { return kind.title.getComponent(); }
    @Override public Component description() { return kind.title.getComponent(); }
    @Override public SFMClientActionRequirement<SFMClientActionContext> requirement() {
        return context->context.originatingHostIsCurrent().getAsBoolean() ? SFMClientActionAvailability.available(context)
                : SFMClientActionAvailability.unavailable(SFMClientActionContext.ORIGINATING_HOST_CHANGED.getComponent());
    }
    @Override public void configureCommandNode(LiteralArgumentBuilder<SFMClientActionSource> node) {
        node.then(RequiredArgumentBuilder.<SFMClientActionSource,Long>argument("icon_capture",LongArgumentType.longArg(1)).executes(this::invoke));
    }
    @Override public int execute(SFMClientActionContext target,CommandContext<SFMClientActionSource> context) throws CommandSyntaxException {
        var capture=SFMItemstackPreviewCaptures.find(LongArgumentType.getLong(context,"icon_capture"))
                .orElseThrow(()->new SimpleCommandExceptionType(Component.literal("Icon capture expired; inspect the entry again")).create());
        String payload=payload(kind,capture.inspection());
        if(kind==Kind.IDS) {
            Minecraft.getInstance().keyboardHandler.setClipboard(payload);
            context.getSource().sendFeedback(Component.literal("Copied icon, rule and provider IDs"));return 1;
        }
        var recipe=new SFMTextEditorPanelRecipe(new ResourceLocation("sfm:text_editor"),SFMTextEditors.V3.getId().orElseThrow().location(),
                new SFMTextDocumentSource.Literal(payload,SFMTextDocumentLanguage.plainText()),true,kind.title.getComponent().getString());
        return OpenPanelAction.openPanel(target,recipe.reopen(),OpenPanelAction.Direction.FOCUSED,recipe);
    }
    public static String payload(Kind kind,SFMItemstackPreviewInspection inspection) {
        return switch(kind) {
            case RULE -> inspection.ruleExplanation(); case RENDER -> inspection.renderingExplanation();
            case IDS -> inspection.idsPayload(); case BOUNDS -> inspection.boundsPayload(); case CACHE -> inspection.cachePayload();
        };
    }
}
