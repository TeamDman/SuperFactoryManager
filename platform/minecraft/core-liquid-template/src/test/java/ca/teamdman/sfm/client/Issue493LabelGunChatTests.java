package ca.teamdman.sfm.client;

import ca.teamdman.sfm.common.config.SFMConfig;
import ca.teamdman.sfm.common.localization.LocalizationEntry;
import ca.teamdman.sfm.common.net.ClientboundLabelGunUseResponsePacket;
import ca.teamdman.sfm.common.net.SFMPacketDaddy.PacketDirection;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1' %}
import net.minecraftforge.common.ForgeConfigSpec;
{% else %}
import net.neoforged.neoforge.common.ModConfigSpec;
{% endcase %}
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Dispatch tests call the production message-sink overload without initializing
 * a Minecraft client, key binding, item registry, or native window. The separate
 * class-resource checks establish compiled dependency boundaries only; they do
 * not substitute for graphical or multiplayer gameplay acceptance.
 */
public class Issue493LabelGunChatTests {
    @Test
    public void issue493EnabledPushDispatchesTheExistingPushedMessage() {
        List<LocalizationEntry> messages = new ArrayList<>();
        ClientLabelGunResponseChatHelper.handle(ClientboundLabelGunUseResponsePacket.Behaviour.Pushed, true, messages::add);
        assertEquals(List.of(ClientLabelGunResponseChatHelper.LABEL_GUN_CHAT_PUSHED), messages);
    }

    @Test
    public void issue493EnabledPullDispatchesTheExistingPulledMessage() {
        List<LocalizationEntry> messages = new ArrayList<>();
        ClientLabelGunResponseChatHelper.handle(ClientboundLabelGunUseResponsePacket.Behaviour.Pulled, true, messages::add);
        assertEquals(List.of(ClientLabelGunResponseChatHelper.LABEL_GUN_CHAT_PULLED), messages);
    }

    @Test
    public void issue493DisabledPushDoesNotTouchTheMessageSink() {
        ClientLabelGunResponseChatHelper.handle(ClientboundLabelGunUseResponsePacket.Behaviour.Pushed, false,
                ignored -> fail("Disabled push resolved a message sink"));
    }

    @Test
    public void issue493DisabledPullDoesNotTouchTheMessageSink() {
        ClientLabelGunResponseChatHelper.handle(ClientboundLabelGunUseResponsePacket.Behaviour.Pulled, false,
                ignored -> fail("Disabled pull resolved a message sink"));
    }

    @Test
    public void issue493DefaultEnabledDispatchesBothExistingSuccessResponses() {
        assertTrue(SFMConfig.CLIENT_CONFIG.showLabelGunPushPullMessages.getDefault());
        boolean enabled = SFMConfig.getOrDefault(SFMConfig.CLIENT_CONFIG.showLabelGunPushPullMessages);
        assertTrue(enabled, "A new or unloaded client config must preserve chat confirmations");
        List<LocalizationEntry> messages = new ArrayList<>();
        ClientLabelGunResponseChatHelper.handle(ClientboundLabelGunUseResponsePacket.Behaviour.Pushed, enabled, messages::add);
        ClientLabelGunResponseChatHelper.handle(ClientboundLabelGunUseResponsePacket.Behaviour.Pulled, enabled, messages::add);
        assertEquals(List.of(ClientLabelGunResponseChatHelper.LABEL_GUN_CHAT_PUSHED,
                ClientLabelGunResponseChatHelper.LABEL_GUN_CHAT_PULLED), messages);
    }

    @Test
    public void issue493UnloadedConfigFallbackPreservesBothBooleanDefaults() {
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1' %}
        var builder = new ForgeConfigSpec.Builder();
{% else %}
        var builder = new ModConfigSpec.Builder();
{% endcase %}
        var enabled = builder.define("enabled", true);
        var disabled = builder.define("disabled", false);
        builder.build();
        // A fresh isolated spec has no loaded config and never mutates the user's settings.
        assertTrue(SFMConfig.getOrDefault(enabled));
        assertFalse(SFMConfig.getOrDefault(disabled));
    }

    @Test
    public void issue493OverlayAndChatHaveSeparateCompiledConfigDependencies() throws IOException {
        assertNotSame(SFMConfig.CLIENT_CONFIG.showLabelGunReminderOverlay,
                SFMConfig.CLIENT_CONFIG.showLabelGunPushPullMessages);
        assertTrue(SFMConfig.CLIENT_CONFIG.showLabelGunReminderOverlay.getDefault());
        String chat = classConstants("ca/teamdman/sfm/client/ClientLabelGunResponseChatHelper");
        assertTrue(chat.contains("showLabelGunPushPullMessages"));
        assertFalse(chat.contains("showLabelGunReminderOverlay"));
        String overlay = classConstants("ca/teamdman/sfm/client/overlay/LabelGunReminderOverlay");
        assertTrue(overlay.contains("showLabelGunReminderOverlay"));
        assertFalse(overlay.contains("showLabelGunPushPullMessages"));
    }

    @Test
    public void issue493WarningAndServerTransferPathsDoNotReadTheChatPreference() throws IOException {
        String warning = classConstants("ca/teamdman/sfm/client/ClientLabelGunWarningHelper");
        String server = classConstants("ca/teamdman/sfm/common/net/ServerboundLabelGunUsePacket$Daddy");
        String transfer = classConstants("ca/teamdman/sfm/common/label/LabelGunManagerPushOrPullAction");
        for (String independent : List.of(warning, server, transfer)) {
            assertFalse(independent.contains("showLabelGunPushPullMessages"));
            assertFalse(independent.contains("ClientLabelGunResponseChatHelper"));
        }
        assertTrue(warning.contains("getConfirmation") && warning.contains("sendToServer")
                && warning.contains("SFMConfirmationScreen"), "Confirmation/warning dispatch must remain compiled");
        assertTrue(server.contains("getLabelGunPlan") && server.contains("run"), "Server action dispatch must remain compiled");
        assertTrue(transfer.contains("sendToPlayer") && transfer.contains("Pushed") && transfer.contains("Pulled"));
        assertTrue(transfer.contains("rebuildProgramAndUpdateDisk") || transfer.contains("LabelGunActions"),
                "Label transfer implementation must remain compiled independently of the chat preference");
    }

    @Test
    public void issue493ResponseRemainsClientboundAndDelegatesToTheLocalHandler() throws IOException {
        assertEquals(PacketDirection.CLIENTBOUND, new ClientboundLabelGunUseResponsePacket.Daddy().getPacketDirection());
        assertEquals(List.of(ClientboundLabelGunUseResponsePacket.Behaviour.Pushed,
                ClientboundLabelGunUseResponsePacket.Behaviour.Pulled),
                List.of(ClientboundLabelGunUseResponsePacket.Behaviour.values()));
        String packet = classConstants("ca/teamdman/sfm/common/net/ClientboundLabelGunUseResponsePacket$Daddy");
        assertTrue(packet.contains("ClientLabelGunResponseChatHelper"));
        assertFalse(packet.contains("showLabelGunPushPullMessages"),
                "The response packet must not acquire a server-synchronized chat preference");
    }

    private static String classConstants(String internalName) throws IOException {
        // ASCII member/class names appear verbatim in the classfile constant pool.
        // Read class bytes as resources without initializing their client/server dependencies.
        try (var stream = Issue493LabelGunChatTests.class.getResourceAsStream("/" + internalName + ".class")) {
            assertNotNull(stream, "Missing compiled production class " + internalName);
            return new String(stream.readAllBytes(), StandardCharsets.ISO_8859_1);
        }
    }
}
