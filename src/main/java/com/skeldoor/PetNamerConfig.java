package com.skeldoor;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("PetNamer")
public interface PetNamerConfig extends Config
{
    @ConfigItem(
            keyName = "localMode",
            name = "Local Only Mode",
            description = "Enable this to only get player names from your local config"
    )
    default boolean localMode()
    {
        return false;
    }
}
