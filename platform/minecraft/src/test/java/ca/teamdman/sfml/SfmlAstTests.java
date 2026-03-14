package ca.teamdman.sfml;

import ca.teamdman.langs.SFMLParser;
import ca.teamdman.sfml.ast.BoolExpr;
import ca.teamdman.sfml.ast.IfStatement;
import ca.teamdman.sfml.ast.ResourceIdentifier;
import ca.teamdman.sfml.ast.SfmlAstBuilder;
import ca.teamdman.sfml.program_builder.SFMLProgramBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SfmlAstTests {
    @Test
    public void resourceIdentifier() {

        String programString = "log";
        ResourceIdentifier<?, ?, ?> id = new SFMLProgramBuilder(programString).build(
                SFMLParser::resourceId,
                SfmlAstBuilder::visitResource,
                SFMLParser.ResourceContext.class
        ).unwrapProgram();

        assertEquals(
                new ResourceIdentifier<>("sfm", "item", ".*", "log"),
                id
        );
    }

    @Test
    public void compileHas() {

        String programString = "chest HAS eq 1 diamond";
        BoolExpr expr = new SFMLProgramBuilder(programString).build(
                SFMLParser::boolExpr,
                SfmlAstBuilder::visitBooleanHas,
                SFMLParser.BooleanHasContext.class
        ).unwrapProgram();
        System.out.println(expr);
    }
    @Test
    public void compileIfWith() {

        String programString = "if chest has > 0 with #c:armor then end";
        IfStatement statement = new SFMLProgramBuilder(programString).<IfStatement>build(
                SFMLParser::ifStatement
        ).unwrapProgram();
        System.out.println(statement);
    }
}
