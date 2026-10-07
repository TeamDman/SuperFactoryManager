package ca.teamdman.sfm.common.registry.registration;


import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.client.ClientRayCastHelpers;
import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
{% if features.client_manager_gui %}
import ca.teamdman.sfm.common.blockentity.ClientManagerBlockEntity;
{% endif %}
import ca.teamdman.sfm.common.blockentity.TestBarrelTankBlockEntity;
{% if features.client_manager_gui %}
import ca.teamdman.sfm.common.containermenu.ClientManagerContainerMenu;
{% endif %}
import ca.teamdman.sfm.common.containermenu.ManagerContainerMenu;
import ca.teamdman.sfm.common.containermenu.TestBarrelTankContainerMenu;
import ca.teamdman.sfm.common.registry.SFMDeferredRegister;
import ca.teamdman.sfm.common.registry.SFMDeferredRegisterBuilder;
import ca.teamdman.sfm.common.registry.SFMRegistryObject;
import ca.teamdman.sfm.common.registry.SFMWellKnownRegistries;
import ca.teamdman.sfm.common.util.SFMEnvironmentUtils;
{% case minecraft_version %}
{% when "1.21", "1.21.1", "26.1.2" %}
import net.minecraft.network.RegistryFriendlyByteBuf;
{% else %}
import net.minecraft.network.FriendlyByteBuf;
{% endcase %}
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.entity.BlockEntity;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1" %}
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.network.IContainerFactory;
{% else %}
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.network.IContainerFactory;
{% endcase %}

public class SFMMenus {
    private static final SFMDeferredRegister<MenuType<?>> MENU_TYPES =
            new SFMDeferredRegisterBuilder<MenuType<?>>()
                    .namespace(SFM.MOD_ID)
                    .registry(SFMWellKnownRegistries.MENU_TYPES.registryKey())
                    .build();

    public static final SFMRegistryObject<MenuType<?>, MenuType<ManagerContainerMenu>> MANAGER = MENU_TYPES.register(
            "manager",
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1" %}
            () -> IForgeMenuType.create(
{% else %}
            () -> IMenuTypeExtension.create(
{% endcase %}
                    new IContainerFactory<>() {
                        @Override
                        public ManagerContainerMenu create(
                                int windowId,
                                Inventory inv,
{% case minecraft_version %}
{% when "1.21", "1.21.1", "26.1.2" %}
                                RegistryFriendlyByteBuf data
{% else %}
                                FriendlyByteBuf data
{% endcase %}
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

{% if features.client_manager_gui %}
    public static final SFMRegistryObject<MenuType<?>, MenuType<ClientManagerContainerMenu>> CLIENT_MANAGER = MENU_TYPES.register(
            "client_manager",
            () -> IForgeMenuType.create(
                    new IContainerFactory<>() {
                        @Override
                        public ClientManagerContainerMenu create(
                                int windowId,
                                Inventory inv,
                                FriendlyByteBuf data
                        ) {
                            return new ClientManagerContainerMenu(windowId, inv, data);
                        }

                        @Override
                        public ClientManagerContainerMenu create(int windowId, Inventory inv) {
                            if (SFMEnvironmentUtils.isClient()) {
                                BlockEntity be = ClientRayCastHelpers.getLookBlockEntity();
                                if (be instanceof ClientManagerBlockEntity manager) {
                                    return new ClientManagerContainerMenu(windowId, inv, manager);
                                }
                            }
                            return IContainerFactory.super.create(windowId, inv);
                        }
                    }
            )
    );

{% endif %}
    public static final SFMRegistryObject<MenuType<?>, MenuType<TestBarrelTankContainerMenu>> TEST_BARREL_TANK = MENU_TYPES.register(
            "test_barrel_tank",
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1" %}
            () -> IForgeMenuType.create(
{% else %}
            () -> IMenuTypeExtension.create(
{% endcase %}
                    new IContainerFactory<>() {
                        @Override
                        public TestBarrelTankContainerMenu create(
                                int windowId,
                                Inventory inv,
{% case minecraft_version %}
{% when "1.21", "1.21.1", "26.1.2" %}
                                RegistryFriendlyByteBuf data
{% else %}
                                FriendlyByteBuf data
{% endcase %}
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
