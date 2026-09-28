package ca.teamdman.sfm.common.enchantment;

import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.core.Holder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

/// A simple container for holding multiple {@link SFMEnchantmentEntry}.
/// This container allows multiple entries with the same {@link SFMEnchantmentKey} and {@link SFMEnchantmentEntry#level()}.
public class SFMEnchantmentCollection implements Collection<SFMEnchantmentEntry> {
    private final List<SFMEnchantmentEntry> inner = new ArrayList<>();

    public SFMEnchantmentCollection() {

    }

    public SFMEnchantmentCollection(@Nullable ItemEnchantments itemEnchantments) {
        if (itemEnchantments == null) {
            return;
        }

        for (Object2IntMap.Entry<Holder<Enchantment>> enchantment : itemEnchantments.entrySet()) {
            SFMEnchantmentKey key = new SFMEnchantmentKey(enchantment.getKey());
            int level = enchantment.getIntValue();
            this.add(new SFMEnchantmentEntry(key, level));
        }
    }

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

        ItemEnchantments itemEnchantments = stack.get(kind.componentType());
        return new SFMEnchantmentCollection(itemEnchantments);
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
    public ItemEnchantments canonicalize() {
        ItemEnchantments.Mutable rtn = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        for (SFMEnchantmentEntry enchantment : this) {
            rtn.set(enchantment.key().inner(), enchantment.level());
        }
        return rtn.toImmutable();
    }

    /// Clobber enchantments into an {@link ItemStack}
    public void write(
            ItemStack stack,
            SFMEnchantmentCollectionKind kind
    ) {

        stack.set(kind.componentType(), this.canonicalize());
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
