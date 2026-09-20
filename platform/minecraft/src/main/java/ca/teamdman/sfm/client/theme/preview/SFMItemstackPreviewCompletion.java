package ca.teamdman.sfm.client.theme.preview;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

/** Cursor-aware typed expression frontier. Exact token ranges preserve all following arguments. */
public final class SFMItemstackPreviewCompletion {
    public record Option(String replacement,String label) {}
    public record Frontier(int start,int end,String expected,List<Option> options) {
        public Frontier { options=List.copyOf(options); }
    }
    private record Token(int start,int end,String text) {}
    private SFMItemstackPreviewCompletion() {}

    public static Optional<Frontier> complete(String input,int start,int cursor,
            SFMItemstackPreviewOperators operators,Optional<SFMItemstackPreviewSubject> subject) {
        if(cursor<start || cursor>input.length() || input.length()-start>16384) return Optional.empty();
        Walker walker=new Walker(input,start,cursor,operators,subject);
        walker.walk(SFMItemstackPreviewOperators.Type.BOOLEAN,"predicate",0);
        return Optional.ofNullable(walker.answer);
    }
    private static final class Walker {
        final String input; final int cursor; final SFMItemstackPreviewOperators registry;
        final Optional<SFMItemstackPreviewSubject> subject; int position,nodes; Frontier answer;
        Walker(String input,int start,int cursor,SFMItemstackPreviewOperators registry,Optional<SFMItemstackPreviewSubject> subject) {
            this.input=input;position=start;this.cursor=cursor;this.registry=registry;this.subject=subject;
        }
        boolean walk(SFMItemstackPreviewOperators.Type type,String name,int depth) {
            if(depth>16 || ++nodes>128 || answer!=null) return false;
            while(position<input.length() && Character.isWhitespace(input.charAt(position))) position++;
            int begin=position;
            if(position==input.length()) {
                if(cursor>=begin) answer=frontier(new Token(begin,begin,""),type,name);
                return false;
            }
            if(input.charAt(position)=='"') {
                position++; boolean escape=false;
                while(position<input.length()) {
                    char c=input.charAt(position++);
                    if(!escape && c=='"') break;
                    if(!escape && c=='\\') escape=true; else escape=false;
                }
            } else while(position<input.length() && !Character.isWhitespace(input.charAt(position))) position++;
            Token token=new Token(begin,position,input.substring(begin,position));
            if(cursor>=begin && cursor<=position) { answer=frontier(token,type,name);return false; }
            if(token.text().startsWith("\"")) return type==SFMItemstackPreviewOperators.Type.STRING;
            SFMItemstackPreviewOperators.Operator operator;
            try { operator=registry.require(token.text()); } catch(IllegalArgumentException absent) { return false; }
            if(operator.result()!=type) return false;
            for(var arg:operator.operands()) if(!walk(arg.type(),operator.id()+"."+arg.name(),depth+1)) return false;
            return true;
        }
        Frontier frontier(Token token,SFMItemstackPreviewOperators.Type type,String name) {
            String prefix=input.substring(token.start(),Math.min(cursor,token.end()));
            ArrayList<Option> result=new ArrayList<>();
            if(!prefix.startsWith("\"")) {
                for(var op:registry.descriptors()) if(op.result()==type && op.id().startsWith(prefix))
                    result.add(new Option(op.id(),op.id()+" · "+op.description()));
            }
            if(type==SFMItemstackPreviewOperators.Type.STRING) {
                LinkedHashSet<String> values=new LinkedHashSet<>();
                subject.ifPresent(s->{values.add(s.name());values.add(s.basename());values.addAll(s.suffixes());values.addAll(s.prefixes());});
                if(values.isEmpty()) values.add("");
                for(String value:values) {
                    if(value.codePointCount(0,value.length())>SFMItemstackPreviewExpression.MAX_LITERAL_CODEPOINTS) continue;
                    String quoted=SFMItemstackPreviewExpression.quote(value);
                    if(prefix.isEmpty() || quoted.startsWith(prefix)) result.add(new Option(quoted,quoted+" · literal string"));
                    if(result.size()>=256) break;
                }
            }
            return new Frontier(token.start(),token.end(),name+" : "+type,result);
        }
    }
}
