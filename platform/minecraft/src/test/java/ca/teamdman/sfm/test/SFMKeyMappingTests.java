package ca.teamdman.sfm.test;

import ca.teamdman.sfm.client.registry.SFMKeyMappings;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.common.util.Lazy;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

public class SFMKeyMappingTests {
    @Test
    @SuppressWarnings("unchecked")
    public void testAllKeyMappingsInArray() {
        var keyMappings = SFMKeyMappings.getSFMKeyMappings();
        Set<KeyMapping> fromMethod = Arrays.stream(keyMappings).collect(Collectors.toSet());

        Set<KeyMapping> fromReflection = Arrays.stream(SFMKeyMappings.class.getFields())
                .filter(f -> Modifier.isPublic(f.getModifiers())
                        && Modifier.isStatic(f.getModifiers())
                        && Modifier.isFinal(f.getModifiers()))
                .filter(f -> Lazy.class.isAssignableFrom(f.getType()))
                .map(f -> {
                    try {
                        return ((Lazy<KeyMapping>) f.get(null)).get();
                    } catch (IllegalAccessException e) {
                        throw new RuntimeException(e);
                    }
                })
                .collect(Collectors.toSet());

        // Print differences (missing and extras) for easier debugging
        var missingInMethod = fromReflection.stream()
                .filter(k -> !fromMethod.contains(k))
                .map(KeyMapping::getName)
                .sorted()
                .toList();
        var extraInMethod = fromMethod.stream()
                .filter(k -> !fromReflection.contains(k))
                .map(KeyMapping::getName)
                .sorted()
                .toList();

        if (!missingInMethod.isEmpty()) {
            System.err.println("[SFMKeyMappingTests] Missing in getSFMKeyMappings():");
            missingInMethod.forEach(n -> System.err.println(" - " + n));
        }
        if (!extraInMethod.isEmpty()) {
            System.err.println("[SFMKeyMappingTests] Extra in getSFMKeyMappings():");
            extraInMethod.forEach(n -> System.err.println(" - " + n));
        }

        Assertions.assertTrue(
                extraInMethod.isEmpty(),
                "Unexpected extras in getSFMKeyMappings(): " + extraInMethod
        );
        Assertions.assertTrue(
                fromMethod.containsAll(fromReflection),
                "Missing from getSFMKeyMappings(): " + missingInMethod
        );
    }
}
