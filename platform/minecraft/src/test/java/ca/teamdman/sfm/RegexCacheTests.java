package ca.teamdman.sfm;

import ca.teamdman.sfm.common.program.RegexCache;
import ca.teamdman.sfm.common.timing.SFMInstant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RegexCacheTests {

    private static final int ITERATIONS = 1_000_000;

    private static final int ALTERNATIONS = 25;

    @BeforeAll
    public static void setup() {
        // Warm-up the JVM
        RegexCache.buildPredicate(".*seeds.*").test("warmup");
        Pattern.compile(".*seeds.*").asMatchPredicate().test("warmup");
    }

    @Test
    public void measureContains() {

        performPerformanceTest("Contains optimization", ".*seeds.*", "wheat_seeds");
    }

    @Test
    public void measureStartsWith() {

        performPerformanceTest("StartsWith optimization", "seeds.*", "beetroot_seeds");
    }

    @Test
    public void measureEndsWith() {

        performPerformanceTest("EndsWith optimization", ".*seeds", "wheat_seeds");
    }

    @Test
    public void measureStartsWithEndsWith() {

        performPerformanceTest("StartsWithEndsWith optimization", "printed.*processor", "printed_advanced_processor");
    }

    @Test
    public void testDoubleWildcardOptimization() {

        performCorrectnessTest("Double wildcard optimization", ".*.*", "anything");
    }

    @Test
    public void testSpecialCharacterPattern() {

        performCorrectnessTest("Special character pattern", ".*\\d+.*", "number123");
    }

    @Test
    public void testNoWildcardPattern() {

        performCorrectnessTest("No wildcard pattern", "exactmatch", "exactmatch");
    }

    @Test
    public void testComplexRegexPattern() {

        performCorrectnessTest("Complex regex pattern", ".*[a-z]{3}\\d+.*", "abc123");
    }

    private Duration measureTime(Runnable runnable) {

        SFMInstant start = SFMInstant.now();
        runnable.run();
        return start.elapsed();
    }

    private void performPerformanceTest(
            String name,
            String pattern,
            String testString
    ) {

        Predicate<String> optimizedPredicate = RegexCache.buildPredicate(pattern);
        Predicate<String> standardPredicate = Pattern.compile(pattern).asMatchPredicate();

        Duration optimizedTime = Duration.ZERO;
        Duration standardTime = Duration.ZERO;

        for (int i = 0; i < ALTERNATIONS; i++) {
            Duration elapsedOptimizedTime = measureTime(() -> {
                for (int j = 0; j < ITERATIONS / ALTERNATIONS; j++) {
                    optimizedPredicate.test(testString);
                }
            });
            optimizedTime = optimizedTime.plus(elapsedOptimizedTime);

            Duration elapsedStandardTime = measureTime(() -> {
                for (int j = 0; j < ITERATIONS / ALTERNATIONS; j++) {
                    standardPredicate.test(testString);
                }
            });
            standardTime = standardTime.plus(elapsedStandardTime);
        }

        System.out.println(name + " - Optimized time: " + optimizedTime.toMillis() + " ms");
        System.out.println(name + " - Standard time: " + standardTime.toMillis() + " ms");

        assertEquals(optimizedPredicate.test(testString), standardPredicate.test(testString));
        assertTrue(
                optimizedTime.compareTo(standardTime) < 0,
                "Expected optimized time to be better\nOptimized=" + optimizedTime.toMillis() + "ms\nStandard=" + standardTime.toMillis() + "ms"
        );
    }

    private void performCorrectnessTest(
            String name,
            String pattern,
            String testString
    ) {

        Predicate<String> optimizedPredicate = RegexCache.buildPredicate(pattern);
        Predicate<String> standardPredicate = Pattern.compile(pattern).asMatchPredicate();

        Duration optimizedTime = measureTime(() -> {
            for (int i = 0; i < ITERATIONS; i++) {
                optimizedPredicate.test(testString);
            }
        });

        Duration standardTime = measureTime(() -> {
            for (int i = 0; i < ITERATIONS; i++) {
                standardPredicate.test(testString);
            }
        });

        System.out.println(name + " - Optimized time: " + optimizedTime.toMillis() + " ms");
        System.out.println(name + " - Standard time: " + standardTime.toMillis() + " ms");

        assertEquals(optimizedPredicate.test(testString), standardPredicate.test(testString));
    }

}
