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

public class EmitterBlockModel implements IModel {
	public static final ResourceLocation EMITTER_MODEL = new ResourceLocation(SuperFactoryManager.MODID+":block/cable_emitter");
	public static final ResourceLocation IDLE   = new ResourceLocation(SuperFactoryManager.MODID+":block/cable_idle");
	public static final ResourceLocation STRONG = new ResourceLocation(SuperFactoryManager.MODID+":block/cable_output_strong");
	public static final ResourceLocation WEAK   = new ResourceLocation(SuperFactoryManager.MODID+":block/cable_output_weak");
	public EmitterBlockModel(IResourceManager resourceManager) {
	}

	@Override
	public Collection<ResourceLocation> getDependencies() {
		return ImmutableList.copyOf(new ResourceLocation[]{EMITTER_MODEL});
	}

	@Override
	public Collection<ResourceLocation> getTextures() {
		return ImmutableList.copyOf(new ResourceLocation[]{STRONG, WEAK, IDLE});
	}

	@Override
	public IBakedModel bake(IModelState state, VertexFormat format, java.util.function.Function<ResourceLocation, TextureAtlasSprite> bakedTextureGetter) {
		return new BakedEmitterBlockModel(bakedTextureGetter);
	}

	@Override
	public IModelState getDefaultState() {
		return null;
	}
}
