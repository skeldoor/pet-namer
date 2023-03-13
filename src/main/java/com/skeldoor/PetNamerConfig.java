package com.skeldoor;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("Pet Namer")
public interface PetNamerConfig extends Config
{
	@ConfigItem(
		keyName = "onlineMode",
		name = "Online mode",
		description = "This toggle will enable sending/receiving player's pet names from the server."
	)
	default boolean onlineMode()
	{
		return true;
	}

	@ConfigItem(
			keyName = "rightClickOthersPets",
			name = "Right click pets",
			description = "This toggle will enable the ability to see a player's pet in the right click menu. If online mode is on, you'll be able to see what they've renamed their pet to too."
	)
	default boolean rightClickPets()
	{
		return true;
	}
}
