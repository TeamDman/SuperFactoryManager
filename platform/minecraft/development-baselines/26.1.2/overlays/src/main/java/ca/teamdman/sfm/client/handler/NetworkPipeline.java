package ca.teamdman.sfm.client.handler;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.event_bus.SFMSubscribeEvent;
import ca.teamdman.sfm.common.util.SFMDist;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;

import java.util.Optional;

public class NetworkPipeline {
    public static final RenderPipeline NETWORK_PIPELINE = RenderPipelines.DEBUG_FILLED_BOX.toBuilder()
            .withLocation(Identifier.fromNamespaceAndPath(SFM.MOD_ID, "pipeline/network_render"))
            .withDepthStencilState(Optional.empty())
            .build();

    @SFMSubscribeEvent(SFMDist.CLIENT)
    public static void registerPipelines(RegisterRenderPipelinesEvent event) {
        event.registerPipeline(NETWORK_PIPELINE);
    }
}
