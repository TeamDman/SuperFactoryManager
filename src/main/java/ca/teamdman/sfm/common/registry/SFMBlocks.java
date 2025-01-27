package ca.teamdman.sfm.common.registry;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.block.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;


public class SFMBlocks {
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(BuiltInRegistries.BLOCK, SFM.MOD_ID);
    public static final Supplier<Block> MANAGER_BLOCK = BLOCKS.register("manager", ManagerBlock::new);
    public static final Supplier<Block> PRINTING_PRESS_BLOCK = BLOCKS.register(
            "printing_press",
            PrintingPressBlock::new
    );
    public static final Supplier<Block> WATER_TANK_BLOCK = BLOCKS.register("water_tank", WaterTankBlock::new);
    public static final Supplier<Block> CABLE_BLOCK = BLOCKS.register("cable", CableBlock::new);
    public static final Supplier<Block> BATTERY_BLOCK = BLOCKS.register("battery", BatteryBlock::new);
    public static final Supplier<Block> TEST_BARREL_BLOCK = BLOCKS.register("test_barrel", TestBarrelBlock::new);
    public static final Supplier<Block> PROXY_BLOCK = BLOCKS.register("proxy", ProxyBlock::new);

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
    }

}
