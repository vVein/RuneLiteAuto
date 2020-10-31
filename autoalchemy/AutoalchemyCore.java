package net.runelite.client.plugins.autoalchemy;

import lombok.Getter;
import net.runelite.api.Point;
import net.runelite.api.*;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.widgets.WidgetInfo;

import javax.inject.Inject;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

public class AutoalchemyCore {

    @Inject
    private Client client;

    @Inject
    private AutoalchemyPlugin plugin;

    public static AtomicBoolean npcChanged = new AtomicBoolean();

    public static AtomicBoolean mouseArrived = new AtomicBoolean();

    public static int canvasOffsetY = 23;

    public static int numberOfStepThreshold = 220;

    public String accountName = "vvvVein";

    public GameObject targetGameObject;

    Random r = new Random();

    public Rectangle hillGiantZone = new Rectangle(3108, 9833,11,12);
    public Rectangle hillGiantArea = new Rectangle(3095, 9822,3126 + 1 - 3095,9854 + 1 - 9822);

    public Rectangle alchemyZone = hillGiantZone;
    public Rectangle alchemyArea = hillGiantArea;

    public int rand(int lowerLimit, int upperLimit)
    {
        int range = upperLimit + 1 - lowerLimit;
        int subRand = r.nextInt(range);
        return subRand + lowerLimit;
    }

    public int randomDistortedDistributionInt (double lowerBound, double upperBound, double centralValue)
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
    
    public boolean playerOutsideTargetZone()
    {
        WorldPoint playerWorldLocation = client.getLocalPlayer().getWorldLocation();
        return !alchemyZone.contains(playerWorldLocation.getX(), playerWorldLocation.getY());
    }

    public WorldPoint targetZonePoint()
    {
        int x = alchemyZone.x + rand(0,alchemyZone.width);
        int y = alchemyZone.y + rand(0,alchemyZone.height);
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
        double minimapX = client.getWidget(WidgetInfo.RESIZABLE_MINIMAP_DRAW_AREA).getBounds().getCenterX();
        double minimapY = client.getWidget(WidgetInfo.RESIZABLE_MINIMAP_DRAW_AREA).getBounds().getCenterY() + canvasOffsetY;
        System.out.println( " minimapX " + minimapX);
        System.out.println( " minimapY " + minimapY);
        double minimapRadius = 73;
        // 75
        double deltaX = Math.abs(minimapX - point.getX());
        double deltaY = Math.abs(minimapY - point.getY());
        double deltaXSQ = Math.pow(deltaX, 2);
        double deltaYSQ = Math.pow(deltaY, 2);
        double Hypotenuse = Math.sqrt(deltaXSQ + deltaYSQ);

        double WorldMinimapX = client.getWidget(WidgetInfo.MINIMAP_WORLDMAP_ORB).getBounds().getCenterX();
        double WorldMinimapY = client.getWidget(WidgetInfo.MINIMAP_WORLDMAP_ORB).getBounds().getCenterY() + canvasOffsetY;
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
        boolean inventoryHidden = AutoalchemyPlugin.inventoryHidden.get();
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

    public void keyPress(int keyEvent) throws AWTException, InterruptedException {
        Robot robot = new Robot();
            int randomDelay0 = rand(400, 600);
            Thread.sleep(randomDelay0);
            robot.keyPress(keyEvent);
            int randomDelay1 = rand(400, 600);
            Thread.sleep(randomDelay1);
            robot.keyRelease(keyEvent);
            int randomDelay2 = rand(900, 1400);
            Thread.sleep(randomDelay2);
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

    public boolean inLocalViewRange(WorldPoint worldPointTarget)
    {
        WorldPoint playerWorldLocation = client.getLocalPlayer().getWorldLocation();
        double distance = playerWorldLocation.distanceTo(worldPointTarget);
        int MAX_ACTOR_VIEW_RANGE = 8;
        return distance < MAX_ACTOR_VIEW_RANGE;
    }

    public int inventoryIndex( List<Integer> itemIDs) {
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

    public int numberOfItemInInventory(List<Integer> itemIDs) {
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

    public int numberOfStackedItemInInventory(List<Integer> itemIDs) {
        int availableItem = 0;
        if (client.getItemContainer(InventoryID.INVENTORY) == null) {
            return availableItem;
        }
        Item[] inventory = client.getItemContainer(InventoryID.INVENTORY).getItems();
        for (Item item : inventory) {
            int itemId = item.getId();
            if (itemIDs.contains(itemId)) {
                availableItem = item.getQuantity();
            }
        }
        return availableItem;
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

    public Point inventoryLocation(List<Integer> itemIDs) {
        int inventoryBaseX = client.getWidget(WidgetInfo.INVENTORY).getCanvasLocation().getX();
        int inventoryBaseY = client.getWidget(WidgetInfo.INVENTORY).getCanvasLocation().getY();
        int n = inventoryIndex(itemIDs);
        int relativeBaseX = 15;
        int relativeBaseY = 39 - 15;
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

    @Getter
    public static Point currentPosition;
    double previousBearing;
    double previousVelocity;
    int numberOfSteps = 0;
    int overShootCounter = 0;
    int offTargetCounter = 0;

    public void wobble(Robot robot) throws InterruptedException {

        Point startPosition = mouseCanvasLocation();
        int startX = startPosition.getX();
        int startY = startPosition.getY();
        int dX = randomDistortedDistributionInt(-2,2,0);
        int dY = randomDistortedDistributionInt(-2,2,0);
        int finalX = startX + dX;
        int finalY = startY + dY;
        Point finalPoint = new Point(finalX, finalY);
        if(outsideEntireWindow(finalPoint) )
        {
            return;
        }
        if(dX == 0 && dY == 0 )
        {
            return;
        }
        double randomStartVelocityAdjustmentFactor = (double) rand(70,80) / 100;
        double baseVelocity = 0.31;
        double startVelocity = randomStartVelocityAdjustmentFactor * baseVelocity;
        double travelTime = rms(dX,dY) / startVelocity;
        robot.mouseMove( finalX, finalY );
        Thread.sleep( (long) travelTime);

    }

    public void moveMouseFixedPoint(Robot robot, Point finalPoint, int toleranceX, int toleranceY, int adjustmentLimitX,
                                    int adjustmentLimitY) throws InterruptedException {
        mouseArrived.set(false);
        final int adjustmentX = rand(-adjustmentLimitX,adjustmentLimitX);
        final int adjustmentY = rand(-adjustmentLimitY,adjustmentLimitY);
        int finalX = finalPoint.getX() + adjustmentX;
        int finalY = finalPoint.getY() + adjustmentY + canvasOffsetY;
        int centreX = finalPoint.getX();
        int centreY = finalPoint.getY() + canvasOffsetY;
        finalPoint = new Point(finalX, finalY);
        Point startPosition = mouseCanvasLocation();
        int currentX = startPosition.getX();
        int currentY = startPosition.getY();
        int remainingX = Math.abs(centreX - currentX);
        int remainingY = Math.abs(centreY - currentY);
        if (remainingX < toleranceX && remainingY < toleranceY ) {
            mouseArrived.set(true);
            return;
        }
        if(outsideEntireWindow(finalPoint) )
        {
            return;
        }
        moveMouseStart(robot,finalPoint);
        currentX = currentPosition.getX();
        currentY = currentPosition.getY();
        remainingX = Math.abs(finalX - currentX);
        remainingY = Math.abs(finalY - currentY);
        while( ( remainingX > toleranceX || remainingY > toleranceY ) && numberOfSteps < numberOfStepThreshold )
        {
            numberOfSteps++;
            moveMouse(robot,finalPoint);
            currentX = currentPosition.getX();
            currentY = currentPosition.getY();
            remainingX = Math.abs(finalX - currentX);
            remainingY = Math.abs(finalY - currentY);
        }
        if (remainingX < toleranceX && remainingY < toleranceY ) {
            mouseArrived.set(true);
        }
    }

    public void moveMouseTile(Robot robot, Tile tile, int toleranceX, int toleranceY, int adjustmentLimitX,
                                    int adjustmentLimitY) throws InterruptedException {
        mouseArrived.set(false);
        final int adjustmentX = rand(-adjustmentLimitX,adjustmentLimitX);
        final int adjustmentY = rand(-adjustmentLimitY,adjustmentLimitY);
        Point tilePoint = Perspective.localToCanvas(client, tile.getLocalLocation(),tile.getPlane());
        int finalX = tilePoint.getX() + adjustmentX;
        int finalY = tilePoint.getY() + adjustmentY + canvasOffsetY;
        Point finalPoint = new Point(finalX, finalY);
        Point startPosition = mouseCanvasLocation();
        int currentX = startPosition.getX();
        int currentY = startPosition.getY();
        int remainingX = Math.abs(finalX - currentX);
        int remainingY = Math.abs(finalY - currentY);
        if (remainingX < toleranceX && remainingY < toleranceY ) {
            mouseArrived.set(true);
            return;
        }
        if(outsideWindow(finalPoint) )
        {
            return;
        }
        moveMouseStart(robot,finalPoint);
        currentX = currentPosition.getX();
        currentY = currentPosition.getY();
        remainingX = Math.abs(finalX - currentX);
        remainingY = Math.abs(finalY - currentY);
        while( ( remainingX > toleranceX || remainingY > toleranceY ) && numberOfSteps < numberOfStepThreshold )
        {
            numberOfSteps++;
            tilePoint = Perspective.localToCanvas(client, tile.getLocalLocation(),tile.getPlane());
            finalX = tilePoint.getX() + adjustmentX;
            finalY = tilePoint.getY() + adjustmentY + canvasOffsetY;
            finalPoint = new Point(finalX, finalY);
            if(outsideWindow(finalPoint))
            {
                return;
            }
            moveMouse(robot,finalPoint);
            currentX = currentPosition.getX();
            currentY = currentPosition.getY();
            remainingX = Math.abs(finalX - currentX);
            remainingY = Math.abs(finalY - currentY);
        }
        if (remainingX < toleranceX && remainingY < toleranceY ) {
            mouseArrived.set(true);
        }
    }

    public void moveMouseWalk(Robot robot, WorldPoint worldPoint, int toleranceX, int toleranceY, int adjustmentLimitX,
                              int adjustmentLimitY) throws InterruptedException {
        mouseArrived.set(false);
        final double adjustmentX = rand(-adjustmentLimitX,adjustmentLimitX);
        final double adjustmentY = rand(-adjustmentLimitY,adjustmentLimitY);
        Point finalPoint;
        if(inLocalViewRange(worldPoint))
        {
            System.out.println(" worldPointToCanvas " );
            finalPoint = worldPointToCanvas(worldPoint);
            if( outsideWindow(finalPoint) ) {
                System.out.println(" outsideWindow " );
                return;
            }
        }
        else
        {
            System.out.println(" worldPointToMiniMap " );
            finalPoint = worldPointToMiniMap(worldPoint);
            if(!pointInsideMiniMap(finalPoint)){
                System.out.println(" !pointInsideMiniMap " );
                return;
            }
        }
        double finalX = finalPoint.getX() + adjustmentX;
        double finalY = finalPoint.getY() + adjustmentY + canvasOffsetY;
        finalPoint = new Point((int) finalX,(int) finalY);
        moveMouseStart(robot,finalPoint);
        int currentX = currentPosition.getX();
        int currentY = currentPosition.getY();
        double remainingX = Math.abs(finalX - currentX);
        double remainingY = Math.abs(finalY - currentY);
        while( ( remainingX > toleranceX || remainingY > toleranceY ) && numberOfSteps < numberOfStepThreshold )
        {
            numberOfSteps++;
            if(inLocalViewRange(worldPoint))
            {
                System.out.println(" worldPointToCanvas " );
                finalPoint = worldPointToCanvas(worldPoint);
                if( outsideWindow(finalPoint) ) {
                    System.out.println(" outsideWindow " );
                    return;
                }
            }
            else
            {
                System.out.println(" worldPointToMiniMap " );
                finalPoint = worldPointToMiniMap(worldPoint);
                if(!pointInsideMiniMap(finalPoint)){
                    System.out.println(" !pointInsideMiniMap " );
                    return;
                }
            }
            finalX = finalPoint.getX() + adjustmentX;
            finalY = finalPoint.getY() + adjustmentY + canvasOffsetY;
            finalPoint = new Point((int) finalX,(int) finalY);
            moveMouse(robot,finalPoint);
            currentX = currentPosition.getX();
            currentY = currentPosition.getY();
            remainingX = Math.abs(finalX - currentX);
            remainingY = Math.abs(finalY - currentY);
        }
        if (remainingX < toleranceX && remainingY < toleranceY ) {
            mouseArrived.set(true);
        }
    }

    public void moveMouseWidget(Robot robot, int widgetGroupID, int widgetChildID) throws InterruptedException {
        mouseArrived.set(false);
        System.out.println(" moveMouseWidget " );
        double width = client.getWidget(widgetGroupID,widgetChildID).getBounds().getWidth();
        double height = client.getWidget(widgetGroupID,widgetChildID).getBounds().getHeight();
        double toleranceX = (width / 2) - width / 10;
        double toleranceY = (height / 2) - height / 10;
        int adjustmentLimitX = (int) Math.round(0.3 * width);
        int adjustmentLimitY = (int) Math.round(0.3 * height);
        final int adjustmentX = rand(-adjustmentLimitX,adjustmentLimitX);
        final int adjustmentY = rand(-adjustmentLimitY,adjustmentLimitY);
        double widgetPointX = client.getWidget(widgetGroupID, widgetChildID).getBounds().getCenterX();
        double widgetPointY = client.getWidget(widgetGroupID, widgetChildID).getBounds().getCenterY();
        int finalX = (int) widgetPointX + adjustmentX;
        int finalY = (int) widgetPointY + adjustmentY + canvasOffsetY;
        int centreX = (int) widgetPointX ;
        int centreY = (int) widgetPointY + canvasOffsetY;
        Point finalPoint = new Point( finalX, finalY);
        System.out.println(" finalPoint " + finalPoint );
        if(outsideEntireWindow(finalPoint))
        {
            return;
        }
        Point startPosition = mouseCanvasLocation();
        int currentX = startPosition.getX();
        int currentY = startPosition.getY();
        int remainingX = Math.abs(centreX - currentX);
        int remainingY = Math.abs(centreY - currentY);
        System.out.println(" remainingX " + remainingX);
        System.out.println(" remainingY " + remainingY);
        if (remainingX < toleranceX && remainingY < toleranceY ) {
            System.out.println(" wobble " );
            mouseArrived.set(true);
            wobble(robot);
            return;
        }
        moveMouseStart(robot,finalPoint);
        currentX = currentPosition.getX();
        currentY = currentPosition.getY();
        remainingX = Math.abs(finalX - currentX);
        remainingY = Math.abs(finalY - currentY);
        System.out.println(" remainingX " + remainingX);
        System.out.println(" remainingY " + remainingY);
        while( ( remainingX > toleranceX || remainingY > toleranceY ) && numberOfSteps < numberOfStepThreshold )
        {
            System.out.println(" numberOfSteps " + numberOfSteps );
            numberOfSteps++;
            //widgetPointX = client.getWidget(widgetGroupID, widgetChildID).getBounds().getCenterX();
           // widgetPointY = client.getWidget(widgetGroupID, widgetChildID).getBounds().getCenterY();
           // finalX = widgetPointX + adjustmentX;
          //  finalY = widgetPointY + adjustmentY + canvasOffsetY;
          //  finalPoint = new Point((int) finalX,(int) finalY);
            moveMouse(robot,finalPoint);
            currentX = currentPosition.getX();
            currentY = currentPosition.getY();
            remainingX = Math.abs(centreX - currentX);
            remainingY = Math.abs(centreY - currentY);
        }
        if (remainingX < toleranceX && remainingY < toleranceY ) {
            mouseArrived.set(true);
        }
    }

    public void moveMouseStart(Robot robot, Point finalPoint) throws InterruptedException {
        if(outsideEntireWindow(finalPoint) )
        {
            return;
        }
        Point startPosition = mouseCanvasLocation();
        int startX = startPosition.getX();
        int startY = startPosition.getY();
        int finalX = finalPoint.getX();
        int finalY = finalPoint.getY();
        double remainingDistance = rms( finalX - startX, finalY - startY);
        double trueBearing = bearing(startPosition,finalPoint);
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
        double baseVelocity = 0.31 + 0.041 * Math.pow(remainingDistance,0.55);
        double startVelocity = randomStartVelocityAdjustmentFactor * baseVelocity;
        double travelTime = sectorLength / startVelocity;

        robot.mouseMove( nextX, nextY );
        Thread.sleep( (long) travelTime);

        int currentX = nextX;
        int currentY = nextY;
        currentPosition = new Point( currentX,currentY);
        previousBearing = startBearing;
        previousVelocity = startVelocity;
        numberOfSteps = 0;
        overShootCounter = 0;
        offTargetCounter = 0;
    }

    public void moveMouse(Robot robot, Point finalPoint) throws InterruptedException {
        if(outsideEntireWindow(finalPoint))
        {
            return;
        }
        double trueBearing = bearing(currentPosition,finalPoint);
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
        int currentX = currentPosition.getX();
        int currentY = currentPosition.getY();
        int finalX = finalPoint.getX();
        int finalY = finalPoint.getY();
        double remainingDistance = rms(finalX - currentX,finalY - currentY);
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
        if(bearingDelta > 80 && remainingDistance < 200 )
        {
            if(offTargetCounter > 8)
            {
                System.out.println(" offTargetCounter " + offTargetCounter);
                return;
            }
            offTargetCounter++;
        }
        if(bearingDelta > 150 && remainingDistance < 200 )
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
        int sectorLengthLimit = Math.min( 300 + (int) Math.floor( remainingDistance * 2) , 800);
        double sectorLength = (double) rand(sectorLengthLimit - 200,sectorLengthLimit) / 100;
        double bearingQuadrantAngle = bearingQuadrantAngle(bearing);
        int signX = 0;
        int signY = 0;
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
        double  dX = signX * sectorLength * Math.cos(Math.toRadians(bearingQuadrantAngle));
        double  dY = signY * sectorLength * Math.sin(Math.toRadians(bearingQuadrantAngle));
        int  nextX = (int) (currentX + dX);
        int  nextY = (int) (currentY + dY);
        Point nextPosition = new Point( nextX, nextY);
        if(sectorLength > 16 || outsideEntireWindow(nextPosition))
        {
            return;
        }
        double baseVelocity = 0.31 + 0.041 * Math.pow(remainingDistance,0.55);
        double accelerationFactor = Math.min(0.8, 4 / ( Math.pow(remainingDistance,0.5) + 0.1 ) );
        double acceleration = (baseVelocity - previousVelocity) * accelerationFactor;
        double velocity = previousVelocity + acceleration;
        double travelTime = sectorLength / velocity;

        robot.mouseMove( nextX, nextY );
        Thread.sleep( (long) travelTime);

        currentX = nextX;
        currentY = nextY;
        currentPosition = new Point( currentX, currentY);
        previousBearing = bearing;
        previousVelocity = velocity;
        }

}
