package net.runelite.client.plugins.autoalchemy;

import net.runelite.api.Client;
import net.runelite.client.ui.overlay.OverlayMenuEntry;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.*;

import static net.runelite.api.MenuAction.RUNELITE_OVERLAY_CONFIG;
import static net.runelite.client.ui.overlay.OverlayManager.OPTION_CONFIGURE;

public class AutoalchemyOverlay extends OverlayPanel {

    private final Client client;
    private final AutoalchemyConfig config;
    private final AutoalchemyPlugin plugin;
    private long refreshTime = 0;

    @Inject
    public AutoalchemyOverlay(Client client, AutoalchemyPlugin plugin, AutoalchemyConfig config)
    {
        super(plugin);
        setPosition(OverlayPosition.ABOVE_CHATBOX_RIGHT);
        this.client = client;
        this.plugin = plugin;
        this.config = config;
        getMenuEntries().add(new OverlayMenuEntry(RUNELITE_OVERLAY_CONFIG, OPTION_CONFIGURE, "Autoalchemy overlay"));
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {

        if(!config.autoalchemyOverlay()){
            return null;
        }

        if(refreshTime == 0)
        {
            refreshTime = System.currentTimeMillis();
        }

        if(System.currentTimeMillis() - refreshTime > 150)
        {
            refreshTime = System.currentTimeMillis();
        }

        panelComponent.getChildren().add(TitleComponent.builder()
                .text("Autoalchemy ")
                .color(Color.DARK_GRAY)
                .build());
        panelComponent.getChildren().add(LineComponent.builder()
                .left(Integer.toString(client.getLocalPlayer().getWorldLocation().getX()))
                .right(Integer.toString(client.getLocalPlayer().getWorldLocation().getY()))
                .build());
        panelComponent.getChildren().add(LineComponent.builder()
                .left("Animation Idle: ")
                .right(Boolean.toString(plugin.idleAnimation()))
                .build());
        panelComponent.getChildren().add(LineComponent.builder()
                .left("mouse location: ")
                .build());
        panelComponent.getChildren().add(LineComponent.builder()
                .right(Double.toString(MouseInfo.getPointerInfo().getLocation().getX()))
                .build());
        panelComponent.getChildren().add(LineComponent.builder()
                .right(Double.toString(MouseInfo.getPointerInfo().getLocation().getY()))
                .build());
        panelComponent.getChildren().add(LineComponent.builder()
                .left("client mouse location: ")
                .build());
        panelComponent.getChildren().add(LineComponent.builder()
                .right(Double.toString(client.getMouseCanvasPosition().getX()))
                .build());
        panelComponent.getChildren().add(LineComponent.builder()
                .right(Double.toString(client.getMouseCanvasPosition().getY()))
                .build());
        return super.render(graphics);
    }

}
