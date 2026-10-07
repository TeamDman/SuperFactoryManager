package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.client.registry.SFMClientScreenTypes;
{% if features.command_palette %}
import ca.teamdman.sfm.client.screen.SFMCommandPaletteScreen;
{% endif %}
import ca.teamdman.sfm.client.screen.workspace.SFMClientScreenType;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenPanel;
import ca.teamdman.sfm.common.localization.LocalizationEntry;
import ca.teamdman.sfm.common.localization.SFMLocalizationDatagen;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
{% case minecraft_version %}
{% when "26.1.2" %}
import net.minecraft.resources.Identifier;
{% else %}
import net.minecraft.resources.ResourceLocation;
{% endcase %}
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

public final class OpenScreenToSideAction implements SFMClientAction<SFMClientActionContext> {
    @SFMLocalizationDatagen
    public static final LocalizationEntry TITLE = new LocalizationEntry(
            "gui.sfm.client_action.workspace.open_to_side.title",
            "Open screen to the side"
    );

    @SFMLocalizationDatagen
    public static final LocalizationEntry DESCRIPTION = new LocalizationEntry(
            "gui.sfm.client_action.workspace.open_to_side.description",
            "Open registered SFM content in a side-by-side workspace"
    );

{% case minecraft_version %}
{% when "26.1.2" %}
    private final Supplier<List<Map.Entry<Identifier, SFMClientScreenType>>> screenTypes;
{% else %}
    private final Supplier<List<Map.Entry<ResourceLocation, SFMClientScreenType>>> screenTypes;
{% endcase %}

    public OpenScreenToSideAction() {
        this(OpenScreenToSideAction::registeredScreenTypes);
    }

{% case minecraft_version %}
{% when "26.1.2" %}
    OpenScreenToSideAction(Supplier<List<Map.Entry<Identifier, SFMClientScreenType>>> screenTypes) {
{% else %}
    OpenScreenToSideAction(Supplier<List<Map.Entry<ResourceLocation, SFMClientScreenType>>> screenTypes) {
{% endcase %}
        this.screenTypes = screenTypes;
    }

    @Override
    public Component title() {
        return TITLE.getComponent();
    }

    @Override
    public Component description() {
        return DESCRIPTION.getComponent();
    }

    @Override
    public SFMClientActionRequirement<SFMClientActionContext> requirement() {
        return context -> context.originatingHostIsCurrent().getAsBoolean()
                ? SFMClientActionAvailability.available(context)
                : SFMClientActionAvailability.unavailable(SFMClientActionContext.ORIGINATING_HOST_CHANGED.getComponent());
    }

    @Override
    public void configureCommandNode(LiteralArgumentBuilder<SFMClientActionSource> node) {
{% case minecraft_version %}
{% when "26.1.2" %}
        for (Map.Entry<Identifier, SFMClientScreenType> registration : screenTypes.get()) {
{% else %}
        for (Map.Entry<ResourceLocation, SFMClientScreenType> registration : screenTypes.get()) {
{% endcase %}
            node.then(registration.getValue().createCommandNode(registration.getKey(), this::open));
        }
    }

    @Override
    public int execute(
            SFMClientActionContext target,
            CommandContext<SFMClientActionSource> context
    ) {
        throw new IllegalStateException("A registered screen type and its arguments are required");
    }

    private int open(
            CommandContext<SFMClientActionSource> commandContext,
            SFMScreenPanel panel
    ) {
        SFMClientActionContext actionContext = commandContext.getSource().context();
        @Nullable Screen origin = actionContext.originatingHost() instanceof Screen screen ? screen : null;
        Minecraft minecraft = Minecraft.getInstance();
{% if features.command_palette %}
        if (minecraft.screen instanceof SFMCommandPaletteScreen palette) {
            palette.onClose();
        }
{% endif %}
        SFMScreenMultiplexer.openToSide(origin, panel);
        return 1;
    }

{% case minecraft_version %}
{% when "26.1.2" %}
    private static List<Map.Entry<Identifier, SFMClientScreenType>> registeredScreenTypes() {
{% else %}
    private static List<Map.Entry<ResourceLocation, SFMClientScreenType>> registeredScreenTypes() {
{% endcase %}
{% case minecraft_version %}
{% when "26.1.2" %}
        List<Map.Entry<Identifier, SFMClientScreenType>> registrations = new ArrayList<>();
{% else %}
        List<Map.Entry<ResourceLocation, SFMClientScreenType>> registrations = new ArrayList<>();
{% endcase %}
{% case minecraft_version %}
{% when "26.1.2" %}
        for (Identifier id : SFMClientScreenTypes.registry().keys()) {
{% else %}
        for (ResourceLocation id : SFMClientScreenTypes.registry().keys()) {
{% endcase %}
{% case minecraft_version %}
{% when "26.1.2" %}
            registrations.add(Map.entry(id, Objects.requireNonNull(SFMClientScreenTypes.registry().get(id).map(reference -> reference.value()).orElse(null))));
{% else %}
            registrations.add(Map.entry(id, Objects.requireNonNull(SFMClientScreenTypes.registry().get(id))));
{% endcase %}
        }
        registrations.sort(Comparator.comparing(entry -> entry.getKey().toString()));
        return registrations;
    }
}
