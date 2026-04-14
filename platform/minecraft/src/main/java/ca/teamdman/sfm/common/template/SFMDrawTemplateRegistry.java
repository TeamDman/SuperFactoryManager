package ca.teamdman.sfm.common.template;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.config.SFMConfig;
import ca.teamdman.sfm.common.registry.registration.SFMResourceTypes;
import ca.teamdman.sfml.program_builder.ProgramBuildResult;
import ca.teamdman.sfml.program_builder.ProgramBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.ModList;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class SFMDrawTemplateRegistry {
    private static final String TEMPLATE_PROGRAMS_PATH = "assets/" + SFM.MOD_ID + "/template_programs";
    private static final Path IDE_TEMPLATE_PROGRAMS_PATH = Paths.get("src/main/resources").resolve(TEMPLATE_PROGRAMS_PATH);

    private SFMDrawTemplateRegistry() {
    }

    public static List<SFMDrawTemplate> gatherAll() {
        Path templateRoot = resolveTemplateRoot();
        if (templateRoot == null || !Files.exists(templateRoot)) {
            return List.of();
        }

        List<SFMDrawTemplate> templates = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(templateRoot)) {
            paths.filter(SFMDrawTemplateRegistry::isTemplatePath)
                    .forEach(path -> {
                        SFMDrawTemplate template = fromPath(templateRoot, path);
                        if (template != null) {
                            templates.add(template);
                        }
                    });
        } catch (IOException e) {
            SFM.LOGGER.error("Failed to enumerate draw templates from {}", templateRoot, e);
            return List.of();
        }

        templates.sort(Comparator.naturalOrder());
        return templates;
    }

    public static List<String> templateKeys() {
        return gatherAll().stream().map(SFMDrawTemplate::key).toList();
    }

    public static @Nullable SFMDrawTemplate findByName(String templateName) {
        String normalized = normalizeTemplateName(templateName);
        if (normalized.isBlank()) {
            return null;
        }

        List<SFMDrawTemplate> templates = gatherAll();
        for (SFMDrawTemplate template : templates) {
            if (normalized.equals(normalizeTemplateName(template.key()))) {
                return template;
            }
        }
        for (SFMDrawTemplate template : templates) {
            if (normalized.equals(normalizeTemplateName(template.resourcePath()))) {
                return template;
            }
        }
        for (SFMDrawTemplate template : templates) {
            if (normalized.equals(normalizeTemplateName(lastPathSegment(template.resourcePath())))) {
                return template;
            }
        }
        for (SFMDrawTemplate template : templates) {
            if (normalized.equals(normalizeTemplateName(template.displayName()))) {
                return template;
            }
        }
        return null;
    }

    private static @Nullable SFMDrawTemplate fromPath(
            Path templateRoot,
            Path path
    ) {
        String programString = readProgramStringFromPath(path);
        if (programString == null) {
            return null;
        }

        String resourcePath = templateRoot.relativize(path).toString().replace('\\', '/');
        String key = stripTemplateExtension(resourcePath);

        ProgramBuildResult result = new ProgramBuilder(programString).build();
        String displayName = result.program() == null ? key : result.program().name();
        if (displayName.isBlank()) {
            displayName = key;
        }

        return new SFMDrawTemplate(key, displayName, resourcePath, programString);
    }

    private static @Nullable String readProgramStringFromPath(Path path) {
        try (BufferedReader reader = Files.newBufferedReader(path)) {
            String programString = reader.lines().collect(Collectors.joining("\n"));
            return interpolateTemplateProgramString(programString);
        } catch (IOException e) {
            SFM.LOGGER.error("Failed to read draw template from {}", path, e);
            return null;
        }
    }

    private static String interpolateTemplateProgramString(String programString) {
        if (!programString.contains("$REPLACE_RESOURCE_TYPES_HERE$")) {
            return programString;
        }
        try {
            List<? extends String> disallowedResourceTypesForTransfer
                    = SFMConfig.getOrDefault(SFMConfig.SERVER_CONFIG.disallowedResourceTypesForTransfer);

            String replacement = SFMResourceTypes.registry().keys()
                    .stream()
                    .map(ResourceLocation::getPath)
                    .map(resourceTypeId -> {
                        String text = "";
                        if (disallowedResourceTypesForTransfer.contains(resourceTypeId)) {
                            text += "-- (disallowed in config) ";
                        }
                        text += "INPUT " + resourceTypeId + ":: FROM a";
                        return text;
                    })
                    .collect(Collectors.joining("\n    "));

            return programString.replace("$REPLACE_RESOURCE_TYPES_HERE$", replacement);
        } catch (Throwable t) {
            SFM.LOGGER.debug("Skipping draw template resource-type interpolation because registries are not bootstrapped", t);
            return programString.replace(
                    "$REPLACE_RESOURCE_TYPES_HERE$",
                    "-- resource type interpolation unavailable in this environment"
            );
        }
    }

    private static boolean isTemplatePath(Path path) {
        if (!Files.isRegularFile(path)) {
            return false;
        }
        String pathString = path.toString().toLowerCase(Locale.ROOT);
        return pathString.endsWith(".sfml") || pathString.endsWith(".sfm");
    }

    private static @Nullable Path resolveTemplateRoot() {
        try {
            var modFileInfo = ModList.get().getModFileById(SFM.MOD_ID);
            if (modFileInfo != null) {
                Path modTemplateRoot = modFileInfo.getFile().findResource(TEMPLATE_PROGRAMS_PATH);
                if (Files.exists(modTemplateRoot)) {
                    return modTemplateRoot;
                }
            }
        } catch (Throwable t) {
            SFM.LOGGER.debug("Falling back to IDE draw template path discovery", t);
        }

        if (Files.exists(IDE_TEMPLATE_PROGRAMS_PATH)) {
            return IDE_TEMPLATE_PROGRAMS_PATH;
        }
        return null;
    }

    private static String normalizeTemplateName(String templateName) {
        return stripTemplateExtension(templateName.strip())
                .replace('\\', '/')
                .toLowerCase(Locale.ROOT)
                .replace('-', '_')
                .replace(' ', '_');
    }

    private static String stripTemplateExtension(String templateName) {
        String normalized = templateName;
        if (normalized.toLowerCase(Locale.ROOT).endsWith(".sfml")) {
            return normalized.substring(0, normalized.length() - 5);
        }
        if (normalized.toLowerCase(Locale.ROOT).endsWith(".sfm")) {
            return normalized.substring(0, normalized.length() - 4);
        }
        return normalized;
    }

    private static String lastPathSegment(String resourcePath) {
        int separatorIndex = resourcePath.lastIndexOf('/');
        return separatorIndex >= 0 ? resourcePath.substring(separatorIndex + 1) : resourcePath;
    }
}