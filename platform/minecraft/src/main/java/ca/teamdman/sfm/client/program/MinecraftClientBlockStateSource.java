package ca.teamdman.sfm.client.program;

import ca.teamdman.sfm.common.value.SFMValue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/** Reads only replicated client block states, not server capabilities or unopened inventories. */
public final class MinecraftClientBlockStateSource
        implements ClientProgramBlockReadSurface.Source<BlockState> {
    private final ClientLevel level;

    public MinecraftClientBlockStateSource(ClientLevel level) {
        this.level = Objects.requireNonNull(level, "level");
    }

    @Override
    public Object worldIdentity() {
        return level;
    }

    @Override
    public ResourceLocation dimension() {
        return level.dimension().location();
    }

    @Override
    public boolean isActive() {
        return Minecraft.getInstance().level == level;
    }

    @Override
    public boolean isLoaded(BlockPos position) {
        return isActive() && !level.isOutsideBuildHeight(position) && level.hasChunkAt(position);
    }

    @Override
    public Optional<ClientProgramBlockReadSurface.Sample<BlockState>> sample(BlockPos position) {
        if (!isLoaded(position)) {
            return Optional.empty();
        }
        BlockState state = level.getBlockState(position);
        // BlockState is immutable and canonical within a loaded block-state palette.
        return Optional.of(new ClientProgramBlockReadSurface.Sample<>(state, state));
    }

    @Override
    public SFMValue project(BlockState state) {
        return projectState(state);
    }

    static SFMValue projectState(BlockState state) {
        ResourceLocation id = ForgeRegistries.BLOCKS.getKey(state.getBlock());
        if (id == null) {
            throw new IllegalArgumentException("Unregistered client block state");
        }
        Map<String, SFMValue> properties = new TreeMap<>();
        for (Map.Entry<Property<?>, Comparable<?>> entry : state.getValues().entrySet()) {
            properties.put(entry.getKey().getName(), propertyValue(entry.getValue()));
        }
        return SFMValue.object(Map.of(
                "schema", SFMValue.of("sfm:block_state@1"),
                "block", SFMValue.of(id.toString()),
                "properties", SFMValue.object(properties)
        ));
    }

    static SFMValue propertyValue(Object raw) {
        Objects.requireNonNull(raw, "raw block property");
        if (raw instanceof Integer number) return SFMValue.of(number.longValue());
        if (raw instanceof Boolean booleanValue) return SFMValue.of(booleanValue);
        return SFMValue.of(raw.toString());
    }
}
