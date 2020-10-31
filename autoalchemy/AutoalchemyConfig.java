package net.runelite.client.plugins.autoalchemy;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("autoalchemy")
public interface AutoalchemyConfig extends Config
{
    enum TargetItem
    {
        DIAMOND_NECKLACE,
        RUBY_NECKLACE
    }

    @ConfigItem(
            position = 1,
            keyName = "autoAlchemy",
            name = "autoAlchemy",
            description = "autoAlchemy"
    )
    default boolean autoAlchemy()
    {
        return false;
    }

    @ConfigItem(
            position = 2,
            keyName = "autoAlchemy overlay",
            name = "autoAlchemy overlay",
            description = "autoAlchemy overlay"
    )
    default boolean autoalchemyOverlay()
    {
        return false;
    }

    @ConfigItem(
            position = 3,
            keyName = "autoAlchemy target",
            name = "autoAlchemy target",
            description = "autoAlchemy target"
    )
    default TargetItem autoAlchemyTarget()
    {
        return TargetItem.DIAMOND_NECKLACE;
    }
}
