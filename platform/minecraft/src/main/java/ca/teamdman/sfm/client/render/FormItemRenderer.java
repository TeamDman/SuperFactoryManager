package ca.teamdman.sfm.client.render;

import ca.teamdman.sfm.client.registry.SFMKeyMappings;
import ca.teamdman.sfm.common.item.FormItem;
import ca.teamdman.sfm.common.util.SFMResourceLocation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.serialization.MapCodec;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Vector3fc;

import java.util.function.Consumer;

public class FormItemRenderer implements SpecialModelRenderer<FormItemRenderer.Data> {

    private static final Identifier BASE_MODEL_ID = SFMResourceLocation.fromSFMPath("item/form_base");
    private final ItemModelResolver itemModelResolver;

    public FormItemRenderer(ItemModelResolver itemModelResolver) {
        this.itemModelResolver = itemModelResolver;
    }

    @Override
    public void submit(
            Data data,
            PoseStack poseStack,
            SubmitNodeCollector submitNodeCollector,
            int lightCoords,
            int overlayCoords,
            boolean hasFoil,
            int outlineColor
    ) {
        poseStack.pushPose();

        if (data.showReference && !data.referenceState.isEmpty()) {
            poseStack.pushPose();
            poseStack.translate(0, 0.5f, 0.3f);
            poseStack.scale(0.5f, 0.5f, 0.5f);

            data.baseState.submit(poseStack, submitNodeCollector, lightCoords, overlayCoords, outlineColor);
            poseStack.popPose();

            data.referenceState.submit(poseStack, submitNodeCollector, lightCoords, overlayCoords, outlineColor);
        } else {
            data.baseState.submit(poseStack, submitNodeCollector, lightCoords, overlayCoords, outlineColor);
        }

        poseStack.popPose();
    }

    @Override
    public void getExtents(Consumer<Vector3fc> output) {
        // Base model extents could be retrieved here if we had a persistent baseState
    }

    @Override
    public Data extractArgument(ItemStack stack) {
        if (!(stack.getItem() instanceof FormItem)) return null;

        ItemStack reference = FormItem.getBorrowedReferenceFromForm(stack);
        ItemStackRenderState referenceState = new ItemStackRenderState();
        if (!reference.isEmpty()) {
            this.itemModelResolver.updateForTopItem(referenceState, reference, ItemDisplayContext.NONE, null, null, 0);
        }

        ItemStackRenderState baseState = new ItemStackRenderState();
        ItemStack baseStack = stack.copy();
        baseStack.set(DataComponents.ITEM_MODEL, BASE_MODEL_ID);
        this.itemModelResolver.updateForTopItem(baseState, baseStack, ItemDisplayContext.NONE, null, null, 0);

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
}
