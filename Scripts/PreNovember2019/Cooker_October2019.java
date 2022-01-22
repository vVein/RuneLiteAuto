package Polished.Scripts.PreNovember2019;

public class Cooker_October2019 {
    /*
     * Copyright (c) 2017, Seth <Sethtroll3@gmail.com>
     * All rights reserved.
     *
     * Redistribution and use in source and binary forms, with or without
     * modification, are permitted provided that the following conditions are met:
     *
     * 1. Redistributions of source code must retain the above copyright notice, this
     *    list of conditions and the following disclaimer.
     * 2. Redistributions in binary form must reproduce the above copyright notice,
     *    this list of conditions and the following disclaimer in the documentation
     *    and/or other materials provided with the distribution.
     *
     * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
     * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
     * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
     * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
     * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
     * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
     * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
     * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
     * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
     * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
     */
package net.runelite.client.plugins.woodcutting;

import com.google.common.collect.ImmutableSet;
import com.google.inject.Provides;

import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.List;
import javax.inject.Inject;
import lombok.Getter;
import net.runelite.api.*;
import net.runelite.api.Point;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameObjectChanged;
import net.runelite.api.events.GameObjectDespawned;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.Notifier;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDependency;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.xptracker.XpTrackerPlugin;
import net.runelite.client.ui.overlay.OverlayManager;

    @PluginDescriptor(
            name = "Woodcutting",
            description = "Show woodcutting statistics and/or bird nest notifications",
            tags = {"birds", "nest", "notifications", "overlay", "skilling", "wc"}
    )
    @PluginDependency(XpTrackerPlugin.class)
    public class WoodcuttingPlugin extends Plugin
    {
        @Inject
        private Notifier notifier;

        @Inject
        private Client client;

        @Inject
        private OverlayManager overlayManager;

        @Inject
        private WoodcuttingOverlay overlay;

        @Inject
        private WoodcuttingTreesOverlay treesOverlay;

        @Inject
        private WoodcuttingConfig config;

        @Getter
        private WoodcuttingSession session;

        @Getter
        private Axe axe;

        @Getter
        private final Set<GameObject> treeObjects = new HashSet<>();

        @Provides
        WoodcuttingConfig getConfig(ConfigManager configManager)
        {
            return configManager.getConfig(WoodcuttingConfig.class);
        }

        @Override
        protected void startUp() throws Exception
        {
            overlayManager.add(overlay);
            overlayManager.add(treesOverlay);
            AddonA_BackgroundRun.start();
        }

        @Override
        protected void shutDown() throws Exception
        {
            overlayManager.remove(overlay);
            overlayManager.remove(treesOverlay);
            treeObjects.clear();
            session = null;
            axe = null;
        }

        @Subscribe
        public void onGameTick(GameTick gameTick)
        {
            idleTimeSecs();
            checkMovementIdle();

            if(client.getLocalPlayer().getAnimation()==-1){
                idle = true;
            }
            else{idle = false;}

            if (session == null || session.getLastLogCut() == null)
            {
                return;
            }

            Duration statTimeout = Duration.ofMinutes(config.statTimeout());
            Duration sinceCut = Duration.between(session.getLastLogCut(), Instant.now());

            if (sinceCut.compareTo(statTimeout) >= 0)
            {
                session = null;
                axe = null;
            }
        }

        @Subscribe
        public void onChatMessage(ChatMessage event)
        {
            if (event.getType() == ChatMessageType.SPAM || event.getType() == ChatMessageType.GAMEMESSAGE)
            {
                if (event.getMessage().startsWith("You get some") && (event.getMessage().endsWith("logs.") || event.getMessage().endsWith("mushrooms.")))
                {
                    if (session == null)
                    {
                        session = new WoodcuttingSession();
                    }

                    session.setLastLogCut();
                }

                if (event.getMessage().contains("A bird's nest falls out of the tree") && config.showNestNotification())
                {
                    notifier.notify("A bird nest has spawned!");
                }
            }
        }

        @Subscribe
        public void onGameObjectSpawned(final GameObjectSpawned event)
        {
            GameObject gameObject = event.getGameObject();
            Tree tree = Tree.findTree(gameObject.getId());
            //System.out.print( " object id " + gameObject.getId());

            if (BANK_IDS.contains(gameObject.getId()) || RANGE_IDS.contains(gameObject.getId()))
            {
                gameObjects.add(gameObject);
            }

            if (tree != null)
            {
                treeObjects.add(gameObject);
            }
        }

        @Subscribe
        public void onGameObjectDespawned(final GameObjectDespawned event)
        {
            treeObjects.remove(event.getGameObject());
        }

        @Subscribe
        public void onGameObjectChanged(final GameObjectChanged event)
        {
            treeObjects.remove(event.getGameObject());
        }

        @Subscribe
        public void onGameStateChanged(final GameStateChanged event)
        {
            if (event.getGameState() != GameState.LOGGED_IN)
            {
                treeObjects.clear();
            }
        }

        @Subscribe
        public void onAnimationChanged(final AnimationChanged event)
        {
            Player local = client.getLocalPlayer();

            if (event.getActor() != local)
            {
                return;
            }

            int animId = local.getAnimation();
            Axe axe = Axe.findAxeByAnimId(animId);
            if (axe != null)
            {
                this.axe = axe;
            }
        }

        private List<GameObject> gameObjects = new ArrayList<>();

        private static final Set<Integer> BANK_IDS = ImmutableSet.of(ObjectID.BANK_BOOTH, ObjectID.BANK_BOOTH_10083, ObjectID.BANK_BOOTH_10355,
                ObjectID.BANK_BOOTH_10357,ObjectID.BANK_BOOTH_10517,ObjectID.BANK_BOOTH_10527,ObjectID.BANK_CHEST_10562,ObjectID.BANK_BOOTH_10583
                ,ObjectID.BANK_BOOTH_10584,ObjectID.BANK	,ObjectID.BANK_BOOTH_11338	,ObjectID.BANK_BOOTH_12798	,ObjectID.BANK_BOOTH_12799
                ,ObjectID.BANK_BOOTH_12800		,ObjectID.BANK_BOOTH_12801	,ObjectID.BANK_BOOTH_14367	,ObjectID.BANK_BOOTH_14368
                ,ObjectID.BANK_BOOTH_16642	,ObjectID.BANK_BOOTH_16700	,ObjectID.BANK_BOOTH_18491	,ObjectID.BANK_COUNTER
                ,ObjectID.BANK_BOOTH_20325	,ObjectID.BANK_BOOTH_20326	,ObjectID.BANK_BOOTH_20327	,ObjectID.BANK_BOOTH_20328
                ,ObjectID.BANK_BOOTH_22819	,ObjectID.BANK_BOOTH_24101,ObjectID.BANK_BOOTH_24347	,ObjectID.BANK_BOOTH_25808
                ,ObjectID.BANK_BOOTH_27254	,ObjectID.BANK_BOOTH_27260	,ObjectID.BANK_BOOTH_27263		,ObjectID.BANK_BOOTH_27265
                ,ObjectID.BANK_BOOTH_27267	,ObjectID.BANK_BOOTH_27292	,ObjectID.BANK_BOOTH_27718	,ObjectID.BANK_BOOTH_27719
                ,ObjectID.BANK_BOOTH_27720	,ObjectID.BANK_BOOTH_27721	,ObjectID.BANK_BOOTH_28429	,ObjectID.BANK_BOOTH_28430
                ,ObjectID.BANK_BOOTH_28431	,ObjectID.BANK_BOOTH_28432	,ObjectID.BANK_BOOTH_28433	,ObjectID.BANK_BOOTH_28546
                ,ObjectID.BANK_BOOTH_28547	,ObjectID.BANK_BOOTH_28548	,ObjectID.BANK_BOOTH_28549	,ObjectID.BANK_BOOTH_32666
                ,ObjectID.BANK_BOOTH_36559);

        private WorldPoint lastPosition;
        private Instant lastMoving;
        private Duration waitDuration = Duration.ofMillis(1062);

        private boolean checkMovementIdle ()
        {
            if (lastPosition == null)
            {
                lastPosition = Objects.requireNonNull(client.getLocalPlayer()).getWorldLocation();
                lastMoving = Instant.now();
                return false;
            }
            WorldPoint currentPosition = Objects.requireNonNull(client.getLocalPlayer()).getWorldLocation();

            if (lastPosition.equals(currentPosition))
            {
                if (Instant.now().compareTo(lastMoving.plus(waitDuration)) >= 0)
                {
                    return true;
                }
                return false;}
            else
            {
                lastPosition = currentPosition;
                lastMoving = Instant.now();
            }
            return false;
        }

        private long startTime = System.currentTimeMillis();
        private boolean idle = false;

        private long idleTimeSecs() {
            long elapsed;
            if (idle) {
                elapsed = ((System.currentTimeMillis() - startTime) / 1000);
            } else {
                elapsed = 0;
                startTime = System.currentTimeMillis();
            }
            return elapsed;
        }

        private long LastBreakTime = System.currentTimeMillis();
        private boolean Break = false;

        private long BreakTimer() {
            long elapsedRunTime;
            if (!Break) {
                elapsedRunTime = ((System.currentTimeMillis() - LastBreakTime) / 1000);
                return elapsedRunTime;
            } else {
                elapsedRunTime = 0;
                LastBreakTime = System.currentTimeMillis();
            }
            return elapsedRunTime;
        }

        private Random random = new Random();

        private int random2(int lowerLimit, int upperLimit){
            int rand2;
            if(lowerLimit == upperLimit){return rand2 = 0;} else
                rand2 = random.nextInt(upperLimit-lowerLimit) + lowerLimit;
            return rand2;
        }

        private double random2Dbl(double lowerLimit, double upperLimit){
            return (lowerLimit + (upperLimit - lowerLimit) * random.nextDouble());}

        private void RandomMouseMove() throws AWTException {
            Robot robot = new Robot();
            Point randomCanvas = new Point(random2(50,1600),random2(200,800));
            moveMouse1(robot,client.getMouseCanvasPosition(),randomCanvas,9,15,15);};

        private void moveMouse(Robot robot, Point CanvasPosition, Point MouseTarget, int speed, int xRandom, int yRandom){
            Point partway = new Point(MouseTarget.getX()+random2(-22,21),MouseTarget.getY()+random2(-18,19));
            moveMouse1(robot,CanvasPosition,partway,speed,xRandom,yRandom);
            moveMouse1(robot,partway,MouseTarget,speed/2,xRandom/2,yRandom/2);
        }

        private void moveMouse1(Robot robot, Point CanvasPosition, Point MouseTarget, int speed, int xRandom, int yRandom){
            if(Math.abs(CanvasPosition.getX()-MouseTarget.getX()) <= xRandom && Math.abs(CanvasPosition.getY()-MouseTarget.getY()) <= yRandom)
                return;

            Point[] cooardList;
            double t;    //the time interval
            double k = .025;
            cooardList = new net.runelite.api.Point[4];

            //set the beginning and end points
            cooardList[0] = CanvasPosition;
            cooardList[3] = new net.runelite.api.Point(MouseTarget.getX()+random2(-xRandom,xRandom),MouseTarget.getY()+(random2(-yRandom,yRandom)));

            int xout = (int)(Math.abs(MouseTarget.getX() - CanvasPosition.getX()) /10);
            int yout = (int)(Math.abs(MouseTarget.getY() - CanvasPosition.getY()) /10);

            int x=0,y=0;

            x = CanvasPosition.getX() < MouseTarget.getX()
                    ? CanvasPosition.getX() + ((xout > 0) ? random2(1,xout) : 1)
                    : CanvasPosition.getX() - ((xout > 0) ? random2(1,xout) : 1);
            y = CanvasPosition.getY() < MouseTarget.getY()
                    ? CanvasPosition.getY() + ((yout > 0) ? random2(1,yout) : 1)
                    : CanvasPosition.getY() - ((yout > 0) ? random2(1,yout) : 1);
            cooardList[1] = new Point(x,y);

            x = MouseTarget.getX() < CanvasPosition.getX()
                    ? MouseTarget.getX() + ((xout > 0) ? random2(1,xout) : 1)
                    : MouseTarget.getX() - ((xout > 0) ? random2(1,xout) : 1);
            y = MouseTarget.getY() < CanvasPosition.getY()
                    ?  MouseTarget.getY() + ((yout > 0) ? random2(1,yout) : 1)
                    : MouseTarget.getY() - ((yout > 0) ? random2(1,yout) : 1);
            cooardList[2] = new Point(x,y);

            double px = 0,py = 0;
            for(t=k;t<=1+k;t+=k){
                //use Berstein polynomials
                px=(cooardList[0].getX()+t*(-cooardList[0].getX()*3+t*(3*cooardList[0].getX()-
                        cooardList[0].getX()*t)))+t*(3*cooardList[1].getX()+t*(-6*cooardList[1].getX()+
                        cooardList[1].getX()*3*t))+t*t*(cooardList[2].getX()*3-cooardList[2].getX()*3*t)+
                        cooardList[3].getX()*t*t*t;
                py=(cooardList[0].getY()+t*(-cooardList[0].getY()*3+t*(3*cooardList[0].getY()-
                        cooardList[0].getY()*t)))+t*(3*cooardList[1].getY()+t*(-6*cooardList[1].getY()+
                        cooardList[1].getY()*3*t))+t*t*(cooardList[2].getY()*3-cooardList[2].getY()*3*t)+
                        cooardList[3].getY()*t*t*t;
                robot.mouseMove((int)px, (int)py);
                robot.delay(random2(speed,speed*2));
                //System.out.println("mouse control : " + px + " " + py + " mouse target " + MouseTarget);
            }
        }

        private void leftClick(Robot robot)
        {
            robot.delay(random2(12,26));
            robot.mousePress(InputEvent.BUTTON1_MASK);
            robot.delay(random2(204,348));
            robot.mouseRelease(InputEvent.BUTTON1_MASK);
            robot.delay(random2(183,299));
        }

        private void rightClick(Robot robot)
        {
            robot.delay(random2(12,26));
            robot.mousePress(InputEvent.BUTTON3_MASK);
            robot.delay(random2(204,348));
            robot.mouseRelease(InputEvent.BUTTON3_MASK);
            robot.delay(random2(183,299));
        }

        private int MenuIndex (String TargetMenuOption)
        {
            if (client.getMenuEntries()==null){return 0;}
            MenuEntry menuOptions[] = client.getMenuEntries();
            client.getWidgetPositionsX();
            int menuSize = menuOptions.length;
            int optionFromBottom = 0;
            int optionIndex;
            for (MenuEntry option : menuOptions)
            {
                if (option.getOption().matches(TargetMenuOption)){
                    optionIndex = menuSize - optionFromBottom;
                    return optionIndex;
                }
                optionFromBottom = optionFromBottom+1;
            }
            return 0;
        }

        private Point MenuIndexPosition (int MenuIndex,Point LastRightClick)
        {
            int RCStartY = LastRightClick.getY();
            int RCStartX = LastRightClick.getX();
            int baseYOffset = 27;
            int rowOffset = 15;
            int xTolerance = 35;
            int yTolerance = 4;
            int menuY = RCStartY + baseYOffset + (MenuIndex-1)*rowOffset + random2(-yTolerance,yTolerance);
            int menuX = RCStartX + random2(-xTolerance,xTolerance);
            Point MenuIndexPoint = new Point (menuX,menuY);
            return MenuIndexPoint;
        }

        private int i =0;
        private Thread AddonA_BackgroundRun = new Thread(new Runnable() { public void run() {
            for (i =0;i<500;i++){

                System.out.println(i+" | started | ");

                if (config.Addon_A() && client.getGameState() == GameState.LOGGED_IN) {

                    System.out.println(i+" | "
                            + " food inv " + rawFoodInventory() + " idle time " + idleTimeSecs()
                            + " idle " + checkMovementIdle() + " WL " + client.getLocalPlayer().getWorldLocation()
                            + " canvas " + client.getMouseCanvasPosition() + " animation " + client.getLocalPlayer().getAnimation()
                            //	+ " shrimp bank " +  BankLocation(getBankItemIndex(ItemID.RAW_SHRIMPS))
                            + " withdraw " + MenuIndexPosition(MenuIndex("Withdraw-All"),client.getMouseCanvasPosition())
                    );

                    try { addon();} catch (AWTException | InterruptedException e) { e.printStackTrace();	}

                    randomNPCCheck();
                    if(randomNPCCheck()!=null) {
                        System.out.println(i + " | OHtext " + randomNPCCheck().getOverheadText());
                    }
                    // OHtext Milord Vein X I, please speak to me!


                    if(client.getMenuEntries()!=null) {
                        System.out.println(i + " | menus " + Arrays.toString(client.getMenuEntries()));
                    }

                }
                try {  Thread.sleep(random2(5000,12000));  } catch (InterruptedException e) { e.printStackTrace(); }
            }}});

        private void addon() throws AWTException, InterruptedException {

            if(randomNPCCheck()!=null){
                dismissRandom();
            }

            if(client.getWidget(WidgetInfo.LEVEL_UP)!=null){
                Robot robot = new Robot();
                try {  Thread.sleep(random2(450,610));  } catch (InterruptedException e) { e.printStackTrace(); }
                robot.keyPress(KeyEvent.VK_SPACE);
                try {  Thread.sleep(random2(450,610));  } catch (InterruptedException e) { e.printStackTrace(); }
                robot.keyPress(KeyEvent.VK_SPACE);
                try {  Thread.sleep(random2(1100,1610));  } catch (InterruptedException e) { e.printStackTrace(); }
            }

            double ii = (double) i;
            double factorCheck = ii/50;
            if( factorCheck == (int)factorCheck ){
                //if(BreakTimer()>360){
                System.out.print(" || SLEEPING || ");
                {try {  Thread.sleep(random2(60000,120000));  } catch (InterruptedException e) { e.printStackTrace(); }}}
            //System.out.println(" | check 2 | ");
            while(!checkMovementIdle()){Thread.sleep(1000);}
            //System.out.println(" | check 3 | ");

            if(rawFoodInventory()>0){

                // Al Kharid range spot : x=3272, y=3180
                WorldPoint TargetArea = new WorldPoint(random2(3272,3275),random2(3179,3181),0);
                //System.out.println(" | check 4 | ");
                if(Math.abs(client.getLocalPlayer().getWorldLocation().getX()-TargetArea.getX())>4 ||
                        Math.abs(client.getLocalPlayer().getWorldLocation().getY()-TargetArea.getY())>4
                ){
                    //System.out.println(" | check 5 | ");
                    walk(TargetArea);
                    return;
                }
                //System.out.print(" | check 6 | ");
                List<GameObject> LocalRange = new ArrayList<>();

                for (GameObject object : gameObjects)
                {
                    if (object != null){
                        //System.out.println(" |      treespots  .  " + object.getId() + " , " + object.getWorldLocation());
                        if (RANGE_IDS.contains(object.getId()) && object.getWorldLocation().distanceTo2D(TargetArea) <= 7 &&
                                worldToCanvas(object.getWorldLocation()).getX()>8 && worldToCanvas(object.getWorldLocation()).getX()<1620
                                && worldToCanvas(object.getWorldLocation()).getY()>180 && worldToCanvas(object.getWorldLocation()).getY()<810 &&
                                idleTimeSecs()>3 )
                        { LocalRange.add(object); }}}
                System.out.println(" |      local range size  .  " + LocalRange.size());
                if (LocalRange.size() != 0 && idleTimeSecs()>3 ){
                    Point spotPerspective = worldToCanvas(LocalRange.get(0).getWorldLocation());
                    Point adjustedSpotPerspective = new Point (spotPerspective.getX()+random2(-5,5),spotPerspective.getY()+random2(0,10));
                    // need canvas check here and iterate
                    Robot robot = new Robot();
                    moveMouse(robot,client.getMouseCanvasPosition(),adjustedSpotPerspective,10,5,5);
                    leftClick(robot);
                    try {  Thread.sleep(random2(1800,2200));  } catch (InterruptedException e) { e.printStackTrace(); }
                    robot.keyPress(KeyEvent.VK_1);
                    return;}
                if (idleTimeSecs()<=3 ){
                    int randomInt = random2(1,10);
                    if (randomInt == 5)	{RandomMouseMove();}
                    return;}
            }

            //	AlKharidBank = (3270,3168,0);

            if (client.getLocalPlayer().getWorldLocation().getX() < AlKharidBank.getX()+5
                    && client.getLocalPlayer().getWorldLocation().getX() > AlKharidBank.getX()-5
                    && client.getLocalPlayer().getWorldLocation().getY() < AlKharidBank.getY()+5
                    && client.getLocalPlayer().getWorldLocation().getY() > AlKharidBank.getY()-5
                    && rawFoodInventory()==0)
            {bankInvent();}

            else{bankWalk();}
        }

        private final Set<Integer> RANGE_IDS = ImmutableSet.of(
                ObjectID.COOKING_RANGE
                ,ObjectID.RANGE
                ,ObjectID.COOKING_RANGE_4172
                ,ObjectID.RANGE_7183
                ,ObjectID.RANGE_7184
                ,ObjectID.COOKING_RANGE_8750
                ,ObjectID.RANGE_9682
                ,ObjectID.RANGE_9736
                ,ObjectID.RANGE_12102
                ,ObjectID.RANGE_12611
                ,ObjectID.STEEL_RANGE
                ,ObjectID.STEEL_RANGE_13540
                ,ObjectID.STEEL_RANGE_13541
                ,ObjectID.FANCY_RANGE
                ,ObjectID.FANCY_RANGE_13543
                ,ObjectID.FANCY_RANGE_13544
                ,ObjectID.COOKING_RANGE_16641
                ,ObjectID.COOKING_RANGE_16893
                ,ObjectID.RANGE_21792
                ,ObjectID.COOKING_RANGE_22154
                ,ObjectID.RANGE_22713
                ,ObjectID.RANGE_22714
                ,ObjectID.RANGE_25730
                ,ObjectID.RANGE_26181
                ,ObjectID.RANGE_26182
                ,ObjectID.RANGE_26183
                ,ObjectID.RANGE_26184
                ,ObjectID.RANGE_27516
                ,ObjectID.RANGE_27517
                ,ObjectID.RANGE_27724
                ,ObjectID.RANGE_31631
                ,ObjectID.COOKING_RANGE_32739
                ,ObjectID.COOKING_RANGE_35877
                ,ObjectID.RANGE_35980
                ,ObjectID.RANGE_36077
                ,ObjectID.RANGE_36699 );


        private boolean Equipped() {

            for (Item item : client.getItemContainer(InventoryID.EQUIPMENT).getItems())
            {
                if (item == null)
                {
                    continue;
                }

                if (item.getId() == ItemID.MITHRIL_SCIMITAR) {
                    return true;
                }
            }
            return false;
        }

        private Point BankLocation(int index) {
            // row = 1, n = index, column = n , while n>8 , column = n-8 row = row +1
            // row 1 y = 115  // row 2 y = 153  // column 1 x = 420 // column 2 x = 469  // 519  ,  189
            //canvas bank 355,88 	// column spacing of 50, tolerance 23 	// row spacing 37 , tolerance 22
            int bankBaseX = client.getWidget(WidgetInfo.BANK_ITEM_CONTAINER).getCanvasLocation().getX();
            int bankBaseY = client.getWidget(WidgetInfo.BANK_ITEM_CONTAINER).getCanvasLocation().getY();
            int n = index;
            int relateBaseX= 75;
            int relateBaseY= 27;
            int columnFactor = 47;
            int rowFactor = 37;
            int row = 1;
            if (n>8){
                while (n>8){n =n-8; row = row + 1;}}
            int column = n;
            int x = bankBaseX + relateBaseX + (column-1)*columnFactor;
            int y = bankBaseY + relateBaseY + (row-1)*rowFactor;
            int xTolerance = x+random2(-8,8);
            int yTolerance = y+random2(-8,8);
            Point itemBankLocation = new Point (xTolerance,yTolerance);
            return itemBankLocation;
        }

        private Point InvLocation(int index) {

            int invBaseX = client.getWidget(WidgetInfo.INVENTORY).getCanvasLocation().getX();
            int invBaseY = client.getWidget(WidgetInfo.INVENTORY).getCanvasLocation().getY();
            int n = index;
            int relateBaseX= 14;
            int relateBaseY= 15;
            int columnFactor = 42;
            int rowFactor = 36;
            int row = 1;
            if (n>4){
                while (n>4){n =n-4; row = row + 1;}}
            int column = n;
            int x = invBaseX + relateBaseX + (column-1)*columnFactor;
            int y = invBaseY + relateBaseY + (row-1)*rowFactor;
            int xTolerance = x+random2(-10,10);
            int yTolerance = y+random2(-10,10);
            Point itemInvLocation = new Point (xTolerance,yTolerance);
            return itemInvLocation;
        }

        private static final Set<Integer> FOOD_IDS = ImmutableSet.of(ItemID.RAW_LOBSTER, ItemID.RAW_SHRIMPS, ItemID.RAW_ANCHOVIES);

        private int rawFoodInventory() {
            int availableFood = 0;
            if (client.getItemContainer(InventoryID.INVENTORY)==null){return availableFood;}

            Item[] inventory = client.getItemContainer(InventoryID.INVENTORY).getItems();
            for (Item item : inventory) {
                //System.out.print(" || items id " + item.getId());
                int itemId = item.getId();
                if (FOOD_IDS.contains(itemId)){	availableFood=availableFood+1;	}}
            return availableFood; 	}

        private void eatFood () throws AWTException {
            while(client.getWidget(WidgetInfo.INVENTORY).isHidden()){
                int inventoryIconTopLeftX = client.getWidget(WidgetInfo.RESIZABLE_VIEWPORT_BOTTOM_LINE_INVENTORY_TAB).getBounds().x;
                int inventoryIconTopLeftY = client.getWidget(WidgetInfo.RESIZABLE_VIEWPORT_BOTTOM_LINE_INVENTORY_TAB).getBounds().y;
                int inventoryIconXWidth = (int) client.getWidget(WidgetInfo.RESIZABLE_VIEWPORT_BOTTOM_LINE_INVENTORY_TAB).getBounds().getWidth();
                int inventoryIconYHeight = (int) client.getWidget(WidgetInfo.RESIZABLE_VIEWPORT_BOTTOM_LINE_INVENTORY_TAB).getBounds().getHeight();
                int inventoryIconX = inventoryIconTopLeftX +3+ random2(0,inventoryIconXWidth-6);
                int inventoryIconY = inventoryIconTopLeftY+3 + random2(0,inventoryIconYHeight-6);
                Point inventoryIcon = new Point (inventoryIconX,inventoryIconY);
                Robot robot = new Robot();
                moveMouse(robot,client.getMouseCanvasPosition(),inventoryIcon,10,5,5);
                try {  Thread.sleep(random2(150,260));  } catch (InterruptedException e) { e.printStackTrace();}
                leftClick(robot);
                try {  Thread.sleep(random2(415,560));  } catch (InterruptedException e) { e.printStackTrace();}
            }
            int foodIndex = InvFoodIndex ();
            Point foodInvLocation = InvLocation(foodIndex);
            Robot robot = new Robot();
            moveMouse(robot,client.getMouseCanvasPosition(),foodInvLocation,10,5,5);
            try {  Thread.sleep(random2(150,260));  } catch (InterruptedException e) { e.printStackTrace();}
            leftClick(robot);
            try {  Thread.sleep(random2(415,560));  } catch (InterruptedException e) { e.printStackTrace();}
        }

        private int InvFoodIndex () {
            Item[] inventory = client.getItemContainer(InventoryID.INVENTORY).getItems();
            for (Item item : inventory) {
                int foodIndex = 1;
                int itemId = item.getId();
                if (FOOD_IDS.contains(itemId)) {
                    return foodIndex;
                }
                foodIndex = foodIndex + 1;
            }
            return 0;
        }

        private WorldPoint AlKharidBank = new WorldPoint(3270,3168,0);

        private void bankWalk() throws AWTException {
            // al kharid bank : x=3271, y=3167 or x=3269, y=3168
            WorldPoint bankLocation = new WorldPoint(AlKharidBank.getX()+random2(-2,+2),
                    AlKharidBank.getY()+random2(-2,+2),0);
            //System.out.print("random bank " + bankLocation);
            walk(bankLocation);
        }
        private GameObject GetBank() {
            List<GameObject> LocalBanks = new ArrayList<>();
            List<GameObject> LocalBanksInView = new ArrayList<>();
            List<GameObject> bankObjects = gameObjects;
            //System.out.println(" GetBank Check 0 " + gameObjects.size());
            for (GameObject object : bankObjects)
            {
                //System.out.println(" GetBank Check 1 ");
                if (object != null)
                {
                    if ( BANK_IDS.contains(object.getId())
                            && object.getWorldLocation().distanceTo2D(client.getLocalPlayer().getWorldLocation()) <= 7
                    )
                    {LocalBanks.add(object);}
                    //System.out.println(" GetBank Check 2 ");
                }}

            if (LocalBanks.size() != 0){
                //System.out.println(" GetBank Check 3 " + LocalBanks.size());
                for (GameObject localBank : LocalBanks)
                {
                    WorldPoint bankBooth1 = localBank.getWorldLocation();
                    Point bankBoothPerspective1 = worldToCanvas(bankBooth1);
                    if(bankBoothPerspective1.getX()>8 && bankBoothPerspective1.getX()<1620 && bankBoothPerspective1.getY()>180
                            && bankBoothPerspective1.getY()<810){
                        LocalBanksInView.add(localBank);}
                }}

            if (LocalBanksInView.size() != 0){
                GameObject LocalBank = LocalBanksInView.get(random2(0,LocalBanksInView.size()-1));
                return LocalBank;}

            return null;
        }

        private void bankInvent() throws AWTException, InterruptedException {
            while(!checkMovementIdle()){Thread.sleep(1063);}
            GameObject BankBooth = GetBank();
            WorldPoint bankBooth = BankBooth.getWorldLocation();
            //System.out.println(" BankInvent Check 0 " + bankBooth);
            Point bankBoothPerspective = worldToCanvas(bankBooth);
            //System.out.println(" BankInvent Check 2 " + bankBoothPerspective);
            if(bankBoothPerspective.getX()>8 && bankBoothPerspective.getX()<1620 && bankBoothPerspective.getY()>180 && bankBoothPerspective.getY()<810){
                //System.out.println(" BankInvent Check 3 ");
                Robot robot = new Robot();
                moveMouse(robot,client.getMouseCanvasPosition(),bankBoothPerspective,11,4,4);
                rightClick(robot);

                try {  Thread.sleep(random2(500,800));  } catch (InterruptedException e) { e.printStackTrace(); }
                Point selectBank = MenuIndexPosition(MenuIndex("Bank"),bankBoothPerspective);

                moveMouse(robot,bankBoothPerspective,selectBank,11,4,4);
                leftClick(robot);
                try {  Thread.sleep(random2(1100,2400));  } catch (InterruptedException e) { e.printStackTrace(); }
                while(!checkMovementIdle()){Thread.sleep(408);}

                if(depositInventoryPoint()==null){return;}
                Point depositInventoryPoint1 = depositInventoryPoint();
                try {  Thread.sleep(random2(400,500));  } catch (InterruptedException e) { e.printStackTrace(); }
                moveMouse(robot,selectBank,depositInventoryPoint1,11,4,4);
                leftClick(robot);
                try {  Thread.sleep(random2(500,800));  } catch (InterruptedException e) { e.printStackTrace(); }

                Point shrimpBanklocation = BankLocation(getBankItemIndex(ItemID.RAW_ANCHOVIES));

                moveMouse1(robot,depositInventoryPoint1,shrimpBanklocation,11,4,4);
                try {  Thread.sleep(random2(500,800));  } catch (InterruptedException e) { e.printStackTrace(); }
                rightClick(robot);

                try {  Thread.sleep(random2(500,800));  } catch (InterruptedException e) { e.printStackTrace(); }
                Point withdraw = MenuIndexPosition(MenuIndex("Withdraw-All"),shrimpBanklocation);

                moveMouse(robot,shrimpBanklocation,withdraw,11,4,4);
                leftClick(robot);

            }}

        private Point worldToCanvas(WorldPoint worldpoint){
            LocalPoint targetLL = LocalPoint.fromWorld(client, worldpoint.getX(), worldpoint.getY());
            Point perspective = Perspective.localToCanvas(client, targetLL, worldpoint.getPlane());
            Point adjustedPerspective = new Point(perspective.getX() + 1, perspective.getY() - 1);
            return adjustedPerspective;}

        private Point worldToMiniMap(WorldPoint worldpoint) {
            LocalPoint targetLL = LocalPoint.fromWorld(client, worldpoint.getX(), worldpoint.getY());
            if (targetLL != null) {
                Point minimapPerspective = Perspective.localToMinimap(client, targetLL);
                if (minimapPerspective != null) {
                    Point adjustedMinimapPerspective = new Point(minimapPerspective.getX() +4, minimapPerspective.getY() + 23);
                    return adjustedMinimapPerspective;
                }}
            return null; }

        private Point depositInventoryPoint(){
            if(client.getWidget(12,42)==null){return null;}
            Widget deposit_Inventory_Widget = client.getWidget(12,42);
            int deposit_x = (int)Math.round(random2Dbl(deposit_Inventory_Widget.getBounds().getMinX()+4, deposit_Inventory_Widget.getBounds().getMaxX())-4);
            int deposit_y = (int)Math.round(random2Dbl(deposit_Inventory_Widget.getBounds().getMinY() +18, deposit_Inventory_Widget.getBounds().getMaxY())+11);
            return new Point (deposit_x, deposit_y );}

        private void walk(WorldPoint finalLocation) throws AWTException {
            Robot robot = new Robot();
            int walkX = 0;
            int walkY = 0;
            int walkPlane = 0;
            WorldPoint temporaryTarget = new WorldPoint(walkX, walkY, walkPlane);
            temporaryTarget = finalLocation;

            Point temporaryTargetPerspective = worldToMiniMap(temporaryTarget);
            System.out.println("temporary target 1st " + temporaryTarget + " , " + temporaryTargetPerspective);

            if (temporaryTargetPerspective != null) {
                if (temporaryTargetPerspective.getX() > 1500 && temporaryTargetPerspective.getX() < 1620 && temporaryTargetPerspective.getY() > 30
                        && temporaryTargetPerspective.getY() < 130) {

                    moveMouse(robot, client.getMouseCanvasPosition(), temporaryTargetPerspective, 11, 3, 3);
                    leftClick(robot);
                    return;
                }
            }

            int startX = client.getLocalPlayer().getWorldLocation().getX();
            int endX = temporaryTarget.getX();
            int midwayX = Math.abs(startX - endX) / 2 + Math.min(startX, endX);
            int startY = client.getLocalPlayer().getWorldLocation().getY();
            int endY = temporaryTarget.getY();
            int midwayY = Math.abs(startY - endY) / 2 + Math.min(startY, endY);
            temporaryTarget = new WorldPoint(midwayX, midwayY, client.getLocalPlayer().getWorldLocation().getPlane());
            temporaryTargetPerspective = worldToMiniMap(temporaryTarget);

            while (temporaryTargetPerspective.getX() <= 1500 && temporaryTargetPerspective.getX() >= 1620 && temporaryTargetPerspective.getY() <= 30 && temporaryTargetPerspective.getY() >= 130) {

                endX = temporaryTarget.getX();
                midwayX = Math.abs(startX - endX) / 2 + Math.min(startX, endX);
                endY = temporaryTarget.getY();
                midwayY = Math.abs(startY - endY) / 2 + Math.min(startY, endY);
                temporaryTarget = new WorldPoint(midwayX, midwayY, client.getLocalPlayer().getWorldLocation().getPlane());
                temporaryTargetPerspective = worldToMiniMap(temporaryTarget);
                //System.out.println("temporary target iter'" + temporaryTarget);
            }
            //System.out.println("temporary target iter used" + temporaryTarget);
            moveMouse(robot, client.getMouseCanvasPosition(), temporaryTargetPerspective, 11, 4, 4);
            leftClick(robot);
        }

        private final Set<Integer> RANDOM_IDS = ImmutableSet.of(NpcID.BEE_KEEPER, NpcID.BEE_KEEPER_6747, NpcID.CAPT_ARNAV, NpcID.NILES, NpcID.MILES, NpcID.GILES, NpcID.SERGEANT_DAMIEN,
                NpcID.DRUNKEN_DWARF, NpcID.FREAKY_FORESTER, NpcID.FROG,NpcID.GENIE,NpcID.EVIL_BOB,NpcID.POSTIE_PETE,NpcID.LEO,NpcID.DR_JEKYLL,NpcID.MYSTERIOUS_OLD_MAN,NpcID.MYSTERIOUS_OLD_MAN_6742,
                NpcID.MYSTERIOUS_OLD_MAN_6750, NpcID.MYSTERIOUS_OLD_MAN_6751, NpcID.MYSTERIOUS_OLD_MAN_6752, NpcID.MYSTERIOUS_OLD_MAN_6753, NpcID.FLIPPA,NpcID.FLIPPA_6744,
                NpcID.PILLORY_GUARD, NpcID.QUIZ_MASTER, NpcID.RICK_TURPENTINE,NpcID.SANDWICH_LADY,NpcID.SECURITY_GUARD,NpcID.STRANGE_PLANT,NpcID.DUNCE);

        private NPC randomNPCCheck (){
            List<NPC> activeRandom = new ArrayList<>();
            List<NPC> NPCList;
            if(client.getNpcs()==null){return null;}
            NPCList = client.getNpcs();
            for (NPC npc : NPCList)
            {	if (RANDOM_IDS.contains(npc.getId()))
            {	activeRandom.add(npc);	}}
            if(activeRandom.size()!=0) {
                for (NPC random : activeRandom){
                    if (random.getOverheadText()!=null){
                        if (random.getOverheadText().contains("Vein")){
                            NPC TargetRandom = random;
                            return random;
                        }}
                }}
            return null;
        }

        private void dismissRandom() throws AWTException {
            NPC targetRandom = randomNPCCheck();
            WorldPoint randomWL = targetRandom.getWorldLocation();
            Point randomCanvas = worldToCanvas(randomWL);
            if(randomCanvas.getX()>8 && randomCanvas.getX()<1620 && randomCanvas.getY()>180 && randomCanvas.getY()<810) {
                Robot robot = new Robot();
                moveMouse(robot,client.getMouseCanvasPosition(),randomCanvas,11,4,4);
                rightClick(robot);

                try {  Thread.sleep(random2(500,800));  } catch (InterruptedException e) { e.printStackTrace(); }
                Point selectDismiss = MenuIndexPosition(MenuIndex("Dismiss"),randomCanvas);

                moveMouse(robot,randomCanvas,selectDismiss,11,4,4);
                leftClick(robot);
            }
        }

        final String[] randomEventActions = new String[] { "Talk-to", "Dismiss", "Examine" };
        // final NPCComposition randomEventComp = activeRandom.get(0).getComposition();

        private int getBankItemIndex(int itemID) {

            int index = 1;
            Item[] bankItems = client.getItemContainer(InventoryID.BANK).getItems();
            //System.out.println(Arrays.toString(bankItems));
            for (Item item : bankItems) {
                if (item.getId() == itemID) {
                    return index;
                } else {index = index + 1;}
            }
            return index = 0;
        }


    }
