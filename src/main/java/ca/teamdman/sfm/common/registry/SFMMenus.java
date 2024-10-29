package ca.teamdman.sfm.common.registry;


import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.client.ClientStuff;
import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.blockentity.ProxyBlockEntity;
import ca.teamdman.sfm.common.containermenu.ManagerContainerMenu;
import ca.teamdman.sfm.common.containermenu.ProxyContainerMenu;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.network.IContainerFactory;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class SFMMenus {
    private static final DeferredRegister<MenuType<?>> MENU_TYPES = DeferredRegister.create(
            BuiltInRegistries.MENU,
            SFM.MOD_ID
    );
    public static final Supplier<MenuType<ManagerContainerMenu>> MANAGER_MENU = MENU_TYPES.register(
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
                            if (FMLEnvironment.dist.isClient()) {
                                BlockEntity be = ClientStuff.getLookBlockEntity();
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
    public static final Supplier<MenuType<ProxyContainerMenu>> PROXY_MENU = MENU_TYPES.register(
            "proxy",
            () -> IMenuTypeExtension.create(
                    new IContainerFactory<>() {
                        @Override
                        public ProxyContainerMenu create(
                                int windowId,
                                Inventory inv,
                                RegistryFriendlyByteBuf data
                        ) {
                            return new ProxyContainerMenu(
                                    windowId,
                                    inv,
                                    data
                            );
                        }

                        @Override
                        public ProxyContainerMenu create(
                                int windowId,
                                Inventory inv
                        ) {
                            if (FMLEnvironment.dist.isClient()) {
                                BlockEntity be = ClientStuff.getLookBlockEntity();
                                if (!(be instanceof ProxyBlockEntity pbe)) {
                                    return IContainerFactory.super.create(windowId, inv);
                                }
                                return new ProxyContainerMenu(windowId, inv, pbe);
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
