package ca.teamdman.sfm.client.screen.workspace;

import net.minecraft.client.gui.screens.Screen;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.HashSet;

/** Allocates the screen host without bootstrapping Minecraft registries. */
public final class SFMHeadlessWorkspaceTestSupport {
    private SFMHeadlessWorkspaceTestSupport() {
    }

    public static SFMScreenMultiplexer create(SFMWorkspaceLayout layout) {
        try {
            Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            Unsafe unsafe = (Unsafe) unsafeField.get(null);
            SFMScreenMultiplexer workspace =
                    (SFMScreenMultiplexer) unsafe.allocateInstance(SFMScreenMultiplexer.class);
            setField(SFMScreenMultiplexer.class, workspace, "layout", layout);
            setField(SFMScreenMultiplexer.class, workspace, "openedPanels", new HashSet<>());
            setField(SFMScreenMultiplexer.class, workspace, "openedPanelInstances", new HashMap<>());
            setField(SFMScreenMultiplexer.class, workspace, "reopenRecipes", new SFMPanelReopenCatalog());
            setField(
                    SFMScreenMultiplexer.class,
                    workspace,
                    "panelBounds",
                    layout.bounds(new SFMScreenPanelBounds(0, 0, 320, 180), 2)
            );
            setIntField(Screen.class, workspace, "width", 320);
            setIntField(Screen.class, workspace, "height", 180);
            return workspace;
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("Could not allocate a headless SFM workspace", failure);
        }
    }

    private static void setField(Class<?> owner, Object target, String name, Object value)
            throws ReflectiveOperationException {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static void setIntField(Class<?> owner, Object target, String name, int value)
            throws ReflectiveOperationException {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        field.setInt(target, value);
    }
}
