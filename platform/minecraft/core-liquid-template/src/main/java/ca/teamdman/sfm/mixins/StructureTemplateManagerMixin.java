package ca.teamdman.sfm.mixins;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.gametest.SFMStructureGenerator;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
import net.minecraft.resources.ResourceLocation;
{% when '26.1.2' %}
import net.minecraft.resources.Identifier;
{% endcase %}
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

@Mixin(value = StructureTemplateManager.class, priority = 0)
public class StructureTemplateManagerMixin {
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1' %}
    @Inject(method = "get", at = @At("HEAD"), cancellable = true)
{% when '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1', '26.1.2' %}
    @Inject(method = "get", at = @At("HEAD"), cancellable = true, remap = false)
{% endcase %}
    public void onTryLoad(
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
            ResourceLocation pId,
{% when '26.1.2' %}
            Identifier pId,
{% endcase %}
            CallbackInfoReturnable<Optional<StructureTemplate>> cir
    ) {
        if (pId.getNamespace().equals(SFM.MOD_ID)) {
            cir.setReturnValue(SFMStructureGenerator.generateStructureTemplate(pId));
        }
    }
}
