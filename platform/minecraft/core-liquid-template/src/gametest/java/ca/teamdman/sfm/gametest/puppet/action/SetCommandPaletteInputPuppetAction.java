package ca.teamdman.sfm.gametest.puppet.action;

{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4" %}
{% when "1.21", "1.21.1", "26.1.2" %}
import ca.teamdman.sfm.common.util.SFMResourceLocation;

{% endcase %}
import ca.teamdman.sfm.client.screen.SFMCommandPaletteScreen;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
import ca.teamdman.sfm.client.registry.SFMClientActions;
import ca.teamdman.sfm.client.action.SFMClientActionContext;
import ca.teamdman.sfm.client.presentation.SFMItemIconResolver;
import ca.teamdman.sfm.common.registry.SFMWellKnownRegistries;
{% endcase %}
import ca.teamdman.sfm.gametest.puppet.ISFMGamePuppetRuntime;
import net.minecraft.client.Minecraft;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
import net.minecraft.resources.ResourceLocation;
{% when "26.1.2" %}
import net.minecraft.resources.Identifier;
{% endcase %}

public final class SetCommandPaletteInputPuppetAction implements SFMPuppetAction {
    private final String input;
    private final String expectedAfterActivation;

    public SetCommandPaletteInputPuppetAction(String input, String expectedAfterActivation) {
        this.input = input;
        this.expectedAfterActivation = expectedAfterActivation;
    }

    @Override
    public String description() {
        return "set command palette input to " + input;
    }

    @Override
    public boolean tick(ISFMGamePuppetRuntime runtime) {
        if (!(Minecraft.getInstance().screen instanceof SFMCommandPaletteScreen palette)) {
            throw new IllegalStateException("Expected command palette while setting puppet input");
        }
        if (expectedAfterActivation == null) palette.setInputForAutomation(input);
        else palette.prepareIncompleteInputForAutomation(input, expectedAfterActivation);
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4" %}
        if (input.contains("sfm:developer/open_")) {
            var action = SFMClientActions.registry().get(new ResourceLocation("sfm", "developer/open_file_explorer"));
            if (action == null) throw new IllegalStateException("File explorer action was not registered");
            var icon = action.itemIcon(SFMClientActionContext.create(Minecraft.getInstance().screen, () -> true))
                    .orElseThrow(() -> new IllegalStateException("File explorer action had no ItemStack icon metadata"));
            var resolved = SFMItemIconResolver.resolve(icon);
            ResourceLocation itemId = SFMWellKnownRegistries.ITEMS.getId(resolved.stack().getItem());
            if (!new ResourceLocation("minecraft", "chest").equals(itemId)) {
                throw new IllegalStateException("File explorer action icon resolved to " + itemId);
            }
        }
{% when "1.21", "1.21.1" %}
        if (input.contains("sfm:developer/open_")) {
            var action = SFMClientActions.registry().get(SFMResourceLocation.fromNamespaceAndPath("sfm", "developer/open_file_explorer"));
            if (action == null) throw new IllegalStateException("File explorer action was not registered");
            var icon = action.itemIcon(SFMClientActionContext.create(Minecraft.getInstance().screen, () -> true))
                    .orElseThrow(() -> new IllegalStateException("File explorer action had no ItemStack icon metadata"));
            var resolved = SFMItemIconResolver.resolve(icon);
            ResourceLocation itemId = SFMWellKnownRegistries.ITEMS.getId(resolved.stack().getItem());
            if (!SFMResourceLocation.fromNamespaceAndPath("minecraft", "chest").equals(itemId)) {
                throw new IllegalStateException("File explorer action icon resolved to " + itemId);
            }
        }
{% when "26.1.2" %}
        if (input.contains("sfm:developer/open_")) {
            var action = SFMClientActions.registry().get(SFMResourceLocation.fromNamespaceAndPath("sfm", "developer/open_file_explorer"))
                    .map(reference -> reference.value()).orElse(null);
            if (action == null) throw new IllegalStateException("File explorer action was not registered");
            var icon = action.itemIcon(SFMClientActionContext.create(Minecraft.getInstance().screen, () -> true))
                    .orElseThrow(() -> new IllegalStateException("File explorer action had no ItemStack icon metadata"));
            var resolved = SFMItemIconResolver.resolve(icon);
            Identifier itemId = SFMWellKnownRegistries.ITEMS.getId(resolved.stack().getItem());
            if (!SFMResourceLocation.fromNamespaceAndPath("minecraft", "chest").equals(itemId)) {
                throw new IllegalStateException("File explorer action icon resolved to " + itemId);
            }
        }
{% endcase %}
        return true;
    }
}
