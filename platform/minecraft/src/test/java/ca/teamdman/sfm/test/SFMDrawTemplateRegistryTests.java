package ca.teamdman.sfm.test;

import ca.teamdman.sfm.common.template.SFMDrawTemplate;
import ca.teamdman.sfm.common.template.SFMDrawTemplateRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class SFMDrawTemplateRegistryTests {
    @Test
    void drawTemplateRegistryLoadsBuiltInTemplates() {
        List<SFMDrawTemplate> templates = SFMDrawTemplateRegistry.gatherAll();

        assertFalse(templates.isEmpty());
    }

    @Test
    void drawTemplateRegistryFindsTemplateByKeyDisplayNameAndPath() {
        SFMDrawTemplate template = SFMDrawTemplateRegistry.findByName("changelog");

        assertNotNull(template);
        assertFalse(template.programString().isBlank());
        assertEquals(template, SFMDrawTemplateRegistry.findByName(template.resourcePath()));
        assertEquals(template, SFMDrawTemplateRegistry.findByName(template.displayName()));
    }

    @Test
    void drawTemplateRegistryFindsSpatialOllamaWalkthroughTemplate() {
        SFMDrawTemplate template = SFMDrawTemplateRegistry.findByName("draw_ollama_prompt_concatenation");

        assertNotNull(template);
        assertEquals("Draw: Ollama prompt concatenation", template.displayName());
        assertFalse(template.programString().isBlank());
    }
}