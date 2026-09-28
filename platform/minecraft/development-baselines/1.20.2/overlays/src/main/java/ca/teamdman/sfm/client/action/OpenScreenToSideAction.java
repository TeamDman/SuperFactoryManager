package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.client.registry.SFMClientScreenTypes;
import ca.teamdman.sfm.client.screen.SFMCommandPaletteScreen;
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
import net.minecraft.resources.ResourceLocation;
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

    private final Supplier<List<Map.Entry<ResourceLocation, SFMClientScreenType>>> screenTypes;

    public OpenScreenToSideAction() {
        this(OpenScreenToSideAction::registeredScreenTypes);
    }

    OpenScreenToSideAction(Supplier<List<Map.Entry<ResourceLocation, SFMClientScreenType>>> screenTypes) {
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
        for (Map.Entry<ResourceLocation, SFMClientScreenType> registration : screenTypes.get()) {
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
        if (minecraft.screen instanceof SFMCommandPaletteScreen palette) {
            palette.onClose();
        }
        SFMScreenMultiplexer.openToSide(origin, panel);
        return 1;
    }

    private static List<Map.Entry<ResourceLocation, SFMClientScreenType>> registeredScreenTypes() {
        List<Map.Entry<ResourceLocation, SFMClientScreenType>> registrations = new ArrayList<>();
        for (ResourceLocation id : SFMClientScreenTypes.registry().keys()) {
            registrations.add(Map.entry(id, Objects.requireNonNull(SFMClientScreenTypes.registry().get(id))));
        }
        registrations.sort(Comparator.comparing(entry -> entry.getKey().toString()));
        return registrations;
    }
}
