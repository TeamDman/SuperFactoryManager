package ca.teamdman.sfm.client.registry;

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
import ca.teamdman.sfm.client.render.FacadeBlockColor;
{% when '26.1.2' %}
import ca.teamdman.sfm.client.render.FacadeBlockTintSource;
{% endcase %}
import ca.teamdman.sfm.common.event_bus.SFMSubscribeEvent;
import ca.teamdman.sfm.common.registry.registration.SFMBlocks;
import ca.teamdman.sfm.common.util.SFMDist;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1' %}
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
{% when '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1', '26.1.2' %}
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
{% endcase %}

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
{% when '26.1.2' %}
import java.util.List;

{% endcase %}
public class SFMBlockColors {
    @SFMSubscribeEvent(value = SFMDist.CLIENT)
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
    public static void registerBlockColor(RegisterColorHandlersEvent.Block event) {
        FacadeBlockColor blockColor = new FacadeBlockColor();
        event.register(blockColor, SFMBlocks.CABLE_FACADE.get());
        event.register(blockColor, SFMBlocks.FANCY_CABLE_FACADE.get());
{% when '26.1.2' %}
    public static void registerBlockColor(RegisterColorHandlersEvent.BlockTintSources event) {
        FacadeBlockTintSource blockColor = new FacadeBlockTintSource();
        event.register(List.of(blockColor), SFMBlocks.CABLE_FACADE.get(), SFMBlocks.FANCY_CABLE_FACADE.get());
{% endcase %}
    }
}
