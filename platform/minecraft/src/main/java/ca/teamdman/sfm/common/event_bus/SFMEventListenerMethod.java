package ca.teamdman.sfm.common.event_bus;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.util.SFMAnnotationUtils;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.IModBusEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.util.function.Consumer;

@SuppressWarnings({"FieldCanBeLocal", "removal"})
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
        EventBusSubscriber.Bus busType = getEventBusType();
        IEventBus eventBus = SFMEventBus.getEventBus(busType);

        // Register listener
        eventBus.addListener(
                annotation.priority(),
                annotation.receiveCanceled(),
                eventClass,
                consumer
        );

        // Log success
        SFM.LOGGER.info("Registered bus={} listener={}", busType, consumer);
    }

    private EventBusSubscriber.Bus getEventBusType() {

        EventBusSubscriber.Bus busType;
        if (IModBusEvent.class.isAssignableFrom(eventClass)) {
            busType = SFMEventBus.EventBusType.MOD;
        } else {
            busType = SFMEventBus.EventBusType.GAME;
        }
        return busType;
    }

}
