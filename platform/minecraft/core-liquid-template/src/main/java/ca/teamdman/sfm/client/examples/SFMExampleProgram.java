package ca.teamdman.sfm.client.examples;

import ca.teamdman.sfm.common.config.SFMConfig;
import ca.teamdman.sfm.common.registry.registration.SFMResourceTypes;
import ca.teamdman.sfml.program_builder.ProgramBuildResult;
import ca.teamdman.sfml.program_builder.ProgramBuilder;
import net.minecraft.client.Minecraft;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
import net.minecraft.resources.ResourceLocation;
{% when '26.1.2' %}
import net.minecraft.resources.Identifier;
{% endcase %}
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
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
        Map<ResourceLocation, Resource> exampleResources = Minecraft.getInstance()
{% when '26.1.2' %}
        Map<Identifier, Resource> exampleResources = Minecraft.getInstance()
{% endcase %}
                .getResourceManager()
                .listResources("template_programs", SFMExampleProgram::isSFMLProgram);

        // Initialize results collection
        List<SFMExampleProgram> rtn = new ArrayList<>();

        // Read the resources into the results collection
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
        for (Map.Entry<ResourceLocation, Resource> exampleResource : exampleResources.entrySet()) {
            ResourceLocation path = exampleResource.getKey();
{% when '26.1.2' %}
        for (Map.Entry<Identifier, Resource> exampleResource : exampleResources.entrySet()) {
            Identifier path = exampleResource.getKey();
{% endcase %}
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

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
    public static boolean isSFMLProgram(ResourceLocation path) {
{% when '26.1.2' %}
    public static boolean isSFMLProgram(Identifier path) {
{% endcase %}

        return path.getPath().endsWith(".sfml") || path.getPath().endsWith(".sfm");
    }

    @Override
    public int compareTo(@NotNull SFMExampleProgram o) {

        return this.displayName().compareTo(o.displayName());
    }

    private static @Nullable SFMExampleProgram fromResource(
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
            ResourceLocation path,
{% when '26.1.2' %}
            Identifier path,
{% endcase %}
            Resource resource
    ) {

        // Read the program string from the resource
        String programString = readProgramStringFromResource(resource);
        if (programString == null) return null;

        // Build the program
        ProgramBuildResult result = new ProgramBuilder(programString).build();
        String displayName = result.program() == null
                             ? String.format("(compile failed) %s", path.toString())
                             : result.program().name();
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
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
                    .map(ResourceLocation::getPath)
{% when '26.1.2' %}
                    .map(Identifier::getPath)
{% endcase %}
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
