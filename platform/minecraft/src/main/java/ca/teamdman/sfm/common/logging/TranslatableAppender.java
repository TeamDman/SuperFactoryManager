package ca.teamdman.sfm.common.logging;

import ca.teamdman.sfm.common.timing.SFMEpochInstant;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginBuilderAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.apache.logging.log4j.message.Message;

import java.io.Serializable;
import java.util.LinkedList;
import java.util.Objects;

@Plugin(name = "TranslatableAppender", category = "Core", elementType = Appender.ELEMENT_TYPE, printObject = true)
public class TranslatableAppender extends AbstractAppender {
    public final LinkedList<TranslatableLogEvent> contents;

    protected TranslatableAppender(String name, Layout<? extends Serializable> layout) {
        super(name, null, layout, true, null);
        this.contents = new LinkedList<>();
    }

    @PluginFactory
    public static TranslatableAppender createAppender(@PluginBuilderAttribute("name") String name) {
        Layout<? extends Serializable> layout = PatternLayout.createDefaultLayout();
        return new TranslatableAppender(name, layout);
    }

    @Override
    public void append(LogEvent event) {
        // clone since the event is mutable
        Level level = event.getLevel();

        Message message = event.getMessage();
        Object[] params = message.getParameters();
        String[] stringParams = new String[params.length];
        // Call toString now instead of later when the object members may have changed
        for (int i = 0; i < params.length; i++) {
            stringParams[i] = Objects.toString(params[i]);
        }
        TranslatableContents content = new TranslatableContents(message.getFormat(), null, stringParams);

        contents.add(new TranslatableLogEvent(
                level,
                SFMEpochInstant.now(),
                content
        ));
    }
}
