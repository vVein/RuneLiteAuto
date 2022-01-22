package Polished.Scripts.PreNovember2019;

import java.awt.*;
import java.awt.event.InputEvent;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.List;

public class Woodcutter_Sep2019 {

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

        @Getter
        private final List<GameObject> gameObjects = new ArrayList<>();

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
            treeBackgroundRun.start();
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

            for (GameObject object : gameObjects)
            {
                if (object != null)
                {
                    if (BANK_IDS.contains(object.getId())){bankBooth1.add(object);}
                }}

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

        private List<Object> bankBooth1 = new ArrayList<>();
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
        private Thread treeBackgroundRun = new Thread(new Runnable() { public void run() {
            for (i =0;i<500;i++){

                System.out.println(i+" | started | ");

                if (config.treeAddon() && client.getGameState() == GameState.LOGGED_IN) {

                    System.out.println(i+" | "  + " av inv " + availableInventory() + " idle time " + idleTimeSecs() + " axe " + axeEquipped()
                            + " idle " + checkMovementIdle() + " WL " + client.getLocalPlayer().getWorldLocation() + " canvas " + client.getMouseCanvasPosition()
                            + " menu options " + MenuIndex("Bank") + " menu types " + Arrays.toString(client.getPlayerMenuTypes())
                            + " menu index point " + MenuIndexPosition(MenuIndex("Bank"),client.getMouseCanvasPosition())
                    );

                    try { addon();} catch (AWTException | InterruptedException e) { e.printStackTrace();	}

                    randomNPCCheck();
                    if(activeRandom.size()!=0){
                        System.out.println(i+" | OHtext "  + activeRandom.get(0).getOverheadText());
                        // OHtext Milord Vein X I, please speak to me!
                    }
                }
                try {  Thread.sleep(random2(3000,10000));  } catch (InterruptedException e) { e.printStackTrace(); }
            }}});

        private void addon() throws AWTException, InterruptedException {
            List<GameObject> localTrees = new ArrayList<>();
            final List<GameObject> spotTemp = new ArrayList<>(treeObjects);
            System.out.println(" | check 1 | ");
            double ii = (double) i;
            double factorCheck = ii/50;
            if( factorCheck == (int)factorCheck ){
                //if(BreakTimer()>360){
                System.out.print(" || SLEEPING || ");
                {try {  Thread.sleep(random2(60000,120000));  } catch (InterruptedException e) { e.printStackTrace(); }}}
            System.out.println(" | check 2 | ");
            while(!checkMovementIdle()){Thread.sleep(1000);}
            System.out.println(" | check 3 | ");
            if(availableInventory()>0 && axeEquipped()){
                WorldPoint WCArea = new WorldPoint(random2(3085,3087),random2(3232,3234),0);
                System.out.println(" | check 4 | ");
                if(Math.abs(client.getLocalPlayer().getWorldLocation().getX()-WCArea.getX())>9 ||
                        Math.abs(client.getLocalPlayer().getWorldLocation().getY()-WCArea.getY())>9
                ){
                    System.out.println(" | check 5 | ");
                    walk(WCArea);
                    return;
                }
                System.out.print(" | check 6 | ");
                for (GameObject object : spotTemp) {
                    Tree spot = Tree.findTree(object.getId());
                    //System.out.println(" |      treespots  .  " + object.getId() + " , " + object.getWorldLocation());
                    if (spot == Tree.WILLOW_TREE_SPAWN && object.getWorldLocation().distanceTo2D(client.getLocalPlayer().getWorldLocation()) <= 7 &&
                            idleTimeSecs()>3 )
                    { localTrees.add(object); }}
                System.out.println(" |      local trees  .  " + localTrees.size());
                if (localTrees.size() != 0 && idleTimeSecs()>3 ){
                    inverseSortTreeDistanceFromPlayer(localTrees);
                    Point spotPerspective = worldToCanvas(localTrees.get(0).getWorldLocation());
                    // need canvas check here and iterate
                    Robot robot = new Robot();
                    moveMouse(robot,client.getMouseCanvasPosition(),spotPerspective,10,5,5);
                    leftClick(robot);
                    return;}
                if (idleTimeSecs()<=3 ){return;}
                if (localTrees.size() == 0 && availableInventory()!=0 ) {return;}
            }

            // WorldPoint(x=3091, y=3243, plane=0) , 10355
            //ObjectID.BANK_BOOTH_10355, WorldPoint(x=3091, y=3245, plane=0)
//sprite Id bank inventory

            if (client.getLocalPlayer().getWorldLocation().getX()<3098&&client.getLocalPlayer().getWorldLocation().getX()>3091
                    &&client.getLocalPlayer().getWorldLocation().getY()<3247&&client.getLocalPlayer().getWorldLocation().getY()>3239&&availableInventory()==0)
            {bankInvent();}
            else if (client.getLocalPlayer().getWorldLocation().getX()<3098&&client.getLocalPlayer().getWorldLocation().getX()>3091
                    &&client.getLocalPlayer().getWorldLocation().getY()<3247&&client.getLocalPlayer().getWorldLocation().getY()>3239&&!axeEquipped())
            {bankInvent();}
            else{bankWalk();}
        }

        private boolean axeEquipped() {

            for (Item item : client.getItemContainer(InventoryID.EQUIPMENT).getItems())
            {
                if (item == null)
                {
                    continue;
                }

                if (item.getId() == ItemID.IRON_AXE || item.getId() == ItemID.STEEL_AXE || item.getId() == ItemID.BLACK_AXE) {
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

        private int availableInventory() {
            int availableInventory = 0;
            if (client.getItemContainer(InventoryID.INVENTORY)==null){availableInventory=1;
                return availableInventory;}
            if (client.getItemContainer(InventoryID.INVENTORY).getItems().length!=28){availableInventory=1;
                return availableInventory;}

            for (Item item : client.getItemContainer(InventoryID.INVENTORY).getItems()) {
                //System.out.print(" || items id " + item.getId());
                if (item.getId() == -1){	availableInventory=availableInventory+1;	}}
            return availableInventory; 	}

        private void bankWalk() throws AWTException {

            WorldPoint bankLocation = new WorldPoint(random2(3092,3097),random2(3240,3246),0);
            //System.out.print("random bank " + bankLocation);
            walk(bankLocation);
        }

        private void bankInvent() throws AWTException, InterruptedException {
            while(!checkMovementIdle()){Thread.sleep(1063);}
            WorldPoint bankBooth10355 = new WorldPoint(3091,3245,0);
            Point bankBoothPerspective = worldToCanvas(bankBooth10355);
            if(bankBoothPerspective.getX()>8 && bankBoothPerspective.getX()<1620 && bankBoothPerspective.getY()>180 && bankBoothPerspective.getY()<810){
                Robot robot = new Robot();
                moveMouse(robot,client.getMouseCanvasPosition(),bankBoothPerspective,11,4,4);
                rightClick(robot);
                //bank at x-45/+45,y+21/29,
                try {  Thread.sleep(random2(500,800));  } catch (InterruptedException e) { e.printStackTrace(); }
                Point selectBank = MenuIndexPosition(MenuIndex("Bank"),bankBoothPerspective);
                //Point selectBankPerspective = new Point(bankBoothPerspective.getX()+random2(-45,45),bankBoothPerspective.getY()+random2(24,29));
                moveMouse(robot,bankBoothPerspective,selectBank,11,4,4);
                leftClick(robot);
                try {  Thread.sleep(random2(1100,2400));  } catch (InterruptedException e) { e.printStackTrace(); }
                while(!checkMovementIdle()){Thread.sleep(408);}
                //BANK_DEPOSIT_INVENTORY = 1041; deposit:775-782,774-797
                //Point depositInventory = new Point(random2(890,909),random2(800,810));
                if(depositInventoryPoint()==null){return;}
                Point depositInventoryPoint1 = depositInventoryPoint();
                try {  Thread.sleep(random2(400,500));  } catch (InterruptedException e) { e.printStackTrace(); }
                moveMouse(robot,selectBank,depositInventoryPoint1,11,4,4);
                leftClick(robot);
                try {  Thread.sleep(random2(500,800));  } catch (InterruptedException e) { e.printStackTrace(); }
                //net 555-576,167-188
                Point axeBanklocation = BankLocation(getBankItemIndex(ItemID.MITHRIL_AXE));
                //System.out.println("Item net : " + " , "  );
                //Point withdrawNet = new Point(random2(676,699),random2(182,198));
                moveMouse1(robot,depositInventoryPoint1,axeBanklocation,11,4,4);
                try {  Thread.sleep(random2(500,800));  } catch (InterruptedException e) { e.printStackTrace(); }
                leftClick(robot);
            }}

        private Point worldToCanvas(WorldPoint worldpoint){
            LocalPoint targetLL = LocalPoint.fromWorld(client, worldpoint.getX(), worldpoint.getY());
            Point perspective = Perspective.localToCanvas(client, targetLL, worldpoint.getPlane());
            Point adjustedPerspective = new Point(perspective.getX(), perspective.getY() + 11);
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

        private boolean FirstLeftClickOption(){

            MenuEntry [] options = client.getMenuEntries();
            int options_Size = options.length;
            int first_Option_Index = options.length - 1;
            MenuEntry firstOption = options[first_Option_Index];
            //option=Walk here
            MenuAction DesiredAction = MenuAction.WALK;
            if (firstOption.getType() == 23)
            {return true;}
            return false;
        }

        private void walk(WorldPoint finalLocation) throws AWTException {
            Robot robot = new Robot();
            WorldPoint temporaryTarget;
            Point temporaryTargetPerspective;

            if(worldToCanvas(finalLocation)!=null){
                temporaryTargetPerspective = worldToCanvas(finalLocation);

                if (temporaryTargetPerspective != null) {
                    if (PointInsideClickableWindow(temporaryTargetPerspective)) {
                        Point MouseCanvasPosition = new Point (client.getMouseCanvasPosition().getX(),client.getMouseCanvasPosition().getY()+20);
                        moveMouse(robot, MouseCanvasPosition, temporaryTargetPerspective, 10, 4, 4);
                        if (FirstLeftClickOption())
                        {					leftClick(robot);
                            return;}

                        rightClick(robot);
                        try {	Thread.sleep(random2(500, 800));} catch (InterruptedException e) {	e.printStackTrace();}

                        Point rightClickMenu = MenuIndexPosition(MenuIndex("Walk here"), temporaryTargetPerspective);
                        MouseCanvasPosition = new Point (client.getMouseCanvasPosition().getX(),client.getMouseCanvasPosition().getY()+20);
                        moveMouse(robot, MouseCanvasPosition, rightClickMenu, 11, 4, 4);
                        leftClick(robot);
                        return;
                    }
                }}
            System.out.print(" | Walk Check 1 | ");
            temporaryTargetPerspective = worldToMiniMap(finalLocation);
            if (temporaryTargetPerspective != null) {
                if (PointInsideMiniMap(temporaryTargetPerspective)) {
                    System.out.print(" | Walk Check 2 | ");
                    Point MouseCanvasPosition = new Point (client.getMouseCanvasPosition().getX(),client.getMouseCanvasPosition().getY()+20);
                    moveMouse1(robot, MouseCanvasPosition, temporaryTargetPerspective, 11, 5, 5);
                    leftClick(robot);
                    return;
                }
            }

            int startX = client.getLocalPlayer().getWorldLocation().getX();
            int endX = finalLocation.getX();
            double partwayXD = startX + (endX - startX) * 0.9;
            int partwayX = (int) partwayXD;
            int startY = client.getLocalPlayer().getWorldLocation().getY();
            int endY = finalLocation.getY();
            double partwayYD = startY + (endY - startY) * 0.9;
            int partwayY = (int) partwayYD;
            temporaryTarget = new WorldPoint(partwayX, partwayY, client.getLocalPlayer().getWorldLocation().getPlane());
            temporaryTargetPerspective = worldToMiniMap(temporaryTarget);
            System.out.print(" | Walk Check 3 | ");

            while (!PointInsideMiniMap(temporaryTargetPerspective)) {

                endX = temporaryTarget.getX();
                partwayXD = startX + (endX - startX) * 0.75;
                partwayX = (int) partwayXD;
                endY = temporaryTarget.getY();
                partwayYD = startY + (endY - startY) * 0.75;
                partwayY = (int) partwayYD;
                temporaryTarget = new WorldPoint (partwayX, partwayY, client.getLocalPlayer().getWorldLocation().getPlane());
                temporaryTargetPerspective = worldToMiniMap(temporaryTarget);
                //System.out.println("temporary target iter'" + temporaryTarget);
            }
            //System.out.println("temporary target iter used" + temporaryTarget);
            Point MouseCanvasPosition = new Point (client.getMouseCanvasPosition().getX(),client.getMouseCanvasPosition().getY()+20);
            moveMouse1(robot, MouseCanvasPosition, temporaryTargetPerspective, 11, 4, 4);
            leftClick(robot);
        }

        private final Set<Integer> RANDOM_IDS = ImmutableSet.of(NpcID.BEE_KEEPER, NpcID.BEE_KEEPER_6747, NpcID.CAPT_ARNAV, NpcID.NILES, NpcID.MILES, NpcID.GILES, NpcID.SERGEANT_DAMIEN,
                NpcID.DRUNKEN_DWARF, NpcID.FREAKY_FORESTER, NpcID.FROG,NpcID.GENIE,NpcID.EVIL_BOB,NpcID.POSTIE_PETE,NpcID.LEO,NpcID.DR_JEKYLL,NpcID.MYSTERIOUS_OLD_MAN,NpcID.MYSTERIOUS_OLD_MAN_6742,
                NpcID.MYSTERIOUS_OLD_MAN_6750, NpcID.MYSTERIOUS_OLD_MAN_6751, NpcID.MYSTERIOUS_OLD_MAN_6752, NpcID.MYSTERIOUS_OLD_MAN_6753,
                NpcID.PILLORY_GUARD, NpcID.QUIZ_MASTER, NpcID.RICK_TURPENTINE,NpcID.SANDWICH_LADY,NpcID.SECURITY_GUARD,NpcID.STRANGE_PLANT,NpcID.DUNCE);

        private List<NPC> activeRandom = new ArrayList<>();

        private void randomNPCCheck (){
            List<NPC> NPCList;
            if(client.getNpcs()==null){return;}
            NPCList = client.getNpcs();
            for (NPC npc : NPCList)
            {	if (RANDOM_IDS.contains(npc.getId()))
            {	activeRandom.add(npc);	}}
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

        private List<GameObject> inverseSortTreeDistanceFromPlayer(List<GameObject> localTrees)
        {
            if (localTrees.isEmpty())
            {
                return null;
            }

            final LocalPoint cameraPoint = new LocalPoint(client.getCameraX(), client.getCameraY());
            localTrees.sort(
                    Comparator.comparing(
                            // Negate to have the furthest first
                            (GameObject object) -> -object.getLocalLocation().distanceTo(cameraPoint))
                            // Order by position
                            .thenComparing(GameObject::getLocalLocation, Comparator.comparing(LocalPoint::getX)
                                    .thenComparing(LocalPoint::getY))
                            // And then by id
                            .thenComparing(GameObject::getId)
            );
            return localTrees;
        }

    }

