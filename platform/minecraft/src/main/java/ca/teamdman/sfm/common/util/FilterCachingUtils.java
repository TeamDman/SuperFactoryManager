package ca.teamdman.sfm.common.util;

import ca.teamdman.sfm.SFM;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntArrays;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.core.Holder;
import net.minecraft.util.Unit;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.apache.commons.lang3.ArrayUtils;

import java.util.BitSet;
import java.util.WeakHashMap;
import java.util.function.IntPredicate;
import java.util.function.Predicate;
import java.util.stream.Stream;

@Mod.EventBusSubscriber(modid = SFM.MOD_ID)
public class FilterCachingUtils {
    private static final Object2ObjectOpenHashMap<Object, IntPredicate> PREDICATE_CACHE = new Object2ObjectOpenHashMap<>();
    private static final WeakHashMap<IFilterCacher, Unit> HOLDERS = new WeakHashMap<>();

    public static final AlwaysTrue ALWAYS_TRUE = new AlwaysTrue();
    public static final AlwaysFalse ALWAYS_FALSE = new AlwaysFalse();

    public static class AlwaysTrue implements IntPredicate {
        @Override
        public boolean test(int id) {
            return true;
        }
    }

    public static class AlwaysFalse implements IntPredicate {
        @Override
        public boolean test(int id) {
            return false;
        }
    }

    public record BitSetMatcher(BitSet bitset, int start) implements IntPredicate {
        @Override
        public boolean test(int id) {
            if (id < start) {
                return false;
            }

            return bitset.get(id - start);
        }
    }

    public record InvertedBitSetMatcher(BitSet bitset, int start) implements IntPredicate {
        @Override
        public boolean test(int id) {
            if (id < start) {
                return false;
            }

            return !bitset.get(id - start);
        }
    }

    public record ArrayMatcher(int[] array) implements IntPredicate {
        @Override
        public boolean test(int id) {
            return array.length <= 8 ? ArrayUtils.contains(array, id) : id >= array[0] && id <= array[array.length - 1] && IntArrays.binarySearch(array, id) >= 0;
        }
    }

    public record InvertedArrayMatcher(int[] array) implements IntPredicate {
        @Override
        public boolean test(int id) {
            return array.length <= 8 ? !ArrayUtils.contains(array, id) : id < array[0] || id > array[array.length - 1] || IntArrays.binarySearch(array, id) < 0;
        }
    }

    public record SingleMatcher(int value) implements IntPredicate {
        @Override
        public boolean test(int id) {
            return id == value;
        }
    }

    public record InvertedSingleMatcher(int value) implements IntPredicate {
        @Override
        public boolean test(int id) {
            return id != value;
        }
    }

    public static <Ext> IntPredicate makePredicate(Object key, Predicate<Holder.Reference<Ext>> entryPredicate, Stream<Holder.Reference<Ext>> registry) {
        return PREDICATE_CACHE.computeIfAbsent(key, ignored -> {
            var trueList = new IntArrayList();
            var falseList = new IntArrayList();

            registry.forEach(entry -> {
                var id = ((AtomicIdExtension) entry.value()).sfm$getAtomicId();
                if (entryPredicate.test(entry)) {
                    trueList.push(id);
                } else {
                    falseList.push(id);
                }
            });

            if (falseList.isEmpty()) {
                return ALWAYS_TRUE;
            }
            if (trueList.isEmpty()) {
                return ALWAYS_FALSE;
            }
            if (trueList.size() == 1) {
                return new SingleMatcher(trueList.getInt(0));
            }
            if (falseList.size() == 1) {
                return new InvertedSingleMatcher(trueList.getInt(0));
            }

            trueList.trim();
            falseList.trim();

            var trues = trueList.elements();
            var falses = falseList.elements();
            IntArrays.radixSort(trues);
            IntArrays.radixSort(falses);

            var truesSpan = Integer.MAX_VALUE;
            if (trues.length != 0) {
                truesSpan = trues[trues.length - 1] - trues[0];
            }
            var falsesSpan = Integer.MAX_VALUE;
            if (falses.length != 0) {
                falsesSpan = falses[falses.length - 1] - falses[0];
            }

            if (truesSpan <= falsesSpan) {
                if (truesSpan > trues.length * Integer.SIZE) {
                    // Sparse
                    return new ArrayMatcher(trues);
                }

                var bitset = new BitSet(truesSpan);
                var start = trues[0];
                for (var n : trues) {
                    bitset.set(n - start);
                }

                return new BitSetMatcher(bitset, start);
            } else {
                if (falsesSpan > falses.length * Integer.SIZE) {
                    // Sparse
                    return new InvertedArrayMatcher(falses);
                }

                var bitset = new BitSet(falsesSpan);
                var start = trues[0];
                for (var n : falses) {
                    bitset.set(n - start);
                }

                return new InvertedBitSetMatcher(bitset, start);
            }
        });
    }

    public static void registerExtension(IFilterCacher extension) {
        HOLDERS.put(extension, Unit.INSTANCE);
    }

    @SubscribeEvent
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        if (event.getPlayer() != null) {
            return;
        }

        if (HOLDERS.size() < PREDICATE_CACHE.size()) {
            SFM.LOGGER.warn("FilterCachers under-registered, expected at least {} but only found {}", PREDICATE_CACHE.size(), HOLDERS.size());
        }

        for (var ext : HOLDERS.keySet()) {
            ext.setPredicate(null);
        }
        HOLDERS.clear();
        PREDICATE_CACHE.clear();
    }
}
