package vswe.superfactory.tiles;

import ca.teamdman.sfm.common.registry.SFMBlocks;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import vswe.superfactory.SuperFactoryManager;
import vswe.superfactory.blocks.ClusterMethodRegistration;
import vswe.superfactory.blocks.IRedstoneNode;
import vswe.superfactory.components.ComponentMenuPulse;
import vswe.superfactory.components.ComponentMenuRedstoneOutput;
import vswe.superfactory.components.ComponentMenuRedstoneSidesEmitter;
import vswe.superfactory.network.packets.*;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;

public class TileEntityOutput extends TileEntityClusterElement implements IPacketBlock, IRedstoneNode {
	private static final String NBT_PULSES   = "Pulses";
	private static final String NBT_SIDES    = "Sides";
	private static final String NBT_STRENGTH = "Strength";
	private static final String NBT_STRONG   = "Strong";
	private static final String NBT_TICK     = "Tick";
	private static final int     UPDATE_BUFFER_DISTANCE = 5;
	private              boolean hasUpdatedData;
	private boolean            hasUpdatedThisTick;
	private List<PulseTimer>[] pulseTimers;
	private List<BlockPos>     scheduledToUpdate = new ArrayList<BlockPos>();
	private int[]     strengths;
	private boolean[] strong;
	private int[]     updatedStrength;
	private boolean[] updatedStrong;
	public TileEntityOutput() {
		strengths = new int[EnumFacing.values().length];
		strong = new boolean[EnumFacing.values().length];

		updatedStrength = new int[EnumFacing.values().length];
		updatedStrong = new boolean[EnumFacing.values().length];

		pulseTimers = new List[EnumFacing.values().length];
		for (int i = 0; i < pulseTimers.length; i++) {
			pulseTimers[i] = new ArrayList<PulseTimer>();
		}
	}

	public boolean hasStrongSignalAtSide(EnumFacing side) {
		return strong[side.ordinal()];
	}

	public boolean hasStrongSignalAtOppositeSide(EnumFacing side) {
		return strong[side.getOpposite().ordinal()];
	}

	public int getStrengthFromOppositeSide(EnumFacing side) {
		return getStrengthFromSide(side.getOpposite());
	}

	public int getStrengthFromSide(EnumFacing side) {
		return strengths[side.ordinal()];
	}

	public void updateState(ComponentMenuRedstoneSidesEmitter sides, ComponentMenuRedstoneOutput output, ComponentMenuPulse pulse) {
		boolean updateClient = false;
		for (int i = 0; i < EnumFacing.values().length; i++) {
			if (sides.isSideRequired(i)) {
				int     oldStrength = updatedStrength[i];
				boolean oldStrong   = updatedStrong[i];

				updateSideState(i, output);
				updatedStrong[i] = sides.useStrongSignal();


				//                if (((updatedStrength[i] > 0) != (oldStrength > 0)) || (oldStrong != updatedStrong[i]))
				//                {
				//                    updateClient = true;
				//                }

				boolean updateBlocks = oldStrength != updatedStrength[i] || oldStrong != updatedStrong[i];
				if (updateBlocks) {
					updateClient = true;
				}

				if (updateBlocks) {
					addBlockScheduledForUpdate(EnumFacing.byIndex(i));
				}

				if (pulse.shouldEmitPulse()) {
					PulseTimer       timer  = new PulseTimer(oldStrength, oldStrong, pulse.getPulseTime() + 1); //add one to counter the first tick (which is the same tick as we add it)
					List<PulseTimer> timers = pulseTimers[i];

					if (timers.size() < 200) { //to block a huge amount of pulses at the same time
						switch (pulse.getSelectedPulseOverride()) {
							case EXTEND_OLD:
								if (timers.size() > 0) {
									if (timers.size() > 1) {
										PulseTimer temp = timers.get(0);
										timers.clear();
										timers.add(temp);
									}

									PulseTimer oldTimer = timers.get(0);
									oldTimer.ticks = Math.max(oldTimer.ticks, timer.ticks);
								} else {
									timers.add(timer);
								}
								break;
							case KEEP_ALL:
								timers.add(timer);
								break;
							case KEEP_NEW:
								timers.clear();
								timers.add(timer);
								break;
							case KEEP_OLD:
								if (timers.isEmpty()) {
									timers.add(timer);
								}
						}
					}
				}
			}
		}

		if (updateClient && !isPartOfCluster()) {
			PacketHandler.sendBlockPacket(this, null, 0);
		}
	}

	private void addBlockScheduledForUpdate(EnumFacing side) {
		hasUpdatedThisTick = true;
		int x = getPos().getX() + side.getXOffset();
		int y = getPos().getY() + side.getYOffset();
		int z = getPos().getZ() + side.getZOffset();

		BlockPos coordinate = new BlockPos(x, y, z);
		if (!scheduledToUpdate.contains(coordinate)) {
			scheduledToUpdate.add(coordinate);
		}
	}

	private void updateSideState(int side, ComponentMenuRedstoneOutput output) {
		int strength         = updatedStrength[side];
		int selectedStrength = output.getSelectedStrength();

		switch (output.getSelectedSetting()) {
			case FIXED:
				strength = selectedStrength;
				break;
			case TOGGLE:
				strength = strength > 0 ? 0 : 15;
				break;
			case MAX:
				strength = Math.max(strength, selectedStrength);
				break;
			case MIN:
				strength = Math.min(strength, selectedStrength);
				break;
			case INCREASE:
				strength = Math.min(15, strength + selectedStrength);
				break;
			case DECREASE:
				strength = Math.max(0, strength - selectedStrength);
				break;
			case FORWARD:
				strength = (strength + selectedStrength) % 16;
				break;
			case BACKWARD:
				strength -= selectedStrength;
				if (strength < 0)
					strength += 16;
				break;
		}
		updatedStrength[side] = strength;
	}

	private void notifyUpdate(BlockPos pos, boolean spread) {
		if (world.getBlockState(pos).getBlock() != null) {
			for (EnumFacing enumfacing : EnumFacing.values()) {
				world.notifyNeighborsOfStateChange(pos.offset(enumfacing), world.getBlockState(pos).getBlock(), false);
			}

			world.notifyBlockUpdate(pos, SFMBlocks.CABLE_OUTPUT.getDefaultState(), SFMBlocks.CABLE_OUTPUT.getDefaultState(), 3);

			if (spread) {
				notifyUpdate(pos.add(-1, 0, 0), false);
				notifyUpdate(pos.add(1, 0, 0), false);
				notifyUpdate(pos.add(0, -1, 0), false);
				notifyUpdate(pos.add(0, 1, 0), false);
				notifyUpdate(pos.add(0, 0, -1), false);
				notifyUpdate(pos.add(0, 0, 1), false);
			}
		}
	}

	@Override
	public void writeData(DataWriter dw, EntityPlayer player, boolean onServer, int id) {
		if (onServer) {
			for (int i = 0; i < EnumFacing.values().length; i++) {
				boolean isOn = updatedStrength[i] > 0;
				dw.writeBoolean(isOn);
				if (isOn) {
					dw.writeData(updatedStrength[i], DataBitHelper.MENU_REDSTONE_ANALOG);
					dw.writeBoolean(updatedStrong[i]);
				}
			}
		} else {
			//nothing to write, empty packet
		}
	}

	@Override
	public void readData(DataReader dr, EntityPlayer player, boolean onServer, int id) {
		if (onServer) {
			//respond by sending the data to the client that required it
			PacketHandler.sendBlockPacket(this, player, 0);
		} else {
			for (int i = 0; i < EnumFacing.values().length; i++) {
				boolean isOn = dr.readBoolean();
				if (isOn) {
					strengths[i] = dr.readData(DataBitHelper.MENU_REDSTONE_ANALOG);
					strong[i] = dr.readBoolean();
				} else {
					strengths[i] = 0;
				}
			}
			world.notifyBlockUpdate(getPos(), getWorld().getBlockState(getPos()), getWorld().getBlockState(getPos()), 3);
		}
	}

	@Override
	public int infoBitLength(boolean onServer) {
		return 0; //won't use the id
	}

	@Override
	public void update() {
		if (world.isRemote) {
			keepClientDataUpdated();
		} else {
			updatePulses();

			if (hasUpdatedThisTick) {
				hasUpdatedThisTick = false;
				List<BlockPos> coordinates = new ArrayList<BlockPos>(scheduledToUpdate);
				scheduledToUpdate.clear();
				for (int i = 0; i < strengths.length; i++) {
					strengths[i] = updatedStrength[i];
					strong[i] = updatedStrong[i];
				}
				for (BlockPos coordinate : coordinates) {
					notifyUpdate(coordinate, true);
				}
			}
		}
	}

	@Override
	public void readContentFromNBT(NBTTagCompound nbtTagCompound) {
		int version = nbtTagCompound.getByte(SuperFactoryManager.NBT_PROTOCOL_VERSION);

		NBTTagList sidesTag = nbtTagCompound.getTagList(NBT_SIDES, 10);
		for (int i = 0; i < sidesTag.tagCount(); i++) {

			NBTTagCompound sideTag = sidesTag.getCompoundTagAt(i);

			strengths[i] = updatedStrength[i] = sideTag.getByte(NBT_STRENGTH);
			strong[i] = updatedStrong[i] = sideTag.getBoolean(NBT_STRONG);

			List<PulseTimer> timers = pulseTimers[i];
			timers.clear();
			NBTTagList pulsesTag = sideTag.getTagList(NBT_PULSES, 10);
			for (int j = 0; j < pulsesTag.tagCount(); j++) {
				NBTTagCompound pulseTag = pulsesTag.getCompoundTagAt(j);

				timers.add(new PulseTimer(pulseTag.getByte(NBT_STRENGTH), pulseTag.getBoolean(NBT_STRONG), pulseTag.getShort(NBT_TICK)));
			}
		}
	}

	@Override
	public void writeContentToNBT(NBTTagCompound nbtTagCompound) {
		nbtTagCompound.setByte(SuperFactoryManager.NBT_PROTOCOL_VERSION, SuperFactoryManager.NBT_CURRENT_PROTOCOL_VERSION);

		NBTTagList sidesTag = new NBTTagList();
		for (int i = 0; i < strengths.length; i++) {
			NBTTagCompound sideTag = new NBTTagCompound();

			sideTag.setByte(NBT_STRENGTH, (byte) updatedStrength[i]);
			sideTag.setBoolean(NBT_STRONG, updatedStrong[i]);

			NBTTagList       pulsesTag = new NBTTagList();
			List<PulseTimer> timers    = pulseTimers[i];

			for (PulseTimer timer : timers) {
				NBTTagCompound pulseTag = new NBTTagCompound();
				pulseTag.setByte(NBT_STRENGTH, (byte) timer.strength);
				pulseTag.setBoolean(NBT_STRONG, timer.strong);
				pulseTag.setShort(NBT_TICK, (short) timer.ticks);
				pulsesTag.appendTag(pulseTag);
			}
			sideTag.setTag(NBT_PULSES, pulsesTag);

			sidesTag.appendTag(sideTag);
		}


		nbtTagCompound.setTag(NBT_SIDES, sidesTag);
	}

	@Override
	protected EnumSet<ClusterMethodRegistration> getRegistrations() {
		return EnumSet.of(ClusterMethodRegistration.CAN_CONNECT_REDSTONE, ClusterMethodRegistration.SHOULD_CHECK_WEAK_POWER, ClusterMethodRegistration.IS_PROVIDING_WEAK_POWER, ClusterMethodRegistration.IS_PROVIDING_STRONG_POWER);
	}

	private void updatePulses() {
		boolean updateClient = false;

		for (int i = 0; i < EnumFacing.values().length; i++) {
			Iterator<PulseTimer> iterator = pulseTimers[i].iterator();

			while (iterator.hasNext()) {
				PulseTimer timer = iterator.next();
				timer.ticks--;
				if (timer.ticks == 0) {
					if (updatedStrength[i] != timer.strength || updatedStrong[i] == timer.strong) {
						updatedStrength[i] = timer.strength;
						updatedStrong[i] = timer.strong;
						addBlockScheduledForUpdate(EnumFacing.byIndex(i));
						updateClient = true;
					}
					iterator.remove();
				}
			}
		}

		if (updateClient) {
			PacketHandler.sendBlockPacket(this, null, 0);
		}
	}

	@SideOnly(Side.CLIENT)
	private void keepClientDataUpdated() {
		if (isPartOfCluster()) {
			return;
		}

		double distance = Minecraft.getMinecraft().player.getDistanceSq(getPos().getX() + 0.5, getPos().getY() + 0.5, getPos().getZ() + 0.5);

		if (distance > Math.pow(PacketHandler.BLOCK_UPDATE_RANGE, 2)) {
			hasUpdatedData = false;
		} else if (!hasUpdatedData && distance < Math.pow(PacketHandler.BLOCK_UPDATE_RANGE - UPDATE_BUFFER_DISTANCE, 2)) {
			hasUpdatedData = true;
			PacketHandler.sendBlockPacket(this, Minecraft.getMinecraft().player, 0);
		}
	}

	@Override
	public int[] getPower() {
		return updatedStrength;
	}

	private class PulseTimer {
		private int     strength;
		private boolean strong;
		private int     ticks;

		private PulseTimer(int strength, boolean strong, int ticks) {
			this.strength = strength;
			this.strong = strong;
			this.ticks = ticks;
		}
	}
}
