package ca.teamdman.sfm.gametest.puppet;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.util.SFMAnnotationUtils;
import ca.teamdman.sfm.properties.SFMProperties;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class SFMGamePuppetDiscovery {
    private SFMGamePuppetDiscovery() {
    }

    public static Collection<SFMDiscoveredGamePuppet> gatherSelectedPuppets() {
        return filterSelectedPuppets(gatherPuppets().toList());
    }

    public static Stream<SFMDiscoveredGamePuppet> gatherPuppets() {
        List<SFMDiscoveredGamePuppet> puppets = SFMAnnotationUtils
                .discoverAnnotations(SFMGamePuppet.class)
                .filter(annotation -> annotation.targetType() == java.lang.annotation.ElementType.TYPE)
                .map(SFMAnnotationUtils.SFMAnnotationData::tryLoadClass)
                .map(SFMGamePuppetDiscovery::discoverPuppet)
                .sorted(Comparator.comparing(SFMDiscoveredGamePuppet::puppetName))
                .toList();

        Set<String> seenNames = new HashSet<>();
        for (SFMDiscoveredGamePuppet puppet : puppets) {
            if (!seenNames.add(puppet.puppetName())) {
                throw new IllegalStateException("Duplicate SFM game puppet id: " + qualifyPuppetName(puppet.puppetName()));
            }
            SFM.LOGGER.info("Discovered SFM game puppet: {} ({})", puppet.puppetName(), puppet.location());
        }
        return puppets.stream();
    }

    private static SFMDiscoveredGamePuppet discoverPuppet(Class<?> owner) {
        String methodName = "run";
        List<Method> matches = Stream.of(owner.getDeclaredMethods())
                .filter(method -> method.getName().equals(methodName))
                .toList();
        if (matches.size() != 1) {
            throw new IllegalStateException(
                    "Expected exactly one game puppet declaration method named "
                    + owner.getName() + "#" + methodName + ", found " + matches.size()
            );
        }

        Method method = matches.get(0);
        if (!Modifier.isPublic(method.getModifiers()) || !Modifier.isStatic(method.getModifiers())) {
            throw new IllegalStateException("Game puppet method must be public static: " + owner.getName() + "#" + methodName);
        }
        if (method.getReturnType() != void.class
            || method.getParameterCount() != 1
            || method.getParameterTypes()[0] != SFMGamePuppetHelper.class) {
            throw new IllegalStateException(
                    "Game puppet method must have signature public static void "
                    + methodName + "(SFMGamePuppetHelper): " + owner.getName()
            );
        }
        return new SFMDiscoveredGamePuppet(
                puppetName(owner),
                method,
                owner.getAnnotation(SFMGamePuppet.class).viewportProfile()
        );
    }

    private static Collection<SFMDiscoveredGamePuppet> filterSelectedPuppets(
            Collection<SFMDiscoveredGamePuppet> puppets
    ) {
        String rawSelection = SFMProperties.gamePuppetSelection();
        if (rawSelection.isEmpty()) {
            throw new IllegalStateException(
                    "No SFM game puppet selection was supplied. Use puppet run <name>."
            );
        }

        List<String> selectors = Stream.of(rawSelection.split(","))
                .map(String::trim)
                .filter(selector -> !selector.isEmpty())
                .map(SFMGamePuppetDiscovery::normalizeSelector)
                .toList();
        if (selectors.isEmpty()) {
            throw new IllegalStateException("SFM game puppet selection must contain at least one nonblank selector.");
        }

        List<SFMDiscoveredGamePuppet> matched = puppets.stream()
                .filter(puppet -> selectors.stream().anyMatch(selector -> wildcardMatches(
                        qualifyPuppetName(puppet.puppetName()),
                        selector
                )))
                .toList();
        SFM.LOGGER.info(
                "Applying SFM game puppet selection '{}': matched {} of {} puppets",
                rawSelection,
                matched.size(),
                puppets.size()
        );
        if (matched.isEmpty()) {
            throw new IllegalStateException(
                    "SFM game puppet selection '" + rawSelection
                    + "' matched zero puppets. Try an exact name or a wildcard like 'sfm:*_walkthrough'."
            );
        }
        matched.forEach(puppet -> SFM.LOGGER.info("Selected SFM game puppet: {}", qualifyPuppetName(puppet.puppetName())));
        return matched;
    }

    public static String puppetName(Class<?> owner) {
        return toSnakeCase(owner.getSimpleName().replaceAll("GamePuppet$", ""));
    }

    private static String qualifyPuppetName(String puppetName) {
        return SFM.MOD_ID + ":" + puppetName;
    }

    private static String normalizeSelector(String selector) {
        return selector.contains(":") ? selector : SFM.MOD_ID + ":" + selector;
    }

    private static boolean wildcardMatches(String candidate, String selector) {
        StringBuilder regex = new StringBuilder("^");
        for (int i = 0; i < selector.length(); i++) {
            char ch = selector.charAt(i);
            switch (ch) {
                case '*' -> regex.append(".*");
                case '?' -> regex.append('.');
                default -> regex.append(Pattern.quote(String.valueOf(ch)));
            }
        }
        regex.append('$');
        return candidate.matches(regex.toString());
    }

    private static String toSnakeCase(String input) {
        return input
                .replaceAll("([a-zA-Z])(\\d+)", "$1_$2")
                .replaceAll("(\\d+)([a-zA-Z])", "$1_$2")
                .replaceAll("([a-z])([A-Z])", "$1_$2")
                .toLowerCase(Locale.ROOT);
    }
}
