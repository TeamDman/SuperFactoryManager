package ca.teamdman.sfm.client.export;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.registry.registration.SFMResourceTypes;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.fml.loading.FMLPaths;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collection;

public class ClientExportHelper {

    private static final Object registryReaderLock = new Object();

    public static Collection<ItemStack> gatherItems() {
        assert Minecraft.getInstance().player != null;
        assert Minecraft.getInstance().level != null;
        CreativeModeTabs.tryRebuildTabContents(
                Minecraft.getInstance().player.connection.enabledFeatures(),
                true,
                Minecraft.getInstance().level.registryAccess()
        );
        return CreativeModeTabs.searchTab().getDisplayItems();
    }

    // https://github.com/TeamDman/tell-me-my-items/blob/6fb767f0145abebff503b87a10a1810ca24580b9/mod/src/main/java/ca/teamdman/tellmemyitems/TellMeMyItems.java#L36
    public static void dumpItems(@Nullable Player player) throws IOException {
        // manually build JSON array
        JsonArray jsonArray = new JsonArray();

        var items = gatherItems();
        for (ItemStack stack : items) {
            JsonObject jsonObject = new JsonObject();

            // Add the id field
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
            jsonObject.addProperty("id", id.toString());

            // Add the data field if it exists
            if (stack.getTag() != null) {
                jsonObject.addProperty("data", stack.getTag().toString());
            }

            // Add the tags
            JsonArray tags = new JsonArray();
            SFMResourceTypes.ITEM.get().getTagsForStack(stack).map(ResourceLocation::toString).forEach(tags::add);
            jsonObject.add("tags", tags);

            // Add the tooltip field (requires player)
            String tooltip = stack.getTooltipLines(player, TooltipFlag.ADVANCED)
                    .stream()
                    .map(Component::getString)
                    .reduce((line1, line2) -> line1 + "\n" + line2)
                    .orElse("");
            jsonObject.addProperty("tooltip", tooltip);

            jsonArray.add(jsonObject);
        }

        // serialize to JSON with pretty printing
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        String content = gson.toJson(jsonArray);

        // ensure folder exists
        var gameDir = FMLPaths.GAMEDIR.get();
        var folder = Paths.get(gameDir.toString(), SFM.MOD_ID);
        Files.createDirectories(folder);

        // write to file
        File itemFile = new File(folder.toFile(), "items.json");
        try (FileOutputStream str = new FileOutputStream(itemFile)) {
            str.write(content.getBytes(StandardCharsets.UTF_8));
        }
        SFM.LOGGER.info("Exported item data to {}", itemFile);
        assert Minecraft.getInstance().player != null;
        Minecraft.getInstance().player.sendSystemMessage(Component.literal(String.format(
                "Exported %d items to \"%s\"",
                items.size(),
                itemFile.getAbsolutePath()
        )));
    }
}
