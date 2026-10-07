package ca.teamdman.sfm.client.render;

{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% if features.tooltip_mode_override %}
import ca.teamdman.sfm.client.tooltip.SFMTooltipModeService;
{% else %}
import ca.teamdman.sfm.client.registry.SFMKeyMappings;
{% endif %}
import ca.teamdman.sfm.common.event_bus.SFMSubscribeEvent;
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
import ca.teamdman.sfm.client.registry.SFMKeyMappings;
import ca.teamdman.sfm.common.event_bus.SFMSubscribeEvent;
{% else %}
import ca.teamdman.sfm.client.registry.SFMKeyMappings;
{% endcase %}
import ca.teamdman.sfm.common.item.FormItem;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
import ca.teamdman.sfm.common.util.SFMDist;
{% else %}
{% endcase %}
import ca.teamdman.sfm.common.util.SFMResourceLocation;
import com.mojang.blaze3d.vertex.PoseStack;
{% case minecraft_version %}
{% when "1.19.2" %}
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.resources.ResourceLocation;
{% when "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4" %}
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
{% when "1.21", "1.21.1" %}
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
{% else %}
import com.mojang.serialization.MapCodec;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemDisplayContext;
{% endcase %}
import net.minecraft.world.item.ItemStack;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1" %}
import net.minecraftforge.client.event.ModelEvent;
{% when "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
import net.neoforged.neoforge.client.event.ModelEvent;
{% else %}
import org.joml.Vector3fc;
{% endcase %}

{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
public class FormItemRenderer extends BlockEntityWithoutLevelRenderer {
{% else %}
import java.util.function.Consumer;
{% endcase %}

{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4" %}
    private static final ResourceLocation BASE_MODEL = SFMResourceLocation.fromSFMPath("item/form_base");
{% when "1.21", "1.21.1" %}
    private static final ModelResourceLocation BASE_MODEL = ModelResourceLocation.standalone(SFMResourceLocation.fromSFMPath("item/form_base"));
{% else %}
public class FormItemRenderer implements SpecialModelRenderer<FormItemRenderer.Data> {
{% endcase %}

{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
    public FormItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }
{% else %}
    public static final Identifier BASE_MODEL_ID = SFMResourceLocation.fromSFMPath("form_base");
    private final ItemModelResolver itemModelResolver;
{% endcase %}

{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
    @SFMSubscribeEvent(value = SFMDist.CLIENT)
    public static void registerModels(ModelEvent.RegisterAdditional event) {
        event.register(BASE_MODEL);
{% else %}
    public FormItemRenderer(ItemModelResolver itemModelResolver) {
        this.itemModelResolver = itemModelResolver;
{% endcase %}
    }

{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
    // Thanks Shadows
    // https://github.com/Shadows-of-Fire/Hostile-Neural-Networks/blob/1.18/src/main/java/shadows/hostilenetworks/client/DataModelItemStackRenderer.java#L71
    // https://discord.com/channels/313125603924639766/915304642668290119/1029330876208795758
{% else %}
{% endcase %}
    @Override
{% case minecraft_version %}
{% when "1.19.2" %}
    public void renderByItem(
            ItemStack stack,
            @MCVersionDependentBehaviour ItemTransforms.TransformType transformType,
{% when "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
    public void renderByItem(
            ItemStack stack,
            @MCVersionDependentBehaviour ItemDisplayContext transformType,
{% else %}
    public void submit(
            Data data,
{% endcase %}
            PoseStack poseStack,
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
            MultiBufferSource multiBuffer,
            int packedLight,
            int packedOverlay
{% else %}
            SubmitNodeCollector submitNodeCollector,
            int lightCoords,
            int overlayCoords,
            boolean hasFoil,
            int outlineColor
{% endcase %}
    ) {
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
        if (!(stack.getItem() instanceof FormItem)) return;
        var renderer = Minecraft.getInstance().getItemRenderer();
        var baseModel = renderer.getItemModelShaper().getModelManager().getModel(BASE_MODEL);
        @SuppressWarnings("deprecation")
        var renderType = ItemBlockRenderTypes.getRenderType(stack, true);
        var buffer = ItemRenderer.getFoilBufferDirect(multiBuffer, renderType, true, stack.hasFoil());
{% else %}
{% endcase %}
        poseStack.pushPose();
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
{% else %}
        poseStack.translate(0.5f, 0.5f, 0.5f);
{% endcase %}

{% case minecraft_version %}
{% when "1.19.2" %}
        if (transformType != ItemTransforms.TransformType.FIXED && transformType != ItemTransforms.TransformType.GUI) {
            poseStack.scale(0.5F, 0.5F, 1F);
            poseStack.translate(0.5, 0.5, 0);
//            poseStack.mulPose(Vector3f.YP.rotationDegrees(-65));
        }
{% when "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
        if (transformType != ItemDisplayContext.FIXED && transformType != ItemDisplayContext.GUI) {
            poseStack.scale(0.5F, 0.5F, 1F);
            poseStack.translate(0.5, 0.5, 0);
//            poseStack.mulPose(Vector3f.YP.rotationDegrees(-65));
        }
{% else %}
        if (data.showReference && !data.referenceState.isEmpty()) {
            data.referenceState.submit(poseStack, submitNodeCollector, lightCoords, overlayCoords, outlineColor);
{% endcase %}

{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% if features.tooltip_mode_override %}
        if (SFMTooltipModeService.INSTANCE.isExpanded()) {
{% else %}
        if (SFMKeyMappings.isKeyDown(SFMKeyMappings.MORE_INFO_TOOLTIP_KEY)) {
{% endif %}
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
        if (SFMKeyMappings.isKeyDown(SFMKeyMappings.MORE_INFO_TOOLTIP_KEY)) {
{% else %}
{% endcase %}
            poseStack.pushPose();
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
            poseStack.translate(0, 0.5f, 0.3f);
            poseStack.scale(0.5f, 0.5f, 0.5f);
            renderer.renderModelLists(baseModel, stack, packedLight, packedOverlay, poseStack, buffer);
{% else %}

            poseStack.translate(-0.2f, 0.2f, 0.05f);
            poseStack.scale(0.6f, 0.6f, 0f);

            data.baseState.submit(poseStack, submitNodeCollector, lightCoords, overlayCoords, outlineColor);
{% endcase %}
            poseStack.popPose();

{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
            var reference = FormItem.getBorrowedReferenceFromForm(stack);
            if (!reference.isEmpty()) {
                var model = renderer.getItemModelShaper().getItemModel(reference.getItem());
                if (model != null) {
                    renderer.renderModelLists(model, stack, packedLight, packedOverlay, poseStack, buffer);
                }
            }
{% else %}
{% endcase %}
        } else {
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
            renderer.renderModelLists(baseModel, stack, packedLight, packedOverlay, poseStack, buffer);
{% else %}
            data.baseState.submit(poseStack, submitNodeCollector, lightCoords, overlayCoords, outlineColor);
{% endcase %}
        }

        poseStack.popPose();
    }
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
{% else %}

    @Override
    public void getExtents(Consumer<Vector3fc> output) {

    }

    @Override
    public Data extractArgument(ItemStack stack) {
        ItemStack reference = FormItem.getBorrowedReferenceFromForm(stack);
        ItemStackRenderState referenceState = new ItemStackRenderState();
        if (!reference.isEmpty()) {
            this.itemModelResolver.updateForTopItem(
                    referenceState, reference, ItemDisplayContext.GUI, null, null, 0
            );
        }

        ItemStackRenderState baseState = new ItemStackRenderState();
        // Use a plain stack with the correct ITEM_MODEL pointing to your base model
        ItemStack baseStack = stack.copy();
        baseStack.set(DataComponents.ITEM_MODEL, BASE_MODEL_ID);
        this.itemModelResolver.updateForTopItem(
                baseState, baseStack, ItemDisplayContext.GUI, null, null, 0
        );

        return new Data(
                referenceState,
                baseState,
                SFMKeyMappings.isKeyDown(SFMKeyMappings.MORE_INFO_TOOLTIP_KEY)
        );
    }

    public record Data(ItemStackRenderState referenceState, ItemStackRenderState baseState, boolean showReference) {}

    public record Unbaked() implements SpecialModelRenderer.Unbaked<Data> {

        public static final MapCodec<Unbaked> MAP_CODEC = MapCodec.unit(new Unbaked());

        @Override
        public MapCodec<Unbaked> type() {
            return MAP_CODEC;
        }

        @Override
        public SpecialModelRenderer<Data> bake(SpecialModelRenderer.BakingContext ctx) {
            return new FormItemRenderer(net.minecraft.client.Minecraft.getInstance().getItemModelResolver());
        }
    }
{% endcase %}
}
