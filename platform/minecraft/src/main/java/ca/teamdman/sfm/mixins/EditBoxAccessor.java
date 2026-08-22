package ca.teamdman.sfm.mixins;

import net.minecraft.client.gui.components.EditBox;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Exact directional-selection evidence for the temporal command document. */
@Mixin(EditBox.class)
public interface EditBoxAccessor {
    @Accessor("highlightPos")
    int sfm$getHighlightPos();
}
