package ca.teamdman.sfm.common.event_bus;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.util.SFMAnnotationUtils;
import ca.teamdman.sfm.common.util.SFMDist;
import org.objectweb.asm.Type;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.EnumSet;

public class SFMAutomaticEventSubscriber {
    /// This is called from our mod class constructor {@link SFM#SFM()}.
    /// Normal EventBusSubscriber annotation discovery happens right after constructor, so this should be fine.
    public static void attachEventBusSubscribers() {

        SFMAnnotationUtils.discoverAnnotations(SFMSubscribeEvent.class)
                .filter(annotationData -> {
                    // Only proceed when the physical side matches
                    EnumSet<SFMDist> sides = annotationData.getEnumSet("value", SFMDist.class);
                    if (sides.isEmpty()) {
                        sides.add(SFMDist.CLIENT);
                        sides.add(SFMDist.DEDICATED_SERVER);
                    }

                    return sides.contains(SFMDist.current());
                })
                .forEach(SFMAutomaticEventSubscriber::tryRegisterAnnotatedMethod);
    }

    /// Discover an annotated method and register it.
    /// Note that we should use {@link net.minecraftforge.eventbus.ASMEventHandler} and {@link net.minecraftforge.eventbus.IEventListenerFactory}
    /// for performance improvements over invoking {@link Method} using reflection.
    private static void tryRegisterAnnotatedMethod(SFMAnnotationUtils.SFMAnnotationData annotationData) {
        // Load the class, since the annotation scraping doesn't do this.
        // By this time, we have already validated if we are on the correct physical side to avoid issues.
        Class<?> handlerMethodParentClass = SFMAnnotationUtils.tryLoadAnnotatedClass(annotationData);

        // Create a display representation of the method for logging
        String methodDisplay = handlerMethodParentClass.getName() + "#" + annotationData.memberName();

        // Discover the annotated method by correlating the name and descriptor
        Method found = null;
        for (Method method : handlerMethodParentClass.getDeclaredMethods()) {
            String methodName = method.getName() + Type.getMethodDescriptor(method);
            if (!methodName.equals(annotationData.memberName())) continue;
            found = method;
            break;
        }

        // Bail if not found
        if (found == null) {
            SFM.LOGGER.error(
                    "Unable to register event subscriber, failed to find method {}",
                    methodDisplay
            );
            return;
        }
        final Method handlerMethod = found;

        // Log registration attempt
        SFM.LOGGER.debug("Registering event subscriber {}", methodDisplay);

        // Validate event handler method constraints
        if (!Modifier.isStatic(handlerMethod.getModifiers())) {
            throw new IllegalArgumentException("Event subscriber method must be static: " + methodDisplay);
        }

        // Get the annotation
        SFMSubscribeEvent annotation = handlerMethod.getAnnotation(SFMSubscribeEvent.class);
        if (annotation == null) {
            throw new IllegalStateException("Event subscriber method somehow missing @SFMSubscribeEvent annotation: "
                                            + methodDisplay);
        }

        // Construct the event listener
        SFMEventListenerMethod<?> eventListener = SFMEventListenerMethod.forStaticMethod(
                handlerMethodParentClass,
                handlerMethod,
                annotationData,
                annotation
        );

        // Register the consumer
        eventListener.register();

    }

}
