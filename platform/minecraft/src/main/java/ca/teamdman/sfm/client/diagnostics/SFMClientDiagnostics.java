package ca.teamdman.sfm.client.diagnostics;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.item.DiskItem;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
import net.minecraft.SharedConstants;
import net.minecraft.client.ClientBrandRetriever;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.versions.forge.ForgeVersion;

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
                    .append(SharedConstants.getCurrentVersion().getName())
                    .append(" (")
                    .append(Minecraft.getInstance().getLaunchedVersion())
                    .append("/")
                    .append(ClientBrandRetriever.getClientModName())
                    .append(")")
                    .append('\n');

            content.append("-- Forge Version: ")
                    .append(ForgeVersion.getVersion())
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
                    content.append("-- * ").append(I18n.get(error.getKey(), error.getArgs())).append("\n");
                }
            }

            var warnings = DiskItem.getWarnings(diskStack);
            if (!warnings.isEmpty()) {
                content.append("\n-- Warnings\n");
                for (var warning : warnings) {
                    content.append("-- * ").append(I18n.get(warning.getKey(), warning.getArgs())).append("\n");
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
