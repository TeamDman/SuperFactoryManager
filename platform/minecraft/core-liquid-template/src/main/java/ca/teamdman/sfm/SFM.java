package ca.teamdman.sfm;

{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4" %}
{% if features.canvas_text_editor or features.client_actions or features.client_manager_gui or features.client_overlay_scenes or features.client_program_consent or features.client_program_reads or features.command_history or features.context_actions or features.developer_tools or features.document_history or features.explorer_compaction or features.explorer_search or features.file_explorer or features.icon_rules or features.java_symbols or features.keyboard_profiles or features.manager_editor_actions or features.multiplayer_packets or features.packet_actions or features.registry_explorer or features.release_review or features.review_sessions or features.route_comparison or features.spatial_coverage or features.tooltip_mode_override or features.touch_display_terminal_mount or features.trajectory_panels or features.workspace_counterfactuals or features.workspace_lifecycle or features.workspace_panels %}
{% else %}
import ca.teamdman.sfm.client.registry.SFMMenuScreens;
{% endif %}
{% when "1.21", "1.21.1", "26.1.2" %}
{% endcase %}
{% if features.canvas_text_editor or features.client_actions or features.client_manager_gui or features.client_overlay_scenes or features.client_program_consent or features.client_program_reads or features.command_history or features.context_actions or features.developer_tools or features.document_history or features.explorer_compaction or features.explorer_search or features.file_explorer or features.icon_rules or features.java_symbols or features.keyboard_profiles or features.manager_editor_actions or features.multiplayer_packets or features.packet_actions or features.registry_explorer or features.release_review or features.review_sessions or features.route_comparison or features.spatial_coverage or features.tooltip_mode_override or features.touch_display_terminal_mount or features.trajectory_panels or features.workspace_counterfactuals or features.workspace_lifecycle or features.workspace_panels %}
{% else %}
import ca.teamdman.sfm.client.registry.SFMTextEditorActions;
{% endif %}
{% if features.canvas_text_editor or features.client_actions or features.client_manager_gui or features.client_overlay_scenes or features.client_program_consent or features.client_program_reads or features.command_history or features.context_actions or features.developer_tools or features.document_history or features.explorer_compaction or features.explorer_search or features.file_explorer or features.icon_rules or features.java_symbols or features.keyboard_profiles or features.manager_editor_actions or features.multiplayer_packets or features.packet_actions or features.registry_explorer or features.release_review or features.review_sessions or features.route_comparison or features.spatial_coverage or features.tooltip_mode_override or features.touch_display_terminal_mount or features.trajectory_panels or features.workspace_counterfactuals or features.workspace_lifecycle or features.workspace_panels %}
{% else %}
import ca.teamdman.sfm.client.registry.SFMTextEditors;
{% endif %}
import ca.teamdman.sfm.common.config.SFMConfig;
import ca.teamdman.sfm.common.event_bus.SFMAutomaticEventSubscriber;
import ca.teamdman.sfm.common.event_bus.SFMEventBus;
import ca.teamdman.sfm.common.localization.LocalizationEntry;
import ca.teamdman.sfm.common.localization.SFMLocalizationDatagen;
import ca.teamdman.sfm.common.registry.registration.*;
{% if features.canvas_text_editor or features.client_actions or features.client_manager_gui or features.client_overlay_scenes or features.client_program_consent or features.client_program_reads or features.command_history or features.context_actions or features.developer_tools or features.document_history or features.explorer_compaction or features.explorer_search or features.file_explorer or features.icon_rules or features.java_symbols or features.keyboard_profiles or features.manager_editor_actions or features.multiplayer_packets or features.packet_actions or features.registry_explorer or features.release_review or features.review_sessions or features.route_comparison or features.spatial_coverage or features.tooltip_mode_override or features.touch_display_terminal_mount or features.trajectory_panels or features.workspace_counterfactuals or features.workspace_lifecycle or features.workspace_panels %}
{% case minecraft_version %}
{% when "1.19.2" %}
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
{% endcase %}
{% endif %}
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1" %}
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
{% if features.canvas_text_editor or features.client_actions or features.client_manager_gui or features.client_overlay_scenes or features.client_program_consent or features.client_program_reads or features.command_history or features.context_actions or features.developer_tools or features.document_history or features.explorer_compaction or features.explorer_search or features.file_explorer or features.icon_rules or features.java_symbols or features.keyboard_profiles or features.manager_editor_actions or features.multiplayer_packets or features.packet_actions or features.registry_explorer or features.release_review or features.review_sessions or features.route_comparison or features.spatial_coverage or features.tooltip_mode_override or features.touch_display_terminal_mount or features.trajectory_panels or features.workspace_counterfactuals or features.workspace_lifecycle or features.workspace_panels %}
{% else %}
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
{% endif %}
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
{% when "1.20.2", "1.20.3" %}
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.common.Mod;
{% if features.canvas_text_editor or features.client_actions or features.client_manager_gui or features.client_overlay_scenes or features.client_program_consent or features.client_program_reads or features.command_history or features.context_actions or features.developer_tools or features.document_history or features.explorer_compaction or features.explorer_search or features.file_explorer or features.icon_rules or features.java_symbols or features.keyboard_profiles or features.manager_editor_actions or features.multiplayer_packets or features.packet_actions or features.registry_explorer or features.release_review or features.review_sessions or features.route_comparison or features.spatial_coverage or features.tooltip_mode_override or features.touch_display_terminal_mount or features.trajectory_panels or features.workspace_counterfactuals or features.workspace_lifecycle or features.workspace_panels %}
{% else %}
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
{% endif %}
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.javafmlmod.FMLJavaModLoadingContext;
{% when "1.20.4" %}
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.common.Mod;
{% if features.canvas_text_editor or features.client_actions or features.client_manager_gui or features.client_overlay_scenes or features.client_program_consent or features.client_program_reads or features.command_history or features.context_actions or features.developer_tools or features.document_history or features.explorer_compaction or features.explorer_search or features.file_explorer or features.icon_rules or features.java_symbols or features.keyboard_profiles or features.manager_editor_actions or features.multiplayer_packets or features.packet_actions or features.registry_explorer or features.release_review or features.review_sessions or features.route_comparison or features.spatial_coverage or features.tooltip_mode_override or features.touch_display_terminal_mount or features.trajectory_panels or features.workspace_counterfactuals or features.workspace_lifecycle or features.workspace_panels %}
{% else %}
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
{% endif %}
{% when "1.21", "1.21.1", "26.1.2" %}
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.common.Mod;
{% endcase %}
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/// Welcome to SFM's source code!
/// I hope you enjoy your visit :D
@Mod(SFM.MOD_ID)
public class SFM {
    public static final String MOD_ID = "sfm";

    public static final Logger LOGGER = LogManager.getLogger(SFM.MOD_ID);

    public static final String ISSUE_TRACKER_URL = "https://github.com/TeamDman/SuperFactoryManager/issues";

    @SFMLocalizationDatagen
    public static final LocalizationEntry MOD_NAME = new LocalizationEntry(
            "mod.name",
            "Super Factory Manager"
    );

{% if features.computercraft %}
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.4", "1.21.1" %}
    @ca.teamdman.sfm.common.util.MCVersionDependentBehaviour
    private static void registerComputerCraftTurtleUpgrades() {

        if (ca.teamdman.sfm.common.compat.SFMModCompat.isComputerCraftLoaded()) {
            ca.teamdman.sfm.common.compat.computercraft.SFMComputerCraftTurtleUpgrades.register(SFMEventBus.MOD_BUS);
        }
    }

{% else %}
    @ca.teamdman.sfm.common.util.MCVersionDependentBehaviour
    private static void registerComputerCraftTurtleUpgrades() {

        // CC:Tweaked sources are retained but excluded because this branch has no compatible runtime.
    }

{% endcase %}
{% endif %}
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3" %}
    public SFM() {

        var bus = FMLJavaModLoadingContext
                .get()
                .getModEventBus();
{% when "1.20.4", "1.21", "1.21.1", "26.1.2" %}
    public SFM(IEventBus bus) {

{% endcase %}
        SFMEventBus.MOD_BUS = bus;

        SFMBlocks.register(bus);

        SFMItems.register(bus);

{% if features.computercraft %}
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20.2", "1.20.3", "1.21", "26.1.2" %}
        registerComputerCraftTurtleUpgrades();

{% endcase %}
{% endif %}
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4" %}
        SFMCreativeTabs.register(bus);
{% if features.computercraft %}
{% case minecraft_version %}
{% when "1.20", "1.20.1", "1.20.4" %}

        registerComputerCraftTurtleUpgrades();
{% endcase %}
{% endif %}

{% when "1.21", "1.21.1", "26.1.2" %}
        SFMDataComponents.register(bus);

        SFMCreativeTabs.register(bus);
{% if features.computercraft %}
{% case minecraft_version %}
{% when "1.20", "1.20.1", "1.20.4" %}

        registerComputerCraftTurtleUpgrades();
{% endcase %}
{% endif %}

{% endcase %}
{% if features.computercraft %}
{% case minecraft_version %}
{% when "1.21.1" %}
        registerComputerCraftTurtleUpgrades();

{% endcase %}
{% endif %}
        SFMResourceTypes.register(bus);

        SFMProgramLinters.register(bus);

        SFMBlockEntities.register(bus);

        SFMGlobalBlockCapabilityProviders.register(bus);

{% if features.canvas_text_editor or features.client_actions or features.client_manager_gui or features.client_overlay_scenes or features.client_program_consent or features.client_program_reads or features.command_history or features.context_actions or features.developer_tools or features.document_history or features.explorer_compaction or features.explorer_search or features.file_explorer or features.icon_rules or features.java_symbols or features.keyboard_profiles or features.manager_editor_actions or features.multiplayer_packets or features.packet_actions or features.registry_explorer or features.release_review or features.review_sessions or features.route_comparison or features.spatial_coverage or features.tooltip_mode_override or features.touch_display_terminal_mount or features.trajectory_panels or features.workspace_counterfactuals or features.workspace_lifecycle or features.workspace_panels %}
{% case minecraft_version %}
{% when "1.19.2" %}
        DistExecutor.safeRunWhenOn(
                Dist.CLIENT,
                () -> ca.teamdman.sfm.client.SFMClientRegistrations::register
        );
{% else %}
        if (ca.teamdman.sfm.common.util.SFMEnvironmentUtils.isClient()) {
            ca.teamdman.sfm.client.SFMClientRegistrations.register(bus);
        }
{% endcase %}
{% else %}
        SFMTextEditors.register(bus);

        SFMTextEditorActions.register(bus);
{% endif %}

        SFMMenus.register(bus);

        SFMRecipeTypes.register(bus);

        SFMRecipeSerializers.register(bus);

        SFMConfig.register(ModLoadingContext.get());

{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3" %}
{% if features.canvas_text_editor or features.client_actions or features.client_manager_gui or features.client_overlay_scenes or features.client_program_consent or features.client_program_reads or features.command_history or features.context_actions or features.developer_tools or features.document_history or features.explorer_compaction or features.explorer_search or features.file_explorer or features.icon_rules or features.java_symbols or features.keyboard_profiles or features.manager_editor_actions or features.multiplayer_packets or features.packet_actions or features.registry_explorer or features.release_review or features.review_sessions or features.route_comparison or features.spatial_coverage or features.tooltip_mode_override or features.touch_display_terminal_mount or features.trajectory_panels or features.workspace_counterfactuals or features.workspace_lifecycle or features.workspace_panels %}
{% else %}
        bus.addListener((FMLClientSetupEvent e) -> SFMMenuScreens.register());

{% endif %}
        bus.addListener((FMLCommonSetupEvent e) -> SFMPackets.register());

{% when "1.20.4" %}
{% if features.canvas_text_editor or features.client_actions or features.client_manager_gui or features.client_overlay_scenes or features.client_program_consent or features.client_program_reads or features.command_history or features.context_actions or features.developer_tools or features.document_history or features.explorer_compaction or features.explorer_search or features.file_explorer or features.icon_rules or features.java_symbols or features.keyboard_profiles or features.manager_editor_actions or features.multiplayer_packets or features.packet_actions or features.registry_explorer or features.release_review or features.review_sessions or features.route_comparison or features.spatial_coverage or features.tooltip_mode_override or features.touch_display_terminal_mount or features.trajectory_panels or features.workspace_counterfactuals or features.workspace_lifecycle or features.workspace_panels %}
{% else %}
        bus.addListener((FMLClientSetupEvent e) -> SFMMenuScreens.register());

{% endif %}
{% when "1.21", "1.21.1", "26.1.2" %}
{% endcase %}
        SFMAutomaticEventSubscriber.attachEventBusSubscribers();
    }

}
