package net.runelite.client.plugins.autoslayer;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("autoslayer")
public interface AutoslayerConfig extends Config
{
    enum TargetNPC
    {
        HILL_GIANTS,
        FLESH_CRAWLERS,
        MOSS_GIANTS
    }

    @ConfigItem(
            position = 1,
            keyName = "autoSlayer",
            name = "autoSlayer",
            description = "autoSlayer"
    )
    default boolean autoSlayer()
    {
        return false;
    }

    @ConfigItem(
            position = 2,
            keyName = "autoSlayer overlay",
            name = "autoSlayer overlay",
            description = "autoSlayer overlay"
    )
    default boolean autoslayerOverlay()
    {
        return false;
    }

    @ConfigItem(
            position = 3,
            keyName = "autoSlayer target",
            name = "autoSlayer target",
            description = "autoSlayer target"
    )
    default TargetNPC autoslayerTarget()
    {
        return TargetNPC.HILL_GIANTS;
    }

    @ConfigItem(
            position = 4,
            keyName = "lootValueThreshold.",
            name = "lootValueThreshold.",
            description = "lootValueThreshold."
    )
    default int lootValueThreshold()
    {
        return 1000;
    }
}
