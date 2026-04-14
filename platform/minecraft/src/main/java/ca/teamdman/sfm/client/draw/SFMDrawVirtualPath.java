package ca.teamdman.sfm.client.draw;

import java.nio.file.Path;

public record SFMDrawVirtualPath(String virtualPath, Path mountedPath) {
}