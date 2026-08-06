package ca.teamdman.sfm.client.keybinding;

import ca.teamdman.sfm.client.registry.SFMKeyboardUsageSituations;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.lwjgl.glfw.GLFW;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SFMKeyBindingStorageTests {
    @TempDir Path temporaryDirectory;

    @Test
    void schemaTwoRoundTripsUserStateAndTombstones() {
        Path path = temporaryDirectory.resolve("bindings.json");
        SFMKeyBinding binding = binding("user", GLFW.GLFW_KEY_K, SFMKeyModifier.CONTROL);
        SFMKeyBinding builtIn = SFMKeyBindingDefaults.definitions().get(0).withEnabled(false);
        SFMKeyBindingUserState expected = new SFMKeyBindingUserState(
                List.of(binding),
                Map.of(builtIn.bindingId(), SFMKeyBindingOverride.from(builtIn)),
                Set.of("builtin/workspace/diagnostics"),
                SFMKeyBindingDefaults.fingerprints());

        SFMKeyBindingStorage.save(path, expected);
        SFMKeyBindingStorage.LoadResult loaded = SFMKeyBindingStorage.load(path);

        assertEquals(SFMKeyBindingStorage.LoadStatus.LOADED_SCHEMA_2, loaded.status());
        assertEquals(expected, loaded.state());
        assertTrue(loaded.writable());
        assertFalse(loaded.migrated());
    }

    @Test
    void schemaOneMigratesExactlyToGlobalAfterCreatingBackup() throws Exception {
        Path path = temporaryDirectory.resolve("bindings.json");
        String schemaOne = schemaOne(GLFW.GLFW_KEY_K, "legacy", "sfm:echo");
        Files.writeString(path, schemaOne, StandardCharsets.UTF_8);

        SFMKeyBindingStorage.LoadResult loaded = SFMKeyBindingStorage.load(path);

        assertEquals(SFMKeyBindingStorage.LoadStatus.MIGRATED_SCHEMA_1, loaded.status());
        assertTrue(loaded.migrated());
        assertEquals(SFMKeyboardUsageSituations.GLOBAL,
                loaded.state().bindings().get(0).situationId());
        assertEquals(schemaOne, Files.readString(
                temporaryDirectory.resolve("bindings.json.schema-1.bak"), StandardCharsets.UTF_8));
        assertTrue(Files.readString(path).contains("\"schema\": 2"));
        assertTrue(Files.readString(path).contains("\"situationId\": \"sfm:global\""));
    }

    @Test
    void migrationTombstonesNewDefaultsThatOverlapLegacyGlobalGestures() throws Exception {
        Path path = temporaryDirectory.resolve("bindings.json");
        Files.writeString(path, schemaOne(GLFW.GLFW_KEY_F3, "legacy-f3", "sfm:echo"));

        SFMKeyBindingStorage.LoadResult loaded = SFMKeyBindingStorage.load(path);

        assertTrue(loaded.state().tombstones().contains("builtin/workspace/diagnostics"));
    }

    @Test
    void corruptAndFutureSchemaFilesRemainUntouchedAndReadOnly() throws Exception {
        Path corrupt = temporaryDirectory.resolve("corrupt.json");
        Path future = temporaryDirectory.resolve("future.json");
        String corruptBytes = "{ definitely not json";
        String futureBytes = "{\"schema\":99,\"bindings\":[]}";
        Files.writeString(corrupt, corruptBytes);
        Files.writeString(future, futureBytes);

        SFMKeyBindingStorage.LoadResult corruptResult = SFMKeyBindingStorage.load(corrupt);
        SFMKeyBindingStorage.LoadResult futureResult = SFMKeyBindingStorage.load(future);

        assertEquals(SFMKeyBindingStorage.LoadStatus.CORRUPT, corruptResult.status());
        assertEquals(SFMKeyBindingStorage.LoadStatus.UNSUPPORTED_SCHEMA, futureResult.status());
        assertFalse(corruptResult.writable());
        assertFalse(futureResult.writable());
        assertEquals(corruptBytes, Files.readString(corrupt));
        assertEquals(futureBytes, Files.readString(future));
    }

    @Test
    void missingRequiredArraysAreCorruptInsteadOfWritableEmptyState() {
        assertEquals(SFMKeyBindingStorage.LoadStatus.CORRUPT,
                SFMKeyBindingStorage.parse("{\"schema\":1}").status());
        assertEquals(SFMKeyBindingStorage.LoadStatus.CORRUPT,
                SFMKeyBindingStorage.parse("{\"schema\":2,\"bindings\":[]}").status());
    }

    @Test
    void newlyObservedDefaultsDoNotShadowExistingSchemaTwoUserGestures() {
        SFMKeyBinding legacyGlobal = binding("existing-f3", GLFW.GLFW_KEY_F3);
        String withoutObservedDefaults = SFMKeyBindingStorage.serialize(
                new SFMKeyBindingUserState(List.of(legacyGlobal), Map.of(), Set.of(), Map.of()));

        SFMKeyBindingStorage.LoadResult loaded = SFMKeyBindingStorage.parse(withoutObservedDefaults);

        assertTrue(loaded.state().tombstones().contains("builtin/workspace/diagnostics"));
        assertEquals(SFMKeyBindingDefaults.fingerprints(), loaded.state().defaultFingerprints());
    }

    @Test
    void saveFailureIsReturnedToTheCaller() throws Exception {
        Path parentFile = temporaryDirectory.resolve("not-a-directory");
        Files.writeString(parentFile, "occupied");

        SFMKeyBindingStorage.SaveResult saved = SFMKeyBindingStorage.save(
                parentFile.resolve("bindings.json"),
                SFMKeyBindingUserState.EMPTY);

        assertFalse(saved.succeeded());
        assertFalse(saved.diagnostics().isEmpty());
        assertEquals("occupied", Files.readString(parentFile));
    }

    @Test
    void duplicateBindingIdsMakeTheDocumentCorrupt() {
        String one = schemaOne(GLFW.GLFW_KEY_K, "duplicate", "sfm:help");
        String duplicate = one.replace("]\n}", "," + bindingJson(
                GLFW.GLFW_KEY_E, "duplicate", "sfm:echo") + "]\n}");

        assertEquals(SFMKeyBindingStorage.LoadStatus.CORRUPT,
                SFMKeyBindingStorage.parse(duplicate).status());
    }

    @Test
    void builtInOverridesTombstonesAndRestoreSurviveStateRoundTrip() {
        SFMKeyBinding builtIn = SFMKeyBindingDefaults.definitions().get(0);
        SFMKeyBindingProfile profile = new SFMKeyBindingProfile(
                SFMKeyBindingDefaults.definitions(),
                SFMKeyBindingUserState.EMPTY,
                builtInCatalog());
        assertTrue(profile.setEnabled(builtIn.bindingId(), false));
        assertFalse(profile.snapshot().bindings().stream()
                .filter(binding -> binding.bindingId().equals(builtIn.bindingId()))
                .findFirst().orElseThrow().enabled());
        assertTrue(profile.remove(builtIn.bindingId()));
        assertTrue(profile.isTombstoned(builtIn.bindingId()));

        SFMKeyBindingProfile restarted = new SFMKeyBindingProfile(
                SFMKeyBindingDefaults.definitions(),
                profile.userState(),
                builtInCatalog());
        assertTrue(restarted.isTombstoned(builtIn.bindingId()));
        assertTrue(restarted.restoreBuiltIn(builtIn.bindingId()));
        assertTrue(restarted.snapshot().bindings().contains(builtIn));
    }

    @Test
    void builtInOverridesInheritLaterImmutableDefinitionCorrections() {
        SFMKeyBinding original = new SFMKeyBinding(
                "builtin/test",
                "sfm:old_action",
                "sfm action invoke sfm:old_action",
                SFMKeyboardUsageSituations.DEFAULT,
                SFMKeySequence.of(SFMKeyStroke.of(GLFW.GLFW_KEY_B)),
                true);
        SFMKeyBindingProfile profile = new SFMKeyBindingProfile(
                List.of(original), SFMKeyBindingUserState.EMPTY, builtInCatalog());
        assertTrue(profile.setEnabled(original.bindingId(), false));
        assertTrue(profile.userState().bindings().isEmpty());
        assertTrue(profile.userState().defaultOverrides().containsKey(original.bindingId()));

        SFMKeyBinding corrected = new SFMKeyBinding(
                original.bindingId(),
                "sfm:new_action",
                "sfm action invoke sfm:new_action",
                original.situationId(),
                original.sequence(),
                true);
        SFMKeyBindingProfile restarted = new SFMKeyBindingProfile(
                List.of(corrected), profile.userState(), builtInCatalog());
        SFMKeyBinding effective = restarted.snapshot().bindings().get(0);

        assertEquals("sfm:new_action", effective.actionId());
        assertEquals("sfm action invoke sfm:new_action", effective.commandDraft());
        assertFalse(effective.enabled());
    }

    @Test
    void earlySchemaTwoFullDefaultOverrideMigratesToFieldOverride() {
        SFMKeyBinding builtIn = SFMKeyBindingDefaults.definitions().get(0).withEnabled(false);
        String prototype = SFMKeyBindingStorage.serialize(new SFMKeyBindingUserState(
                List.of(builtIn), Map.of(), Set.of(), SFMKeyBindingDefaults.fingerprints()));

        SFMKeyBindingStorage.LoadResult loaded = SFMKeyBindingStorage.parse(prototype);

        assertTrue(loaded.state().bindings().isEmpty());
        assertEquals(SFMKeyBindingOverride.from(builtIn),
                loaded.state().defaultOverrides().get(builtIn.bindingId()));
    }

    @Test
    void mainAndKeypadScaleDefaultsHaveParityAndReadablePlusDisplay() {
        List<SFMKeyBinding> increase = SFMKeyBindingDefaults.definitions().stream()
                .filter(binding -> binding.actionId().equals("sfm:panel/scale/increase"))
                .toList();

        assertEquals(2, increase.size());
        assertTrue(increase.stream().allMatch(binding ->
                binding.situationId().equals(SFMKeyboardUsageSituations.DEFAULT)));
        assertTrue(increase.stream().anyMatch(binding ->
                binding.sequence().strokes().get(0).keyCode() == GLFW.GLFW_KEY_EQUAL));
        assertTrue(increase.stream().anyMatch(binding ->
                binding.sequence().strokes().get(0).keyCode() == GLFW.GLFW_KEY_KP_ADD));
        assertEquals("Ctrl++", SFMKeyBindingDisplay.format(increase.stream()
                .filter(binding -> binding.sequence().strokes().get(0).keyCode() == GLFW.GLFW_KEY_EQUAL)
                .findFirst().orElseThrow().sequence()));
    }

    @Test
    void diagnosticsAndCloseAreDiscoverableContextualDefaults() {
        List<SFMKeyBinding> defaults = SFMKeyBindingDefaults.definitions();
        SFMKeyBinding diagnostics = defaults.stream()
                .filter(binding -> binding.actionId().equals("sfm:panel/diagnostics/open"))
                .findFirst().orElseThrow();
        SFMKeyBinding close = defaults.stream()
                .filter(binding -> binding.actionId().equals("sfm:panel/close"))
                .findFirst().orElseThrow();

        assertEquals(SFMKeyboardUsageSituations.DEFAULT, diagnostics.situationId());
        assertEquals(GLFW.GLFW_KEY_F3, diagnostics.sequence().strokes().get(0).keyCode());
        assertEquals(SFMKeyboardUsageSituations.DEFAULT, close.situationId());
        assertEquals(Set.of(SFMKeyModifier.CONTROL, SFMKeyModifier.SHIFT),
                close.sequence().strokes().get(0).modifiers());
    }

    @Test
    void serializationDoesNotDiscardIdsThatHappenToStartWithPuppet() {
        SFMKeyBinding binding = binding("puppet-user-choice", GLFW.GLFW_KEY_P);

        assertTrue(SFMKeyBindingStorage.serialize(new SFMKeyBindingUserState(
                List.of(binding), Set.of("puppet-default-choice"))).contains("puppet-user-choice"));
    }

    @Test
    void ephemeralEnableChangesRemainEphemeralAndEffective() {
        SFMKeyBindingProfile profile = new SFMKeyBindingProfile();
        SFMKeyBinding binding = binding("session-only", GLFW.GLFW_KEY_P);
        profile.putEphemeral(binding);

        assertTrue(profile.setEnabled(binding.bindingId(), false));

        assertFalse(profile.snapshot().bindings().stream()
                .filter(candidate -> candidate.bindingId().equals(binding.bindingId()))
                .findFirst().orElseThrow().enabled());
        assertTrue(profile.userState().bindings().isEmpty());
    }

    private static SFMKeyBinding binding(String id, int key, SFMKeyModifier... modifiers) {
        return new SFMKeyBinding(
                id,
                "sfm:help",
                "sfm action invoke sfm:help",
                SFMKeyboardUsageSituations.GLOBAL,
                SFMKeySequence.of(SFMKeyStroke.of(key, modifiers)),
                true);
    }

    private static SFMKeyboardUsageSituationCatalog builtInCatalog() {
        return new SFMKeyboardUsageSituationCatalog(SFMKeyboardUsageSituations.builtIns());
    }

    private static String schemaOne(int key, String id, String action) {
        return """
                {
                  "schema": 1,
                  "bindings": [%s]
                }
                """.formatted(bindingJson(key, id, action));
    }

    private static String bindingJson(int key, String id, String action) {
        return """
                {
                  "bindingId": "%s",
                  "actionId": "%s",
                  "commandDraft": "sfm action invoke %s value",
                  "enabled": true,
                  "strokes": [{"keyCode": %d, "modifiers": []}]
                }
                """.formatted(id, action, action, key);
    }
}
