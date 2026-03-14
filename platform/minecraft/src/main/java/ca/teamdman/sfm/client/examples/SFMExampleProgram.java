package ca.teamdman.sfm.client.examples;

import ca.teamdman.sfm.common.config.SFMConfig;
import ca.teamdman.sfm.common.registry.SFMResourceTypes;
import ca.teamdman.sfml.program_builder.SFMLProgramBuildResult;
import ca.teamdman.sfml.program_builder.SFMLProgramBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public record SFMExampleProgram(
        String displayName,

        String programString
) implements Comparable<SFMExampleProgram> {

    public static List<SFMExampleProgram> gatherAll() {

        // Discover the example resources
        Map<ResourceLocation, Resource> exampleResources = Minecraft.getInstance()
                .getResourceManager()
                .listResources("template_programs", SFMExampleProgram::isSFMLProgram);

        // Initialize results collection
        List<SFMExampleProgram> rtn = new ArrayList<>();

        // Read the resources into the results collection
        for (Map.Entry<ResourceLocation, Resource> exampleResource : exampleResources.entrySet()) {
            ResourceLocation path = exampleResource.getKey();
            Resource resource = exampleResource.getValue();
            SFMExampleProgram program = fromResource(path, resource);
            if (program != null) {
                rtn.add(program);
            }
        }

        // Sort the results before returning
        rtn.sort(Comparator.naturalOrder());

        // Return
        return rtn;
    }

    public static SFMExampleProgram getChangelog() {

        for (SFMExampleProgram e : gatherAll()) {
            if (e.displayName().equals("Changelog")) {
                return e;
            }
        }
        return new SFMExampleProgram(
                "Failed to load changelog",
                "Failed to load changelog"
        );
    }

    public static boolean isSFMLProgram(ResourceLocation path) {

        return path.getPath().endsWith(".sfml") || path.getPath().endsWith(".sfm");
    }

    @Override
    public int compareTo(@NotNull SFMExampleProgram o) {

        return this.displayName().compareTo(o.displayName());
    }

    private static @Nullable SFMExampleProgram fromResource(
            ResourceLocation path,
            Resource resource
    ) {

        // Read the program string from the resource
        String programString = readProgramStringFromResource(resource);
        if (programString == null) return null;

        // Build the program
        SFMLProgramBuildResult result = new SFMLProgramBuilder(programString).build();
        String displayName = result.maybeProgram() == null
                             ? String.format("(compile failed) %s", path.toString())
                             : result.maybeProgram().name();
        return new SFMExampleProgram(displayName, programString);
    }

    private static @Nullable String readProgramStringFromResource(Resource resource) {

        try (BufferedReader reader = resource.openAsReader()) {

            // Join the lines to a single string
            String programString = reader.lines().collect(Collectors.joining("\n"));

            // If the result contains no variables that need interpolation, return the result
            if (!programString.contains("$REPLACE_RESOURCE_TYPES_HERE$")) {
                return programString;
            }

            // Get the disallowed resource types
            List<? extends String> disallowedResourceTypesForTransfer
                    = SFMConfig.getOrDefault(SFMConfig.SERVER_CONFIG.disallowedResourceTypesForTransfer);

            // Build the replacement string
            String replacement = SFMResourceTypes.registry().keys()
                    .stream()
                    .map(ResourceLocation::getPath)
                    .map(e -> {
                        String text = "";
                        if (disallowedResourceTypesForTransfer.contains(e))
                            text += "-- (disallowed in config) ";
                        text += "INPUT " + e + ":: FROM a";
                        return text;
                    })
                    .collect(Collectors.joining("\n    "));

            // Perform the replacement
            programString = programString.replace("$REPLACE_RESOURCE_TYPES_HERE$", replacement);

            // Return the result
            return programString;

        } catch (IOException ignored) {
            return null;
        }
    }

}
