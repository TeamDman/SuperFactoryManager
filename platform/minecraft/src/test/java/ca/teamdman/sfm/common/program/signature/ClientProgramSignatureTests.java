package ca.teamdman.sfm.common.program.signature;

import ca.teamdman.sfm.client.program.ClientProgramConsentGate;
import ca.teamdman.sfm.client.program.ClientProgramIdentity;
import ca.teamdman.sfm.client.program.ClientProgramSignerTrustGrant;
import ca.teamdman.sfm.client.program.ClientProgramWorldIdentity;
import ca.teamdman.sfml.ast.ProgramExecutionSide;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ClientProgramSignatureTests {
    private static final ResourceLocation EXECUTE = ClientProgramConsentGate.EXECUTE;
    private static final ResourceLocation RENDER = ClientProgramConsentGate.RENDER;
    private static final ResourceLocation SEND = new ResourceLocation("sfm", "packet/send");
    private static final Set<ResourceLocation> CAPS = Set.of(EXECUTE, RENDER);
    private static final String RUNTIME = ClientProgramIdentity.CLIENT_MANAGER_RUNTIME;
    private static final String SOURCE = "CLIENT BTW\n-- authored source\n";
    private static final ClientProgramWorldIdentity WORLD = ClientProgramWorldIdentity.integrated(
            UUID.fromString("0eb7b58f-0b9b-4da5-aa3c-6aa773b8119c"));
    private static final ResourceLocation DIMENSION = new ResourceLocation("minecraft", "overworld");

    private static KeyPair key() throws Exception {
        return KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
    }

    private static ProgramSignatureDescriptor descriptor(String source) {
        return ProgramSignatureDescriptor.fromSource(source, RUNTIME, CAPS);
    }

    private static ClientProgramIdentity identity(String source, String bindings, Set<ResourceLocation> capabilities) {
        return ClientProgramIdentity.fromStoredSourceAndBindings(source, bindings, ProgramExecutionSide.CLIENT,
                WORLD, DIMENSION, new BlockPos(1, 64, 2), RUNTIME, capabilities);
    }

    @Test
    void canonicalDescriptorNormalizesOnlyLineEndingsAndOrdersCapabilities() {
        var descriptor = descriptor(SOURCE);
        assertEquals(descriptor, descriptor(SOURCE.replace("\n", "\r\n")));
        assertEquals(descriptor, descriptor(SOURCE.replace("\n", "\r")));
        assertEquals(descriptor, ProgramSignatureDescriptor.fromSource(SOURCE, RUNTIME, List.of(RENDER, EXECUTE, RENDER)));
        assertNotEquals(descriptor, descriptor(SOURCE + " "));
        assertNotEquals(descriptor, descriptor(SOURCE.replace("authored", "changed")));
        assertNotEquals(descriptor, ProgramSignatureDescriptor.fromSource(SOURCE, "sfm:client_manager@2", CAPS));
        assertNotEquals(descriptor, ProgramSignatureDescriptor.fromSource(SOURCE, RUNTIME, Set.of(EXECUTE, RENDER, SEND)));
        String json = new String(descriptor.canonicalBytes(), StandardCharsets.UTF_8);
        assertTrue(json.startsWith("{\"schema\":\"sfm:program_descriptor@1\",\"sourceNormalization\":\"sfm:utf8_lf@1\""));
        assertTrue(json.endsWith("\"capabilities\":[\"sfm:client_program/execute\",\"sfm:touch_display/render\"]}"));
        byte[] copy = descriptor.canonicalBytes();
        copy[0] = 0;
        assertEquals('{', descriptor.canonicalBytes()[0]);
    }

    @Test
    void rejectsMalformedUnicodeAndOversizedOrUndeclaredMetadata() {
        assertThrows(IllegalArgumentException.class, () -> descriptor("\ud800"));
        assertThrows(IllegalArgumentException.class, () -> descriptor("x".repeat(65_537)));
        assertThrows(IllegalArgumentException.class, () -> descriptor("\u20ac".repeat(30_000)));
        assertThrows(IllegalArgumentException.class,
                () -> ProgramSignatureDescriptor.fromSource(SOURCE, RUNTIME, Set.of(RENDER)));
        assertThrows(IllegalArgumentException.class,
                () -> new ProgramSignatureDescriptor("0".repeat(64), "sfm:invalid\"@1", List.of(EXECUTE)));
        assertThrows(IllegalArgumentException.class,
                () -> ProgramSignatureDescriptor.fromSource(SOURCE, RUNTIME, java.util.Collections.nCopies(65, EXECUTE)));
    }

    @Test
    void independentSignaturesSurviveAppendButNotBodyOrManifestChanges() throws Exception {
        KeyPair alice = key();
        KeyPair bob = key();
        var original = descriptor(SOURCE);
        var changed = descriptor(SOURCE + "-- Bob changed the body\n");
        var aliceA = ProgramAttestation.sign(original, alice);
        var bobA = ProgramAttestation.sign(original, bob);
        var bobB = ProgramAttestation.sign(changed, bob);
        var history = new ProgramAttestationHistory();
        assertTrue(history.append(aliceA));
        assertTrue(history.append(bobA));
        assertFalse(history.append(aliceA));
        assertEquals(List.of(aliceA, bobA), history.active(original));
        assertTrue(history.active(changed).isEmpty());
        assertTrue(history.append(bobB));
        assertEquals(List.of(bobB), history.active(changed));
        assertEquals(List.of(aliceA, bobA), history.active(original), "Exact source reversion restores matching evidence");
        assertFalse(aliceA.verifies(ProgramSignatureDescriptor.fromSource(SOURCE, RUNTIME, Set.of(EXECUTE, RENDER, SEND))));
        assertNotEquals(aliceA.fingerprint(), bobA.fingerprint());
        assertThrows(UnsupportedOperationException.class, () -> history.snapshot().clear());
    }

    @Test
    void rejectsForgedSignaturesNoncanonicalEncodingAndMismatchedKeys() throws Exception {
        KeyPair alice = key();
        KeyPair bob = key();
        var original = descriptor(SOURCE);
        var valid = ProgramAttestation.sign(original, alice);
        byte[] forgedBytes = Base64.getDecoder().decode(valid.signature());
        forgedBytes[0] ^= 1;
        var forged = new ProgramAttestation(original, valid.publicKey(), Base64.getEncoder().encodeToString(forgedBytes));
        assertFalse(forged.verifies(original));
        assertThrows(IllegalArgumentException.class, () -> new ProgramAttestationHistory().append(forged));
        assertThrows(IllegalArgumentException.class,
                () -> new ProgramAttestation(original, valid.publicKey(), valid.signature().replace("=", "")));
        assertThrows(IllegalArgumentException.class,
                () -> new ProgramAttestation(original, "A".repeat(1000), valid.signature()));
        assertThrows(IllegalArgumentException.class,
                () -> ProgramAttestation.sign(original, new KeyPair(alice.getPublic(), bob.getPrivate())));
        var rewrapped = new ProgramAttestation(descriptor(SOURCE + "-- forged body"), valid.publicKey(), valid.signature());
        assertFalse(rewrapped.verifies(rewrapped.descriptor()));
    }

    @Test
    void signatureHistoryCapacityCannotEvictEarlierTrustedEvidence() throws Exception {
        KeyPair alice = key();
        var history = new ProgramAttestationHistory();
        var first = ProgramAttestation.sign(descriptor(SOURCE), alice);
        history.append(first);
        for (int i = 1; i < ProgramAttestationHistory.MAX_ATTESTATIONS; i++) {
            history.append(ProgramAttestation.sign(descriptor(SOURCE + "-- revision " + i), alice));
        }
        assertThrows(IllegalStateException.class,
                () -> history.append(ProgramAttestation.sign(descriptor(SOURCE + "-- overflow"), alice)));
        assertEquals(List.of(first), history.active(descriptor(SOURCE)));
        assertFalse(history.append(first), "Re-adding existing evidence needs no new storage slot");
    }

    @Test
    void scopedSignerTrustMatchesAliceSubsetButCannotBorrowBobOrChangedScope() throws Exception {
        KeyPair alice = key();
        KeyPair bob = key();
        ClientProgramIdentity identity = identity(SOURCE, "display=1", CAPS);
        var aliceA = ProgramAttestation.sign(descriptor(SOURCE), alice);
        var bobA = ProgramAttestation.sign(descriptor(SOURCE), bob);
        var grant = new ClientProgramSignerTrustGrant(aliceA.fingerprint(), RUNTIME, WORLD, DIMENSION,
                identity.managerPosition(), identity.bindingSha256(), CAPS, 10_000L);
        assertTrue(grant.matches(identity, SOURCE, RENDER, List.of(aliceA), 100));
        assertTrue(grant.matches(identity, SOURCE, RENDER, List.of(bobA, aliceA), 100), "Bob cannot veto Alice by appending");
        assertFalse(grant.matches(identity, SOURCE, RENDER, List.of(bobA), 100));
        assertFalse(grant.matches(identity, SOURCE, RENDER, List.of(aliceA), 10_000));
        assertFalse(grant.matches(identity, SOURCE, SEND, List.of(aliceA), 100));
        assertFalse(grant.matches(identity(SOURCE, "display=2", CAPS), SOURCE, RENDER, List.of(aliceA), 100));
        assertFalse(grant.matches(identity(SOURCE, "display=1", Set.of(EXECUTE, RENDER, SEND)), SOURCE, RENDER,
                List.of(ProgramAttestation.sign(ProgramSignatureDescriptor.fromSource(SOURCE, RUNTIME,
                        Set.of(EXECUTE, RENDER, SEND)), alice)), 100));
        var changedLocation = ClientProgramIdentity.fromStoredSourceAndBindings(SOURCE, "display=1",
                ProgramExecutionSide.CLIENT, WORLD, DIMENSION, new BlockPos(2, 64, 2), RUNTIME, CAPS);
        assertFalse(grant.matches(changedLocation, SOURCE, RENDER, List.of(aliceA), 100));
        assertFalse(grant.matches(identity, SOURCE + " ", RENDER, List.of(aliceA), 100), "Presented source must match exact consent identity");
        String aliceRevisionB = SOURCE + "-- Alice revision B";
        assertTrue(grant.matches(identity(aliceRevisionB, "display=1", CAPS), aliceRevisionB, RENDER,
                List.of(ProgramAttestation.sign(descriptor(aliceRevisionB), alice)), 100));
        var smaller = identity(SOURCE, "display=1", Set.of(EXECUTE));
        assertTrue(grant.matches(smaller, SOURCE, EXECUTE,
                List.of(ProgramAttestation.sign(ProgramSignatureDescriptor.fromSource(SOURCE, RUNTIME, Set.of(EXECUTE)), alice)), 100));
    }

    @Test
    void signerTrustKeepsExactWorldAndBindingScopeEvenWhenPortableSourceMatches() throws Exception {
        var attestation = ProgramAttestation.sign(descriptor(SOURCE), key());
        var identity = identity(SOURCE, "display=1", CAPS);
        var mutable = new BlockPos.MutableBlockPos(1, 64, 2);
        var grant = new ClientProgramSignerTrustGrant(attestation.fingerprint(), RUNTIME, WORLD, DIMENSION,
                mutable, identity.bindingSha256(), CAPS, null);
        mutable.set(99, 99, 99);
        assertTrue(grant.matches(identity, SOURCE, RENDER, List.of(attestation), Long.MAX_VALUE));
        var elsewhere = ClientProgramIdentity.fromStoredSourceAndBindings(SOURCE, "display=1", ProgramExecutionSide.CLIENT,
                ClientProgramWorldIdentity.integrated(UUID.randomUUID()), DIMENSION, identity.managerPosition(), RUNTIME, CAPS);
        assertFalse(grant.matches(elsewhere, SOURCE, RENDER, List.of(attestation), 100));
        var otherDimension = ClientProgramIdentity.fromStoredSourceAndBindings(SOURCE, "display=1", ProgramExecutionSide.CLIENT,
                WORLD, new ResourceLocation("minecraft", "the_nether"), identity.managerPosition(), RUNTIME, CAPS);
        assertFalse(grant.matches(otherDimension, SOURCE, RENDER, List.of(attestation), 100));
        String windowsSource = SOURCE.replace("\n", "\r\n");
        var windowsIdentity = identity(windowsSource, "display=1", CAPS);
        assertNotEquals(identity.sourceSha256(), windowsIdentity.sourceSha256());
        assertTrue(grant.matches(windowsIdentity, windowsSource, RENDER, List.of(attestation), 100));
        assertFalse(grant.matches(identity, SOURCE, RENDER,
                java.util.Collections.nCopies(33, attestation), 100));
        assertThrows(UnsupportedOperationException.class, () -> grant.allowedCapabilities().clear());
    }
}
