package ca.teamdman.sfml.intellisense;

import ca.teamdman.langs.SFMLLexer;
import ca.teamdman.langs.SFMLParser;
import ca.teamdman.sfm.common.registry.registration.SFMResourceTypes;
import ca.teamdman.sfm.common.resourcetype.ResourceType;
import ca.teamdman.sfm.common.util.SFMEnvironmentUtils;
import ca.teamdman.sfml.ext_antlr4c3.CodeCompletionCore;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.Vocabulary;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * https://neuroning.com/post/implementing-code-completion-for-vscode-with-antlr/
 * https://soft-gems.net/universal-code-completion-using-antlr3/
 */
public class SFMLIntellisense {
    public static List<IntellisenseAction> getSuggestions(
            IntellisenseContext context
    ) {
        List<IntellisenseAction> rtn = new ArrayList<>();
        // Short circuit if disabled
        if (context.intellisenseLevel().isDisabled()) {
            return rtn;
        }

        // Create code completion core
        SFMLParser parser = context.programBuildResult().metadata().parser();
        Set<Integer> preferredRules = Set.of(
                SFMLParser.RULE_resourceId,
                SFMLParser.RULE_label
        );
        Set<Integer> ignoredTokens = Set.of(
                SFMLParser.WS,
                SFMLParser.EOF
        );
        CodeCompletionCore core = new CodeCompletionCore(parser, preferredRules, ignoredTokens);

        // Identify caret position
        @Nullable Token caretToken = context.programBuildResult().getTokenAtCursorPosition(context.cursorPosition());
        if (caretToken == null) return new ArrayList<>();
        int caretTokenIndex = caretToken.getTokenIndex();

        // Get candidates
        CodeCompletionCore.CandidatesCollection candidates = core.collectCandidates(caretTokenIndex, null);
        Vocabulary vocabulary = parser.getVocabulary();

        // Process rules
        candidates.rules.forEach((head, tail) -> {
            switch (head) {
                case SFMLParser.RULE_resourceId -> {
                    if (SFMEnvironmentUtils.isGameLoaded() && context
                            .intellisenseLevel()
                            .isResourceIntellisenseEnabled()) {
                        for (ResourceType<?, ?, ?> resourceType : SFMResourceTypes.registry()) {
                            gatherIntellisenseActions(context, resourceType, rtn::add);
                        }
                    }
                }
                case SFMLParser.RULE_label -> {
                    rtn.add(new SuggestedTokensIntellisenseAction(
                            SFMLLexer.STRING,
                            new ArrayList<>(),
                            vocabulary
                    ));
                    context.labelPositionHolder().labels().forEach(
                            (key, value) ->
                                    rtn.add(
                                            new SuggestedLabelIntellisenseAction(
                                                    key,
                                                    value.size()
                                            )
                                    )
                    );
                }
            }
        });

        // Process tokens
        for (Map.Entry<Integer, List<Integer>> entry : candidates.tokens.entrySet()) {
            rtn.add(new SuggestedTokensIntellisenseAction(
                    entry.getKey(),
                    entry.getValue(),
                    vocabulary
            ));
        }

        return rtn;
    }

    private static <STACK, ITEM, CAP> void gatherIntellisenseActions(
            IntellisenseContext ignoredContext,
            ResourceType<STACK, ITEM, CAP> resourceType,
            Consumer<IntellisenseAction> results
    ) {
//        String word = context.createMutableProgramString().getWord();
        for (ITEM item : resourceType.getItems()) {
            var suggestion = new SuggestedResourceIntellisenseAction<>(
                    resourceType,
                    item
            );
//            if (suggestion.getComponent().getString().contains(word)) {
            results.accept(suggestion);
//            }
        }
    }
}
