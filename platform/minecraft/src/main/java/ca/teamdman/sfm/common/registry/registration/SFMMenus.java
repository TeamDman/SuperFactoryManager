package ca.teamdman.sfm.common.registry.registration;


import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.client.ClientRayCastHelpers;
import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.blockentity.TestBarrelTankBlockEntity;
import ca.teamdman.sfm.common.containermenu.ManagerContainerMenu;
import ca.teamdman.sfm.common.containermenu.TestBarrelTankContainerMenu;
import ca.teamdman.sfm.common.registry.SFMDeferredRegister;
import ca.teamdman.sfm.common.registry.SFMDeferredRegisterBuilder;
import ca.teamdman.sfm.common.registry.SFMRegistryObject;
import ca.teamdman.sfm.common.registry.SFMWellKnownRegistries;
import ca.teamdman.sfm.common.util.SFMEnvironmentUtils;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.network.IContainerFactory;

public class SFMMenus {
    private static final SFMDeferredRegister<MenuType<?>> MENU_TYPES =
            new SFMDeferredRegisterBuilder<MenuType<?>>()
                    .namespace(SFM.MOD_ID)
                    .registry(SFMWellKnownRegistries.MENU_TYPES.registryKey())
                    .build();

    public static final SFMRegistryObject<MenuType<?>, MenuType<ManagerContainerMenu>> MANAGER = MENU_TYPES.register(
            "manager",
            () -> IMenuTypeExtension.create(
                    new IContainerFactory<>() {
                        @Override
                        public ManagerContainerMenu create(
                                int windowId,
                                Inventory inv,
                                RegistryFriendlyByteBuf data
                        ) {

                            return new ManagerContainerMenu(
                                    windowId,
                                    inv,
                                    data
                            );
                        }

                        @Override
                        public ManagerContainerMenu create(
                                int windowId,
                                Inventory inv
                        ) {

                            if (SFMEnvironmentUtils.isClient()) {
                                BlockEntity be = ClientRayCastHelpers.getLookBlockEntity();
                                if (!(be instanceof ManagerBlockEntity mbe)) {
                                    return IContainerFactory.super.create(windowId, inv);
                                }
                                return new ManagerContainerMenu(windowId, inv, mbe);
                            } else {
                                return IContainerFactory.super.create(
                                        windowId,
                                        inv
                                );
                            }
                        }
                    })
    );

    public static final SFMRegistryObject<MenuType<?>, MenuType<TestBarrelTankContainerMenu>> TEST_BARREL_TANK = MENU_TYPES.register(
            "test_barrel_tank",
            () -> IMenuTypeExtension.create(
                    new IContainerFactory<>() {
                        @Override
                        public TestBarrelTankContainerMenu create(
                                int windowId,
                                Inventory inv,
                                RegistryFriendlyByteBuf data
                        ) {

                            return new TestBarrelTankContainerMenu(
                                    windowId,
                                    inv,
                                    data
                            );
                        }

                        @Override
                        public TestBarrelTankContainerMenu create(
                                int windowId,
                                Inventory inv
                        ) {

                            if (SFMEnvironmentUtils.isClient()) {
                                BlockEntity be = ClientRayCastHelpers.getLookBlockEntity();
                                if (!(be instanceof TestBarrelTankBlockEntity blockEntity)) {
                                    return IContainerFactory.super.create(windowId, inv);
                                }
                                return new TestBarrelTankContainerMenu(windowId, inv, blockEntity);
                            } else {
                                return IContainerFactory.super.create(
                                        windowId,
                                        inv
                                );
                            }
                        }
                    })
    );

    public static void register(IEventBus bus) {

        MENU_TYPES.register(bus);
    }


}
