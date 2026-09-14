package ca.teamdman.sfm.common.program;

import ca.teamdman.sfm.common.value.SFMValue;
import ca.teamdman.sfm.common.value.SFMValuePattern;
import ca.teamdman.sfml.ast.CreateInputStatement;
import ca.teamdman.sfml.ast.ObjectConstructionValueExpression;
import ca.teamdman.sfml.ast.ObjectFieldValueExpression;
import ca.teamdman.sfml.ast.SFMTextResourceAdapters;
import ca.teamdman.sfml.ast.TextReadValueExpression;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProgramValueExecutionTests {
    static {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void objectConstructionPreservesEqualOccurrencesAndMemoizesFreshGuids() {
        ProgramContext context = ProgramContext.createDetachedTestContext(null, new ExecuteProgramBehaviour());
        ProgramOccurrenceId firstId = ProgramOccurrenceId.create();
        ProgramOccurrenceId secondId = ProgramOccurrenceId.create();
        context.getVariableEnvironment().setRelation("prompt", new ProgramRelation(List.of(
                new ProgramRelationRow(firstId, ProgramValueReference.resolved(SFMValue.of("same"))),
                new ProgramRelationRow(secondId, ProgramValueReference.resolved(SFMValue.of("same")))
        )));

        SFMValuePattern.ObjectPattern requestPattern = new SFMValuePattern.ObjectPattern(Map.of(
                "type", new SFMValuePattern.LiteralPattern(SFMValue.of("Request")),
                "prompt", SFMValuePattern.STRING,
                "JobId", SFMValuePattern.GUID
        ));
        LinkedHashMap<String, ObjectFieldValueExpression> fields = new LinkedHashMap<>();
        fields.put("prompt", new ObjectFieldValueExpression.Variable("prompt"));
        fields.put("JobId", new ObjectFieldValueExpression.NewGuid());
        ProgramRelation requests = new ObjectConstructionValueExpression("Request", requestPattern, fields)
                .evaluate(context);

        assertEquals(2, requests.rows().size());
        assertSame(firstId, requests.rows().get(0).occurrenceId());
        assertSame(secondId, requests.rows().get(1).occurrenceId());
        ProgramValueReference first = (ProgramValueReference) requests.rows().get(0).value();
        ProgramValueReference second = (ProgramValueReference) requests.rows().get(1).value();
        assertFalse(first.isResolved());
        SFMValue.ObjectValue firstValue = (SFMValue.ObjectValue) first.get();
        assertSame(firstValue, first.get());
        SFMValue.ObjectValue secondValue = (SFMValue.ObjectValue) second.get();
        assertEquals(SFMValue.of("same"), firstValue.fields().get("prompt"));
        assertNotEquals(firstValue.fields().get("JobId"), secondValue.fields().get("JobId"));
        context.free();
    }

    @Test
    void emptyRelationsStayEmptyAndCreateRegistersOnlyLazyOccurrences() {
        ProgramContext context = ProgramContext.createDetachedTestContext(null, new ExecuteProgramBehaviour());
        context.getVariableEnvironment().setRelation("prompt", ProgramRelation.EMPTY);
        SFMValuePattern.ObjectPattern pattern = new SFMValuePattern.ObjectPattern(Map.of(
                "prompt", SFMValuePattern.STRING,
                "JobId", SFMValuePattern.GUID
        ));
        ProgramRelation empty = new ObjectConstructionValueExpression("Request", pattern, Map.of(
                "prompt", new ObjectFieldValueExpression.Variable("prompt"),
                "JobId", new ObjectFieldValueExpression.NewGuid()
        )).evaluate(context);
        assertEquals(ProgramRelation.EMPTY, empty);

        context.getVariableEnvironment().setRelation("requests", new ProgramRelation(List.of(
                new ProgramRelationRow(ProgramOccurrenceId.create(), ProgramValueReference.lazy(() -> SFMValue.of("a"))),
                new ProgramRelationRow(ProgramOccurrenceId.create(), ProgramValueReference.lazy(() -> SFMValue.of("b")))
        )));
        new CreateInputStatement("sfm:packet", "requests").tick(context);
        assertEquals(2, context.getInputs().size());
        context.getInputs().forEach(source -> assertFalse(((GeneratedItemProgramInputSource) source).isMaterialized()));
        context.free();
    }

    @Test
    void constructorsRejectUnrelatedRelations() {
        SFMValuePattern.ObjectPattern pattern = new SFMValuePattern.ObjectPattern(Map.of(
                "a", SFMValuePattern.STRING,
                "b", SFMValuePattern.STRING
        ));
        assertThrows(IllegalArgumentException.class, () -> new ObjectConstructionValueExpression(
                "Pair",
                pattern,
                Map.of(
                        "a", new ObjectFieldValueExpression.Variable("left"),
                        "b", new ObjectFieldValueExpression.Variable("right")
                )
        ));
    }

    @Test
    void textReadUsesCopiedWritableAndWrittenBookContent() {
        var itemType = new GeneratedItemProgramInputSourceTests.TestItemResourceType();
        ItemStack writable = new ItemStack(Items.WRITABLE_BOOK);
        ListTag writablePages = new ListTag();
        writablePages.add(StringTag.valueOf("first"));
        writablePages.add(StringTag.valueOf("second"));
        writable.getOrCreateTag().put("pages", writablePages);

        ProgramContext context = ProgramContext.createDetachedTestContext(null, new ExecuteProgramBehaviour());
        context.getVariableEnvironment().setRelation("book", new ProgramRelation(List.of(new ProgramRelationRow(
                ProgramOccurrenceId.create(),
                new ProgramResourceValue(itemType, writable.copy())
        ))));
        ProgramRelation strings = new TextReadValueExpression("sfm:text/read", "book").evaluate(context);
        writablePages.set(0, StringTag.valueOf("changed"));
        assertEquals(
                SFMValue.of("first\nsecond"),
                ((ProgramValueReference) strings.rows().get(0).value()).get()
        );

        ItemStack written = new ItemStack(Items.WRITTEN_BOOK);
        ListTag writtenPages = new ListTag();
        writtenPages.add(StringTag.valueOf("{\"text\":\"hello\",\"extra\":[{\"text\":\" world\"}]}"));
        written.getOrCreateTag().put("pages", writtenPages);
        assertEquals("hello world", SFMTextResourceAdapters.read(written));
        context.free();
    }
}
