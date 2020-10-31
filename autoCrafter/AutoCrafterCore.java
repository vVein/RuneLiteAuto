package net.runelite.client.plugins.autoCrafter;

import com.google.common.collect.ImmutableList;
import lombok.AccessLevel;
import lombok.Getter;
import net.runelite.api.Point;
import net.runelite.api.*;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.plugins.autoalchemy.AutoalchemyPlugin;

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

public class AutoCrafterCore {

    @Inject
    private Client client;

    @Inject
    private AutoCrafterPlugin plugin;

    @Getter (AccessLevel.PACKAGE)
    public static AtomicBoolean npcChanged = new AtomicBoolean();

    @Getter (AccessLevel.PACKAGE)
    public static AtomicBoolean mouseArrived = new AtomicBoolean();

    @Getter (AccessLevel.PACKAGE)
    public static AtomicBoolean bankWidgetOpen = new AtomicBoolean();

    @Getter (AccessLevel.PACKAGE)
    public static AtomicBoolean activeTargetGameObject = new AtomicBoolean();

    @Getter (AccessLevel.PACKAGE)
    public static AtomicBoolean runActivated = new AtomicBoolean();

    public static int canvasOffsetY = 23;

    public int runEnergy = 0;

    public static int numberOfStepThreshold = 220;

    public String accountName = "vvvVein";

    @Getter (AccessLevel.PACKAGE)
    public GameObject targetGameObject;

    @Getter (AccessLevel.PACKAGE)
    public Rectangle activeTargetGameObjectClickBox;

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
    public String requiredItem1String = requiredItem1NecklaceMouldString;
    public String requiredItem2String = "Gold bar";
    public String requiredItem3SapphireString = "Sapphire";
    public String requiredItem3RubyString = "Ruby";
    public String requiredItem3DiamondString = "Diamond";
    public String requiredItem3String = requiredItem3DiamondString;

    public String walkText = "Walk here";
    public String withdrawHighlightText = "Withdraw-1";
    public String withdrawAmountTargetText = "Withdraw-13";
    public String depositInventoryText = "Deposit inventory";

    public WorldPoint edgevilleFurnaceSpot = new WorldPoint(3109, 3499,0);
    public Rectangle edgevilleFurnaceArea = new Rectangle(3105, 3496,3111 + 1 - 3105,3502 + 1 - 3496);
    public Rectangle edgevilleFurnaceWalkArea = new Rectangle(3105, 3498,3107 + 1 - 3105,3499 + 1 - 3498);
    public Rectangle edgevilleFurnaceContainerArea = new Rectangle(3109, 3498,3111 + 1 - 3109,3500 + 1 - 3498);

    public Rectangle edgevilleBankBoothContainerArea = new Rectangle(3095, 3492,3098 + 1 - 3095,3494 + 1 - 3492);
    public Rectangle edgevilleBankArea = new Rectangle(3094, 3493,3099 + 1 - 3094,3496 + 1 - 3493);
    public Rectangle edgevilleBankWalkArea = new Rectangle(3096, 3494,3098 + 1 - 3096,3496 + 1 - 3494);

    public Rectangle bankBoothContainerArea = edgevilleBankBoothContainerArea;
    public Rectangle bankArea = edgevilleBankArea;
    public List<Integer> bankObjectList = edgevilleTopEdgeBankObjectList;
    public WorldPoint targetSpot = edgevilleFurnaceSpot;
    public Rectangle targetArea = edgevilleFurnaceArea;
    public String useBankText = useEdgevilleBankText;

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
                System.out.println(" firstOption.getTarget " + firstOption.getTarget() );
                System.out.println(" firstOption.getIdentifier " + firstOption.getIdentifier() );
                System.out.println(" firstOption.getOption " + firstOption.getOption() );
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
                System.out.println(" firstOption.getTarget " + firstOption.getTarget() );
                System.out.println(" firstOption.getIdentifier " + firstOption.getIdentifier() );
                System.out.println(" firstOption.getOption " + firstOption.getOption() );
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
                System.out.println(" firstOption.getTarget " + firstOption.getTarget() );
                System.out.println(" firstOption.getIdentifier " + firstOption.getIdentifier() );
                System.out.println(" firstOption.getOption " + firstOption.getOption() );
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
                System.out.println(" firstOption.getTarget " + firstOption.getTarget() );
                System.out.println(" firstOption.getIdentifier " + firstOption.getIdentifier() );
                System.out.println(" firstOption.getOption " + firstOption.getOption() );
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

    public int rightClickMenuIndex(String targetName, String targetAction) {
        if (client.getMenuEntries() == null) {
            return 0;
        }
        MenuEntry[] menuOptions = client.getMenuEntries();
        int menuSize = menuOptions.length;
        int optionFromBottom = 0;
        int optionIndex;
        for (MenuEntry option : menuOptions) {
            if (option.getTarget().contains(targetName) && option.getOption().equals(targetAction)) {
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

    public Point rightClickMenuPoint(String targetName, String targetAction) {
        Point startPosition = mouseCanvasLocation();
        int startX = startPosition.getX();
        int startY = startPosition.getY();
        int baseYOffset = 4; /// + canvasOffsetY;
        int rowOffset = 15;
        int xTolerance = 35;
        int yTolerance = 3;
        int randomX = rand(-xTolerance, xTolerance);
        int randomY = rand(-yTolerance, yTolerance);
        int menuIndex = rightClickMenuIndex(targetName, targetAction);
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

    public void bankWidgetOpenCheck(){

        if (client.getWidget(WidgetInfo.BANK_ITEM_CONTAINER) == null) {
            bankWidgetOpen.set(false);
            return;
        }
        bankWidgetOpen.set(!client.getWidget(WidgetInfo.BANK_ITEM_CONTAINER).isHidden());
    }

    public void craftingWidgetOpenCheck(){

        if (client.getWidget(446, 1) == null) {
            AutoCrafterPlugin.jewelryCraftingWidgetOpen.set(false);
            return;
        }

        AutoCrafterPlugin.jewelryCraftingWidgetOpen.set(!client.getWidget(446, 1).isHidden());

    }

    public void openBank() throws InterruptedException, AWTException {
        Robot robot = new Robot();
        if(!bankWidgetOpen.get()) {
            if(playerInTargetArea(bankArea)) {
                if (gameObjectFromTiles(bankObjectList, bankBoothContainerArea)) {
                    moveMouseGameObject(robot, 0.35, 0.33);
                    if (mouseArrived.get()){
                        AutoCrafterCore.activeTargetGameObject.set(false);
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
                            if (AutoCrafterCore.mouseArrived.get() ) {
                                leftClick(robot);
                                int randomDelay2 = rand(1000, 2000);
                                Thread.sleep(randomDelay2);
                            }
                        }
                    }

                }
            }
            else if(gameObjectFromTiles(bankObjectList, bankBoothContainerArea)){
                moveMouseGameObject(robot, 0.35, 0.33);
                if (mouseArrived.get()){
                    AutoCrafterCore.activeTargetGameObject.set(false);
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
                        if (AutoCrafterCore.mouseArrived.get() ) {
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
            if (AutoCrafterCore.mouseArrived.get() ) {
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

    public boolean inLocalViewRange(WorldPoint worldPointTarget)
    {
        WorldPoint playerWorldLocation = client.getLocalPlayer().getWorldLocation();
        double distance = playerWorldLocation.distanceTo(worldPointTarget);
        int MAX_ACTOR_VIEW_RANGE = 8;
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
                            if (client.getLocalPlayer().getWorldLocation().distanceTo(tile.getWorldLocation()) < 7 &&
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
        matchedTargets.sort(new gameObjectDistanceComparator());
        targetGameObject = matchedTargets.get(0);
        System.out.println(" targetGameObject " + targetGameObject);
        if(targetGameObject.getCanvasTilePoly() != null){
            activeTargetGameObjectClickBox = targetGameObject.getCanvasTilePoly().getBounds();
            if(outsideWindow(new Point((int) activeTargetGameObjectClickBox.getCenterX(),(int) activeTargetGameObjectClickBox.getCenterY()))){
                return false;
            }
            activeTargetGameObject.set(true);
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
            System.out.println(" moveMouseFixedPoint remainingX " + remainingX + " , " + remainingY );
        }
        if (remainingX < toleranceX && remainingY < toleranceY ) {
            mouseArrived.set(true);
        }
    }

    public void moveMouseBankItem(Robot robot, Widget bankItem ) throws InterruptedException {
        int centreX = bankItem.getCanvasLocation().getX() + bankItem.getWidth() / 2;
        int centreY = bankItem.getCanvasLocation().getY() + bankItem.getHeight() / 2 + canvasOffsetY;
        int toleranceX = (int) (0.15 * bankItem.getWidth() );
        int toleranceY = (int) (0.15 * bankItem.getHeight() );
        System.out.println(" moveMouseBankItem tolerance " + toleranceX + " , " + toleranceY );
        int adjustmentLimitX = (int) (0.3 * bankItem.getWidth() );
        int adjustmentLimitY = (int)  (0.3 * bankItem.getHeight() );
        System.out.println(" moveMouseBankItem adjustmentLimitX " + adjustmentLimitX + " , " + adjustmentLimitY );
        mouseArrived.set(false);
        final int adjustmentX = rand(-adjustmentLimitX,adjustmentLimitX);
        final int adjustmentY = rand(-adjustmentLimitY,adjustmentLimitY);
        int finalX = centreX + adjustmentX;
        int finalY = centreY + adjustmentY;
        System.out.println(" moveMouseBankItem final " + finalX + " , " + finalY );
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
            System.out.println(" moveMouseBankItem numberOfSteps " + numberOfSteps );
            moveMouse(robot,finalPoint);
            currentX = currentPosition.getX();
            currentY = currentPosition.getY();
            remainingX = Math.abs(finalX - currentX);
            remainingY = Math.abs(finalY - currentY);
            System.out.println(" moveMouseBankItem remainingX " + remainingX + " , " + remainingY );
        }
        System.out.println(" moveMouseBankItem stop position " + currentX + " , " + currentY );
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
        }
    }

    public void moveMouseGameObject(Robot robot, double adjustmentXMultiplier, double adjustmentYMultiplier ) throws InterruptedException {
        mouseArrived.set(false);
        Rectangle2D objectClickBox = activeTargetGameObjectClickBox;
        int clickboxCentreX = (int) objectClickBox.getCenterX();
        int clickboxCentreY = (int) objectClickBox.getCenterY() + canvasOffsetY;
        int priorClickboxCentreX = (int) objectClickBox.getCenterX();
        int priorClickboxCentreY = (int) objectClickBox.getCenterY() + canvasOffsetY;
        int toleranceX = (int) (0.12 * objectClickBox.getWidth());
        int toleranceY = (int) (0.12 * objectClickBox.getHeight());
        System.out.println(" moveMouseGameObject tolerance " + toleranceX + " , " + toleranceY );
        int adjustmentLimitX = (int) (adjustmentXMultiplier * objectClickBox.getWidth());
        int adjustmentLimitY = (int) (adjustmentYMultiplier * objectClickBox.getHeight());
        final int adjustmentX = rand(-adjustmentLimitX,adjustmentLimitX);
        final int adjustmentY = rand(-adjustmentLimitY,adjustmentLimitY);

        int finalX = clickboxCentreX + adjustmentX;
        int finalY = clickboxCentreY + adjustmentY;
        Point finalPoint = new Point(finalX, finalY);
        System.out.println(" moveMouseGameObject finalPoint " + finalPoint );
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
            objectClickBox = activeTargetGameObjectClickBox;
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
            System.out.println(" moveMouseGameObject remaining " + remainingX + " , " + remainingY );
        }
        if (remainingX < toleranceX && remainingY < toleranceY ) {
            mouseArrived.set(true);
            System.out.println(" moveMouseGameObject stop position " + currentX + " , " + currentY );
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
        while( ( remainingX >= toleranceX || remainingY >= toleranceY ) && numberOfSteps < numberOfStepThreshold )
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
        System.out.println(" finalPoint " + finalPoint );
        if(outsideEntireWindow(finalPoint))
        {
            return;
        }
        Point startPosition = mouseCanvasLocation();
        int currentX = startPosition.getX();
        int currentY = startPosition.getY();
        int remainingX = Math.abs(finalX - currentX);
        int remainingY = Math.abs(finalY - currentY);
        System.out.println(" remainingX " + remainingX);
        System.out.println(" remainingY " + remainingY);
        if (remainingX < toleranceX && remainingY < toleranceY ) {
            System.out.println(" already on point " );
            mouseArrived.set(true);
            return;
        }
        moveMouseStart(robot,finalPoint);
        currentX = currentPosition.getX();
        currentY = currentPosition.getY();
        remainingX = Math.abs(finalX - currentX);
        remainingY = Math.abs(finalY - currentY);
        System.out.println(" remainingX " + remainingX);
        System.out.println(" remainingY " + remainingY);
        while( ( remainingX >= toleranceX || remainingY >= toleranceY ) && numberOfSteps < numberOfStepThreshold )
        {
            //System.out.println(" numberOfSteps " + numberOfSteps );
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
        double remainingDistance = rootSumSquare(finalX - currentX,finalY - currentY);
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
            if(offTargetCounter > 6)
            {
                System.out.println(" offTargetCounter " + offTargetCounter);
                offTargetCounter = 0;
                Thread.sleep(2000);
                return;
            }
            offTargetCounter++;
        }
        if(bearingDelta > 150 && remainingDistance < 200 )
        {
            if(remainingDistance < 200 && overShootCounter < 2 ) {
                double discrepancy = rand(-12, 12);
                bearing = trueBearing + discrepancy;
                bearingDelta = Math.abs(trueBearing - bearing);
            }
            if(overShootCounter > 1 )
            {
                System.out.println(" overShootCounter " + overShootCounter);
                overShootCounter = 0;
                Thread.sleep(2000);
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
        double baseVelocity = 0.26 + 0.041 * Math.pow(remainingDistance,0.55);
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
