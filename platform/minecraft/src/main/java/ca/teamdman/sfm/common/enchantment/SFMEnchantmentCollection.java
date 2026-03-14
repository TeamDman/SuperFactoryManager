package ca.teamdman.sfm.common.enchantment;

import ca.teamdman.sfm.common.registry.SFMWellKnownRegistries;
import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/// A simple container for holding multiple {@link SFMEnchantmentEntry}.
/// This container allows multiple entries with the same {@link SFMEnchantmentKey} and {@link SFMEnchantmentEntry#level()}.
public class SFMEnchantmentCollection implements Collection<SFMEnchantmentEntry> {
    private final List<SFMEnchantmentEntry> inner = new ArrayList<>();

    @Override
    public boolean addAll(@NotNull Collection<? extends SFMEnchantmentEntry> c) {

        return this.inner.addAll(c);
    }

    @Override
    public boolean removeAll(@NotNull Collection<?> c) {

        return false;
    }

    @Override
    public boolean retainAll(@NotNull Collection<?> c) {

        return false;
    }

    @Override
    public void clear() {

    }

    public static SFMEnchantmentCollection empty() {

        return new SFMEnchantmentCollection();
    }


    @MCVersionDependentBehaviour
    public static SFMEnchantmentCollection fromItemStack(
            ItemStack stack,
            SFMEnchantmentCollectionKind kind
    ) {

        SFMEnchantmentCollection rtn = new SFMEnchantmentCollection();
        switch (kind) {
            case HoldingLikeABook -> {

                // Get the list of enchantments from the book
                ListTag enchantedBookEnchantments = EnchantedBookItem.getEnchantments(stack);

                // For each enchantment
                int numEnchantments = enchantedBookEnchantments.size();
                for (int i = 0; i < numEnchantments; i++) {
                    CompoundTag compoundTag = enchantedBookEnchantments.getCompound(i);

                    // Get and parse the ID
                    ResourceLocation id = EnchantmentHelper.getEnchantmentId(compoundTag);
                    if (id == null) {
                        continue;
                    }

                    // Get the enchantment from the ID
                    Enchantment enchantment = SFMWellKnownRegistries.ENCHANTMENTS.get(id);
                    if (enchantment == null) {
                        continue;
                    }

                    // Create the enchantment key
                    SFMEnchantmentKey key = new SFMEnchantmentKey(enchantment);

                    // Get the level of the enchantment
                    int level = EnchantmentHelper.getEnchantmentLevel(compoundTag);

                    // Create the entry
                    SFMEnchantmentEntry entry = new SFMEnchantmentEntry(key, level);

                    // Track the entry
                    rtn.add(entry);
                }

            }
            case EnchantedLikeATool -> {

                // Get the enchantments on the stack
                Map<Enchantment, Integer> enchantments = EnchantmentHelper.getEnchantments(stack);

                // For each (enchantment, level) pair
                for (Map.Entry<Enchantment, Integer> enchantmentEntry : enchantments.entrySet()) {

                    // Get the enchantment
                    Enchantment enchantment = enchantmentEntry.getKey();

                    // Create the enchantment key
                    SFMEnchantmentKey key = new SFMEnchantmentKey(enchantment);

                    // Get the level of the enchantment
                    Integer level = enchantmentEntry.getValue();

                    // Create the entry
                    SFMEnchantmentEntry entry = new SFMEnchantmentEntry(key, level);

                    // Track the entry
                    rtn.add(entry);
                }
            }
        }
        return rtn;
    }

    @Override
    public boolean isEmpty() {

        return this.inner.isEmpty();
    }

    @Override
    public boolean contains(Object o) {

        return this.inner.contains(o);
    }

    @Override
    public boolean add(SFMEnchantmentEntry entry) {

        return this.inner.add(entry);
    }

    @Override
    public boolean remove(Object o) {

        return this.inner.remove(o);
    }

    @SuppressWarnings("SlowListContainsAll")
    @Override
    public boolean containsAll(Collection<?> c) {

        return this.inner.containsAll(c);
    }

    @Override
    public int size() {

        return this.inner.size();
    }

    @Override
    public Iterator<SFMEnchantmentEntry> iterator() {

        return this.inner.iterator();
    }

    @Override
    public Object[] toArray() {

        return this.inner.toArray();
    }

    @Override
    public <T> T[] toArray(T[] a) {

        return this.inner.toArray(a);
    }

    /// Convert this collection to a dictionary where the last entry for each {@link SFMEnchantmentKey} is the winner.
    public Map<SFMEnchantmentKey, SFMEnchantmentEntry> canonicalize() {
        HashMap<SFMEnchantmentKey, SFMEnchantmentEntry> rtn = new HashMap<>();
        for (SFMEnchantmentEntry enchantment : this) {
            rtn.put(enchantment.key(), enchantment);
        }
        return rtn;
    }

    /// Clobber enchantments into an {@link ItemStack}
    public void write(
            ItemStack stack,
            SFMEnchantmentCollectionKind kind
    ) {

        switch (kind) {
            case HoldingLikeABook -> {

                // Clear existing enchantments
                stack.getOrCreateTag().remove("StoredEnchantments");

                // Append the enchantments to the book
                for (SFMEnchantmentEntry enchantment : this.canonicalize().values()) {
                    EnchantedBookItem.addEnchantment(
                            stack,
                            new EnchantmentInstance(
                                    enchantment.key().inner(),
                                    enchantment.level()
                            )
                    );
                }
            }
            case EnchantedLikeATool -> {

                // Create a new (enchantment, level) lookup
                Map<Enchantment, Integer> enchantments = new HashMap<>();

                for (SFMEnchantmentEntry enchantment : this.canonicalize().values()) {
                    // Add our enchantments
                    enchantments.put(
                            enchantment.key().inner(),
                            enchantment.level()
                    );
                }

                // Clobber the enchantments back to the stack
                EnchantmentHelper.setEnchantments(enchantments, stack);
            }
        }

    }

    public int getLevel(SFMEnchantmentKey enchant) {

        for (SFMEnchantmentEntry entry : this) {
            if (entry.key().equals(enchant)) {
                return entry.level();
            }
        }
        return 0;
    }

    @Override
    public final boolean equals(Object o) {

        if (!(o instanceof SFMEnchantmentCollection that)) return false;

        return inner.equals(that.inner);
    }

    @Override
    public int hashCode() {

        return inner.hashCode();
    }

    public ItemStack createEnchantedBook() {
        ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
        this.write(book, SFMEnchantmentCollectionKind.HoldingLikeABook);
        return book;
    }

}
