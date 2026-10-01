package ca.teamdman.sfm.common.event_bus;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.util.SFMDist;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1" %}
import net.minecraftforge.eventbus.api.EventPriority;
{% else %}
import net.neoforged.bus.api.EventPriority;
{% endcase %}

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/// Used to reduce {@link ca.teamdman.sfm.common.util.MCVersionDependentBehaviour}.
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1" %}
/// Should be effectively equivalent to {@link net.minecraftforge.eventbus.api.SubscribeEvent}
{% else %}
/// Should be effectively equivalent to {@link net.neoforged.bus.api.SubscribeEvent}
{% endcase %}
@Retention(value = RUNTIME)
@Target(value = METHOD)
public @interface SFMSubscribeEvent {

    SFMDist[] value() default {SFMDist.CLIENT, SFMDist.DEDICATED_SERVER};

    EventPriority priority() default EventPriority.NORMAL;

{% if features.mod_event_filtering %}
    boolean receiveCanceled() default false;

    /**
     * Optional dependency that must be loaded before this subscriber class is resolved.
     * This keeps handlers whose signatures reference an optional mod out of automatic discovery.
     */
    String requiredModId() default "";

    // This is unused and idk what the modid param in the built-in event bus subscriber does so I'll leave until
{% else %}
    boolean receiveCanceled() default false;

    // This is unused and idk what the modid param in the built-in event bus subscriber does so I'll leave until
{% endif %}
    // I understand enough to safely remove it.
    @SuppressWarnings({"unused", "SpellCheckingInspection"})
    String modid() default SFM.MOD_ID;

}
