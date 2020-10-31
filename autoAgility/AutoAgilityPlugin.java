package net.runelite.client.plugins.autoAgility;

import com.google.common.collect.ImmutableList;
import com.google.inject.Provides;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Point;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameTick;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
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
        name = "Auto Agility",
        description = "Automatic Agility",
        tags = {"Agility", "obstacles"}
)
@Slf4j

public class AutoAgilityPlugin extends Plugin {

    @Inject
    private Client client;

    @Inject
    private AutoAgilityConfig config;

    @Inject
    private AutoAgilityCore core;

    @Inject
    private AutoAgilityOverlay overlay;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private ItemManager itemManager;

    @Getter
    public static AtomicBoolean inventoryHidden = new AtomicBoolean();

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

    public final List<Integer> valuableItemList = ImmutableList.of(ItemID.MARK_OF_GRACE);

    public final List<Integer> targetItemsIds = ImmutableList.of(ItemID.DIAMOND_NECKLACE);

    public final List<Integer> requiredItemIds1 = ImmutableList.of(ItemID.NECKLACE_MOULD);

    public final List<Integer> requiredItemIds2 = ImmutableList.of(ItemID.GOLD_BAR);

    public final List<Integer> requiredItemIds3 = ImmutableList.of(ItemID.DIAMOND);

    public final int requiredItemId2 = ItemID.GOLD_BAR;

    public final int requiredItemId3 = ItemID.DIAMOND;

    String targetAction = "Craft";

    String targetActionDescriptor = "Cast";

    @Provides
    AutoAgilityConfig getConfig(ConfigManager configManager)
    {
        return configManager.getConfig(AutoAgilityConfig.class);
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
    public void onGameTick(GameTick gameTick)
    {
        if (config.autoAgility() && client.getGameState() == GameState.LOGGED_IN)
        {
            activeTargetObjectMethod();
            core.runEnergyCheck();
        }
        idleAnimation();
    }

    public void activeTargetObjectMethod(){
            if (AutoAgilityCore.activeTargetGameObject.get()) {
                core.activeTargetObjectClickBox = core.targetGameObject.getClickbox().getBounds();
                if (core.activeTargetObjectClickBox != null) {

                } else {
                    core.activeTargetObjectClickBox = new Rectangle(0, 0, 0, 0);
                    AutoAgilityCore.activeTargetGameObject.set(false);
                }
            }
            if (AutoAgilityCore.activeTargetGroundObject.get()) {
                core.activeTargetObjectClickBox = core.targetGroundObject.getClickbox().getBounds();
            }
            if (AutoAgilityCore.activeTargetDecorativeObject.get()) {
                core.activeTargetObjectClickBox = core.targetDecorativeObject.getClickbox().getBounds();
            }
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

    public int rand(int lowerLimit, int upperLimit)
    {
        int range = upperLimit + 1 - lowerLimit;
        int subRand = r.nextInt(range);
        return subRand + lowerLimit;
    }

    public void adjustCamera() throws AWTException {
        Robot robot = new Robot();
        long lastAdjustmentTimeThreshold = rand(14 * 60 * 1000,18 * 60 * 1000);
        if( System.currentTimeMillis() - lastAdjustmentTime > lastAdjustmentTimeThreshold ){
            int randomOption = rand(1,4);
            if(randomOption == 1) {
                int randomDelay0 = rand(10, 50);
                robot.delay(randomDelay0);
                robot.keyPress(KeyEvent.VK_LEFT);
                int randomDelay1 = rand(200, 400);
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
                int randomDelay1 = rand(200, 400);
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
                int randomDelay1 = rand(200, 500);
                robot.delay(randomDelay1);
                robot.keyRelease(KeyEvent.VK_UP);
                int randomDelay2 = rand(50, 200);
                robot.delay(randomDelay2);
                lastAdjustmentTime = System.currentTimeMillis();
                return;
            }
            if(randomOption == 4 && client.getCameraPitch() > 270 ) {
                int randomDelay0 = rand(10, 50);
                robot.delay(randomDelay0);
                robot.keyPress(KeyEvent.VK_DOWN);
                int randomDelay1 = rand(120, 300);
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
                            if (client.getLocalPlayer().getWorldLocation().distanceTo(tile.getWorldLocation()) < 9) {
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

    public Point player_World_Location(){
        Point player_WL = new Point(client.getLocalPlayer().getWorldLocation().getX(),client.getLocalPlayer().getWorldLocation().getY());
        return player_WL;
    }

    public void pickUpLoot() throws AWTException, InterruptedException {
        String pickUpLootText = "Take";
        Robot robot = new Robot();
        int randomDelay = rand(1000,1500);
        if(valuableItemTileFromTiles() && core.availableInventory() >= 1){
            Point tilePoint = Perspective.localToCanvas(client, valuableItemTile.getLocalLocation(),valuableItemTile.getPlane());
            if(!core.outsideWindow( tilePoint )){
                Point currentPosition = core.mouseCanvasLocation();
                double deltaX = Math.abs(currentPosition.getX() - tilePoint.getX());
                double deltaY = Math.abs(currentPosition.getY() - (tilePoint.getY() + canvasOffsetY));
                double tolerance = 5;
                if(deltaX > tolerance || deltaY > tolerance) {
                    core.moveMouseTile(robot, valuableItemTile,10,10,5,5);
                }
                if(core.leftClickIdentifier(valuableTileItemID)){
                    core.leftClick(robot);
                    Thread.sleep(randomDelay);
                }
                else {
                    core.rightClick(robot);
                    Point itemPoint = core.rightClickMenuPoint(valuableTileItemID, pickUpLootText);
                    core.moveMouseFixedPoint(robot,itemPoint,24,9,12,4);
                    if (AutoAgilityCore.mouseArrived.get()) {
                        core.leftClick(robot);
                        Thread.sleep(randomDelay);
                    }
                }
            }
            else{
                core.moveMouseWalk(robot,valuableItemTileWorldLocation,4,4,7,6);
                if (AutoAgilityCore.mouseArrived.get()) {
                    core.leftClick(robot);
                    Thread.sleep(randomDelay);
                }
            }
        }
    }

    public void bankRoutine() throws AWTException, InterruptedException {
        Robot robot = new Robot();
        if(AutoAgilityCore.bankWidgetOpen.get()) {

            if (core.numberOfItemInInventory(requiredItemIds1) == 0 && core.availableInventory() == 0) {
                core.depositInventory();
            }

            if (core.numberOfItemInInventory(requiredItemIds2) != 13 && core.availableInventory() < 26 ) {
                core.depositInventory();
            }

            if (core.numberOfItemInInventory(requiredItemIds3) != 13 && core.availableInventory() < 13 ){
                core.depositInventory();
            }

            if (core.numberOfItemInBank(requiredItemIds2) < 13 || core.numberOfItemInBank(requiredItemIds3) < 13 ){
                int randomDelay0 = core.rand(5*60*1000, 6*60*1000);
                Thread.sleep(randomDelay0);
                return;
            }

            if (core.numberOfItemInInventory(requiredItemIds1) == 0 && core.availableInventory() >= 1) {
                core.moveMouseBankItem(robot, core.bankItemWidget(requiredItemIds1));
                int randomDelay0 = core.rand(1000, 2000);
                Thread.sleep(randomDelay0);
                if (AutoAgilityCore.mouseArrived.get() && core.leftClickOption(core.withdrawHighlightText)
                        && core.leftClickTarget(core.requiredItem1String)) {

                    core.leftClick(robot);
                    int randomDelay1 = core.rand(1000, 2000);
                    Thread.sleep(randomDelay1);
                }
            }

            if (core.numberOfItemInInventory(requiredItemIds2) != 13 && core.availableInventory() >= 13
                    && core.numberOfItemInInventory(requiredItemIds1) == 1 ) {
                core.moveMouseBankItem(robot, core.bankItemWidget(requiredItemIds2));
                int randomDelay0 = core.rand(400, 600);
                Thread.sleep(randomDelay0);
                if (AutoAgilityCore.mouseArrived.get() && core.leftClickOption(core.withdrawHighlightText)
                        && core.leftClickTarget(core.requiredItem2String)) {
                    core.rightClick(robot);
                    int randomDelay1 = core.rand(1000, 2000);
                    Thread.sleep(randomDelay1);
                    if (AutoAgilityCore.mouseArrived.get()) {
                        net.runelite.api.Point menuOption = core.rightClickMenuPoint(core.requiredItem2String, core.withdrawAmountTargetText);
                        core.moveMouseFixedPoint(robot, menuOption, 10, 2, 18, 8);
                        int randomDelay3 = core.rand(700, 1200);
                        Thread.sleep(randomDelay3);
                        if (AutoAgilityCore.mouseArrived.get() ) {
                            core.leftClick(robot);
                            int randomDelay2 = core.rand(1000, 2000);
                            Thread.sleep(randomDelay2);
                        }
                    }
                }
            }

            if (core.numberOfItemInInventory(requiredItemIds3) != 13 &&
                    core.numberOfItemInInventory(requiredItemIds2) == 13 && core.availableInventory() >= 13
                    && core.numberOfItemInInventory(requiredItemIds1) == 1 ) {
                core.moveMouseBankItem(robot, core.bankItemWidget(requiredItemIds3));
                int randomDelay0 = core.rand(400, 600);
                Thread.sleep(randomDelay0);
                if (AutoAgilityCore.mouseArrived.get() && core.leftClickOption(core.withdrawHighlightText)
                        && core.leftClickTarget(core.requiredItem3String)) {
                    core.rightClick(robot);
                    int randomDelay1 = core.rand(1000, 2000);
                    Thread.sleep(randomDelay1);
                    Point menuOption = core.rightClickMenuPoint(core.requiredItem3String, core.withdrawAmountTargetText);
                    core.moveMouseFixedPoint(robot, menuOption, 10, 2, 24, 7);
                    int randomDelay3 = core.rand(700, 1200);
                    Thread.sleep(randomDelay3);
                    if (AutoAgilityCore.mouseArrived.get() ) {
                        core.leftClick(robot);
                        int randomDelay2 = core.rand(1000, 2000);
                        Thread.sleep(randomDelay2);
                    }
                }
            }

        }
    }

    public void AutoAgility() throws InterruptedException, AWTException {
        Robot robot = new Robot();
        long idleTimeThreshold = rand(1200,2000);
        core.runEnergyCheck();
        String ground = "ground";
        String game = "game";
        String decorative = "decorative";

        if( idleAnimationTime > idleTimeThreshold ){
            boolean walk = true;
            for(AutoAgilityCourses course : AutoAgilityCourses.values()){
                if(core.playerInTargetArea(course.getPre_Obstacle_Area()) && client.getPlane() == course.getObstacle_World_Location().getPlane() ){
                    if(valuableItemTileFromTiles() && core.availableInventory() >= 1
                            && course.getPre_Obstacle_Area().contains(valuableItemTileWorldLocation.getX(),valuableItemTileWorldLocation.getY())){
                            Point tilePoint = Perspective.localToCanvas(client, valuableItemTile.getLocalLocation(),valuableItemTile.getPlane());
                            if(!core.outsideWindow( tilePoint )){
                                pickUpLoot();
                                int randomDelay0 = rand(1000, 2000);
                                Thread.sleep(randomDelay0);
                            } else {
                                core.moveMouseWalk(robot, core.random_WorldPoint(valuableItemTileWorldLocation), 4, 4, 6, 6);
                                if (AutoAgilityCore.mouseArrived.get()) {
                                    core.leftClick(robot);
                                    int randomDelay1 = rand(1000, 2000);
                                    Thread.sleep(randomDelay1);
                                }
                            }
                    } else if(course.getObstacle_Type().equals(game)){
                    core.game_Object_From_Tiles(course.getObstacle_ID());
                        if(AutoAgilityCore.activeTargetGameObject.get()) {
                            core.moveMouseObject(robot, 0.18, 0.33);
                            if (AutoAgilityCore.mouseArrived.get() && core.leftClickOption(course.getObstacle_text())) {
                                core.leftClick(robot);
                                int randomDelay1 = rand(3000, 5000);
                                Thread.sleep(randomDelay1);
                            } else {
                                int randomDelay2 = rand(400, 600);
                                Thread.sleep(randomDelay2);
                                if (AutoAgilityCore.mouseArrived.get() && core.leftClickOption(course.getObstacle_text())) {
                                    core.leftClick(robot);
                                    int randomDelay1 = rand(3000, 5000);
                                    Thread.sleep(randomDelay1);
                                }
                            }
                            AutoAgilityCore.activeTargetGameObject.set(false);
                        } else {
                            core.moveMouseWalk(robot, core.random_WorldPoint(course.getObstacle_World_Location()), 4, 4, 6, 6);
                            if (AutoAgilityCore.mouseArrived.get()) {
                                core.leftClick(robot);
                                int randomDelay1 = rand(1000, 2000);
                                Thread.sleep(randomDelay1);
                            }
                        }
                    } else if (course.getObstacle_Type().equals(ground)){
                        core.ground_Object_From_Tiles(course.getObstacle_ID());
                        if(AutoAgilityCore.activeTargetGroundObject.get()) {
                            core.moveMouseObject(robot, 0.18, 0.33);
                            if (AutoAgilityCore.mouseArrived.get() && core.leftClickOption(course.getObstacle_text())) {
                                core.leftClick(robot);
                                int randomDelay1 = rand(3000, 5000);
                                Thread.sleep(randomDelay1);
                            }
                            AutoAgilityCore.activeTargetGroundObject.set(false);
                        } else {
                            core.moveMouseWalk(robot, core.random_WorldPoint(course.getObstacle_World_Location()), 4, 4, 6, 6);
                            if (AutoAgilityCore.mouseArrived.get()) {
                                core.leftClick(robot);
                                int randomDelay1 = rand(1000, 2000);
                                Thread.sleep(randomDelay1);
                            }
                        }
                    } else if (course.getObstacle_Type().equals(decorative)){
                        core.decorative_Object_From_Tiles(course.getObstacle_ID());
                        if(AutoAgilityCore.activeTargetDecorativeObject.get()) {
                            core.moveMouseObject(robot, 0.18, 0.33);
                            if (AutoAgilityCore.mouseArrived.get() && core.leftClickOption(course.getObstacle_text())) {
                                core.leftClick(robot);
                                int randomDelay1 = rand(3000, 5000);
                                Thread.sleep(randomDelay1);
                            }
                            AutoAgilityCore.activeTargetDecorativeObject.set(false);
                        } else {
                            core.moveMouseWalk(robot, core.random_WorldPoint(course.getObstacle_World_Location()), 4, 4, 6, 6);
                            if (AutoAgilityCore.mouseArrived.get()) {
                                core.leftClick(robot);
                                int randomDelay1 = rand(1000, 2000);
                                Thread.sleep(randomDelay1);
                            }
                        }
                    }
                    walk = false;
                }
            }
            if (client.getPlane() == 0 && walk) {

                if ( core.playerInTargetArea(AutoAgilityCourses.seers_Pathing_1) ){
                    core.moveMouseWalk(robot, core.randomAreaPoint(AutoAgilityCourses.seers_Pathing_Target_1), 4, 4, 6, 6);
                }
                else {
                    core.moveMouseWalk(robot, core.random_WorldPoint(AutoAgilityCourses.SEERS1.getObstacle_World_Location()), 4, 4, 6, 6);
                }

                if ( AutoAgilityCore.mouseArrived.get() ){
                    if ( core.leftClickOption("Walk here") || core.pointInsideMiniMap(core.mouseCanvasLocation()) ) {
                        core.leftClick(robot);
                        int randomDelay1 = rand(600, 1600);
                        Thread.sleep(randomDelay1);
                    }
                    else if(!core.pointInsideMiniMap(core.mouseCanvasLocation())) {
                        core.rightClick(robot);
                        Point menuPoint = core.rightClickMenuPoint("","Walk here");
                        core.moveMouseFixedPoint(robot, menuPoint, 24, 5, 12, 3);
                        if (AutoAgilityCore.mouseArrived.get() ) {
                            core.leftClick(robot);
                        }
                    }
                }
            }
        }
        int sleepTime = core.randomDistortedDistributionInt(2000, 8000,3000);
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
                if (config.autoAgility() && client.getGameState() == GameState.LOGGED_IN && runTime < maxRunTime) {
                    AutoAgility();
                }
            }
        }
    }
    );
    
}
