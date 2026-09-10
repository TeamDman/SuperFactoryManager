package ca.teamdman.sfm.client.theme.preview;

import ca.teamdman.sfm.client.explorer.SFMPath;
import ca.teamdman.sfm.client.explorer.lazy.SFMExplorerEntry;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Immutable, already-known entry facts. Constructing/evaluating this never reads a file. */
public record SFMItemstackPreviewSubject(SFMPath path, String resolverId, Kind kind,
                                       String name, String basename, List<String> suffixes,
                                       Map<String, String> metadata) {
    public enum Kind { FILE, CONTAINER, OTHER }

    public SFMItemstackPreviewSubject {
        Objects.requireNonNull(path); Objects.requireNonNull(resolverId); Objects.requireNonNull(kind);
        Objects.requireNonNull(name); Objects.requireNonNull(basename);
        suffixes = List.copyOf(suffixes); metadata = Map.copyOf(metadata);
    }

    public static SFMItemstackPreviewSubject from(SFMExplorerEntry entry) {
        String name = entry.path().segments().isEmpty() ? entry.label()
                : entry.path().segments().get(entry.path().segments().size() - 1);
        // A review/container resolver can publish its actual name separately from its opaque address.
        name = entry.sortKey(SFMExplorerEntry.SUBJECT_NAME).value().orElse(name);
        Kind kind = entry.expandable() ? Kind.CONTAINER
                : entry.path().kind() == SFMPath.Kind.FILE ? Kind.FILE : Kind.OTHER;
        String declaredKind=entry.sortKey(SFMExplorerEntry.SUBJECT_KIND).value().orElse("");
        if (declaredKind.equals("file")) kind=Kind.FILE;
        else if (declaredKind.equals("container")) kind=Kind.CONTAINER;
        else if (declaredKind.equals("other")) kind=Kind.OTHER;
        var metadata=new java.util.TreeMap<String,String>();
        entry.sortKeys().forEach((key,value)->value.value().ifPresent(text->metadata.put(key,text)));
        return of(entry.path(), entry.path().scheme(), kind, name, metadata);
    }

    public static SFMItemstackPreviewSubject of(SFMPath path, String resolver, Kind kind,
                                               String name, Map<String, String> metadata) {
        int dot = name.lastIndexOf('.');
        String base = dot > 0 && dot < name.length() - 1 ? name.substring(0, dot) : name;
        ArrayList<String> suffixes = new ArrayList<>();
        for (int i = 1; i < name.length() - 1; i++) {
            if (name.charAt(i) == '.') suffixes.add(name.substring(i));
        }
        return new SFMItemstackPreviewSubject(path, resolver, kind, name, base, suffixes, metadata);
    }

    public List<String> prefixes() {
        ArrayList<String> answer = new ArrayList<>();
        for (int i = 0; i < name.length() && answer.size()<SFMItemstackPreviewExpression.MAX_LITERAL_CODEPOINTS;) {
            i += Character.charCount(name.codePointAt(i));
            answer.add(name.substring(0, i));
        }
        return List.copyOf(answer);
    }
}
