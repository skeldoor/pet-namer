package com.example;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("example")
public interface ExampleConfig extends Config
{
	@ConfigItem(
		keyName = "greeting",
		name = "Welcome Greeting",
		description = "The message to show to the user when they login"
	)
	default String greeting()
	{
		return "Hello";
	}

	@ConfigItem(
			keyName = "cat",
			name = "Cat",
			description = "The name of your kitten/cat/hellcat"
	)
	default String catName()
	{
		return "";
	}

	@ConfigItem(
			keyName = "abyssalOrphan",
			name = "Abyssal orphan",
			description = "The name of your Abyssal orphan"
	)
	default String abyssalOrphanName()
	{
		return "";
	}

	@ConfigItem(
			keyName = "babyMole",
			name = "Baby mole",
			description = "The name of your Baby mole"
	)
	default String babyMoleName()
	{
		return "";
	}

}
