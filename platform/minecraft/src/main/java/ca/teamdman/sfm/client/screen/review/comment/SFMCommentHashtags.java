package ca.teamdman.sfm.client.screen.review.comment;

import java.util.LinkedHashSet;
import java.util.Set;

/** Independent derived parser; comment text remains the sole hashtag authority. */
public final class SFMCommentHashtags {
    private SFMCommentHashtags() {}
    public static Set<String> derive(String text) {
        LinkedHashSet<String> answer = new LinkedHashSet<>();
        for (int i=0;i<text.length();i++) {
            if (text.charAt(i)!='#') continue;
            if (i+1<text.length() && text.charAt(i+1)=='"') {
                int end=text.indexOf('"',i+2);
                if(end>i+2){ answer.add("#"+text.substring(i+2,end).toLowerCase(java.util.Locale.ROOT)); i=end; }
            } else {
                int end=i+1;
                while(end<text.length() && (Character.isLetterOrDigit(text.charAt(end))||text.charAt(end)=='_'||text.charAt(end)=='-')) end++;
                if(end>i+1){answer.add(text.substring(i,end).toLowerCase(java.util.Locale.ROOT));i=end-1;}
            }
        }
        return Set.copyOf(answer);
    }
}
