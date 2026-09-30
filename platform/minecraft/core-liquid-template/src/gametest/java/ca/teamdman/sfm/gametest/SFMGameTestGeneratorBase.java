package ca.teamdman.sfm.gametest;

import java.util.function.Consumer;

/**
 * Base class for game test generators. Subclasses annotated with
 * {@link SFMGameTestGenerator} will have their {@link #generateTests} method
 * invoked during test discovery to produce multiple game test definitions.
 */
public abstract class SFMGameTestGeneratorBase {

    /**
     * Generates game test definitions and passes them to the provided consumer.
     *
     * @param testConsumer a consumer that accepts generated test definitions
     */
    public abstract void generateTests(Consumer<SFMGameTestDefinition> testConsumer);
}
