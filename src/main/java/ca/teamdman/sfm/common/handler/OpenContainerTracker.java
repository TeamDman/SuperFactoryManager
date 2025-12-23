package ca.teamdman.sfm.common.handler;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.containermenu.ManagerContainerMenu;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.stream.Stream;

import static net.minecraftforge.event.entity.player.PlayerContainerEvent.Close;
import static net.minecraftforge.event.entity.player.PlayerContainerEvent.Open;

// TODO: consider replacing with ContainerOpenersCounter, see BarrelBlockEntity
@Mod.EventBusSubscriber(modid = SFM.MOD_ID)
public class OpenContainerTracker {
    private static final Map<BlockPos, Map<EntityPlayerMP, ManagerContainerMenu>> OPEN_CONTAINERS = new WeakHashMap<>();

    public static Stream<Map.Entry<EntityPlayerMP, ManagerContainerMenu>> getOpenManagerMenus(BlockPos pos) {
        if (OPEN_CONTAINERS.containsKey(pos)) {
            return OPEN_CONTAINERS.get(pos).entrySet().stream();
        } else {
            return Stream.empty();
        }
    }

    @SubscribeEvent
    public static void onOpenContainer(Open event) {
        if (event.getEntity() instanceof EntityPlayerMP serverPlayer
            && event.getContainer() instanceof ManagerContainerMenu mcm) {
            OPEN_CONTAINERS.computeIfAbsent(mcm.MANAGER_POSITION, k -> new HashMap<>()).put(serverPlayer, mcm);
        }
    }

    @SubscribeEvent
    public static void onCloseContainer(Close event) {
        if (event.getEntity() instanceof EntityPlayerMP serverPlayer
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
