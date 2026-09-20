package ca.teamdman.sfm.gametest.tests.general;

import ca.teamdman.sfm.common.blockentity.ClientManagerBlockEntity;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfm.common.program.signature.*;
import ca.teamdman.sfm.common.registry.registration.SFMBlocks;
import ca.teamdman.sfm.common.registry.registration.SFMItems;
import ca.teamdman.sfm.gametest.SFMGameTest;
import ca.teamdman.sfm.gametest.SFMGameTestDefinition;
import ca.teamdman.sfm.gametest.SFMGameTestHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.security.GeneralSecurityException;
import java.security.KeyPairGenerator;
import java.util.List;
import java.util.UUID;

/** Common storage/CAS/projection proof. Network authoring and consent UI are separate tests. */
@SFMGameTest
public final class ClientManagerSigningGameTest extends SFMGameTestDefinition {
    @Override public String template() { return "3x3x3"; }
    @Override public int maxTicks() { return 40; }

    @Override
    public void run(SFMGameTestHelper helper) {
        BlockPos position = new BlockPos(1, 2, 1);
        helper.setBlock(position, SFMBlocks.CLIENT_MANAGER.get());
        var manager = helper.getBlockEntity(position, ClientManagerBlockEntity.class);
        ItemStack disk = new ItemStack(SFMItems.DISK.get());
        disk.getOrCreateTag().putString("sfm:program", "CLIENT BTW\r\n-- original\r");
        disk.getOrCreateTag().putString("unrelated", "preserved-server-only");
        LabelPositionHolder.empty().add("unused", helper.absolutePos(BlockPos.ZERO)).save(disk);
        manager.setDisk(disk);
        helper.assertTrue(manager.storedSource().equals("CLIENT BTW\n-- original\n"),
                "Inserted source was not normalized before review");
        var session = new ClientManagerSigningSession();
        UUID player = UUID.randomUUID();
        List<ResourceLocation> capabilities = List.of(new ResourceLocation("sfm:client_program/execute"));
        long tick = helper.getLevel().getGameTime();
        var initial = manager.reviewForSigning(session, player, capabilities, tick).acknowledgement().orElseThrow();
        var saved = manager.saveForSigning(session, player, initial.snapshot().incarnation(), initial.snapshot().revision(),
                "CLIENT BTW\r\n-- acknowledged\r", capabilities, tick);
        helper.assertTrue(saved.status() == ClientManagerSigningState.Status.SAVED, "Save was not acknowledged");
        var acknowledgement = saved.acknowledgement().orElseThrow();
        helper.assertTrue(manager.storedSource().equals(acknowledgement.snapshot().body().source()),
                "Acknowledgement was not the exact committed source");
        ProgramAttestation signature;
        try {
            signature = ProgramAttestation.sign(acknowledgement.descriptor(),
                    KeyPairGenerator.getInstance("Ed25519").generateKeyPair());
        } catch (GeneralSecurityException unavailable) {
            throw new IllegalStateException("Ed25519 fixture unavailable", unavailable);
        }
        helper.assertTrue(manager.submitSignature(session, player, acknowledgement.snapshot().incarnation(),
                acknowledgement.snapshot().revision(), acknowledgement.challenge(), ProgramAttestationCodec.encode(signature), tick)
                         == ClientManagerSigningState.Status.SIGNED, "Acknowledged revision could not be signed");
        helper.assertTrue(manager.signingRevision() == acknowledgement.snapshot().revision(),
                "Appending an attestation changed the body revision");

        var persisted = manager.saveWithFullMetadata();
        var restored = new ClientManagerBlockEntity(helper.absolutePos(position), manager.getBlockState());
        restored.load(persisted);
        helper.assertTrue(manager.signingSnapshot().equals(restored.signingSnapshot()),
                "Incarnation, exact source, labels, revision or history did not survive restart");
        helper.assertTrue(restored.disk().getOrCreateTag().getString("unrelated").equals("preserved-server-only"),
                "Signing persistence destroyed unrelated server disk metadata");
        var projection = manager.getUpdateTag();
        helper.assertTrue(!projection.getCompound("disk").getCompound("tag").contains("unrelated"),
                "Client signing projection leaked unrelated disk data");
        var remote = new ClientManagerBlockEntity(helper.absolutePos(position), manager.getBlockState());
        remote.handleUpdateTag(projection);
        helper.assertTrue(manager.signingSnapshot().equals(remote.signingSnapshot()),
                "Bounded client projection lost verified public signing evidence");

        var tampered = projection.copy();
        tampered.getCompound("disk").getCompound("tag").putString("sfm:program", "CLIENT BTW\n-- changed");
        remote.handleUpdateTag(tampered);
        helper.assertTrue(remote.attestations().isEmpty() && remote.signingSnapshot() == null,
                "Mismatched remote source was repaired into signer authority");
        helper.assertTrue(!remote.storedSource().isEmpty(), "Invalid metadata erased source available for exact review");
        var stale = manager.reviewForSigning(session, player, capabilities, tick).acknowledgement().orElseThrow();
        ItemStack relabelled = manager.disk();
        LabelPositionHolder.empty().add("unused", helper.absolutePos(new BlockPos(2, 0, 2))).save(relabelled);
        manager.setDisk(relabelled);
        helper.assertTrue(manager.signingRevision() > stale.snapshot().revision() && manager.attestations().equals(List.of(signature)),
                "A label edit failed to advance the revision or erased signature history");
        helper.assertTrue(manager.submitSignature(session, player, stale.snapshot().incarnation(), stale.snapshot().revision(),
                stale.challenge(), ProgramAttestationCodec.encode(signature), tick) == ClientManagerSigningState.Status.STALE_REVISION,
                "A relabelled manager accepted a stale reviewed revision");
        helper.succeed();
    }
}
