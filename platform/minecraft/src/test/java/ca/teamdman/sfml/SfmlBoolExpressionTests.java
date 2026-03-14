package ca.teamdman.sfml;

import ca.teamdman.langs.SFMLParser;
import ca.teamdman.sfml.ast.BoolExpr;
import ca.teamdman.sfml.program_builder.SFMLProgramBuilder;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SfmlBoolExpressionTests {

    @Test
    public void boolExprTrue(){
        assertBoolExpr(true, "true");
    }

    @Test
    public void boolExprFalse(){
        assertBoolExpr(false, "false");
    }

    @Test
    public void boolExprConjunction(){
        assertBoolExpr(false, "true and false");
        assertBoolExpr(false, "false and true");
    }

    @Test
    public void boolExprDisjunction(){
        assertBoolExpr(true, "true or false");
        assertBoolExpr(true, "false or true");
    }

    @Test
    public void boolExprCombos(){
        assertBoolExpr(true, "true or false and true");
        assertBoolExpr(true, "false and true or true");
        assertBoolExpr(false, "true and false and true");
        assertBoolExpr(false, "false and true and true");
        assertBoolExpr(true, "false or true and true");
        assertBoolExpr(true, "true and true or false");
        assertBoolExpr(true, "false and true or true");
    }

    @Test
    public void boolExprNegation(){
        assertBoolExpr(false, "not true");
        assertBoolExpr(true, "not false");
        assertBoolExpr(false, "not not false");
        assertBoolExpr(true, "not not true");
    }

    @Test
    public void boolExprParenPrecedence(){
        assertBoolExpr(false, "(true or false) and false");
        assertBoolExpr(false, "false and (true or false)");
        assertBoolExpr(true, "true or (false and false)");
        assertBoolExpr(true, "(false and false) or true");
        assertBoolExpr(false, "(true and false) or (false and true)");
        assertBoolExpr(false, "(false and true) or (true and false)");
        assertBoolExpr(true, "(true and true) or (false and false)");
        assertBoolExpr(true, "(false and false) or (true and true)");
    }

    @Test
    public void boolExprAndHasHigherPrecedenceThanOr(){
        assertBoolExpr(true, "true or false and false");
        assertBoolExpr(true, "false and false or true");
        assertBoolExpr(false, "false and true or false");
        assertBoolExpr(false, "false or false and true");
        assertBoolExpr(true, "false or true and true");
        assertBoolExpr(true, "true and true or false");
    }

    @Test
    public void boolExprNotHasHigherPrecendenceThanAnd(){
        assertBoolExpr(false, "not true and true");
        assertBoolExpr(false, "true and not true");
        assertBoolExpr(true, "not false and true");
        assertBoolExpr(false, "not false and false");
        assertBoolExpr(true, "true and not false");
        assertBoolExpr(false, "false and not false");
        assertBoolExpr(false, "false or not true");
        assertBoolExpr(false, "not true or false");
        assertBoolExpr(true, "true or not false and false");
        assertBoolExpr(true, "not false and false or true");
    }

    @Test
    public void boolExprNestedNegationAndParen(){
        assertBoolExpr(false, "not (true or false)");
        assertBoolExpr(true, "not (false and false)");
        assertBoolExpr(true, "not false or false");
        assertBoolExpr(true, "false or not false");
    }

    private static @NotNull BoolExpr parseBoolExpr(String programString) {
        return new SFMLProgramBuilder(programString)
                .<BoolExpr>build(SFMLParser::boolExpr).unwrapProgram();
    }

    private static void assertBoolExpr(boolean expected, String expr) {
        BoolExpr boolExpr = parseBoolExpr(expr);
        boolean actual = boolExpr.test(null);
        assertEquals(expected, actual, "expr: " + expr + " parsed: " + boolExpr);
    }
}
