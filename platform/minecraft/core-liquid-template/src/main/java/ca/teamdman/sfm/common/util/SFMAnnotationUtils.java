package ca.teamdman.sfm.common.util;

import ca.teamdman.sfm.SFM;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1" %}
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.moddiscovery.ModAnnotation;
import net.minecraftforge.forgespi.language.ModFileScanData;
{% when "1.20.2", "1.20.3", "1.20.4" %}
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.moddiscovery.ModAnnotation;
import net.neoforged.neoforgespi.language.ModFileScanData;
{% else %}
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.modscan.ModAnnotation;
import net.neoforged.neoforgespi.language.ModFileScanData;
{% endcase %}
import org.jetbrains.annotations.Nullable;
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

{% case minecraft_version %}
{% when "1.21", "1.21.0", "1.21.1", "26.1.2" %}
        return holder.value();
{% else %}
        return holder.getValue();
{% endcase %}
    }

    @SuppressWarnings("unused")
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

{% if features.mod_event_filtering %}
        public String getString(String key, String defaultValue) {

            Object value = annotationData().get(key);
            return value instanceof String string ? string : defaultValue;
        }

{% endif %}
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

        public <T extends Enum<T>> @Nullable T getEnum(
                String key,
                Class<T> clazz
        ) {

            var existing = (ModAnnotation.EnumHolder) annotationData().get(key);
            return existing == null ? null : Enum.valueOf(clazz, getEnumValue(existing));
        }

        public Class<?> tryLoadClass() {
            // load the class
            try {
                return Class.forName(
                        clazz().getClassName(),
                        true,
                        SFM.class.getClassLoader()
                );
            } catch (ClassNotFoundException | NoClassDefFoundError e) {
                throw new RuntimeException("Failed to load class " + clazz().getClassName(), e);
            }
        }

    }

}
