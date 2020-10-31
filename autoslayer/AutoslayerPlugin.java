package net.runelite.client.plugins.autoslayer;

import com.google.common.collect.ImmutableList;
import com.google.inject.Provides;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Point;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.NpcDespawned;
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
import java.awt.geom.Rectangle2D;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

@PluginDescriptor(
        name = "Auto Slayer",
        description = "Automatic slayerness",
        tags = {"combat", "pve"}
)
@Slf4j
public class AutoslayerPlugin extends Plugin
{
    @Inject
    private Client client;

    @Inject
    private AutoslayerCore core;

    @Inject
    private AutoslayerConfig config;

    @Inject
    private AutoslayerOverlay overlay;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private ItemManager itemManager;

    @Getter (AccessLevel.PACKAGE)
    public static AtomicBoolean validActiveTarget = new AtomicBoolean();

    @Getter (AccessLevel.PACKAGE)
    public static AtomicBoolean activeTargetGameObject = new AtomicBoolean();

    @Getter (AccessLevel.PACKAGE)
    public static AtomicBoolean inventoryHidden = new AtomicBoolean();

    @Getter (AccessLevel.PACKAGE)
    public static Rectangle2D activeTargetGameObjectHull;

    @Getter (AccessLevel.PACKAGE)
    public static Point activeTargetGameObjectCentre;

    @Getter (AccessLevel.PACKAGE)
    public static NPC target;

    @Getter (AccessLevel.PACKAGE)
    public static Point targetCentre;

    @Getter (AccessLevel.PACKAGE)
    public static Rectangle2D targetHull;

    public String setTarget = "HILL_GIANTS";

    long runTime;
    long startTime = 0;
    long maxRunTime = 4 * 60 * 60 * 1000;

    long lastAdjustmentTime;

    Random r = new Random();

    long idleAnimationTime;
    long idleAnimationTimeStamp;
    long idleAnimationThreshold = 1500;

    int hitpointsThreshold = 52;
    int valueThreshold = 5000;

    WorldPoint valuableItemTileWorldLocation;
    int valuableTileItemID;
    Tile valuableItemTile;

    public int canvasOffsetY = 23;

    public final List<Integer> valuableItemList = ImmutableList.of(ItemID.DRAGON_DEFENDER,ItemID.GIANT_KEY,ItemID.MITHRIL_DEFENDER,
            ItemID.ADAMANT_DEFENDER,ItemID.MITHRIL_PLATEBODY,ItemID.MITHRIL_PLATELEGS,
            ItemID.MITHRIL_FULL_HELM,ItemID.ADAMANT_PLATEBODY,ItemID.ADAMANT_PLATELEGS,
            ItemID.ADAMANT_FULL_HELM,ItemID.WARRIOR_GUILD_TOKEN);

    private final List<Integer> cowIds = ImmutableList.of(NpcID.COW,NpcID.COW_2791,NpcID.COW_2793);
    private final List<Integer> frogIds = ImmutableList.of(NpcID.GIANT_FROG_8700, NpcID.GIANT_FROG,NpcID.BIG_FROG_8701);
    private final List<Integer> fleshCrawlerIds = ImmutableList.of(NpcID.FLESH_CRAWLER_2499);
    private final List<Integer> hillGiantIds = ImmutableList.of(NpcID.HILL_GIANT,NpcID.HILL_GIANT_2099,NpcID.HILL_GIANT_2100
    ,NpcID.HILL_GIANT_2101,NpcID.HILL_GIANT_2102,NpcID.HILL_GIANT_2103);
    private final List<Integer> mossGiantIds = ImmutableList.of(NpcID.MOSS_GIANT, NpcID.MOSS_GIANT_2091, NpcID.MOSS_GIANT_2092,
            NpcID.MOSS_GIANT_2093);
    private final List<Integer> cyclopsIds = ImmutableList.of(NpcID.CYCLOPS_2463,NpcID.CYCLOPS_2464,NpcID.CYCLOPS_2465,
            NpcID.CYCLOPS_2466,NpcID.CYCLOPS_2467,NpcID.CYCLOPS_2468
            ,NpcID.CYCLOPS_2137,NpcID.CYCLOPS_2138,NpcID.CYCLOPS_2139,NpcID.CYCLOPS_2140,NpcID.CYCLOPS_2141,NpcID.CYCLOPS_2142);
    private final List<Integer> animatedArmourIds = ImmutableList.of(NpcID.ANIMATED_MITHRIL_ARMOUR,NpcID.ANIMATED_ADAMANT_ARMOUR);

    String cowName = "Cow";
    String giantFrogName = "Giant Frog";
    String hillGiantName = "Hill Giant";
    String fleshCrawlerName = "Flesh Crawler";
    String mossGiantName = "Moss Giant";
    String cyclopsName = "Cyclops";
    String animatedArmourName = "Animated Armour";

    public String monsterName = fleshCrawlerName;

    public List<Integer> targetIds = fleshCrawlerIds;

    public final List<Integer> foodIDs = ImmutableList.of(ItemID.SALMON, ItemID.LOBSTER, ItemID.SWORDFISH );

    public List<Integer> animateArmourObject = ImmutableList.of(ObjectID.MAGICAL_ANIMATOR);

    public Rectangle animateArmourObjectWorldLocation = new Rectangle(2856, 3535,3,3);

    @Provides
    AutoslayerConfig getConfig(ConfigManager configManager)
    {
        return configManager.getConfig(AutoslayerConfig.class);
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
    public void onNpcDespawned(NpcDespawned npcDespawned)
    {
        NPC npc = npcDespawned.getNpc();

        if (!npc.isDead())
        {
            return;
        }

        //if(activeTarget())
        //{
            //if(target.getIndex() == npc.getIndex())
            //{
               // System.out.println("target died");
            //}
        //}

    }

    @Subscribe
    public void onGameTick(GameTick t)
    {
        if (config.autoSlayer() && client.getGameState() == GameState.LOGGED_IN)
        {
            activeTarget();
            activeTargetGameObjectMethod();
            inventoryHidden.set(client.getWidget(WidgetInfo.INVENTORY).isHidden());
        }
        idleAnimation();
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event)
    {
        updateTargetNPCConfig();
        valueThreshold = config.lootValueThreshold();
    }

    public void updateTargetNPCConfig()
    {
        switch (config.autoslayerTarget())
        {
            case HILL_GIANTS:
                monsterName = hillGiantName;
                targetIds = hillGiantIds;
                core.slayerZone = core.hillGiantZone;
                core.slayerArea = core.hillGiantArea;
                break;
            case FLESH_CRAWLERS:
                monsterName = fleshCrawlerName;
                targetIds = fleshCrawlerIds;
                core.slayerZone = core.fleshCrawlerZone;
                core.slayerArea = core.fleshCrawlerArea;
                break;
            case MOSS_GIANTS:
                monsterName = mossGiantName;
                targetIds = mossGiantIds;
                core.slayerZone = core.mossGiantZone;
                core.slayerArea = core.mossGiantArea;
                break;
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

    public boolean activeTarget(){
        List<NPC> targetNPCs;
        targetNPCs = core.getNearbyNPC(targetIds);
        if(targetNPCs.isEmpty())
        {
            validActiveTarget.set(false);
            return false;
        }
        target = targetNPCs.get(0);
        targetCentre = new Point( (int) target.getConvexHull().getBounds2D().getCenterX(),
                (int) target.getConvexHull().getBounds2D().getCenterY());
        targetHull = target.getConvexHull().getBounds2D();
        if(targetCentre.getX() <= 0 || targetCentre.getY() <= 0)
        {
            targetCentre = new Point(650,450);
            validActiveTarget.set(false);
            return false;
        }
        validActiveTarget.set(true);
        return true;
    }

    public void activeTargetGameObjectMethod(){
        if(activeTargetGameObject.get()){
            activeTargetGameObjectCentre = new Point( (int) core.targetGameObject.getConvexHull().getBounds2D().getCenterX(),
                    (int) core.targetGameObject.getConvexHull().getBounds2D().getCenterY());
            activeTargetGameObjectHull = core.targetGameObject.getConvexHull().getBounds2D();
        }
    }

    public int rand(int lowerLimit, int upperLimit)
    {
        int range = upperLimit + 1 - lowerLimit;
        int subRand = r.nextInt(range);
        return subRand + lowerLimit;
    }

    public void checkHealth() throws AWTException, InterruptedException {
        Robot robot = new Robot();
        int hitpoints = client.getBoostedSkillLevel(Skill.HITPOINTS);
        String eat = "Eat";
        if(hitpoints < hitpointsThreshold){
            for (int tries = 0 ; tries < 2 ; tries++){
                core.moveMouseToInventory(robot, foodIDs);
                int randomDelay3 = rand(700, 900);
                Thread.sleep(randomDelay3);
                System.out.println(" leftClickMatchTargetIdentifier(foodIDs) && core.leftClickOption(eat) "
                        + core.leftClickMatchTargetIdentifier(foodIDs) + core.leftClickOption(eat));
                if (core.leftClickMatchTargetIdentifier(foodIDs) && core.leftClickOption(eat)) {
                    int randomDelay0 = rand(150, 300);
                    Thread.sleep(randomDelay0);
                    core.leftClick(robot);
                    int randomDelay1 = rand(450, 600);
                    Thread.sleep(randomDelay1);
                    core.moveMouseOutsideInventory(robot);
                    return;
                }
            }
        }
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
            Point tilePoint = Perspective.localToCanvas(client, valuableItemTile.getLocalLocation(),valuableItemTile.getPlane());
            if(!core.outsideWindow( tilePoint )){
                Point currentPosition = core.mouseCanvasLocation();
                double deltaX = Math.abs(currentPosition.getX() - tilePoint.getX());
                double deltaY = Math.abs(currentPosition.getY() - (tilePoint.getY() + canvasOffsetY));
                double tolerance = 5;
                if(deltaX > tolerance || deltaY > tolerance) {
                    core.moveMouseToTile(robot, valuableItemTile);
                }
                if(core.leftClickMatchTargetIdentifier(valuableTileItemID)){
                    core.leftClick(robot);
                    Thread.sleep(randomDelay);
                }
                else {
                    core.rightClick(robot);
                    Point itemPoint = core.rightClickMenuPoint(valuableTileItemID, pickUpLootText);
                    core.moveMouseToFixedPoint(robot,itemPoint);
                    if (AutoslayerCore.mouseArrived.get()) {
                        core.leftClick(robot);
                        Thread.sleep(randomDelay);
                    }
                }
            }
            else{
                core.moveMouseToWalk(robot,valuableItemTileWorldLocation);
                if (AutoslayerCore.mouseArrived.get()) {
                    core.leftClick(robot);
                    Thread.sleep(randomDelay);
                }
            }
        }
    }

    public void autoSlayer() throws InterruptedException, AWTException {
        Robot robot = new Robot();
        long idleTimeThreshold = rand(1800,4200);
        checkHealth();
        pickUpLoot();
        if( idleAnimationTime > idleTimeThreshold ){
            core.combatPotions();
            if( validActiveTarget.get() ) {
                if(core.pointInsideMiniMap(core.mouseCanvasLocation())){
                    core.moveMouseOutsideInventory(robot);
                    int randomDelay1 = rand(450, 600);
                    Thread.sleep(randomDelay1);
                }
                core.moveMouseToNPC(robot);
                String attack = "Attack";
                if (AutoslayerCore.mouseArrived.get() && core.leftClickOption(attack) && !target.isDead()) {
                    core.leftClick(robot);
                }
                else if (AutoslayerCore.mouseArrived.get()){
                    core.rightClick(robot);
                    System.out.println(" target.getId() " + target.getId() );
                    Point attackOption = core.rightClickMenuPoint(monsterName, attack);
                    core.moveMouseToFixedPoint(robot,attackOption);
                    if (AutoslayerCore.mouseArrived.get() && !target.isDead()) {
                        core.leftClick(robot);
                    }
                }
            }
            else if (core.playerOutsideTargetZone())
            {
                System.out.println(" walk ");
                core.moveMouseToWalk(robot, core.targetZonePoint());
                if (AutoslayerCore.mouseArrived.get()) {
                    core.leftClick(robot);
                }
            }
        }
        int sleepTime = core.randomDistoredDistributionInt(2000,20000,5000);
        System.out.println(" sleepTime " + sleepTime);
        Thread.sleep(sleepTime);
        adjustCamera();
    }

    public void autoTokens() throws InterruptedException, AWTException {
        Robot robot = new Robot();
        long idleTimeThreshold = rand(1500,4000);
        checkHealth();
        pickUpLoot();
        pickUpLoot();
        pickUpLoot();
        pickUpLoot();
        if( idleAnimationTime > idleTimeThreshold ){
            core.combatPotions();
            if( core.gameObjectFromTiles(animateArmourObject, animateArmourObjectWorldLocation) && core.isItemInInventory(ItemID.MITHRIL_FULL_HELM)
                    && core.isItemInInventory(ItemID.MITHRIL_PLATELEGS) && core.isItemInInventory(ItemID.MITHRIL_PLATEBODY)) {
                core.moveMouseToGameObject(robot);
                String animate = "Animate";
                if (AutoslayerCore.mouseArrived.get() && core.leftClickOption(animate)) {
                    core.leftClick(robot);
                }
                else if (AutoslayerCore.mouseArrived.get()){
                    core.rightClick(robot);
                    Point menuOption = core.rightClickMenuPoint(monsterName, animate);
                    core.moveMouseToFixedPoint(robot,menuOption);
                    if (AutoslayerCore.mouseArrived.get()) {
                        core.leftClick(robot);
                    }
                }
                AutoslayerPlugin.activeTargetGameObject.set(false);
            }
            else if ( validActiveTarget.get() ) {
                core.moveMouseToNPC(robot);
                String attack = "Attack";
                if (AutoslayerCore.mouseArrived.get() && core.leftClickOption(attack)) {
                    core.leftClick(robot);
                }
                else if (AutoslayerCore.mouseArrived.get()){
                    core.rightClick(robot);
                    System.out.println(" target.getId() " + target.getId() );
                    Point attackOption = core.rightClickMenuPoint(monsterName, attack);
                    core.moveMouseToFixedPoint(robot,attackOption);
                    if (AutoslayerCore.mouseArrived.get()) {
                        core.leftClick(robot);
                    }
                }
            }
            else if (core.playerOutsideTargetZone())
            {
                System.out.println(" walk ");
                core.moveMouseToWalk(robot, core.targetZonePoint());
                if (AutoslayerCore.mouseArrived.get()) {
                    core.leftClick(robot);
                }
            }
        }
        int sleepTime = core.randomDistoredDistributionInt(2000,20000,5000);
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
                if (config.autoSlayer() && client.getGameState() == GameState.LOGGED_IN && runTime < maxRunTime) {
                    autoSlayer();
                }
            }
        }
    }
    );

}
