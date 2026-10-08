package ca.teamdman.sfm.common.event_bus;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.util.SFMAnnotationUtils;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1' %}
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;
import net.minecraftforge.fml.event.IModBusEvent;
import org.jetbrains.annotations.NotNull;
{% when '1.20.2', '1.20.3', '1.20.4' %}
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod.EventBusSubscriber.Bus;
import net.neoforged.fml.event.IModBusEvent;
import org.jetbrains.annotations.NotNull;
{% when '1.21' %}
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.IModBusEvent;
import org.jetbrains.annotations.NotNull;
{% when '1.21.1' %}
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.IModBusEvent;
import org.jetbrains.annotations.NotNull;
{% when '26.1.2' %}
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.IModBusEvent;
{% endcase %}
import org.jetbrains.annotations.Nullable;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.util.function.Consumer;

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
@SuppressWarnings("FieldCanBeLocal")
{% when '1.21' %}
import static net.neoforged.fml.common.EventBusSubscriber.Bus;

@SuppressWarnings("FieldCanBeLocal")
{% when '1.21.1', '26.1.2' %}
@SuppressWarnings({"FieldCanBeLocal", "removal"})
{% endcase %}
public class SFMEventListenerMethod<T extends Event> {
    private final Method method;

    private final @Nullable Object target;

    private final SFMAnnotationUtils.SFMAnnotationData annotationData;

    private final SFMSubscribeEvent annotation;

    private final Class<T> eventClass;

    private final Class<?> methodParent;

    public SFMEventListenerMethod(
            Class<?> methodParent,
            Method method,
            @Nullable Object target,
            SFMAnnotationUtils.SFMAnnotationData annotationData,
            SFMSubscribeEvent annotation
    ) {

        this.methodParent = methodParent;
        this.method = method;
        this.target = target;
        this.annotationData = annotationData;
        this.annotation = annotation;


        if (method.getParameterCount() != 1) {
            throw new IllegalArgumentException(
                    "Event subscriber method must have exactly one parameter: " + this
            );
        }

        Class<?> paramClass = method.getParameterTypes()[0];
        if (!Event.class.isAssignableFrom(paramClass)) {
            throw new IllegalArgumentException(
                    "Event subscriber method must have a single parameter of type Event: " + this
            );
        }
        //noinspection unchecked
        this.eventClass = (Class<T>) paramClass;
    }

    @Override
    public String toString() {

        return "SFMEventListenerMethod{" + methodParent.getName() + "#" + annotationData.memberName() + "}";
    }

    public static SFMEventListenerMethod<?> forStaticMethod(
            Class<?> methodParent,
            Method method,
            SFMAnnotationUtils.SFMAnnotationData annotationData,
            SFMSubscribeEvent annotation
    ) {

        return new SFMEventListenerMethod<>(methodParent, method, null, annotationData, annotation);
    }

    public static SFMEventListenerMethod<?> forInstanceMethod(
            Class<?> methodParent,
            Method method,
            Object target,
            SFMAnnotationUtils.SFMAnnotationData annotationData,
            SFMSubscribeEvent annotation
    ) {

        return new SFMEventListenerMethod<>(methodParent, method, target, annotationData, annotation);
    }

    public Consumer<T> createConsumer() {

        try {
            method.setAccessible(true);
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            MethodHandle methodHandle = lookup.unreflect(method);

            // method is either (T)void [static] or (DeclaringClass, T)void [instance]
            if (target != null) {
                methodHandle = methodHandle.bindTo(target); // now methodHandle is (T)void
            }

            // Ensure the exact type is (Object)->void for Consumer.accept
            MethodHandle adapted =
                    methodHandle.asType(MethodType.methodType(void.class, Object.class));

            return new Consumer<>() {
                @Override
                public void accept(T e) {

                    try {
                        adapted.invokeExact((Object) e);
                    } catch (Throwable t) {
                        if (t instanceof RuntimeException re) throw re;
                        if (t instanceof Error err) throw err;
                        throw new RuntimeException(t);
                    }
                }

                @Override
                public String toString() {

                    return "SFMEventListenerMethod.createConsumer(){"
                           + methodParent.getName()
                           + "#"
                           + annotationData.memberName()
                           + "}";
                }
            };
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    public void register() {
        // Create consumer
        Consumer<T> consumer = createConsumer();

        // Determine bus
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21' %}
        Bus busType = getEventBusType();
        IEventBus eventBus = SFMEventBus.getEventBus(busType);
{% when '1.21.1' %}
        EventBusSubscriber.Bus busType = getEventBusType();
        IEventBus eventBus = SFMEventBus.getEventBus(busType);
{% when '26.1.2' %}
        IEventBus eventBus = getEventBus();
{% endcase %}

        // Register listener
        eventBus.addListener(
                annotation.priority(),
                annotation.receiveCanceled(),
                eventClass,
                consumer
        );

        // Log success
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
        SFM.LOGGER.info("Registered bus={} listener={}", busType, consumer);
{% when '26.1.2' %}
        SFM.LOGGER.info("Registered listener={}", consumer);
{% endcase %}
    }

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21' %}
    private @NotNull Bus getEventBusType() {

        Bus busType;
{% when '1.21.1' %}
    private EventBusSubscriber.Bus getEventBusType() {

        EventBusSubscriber.Bus busType;
{% when '26.1.2' %}
    private IEventBus getEventBus() {
{% endcase %}
        if (IModBusEvent.class.isAssignableFrom(eventClass)) {
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
            busType = SFMEventBus.EventBusType.MOD;
{% when '26.1.2' %}
            return SFMEventBus.MOD_BUS;
{% endcase %}
        } else {
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
            busType = SFMEventBus.EventBusType.GAME;
{% when '26.1.2' %}
            return SFMEventBus.GAME_BUS;
{% endcase %}
        }
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
        return busType;
{% when '26.1.2' %}
{% endcase %}
    }
}
