package ca.teamdman.sfm.client.examples;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.config.SFMConfig;
import ca.teamdman.sfm.common.registry.registration.SFMResourceTypes;
import ca.teamdman.sfm.common.util.SFMResourceLocation;
import ca.teamdman.sfml.program_builder.ProgramBuildResult;
import ca.teamdman.sfml.program_builder.ProgramBuilder;
import com.github.bsideup.jabel.Desugar;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IResource;
import net.minecraft.util.ResourceLocation;
import org.apache.commons.io.IOUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Desugar
public record SFMExampleProgram(
        String displayName,

        String programString
) implements Comparable<SFMExampleProgram> {

    public static List<SFMExampleProgram> gatherAll() {
        FileSystem filesystem = null;

        var resources = Minecraft.getMinecraft()
                .getResourceManager();

        List<SFMExampleProgram> rtn = new ArrayList<>();


        try {
            URL url = SFM.class.getResource("/assets/superfactorymanager/template_programs");

            if (url != null) {
                URI uri = url.toURI();
                Path path;

                if ("file".equals(uri.getScheme())) {
                    path = Paths.get(SFM.class.getResource("/assets/superfactorymanager/template_programs").toURI());
                } else {
                    if (!"jar".equals(uri.getScheme())) {
                        SFM.LOGGER.error("Unsupported scheme " + uri + " trying to list template programs");
                        return rtn;
                    }

                    filesystem = FileSystems.newFileSystem(uri, Collections.emptyMap());
                    path = filesystem.getPath("/assets/superfactorymanager/template_programs");
                }

                try (
                        Stream<Path> stream = Files.walk(path)
                )
                {
                    Iterator<Path> iterator = stream.iterator();
                    while (iterator.hasNext()) {
                        Path path1 = iterator.next();

                        Path path2 = path.relativize(path1);
                        String s = path2.toString().replaceAll("\\\\", "/");
                        ResourceLocation resourcelocation = SFMResourceLocation.fromSFMPath("template_programs/" + s);
                        if (SFMExampleProgram.isSFMLProgram(resourcelocation)) {
                            IResource resource = resources.getResource(resourcelocation);

                            SFMExampleProgram program = fromResource(resourcelocation, resource);
                            if (program != null) {
                                rtn.add(program);
                            }
                        }
                    }
                }
            } else {
                SFM.LOGGER.error("Couldn't find template programs root");
            }
        } catch (IOException | URISyntaxException urisyntaxexception) {
            SFM.LOGGER.error("Couldn't get a list of all recipe files", urisyntaxexception);
            return rtn;
        } finally {
            IOUtils.closeQuietly(filesystem);
        }
        rtn.sort(Comparator.naturalOrder());



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
            IResource resource
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

    private static @Nullable String readProgramStringFromResource(IResource resource) {

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource.getInputStream()))) {

            // Join the lines to a single string
            String programString = reader.lines().collect(Collectors.joining("\n"));

            // If the result contains no variables that need interpolation, return the result
            if (!programString.contains("$REPLACE_RESOURCE_TYPES_HERE$")) {
                return programString;
            }

            // Get the disallowed resource types
            List<? extends String> disallowedResourceTypesForTransfer
                    = Arrays.asList(SFMConfig.server.disallowedResourceTypesForTransfer);

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
