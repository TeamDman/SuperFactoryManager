package vswe.superfactory.tiles;

import ca.teamdman.sfm.common.blockentity.IFacadeBlockEntity;
import ca.teamdman.sfm.common.facade.FacadeData;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.IStringSerializable;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.jetbrains.annotations.Nullable;
import vswe.superfactory.SuperFactoryManager;
import vswe.superfactory.blocks.BlockCableCamouflages;
import vswe.superfactory.blocks.BlockCamouflageBase;
import vswe.superfactory.blocks.ClusterMethodRegistration;
import vswe.superfactory.components.ComponentMenuCamouflageInside;
import vswe.superfactory.components.ComponentMenuCamouflageShape;
import vswe.superfactory.network.packets.*;

import java.util.EnumSet;
import java.util.Random;

public class TileEntityCamouflage extends TileEntityClusterElement implements IPacketBlock, IFacadeBlockEntity {
    private static final String NBT_COLLISION = "Collision";
    private static final String NBT_FULL = "Full";
    private static final String NBT_ID = "Id";
    private static final String NBT_MAX_X = "MaxX";
    private static final String NBT_MAX_Y = "MaxY";
    private static final String NBT_MAX_Z = "MaxZ";
    private static final String NBT_META = "Meta";
    private static final String NBT_MIN_X = "MinX";
    private static final String NBT_MIN_Y = "MinY";
    private static final String NBT_MIN_Z = "MinZ";
    private static final String NBT_SIDES = "Sides";
    private static final int UPDATE_BUFFER_DISTANCE = 5;
    private static final Random rand = new Random();
    public int rotate = 0;
    private int[] bounds = {0, 32, 0, 32, 0, 32};
    private boolean fullCollision = false;
    private boolean hasClientUpdatedData;
    private int[] ids = new int[EnumFacing.values().length * 2];
    private boolean isServerDirty;
    private int[] metas = new int[EnumFacing.values().length * 2];
    private boolean useCollision = true;

    public boolean isNormalBlock() {
        if (getCamouflageType().useSpecialShape()) {
            if (!useCollision) {
                return false;
            } else {
                for (int i = 0; i < bounds.length; i++) {
                    if (bounds[i] != (i % 2 == 0 ? 0 : 32)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    public CamouflageType getCamouflageType() {
        return CamouflageType.values()[BlockCableCamouflages.getId(getBlockMetadata())];
    }
//	// unused for now
//	@SideOnly(Side.CLIENT)
//	public boolean addBlockEffect(BlockCamouflageBase camoBlock, IBlockState state, World world, EnumFacing sideHit, ParticleManager effectRenderer) {
//		try {
//			if (ids[sideHit.ordinal()] != 0) {
//				Block block = Block.getBlockById(ids[sideHit.ordinal()]);
//				if (block != null) {
//					float         f             = 0.1F;
//					AxisAlignedBB axisalignedbb = state.getBoundingBox(world, getPos());
//					double        x             = (double) getPos().getX() + rand.nextDouble() * (axisalignedbb.maxX - axisalignedbb.minX - (double) (f * 2.0F)) + (double) f + axisalignedbb.minX;
//					double        y             = (double) getPos().getY() + rand.nextDouble() * (axisalignedbb.maxY - axisalignedbb.minY - (double) (f * 2.0F)) + (double) f + axisalignedbb.minY;
//					double        z             = (double) getPos().getZ() + rand.nextDouble() * (axisalignedbb.maxZ - axisalignedbb.minZ - (double) (f * 2.0F)) + (double) f + axisalignedbb.minZ;
//
//					switch (sideHit) {
//						case DOWN:
//							y = (double) getPos().getY() + axisalignedbb.minY - (double) f;
//							break;
//						case UP:
//							y = (double) getPos().getY() + axisalignedbb.maxY + (double) f;
//							break;
//						case NORTH:
//							z = (double) getPos().getZ() + axisalignedbb.minZ - (double) f;
//							break;
//						case SOUTH:
//							z = (double) getPos().getZ() + axisalignedbb.maxZ + (double) f;
//							break;
//						case WEST:
//							x = (double) getPos().getX() + axisalignedbb.minX - (double) f;
//							break;
//						case EAST:
//							x = (double) getPos().getX() + axisalignedbb.maxX + (double) f;
//							break;
//					}
//
//
//					//                    effectRenderer.addEffect((new EntityDiggingFX.Factory().getEntityFX(0, this.worldObj, x, y, z, 0.0D, 0.0D, 0.0D, Block.getIdFromBlock(camoBlock))).multiplyVelocity(0.2F).multipleParticleScaleBy(0.6F));
//					return true;
//				}
//			}
//		} catch (Exception ignored) {
//		}
//
//		return false;
//	}

    public AxisAlignedBB getBlockBounds() {
        return new AxisAlignedBB(
                bounds[0] / 32D,
                bounds[2] / 32D,
                bounds[4] / 32D,
                bounds[1] / 32D,
                bounds[3] / 32D,
                bounds[5] / 32D
        );
    }

    public boolean isUseCollision() {
        return useCollision;
    }

    public boolean isFullCollision() {
        return fullCollision;
    }

    public void setBounds(ComponentMenuCamouflageShape menu) {
        if (getCamouflageType().useSpecialShape() && menu.shouldUpdate()) {
            if (menu.isUseCollision() != useCollision) {
                useCollision = menu.isUseCollision();
                isServerDirty = true;
            }

            if (menu.isFullCollision() != fullCollision) {
                fullCollision = menu.isFullCollision();
                isServerDirty = true;
            }

            for (int i = 0; i < bounds.length; i++) {
                if (bounds[i] != menu.getBounds(i)) {
                    bounds[i] = menu.getBounds(i);
                    isServerDirty = true;
                }
            }

            for (int i = 0; i < bounds.length; i += 2) {
                if (bounds[i] > bounds[i + 1]) {
                    int tmp = bounds[i + 1];
                    bounds[i + 1] = bounds[i];
                    bounds[i] = tmp;
                }
            }
        }
    }

    public void setItem(ItemStack item, int side, ComponentMenuCamouflageInside.InsideSetType type) {
        switch (type) {
            case ONLY_OUTSIDE:
                setItem(item, side);
                break;
            case ONLY_INSIDE:
                setItemForInside(item, side + EnumFacing.values().length);
                break;
            case SAME:
                setItem(item, side);
                setItemForInside(item, side + EnumFacing.values().length);
                break;
            case OPPOSITE:
                setItem(item, side);
                int sidePairInternalId = side % 2;
                int insideSide = side + (sidePairInternalId == 0 ? 1 : -1);
                setItemForInside(item, insideSide + EnumFacing.values().length);
                break;
            default:
        }
    }

    private void setItemForInside(ItemStack item, int side) {
        if (getCamouflageType().useDoubleRendering()) {
            setItem(item, side);
        }
    }

    private void setItem(ItemStack item, int side) {
        int oldId = ids[side];
        int oldMeta = metas[side];

        if (item.isEmpty()) {
            ids[side] = 0;
            metas[side] = 0;
        } else if (item.getItem() != null && item.getItem() instanceof ItemBlock) {
            Block block = ((ItemBlock) item.getItem()).getBlock();
            if (block != null) {
                ids[side] = Block.getIdFromBlock(block);
                metas[side] = item.getItem().getMetadata(item.getItemDamage());
                validateSide(side);
            } else {
                ids[side] = 0;
                metas[side] = 0;
            }
        }

        if (ids[side] != oldId || metas[side] != oldMeta) {
            isServerDirty = true;
        }
    }

    private void validateSide(int i) {
        //        if (ids[i] < 0 || ids[i] >= Block.blockRegistry.getKeys().size()) {
        //            ids[i] = 0;
        //        }
    }

    public int getId(int side) {
        return ids[side];
    }

    public int getMeta(int side) {
        return metas[side];
    }

    @Override
    public void writeData(DataWriter dw, EntityPlayer player, boolean onServer, int id) {
        if (onServer) {
            for (int i = 0; i < getSideCount(); i++) {
                if (ids[i] == 0) {
                    dw.writeBoolean(false);
                } else {
                    dw.writeBoolean(true);
                    dw.writeData(ids[i], DataBitHelper.BLOCK_ID);
                    dw.writeData(metas[i], DataBitHelper.BLOCK_META);
                }
            }
            if (getCamouflageType().useSpecialShape()) {
                dw.writeBoolean(useCollision);
                if (useCollision) {
                    dw.writeBoolean(fullCollision);
                }
                for (int bound : bounds) {
                    //This is done since 0 and 32 are the most common values and the final bit would only be used by 32 anyways
                    //0 -> 01
                    //32 -> 11
                    //1 to 31 ->  bin(bound) << 1

                    if (bound == 0) {
                        dw.writeBoolean(true);
                        dw.writeBoolean(false);
                    } else if (bound == 32) {
                        dw.writeBoolean(true);
                        dw.writeBoolean(true);
                    } else {
                        dw.writeData(bound << 1, DataBitHelper.CAMOUFLAGE_BOUNDS.getBitCount());
                    }
                }
            }
        } else {
            //nothing to write, empty packet
        }
    }

    private int getSideCount() {
        return getCamouflageType().useDoubleRendering() ? ids.length : ids.length / 2;
    }

    @Override
    public void readData(DataReader dr, EntityPlayer player, boolean onServer, int id) {
        if (onServer) {
            //respond by sending the data to the client that required it
            PacketHandler.sendBlockPacket(this, player, 0);
        } else {
            for (int i = 0; i < getSideCount(); i++) {
                if (!dr.readBoolean()) {
                    ids[i] = 0;
                    metas[i] = 0;
                } else {
                    ids[i] = dr.readData(DataBitHelper.BLOCK_ID);
                    metas[i] = dr.readData(DataBitHelper.BLOCK_META);
                    validateSide(i);
                }
            }
            if (getCamouflageType().useSpecialShape()) {
                useCollision = dr.readBoolean();
                if (useCollision) {
                    fullCollision = dr.readBoolean();
                } else {
                    fullCollision = false;
                }

                for (int i = 0; i < bounds.length; i++) {
                    //This is done since 0 and 32 are the most common values and the final bit would only be used by 32 anyways
                    //0 -> 01
                    //32 -> 11
                    //1 to 31 ->  bin(bound) << 1

                    if (dr.readBoolean()) {
                        bounds[i] = dr.readBoolean() ? 32 : 0;
                    } else {
                        bounds[i] = dr.readData(DataBitHelper.CAMOUFLAGE_BOUNDS.getBitCount() - 1);
                    }
                }
            }
            world.notifyBlockUpdate(
                    getPos(),
                    getWorld().getBlockState(getPos()),
                    getWorld().getBlockState(getPos()),
                    3
            );
        }
    }

    @Override
    public int infoBitLength(boolean onServer) {
        return 1;
    }

    @Override
    public void update() {
        if (world.isRemote) {
            keepClientDataUpdated();
        } else {
            if (isServerDirty) {
                isServerDirty = false;
                PacketHandler.sendBlockPacket(this, null, 0);
            }
        }
    }

    @SideOnly(Side.CLIENT)
    protected void keepClientDataUpdated() {
        double distance = Minecraft.getMinecraft().player.getDistanceSq(
                getPos().getX() + 0.5,
                getPos().getY() + 0.5,
                getPos().getZ() + 0.5
        );

        if (distance > Math.pow(PacketHandler.BLOCK_UPDATE_RANGE, 2)) {
            hasClientUpdatedData = false;
        } else if (!hasClientUpdatedData && distance < Math.pow(
                PacketHandler.BLOCK_UPDATE_RANGE - UPDATE_BUFFER_DISTANCE,
                2
        )) {
            hasClientUpdatedData = true;
            PacketHandler.sendBlockPacket(this, Minecraft.getMinecraft().player, 0);
        }
    }

    @Override
    protected void readContentFromNBT(NBTTagCompound tagCompound) {
        NBTTagList list = tagCompound.getTagList(NBT_SIDES, 10);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound element = list.getCompoundTagAt(i);

            ids[i] = element.getShort(NBT_ID);
            metas[i] = element.getByte(NBT_META);
            validateSide(i);
        }

        if (tagCompound.hasKey(NBT_COLLISION)) {
            useCollision = tagCompound.getBoolean(NBT_COLLISION);
            fullCollision = tagCompound.getBoolean(NBT_FULL);

            bounds[0] = tagCompound.getByte(NBT_MIN_X);
            bounds[1] = tagCompound.getByte(NBT_MAX_X);
            bounds[2] = tagCompound.getByte(NBT_MIN_Y);
            bounds[3] = tagCompound.getByte(NBT_MAX_Y);
            bounds[4] = tagCompound.getByte(NBT_MIN_Z);
            bounds[5] = tagCompound.getByte(NBT_MAX_Z);
        }
    }

    @Override
    protected void writeContentToNBT(NBTTagCompound tagCompound) {
        NBTTagList list = new NBTTagList();
        for (int i = 0; i < getSideCount(); i++) {
            NBTTagCompound element = new NBTTagCompound();

            element.setShort(NBT_ID, (short) ids[i]);
            element.setByte(NBT_META, (byte) metas[i]);

            list.appendTag(element);
        }
        tagCompound.setTag(NBT_SIDES, list);

        if (getCamouflageType().useSpecialShape()) {
            tagCompound.setBoolean(NBT_COLLISION, useCollision);
            tagCompound.setBoolean(NBT_FULL, fullCollision);

            tagCompound.setByte(NBT_MIN_X, (byte) bounds[0]);
            tagCompound.setByte(NBT_MAX_X, (byte) bounds[1]);
            tagCompound.setByte(NBT_MIN_Y, (byte) bounds[2]);
            tagCompound.setByte(NBT_MAX_Y, (byte) bounds[3]);
            tagCompound.setByte(NBT_MIN_Z, (byte) bounds[4]);
            tagCompound.setByte(NBT_MAX_Z, (byte) bounds[5]);
        }
    }


    @Override
    protected EnumSet<ClusterMethodRegistration> getRegistrations() {
        return EnumSet.of(ClusterMethodRegistration.ON_BLOCK_PLACED_BY);
    }

    @Override
    public void updateFacadeData(FacadeData newFacadeData) {
        ids[0] = ids[1] = ids[2] = ids[3] = ids[4] = ids[5] = Block.getIdFromBlock(newFacadeData.facadeBlockState().getBlock());
        metas[0] = metas[1] = metas[2] = metas[3] = metas[4] = metas[5]
                = newFacadeData.facadeBlockState().getBlock().getMetaFromState(newFacadeData.facadeBlockState());
        isServerDirty = true;
    }

    @Override
    public @Nullable FacadeData getFacadeData() {
        return null;
    }

    public enum CamouflageType implements IStringSerializable {
        NORMAL("cable_camouflage", "cable_camouflage", false, false),
        INSIDE("cable_camouflage_inside", "cable_camouflage_inside", true, false),
        SHAPE("cable_camouflage_shape", "cable_camouflage_shape", true, true);

        private String icon;
        private String unlocalized;
        private boolean useDouble;
        private boolean useShape;

        CamouflageType(String unlocalized, String icon, boolean useDouble, boolean useShape) {
            this.unlocalized = unlocalized;
            this.icon = icon;
            this.useDouble = useDouble;
            this.useShape = useShape;
        }

        public static CamouflageType getCamouflageType(int id) {
            CamouflageType type = values()[id % values().length];
            return type;
        }

        public String getUnlocalized() {
            return unlocalized;
        }

        public String getIcon() {
            return SuperFactoryManager.MODID + ":block/" + icon;
        }

        public boolean useDoubleRendering() {
            return useDouble;
        }

        public boolean useSpecialShape() {
            return useShape;
        }

        @Override
        public String getName() {
            return icon;
        }

        @Override
        public String toString() {
            return super.toString().toLowerCase();
        }
    }
}
