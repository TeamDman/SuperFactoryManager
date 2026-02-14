package ca.teamdman.sfm.common.util;

import ca.teamdman.sfm.SFM;
import com.github.bsideup.jabel.Desugar;
import net.minecraftforge.fml.common.discovery.ASMDataTable;
import net.minecraftforge.fml.common.discovery.asm.ModAnnotation;
import net.minecraftforge.fml.relauncher.libraries.ModList;
import org.jetbrains.annotations.UnknownNullability;
import org.objectweb.asm.Type;

import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.util.*;
import java.util.stream.Stream;

public class SFMAnnotationUtils {
    public static Stream<SFMAnnotationData> discoverAnnotations(ASMDataTable table, Class<? extends Annotation> annotationClass) {

        Type annotationType = Type.getType(annotationClass);
        var a = table
                .getAll(annotationClass.getCanonicalName());
//                .stream()
//                .map(SFMAnnotationData::new);
        return Stream.empty();
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

    @Desugar
    public record SFMAnnotationData(
            Type annotationType,
            ElementType targetType,
            Map<String, Object> annotationData,
            String memberName,
            Type clazz
    )
    {

//        SFMAnnotationData(ASMDataTable.ASMData data) {
//            this(
//                    data.getAnnotationName(),
//                    data.
//            );
//        }


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
