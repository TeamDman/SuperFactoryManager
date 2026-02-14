package vswe.superfactory.util;

import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import mezz.jei.plugins.jei.JEIInternalPlugin;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.EnumCreatureAttribute;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.NonNullList;
import net.minecraft.util.text.translation.I18n;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.oredict.OreDictionary;

import javax.annotation.Nullable;
import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.IntStream;
import java.util.stream.StreamSupport;

import static net.minecraft.item.ItemStack.DECIMALFORMAT;

/**
 * A class used to cache the concatenated Tooltip string representation of items for searching performance improvements
 */
public class SearchUtil {
	private static final Multimap<ItemStack, String> cache = Multimaps.synchronizedListMultimap(LinkedListMultimap.create());

	/**
	 * Populate the {@link SearchUtil#cache} object with ItemStacks and their respective tooltips
	 * Note: Tooltips, meaning when you hover over it, including the name
	 * Node: The method to get an ItemStack tooltip is costly, that's the point of this caching operation
	 */
	public static void buildCache() {
		new Thread(() -> {
			long time_no_see = System.currentTimeMillis();
			try {
				NonNullList<ItemStack> stacks = NonNullList.create();

				if (Loader.isModLoaded("jei") && JEIInternalPlugin.ingredientRegistry != null) {
					stacks.addAll(JEIInternalPlugin.ingredientRegistry.getAllIngredients(ItemStack.class));
				} else {
					StreamSupport.stream(Item.REGISTRY.spliterator(), false)
							.filter(Objects::nonNull)
							.filter(i -> i.getCreativeTab() != null)
							.forEach(i -> {
								try {
									i.getSubItems(CreativeTabs.SEARCH, stacks);
								} catch (Exception ignored) {

								}
							});
				}

				if (Launch.blackboard.get("fml.deobfuscatedEnvironment") != null) {
					Iterator<ItemStack> iter = stacks.listIterator();
					while (stacks.size() < 100000)
						stacks.add(iter.next());
				}

				cache.clear();
				stacks.stream()
						.filter(Objects::nonNull)
						.filter(itemStack -> !itemStack.isEmpty())
						.sorted(Comparator.<ItemStack>comparingInt(s -> s.getItem().getRegistryName() != null && s.getItem().getRegistryName().getNamespace().equals("minecraft") ? 0 : 1)
								.thenComparingInt(s -> s.getDisplayName().length())
								.thenComparing(ItemStack::getDisplayName))
						.forEach(stack -> {
							try {
								// Add just the stack name, so regex anchors play nice
								cache.put(stack, stack.getDisplayName());

								// Add full tooltip text
								cache.put(stack, getTooltip(stack, null));

								// Add oredict
								int[] oreDict = OreDictionary.getOreIDs(stack);
								for (int i = 0; i < oreDict.length; i++) {
									cache.put(stack, OreDictionary.getOreName(oreDict[i]));
								}
							} catch (Exception ignored) {
							}
						});
				System.out.println("[SFM] Indexed " + stacks.size() + " items in " + (System.currentTimeMillis() - time_no_see) + "ms.");

			} catch (Exception ignored) {
				cache.put(ItemStack.EMPTY, ""); // Make sure cache isn't empty in case of errors
			}
		}).start();
	}

	public static Multimap<ItemStack, String> getCache() {
		//		return Collections.unmodifiableMap(cache);
		return cache;
	}

	private static final List<Query> queries = new ArrayList<>();

	/**
	 * Finds stacks matching the search search
	 *
	 * @param search  search
	 * @param showAll should display all, user can enter ".all" for this to be true
	 * @return Search results
	 */
	@SideOnly(Side.CLIENT)
	public static List<ItemStack> getSearchResults(final String search, final boolean showAll) {
		queries.forEach(Query::stop);
		queries.clear();
		final NonNullList<ItemStack> results = NonNullList.create();
		if (search.equals(".inv")) {
			IInventory inventory = Minecraft.getMinecraft().player.inventory;
			IntStream.range(0, inventory.getSizeInventory())
					.mapToObj(inventory::getStackInSlot)
					.filter(s -> !s.isEmpty())
					.map(s -> ItemHandlerHelper.copyStackWithSize(s, 1))
					.forEach(s -> {
								if (results.stream().noneMatch(r -> ItemStack.areItemStacksEqual(s, r)))
									results.add(s);
							}
					);
		} else {
			if (showAll || search.length() == 0) {
				results.addAll(SearchUtil.getCache().keySet());
			} else {
				queries.add(new Query(search, results));
			}
		}

		return results;
	}

	private static class Query {
		private volatile boolean running = true;
		public Query(String search, List<ItemStack> results) {
			new Thread(() -> {
				Pattern p;
				try {
					p = Pattern.compile(search, Pattern.CASE_INSENSITIVE);
				} catch (PatternSyntaxException e) {
					p = Pattern.compile(Pattern.quote(search), Pattern.CASE_INSENSITIVE);
				}
				final Pattern       pattern = p;
				Iterator<Map.Entry<ItemStack, String>> iter    = getCache().entries().iterator();
				while (iter.hasNext() && running) {
					Map.Entry<ItemStack, String> entry = iter.next();
					if (pattern.matcher(entry.getValue()).find())
						if (!results.contains(entry.getKey()))
							results.add(entry.getKey());
				}
			}).start();
		}
		public void stop() {
			this.running= false;
		}
	}

	protected static final UUID ATTACK_DAMAGE_MODIFIER = UUID.fromString("CB3F55D3-645C-4F38-A497-9C13A33DB5CF");
	protected static final UUID ATTACK_SPEED_MODIFIER  = UUID.fromString("FA233E1C-4180-4865-B01B-BCCE9785ACA3");

	/**
	 * Return a list of strings containing information about the item
	 */
	@SuppressWarnings({"deprecation", "ConstantConditions"})
	@SideOnly(Side.CLIENT)
	public static String getTooltip(ItemStack stack, @Nullable EntityPlayer playerIn) {
		StringBuilder list = new StringBuilder();
		String        s    = stack.getDisplayName();

		String s1 = "";

		if (!s.isEmpty()) {
			s = s + " (";
			s1 = ")";
		}

		int i = Item.getIdFromItem(stack.getItem());

		if (stack.getHasSubtypes()) {
			s = s + String.format("#%04d/%d%s", i, stack.getItemDamage(), s1);
		} else {
			s = s + String.format("#%04d%s", i, s1);
		}

		list.append(s);

		ArrayList<String> asd = new ArrayList<>();
		stack.getItem().addInformation(stack, playerIn == null ? null : playerIn.world, asd, ITooltipFlag.TooltipFlags.ADVANCED);
		asd.forEach(list::append);

		if (stack.hasTagCompound()) {

			NBTTagList nbttaglist = stack.getEnchantmentTagList();

			for (int j = 0; j < nbttaglist.tagCount(); ++j) {
				NBTTagCompound nbttagcompound = nbttaglist.getCompoundTagAt(j);
				int            k              = nbttagcompound.getShort("id");
				int            l              = nbttagcompound.getShort("lvl");
				Enchantment    enchantment    = Enchantment.getEnchantmentByID(k);

				if (enchantment != null) {
					list.append(enchantment.getTranslatedName(l));
				}
			}


			if (stack.getTagCompound().hasKey("display", 10)) {
				NBTTagCompound nbttagcompound1 = stack.getTagCompound().getCompoundTag("display");
				if (nbttagcompound1.getTagId("Lore") == 9) {
					NBTTagList nbttaglist3 = nbttagcompound1.getTagList("Lore", 8);
					if (!nbttaglist3.isEmpty()) {
						for (int l1 = 0; l1 < nbttaglist3.tagCount(); ++l1) {
							list.append(nbttaglist3.getStringTagAt(l1));
						}
					}
				}
			}
		}

		for (EntityEquipmentSlot entityequipmentslot : EntityEquipmentSlot.values()) {
			Multimap<String, AttributeModifier> multimap = stack.getAttributeModifiers(entityequipmentslot);

			if (!multimap.isEmpty()) {
				list.append(I18n.translateToLocal("item.modifiers." + entityequipmentslot.getName()));

				for (Map.Entry<String, AttributeModifier> entry : multimap.entries()) {
					AttributeModifier attributemodifier = entry.getValue();
					double            d0                = attributemodifier.getAmount();
					boolean           flag              = false;

					if (playerIn != null) {
						if (attributemodifier.getID() == ATTACK_DAMAGE_MODIFIER) {
							d0 = d0 + playerIn.getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE).getBaseValue();
							d0 = d0 + (double) EnchantmentHelper.getModifierForCreature(stack, EnumCreatureAttribute.UNDEFINED);
							flag = true;
						} else if (attributemodifier.getID() == ATTACK_SPEED_MODIFIER) {
							d0 += playerIn.getEntityAttribute(SharedMonsterAttributes.ATTACK_SPEED).getBaseValue();
							flag = true;
						}
					}

					double d1;

					if (attributemodifier.getOperation() != 1 && attributemodifier.getOperation() != 2) {
						d1 = d0;
					} else {
						d1 = d0 * 100.0D;
					}

					if (flag) {
						list.append(I18n.translateToLocalFormatted("attribute.modifier.equals." + attributemodifier.getOperation(), DECIMALFORMAT.format(d1), I18n.translateToLocal("attribute.name." + entry.getKey())));
					} else if (d0 > 0.0D) {
						list.append(I18n.translateToLocalFormatted("attribute.modifier.plus." + attributemodifier.getOperation(), DECIMALFORMAT.format(d1), I18n.translateToLocal("attribute.name." + entry.getKey())));
					} else if (d0 < 0.0D) {
						d1 = d1 * -1.0D;
						list.append(I18n.translateToLocalFormatted("attribute.modifier.take." + attributemodifier.getOperation(), DECIMALFORMAT.format(d1), I18n.translateToLocal("attribute.name." + entry.getKey())));
					}
				}
			}
		}

		if (stack.hasTagCompound() && stack.getTagCompound().getBoolean("Unbreakable")) {
			list.append(I18n.translateToLocal("item.unbreakable"));
		}

		if (stack.hasTagCompound() && stack.getTagCompound().hasKey("CanDestroy", 9)) {
			NBTTagList nbttaglist1 = stack.getTagCompound().getTagList("CanDestroy", 8);

			if (!nbttaglist1.isEmpty()) {
				list.append(I18n.translateToLocal("item.canBreak"));

				for (int j1 = 0; j1 < nbttaglist1.tagCount(); ++j1) {
					Block block = Block.getBlockFromName(nbttaglist1.getStringTagAt(j1));

					if (block != null) {
						list.append(block.getLocalizedName());
					} else {
						list.append("missingno");
					}
				}
			}
		}

		if (stack.hasTagCompound() && stack.getTagCompound().hasKey("CanPlaceOn", 9)) {
			NBTTagList nbttaglist2 = stack.getTagCompound().getTagList("CanPlaceOn", 8);

			if (!nbttaglist2.isEmpty()) {
				list.append(I18n.translateToLocal("item.canPlace"));

				for (int k1 = 0; k1 < nbttaglist2.tagCount(); ++k1) {
					Block block1 = Block.getBlockFromName(nbttaglist2.getStringTagAt(k1));

					if (block1 != null) {
						list.append(block1.getLocalizedName());
					} else {
						list.append("missingno");
					}
				}
			}
		}

		if (stack.isItemDamaged()) {
			list.append(I18n.translateToLocalFormatted("item.durability", stack.getMaxDamage() - stack.getItemDamage(), stack.getMaxDamage()));
		}

		list.append((Item.REGISTRY.getNameForObject(stack.getItem())).toString());

		if (stack.hasTagCompound()) {
			list.append(I18n.translateToLocalFormatted("item.nbt_tags", stack.getTagCompound().getKeySet().size()));
		}

		return list.toString();
	}
}
