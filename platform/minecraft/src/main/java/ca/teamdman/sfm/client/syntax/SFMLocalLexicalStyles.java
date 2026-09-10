package ca.teamdman.sfm.client.syntax;

import ca.teamdman.sfm.client.screen.SFMDrawCanvasRemoteSyntaxStyles.FormattingSpan;
import net.minecraft.ChatFormatting;
import java.util.*;

/** Bounded, dependency-free lexical colouring; deliberately not a semantic parser. */
public final class SFMLocalLexicalStyles {
    public static final int MAX_CHARACTERS = 262144;
    private static final Set<String> LANGUAGES = Set.of("gradle", "groovy", "markdown", "json", "json5", "toml", "properties", "cfg", "ini");
    private static final Set<String> KEYWORDS = Set.of("def", "class", "import", "plugins", "dependencies", "repositories", "tasks", "true", "false", "null", "if", "else", "return", "for", "in", "new", "extends", "apply", "buildscript", "allprojects", "subprojects");
    private SFMLocalLexicalStyles() {}
    public static boolean supports(String language) { return LANGUAGES.contains(language); }
    public static List<FormattingSpan> highlight(String language, String text) {
        if (!supports(language) || text.length() > MAX_CHARACTERS) return List.of();
        int[] bytes = new int[text.length()+1];
        for (int i=0, offset=0; i<text.length();) {
            int cp=text.codePointAt(i), units=Character.charCount(cp);
            bytes[i]=offset;
            if (units==2) bytes[i+1]=offset;
            offset += cp<=0x7f ? 1 : cp<=0x7ff ? 2 : cp<=0xffff ? 3 : 4;
            i+=units; bytes[i]=offset;
        }
        var spans=new ArrayList<FormattingSpan>();
        boolean markdown=language.equals("markdown");
        for (int i=0; i<text.length();) {
            int start=i; char c=text.charAt(i);
            ChatFormatting colour=null;
            if (markdown) {
                if ((i==0 || text.charAt(i-1)=='\n') && c=='#') {
                    while (i<text.length() && text.charAt(i)!='\n') i++;
                    colour=ChatFormatting.GOLD;
                } else if (c=='`') {
                    i++; while (i<text.length() && text.charAt(i)!='`' && text.charAt(i)!='\n') i++;
                    if (i<text.length() && text.charAt(i)=='`') i++;
                    colour=ChatFormatting.GREEN;
                } else i++;
            } else if (c=='\'' || c=='"') {
                boolean triple=i+2<text.length() && text.charAt(i+1)==c && text.charAt(i+2)==c;
                i+=triple ? 3 : 1;
                while (i<text.length()) {
                    if (text.charAt(i)=='\\') { i=Math.min(text.length(),i+2); continue; }
                    if (text.charAt(i)==c && (!triple || i+2<text.length() && text.charAt(i+1)==c && text.charAt(i+2)==c)) {
                        i+=triple ? 3 : 1; break;
                    }
                    i++;
                }
                colour=ChatFormatting.GREEN;
            } else if (c=='/' && i+1<text.length() && text.charAt(i+1)=='*') {
                i+=2;
                while (i+1<text.length() && !(text.charAt(i)=='*' && text.charAt(i+1)=='/')) i++;
                i=Math.min(text.length(),i+2); colour=ChatFormatting.DARK_GRAY;
            } else if (c=='#' || c=='/' && i+1<text.length() && text.charAt(i+1)=='/') {
                while (i<text.length() && text.charAt(i)!='\n') i++;
                colour=ChatFormatting.DARK_GRAY;
            } else if (Character.isJavaIdentifierStart(c)) {
                i++; while (i<text.length() && Character.isJavaIdentifierPart(text.charAt(i))) i++;
                if (KEYWORDS.contains(text.substring(start,i))) colour=ChatFormatting.LIGHT_PURPLE;
            } else if (Character.isDigit(c)) {
                i++; while (i<text.length() && (Character.isDigit(text.charAt(i)) || text.charAt(i)=='.')) i++;
                colour=ChatFormatting.AQUA;
            } else i++;
            if (colour!=null && bytes[i]>bytes[start]) spans.add(new FormattingSpan(bytes[start],bytes[i],List.of(colour)));
        }
        return List.copyOf(spans);
    }
}
