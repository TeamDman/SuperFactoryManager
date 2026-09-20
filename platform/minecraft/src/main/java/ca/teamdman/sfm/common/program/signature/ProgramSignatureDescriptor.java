package ca.teamdman.sfm.common.program.signature;

import net.minecraft.resources.ResourceLocation;

import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/** Versioned, portable author statement. Location and local trust are separate from authorship. */
public record ProgramSignatureDescriptor(String sourceSha256, String runtime,
                                         List<ResourceLocation> capabilities) {
    public static final String SCHEMA = "sfm:program_descriptor@1";
    public static final String SOURCE_NORMALIZATION = "sfm:utf8_lf@1";
    public static final int MAX_SOURCE_BYTES = 64 * 1024;
    public static final int MAX_CAPABILITIES = 64;

    public ProgramSignatureDescriptor {
        Objects.requireNonNull(sourceSha256);
        Objects.requireNonNull(runtime);
        Objects.requireNonNull(capabilities);
        if (!sourceSha256.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("Invalid source digest");
        if (runtime.length() > 128 || !runtime.matches("[a-z0-9_.-]+:[a-z0-9/._-]+@[1-9][0-9]*")) {
            throw new IllegalArgumentException("Invalid runtime identity");
        }
        if (capabilities.isEmpty() || capabilities.size() > MAX_CAPABILITIES) {
            throw new IllegalArgumentException("Invalid capability count");
        }
        capabilities = capabilities.stream().map(Objects::requireNonNull)
                .sorted(java.util.Comparator.comparing(ResourceLocation::toString)).distinct().toList();
        if (capabilities.stream().anyMatch(id -> id.toString().length() > 256)
            || capabilities.stream().noneMatch(id -> id.toString().equals("sfm:client_program/execute"))) {
            throw new IllegalArgumentException("Invalid client program capability manifest");
        }
    }

    public static ProgramSignatureDescriptor fromSource(String source, String runtime,
                                                         Collection<ResourceLocation> capabilities) {
        return new ProgramSignatureDescriptor(sha256(normalizedSourceBytes(source)), runtime, List.copyOf(capabilities));
    }

    /** Only line endings normalize. Comments, whitespace, case and Unicode composition remain significant. */
    public static byte[] normalizedSourceBytes(String source) {
        Objects.requireNonNull(source);
        if (source.length() > MAX_SOURCE_BYTES) throw new IllegalArgumentException("Program source exceeds signature budget");
        String normalized = source.replace("\r\n", "\n").replace('\r', '\n');
        try {
            var encoded = StandardCharsets.UTF_8.newEncoder()
                    .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                    .encode(CharBuffer.wrap(normalized));
            if (encoded.remaining() > MAX_SOURCE_BYTES) throw new IllegalArgumentException("Program source exceeds signature budget");
            byte[] result = new byte[encoded.remaining()];
            encoded.get(result);
            return result;
        } catch (CharacterCodingException invalidUnicode) {
            throw new IllegalArgumentException("Program source is not valid Unicode", invalidUnicode);
        }
    }

    /** Fixed field order and validated ASCII IDs make this exact JSON representation canonical for version 1. */
    public byte[] canonicalBytes() {
        String manifest = capabilities.stream().map(id -> "\"" + id + "\"")
                .collect(java.util.stream.Collectors.joining(","));
        return ("{\"schema\":\"" + SCHEMA + "\",\"sourceNormalization\":\"" + SOURCE_NORMALIZATION
                + "\",\"sourceSha256\":\"" + sourceSha256 + "\",\"runtime\":\"" + runtime
                + "\",\"capabilities\":[" + manifest + "]}").getBytes(StandardCharsets.UTF_8);
    }

    public String descriptorSha256() { return sha256(canonicalBytes()); }

    public static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException("SHA-256 is unavailable", unavailable);
        }
    }
}
