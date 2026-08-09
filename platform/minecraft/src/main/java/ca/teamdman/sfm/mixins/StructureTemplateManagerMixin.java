package ca.teamdman.sfm.mixins;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.gametest.SFMStructureGenerator;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

@Mixin(value = StructureTemplateManager.class, priority = 0)
public class StructureTemplateManagerMixin {
    @Inject(method = "get", at = @At("HEAD"), cancellable = true)
    public void onTryLoad(
            ResourceLocation pId,
            CallbackInfoReturnable<Optional<StructureTemplate>> cir
    ) {
        if (pId.getNamespace().equals(SFM.MOD_ID)) {
            cir.setReturnValue(SFMStructureGenerator.generateStructureTemplate(pId));
        }
    }
}
