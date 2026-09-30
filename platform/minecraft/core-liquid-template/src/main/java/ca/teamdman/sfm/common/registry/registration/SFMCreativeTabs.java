package ca.teamdman.sfm.common.registry.registration;

{% case minecraft_version %}
{% when '1.19.2', '1.21', '1.21.1', '26.1.2' %}
import ca.teamdman.sfm.SFM;
{% when '1.19.4' %}
import ca.teamdman.sfm.common.event_bus.SFMSubscribeEvent;
{% when '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.event_bus.SFMSubscribeEvent;
{% endcase %}
import ca.teamdman.sfm.common.localization.LocalizationEntry;
import ca.teamdman.sfm.common.localization.SFMLocalizationDatagen;
{% case minecraft_version %}
{% when '1.19.2' %}
{% when '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1', '26.1.2' %}
import ca.teamdman.sfm.common.registry.SFMRegistryObject;
{% endcase %}
import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
{% case minecraft_version %}
{% when '1.19.2' %}
import net.minecraft.network.chat.Component;
{% when '1.19.4' %}
import ca.teamdman.sfm.common.util.SFMResourceLocation;
{% when '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1', '26.1.2' %}
import net.minecraft.core.registries.Registries;
{% endcase %}
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
{% case minecraft_version %}
{% when '1.19.2' %}
{% when '1.19.4' %}
import net.minecraftforge.event.CreativeModeTabEvent;
{% when '1.20', '1.20.1' %}
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;
{% when '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1', '26.1.2' %}
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;
{% endcase %}

@MCVersionDependentBehaviour
public class SFMCreativeTabs {
{% case minecraft_version %}
{% when '1.19.2' %}
    public static final CreativeModeTab MAIN = new SFMCreativeModeTab();

{% when '1.19.4' %}
    @SuppressWarnings("NotNullFieldNotInitialized")
{% when '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1', '26.1.2' %}

{% endcase %}
    @SFMLocalizationDatagen
    public static final LocalizationEntry CREATIVE_TAB_NAME = new LocalizationEntry(
            "item_group.sfm",
            "Super Factory Manager"
    );

{% case minecraft_version %}
{% when '1.19.2' %}
    public static class SFMCreativeModeTab extends CreativeModeTab {
        public SFMCreativeModeTab() {
{% when '1.19.4' %}
    public static CreativeModeTab MAIN;
{% when '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1', '26.1.2' %}
    private static final DeferredRegister<CreativeModeTab> CREATIVE_TABS = DeferredRegister.create(
            Registries.CREATIVE_MODE_TAB,
            SFM.MOD_ID
    );
{% endcase %}

{% case minecraft_version %}
{% when '1.19.2' %}
            super(SFM.MOD_ID);
        }
{% when '1.19.4' %}
    @SFMSubscribeEvent
    public static void onRegister(CreativeModeTabEvent.Register event) {
{% when '1.20', '1.20.1' %}
    @SuppressWarnings("unused")
    public static final RegistryObject<CreativeModeTab> MAIN = CREATIVE_TABS.register(
            "main",
            () -> CreativeModeTab
                    .builder()
                    .title(CREATIVE_TAB_NAME.getComponent())
                    .icon(() -> new ItemStack(SFMBlocks.MANAGER.get()))
                    .displayItems(SFMCreativeTabs::populateMainCreativeTab)
                    .build()
    );
{% when '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1', '26.1.2' %}
    @SuppressWarnings("unused")
    public static final Supplier<CreativeModeTab> MAIN = CREATIVE_TABS.register(
            "main",
            () -> CreativeModeTab
                    .builder()
                    .title(CREATIVE_TAB_NAME.getComponent())
                    .icon(() -> new ItemStack(SFMBlocks.MANAGER.get()))
                    .displayItems(SFMCreativeTabs::populateMainCreativeTab)
                    .build()
    );
{% endcase %}

{% case minecraft_version %}
{% when '1.19.2' %}
        @Override
        public ItemStack makeIcon() {

            return new ItemStack(SFMBlocks.MANAGER.get());
        }

        @Override
        public Component getDisplayName() {

            return CREATIVE_TAB_NAME.getComponent();
        }

{% when '1.19.4' %}
        MAIN = event.registerCreativeModeTab(
                SFMResourceLocation.fromSFMPath("main"),
                builder ->
                        builder.title(CREATIVE_TAB_NAME.getComponent())
                                .icon(() -> new ItemStack(SFMBlocks.MANAGER.get()))
                                .displayItems((params, output) -> output.acceptAll(SFMItems.REGISTERER.getOurEntries()
                                                                                           .stream()
                                                                                           .map(SFMRegistryObject::get)
                                                                                           .map(ItemStack::new)
                                                                                           .toList()))
        );
{% when '1.20', '1.20.1' %}
    public static void register(IEventBus bus) {

        CREATIVE_TABS.register(bus);
    }

    public static void populateMainCreativeTab(
            @SuppressWarnings("unused")
            CreativeModeTab.ItemDisplayParameters params,
            CreativeModeTab.Output output
    ) {

        output.acceptAll(
                SFMItems.REGISTERER
                        .getOurEntries()
                        .stream()
                        .map(SFMRegistryObject::get)
                        .map(ItemStack::new)
                        .toList()
        );
{% when '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1', '26.1.2' %}
    public static void register(IEventBus bus) {

        CREATIVE_TABS.register(bus);
    }


    public static void populateMainCreativeTab(
            @SuppressWarnings("unused")
            CreativeModeTab.ItemDisplayParameters params,
            CreativeModeTab.Output output
    ) {

        output.acceptAll(
                SFMItems.REGISTERER
                        .getOurEntries()
                        .stream()
                        .map(SFMRegistryObject::get)
                        .map(ItemStack::new)
                        .toList()
        );
{% endcase %}
    }

}
