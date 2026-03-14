package ca.teamdman.sfml.ast;

import ca.teamdman.sfm.common.localization.LocalizationKeys;
import ca.teamdman.sfm.common.logging.TranslatableLogger;
import ca.teamdman.sfm.common.program.ProgramContext;
import org.apache.logging.log4j.Level;

import java.util.List;

public record LogExpression(
        SfmlLogLevel logLevel,

        Displayable message
) implements Tickable {
    @Override
    public List<? extends SfmlAstNode> getChildNodes() {

        return List.of(logLevel, message);
    }

    @Override
    public void tick(ProgramContext programContext) {

        tick(programContext.logger());
    }

    public void tick(TranslatableLogger logger) {

        Level level = logLevel.level();
        String msg = message.display();
        if (level == Level.INFO) {
            logger.info(LocalizationKeys.LOG_PROGRAM_LOG_EXPRESSION.get(msg));
        } else if (level == Level.DEBUG) {
            logger.debug(LocalizationKeys.LOG_PROGRAM_LOG_EXPRESSION.get(msg));
        } else if (level == Level.WARN) {
            logger.warn(LocalizationKeys.LOG_PROGRAM_LOG_EXPRESSION.get(msg));
        } else if (level == Level.ERROR) {
            logger.error(LocalizationKeys.LOG_PROGRAM_LOG_EXPRESSION.get(msg));
        } else if (level == Level.TRACE) {
            logger.trace(LocalizationKeys.LOG_PROGRAM_LOG_EXPRESSION.get(msg));
        }
    }

    @Override
    public String toString() {

        return "LOG " + logLevel + " " + message;
    }

}
