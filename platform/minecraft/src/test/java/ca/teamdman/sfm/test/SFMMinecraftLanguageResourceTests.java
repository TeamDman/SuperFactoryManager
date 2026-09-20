package ca.teamdman.sfm.test;

import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The plain JUnit runtime needs vanilla resources as well as Minecraft classes. */
class SFMMinecraftLanguageResourceTests {
    @Test
    void vanillaEnglishResourceIsAvailableWithoutAClientOrLanguageBootstrap() throws Exception {
        // A class literal does not initialize Language. Diagnose the absent resource
        // directly instead of letting the first translated exception poison the JVM.
        try (InputStream english = Language.class.getResourceAsStream("/assets/minecraft/lang/en_us.json")) {
            assertNotNull(english, "The JUnit classpath must include the locked vanilla resource-only jar");
            assertTrue(new String(english.readAllBytes(), StandardCharsets.UTF_8).contains("\"gui.done\""));
        }
    }

    @Test
    void translatableComponentsResolveTheRealVanillaLanguageWithoutLaunchingMinecraft() {
        assertTrue(Language.getInstance().has("gui.done"));
        assertEquals("Done", Component.translatable("gui.done").getString());
    }
}
