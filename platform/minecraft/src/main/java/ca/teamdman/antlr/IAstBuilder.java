package ca.teamdman.antlr;

import ca.teamdman.sfml.ast.ForgetStatement;
import ca.teamdman.sfml.ast.InputStatement;
import com.mojang.datafixers.util.Pair;
import org.antlr.v4.runtime.ParserRuleContext;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public interface IAstBuilder<AST_NODE extends IAstNode<?>> {

    /// Used for program editor context actions; ctrl+space on a token
    List<Pair<WeakReference<AST_NODE>, ParserRuleContext>> contexts();

    /// @return hierarchy of nodes; e.g., Program > Trigger > Block > IOStatement > ResourceAccess > Label
    default List<Pair<AST_NODE, ParserRuleContext>> getNodesUnderCursor(int cursorPos) {

        return contexts()
                .stream()
                .filter(pair -> pair.getSecond() != null)
                .filter(pair -> pair.getSecond().start.getStartIndex() <= cursorPos
                                && pair.getSecond().stop.getStopIndex() >= cursorPos)
                .map(pair -> Pair.of(pair.getFirst().get(), pair.getSecond()))
                .filter(pair -> pair.getFirst() != null)
                .collect(Collectors.toList());
    }

    /// @return {@link #contexts()}.get({@code index})
    default Optional<AST_NODE> getNodeAtIndex(int index) {

        if (index < 0 || index >= contexts().size()) return Optional.empty();
        WeakReference<AST_NODE> nodeRef = contexts().get(index).getFirst();
        return Optional.ofNullable(nodeRef.get());
    }


    /// Tracks an {@link AST_NODE} and its {@link ParserRuleContext} for later retrieval for editor context actions.
    default <T extends AST_NODE> void trackNode(
            T node,
            ParserRuleContext ctx
    ) {

        WeakReference<AST_NODE> nodeRef = new WeakReference<>(node);

        contexts().add(new Pair<>(nodeRef, ctx));
    }

    /// Used by {@link ForgetStatement} to track the provenance of dynamically generated {@link InputStatement} instances.
    /// We should use weak references for these dynamically generated nodes to let them get garbage collected; <a href="https://github.com/TeamDman/SuperFactoryManager/issues/405">#405</a>.
    default <L extends AST_NODE, R extends AST_NODE> void setLocationFromOtherNode(
            L node,
            R otherNode
    ) {

        trackNode(node, contexts().get(getIndexForNode(otherNode)).getSecond());
    }


    default <T extends AST_NODE> Optional<ParserRuleContext> getContextForNode(T node) {

        return contexts()
                .stream()
                .filter(pair -> pair.getFirst().get() == node)
                .map(Pair::getSecond)
                .findFirst();
    }

    default String getLineColumnForNode(AST_NODE node) {
        // todo: return TranslatableContents
        return getContextForNode(node)
                .map(IAstBuilder::getLineColumnForContext)
                .orElse("Unknown location");
    }

    /// Used for client-server collaboration to make context menu actions work.
    /// The client calls {@link #getNodesUnderCursor(int)} and then {@link ca.teamdman.sfm.client.ProgramTokenContextActions#getContextAction(String, int)} to build the pick list.
    /// When a context action is invoked, a packet is sent to the server containing the index of the node so that the
    /// packet handler can do its work with the server's instance of the {@link AST_NODE}.
    default <T extends AST_NODE> int getIndexForNode(T node) {

        int contextCount = contexts().size();
        for (int i = 0; i < contextCount; i++) {
            Pair<WeakReference<AST_NODE>, ParserRuleContext> pair = contexts().get(i);
            // Intentional reference equality check, don't forget the `.get()`!
            if (pair.getFirst().get() == node) {
                return i;
            }
        }
        return -1;
    }

    static String getLineColumnForContext(ParserRuleContext ctx) {

        return "Line " + ctx.start.getLine() + ", Column " + ctx.start.getCharPositionInLine();
    }

}
