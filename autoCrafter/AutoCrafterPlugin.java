package net.runelite.client.plugins.autoCrafter;

import com.google.common.collect.ImmutableList;
import com.google.inject.Provides;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Point;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameTick;
import net.runelite.api.widgets.WidgetInfo;
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
        name = "Auto Crafter",
        description = "Automatic Crafter",
        tags = {"crafting", "jewelry"}
)
@Slf4j

public class AutoCrafterPlugin extends Plugin{

    @Inject
    private Client client;

    @Inject
    private AutoCrafterConfig config;

    @Inject
    private AutoCrafterCore core;

    @Inject
    private AutoCrafterOverlay overlay;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private ItemManager itemManager;

    @Getter
    public static AtomicBoolean inventoryHidden = new AtomicBoolean();

    @Getter
    public static AtomicBoolean jewelryCraftingWidgetOpen = new AtomicBoolean();

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

    public final List<Integer> targetItemsIds = ImmutableList.of(ItemID.DIAMOND_NECKLACE);

    public final List<Integer> requiredItemIds1 = ImmutableList.of(ItemID.NECKLACE_MOULD);

    public final List<Integer> requiredItemIds2 = ImmutableList.of(ItemID.GOLD_BAR);

    public final List<Integer> requiredItemIds3 = ImmutableList.of(ItemID.DIAMOND);

    public final int requiredItemId2 = ItemID.GOLD_BAR;

    public final int requiredItemId3 = ItemID.DIAMOND;

    String targetAction = "Craft";

    String targetActionDescriptor = "Cast";

    @Provides
    AutoCrafterConfig getConfig(ConfigManager configManager)
    {
        return configManager.getConfig(AutoCrafterConfig.class);
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
        if (config.autoCrafter() && client.getGameState() == GameState.LOGGED_IN)
        {
            core.bankWidgetOpenCheck();
            core.craftingWidgetOpenCheck();
            inventoryHidden.set(client.getWidget(WidgetInfo.INVENTORY).isHidden());
            activeTargetGameObjectMethod();
            core.runEnergyCheck();
        }
        idleAnimation();
    }

    public void activeTargetGameObjectMethod(){
        if(AutoCrafterCore.activeTargetGameObject.get()){
            core.activeTargetGameObjectClickBox = core.targetGameObject.getClickbox().getBounds();
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
            if(randomOption == 4 && client.getCameraPitch() > 260 ) {
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
                    if (AutoCrafterCore.mouseArrived.get()) {
                        core.leftClick(robot);
                        Thread.sleep(randomDelay);
                    }
                }
            }
            else{
                core.moveMouseWalk(robot,valuableItemTileWorldLocation,20,9,10,5);
                if (AutoCrafterCore.mouseArrived.get()) {
                    core.leftClick(robot);
                    Thread.sleep(randomDelay);
                }
            }
        }
    }

    public void bankRoutine() throws AWTException, InterruptedException {
        Robot robot = new Robot();
        if(AutoCrafterCore.bankWidgetOpen.get()) {

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
                System.out.println(" core.numberOfItemInBank(requiredItemIds2) " + core.numberOfItemInBank(requiredItemIds2));
                int randomDelay0 = core.rand(5*60*1000, 6*60*1000);
                Thread.sleep(randomDelay0);
                return;
            }

            if (core.numberOfItemInInventory(requiredItemIds1) == 0 && core.availableInventory() >= 1) {
                core.moveMouseBankItem(robot, core.bankItemWidget(requiredItemIds1));
                int randomDelay0 = core.rand(1000, 2000);
                Thread.sleep(randomDelay0);
                if (AutoCrafterCore.mouseArrived.get() && core.leftClickOption(core.withdrawHighlightText)
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
                if (AutoCrafterCore.mouseArrived.get() && core.leftClickOption(core.withdrawHighlightText)
                        && core.leftClickTarget(core.requiredItem2String)) {
                    core.rightClick(robot);
                    int randomDelay1 = core.rand(1000, 2000);
                    Thread.sleep(randomDelay1);
                    if (AutoCrafterCore.mouseArrived.get()) {
                        Point menuOption = core.rightClickMenuPoint(core.requiredItem2String, core.withdrawAmountTargetText);
                        core.moveMouseFixedPoint(robot, menuOption, 10, 2, 18, 8);
                        int randomDelay3 = core.rand(700, 1200);
                        Thread.sleep(randomDelay3);
                        if (AutoCrafterCore.mouseArrived.get() ) {
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
                if (AutoCrafterCore.mouseArrived.get() && core.leftClickOption(core.withdrawHighlightText)
                        && core.leftClickTarget(core.requiredItem3String)) {
                    core.rightClick(robot);
                    int randomDelay1 = core.rand(1000, 2000);
                    Thread.sleep(randomDelay1);
                    Point menuOption = core.rightClickMenuPoint(core.requiredItem3String, core.withdrawAmountTargetText);
                    core.moveMouseFixedPoint(robot, menuOption, 10, 2, 24, 7);
                    int randomDelay3 = core.rand(700, 1200);
                    Thread.sleep(randomDelay3);
                    if (AutoCrafterCore.mouseArrived.get() ) {
                        core.leftClick(robot);
                        int randomDelay2 = core.rand(1000, 2000);
                        Thread.sleep(randomDelay2);
                    }
                }
            }

        }
    }

    public void autoCrafter() throws InterruptedException, AWTException {
        Robot robot = new Robot();
        long idleTimeThreshold = rand(1200,2000);
        pickUpLoot();
        if( idleAnimationTime > idleTimeThreshold ){
            if(core.numberOfItemInInventory(requiredItemIds1) >= 1 && core.numberOfItemInInventory(requiredItemIds2) >= 1
                    && core.numberOfItemInInventory(requiredItemIds3) >= 1)
            {
                if(core.playerOnTargetSpot(core.targetSpot)){
                    if(jewelryCraftingWidgetOpen.get()){
                        core.moveMouseWidget(robot,core.craftJewelryWidgetID,core.craftJewelryWidgetChildID);
                        if (AutoCrafterCore.mouseArrived.get()
                                && core.leftClickDoubleOption(core.craftJewelryText, core.craftJewelryTargetItemDescriptor)) {
                            core.leftClick(robot);
                            int randomDelay1 = core.rand(4000,8000);
                            Thread.sleep(randomDelay1);
                        }
                    } else {
                        core.gameObjectFromTiles(core.edgevilleFurnaceObject,core.edgevilleFurnaceContainerArea);
                        core.moveMouseGameObject(robot, 0.18, 0.33);
                        if (AutoCrafterCore.mouseArrived.get() && core.leftClickOption(core.useFurnaceText)){
                            core.leftClick(robot);
                            int randomDelay1 = rand(1000,2000);
                            Thread.sleep(randomDelay1);
                        }
                        AutoCrafterCore.activeTargetGameObject.set(false);
                    }
                } else if (core.playerInTargetArea(core.targetArea)){
                    if(core.gameObjectFromTiles(core.edgevilleFurnaceObject,core.edgevilleFurnaceContainerArea)){
                        core.moveMouseGameObject(robot, 0.18, 0.33);
                        if (AutoCrafterCore.mouseArrived.get() && core.leftClickOption(core.useFurnaceText)){
                            core.leftClick(robot);
                            int randomDelay1 = rand(1000,2000);
                            Thread.sleep(randomDelay1);
                        }
                        AutoCrafterCore.activeTargetGameObject.set(false);
                    } else {
                        core.moveMouseWalk(robot, core.randomAreaPoint(core.edgevilleFurnaceWalkArea), 4, 4, 8, 8);
                        if (AutoCrafterCore.mouseArrived.get()) {
                            core.leftClick(robot);
                            int randomDelay1 = rand(1000, 2000);
                            Thread.sleep(randomDelay1);
                        }
                    }
                } else if (core.gameObjectFromTiles(core.edgevilleFurnaceObject,core.edgevilleFurnaceContainerArea)){
                    core.moveMouseGameObject(robot, 0.18, 0.33);
                    if (AutoCrafterCore.mouseArrived.get() && core.leftClickOption(core.useFurnaceText)){
                        core.leftClick(robot);
                        int randomDelay1 = rand(1000,2000);
                        Thread.sleep(randomDelay1);
                    }
                    AutoCrafterCore.activeTargetGameObject.set(false);
                } else {
                    core.toggleRun();
                    core.moveMouseWalk(robot,core.randomAreaPoint(core.edgevilleFurnaceWalkArea),4,4,8,8);
                    if (AutoCrafterCore.mouseArrived.get()){
                        core.leftClick(robot);
                        int randomDelay1 = rand(1000,2000);
                        Thread.sleep(randomDelay1);
                    }
                }

            }
            else
            {
                if(core.playerInTargetArea(core.bankArea)){
                    System.out.println(" bankWidgetOpen InTargetArea " + AutoCrafterCore.bankWidgetOpen.get() );
                    if(!AutoCrafterCore.bankWidgetOpen.get()) {
                        core.openBank();
                    }
                    if(AutoCrafterCore.bankWidgetOpen.get()) {
                        bankRoutine();
                    }
                }
                else if(!core.playerInTargetArea(core.bankArea)){
                    if(core.gameObjectFromTiles(core.bankObjectList, core.bankBoothContainerArea)){
                        core.openBank();
                    }
                    else
                    {
                        System.out.println(" bankWidgetOpen !InTargetArea " + AutoCrafterCore.bankWidgetOpen.get() );
                        core.moveMouseWalk(robot,core.randomAreaPoint(core.edgevilleBankWalkArea),10,10,5,5);
                        if (AutoCrafterCore.mouseArrived.get()) {
                            core.leftClick(robot);
                            int randomDelay1 = core.rand(4000,6000);
                            Thread.sleep(randomDelay1);
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
                if (config.autoCrafter() && client.getGameState() == GameState.LOGGED_IN && runTime < maxRunTime) {
                    autoCrafter();
                }
            }
        }
    }
    );

}
