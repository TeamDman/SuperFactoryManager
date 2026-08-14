package ca.teamdman.sfm.gametest.puppet.action;

import ca.teamdman.sfm.client.explorer.SFMPath;
import ca.teamdman.sfm.client.registry.SFMTextEditors;
import ca.teamdman.sfm.client.screen.explorer.SFMExplorerPanel;
import ca.teamdman.sfm.client.screen.text_editor.SFMTextDocumentPanelState;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenPanelBounds;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelId;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentSnapshot;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentSource;
import ca.teamdman.sfm.client.text_editor.SFMTextEditorPanelRecipe;
import ca.teamdman.sfm.gametest.puppet.ISFMGamePuppetRuntime;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppetArtifactFormat;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppetHelper;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;

/** Final machine witness for the real-source explorer-to-v3-editor journey. */
public final class AssertAddressedSfmJavaPuppetAction implements SFMPuppetAction {
    private final Path root;
    private final Path file;
    private final SFMPath rootAddress;
    private final SFMPath fileAddress;
    private final String baselineSha256;
    private final long baselineBytes;
    private final Instant baselineLastModified;
    private int ticks;

    public AssertAddressedSfmJavaPuppetAction(Path root, Path file) {
        this.root = Objects.requireNonNull(root, "root").toAbsolutePath().normalize();
        this.file = Objects.requireNonNull(file, "file").toAbsolutePath().normalize();
        rootAddress = SFMPath.fromNative(this.root);
        fileAddress = SFMPath.fromNative(this.file);
        try {
            byte[] bytes = Files.readAllBytes(this.file);
            baselineSha256 = sha256(bytes);
            baselineBytes = bytes.length;
            baselineLastModified = Files.getLastModifiedTime(this.file).toInstant();
        } catch (IOException failure) {
            throw new IllegalStateException("Could not establish the SFM.java no-write baseline", failure);
        }
    }

    @Override
    public String description() {
        return "assert real SFM.java addressed editor evidence";
    }

    @Override
    public boolean tick(ISFMGamePuppetRuntime runtime) {
        if (!(Minecraft.getInstance().screen instanceof SFMScreenMultiplexer workspace)) {
            return waitOrFail("the SFM workspace");
        }
        SFMWorkspacePanelId explorerId = null;
        SFMExplorerPanel explorer = null;
        SFMWorkspacePanelId editorId = null;
        SFMTextDocumentPanelState editor = null;
        for (SFMWorkspacePanelId panelId : workspace.panelIds()) {
            Object panel = workspace.panelInstance(panelId);
            if (panel instanceof SFMExplorerPanel value) {
                explorerId = panelId;
                explorer = value;
            } else if (panel instanceof SFMTextDocumentPanelState value) {
                editorId = panelId;
                editor = value;
            }
        }
        if (explorer == null || editor == null || editor.documentSnapshot().isEmpty()) {
            return waitOrFail("the addressed explorer and loaded editor");
        }

        SFMTextDocumentSnapshot document = editor.documentSnapshot().orElseThrow();
        require(document.state() == SFMTextDocumentSnapshot.State.READY,
                "Expected a ready document, got " + document.state());
        require(document.readOnly() && editor.isReadOnly(), "Addressed source must remain read-only");
        require(document.path().equals(java.util.Optional.of(fileAddress)), "Editor path identity changed");
        require(document.authorizedRoot().equals(java.util.Optional.of(rootAddress)),
                "Editor root authority changed");
        require(document.sha256().equals(java.util.Optional.of(baselineSha256)),
                "Loaded SFM.java hash does not match the baseline");
        require(document.byteLength().isPresent() && document.byteLength().getAsLong() == baselineBytes,
                "Loaded SFM.java byte length does not match the baseline");
        require(explorer.sessionSnapshot().navigationCursor().equals(java.util.Optional.of(fileAddress)),
                "Explorer did not retain SFM.java as its selected row");
        require(editorId.equals(workspace.focusedPanelId()), "Ctrl+Enter did not focus the new editor panel");
        require(workspace.visiblePanelEntries().size() == 2, "Expected one explorer and one adjacent editor");

        SFMTextEditorPanelRecipe recipe = workspace.reopenRecipe(editorId)
                .filter(SFMTextEditorPanelRecipe.class::isInstance)
                .map(SFMTextEditorPanelRecipe.class::cast)
                .orElseThrow(() -> new IllegalStateException("Addressed editor has no typed reopen recipe"));
        require(recipe.editorId().equals(SFMTextEditors.V3.getId().orElseThrow().location()),
                "Addressed files must use Text Editor v3");
        SFMTextDocumentSource.PathAddress source = (SFMTextDocumentSource.PathAddress) recipe.documentSource();
        require(source.expectedSha256().equals(java.util.Optional.of(baselineSha256)),
                "The first successful read did not pin the reopen recipe hash");

        try {
            byte[] current = Files.readAllBytes(file);
            require(sha256(current).equals(baselineSha256), "SFM.java contents changed during read-only opening");
            require(current.length == baselineBytes, "SFM.java size changed during read-only opening");
            require(Files.getLastModifiedTime(file).toInstant().equals(baselineLastModified),
                    "SFM.java timestamp changed during read-only opening");
        } catch (IOException failure) {
            throw new IllegalStateException("Could not verify the SFM.java no-write witness", failure);
        }

        SFMScreenPanelBounds explorerPhysical = requireBounds(workspace.panelBounds(explorerId), "explorer physical");
        SFMScreenPanelBounds editorPhysical = requireBounds(workspace.panelBounds(editorId), "editor physical");
        SFMScreenPanelBounds explorerLogical = requireBounds(workspace.panelContentBounds(explorerId), "explorer logical");
        SFMScreenPanelBounds editorLogical = requireBounds(workspace.panelContentBounds(editorId), "editor logical");
        JsonObject evidence = new JsonObject();
        evidence.addProperty("schema", "sfm.addressed-source-puppet/1");
        evidence.addProperty("root", rootAddress.canonical());
        evidence.addProperty("path", fileAddress.canonical());
        evidence.addProperty("sha256", baselineSha256);
        evidence.addProperty("byte_length", baselineBytes);
        evidence.addProperty("last_modified", baselineLastModified.toString());
        evidence.addProperty("read_only", true);
        evidence.addProperty("no_write", true);
        evidence.addProperty("editor", recipe.editorId().toString());
        evidence.addProperty("focused_panel", editorId.toString());
        evidence.addProperty("explorer_selection", explorer.sessionSnapshot().navigationCursor()
                .orElseThrow().canonical());
        evidence.addProperty("explorer_location", explorer.sessionSnapshot().location().canonical());
        evidence.add("target_range", JsonNull.INSTANCE);
        evidence.addProperty("configured_gui_scale", Minecraft.getInstance().options.guiScale().get());
        evidence.addProperty("effective_gui_scale", Minecraft.getInstance().getWindow().getGuiScale());
        evidence.add("explorer_physical_bounds", bounds(explorerPhysical));
        evidence.add("editor_physical_bounds", bounds(editorPhysical));
        evidence.add("explorer_logical_bounds", bounds(explorerLogical));
        evidence.add("editor_logical_bounds", bounds(editorLogical));
        runtime.writeArtifact(
                "addressed-sfm-java",
                SFMGamePuppetArtifactFormat.JSON,
                new GsonBuilder().setPrettyPrinting().create().toJson(evidence)
        );
        return true;
    }

    private boolean waitOrFail(String expected) {
        if (++ticks > SFMGamePuppetHelper.SCREEN_TIMEOUT_TICKS) {
            throw new IllegalStateException("Timed out waiting for " + expected);
        }
        return false;
    }

    private static JsonObject bounds(SFMScreenPanelBounds value) {
        JsonObject result = new JsonObject();
        result.addProperty("x", value.x());
        result.addProperty("y", value.y());
        result.addProperty("width", value.width());
        result.addProperty("height", value.height());
        return result;
    }

    private static SFMScreenPanelBounds requireBounds(SFMScreenPanelBounds value, String name) {
        if (value == null || value.width() <= 0 || value.height() <= 0) {
            throw new IllegalStateException("Missing or empty " + name + " bounds");
        }
        return value;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
