package ca.teamdman.sfm.common.tutorial;

import ca.teamdman.sfm.SFM;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.Level;

public final class SFMTutorialWorld {
    public static final ResourceLocation TUTORIAL_DIMENSION_ID = new ResourceLocation(SFM.MOD_ID, "tutorial");
    public static final ResourceKey<Level> TUTORIAL_LEVEL_KEY = ResourceKey.create(
            Registry.DIMENSION_REGISTRY,
            TUTORIAL_DIMENSION_ID
    );

    private SFMTutorialWorld() {
    }

    public static boolean isTutorialLevel(Level level) {
        return level.dimension().equals(TUTORIAL_LEVEL_KEY);
    }

    public static void enforceWorldState(ServerLevel level) {
        level.setDayTime(6000L);
        level.setWeatherParameters(0, 0, false, false);
    }

    public static void applyNightVision(ServerPlayer player) {
        player.addEffect(new MobEffectInstance(
                MobEffects.NIGHT_VISION,
                300,
                0,
                true,
                false,
                false
        ));
    }
}