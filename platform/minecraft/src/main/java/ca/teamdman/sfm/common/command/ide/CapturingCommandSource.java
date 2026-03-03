package ca.teamdman.sfm.common.command.ide;

import net.minecraft.commands.CommandSource;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class CapturingCommandSource implements CommandSource {
    private final ArrayList<String> capturedLines = new ArrayList<>();

    @Override
    public void sendSystemMessage(Component pComponent) {
        capturedLines.add(pComponent.getString());
    }

    @Override
    public boolean acceptsSuccess() {
        return true;
    }

    @Override
    public boolean acceptsFailure() {
        return true;
    }

    @Override
    public boolean shouldInformAdmins() {
        return false;
    }

    public List<String> getCapturedLines() {
        return capturedLines;
    }
}
