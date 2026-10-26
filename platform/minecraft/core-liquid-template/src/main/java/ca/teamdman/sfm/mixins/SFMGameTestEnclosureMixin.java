package ca.teamdman.sfm.mixins;

import ca.teamdman.sfm.SFM;
{% case minecraft_version %}
{% when "1.21", "1.21.1" %}
import ca.teamdman.sfm.gametest.SFMGameTestFunctionOwnership;
{% endcase %}
import net.minecraft.gametest.framework.GameTestInfo;
{% case minecraft_version %}
{% when "1.21", "1.21.1" %}
import net.minecraft.gametest.framework.StructureUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
{% when "26.1.2" %}
import net.minecraft.world.level.block.entity.TestInstanceBlockEntity;
{% endcase %}
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Skip the generated enclosure only when this GameTest belongs to SFM. */
@Mixin(GameTestInfo.class)
public abstract class SFMGameTestEnclosureMixin {
{% case minecraft_version %}
{% when "1.21", "1.21.1" %}
    @Redirect(method = "prepareTestStructure", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/gametest/framework/StructureUtils;encaseStructure(Lnet/minecraft/world/phys/AABB;Lnet/minecraft/server/level/ServerLevel;Z)V"), remap = false)
    private void sfm$skipOwnEnclosure(AABB bounds, ServerLevel level, boolean placeBarriers) {
        GameTestInfo info = (GameTestInfo) (Object) this;
        if (!SFMGameTestFunctionOwnership.isOwned(info.getTestFunction())) {
            StructureUtils.encaseStructure(bounds, level, placeBarriers);
        }
    }
{% when "26.1.2" %}
    @Redirect(method = "placeStructure", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/entity/TestInstanceBlockEntity;encaseStructure()V"), remap = false)
    private void sfm$skipOwnEnclosure(TestInstanceBlockEntity test) {
        GameTestInfo info = (GameTestInfo) (Object) this;
        if (!SFM.MOD_ID.equals(info.id().getNamespace())) {
            test.encaseStructure();
        }
    }
{% endcase %}
}
