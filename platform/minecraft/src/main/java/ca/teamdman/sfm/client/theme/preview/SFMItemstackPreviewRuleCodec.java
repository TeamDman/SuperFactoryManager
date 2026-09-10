package ca.teamdman.sfm.client.theme.preview;

import ca.teamdman.sfm.client.presentation.SFMItemIcon;
import com.electronwill.nightconfig.core.Config;
import net.minecraft.resources.ResourceLocation;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Additive versioned TOML section. Persisted predicates are trees, not opaque command strings. */
public final class SFMItemstackPreviewRuleCodec {
    public static final String SECTION="itemstack_preview_rules";
    private SFMItemstackPreviewRuleCodec() {}
    public static List<SFMItemstackPreviewRules.Rule> read(Config root,SFMItemstackPreviewOperators operators) {
        Object raw=root.get(SECTION);
        if(raw==null) return List.of();
        if(!(raw instanceof Config section)) throw new IllegalArgumentException(SECTION+" must be a table");
        Object version=section.get("schema_version");
        if(!(version instanceof Number n) || n.doubleValue()!=1.0) throw new IllegalArgumentException("Rule schema_version must be 1");
        Object rawRules=section.get("rules");
        if(!(rawRules instanceof List<?> rules) || rules.size()>1024) throw new IllegalArgumentException("Rule section requires at most 1024 rules");
        ArrayList<SFMItemstackPreviewRules.Rule> answer=new ArrayList<>();
        Set<String> ids=new HashSet<>();
        for(Object value:rules) {
            if(!(value instanceof Config rule)) throw new IllegalArgumentException("Rule must be an inline table");
            String id=string(rule,"id");
            new ResourceLocation(id);
            if(!ids.add(id)) throw new IllegalArgumentException("Duplicate preview rule id "+id);
            SFMItemstackPreviewExpression expression=expression(rule.get("predicate"),0,new int[]{0});
            if(expression.validate(operators)!=SFMItemstackPreviewOperators.Type.BOOLEAN || expression.print().length()>SFMItemstackPreviewExpression.MAX_CHARS)
                throw new IllegalArgumentException("Invalid Boolean rule predicate");
            SFMItemIcon icon=new SFMItemIcon(new ResourceLocation(string(rule,"item")),
                    new ResourceLocation(string(rule,"fallback")),string(rule,"label"));
            answer.add(new SFMItemstackPreviewRules.Rule(id,SFMItemstackPreviewRules.Layer.USER,expression,icon));
        }
        return List.copyOf(answer);
    }
    private static String string(Config config,String key) {
        Object value=config.get(key);
        if(!(value instanceof String text)) throw new IllegalArgumentException("Expected string "+key);
        return text;
    }
    private static SFMItemstackPreviewExpression expression(Object value,int depth,int[] count) {
        if(depth>SFMItemstackPreviewExpression.MAX_DEPTH || ++count[0]>SFMItemstackPreviewExpression.MAX_NODES)
            throw new IllegalArgumentException("Stored expression exceeds depth/node limits");
        if(!(value instanceof Config node)) throw new IllegalArgumentException("Expression node must be a table");
        if(node.contains("literal")) {
            if(node.valueMap().size()!=1) throw new IllegalArgumentException("Literal node contains operator/unknown fields");
            return SFMItemstackPreviewExpression.literal(string(node,"literal"));
        }
        if(!node.valueMap().keySet().equals(Set.of("operator","operands"))) throw new IllegalArgumentException("Operator node fields must be operator and operands");
        Object children=node.get("operands");
        if(!(children instanceof List<?> list) || list.size()>8) throw new IllegalArgumentException("Invalid expression operands");
        ArrayList<SFMItemstackPreviewExpression> args=new ArrayList<>();
        for(Object child:list) args.add(expression(child,depth+1,count));
        return new SFMItemstackPreviewExpression(string(node,"operator"),null,args);
    }
    public static String write(List<SFMItemstackPreviewRules.Rule> rules) {
        StringBuilder out=new StringBuilder("\n["+SECTION+"]\nschema_version = 1\nrules = [\n");
        rules.stream().sorted(java.util.Comparator.comparing(SFMItemstackPreviewRules.Rule::id)).forEach(rule -> {
            if(rule.layer()!=SFMItemstackPreviewRules.Layer.USER) throw new IllegalArgumentException("Only user rules are persisted here");
            out.append("  { id = ").append(q(rule.id())).append(", predicate = ").append(tree(rule.predicate()))
                    .append(", item = ").append(q(rule.icon().requestedItem().toString()))
                    .append(", fallback = ").append(q(rule.icon().fallbackItem().toString()))
                    .append(", label = ").append(q(rule.icon().accessibleLabel())).append(" },\n");
        });
        return out.append("]\n").toString();
    }
    private static String tree(SFMItemstackPreviewExpression expression) {
        if(expression.literal()!=null) return "{ literal = "+q(expression.literal())+" }";
        return "{ operator = "+q(expression.operator())+", operands = ["+expression.operands().stream()
                .map(SFMItemstackPreviewRuleCodec::tree).collect(java.util.stream.Collectors.joining(", "))+"] }";
    }
    private static String q(String value) { return SFMItemstackPreviewExpression.quote(value); }
}
