package ca.teamdman.sfm.common.util;

import java.util.AbstractMap;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class CollectionUtils {
    public static <K, V> Map<K, V> mapOf(K key, V value) {
        return Stream.of(new AbstractMap.SimpleImmutableEntry<>(key, value))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    public static <T> Set<T> setOf(T... elements) {
        if (elements.length == 0) return Collections.emptySet();
        return Stream.of(elements).collect(Collectors.toSet());
    }
}
