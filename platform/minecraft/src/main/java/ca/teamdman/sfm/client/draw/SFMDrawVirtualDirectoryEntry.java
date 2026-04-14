package ca.teamdman.sfm.client.draw;

public record SFMDrawVirtualDirectoryEntry(
        String displayName,
        String virtualPath,
        boolean directory
) {
    public String label() {
        return directory ? displayName + "/" : displayName;
    }
}