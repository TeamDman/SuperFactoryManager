package vswe.superfactory.blocks;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.Collections2;
import com.google.common.collect.Lists;
import net.minecraft.block.properties.PropertyEnum;
import vswe.superfactory.tiles.TileEntityCamouflage;

import java.util.Collection;

public class PropertyCamouflageType extends PropertyEnum {
	protected PropertyCamouflageType(String name, Collection allowedValues) {
		super(name, TileEntityCamouflage.CamouflageType.class, allowedValues);
	}

	/**
	 * Create a new PropertyDirection with the given name
	 */
	public static PropertyCamouflageType create(String name) {
		/**
		 * Create a new PropertyDirection with all directions that match the given Predicate
		 */
		return create(name, Predicates.alwaysTrue());
	}

	/**
	 * Create a new PropertyDirection with all directions that match the given Predicate
	 */
	public static PropertyCamouflageType create(String name, Predicate filter) {
		/**
		 * Create a new PropertyDirection for the given direction values
		 */
		return create(name, Collections2.filter(Lists.newArrayList(TileEntityCamouflage.CamouflageType.values()), filter));
	}

	/**
	 * Create a new PropertyDirection for the given direction values
	 */
	public static PropertyCamouflageType create(String name, Collection values) {
		return new PropertyCamouflageType(name, values);
	}
}
