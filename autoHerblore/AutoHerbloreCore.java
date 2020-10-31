package net.runelite.client.plugins.autoHerblore;

import com.google.common.collect.ImmutableList;
import lombok.AccessLevel;
import lombok.Getter;
import net.runelite.api.Point;
import net.runelite.api.*;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;

import javax.inject.Inject;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

public class AutoHerbloreCore {

    @Inject
    private Client client;

    @Inject
    private AutoHerblorePlugin plugin;

    @Getter (AccessLevel.PACKAGE)
    public static AtomicBoolean npcChanged = new AtomicBoolean();

    @Getter (AccessLevel.PACKAGE)
    public static AtomicBoolean mouseArrived = new AtomicBoolean();

    @Getter (AccessLevel.PACKAGE)
    public static AtomicBoolean bankWidgetOpen = new AtomicBoolean();

    @Getter (AccessLevel.PACKAGE)
    public static AtomicBoolean activeTargetGameObject = new AtomicBoolean();

    @Getter (AccessLevel.PACKAGE)
    public static AtomicBoolean activeTargetGroundObject = new AtomicBoolean();

    @Getter (AccessLevel.PACKAGE)
    public static AtomicBoolean activeTargetDecorativeObject = new AtomicBoolean();

    @Getter (AccessLevel.PACKAGE)
    public static AtomicBoolean runActivated = new AtomicBoolean();

    public static int canvasOffsetY = 23;

    public int runEnergy = 0;

    public static int numberOfStepThreshold = 220;

    public String accountName = "vvvVein";

    @Getter (AccessLevel.PACKAGE)
    public GameObject targetGameObject;

    @Getter (AccessLevel.PACKAGE)
    public GroundObject targetGroundObject;

    @Getter (AccessLevel.PACKAGE)
    public DecorativeObject targetDecorativeObject;

    @Getter (AccessLevel.PACKAGE)
    public Rectangle activeTargetObjectClickBox;

    Random r = new Random();

    public List<Integer> edgevilleFurnaceObject = ImmutableList.of(ObjectID.FURNACE_16469);

    public List<Integer> edgevilleTopEdgeBankObjectList = ImmutableList.of(ObjectID.BANK_BOOTH_10355);

    public String useFurnaceText = "Smelt";
    public String craftJewelryText = "Make";
    public String craftGoldAmuletTargetItemDescriptor = "Gold amulet (u)";
    public String craftSapphireAmuletTargetItemDescriptor = "Sapphire amulet (u)";
    public String craftRubyNecklaceTargetItemDescriptor = "Ruby necklace";
    public String craftRubyAmuletTargetItemDescriptor = "Ruby amulet (u)";
    public String craftDiamondNecklaceTargetItemDescriptor = "Diamond necklace";
    public String craftJewelryTargetItemDescriptor = craftDiamondNecklaceTargetItemDescriptor;
    public String useEdgevilleBankText = "Bank";

    public String requiredItem1AmuletMouldString = "Amulet mould";
    public String requiredItem1NecklaceMouldString = "Necklace mould";
    public String itemStringEyeOfNewt = "Eye of newt";
    public String itemStringUnfinishedGuamPotion = "Guam potion (unf)";
    public String itemStringVialOfWater = "Vial of water";
    public String itemStringRanarrWeed = "Ranarr weed";
    public String requiredItem1String = itemStringVialOfWater;
    public String requiredItem2String = itemStringRanarrWeed;
    public String requiredItem3SapphireString = "Sapphire";
    public String requiredItem3RubyString = "Ruby";
    public String requiredItem3DiamondString = "Diamond";
    public String requiredItem3String = requiredItem3DiamondString;

    public String walkText = "Walk here";
    public String withdrawHighlightText = "Withdraw-1";
    public String withdrawAmountTargetText = "Withdraw-14";
    public String depositInventoryText = "Deposit inventory";

    public WorldPoint edgevilleFurnaceSpot = new WorldPoint(3109, 3499,0);
    public Rectangle edgevilleFurnaceArea = new Rectangle(3105, 3496,3111 + 1 - 3105,3502 + 1 - 3496);
    public Rectangle edgevilleFurnaceWalkArea = new Rectangle(3105, 3498,3107 + 1 - 3105,3499 + 1 - 3498);
    public Rectangle edgevilleFurnaceContainerArea = new Rectangle(3109, 3498,3111 + 1 - 3109,3500 + 1 - 3498);

    public Rectangle edgevilleBankBoothContainerArea = new Rectangle(3095, 3492,3098 + 1 - 3095,3494 + 1 - 3492);
    public Rectangle edgevilleBankArea = new Rectangle(3094, 3493,3099 + 1 - 3094,3496 + 1 - 3493);
    public Rectangle edgevilleBankWalkArea = new Rectangle(3096, 3494,3098 + 1 - 3096,3496 + 1 - 3494);

    public List<Integer> CW_Bank_Chest_ID = ImmutableList.of(ObjectID.BANK_CHEST_4483);
    public String CW_Bank_Chest_Access_String = "Use";
    public Rectangle CW_Bank_Area = new Rectangle(2440, 3082, 5, 4);
    public WorldPoint CW_Bank_WL = new WorldPoint(2443, 3083, 0);
    public Rectangle CW_Bank_Walk_Area = new Rectangle(2443-2, 3083,2, 2);

    public Rectangle bankBoothContainerArea = CW_Bank_Area;
    public Rectangle bankArea = CW_Bank_Area;
    public List<Integer> bankObjectList = CW_Bank_Chest_ID;
    public Rectangle bankWalkArea = CW_Bank_Walk_Area;
    public WorldPoint targetSpot = edgevilleFurnaceSpot;
    public Rectangle targetArea = edgevilleFurnaceArea;
    public String useBankText = CW_Bank_Chest_Access_String;

    WidgetInfo bankItemContainerWidget = WidgetInfo.BANK_ITEM_CONTAINER;
    int craftJewelryWidgetID = 446;
    int craftGoldAmuletUWidgetChildID = 34;
    int craftSapphireAmuletUWidgetChildID = 35;
    int craftRubyAmuletUWidgetChildID = 37;
    int craftRubyNecklaceWidgetChildID = 24;
    int craftDiamondNecklaceWidgetChildID = 25;
    int craftJewelryWidgetChildID = craftDiamondNecklaceWidgetChildID;

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
                return firstOption.getOption().contains(testText);
            }
        }
        return false;
    }

    public boolean leftClickDoubleOption(String targetString, String actionString) {
        MenuEntry[] options = client.getMenuEntries();
        MenuEntry firstOption;
        if (options.length == 0) {
            return false;
        }
        firstOption = options[options.length - 1];
        if (firstOption != null) {
            if (firstOption.getOption() != null) {
                return firstOption.getOption().contains(targetString) && firstOption.getOption().contains(actionString);
            }
        }
        return false;
    }

    public boolean leftClickTarget(String testText) {
        MenuEntry[] options = client.getMenuEntries();
        MenuEntry firstOption;
        if (options.length == 0) {
            return testText.matches("Walk here");
        }
        firstOption = options[options.length - 1];
        if (firstOption != null) {
            if (firstOption.getTarget() != null) {
                return firstOption.getTarget().contains(testText);
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

    public boolean playerOnTargetSpot(WorldPoint targetSpot)
    {
        WorldPoint playerWorldLocation = client.getLocalPlayer().getWorldLocation();
        return targetSpot.getX() == playerWorldLocation.getX() && targetSpot.getY() == playerWorldLocation.getY();
    }

    public boolean playerInTargetArea(Rectangle targetArea)
    {
        WorldPoint playerWorldLocation = client.getLocalPlayer().getWorldLocation();
        return targetArea.contains(playerWorldLocation.getX(),playerWorldLocation.getY());
    }

    public Point mouseCanvasLocation() {
        return new Point(MouseInfo.getPointerInfo().getLocation().x, MouseInfo.getPointerInfo().getLocation().y);
    }

    public double rootSumSquare(double x, double y)
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
        Rectangle window = new Rectangle(5, 7 , 1630, 1004 - canvasOffsetY);
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

    public boolean leftClickIdentifier(int targetIdentifier) {
        MenuEntry[] options = client.getMenuEntries();
        MenuEntry firstOption;
        if (options.length == 0) {
            return false;
        }
        firstOption = options[options.length - 1];
        if (firstOption != null) {
            if (firstOption.getTarget() != null) {
                if (firstOption.getIdentifier() == targetIdentifier) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean leftClickIdentifier( List<Integer> targetIdentifiers) {
        MenuEntry[] options = client.getMenuEntries();
        MenuEntry firstOption;
        if (options.length == 0) {
            return false;
        }
        firstOption = options[options.length - 1];
        if (firstOption != null) {
            if (firstOption.getTarget() != null) {
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

    public int rightClickMenuIndex(String targetName, String targetOption) {
        if (client.getMenuEntries() == null) {
            return 0;
        }
        MenuEntry[] menuOptions = client.getMenuEntries();
        int menuSize = menuOptions.length;
        int optionFromBottom = 0;
        int optionIndex;
        for (MenuEntry option : menuOptions) {
            //System.out.println(option);
            if (option.getTarget().contains(targetName) && option.getOption().equals(targetOption)) {
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

    public Point rightClickMenuPoint(String targetName, String targetOption) {
        Point startPosition = mouseCanvasLocation();
        int startX = startPosition.getX();
        int startY = startPosition.getY();
        int baseYOffset = 4; /// + canvasOffsetY;
        int rowOffset = 15;
        int xTolerance = 35;
        int yTolerance = 3;
        int randomX = rand(-xTolerance, xTolerance);
        int randomY = rand(-yTolerance, yTolerance);
        int menuIndex = rightClickMenuIndex(targetName, targetOption);
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

    public void closeBank() throws InterruptedException, AWTException {
        Robot robot = new Robot();
        if(bankWidgetOpen.get()) {
            Point fixedPoint = new Point ((int)client.getWidget(12,2).getChild(11).getBounds().getCenterX()+3,
                    (int)client.getWidget(12,2).getChild(11).getBounds().getCenterY()-5);
            moveMouseFixedPoint(robot, fixedPoint, 12, 12, 4, 4);
            if (AutoHerbloreCore.mouseArrived.get() && leftClickOption("Close")) {
                leftClick(robot);
                int randomDelay1 = rand(1000, 2000);
                Thread.sleep(randomDelay1);
            }
        }
    }

    public void bankWidgetOpenCheck(){

        if (client.getWidget(WidgetInfo.BANK_ITEM_CONTAINER) == null) {
            bankWidgetOpen.set(false);
            return;
        }
        bankWidgetOpen.set(!client.getWidget(WidgetInfo.BANK_ITEM_CONTAINER).isHidden());
    }

    public void openBank() throws InterruptedException, AWTException {
        Robot robot = new Robot();
        if(!bankWidgetOpen.get()) {
            if(playerInTargetArea(bankArea)) {
                if (gameObjectFromTiles(bankObjectList, bankBoothContainerArea)) {
                    moveMouseObject(robot, 0.35, 0.33);
                    if (mouseArrived.get()){
                        activeTargetGameObject.set(false);
                        if( leftClickOption(useBankText)) {
                            leftClick(robot);
                            int randomDelay1 = rand(1000, 2000);
                            Thread.sleep(randomDelay1);
                        } else {
                            rightClick(robot);
                            int randomDelay1 = rand(1000, 2000);
                            Thread.sleep(randomDelay1);
                            Point menuOption = rightClickMenuPoint(useBankText,useBankText);
                            moveMouseFixedPoint(robot, menuOption, 24, 8, 10, 3);
                            int randomDelay3 = rand(700, 1200);
                            Thread.sleep(randomDelay3);
                            if (mouseArrived.get() ) {
                                leftClick(robot);
                                int randomDelay2 = rand(1000, 2000);
                                Thread.sleep(randomDelay2);
                            }
                        }
                    }

                }
            }
            else if(gameObjectFromTiles(bankObjectList, bankBoothContainerArea)){
                moveMouseObject(robot, 0.35, 0.33);
                if (mouseArrived.get()){
                    activeTargetGameObject.set(false);
                    if( leftClickOption(useBankText)) {
                        leftClick(robot);
                        int randomDelay1 = rand(1000, 2000);
                        Thread.sleep(randomDelay1);
                    } else {
                        rightClick(robot);
                        int randomDelay1 = rand(1000, 2000);
                        Thread.sleep(randomDelay1);
                        Point menuOption = rightClickMenuPoint(useBankText,useBankText);
                        moveMouseFixedPoint(robot, menuOption, 24, 8, 10, 3);
                        int randomDelay3 = rand(700, 1200);
                        Thread.sleep(randomDelay3);
                        if (mouseArrived.get() ) {
                            leftClick(robot);
                            int randomDelay2 = rand(1000, 2000);
                            Thread.sleep(randomDelay2);
                        }
                    }
                }
            }
        }
    }

    public Widget bankItemWidget ( List<Integer> targetIdentifiers ) throws AWTException, InterruptedException {

        if(!bankWidgetOpen.get()) {
            openBank();
            Thread.sleep(400);
        }

        if(bankWidgetOpen.get()) {
            Thread.sleep(400);
            if(client.getWidget(WidgetInfo.BANK_ITEM_CONTAINER).getChildren() != null) {
                Widget[] bankItems = client.getWidget(WidgetInfo.BANK_ITEM_CONTAINER).getChildren();
                for (Widget bankItem : bankItems) {
                    if (targetIdentifiers.contains(bankItem.getItemId())) {
                        return bankItem;
                    }
                }
            }

        }
        return null;
    }

    public Point worldPointToCanvas(WorldPoint worldPoint)
    {
        LocalPoint localPoint = LocalPoint.fromWorld(client,worldPoint);
        return Perspective.localToCanvas(client,localPoint,client.getPlane());
    }

    public boolean pointInsideMiniMap(Point point) {
        double minimapX = client.getWidget(WidgetInfo.RESIZABLE_MINIMAP_DRAW_AREA).getBounds().getCenterX();
        double minimapY = client.getWidget(WidgetInfo.RESIZABLE_MINIMAP_DRAW_AREA).getBounds().getCenterY() + canvasOffsetY;
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
            LocalPoint localPoint = LocalPoint.fromWorld(client,interimWorldPoint);
            Point interimCanvasPoint = Perspective.localToMinimap(client,localPoint);
            if(pointInsideMiniMap(interimCanvasPoint))
            {
                return interimCanvasPoint;
            }
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
        boolean inventoryHidden = AutoHerblorePlugin.inventoryHidden.get();
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

    public void runEnergyCheck(){
        int runSpriteID = client.getWidget(160,27).getSpriteId();
        if (runSpriteID == 1069){
            runActivated.set(false);
        }
        if (runSpriteID == 1070){
            runActivated.set(true);
        }
        runEnergy = client.getEnergy();
    }

    public void toggleRun() throws AWTException, InterruptedException {
        Robot robot = new Robot();
        if (runEnergy > 85 && !runActivated.get()) {
            int randomDelay0 = rand(400, 600);
            Thread.sleep(randomDelay0);
            moveMouseWidget(robot, 160, 27);
            int randomDelay3 = rand(700, 1200);
            Thread.sleep(randomDelay3);
            if (mouseArrived.get() ) {
                leftClick(robot);
                int randomDelay2 = rand(1000, 2000);
                Thread.sleep(randomDelay2);
            }
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

    int MAX_ACTOR_VIEW_RANGE = 9;

    public boolean inLocalViewRange(WorldPoint worldPointTarget)
    {
        WorldPoint playerWorldLocation = client.getLocalPlayer().getWorldLocation();
        double distance = playerWorldLocation.distanceTo(worldPointTarget);
        return distance < MAX_ACTOR_VIEW_RANGE;
    }

    public boolean gameObjectFromTiles (List <Integer> targetObjectIDs, Rectangle targetArea) {
        List<GameObject> matchedTargets = new ArrayList<>();
        Tile[][][] tiles = client.getScene().getTiles();
        for (Tile[][] outerTileArray : tiles) {
            for (Tile[] innerTileArray : outerTileArray) {
                for (Tile tile : innerTileArray) {
                    if (tile != null) {
                        if (tile.getWorldLocation() != null) {
                            if (client.getLocalPlayer().getWorldLocation().distanceTo(tile.getWorldLocation()) < MAX_ACTOR_VIEW_RANGE &&
                                    targetArea.contains(tile.getWorldLocation().getX(),tile.getWorldLocation().getY())) {
                                for (GameObject gameObject : tile.getGameObjects()) {
                                    if (gameObject != null) {
                                        if (targetObjectIDs.contains(gameObject.getId()) ) {
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
            activeTargetGameObject.set(false);
            return false;
        }
        matchedTargets.sort(new AutoHerbloreCore.gameObjectDistanceComparator());
        targetGameObject = matchedTargets.get(0);
        if(targetGameObject.getCanvasTilePoly() != null){
            activeTargetObjectClickBox = targetGameObject.getClickbox().getBounds();
            if(outsideWindow(new Point((int) activeTargetObjectClickBox.getCenterX(),(int) activeTargetObjectClickBox.getCenterY()))){
                return false;
            }
            activeTargetGameObject.set(true);
            return true;
        }
        return false;
    }

    public boolean game_Object_From_Tiles (int targetObjectID) {
        List<GameObject> matchedTargets = new ArrayList<>();
        Tile[][][] tiles = client.getScene().getTiles();
        for (Tile[][] outerTileArray : tiles) {
            for (Tile[] innerTileArray : outerTileArray) {
                for (Tile tile : innerTileArray) {
                    if (tile != null) {
                        if (tile.getWorldLocation() != null) {
                            if (client.getLocalPlayer().getWorldLocation().distanceTo(tile.getWorldLocation()) < MAX_ACTOR_VIEW_RANGE ) {
                                for (GameObject gameObject : tile.getGameObjects()) {
                                    if (gameObject != null) {
                                        if (targetObjectID == gameObject.getId() ) {
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
            activeTargetGameObject.set(false);
            return false;
        }
        matchedTargets.sort(new AutoHerbloreCore.gameObjectDistanceComparator());
        targetGameObject = matchedTargets.get(0);
        if(targetGameObject.getCanvasTilePoly() != null){
            activeTargetObjectClickBox = targetGameObject.getCanvasTilePoly().getBounds();
            if(outsideWindow(new Point((int) activeTargetObjectClickBox.getCenterX(),(int) activeTargetObjectClickBox.getCenterY()))){
                return false;
            }
            activeTargetGameObject.set(true);
            return true;
        }
        return false;
    }

    public boolean ground_Object_From_Tiles (int targetObjectID) {
        List<GroundObject> matchedTargets = new ArrayList<>();
        Tile[][][] tiles = client.getScene().getTiles();
        for (Tile[][] outerTileArray : tiles) {
            for (Tile[] innerTileArray : outerTileArray) {
                for (Tile tile : innerTileArray) {
                    if (tile != null) {
                        if (tile.getWorldLocation() != null) {
                            if (client.getLocalPlayer().getWorldLocation().distanceTo(tile.getWorldLocation()) < MAX_ACTOR_VIEW_RANGE ) {
                                GroundObject groundObject = tile.getGroundObject();
                                if (groundObject != null) {
                                    if (targetObjectID == groundObject.getId() ) {
                                        matchedTargets.add(groundObject);
                                        break;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        if(matchedTargets.isEmpty()){
            activeTargetGroundObject.set(false);
            return false;
        }
        targetGroundObject = matchedTargets.get(0);
        if(targetGroundObject.getCanvasTilePoly() != null){
            activeTargetObjectClickBox = targetGroundObject.getClickbox().getBounds();
            if(outsideWindow(new Point((int) activeTargetObjectClickBox.getCenterX(),(int) activeTargetObjectClickBox.getCenterY()))){
                return false;
            }
            activeTargetGroundObject.set(true);
            return true;
        }
        return false;
    }

    public boolean decorative_Object_From_Tiles (int targetObjectID) {
        List<DecorativeObject> matchedTargets = new ArrayList<>();
        Tile[][][] tiles = client.getScene().getTiles();
        for (Tile[][] outerTileArray : tiles) {
            for (Tile[] innerTileArray : outerTileArray) {
                for (Tile tile : innerTileArray) {
                    if (tile != null) {
                        if (tile.getWorldLocation() != null) {
                            if (client.getLocalPlayer().getWorldLocation().distanceTo(tile.getWorldLocation()) < MAX_ACTOR_VIEW_RANGE ) {
                                DecorativeObject decorative_Object = tile.getDecorativeObject();
                                if (decorative_Object != null) {
                                    if (targetObjectID == decorative_Object.getId() ) {
                                        matchedTargets.add(decorative_Object);
                                        break;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        if(matchedTargets.isEmpty()){
            activeTargetDecorativeObject.set(false);
            return false;
        }
        targetDecorativeObject = matchedTargets.get(0);
        if(targetDecorativeObject.getCanvasTilePoly() != null){
            activeTargetObjectClickBox = targetDecorativeObject.getClickbox().getBounds();
            if(outsideWindow(new Point((int) activeTargetObjectClickBox.getCenterX(),(int) activeTargetObjectClickBox.getCenterY()))){
                return false;
            }
            activeTargetDecorativeObject.set(true);
            return true;
        }
        return false;
    }

    public int numberOfItemInBank(List<Integer> itemIDs) throws AWTException, InterruptedException {
        int numberOfItemAvailable = 0;

        if(!bankWidgetOpen.get()) {
            openBank();
            Thread.sleep(400);
        }

        if(bankWidgetOpen.get()) {
            Thread.sleep(400);
            if(client.getWidget(WidgetInfo.BANK_ITEM_CONTAINER).getChildren() != null) {
                Widget[] bankItems = client.getWidget(WidgetInfo.BANK_ITEM_CONTAINER).getChildren();
                for (Widget bankItem : bankItems) {
                    if (itemIDs.contains(bankItem.getItemId())) {
                        numberOfItemAvailable = bankItem.getItemQuantity();
                        return numberOfItemAvailable;
                    }
                }
            }

        }
        return numberOfItemAvailable;
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
        int relativeBaseY = 35 - 15;
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

    public void depositInventory() throws InterruptedException, AWTException {

        int randomDelay0 = rand(400, 600);
        Thread.sleep(randomDelay0);

        Robot robot = new Robot();

        moveMouseWidget(robot,12,41);
        if (mouseArrived.get() && leftClickOption(depositInventoryText)) {
            leftClick(robot);
            int randomDelay1 = rand(1500, 3000);
            Thread.sleep(randomDelay1);
        }
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
        double travelTime = rootSumSquare(dX,dY) / startVelocity;
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
        finalPoint = new Point(finalX, finalY);
        Point startPosition = mouseCanvasLocation();
        int currentX = startPosition.getX();
        int currentY = startPosition.getY();
        int remainingX = Math.abs(finalX - currentX);
        int remainingY = Math.abs(finalY - currentY);
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
        while( ( remainingX >= toleranceX || remainingY >= toleranceY ) && numberOfSteps < numberOfStepThreshold )
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
            long pause = rand(50,100);
            Thread.sleep( pause);
        }
    }

    public void moveMouseBankItem(Robot robot, Widget bankItem ) throws InterruptedException {
        int centreX = bankItem.getCanvasLocation().getX() + bankItem.getWidth() / 2;
        int centreY = bankItem.getCanvasLocation().getY() + bankItem.getHeight() / 2 + canvasOffsetY;
        int toleranceX = (int) (0.15 * bankItem.getWidth() );
        int toleranceY = (int) (0.15 * bankItem.getHeight() );
        int adjustmentLimitX = (int) (0.3 * bankItem.getWidth() );
        int adjustmentLimitY = (int)  (0.3 * bankItem.getHeight() );
        mouseArrived.set(false);
        final int adjustmentX = rand(-adjustmentLimitX,adjustmentLimitX);
        final int adjustmentY = rand(-adjustmentLimitY,adjustmentLimitY);
        int finalX = centreX + adjustmentX;
        int finalY = centreY + adjustmentY;
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
        if(outsideEntireWindow(finalPoint) )
        {
            return;
        }
        moveMouseStart(robot,finalPoint);
        currentX = currentPosition.getX();
        currentY = currentPosition.getY();
        remainingX = Math.abs(finalX - currentX);
        remainingY = Math.abs(finalY - currentY);
        while( ( remainingX >= toleranceX || remainingY >= toleranceY ) && numberOfSteps < numberOfStepThreshold
                && offTargetCounter < 8 && overShootCounter < 2)
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
            long pause = rand(50,100);
            Thread.sleep( pause);
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
        while( ( remainingX >= toleranceX || remainingY >= toleranceY ) && numberOfSteps < numberOfStepThreshold )
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
            long pause = rand(50,100);
            Thread.sleep( pause);
        }
    }

    public void moveMouseObject(Robot robot, double adjustmentXMultiplier, double adjustmentYMultiplier ) throws InterruptedException {
        mouseArrived.set(false);
        Rectangle2D objectClickBox = activeTargetObjectClickBox;
        int clickboxCentreX = (int) objectClickBox.getCenterX();
        int clickboxCentreY = (int) objectClickBox.getCenterY() + canvasOffsetY;
        int priorClickboxCentreX = (int) objectClickBox.getCenterX();
        int priorClickboxCentreY = (int) objectClickBox.getCenterY() + canvasOffsetY;
        int toleranceX = (int) (0.12 * objectClickBox.getWidth());
        int toleranceY = (int) (0.12 * objectClickBox.getHeight());
        int adjustmentLimitX = (int) (adjustmentXMultiplier * objectClickBox.getWidth());
        int adjustmentLimitY = (int) (adjustmentYMultiplier * objectClickBox.getHeight());
        final int adjustmentX = rand(-adjustmentLimitX,adjustmentLimitX);
        final int adjustmentY = rand(-adjustmentLimitY,adjustmentLimitY);

        int finalX = clickboxCentreX + adjustmentX;
        int finalY = clickboxCentreY + adjustmentY;
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
        while( ( remainingX >= toleranceX || remainingY >= toleranceY ) && numberOfSteps < numberOfStepThreshold )
        {
            numberOfSteps++;
            objectClickBox = activeTargetObjectClickBox;
            toleranceX = (int) (0.12 * objectClickBox.getWidth());
            toleranceY = (int) (0.12 * objectClickBox.getHeight());
            clickboxCentreX = (int) objectClickBox.getCenterX();
            clickboxCentreY = (int) objectClickBox.getCenterY() + canvasOffsetY;
            if( priorClickboxCentreX != clickboxCentreX || priorClickboxCentreY != clickboxCentreY){
                offTargetCounter = 0;
                overShootCounter = 0;
                priorClickboxCentreX = clickboxCentreX;
                priorClickboxCentreY = clickboxCentreY;
            }
            finalX = clickboxCentreX + adjustmentX;
            finalY = clickboxCentreY + adjustmentY;
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
            long pause = rand(50,100);
            Thread.sleep( pause);
        }
    }

    public WorldPoint randomAreaPoint(Rectangle targetArea) {
        int width = (int) targetArea.getWidth();
        int height = (int) targetArea.getHeight();
        final int adjustmentX = rand(0,width);
        final int adjustmentY = rand(0,height);
        WorldPoint randomPoint = new WorldPoint(targetArea.x + adjustmentX , targetArea.y + adjustmentY, client.getPlane());
        return randomPoint;
    }

    public WorldPoint random_WorldPoint(WorldPoint target_WorldPoint) {
        int width = 1;
        int height = 1;
        final int adjustmentX = rand(0,width);
        final int adjustmentY = rand(0,height);
        WorldPoint randomPoint = new WorldPoint(target_WorldPoint.getX() + adjustmentX ,
                target_WorldPoint.getY() + adjustmentY, client.getPlane());
        return randomPoint;
    }

    public double bearingConversion(double input){
        double output;
        if (input > 360){
            output = input - 360;
            return output;
        }
        else if (input < 0){
            output = input + 360;
            return output;
        }
        return input;
    }

    public void moveMouseWalk(Robot robot, WorldPoint worldPoint, int toleranceX, int toleranceY, int adjustmentLimitX,
                              int adjustmentLimitY) throws InterruptedException {
        mouseArrived.set(false);
        double adjustmentX = rand(-adjustmentLimitX,adjustmentLimitX);
        double adjustmentY = rand(-adjustmentLimitY,adjustmentLimitY);
        Point finalPoint;
        boolean minimap = false;
        if(inLocalViewRange(worldPoint))
        {
            finalPoint = worldPointToCanvas(worldPoint);
            if( outsideWindow(finalPoint) ) {
                minimap = true;
                finalPoint = worldPointToMiniMap(worldPoint);
                adjustmentX = 0.5 * adjustmentX;
                adjustmentY = 0.5 * adjustmentY;
                toleranceX = (int) ( 0.5 * toleranceX);
                toleranceY = (int) ( 0.5 * toleranceY);
                if(!pointInsideMiniMap(finalPoint)){
                    return;
                }
            }
        }
        else
        {
            finalPoint = worldPointToMiniMap(worldPoint);
            minimap = true;
            adjustmentX = 0.5 * adjustmentX;
            adjustmentY = 0.5 * adjustmentY;
            toleranceX = (int) ( 0.5 * toleranceX);
            toleranceY = (int) ( 0.5 * toleranceY);
            if(!pointInsideMiniMap(finalPoint)){
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
        while( ( remainingX >= toleranceX || remainingY >= toleranceY ) && numberOfSteps < numberOfStepThreshold )
        {
            numberOfSteps++;
            if(!minimap){
                if(inLocalViewRange(worldPoint)) {
                    finalPoint = worldPointToCanvas(worldPoint);
                    if (outsideWindow(finalPoint)) {
                        return;
                    }
                }
            }
            else
            {
                finalPoint = worldPointToMiniMap(worldPoint);
                if(!pointInsideMiniMap(finalPoint)){
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
            long pause = rand(50,100);
            Thread.sleep( pause);
        }
    }

    public void moveMouseWidget(Robot robot, int widgetGroupID, int widgetChildID) throws InterruptedException {
        mouseArrived.set(false);
        double width = client.getWidget(widgetGroupID,widgetChildID).getBounds().getWidth();
        double height = client.getWidget(widgetGroupID,widgetChildID).getBounds().getHeight();
        double toleranceX = 0.15 * width;
        double toleranceY = 0.15 * height;
        int adjustmentLimitX = (int) (0.3 * width);
        int adjustmentLimitY = (int) (0.3 * height);
        final int adjustmentX = rand(-adjustmentLimitX,adjustmentLimitX);
        final int adjustmentY = rand(-adjustmentLimitY,adjustmentLimitY);
        double widgetPointX = client.getWidget(widgetGroupID, widgetChildID).getBounds().getCenterX();
        double widgetPointY = client.getWidget(widgetGroupID, widgetChildID).getBounds().getCenterY();
        int finalX = (int) widgetPointX + adjustmentX;
        int finalY = (int) widgetPointY + adjustmentY + canvasOffsetY;
        Point finalPoint = new Point( finalX, finalY);
        if(outsideEntireWindow(finalPoint))
        {
            return;
        }
        Point startPosition = mouseCanvasLocation();
        int currentX = startPosition.getX();
        int currentY = startPosition.getY();
        int remainingX = Math.abs(finalX - currentX);
        int remainingY = Math.abs(finalY - currentY);
        if (remainingX < toleranceX && remainingY < toleranceY ) {
            mouseArrived.set(true);
            return;
        }
        moveMouseStart(robot,finalPoint);
        currentX = currentPosition.getX();
        currentY = currentPosition.getY();
        remainingX = Math.abs(finalX - currentX);
        remainingY = Math.abs(finalY - currentY);
        while( ( remainingX >= toleranceX || remainingY >= toleranceY ) && numberOfSteps < numberOfStepThreshold )
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
            long pause = rand(50,100);
            Thread.sleep( pause);
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
        double remainingDistance = rootSumSquare( finalX - startX, finalY - startY);
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
        if(rootSumSquare(dX,dY) > 16)
        {
            return;
        }
        double randomStartVelocityAdjustmentFactor = (double) rand(70,80) / 100;
        double baseVelocity = 0.26 + 0.041 * Math.pow(remainingDistance,0.55);
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

        int currentX = currentPosition.getX();
        int currentY = currentPosition.getY();
        int finalX = finalPoint.getX();
        int finalY = finalPoint.getY();
        double remainingDistance = rootSumSquare(finalX - currentX,finalY - currentY);

        double bearingVariant = Math.min( 25, remainingDistance / 4 );
        double bearing = bearingConversion( trueBearing + randomDistributionDouble(-bearingVariant,bearingVariant) );

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
        if(sectorLength > 16 )
        {
            return;
        }
        if(outsideEntireWindow(nextPosition))
        {
            Rectangle window = new Rectangle(5, 7 + canvasOffsetY, 1630, 1004);
            if (nextX < window.x){
                nextX = window.x + rand(2,8);
            }
            if (nextX > window.x + window.width){
                nextX = window.x + window.width - rand(2,8);
            }
            if (nextY < window.y){
                nextY = window.y + rand(2,8);
            }
            if (nextY > window.y + window.width){
                nextY = window.y + window.height - rand(2,8);
            }
        }
        double baseVelocity = 0.26 + 0.041 * Math.pow(remainingDistance,0.55);
        double accelerationFactor = Math.min(0.8, 4 / ( Math.pow(remainingDistance,0.5) + 0.1 ) );
        double acceleration = (baseVelocity - previousVelocity) * accelerationFactor;
        double velocity = randomDistributionDouble(90,130) * (previousVelocity + acceleration) / 100;
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
