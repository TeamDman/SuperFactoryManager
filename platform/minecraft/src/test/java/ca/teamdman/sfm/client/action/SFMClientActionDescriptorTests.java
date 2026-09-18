package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.common.value.SFMValue;
import ca.teamdman.sfm.common.value.SFMValueSchema;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SFMClientActionDescriptorTests {
    @Test
    void numericSchemasKeepExactIntegersSeparateFromFloatingValues() {
        SFMValueSchema integer = SFMValueSchema.integer(Long.MAX_VALUE, Long.MAX_VALUE);
        assertTrue(integer.validate(SFMValue.of(Long.MAX_VALUE)).isEmpty());
        assertEquals(new SFMValueSchema.Failure("wrong_type", ""),
                integer.validate(SFMValue.of((double) Long.MAX_VALUE)).orElseThrow());

        SFMValueSchema floating = SFMValueSchema.floating(0.0, 1.0);
        assertTrue(floating.validate(SFMValue.of(0.5)).isEmpty());
        assertEquals("wrong_type", floating.validate(SFMValue.of(1L)).orElseThrow().code());
        assertEquals("out_of_range", floating.validate(SFMValue.of(1.1)).orElseThrow().code());

        SFMValueSchema either = SFMValueSchema.number(BigDecimal.ZERO, BigDecimal.valueOf(10));
        assertTrue(either.validate(SFMValue.of(10L)).isEmpty());
        assertTrue(either.validate(SFMValue.of(10.0)).isEmpty());
    }

    @Test
    void arraysUnionsOptionalAndOpenClosedObjectsHaveStablePaths() {
        SFMValueSchema item = SFMValueSchema.union(List.of(
                SFMValueSchema.string(1, 20),
                SFMValueSchema.integer(0, 9)
        ));
        Map<String, SFMValueSchema.Field> fields = Map.of(
                "items", SFMValueSchema.Field.required(SFMValueSchema.array(item, 1, 2)),
                "note", SFMValueSchema.Field.optional(SFMValueSchema.optional(SFMValueSchema.string(0, 20)))
        );
        SFMValueSchema closed = SFMValueSchema.object(fields, false);
        SFMValueSchema open = SFMValueSchema.object(fields, true);
        SFMValue valid = SFMValue.object(Map.of("items", SFMValue.array(List.of(SFMValue.of("first"), SFMValue.of(2L)))));
        assertTrue(closed.validate(valid).isEmpty());
        assertTrue(closed.validate(SFMValue.object(Map.of(
                "items", SFMValue.array(List.of(SFMValue.of(1L))),
                "note", SFMValue.nullValue()
        ))).isEmpty());

        assertEquals(new SFMValueSchema.Failure("missing_field", "/items"),
                closed.validate(SFMValue.object(Map.of())).orElseThrow());
        assertEquals(new SFMValueSchema.Failure("no_union_match", "/items/0"),
                closed.validate(SFMValue.object(Map.of("items", SFMValue.array(List.of(SFMValue.of(true))))))
                        .orElseThrow());
        assertEquals(new SFMValueSchema.Failure("extra_field", "/unknown"),
                closed.validate(SFMValue.object(Map.of(
                        "items", SFMValue.array(List.of(SFMValue.of(1L))),
                        "unknown", SFMValue.of(4L)
                ))).orElseThrow());
        assertTrue(open.validate(SFMValue.object(Map.of(
                "items", SFMValue.array(List.of(SFMValue.of(1L))),
                "unknown", SFMValue.of(4L)
        ))).isEmpty());
        assertEquals(new SFMValueSchema.Failure("length_out_of_range", "/items"),
                closed.validate(SFMValue.object(Map.of("items", SFMValue.array(List.of())))).orElseThrow());
    }

    @Test
    void unionAndOptionalWrappersDoNotConsumeTheValueNestingBudget() {
        SFMValue value = SFMValue.of(1L);
        SFMValueSchema schema = SFMValueSchema.integer(1, 1);
        for (int i = 0; i < SFMValueSchema.MAX_SCHEMA_DEPTH; i++) {
            value = SFMValue.array(List.of(value));
            schema = SFMValueSchema.array(schema, 1, 1);
        }
        assertTrue(SFMValueSchema.optional(SFMValueSchema.union(List.of(schema))).validate(value).isEmpty());
    }

    @Test
    void packetSendDescriptorIsTypedScopedAndNeverClaimsDelivery() {
        AtomicBoolean sent = new AtomicBoolean();
        SFMPacketSendAction action = new SFMPacketSendAction(Optional::empty, (address, value) -> {
            sent.set(true);
            return true;
        });
        SFMClientActionDescriptor descriptor = action.programmaticDescriptor().orElseThrow();
        assertEquals(new ResourceLocation("sfm", "packet/send"), descriptor.actionId());
        assertEquals(SFMClientActionDescriptor.ExecutionSide.CLIENT, descriptor.executionSide());
        assertEquals(SFMClientActionDescriptor.CostClass.SERVER_EFFECT, descriptor.costClass());
        assertEquals(SFMClientActionDescriptor.Acknowledgement.LOCAL_TRANSPORT_ATTEMPT_ONLY,
                descriptor.acknowledgement());
        assertEquals(SFMClientActionDescriptor.StatusKind.ATTEMPTED,
                descriptor.resultStatuses().get("send_attempted"));

        SFMValue input = SFMValue.object(Map.of(
                "dimension", SFMValue.of("minecraft:overworld"),
                "x", SFMValue.of(12L),
                "y", SFMValue.of(64L),
                "z", SFMValue.of(-7L),
                "side", SFMValue.of("north"),
                "value", SFMValue.object(Map.of("red", SFMValue.of(true)))
        ));
        SFMClientActionDescriptor.InputCheck.Accepted accepted = assertInstanceOf(
                SFMClientActionDescriptor.InputCheck.Accepted.class, descriptor.checkInput(input));
        assertEquals(1, accepted.dataScopes().size());
        assertEquals(descriptor.controlPermission(), accepted.dataScopes().get(0).permission());
        SFMValue.ObjectValue subject = (SFMValue.ObjectValue) accepted.dataScopes().get(0).subject();
        assertFalse(subject.fields().containsKey("value"));
        assertEquals(SFMValue.of(12L), subject.fields().get("x"));
        assertFalse(sent.get(), "describing or validating must not invoke the effect");

        assertTrue(descriptor.checkResult(SFMValue.object(Map.of(
                "status", SFMValue.of("send_attempted"),
                "local_transport_accepted", SFMValue.of(true)
        ))).isEmpty());
        assertEquals("no_union_match", descriptor.checkResult(SFMValue.object(Map.of(
                "status", SFMValue.of("delivered"),
                "local_transport_accepted", SFMValue.of(true)
        ))).orElseThrow().code());
    }

    @Test
    void packetSendRejectsInvalidScopeAndMalformedValuesBeforeEffect() {
        SFMPacketSendAction action = new SFMPacketSendAction(Optional::empty, (address, value) -> {
            throw new AssertionError("descriptor checks must not call the transport");
        });
        SFMClientActionDescriptor descriptor = action.programmaticDescriptor().orElseThrow();
        SFMValue invalidDimension = SFMValue.object(Map.of(
                "dimension", SFMValue.of("overworld"),
                "x", SFMValue.of(0L), "y", SFMValue.of(0L), "z", SFMValue.of(0L),
                "value", SFMValue.nullValue()
        ));
        SFMClientActionDescriptor.InputCheck.Rejected invalid = assertInstanceOf(
                SFMClientActionDescriptor.InputCheck.Rejected.class, descriptor.checkInput(invalidDimension));
        assertEquals(new SFMValueSchema.Failure("invalid_identifier", "/dimension"), invalid.failure());

        SFMValue wrongCoordinate = SFMValue.object(Map.of(
                "dimension", SFMValue.of("minecraft:overworld"),
                "x", SFMValue.of(0.25), "y", SFMValue.of(0L), "z", SFMValue.of(0L),
                "value", SFMValue.nullValue()
        ));
        SFMClientActionDescriptor.InputCheck.Rejected wrong = assertInstanceOf(
                SFMClientActionDescriptor.InputCheck.Rejected.class, descriptor.checkInput(wrongCoordinate));
        assertEquals(new SFMValueSchema.Failure("wrong_type", "/x"), wrong.failure());
        SFMValue overlarge = SFMValue.object(Map.of(
                "dimension", SFMValue.of("minecraft:overworld"),
                "x", SFMValue.of(0L), "y", SFMValue.of(0L), "z", SFMValue.of(0L),
                "value", SFMValue.of("x".repeat(4_000))
        ));
        SFMClientActionDescriptor.InputCheck.Rejected tooLarge = assertInstanceOf(
                SFMClientActionDescriptor.InputCheck.Rejected.class, descriptor.checkInput(overlarge));
        assertEquals(new SFMValueSchema.Failure("packet_value_out_of_bounds", "/value"), tooLarge.failure());

        SFMValue nearPacketLimit = SFMValue.object(Map.of(
                "dimension", SFMValue.of("minecraft:overworld"),
                "x", SFMValue.of(0L), "y", SFMValue.of(0L), "z", SFMValue.of(0L),
                "value", SFMValue.of("x".repeat(3_000))
        ));
        assertInstanceOf(SFMClientActionDescriptor.InputCheck.Accepted.class,
                descriptor.checkInput(nearPacketLimit),
                "the action wrapper must not reduce the existing packet payload budget");

        SFMValue actionEnvelopeOverflow = SFMValue.object(Map.of(
                "dimension", SFMValue.of("minecraft:overworld"),
                "x", SFMValue.of(0L), "y", SFMValue.of(0L), "z", SFMValue.of(0L),
                "value", SFMValue.of("x".repeat(20_000))
        ));
        SFMClientActionDescriptor.InputCheck.Rejected envelopeFailure = assertInstanceOf(
                SFMClientActionDescriptor.InputCheck.Rejected.class, descriptor.checkInput(actionEnvelopeOverflow));
        assertEquals("value_out_of_bounds", envelopeFailure.failure().code());
    }

    @Test
    void clipboardFilesystemAndProcessActionsRemainUnavailableToPrograms() {
        assertTrue(new SFMClipboardCopyAction().programmaticDescriptor().isEmpty());
        assertTrue(new SFMPathOpenAction().programmaticDescriptor().isEmpty());
        assertTrue(new StartRustServerAction().programmaticDescriptor().isEmpty());
    }
}
