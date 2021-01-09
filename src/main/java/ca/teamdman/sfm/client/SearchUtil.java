/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package ca.teamdman.sfm.client;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.SFMUtil;
import ca.teamdman.sfm.common.config.Config.Client;
import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;
import net.minecraft.client.util.ITooltipFlag.TooltipFlags;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;
import net.minecraft.util.registry.Registry;
import net.minecraft.util.text.ITextComponent;

public class SearchUtil {

	private static final Multimap<ItemStack, String> cache = Multimaps.synchronizedListMultimap(
		LinkedListMultimap.create()
	);
	private static boolean cacheReady = false;

	public static void buildCacheInBackground() {
		if (cacheReady) {
			return;
		}
		new Thread(() -> {
			long time_no_see = System.currentTimeMillis();
			try {
				buildCache();
			} finally {
				SFM.LOGGER.info(
					SFMUtil.getMarker(SearchUtil.class),
					"Indexed {} items in {} ms",
					cache.keys().size(),
					System.currentTimeMillis() - time_no_see
				);
				cacheReady = true;
			}
		}).start();
	}

	/**
	 * Populate the {@link SearchUtil#cache} object with ItemStacks and their respective tooltips
	 * Note: Tooltips, meaning when you hover over it, including the name Node: The method to get an
	 * ItemStack tooltip is costly, that's the point of this caching operation
	 */
	public static void buildCache() {
		cache.clear();
		getSearchableItems().stream()
			.filter(Objects::nonNull)
			.filter(itemStack -> !itemStack.isEmpty())
			.sorted(getSearchPriorityComparator())
			.forEach(stack -> {
				try {
					// Add just the stack name, so regex anchors play nice
					cache.put(stack, stack.getDisplayName().getString());

					// Add full tooltip text
					cache.put(
						stack,
						stack.getTooltip(null, TooltipFlags.ADVANCED).stream()
							.map(ITextComponent::getString)
							.collect(Collectors.joining(" "))
					);

					// Add oredict
					stack.getItem().getTags().forEach(tag -> cache.put(stack, tag.toString()));
				} catch (Exception ignored) {
				}
			});

	}

	public static List<ItemStack> getSearchableItems() {
		NonNullList<ItemStack> stacks = NonNullList.create();
		Registry.ITEM.stream()
			.filter(Objects::nonNull)
			.filter(i -> i.getCreativeTabs().size() > 0)
			.forEach(i -> {
				try {
					i.fillItemGroup(ItemGroup.SEARCH, stacks);
				} catch (Exception ignored) {

				}
			});
		return stacks;
	}

	private static Comparator<ItemStack> getSearchPriorityComparator() {
		return Comparator.comparingInt(SearchUtil::sortMinecraftFirst)
			.thenComparingInt(SearchUtil::sortShorterNamesFirst)
			.thenComparing(x -> x.getDisplayName().getString());
	}

	private static int sortMinecraftFirst(ItemStack in) {
		return in.getItem().getRegistryName() != null
			&& in.getItem().getRegistryName().getNamespace().equals("minecraft") ? 0 : 1;
	}

	private static int sortShorterNamesFirst(ItemStack in) {
		return in.getDisplayName().getString().length();
	}

	private static void populateStressTest(List<ItemStack> list) {
//		if (Launcher.INSTANCE.blackboard().get(Keys.of("fml.deobfuscatedEnvironment")).isPresent()) {
		Iterator<ItemStack> iter = list.listIterator();
		while (list.size() < 100000) {
			list.add(iter.next());
		}
//		}
	}

	public static Multimap<ItemStack, String> getCache() {
		//		return Collections.unmodifiableMap(cache);
		return cache;
	}

	public static class Query {

		private final Queue<ItemStack> RESULTS = new ConcurrentLinkedQueue<>();
		private boolean running;
		private Thread background;

		/**
		 * Get thread-safe queue of search results. This object is updated in real-time as the
		 * search progresses.
		 *
		 * @return Search results
		 */
		public Queue<ItemStack> getResults() {
			return RESULTS;
		}

		/**
		 * Clear the results, cancels previous searches, and starts a new search.
		 *
		 * @param search Search text/pattern to be used
		 */
		public void start(String search) {
			// Terminate previous searches prematurely
			if (background != null) {
				stop();
				try {
					background.join(); // Wait for previous background search to finish
				} catch (InterruptedException ignored) {

				}
			}

			// Clear previous results
			RESULTS.clear();

			// Start new search
			running = true;
			background = new Thread(() -> {
				Pattern pattern = getPattern(search);
				for (Entry<ItemStack, Collection<String>> entry : getCache().asMap().entrySet()) {
					if (!running) { // Premature thread exit condition
						return;
					}
					if (entry.getValue().stream().anyMatch(v -> pattern.matcher(v).find())) {
						RESULTS.add(entry.getKey());
					}
				}
			});
			background.start();
		}

		public void stop() {
			this.running = false;
		}

		private Pattern getPattern(String search) {
			Pattern basicPattern = Pattern.compile(Pattern.quote(search), Pattern.CASE_INSENSITIVE);
			if (!Client.enableRegexSearch) {
				return basicPattern;
			}
			try {
				return Pattern.compile(search, Pattern.CASE_INSENSITIVE);
			} catch (PatternSyntaxException e) {
				return basicPattern;
			}
		}
	}
}