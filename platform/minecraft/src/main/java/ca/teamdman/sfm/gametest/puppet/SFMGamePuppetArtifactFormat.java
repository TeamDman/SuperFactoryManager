package ca.teamdman.sfm.gametest.puppet;

/** Supported, portable formats for bounded game-puppet evidence artifacts. */
public enum SFMGamePuppetArtifactFormat {
    UTF8("utf8", "txt", "text/plain; charset=utf-8"),
    JSON("json", "json", "application/json");

    private final String id;
    private final String extension;
    private final String contentType;

    SFMGamePuppetArtifactFormat(String id, String extension, String contentType) {
        this.id = id;
        this.extension = extension;
        this.contentType = contentType;
    }

    public String id() {
        return id;
    }

    public String extension() {
        return extension;
    }

    public String contentType() {
        return contentType;
    }
}
