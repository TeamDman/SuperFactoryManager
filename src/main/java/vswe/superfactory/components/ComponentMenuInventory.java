package vswe.superfactory.components;

import java.util.List;

import vswe.superfactory.Localization;
import vswe.superfactory.blocks.ConnectionBlockType;

public class ComponentMenuInventory extends ComponentMenuContainer {

    public ComponentMenuInventory(FlowComponent parent) {
        super(parent, ConnectionBlockType.INVENTORY);
    }

    @Override
    public String getName() {
        return Localization.INVENTORY_MENU.toString();
    }

    @Override
    public void addErrors(List<String> errors) {
        if (selectedInventories.isEmpty()) {
            errors.add(Localization.NO_INVENTORY_ERROR.toString());
        }
    }
}
