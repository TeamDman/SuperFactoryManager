package ca.teamdman.toml;

import ca.teamdman.sfm.client.text_editor.TextEditScreenContentLanguage;
import ca.teamdman.sfm.client.text_styling.ProgramSyntaxHighlightingHelper;
import ca.teamdman.toml.ast.TomlAstNode;
import ca.teamdman.toml.toml_builder.TomlProgramBuilder;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class SfmTomlTests {
    @Test
    public void testToml() {
        String programString = """
                preferredEditor = "sfm:v2"
                showLineNumbers = false
                #Allowed Values: OFF, BASIC, ADVANCED
                intellisenseLevel = "OFF"
        """.stripTrailing().stripIndent();
        TomlAstNode toml = new TomlProgramBuilder(programString).build().maybeProgram();
        System.out.println(toml);
    }

    @Test
    public void tomlSyntaxHighlighting() {
        var rawInput = """
                preferredEditor = "sfm:v2"
                showLineNumbers = false
                #Allowed Values: OFF, BASIC, ADVANCED
                intellisenseLevel = "OFF"
                """.stripIndent();
        var lines = rawInput.split("\n", -1);

        var colouredLines = ProgramSyntaxHighlightingHelper.withSyntaxHighlighting(
                rawInput,
                false,
                TextEditScreenContentLanguage.TOML
        );
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
    public void tomlSyntaxHighlightingWithComplexContent() {
        var rawInput = """
                # This is a comment
                [section]
                key = "value"
                number = 42
                float = 3.14
                bool = true
                
                [[array_of_tables]]
                name = "first"
                
                [[array_of_tables]]
                name = "second"
                """.stripTrailing().stripIndent();
        var lines = rawInput.split("\n", -1);

        var colouredLines = ProgramSyntaxHighlightingHelper.withSyntaxHighlighting(
                rawInput,
                false,
                TextEditScreenContentLanguage.TOML
        );
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
}
