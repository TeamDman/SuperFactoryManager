package vswe.superfactory.components;

import java.util.List;

import vswe.superfactory.Localization;
import vswe.superfactory.blocks.ConnectionBlockType;

public class ComponentMenuEmitters extends ComponentMenuContainer {

    public ComponentMenuEmitters(FlowComponent parent) {
        super(parent, ConnectionBlockType.EMITTER);
    }

    @Override
    public String getName() {
        return Localization.EMITTER_MENU.toString();
    }

    @Override
    public void addErrors(List<String> errors) {
        if (selectedInventories.isEmpty()) {
            errors.add(Localization.NO_EMITTER_ERROR.toString());
        }
    }

    @Override
    protected void initRadioButtons() {}
}
