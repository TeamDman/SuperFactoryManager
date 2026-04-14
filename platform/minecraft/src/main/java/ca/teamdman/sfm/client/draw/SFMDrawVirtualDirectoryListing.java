package ca.teamdman.sfm.client.draw;

import java.util.List;

public record SFMDrawVirtualDirectoryListing(
        SFMDrawVirtualPath target,
        List<SFMDrawVirtualDirectoryEntry> entries
) {
}