package vswe.superfactory.blocks.client;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.property.IExtendedBlockState;
import org.lwjgl.util.vector.Vector3f;
import vswe.superfactory.SuperFactoryManager;
import vswe.superfactory.blocks.BlockCableOutput;

import java.util.LinkedList;
import java.util.List;

enum SideQuad {
	STRONG,
	WEAK,
	IDLE
}

//Needed to render all the sides individually, if they where made with JSON it would be a huge amount of files, the new format is not good for dynamic and advanced blocks
public class BakedEmitterBlockModel implements IBakedModel {

	private VertexFormat       format;
	private BakedQuad[] idleQuads   = new BakedQuad[EnumFacing.values().length];
	private TextureAtlasSprite idleSprite;
	private BakedQuad[] strongQuads = new BakedQuad[EnumFacing.values().length];
	private TextureAtlasSprite strongSprite;
	private BakedQuad[] weakQuads   = new BakedQuad[EnumFacing.values().length];
	private TextureAtlasSprite weakSprite;

	public BakedEmitterBlockModel(java.util.function.Function<ResourceLocation, TextureAtlasSprite> bakedTextureGetter) {
		FaceBakery  faceBakery = new FaceBakery();
		BlockFaceUV bfUV       = new BlockFaceUV(new float[]{0, 0, 16, 16}, 0);
		strongSprite = bakedTextureGetter.apply(EmitterBlockModel.STRONG);
		weakSprite = bakedTextureGetter.apply(EmitterBlockModel.WEAK);
		idleSprite = bakedTextureGetter.apply(EmitterBlockModel.IDLE);

		Vector3f v1 = new Vector3f(0, 0, 0);
		Vector3f v2 = new Vector3f(16, 16, 16);

		for (EnumFacing facing : EnumFacing.values()) {
			strongQuads[facing.getIndex()] = faceBakery.makeBakedQuad(v1, v2, new BlockPartFace(facing, -1, SuperFactoryManager.MODID+":block/cable_output_strong", bfUV), strongSprite, facing, ModelRotation.X0_Y0, null, false, true);
			weakQuads[facing.getIndex()] = faceBakery.makeBakedQuad(v1, v2, new BlockPartFace(facing, -1, SuperFactoryManager.MODID+":block/cable_weak_strong", bfUV), weakSprite, facing, ModelRotation.X0_Y0, null, false, true);
			idleQuads[facing.getIndex()] = faceBakery.makeBakedQuad(v1, v2, new BlockPartFace(facing, -1, SuperFactoryManager.MODID+":block/cable_idle", bfUV), idleSprite, facing, ModelRotation.X0_Y0, null, false, true);
		}
	}

	@Override
	public List<BakedQuad> getQuads(IBlockState state, EnumFacing side, long rand) {
		if (state instanceof IExtendedBlockState) {
			IExtendedBlockState blockState = (IExtendedBlockState) state;
			return new AssembledBakedModel(blockState).getQuads(state, side, rand);
		}

		return new AssembledBakedModel().getQuads(state, side, rand);
	}

	//Apparently it needs to be separate because it could be overridden my another thread as rendering is multithreaded
	//https://github.com/TheGreyGhost/MinecraftByExample/blob/master/src/main/java/minecraftbyexample/mbe05_block_smartblockmodel2/CompositeModel.java
	public class AssembledBakedModel implements IBakedModel {
		private SideQuad[] sideQuads = new SideQuad[EnumFacing.values().length];

		public AssembledBakedModel(IExtendedBlockState blockState) {
			Object obj = blockState.getValue(BlockCableOutput.STRONG_SIDES);

			if (obj != null) {

				int strongVals = blockState.getValue(BlockCableOutput.STRONG_SIDES);
				int weakVals   = blockState.getValue(BlockCableOutput.WEAK_SIDES);

				for (EnumFacing facing : EnumFacing.values()) {
					if ((strongVals & (1 << facing.getIndex())) >> facing.getIndex() == 1) {
						sideQuads[facing.getIndex()] = SideQuad.STRONG;
					} else if ((weakVals & (1 << facing.getIndex())) >> facing.getIndex() == 1) {
						sideQuads[facing.getIndex()] = SideQuad.WEAK;
					} else {
						sideQuads[facing.getIndex()] = SideQuad.IDLE;
					}
				}
			} else {
				for (int i = 0; i < 6; i++) {
					sideQuads[i] = SideQuad.IDLE;
				}
			}
		}

		public AssembledBakedModel() {
			for (int i = 0; i < 6; i++) {
				sideQuads[i] = SideQuad.IDLE;
			}
		}

		private BakedQuad getQuadFromSide(SideQuad sideQuad, EnumFacing facing) {
			switch (sideQuad) {
				case STRONG:
					return strongQuads[facing.getIndex()];
				case WEAK:
					return weakQuads[facing.getIndex()];
				case IDLE:
				default:
					return idleQuads[facing.getIndex()];
			}
		}

		/*
				@Override
				public List getFaceQuads(EnumFacing side) {
					List<BakedQuad> allFaceQuads = new LinkedList<BakedQuad>();

					allFaceQuads.add(getQuadFromSide(sideQuads[side.getIndex()], side));

					return allFaceQuads;
				}

				@Override
				public List getGeneralQuads() {
					List<BakedQuad> allQuads = new LinkedList<BakedQuad>();

					for (EnumFacing facing: EnumFacing.values()) {
						allQuads.add(getQuadFromSide(sideQuads[facing.getIndex()], facing));
					}

					return allQuads;
				}
		*/
		@Override
		public List<BakedQuad> getQuads(IBlockState state, EnumFacing side, long rand) {
			List<BakedQuad> allFaceQuads = new LinkedList<BakedQuad>();

			if (side != null) {
				allFaceQuads.add(getQuadFromSide(sideQuads[side.getIndex()], side));
			} else {
				for (EnumFacing facing : EnumFacing.values()) {
					allFaceQuads.add(getQuadFromSide(sideQuads[facing.getIndex()], facing));
				}
			}

			return allFaceQuads;
		}

		@Override
		public boolean isAmbientOcclusion() {
			return true;
		}

		@Override
		public boolean isGui3d() {
			return false;
		}

		@Override
		public boolean isBuiltInRenderer() {
			return false;
		}

		@Override
		public TextureAtlasSprite getParticleTexture() {
			return idleSprite;
		}

		@Override
		public ItemCameraTransforms getItemCameraTransforms() {
			return ItemCameraTransforms.DEFAULT;
		}

		@Override
		public ItemOverrideList getOverrides() {
			return ItemOverrideList.NONE;
		}
	}	@Override
	public boolean isAmbientOcclusion() {
		return true;
	}

	@Override
	public boolean isGui3d() {
		return false;
	}

	@Override
	public boolean isBuiltInRenderer() {
		return false;
	}

	@Override
	public TextureAtlasSprite getParticleTexture() {
		return idleSprite;
	}

	@Override
	public ItemCameraTransforms getItemCameraTransforms() {
		return ItemCameraTransforms.DEFAULT;
	}

	@Override
	public ItemOverrideList getOverrides() {
		return ItemOverrideList.NONE;
	}


}
