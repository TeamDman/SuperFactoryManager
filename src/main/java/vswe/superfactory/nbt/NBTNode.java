package vswe.superfactory.nbt;

import net.minecraft.nbt.*;

import java.util.ArrayList;
import java.util.List;

public class NBTNode {
	private static final int BYTE_ARRAY_TAG = 7;
	private static final int BYTE_TAG       = 1;
	private static final int COMPOUND_TAG   = 10;
	private static final int DOUBLE_TAG     = 6;
	private static final int END_TAG        = 0;
	private static final int FLOAT_TAG      = 5;
	private static final int INT_ARRAY_TAG  = 11;
	private static final int INT_TAG        = 3;
	private static final int LIST_TAG       = 9;
	private static final int LONG_TAG       = 4;
	private static final int SHORT_TAG      = 2;
	private static final int STRING_TAG     = 8;
	private int           cachedDepth;
	private int           cachedLine;
	private List<NBTNode> nodes;
	private boolean       open;
	private NBTBase       tag;
	private String        value;

	public NBTNode(NBTBase tag) {
		this.tag = tag;
		open = true;
	}

	public static NBTNode generateNodes(NBTTagCompound compound) {
		NBTNode node = generateNodesFromTag(compound);
		node.updatePosition();
		return node;
	}

	private static NBTNode generateNodesFromTag(NBTTagCompound compound) {
		NBTNode node = new NBTNode(compound);
		node.nodes = new ArrayList<NBTNode>();
		for (Object obj : compound.getKeySet()) {
			NBTBase tag = (NBTBase) obj;

			if (tag.getId() == END_TAG)
				break;

			node.nodes.add(createElementNode(tag));
		}

		return node;
	}

	private static NBTNode generateNodesFromList(NBTTagList compound) {
		NBTNode node = new NBTNode(compound);
		node.nodes = new ArrayList<NBTNode>();
		for (int i = 0; i < compound.tagCount(); i++) {
			node.nodes.add(createElementNode(compound.getCompoundTagAt(i)));
		}
		return node;
	}

	private static NBTNode generateNodesFromArray(NBTTagByteArray compound) {
		NBTNode node = new NBTNode(compound);
		node.nodes = new ArrayList<NBTNode>();
		for (byte b : compound.getByteArray()) {
			NBTNode child = new NBTNode(null);
			child.value = String.valueOf(b);
			node.nodes.add(child);
		}
		return node;
	}

	private static NBTNode generateNodesFromArray(NBTTagIntArray compound) {
		NBTNode node = new NBTNode(compound);
		node.nodes = new ArrayList<NBTNode>();
		for (int n : compound.getIntArray()) {
			NBTNode child = new NBTNode(null);
			child.value = String.valueOf(n);
			node.nodes.add(child);
		}
		return node;
	}

	private static NBTNode createElementNode(NBTBase tag) {
		switch (tag.getId()) {
			case COMPOUND_TAG:
				return generateNodesFromTag((NBTTagCompound) tag);
			case LIST_TAG:
				return generateNodesFromList((NBTTagList) tag);
			case BYTE_ARRAY_TAG:
				return generateNodesFromArray((NBTTagByteArray) tag);
			case INT_ARRAY_TAG:
				return generateNodesFromArray((NBTTagIntArray) tag);
			default:
				NBTNode node = new NBTNode(tag);
				node.value = tag.toString() + " [type = " + tag.getId() + "]";
				return node;
		}
	}

	public void updatePosition() {
		updatePosition(0, -1);
	}

	private int updatePosition(int line, int depth) {
		cachedLine = line;
		cachedDepth = depth;
		if (depth >= 0) {
			line++;
		}

		if (open && nodes != null) {
			for (NBTNode node : nodes) {
				line = node.updatePosition(line, depth + 1);
			}
		}

		return line;
	}

	public List<NBTNode> getNodes() {
		return nodes;
	}

	public boolean isOpen() {
		return open;
	}

	public void setOpen(boolean open) {
		this.open = open;
	}

	public int getCachedLine() {
		return cachedLine;
	}

	public int getCachedDepth() {
		return cachedDepth;
	}

	public void setCachedDepth(int cachedDepth) {
		this.cachedDepth = cachedDepth;
	}

	public String getValue() {
		return value;
	}

	public String getName() {
		if (tag == null) {
			return "Element";
		} else {
			String name = ""; // NBTBase.func_150283_g(tag.getId()); TODO
			if (name.equals("UNKNOWN")) {
				return "Node";
			} else {
				return name;
			}
		}
	}
}
