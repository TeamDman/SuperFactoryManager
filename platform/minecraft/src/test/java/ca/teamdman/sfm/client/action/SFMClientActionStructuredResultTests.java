package ca.teamdman.sfm.client.action;

import com.google.gson.JsonObject;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SFMClientActionStructuredResultTests {
    @Test
    void legacyConstructorsKeepFeedbackAndStructuredResultsIndependent() {
        SFMClientActionContext context = SFMClientActionContext.create(null, () -> true);
        ArrayList<Component> feedback = new ArrayList<>();
        SFMClientActionSource legacy = new SFMClientActionSource(context, feedback::add);

        legacy.sendFeedback(Component.literal("human"));
        legacy.publishStructuredResult(SFMClientActionStructuredResult.of(
                "sfm.packet.list/1",
                new JsonObject()
        ));

        assertEquals("human", feedback.get(0).getString());
    }

    @Test
    void structuredResultsRequireVersionedSchemaAndBoundedJsonObjects() {
        assertThrows(IllegalArgumentException.class,
                () -> new SFMClientActionStructuredResult("not-versioned", "{}"));
        assertThrows(IllegalArgumentException.class,
                () -> new SFMClientActionStructuredResult("sfm.packet.list/1", "[]"));
        assertThrows(IllegalArgumentException.class,
                () -> new SFMClientActionStructuredResult("sfm.packet.list/1", "not-json"));
        assertThrows(IllegalArgumentException.class,
                () -> new SFMClientActionStructuredResult(
                        "sfm.packet.list/1",
                        "{\"value\":\"" + (char) 0xD800 + "\"}"
                ));
    }
}
