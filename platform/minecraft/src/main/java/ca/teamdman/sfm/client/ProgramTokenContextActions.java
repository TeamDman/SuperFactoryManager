package ca.teamdman.sfm.client;

import ca.teamdman.langs.SFMLLexer;
import ca.teamdman.langs.SFMLParser;
import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.client.screen.SFMScreenChangeHelpers;
import ca.teamdman.sfm.common.net.*;
import ca.teamdman.sfm.common.registry.registration.SFMPackets;
import ca.teamdman.sfml.ast.*;
import com.mojang.datafixers.util.Pair;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;

import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ProgramTokenContextActions {

    public static Optional<Runnable> getContextAction(
            String programString,
            int cursorPosition
    ) {
        var lexer = new SFMLLexer(CharStreams.fromString(programString));
        var tokens = new CommonTokenStream(lexer);
        var parser = new SFMLParser(tokens);
        var builder = new ASTBuilder();
        try {
            var program = builder.visitProgram(parser.program());
            SFM.LOGGER.info("Gathering context actions for cursor position {}", cursorPosition);
            var contextAction = getElementsAroundCursor(cursorPosition, builder)
                    .map(pair -> getContextAction(
                            programString,
                            builder,
                            pair.getFirst(),
                            pair.getSecond(),
                            cursorPosition
                    ))
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .findFirst();
            // ASTBuilder stores node locations as weak references, so keep the parsed root reachable
            // until after cursor lookup has finished.
            program.name();
            return contextAction;
        } catch (Throwable t) {
            return Optional.of(() -> SFMScreenChangeHelpers.showProgramEditScreen("-- Encountered error, program parse failed:\n--"
                                                                                  + t.getMessage()));
        }
    }

    public static Stream<Pair<ASTNode, ParserRuleContext>> getElementsAroundCursor(
            int cursorPosition,
            ASTBuilder builder
    ) {
        return Stream.concat(
                builder
                        .getNodesUnderCursor(cursorPosition)
                        .stream(),
                builder
                        .getNodesUnderCursor(cursorPosition - 1)
                        .stream()
        );
    }

    public static Optional<Runnable> getContextAction(
            String programString,
            ASTBuilder builder,
            ASTNode node,
            ParserRuleContext parserRuleContext,
            int cursorPosition
    ) {
        SFM.LOGGER.info("Checking if context action exists for node {} {}", node.getClass(), node);
        if (node instanceof ResourceIdentifier<?, ?, ?> rid) {
            SFM.LOGGER.info("Found context action for resource identifier node");
            return Optional.of(() -> {
                String expansion = rid
                        .expand()
                        .stream()
                        .map(ResourceIdentifier::toStringCondensed)
                        .collect(Collectors.joining(",\n"));
                SFMScreenChangeHelpers.showProgramEditScreen(expansion);
            });
        } else if (node instanceof Label label) {
            SFM.LOGGER.info("Found context action for label node");
            return Optional.of(() -> SFMPackets.sendToServer(new ServerboundLabelInspectionRequestPacket(
                    label.name()
            )));
        } else if (node instanceof InputStatement) {
            if (cursorPosition > parserRuleContext.getStart().getStartIndex() + "INPUT".length()) {
                SFM.LOGGER.info("Found context action for input node, but the cursor isn't at the start of the node");
                return Optional.empty();
            }
            SFM.LOGGER.info("Found context action for input node");
            int nodeIndex = builder.getIndexForNode(node);
            return Optional.of(() -> SFMPackets.sendToServer(new ServerboundInputInspectionRequestPacket(
                    programString,
                    nodeIndex
            )));
        } else if (node instanceof OutputStatement) {
            if (cursorPosition > parserRuleContext.getStart().getStartIndex() + "OUTPUT".length()) {
                SFM.LOGGER.info("Found context action for output node, but the cursor isn't at the start of the node");
                return Optional.empty();
            }
            SFM.LOGGER.info("Found context action for output node");
            int nodeIndex = builder.getIndexForNode(node);
            return Optional.of(() -> SFMPackets.sendToServer(new ServerboundOutputInspectionRequestPacket(
                    programString,
                    nodeIndex
            )));
        } else if (node instanceof BoolExpr) {
            SFM.LOGGER.info("Found context action for BoolExpr node");
            int nodeIndex = builder.getIndexForNode(node);
            return Optional.of(() -> SFMPackets.sendToServer(new ServerboundBoolExprStatementInspectionRequestPacket(
                    programString,
                    nodeIndex
            )));
        } else if (node instanceof IfStatement) {
            SFM.LOGGER.info("Found context action for if statement node");
            int nodeIndex = builder.getIndexForNode(node);
            return Optional.of(() -> SFMPackets.sendToServer(new ServerboundIfStatementInspectionRequestPacket(
                    programString,
                    nodeIndex
            )));
        }
        // todo: add ctrl+space inspection for WITH TAG to show items matching tag
        return Optional.empty();
    }

    public static boolean hasContextAction(Token token) {
        return switch (token.getType()) {
            case SFMLLexer.INPUT, SFMLLexer.OUTPUT, SFMLLexer.IDENTIFIER, SFMLLexer.IF, SFMLLexer.HAS -> true;
            default -> false;
        };
    }
}
