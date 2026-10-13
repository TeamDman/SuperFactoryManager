package ca.teamdman.sfml.ast;

import ca.teamdman.sfm.common.resourcetype.ResourceType;
import ca.teamdman.sfm.common.util.AtomicIdExtension;
import ca.teamdman.sfm.common.util.FilterCachingUtils;
import ca.teamdman.sfm.common.util.IFilterCacher;
import net.minecraft.core.Registry;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.function.IntPredicate;

public final class With implements WithClause, ToStringPretty, IFilterCacher {
    public static final With ALWAYS_TRUE = new With(
            new WithAlwaysTrue(),
            WithMode.WITH
    );
    private final WithClause condition;
    private final WithMode mode;
    private @Nullable IntPredicate predicate;

    public With(
            WithClause condition,
            WithMode mode
    ) {
        this.condition = condition;
        this.mode = mode;
    }

    @Override
    public <STACK> boolean matchesStack(
            ResourceType<STACK, ?, ?> resourceType,
            STACK stack
    ) {
        if (predicate == null) {
            var whitelist = this.mode == With.WithMode.WITH;
            if (stack instanceof ItemStack) {
                //noinspection unchecked
                predicate = FilterCachingUtils.makePredicate(this, e -> condition.matchesStack(resourceType, (STACK) e.value().getDefaultInstance()) == whitelist, Registry.ITEM.holders());
                FilterCachingUtils.registerExtension(this);
            } else if (stack instanceof FluidStack) {
                //noinspection unchecked
                predicate = FilterCachingUtils.makePredicate(this, e -> condition.matchesStack(resourceType, (STACK) new FluidStack(e.value(), 1000)) == whitelist, Registry.FLUID.holders());
                FilterCachingUtils.registerExtension(this);
            }
        }

        if (predicate != null && stack instanceof AtomicIdExtension s) {
            return predicate.test(s.sfm$getAtomicId());
        }

        boolean matches = condition.matchesStack(resourceType, stack);
        return switch (mode) {
            case WITH -> matches;
            case WITHOUT -> !matches;
        };
    }

    @Override
    public String toString() {
        return switch (mode) {
            case WITH -> "WITH " + condition.toStringPretty();
            case WITHOUT -> "WITHOUT " + condition.toStringPretty();
        };
    }

    public WithClause condition() {
        return condition;
    }

    public WithMode mode() {
        return mode;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        } else if (obj instanceof With that) {
            return Objects.equals(this.condition, that.condition) && Objects.equals(this.mode, that.mode);
        } else {
            return true;
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(condition, mode);
    }

    public enum WithMode {
        WITH,
        WITHOUT
    }

    @Override
    public void setPredicate(IntPredicate predicate) {
        this.predicate = predicate;
    }

    @Override
    @Nullable
    public IntPredicate getPredicate() {
        return predicate;
    }
}
