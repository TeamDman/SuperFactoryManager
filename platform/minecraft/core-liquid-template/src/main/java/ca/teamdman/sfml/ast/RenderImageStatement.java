package ca.teamdman.sfml.ast;

import ca.teamdman.sfm.common.program.ProgramContext;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/** Selects a client-local image reference for the current bound Touch Display. */
public record RenderImageStatement(ResourceLocation image, String binding) implements Statement {
    public RenderImageStatement {
        Objects.requireNonNull(image, "image");
        Objects.requireNonNull(binding, "binding");
    }

    @Override
    public void tick(ProgramContext context) {
        throw new IllegalStateException("RENDER IMAGE cannot run in the server ProgramContext");
    }

    @Override
    public String toString() {
        return "RENDER IMAGE \"" + image + "\" TO " + binding;
    }
}
