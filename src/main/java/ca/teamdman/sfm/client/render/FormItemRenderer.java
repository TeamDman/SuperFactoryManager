package ca.teamdman.sfm.client.render;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.client.ClientStuff;
import ca.teamdman.sfm.common.item.FormItem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ModelEvent;

@EventBusSubscriber(bus = EventBusSubscriber.Bus.MOD, modid = SFM.MOD_ID, value = Dist.CLIENT)
public class FormItemRenderer extends BlockEntityWithoutLevelRenderer {

    private static final ModelResourceLocation BASE_MODEL = ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath(SFM.MOD_ID, "item/form_base"));

    public FormItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    @SubscribeEvent
    public static void registerModels(ModelEvent.RegisterAdditional event) {
        event.register(BASE_MODEL);
    }

    // Thanks Shadows
    // https://github.com/Shadows-of-Fire/Hostile-Neural-Networks/blob/1.18/src/main/java/shadows/hostilenetworks/client/DataModelItemStackRenderer.java#L71
    // https://discord.com/channels/313125603924639766/915304642668290119/1029330876208795758
    @Override
    public void renderByItem(
            ItemStack stack,
            ItemDisplayContext transformType,
            PoseStack poseStack,
            MultiBufferSource multiBuffer,
            int packedLight,
            int packedOverlay
    ) {
        if (!(stack.getItem() instanceof FormItem)) return;
        var renderer = Minecraft.getInstance().getItemRenderer();
        var baseModel = renderer.getItemModelShaper().getModelManager().getModel(BASE_MODEL);
        @SuppressWarnings("deprecation")
        var renderType = ItemBlockRenderTypes.getRenderType(stack, true);
        var buffer = ItemRenderer.getFoilBufferDirect(multiBuffer, renderType, true, stack.hasFoil());
        poseStack.pushPose();

        if (transformType != ItemDisplayContext.FIXED && transformType != ItemDisplayContext.GUI) {
            poseStack.scale(0.5F, 0.5F, 1F);
            poseStack.translate(0.5, 0.5, 0);
//            poseStack.mulPose(Vector3f.YP.rotationDegrees(-65));
        }

        if (ClientStuff.isMoreInfoKeyDown()) {
            poseStack.pushPose();
            poseStack.translate(0, 0.5f, 0.3f);
            poseStack.scale(0.5f, 0.5f, 0.5f);
            renderer.renderModelLists(baseModel, stack, packedLight, packedOverlay, poseStack, buffer);
            poseStack.popPose();

            var reference = FormItem.getReference(stack);
            if (!reference.isEmpty()) {
                var model = renderer.getItemModelShaper().getItemModel(reference.getItem());
                if (model != null) {
                    renderer.renderModelLists(model, stack, packedLight, packedOverlay, poseStack, buffer);
                }
            }
        } else {
            renderer.renderModelLists(baseModel, stack, packedLight, packedOverlay, poseStack, buffer);
        }

        poseStack.popPose();
    }
}
