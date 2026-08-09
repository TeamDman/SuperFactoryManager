package ca.teamdman.sfml.test;

import ca.teamdman.sfm.client.ProgramTokenContextActions;
import ca.teamdman.sfm.client.text_styling.ProgramSyntaxHighlightingHelper;
import ca.teamdman.sfm.common.config.SFMConfig;
import ca.teamdman.sfm.common.net.ServerboundLabelGunSetActiveLabelPacket;
import ca.teamdman.sfml.ast.ResourceIdentifier;
import com.google.common.collect.Sets;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;
import org.apache.commons.compress.utils.FileNameUtils;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

import static ca.teamdman.sfml.test.SFMLTestHelpers.*;
import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("unchecked")
public class SFMLTests {

    @Test
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
        assertEquals(ChatFormatting.BLUE, every.colour());

        var ticks = highlights.stream()
                .filter(highlight -> highlight.text().equals("TICKS"))
                .findFirst()
                .orElseThrow();
        assertEquals(ChatFormatting.GOLD, ticks.colour());
    }


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
        assertNotNull(examplesPath, "Could not locate examples directory starting from " + System.getProperty("user.dir"));
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
        assertNotNull(examplesPath, "Could not locate template programs directory starting from " + System.getProperty("user.dir"));
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
        Path cwd = Paths.get(System.getProperty("user.dir"));
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

    // ===== STRUCT TESTS =====

    @Test
    public void structBasicDefinition() {
        assertNoCompileErrors(
                """
                        NAME "Struct Test"

                        struct Furnace
                            input: TOP SIDE SLOTS 0
                            fuel: BOTTOM SIDE SLOTS 1
                            output: BOTTOM SIDE SLOTS 2
                        end

                        let smelter = Furnace
                        every 20 ticks do
                            input from ore_chest
                            output to smelter using input
                        end
                        """
        );
    }

    @Test
    public void structWithUsingClause() {
        var input = """
                NAME "Struct Using Test"

                struct Furnace
                    input: TOP SIDE SLOTS 0
                    output: BOTTOM SIDE SLOTS 2
                end

                let smelter = Furnace
                every 20 ticks do
                    input from chest
                    output to smelter using input

                    forget

                    input from smelter using output
                    output to storage
                end
                """;
        assertNoCompileErrors(input);
        var program = compile(input);
        assertEquals(1, program.structDefinitions().size());
        assertEquals(1, program.letStatements().size());
        assertEquals("Furnace", program.structDefinitions().get(0).name());
        assertEquals("smelter", program.letStatements().get(0).variableName());
    }

    @Test
    public void structWithFieldOverride() {
        var input = """
                NAME "Field Override Test"

                struct Furnace
                    input: TOP SIDE SLOTS 0
                end

                let smelter = Furnace WITH input: NORTH SIDE SLOTS 1-2

                every 20 ticks do
                    input from chest
                    output to smelter using input
                end
                """;
        assertNoCompileErrors(input);
    }

    @Test
    public void structUnknownStruct() {
        var input = """
                NAME "Unknown Struct Test"

                let smelter = UnknownStruct
                every 20 ticks do
                    input from chest
                end
                """;
        assertCompileErrorsPresent(input);
    }

    @Test
    public void structUnknownVariable() {
        var input = """
                NAME "Unknown Variable Test"

                struct Furnace
                    input: TOP SIDE
                end

                let smelter = Furnace
                every 20 ticks do
                    input from unknown_var using input
                end
                """;
        assertCompileErrorsPresent(input);
    }

    @Test
    public void structUnknownField() {
        var input = """
                NAME "Unknown Field Test"

                struct Furnace
                    input: TOP SIDE
                end

                let smelter = Furnace
                every 20 ticks do
                    input from smelter using unknown_field
                end
                """;
        assertCompileErrorsPresent(input);
    }

    @Test
    public void structDuplicateDefinition() {
        var input = """
                NAME "Duplicate Struct Test"

                struct Furnace
                    input: TOP SIDE
                end

                struct Furnace
                    output: BOTTOM SIDE
                end

                let smelter = Furnace
                every 20 ticks do
                    input from chest
                end
                """;
        assertCompileErrorsPresent(input);
    }

    @Test
    public void structDuplicateField() {
        var input = """
                NAME "Duplicate Field Test"

                struct Furnace
                    input: TOP SIDE
                    input: BOTTOM SIDE
                end

                let smelter = Furnace
                every 20 ticks do
                    input from chest
                end
                """;
        assertCompileErrorsPresent(input);
    }

    @Test
    public void structSideOverride() {
        var input = """
                NAME "Side Override Test"

                struct Furnace
                    input: TOP SIDE SLOTS 0
                end

                let smelter = Furnace
                every 20 ticks do
                    input from chest
                    output to smelter using input BOTTOM SIDE
                end
                """;
        assertNoCompileErrors(input);
    }

    @Test
    public void structSlotOnlyField() {
        assertNoCompileErrors(
                """
                        NAME "Slot Only Field Test"

                        struct Storage
                            main: SLOTS 0-26
                            hotbar: SLOTS 27-35
                        end

                        let inv = Storage
                        every 20 ticks do
                            input from inv using main
                            output to inv using hotbar
                        end
                        """
        );
    }

    // ===== PROTOCOL TESTS =====

    @Test
    public void protocolBasicDefinition() {
        assertNoCompileErrors(
                """
                        NAME "Protocol Test"

                        protocol Smeltable
                            input: sidequalifier slotqualifier
                            fuel: sidequalifier slotqualifier
                            output: sidequalifier slotqualifier
                        end

                        struct Furnace : Smeltable
                            input: TOP SIDE SLOTS 0
                            fuel: BOTTOM SIDE SLOTS 1
                            output: BOTTOM SIDE SLOTS 2
                        end

                        let smelter = Furnace
                        every 20 ticks do
                            input from chest
                            output to smelter using input
                        end
                        """
        );
    }

    @Test
    public void protocolMultipleImplementation() {
        assertNoCompileErrors(
                """
                        NAME "Multiple Protocol Test"

                        protocol HasInput
                            input: sidequalifier slotqualifier
                        end

                        protocol HasOutput
                            output: sidequalifier slotqualifier
                        end

                        struct Machine : HasInput, HasOutput
                            input: TOP SIDE SLOTS 0
                            output: BOTTOM SIDE SLOTS 1
                        end

                        let machine = Machine
                        every 20 ticks do
                            input from chest
                            output to machine using input
                        end
                        """
        );
    }

    @Test
    public void protocolUnknownProtocol() {
        var input = """
                NAME "Unknown Protocol Test"

                struct Furnace : UnknownProtocol
                    input: TOP SIDE SLOTS 0
                end

                let smelter = Furnace
                every 20 ticks do
                    input from chest
                end
                """;
        assertCompileErrorsPresent(input);
    }

    @Test
    public void protocolMissingField() {
        var input = """
                NAME "Missing Protocol Field Test"

                protocol Smeltable
                    input: sidequalifier slotqualifier
                    output: sidequalifier slotqualifier
                end

                struct Furnace : Smeltable
                    input: TOP SIDE SLOTS 0
                end

                let smelter = Furnace
                every 20 ticks do
                    input from chest
                end
                """;
        assertCompileErrorsPresent(input);
    }

    @Test
    public void protocolFieldTypeMismatch() {
        var input = """
                NAME "Protocol Field Type Mismatch Test"

                protocol Smeltable
                    input: sidequalifier slotqualifier
                end

                struct Furnace : Smeltable
                    input: 42
                end

                let smelter = Furnace
                every 20 ticks do
                    input from chest
                end
                """;
        assertCompileErrorsPresent(input);
    }

    @Test
    public void protocolDuplicateDefinition() {
        var input = """
                NAME "Duplicate Protocol Test"

                protocol Smeltable
                    input: sidequalifier
                end

                protocol Smeltable
                    output: sidequalifier
                end

                every 20 ticks do
                    input from chest
                end
                """;
        assertCompileErrorsPresent(input);
    }

    // ===== MACRO TESTS =====

    @Test
    public void macroBasicDefinition() {
        assertNoCompileErrors(
                """
                        NAME "Macro Test"

                        macro transfer(source, dest)
                            input from source
                            output to dest
                        end

                        every 20 ticks do
                            DO transfer(chest_a, chest_b)
                        end
                        """
        );
    }

    @Test
    public void macroWithProtocolConstraint() {
        assertNoCompileErrors(
                """
                        NAME "Macro with Protocol Test"

                        protocol Smeltable
                            input: sidequalifier slotqualifier
                            output: sidequalifier slotqualifier
                        end

                        struct Furnace : Smeltable
                            input: TOP SIDE SLOTS 0
                            output: BOTTOM SIDE SLOTS 1
                        end

                        macro smelt(machine: Smeltable, source, dest)
                            input from source
                            output to machine using input
                            forget
                            input from machine using output
                            output to dest
                        end

                        let furnace = Furnace
                        every 20 ticks do
                            DO smelt(furnace, ore_chest, ingot_chest)
                        end
                        """
        );
    }

    @Test
    public void macroUnknownMacro() {
        var input = """
                NAME "Unknown Macro Test"

                every 20 ticks do
                    DO unknown_macro(chest_a, chest_b)
                end
                """;
        assertCompileErrorsPresent(input);
    }

    @Test
    public void macroWrongArgCount() {
        var input = """
                NAME "Wrong Arg Count Test"

                macro transfer(source, dest)
                    input from source
                    output to dest
                end

                every 20 ticks do
                    DO transfer(chest_a)
                end
                """;
        assertCompileErrorsPresent(input);
    }

    @Test
    public void macroProtocolConstraintNotMet() {
        var input = """
                NAME "Protocol Constraint Not Met Test"

                protocol Smeltable
                    input: sidequalifier slotqualifier
                end

                struct NonSmeltable
                    other: TOP SIDE
                end

                macro smelt(machine: Smeltable)
                    input from machine using input
                end

                let device = NonSmeltable
                every 20 ticks do
                    DO smelt(device)
                end
                """;
        assertCompileErrorsPresent(input);
    }

    @Test
    public void macroDuplicateDefinition() {
        var input = """
                NAME "Duplicate Macro Test"

                macro transfer(a, b)
                    input from a
                end

                macro transfer(x, y)
                    output to x
                end

                every 20 ticks do
                    DO transfer(chest_a, chest_b)
                end
                """;
        assertCompileErrorsPresent(input);
    }

    @Test
    public void macroWithForget() {
        assertNoCompileErrors(
                """
                        NAME "Macro with Forget Test"

                        macro process(source, dest)
                            input from source
                            output to dest
                            forget
                        end

                        every 20 ticks do
                            @process(chest_a, chest_b)
                            @process(chest_c, chest_d)
                        end
                        """
        );
    }

    @Test
    public void macroWithResourceLimits() {
        assertNoCompileErrors(
                """
                        NAME "Macro with Resource Limits Test"

                        macro limited_transfer(source, dest)
                            input 64 iron from source
                            output 64 iron to dest
                        end

                        every 20 ticks do
                            DO limited_transfer(chest_a, chest_b)
                        end
                        """
        );
    }

    @Test
    public void macroWithRetain() {
        assertNoCompileErrors(
                """
                        NAME "Macro with Retain Test"

                        protocol Smeltable
                            input: sidequalifier slotqualifier
                            fuel: sidequalifier slotqualifier
                            output: sidequalifier slotqualifier
                        end

                        struct Furnace : Smeltable
                            input: TOP SIDE SLOTS 0
                            fuel: BOTTOM SIDE SLOTS 1
                            output: BOTTOM SIDE SLOTS 0
                        end

                        macro smelt(machine: Smeltable, ore_source, fuel_source, dest)
                            input from ore_source
                            output retain 2 to each machine using input

                            input from fuel_source
                            output retain 2 to each machine using fuel

                            forget

                            input from machine using output
                            output to dest
                        end

                        let furnace = Furnace
                        every 20 ticks do
                            DO smelt(furnace, ore_chest, fuel_chest, result_chest)
                        end
                        """
        );
    }

    @Test
    public void macroExpansionVerification() {
        var input = """
                NAME "Macro Expansion Verification Test"

                macro transfer(source, dest)
                    input from source
                    output to dest
                end

                every 20 ticks do
                    DO transfer(chest_a, chest_b)
                end
                """;
        assertNoCompileErrors(input);
        var program = compile(input);

        // Verify structure
        assertEquals(1, program.triggers().size());
        var trigger = program.triggers().get(0);
        var block = trigger.getBlock();
        var statements = block.getStatements();

        // Should have 1 expand statement
        assertEquals(1, statements.size());
        assertTrue(statements.get(0) instanceof ca.teamdman.sfml.ast.ExpandStatement);

        // Verify the expand statement has 2 expanded statements (input + output)
        var expandStmt = (ca.teamdman.sfml.ast.ExpandStatement) statements.get(0);
        assertEquals(2, expandStmt.expandedStatements().size());
        assertTrue(expandStmt.expandedStatements().get(0) instanceof ca.teamdman.sfml.ast.InputStatement);
        assertTrue(expandStmt.expandedStatements().get(1) instanceof ca.teamdman.sfml.ast.OutputStatement);
    }

    // ===== LIBRARY TESTS =====

    @Test
    public void libraryStatement() {
        // Library statements parse correctly but resolution fails without in-game library blocks
        assertCompileErrorsPresent(
                """
                        NAME "Library Test"

                        use library "factory_config"

                        every 20 ticks do
                            input from chest
                        end
                        """,
                new IllegalArgumentException("Library 'factory_config' not found in cable network")
        );
    }

    @Test
    public void multipleLibraries() {
        // Library statements parse correctly but resolution fails without in-game library blocks
        assertCompileErrorsPresent(
                """
                        NAME "Multiple Libraries Test"

                        use library "factory_config"
                        use library "shared_macros"

                        every 20 ticks do
                            input from chest
                        end
                        """,
                new IllegalArgumentException("Library 'factory_config' not found in cable network")
        );
    }

    @Test
    public void libraryWithInputOutputProtocolStruct() {
        String librarySource = """
            NAME "io_lib"

            protocol HasInput
                input: sidequalifier slotqualifier
            end

            protocol HasOutput
                output: sidequalifier slotqualifier
            end

            struct IODevice : HasInput, HasOutput
                input: TOP SIDE SLOTS 0
                output: BOTTOM SIDE SLOTS 1
            end
            """;

        Map<String, String> libraries = Map.of("io_lib", librarySource);

        String managerProgram = """
            NAME "IO Manager"

            use library "io_lib"

            let device = IODevice
            every 20 ticks do
                input from source_chest
                output to device using input
                forget
                input from device using output
                output to dest_chest
            end
            """;

        assertNoCompileErrorsWithLibraries(managerProgram, libraries);
    }

    @Test
    public void libraryWithTransferMacro() {
        String librarySource = """
            NAME "transfer_lib"

            protocol HasInput
                input: sidequalifier slotqualifier
            end

            protocol HasOutput
                output: sidequalifier slotqualifier
            end

            struct Processor : HasInput, HasOutput
                input: TOP SIDE SLOTS 0
                output: BOTTOM SIDE SLOTS 1
            end

            macro transfer_through(machine: HasInput, machine2: HasOutput, src, dst)
                input from src
                output to machine using input
                forget
                input from machine2 using output
                output to dst
            end
            """;

        Map<String, String> libraries = Map.of("transfer_lib", librarySource);

        String managerProgram = """
            NAME "Transfer Manager"

            use library "transfer_lib"

            let processor = Processor
            every 20 ticks do
                DO transfer_through(processor, processor, input_chest, output_chest)
            end
            """;

        assertNoCompileErrorsWithLibraries(managerProgram, libraries);

        var program = compileWithLibraries(managerProgram, libraries);

        // Verify the program structure
        assertEquals(1, program.triggers().size());
        assertEquals(1, program.letStatements().size());

        // Verify macro expansion
        var trigger = program.triggers().get(0);
        var statements = trigger.getBlock().getStatements();
        assertEquals(1, statements.size());
        assertTrue(statements.get(0) instanceof ca.teamdman.sfml.ast.ExpandStatement);
    }

    @Test
    public void libraryWithCombinedIOProtocolAndMacro() {
        String librarySource = """
            NAME "combined_lib"

            protocol Processable
                input: sidequalifier slotqualifier
                output: sidequalifier slotqualifier
            end

            struct Machine : Processable
                input: TOP SIDE SLOTS 0
                output: BOTTOM SIDE SLOTS 1
            end

            macro process(device: Processable, src, dst)
                input from src
                output to device using input
                forget
                input from device using output
                output to dst
            end
            """;

        Map<String, String> libraries = Map.of("combined_lib", librarySource);

        String managerProgram = """
            NAME "Combined Manager"

            use library "combined_lib"

            let machine = Machine
            every 20 ticks do
                DO process(machine, source, dest)
            end
            """;

        assertNoCompileErrorsWithLibraries(managerProgram, libraries);

        var program = compileWithLibraries(managerProgram, libraries);

        // Verify the program structure - library definitions are used for validation
        // but only local definitions appear in the program's lists
        assertEquals(1, program.triggers().size());
        assertEquals(1, program.letStatements().size());

        // Verify the let statement references the imported struct correctly
        assertEquals("machine", program.letStatements().get(0).variableName());
        assertEquals("Machine", program.letStatements().get(0).instance().definition().name());

        // Verify the struct implements the protocol (from the struct definition)
        var struct = program.letStatements().get(0).instance().definition();
        assertTrue(struct.implementsProtocol("Processable"));
    }

    @Test
    public void libraryMacroProtocolConstraintValidation() {
        String librarySource = """
            NAME "constrained_lib"

            protocol IOCapable
                input: sidequalifier slotqualifier
                output: sidequalifier slotqualifier
            end

            struct ValidDevice : IOCapable
                input: TOP SIDE SLOTS 0
                output: BOTTOM SIDE SLOTS 1
            end

            struct InvalidDevice
                storage: SLOTS 0-26
            end

            macro transfer(device: IOCapable, src, dst)
                input from src
                output to device using input
            end
            """;

        Map<String, String> libraries = Map.of("constrained_lib", librarySource);

        // Valid usage
        String validProgram = """
            NAME "Valid Usage"
            use library "constrained_lib"

            let device = ValidDevice

            every 20 ticks do
                DO transfer(device, a, b)
            end
            """;
        assertNoCompileErrorsWithLibraries(validProgram, libraries);

        // Invalid usage - struct doesn't implement required protocol
        String invalidProgram = """
            NAME "Invalid Usage"
            use library "constrained_lib"

            let device = InvalidDevice

            every 20 ticks do
                DO transfer(device, a, b)
            end
            """;

        assertThrows(RuntimeException.class, () -> {
            compileWithLibraries(invalidProgram, libraries);
        });
    }

    @Test
    public void chainedLibraryImports() {
        // Libraries must explicitly import their dependencies - transitive imports
        // make definitions available for validation but don't re-export them.
        String baseLib = """
            NAME "base_protocols"

            protocol HasInput
                input: sidequalifier slotqualifier
            end

            protocol HasOutput
                output: sidequalifier slotqualifier
            end
            """;

        String structLib = """
            NAME "struct_lib"
            use library "base_protocols"

            struct Furnace : HasInput, HasOutput
                input: TOP SIDE SLOTS 0
                output: BOTTOM SIDE SLOTS 2
            end
            """;

        // macro_lib needs to import both base_protocols (for protocol constraints)
        // and struct_lib (for the Furnace struct)
        String macroLib = """
            NAME "macro_lib"
            use library "base_protocols"
            use library "struct_lib"

            macro smelt(machine: HasInput, machine2: HasOutput, src, dst)
                input from src
                output to machine using input
                forget
                input from machine2 using output
                output to dst
            end
            """;

        Map<String, String> libraries = Map.of(
            "base_protocols", baseLib,
            "struct_lib", structLib,
            "macro_lib", macroLib
        );

        // The manager also needs to import all required definitions
        String managerProgram = """
            NAME "Chained Manager"
            use library "base_protocols"
            use library "struct_lib"
            use library "macro_lib"

            let furnace = Furnace

            every 20 ticks do
                DO smelt(furnace, furnace, ore_chest, ingot_chest)
            end
            """;

        assertNoCompileErrorsWithLibraries(managerProgram, libraries);

        var program = compileWithLibraries(managerProgram, libraries);

        // Verify the program structure
        assertEquals(1, program.triggers().size());
        assertEquals(1, program.letStatements().size());

        // Verify the struct from the library chain is accessible
        var instance = program.letStatements().get(0).instance();
        assertEquals("Furnace", instance.definition().name());
        assertTrue(instance.definition().implementsProtocol("HasInput"));
        assertTrue(instance.definition().implementsProtocol("HasOutput"));
    }

    // ===== COMBINED FEATURE TESTS =====

    @Test
    public void fullAutomationExample() {
        assertNoCompileErrors(
                """
                        NAME "Full Automation"

                        protocol Smeltable
                            input: sidequalifier slotqualifier
                            fuel: sidequalifier slotqualifier
                            output: sidequalifier slotqualifier
                        end

                        struct Furnace : Smeltable
                            input: TOP SIDE SLOTS 0
                            fuel: BOTTOM SIDE SLOTS 1
                            output: BOTTOM SIDE SLOTS 2
                        end

                        macro smelt(machine: Smeltable, source, dest)
                            input from source
                            output to machine using input
                            forget
                            input from machine using output
                            output to dest
                        end

                        let furnace = Furnace
                        every 20 ticks do
                            DO smelt(furnace, ore_chest, ingot_chest)
                        end
                        """
        );
    }

    @Test
    public void protocolWithAllFieldTypes() {
        assertNoCompileErrors(
                """
                        NAME "All Field Types Test"

                        protocol AllTypes
                            sides: sidequalifier
                            slots: slotqualifier
                            both: sidequalifier slotqualifier
                            lbl: label
                            num: number
                        end

                        struct Implementation : AllTypes
                            sides: TOP, BOTTOM SIDE
                            slots: SLOTS 0-5
                            both: EACH SIDE SLOTS 0
                            lbl: "my_label"
                            num: 42
                        end

                        let impl = Implementation
                        every 20 ticks do
                            input from chest
                        end
                        """
        );
    }
}
