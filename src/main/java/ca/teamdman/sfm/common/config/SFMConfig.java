package ca.teamdman.sfm.common.config;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.client.text_editor.SFMTextEditorIntellisenseLevel;
import net.minecraftforge.common.config.Config;
import net.minecraftforge.common.config.Config.Comment;
import net.minecraftforge.common.config.Config.Name;
import net.minecraftforge.common.config.Config.RangeInt;
import net.minecraftforge.common.config.Config.RequiresMcRestart;
import net.minecraftforge.fml.client.event.ConfigChangedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

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
        }
    }

    @Name("client")
    @Comment("Client-side settings")
    public static final Client client = new Client();

    @Name("server")
    @Comment("Server-side settings")
    @RequiresMcRestart
    public static final Server server = new Server();

    public static class Client {
        @Name("showLineNumbers")
        @Comment("Show line numbers in the text editor")
        public boolean showLineNumbers = true;

        @Name("intellisenseLevel")
        @Comment("Controls the level of intellisense in the text editor")
        public SFMTextEditorIntellisenseLevel intellisenseLevel = SFMTextEditorIntellisenseLevel.ADVANCED;

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

        @Name("levelsToShards")
        @Comment({
            "How to convert Enchanted Books to Experience Shards",
            "JustOne = always produces 1 shard regardless of enchantments",
            "EachOne = produces 1 shard per enchantment on the book.",
            "SumLevels = produces a number of shards equal to the sum of the enchantments' levels",
            "SumLevelsScaledExponentially = produces a number of shards equal to the sum of 2 to the power of each enchantment's level (1 -> 1 shard, 2 -> 4 shards, 3 -> 8 shards, etc)"
        })
        public LevelsToShards levelsToShards = LevelsToShards.JustOne;
    }

    public enum LevelsToShards {
        JustOne,
        EachOne,
        SumLevels,
        SumLevelsScaledExponentially,
    }
}