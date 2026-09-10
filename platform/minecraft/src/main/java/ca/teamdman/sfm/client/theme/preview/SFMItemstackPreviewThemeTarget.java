package ca.teamdman.sfm.client.theme.preview;

import java.net.URI;
import java.nio.file.Path;
import java.util.Objects;

/** Exact portable file authority plus optimistic revision, captured without I/O at completion time. */
public record SFMItemstackPreviewThemeTarget(Path path,String sha256) {
    public SFMItemstackPreviewThemeTarget {
        path=Objects.requireNonNull(path).toAbsolutePath().normalize();
        if (sha256==null || !sha256.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("Expected lowercase SHA-256 theme revision");
    }
    public String canonical() { return path.toUri().toASCIIString()+"#sha256="+sha256; }
    public String argument() { return SFMItemstackPreviewExpression.quote(canonical()); }
    public static SFMItemstackPreviewThemeTarget parse(String value) {
        int separator=value.lastIndexOf("#sha256=");
        if (separator<0) throw new IllegalArgumentException("Theme target must contain #sha256=<revision>");
        URI uri=URI.create(value.substring(0,separator));
        if (!"file".equals(uri.getScheme()) || uri.getFragment()!=null || uri.getQuery()!=null)
            throw new IllegalArgumentException("Theme target must be an exact file URI");
        return new SFMItemstackPreviewThemeTarget(Path.of(uri),value.substring(separator+8));
    }
}
