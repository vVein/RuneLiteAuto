package net.runelite.client.plugins.autoalchemy;

import com.google.common.collect.ImmutableList;
import com.google.inject.Provides;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.Point;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameTick;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

@PluginDescriptor(
        name = "Auto Alchemy",
        description = "Automatic alcher",
        tags = {"magic", "gold"}
)
@Slf4j
public class AutoalchemyPlugin extends Plugin {

    @Inject
    private Client client;

    @Inject
    private AutoalchemyConfig config;

    @Inject
    private AutoalchemyCore core;

    @Inject
    private AutoalchemyOverlay overlay;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private ItemManager itemManager;

    @Getter
    public static AtomicBoolean inventoryHidden = new AtomicBoolean();

    @Getter
    public static AtomicBoolean spellBookOpen = new AtomicBoolean();

    long runTime;
    long startTime = 0;
    long maxRunTime = 4 * 60 * 60 * 1000;

    long lastAdjustmentTime;

    Random r = new Random();

    long idleAnimationTime;
    long idleAnimationTimeStamp;
    long idleAnimationThreshold = 1200;

    int valueThreshold = 10000;

    WorldPoint valuableItemTileWorldLocation;
    int valuableTileItemID;
    Tile valuableItemTile;

    public int canvasOffsetY = 23;

    public final List<Integer> valuableItemList = ImmutableList.of(ItemID.DRAGON_DEFENDER);

    public final List<Integer> DIAMOND_NECKLACEIds = ImmutableList.of(ItemID.DIAMOND_NECKLACE); //_NOTED
    public final List<Integer> RUBY_NECKLACEIds = ImmutableList.of(ItemID.RUBY_NECKLACE);

    public List<Integer> targetItemsIds = DIAMOND_NECKLACEIds;

    public final List<Integer> requiredRunesIds = ImmutableList.of(ItemID.NATURE_RUNE);

    String targetAction = "High Level Alchemy";

    String targetActionDescriptor = "Cast";

    @Provides
    AutoalchemyConfig getConfig(ConfigManager configManager)
    {
        return configManager.getConfig(AutoalchemyConfig.class);
    }

    @Override
    protected void startUp() throws Exception
    {
        overlayManager.add(overlay);
        script.start();
    }

    @Override
    protected void shutDown() throws Exception
    {
        overlayManager.remove(overlay);
    }

    @Subscribe
    public void onGameTick(GameTick t)
    {
        if (config.autoAlchemy() && client.getGameState() == GameState.LOGGED_IN)
        {
            inventoryHidden.set(client.getWidget(WidgetInfo.INVENTORY).isHidden());
            spellBookOpen.set(!client.getWidget(218, 39).isHidden());
        }
        idleAnimation();
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event)
    {
        updateTargetItemConfig();
    }

    public boolean idleAnimation()
    {
        if (client.getLocalPlayer().getAnimation()!=-1)
        {
            idleAnimationTime = 0;
            idleAnimationTimeStamp = System.currentTimeMillis();
            return false;
        }
        else
        {
            idleAnimationTime = System.currentTimeMillis() - idleAnimationTimeStamp;
            return idleAnimationTime > idleAnimationThreshold;
        }
    }

    public void updateTargetItemConfig()
    {
        switch (config.autoAlchemyTarget())
        {
            case DIAMOND_NECKLACE:
                targetItemsIds = DIAMOND_NECKLACEIds;
                break;
            case RUBY_NECKLACE:
                targetItemsIds = RUBY_NECKLACEIds;
                break;
        }

    }

    public int rand(int lowerLimit, int upperLimit)
    {
        int range = upperLimit + 1 - lowerLimit;
        int subRand = r.nextInt(range);
        return subRand + lowerLimit;
    }

    public void adjustCamera() throws AWTException {
        Robot robot = new Robot();
        long lastAdjustmentTimeThreshold = rand(10 * 60 * 1000,15 * 60 * 1000);
        if( System.currentTimeMillis() - lastAdjustmentTime > lastAdjustmentTimeThreshold ){
            int randomOption = rand(1,4);
            if(randomOption == 1) {
                int randomDelay0 = rand(10, 50);
                robot.delay(randomDelay0);
                robot.keyPress(KeyEvent.VK_LEFT);
                int randomDelay1 = rand(300, 900);
                robot.delay(randomDelay1);
                robot.keyRelease(KeyEvent.VK_LEFT);
                int randomDelay2 = rand(50, 200);
                robot.delay(randomDelay2);
                lastAdjustmentTime = System.currentTimeMillis();
                return;
            }
            if(randomOption == 2) {
                int randomDelay0 = rand(10, 50);
                robot.delay(randomDelay0);
                robot.keyPress(KeyEvent.VK_RIGHT);
                int randomDelay1 = rand(300, 900);
                robot.delay(randomDelay1);
                robot.keyRelease(KeyEvent.VK_RIGHT);
                int randomDelay2 = rand(50, 200);
                robot.delay(randomDelay2);
                lastAdjustmentTime = System.currentTimeMillis();
                return;
            }
            if(randomOption == 3) {
                int randomDelay0 = rand(10, 50);
                robot.delay(randomDelay0);
                robot.keyPress(KeyEvent.VK_UP);
                int randomDelay1 = rand(300, 900);
                robot.delay(randomDelay1);
                robot.keyRelease(KeyEvent.VK_UP);
                int randomDelay2 = rand(50, 200);
                robot.delay(randomDelay2);
                lastAdjustmentTime = System.currentTimeMillis();
                return;
            }
            if(randomOption == 4 && client.getCameraPitch() > 250 ) {
                int randomDelay0 = rand(10, 50);
                robot.delay(randomDelay0);
                robot.keyPress(KeyEvent.VK_DOWN);
                int randomDelay1 = rand(200, 400);
                robot.delay(randomDelay1);
                robot.keyRelease(KeyEvent.VK_DOWN);
                int randomDelay2 = rand(50, 200);
                robot.delay(randomDelay2);
                lastAdjustmentTime = System.currentTimeMillis();
                return;
            }
        }
    }

    public boolean valuableItemTileFromTiles() {
        Tile[][][] tiles = client.getScene().getTiles();
        for (Tile[][] tileOuter : tiles) {
            for (Tile[] tileInner : tileOuter) {
                for (Tile tile : tileInner) {
                    if (tile != null) {
                        if (tile.getWorldLocation() != null) {
                            if (client.getLocalPlayer().getWorldLocation().distanceTo(tile.getWorldLocation()) < 8) {
                                if (tile.getGroundItems() != null) {
                                    for (TileItem tileItem : tile.getGroundItems()) {
                                        if (tileItem != null) {
                                            int gePrice = itemManager.getItemPrice(tileItem.getId()) * tileItem.getQuantity();
                                            if (gePrice > valueThreshold) {
                                                valuableItemTileWorldLocation = tile.getWorldLocation();
                                                valuableTileItemID = tileItem.getId();
                                                valuableItemTile = tile;
                                                return true;
                                            }
                                            int itemID = tileItem.getId();
                                            if(valuableItemList.contains(itemID)){
                                                valuableItemTileWorldLocation = tile.getWorldLocation();
                                                valuableTileItemID = tileItem.getId();
                                                valuableItemTile = tile;
                                                return true;
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return false;
    }

    public void pickUpLoot() throws AWTException, InterruptedException {
        String pickUpLootText = "Take";
        Robot robot = new Robot();
        int randomDelay = rand(1000,1500);
        if(valuableItemTileFromTiles() && core.availableInventory() >= 1){
            net.runelite.api.Point tilePoint = Perspective.localToCanvas(client, valuableItemTile.getLocalLocation(),valuableItemTile.getPlane());
            if(!core.outsideWindow( tilePoint )){
                net.runelite.api.Point currentPosition = core.mouseCanvasLocation();
                double deltaX = Math.abs(currentPosition.getX() - tilePoint.getX());
                double deltaY = Math.abs(currentPosition.getY() - (tilePoint.getY() + canvasOffsetY));
                double tolerance = 5;
                if(deltaX > tolerance || deltaY > tolerance) {
                    core.moveMouseTile(robot, valuableItemTile,10,10,5,5);
                }
                if(core.leftClickMatchTargetIdentifier(valuableTileItemID)){
                    core.leftClick(robot);
                    Thread.sleep(randomDelay);
                }
                else {
                    core.rightClick(robot);
                    Point itemPoint = core.rightClickMenuPoint(valuableTileItemID, pickUpLootText);
                     core.moveMouseFixedPoint(robot,itemPoint,9,9,4,4);
                    if (AutoalchemyCore.mouseArrived.get()) {
                        core.leftClick(robot);
                        Thread.sleep(randomDelay);
                    }
                }
            }
            else{
               core.moveMouseWalk(robot,valuableItemTileWorldLocation,9,9,5,5);
                if (AutoalchemyCore.mouseArrived.get()) {
                    core.leftClick(robot);
                    Thread.sleep(randomDelay);
                }
            }
        }
    }

    public void autoAlchemy() throws InterruptedException, AWTException {
        Robot robot = new Robot();
        long idleTimeThreshold = rand(1200,1800);
        pickUpLoot();
        if( idleAnimationTime > idleTimeThreshold ){
            if(core.numberOfStackedItemInInventory(requiredRunesIds) >= 1) {
                if (core.numberOfStackedItemInInventory(targetItemsIds) >= 1) {
                    if (client.getSpellSelected() && !inventoryHidden.get()) {
                        core.moveMouseFixedPoint(robot, core.inventoryLocation(targetItemsIds), 12, 12, 4, 4);
                        if (AutoalchemyCore.mouseArrived.get() && core.leftClickMatchTargetIdentifier(targetItemsIds)) {
                            core.leftClick(robot);
                            int randomDelay1 = core.randomDistortedDistributionInt(1200,8000,1500);
                            Thread.sleep(randomDelay1);
                        }
                    } else {
                        if (!spellBookOpen.get()) {
                            core.keyPress(KeyEvent.VK_F6);
                        }
                        if (spellBookOpen.get()) {
                            System.out.println(" spellBookOpen " );
                            core.moveMouseWidget(robot, 218, 39);
                            if (AutoalchemyCore.mouseArrived.get() && core.leftClickOption(targetActionDescriptor)) {
                                core.leftClick(robot);
                            }
                        }
                    }
                }
            }
        }
        int sleepTime = rand(1000, 2000);
        System.out.println(" sleepTime " + sleepTime);
        Thread.sleep(sleepTime);
        adjustCamera();
    }

    private final Thread script = new Thread(new Runnable() {
        @SneakyThrows
        public void run() {
            Thread.sleep(15000);
            int i;
            for (i = 0; i < 99999; i++) {
                Thread.sleep(400);
                if (startTime == 0) {
                    startTime = System.currentTimeMillis();
                }
                runTime = System.currentTimeMillis() - startTime;
                if (config.autoAlchemy() && client.getGameState() == GameState.LOGGED_IN && runTime < maxRunTime) {
                    autoAlchemy();
                }
            }
        }
    }
    );

}
