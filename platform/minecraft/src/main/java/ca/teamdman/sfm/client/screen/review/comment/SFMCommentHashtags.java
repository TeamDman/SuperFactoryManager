package ca.teamdman.sfm.client.screen.review.comment;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.text.Normalizer;

/** Independent derived parser; comment text remains the sole hashtag authority. */
public final class SFMCommentHashtags {
    private SFMCommentHashtags() {}
    public static Set<String> derive(String text) {
        text = Normalizer.normalize(text, Normalizer.Form.NFC);
        LinkedHashSet<String> answer = new LinkedHashSet<>();
        for (int offset=0;offset<text.length();) {
            int cp=text.codePointAt(offset), cpLength=Character.charCount(cp);
            if(cp!='#'){offset+=cpLength;continue;}
            if(offset>0){int previous=text.codePointBefore(offset);if(Character.isLetterOrDigit(previous)||previous=='_'){offset+=cpLength;continue;}}
            int cursor=offset+1;
            if(cursor<text.length()&&text.charAt(cursor)=='"') {
                cursor++;StringBuilder value=new StringBuilder();boolean closed=false;
                while(cursor<text.length()){
                    char character=text.charAt(cursor++);
                    if(character=='"'){closed=true;break;}
                    if(character=='\\'&&cursor<text.length()){
                        char escaped=text.charAt(cursor);
                        if(escaped=='\\'||escaped=='"'){value.append(escaped);cursor++;continue;}
                    }
                    value.append(character);
                }
                if(closed&&!value.toString().isBlank())answer.add("#\""+normalize(value.toString())+"\"");
                offset=cursor;
            } else {
                int start=cursor;
                while(cursor<text.length()){int current=text.codePointAt(cursor);if(!(Character.isLetterOrDigit(current)||current=='_'||current=='-'))break;cursor+=Character.charCount(current);}
                if(cursor>start)answer.add("#"+normalize(text.substring(start,cursor)));
                offset=Math.max(cursor,offset+1);
            }
        }
        return java.util.Collections.unmodifiableSet(answer);
    }
    private static String normalize(String value){return Normalizer.normalize(value,Normalizer.Form.NFC).toLowerCase(Locale.ROOT);}
}
