package ca.teamdman.sfm.common.config;

import net.minecraftforge.common.config.Config;
import net.minecraftforge.common.config.Config.Comment;
import net.minecraftforge.common.config.Config.Name;
import net.minecraftforge.common.config.Config.RangeInt;
import net.minecraftforge.common.config.Config.RequiresMcRestart;
import net.minecraftforge.common.config.ConfigManager;
import net.minecraftforge.fml.client.event.ConfigChangedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.client.text_editor.SFMTextEditorIntellisenseLevel;

@Mod.EventBusSubscriber(modid = SFM.MOD_ID)
@Config(modid = SFM.MOD_ID, name = "superfactorymanager")
public class SFMConfig {

    @Config.Ignore
    protected static int configRevision = 1;

    public static int getConfigRevision() {
        return configRevision;
    }

    @SubscribeEvent
    public static void onConfigChanged(ConfigChangedEvent.OnConfigChangedEvent event) {
        if (event.getModID().equals(SFM.MOD_ID)) {
            configRevision++;

            ConfigManager.sync(SFM.MOD_ID, Config.Type.INSTANCE);
        }

    }

    @Name("client")
    @Comment("Client-side settings")
    public static final Client client = new Client();

    @Name("server")
    @Comment("Server-side settings")

    public static final Server server = new Server();

    public static class Client {

        @Name("showLineNumbers")
        @Comment("Show line numbers in the text editor")
        public boolean showLineNumbers = true;

        @Name("intellisenseLevel")
        @Comment("Controls the level of intellisense in the text editor")
        public SFMTextEditorIntellisenseLevel intellisenseLevel = SFMTextEditorIntellisenseLevel.ADVANCED;

        @Name("preferredEditor")
        public String preferredEditor = "sfm:v2";

        @Name("showLabelGunReminderOverlay")
        @Comment("Show the label gun reminder overlay")
        public boolean showLabelGunReminderOverlay = true;

        @Name("showNetworkToolReminderOverlay")
        public boolean showNetworkToolReminderOverlay = true;
    }

    public static class Server {

        @Name("disableProgramExecution")
        @Comment("Prevents factory managers from compiling and running code (for emergencies)")
        public boolean disableProgramExecution = false;

        @Name("logResourceLossToConsole")
        @Comment("Log resource loss to console")
        public boolean logResourceLossToConsole = true;

        @Name("timerTriggerMinimumIntervalInTicks")
        @RangeInt(min = 1)
        public int timerTriggerMinimumIntervalInTicks = 20;

        @Name("timerTriggerMinimumIntervalInTicksWhenOnlyForgeEnergyIO")
        @RangeInt(min = 1)
        public int timerTriggerMinimumIntervalInTicksWhenOnlyForgeEnergyIO = 1;

        @Name("maxIfStatementsInTriggerBeforeSimulationIsntAllowed")
        @Comment("The number of scenarios to check is 2^n where n is the number of if statements in a trigger")
        @RangeInt(min = 0)
        public int maxIfStatementsInTriggerBeforeSimulationIsntAllowed = 10;

        @Name("disallowedResourceTypesForTransfer")
        @Comment("What resource types should SFM not be allowed to move")
        public String[] disallowedResourceTypesForTransfer = new String[0];

        @Name("maxDiskProblems")
        public int maxDiskProblems = 10;

    }

}
