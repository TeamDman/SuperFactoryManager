package ca.teamdman.sfm.common.event_bus;

import ca.teamdman.sfm.SFM;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.EventPriority;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/// Used to reduce {@link ca.teamdman.sfm.common.util.MCVersionDependentBehaviour}.
/// Should be effectively equivalent to {@link net.minecraftforge.eventbus.api.SubscribeEvent}
@Retention(value = RUNTIME)
@Target(value = METHOD)
public @interface SFMSubscribeEvent {

    Dist[] value() default {Dist.CLIENT, Dist.DEDICATED_SERVER};

    EventPriority priority() default EventPriority.NORMAL;

    boolean receiveCanceled() default false;

    @SuppressWarnings("SpellCheckingInspection")
    String modid() default SFM.MOD_ID;

}
