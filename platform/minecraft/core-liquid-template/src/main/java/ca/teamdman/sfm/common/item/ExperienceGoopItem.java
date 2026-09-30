package ca.teamdman.sfm.common.item;

import ca.teamdman.sfm.common.localization.LocalizationEntry;
import ca.teamdman.sfm.common.localization.SFMLocalizationDatagen;
{% case minecraft_version %}
{% when '1.19.2' %}
import ca.teamdman.sfm.common.registry.registration.SFMCreativeTabs;
{% when '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1', '26.1.2' %}
{% endcase %}
import ca.teamdman.sfm.common.registry.registration.SFMItems;
import net.minecraft.world.item.Item;

public class ExperienceGoopItem extends Item {
    @SFMLocalizationDatagen
    public static final LocalizationEntry EXPERIENCE_GOOP_ITEM = new LocalizationEntry(
            () -> SFMItems.EXPERIENCE_GOOP.get().getDescriptionId(),
            () -> "Experience Goop"
    );

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
    public ExperienceGoopItem() {
{% when '26.1.2' %}
    public ExperienceGoopItem(Item.Properties properties) {
{% endcase %}

{% case minecraft_version %}
{% when '1.19.2' %}
        super(new Properties().tab(SFMCreativeTabs.MAIN));
{% when '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
        super(new Properties());
{% when '26.1.2' %}
        super(properties);
{% endcase %}
    }

}
