package ca.teamdman.sfm.client.theme.preview;

import ca.teamdman.sfm.client.presentation.SFMItemIcon;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Pure, conservative rule selection. Unknown implication is never a numeric priority. */
public final class SFMItemstackPreviewRules {
    public enum Layer { DEFAULT, MOD, USER }
    public enum Status { MATCHED, NO_MATCH, AMBIGUOUS, UNAVAILABLE }
    public record Rule(String id, Layer layer, SFMItemstackPreviewExpression predicate, SFMItemIcon icon) {
        public Rule {
            Objects.requireNonNull(id); Objects.requireNonNull(layer); Objects.requireNonNull(predicate); Objects.requireNonNull(icon);
            if (id.isBlank()) throw new IllegalArgumentException("Rule id must not be blank");
        }
        public static Rule user(SFMItemstackPreviewExpression predicate, SFMItemIcon icon) {
            return new Rule("sfm:preview-rule/" + sha256(predicate.print()), Layer.USER, predicate, icon);
        }
    }
    public record Decision(Status status, Optional<Rule> winner, List<Rule> candidates, List<String> diagnostics) {
        public Decision { candidates=List.copyOf(candidates); diagnostics=List.copyOf(diagnostics); }
    }
    private SFMItemstackPreviewRules() {}

    public static Decision resolve(List<Rule> rules, SFMItemstackPreviewOperators registry, SFMItemstackPreviewSubject subject) {
        if(rules.size()>1024) throw new IllegalArgumentException("Rule set exceeds 1024 entries");
        ArrayList<Rule> matches=new ArrayList<>(), unavailable=new ArrayList<>();
        ArrayList<String> diagnostics=new ArrayList<>();
        for(Rule rule:rules) {
            try {
                if(rule.predicate().validate(registry)!=SFMItemstackPreviewOperators.Type.BOOLEAN)
                    throw new IllegalArgumentException("Rule predicate must be BOOLEAN");
                var result=rule.predicate().evaluate(registry,subject);
                if(result.isEmpty()) { unavailable.add(rule); diagnostics.add(rule.id()+": required subject fact unavailable"); }
                else if(Boolean.TRUE.equals(result.get())) matches.add(rule);
            } catch(RuntimeException error) {
                unavailable.add(rule); diagnostics.add(rule.id()+": "+error.getMessage());
            }
        }
        matches.sort(Comparator.comparing(Rule::id)); unavailable.sort(Comparator.comparing(Rule::id));
        int layer=matches.stream().mapToInt(r->r.layer().ordinal()).max().orElse(-1);
        if(unavailable.stream().anyMatch(r->r.layer().ordinal()>=layer))
            return new Decision(Status.UNAVAILABLE,Optional.empty(),unavailable,diagnostics);
        if(matches.isEmpty()) return new Decision(Status.NO_MATCH,Optional.empty(),List.of(),diagnostics);
        List<Rule> authority=matches.stream().filter(r->r.layer().ordinal()==layer).toList();
        List<Rule> maximal=authority.stream().filter(candidate->authority.stream().noneMatch(other -> other!=candidate
                && implies(other.predicate(),candidate.predicate()) && !implies(candidate.predicate(),other.predicate()))).toList();
        if(maximal.size()==1) return new Decision(Status.MATCHED,Optional.of(maximal.get(0)),maximal,diagnostics);
        // Identical resulting icons are safe to render; every contributing identity remains inspectable.
        if(maximal.stream().map(Rule::icon).distinct().count()==1)
            return new Decision(Status.MATCHED,Optional.of(maximal.get(0)),maximal,diagnostics);
        return new Decision(Status.AMBIGUOUS,Optional.empty(),maximal,List.of("Incomparable matching rules; baseline icon retained"));
    }

    /** Sound sufficient conditions only; false means not proved, not logical negation. */
    public static boolean implies(SFMItemstackPreviewExpression source, SFMItemstackPreviewExpression target) {
        if(source.equals(target)) return true;
        if(is(target,"sfm:bool/and")) return target.operands().stream().allMatch(t->implies(source,t));
        if(is(source,"sfm:bool/or")) return source.operands().stream().allMatch(s->implies(s,target));
        if(is(source,"sfm:bool/and") && source.operands().stream().anyMatch(s->implies(s,target))) return true;
        if(is(target,"sfm:bool/or") && target.operands().stream().anyMatch(t->implies(source,t))) return true;
        if(is(source,"sfm:entry/has_suffix") && is(target,"sfm:entry/has_suffix")
                && source.operands().size()==1 && target.operands().size()==1) {
            String left=source.operands().get(0).literal(), right=target.operands().get(0).literal();
            return left!=null && right!=null && right.startsWith(".")
                    && SFMItemstackPreviewOperators.folded(left).endsWith(SFMItemstackPreviewOperators.folded(right));
        }
        if(source.operands().size()!=2 || target.operands().size()!=2) return false;
        if(!source.operands().get(0).equals(target.operands().get(0))) return false;
        String left=source.operands().get(1).literal(), right=target.operands().get(1).literal();
        if(left==null || right==null) return false;
        left=SFMItemstackPreviewOperators.folded(left); right=SFMItemstackPreviewOperators.folded(right);
        if(is(source,"sfm:string/equals")) return switch(target.operator()) {
            case "sfm:string/equals" -> left.equals(right);
            case "sfm:string/starts_with" -> left.startsWith(right);
            case "sfm:string/ends_with" -> left.endsWith(right);
            default -> false;
        };
        if(is(source,"sfm:string/starts_with") && is(target,"sfm:string/starts_with")) return left.startsWith(right);
        if(is(source,"sfm:string/ends_with") && is(target,"sfm:string/ends_with")) return left.endsWith(right);
        return false;
    }
    private static boolean is(SFMItemstackPreviewExpression expression,String id) { return id.equals(expression.operator()); }
    public static String sha256(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch(NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }
}
