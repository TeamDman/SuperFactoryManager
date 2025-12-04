package vswe.superfactory.blocks.client;

import net.minecraft.block.Block;
import net.minecraft.block.BlockAir;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.BlockModelShapes;
import net.minecraft.client.renderer.BlockRendererDispatcher;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.client.model.IModel;
import net.minecraftforge.client.model.ModelLoaderRegistry;
import net.minecraftforge.common.model.IModelState;
import net.minecraftforge.common.property.IExtendedBlockState;
import net.minecraftforge.fml.client.FMLClientHandler;
import org.lwjgl.util.vector.Vector3f;
import vswe.superfactory.blocks.BlockCableCamouflages;
import vswe.superfactory.blocks.BlockCableCluster;
import vswe.superfactory.tiles.TileEntityCamouflage;
import vswe.superfactory.tiles.TileEntityCluster;

import java.util.LinkedList;
import java.util.List;

public class BakedCamouflageBlockModel implements IBakedModel {
	private java.util.function.Function<ResourceLocation, TextureAtlasSprite> bakedTextureGetter;
	private FaceBakery                                                        bakery;
	private TextureAtlasSprite clusterFront;
	private TextureAtlasSprite clusterFrontAdv;
	private TextureAtlasSprite clusterSide;
	private TextureAtlasSprite clusterSideAdv;
	private VertexFormat       format;
	private TextureAtlasSprite insideSprite;
	private IModelState                                                       modelState;
	private TextureAtlasSprite normalSprite;
	private TextureAtlasSprite transformSprite;

	public BakedCamouflageBlockModel(IModelState modelState, VertexFormat format, java.util.function.Function<ResourceLocation, TextureAtlasSprite> bakedTextureGetter, boolean isCamouflage) {
		this.format = format;

		this.bakedTextureGetter = bakedTextureGetter;
		this.modelState = modelState;
		normalSprite = bakedTextureGetter.apply(CamouflageBlockModel.NORMAL);
		insideSprite = bakedTextureGetter.apply(CamouflageBlockModel.INSIDE);
		transformSprite = bakedTextureGetter.apply(CamouflageBlockModel.TRANSFORM);

		if (!isCamouflage) {
			clusterFront = bakedTextureGetter.apply(CamouflageBlockModel.CL_FRONT);
			clusterSide = bakedTextureGetter.apply(CamouflageBlockModel.CL_SIDE);
			clusterFrontAdv = bakedTextureGetter.apply(CamouflageBlockModel.CL_ADV_FRONT);
			clusterSideAdv = bakedTextureGetter.apply(CamouflageBlockModel.CL_ADV_SIDE);
		}

		bakery = new FaceBakery();
	}

	@Override
	public List<BakedQuad> getQuads(IBlockState state, EnumFacing side, long rand) {
		if (state instanceof IExtendedBlockState) {
			IExtendedBlockState blockState = (IExtendedBlockState) state;
			Object              obj        = blockState.getValue(BlockCableCamouflages.BLOCK_POS);

			if (obj != null) {
				BlockPos                pos         = (BlockPos) obj;
				BlockRendererDispatcher dispatcher  = FMLClientHandler.instance().getClient().getBlockRendererDispatcher();
				BlockModelShapes        modelShapes = dispatcher.getBlockModelShapes();

				TileEntity           tileEntity = FMLClientHandler.instance().getWorldClient().getTileEntity(pos);
				TileEntityCamouflage camouflage = null;
				TileEntityCluster    cluster    = null;

				if (tileEntity instanceof TileEntityCluster) {
					cluster = (TileEntityCluster) tileEntity;
					camouflage = TileEntityCluster.getTileEntity(TileEntityCamouflage.class, FMLClientHandler.instance().getWorldClient(), pos);

					BlockCableCluster blockCluster = (BlockCableCluster) cluster.getBlockType();

					if (camouflage == null) {
						IModel clusterModel = null;
						try {
							clusterModel = ModelLoaderRegistry.getModel(BlockCableCluster.isAdvanced(cluster.getBlockMetadata()) ? CamouflageBlockModel.MODEL_CLUSTER_ADV : CamouflageBlockModel.MODEL_CLUSTER);
						} catch (Exception ignored) {
						}

						if (clusterModel != null) {
							return clusterModel.bake(modelState, format, bakedTextureGetter).getQuads(state, side, rand);
						}
					}
				} else if (tileEntity instanceof TileEntityCamouflage) {
					camouflage = (TileEntityCamouflage) tileEntity;
				}
				return new AssembledBakedModel(camouflage, pos, blockState, modelShapes, cluster, format).getQuads(state, side, rand);
			}
		}

		return new AssembledBakedModel().getQuads(state, side, rand);
	}

	//Apparently it needs to be separate because it could be overridden my another thread as rendering is multithreaded
	//https://github.com/TheGreyGhost/MinecraftByExample/blob/master/src/main/java/minecraftbyexample/mbe05_block_smartblockmodel2/CompositeModel.java
	public class AssembledBakedModel implements IBakedModel {
		private VertexFormat    format;
		private List<BakedQuad> quads = new LinkedList<BakedQuad>();

		public AssembledBakedModel(TileEntityCamouflage camouflage, BlockPos pos, IExtendedBlockState blockState, BlockModelShapes modelShapes, TileEntityCluster cluster, VertexFormat format) {
			this.format = format;

			if (camouflage != null && quads.isEmpty()) {

				for (EnumFacing facing : EnumFacing.values()) {
					Block block       = Block.getBlockById(camouflage.getId(facing.getIndex()));
					Block insideBlock = Block.getBlockById(camouflage.getId(facing.getIndex() + EnumFacing.values().length));

					try {
						generateQuads(block, pos, blockState, modelShapes, camouflage, facing, false, cluster);
					} catch (Exception e) {
						e.printStackTrace();
					}
					if (camouflage.getCamouflageType().useDoubleRendering()) {
						generateQuads(insideBlock, pos, blockState, modelShapes, camouflage, facing, true, cluster);
					}
				}
			}
		}

		public void generateQuads(Block block, BlockPos pos, IExtendedBlockState blockState, BlockModelShapes modelShapes, TileEntityCamouflage camouflage, EnumFacing facing, boolean inside, TileEntityCluster cluster) {
			if (block instanceof BlockAir || block instanceof BlockCableCamouflages || block instanceof BlockCableCluster) {
				if (cluster == null) {
					TileEntityCamouflage.CamouflageType camoType = (TileEntityCamouflage.CamouflageType) blockState.getValue(BlockCableCamouflages.CAMO_TYPE);
					quads.add(getTransformedQuad(blockState, pos, blockState.getBlock(), facing, camoType.getIcon(), camoType == TileEntityCamouflage.CamouflageType.NORMAL && !inside ? normalSprite : camoType == TileEntityCamouflage.CamouflageType.INSIDE ? insideSprite : transformSprite, inside, 0));
				} else {
					BlockCableCluster blockCluster  = (BlockCableCluster) cluster.getBlockType();
					int               clusterMeta   = cluster.getBlockMetadata();
					EnumFacing        clusterFacing = blockCluster.getSide(clusterMeta);
					boolean           isFacingFront = ((camouflage != null && camouflage.getCamouflageType().useSpecialShape() && inside) || (camouflage == null && inside)) ? (facing == clusterFacing.getOpposite()) : (facing == clusterFacing);
					boolean           isAdvanced    = BlockCableCluster.isAdvanced(clusterMeta);

					String             resource = (isAdvanced ? (isFacingFront ? CamouflageBlockModel.CL_ADV_FRONT : CamouflageBlockModel.CL_ADV_SIDE) : isFacingFront ? CamouflageBlockModel.CL_FRONT : CamouflageBlockModel.CL_SIDE).toString();
					TextureAtlasSprite texture  = isAdvanced ? (isFacingFront ? clusterFrontAdv : clusterSideAdv) : isFacingFront ? clusterFront : clusterSide;

					quads.add(getTransformedQuad(blockState, pos, blockState.getBlock(), facing, resource, texture, inside, 0));
				}
			} else {
				IBlockState camoState = block.getStateFromMeta(camouflage.getMeta(facing.getIndex() + (inside ? EnumFacing.values().length : 0)));
				IBakedModel model     = modelShapes.getModelForState(camoState);

				List<BakedQuad> bakedQuads   = model.getQuads(camoState, facing, 0);
				List<BakedQuad> reBakedQuads = new LinkedList<BakedQuad>();

				for (BakedQuad quad : bakedQuads) {
					if (quad.getFace() == facing) {
						if (camouflage.getCamouflageType().useDoubleRendering()) {
							quad = reBakeQuadForBlock(quad, blockState, pos, blockState.getBlock(), facing, quad.getSprite(), inside, camouflage.rotate);
						}
						reBakedQuads.add(quad);
					}
				}

				quads.addAll(reBakedQuads);
			}
		}

		private BakedQuad getTransformedQuad(IExtendedBlockState blockState, BlockPos pos, Block block, EnumFacing facing, String resource, TextureAtlasSprite sprite, boolean inside, int rotation) {
			AxisAlignedBB alignedBB = block.getBoundingBox(blockState, FMLClientHandler.instance().getWorldClient(), pos);
			float         maxX      = (((float) alignedBB.maxX) * 16f);
			float         maxY      = (((float) alignedBB.maxY) * 16f);
			float         maxZ      = (((float) alignedBB.maxZ) * 16f);
			float         minX      = (((float) alignedBB.minX) * 16f);
			float         minY      = (((float) alignedBB.minY) * 16f);
			float         minZ      = (((float) alignedBB.minZ) * 16f);

			BlockFaceUV faceUV = null;
			float       f      = 0.002F;

			if (inside) {
				float temp = maxY;
				maxY = 16 - minY;
				minY = 16 - temp;

				temp = maxZ;
				maxZ = 16 - minZ;
				minZ = 16 - temp;
			}

			switch (facing) {
				case DOWN:
					if (!inside) {
						faceUV = new BlockFaceUV(new float[]{16 - minX, 16 - maxZ, 16 - maxX, 16 - minZ}, 0);
					} else {
						faceUV = new BlockFaceUV(new float[]{16 - minX, 16 - minZ, 16 - maxX, 16 - maxZ}, 0);
						maxY = minY + f;
					}
					break;
				case UP:
					if (!inside) {
						faceUV = new BlockFaceUV(new float[]{minX, minZ, maxX, maxZ}, 0);
					} else {
						faceUV = new BlockFaceUV(new float[]{minX, maxZ, maxX, minZ}, 0);
						minY = maxY - f;
					}
					break;
				case NORTH:
					if (!inside) {
						faceUV = new BlockFaceUV(new float[]{16 - maxX, 16 - maxY, 16 - minX, 16 - minY}, 0);
					} else {
						faceUV = new BlockFaceUV(new float[]{16 - minX, maxY, 16 - maxX, minY}, 0);
						maxZ = minZ + f;
					}
					break;
				case SOUTH:
					if (!inside) {
						faceUV = new BlockFaceUV(new float[]{minX, 16 - maxY, maxX, 16 - minY}, 0);
					} else {
						faceUV = new BlockFaceUV(new float[]{maxX, maxY, minX, minY}, 0);
						minZ = maxZ - f;
					}
					break;
				case WEST:
					if (!inside) {
						faceUV = new BlockFaceUV(new float[]{minZ, 16 - maxY, maxZ, 16 - minY}, 0);
					} else {
						faceUV = new BlockFaceUV(new float[]{maxZ, maxY, minZ, minY}, 0);
						maxX = minX + f;
					}
					break;
				case EAST:
					if (!inside) {
						faceUV = new BlockFaceUV(new float[]{16 - maxZ, 16 - maxY, 16 - minZ, 16 - minY}, 0);
					} else {
						faceUV = new BlockFaceUV(new float[]{16 - minZ, maxY, 16 - maxZ, minY}, 0);
						minX = maxX - f;
					}
					break;
			}

			ModelRotation modelRotation = inside ? ModelRotation.getModelRotation(180, rotation * 90) : ModelRotation.getModelRotation(0, rotation * 90);
			return bakery.makeBakedQuad(new Vector3f(minX, minY, minZ), new Vector3f(maxX, maxY, maxZ), new BlockPartFace(facing, -1, resource == null ? "" : resource, faceUV), sprite, inside ? facing.getOpposite() : facing, modelRotation, null, false, true);
		}

		private BakedQuad reBakeQuadForBlock(BakedQuad original, IExtendedBlockState blockState, BlockPos pos, Block block, EnumFacing facing, TextureAtlasSprite sprite, boolean inside, int rotation) {
			BakedQuad transformedQuad     = getTransformedQuad(blockState, pos, block, facing, null, sprite, inside, rotation);
			int[]     transformedFaceData = transformedQuad.getVertexData();

			int        tintIndex = original.getTintIndex();
			EnumFacing face      = original.getFace();
			int[]      faceData  = original.getVertexData().clone();

			for (int i = 0; i < 4; i++) {
				int storeIndex = i * 7;

				float[] d = new float[3];

				for (int j = 0; j < 3; j++) {
					d[j] = (Float.intBitsToFloat(faceData[storeIndex + j]) * 16) - (Float.intBitsToFloat(transformedFaceData[storeIndex + j]) * 16);
				}

				faceData[storeIndex] = transformedFaceData[storeIndex];
				faceData[storeIndex + 1] = transformedFaceData[storeIndex + 1];
				faceData[storeIndex + 2] = transformedFaceData[storeIndex + 2];
				faceData[storeIndex + 4] = transformedFaceData[storeIndex + 4];
				faceData[storeIndex + 5] = transformedFaceData[storeIndex + 5];
			}

			return new BakedQuad(faceData, tintIndex, face, sprite, true, format);
		}

		public AssembledBakedModel() {
		}

		@Override
		public List<BakedQuad> getQuads(IBlockState state, EnumFacing side, long rand) {
			List<BakedQuad> allFaceQuads = new LinkedList<BakedQuad>();

			if (side != null) {
				for (BakedQuad quad : quads) {
					if (FaceBakery.getFacingFromVertexData(quad.getVertexData()) == side) {
						allFaceQuads.add(quad);
					}
				}
			} else {
				return new LinkedList<BakedQuad>(quads);
			}

			return allFaceQuads;
		}

		@Override
		public boolean isAmbientOcclusion() {
			return false;
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
			return normalSprite;
		}

		@Override
		public ItemCameraTransforms getItemCameraTransforms() {
			return ItemCameraTransforms.DEFAULT;
		}

		@Override
		public ItemOverrideList getOverrides() {
			return null;
		}
	}	@Override
	public boolean isAmbientOcclusion() {
		return false;
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
		return normalSprite;
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
