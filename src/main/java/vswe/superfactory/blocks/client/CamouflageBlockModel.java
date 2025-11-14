package vswe.superfactory.blocks.client;

import com.google.common.collect.ImmutableList;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.model.IModel;
import net.minecraftforge.common.model.IModelState;
import vswe.superfactory.SuperFactoryManager;

import java.util.Collection;

public class CamouflageBlockModel implements IModel {
	public static final ResourceLocation CL_ADV_FRONT = new ResourceLocation(SuperFactoryManager.MODID+":block/cable_cluster_adv_front");
	public static final ResourceLocation CL_ADV_SIDE  = new ResourceLocation(SuperFactoryManager.MODID+":block/cable_cluster_adv");
	public static final ResourceLocation CL_FRONT     = new ResourceLocation(SuperFactoryManager.MODID+":block/cable_cluster_front");
	public static final ResourceLocation CL_SIDE      = new ResourceLocation(SuperFactoryManager.MODID+":block/cable_cluster_front");
	public static final ResourceLocation INSIDE    = new ResourceLocation(SuperFactoryManager.MODID+":block/cable_camo_inside");
	public static final ResourceLocation MODEL             = new ResourceLocation(SuperFactoryManager.MODID+":block/cable_camouflage");
	public static final ResourceLocation MODEL_CLUSTER     = new ResourceLocation(SuperFactoryManager.MODID+":block/cable_cluster");
	public static final ResourceLocation MODEL_CLUSTER_ADV = new ResourceLocation(SuperFactoryManager.MODID+":block/cable_cluster_advanced");
	public static final ResourceLocation NORMAL    = new ResourceLocation(SuperFactoryManager.MODID+":block/cable_camo");
	public static final ResourceLocation TRANSFORM = new ResourceLocation(SuperFactoryManager.MODID+":block/cable_camo_shape");
	private boolean isCamouflage;

	public CamouflageBlockModel(IResourceManager resourceManager, boolean isCamouflage) {
		this.isCamouflage = isCamouflage;
	}

	@Override
	public Collection<ResourceLocation> getDependencies() {
		return isCamouflage ? ImmutableList.copyOf(new ResourceLocation[]{MODEL}) : ImmutableList.copyOf(new ResourceLocation[]{MODEL, MODEL_CLUSTER, MODEL_CLUSTER_ADV});
	}

	@Override
	public Collection<ResourceLocation> getTextures() {
		return isCamouflage ? ImmutableList.copyOf(new ResourceLocation[]{NORMAL, INSIDE, TRANSFORM}) : ImmutableList.copyOf(new ResourceLocation[]{NORMAL, INSIDE, TRANSFORM, CL_SIDE, CL_FRONT, CL_ADV_SIDE, CL_ADV_FRONT});
	}

	@Override
	public IBakedModel bake(IModelState state, VertexFormat format, java.util.function.Function<ResourceLocation, TextureAtlasSprite> bakedTextureGetter) {
		return new BakedCamouflageBlockModel(state, format, bakedTextureGetter, isCamouflage);
	}

//	@Override
//	public IModelState getDefaultState() {
//		return null;
//	}
}
