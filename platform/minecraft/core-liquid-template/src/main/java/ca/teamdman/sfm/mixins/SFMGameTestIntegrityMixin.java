package ca.teamdman.sfm.mixins;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.gametest.SFMGameTestIntegrityAccess;
import net.minecraft.gametest.framework.GameTestAssertException;
{% case minecraft_version %}
{% when "26.1.2" %}
import net.minecraft.gametest.framework.GameTestException;
{% endcase %}
import net.minecraft.gametest.framework.GameTestInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;

/** Checks SFM-owned tests before each tick and before Minecraft marks them successful. */
@Mixin(GameTestInfo.class)
public abstract class SFMGameTestIntegrityMixin implements SFMGameTestIntegrityAccess {
    @Unique private Consumer<String> sfm$integrityCheck;
    @Unique private boolean sfm$reportingIntegrityFailure;

    @Override
    public void sfm$installIntegrityCheck(Consumer<String> check) {
        this.sfm$integrityCheck = check;
    }

{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1" %}
    @Inject(method = "tickInternal", at = @At("HEAD"), cancellable = true)
{% else %}
    @Inject(method = "tickInternal", at = @At("HEAD"), cancellable = true, remap = false)
{% endcase %}
    private void sfm$checkDuringTick(CallbackInfo ci) {
        sfm$check("tick", ci);
    }

{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1" %}
    @Inject(method = "succeed", at = @At("HEAD"), cancellable = true)
{% else %}
    @Inject(method = "succeed", at = @At("HEAD"), cancellable = true, remap = false)
{% endcase %}
    private void sfm$checkBeforeSuccess(CallbackInfo ci) {
        sfm$check("success", ci);
    }

{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1" %}
    @Inject(method = "fail", at = @At("HEAD"))
    private void sfm$checkBeforeFailure(Throwable original, CallbackInfo ci) {
{% when "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
    @Inject(method = "fail", at = @At("HEAD"), remap = false)
    private void sfm$checkBeforeFailure(Throwable original, CallbackInfo ci) {
{% when "26.1.2" %}
    @Inject(method = "fail(Lnet/minecraft/gametest/framework/GameTestException;)V", at = @At("HEAD"), remap = false)
    private void sfm$checkBeforeFailure(GameTestException original, CallbackInfo ci) {
{% endcase %}
        Consumer<String> check = this.sfm$integrityCheck;
        if (check == null || sfm$reportingIntegrityFailure) {
            return;
        }
        try {
            check.accept("failure");
        } catch (GameTestAssertException integrityFailure) {
            if (original != integrityFailure) {
                original.addSuppressed(integrityFailure);
            }
            SFM.LOGGER.error("SFM GameTest infrastructure was damaged while recording the original test failure", integrityFailure);
        }
    }

    @Unique
    private void sfm$check(String phase, CallbackInfo ci) {
        Consumer<String> check = this.sfm$integrityCheck;
        if (check == null) {
            return;
        }
        try {
            check.accept(phase);
        } catch (GameTestAssertException integrityFailure) {
            sfm$reportingIntegrityFailure = true;
            try {
                ((GameTestInfo) (Object) this).fail(integrityFailure);
            } finally {
                sfm$reportingIntegrityFailure = false;
            }
            ci.cancel();
        }
    }
}
