package ca.teamdman.sfm.common.event_bus;

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1' %}
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;
{% when '1.20.2', '1.20.3', '1.20.4' %}
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod.EventBusSubscriber.Bus;
import net.neoforged.neoforge.common.NeoForge;
{% when '1.21' %}
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.EventBusSubscriber.Bus;
import net.neoforged.neoforge.common.NeoForge;
{% when '1.21.1' %}
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
{% when '26.1.2' %}
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
{% endcase %}
import org.jetbrains.annotations.UnknownNullability;

/// Used to reduce {@link ca.teamdman.sfm.common.util.MCVersionDependentBehaviour}.
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21' %}
{% when '1.21.1', '26.1.2' %}
@SuppressWarnings("removal")
{% endcase %}
public class SFMEventBus {
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1' %}
    public static final IEventBus GAME_BUS = MinecraftForge.EVENT_BUS;
{% when '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1', '26.1.2' %}
    public static final IEventBus GAME_BUS = NeoForge.EVENT_BUS;
{% endcase %}

    public static @UnknownNullability IEventBus MOD_BUS = null;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}

    public static IEventBus getEventBus(Bus busType) {

        if (busType == EventBusType.MOD) {
            return MOD_BUS;
        } else if (busType == EventBusType.GAME) {
            return GAME_BUS;
        } else {
            throw new IllegalArgumentException("Invalid busType: " + busType);
        }
    }

    public static class EventBusType {

        public static final Bus MOD = Bus.MOD;

        public static final Bus GAME = Bus.FORGE;

    }

{% when '1.21' %}

    public static IEventBus getEventBus(Bus busType) {

        if (busType == EventBusType.MOD) {
            return MOD_BUS;
        } else if (busType == EventBusType.GAME) {
            return GAME_BUS;
        } else {
            throw new IllegalArgumentException("Invalid busType: " + busType);
        }
    }

    public static class EventBusType {

        public static final Bus MOD = Bus.MOD;

        public static final Bus GAME = Bus.GAME;

    }

{% when '1.21.1' %}

    public static IEventBus getEventBus(EventBusSubscriber.Bus busType) {

        if (busType == EventBusType.MOD) {
            return MOD_BUS;
        } else if (busType == EventBusType.GAME) {
            return GAME_BUS;
        } else {
            throw new IllegalArgumentException("Invalid busType: " + busType);
        }
    }

    public static class EventBusType {

        public static final EventBusSubscriber.Bus MOD = EventBusSubscriber.Bus.MOD;

        public static final EventBusSubscriber.Bus GAME = EventBusSubscriber.Bus.GAME;

    }

{% when '26.1.2' %}
{% endcase %}
}
