package ca.teamdman.sfm.common.event_bus;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.util.SFMDist;
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

    SFMDist[] value() default {SFMDist.CLIENT, SFMDist.DEDICATED_SERVER};

    EventPriority priority() default EventPriority.NORMAL;

    boolean receiveCanceled() default false;

    // This is unused and idk what the modid param in the built-in event bus subscriber does so I'll leave until
    // I understand enough to safely remove it.
    @SuppressWarnings({"unused", "SpellCheckingInspection"})
    String modid() default SFM.MOD_ID;

}
