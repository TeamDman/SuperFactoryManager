/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package ca.teamdman.sfm.common.flow.data;

import ca.teamdman.sfm.SFMUtil;
import ca.teamdman.sfm.client.gui.flow.core.FlowComponent;
import ca.teamdman.sfm.client.gui.flow.impl.manager.core.ManagerFlowController;
import ca.teamdman.sfm.client.gui.flow.impl.manager.flowdataholder.ItemStackTileEntityRuleFlowComponent;
import ca.teamdman.sfm.common.flow.core.Position;
import ca.teamdman.sfm.common.flow.core.PositionHolder;
import ca.teamdman.sfm.common.registrar.FlowDataSerializerRegistrar.FlowDataSerializers;
import java.util.List;
import java.util.UUID;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.ResourceLocation;

public class ItemStackTileEntityRuleFlowData extends FlowData implements
	PositionHolder {

	public FilterMode filterMode;
	public String name;
	public ItemStack icon;
	public Position position;
	public List<UUID> matcherIds;

	public ItemStackTileEntityRuleFlowData(

		UUID uuid,
		String name,
		ItemStack icon,
		Position position,
		FilterMode filterMode,
		List<UUID> matcherIds
	) {
		super(uuid);
		this.name = name;
		this.icon = icon;
		this.position = position;
		this.filterMode = filterMode;
		this.matcherIds = matcherIds;
	}

	public ItemStack getIcon() {
		return icon;
	}

	@Override
	public Position getPosition() {
		return position;
	}

	@Override
	public FlowComponent createController(
		FlowComponent parent
	) {
		if (!(parent instanceof ManagerFlowController)) {
			return null;
		}
		return new ItemStackTileEntityRuleFlowComponent((ManagerFlowController) parent, this);
	}

	@Override
	public FlowDataSerializer getSerializer() {
		return FlowDataSerializers.TILE_ENTITY_RULE;
	}


	public enum FilterMode {
		WHITELIST,
		BLACKLIST
	}

	public static class FlowTileEntityRuleDataSerializer extends
		FlowDataSerializer<ItemStackTileEntityRuleFlowData> {

		public FlowTileEntityRuleDataSerializer(ResourceLocation registryName) {
			super(registryName);
		}

		@Override
		public ItemStackTileEntityRuleFlowData fromNBT(CompoundNBT tag) {
			return new ItemStackTileEntityRuleFlowData(
				UUID.fromString(tag.getString("uuid")),
				tag.getString("name"),
				ItemStack.read(tag.getCompound("icon")),
				new Position(tag.getCompound("pos")),
				FilterMode.valueOf(tag.getString("filterMode")),
				SFMUtil.deserializeUUIDList(tag, "matchers")
			);
		}

		@Override
		public CompoundNBT toNBT(ItemStackTileEntityRuleFlowData data) {
			CompoundNBT tag = super.toNBT(data);
			tag.put("pos", data.position.serializeNBT());
			tag.putString("name", data.name);
			tag.put("icon", data.icon.serializeNBT());
			tag.putString("filterMode", data.filterMode.name());
			tag.put("matchers", SFMUtil.serializeUUIDList(data.matcherIds));
			return tag;
		}

		@Override
		public ItemStackTileEntityRuleFlowData fromBuffer(PacketBuffer buf) {
			return new ItemStackTileEntityRuleFlowData(
				SFMUtil.readUUID(buf),
				buf.readString(),
				buf.readItemStack(),
				Position.fromLong(buf.readLong()),
				FilterMode.valueOf(buf.readString()),
				SFMUtil.deserializeUUIDList(buf)
			);
		}

		@Override
		public void toBuffer(ItemStackTileEntityRuleFlowData data, PacketBuffer buf) {
			buf.writeString(data.getId().toString());
			buf.writeString(data.name);
			buf.writeItemStack(data.icon);
			buf.writeLong(data.position.toLong());
			buf.writeString(data.filterMode.name());
			SFMUtil.serializeUUIDList(data.matcherIds, buf);
		}
	}
}