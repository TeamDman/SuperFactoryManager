package ca.teamdman.sfm.client.screen;

import ca.teamdman.sfm.client.ClientTranslationHelpers;
import ca.teamdman.sfm.client.text_styling.ProgramSyntaxHighlightingHelper;
import ca.teamdman.sfm.common.logging.TranslatableLogEvent;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import org.apache.logging.log4j.Level;

import java.util.ArrayList;
import java.util.List;

public class LogsTextStylingHelper {
    public static List<ITextComponent> getStyledLogs(Iterable<TranslatableLogEvent> logs) {
        List<ITextComponent> processedLogs = new ArrayList<>();
        for (TranslatableLogEvent log : logs) {
            int secondsAgo = Math.toIntExact(log.instant().elapsed().getSeconds());
            int minutes = secondsAgo / 60;
            secondsAgo = secondsAgo % 60;
            var ago = new TextComponentString(minutes + "m" + secondsAgo + "s ago").setStyle(new Style().setColor(TextFormatting.GRAY));

            var level = new TextComponentString(" [" + log.level() + "] ");
            if (log.level() == Level.ERROR) {
                level.getStyle().setColor(TextFormatting.RED);
            } else if (log.level() == Level.WARN) {
                level.getStyle().setColor(TextFormatting.YELLOW);
            } else if (log.level() == Level.INFO) {
                level.getStyle().setColor(TextFormatting.GREEN);
            } else if (log.level() == Level.DEBUG) {
                level.getStyle().setColor(TextFormatting.AQUA);
            } else if (log.level() == Level.TRACE) {
                level.getStyle().setColor(TextFormatting.DARK_GRAY);
            }

            String[] lines = ClientTranslationHelpers.resolveTranslation(log.contents()).split("\n", -1);

            StringBuilder codeBlock = new StringBuilder();
            boolean insideCodeBlock = false;

            for (int i = 0; i < lines.length; i++) {
                String line = lines[i];
                ITextComponent lineComponent;

                if (line.equals("```")) {
                    if (insideCodeBlock) {
                        // output processed code
                        var codeLines = ProgramSyntaxHighlightingHelper.withSyntaxHighlighting(
                                codeBlock.toString(),
                                false
                        );
                        processedLogs.addAll(codeLines);
                        codeBlock = new StringBuilder();
                    } else {
                        // begin tracking code
                        insideCodeBlock = true;
                    }
                } else if (insideCodeBlock) {
                    codeBlock.append(line).append("\n");
                } else {
                    lineComponent = new TextComponentString(line).setStyle(new Style().setColor(TextFormatting.WHITE));
                    if (i == 0) {
                        lineComponent = ago
                                .appendSibling(level)
                                .appendSibling(lineComponent);
                    }
                    processedLogs.add(lineComponent);
                }
            }
        }
        return processedLogs;
    }
}
