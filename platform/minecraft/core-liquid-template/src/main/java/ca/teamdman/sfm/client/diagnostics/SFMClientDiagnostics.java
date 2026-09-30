package ca.teamdman.sfm.client.diagnostics;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.item.DiskItem;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
import net.minecraft.SharedConstants;
import net.minecraft.client.ClientBrandRetriever;
import net.minecraft.client.Minecraft;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
import net.minecraft.client.resources.language.I18n;
{% when '1.21', '1.21.1', '26.1.2' %}
{% endcase %}
import net.minecraft.world.item.ItemStack;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1' %}
import net.minecraftforge.fml.ModList;
import net.minecraftforge.versions.forge.ForgeVersion;
{% when '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.internal.versions.neoforge.NeoForgeVersion;
{% when '26.1.2' %}
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.internal.NeoForgeVersionCheck;
{% endcase %}

import java.text.SimpleDateFormat;
import java.util.Date;

public class SFMClientDiagnostics {
    public static String getDiagnosticsSummary(
            ItemStack diskStack
    ) {
        StringBuilder content = new StringBuilder();
        try {
            content
                    .append("-- Diagnostic info --\n");

            content.append("-- Program:\n")
                    .append(DiskItem.getProgramString(diskStack))
                    .append("\n\n");

            content.append("-- DateTime: ")
                    .append(new SimpleDateFormat("yyyy-MM-dd HH:mm.ss").format(new Date()))
                    .append('\n');

            content
                    .append("-- Game Version: ")
                    .append("Minecraft ")
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
                    .append(SharedConstants.getCurrentVersion().getName())
{% when '26.1.2' %}
                    .append(SharedConstants.getCurrentVersion().name())
{% endcase %}
                    .append(" (")
                    .append(Minecraft.getInstance().getLaunchedVersion())
                    .append("/")
                    .append(ClientBrandRetriever.getClientModName())
                    .append(")")
                    .append('\n');

            content.append("-- Forge Version: ")
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1' %}
                    .append(ForgeVersion.getVersion())
{% when '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
                    .append(NeoForgeVersion.getVersion())
{% when '26.1.2' %}
                    .append(NeoForgeVersionCheck.getTarget())
{% endcase %}
                    .append('\n');

            //noinspection CodeBlock2Expr
            ModList.get().getModContainerById(SFM.MOD_ID).ifPresent(mod -> {
                content.append("-- SFM Version: ")
                        .append(mod.getModInfo().getVersion())
                        .append('\n');
            });

            var errors = DiskItem.getErrors(diskStack);
            if (!errors.isEmpty()) {
                content.append("\n-- Errors\n");
                for (var error : errors) {
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
                    content.append("-- * ").append(I18n.get(error.getKey(), error.getArgs())).append("\n");
{% when '1.21', '1.21.1', '26.1.2' %}
                    content.append("-- * ").append(error.getString()).append("\n");
{% endcase %}
                }
            }

            var warnings = DiskItem.getWarnings(diskStack);
            if (!warnings.isEmpty()) {
                content.append("\n-- Warnings\n");
                for (var warning : warnings) {
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
                    content.append("-- * ").append(I18n.get(warning.getKey(), warning.getArgs())).append("\n");
{% when '1.21', '1.21.1', '26.1.2' %}
                    content.append("-- * ").append(warning.getString()).append("\n");
{% endcase %}
                }
            }

            var labels = LabelPositionHolder.from(diskStack);
            content.append("\n-- Labels\n").append(labels.toDebugString());
        } catch (Throwable t) {
            SFM.LOGGER.error("Failed gathering diagnostic info, returning partial results. Error: ", t);
        }
        return content.toString();
    }
}
