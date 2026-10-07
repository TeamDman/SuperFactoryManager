package ca.teamdman.sfml.ast;

import ca.teamdman.sfm.common.item.PacketItem;
import ca.teamdman.sfm.common.program.ProgramResourceValue;
import ca.teamdman.sfm.common.program.ProgramValueReference;
import ca.teamdman.sfm.common.registry.registration.SFMResourceTypes;
import ca.teamdman.sfm.common.resourcetype.ResourceType;
import ca.teamdman.sfm.common.value.SFMValuePattern;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;
import java.util.Objects;

/** Additional semantic selection performed by an SFML input statement. */
public sealed interface ProgramInputSelection extends ASTNode permits
        ProgramInputSelection.AnySelection,
        ProgramInputSelection.CapabilitySelection,
        ProgramInputSelection.PatternSelection {
    ProgramInputSelection ANY = new AnySelection();

    boolean matches(ResourceType<?, ?, ?> resourceType, Object stack);

    Object bind(ResourceType<?, ?, ?> resourceType, Object unitStack);

    String toSource();

    record AnySelection() implements ProgramInputSelection {
        @Override
        public boolean matches(ResourceType<?, ?, ?> resourceType, Object stack) {
            return true;
        }

        @Override
        public Object bind(ResourceType<?, ?, ?> resourceType, Object unitStack) {
            return new ProgramResourceValue(resourceType, unitStack);
        }

        @Override
        public String toSource() {
            return "";
        }
    }

    record CapabilitySelection(String capabilityId) implements ProgramInputSelection {
        public CapabilitySelection {
            capabilityId = Objects.requireNonNull(capabilityId).toLowerCase(Locale.ROOT);
            if (!capabilityId.equals("sfm:text")) {
                throw new IllegalArgumentException("Unknown input capability: " + capabilityId);
            }
        }

        @Override
        public boolean matches(ResourceType<?, ?, ?> resourceType, Object stack) {
            return resourceType == SFMResourceTypes.ITEM.get()
                   && stack instanceof ItemStack itemStack
                   && SFMTextResourceAdapters.supports(itemStack);
        }

        @Override
        public Object bind(ResourceType<?, ?, ?> resourceType, Object unitStack) {
            return new ProgramResourceValue(resourceType, unitStack);
        }

        @Override
        public String toSource() {
            return "WITH CAPABILITY " + capabilityId;
        }
    }

    record PatternSelection(String alias, SFMValuePattern pattern) implements ProgramInputSelection {
        public PatternSelection {
            Objects.requireNonNull(alias);
            Objects.requireNonNull(pattern);
        }

        @Override
        public boolean matches(ResourceType<?, ?, ?> resourceType, Object stack) {
            return resourceType == SFMResourceTypes.ITEM.get()
                   && stack instanceof ItemStack itemStack
                   && PacketItem.getValue(itemStack).filter(pattern::matches).isPresent();
        }

        @Override
        public Object bind(ResourceType<?, ?, ?> resourceType, Object unitStack) {
            if (!(unitStack instanceof ItemStack itemStack)) {
                throw new IllegalArgumentException("Pattern-selected input is not an item stack");
            }
            return PacketItem.getValue(itemStack)
                    .filter(pattern::matches)
                    .<Object>map(ProgramValueReference::resolved)
                    .orElseThrow(() -> new IllegalArgumentException("Pattern-selected packet no longer matches " + alias));
        }

        @Override
        public String toSource() {
            return "LIKE " + alias;
        }
    }
}
