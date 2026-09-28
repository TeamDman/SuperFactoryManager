package ca.teamdman.sfml.test;

import ca.teamdman.sfm.client.ProgramTokenContextActions;
import ca.teamdman.sfm.client.text_styling.ProgramSyntaxHighlightingHelper;
import ca.teamdman.sfm.common.config.SFMConfig;
import ca.teamdman.sfm.common.net.ServerboundLabelGunSetActiveLabelPacket;
import ca.teamdman.sfml.ast.ResourceIdentifier;
import com.google.common.collect.Sets;
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
}
