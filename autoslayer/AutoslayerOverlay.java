package net.runelite.client.plugins.autoslayer;

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

public class AutoslayerOverlay extends OverlayPanel {

    private final Client client;
    private final AutoslayerConfig config;
    private final AutoslayerPlugin plugin;
    private long refreshTime = 0;

    @Inject
    public AutoslayerOverlay(Client client, AutoslayerPlugin plugin, AutoslayerConfig config)
    {
        super(plugin);
        setPosition(OverlayPosition.ABOVE_CHATBOX_RIGHT);
        this.client = client;
        this.plugin = plugin;
        this.config = config;
        getMenuEntries().add(new OverlayMenuEntry(RUNELITE_OVERLAY_CONFIG, OPTION_CONFIGURE, "Autoslayer overlay"));
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {

        if(!config.autoslayerOverlay()){
            return null;
        }

        if(refreshTime == 0)
        {
            refreshTime = System.currentTimeMillis();
        }

        if(System.currentTimeMillis() - refreshTime > 150)
        {
            plugin.activeTarget();
            refreshTime = System.currentTimeMillis();
        }

        panelComponent.getChildren().add(TitleComponent.builder()
                .text("Autoslayer ")
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
                .left("Mouse Coords: ")
                .build());
        panelComponent.getChildren().add(LineComponent.builder()
                .right(MouseInfo.getPointerInfo().getLocation().toString())
                .build());
        panelComponent.getChildren().add(LineComponent.builder()
                .left("NPC bounds: ")
                .build());
        panelComponent.getChildren().add(LineComponent.builder()
                .right(Double.toString(AutoslayerPlugin.getTarget().getConvexHull().getBounds2D().getCenterX()))
                .build());
        panelComponent.getChildren().add(LineComponent.builder()
                .right(Double.toString(AutoslayerPlugin.getTarget().getConvexHull().getBounds2D().getCenterY()))
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
        panelComponent.getChildren().add(LineComponent.builder()
                .left("npc hp scale/ratio : ")
                .build());
        panelComponent.getChildren().add(LineComponent.builder()
                .right(Double.toString(AutoslayerPlugin.getTarget().getHealthScale()))
                .build());
        panelComponent.getChildren().add(LineComponent.builder()
                .right(Double.toString(AutoslayerPlugin.getTarget().getHealthRatio()))
                .build());
        return super.render(graphics);
    }

}
