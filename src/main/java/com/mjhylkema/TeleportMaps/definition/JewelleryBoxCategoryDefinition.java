package com.mjhylkema.TeleportMaps.definition;

import lombok.Getter;

/**
 * A jewellery type offered by the POH jewellery box. Its destinations are
 * marked on the map with the jewellery's item icon.
 */
@Getter
public class JewelleryBoxCategoryDefinition
{
	/* In the tabbed layout, shows this jewellery's tab on the Skills Necklace map */
	public static final String MAP_SKILLS_NECKLACE = "skills";

	private String name;
	/* The tab label in the tabbed layout */
	private String shortName;
	private int itemId;
	/* The jewellery box layer holding this jewellery type's buttons */
	private int childId;
	private String map;
	private JewelleryBoxDefinition[] destinations;

	public boolean usesSkillsNecklaceMap()
	{
		return MAP_SKILLS_NECKLACE.equals(this.map);
	}
}
