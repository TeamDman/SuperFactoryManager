package ca.teamdman.sfm.client.screen;

import ca.teamdman.sfm.client.ClientTranslationHelpers;
import ca.teamdman.sfm.client.text_editor.TextEditScreenContentLanguage;
import ca.teamdman.sfm.client.text_styling.ProgramSyntaxHighlightingHelper;
import ca.teamdman.sfm.common.logging.TranslatableLogEvent;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.apache.logging.log4j.Level;

import java.util.ArrayList;
import java.util.List;

public class LogsTextStylingHelper {
    public static List<MutableComponent> getStyledLogs(Iterable<TranslatableLogEvent> logs) {
        List<MutableComponent> processedLogs = new ArrayList<>();
        for (TranslatableLogEvent log : logs) {
            int secondsAgo = Math.toIntExact(log.instant().elapsed().toSeconds());
            int minutes = secondsAgo / 60;
            secondsAgo = secondsAgo % 60;
            var ago = Component.literal(minutes + "m" + secondsAgo + "s ago").withStyle(ChatFormatting.GRAY);

            var level = Component.literal(" [" + log.level() + "] ");
            if (log.level() == Level.ERROR) {
                level = level.withStyle(ChatFormatting.RED);
            } else if (log.level() == Level.WARN) {
                level = level.withStyle(ChatFormatting.YELLOW);
            } else if (log.level() == Level.INFO) {
                level = level.withStyle(ChatFormatting.GREEN);
            } else if (log.level() == Level.DEBUG) {
                level = level.withStyle(ChatFormatting.AQUA);
            } else if (log.level() == Level.TRACE) {
                level = level.withStyle(ChatFormatting.DARK_GRAY);
            }

            String[] lines = ClientTranslationHelpers.resolveTranslation(log.contents()).split("\n", -1);

            StringBuilder codeBlock = new StringBuilder();
            boolean insideCodeBlock = false;

            for (int i = 0; i < lines.length; i++) {
                String line = lines[i];
                MutableComponent lineComponent;

                if (line.equals("```")) {
                    if (insideCodeBlock) {
                        // output processed code
                        var codeLines = ProgramSyntaxHighlightingHelper.withSyntaxHighlighting(
                                codeBlock.toString(),
                                false,
                                TextEditScreenContentLanguage.SFML
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
                    lineComponent = Component.literal(line).withStyle(ChatFormatting.WHITE);
                    if (i == 0) {
                        lineComponent = ago
                                .append(level)
                                .append(lineComponent);
                    }
                    processedLogs.add(lineComponent);
                }
            }
        }
        return processedLogs;
    }
}
