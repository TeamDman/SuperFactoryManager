package ca.teamdman.sfm.common.program.signature;

import ca.teamdman.sfm.common.blockentity.ClientManagerProgramProjection;
import ca.teamdman.sfml.ast.Program;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.Tag;

import java.nio.charset.StandardCharsets;
import java.util.*;

/** Exact acknowledged LF source and ALL bounded labels, not only the runtime's referenced-label consent scope. */
public record ClientManagerSigningBody(String source, Map<String, List<Long>> labels) {
    public ClientManagerSigningBody {
        Objects.requireNonNull(source);
        Objects.requireNonNull(labels);
        source = new String(ProgramSignatureDescriptor.normalizedSourceBytes(source), StandardCharsets.UTF_8);
        ProgramAttestationCodec.require(source.length() <= Program.MAX_PROGRAM_LENGTH);
        ProgramAttestationCodec.require(labels.size() <= ClientManagerProgramProjection.MAX_LABELS);
        var copied = new TreeMap<String, List<Long>>();
        int positions = 0;
        for (var entry : labels.entrySet()) {
            String name = Objects.requireNonNull(entry.getKey());
            ProgramAttestationCodec.require(!name.isBlank() && name.length() <= Program.MAX_LABEL_LENGTH);
            ProgramAttestationCodec.strictBytes(name, 1024);
            List<Long> values = Objects.requireNonNull(entry.getValue());
            ProgramAttestationCodec.require(values.size() <= ClientManagerProgramProjection.MAX_POSITIONS - positions);
            positions += values.size();
            copied.put(name, values.stream().map(Objects::requireNonNull).sorted().distinct().toList());
        }
        labels = Collections.unmodifiableMap(copied);
    }

    public static ClientManagerSigningBody fromDiskTag(CompoundTag diskTag) {
        CompoundTag projection = ClientManagerProgramProjection.project(diskTag)
                .orElseThrow(() -> new IllegalArgumentException("Client Manager program is not projectable"));
        var labels = new TreeMap<String, List<Long>>();
        var labelTag = projection.getCompound("sfm:labels");
        for (String name : labelTag.getAllKeys()) {
            ListTag positions = labelTag.getList(name, Tag.TAG_LONG);
            labels.put(name, positions.stream().map(LongTag.class::cast).map(LongTag::getAsLong).toList());
        }
        return new ClientManagerSigningBody(projection.getString("sfm:program"), labels);
    }

    public CompoundTag toDiskProjection() {
        CompoundTag result = new CompoundTag();
        result.putString("sfm:program", source);
        CompoundTag labelTag = new CompoundTag();
        labels.forEach((name, values) -> {
            ListTag positions = new ListTag();
            values.forEach(value -> positions.add(LongTag.valueOf(value)));
            labelTag.put(name, positions);
        });
        result.put("sfm:labels", labelTag);
        return result;
    }

    public String canonicalBindings() {
        StringBuilder result = new StringBuilder();
        labels.forEach((name, values) -> {
            result.append(name.length()).append(':').append(name).append('=').append(values.size()).append(':');
            values.forEach(value -> result.append(Long.toHexString(value)).append(','));
            result.append(';');
        });
        return result.toString();
    }

    public String sourceSha256() { return ProgramSignatureDescriptor.sha256(source.getBytes(StandardCharsets.UTF_8)); }
    public String bindingSha256() { return ProgramSignatureDescriptor.sha256(canonicalBindings().getBytes(StandardCharsets.UTF_8)); }
    public int chargedBytes() {
        return source.getBytes(StandardCharsets.UTF_8).length + canonicalBindings().getBytes(StandardCharsets.UTF_8).length + 128;
    }
}
