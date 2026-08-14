package ca.teamdman.sfm.client.screen.workspace;

import ca.teamdman.sfm.client.action.SFMClientActionContext;
import ca.teamdman.sfm.client.action.SFMClientActionSource;
import ca.teamdman.sfm.client.explorer.SFMPathExpression;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class SFMExplorerScreenTypeTests {
    private static final ResourceLocation SCENE_ID = new ResourceLocation("sfm", "explorer");

    @Test
    void omittedLocationUsesTheDocumentedItemRegistryDefault() throws Exception {
        SFMExplorerScreenType.Recipe recipe = parseRecipe("");

        SFMPathExpression.Literal literal = assertInstanceOf(
                SFMPathExpression.Literal.class,
                recipe.initialLocation()
        );
        assertEquals("registry://minecraft/item/", literal.canonical());
    }

    @Test
    void literalLocationIsRetainedAsTheImmutableReopenRecipe() throws Exception {
        SFMExplorerScreenType.Recipe recipe = parseRecipe(" file:///D:/Repos/Minecraft/SFM");

        assertInstanceOf(SFMPathExpression.Literal.class, recipe.initialLocation());
        assertEquals("file:///D:/Repos/Minecraft/SFM", recipe.initialLocation().canonical());
    }

    @Test
    void unionLocationConsumesNestedCommasAndParenthesesAsOneArgument() throws Exception {
        String canonical = "union(file:///D:/Repos/Minecraft/SFM,registry://minecraft/item/)";
        SFMExplorerScreenType.Recipe recipe = parseRecipe(" " + canonical);

        SFMPathExpression.Union union = assertInstanceOf(
                SFMPathExpression.Union.class,
                recipe.initialLocation()
        );
        assertEquals(2, union.expressions().size());
        assertEquals(canonical, union.canonical());
    }

    @Test
    void membersLocationRetainsTheSelectionExpressionRatherThanMaterializingIt() throws Exception {
        String canonical = "members(union(id(selection-1),name(primary)))";
        SFMExplorerScreenType.Recipe recipe = parseRecipe(" " + canonical);

        assertInstanceOf(SFMPathExpression.Members.class, recipe.initialLocation());
        assertEquals(canonical, recipe.initialLocation().canonical());
    }

    private static SFMExplorerScreenType.Recipe parseRecipe(String arguments) throws Exception {
        AtomicReference<SFMPanelReopenRecipe> captured = new AtomicReference<>();
        CommandDispatcher<SFMClientActionSource> dispatcher = new CommandDispatcher<>();
        dispatcher.register(new SFMExplorerScreenType().createCommandNode(
                SCENE_ID,
                (context, recipe) -> {
                    captured.set(recipe);
                    return 1;
                }
        ));
        SFMClientActionSource source = new SFMClientActionSource(SFMClientActionContext.create(null, () -> true));

        assertEquals(1, dispatcher.execute(SCENE_ID + arguments, source));
        return assertInstanceOf(SFMExplorerScreenType.Recipe.class, captured.get());
    }
}
