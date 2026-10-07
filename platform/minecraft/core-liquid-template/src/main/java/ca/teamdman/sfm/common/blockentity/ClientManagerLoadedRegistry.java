package ca.teamdman.sfm.common.blockentity;

import net.minecraft.world.level.Level;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.WeakHashMap;

/** Tracks only loaded client manager block entities; it never requests chunk loading. */
public final class ClientManagerLoadedRegistry {
    private static final WeakHashMap<Level, Set<ClientManagerBlockEntity>> BY_LEVEL = new WeakHashMap<>();

    private ClientManagerLoadedRegistry() {}

    public static synchronized void add(Level level, ClientManagerBlockEntity manager) {
        BY_LEVEL.computeIfAbsent(level, ignored -> Collections.newSetFromMap(new IdentityHashMap<>())).add(manager);
    }

    public static synchronized void remove(Level level, ClientManagerBlockEntity manager) {
        Set<ClientManagerBlockEntity> entries = BY_LEVEL.get(level);
        if (entries == null) return;
        entries.remove(manager);
        if (entries.isEmpty()) BY_LEVEL.remove(level);
    }

    public static synchronized Set<ClientManagerBlockEntity> snapshot(Level level) {
        Set<ClientManagerBlockEntity> entries = BY_LEVEL.get(level);
        return entries == null ? Set.of() : Set.copyOf(entries);
    }

    public static synchronized void clear(Level level) {
        BY_LEVEL.remove(level);
    }
}
