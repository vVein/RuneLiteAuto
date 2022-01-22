package Polished.Scripts.PreNovember2019;

import java.awt.*;
import java.awt.event.InputEvent;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.List;

public class ShrimpFisherSep2019 {

    /*
     * Copyright (c) 2017, Seth <Sethtroll3@gmail.com>
     * Copyright (c) 2018, Levi <me@levischuck.com>
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
package net.runelite.client.plugins.fishing;

import com.google.common.collect.ImmutableSet;
import com.google.inject.Provides;

import java.awt.*;
import java.awt.event.InputEvent;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.Point;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.InteractingChanged;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.NpcSpawned;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetID;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.api.widgets.WidgetItem;
import net.runelite.client.Notifier;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDependency;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.xptracker.XpTrackerPlugin;
import net.runelite.client.ui.overlay.OverlayManager;

    @PluginDescriptor(
            name = "Fishing",
            description = "Show fishing stats and mark fishing spots",
            tags = {"overlay", "skilling"}
    )
    @PluginDependency(XpTrackerPlugin.class)
    @Singleton
    @Slf4j
    public class FishingPlugin extends Plugin
    {
        private static final int TRAWLER_SHIP_REGION_NORMAL = 7499;
        private static final int TRAWLER_SHIP_REGION_SINKING = 8011;
        private static final int TRAWLER_TIME_LIMIT_IN_SECONDS = 614;
        private static final int TRAWLER_ACTIVITY_THRESHOLD = Math.round(0.15f * 255);

        private Instant trawlerStartTime;

        @Getter(AccessLevel.PACKAGE)
        private final FishingSession session = new FishingSession();

        @Getter(AccessLevel.PACKAGE)
        private final Map<Integer, MinnowSpot> minnowSpots = new HashMap<>();

        @Getter(AccessLevel.PACKAGE)
        private final List<NPC> fishingSpots = new ArrayList<>();

        @Getter(AccessLevel.PACKAGE)
        private final List<GameObject> gameObjects = new ArrayList<>();

        @Getter
        private final Set<Object> gameObjects1 = new HashSet<>();

        @Getter(AccessLevel.PACKAGE)
        private FishingSpot currentSpot;

        @Inject
        private Client client;

        @Inject
        private Notifier notifier;

        @Inject
        private OverlayManager overlayManager;

        @Inject
        private FishingConfig config;

        @Inject
        private FishingOverlay overlay;

        @Inject
        private FishingSpotOverlay spotOverlay;

        @Inject
        private FishingSpotMinimapOverlay fishingSpotMinimapOverlay;

        private boolean trawlerNotificationSent;

        @Provides
        FishingConfig provideConfig(ConfigManager configManager)
        {
            return configManager.getConfig(FishingConfig.class);
        }

        @Override
        protected void startUp() throws Exception
        {
            overlayManager.add(overlay);
            overlayManager.add(spotOverlay);
            overlayManager.add(fishingSpotMinimapOverlay);
            backgroundRun.start();
        }

        @Override
        protected void shutDown() throws Exception
        {
            spotOverlay.setHidden(true);
            fishingSpotMinimapOverlay.setHidden(true);
            overlayManager.remove(overlay);
            overlayManager.remove(spotOverlay);
            overlayManager.remove(fishingSpotMinimapOverlay);
            fishingSpots.clear();
            minnowSpots.clear();
            trawlerNotificationSent = false;
            currentSpot = null;
            trawlerStartTime = null;
        }

        @Subscribe
        public void onGameStateChanged(GameStateChanged gameStateChanged)
        {
            GameState gameState = gameStateChanged.getGameState();
            if (gameState == GameState.CONNECTION_LOST || gameState == GameState.LOGIN_SCREEN || gameState == GameState.HOPPING)
            {
                fishingSpots.clear();
                minnowSpots.clear();
            }
        }

        @Subscribe
        public void onItemContainerChanged(ItemContainerChanged event)
        {

            if (event.getItemContainer() != client.getItemContainer(InventoryID.INVENTORY)
                    && event.getItemContainer() != client.getItemContainer(InventoryID.EQUIPMENT))
            {
                return;
            }

            final boolean showOverlays = session.getLastFishCaught() != null
                    || canPlayerFish(client.getItemContainer(InventoryID.INVENTORY))
                    || canPlayerFish(client.getItemContainer(InventoryID.EQUIPMENT));

            if (!showOverlays)
            {
                currentSpot = null;
            }

            spotOverlay.setHidden(!showOverlays);
            fishingSpotMinimapOverlay.setHidden(!showOverlays);
        }

        @Subscribe
        public void onChatMessage(ChatMessage event)
        {
            if (event.getType() != ChatMessageType.SPAM)
            {
                return;
            }

            if (event.getMessage().contains("You catch a") || event.getMessage().contains("You catch some") ||
                    event.getMessage().equals("Your cormorant returns with its catch."))
            {
                session.setLastFishCaught(Instant.now());
                spotOverlay.setHidden(false);
                fishingSpotMinimapOverlay.setHidden(false);
            }
        }

        @Subscribe
        public void onInteractingChanged(InteractingChanged event)
        {
            if (event.getSource() != client.getLocalPlayer())
            {
                return;
            }

            final Actor target = event.getTarget();

            if (!(target instanceof NPC))
            {
                return;
            }

            final NPC npc = (NPC) target;
            FishingSpot spot = FishingSpot.findSpot(npc.getId());

            if (spot == null)
            {
                return;
            }

            currentSpot = spot;
        }

        private boolean canPlayerFish(final ItemContainer itemContainer)
        {
            if (itemContainer == null)
            {
                return false;
            }

            for (Item item : itemContainer.getItems())
            {
                if (item == null)
                {
                    continue;
                }
                switch (item.getId())
                {
                    case ItemID.DRAGON_HARPOON:
                    case ItemID.INFERNAL_HARPOON:
                    case ItemID.INFERNAL_HARPOON_UNCHARGED:
                    case ItemID.HARPOON:
                    case ItemID.BARBTAIL_HARPOON:
                    case ItemID.BIG_FISHING_NET:
                    case ItemID.SMALL_FISHING_NET:
                    case ItemID.SMALL_FISHING_NET_6209:
                    case ItemID.FISHING_ROD:
                    case ItemID.FLY_FISHING_ROD:
                    case ItemID.PEARL_BARBARIAN_ROD:
                    case ItemID.PEARL_FISHING_ROD:
                    case ItemID.PEARL_FLY_FISHING_ROD:
                    case ItemID.BARBARIAN_ROD:
                    case ItemID.OILY_FISHING_ROD:
                    case ItemID.LOBSTER_POT:
                    case ItemID.KARAMBWAN_VESSEL:
                    case ItemID.KARAMBWAN_VESSEL_3159:
                    case ItemID.CORMORANTS_GLOVE:
                    case ItemID.CORMORANTS_GLOVE_22817:
                        return true;
                }
            }

            return false;
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

        @Subscribe
        public void onGameTick(GameTick event)
        {
            idleTimeSecs();
            checkMovementIdle();
            for (GameObject object : gameObjects)
            {
                if (object != null)
                {
                    if (BANK_IDS.contains(object.getId())){bankBooth1.add(object);}
                }}

            // Reset fishing session
            if (session.getLastFishCaught() != null)
            {
                final Duration statTimeout = Duration.ofMinutes(config.statTimeout());
                final Duration sinceCaught = Duration.between(session.getLastFishCaught(), Instant.now());

                if (sinceCaught.compareTo(statTimeout) >= 0)
                {
                    currentSpot = null;
                    session.setLastFishCaught(null);
                }
            }

            inverseSortSpotDistanceFromPlayer();

            for (NPC npc : fishingSpots)
            {
                if (FishingSpot.findSpot(npc.getId()) == FishingSpot.MINNOW && config.showMinnowOverlay())
                {
                    final int id = npc.getIndex();
                    final MinnowSpot minnowSpot = minnowSpots.get(id);

                    // create the minnow spot if it doesn't already exist
                    // or if it was moved, reset it
                    if (minnowSpot == null
                            || !minnowSpot.getLoc().equals(npc.getWorldLocation()))
                    {
                        minnowSpots.put(id, new MinnowSpot(npc.getWorldLocation(), Instant.now()));
                    }
                }
            }

            if (config.trawlerTimer())
            {
                updateTrawlerTimer();
            }

            if(client.getLocalPlayer().getAnimation()==-1){
                idle = true;
            }
            else{idle = false;}
        }

        @Subscribe
        public void onNpcSpawned(NpcSpawned event)
        {
            final NPC npc = event.getNpc();

            if (RANDOM_IDS.contains(npc.getId()))
            // distance to npc is less than 3 and remove from list once attempted dismiss
            {
                activeRandom.add(npc);
            }
            if (FishingSpot.findSpot(npc.getId()) == null)
            {
                return;
            }

            fishingSpots.add(npc);
            inverseSortSpotDistanceFromPlayer();
        }

        @Subscribe
        public void onNpcDespawned(NpcDespawned npcDespawned)
        {
            final NPC npc = npcDespawned.getNpc();

            fishingSpots.remove(npc);

            MinnowSpot minnowSpot = minnowSpots.remove(npc.getIndex());
            if (minnowSpot != null)
            {
                log.debug("Minnow spot {} despawned", npc);
            }
        }

        @Subscribe
        public void onVarbitChanged(VarbitChanged event)
        {
            if (!config.trawlerNotification() || client.getGameState() != GameState.LOGGED_IN)
            {
                return;
            }

            int regionID = client.getLocalPlayer().getWorldLocation().getRegionID();

            if ((regionID == TRAWLER_SHIP_REGION_NORMAL || regionID == TRAWLER_SHIP_REGION_SINKING)
                    && client.getVar(Varbits.FISHING_TRAWLER_ACTIVITY) <= TRAWLER_ACTIVITY_THRESHOLD)
            {
                if (!trawlerNotificationSent)
                {
                    notifier.notify("[" + client.getLocalPlayer().getName() + "] has low Fishing Trawler activity!");
                    trawlerNotificationSent = true;
                }
            }
            else
            {
                trawlerNotificationSent = false;
            }
        }

        @Subscribe
        public void onWidgetLoaded(WidgetLoaded event)
        {
            if (event.getGroupId() == WidgetID.FISHING_TRAWLER_GROUP_ID)
            {
                trawlerStartTime = Instant.now();
            }
        }

        /**
         * Changes the Fishing Trawler timer widget from minutes to minutes and seconds
         */
        private void updateTrawlerTimer()
        {
            if (trawlerStartTime == null)
            {
                return;
            }

            int regionID = client.getLocalPlayer().getWorldLocation().getRegionID();
            if (regionID != TRAWLER_SHIP_REGION_NORMAL && regionID != TRAWLER_SHIP_REGION_SINKING)
            {
                log.debug("Trawler session ended");
                return;
            }

            Widget trawlerTimerWidget = client.getWidget(WidgetInfo.FISHING_TRAWLER_TIMER);
            if (trawlerTimerWidget == null)
            {
                return;
            }

            long timeLeft = TRAWLER_TIME_LIMIT_IN_SECONDS - Duration.between(trawlerStartTime, Instant.now()).getSeconds();
            if (timeLeft < 0)
            {
                timeLeft = 0;
            }

            int minutes = (int) timeLeft / 60;
            int seconds = (int) timeLeft % 60;

            final StringBuilder trawlerText = new StringBuilder();
            trawlerText.append("Time Left: ");

            if (minutes > 0)
            {
                trawlerText.append(minutes);
            }
            else
            {
                trawlerText.append("00");
            }

            trawlerText.append(':');

            if (seconds < 10)
            {
                trawlerText.append("0");
            }

            trawlerText.append(seconds);

            trawlerTimerWidget.setText(trawlerText.toString());
        }

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

        private void inverseSortSpotDistanceFromPlayer()
        {
            if (fishingSpots.isEmpty())
            {
                return;
            }

            final LocalPoint cameraPoint = new LocalPoint(client.getCameraX(), client.getCameraY());
            fishingSpots.sort(
                    Comparator.comparing(
                            // Negate to have the furthest first
                            (NPC npc) -> -npc.getLocalLocation().distanceTo(cameraPoint))
                            // Order by position
                            .thenComparing(NPC::getLocalLocation, Comparator.comparing(LocalPoint::getX)
                                    .thenComparing(LocalPoint::getY))
                            // And then by id
                            .thenComparing(NPC::getId)
            );
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

        private Random random = new Random();

        private int random1(int n){
            return random.nextInt(n);
        }

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

        private int i =0;
        private Thread backgroundRun = new Thread(new Runnable() { public void run() {
            for (i =0;i<500;i++){
                if (config.addonOn() && client.getGameState() == GameState.LOGGED_IN) {
                    try { addon();} catch (AWTException | InterruptedException e) { e.printStackTrace();	}

                    System.out.println(i+" | "  + checkMovementIdle()
                                    // + " 1 "+ client.getWidget(WidgetInfo.DIALOG_NPC)
                                    //   + " 2 "+ client.getWidget(WidgetInfo.DIALOG_NPC_NAME)
                                    //    + " 3 " + client.getWidget(WidgetInfo.DIALOG_PLAYER)
                                    //   + " 4 " + client.getWidget(WidgetInfo.DIALOG_SPRITE_TEXT)
                                    + " 5 " + client.getLocalPlayer().getInteracting().getOverheadText()
                            // + " 6 " +	getBankItemIndex(ItemID.SMALL_FISHING_NET)
                    ) ;
                    if(activeRandom.size()!=0){
                        System.out.println(i+" | "  +activeRandom.get(0).getOverheadText());}
                }


                try {  Thread.sleep(random2(3000,10000));  } catch (InterruptedException e) { e.printStackTrace(); }
            }}});

        private void addon() throws AWTException, InterruptedException {
            final List<NPC> localSpots = new ArrayList<>();
            final List<NPC> spotTemp = new ArrayList<>(fishingSpots);

            if( i == 50 || i == 100 || i == 150 || i == 200 || i == 250 || i == 300 )
            {try {  Thread.sleep(random2(60000,120000));  } catch (InterruptedException e) { e.printStackTrace(); }}

            while(!checkMovementIdle()){Thread.sleep(1000);}

            if(availableInventory()>0 && netInInventory()){
                WorldPoint ShrimpFishArea = new WorldPoint(3087,3228,0);

                if(Math.abs(client.getLocalPlayer().getWorldLocation().getX()-ShrimpFishArea.getX())>3 ||
                        Math.abs(client.getLocalPlayer().getWorldLocation().getY()-ShrimpFishArea.getY())>3
                ){
                    walk(ShrimpFishArea);
                    return;
                }

                for (NPC npc : spotTemp) {
                    FishingSpot spot = FishingSpot.findSpot(npc.getId());
                    if (spot == FishingSpot.SHRIMP && npc.getWorldLocation().distanceTo2D(client.getLocalPlayer().getWorldLocation()) <= 7 && idleTimeSecs()>3)
                    { localSpots.add(npc); }}

                if (localSpots.size() != 0 && idleTimeSecs()>3 ){
                    Point spotPerspective = worldToCanvas(localSpots.get(0).getWorldLocation());
                    Robot robot = new Robot();
                    moveMouse(robot,client.getMouseCanvasPosition(),spotPerspective,10,5,5);
                    leftClick(robot);
                    return;}
                if (idleTimeSecs()<=3 ){return;}
            }
            // WorldPoint(x=3091, y=3243, plane=0) , 10355
            //ObjectID.BANK_BOOTH_10355, WorldPoint(x=3091, y=3245, plane=0)
//sprite Id bank inventory

            if (client.getLocalPlayer().getWorldLocation().getX()<3098&&client.getLocalPlayer().getWorldLocation().getX()>3091
                    &&client.getLocalPlayer().getWorldLocation().getY()<3247&&client.getLocalPlayer().getWorldLocation().getY()>3239&&availableInventory()==0)
            {bankInvent();}
            else if (client.getLocalPlayer().getWorldLocation().getX()<3098&&client.getLocalPlayer().getWorldLocation().getX()>3091
                    &&client.getLocalPlayer().getWorldLocation().getY()<3247&&client.getLocalPlayer().getWorldLocation().getY()>3239&&!netInInventory())
            {bankInvent();}
            else{bankWalk();}
        }

        private boolean netInInventory() {

            for (Item item : client.getItemContainer(InventoryID.INVENTORY).getItems())
            {
                if (item == null)
                {
                    continue;
                }

                if (item.getId() == ItemID.SMALL_FISHING_NET) {
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
            int rowFactor = 36;
            int row = 1;
            if (n>8){
                while (n>8){n =n-8; row = row + 1;}}
            int column = n;
            int x = bankBaseX + relateBaseX + (column-1)*columnFactor;
            int y = bankBaseY + relateBaseY + (row-1)*rowFactor;
            int xTolerance = x+random2(-16,16);
            int yTolerance = y+random2(-15,15);
            Point itemBankLocation = new Point (x,y);
            return itemBankLocation;
        }

        private int availableInventory() {
            int availableInventory = 0;

            if (client.getItemContainer(InventoryID.INVENTORY).getItems().length!=28){availableInventory=1;
                return availableInventory;}

            for (Item item : client.getItemContainer(InventoryID.INVENTORY).getItems()) {
                //System.out.print(" || items id " + item.getId());
                if (item.getId() == -1){	availableInventory=availableInventory+1;	}}
            return availableInventory; 	}

        private void bankWalk() throws AWTException {

            WorldPoint bankLocation = new WorldPoint(random2(3092,3097),random2(3240,3246),0);
            System.out.print("random bank " + bankLocation);
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
                Point selectBankPerspective = new Point(bankBoothPerspective.getX()+random2(-45,45),bankBoothPerspective.getY()+random2(24,29));
                moveMouse(robot,bankBoothPerspective,selectBankPerspective,11,4,4);
                leftClick(robot);
                try {  Thread.sleep(random2(1100,2400));  } catch (InterruptedException e) { e.printStackTrace(); }

                //BANK_DEPOSIT_INVENTORY = 1041; deposit:775-782,774-797
                Point depositInventory = new Point(random2(890,909),random2(800,810));
                if(depositInventoryPoint()==null){return;}
                Point depositInventoryPoint1 = depositInventoryPoint();
                try {  Thread.sleep(random2(400,500));  } catch (InterruptedException e) { e.printStackTrace(); }
                moveMouse(robot,selectBankPerspective,depositInventoryPoint1,11,4,4);
                leftClick(robot);
                try {  Thread.sleep(random2(500,800));  } catch (InterruptedException e) { e.printStackTrace(); }
                //net 555-576,167-188
                Point netBanklocation = BankLocation(getBankItemIndex(ItemID.SMALL_FISHING_NET));
                //System.out.println("Item net : " + " , "  );
                //Point withdrawNet = new Point(random2(676,699),random2(182,198));
                moveMouse1(robot,depositInventoryPoint1,netBanklocation,11,4,4);
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
                    Point adjustedMinimapPerspective = new Point(minimapPerspective.getX(), minimapPerspective.getY() + 24);
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
            //WorldPoint ShrimpFishArea = new WorldPoint(3087,3228,0);

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
                NpcID.MYSTERIOUS_OLD_MAN_6750, NpcID.MYSTERIOUS_OLD_MAN_6751, NpcID.MYSTERIOUS_OLD_MAN_6752, NpcID.MYSTERIOUS_OLD_MAN_6753,
                NpcID.PILLORY_GUARD, NpcID.QUIZ_MASTER, NpcID.RICK_TURPENTINE,NpcID.SANDWICH_LADY,NpcID.SECURITY_GUARD,NpcID.STRANGE_PLANT,NpcID.DUNCE);

        @Getter(AccessLevel.PACKAGE)
        private List<NPC> activeRandom = new ArrayList<>();

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


}
