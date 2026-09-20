package ca.teamdman.sfm.client.tooltip;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Guards physical-key compatibility and client-only wiring without depending on a native input window. */
class SFMTooltipModeSourceTests {
    @Test void deprecatedKeyQueryKeepsPhysicalSemanticsSeparateFromTooltipIntent() throws Exception {
        String utility = source("common/util/SFMItemUtils.java");
        String physical = method(utility, "isClientAndMoreInfoKeyPressed()");
        assertTrue(physical.contains("SFMEnvironmentUtils.isClient() && SFMKeyMappings.isKeyDown(SFMKeyMappings.MORE_INFO_TOOLTIP_KEY)"));
        assertFalse(physical.contains("isClientAndMoreInfoRequested"));
        assertFalse(physical.contains("SFMTooltipModeService"));
        String semantic = method(utility, "isClientAndMoreInfoRequested()");
        assertTrue(semantic.contains("SFMEnvironmentUtils.isClient() && SFMTooltipModeService.INSTANCE.isExpanded()"));
        assertFalse(semantic.contains("SFMKeyMappings.isKeyDown"));
    }

    @Test void everyInTreeMoreInfoConsumerUsesSemanticIntent() throws Exception {
        for (String item : List.of("PacketItem", "DiskItem", "LabelGunItem")) {
            String consumer = source("common/item/" + item + ".java");
            assertTrue(consumer.contains("SFMItemUtils.isClientAndMoreInfoRequested()"), item);
            assertFalse(consumer.contains("isClientAndMoreInfoKeyPressed()"), item);
            assertFalse(consumer.contains("MORE_INFO_TOOLTIP_KEY"), item);
        }
        String form = source("client/render/FormItemRenderer.java");
        assertTrue(form.contains("SFMTooltipModeService.INSTANCE.isExpanded()"));
        assertFalse(form.contains("MORE_INFO_TOOLTIP_KEY"));
    }

    @Test void logoutWiringDistinguishesPlayerSessionsAndRegistrationStaysClientOnly() throws Exception {
        String lifecycle = source("client/tooltip/SFMTooltipModeLifecycle.java");
        assertTrue(lifecycle.contains("@SFMSubscribeEvent(SFMDist.CLIENT)"));
        assertTrue(lifecycle.contains("resetAfterLogout(SFMTooltipModeService.INSTANCE, event.getPlayer() != null)"));

        String commonRegistration = source("SFM.java");
        int distGuard = commonRegistration.indexOf("DistExecutor.safeRunWhenOn(");
        int clientDelegate = commonRegistration.indexOf("SFMClientRegistrations::register", distGuard);
        assertTrue(distGuard >= 0 && clientDelegate > distGuard);
        assertFalse(commonRegistration.contains("SFMTooltipModeActions"));
        assertFalse(commonRegistration.contains("SFMMultiplayerClientRuntime"));

        String clientRegistration = source("client/SFMClientRegistrations.java");
        int clientSetup = clientRegistration.indexOf("bus.addListener((FMLClientSetupEvent event)");
        for (String contributor : List.of(
                "SFMTooltipModeActions",
                "SFMPacketActions",
                "SFMClientProgramConsentActions",
                "SFMClientProgramReadActions",
                "SFMTerminalDisplayActions"
        )) {
            int action = clientRegistration.indexOf(contributor + ".register(bus)");
            assertTrue(action >= 0 && action < clientSetup, contributor);
        }
        assertTrue(clientRegistration.indexOf("SFMMultiplayerClientRuntime.initialize();", clientSetup) > clientSetup);
    }

    private static String method(String source, String name) {
        int start = source.indexOf("public static boolean " + name);
        assertTrue(start >= 0);
        return source.substring(start, source.indexOf("\n    }", start));
    }
    private static String source(String relative) throws Exception {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        while (current != null) {
            for (Path project : List.of(current, current.resolve("platform/minecraft"))) {
                Path file = project.resolve("src/main/java/ca/teamdman/sfm").resolve(relative);
                if (Files.isRegularFile(file)) return Files.readString(file).replace("\r", "");
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Could not locate Minecraft source: " + relative);
    }
}
