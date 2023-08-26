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
            description = "Enable this to only show pet names for your own pets and to stop pulling names from the 3rd party server"
    )
    default boolean localMode()
    {
        return false;
    }
}
