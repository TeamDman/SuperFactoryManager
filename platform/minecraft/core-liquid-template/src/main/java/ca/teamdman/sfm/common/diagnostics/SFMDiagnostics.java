package ca.teamdman.sfm.common.diagnostics;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.client.diagnostics.SFMClientDiagnostics;
import ca.teamdman.sfm.common.item.DiskItem;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfm.common.util.SFMEnvironmentUtils;
import net.minecraft.SharedConstants;
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
import net.neoforged.neoforge.common.NeoForgeVersion;
{% endcase %}

import java.text.SimpleDateFormat;
import java.util.Date;

public class SFMDiagnostics {
    public static String getDiagnosticsSummary(
            ItemStack diskStack
    ) {
        if (SFMEnvironmentUtils.isClient()) {
            return SFMClientDiagnostics.getDiagnosticsSummary(diskStack);
        }

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
                    .append('\n');

            content.append("-- Forge Version: ")
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1' %}
                    .append(ForgeVersion.getVersion())
{% when '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1', '26.1.2' %}
                    .append(NeoForgeVersion.getVersion())
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
                    content.append("-- * ").append(error.toString()).append("\n");
                }
            }

            var warnings = DiskItem.getWarnings(diskStack);
            if (!warnings.isEmpty()) {
                content.append("\n-- Warnings\n");
                for (var warning : warnings) {
                    content.append("-- * ").append(warning.toString()).append("\n");
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
