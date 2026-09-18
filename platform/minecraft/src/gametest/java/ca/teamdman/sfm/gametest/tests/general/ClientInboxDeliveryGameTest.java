package ca.teamdman.sfm.gametest.tests.general;

import ca.teamdman.sfm.client.net.SFMClientInbox;
import ca.teamdman.sfm.client.net.SFMClientInboxRuntime;
import ca.teamdman.sfm.client.net.SFMClientInboxTransport;
import ca.teamdman.sfm.common.net.SFMClientInboxAddress;
import ca.teamdman.sfm.common.net.SFMServerClientInboxTransport;
import ca.teamdman.sfm.common.util.SFMDist;
import ca.teamdman.sfm.common.value.SFMValue;
import ca.teamdman.sfm.gametest.SFMGameTest;
import ca.teamdman.sfm.gametest.SFMGameTestDefinition;
import ca.teamdman.sfm.gametest.SFMGameTestHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** Proves actual subscribed clientbound dispatch without a GUI or window control. */
@SFMGameTest(SFMDist.CLIENT)
public final class ClientInboxDeliveryGameTest extends SFMGameTestDefinition {
    private static final ResourceLocation CHANNEL = new ResourceLocation("sfm", "gametest_inbox_delivery");

    @Override
    public String template() {
        return "1x1x1";
    }

    @Override
    public void run(SFMGameTestHelper helper) {
        List<ServerPlayer> players = helper.getLevel().getServer().getPlayerList().getPlayers();
        helper.assertTrue(players.size() == 1, "Client inbox test requires one private integrated owner");
        ServerPlayer owner = players.get(0);
        SFMClientInboxAddress address = new SFMClientInboxAddress(
                owner.getUUID(), owner.getLevel().dimension().location(), CHANNEL
        );
        SFMValue expected = SFMValue.of("delivery-" + UUID.randomUUID());
        AtomicReference<SFMClientInboxRuntime.Subscription> subscription = new AtomicReference<>();
        AtomicReference<String> clientFailure = new AtomicReference<>();
        AtomicBoolean published = new AtomicBoolean();

        Minecraft.getInstance().execute(() -> {
            try {
                subscription.set(SFMClientInboxTransport.subscribe(address).orElseThrow());
            } catch (RuntimeException failure) {
                clientFailure.set(failure.toString());
            }
        });

        helper.succeedWhen(() -> {
            helper.assertTrue(clientFailure.get() == null, String.valueOf(clientFailure.get()));
            if (SFMServerClientInboxTransport.isSubscribed(owner, address)
                && published.compareAndSet(false, true)) {
                helper.assertTrue(
                        SFMServerClientInboxTransport.publish(owner, address, expected)
                                == SFMServerClientInboxTransport.Result.SENT,
                        "Subscribed integrated owner did not accept the addressed value"
                );
            }
            helper.assertTrue(published.get(), "Waiting for serverbound inbox subscription");
            SFMClientInbox.Page page = SFMClientInboxRuntime.get()
                    .page(CHANNEL, Optional.empty(), 10)
                    .orElseThrow(() -> new AssertionError("Client inbox subscription is not active"));
            helper.assertTrue(
                    page.entries().stream().filter(entry -> expected.equals(entry.value())).count() == 1,
                    "Waiting for one addressed clientbound value"
            );
            Minecraft.getInstance().execute(() -> {
                SFMClientInboxRuntime.Subscription active = subscription.get();
                if (active != null) {
                    active.close();
                }
            });
        });
    }
}
