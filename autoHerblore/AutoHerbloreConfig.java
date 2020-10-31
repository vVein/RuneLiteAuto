package net.runelite.client.plugins.autoHerblore;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("autoHerblore")
public interface AutoHerbloreConfig extends Config {

    @ConfigItem(
            position = 1,
            keyName = "autoHerblore",
            name = "autoHerblore",
            description = "autoHerblore"
    )
    default boolean autoHerblore()
    {
        return false;
    }

    @ConfigItem(
            position = 2,
            keyName = "autoHerblore overlay",
            name = "autoHerblore overlay",
            description = "autoHerblore overlay"
    )
    default boolean autoHerbloreOverlay()
    {
        return false;
    }

}
