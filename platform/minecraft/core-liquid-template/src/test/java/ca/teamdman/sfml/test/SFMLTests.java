package ca.teamdman.sfml.test;

import ca.teamdman.sfm.client.ProgramTokenContextActions;
import ca.teamdman.sfm.client.text_styling.ProgramSyntaxHighlightingHelper;
import ca.teamdman.sfm.common.config.SFMConfig;
import ca.teamdman.sfm.common.net.ServerboundLabelGunSetActiveLabelPacket;
{% if features.client_properties %}
import ca.teamdman.sfm.properties.SFMProperties;
{% endif %}
import ca.teamdman.sfml.ast.ResourceIdentifier;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% if features.packet_transport_private %}
import ca.teamdman.sfml.ast.*;
import ca.teamdman.sfm.common.value.SFMValuePattern;
{% endif %}
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
{% endcase %}
import com.google.common.collect.Sets;
{% if features.client_theme %}
import net.minecraft.ChatFormatting;
{% endif %}
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1" %}
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;
{% when "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
{% endcase %}
import org.apache.commons.compress.utils.FileNameUtils;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
{% if features.client_properties %}
{% else %}
import java.nio.file.Paths;
{% endif %}
import java.util.Arrays;
import java.util.stream.Collectors;

import static ca.teamdman.sfml.test.SFMLTestHelpers.*;
import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("unchecked")
public class SFMLTests {

    @Test
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% if features.packet_transport_private %}
    public void packetComputationLanguageSurfaceBuildsDefinitionsAndStatements() {
        String source = """
                let me be player of TeamDman
                let JobId be like guid
                let Request be like object with field type of "Request"
                    and field prompt like string
                    and field JobId
                let Response be like object with field type of "Response"
                    and field JobId like guid
                    and field text like string

                every 20 ticks do
                    input WITH CAPABILITY sfm:text from chest as userinput
                    let userinputstring be string of invoke sfm:text/read with userinput
                    let request be Request with field prompt of userinputstring
                        and field JobId of new guid
                    create input sfm:packet with request
                    broadcast to me
                    output to chest1
                end

                every 20 ticks do
                    input like Response from inbox as r
                    output to responsechest
                end
                """;

        assertNoCompileErrors(source);
        Program program = compile(source);
        assertEquals("TeamDman", program.definitions().player("ME").orElseThrow());
        assertSame(SFMValuePattern.GUID, program.definitions().pattern("jobid").orElseThrow());
        assertTrue(program.definitions().pattern("request").orElseThrow() instanceof SFMValuePattern.ObjectPattern);
        assertEquals(
                java.util.List.of(
                        InputStatement.class,
                        LetStatement.class,
                        LetStatement.class,
                        CreateInputStatement.class,
                        BroadcastStatement.class,
                        OutputStatement.class
                ),
                program.triggers().get(0).getStatements().get(0).getStatements().stream()
                        .map(Object::getClass)
                        .toList()
        );
        assertNoCompileErrors(program.toString());
        assertTrue(program.astBuilder().getNodesUnderCursor(source.indexOf("broadcast"))
                                  .stream()
                                  .map(com.mojang.datafixers.util.Pair::getFirst)
                                  .anyMatch(BroadcastStatement.class::isInstance));
        assertTrue(program.astBuilder().getNodesUnderCursor(source.indexOf("let Request"))
                                  .stream()
                                  .map(com.mojang.datafixers.util.Pair::getFirst)
                                  .anyMatch(ProgramPatternDeclaration.class::isInstance));
    }

    @Test
{% endif %}
{% if features.client_inbox %}
    public void addressedBroadcastPreservesLegacyFormAndSourceRoundTrip() {
        String source = """
                let viewer be player of TeamDman
                every 20 ticks do
                    broadcast to viewer
                    broadcast to viewer channel sfm:dashboard_state
                end
                """;
        assertNoCompileErrors(source);
        Program program = compile(source);
        var statements = program.triggers().get(0).getStatements().get(0).getStatements();
        BroadcastStatement legacy = (BroadcastStatement) statements.get(0);
        BroadcastStatement addressed = (BroadcastStatement) statements.get(1);
        assertNull(legacy.channel());
        assertEquals("sfm:dashboard_state", addressed.channel().toString());
        assertNoCompileErrors(program.toString());
    }

    @Test
{% endif %}
{% if features.packet_computation or features.packet_transport_private or features.client_inbox %}
    public void newKeywordsRemainLegalLegacyLabels() {
        assertNoCompileErrors("""
                every 20 ticks do
                    input from let
                    output to like
                    input from object
                    output to broadcast
                    input from channel
                end
                """);
    }

    @Test
{% endif %}
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
{% endcase %}
    public void resourceIdentifierClassLoadingRegression() {
        new ResourceIdentifier<ItemStack, Item, IItemHandler>("stone");
    }

    @Test
    public void simpleComparisons() {
        assertNoCompileErrors(
                """
                            name "hello world"
                        
                            every 20 ticks do
                                input from a
                                if a has gt 100 iron then
                                    output to b
                                else if a has gt 50 iron then
                                    output to c
                                else if a has gt 10 iron then
                                    output to d
                                else if a has gt 2 iron then
                                    output to e
                                end
                            end
                        """
        );
    }


    @Test
    public void resource1() {
        var input = """
                    name "hello world"
                
                    every 20 ticks do
                        input item:minecraft:stick from a
                    end
                """;
        assertNoCompileErrors(input);
        var program = compile(input);
        assertEquals(
                Sets.newHashSet(new ResourceIdentifier<FluidStack, Fluid, IFluidHandler>("item", "minecraft", "stick")),
                program.referencedResources()
        );
    }

    @Test
    public void resource2() {
        var input = """
                    name "hello world"
                
                    every 20 ticks do
                        input item::stick from a
                    end
                """;
        assertNoCompileErrors(input);
        var program = compile(input);
        assertEquals(
                Sets.newHashSet(new ResourceIdentifier<FluidStack, Fluid, IFluidHandler>("item", ".*", "stick")),
                program.referencedResources()
        );
    }

    @Test
    public void resource3() {
        var input = """
                    name "hello world"
                
                    every 20 ticks do
                        input item::stick from a
                    end
                """;
        assertNoCompileErrors(input);
        var program = compile(input);
        assertEquals(
                Sets.newHashSet(new ResourceIdentifier<FluidStack, Fluid, IFluidHandler>("item", ".*", "stick")),
                program.referencedResources()
        );
    }

    @Test
    public void resource4() {
        var input = """
                    name "hello world"
                
                    every 20 ticks do
                        input stick from a
                    end
                """;
        assertNoCompileErrors(input);
        var program = compile(input);
        assertEquals(
                Sets.newHashSet(new ResourceIdentifier<FluidStack, Fluid, IFluidHandler>("item", ".*", "stick")),
                program.referencedResources()
        );
    }

    @Test
    public void resource5() {
        var input = """
                    name "hello world"
                
                    every 20 ticks do
                        input fluid::water from a
                    end
                """;
        assertNoCompileErrors(input);
        var program = compile(input);
        assertEquals(
                Sets.newHashSet(new ResourceIdentifier<FluidStack, Fluid, IFluidHandler>("fluid", ".*", "water")),
                program.referencedResources()
        );
    }

    @Test
    public void resource6() {
        var input = """
                    name "hello world"
                
                    every 20 ticks do
                        input fluid:minecraft:water from a
                    end
                """;
        assertNoCompileErrors(input);
        var program = compile(input);
        assertEquals(
                Sets.newHashSet(new ResourceIdentifier<FluidStack, Fluid, IFluidHandler>(
                        "fluid",
                        "minecraft",
                        "water"
                )),
                program.referencedResources()
        );
    }

    @Test
    public void resource7() {
        var input = """
                    name "hello world"
                
                    every 20 ticks do
                        input fluid:: from a
                    end
                """;
        assertNoCompileErrors(input);
        var program = compile(input);
        assertEquals(
                Sets.newHashSet(new ResourceIdentifier<FluidStack, Fluid, IFluidHandler>("fluid", ".*", ".*")),
                program.referencedResources()
        );
    }

    @Test
    public void badResource() {
        var input = """
                    name "hello world"
                
                    every 20 ticks do
                        input :fluid:: from a
                    end
                """;
        assertCompileErrorsPresent(input);
    }


    @Test
    public void badTimerIntervalCheckingConfig() {
        var min = SFMConfig.SERVER_CONFIG.timerTriggerMinimumIntervalInTicks.getDefault();
        var template = """
                    name "hello world"
                
                    every X ticks do
                        input from a
                    end
                """;
        for (int i = min - 1; i > 0; i--) {
            String program = template.replace("X", String.valueOf(min - 1));
            assertCompileErrorsPresent(
                    program,
                    new CompileErrors(
                            new IllegalArgumentException("Minimum trigger interval is " + min + " ticks.")
                    )
            );
        }
    }

    @Test
    public void badLabelLength() {
        var template = """
                    name "hello world"
                
                    every 20 ticks do
                        input from X
                    end
                """;
        String program = template.replace("X", "a".repeat(257));
        assertCompileErrorsPresent(
                program,
                new CompileErrors(
                        new IllegalArgumentException(
                                "Maximum label length is "
                                + ServerboundLabelGunSetActiveLabelPacket.MAX_LABEL_LENGTH
                                + " characters. "
                        )
                )
        );
    }

    @Test
    public void emptyLabel() {
        // not particularly useful, but there's not much reason to disallow it
        assertNoCompileErrors("""
            name "hello world"
        
            every 20 ticks do
                input from ""
            end
        """);
    }

    @Test
    public void forgeTimerIntervalPass() {
        var min = SFMConfig.SERVER_CONFIG.timerTriggerMinimumIntervalInTicksWhenOnlyForgeEnergyIO.getDefault();
        assertEquals(1, min);
        var input = """
                    name "hello world"
                
                    every 1 ticks do
                        input forge_energy:: from a
                    end
                """;
        assertNoCompileErrors(input);
    }

    @Test
    public void forgeTimerIntervalFail1() {
        var min = SFMConfig.SERVER_CONFIG.timerTriggerMinimumIntervalInTicksWhenOnlyForgeEnergyIO.getDefault();
        assertEquals(1, min);
        var input = """
                    name "hello world"
                
                    every 0 ticks do
                        input forge_energy:: from a
                    end
                """;
        assertCompileErrorsPresent(input);
    }

    @Test
    public void forgeTimerIntervalFail2() {
        var min = SFMConfig.SERVER_CONFIG.timerTriggerMinimumIntervalInTicksWhenOnlyForgeEnergyIO.getDefault();
        assertEquals(1, min);
        var input = """
                    name "hello world"
                
                    every 1 ticks do
                        input forge_energy:: from a
                        output to b -- this is an item io statement
                    end
                """;
        assertCompileErrorsPresent(input);
    }

    @Test
    public void resource8() {
        var input = """
                    name "hello world"
                
                    every 20 ticks do
                        input forge_energy:forge:energy from a
                    end
                """;
        assertNoCompileErrors(input);
        var program = compile(input);
        assertEquals(
                Sets.newHashSet(new ResourceIdentifier<FluidStack, Fluid, IFluidHandler>(
                        "forge_energy",
                        "forge",
                        "energy"
                )),
                program.referencedResources()
        );
    }

    @Test
    public void resource9() {
        var input = """
                    name "hello world"
                
                    every 20 ticks do
                        input forge_energy:forge:energy from a
                    end
                """;
        assertNoCompileErrors(input);
        var program = compile(input);
        assertEquals(
                Sets.newHashSet(new ResourceIdentifier<FluidStack, Fluid, IFluidHandler>(
                        "forge_energy",
                        "forge",
                        "energy"
                )),
                program.referencedResources()
        );
    }

    @Test
    public void resource10() {
        var input = """
                    name "hello world"
                
                    every 20 ticks do
                        input gas::ethylene from a
                    end
                """;
        assertNoCompileErrors(input);
        var program = compile(input);
        assertEquals(
                Sets.newHashSet(new ResourceIdentifier<FluidStack, Fluid, IFluidHandler>("gas", ".*", "ethylene")),
                program.referencedResources()
        );
    }

    @Test
    public void wildcardResourceIdentifiers() {
        assertNoCompileErrors(
                """
                        name "hello world"
                        
                        every 20 ticks do
                            INPUT fluid:minecraft:water from a TOP SIDE
                            OUTPUT fluid:*:* to b
                            OUTPUT minecraft:* to b
                            OUTPUT *:iron_ingot to b
                            OUTPUT *:*:* to b
                            OUTPUT *:* to b
                            OUTPUT * to b
                            OUTPUT ".*:.*:.*" to b
                            OUTPUT ".*:.*" to b
                            OUTPUT ".*" to b
                        end
                        """
        );
    }

    @Test
    public void quotedResourceIdentifiers() {
        assertNoCompileErrors(
                """
                        EVERY 20 TICKS DO
                            INPUT FROM a
                            OUTPUT "redstone" to b
                            OUTPUT "minecraft:iron_ingot" to b
                            OUTPUT "item:minecraft:gold_ingot" to b
                        END
                        """
        );
    }

    @Test
    public void malformedResourceIdentifier1() {
        var input = """
                EVERY 20 TICKS DO
                    INPUT FROM a
                    OUTPUT minecraft:"redstone" to b
                END
                """;
        assertCompileErrorsPresent(input);
    }

    @Test
    public void malformedResourceIdentifier2() {
        var input = """
                EVERY 20 TICKS DO
                    INPUT FROM a
                    OUTPUT "minecraft":"redstone" to b
                END
                """;
        assertCompileErrorsPresent(input);
    }

    @Test
    public void malformedResourceIdentifier3() {
        var input = """
                EVERY 20 TICKS DO
                    INPUT FROM a
                    OUTPUT "item":minecraft:redstone to b
                END
                """;
        assertCompileErrorsPresent(input);
    }

    @Test
    public void malformedResourceIdentifier4() {
        assertNoCompileErrors(
                """
                        EVERY 20 TICKS DO
                            INPUT FROM a
                            OUTPUT item:minecraft:redstone to b
                        END
                        """
        );
    }

    @Test
    public void malformedResourceIdentifier5() {
        assertNoCompileErrors(
                """
                        EVERY 20 TICKS DO
                            INPUT FROM a
                            OUTPUT minecraft:redstone to b
                        END
                        """
        );
    }

    @Test
    public void malformedResourceIdentifier6() {
        assertNoCompileErrors(
                """
                        EVERY 20 TICKS DO
                            INPUT FROM a
                            OUTPUT redstone to b
                        END
                        """
        );
    }


    @Test
    public void comments() {
        var input = """
                EVERY 20 TICKS DO
                    INPUT FROM a -- hehehehaw
                    OUTPUT "minecraft":"redstone" to b
                END
                """;
        assertCompileErrorsPresent(input);
    }

    @Test
    public void syntaxHighlighting1() {
        var rawInput = """
                EVERY 20 TICKS DO
                
                    INPUT FROM a''" -- hehehehaw
                    -- we want there to be no issues highlighting even if errors are present
                    "'''''
                
                    -- we want to test to make sure whitespace is preserved
                    -- in the
                
                    -- syntax highlighting
                
                    INPUT FROM hehehehehehehehehhe
                
                    OUTPUT stone to b
                END
                """.stripIndent();
        assertCompileErrorsPresent(rawInput);
        var lines = rawInput.split("\n", -1);

        var colouredLines = ProgramSyntaxHighlightingHelper.withSyntaxHighlighting(rawInput, false);
        String colouredInput = colouredLines.stream().map(Component::getString).collect(Collectors.joining("\n"));

        assertEquals(rawInput, colouredInput);

        // newlines should not be present
        // instead, each line should be its own component
        assertFalse(colouredLines.stream().anyMatch(x -> x.getString().contains("\n")));

        assertEquals(lines.length, colouredLines.size());
        for (int i = 0; i < lines.length; i++) {
            assertEquals(lines[i], colouredLines.get(i).getString());
        }
    }

{% if features.client_theme %}
    @Test
    public void syntaxHighlightingTokenRanges() {
        var rawInput = "EVERY 20 TICKS DO\nEND";

        var highlights = ProgramSyntaxHighlightingHelper.getTokenHighlights(rawInput);

        var every = highlights.stream()
                .filter(highlight -> highlight.text().equals("EVERY"))
                .findFirst()
                .orElseThrow();
        assertEquals(0, every.startIndex());
        assertEquals(4, every.stopIndex());
        assertEquals(0xFF5555FF, every.colour());

        var ticks = highlights.stream()
                .filter(highlight -> highlight.text().equals("TICKS"))
                .findFirst()
                .orElseThrow();
        assertEquals(0xFFFFAA00, ticks.colour());
    }

{% endif %}
    @Test
    public void syntaxHighlighting2() {
        var rawInput = """
                EVERY 20 TICKS DO
                
                    INPUT FROM a
                    INPUT FROM hehehehehehehehehhe
                
                    OUTPUT stone to b
                END
                """.stripIndent();
        assertNoCompileErrors(rawInput);
        var lines = rawInput.split("\n", -1);

        var colouredLines = ProgramSyntaxHighlightingHelper.withSyntaxHighlighting(rawInput, false);
        String colouredInput = colouredLines.stream().map(Component::getString).collect(Collectors.joining("\n"));

        assertEquals(rawInput, colouredInput);

        // newlines should not be present
        // instead, each line should be its own component
        assertFalse(colouredLines.stream().anyMatch(x -> x.getString().contains("\n")));

        assertEquals(lines.length, colouredLines.size());
        for (int i = 0; i < lines.length; i++) {
            assertEquals(lines[i], colouredLines.get(i).getString());
        }
    }

    @Test
    public void syntaxHighlightingWhitespaceRegression1() {
        // the empty newline is important
        var rawInput = """
                    EVERY 20 TICKS DO
                --test
                        INPUT FROM a
                        OUTPUT TO b
                    END""";
        assertNoCompileErrors(rawInput);

        var lines = rawInput.split("\n", -1);

        var colouredLines = ProgramSyntaxHighlightingHelper.withSyntaxHighlighting(rawInput, false);
        String colouredInput = colouredLines.stream().map(Component::getString).collect(Collectors.joining("\n"));

        assertEquals(rawInput, colouredInput);

        // newlines should not be present
        // instead, each line should be its own component
        assertFalse(colouredLines.stream().anyMatch(x -> x.getString().contains("\n")));

        assertEquals(lines.length, colouredLines.size());
        for (int i = 0; i < lines.length; i++) {
            assertEquals(lines[i], colouredLines.get(i).getString());
        }
    }

    @Test
    public void syntaxHighlightingWhitespaceRegression2() {
        // the empty newline is important
        var rawInput = """
                
                EVERY 20 TICKS DO
                    INPUT FROM a
                    OUTPUT TO b
                END""";
        assertNoCompileErrors(rawInput);

        var lines = rawInput.split("\n", -1);

        var colouredLines = ProgramSyntaxHighlightingHelper.withSyntaxHighlighting(rawInput, false);
        String colouredInput = colouredLines.stream().map(Component::getString).collect(Collectors.joining("\n"));

        assertEquals(rawInput, colouredInput);

        // newlines should not be present
        // instead, each line should be its own component
        assertFalse(colouredLines.stream().anyMatch(x -> x.getString().contains("\n")));

        assertEquals(lines.length, colouredLines.size());
        for (int i = 0; i < lines.length; i++) {
            assertEquals(lines[i], colouredLines.get(i).getString());
        }
    }


    @Test
    public void syntaxHighlighting3() {
        var rawRawInput = """
                EVERY 20 TICKS DO
                
                    INPUT FROM a
                    INPUT FROM hehehehehehehehehhe
                
                    OUTPUT stone to b
                END
                """.stripIndent();
        String[] rawRawLines = rawRawInput.split("\n");
        for (int i = 0; i < rawRawLines.length; i++) {
            var rawInput = Arrays.stream(rawRawLines, 0, i)
                    .collect(Collectors.joining("\n"));
            var lines = rawInput.split("\n", -1);

            var colouredLines = ProgramSyntaxHighlightingHelper.withSyntaxHighlighting(rawInput, false);
            String colouredInput = colouredLines.stream().map(Component::getString).collect(Collectors.joining("\n"));

            assertEquals(rawInput, colouredInput);

            // newlines should not be present
            // instead, each line should be its own component
            assertFalse(colouredLines.stream().anyMatch(x -> x.getString().contains("\n")));

            assertEquals(lines.length, colouredLines.size());
            for (int j = 0; j < lines.length; j++) {
                assertEquals(lines[j], colouredLines.get(j).getString());
            }
        }
    }


    @Test
    public void syntaxHighlightingUnusedToken() {
        var rawInput = """
                EVERY 20 TICKS DO
                
                    INPUT FROM a
                    INPUT FROM hehehehehehehehehhe=
                
                    OUTPUT stone to b
                END
                """.stripIndent();
        assertCompileErrorsPresent(rawInput);

        var lines = rawInput.split("\n", -1);

        var colouredLines = ProgramSyntaxHighlightingHelper.withSyntaxHighlighting(rawInput, false);
        String colouredInput = colouredLines.stream().map(Component::getString).collect(Collectors.joining("\n"));

        assertEquals(rawInput, colouredInput);

        // newlines should not be present
        // instead, each line should be its own component
        assertFalse(colouredLines.stream().anyMatch(x -> x.getString().contains("\n")));

        assertEquals(lines.length, colouredLines.size());
        for (int i = 0; i < lines.length; i++) {
            assertEquals(lines[i], colouredLines.get(i).getString());
        }
    }


    @Test
    public void booleanHasOperator() {
        assertNoCompileErrors(
                """
                        name "hello world"
                        
                        every 20 ticks do
                            input from a
                            if a has gt 100 energy:minecraft:iron then
                                output to b
                            end
                        end
                        """
        );
    }


    @Test
    public void quotedLabels() {
        assertNoCompileErrors(
                """
                        name "hello world"
                        
                        every 20 ticks do
                            input from "hehe beans 😀"
                            output to "haha benis"
                        end
                        """
        );
    }

    @Test
    public void relativeDirectionLabels() {
        assertNoCompileErrors(
                """
                        every 20 ticks do
                            input from left right, null side
                            output to right left, top side
                        end
                        """
        );
    }

    @Test
    public void basicResourceIdentifier() {
        var identifier = ResourceIdentifier.fromString("wool");
        assertEquals("sfm:item:.*:wool", identifier.toString());
    }


    @Test
    public void demos() throws IOException {
        var examplesPath = findDirectoryUpwards("examples");
{% if features.client_properties %}
        assertNotNull(examplesPath, "Could not locate examples directory starting from " + SFMProperties.userDirectory());
{% else %}
        assertNotNull(examplesPath, "Could not locate examples directory starting from " + System.getProperty("user.dir"));
{% endif %}
        var found = 0;
        try (var ds = Files.newDirectoryStream(examplesPath)) {
            for (var entryPath : ds) {
                var entry = entryPath.toFile();
                if (!"sfm".equals(FileNameUtils.getExtension(entry.getPath()))) continue;
                System.out.println("Reading " + entry);
                var content = Files.readString(entryPath);
                assertNoCompileErrors(content);
                found++;
            }
        }
        assertNotEquals(0, found);
    }

    @Test
    public void templates() throws IOException {
        var examplesPath = findDirectoryUpwards("src/main/resources/assets/sfm/template_programs");
{% if features.client_properties %}
        assertNotNull(examplesPath, "Could not locate template programs directory starting from " + SFMProperties.userDirectory());
{% else %}
        assertNotNull(examplesPath, "Could not locate template programs directory starting from " + System.getProperty("user.dir"));
{% endif %}
        var found = 0;
        try (var ds = Files.newDirectoryStream(examplesPath)) {
            for (var entryPath : ds) {
                var entry = entryPath.toFile();
                assertEquals("sfml", FileNameUtils.getExtension(entry.getPath()));
                System.out.println("Reading " + entry);
                var content = Files.readString(entryPath);
                content = content.replace("$REPLACE_RESOURCE_TYPES_HERE$", "");
                assertNoCompileErrors(content);
                found++;
            }
        }
        assertNotEquals(0, found);
    }

    @Test
    public void symbolUnderCursor1() {
        var programString = """
                NAME "test"
                EVERY 20 TICKS DO
                    INPUT FROM a
                    OUTPUT TO b
                END
                """.stripTrailing().stripIndent();
        var cursorPos = programString.indexOf("INPUT") + 2;
        var x = ProgramTokenContextActions.getContextAction(programString, cursorPos);
        assertTrue(x.isPresent());
    }

    private static Path findDirectoryUpwards(String relativePath) {
{% if features.client_properties %}
        Path cwd = SFMProperties.userDirectory();
{% else %}
        Path cwd = Paths.get(System.getProperty("user.dir"));
{% endif %}
        System.out.println("Starting search for " + relativePath + " from " + cwd);
        for (int i = 0; i < 5; i++) {
            Path candidate = cwd.resolve(relativePath);
            System.out.println("Checking " + candidate);
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
            cwd = cwd.getParent();
            if (cwd == null) break;
        }
        return null;
    }

    @Test
    public void condensedIdentifier1() {
        ResourceIdentifier<?, ?, ?> ident = new ResourceIdentifier<>("sfm", "fluid", "minecraft", "water");
        assertEquals("sfm:fluid:minecraft:water", ident.toString());
        assertEquals("fluid:minecraft:water", ident.toStringCondensed());
    }

    @Test
    public void condensedIdentifier2() {
        ResourceIdentifier<?, ?, ?> ident = new ResourceIdentifier<>("sfm", "item", "minecraft", "stick");
        assertEquals("sfm:item:minecraft:stick", ident.toString());
        assertEquals("minecraft:stick", ident.toStringCondensed());
    }

    @Test
    public void condensedIdentifier3() {
        ResourceIdentifier<?, ?, ?> ident = new ResourceIdentifier<>("sfm", "item", ".*", "stick");
        assertEquals("sfm:item:.*:stick", ident.toString());
        assertEquals("stick", ident.toStringCondensed());
    }

    @Test
    public void condensedIdentifier4() {
        ResourceIdentifier<?, ?, ?> ident = new ResourceIdentifier<>("sfm", "item", ".*", ".*");
        assertEquals("sfm:item:.*:.*", ident.toString());
        assertEquals("", ident.toStringCondensed());
    }

    @Test
    public void condensedIdentifier5() {
        ResourceIdentifier<?, ?, ?> ident = new ResourceIdentifier<>("sfm", "fluid", ".*", ".*");
        assertEquals("sfm:fluid:.*:.*", ident.toString());
        assertEquals("fluid::", ident.toStringCondensed());
    }

    @Test
    public void condensedIdentifier6() {
        ResourceIdentifier<?, ?, ?> ident = new ResourceIdentifier<>("sfm", "fluid", ".*", "lava");
        assertEquals("sfm:fluid:.*:lava", ident.toString());
        assertEquals("fluid::lava", ident.toStringCondensed());
    }

    @Test
    public void condensedIdentifier7() {
        ResourceIdentifier<?, ?, ?> ident = new ResourceIdentifier<>("sfm", "fluid", "minecraft", ".*");
        assertEquals("sfm:fluid:minecraft:.*", ident.toString());
        assertEquals("fluid:minecraft:", ident.toStringCondensed());
    }
}
