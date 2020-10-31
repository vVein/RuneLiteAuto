package net.runelite.client.plugins.autoCrafter;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("autoCrafter")
public interface AutoCrafterConfig extends Config {

    @ConfigItem(
            position = 1,
            keyName = "autoCrafter",
            name = "autoCrafter",
            description = "autoCrafter"
    )
    default boolean autoCrafter()
    {
        return false;
    }

    @ConfigItem(
            position = 2,
            keyName = "autoCrafter overlay",
            name = "autoCrafter overlay",
            description = "autoCrafter overlay"
    )
    default boolean autoCrafterOverlay()
    {
        return false;
    }
}
