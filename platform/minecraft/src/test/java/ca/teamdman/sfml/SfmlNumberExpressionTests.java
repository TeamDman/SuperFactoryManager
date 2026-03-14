package ca.teamdman.sfml;

import ca.teamdman.antlr.IAstNode;
import ca.teamdman.langs.SFMLParser;
import ca.teamdman.sfml.ast.*;
import ca.teamdman.sfml.program_builder.SFMLProgramBuilder;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

public class SfmlNumberExpressionTests {

    @Test
    public void numberExpressionLiteral() {

        String programString = "42";
        NumberExpression expr = parseNumberExpression(programString);

        assertEquals(42, expr.value());
        assertInstanceOf(NumberLiteral.class, expr.expression());
        assertEquals("42", expr.toString());
    }

    @Test
    public void numberExpressionAddition() {
        cases("2 + 3", expr -> {
            assertEquals(5, expr.value());
            assertInstanceOf(NumberAddition.class, expr.expression());
        });
    }

    @Test
    public void numberExpressionSubtraction() {
        cases("10 - 4", expr -> {
            assertEquals(6, expr.value());
            assertInstanceOf(NumberSubtraction.class, expr.expression());
        });
    }

    @Test
    public void numberExpressionMultiplication() {
        cases("6 * 7", expr -> {
            assertEquals(42, expr.value());
            assertInstanceOf(NumberMultiplication.class, expr.expression());
        });
    }

    @Test
    public void numberExpressionDivision() {
        cases("20 / 4", expr -> {
            assertEquals(5, expr.value());
            assertInstanceOf(NumberDivision.class, expr.expression());
        });
    }

    @Test
    public void numberExpressionModulus() {
        cases("17 % 5", expr -> {
            assertEquals(2, expr.value());
            assertInstanceOf(NumberModulus.class, expr.expression());
        });
    }

    @Test
    public void numberExpressionExponential() {
        cases("2 ^ 10", expr -> {
            assertEquals(1024, expr.value());
            assertInstanceOf(NumberExponential.class, expr.expression());
        });
    }

    @Test
    public void numberExpressionParen() {

        String programString = "(5)";
        NumberExpression expr = parseNumberExpression(programString);

        assertEquals(5, expr.value());
        assertInstanceOf(NumberParen.class, expr.expression());
        assertEquals("(5)", expr.toString());
    }

    @Test
    public void numberExpressionPrecedenceMultiplicationBeforeAddition() {
        // Tests 2 + 3 * 4 = 2 + 12 = 14 (not (2+3)*4 = 20)
        cases("2 + 3 * 4", expr -> {
            assertEquals(14, expr.value());
            assertInstanceOf(NumberAddition.class, expr.expression());
        });
    }

    @Test
    public void numberExpressionPrecedenceParenOverridesOrder() {
        // Tests (2 + 3) * 4 = 5 * 4 = 20
        cases("(2 + 3) * 4", expr -> {
            assertEquals(20, expr.value());
            assertInstanceOf(NumberMultiplication.class, expr.expression());
        });
    }

    @Test
    public void numberExpressionComplexExpression() {
        // Tests 2 + 2 * 70 = 2 + 140 = 142
        cases("2 + 2 * 70", expr -> assertEquals(142, expr.value()));
    }

    @Test
    public void numberExpressionDivisionByZeroThrows() {

        String programString = "10 / 0";
        var result = new SFMLProgramBuilder(programString).build(
                SFMLParser::numberExpression,
                (builder, ctx) -> (NumberExpression) builder.visit(ctx),
                SFMLParser.NumberExpressionContext.class
        );
        // The error is captured in the metadata, not thrown
        assertFalse(result.metadata().errors().isEmpty(), "Expected an error for division by zero");
        assertTrue(
                result.metadata().errors().stream().anyMatch(e -> e.toString().contains("Division by zero")),
                "Expected 'Division by zero' error message"
        );
    }

    @Test
    public void numberExpressionNestedExpressions() {
        // Tests ((2 + 3) * (4 - 1)) ^ 2 = (5 * 3) ^ 2 = 15 ^ 2 = 225
        cases("((2 + 3) * (4 - 1)) ^ 2", expr -> {
            assertEquals(225, expr.value());
            assertInstanceOf(NumberExponential.class, expr.expression());
        });
    }

    private static @NotNull NumberExpression parseNumberExpression(String programString) {
        return new SFMLProgramBuilder(programString)
                .<NumberExpression>build(SFMLParser::numberExpression).unwrapProgram();
    }

    /**
     * Test helper that runs assertions against both the original expression and the same expression with whitespace removed.
     * This ensures that both forms (with and without spaces) produce the same result.
     */
    private void cases(String exprString, Consumer<NumberExpression> assertions) {
        // Test with original whitespace
        NumberExpression expr1 = parseNumberExpression(exprString);
        assertions.accept(expr1);

        // Test with whitespace removed
        String noSpaces = exprString.replaceAll("\\s+", "");
        NumberExpression expr2 = parseNumberExpression(noSpaces);
        assertions.accept(expr2);
    }

    @Test
    public void numberExpressionPreservesAstStructure() {
        // Verify that the AST structure is preserved for pretty printing
        String programString = "10 + 5";
        NumberExpression expr = parseNumberExpression(programString);

        // Check the expression has the right structure
        assertInstanceOf(NumberAddition.class, expr.expression());
        NumberAddition addExpr = (NumberAddition) expr.expression();

        // Check left operand
        assertInstanceOf(NumberLiteral.class, addExpr.left().expression());
        assertEquals(10, addExpr.left().value());

        // Check right operand
        assertInstanceOf(NumberLiteral.class, addExpr.right().expression());
        assertEquals(5, addExpr.right().value());
    }

    @Test
    public void numberExpressionFromLiteral() {
        // Test the factory method for creating literals programmatically
        NumberExpression expr = NumberExpression.fromLiteral(100);

        assertEquals(100, expr.value());
        assertInstanceOf(NumberLiteral.class, expr.expression());
        assertEquals("100", expr.toString());
    }

    @Test
    public void numberExpressionWithUnderscores() {
        // Test that underscores are supported as digit separators
        String programString = "1_000_000";
        NumberExpression expr = parseNumberExpression(programString);

        assertEquals(1000000, expr.value());
        assertInstanceOf(NumberLiteral.class, expr.expression());
    }

    @Test
    public void numberExpressionOperatorPrecedence() {
        // This lexes as NUMBER PLUS NUMBER IDENTIFIER
        // So we added the NumberExpressionIdentifierMultiplication case to handle this
        NumberExpression number = parseNumberExpression("2+2*70");
        print(number, 0);
        assertEquals(142, number.value());
    }

    public static void print(IAstNode<?> node, int indent) {
        String line = " ".repeat(indent) + node.getClass().getSimpleName() + node;
        System.out.println(line);
        node.getChildNodes().forEach(child -> print(child, indent+1));
    }

    /*
    GitHub Lines
APP
 — 6:52 PM
numberExpression  : NUMBER                                      # NumberExpressionLiteral
                  | LPAREN numberExpression RPAREN              # NumberExpressionParen
                  | numberExpression CARET numberExpression     # NumberExpressionExponential
                  | numberExpression ASTERISK numberExpression  # NumberExpressionMultiplication
                  | numberExpression SLASH numberExpression     # NumberExpressionDivision
                  | numberExpression PLUS numberExpression      # NumberExpressionAddition
                  | numberExpression DASH numberExpression      # NumberExpressionSubtraction
                  | numberExpression PERCENT numberExpression   # NumberExpressionModulus
Mei

OP
 — 6:53 PM
Wait, that on its own gives precedence to multiplication over addition? Huh
Teamy — 6:54 PM
if my unit tests are to be believed, yes
Mei

OP
 — 6:54 PM
Would have expected it to default to left-associative, that's wild
Ah, it's not quite exactly bodmas, ANTLR gives higher precedence to the options listed first
So multiplication is given precedence over division with that, whereas they're left-associative with each other usually. That only matters if the operations return integers and for floating point imprecision
Teamy — 7:00 PM
ah hm
sounds like a problem for future-me
since the branches are still ordered could do numberExpression (ASTERISK | SLASH ) numberExpression # numberExpressionMulOrDiv and resolve it that way
    https://discord.com/channels/967118679370264627/1457113516430327819/1457161728969412754
    */
}
