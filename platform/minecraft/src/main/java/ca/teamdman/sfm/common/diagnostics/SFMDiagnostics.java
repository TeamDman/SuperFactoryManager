package ca.teamdman.sfm.common.diagnostics;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.client.diagnostics.SFMClientDiagnostics;
import ca.teamdman.sfm.common.item.DiskItem;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfm.common.util.SFMEnvironmentUtils;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.ForgeVersion;
import net.minecraftforge.fml.common.Loader;

import java.text.SimpleDateFormat;

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
                    .append(new SimpleDateFormat("yyyy-MM-dd HH:mm.ss").format(new java.util.Date()))
                    .append('\n');

//            content
//                    .append("-- Game Version: ")
//                    .append("Minecraft ")
//                    .append(MinecraftServer.getServer().getMinecraftVersion())
//                    .append('\n');

            content.append("-- Forge Version: ")
                    .append(ForgeVersion.getVersion())
                    .append('\n');

            var modContainer = Loader.instance().getIndexedModList().getOrDefault(SFM.MOD_ID, null);
            //noinspection CodeBlock2Expr

            content.append("-- SFM Version: ")
                    .append(modContainer.getVersion())
                    .append('\n');


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
