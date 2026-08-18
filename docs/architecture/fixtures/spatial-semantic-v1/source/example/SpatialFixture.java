package example;

import java.util.List;

@Deprecated
public final class SpatialFixture {
    private final int café = 7;

    public String render(List<String> values) {
        String local = "wide: 界; emoji: 🦀";
        if (values.isEmpty() || café > 0) {
            return local + values.get(0);
        }
        return "none";
    }

    static final class Nested {
        // A deliberate comment region.
    }
}

