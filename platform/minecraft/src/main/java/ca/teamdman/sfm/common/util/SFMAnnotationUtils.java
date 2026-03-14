package ca.teamdman.sfm.common.util;

import ca.teamdman.sfm.SFM;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.moddiscovery.ModAnnotation;
import net.minecraftforge.forgespi.language.ModFileScanData;
import org.jetbrains.annotations.UnknownNullability;
import org.objectweb.asm.Type;

import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.util.*;
import java.util.stream.Stream;

public class SFMAnnotationUtils {
    public static Stream<SFMAnnotationData> discoverAnnotations(Class<? extends Annotation> annotationClass) {

        Type annotationType = Type.getType(annotationClass);
        return ModList.get().getAllScanData().stream()
                .map(ModFileScanData::getAnnotations)
                .flatMap(Collection::stream)
                .filter(annotationData -> annotationType.equals(annotationData.annotationType()))
                .map(SFMAnnotationData::new);
    }

    public static Class<?> tryLoadAnnotatedClass(
            SFMAnnotationData annotation
    ) {
        // load the class
        try {
            return Class.forName(
                    annotation.clazz().getClassName(),
                    true,
                    SFM.class.getClassLoader()
            );
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    public static <T> T tryConstruct(
            Class<?> clazz,
            Class<T> desiredClass
    ) {

        if (!desiredClass.isAssignableFrom(clazz)) {
            throw new RuntimeException(
                    "Class "
                    + clazz.getName()
                    + " is not assignable to "
                    + desiredClass.getName()
            );
        }

        try {
            @SuppressWarnings("unchecked")
            T instance = (T) clazz.getConstructor().newInstance();
            return instance;
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to instantiate test builder for " + clazz.getName(), e);
        }
    }

    @MCVersionDependentBehaviour
    public static String getEnumValue(ModAnnotation.EnumHolder holder) {
        return holder.getValue();
    }

    public record SFMAnnotationData(
            ModFileScanData.AnnotationData inner
    ) {
        public Type annotationType() {

            return inner.annotationType();
        }

        public ElementType targetType() {

            return inner.targetType();
        }

        public Map<String, Object> annotationData() {

            return inner.annotationData();
        }

        public String memberName() {

            return inner.memberName();
        }

        public Type clazz() {

            return inner.clazz();
        }

        @SuppressWarnings("unchecked")
        public <T extends Enum<T>> EnumSet<T> getEnumSet(
                String key,
                Class<T> clazz
        ) {
            var existing = (List<ModAnnotation.EnumHolder>) annotationData().getOrDefault(
                    key,
                    new ArrayList<>()
            );

            var rtn = EnumSet.noneOf(clazz);
            for (ModAnnotation.EnumHolder enumHolder : existing) {
                rtn.add(Enum.valueOf(clazz, getEnumValue(enumHolder)));
            }
            return rtn;
        }

        public <T extends Enum<T>> @UnknownNullability T getEnum(String key, Class<T> clazz) {
            var existing = (ModAnnotation.EnumHolder) annotationData().get(key);
            return existing == null ? null : Enum.valueOf(clazz, getEnumValue(existing));
        }

    }

}
