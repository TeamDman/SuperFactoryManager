package ca.teamdman.sfm.client.theme;

import ca.teamdman.sfm.client.theme.preview.*;
import ca.teamdman.sfm.client.presentation.SFMItemIcon;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

public class SFMItemstackPreviewAtomicWriteTests {
    @TempDir Path directory;
    @AfterEach void reset() { SFMClientThemeService.resetForTests(); }
    @Test void failedActualWriteBoundaryRetainsFileAuthorityAndLastValidTheme() throws Exception {
        Path file=directory.resolve("failure.toml");String original="schema_version=1\n[unrelated]\nkeep=42\n";Files.writeString(file,original);
        assertTrue(SFMClientThemeService.reload(file).valid());var baseline=SFMClientThemeService.active();var target=SFMClientThemeService.activeAuthority().orElseThrow();
        var writes=new AtomicInteger();var rule=SFMItemstackPreviewRules.Rule.user(SFMItemstackPreviewExpression.call("sfm:entry/is_file"),SFMItemIcon.vanilla("bell","bell"));
        var result=SFMClientThemeService.mutatePreviewRules(target,before->List.of(rule),()->true,(path,text)->{
            assertEquals(file.toRealPath(),path);assertTrue(text.contains("itemstack_preview_rules"));writes.incrementAndGet();throw new IOException("injected atomic replacement failure");
        });
        assertEquals(1,writes.get());assertFalse(result.valid());assertEquals(original,Files.readString(file));
        assertSame(baseline,SFMClientThemeService.active());assertEquals(target,SFMClientThemeService.activeAuthority().orElseThrow());
        assertTrue(result.diagnostics().toString().contains("injected atomic replacement failure"));
    }
}
