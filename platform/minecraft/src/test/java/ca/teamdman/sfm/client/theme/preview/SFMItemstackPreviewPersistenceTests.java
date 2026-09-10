package ca.teamdman.sfm.client.theme.preview;

import ca.teamdman.sfm.client.theme.*;
import ca.teamdman.sfm.client.presentation.SFMItemIcon;
import com.electronwill.nightconfig.toml.TomlFormat;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

public class SFMItemstackPreviewPersistenceTests {
    @TempDir Path temporary;
    @AfterEach void reset() { SFMClientThemeService.resetForTests(); }
    private Path create(String name) throws Exception {
        Path file=temporary.resolve(name); Files.writeString(file,"schema_version=1\n[unrelated]\nkeep=[1,2,3]\n[colours]\n\"text.accent\"=\"#FF123456\"\n");
        assertTrue(SFMClientThemeService.reload(file).valid()); return file;
    }
    private SFMItemstackPreviewRules.Rule rule() {
        return SFMItemstackPreviewRules.Rule.user(SFMItemstackPreviewExpression.call("sfm:entry/is_file"),SFMItemIcon.vanilla("bell","bell"));
    }
    @Test void savePreservesUnrelatedValuesAndReloadRetainsExactRule() throws Exception {
        Path file=create("theme.toml"); var target=SFMClientThemeService.activeAuthority().orElseThrow();
        var result=SFMClientThemeService.mutatePreviewRules(target,before->List.of(rule()),()->true);
        assertTrue(result.valid(),result.diagnostics().toString());
        var parsed=TomlFormat.instance().createParser().parse(Files.readString(file));
        assertEquals(List.of(1L,2L,3L),((List<Number>)parsed.get("unrelated.keep")).stream().map(Number::longValue).toList());
        assertEquals(0xFF123456,SFMClientThemeService.active().colour(SFMColourRole.TEXT_ACCENT));
        SFMClientThemeService.resetForTests(); assertTrue(SFMClientThemeService.reload(file).valid());
        assertEquals(List.of(rule()),SFMClientThemeService.active().previewRules());
        assertNotEquals(target,SFMClientThemeService.activeAuthority().orElseThrow());
    }
    @Test void cancellationDiskChangeAndAuthoritySwitchCannotWriteOrRetarget() throws Exception {
        Path file=create("one.toml"); String original=Files.readString(file); var target=SFMClientThemeService.activeAuthority().orElseThrow();
        assertFalse(SFMClientThemeService.mutatePreviewRules(target,before->List.of(rule()),()->false).valid());
        assertEquals(original,Files.readString(file));
        Files.writeString(file,original+"\n# external edit\n");
        assertFalse(SFMClientThemeService.mutatePreviewRules(target,before->List.of(rule()),()->true).valid());
        Path other=create("two.toml"); String second=Files.readString(other);
        assertFalse(SFMClientThemeService.mutatePreviewRules(target,before->List.of(rule()),()->true).valid());
        assertEquals(second,Files.readString(other)); assertEquals(original+"\n# external edit\n",Files.readString(file));
    }
    @Test void malformedAndWriteFailureRetainLastValidSnapshotAndResetRemovesOnlyUserRule() throws Exception {
        Path file=create("theme.toml"); var target=SFMClientThemeService.activeAuthority().orElseThrow();
        assertTrue(SFMClientThemeService.mutatePreviewRules(target,before->List.of(rule()),()->true).valid());
        var valid=SFMClientThemeService.active(); var next=SFMClientThemeService.activeAuthority().orElseThrow();
        assertFalse(SFMClientThemeService.mutatePreviewRules(next,before->{throw new IllegalArgumentException("injected write preparation failure");},()->true).valid());
        assertSame(valid,SFMClientThemeService.active());
        assertTrue(SFMClientThemeService.mutatePreviewRules(next,before->List.of(),()->true).valid());
        assertTrue(SFMClientThemeService.active().previewRules().isEmpty());
        String reset=Files.readString(file); Files.writeString(file,"schema_version=99\n");
        assertFalse(SFMClientThemeService.reload(file).valid());
        assertTrue(SFMClientThemeService.active().previewRules().isEmpty());
        assertFalse(SFMClientThemeService.mutatePreviewRules(SFMClientThemeService.activeAuthority().orElseThrow(),before->List.of(rule()),()->true).valid());
        assertEquals("schema_version=99\n",Files.readString(file));
        assertTrue(reset.contains("unrelated"));
    }
}
