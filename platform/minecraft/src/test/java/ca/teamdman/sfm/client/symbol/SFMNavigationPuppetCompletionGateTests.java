package ca.teamdman.sfm.client.symbol;

import ca.teamdman.sfm.client.context.SFMContextContribution;
import ca.teamdman.sfm.client.context.SFMContextDocumentProjection;
import ca.teamdman.sfm.client.context.SFMContextGenerationEvidence;
import ca.teamdman.sfm.client.context.SFMContextOriginId;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelId;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentSnapshot;
import ca.teamdman.sfm.properties.SFMProperties;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SFMNavigationPuppetCompletionGateTests {
    @Test
    void controllerHookCanAttachOnlyToAnIdeGamePuppetLaunch() {
        for (SFMProperties.ClientRunMode runMode : SFMProperties.ClientRunMode.values()) {
            assertEquals(
                    runMode == SFMProperties.ClientRunMode.GAME_PUPPET,
                    SFMNavigationPuppetCompletionGate.puppetHookAllowed(runMode, true)
            );
            assertFalse(SFMNavigationPuppetCompletionGate.puppetHookAllowed(runMode, false));
        }
    }

    @Test
    void disabledPolicyCannotArmAndAnUnarmedGateCannotDelayCompletion() {
        SFMNavigationPuppetCompletionGate gate = SFMNavigationPuppetCompletionGate.forTests(
                () -> false,
                System::nanoTime,
                (timeout, action) -> { }
        );
        SFMNavigationRequestWitness witness = witness();
        AtomicInteger completions = new AtomicInteger();

        assertThrows(IllegalStateException.class, () -> gate.arm(Duration.ofSeconds(1)));
        gate.dispatchCompletion(witness, completions::incrementAndGet);

        assertEquals(1, completions.get(), "an unarmed hook must remain a direct pass-through");
    }

    @Test
    void acceptedCompletionIsHeldUntilReleaseAndRetainsTheTypedRejection() {
        AtomicLong nanos = new AtomicLong(100);
        AtomicReference<Runnable> timeoutAction = new AtomicReference<>();
        SFMNavigationPuppetCompletionGate gate = SFMNavigationPuppetCompletionGate.forTests(
                () -> true,
                nanos::incrementAndGet,
                (timeout, action) -> timeoutAction.set(action)
        );
        SFMNavigationRequestWitness witness = witness();
        AtomicInteger completions = new AtomicInteger();

        try (SFMNavigationPuppetCompletionGate.Lease lease = gate.arm(Duration.ofSeconds(5))) {
            gate.accepted(witness);
            gate.dispatchCompletion(witness, completions::incrementAndGet);

            SFMNavigationPuppetCompletionGate.Observation held = lease.observation();
            assertTrue(held.accepted());
            assertTrue(held.completionHeld());
            assertFalse(held.released());
            assertEquals(0, completions.get());

            lease.release();
            gate.rejected(witness, SFMNavigationRequestWitness.RejectionReason.DOCUMENT_CONTENT_CHANGED);

            SFMNavigationPuppetCompletionGate.Observation rejected = lease.observation();
            assertEquals(1, completions.get());
            assertTrue(rejected.released());
            assertFalse(rejected.timedOut());
            assertEquals(Optional.of("DOCUMENT_CONTENT_CHANGED"), rejected.rejectionCode());
            assertEquals(
                    Optional.of("the source document bytes changed"),
                    rejected.rejectionDescription()
            );
        }
        assertTrue(timeoutAction.get() != null, "every armed gate must schedule a bounded timeout");
    }

    @Test
    void timeoutReleasesHeldCompletionAndMarksTheLease() {
        AtomicReference<Runnable> timeoutAction = new AtomicReference<>();
        SFMNavigationPuppetCompletionGate gate = SFMNavigationPuppetCompletionGate.forTests(
                () -> true,
                System::nanoTime,
                (timeout, action) -> timeoutAction.set(action)
        );
        SFMNavigationRequestWitness witness = witness();
        AtomicInteger completions = new AtomicInteger();

        try (SFMNavigationPuppetCompletionGate.Lease lease = gate.arm(Duration.ofSeconds(5))) {
            gate.accepted(witness);
            gate.dispatchCompletion(witness, completions::incrementAndGet);
            timeoutAction.get().run();

            assertEquals(1, completions.get());
            assertTrue(lease.observation().released());
            assertTrue(lease.observation().timedOut());
        }
    }

    private static SFMNavigationRequestWitness witness() {
        String text = "class Use { Target value; }\n";
        SFMTextDocumentSnapshot baseline = SFMTextDocumentSnapshot.literal(text);
        SFMContextContribution contribution = new SFMContextContribution(
                new SFMContextOriginId("sfm:text-editor", "panel-1", "editor-v3"),
                new SFMContextGenerationEvidence(1, 1, 1, 7),
                SFMContextDocumentProjection.capture(
                        "editor-v3",
                        baseline,
                        text,
                        false,
                        true,
                        List.of(),
                        List.of()
                )
        );
        return SFMNavigationRequestWitness.capture(
                uninitializedWorkspace(),
                new SFMWorkspacePanelId(1),
                new Object(),
                contribution,
                3
        );
    }

    private static SFMScreenMultiplexer uninitializedWorkspace() {
        try {
            Field field = Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            return (SFMScreenMultiplexer) ((Unsafe) field.get(null))
                    .allocateInstance(SFMScreenMultiplexer.class);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("Could not allocate a headless workspace test double", failure);
        }
    }
}
