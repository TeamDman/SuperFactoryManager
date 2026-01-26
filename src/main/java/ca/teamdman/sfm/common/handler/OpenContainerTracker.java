package ca.teamdman.sfm.common.handler;

import ca.teamdman.sfm.common.containermenu.ManagerContainerMenu;
import ca.teamdman.sfm.common.event_bus.SFMSubscribeEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.stream.Stream;

import static net.minecraftforge.event.entity.player.PlayerContainerEvent.Close;
import static net.minecraftforge.event.entity.player.PlayerContainerEvent.Open;

// TODO: consider replacing with ContainerOpenersCounter, see BarrelBlockEntity
public class OpenContainerTracker {
    private static final Map<BlockPos, Map<ServerPlayer, ManagerContainerMenu>> OPEN_CONTAINERS = new WeakHashMap<>();

    public static Stream<Map.Entry<ServerPlayer, ManagerContainerMenu>> getOpenManagerMenus(BlockPos pos) {
        if (OPEN_CONTAINERS.containsKey(pos)) {
            return OPEN_CONTAINERS.get(pos).entrySet().stream();
        } else {
            return Stream.empty();
        }
    }

    @SFMSubscribeEvent
    public static void onOpenContainer(Open event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer
            && event.getContainer() instanceof ManagerContainerMenu mcm) {
            OPEN_CONTAINERS.computeIfAbsent(mcm.MANAGER_POSITION, k -> new HashMap<>()).put(serverPlayer, mcm);
        }
    }

    @SFMSubscribeEvent
    public static void onCloseContainer(Close event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer
            && event.getContainer() instanceof ManagerContainerMenu mcm) {
            if (OPEN_CONTAINERS.containsKey(mcm.MANAGER_POSITION)) {
                OPEN_CONTAINERS.get(mcm.MANAGER_POSITION).remove(serverPlayer);
                if (OPEN_CONTAINERS.get(mcm.MANAGER_POSITION).isEmpty()) {
                    OPEN_CONTAINERS.remove(mcm.MANAGER_POSITION);
                }
            }
        }
    }
}
