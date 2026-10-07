package ca.teamdman.sfm.client.screen;

import ca.teamdman.sfm.client.action.SFMClientAction;
import ca.teamdman.sfm.client.action.SFMClientActionAvailability;
import ca.teamdman.sfm.client.action.SFMClientActionCommandTree;
import ca.teamdman.sfm.client.action.SFMClientActionContext;
import ca.teamdman.sfm.client.action.SFMClientActionDispatcherCompiler;
import ca.teamdman.sfm.client.action.SFMClientActionRequirement;
import ca.teamdman.sfm.client.action.SFMClientActionSource;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SFMActionChoiceCatalogTests {
    private static final ResourceLocation AVAILABLE = new ResourceLocation("sfm", "available");
    private static final ResourceLocation UNAVAILABLE = new ResourceLocation("sfm", "unavailable");
    private static final ResourceLocation REQUIRED = new ResourceLocation("sfm", "required");

    @Test
    void exactBoundedChoicesAreDeduplicatedAndFilteredByRegisteredAvailability() {
        Map<ResourceLocation, SFMClientAction<?>> actions = new LinkedHashMap<>();
        actions.put(AVAILABLE, new TestAction(true));
        actions.put(UNAVAILABLE, new TestAction(false));
        actions.put(REQUIRED, new RequiredAction());
        SFMClientActionCommandTree tree = SFMClientActionDispatcherCompiler.compileCommandTree(
                actions.entrySet());
        SFMClientActionContext context = SFMClientActionContext.create(new Object(), () -> true);
        SFMActionChoice first = SFMActionChoice.invoke(AVAILABLE, "");

        List<SFMActionChoice> choices = SFMActionChoiceCatalog.available(
                List.of(
                        first,
                        first,
                        SFMActionChoice.invoke(UNAVAILABLE, ""),
                        SFMActionChoice.invoke(REQUIRED, ""),
                        SFMActionChoice.invoke(new ResourceLocation("sfm", "missing"), "")),
                actions::get,
                tree,
                context);

        assertEquals(List.of(first), choices);
    }

    @Test
    void aChoiceCannotClaimOneActionWhileInvokingAnother() {
        assertThrows(IllegalArgumentException.class, () -> new SFMActionChoice(
                AVAILABLE,
                "sfm action invoke " + UNAVAILABLE));
    }

    private record TestAction(boolean available) implements SFMClientAction<Object> {
        @Override
        public Component title() {
            return Component.literal("test");
        }

        @Override
        public Component description() {
            return Component.literal("test");
        }

        @Override
        public SFMClientActionRequirement<Object> requirement() {
            return ignored -> available
                    ? SFMClientActionAvailability.available(new Object())
                    : SFMClientActionAvailability.unavailable(Component.literal("unavailable"));
        }

        @Override
        public int execute(Object target, CommandContext<SFMClientActionSource> context) {
            return 1;
        }
    }

    private static final class RequiredAction implements SFMClientAction<Object> {
        @Override
        public Component title() {
            return Component.literal("required");
        }

        @Override
        public Component description() {
            return Component.literal("required");
        }

        @Override
        public SFMClientActionRequirement<Object> requirement() {
            return ignored -> SFMClientActionAvailability.available(new Object());
        }

        @Override
        public void configureCommandNode(LiteralArgumentBuilder<SFMClientActionSource> node) {
            node.then(RequiredArgumentBuilder.<SFMClientActionSource, String>argument(
                    "value", StringArgumentType.word()).executes(this::invoke));
        }

        @Override
        public int execute(Object target, CommandContext<SFMClientActionSource> context) {
            return 1;
        }
    }
}
