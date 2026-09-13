package ca.teamdman.sfm.gametest.tests.general;

import ca.teamdman.sfm.client.action.SFMClientActionContext;
import ca.teamdman.sfm.client.action.SFMClientActionExecutor;
import ca.teamdman.sfm.client.action.SFMClientActionStructuredResult;
import ca.teamdman.sfm.client.action.SFMPacketListAction;
import ca.teamdman.sfm.client.action.SFMPacketSendAction;
import ca.teamdman.sfm.client.net.SFMPacketObservationLog;
import ca.teamdman.sfm.client.net.SFMPacketObservationRuntime;
import ca.teamdman.sfm.common.item.PacketItem;
import ca.teamdman.sfm.common.registry.registration.SFMPackets;
import ca.teamdman.sfm.common.util.SFMDist;
import ca.teamdman.sfm.common.value.SFMValue;
import ca.teamdman.sfm.common.value.SFMValueJsonCodec;
import ca.teamdman.sfm.gametest.SFMGameTest;
import ca.teamdman.sfm.gametest.SFMGameTestDefinition;
import ca.teamdman.sfm.gametest.SFMGameTestHelper;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/** Narrow real-client proof for the A3 registered-action surface. */
@SFMGameTest(SFMDist.CLIENT)
public class PacketControlActionsGameTest extends SFMGameTestDefinition {
    @Override
    public String template() {
        return "3x3x1";
    }

    @Override
    public void run(SFMGameTestHelper helper) {
        BlockPos chestPos = new BlockPos(0, 2, 0);
        helper.setBlock(chestPos, Blocks.CHEST);
        List<ServerPlayer> players = helper.getLevel().getServer().getPlayerList().getPlayers();
        helper.assertTrue(players.size() == 1, "Integrated packet control test requires exactly one player");

        SFMValue observed = SFMValue.object(Map.of(
                "a3", SFMValue.of("registered-list-action"),
                "sequence", SFMValue.of(31)
        ));
        SFMValue sent = SFMValue.object(Map.of(
                "a3", SFMValue.of("registered-send-action"),
                "sequence", SFMValue.of(37)
        ));
        helper.assertTrue(
                SFMPackets.sendPacketObservation(players.get(0), observed),
                "Private integrated owner should pass the observation gate"
        );

        AtomicReference<JsonObject> listResult = new AtomicReference<>();
        AtomicReference<JsonObject> sendResult = new AtomicReference<>();
        AtomicReference<String> clientFailure = new AtomicReference<>();
        BlockPos absoluteChestPos = helper.absolutePos(chestPos);
        String dimension = helper.getLevel().dimension().location().toString();
        Minecraft minecraft = Minecraft.getInstance();
        AtomicReference<Runnable> clientStep = new AtomicReference<>();
        clientStep.set(() -> {
            try {
                boolean observationArrived = SFMPacketObservationRuntime.get()
                        .page(Optional.empty(), SFMPacketObservationLog.MAX_PAGE_SIZE)
                        .stream()
                        .flatMap(page -> page.entries().stream())
                        .map(SFMPacketObservationLog.Entry::value)
                        .anyMatch(observed::equals);
                if (!observationArrived) {
                    minecraft.execute(clientStep.get());
                    return;
                }

                SFMClientActionContext context = SFMClientActionContext.create(
                        minecraft.screen,
                        () -> minecraft.screen == null
                );
                SFMClientActionExecutor.execute(
                        "sfm action invoke sfm:packet/list limit 100",
                        context,
                        ignored -> {
                        },
                        value -> listResult.set(requireSchema(value, SFMPacketListAction.RESULT_SCHEMA))
                );

                String valueJson = SFMValueJsonCodec.encode(sent);
                String sendCommand = "sfm action invoke sfm:packet/send "
                                     + dimension + " "
                                     + absoluteChestPos.getX() + " " + absoluteChestPos.getY() + " "
                                     + absoluteChestPos.getZ() + " "
                                     + StringArgumentType.escapeIfRequired(valueJson);
                SFMClientActionExecutor.execute(
                        sendCommand,
                        context,
                        ignored -> {
                        },
                        value -> sendResult.set(requireSchema(value, SFMPacketSendAction.RESULT_SCHEMA))
                );
            } catch (RuntimeException | com.mojang.brigadier.exceptions.CommandSyntaxException failure) {
                clientFailure.set(failure.toString());
            }
        });
        minecraft.execute(clientStep.get());

        helper.succeedWhen(() -> {
            helper.assertTrue(clientFailure.get() == null, String.valueOf(clientFailure.get()));
            JsonObject listed = listResult.get();
            JsonObject send = sendResult.get();
            helper.assertTrue(listed != null, "Registered packet list action has not completed");
            helper.assertTrue(send != null, "Registered packet send action has not completed");
            helper.assertTrue("ok".equals(listed.get("status").getAsString()),
                    "Packet list action did not return ok");
            boolean listedExpected = false;
            for (var element : listed.getAsJsonArray("entries")) {
                if (SFMValueJsonCodec.encode(observed).equals(
                        element.getAsJsonObject().get("value").toString())) {
                    listedExpected = true;
                    break;
                }
            }
            helper.assertTrue(listedExpected, "Registered packet list action omitted the observed value");
            helper.assertTrue("send_attempted".equals(send.get("status").getAsString()),
                    "Packet send action did not report local send attempt");
            helper.assertTrue(send.get("local_transport_accepted").getAsBoolean(),
                    "Packet send action was not accepted by the local transport");
            helper.assertCount(
                    helper.getItemHandler(chestPos),
                    PacketItem.create(sent),
                    1,
                    "Registered packet send action did not reach the exact chest"
            );
        });
    }

    private static JsonObject requireSchema(
            SFMClientActionStructuredResult result,
            String expectedSchema
    ) {
        if (!expectedSchema.equals(result.schemaId())) {
            throw new IllegalStateException("Unexpected packet action schema " + result.schemaId());
        }
        return JsonParser.parseString(result.json()).getAsJsonObject();
    }
}
