package ca.teamdman.sfm.common.program;

import ca.teamdman.sfm.common.resourcetype.ResourceType;
import ca.teamdman.sfml.ast.Label;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
{% if features.packet_computation %}
import org.jetbrains.annotations.Nullable;
{% else %}
{% endif %}

public interface LimitedSlot<STACK, ITEM, CAP> {
    ResourceType<STACK, ITEM, CAP> getType();

    CAP getHandler();

{% if features.packet_computation %}
    @Nullable BlockPos getPos();
{% else %}
    BlockPos getPos();
{% endif %}

{% if features.packet_computation %}
    @Nullable Label getLabel();
{% else %}
    Label getLabel();
{% endif %}

{% if features.packet_computation %}
    @Nullable Direction getDirection();
{% else %}
    Direction getDirection();
{% endif %}

    int getSlot();
}
