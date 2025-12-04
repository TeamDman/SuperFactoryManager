package vswe.superfactory.blocks;

import vswe.superfactory.tiles.TileEntityManager;

public interface ISystemListener {
	void added(TileEntityManager owner);

	void removed(TileEntityManager owner);
}
