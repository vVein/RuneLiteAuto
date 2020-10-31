package net.runelite.client.plugins.autoAgility;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("autoAgility")
public interface AutoAgilityConfig extends Config {

    @ConfigItem(
            position = 1,
            keyName = "autoAgility",
            name = "autoAgility",
            description = "autoAgility"
    )
    default boolean autoAgility()
    {
        return false;
    }

    @ConfigItem(
            position = 2,
            keyName = "autoAgility overlay",
            name = "autoAgility overlay",
            description = "autoAgility overlay"
    )
    default boolean autoAgilityOverlay()
    {
        return false;
    }
    
}
