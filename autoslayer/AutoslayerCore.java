package net.runelite.client.plugins.autoslayer;

import com.google.common.collect.ImmutableList;
import net.runelite.api.*;
import net.runelite.api.Point;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import javax.inject.Inject;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

public class AutoslayerCore {

    @Inject
    private Client client;

    @Inject
    private AutoslayerPlugin plugin;

    public static AtomicBoolean npcChanged = new AtomicBoolean();

    public static AtomicBoolean mouseArrived = new AtomicBoolean();

    public static int canvasOffsetY = 23;

    public String accountName = "vvvVein";

    public GameObject targetGameObject;

    Random r = new Random();

    public Rectangle cowZone = new Rectangle(3251, 3282,8,8);
    public Rectangle cowArea = new Rectangle(3244, 3274,3264 + 1 - 3245,3296 + 1 - 3274);

    public Rectangle frogZone = new Rectangle(3195, 3176,6,6);
    public Rectangle frogArea = new Rectangle(3194, 3170,3208 + 1 - 3194,3196 + 1 - 3170);

    public Rectangle fleshCrawlerZone = new Rectangle(2040, 5188,5,4);
    public Rectangle fleshCrawlerArea = new Rectangle(2037, 5185,2046 + 1 - 2037,5193 + 1 - 5185);

    public Rectangle mossGiantZone = new Rectangle(2551, 3402,7,7);
    public Rectangle mossGiantArea = new Rectangle(2548, 3399,2561 + 1 - 2548,3413 + 1 - 3399);

    public Rectangle mossGiantZone1 = new Rectangle(3156, 9894,4,5);
    public Rectangle mossGiantArea1 = new Rectangle(3151, 9876,3173 + 1 - 3151,9911 + 1 - 9876);

    public Rectangle hillGiantZone = new Rectangle(3108, 9833,11,12);
    public Rectangle hillGiantArea = new Rectangle(3095, 9822,3126 + 1 - 3095,9854 + 1 - 9822);

    public Rectangle cyclopsZone = new Rectangle(2856, 3542,10,7);
    public Rectangle cyclopsArea = new Rectangle(2838, 3534,2876 + 1 - 2838,3557 + 1 - 3534);

    public Rectangle basementCyclopsZone = new Rectangle(2920, 9961,10,8);
    public Rectangle basementCyclopsArea = new Rectangle(2905, 9957,2940 + 1 - 2905,9973 + 1 - 9957);

    public Rectangle animatedArmourZone = new Rectangle(2856, 3538,2,2);
    public Rectangle animatedArmourArea = new Rectangle(2849, 3534,2861 + 1 - 2849,3545 + 1 - 3534);

    public Rectangle slayerZone = fleshCrawlerZone;
    public Rectangle slayerArea = fleshCrawlerArea;

    public final List<Integer> superAttackPotionIDs = ImmutableList.of(ItemID.SUPER_ATTACK1, ItemID.SUPER_ATTACK2,
            ItemID.SUPER_ATTACK3,ItemID.SUPER_ATTACK4);
    public final List<Integer> superStrengthPotionIDs = ImmutableList.of(ItemID.SUPER_STRENGTH1, ItemID.SUPER_STRENGTH2,
            ItemID.SUPER_STRENGTH3,ItemID.SUPER_STRENGTH4);
    public final List<Integer> strengthPotionIDs = ImmutableList.of(ItemID.STRENGTH_POTION1, ItemID.STRENGTH_POTION2,
            ItemID.STRENGTH_POTION3,ItemID.STRENGTH_POTION4);
    public final List<Integer> superDefencePotionIDs = ImmutableList.of(ItemID.SUPER_DEFENCE1, ItemID.SUPER_DEFENCE2,
            ItemID.SUPER_DEFENCE3,ItemID.SUPER_DEFENCE4);

    public int rand(int lowerLimit, int upperLimit)
    {
        int range = upperLimit + 1 - lowerLimit;
        int subRand = r.nextInt(range);
        return subRand + lowerLimit;
    }

    public int randomDistoredDistributionInt (double lowerBound, double upperBound, double centralValue)
    {
        double range = upperBound - lowerBound;
        double randd = r.nextGaussian() * range / 4 + centralValue;
        int rand = (int) randd;
        if(rand<lowerBound){rand = (int) lowerBound + rand(0,(int)range/10);}
        if(rand>upperBound){rand = (int) upperBound - rand(0,(int)range/10);}
        return rand;
    }

    public boolean leftClickOption(String testText) {
        MenuEntry[] options = client.getMenuEntries();
        MenuEntry firstOption;
        if (options.length == 0) {
            return testText.matches("Walk here");
        }
        firstOption = options[options.length - 1];
        if (firstOption != null) {
            if (firstOption.getOption() != null) {
                return firstOption.getOption().matches(testText);
            }
        }
        return false;
    }

    private boolean isInViewRange(WorldPoint WPTarget)
    {
        WorldPoint playerWorldLocation = client.getLocalPlayer().getWorldLocation();
        int distance = playerWorldLocation.distanceTo(WPTarget);
        int MAX_ACTOR_VIEW_RANGE = 12;
        return distance < MAX_ACTOR_VIEW_RANGE;
    }

    public class npcDistanceComparator implements Comparator<NPC> {
        @Override
        public int compare(NPC npc1, NPC npc2) {
            WorldPoint playerWL = client.getLocalPlayer().getWorldLocation();
            double dist1 = npc1.getWorldLocation().distanceTo(playerWL);
            double dist2 = npc2.getWorldLocation().distanceTo(playerWL);
            return (int)(dist1 - dist2);
        }
    }

    public class gameObjectDistanceComparator implements Comparator<GameObject> {
        public int compare(GameObject object1, GameObject object2) {
            WorldPoint playerWL = client.getLocalPlayer().getWorldLocation();
            double dist1 = object1.getWorldLocation().distanceTo(playerWL);
            double dist2 = object2.getWorldLocation().distanceTo(playerWL);
            return (int)(dist1 - dist2);
        }
    }

    public List<NPC> getNearbyNPC(List<Integer> npcID) {

        List<NPC> targetNpc = new ArrayList<>();
        List<NPC> targetNpcs = new ArrayList<>();

        if(AutoslayerPlugin.validActiveTarget.get()) {
            if (!outsideWindow(AutoslayerPlugin.targetCentre)) {
                for (NPC npc : client.getNpcs()) {
                        if (npcID.contains(npc.getId())
                                && !npc.isDead()
                                && isInViewRange( npc.getWorldLocation())
                                && ((npc.getInteracting() == null && npc.getHealthRatio() == -1 ) || npc.getInteracting().getName().matches(accountName))
                                && AutoslayerPlugin.getTarget().getIndex() == npc.getIndex()
                        && slayerArea.contains(npc.getWorldLocation().getX(),npc.getWorldLocation().getY()) ) {
                            targetNpc.add(npc);
                            npcChanged.set(false);
                            return targetNpc;
                    }
                }
            }
        }

        for (NPC npc : client.getNpcs()) {
            if (npcID.contains(npc.getId())
                    && !npc.isDead()
                    && isInViewRange( npc.getWorldLocation())
                    && slayerArea.contains(npc.getWorldLocation().getX(),npc.getWorldLocation().getY())
                    && ((npc.getInteracting() == null && npc.getHealthRatio() == -1 ) || npc.getInteracting().getName().matches(accountName)))
            {

                System.out.println();
                targetNpcs.add(npc);
            }
        }

        if(targetNpcs.isEmpty())
        {
            return targetNpc;
        }
        targetNpcs.sort(new npcDistanceComparator());
        targetNpc.add(targetNpcs.get(0));
        npcChanged.set(true);
        return targetNpc;
    }

    public boolean playerOutsideTargetZone()
    {
        WorldPoint playerWorldLocation = client.getLocalPlayer().getWorldLocation();
        return !slayerZone.contains(playerWorldLocation.getX(), playerWorldLocation.getY());
    }

    public WorldPoint targetZonePoint()
    {
        int x = slayerZone.x + rand(0,slayerZone.width);
        int y = slayerZone.y + rand(0,slayerZone.height);
        return new WorldPoint(x,y,client.getPlane());
    }

    public Point mouseCanvasLocation() {
        return new Point(MouseInfo.getPointerInfo().getLocation().x, MouseInfo.getPointerInfo().getLocation().y);
    }

    public double rms(double x, double y)
    {
        return Math.sqrt(x*x+y*y);
    }

    public double reverseRootSumSq ( double c, double a)
    {
        return Math.sqrt(c*c-a*a);
    }

    public double randomDistributionDouble (double lowerBound, double upperBound)
    {
        double range = upperBound - lowerBound;
        double centralValue = lowerBound + range/2;
        double rand = r.nextGaussian() * range / 4 + centralValue;
        if(rand<lowerBound){rand = lowerBound;}
        if(rand>upperBound){rand = upperBound;}
        return rand;
    }

    public boolean outsideWindow(Point point) {
        double pointX = point.getX();
        double pointY = point.getY();
        Rectangle window1 = new Rectangle(5, 7 + canvasOffsetY, (1420 - 3), (841 - 26));
        Rectangle rightWindow = new Rectangle(1418, 200, (1630 - 1418), (694 - 200));
        Rectangle bottomWindow = new Rectangle(530, 840, (1420 - 530), (1004 - 840));
        Rectangle bottomRightArea = new Rectangle(1218, 703, 1434 + 1 - 1218, 970 + 1 - 703);
        if (window1.contains(pointX, pointY) || rightWindow.contains(pointX, pointY)
                || bottomWindow.contains(pointX, pointY) || bottomRightArea.contains(pointX, pointY))
        {
            return false;
        }
        else
        {
            return true;
        }
    }

    public static boolean outsideEntireWindow(Point point) {
        double pointX = point.getX();
        double pointY = point.getY();
        Rectangle window = new Rectangle(5, 7 + canvasOffsetY, 1630, 1004);
        if (window.contains(pointX, pointY))
        {
            return false;
        }
        else
        {
            return true;
        }
    }

    public int randomDistributionInt (double lowerBound, double upperBound)
    {
        double range = upperBound - lowerBound;
        double centralValue = lowerBound + range / 2;
        double randd = r.nextGaussian() * range / 4 + centralValue;
        int rand = (int) randd;
        if(rand<lowerBound){rand = (int) lowerBound;}
        if(rand>upperBound){rand = (int) upperBound;}
        return rand;
    }

    public boolean leftClickMatchTargetIdentifier(int targetIdentifier) {
        MenuEntry[] options = client.getMenuEntries();
        MenuEntry firstOption;
        if (options.length == 0) {
            return false;
        }
        firstOption = options[options.length - 1];
        if (firstOption != null) {
            if (firstOption.getOption() != null) {
                if (firstOption.getIdentifier() == targetIdentifier && firstOption.getOption().equals("Take")) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean leftClickMatchTargetIdentifier( List<Integer> targetIdentifiers) {
        MenuEntry[] options = client.getMenuEntries();
        MenuEntry firstOption;
        if (options.length == 0) {
            return false;
        }
        firstOption = options[options.length - 1];
        if (firstOption != null) {
            if (firstOption.getOption() != null) {
                if (targetIdentifiers.contains(firstOption.getIdentifier()) ) {
                    return true;
                }
            }
        }
        return false;
    }

    public int rightClickMenuIndex(int targetIdentifier, String targetString) {
        if (client.getMenuEntries() == null) {
            return 0;
        }
        MenuEntry[] menuOptions = client.getMenuEntries();
        int menuSize = menuOptions.length;
        int optionFromBottom = 0;
        int optionIndex;
        for (MenuEntry option : menuOptions) {
            if (option.getIdentifier() == targetIdentifier && option.getOption().equals(targetString)) {
                optionIndex = menuSize - optionFromBottom;
                return optionIndex;
            }
            optionFromBottom = optionFromBottom + 1;
        }
        return 0;
    }

    public int rightClickMenuIndex(String targetIdentifier, String targetString) {
        if (client.getMenuEntries() == null) {
            return 0;
        }
        MenuEntry[] menuOptions = client.getMenuEntries();
        int menuSize = menuOptions.length;
        int optionFromBottom = 0;
        int optionIndex;
        for (MenuEntry option : menuOptions) {
            if (option.getTarget().contains(targetIdentifier) && option.getOption().equals(targetString)) {
                optionIndex = menuSize - optionFromBottom;
                return optionIndex;
            }
            optionFromBottom = optionFromBottom + 1;
        }
        return 0;
    }

    public Point rightClickMenuPoint(int targetIdentifier, String targetString) {
        Point startPosition = mouseCanvasLocation();
        int startX = startPosition.getX();
        int startY = startPosition.getY();
        int baseYOffset = 4; /// + canvasOffsetY;
        int rowOffset = 15;
        int xTolerance = 35;
        int yTolerance = 3;
        int randomX = rand(-xTolerance, xTolerance);
        int randomY = rand(-yTolerance, yTolerance);
        int menuIndex = rightClickMenuIndex(targetIdentifier, targetString);
        int menuY = startY + baseYOffset + (menuIndex - 1) * rowOffset + randomY;
        if (menuIndex == 0) {
            menuY = startY + baseYOffset + randomY - 25;
        }
        int menuX = startX + randomX;
        Point MenuIndexPoint = new Point(menuX, menuY);
        return MenuIndexPoint;
    }

    public Point rightClickMenuPoint(String targetIdentifier, String targetString) {
        Point startPosition = mouseCanvasLocation();
        int startX = startPosition.getX();
        int startY = startPosition.getY();
        int baseYOffset = 4; /// + canvasOffsetY;
        int rowOffset = 15;
        int xTolerance = 35;
        int yTolerance = 3;
        int randomX = rand(-xTolerance, xTolerance);
        int randomY = rand(-yTolerance, yTolerance);
        int menuIndex = rightClickMenuIndex(targetIdentifier, targetString);
        int menuY = startY + baseYOffset + (menuIndex - 1) * rowOffset + randomY;
        if (menuIndex == 0) {
            menuY = startY + baseYOffset + randomY - 25;
        }
        int menuX = startX + randomX;
        Point MenuIndexPoint = new Point(menuX, menuY);
        return MenuIndexPoint;
    }

    public int availableInventory() {
        int availableInventory = 0;
        if (client.getItemContainer(InventoryID.INVENTORY) == null) {
            return 28;
        }
        if (client.getItemContainer(InventoryID.INVENTORY).getItems().length != 28) {
            for (Item item : client.getItemContainer(InventoryID.INVENTORY).getItems()) {
                if (item.getId() == -1) {
                    availableInventory = availableInventory + 1;
                }
            }
            availableInventory = availableInventory + 28 - client.getItemContainer(InventoryID.INVENTORY).getItems().length;
            return availableInventory;
        }
        for (Item item : client.getItemContainer(InventoryID.INVENTORY).getItems()) {
            if (item.getId() == -1) {
                availableInventory = availableInventory + 1;
            }
        }
        return availableInventory;
    }

    public Point worldPointToCanvas(WorldPoint worldPoint)
    {
        LocalPoint localPoint = LocalPoint.fromWorld(client,worldPoint);
        return Perspective.localToCanvas(client,localPoint,client.getPlane());
    }

    public boolean pointInsideMiniMap(Point point) {
        double minimapX = client.getWidget(net.runelite.api.widgets.WidgetInfo.RESIZABLE_MINIMAP_DRAW_AREA).getBounds().getCenterX();
        double minimapY = client.getWidget(net.runelite.api.widgets.WidgetInfo.RESIZABLE_MINIMAP_DRAW_AREA).getBounds().getCenterY() + canvasOffsetY;
        System.out.println( " minimapX " + minimapX);
        System.out.println( " minimapY " + minimapY);
        double minimapRadius = 73;
        // 75
        double deltaX = Math.abs(minimapX - point.getX());
        double deltaY = Math.abs(minimapY - point.getY());
        double deltaXSQ = Math.pow(deltaX, 2);
        double deltaYSQ = Math.pow(deltaY, 2);
        double Hypotenuse = Math.sqrt(deltaXSQ + deltaYSQ);

        double WorldMinimapX = client.getWidget(net.runelite.api.widgets.WidgetInfo.MINIMAP_WORLDMAP_ORB).getBounds().getCenterX();
        double WorldMinimapY = client.getWidget(net.runelite.api.widgets.WidgetInfo.MINIMAP_WORLDMAP_ORB).getBounds().getCenterY() + canvasOffsetY;
        double WorldMinimapRadius = 18;
        double deltaX1 = Math.abs(WorldMinimapX - point.getX());
        double deltaY1 = Math.abs(WorldMinimapY - point.getY());
        double deltaXSQ1 = Math.pow(deltaX1, 2);
        double deltaYSQ1 = Math.pow(deltaY1, 2);
        double Hypotenuse1 = Math.sqrt(deltaXSQ1 + deltaYSQ1);

        if (Hypotenuse < minimapRadius && Hypotenuse1 > WorldMinimapRadius)
        {
            return true;
        }
        return false;
    }

    public Point worldPointToMiniMap(WorldPoint worldPoint)
    {
        WorldPoint playerWorldLocation = client.getLocalPlayer().getWorldLocation();
        System.out.println( " playerWorldLocation " + playerWorldLocation);
        if(playerWorldLocation.distanceTo(worldPoint) > 15)
        {
            double bearing = bearingWorldLocation(playerWorldLocation,worldPoint);
            double bearingQuadrantAngle = bearingQuadrantAngle(bearing);
            double signX = 0;
            double signY = 0;
            if(bearing <= 90)
            {
                signX = +1;
                signY = +1;
            }
            if(bearing > 90 && bearing <= 180)
            {
                signX = +1;
                signY = -1;
            }
            if(bearing > 180 && bearing <= 270)
            {
                signX = -1;
                signY = -1;
            }
            if(bearing > 270 && bearing <= 360)
            {
                signX = -1;
                signY = +1;
            }
            double sectorLength = (double) rand(120,140) / 10;
            double dX = signX * Math.min(Math.abs(sectorLength * Math.cos(Math.toRadians(bearingQuadrantAngle))),15);
            double dY = signY * Math.min(Math.abs(sectorLength * Math.sin(Math.toRadians(bearingQuadrantAngle))),15);
            WorldPoint interimWorldPoint = new WorldPoint( playerWorldLocation.getX() + (int) dX , playerWorldLocation.getY() + (int) dY , client.getPlane());
            System.out.println( " interimWorldPoint " + interimWorldPoint);
            LocalPoint localPoint = LocalPoint.fromWorld(client,interimWorldPoint);
            System.out.println( " localPoint " + localPoint);
            Point interimCanvasPoint = Perspective.localToMinimap(client,localPoint);
            System.out.println( " interimCanvasPoint " + interimCanvasPoint);
            if(pointInsideMiniMap(interimCanvasPoint))
            {
                return interimCanvasPoint;
            }
            System.out.println(" worldPointToMiniMap calculation error ");
            localPoint = LocalPoint.fromWorld(client,interimWorldPoint);
            interimCanvasPoint = Perspective.localToMinimap(client,localPoint);
            return interimCanvasPoint;
        }
        LocalPoint localPoint = LocalPoint.fromWorld(client,worldPoint);
        return Perspective.localToMinimap(client,localPoint);
    }

    public void leftClick(Robot robot) {
        robot.delay(randomDistributionInt(10,50));
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.delay(rand(150, 400));
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        robot.delay(rand(50, 200));
    }

    public void rightClick(Robot robot) {
        robot.delay(randomDistributionInt(10,50));
        robot.mousePress(InputEvent.BUTTON3_DOWN_MASK);
        robot.delay(rand(150, 400));
        robot.mouseRelease(InputEvent.BUTTON3_DOWN_MASK);
        robot.delay(rand(500, 700));
    }

    public void openInventory() throws AWTException, InterruptedException {
        Robot robot = new Robot();
        boolean inventoryHidden = AutoslayerPlugin.inventoryHidden.get();
        if (inventoryHidden) {
            int randomDelay0 = rand(400, 600);
            Thread.sleep(randomDelay0);
            robot.keyPress(KeyEvent.VK_ESCAPE);
            int randomDelay1 = rand(400, 600);
            Thread.sleep(randomDelay1);
            robot.keyRelease(KeyEvent.VK_ESCAPE);
            int randomDelay2 = rand(900, 1400);
            Thread.sleep(randomDelay2);
        }
    }

    public boolean gameObjectFromTiles (List <Integer> targetIDs, Rectangle targetArea) {
        List<GameObject> matchedTargets = new ArrayList<>();
        Tile[][][] tiles = client.getScene().getTiles();
        for (Tile[][] outerTileArray : tiles) {
            for (Tile[] innerTileArray : outerTileArray) {
                for (Tile tile : innerTileArray) {
                    if (tile != null) {
                        if (tile.getWorldLocation() != null) {
                            if (client.getLocalPlayer().getWorldLocation().distanceTo(tile.getWorldLocation()) < 7 &&
                                    targetArea.contains(tile.getWorldLocation().getX(),tile.getWorldLocation().getY())) {
                                for (GameObject gameObject : tile.getGameObjects()) {
                                    if (gameObject != null) {
                                        if (targetIDs.contains(gameObject.getId()) ) {
                                            matchedTargets.add(gameObject);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        if(matchedTargets.isEmpty()){
            AutoslayerPlugin.activeTargetGameObject.set(false);
            return false;
        }
        matchedTargets.sort(new gameObjectDistanceComparator());
        targetGameObject = matchedTargets.get(0);
        AutoslayerPlugin.activeTargetGameObjectCentre = new Point( (int) targetGameObject.getConvexHull().getBounds2D().getCenterX(),
                (int) targetGameObject.getConvexHull().getBounds2D().getCenterY());
        AutoslayerPlugin.activeTargetGameObjectHull = targetGameObject.getConvexHull().getBounds2D();
        AutoslayerPlugin.activeTargetGameObject.set(true);
        return true;
    }

    public void combatPotions() throws InterruptedException, AWTException {
        Robot robot = new Robot();
        String drink = "Drink";
        int boostedAttack = client.getBoostedSkillLevel(Skill.ATTACK);
        int attackLevel = client.getRealSkillLevel(Skill.ATTACK);
        int boostedStrength = client.getBoostedSkillLevel(Skill.STRENGTH);
        int strengthLevel = client.getRealSkillLevel(Skill.STRENGTH);
        int boostedDefence = client.getBoostedSkillLevel(Skill.DEFENCE);
        int defenceLevel = client.getRealSkillLevel(Skill.DEFENCE);
        if (numberOfItemInInventory(superAttackPotionIDs) >= 1 && boostedAttack <= attackLevel
                || numberOfItemInInventory(superStrengthPotionIDs) >= 1 && boostedStrength <= strengthLevel
                || numberOfItemInInventory(superDefencePotionIDs) >= 1 && boostedDefence <= defenceLevel) {
            openInventory();
            if(!client.getWidget(net.runelite.api.widgets.WidgetInfo.INVENTORY).isHidden()) {
                if (numberOfItemInInventory(superAttackPotionIDs) >= 1 && boostedAttack <= attackLevel) {
                    for (int tries = 0 ; tries < 2 ; tries++) {
                        moveMouseToInventory(robot, superAttackPotionIDs);
                        int randomDelay3 = rand(700, 1000);
                        Thread.sleep(randomDelay3);
                        if (leftClickOption(drink) && leftClickMatchTargetIdentifier(superAttackPotionIDs)) {
                            int randomDelay0 = rand(300, 500);
                            Thread.sleep(randomDelay0);
                            leftClick(robot);
                            int randomDelay1 = rand(700, 1000);
                            Thread.sleep(randomDelay1);
                            break;
                        }
                    }
                }
                if (numberOfItemInInventory(superStrengthPotionIDs) >= 1 && boostedStrength <= strengthLevel) {
                    for (int tries = 0 ; tries < 2 ; tries++) {
                        moveMouseToInventory(robot, superStrengthPotionIDs);
                        int randomDelay3 = rand(700, 1000);
                        Thread.sleep(randomDelay3);
                        if (leftClickOption(drink) && leftClickMatchTargetIdentifier(superStrengthPotionIDs)) {
                            int randomDelay0 = rand(300, 500);
                            Thread.sleep(randomDelay0);
                            leftClick(robot);
                            int randomDelay1 = rand(700, 1000);
                            Thread.sleep(randomDelay1);
                            break;
                        }
                    }
                }
                if (numberOfItemInInventory(superDefencePotionIDs) >= 1 && boostedDefence <= defenceLevel) {
                    for (int tries = 0 ; tries < 2 ; tries++) {
                        moveMouseToInventory(robot, superDefencePotionIDs);
                        int randomDelay3 = rand(700, 1000);
                        Thread.sleep(randomDelay3);
                        if (leftClickOption(drink) && leftClickMatchTargetIdentifier(superDefencePotionIDs)) {
                            int randomDelay0 = rand(300, 500);
                            Thread.sleep(randomDelay0);
                            leftClick(robot);
                            int randomDelay1 = rand(700, 1000);
                            Thread.sleep(randomDelay1);
                            break;
                        }
                    }
                }
                moveMouseOutsideInventory(robot);
                int randomDelay2 = rand(700, 1200);
                Thread.sleep(randomDelay2);
            }
        }
    }

    public double bearing(Point from, Point to)
    {
        double dX = to.getX() - from.getX();
        double dY = to.getY() - from.getY();
        double signX = 1;
        double signY = 1;
        double mew = - 1000;
        if(dX < 0)
        {
            signX=-1;
        }
        if(dY < 0)
        {
            signY=-1;
        }

        /// Q1
        if( signX == +1 && signY == -1)
        {
            mew = Math.abs(Math.toDegrees(Math.atan(dX / dY)));
            return mew;
        }
        /// Q2
        if( signX == +1 && signY == +1)
        {
            mew = Math.abs(Math.toDegrees(Math.atan(dY / dX))) + 90;
            return mew;
        }
        /// Q3
        if( signX == -1 && signY == +1)
        {
            mew = Math.abs(Math.toDegrees(Math.atan(dX / dY))) + 180;
            return mew;
        }
        /// Q4
        if( signX == -1 && signY == -1)
        {
            mew = Math.abs(Math.toDegrees(Math.atan(dY / dX))) + 270;
            return mew;
        }

        return mew;
    }

    public double bearingWorldLocation(WorldPoint from, WorldPoint to)
    {
        double dX = to.getX() - from.getX();
        double dY = to.getY() - from.getY();
        double signX = 1;
        double signY = 1;
        double mew = - 1000;
        if(dX < 0)
        {
            signX=-1;
        }
        if(dY < 0)
        {
            signY=-1;
        }

        /// Q1
        if( signX == +1 && signY == +1)
        {
            mew = Math.abs(Math.toDegrees(Math.atan(dX / dY)));
            return mew;
        }
        /// Q2
        if( signX == +1 && signY == -1)
        {
            mew = Math.abs(Math.toDegrees(Math.atan(dY / dX))) + 90;
            return mew;
        }
        /// Q3
        if( signX == -1 && signY == -1)
        {
            mew = Math.abs(Math.toDegrees(Math.atan(dX / dY))) + 180;
            return mew;
        }
        /// Q4
        if( signX == -1 && signY == +1)
        {
            mew = Math.abs(Math.toDegrees(Math.atan(dY / dX))) + 270;
            return mew;
        }

        return mew;
    }

    public double bearingQuadrantAngle(double bearing)
    {
        double phi = -900;
        /// Q1
        if( bearing < 90)
        {
            phi = 90 - bearing;
            return phi;
        }
        /// Q2
        if( bearing < 180)
        {
            phi = bearing - 90;
            return phi;
        }
        /// Q3
        if( bearing < 270)
        {
            phi = 270 - bearing;
            return phi;
        }
        /// Q4
        if( bearing < 361)
        {
            phi = bearing - 270;
            return phi;
        }
        return phi;
    }

    public void moveMouseToNPC(Robot robot) throws InterruptedException {
        mouseArrived.set(false);
        if(outsideWindow(AutoslayerPlugin.targetCentre) || !AutoslayerPlugin.validActiveTarget.get())
        {
            return;
        }
        Point startPosition = mouseCanvasLocation();
        double startX = startPosition.getX();
        double startY = startPosition.getY();
        System.out.println(" Start position " + startPosition);
        double adjustmentLimitX = 5;
        double adjustmentLimitY = 5;
        double toleranceX = adjustmentLimitX + 3;
        double toleranceY = adjustmentLimitY + 3;
        final double adjustmentX = rand(-(int)adjustmentLimitX,(int)adjustmentLimitX);
        final double adjustmentY = rand(-(int)adjustmentLimitY,(int)adjustmentLimitY);
        double finalX = AutoslayerPlugin.targetCentre.getX() + adjustmentX;
        double finalY = AutoslayerPlugin.targetCentre.getY() + adjustmentY + canvasOffsetY;
        Point finalP = new Point((int) finalX,(int) finalY);
        System.out.println(" Final position " + finalP);
        double trueBearing = bearing(startPosition,finalP);
        double startDiscrepancy = rand(-30,30);
        double startBearing = trueBearing + startDiscrepancy;
        double sectorLength = (double) rand(600,800) / 100;
        double bearingQuadrantAngle = bearingQuadrantAngle(startBearing);
        int signX = 0;
        int signY = 0;
        if(startBearing <= 90)
        {
            signX = 1;
            signY = -1;
        }
        if(startBearing > 90 && startBearing <= 180)
        {
            signX = 1;
            signY = 1;
        }
        if(startBearing > 180 && startBearing <= 270)
        {
            signX = -1;
            signY = 1;
        }
        if(startBearing > 270 && startBearing <= 360)
        {
            signX = -1;
            signY = -1;
        }
        double dX = signX * sectorLength * Math.cos(Math.toRadians(bearingQuadrantAngle));
        double dY = signY * sectorLength * Math.sin(Math.toRadians(bearingQuadrantAngle));
        int nextX = (int) (startX + dX);
        int nextY = (int) (startY + dY);
        if(rms(dX,dY) > 16)
        {
            return;
        }
        double remainingDistance = rms( finalX - startX, finalY - startY);
        double randomStartVelocityAdjustmentFactor = (double) rand(70,80) / 100;
        double baseVelocity = 0.42 + 0.04 * Math.pow(remainingDistance,0.53);
        double startVelocity = randomStartVelocityAdjustmentFactor * baseVelocity;
        double travelTime = sectorLength / startVelocity;

        robot.mouseMove( nextX, nextY );
        Thread.sleep( (long) travelTime);

        int currentX = nextX;
        int currentY = nextY;
        Point currentPosition = new Point( currentX,currentY);
        double previousBearing = startBearing;
        double previousRemainingDistance = remainingDistance;
        double previousVelocity = startVelocity;
        int numberOfSteps = 0;
        int overShootCounter = 0;
        int offTargetCounter = 0;
        while(remainingDistance > rms(toleranceX,toleranceY) && numberOfSteps < 220 )
        {
            numberOfSteps++;
            finalX = AutoslayerPlugin.targetCentre.getX() + adjustmentX;
            finalY = AutoslayerPlugin.targetCentre.getY() + adjustmentY + canvasOffsetY;
            if(outsideWindow(AutoslayerPlugin.targetCentre) || AutoslayerCore.npcChanged.get())
            {
                AutoslayerCore.npcChanged.set(false);
                return;
            }
            finalP = new Point((int) finalX,(int) finalY);
            trueBearing = bearing(currentPosition,finalP);
            int bearingCorrectionSign = 0;
            if(trueBearing > previousBearing)
            {
                bearingCorrectionSign = +1;
            }
            if(trueBearing < previousBearing)
            {
                bearingCorrectionSign = -1;
            }
            double bearingCorrection = (double) rand(-20,100) / 10;
            double bearing = previousBearing + bearingCorrection * bearingCorrectionSign;
            double bearingDelta = Math.abs(trueBearing - bearing);
            remainingDistance = rms(finalX - currentX,finalY - currentY);
            if(bearingDelta <= 12 && remainingDistance < 200)
            {
                bearingCorrection = (double) rand(0,30) / 10;
                bearing = previousBearing + bearingCorrection * bearingCorrectionSign;
                bearingDelta = Math.abs(trueBearing - bearing);
            }
            if(bearingDelta > 12 && remainingDistance < 200)
            {
                bearingCorrection = rand((int) bearingDelta / 10,(int) bearingDelta / 4);
                bearing = previousBearing + bearingCorrection * bearingCorrectionSign;
                bearingDelta = Math.abs(trueBearing - bearing);
            }
            if(bearingDelta > 80 )
            {
                if(offTargetCounter > 5)
                {
                    if(AutoslayerPlugin.targetHull.contains(currentX,currentY)){
                        System.out.println(" targetHull.contains " );
                        mouseArrived.set(true);
                        return;
                    }
                    System.out.println(" offTargetCounter " + offTargetCounter);
                    return;
                }
                offTargetCounter++;
            }
            if(bearingDelta > 150 )
            {
                if(remainingDistance < 100 && overShootCounter < 2 ) {
                    double discrepancy = rand(-12, 12);
                    bearing = trueBearing + discrepancy;
                    bearingDelta = Math.abs(trueBearing - bearing);
                }
                if(overShootCounter > 1 )
                {
                    if(AutoslayerPlugin.targetHull.contains(currentX,currentY)){
                        System.out.println(" targetHull.contains " );
                        mouseArrived.set(true);
                        return;
                    }
                    System.out.println(" overShootCounter " + overShootCounter);
                    return;
                }
                overShootCounter++;
            }
            sectorLength = (double) rand(600,800) / 100;
            bearingQuadrantAngle = bearingQuadrantAngle(bearing);
            signX = 0;
            signY = 0;
            if(bearing <= 90)
            {
                signX = 1;
                signY = -1;
            }
            if(bearing > 90 && bearing <= 180)
            {
                signX = 1;
                signY = 1;
            }
            if(bearing > 180 && bearing <= 270)
            {
                signX = -1;
                signY = 1;
            }
            if(bearing > 270 && bearing <= 360)
            {
                signX = -1;
                signY = -1;
            }
            dX = signX * sectorLength * Math.cos(Math.toRadians(bearingQuadrantAngle));
            dY = signY * sectorLength * Math.sin(Math.toRadians(bearingQuadrantAngle));
            nextX = (int) (currentX + dX);
            nextY = (int) (currentY + dY);
            Point nextPosition = new Point( nextX, nextY);
            if(sectorLength > 16 || outsideWindow(nextPosition))
            {
                return;
            }
            baseVelocity = 0.42 + 0.04 * Math.pow(remainingDistance,0.53);
            double accelerationFactor = Math.min(0.8, 4 / ( Math.pow(remainingDistance,0.5) + 0.1 ) );
            double acceleration = (baseVelocity - previousVelocity) * accelerationFactor;
            double velocity = previousVelocity + acceleration;
            travelTime = sectorLength / velocity;

            robot.mouseMove( nextX, nextY );
            Thread.sleep( (long) travelTime);

            currentX = nextX;
            currentY = nextY;
            currentPosition = new Point( currentX, currentY);
            previousBearing = bearing;
            previousRemainingDistance = remainingDistance;
            previousVelocity = velocity;
        }
        mouseArrived.set(true);
    }

    public void moveMouseToGameObject(Robot robot) throws InterruptedException {
        mouseArrived.set(false);
        if(outsideWindow(AutoslayerPlugin.activeTargetGameObjectCentre) || !AutoslayerPlugin.activeTargetGameObject.get())
        {
            return;
        }
        Point startPosition = mouseCanvasLocation();
        double startX = startPosition.getX();
        double startY = startPosition.getY();
        System.out.println(" Start position " + startPosition);
        double adjustmentLimitX = 5;
        double adjustmentLimitY = 5;
        double toleranceX = adjustmentLimitX + 4;
        double toleranceY = adjustmentLimitY + 4;
        final double adjustmentX = rand(-(int)adjustmentLimitX,(int)adjustmentLimitX);
        final double adjustmentY = rand(-(int)adjustmentLimitY,(int)adjustmentLimitY);
        double finalX = AutoslayerPlugin.activeTargetGameObjectCentre.getX() + adjustmentX;
        double finalY = AutoslayerPlugin.activeTargetGameObjectCentre.getY() + adjustmentY + canvasOffsetY;
        Point finalP = new Point((int) finalX,(int) finalY);
        System.out.println(" Final position " + finalP);
        double trueBearing = bearing(startPosition,finalP);
        double startDiscrepancy = rand(-30,30);
        double startBearing = trueBearing + startDiscrepancy;
        double sectorLength = (double) rand(600,800) / 100;
        double bearingQuadrantAngle = bearingQuadrantAngle(startBearing);
        int signX = 0;
        int signY = 0;
        if(startBearing <= 90)
        {
            signX = 1;
            signY = -1;
        }
        if(startBearing > 90 && startBearing <= 180)
        {
            signX = 1;
            signY = 1;
        }
        if(startBearing > 180 && startBearing <= 270)
        {
            signX = -1;
            signY = 1;
        }
        if(startBearing > 270 && startBearing <= 360)
        {
            signX = -1;
            signY = -1;
        }
        double dX = signX * sectorLength * Math.cos(Math.toRadians(bearingQuadrantAngle));
        double dY = signY * sectorLength * Math.sin(Math.toRadians(bearingQuadrantAngle));
        int nextX = (int) (startX + dX);
        int nextY = (int) (startY + dY);
        if(rms(dX,dY) > 16)
        {
            return;
        }
        double remainingDistance = rms( finalX - startX, finalY - startY);
        double randomStartVelocityAdjustmentFactor = (double) rand(70,80) / 100;
        double baseVelocity = 0.42 + 0.04 * Math.pow(remainingDistance,0.53);
        double startVelocity = randomStartVelocityAdjustmentFactor * baseVelocity;
        double travelTime = sectorLength / startVelocity;

        robot.mouseMove( nextX, nextY );
        Thread.sleep( (long) travelTime);

        int currentX = nextX;
        int currentY = nextY;
        Point currentPosition = new Point( currentX,currentY);
        double previousBearing = startBearing;
        double previousRemainingDistance = remainingDistance;
        double previousVelocity = startVelocity;
        int numberOfSteps = 0;
        int overShootCounter = 0;
        int offTargetCounter = 0;
        while(remainingDistance > rms(toleranceX,toleranceY) && numberOfSteps < 220 )
        {
            numberOfSteps++;
            finalX = AutoslayerPlugin.activeTargetGameObjectCentre.getX() + adjustmentX;
            finalY = AutoslayerPlugin.activeTargetGameObjectCentre.getY() + adjustmentY + canvasOffsetY;
            if(outsideWindow(AutoslayerPlugin.activeTargetGameObjectCentre) )
            {
                return;
            }
            finalP = new Point((int) finalX,(int) finalY);
            trueBearing = bearing(currentPosition,finalP);
            int bearingCorrectionSign = 0;
            if(trueBearing > previousBearing)
            {
                bearingCorrectionSign = +1;
            }
            if(trueBearing < previousBearing)
            {
                bearingCorrectionSign = -1;
            }
            double bearingCorrection = (double) rand(-20,100) / 10;
            double bearing = previousBearing + bearingCorrection * bearingCorrectionSign;
            double bearingDelta = Math.abs(trueBearing - bearing);
            remainingDistance = rms(finalX - currentX,finalY - currentY);
            if(bearingDelta <= 12 && remainingDistance < 200)
            {
                bearingCorrection = (double) rand(0,30) / 10;
                bearing = previousBearing + bearingCorrection * bearingCorrectionSign;
                bearingDelta = Math.abs(trueBearing - bearing);
            }
            if(bearingDelta > 12 && remainingDistance < 200)
            {
                bearingCorrection = rand((int) bearingDelta / 10,(int) bearingDelta / 4);
                bearing = previousBearing + bearingCorrection * bearingCorrectionSign;
                bearingDelta = Math.abs(trueBearing - bearing);
            }
            if(bearingDelta > 80 )
            {
                if(offTargetCounter > 5)
                {
                    if(AutoslayerPlugin.activeTargetGameObjectHull.contains(currentX,currentY)){
                        System.out.println(" targetHull.contains " );
                        mouseArrived.set(true);
                        return;
                    }
                    System.out.println(" offTargetCounter " + offTargetCounter);
                    return;
                }
                offTargetCounter++;
            }
            if(bearingDelta > 150 )
            {
                if(remainingDistance < 100 && overShootCounter < 2 ) {
                    double discrepancy = rand(-12, 12);
                    bearing = trueBearing + discrepancy;
                    bearingDelta = Math.abs(trueBearing - bearing);
                }
                if(overShootCounter > 1 )
                {
                    if(AutoslayerPlugin.activeTargetGameObjectHull.contains(currentX,currentY)){
                        System.out.println(" targetHull.contains " );
                        mouseArrived.set(true);
                        return;
                    }
                    System.out.println(" overShootCounter " + overShootCounter);
                    return;
                }
                overShootCounter++;
            }
            sectorLength = (double) rand(600,800) / 100;
            bearingQuadrantAngle = bearingQuadrantAngle(bearing);
            signX = 0;
            signY = 0;
            if(bearing <= 90)
            {
                signX = 1;
                signY = -1;
            }
            if(bearing > 90 && bearing <= 180)
            {
                signX = 1;
                signY = 1;
            }
            if(bearing > 180 && bearing <= 270)
            {
                signX = -1;
                signY = 1;
            }
            if(bearing > 270 && bearing <= 360)
            {
                signX = -1;
                signY = -1;
            }
            dX = signX * sectorLength * Math.cos(Math.toRadians(bearingQuadrantAngle));
            dY = signY * sectorLength * Math.sin(Math.toRadians(bearingQuadrantAngle));
            nextX = (int) (currentX + dX);
            nextY = (int) (currentY + dY);
            Point nextPosition = new Point( nextX, nextY);
            if(sectorLength > 16 || outsideWindow(nextPosition))
            {
                return;
            }
            baseVelocity = 0.42 + 0.04 * Math.pow(remainingDistance,0.53);
            double accelerationFactor = Math.min(0.8, 4 / ( Math.pow(remainingDistance,0.5) + 0.1 ) );
            double acceleration = (baseVelocity - previousVelocity) * accelerationFactor;
            double velocity = previousVelocity + acceleration;
            travelTime = sectorLength / velocity;

            robot.mouseMove( nextX, nextY );
            Thread.sleep( (long) travelTime);

            currentX = nextX;
            currentY = nextY;
            currentPosition = new Point( currentX, currentY);
            previousBearing = bearing;
            previousRemainingDistance = remainingDistance;
            previousVelocity = velocity;
        }
        mouseArrived.set(true);
    }

    public void moveMouseToFixedPoint(Robot robot, Point finalPoint) throws InterruptedException {
        mouseArrived.set(false);
        if(outsideWindow(finalPoint) )
        {
            System.out.println(" outsideWindow " );
            return;
        }
        Point startPosition = mouseCanvasLocation();
        double startX = startPosition.getX();
        double startY = startPosition.getY();
        System.out.println(" Start position " + startPosition);
        double adjustmentLimitX = 4;
        double adjustmentLimitY = 4;
        double toleranceX = adjustmentLimitX + 4;
        double toleranceY = adjustmentLimitY + 4;
        final double adjustmentX = rand(-(int)adjustmentLimitX,(int)adjustmentLimitX);
        final double adjustmentY = rand(-(int)adjustmentLimitY,(int)adjustmentLimitY);
        double finalX = finalPoint.getX() + adjustmentX;
        double finalY = finalPoint.getY() + adjustmentY + canvasOffsetY;
        Point finalP = new Point((int) finalX,(int) finalY);
        System.out.println(" Final position " + finalP);
        double trueBearing = bearing(startPosition,finalP);
        double remainingDistance = rms( finalX - startX, finalY - startY);
        int startDiscrepancyLimit = Math.min( 1 + (int) Math.floor( remainingDistance / 20) , 30);
        double startDiscrepancy = rand(-startDiscrepancyLimit,startDiscrepancyLimit);
        double startBearing = trueBearing + startDiscrepancy;
        int sectorLengthLimit = Math.min( 300 + (int) Math.floor( remainingDistance * 2) , 800);
        double sectorLength = (double) rand(sectorLengthLimit - 200,sectorLengthLimit) / 100;
        double bearingQuadrantAngle = bearingQuadrantAngle(startBearing);
        int signX = 0;
        int signY = 0;
        if(startBearing <= 90)
        {
            signX = 1;
            signY = -1;
        }
        if(startBearing > 90 && startBearing <= 180)
        {
            signX = 1;
            signY = 1;
        }
        if(startBearing > 180 && startBearing <= 270)
        {
            signX = -1;
            signY = 1;
        }
        if(startBearing > 270 && startBearing <= 360)
        {
            signX = -1;
            signY = -1;
        }
        double dX = signX * sectorLength * Math.cos(Math.toRadians(bearingQuadrantAngle));
        double dY = signY * sectorLength * Math.sin(Math.toRadians(bearingQuadrantAngle));
        int nextX = (int) (startX + dX);
        int nextY = (int) (startY + dY);
        if(rms(dX,dY) > 16)
        {
            return;
        }
        double randomStartVelocityAdjustmentFactor = (double) rand(70,80) / 100;
        double baseVelocity = 0.42 + 0.04 * Math.pow(remainingDistance,0.53);
        double startVelocity = randomStartVelocityAdjustmentFactor * baseVelocity;
        double travelTime = sectorLength / startVelocity;

        robot.mouseMove( nextX, nextY );
        Thread.sleep( (long) travelTime);

        int currentX = nextX;
        int currentY = nextY;
        Point currentPosition = new Point( currentX,currentY);
        double previousBearing = startBearing;
        double previousRemainingDistance = remainingDistance;
        double previousVelocity = startVelocity;
        int numberOfSteps = 0;
        int overShootCounter = 0;
        int offTargetCounter = 0;
        while(remainingDistance > rms(toleranceX,toleranceY) && numberOfSteps < 220 )
        {
            numberOfSteps++;
            if(outsideWindow(finalPoint) )
            {
                AutoslayerCore.npcChanged.set(false);
                return;
            }
            finalP = new Point((int) finalX,(int) finalY);
            trueBearing = bearing(currentPosition,finalP);
            int bearingCorrectionSign = 0;
            if(trueBearing > previousBearing)
            {
                bearingCorrectionSign = +1;
            }
            if(trueBearing < previousBearing)
            {
                bearingCorrectionSign = -1;
            }
            double bearingCorrection = (double) rand(-20,100) / 10;
            double bearing = previousBearing + bearingCorrection * bearingCorrectionSign;
            double bearingDelta = Math.abs(trueBearing - bearing);
            remainingDistance = rms(finalX - currentX,finalY - currentY);
            if(bearingDelta <= 12 && remainingDistance < 200)
            {
                bearingCorrection = (double) rand(0,30) / 10;
                bearing = previousBearing + bearingCorrection * bearingCorrectionSign;
                bearingDelta = Math.abs(trueBearing - bearing);
            }
            if(bearingDelta > 12 && remainingDistance < 200)
            {
                bearingCorrection = rand((int) bearingDelta / 10,(int) bearingDelta / 4);
                bearing = previousBearing + bearingCorrection * bearingCorrectionSign;
                bearingDelta = Math.abs(trueBearing - bearing);
            }
            if(bearingDelta > 80 )
            {
                if(offTargetCounter > 5)
                {
                    System.out.println(" offTargetCounter " + offTargetCounter);
                    return;
                }
                offTargetCounter++;
            }
            if(bearingDelta > 150 )
            {
                if(remainingDistance < 100 && overShootCounter < 2 ) {
                    double discrepancy = rand(-12, 12);
                    bearing = trueBearing + discrepancy;
                    bearingDelta = Math.abs(trueBearing - bearing);
                }
                if(overShootCounter > 1 )
                {
                    System.out.println(" overShootCounter " + overShootCounter);
                    return;
                }
                overShootCounter++;
            }
            sectorLengthLimit = Math.min( 300 + (int) Math.floor( remainingDistance * 2) , 800);
            sectorLength = (double) rand(sectorLengthLimit - 200,sectorLengthLimit) / 100;
            bearingQuadrantAngle = bearingQuadrantAngle(bearing);
            signX = 0;
            signY = 0;
            if(bearing <= 90)
            {
                signX = 1;
                signY = -1;
            }
            if(bearing > 90 && bearing <= 180)
            {
                signX = 1;
                signY = 1;
            }
            if(bearing > 180 && bearing <= 270)
            {
                signX = -1;
                signY = 1;
            }
            if(bearing > 270 && bearing <= 360)
            {
                signX = -1;
                signY = -1;
            }
            dX = signX * sectorLength * Math.cos(Math.toRadians(bearingQuadrantAngle));
            dY = signY * sectorLength * Math.sin(Math.toRadians(bearingQuadrantAngle));
            nextX = (int) (currentX + dX);
            nextY = (int) (currentY + dY);
            Point nextPosition = new Point( nextX, nextY);
            if(sectorLength > 16 || outsideWindow(nextPosition))
            {
                return;
            }
            baseVelocity = 0.42 + 0.04 * Math.pow(remainingDistance,0.53);
            double accelerationFactor = Math.min(0.8, 4 / ( Math.pow(remainingDistance,0.5) + 0.1 ) );
            double acceleration = (baseVelocity - previousVelocity) * accelerationFactor;
            double velocity = previousVelocity + acceleration;
            travelTime = sectorLength / velocity;

            robot.mouseMove( nextX, nextY );
            Thread.sleep( (long) travelTime);

            currentX = nextX;
            currentY = nextY;
            currentPosition = new Point( currentX, currentY);
            previousBearing = bearing;
            previousRemainingDistance = remainingDistance;
            previousVelocity = velocity;
        }
        mouseArrived.set(true);
    }

    public void moveMouseToTile(Robot robot, Tile tile) throws InterruptedException {
        mouseArrived.set(false);
        Point tilePoint = Perspective.localToCanvas(client, tile.getLocalLocation(),tile.getPlane());
        if(outsideWindow(tilePoint) )
        {
            System.out.println(" outsideWindow " );
            return;
        }
        Point startPosition = mouseCanvasLocation();
        double startX = startPosition.getX();
        double startY = startPosition.getY();
        System.out.println(" Start position " + startPosition);
        double adjustmentLimitX = 4;
        double adjustmentLimitY = 4;
        double toleranceX = adjustmentLimitX + 4;
        double toleranceY = adjustmentLimitY + 4;
        final double adjustmentX = rand(-(int)adjustmentLimitX,(int)adjustmentLimitX);
        final double adjustmentY = rand(-(int)adjustmentLimitY,(int)adjustmentLimitY);
        double finalX = tilePoint.getX() + adjustmentX;
        double finalY = tilePoint.getY() + adjustmentY + canvasOffsetY;
        Point finalP = new Point((int) finalX,(int) finalY);
        System.out.println(" Final position " + finalP);
        double trueBearing = bearing(startPosition,finalP);
        double startDiscrepancy = rand(-30,30);
        double startBearing = trueBearing + startDiscrepancy;
        double sectorLength = (double) rand(600,800) / 100;
        double bearingQuadrantAngle = bearingQuadrantAngle(startBearing);
        int signX = 0;
        int signY = 0;
        if(startBearing <= 90)
        {
            signX = 1;
            signY = -1;
        }
        if(startBearing > 90 && startBearing <= 180)
        {
            signX = 1;
            signY = 1;
        }
        if(startBearing > 180 && startBearing <= 270)
        {
            signX = -1;
            signY = 1;
        }
        if(startBearing > 270 && startBearing <= 360)
        {
            signX = -1;
            signY = -1;
        }
        double dX = signX * sectorLength * Math.cos(Math.toRadians(bearingQuadrantAngle));
        double dY = signY * sectorLength * Math.sin(Math.toRadians(bearingQuadrantAngle));
        int nextX = (int) (startX + dX);
        int nextY = (int) (startY + dY);
        if(rms(dX,dY) > 16)
        {
            return;
        }
        double remainingDistance = rms( finalX - startX, finalY - startY);
        double randomStartVelocityAdjustmentFactor = (double) rand(70,80) / 100;
        double baseVelocity = 0.42 + 0.04 * Math.pow(remainingDistance,0.53);
        double startVelocity = randomStartVelocityAdjustmentFactor * baseVelocity;
        double travelTime = sectorLength / startVelocity;

        robot.mouseMove( nextX, nextY );
        Thread.sleep( (long) travelTime);

        int currentX = nextX;
        int currentY = nextY;
        Point currentPosition = new Point( currentX,currentY);
        double previousBearing = startBearing;
        double previousRemainingDistance = remainingDistance;
        double previousVelocity = startVelocity;
        int numberOfSteps = 0;
        int overShootCounter = 0;
        int offTargetCounter = 0;
        while(remainingDistance > rms(toleranceX,toleranceY) && numberOfSteps < 220 )
        {
            numberOfSteps++;
            tilePoint = Perspective.localToCanvas(client, tile.getLocalLocation(),tile.getPlane());
            finalX = tilePoint.getX() + adjustmentX;
            finalY = tilePoint.getY() + adjustmentY + canvasOffsetY;
            if(outsideWindow(tilePoint) )
            {
                AutoslayerCore.npcChanged.set(false);
                return;
            }
            finalP = new Point((int) finalX,(int) finalY);
            trueBearing = bearing(currentPosition,finalP);
            int bearingCorrectionSign = 0;
            if(trueBearing > previousBearing)
            {
                bearingCorrectionSign = +1;
            }
            if(trueBearing < previousBearing)
            {
                bearingCorrectionSign = -1;
            }
            double bearingCorrection = (double) rand(-20,100) / 10;
            double bearing = previousBearing + bearingCorrection * bearingCorrectionSign;
            double bearingDelta = Math.abs(trueBearing - bearing);
            remainingDistance = rms(finalX - currentX,finalY - currentY);
            if(bearingDelta <= 12 && remainingDistance < 200)
            {
                bearingCorrection = (double) rand(0,30) / 10;
                bearing = previousBearing + bearingCorrection * bearingCorrectionSign;
                bearingDelta = Math.abs(trueBearing - bearing);
            }
            if(bearingDelta > 12 && remainingDistance < 200)
            {
                bearingCorrection = rand((int) bearingDelta / 10,(int) bearingDelta / 4);
                bearing = previousBearing + bearingCorrection * bearingCorrectionSign;
                bearingDelta = Math.abs(trueBearing - bearing);
            }
            if(bearingDelta > 80 )
            {
                if(offTargetCounter > 5)
                {
                    System.out.println(" offTargetCounter " + offTargetCounter);
                    return;
                }
                offTargetCounter++;
            }
            if(bearingDelta > 150 )
            {
                if(remainingDistance < 100 && overShootCounter < 2 ) {
                    double discrepancy = rand(-12, 12);
                    bearing = trueBearing + discrepancy;
                    bearingDelta = Math.abs(trueBearing - bearing);
                }
                if(overShootCounter > 1 )
                {
                    System.out.println(" overShootCounter " + overShootCounter);
                    return;
                }
                overShootCounter++;
            }
            sectorLength = (double) rand(600,800) / 100;
            bearingQuadrantAngle = bearingQuadrantAngle(bearing);
            signX = 0;
            signY = 0;
            if(bearing <= 90)
            {
                signX = 1;
                signY = -1;
            }
            if(bearing > 90 && bearing <= 180)
            {
                signX = 1;
                signY = 1;
            }
            if(bearing > 180 && bearing <= 270)
            {
                signX = -1;
                signY = 1;
            }
            if(bearing > 270 && bearing <= 360)
            {
                signX = -1;
                signY = -1;
            }
            dX = signX * sectorLength * Math.cos(Math.toRadians(bearingQuadrantAngle));
            dY = signY * sectorLength * Math.sin(Math.toRadians(bearingQuadrantAngle));
            nextX = (int) (currentX + dX);
            nextY = (int) (currentY + dY);
            Point nextPosition = new Point( nextX, nextY);
            if(sectorLength > 16 || outsideWindow(nextPosition))
            {
                return;
            }
            baseVelocity = 0.42 + 0.04 * Math.pow(remainingDistance,0.53);
            double accelerationFactor = Math.min(0.8, 4 / ( Math.pow(remainingDistance,0.5) + 0.1 ) );
            double acceleration = (baseVelocity - previousVelocity) * accelerationFactor;
            double velocity = previousVelocity + acceleration;
            travelTime = sectorLength / velocity;

            robot.mouseMove( nextX, nextY );
            Thread.sleep( (long) travelTime);

            currentX = nextX;
            currentY = nextY;
            currentPosition = new Point( currentX, currentY);
            previousBearing = bearing;
            previousRemainingDistance = remainingDistance;
            previousVelocity = velocity;
        }
        mouseArrived.set(true);
    }

    public boolean inLocalViewRange(WorldPoint worldPointTarget)
    {
        WorldPoint playerWorldLocation = client.getLocalPlayer().getWorldLocation();
        double distance = playerWorldLocation.distanceTo(worldPointTarget);
        int MAX_ACTOR_VIEW_RANGE = 8;
        return distance < MAX_ACTOR_VIEW_RANGE;
    }

    public void moveMouseToWalk(Robot robot, WorldPoint worldPoint) throws InterruptedException {
        mouseArrived.set(false);
        Point startPosition = mouseCanvasLocation();
        double startX = startPosition.getX();
        double startY = startPosition.getY();
        double adjustmentLimitX = 3;
        double adjustmentLimitY = 3;
        double toleranceX = adjustmentLimitX + 20;
        double toleranceY = adjustmentLimitY + 20;
        final double adjustmentX = rand(-(int)adjustmentLimitX,(int)adjustmentLimitX);
        final double adjustmentY = rand(-(int)adjustmentLimitY,(int)adjustmentLimitY);
        Point finalPoint;
        if(inLocalViewRange(worldPoint))
        {
            System.out.println(" worldPointToCanvas " );
            finalPoint = worldPointToCanvas(worldPoint);
        }
        else
        {
            System.out.println(" worldPointToMiniMap " );
            finalPoint = worldPointToMiniMap(worldPoint);
        }
        double finalX = finalPoint.getX() + adjustmentX;
        double finalY = finalPoint.getY() + adjustmentY + canvasOffsetY;
        if( outsideWindow(finalPoint) && !pointInsideMiniMap(finalPoint) )
        {
            System.out.println(" outsideWindow / !pointInsideMiniMap " );
            return;
        }
        double trueBearing = bearing(startPosition,finalPoint);
        double remainingX = Math.abs(finalX - startX);
        double remainingY = Math.abs(finalY - startY);
        double remainingDistance = rms(remainingX,remainingY);
        int startDiscrepancyLimit = Math.min( 2 + (int) Math.floor( remainingDistance / 20) , 30);
        double startDiscrepancy = rand(-startDiscrepancyLimit,startDiscrepancyLimit);
        double startBearing = trueBearing + startDiscrepancy;
        System.out.println(" trueBearing / startDiscrepancy / startBearing " + trueBearing + " , " + startDiscrepancy + " , " + startBearing );
        int sectorLengthLimit = Math.min( 300 + (int) Math.floor( remainingDistance * 2) , 800);
        double sectorLength = (double) rand(sectorLengthLimit - 200,sectorLengthLimit) / 100;
        double bearingQuadrantAngle = bearingQuadrantAngle(startBearing);
        int signX = 0;
        int signY = 0;
        if(startBearing <= 90)
        {
            signX = 1;
            signY = -1;
        }
        if(startBearing > 90 && startBearing <= 180)
        {
            signX = 1;
            signY = 1;
        }
        if(startBearing > 180 && startBearing <= 270)
        {
            signX = -1;
            signY = 1;
        }
        if(startBearing > 270 && startBearing <= 360)
        {
            signX = -1;
            signY = -1;
        }
        double dX = signX * sectorLength * Math.cos(Math.toRadians(bearingQuadrantAngle));
        double dY = signY * sectorLength * Math.sin(Math.toRadians(bearingQuadrantAngle));
        int nextX = (int) (startX + dX);
        int nextY = (int) (startY + dY);
        if(rms(dX,dY) > 16)
        {
            return;
        }
        double randomStartVelocityAdjustmentFactor = (double) rand(70,80) / 100;
        double baseVelocity = 0.42 + 0.04 * Math.pow(remainingDistance,0.53);
        double startVelocity = randomStartVelocityAdjustmentFactor * baseVelocity;
        double travelTime = sectorLength / startVelocity;

        robot.mouseMove( nextX, nextY );
        Thread.sleep( (long) travelTime);

        int currentX = nextX;
        int currentY = nextY;
        Point currentPosition = new Point( currentX,currentY);
        double previousBearing = startBearing;
        double previousRemainingDistance = remainingDistance;
        double previousVelocity = startVelocity;
        int numberOfSteps = 0;
        int overShootCounter = 0;
        int offTargetCounter = 0;
        remainingX = Math.abs(finalX - startX);
        remainingY = Math.abs(finalY - startY);
        while( ( remainingX > toleranceX || remainingY > toleranceY ) && numberOfSteps < 220 )
        {
            numberOfSteps++;
            if(inLocalViewRange(worldPoint))
            {
                finalPoint = worldPointToCanvas(worldPoint);
            }
            else
            {
                finalPoint = worldPointToMiniMap(worldPoint);
            }
            finalX = finalPoint.getX() + adjustmentX;
            finalY = finalPoint.getY() + adjustmentY + canvasOffsetY;
            if( outsideWindow(finalPoint) && !pointInsideMiniMap(finalPoint) )
            {
                return;
            }
            trueBearing = bearing(currentPosition,finalPoint);
            int bearingCorrectionSign = 0;
            if(trueBearing > previousBearing)
            {
                bearingCorrectionSign = +1;
            }
            if(trueBearing < previousBearing)
            {
                bearingCorrectionSign = -1;
            }
            double bearingCorrection = (double) rand(-20,100) / 10;
            double bearing = previousBearing + bearingCorrection * bearingCorrectionSign;
            double bearingDelta = Math.abs(trueBearing - bearing);
            remainingX = Math.abs(finalX - currentX);
            remainingY = Math.abs(finalY - currentY);
            remainingDistance = rms(remainingX,remainingY);
            System.out.println(" remainingX " + remainingX);
            System.out.println(" remainingY " + remainingY);
            if(bearingDelta <= 12 && remainingDistance < 200)
            {
                bearingCorrection = (double) rand(0,30) / 10;
                bearing = previousBearing + bearingCorrection * bearingCorrectionSign;
                bearingDelta = Math.abs(trueBearing - bearing);
            }
            if(bearingDelta > 12 && remainingDistance < 200)
            {
                bearingCorrection = rand((int) bearingDelta / 10,(int) bearingDelta / 5);
                bearing = previousBearing + bearingCorrection * bearingCorrectionSign;
                bearingDelta = Math.abs(trueBearing - bearing);
            }
            if(bearingDelta > 80 )
            {
                if(offTargetCounter > 3)
                {
                    System.out.println(" offTargetCounter " + offTargetCounter);
                    return;
                }
                offTargetCounter++;
            }
            if(bearingDelta > 150 )
            {
                if(remainingDistance < 100 && overShootCounter < 1 ) {
                    double discrepancy = rand(-12, 12);
                    bearing = trueBearing + discrepancy;
                    bearingDelta = Math.abs(trueBearing - bearing);
                }
                if(overShootCounter > 1 )
                {
                    System.out.println(" overShootCounter " + overShootCounter);
                    return;
                }
                overShootCounter++;
            }
            sectorLengthLimit = Math.min( 300 + (int) Math.floor( remainingDistance * 2 ) , 800);
            sectorLength = (double) rand(sectorLengthLimit - 200,sectorLengthLimit) / 100;
            bearingQuadrantAngle = bearingQuadrantAngle(bearing);
            signX = 0;
            signY = 0;
            if(bearing <= 90)
            {
                signX = 1;
                signY = -1;
            }
            if(bearing > 90 && bearing <= 180)
            {
                signX = 1;
                signY = 1;
            }
            if(bearing > 180 && bearing <= 270)
            {
                signX = -1;
                signY = 1;
            }
            if(bearing > 270 && bearing <= 360)
            {
                signX = -1;
                signY = -1;
            }
            dX = signX * sectorLength * Math.cos(Math.toRadians(bearingQuadrantAngle));
            dY = signY * sectorLength * Math.sin(Math.toRadians(bearingQuadrantAngle));
            nextX = (int) (currentX + dX);
            nextY = (int) (currentY + dY);
            Point nextPosition = new Point( nextX, nextY);
            if(sectorLength > 16 || outsideEntireWindow(nextPosition) )
            {
                System.out.println(" outsideEntireWindow " );
                return;
            }
            remainingX = Math.abs(finalX - currentX);
            remainingY = Math.abs(finalY - currentY);
            remainingDistance = rms(remainingX,remainingY);
            baseVelocity = 0.42 + 0.04 * Math.pow(remainingDistance,0.53);
            double accelerationFactor = Math.min(0.8, 4 / ( Math.pow(remainingDistance,0.5) + 0.1 ) );
            double acceleration = (baseVelocity - previousVelocity) * accelerationFactor;
            double velocity = previousVelocity + acceleration;
            travelTime = sectorLength / velocity;

            robot.mouseMove( nextX, nextY );
            Thread.sleep( (long) travelTime);

            currentX = nextX;
            currentY = nextY;
            currentPosition = new Point( currentX, currentY);
            previousBearing = bearing;
            previousRemainingDistance = remainingDistance;
            previousVelocity = velocity;
        }
        mouseArrived.set(true);
    }

    public int inventoryIndex( List <Integer> itemIDs) {
        /// Requires item in bottom right corner of inventory
        Item[] inventory = client.getItemContainer(InventoryID.INVENTORY).getItems();
        int index = 1;
        for (Item item : inventory) {
            int itemId = item.getId();
            if (itemIDs.contains(itemId)) {
                return index;
            }
            index = index + 1;
        }
        return 0;
    }

    public int numberOfItemInInventory(List <Integer> itemIDs) {
        int numberOfItemAvailable = 0;
        if (client.getItemContainer(InventoryID.INVENTORY) == null) {
            return numberOfItemAvailable;
        }
        Item[] inventory = client.getItemContainer(InventoryID.INVENTORY).getItems();
        for (Item item : inventory) {
            int itemId = item.getId();
            if (itemIDs.contains(itemId)) {
                numberOfItemAvailable = numberOfItemAvailable + 1;
            }
        }
        return numberOfItemAvailable;
    }

    public boolean isItemInInventory(int targetItemID) {
        if (client.getItemContainer(InventoryID.INVENTORY) == null) {
            return false;
        }
        Item[] inventory = client.getItemContainer(InventoryID.INVENTORY).getItems();
        for (Item item : inventory) {
            int itemId = item.getId();
            if (targetItemID == itemId) {
                return true;
            }
        }
        return false;
    }

    private Point inventoryLocation(List <Integer> itemIDs) {
        int inventoryBaseX = client.getWidget(net.runelite.api.widgets.WidgetInfo.INVENTORY).getCanvasLocation().getX();
        int inventoryBaseY = client.getWidget(net.runelite.api.widgets.WidgetInfo.INVENTORY).getCanvasLocation().getY();
        int n = inventoryIndex(itemIDs);
        int relativeBaseX = 15;
        int relativeBaseY = 39;
        int columnSpacing = 42;
        int rowSpacing = 36;
        int row = 1;
        if (n > 4) {
            while (n > 4) {
                n = n - 4;
                row = row + 1;
            }
        }
        int column = n;
        int x = inventoryBaseX + relativeBaseX + (column - 1) * columnSpacing;
        int y = inventoryBaseY + relativeBaseY + (row - 1) * rowSpacing;
        int xTolerance = rand(-1, 1);
        int yTolerance = rand(-1, 1);
        Point itemInvLocation = new Point(x + xTolerance,y +  yTolerance);
        return itemInvLocation;
    }

    public void moveMouseToInventory(Robot robot, List <Integer> itemIDs) throws InterruptedException, AWTException {
        mouseArrived.set(false);
        Point startPosition = mouseCanvasLocation();
        double startX = startPosition.getX();
        double startY = startPosition.getY();
        double adjustmentLimitX = 3;
        double adjustmentLimitY = 3;
        double toleranceX = adjustmentLimitX + 3;
        double toleranceY = adjustmentLimitY + 3;
        final double adjustmentX = rand(-(int)adjustmentLimitX,(int)adjustmentLimitX);
        final double adjustmentY = rand(-(int)adjustmentLimitY,(int)adjustmentLimitY);
        openInventory();
        Point finalPoint = inventoryLocation(itemIDs);
        double finalX = finalPoint.getX() + adjustmentX;
        double finalY = finalPoint.getY() + adjustmentY + canvasOffsetY;
        if( outsideEntireWindow(finalPoint) )
        {
            System.out.println(" outsideEntireWindow " );
            return;
        }
        double trueBearing = bearing(startPosition,finalPoint);
        double remainingDistance = rms(finalX-startX,finalY-startY);
        int startDiscrepancyLimit = Math.min( 1 + (int) Math.floor( remainingDistance / 20) , 30);
        double startDiscrepancy = rand(-startDiscrepancyLimit,startDiscrepancyLimit);
        double startBearing = trueBearing + startDiscrepancy;
        int sectorLengthLimit = Math.min( 300 + (int) Math.floor( remainingDistance * 2) , 800);
        double sectorLength = (double) rand(sectorLengthLimit - 200,sectorLengthLimit) / 100;
        double bearingQuadrantAngle = bearingQuadrantAngle(startBearing);
        int signX = 0;
        int signY = 0;
        if(startBearing <= 90)
        {
            signX = 1;
            signY = -1;
        }
        if(startBearing > 90 && startBearing <= 180)
        {
            signX = 1;
            signY = 1;
        }
        if(startBearing > 180 && startBearing <= 270)
        {
            signX = -1;
            signY = 1;
        }
        if(startBearing > 270 && startBearing <= 360)
        {
            signX = -1;
            signY = -1;
        }
        double dX = signX * sectorLength * Math.cos(Math.toRadians(bearingQuadrantAngle));
        double dY = signY * sectorLength * Math.sin(Math.toRadians(bearingQuadrantAngle));
        int nextX = (int) (startX + dX);
        int nextY = (int) (startY + dY);
        if(rms(dX,dY) > 16)
        {
            System.out.println(" rms>16 " );
            return;
        }
        double randomStartVelocityAdjustmentFactor = (double) rand(70,80) / 100;
        double baseVelocity = 0.42 + 0.04 * Math.pow(remainingDistance,0.53);
        double startVelocity = randomStartVelocityAdjustmentFactor * baseVelocity;
        double travelTime = sectorLength / startVelocity;

        robot.mouseMove( nextX, nextY );
        Thread.sleep( (long) travelTime);

        int currentX = nextX;
        int currentY = nextY;
        Point currentPosition = new Point( currentX,currentY);
        double previousBearing = startBearing;
        double previousRemainingDistance = remainingDistance;
        double previousVelocity = startVelocity;
        int numberOfSteps = 0;
        int overShootCounter = 0;
        int offTargetCounter = 0;
        while(remainingDistance > rms(toleranceX,toleranceY) && numberOfSteps < 150 )
        {
            numberOfSteps++;
            finalX = finalPoint.getX() + adjustmentX;
            finalY = finalPoint.getY() + adjustmentY + canvasOffsetY;
            if( outsideEntireWindow(finalPoint) )
            {
                System.out.println(" outsideEntireWindow " );
                return;
            }
            trueBearing = bearing(currentPosition,finalPoint);
            int bearingCorrectionSign = 0;
            if(trueBearing > previousBearing)
            {
                bearingCorrectionSign = +1;
            }
            if(trueBearing < previousBearing)
            {
                bearingCorrectionSign = -1;
            }
            double bearingCorrection = (double) rand(-20,100) / 10;
            double bearing = previousBearing + bearingCorrection * bearingCorrectionSign;
            double bearingDelta = Math.abs(trueBearing - bearing);
            remainingDistance = rms(finalX - currentX,finalY - currentY);
            if(bearingDelta <= 12 && remainingDistance < 200)
            {
                bearingCorrection = (double) rand(0,30) / 10;
                bearing = previousBearing + bearingCorrection * bearingCorrectionSign;
                bearingDelta = Math.abs(trueBearing - bearing);
            }
            if(bearingDelta > 12 && remainingDistance < 200)
            {
                bearingCorrection = rand((int) bearingDelta / 10,(int) bearingDelta / 4);
                bearing = previousBearing + bearingCorrection * bearingCorrectionSign;
                bearingDelta = Math.abs(trueBearing - bearing);
            }
            if(bearingDelta > 80 )
            {
                if(offTargetCounter > 3)
                {
                    System.out.println(" offTargetCounter " + offTargetCounter);
                    return;
                }
                offTargetCounter++;
            }
            if(bearingDelta > 150 )
            {
                if(remainingDistance < 100 && overShootCounter < 1 ) {
                    double discrepancy = rand(-12, 12);
                    bearing = trueBearing + discrepancy;
                    bearingDelta = Math.abs(trueBearing - bearing);
                }
                if(overShootCounter >= 1 )
                {
                    System.out.println(" overShootCounter " + overShootCounter);
                    return;
                }
                overShootCounter++;
            }
            sectorLengthLimit = Math.min( 300 + (int) Math.floor( remainingDistance * 2 ) , 800);
            sectorLength = (double) rand(sectorLengthLimit - 200,sectorLengthLimit) / 100;
            bearingQuadrantAngle = bearingQuadrantAngle(bearing);
            signX = 0;
            signY = 0;
            if(bearing <= 90)
            {
                signX = 1;
                signY = -1;
            }
            if(bearing > 90 && bearing <= 180)
            {
                signX = 1;
                signY = 1;
            }
            if(bearing > 180 && bearing <= 270)
            {
                signX = -1;
                signY = 1;
            }
            if(bearing > 270 && bearing <= 360)
            {
                signX = -1;
                signY = -1;
            }
            dX = signX * sectorLength * Math.cos(Math.toRadians(bearingQuadrantAngle));
            dY = signY * sectorLength * Math.sin(Math.toRadians(bearingQuadrantAngle));
            nextX = (int) (currentX + dX);
            nextY = (int) (currentY + dY);
            Point nextPosition = new Point( nextX, nextY);
            if(sectorLength > 16 || outsideEntireWindow(nextPosition) )
            {
                System.out.println(" sectorLength > 16 / outsideEntireWindow " );
                return;
            }
            remainingDistance = rms(finalX-currentX,finalY-currentY);
            baseVelocity = 0.42 + 0.04 * Math.pow(remainingDistance,0.53);
            double accelerationFactor = Math.min(0.8, 4 / ( Math.pow(remainingDistance,0.5) + 0.1 ) );
            double acceleration = (baseVelocity - previousVelocity) * accelerationFactor;
            double velocity = previousVelocity + acceleration;
            travelTime = sectorLength / velocity;

            robot.mouseMove( nextX, nextY );
            Thread.sleep( (long) travelTime);

            currentX = nextX;
            currentY = nextY;
            currentPosition = new Point( currentX, currentY);
            previousBearing = bearing;
            previousRemainingDistance = remainingDistance;
            previousVelocity = velocity;
        }
        mouseArrived.set(true);
    }

    public void moveMouseOutsideInventory(Robot robot) throws InterruptedException {
        mouseArrived.set(false);
        Point startPosition = mouseCanvasLocation();
        double startX = startPosition.getX();
        double startY = startPosition.getY();
        double adjustmentLimitX = 0;
        double adjustmentLimitY = 0;
        double toleranceX = adjustmentLimitX + 20;
        double toleranceY = adjustmentLimitY + 20;
        final double adjustmentX = rand(-(int)adjustmentLimitX,(int)adjustmentLimitX);
        final double adjustmentY = rand(-(int)adjustmentLimitY,(int)adjustmentLimitY);
        int centreScreenBoxMinimumX = 900;
        int centreScreenBoxMaximumX = 1100;
        int centreScreenBoxMinimumY = 400;
        int centreScreenBoxMaximumY = 600;
        int finalX = rand(centreScreenBoxMinimumX,centreScreenBoxMaximumX);
        int finalY = rand(centreScreenBoxMinimumY,centreScreenBoxMaximumY) + canvasOffsetY;
        Point finalPoint = new Point(finalX,finalY);
        if( outsideWindow(finalPoint) )
        {
            System.out.println(" outsideWindow " );
            return;
        }
        double trueBearing = bearing(startPosition,finalPoint);
        double startDiscrepancy = rand(-30,30);
        double startBearing = trueBearing + startDiscrepancy;
        double sectorLength = (double) rand(600,800) / 100;
        double bearingQuadrantAngle = bearingQuadrantAngle(startBearing);
        int signX = 0;
        int signY = 0;
        if(startBearing <= 90)
        {
            signX = 1;
            signY = -1;
        }
        if(startBearing > 90 && startBearing <= 180)
        {
            signX = 1;
            signY = 1;
        }
        if(startBearing > 180 && startBearing <= 270)
        {
            signX = -1;
            signY = 1;
        }
        if(startBearing > 270 && startBearing <= 360)
        {
            signX = -1;
            signY = -1;
        }
        double dX = signX * sectorLength * Math.cos(Math.toRadians(bearingQuadrantAngle));
        double dY = signY * sectorLength * Math.sin(Math.toRadians(bearingQuadrantAngle));
        int nextX = (int) (startX + dX);
        int nextY = (int) (startY + dY);
        if(rms(dX,dY) > 16)
        {
            System.out.println(" rms>16 " );
            return;
        }
        double remainingDistance = rms(finalX-startX,finalY-startY);
        double randomStartVelocityAdjustmentFactor = (double) rand(70,80) / 100;
        double baseVelocity = 0.42 + 0.04 * Math.pow(remainingDistance,0.53);
        double startVelocity = randomStartVelocityAdjustmentFactor * baseVelocity;
        double travelTime = sectorLength / startVelocity;

        robot.mouseMove( nextX, nextY );
        Thread.sleep( (long) travelTime);

        int currentX = nextX;
        int currentY = nextY;
        Point currentPosition = new Point( currentX,currentY);
        double previousBearing = startBearing;
        double previousRemainingDistance = remainingDistance;
        double previousVelocity = startVelocity;
        int numberOfSteps = 0;
        int overShootCounter = 0;
        int offTargetCounter = 0;
        while(remainingDistance > rms(toleranceX,toleranceY) && numberOfSteps < 150 )
        {
            numberOfSteps++;
            trueBearing = bearing(currentPosition,finalPoint);
            int bearingCorrectionSign = 0;
            if(trueBearing > previousBearing)
            {
                bearingCorrectionSign = +1;
            }
            if(trueBearing < previousBearing)
            {
                bearingCorrectionSign = -1;
            }
            double bearingCorrection = (double) rand(-20,100) / 10;
            double bearing = previousBearing + bearingCorrection * bearingCorrectionSign;
            double bearingDelta = Math.abs(trueBearing - bearing);
            remainingDistance = rms(finalX - currentX,finalY - currentY);
            if(bearingDelta < 12 && remainingDistance < 200)
            {
                bearingCorrection = (double) rand(0,30) / 10;
                bearing = previousBearing + bearingCorrection * bearingCorrectionSign;
                bearingDelta = Math.abs(trueBearing - bearing);
            }
            if(bearingDelta > 12 && remainingDistance < 200)
            {
                bearingCorrection = rand((int) bearingDelta / 10,(int) bearingDelta / 4);
                bearing = previousBearing + bearingCorrection * bearingCorrectionSign;
                bearingDelta = Math.abs(trueBearing - bearing);
            }
            if(bearingDelta > 80 )
            {
                if(offTargetCounter > 6)
                {
                    System.out.println(" offTargetCounter " + offTargetCounter);
                    return;
                }
                offTargetCounter++;
            }
            if(bearingDelta > 150 )
            {
                if(remainingDistance < 100 && overShootCounter < 2 ) {
                    double discrepancy = rand(-12, 12);
                    bearing = trueBearing + discrepancy;
                    bearingDelta = Math.abs(trueBearing - bearing);
                }
                if(overShootCounter > 1 )
                {
                    System.out.println(" overShootCounter " + overShootCounter);
                    return;
                }
                overShootCounter++;
            }
            sectorLength = (double) rand(600,800) / 100;
            bearingQuadrantAngle = bearingQuadrantAngle(bearing);
            signX = 0;
            signY = 0;
            if(bearing <= 90)
            {
                signX = 1;
                signY = -1;
            }
            if(bearing > 90 && bearing <= 180)
            {
                signX = 1;
                signY = 1;
            }
            if(bearing > 180 && bearing <= 270)
            {
                signX = -1;
                signY = 1;
            }
            if(bearing > 270 && bearing <= 360)
            {
                signX = -1;
                signY = -1;
            }
            dX = signX * sectorLength * Math.cos(Math.toRadians(bearingQuadrantAngle));
            dY = signY * sectorLength * Math.sin(Math.toRadians(bearingQuadrantAngle));
            nextX = (int) (currentX + dX);
            nextY = (int) (currentY + dY);
            Point nextPosition = new Point( nextX, nextY);
            if(sectorLength > 16 || outsideEntireWindow(nextPosition) )
            {
                System.out.println(" sectorLength > 16 / outsideEntireWindow " );
                return;
            }
            remainingDistance = rms(finalX-currentX,finalY-currentY);
            baseVelocity = 0.42 + 0.04 * Math.pow(remainingDistance,0.53);
            double accelerationFactor = Math.min(0.8, 4 / ( Math.pow(remainingDistance,0.5) + 0.1 ) );
            double acceleration = (baseVelocity - previousVelocity) * accelerationFactor;
            double velocity = previousVelocity + acceleration;
            travelTime = sectorLength / velocity;

            robot.mouseMove( nextX, nextY );
            Thread.sleep( (long) travelTime);

            currentX = nextX;
            currentY = nextY;
            currentPosition = new Point( currentX, currentY);
            previousBearing = bearing;
            previousRemainingDistance = remainingDistance;
            previousVelocity = velocity;
        }
        mouseArrived.set(true);
    }

}
