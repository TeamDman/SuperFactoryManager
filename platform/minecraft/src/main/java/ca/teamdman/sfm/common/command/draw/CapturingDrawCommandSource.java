package ca.teamdman.sfm.common.command.draw;

import net.minecraft.commands.CommandSource;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class CapturingDrawCommandSource implements CommandSource {
    private final ArrayList<String> capturedLines = new ArrayList<>();
    private final DrawCommandClientContext clientContext;
    private @Nullable DrawManagerProgramCard managerProgramCard;

    public CapturingDrawCommandSource() {
        this(null);
    }

    public CapturingDrawCommandSource(DrawCommandClientContext clientContext) {
        this.clientContext = clientContext;
    }

    @Override
    public void sendSystemMessage(Component component) {
        capturedLines.add(component.getString());
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

    public DrawCommandClientContext getClientContext() {
        return clientContext;
    }

    public void captureManagerProgramCard(DrawManagerProgramCard managerProgramCard) {
        this.managerProgramCard = managerProgramCard;
    }

    public @Nullable DrawManagerProgramCard getManagerProgramCard() {
        return managerProgramCard;
    }
}