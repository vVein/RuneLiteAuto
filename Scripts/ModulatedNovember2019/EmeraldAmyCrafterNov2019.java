package Polished.Scripts.ModulatedNovember2019;

public class EmeraldAmyCrafterNov2019 {

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
import java.awt.event.KeyEvent;
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
import net.runelite.client.Notifier;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.OverlayMenuClicked;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDependency;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.xptracker.XpTrackerPlugin;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.OverlayMenuEntry;

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
        public void onOverlayMenuClicked(OverlayMenuClicked overlayMenuClicked)
        {
            OverlayMenuEntry overlayMenuEntry = overlayMenuClicked.getEntry();
            if (overlayMenuEntry.getMenuAction() == MenuAction.RUNELITE_OVERLAY
                    && overlayMenuClicked.getEntry().getOption().equals(FishingOverlay.FISHING_RESET)
                    && overlayMenuClicked.getOverlay() == overlay)
            {
                session.setLastFishCaught(null);
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

        @Subscribe
        public void onGameTick(GameTick event)
        {

            idleTimeSecs();
            idleMovementTimeSecs();
            if (client.getLocalPlayer().getAnimation() == -1) { idle = true; } else { idle = false; }

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
        }

        @Subscribe
        public void onNpcSpawned(NpcSpawned event)
        {
            final NPC npc = event.getNpc();

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
                trawlerStartTime = null;
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

        private WorldPoint lastPosition;

        private boolean checkMovementIdle() {
            if (lastPosition == null) {
                lastPosition = Objects.requireNonNull(client.getLocalPlayer()).getWorldLocation();
                return false;
            }

            WorldPoint currentPosition = Objects.requireNonNull(client.getLocalPlayer()).getWorldLocation();

            if (lastPosition.getX() == currentPosition.getX() && lastPosition.getY() == currentPosition.getY()) {
                {
                    return true;
                }
            }
            lastPosition = currentPosition;
            return false;
        }

        private long startTimeMovement = System.currentTimeMillis();

        private long idleMovementTimeSecs() {
            long elapsedM;
            if (checkMovementIdle()) {
                elapsedM = ((System.currentTimeMillis() - startTimeMovement) / 1000);
            } else {
                elapsedM = 0;
                startTimeMovement = System.currentTimeMillis();
            }
            return elapsedM;
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

        private int random1(int n) {
            return random.nextInt(n);
        }

        private int random2(int lowerLimit, int upperLimit) {
            int rand2;
            if (lowerLimit == upperLimit) {
                return rand2 = 0;
            } else {
                rand2 = random.nextInt(upperLimit + 1 - lowerLimit) + lowerLimit;
                return rand2;
            }}

        private double random2Dbl(double lowerLimit, double upperLimit) {
            return (lowerLimit + (upperLimit - lowerLimit) * random.nextDouble());
        }

        private void moveMouse(Robot robot, net.runelite.api.Point CanvasPosition, net.runelite.api.Point MouseTarget, int speed, int xRandom, int yRandom) {
            double XDelta = Math.abs(CanvasPosition.getX() - MouseTarget.getX());
            double YDelta = Math.abs(CanvasPosition.getY() - MouseTarget.getY());
            int XDeviationRange = (int) XDelta / (random2(8, 15));
            int YDeviationRange = (int) YDelta / (random2(8, 15));
            net.runelite.api.Point partway = new net.runelite.api.Point(MouseTarget.getX() + random2(-XDeviationRange, XDeviationRange), MouseTarget.getY() + random2(-YDeviationRange, YDeviationRange));
            moveMouse1(robot, CanvasPosition, partway, speed, xRandom, yRandom);
            moveMouse1(robot, partway, MouseTarget, speed / 2, xRandom / 2, yRandom / 2);
        }

        private void moveMouse(Robot robot, net.runelite.api.Point CanvasPosition, NPC npc, int speed, int xRandom, int yRandom) {
            double XDelta = Math.abs(CanvasPosition.getX() - npc.getCanvasTilePoly().getBounds().getCenterX());
            double YDelta = Math.abs(CanvasPosition.getY() - npc.getCanvasTilePoly().getBounds().getCenterY());
            int XDeviationRange = (int) XDelta / (random2(8, 15));
            int YDeviationRange = (int) YDelta / (random2(8, 15));
            net.runelite.api.Point partway = new net.runelite.api.Point(((int) npc.getCanvasTilePoly().getBounds().getCenterX()) + random2(-XDeviationRange, XDeviationRange),
                    ((int) npc.getCanvasTilePoly().getBounds().getCenterY()) + random2(-YDeviationRange, YDeviationRange));
            moveMouse1(robot, CanvasPosition, partway, speed, xRandom, yRandom);
            moveMouse1(robot, partway, npc, speed / 2, xRandom / 2, yRandom / 2);
        }

        private void moveMouse1(Robot robot, net.runelite.api.Point CanvasPosition, net.runelite.api.Point MouseTarget, int speed, int xRandom, int yRandom) {
            if (Math.abs(CanvasPosition.getX() - MouseTarget.getX()) <= xRandom && Math.abs(CanvasPosition.getY() - MouseTarget.getY()) <= yRandom)
                return;

            net.runelite.api.Point[] cooardList;
            double t;    //the time interval
            double k = .025;
            cooardList = new net.runelite.api.Point[4];

            //set the beginning and end points
            cooardList[0] = CanvasPosition;
            cooardList[3] = new net.runelite.api.Point(MouseTarget.getX() + random2(-xRandom, xRandom), MouseTarget.getY() + (random2(-yRandom, yRandom)));

            int xout = (int) (Math.abs(MouseTarget.getX() - CanvasPosition.getX()) / 10);
            int yout = (int) (Math.abs(MouseTarget.getY() - CanvasPosition.getY()) / 10);

            int x = 0, y = 0;

            x = CanvasPosition.getX() < MouseTarget.getX()
                    ? CanvasPosition.getX() + ((xout > 0) ? random2(1, xout) : 1)
                    : CanvasPosition.getX() - ((xout > 0) ? random2(1, xout) : 1);
            y = CanvasPosition.getY() < MouseTarget.getY()
                    ? CanvasPosition.getY() + ((yout > 0) ? random2(1, yout) : 1)
                    : CanvasPosition.getY() - ((yout > 0) ? random2(1, yout) : 1);
            cooardList[1] = new net.runelite.api.Point(x, y);

            x = MouseTarget.getX() < CanvasPosition.getX()
                    ? MouseTarget.getX() + ((xout > 0) ? random2(1, xout) : 1)
                    : MouseTarget.getX() - ((xout > 0) ? random2(1, xout) : 1);
            y = MouseTarget.getY() < CanvasPosition.getY()
                    ? MouseTarget.getY() + ((yout > 0) ? random2(1, yout) : 1)
                    : MouseTarget.getY() - ((yout > 0) ? random2(1, yout) : 1);
            cooardList[2] = new net.runelite.api.Point(x, y);

            double px = 0, py = 0;
            for (t = k; t <= 1 + k; t += k) {
                //use Berstein polynomials
                px = (cooardList[0].getX() + t * (-cooardList[0].getX() * 3 + t * (3 * cooardList[0].getX() -
                        cooardList[0].getX() * t))) + t * (3 * cooardList[1].getX() + t * (-6 * cooardList[1].getX() +
                        cooardList[1].getX() * 3 * t)) + t * t * (cooardList[2].getX() * 3 - cooardList[2].getX() * 3 * t) +
                        cooardList[3].getX() * t * t * t;
                py = (cooardList[0].getY() + t * (-cooardList[0].getY() * 3 + t * (3 * cooardList[0].getY() -
                        cooardList[0].getY() * t))) + t * (3 * cooardList[1].getY() + t * (-6 * cooardList[1].getY() +
                        cooardList[1].getY() * 3 * t)) + t * t * (cooardList[2].getY() * 3 - cooardList[2].getY() * 3 * t) +
                        cooardList[3].getY() * t * t * t;
                robot.mouseMove((int) px, (int) py);
                robot.delay(random2(speed, speed * 2));
                //System.out.println("mouse control : " + px + " " + py + " mouse target " + MouseTarget);
            }
        }

        private void moveMouse1(Robot robot, net.runelite.api.Point CanvasPosition, NPC npc, int speed, int xRandom, int yRandom) {

            int XRandom = random2(-4, 4);
            int YRandom = random2(-4, 4);

            if (Math.abs(CanvasPosition.getX() - (npc.getCanvasTilePoly().getBounds().getCenterX() + XRandom)) <= xRandom
                    && Math.abs(CanvasPosition.getY() - (npc.getCanvasTilePoly().getBounds().getCenterY() + YRandom)) <= yRandom)
                return;

            net.runelite.api.Point[] cooardList;
            double t;    //the time interval
            double k = .025;
            cooardList = new net.runelite.api.Point[4];

            //set the beginning and end points
            cooardList[0] = CanvasPosition;
            cooardList[3] = new net.runelite.api.Point(((int) npc.getCanvasTilePoly().getBounds().getCenterX() + XRandom) + random2(-xRandom, xRandom),
                    ((int) npc.getCanvasTilePoly().getBounds().getCenterY() + YRandom) + (random2(-yRandom, yRandom)));

            int xout = (int) (Math.abs(((int) npc.getCanvasTilePoly().getBounds().getCenterX() + XRandom) - CanvasPosition.getX()) / 10);
            int yout = (int) (Math.abs(((int) npc.getCanvasTilePoly().getBounds().getCenterY() + YRandom) - CanvasPosition.getY()) / 10);

            int x = 0, y = 0;

            x = CanvasPosition.getX() < ((int) npc.getCanvasTilePoly().getBounds().getCenterX() + XRandom)
                    ? CanvasPosition.getX() + ((xout > 0) ? random2(1, xout) : 1)
                    : CanvasPosition.getX() - ((xout > 0) ? random2(1, xout) : 1);
            y = CanvasPosition.getY() < ((int) npc.getCanvasTilePoly().getBounds().getCenterY() + YRandom)
                    ? CanvasPosition.getY() + ((yout > 0) ? random2(1, yout) : 1)
                    : CanvasPosition.getY() - ((yout > 0) ? random2(1, yout) : 1);
            cooardList[1] = new net.runelite.api.Point(x, y);

            x = ((int) npc.getCanvasTilePoly().getBounds().getCenterX() + XRandom) < CanvasPosition.getX()
                    ? ((int) npc.getCanvasTilePoly().getBounds().getCenterX() + XRandom) + ((xout > 0) ? random2(1, xout) : 1)
                    : ((int) npc.getCanvasTilePoly().getBounds().getCenterX() + XRandom) - ((xout > 0) ? random2(1, xout) : 1);
            y = ((int) npc.getCanvasTilePoly().getBounds().getCenterY() + YRandom) < CanvasPosition.getY()
                    ? ((int) npc.getCanvasTilePoly().getBounds().getCenterY() + YRandom) + ((yout > 0) ? random2(1, yout) : 1)
                    : ((int) npc.getCanvasTilePoly().getBounds().getCenterY() + YRandom) - ((yout > 0) ? random2(1, yout) : 1);
            cooardList[2] = new net.runelite.api.Point(x, y);

            double px = 0, py = 0;
            for (t = k; t <= 1 + k; t += k) {
                //use Berstein polynomials
                px = (cooardList[0].getX() + t * (-cooardList[0].getX() * 3 + t * (3 * cooardList[0].getX() -
                        cooardList[0].getX() * t))) + t * (3 * cooardList[1].getX() + t * (-6 * cooardList[1].getX() +
                        cooardList[1].getX() * 3 * t)) + t * t * (cooardList[2].getX() * 3 - cooardList[2].getX() * 3 * t) +
                        cooardList[3].getX() * t * t * t;
                py = (cooardList[0].getY() + t * (-cooardList[0].getY() * 3 + t * (3 * cooardList[0].getY() -
                        cooardList[0].getY() * t))) + t * (3 * cooardList[1].getY() + t * (-6 * cooardList[1].getY() +
                        cooardList[1].getY() * 3 * t)) + t * t * (cooardList[2].getY() * 3 - cooardList[2].getY() * 3 * t) +
                        cooardList[3].getY() * t * t * t;
                robot.mouseMove((int) px, (int) py);
                robot.delay(random2(speed, speed * 2));
                //System.out.println("mouse control : " + px + " " + py + " mouse target " + MouseTarget);
            }
        }

        private void leftClick(Robot robot) {
            robot.delay(random2(12, 26));
            robot.mousePress(InputEvent.BUTTON1_MASK);
            robot.delay(random2(204, 348));
            robot.mouseRelease(InputEvent.BUTTON1_MASK);
            robot.delay(random2(183, 299));
        }

        private void rightClick(Robot robot) {
            robot.delay(random2(12, 26));
            robot.mousePress(InputEvent.BUTTON3_MASK);
            robot.delay(random2(204, 348));
            robot.mouseRelease(InputEvent.BUTTON3_MASK);
            robot.delay(random2(183, 299));
        }

        private boolean PointInsideMiniMap(net.runelite.api.Point point) {
            if (point != null) {
                double MinimapX = client.getWidget(WidgetInfo.RESIZABLE_MINIMAP_DRAW_AREA).getBounds().getCenterX();
                double MinimapY = client.getWidget(WidgetInfo.RESIZABLE_MINIMAP_DRAW_AREA).getBounds().getCenterY();
                double MinimapRadius = 71;
                // 75
                double deltaX = Math.abs(MinimapX - point.getX());
                double deltaY = Math.abs(MinimapY - point.getY());
                double deltaXSQ = Math.pow(deltaX, 2);
                double deltaYSQ = Math.pow(deltaY, 2);
                double Hypotenuse = Math.sqrt(deltaXSQ + deltaYSQ);

                double WorldMinimapX = client.getWidget(WidgetInfo.MINIMAP_WORLDMAP_ORB).getBounds().getCenterX();
                double WorldMinimapY = client.getWidget(WidgetInfo.MINIMAP_WORLDMAP_ORB).getBounds().getCenterY();
                double WorldMinimapRadius = 15;
                double deltaX1 = Math.abs(WorldMinimapX - point.getX());
                double deltaY1 = Math.abs(WorldMinimapY - point.getY());
                double deltaXSQ1 = Math.pow(deltaX1, 2);
                double deltaYSQ1 = Math.pow(deltaY1, 2);
                double Hypotenuse1 = Math.sqrt(deltaXSQ1 + deltaYSQ1);

                if (Hypotenuse < MinimapRadius && Hypotenuse1 > WorldMinimapRadius) {
                    return true;
                }
            }
            return false;
        }

        private void RandomMouseMove() throws AWTException {
            Robot robot = new Robot();
            net.runelite.api.Point randomCanvas = new net.runelite.api.Point(random2(50, 1600), random2(200, 800));
            moveMouse1(robot, client.getMouseCanvasPosition(), randomCanvas, 9, 15, 15);
        }

        private net.runelite.api.Point BankLocation(int index) {
            // row = 1, n = index, column = n , while n>8 , column = n-8 row = row +1
            // row 1 y = 115  // row 2 y = 153  // column 1 x = 420 // column 2 x = 469  // 519  ,  189
            //canvas bank 355,88 	// column spacing of 50, tolerance 23 	// row spacing 37 , tolerance 22
            int bankBaseX = client.getWidget(WidgetInfo.BANK_ITEM_CONTAINER).getCanvasLocation().getX();
            int bankBaseY = client.getWidget(WidgetInfo.BANK_ITEM_CONTAINER).getCanvasLocation().getY();
            int n = index;
            int relateBaseX = 75;
            int relateBaseY = 37;
            //was 32
            int columnFactor = 47;
            int rowFactor = 36;
            int row = 1;
            if (n > 8) {
                while (n > 8) {
                    n = n - 8;
                    row = row + 1;
                }
            }
            int column = n;
            int x = bankBaseX + relateBaseX + (column - 1) * columnFactor;
            int y = bankBaseY + relateBaseY + (row - 1) * rowFactor;
            int xTolerance = x + random2(-7, 7);
            int yTolerance = y + random2(-6, 6);
            net.runelite.api.Point itemBankLocation = new net.runelite.api.Point(xTolerance, yTolerance);
            return itemBankLocation;
        }

        private int availableInventory() {
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
                //System.out.print(" || items id " + item.getId());
                if (item.getId() == -1) {
                    availableInventory = availableInventory + 1;
                }
            }
            return availableInventory;
        }

        private int getBankItemIndex(int itemID) {
            int index = 1;
            if (client.getItemContainer(InventoryID.BANK) != null) {
                if (client.getItemContainer(InventoryID.BANK).getItems() != null) {
                    Item[] bankItems = client.getItemContainer(InventoryID.BANK).getItems();
                    //System.out.println(Arrays.toString(bankItems));
                    for (Item item : bankItems) {
                        if (item.getId() == itemID) {
                            return index;
                        } else {
                            index = index + 1;
                        }
                    }
                }
            }
            return index = 0;
        }

        private java.util.List<NPC> GetNPC(Set<Integer> ids) {

            java.util.List<NPC> localNPCs = new ArrayList<>();
            java.util.List<NPC> NPCList;

            if (client.getNpcs() == null) {
                return null;
            }

            NPCList = client.getNpcs();

            for (NPC npc : NPCList) {
                if (npc.getWorldLocation().distanceTo2D(client.getLocalPlayer().getWorldLocation()) < 6 )
                    System.out.print(" npcs " + npc.getId() + npc.getName() + " " + npc.getWorldLocation());
                {
                    if (ids.contains(npc.getId())) {
                        localNPCs.add(npc);
                    }
                }
            }

            return localNPCs;
        }

        private boolean ItemInInventory(Set<Integer> testList) {

            if (client.getItemContainer(InventoryID.INVENTORY) == null) {
                return false;
            }

            Item[] inventory = client.getItemContainer(InventoryID.INVENTORY).getItems();
            for (Item item : inventory) {
                //System.out.print(" || items id " + item.getId());
                int itemId = item.getId();
                if (testList.contains(itemId)) {
                    return true;
                }
            }
            return false;
        }

        private int InvIndex(int ItemID) {
            Item[] inventory = client.getItemContainer(InventoryID.INVENTORY).getItems();
            int foodIndex = 1;
            for (Item item : inventory) {
                int itemId = item.getId();
                if (itemId == ItemID) {
                    return foodIndex;
                }
                foodIndex = foodIndex + 1;
            }
            return 0;
        }

        private net.runelite.api.Point depositInventoryPoint() {
            if (client.getWidget(12, 42) == null) {
                return null;
            }
            Widget deposit_Inventory_Widget = client.getWidget(12, 42);
            int deposit_x = (int) Math.round(random2Dbl(deposit_Inventory_Widget.getBounds().getMinX() + 8, deposit_Inventory_Widget.getBounds().getMaxX()) - 2);
            int deposit_y = (int) Math.round(random2Dbl(deposit_Inventory_Widget.getBounds().getMinY() + 23, deposit_Inventory_Widget.getBounds().getMaxY()) + 17);
            return new net.runelite.api.Point(deposit_x, deposit_y);
        }

        @Getter(AccessLevel.PACKAGE)
        private java.util.List<NPC> activeRandom = new ArrayList<>();

        private net.runelite.api.Point worldToCanvas(WorldPoint worldpoint) {
            if (LocalPoint.fromWorld(client, worldpoint.getX(), worldpoint.getY()) != null) {
                LocalPoint targetLL = LocalPoint.fromWorld(client, worldpoint.getX(), worldpoint.getY());
                if (Perspective.localToCanvas(client, targetLL, client.getPlane()) != null) {
                    net.runelite.api.Point perspective = Perspective.localToCanvas(client, targetLL, client.getPlane());
                    net.runelite.api.Point adjustedPerspective = new net.runelite.api.Point(perspective.getX() + random2(-3, 3),
                            perspective.getY() + random2(-3, 3));
                    return adjustedPerspective;
                }
            }
            return null;
        }

        private net.runelite.api.Point worldToMiniMap(WorldPoint worldpoint) {
            LocalPoint targetLL = LocalPoint.fromWorld(client, worldpoint.getX(), worldpoint.getY());
            if (targetLL != null) {
                net.runelite.api.Point minimapPerspective = Perspective.localToMinimap(client, targetLL);
                if (minimapPerspective != null) {
                    net.runelite.api.Point adjustedMinimapPerspective = new net.runelite.api.Point(minimapPerspective.getX() + 3 + random2(-1, 1),
                            minimapPerspective.getY() + 22 + random2(-1, 1));
                    return adjustedMinimapPerspective;
                }
            }
            return null;
        }

        private boolean PointInsideClickableWindow(net.runelite.api.Point point) {

            Rectangle ClickableWindow1 = new Rectangle(3, 3, (1420 - 3), (815 - 3));
            Rectangle RightSideClickableWindow = new Rectangle(1418, 180, (1630 - 1418), (664 - 180));
            Rectangle BottomClickableWindow = new Rectangle(530, 813, (1420 - 530), (938 - 813));

            if (ClickableWindow1.contains(point.getX(), point.getY())
                    || RightSideClickableWindow.contains(point.getX(), point.getY())
                    || BottomClickableWindow.contains(point.getX(), point.getY())
            ) {
                return true;
            }
            return false;
        }

        private boolean FirstLeftClickOption(String testText) {

            MenuEntry[] options = client.getMenuEntries();
            MenuEntry firstOption = options[options.length - 1];
            //option=Walk here
            if (firstOption != null) {
                if (firstOption.getOption() != null) {
                    if (firstOption.getOption().equals(testText)) {
                        return true;
                    }
                }
            }
            return false;
        }

        private boolean FirstLeftClickOption() {

            MenuEntry[] options = client.getMenuEntries();
            int first_Option_Index = options.length - 1;
            MenuEntry firstOption = options[first_Option_Index];
            MenuAction DesiredAction = MenuAction.WALK;
            if (firstOption != null) {
                if (firstOption.getType() == 23) {
                    return true;
                }
            }
            return false;
        }

        private int NumberofItemInInventory(int ItemID) {
            int availableItem = 0;
            if (client.getItemContainer(InventoryID.INVENTORY) == null) {
                return availableItem;
            }

            Item[] inventory = client.getItemContainer(InventoryID.INVENTORY).getItems();
            for (Item item : inventory) {
                //System.out.print(" || items id " + item.getId());
                int itemId = item.getId();
                if (itemId == ItemID) {
                    availableItem = availableItem + 1;
                }
            }
            return availableItem;
        }

        private int NumberofStackedItemInInventory(int ItemID) {
            int availableItem = 0;
            if (client.getItemContainer(InventoryID.INVENTORY) == null) {
                return availableItem;
            }

            Item[] inventory = client.getItemContainer(InventoryID.INVENTORY).getItems();
            for (Item item : inventory) {
                //System.out.print(" || items id " + item.getId());
                int itemId = item.getId();
                if (itemId == ItemID) {
                    availableItem = item.getQuantity();
                }
            }
            return availableItem;
        }

        private void walk(WorldPoint finalLocation) throws AWTException {
            Robot robot = new Robot();
            WorldPoint temporaryTarget;
            net.runelite.api.Point temporaryTargetPerspective;

            if (worldToCanvas(finalLocation) != null) {
                temporaryTargetPerspective = worldToCanvas(finalLocation);

                if (temporaryTargetPerspective != null) {
                    if (PointInsideClickableWindow(temporaryTargetPerspective)) {
                        net.runelite.api.Point MouseCanvasPosition = new net.runelite.api.Point(client.getMouseCanvasPosition().getX(), client.getMouseCanvasPosition().getY() + 20);
                        moveMouse(robot, MouseCanvasPosition, temporaryTargetPerspective, 10, 4, 4);
                        if (FirstLeftClickOption()) {
                            leftClick(robot);
                            return;
                        }

                        rightClick(robot);
                        try {
                            Thread.sleep(random2(500, 800));
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }

                        net.runelite.api.Point rightClickMenu = MenuIndexPosition(MenuIndex("Walk here"), temporaryTargetPerspective);
                        MouseCanvasPosition = new net.runelite.api.Point(client.getMouseCanvasPosition().getX(), client.getMouseCanvasPosition().getY() + 20);
                        moveMouse(robot, MouseCanvasPosition, rightClickMenu, 11, 4, 4);
                        leftClick(robot);
                        return;
                    }
                }
            }
            System.out.print(" | Walk Check 1 | ");
            temporaryTargetPerspective = worldToMiniMap(finalLocation);
            if (temporaryTargetPerspective != null) {
                if (PointInsideMiniMap(temporaryTargetPerspective)) {
                    System.out.print(" | Walk Check 2 | ");
                    net.runelite.api.Point MouseCanvasPosition = new net.runelite.api.Point(client.getMouseCanvasPosition().getX(), client.getMouseCanvasPosition().getY() + 20);
                    moveMouse1(robot, MouseCanvasPosition, temporaryTargetPerspective, 11, 5, 5);
                    leftClick(robot);
                    return;
                }
            }

            int startX = client.getLocalPlayer().getWorldLocation().getX();
            int endX = finalLocation.getX();
            double partwayXD = startX + (endX - startX) * 0.7;
            int partwayX = (int) partwayXD;
            int startY = client.getLocalPlayer().getWorldLocation().getY();
            int endY = finalLocation.getY();
            double partwayYD = startY + (endY - startY) * 0.7;
            int partwayY = (int) partwayYD;
            temporaryTarget = new WorldPoint(partwayX, partwayY, client.getLocalPlayer().getWorldLocation().getPlane());
            temporaryTargetPerspective = worldToMiniMap(temporaryTarget);
            System.out.print(" | Walk Check 3 | ");

            while (!PointInsideMiniMap(temporaryTargetPerspective)) {

                endX = temporaryTarget.getX();
                partwayXD = startX + (endX - startX) * 0.7;
                partwayX = (int) partwayXD;
                endY = temporaryTarget.getY();
                partwayYD = startY + (endY - startY) * 0.7;
                partwayY = (int) partwayYD;
                temporaryTarget = new WorldPoint(partwayX, partwayY, client.getLocalPlayer().getWorldLocation().getPlane());
                temporaryTargetPerspective = worldToMiniMap(temporaryTarget);
                //System.out.println("temporary target iter'" + temporaryTarget);
            }
            //System.out.println("temporary target iter used" + temporaryTarget);
            net.runelite.api.Point MouseCanvasPosition = new net.runelite.api.Point(client.getMouseCanvasPosition().getX(), client.getMouseCanvasPosition().getY() + 20);
            moveMouse1(robot, MouseCanvasPosition, temporaryTargetPerspective, 11, 4, 4);
            leftClick(robot);
        }

        private final Set<Integer> RANDOM_IDS = ImmutableSet.of(NpcID.BEE_KEEPER,
                NpcID.NILES, NpcID.SERGEANT_DAMIEN,
                NpcID.FREAKY_FORESTER, NpcID.FROG, NpcID.FROG_5429, NpcID.FROG_479,
                NpcID.FROG_3290, NpcID.FROG_5430, NpcID.FROG_5431, NpcID.FROG_5432, NpcID.FROG_5833, NpcID.FROG_8702,
                NpcID.GENIE_4738,
                NpcID.POSTIE_PETE, NpcID.LEO,
                NpcID.MYSTERIOUS_OLD_MAN, NpcID.MYSTERIOUS_OLD_MAN_6742,
                NpcID.FLIPPA,
                NpcID.QUIZ_MASTER,
                NpcID.SECURITY_GUARD, NpcID.STRANGE_PLANT, NpcID.DUNCE,
                NpcID.DR_JEKYLL, NpcID.DR_JEKYLL_314,
                NpcID.BEE_KEEPER_6747,
                NpcID.CAPT_ARNAV,
                NpcID.SERGEANT_DAMIEN_6743,
                NpcID.DRUNKEN_DWARF,
                NpcID.FREAKY_FORESTER_6748,
                NpcID.GENIE, NpcID.GENIE_327,
                NpcID.EVIL_BOB, NpcID.EVIL_BOB_6754,
                NpcID.POSTIE_PETE_6738,
                NpcID.LEO_6746,
                NpcID.MYSTERIOUS_OLD_MAN_6750, NpcID.MYSTERIOUS_OLD_MAN_6751,
                NpcID.MYSTERIOUS_OLD_MAN_6752, NpcID.MYSTERIOUS_OLD_MAN_6753,
                NpcID.PILLORY_GUARD,
                NpcID.FLIPPA_6744,
                NpcID.QUIZ_MASTER_6755,
                NpcID.RICK_TURPENTINE, NpcID.RICK_TURPENTINE_376,
                NpcID.SANDWICH_LADY,
                NpcID.DUNCE_6749,
                NpcID.NILES, NpcID.NILES_5439,
                NpcID.MILES, NpcID.MILES_5440,
                NpcID.GILES, NpcID.GILES_5441
        );

        private NPC randomNPCCheck() {
            java.util.List<NPC> activeRandom = new ArrayList<>();
            java.util.List<NPC> NPCList;
            if (client.getNpcs() == null) {
                return null;
            }
            NPCList = client.getNpcs();

            for (NPC npc : NPCList) {
                if (npc.getWorldLocation().distanceTo2D(client.getLocalPlayer().getWorldLocation()) < 4) {
                    System.out.print(" NPCList " + npc.getId() + " , " + npc.getIndex() + " , " + npc.getName());
                }
            }

            for (NPC npc : NPCList) {
                if (RANDOM_IDS.contains(npc.getId())) {
                    activeRandom.add(npc);
                }
            }
            if (activeRandom.size() != 0) {
                for (NPC random : activeRandom) {
                    if (random.getInteracting() != null) {
                        if (random.getInteracting().getName() != null) {
                            if (random.getInteracting().getName().contains("VEIN")) {
                                return random;
                            }
                        }
                    }
                    if (random.getOverheadText() != null) {
                        if (random.getOverheadText().contains("VEIN")) {
                            return random;
                        }
                    }
                }
            }
            return null;
        }

        private net.runelite.api.Point InvLocation(int index) {

            int invBaseX = client.getWidget(WidgetInfo.INVENTORY).getCanvasLocation().getX();
            int invBaseY = client.getWidget(WidgetInfo.INVENTORY).getCanvasLocation().getY();
            int n = index;
            int relateBaseX = 15;
            int relateBaseY = 41;
            int columnFactor = 42;
            int rowFactor = 36;
            int row = 1;
            if (n > 4) {
                while (n > 4) {
                    n = n - 4;
                    row = row + 1;
                }
            }
            int column = n;
            int x = invBaseX + relateBaseX + (column - 1) * columnFactor;
            int y = invBaseY + relateBaseY + (row - 1) * rowFactor;
            int xTolerance = x + random2(-9, 9);
            int yTolerance = y + random2(-9, 9);
            net.runelite.api.Point itemInvLocation = new net.runelite.api.Point(xTolerance, yTolerance);
            return itemInvLocation;
        }

        private void dismissRandom() throws AWTException {
            NPC targetRandom = randomNPCCheck();
            WorldPoint randomWL = targetRandom.getWorldLocation();
            net.runelite.api.Point randomCanvas = worldToCanvas(randomWL);
            if (PointInsideClickableWindow(randomCanvas)) {
                Robot robot = new Robot();
                net.runelite.api.Point MouseCanvasPosition = new net.runelite.api.Point(client.getMouseCanvasPosition().getX(), client.getMouseCanvasPosition().getY() + 20);
                moveMouse(robot, MouseCanvasPosition, randomCanvas, 11, 4, 4);
                rightClick(robot);

                try {
                    Thread.sleep(random2(500, 800));
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                net.runelite.api.Point selectDismiss = MenuIndexPosition(MenuIndex("Dismiss"), randomCanvas);

                moveMouse(robot, randomCanvas, selectDismiss, 11, 4, 4);
                leftClick(robot);
            }
        }

        private net.runelite.api.Point MenuIndexPosition(int MenuIndex, net.runelite.api.Point LastRightClick) {
            int RCStartY = LastRightClick.getY();
            int RCStartX = LastRightClick.getX();
            int baseYOffset = 27;
            int rowOffset = 15;
            int xTolerance = 35;
            int yTolerance = 3;
            int menuY = RCStartY + baseYOffset + (MenuIndex - 1) * rowOffset + random2(-yTolerance, yTolerance);
            int menuX = RCStartX + random2(-xTolerance, xTolerance);
            net.runelite.api.Point MenuIndexPoint = new net.runelite.api.Point(menuX, menuY);
            return MenuIndexPoint;
        }

        private boolean Equipped(int testID) {

            if (client.getItemContainer(InventoryID.EQUIPMENT) == null) {
                return false;
            }

            for (Item item : client.getItemContainer(InventoryID.EQUIPMENT).getItems()) {
                if (item == null) {
                    continue;
                }

                if (item.getId() == testID) {
                    return true;
                }
            }
            return false;
        }

        private int MenuIndex(String TargetMenuOption) {
            if (client.getMenuEntries() == null) {
                return 0;
            }
            MenuEntry menuOptions[] = client.getMenuEntries();
            client.getWidgetPositionsX();
            int menuSize = menuOptions.length;
            int optionFromBottom = 0;
            int optionIndex;
            for (MenuEntry option : menuOptions) {
                if (option.getOption().matches(TargetMenuOption)) {
                    optionIndex = menuSize - optionFromBottom;
                    return optionIndex;
                }
                optionFromBottom = optionFromBottom + 1;
            }
            return 0;
        }

        private GameObject ObjectFromTiles(int id) {
            GameObject object;
            Tile[][][] tiles = client.getScene().getTiles();

            for (int l = 0; l < tiles.length; l++) {
                for (int j = 0; j < tiles[l].length; j++) {
                    for (int k = 0; k < tiles[l][j].length; k++) {
                        Tile x = tiles[l][j][k];
                        if (x != null) {
                            if (x.getWorldLocation() != null) {
                                if (client.getLocalPlayer().getWorldLocation().distanceTo(x.getWorldLocation()) < 8) {
                                    for (GameObject objects1 : x.getGameObjects()) {
                                        if (objects1 != null) {
                                            System.out.print(" | Object ids | " + objects1.getId());
                                            if (objects1.getId() == id) {
                                                object = objects1;
                                                return object;
                                            }
                                        }
                                    }
                                }

                            }
                        }
                    }
                }
            }
            return null;
        }

        private List<GameObject> ObjectsFromTiles(int id) {
            List<GameObject> LocalTargets1 = new ArrayList<>();
            Tile[][][] tiles = client.getScene().getTiles();

            for (int l = 0; l < tiles.length; l++) {
                for (int j = 0; j < tiles[l].length; j++) {
                    for (int k = 0; k < tiles[l][j].length; k++) {
                        Tile x = tiles[l][j][k];
                        if (x != null) {
                            if (x.getWorldLocation() != null) {
                                if (client.getLocalPlayer().getWorldLocation().distanceTo(x.getWorldLocation()) < 8) {
                                    for (GameObject objects1 : x.getGameObjects()) {
                                        if (objects1 != null) {
                                            System.out.print(" | Object ids | " + objects1.getId());
                                            if (objects1.getId() == id) {
                                                LocalTargets1.add(objects1);
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            return LocalTargets1;
        }

        private java.util.List<NPC> inverseSortListDistanceFromPlayer(java.util.List<NPC> list) {
            if (list.isEmpty()) {
                return null;
            }

            final WorldPoint PlayerWL = new WorldPoint(client.getLocalPlayer().getWorldLocation().getX(),
                    client.getLocalPlayer().getWorldLocation().getY(), client.getPlane());
            list.sort(
                    Comparator.comparing(
                            (NPC npc) -> npc.getWorldLocation().distanceTo(PlayerWL))
            );
            return list;
        }

        private int lastI = 0;

        private void ActivateRun() throws AWTException, InterruptedException {
            if (client.getEnergy() == 100) {

                if ((i - lastI) > 30) {
                    Robot robot = new Robot();
                    double x = client.getWidget(WidgetInfo.MINIMAP_RUN_ORB).getBounds().getCenterX();
                    double y = client.getWidget(WidgetInfo.MINIMAP_RUN_ORB).getBounds().getCenterY();
                    int xx = (int) x + random2(5, 15);
                    int yy = (int) y + random2(20, 30);
                    net.runelite.api.Point adjustedAltarPerspective = new net.runelite.api.Point(xx, yy);
                    net.runelite.api.Point MouseCanvasPosition = new net.runelite.api.Point(client.getMouseCanvasPosition().getX(), client.getMouseCanvasPosition().getY() + 20);

                    moveMouse(robot, MouseCanvasPosition, adjustedAltarPerspective, 11, 5, 5);
                    leftClick(robot);
                    Thread.sleep(random2(770, 1186));
                    lastI = i;
                }
            }
        }

        private GameObject ObjectsOnGround(int id) {
            GameObject object;
            Tile[][][] tiles = client.getScene().getTiles();

            for (int l = 0; l < tiles.length; l++) {
                for (int j = 0; j < tiles[l].length; j++) {
                    for (int k = 0; k < tiles[l][j].length; k++) {
                        Tile x = tiles[l][j][k];
                        if (x != null) {
                            if (x.getWorldLocation() != null) {
                                if (client.getLocalPlayer().getWorldLocation().distanceTo(x.getWorldLocation()) < 1) {
                                    for (GameObject objects1 : x.getGameObjects()) {
                                        if (objects1 != null) {
                                            System.out.print(" | Object ids | " + objects1.getId());
                                            if (objects1.getId() == id) {
                                                object = objects1;
                                                return object;
                                            }
                                        }
                                    }
                                }

                            }
                        }
                    }
                }
            }
            return null;
        }

        private int InvFoodIndex() {
            Item[] inventory = client.getItemContainer(InventoryID.INVENTORY).getItems();
            int foodIndex = 1;
            for (Item item : inventory) {
                int itemId = item.getId();
                if (FOOD_IDS.contains(itemId)) {
                    return foodIndex;
                }
                foodIndex = foodIndex + 1;
            }
            return 0;
        }

        private int foodInventory() {
            int availableFood = 0;
            if (client.getItemContainer(InventoryID.INVENTORY) == null) {
                return availableFood;
            }

            Item[] inventory = client.getItemContainer(InventoryID.INVENTORY).getItems();

            for (Item item : inventory) {
                //System.out.print(" || items id " + item.getId());
                int itemId = item.getId();
                if (FOOD_IDS.contains(itemId)) {
                    availableFood = availableFood + 1;
                }
            }
            return availableFood;
        }

        private int InvTeleIndex() {
            Item[] inventory = client.getItemContainer(InventoryID.INVENTORY).getItems();
            int teleIndex = 1;
            for (Item item : inventory) {
                int itemId = item.getId();
                if (TELE_IDS.contains(itemId)) {
                    return teleIndex;
                }
                teleIndex = teleIndex + 1;
            }
            return 0;
        }

        private void VarrockTele() throws AWTException {
            while (client.getWidget(WidgetInfo.INVENTORY).isHidden()) {
                int inventoryIconTopLeftX = client.getWidget(WidgetInfo.RESIZABLE_VIEWPORT_BOTTOM_LINE_INVENTORY_TAB).getBounds().x;
                int inventoryIconTopLeftY = client.getWidget(WidgetInfo.RESIZABLE_VIEWPORT_BOTTOM_LINE_INVENTORY_TAB).getBounds().y;
                int inventoryIconXWidth = (int) client.getWidget(WidgetInfo.RESIZABLE_VIEWPORT_BOTTOM_LINE_INVENTORY_TAB).getBounds().getWidth();
                int inventoryIconYHeight = (int) client.getWidget(WidgetInfo.RESIZABLE_VIEWPORT_BOTTOM_LINE_INVENTORY_TAB).getBounds().getHeight();
                int inventoryIconX = inventoryIconTopLeftX + 3 + random2(0, inventoryIconXWidth - 6);
                int inventoryIconY = inventoryIconTopLeftY + 3 + random2(0, inventoryIconYHeight - 6);
                Point inventoryIcon = new Point(inventoryIconX, inventoryIconY);
                Robot robot = new Robot();
                moveMouse(robot, client.getMouseCanvasPosition(), inventoryIcon, 10, 5, 5);
                try {
                    Thread.sleep(random2(150, 260));
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                leftClick(robot);
                try {
                    Thread.sleep(random2(415, 560));
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            int TeleTab = InvTeleIndex();
            Point TeleInvLocation = InvLocation(TeleTab);
            Robot robot = new Robot();
            moveMouse(robot, client.getMouseCanvasPosition(), TeleInvLocation, 10, 5, 5);
            try {
                Thread.sleep(random2(150, 260));
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            leftClick(robot);
            try {
                Thread.sleep(random2(415, 560));
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        private void eatFood() throws AWTException {

            while (client.getWidget(WidgetInfo.INVENTORY).isHidden()) {
                int inventoryIconTopLeftX = client.getWidget(WidgetInfo.RESIZABLE_VIEWPORT_BOTTOM_LINE_INVENTORY_TAB).getBounds().x;
                int inventoryIconTopLeftY = client.getWidget(WidgetInfo.RESIZABLE_VIEWPORT_BOTTOM_LINE_INVENTORY_TAB).getBounds().y;
                int inventoryIconXWidth = (int) client.getWidget(WidgetInfo.RESIZABLE_VIEWPORT_BOTTOM_LINE_INVENTORY_TAB).getBounds().getWidth();
                int inventoryIconYHeight = (int) client.getWidget(WidgetInfo.RESIZABLE_VIEWPORT_BOTTOM_LINE_INVENTORY_TAB).getBounds().getHeight();
                int inventoryIconX = inventoryIconTopLeftX + 3 + random2(0, inventoryIconXWidth - 6);
                int inventoryIconY = inventoryIconTopLeftY + 3 + random2(0, inventoryIconYHeight - 6);
                Point inventoryIcon = new Point(inventoryIconX, inventoryIconY);
                Robot robot = new Robot();
                moveMouse(robot, client.getMouseCanvasPosition(), inventoryIcon, 10, 5, 5);
                try {
                    Thread.sleep(random2(150, 260));
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                leftClick(robot);
                try {
                    Thread.sleep(random2(415, 560));
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            int foodIndex = InvFoodIndex();
            Point foodInvLocation = InvLocation(foodIndex);
            Robot robot = new Robot();
            moveMouse(robot, client.getMouseCanvasPosition(), foodInvLocation, 10, 5, 5);
            try {
                Thread.sleep(random2(150, 260));
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            leftClick(robot);
            try {
                Thread.sleep(random2(415, 560));
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        private Point openBank () throws InterruptedException, AWTException {

            while (!checkMovementIdle()) {
                Thread.sleep(1063);    }

            List<GameObject> LocalTarget0;
            List<GameObject> LocalTarget = new ArrayList<>();
            LocalTarget0 = ObjectsFromTiles(BankBooth);

            if (LocalTarget0.size() != 0) {
                for ( GameObject bankbooths : LocalTarget0) {
                    if(BankBoothsArea.contains(bankbooths.getWorldLocation().getX(),bankbooths.getWorldLocation().getY()))
                    {LocalTarget.add(bankbooths);}
                }}

            if (LocalTarget.size() != 0) {
                GameObject bank = LocalTarget.get(random2(0, LocalTarget.size() - 1));
                double x = bank.getCanvasTilePoly().getBounds().getCenterX();
                double y = bank.getCanvasTilePoly().getBounds().getCenterY();
                int xx = (int) x + random2(-5, 5);
                int yy = (int) y + random2(-5, 5);
                Point bankBoothPerspective = new Point(xx, yy);
                return (bankBoothPerspective);
            }
            return null;
        }

        private void bankInvent() throws AWTException, InterruptedException {
            while (!checkMovementIdle()) {
                Thread.sleep(1063);	}

            Robot robot = new Robot();

            if (client.getWidget(WidgetInfo.BANK_ITEM_CONTAINER) == null) {
                if(openBank()!=null){
                    if(PointInsideClickableWindow(openBank())){

                        Point MouseCanvasPosition = new Point(client.getMouseCanvasPosition().getX(), client.getMouseCanvasPosition().getY() + 20);
                        moveMouse(robot, MouseCanvasPosition, openBank(), 11, 4, 4);

                        try {	Thread.sleep(random2(411, 614));}
                        catch (InterruptedException e) {	e.printStackTrace();}

                        if (FirstLeftClickOption("Bank")){
                            leftClick(robot);
                            while (!checkMovementIdle()) {	Thread.sleep(364);	}
                            try {Thread.sleep(random2(216, 480));}
                            catch (InterruptedException e) {e.printStackTrace();}
                        }}}}

            if (client.getWidget(WidgetInfo.BANK_ITEM_CONTAINER) != null){

                if (availableInventory() < 26) {

                    Point depositInventoryPoint1 = depositInventoryPoint();
                    try {	Thread.sleep(random2(500, 600));}
                    catch (InterruptedException e) { e.printStackTrace(); }

                    Point MouseCanvasPosition = new Point(client.getMouseCanvasPosition().getX(), client.getMouseCanvasPosition().getY() + 20);

                    moveMouse(robot, MouseCanvasPosition, depositInventoryPoint1, 11, 4, 4);
                    leftClick(robot);
                    try {
                        Thread.sleep(random2(600, 750));
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }

                if (getBankItemIndex(AMULET_MOULD_1)!=0 && !ItemInInventory(AMULET_MOULD)) {
                    try {
                        Thread.sleep(random2(50, 80));
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }

                    Point Banklocation = BankLocation(getBankItemIndex(AMULET_MOULD_1));

                    net.runelite.api.Point MouseCanvasPosition = new net.runelite.api.Point(client.getMouseCanvasPosition().getX(),
                            client.getMouseCanvasPosition().getY() + 20);

                    moveMouse1(robot, MouseCanvasPosition, Banklocation, 11, 4, 4);
                    leftClick(robot);
                    try {
                        Thread.sleep(random2(500, 800));
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }

                }

                if (getBankItemIndex(GOLD_BAR)!=0 && NumberofItemInInventory(GOLD_BAR)<13) {
                    try {
                        Thread.sleep(random2(50, 80));
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }

                    Point Banklocation = BankLocation(getBankItemIndex(GOLD_BAR));

                    net.runelite.api.Point MouseCanvasPosition = new net.runelite.api.Point(client.getMouseCanvasPosition().getX(), client.getMouseCanvasPosition().getY() + 20);

                    moveMouse1(robot, MouseCanvasPosition, Banklocation, 11, 4, 4);
                    try {
                        Thread.sleep(random2(500, 800));
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    rightClick(robot);

                    try {
                        Thread.sleep(random2(500, 800));
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    Point withdraw = MenuIndexPosition(MenuIndex("Withdraw-13"), Banklocation);

                    moveMouse(robot, Banklocation, withdraw, 11, 4, 4);
                    leftClick(robot);
                }

                if (getBankItemIndex(EMERALD)!=0 && NumberofItemInInventory(EMERALD)<13) {
                    try {
                        Thread.sleep(random2(50, 80));
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }

                    Point Banklocation = BankLocation(getBankItemIndex(EMERALD));

                    net.runelite.api.Point MouseCanvasPosition = new net.runelite.api.Point(client.getMouseCanvasPosition().getX(), client.getMouseCanvasPosition().getY() + 20);

                    moveMouse1(robot, MouseCanvasPosition, Banklocation, 11, 4, 4);
                    try {
                        Thread.sleep(random2(500, 800));
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    rightClick(robot);

                    try {
                        Thread.sleep(random2(500, 800));
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    Point withdraw = MenuIndexPosition(MenuIndex("Withdraw-13"), Banklocation);

                    moveMouse(robot, Banklocation, withdraw, 11, 4, 4);
                    leftClick(robot);
                }}
        }

        private int i =0;
        private Thread backgroundRun = new Thread(new Runnable() { public void run() {
            for (i =0;i<1000;i++){
                if (config.FishAddon() && client.getGameState() == GameState.LOGGED_IN) {

                    System.out.println(" | " + i + " | "
                                    + " idle time " + idleTimeSecs() + " plane " + client.getPlane()
                                    + " idle movement " + checkMovementIdle() + " WL " + client.getLocalPlayer().getWorldLocation()
                                    + " canvas " + client.getMouseCanvasPosition() + " animation " + client.getLocalPlayer().getAnimation()
                                    + " not moving time " + idleMovementTimeSecs()
                                    + " availableInventory " + availableInventory()
                                    // + " equipped " + Equipped()
                                    //+  " FirstLeftClickOption walk " + FirstLeftClickOption()
                                    + " NumberofItemInInventory(Feathers " + NumberofItemInInventory(GOLD_BAR)
                                    + " ItemInInventory(Fishing_Rod_List) " + ItemInInventory(AMULET_MOULD)
                                    + " random2 01 " + random2(0, 1)
                            //	+ " smithing widget " + (client.getWidget(WidgetInfo.BANK_ITEM_CONTAINER))
                            //	+ " smithing widget " + (client.getWidget(WidgetInfo.CRAFTING_JEWELERY_GOLD_AMULETS_U).getBounds().getCenterY())
                    );

                    //  if (client.getSelectedSceneTile().getGameObjects() != null) {
                    //	for(GameObject gameObject : client.getSelectedSceneTile().getGameObjects())
                    //	{if (gameObject!=null){
                    //				System.out.println(i + " | object " + gameObject.getId());
                    //		}}}

                    try { addon();} catch (AWTException | InterruptedException e) { e.printStackTrace();	}

                }
                try {   Thread.sleep(random2(814, 1631));  } catch (InterruptedException e) { e.printStackTrace(); }
            }}});

        private void addon() throws AWTException, InterruptedException {

            WorldPoint bankLocation = new WorldPoint(BankArea0.getX()+random2(-2,1),BankArea0.getY()+random2(-1,2),0);

            if(randomNPCCheck()!=null){
                dismissRandom();
            }

            if(client.getWidget(WidgetInfo.LEVEL_UP)!=null){
                Robot robot = new Robot();
                try {  Thread.sleep(random2(450,610));  } catch (InterruptedException e) { e.printStackTrace(); }
                robot.keyPress(KeyEvent.VK_SPACE);
                try {  Thread.sleep(random2(1522,2641));  } catch (InterruptedException e) { e.printStackTrace(); }
                robot.keyPress(KeyEvent.VK_SPACE);
                try {  Thread.sleep(random2(1100,1610));  } catch (InterruptedException e) { e.printStackTrace(); }
            }

            double ii = (double) i;
            double breakCheck = ii / 80;
            if (breakCheck == (int) breakCheck) {
                System.out.print(" || SLEEPING || ");
                {	try {		Thread.sleep(random2(10000, 60000));	}
                catch (InterruptedException e) {	e.printStackTrace();	}		}		}

            double breakCheck2 = ii / 15;
            if (breakCheck2 == (int) breakCheck2) {
                System.out.print(" | BRIEF BREAK | ");
                {	try {		Thread.sleep(random2(3000, 9000));	}
                catch (InterruptedException e) {	e.printStackTrace();	}		}		}

            double idleTimeThreshold1 = random2(1214,2862);
            double idleTimeThreshold = idleTimeThreshold1/1000;
            //System.out.println(" | check 2 | ");
            while (idleMovementTimeSecs() < idleTimeThreshold ) {
                Thread.sleep(random2(816, 1186));
            }

            WorldPoint FurnaceSpot = new WorldPoint(FurnaceSpot0.getX() + random2(-3, 0),
                    FurnaceSpot0.getY() + random2(-2, 1), 0);

            if(NumberofItemInInventory(GOLD_BAR)>0 && NumberofItemInInventory(EMERALD)>0
                    && ItemInInventory(AMULET_MOULD) && idleTimeSecs()>idleTimeThreshold ){
                System.out.println(" | check 3 | ");
                if(FurnaceSpot0.distanceTo(client.getLocalPlayer().getWorldLocation())<5) {
                    List<GameObject> LocalRange;
                    LocalRange = ObjectsFromTiles(FURNACE_ID);

                    if (LocalRange.size() != 0) {

                        GameObject Target = LocalRange.get(0);
                        System.out.println(" | check 8 | ");
                        if (Target != null) {
                            System.out.println(" | check 9 | ");
                            double x = Target.getCanvasTilePoly().getBounds().getCenterX();
                            double y = Target.getCanvasTilePoly().getBounds().getCenterY();
                            int xx = (int) x + random2(-6, 6);
                            int yy = (int) y + random2(-6, 6);

                            net.runelite.api.Point adjustedSpotPerspective = new net.runelite.api.Point(xx, yy);

                            if (PointInsideClickableWindow(adjustedSpotPerspective) && client.getWidget(WidgetInfo.CRAFTING_JEWELERY_EMERALD_AMULETS_U)==null) {
                                System.out.println(" | check 11 | ");
                                Robot robot = new Robot();
                                net.runelite.api.Point MouseCanvasPosition = new net.runelite.api.Point(client.getMouseCanvasPosition().getX(),
                                        client.getMouseCanvasPosition().getY() + 20);
                                moveMouse(robot, MouseCanvasPosition, adjustedSpotPerspective, 11, 5, 5);
                                if (FirstLeftClickOption("Smelt")) {
                                    leftClick(robot);
                                    while (idleMovementTimeSecs() < 1) {
                                        Thread.sleep(random2(816, 1186));
                                    }
                                    try {
                                        Thread.sleep(random2(1640, 3127));
                                    } catch (InterruptedException e) {
                                        e.printStackTrace();
                                    }}}

                            if(client.getWidget(WidgetInfo.CRAFTING_JEWELERY_EMERALD_AMULETS_U)!=null){
                                x = client.getWidget(WidgetInfo.CRAFTING_JEWELERY_EMERALD_AMULETS_U).getBounds().getCenterX();
                                y = client.getWidget(WidgetInfo.CRAFTING_JEWELERY_EMERALD_AMULETS_U).getBounds().getCenterY();
                                xx = (int) x + random2(-6, 6);
                                yy = (int) y + random2(+18, 27);
                                adjustedSpotPerspective = new net.runelite.api.Point(xx, yy);
                                if (PointInsideClickableWindow(adjustedSpotPerspective)){
                                    Robot robot = new Robot();
                                    net.runelite.api.Point MouseCanvasPosition = new net.runelite.api.Point(client.getMouseCanvasPosition().getX(),
                                            client.getMouseCanvasPosition().getY() + 20);
                                    moveMouse(robot, MouseCanvasPosition, adjustedSpotPerspective, 11, 5, 5);

                                    leftClick(robot);
                                    try {   Thread.sleep(random2(814, 1631));  }
                                    catch (InterruptedException e) { e.printStackTrace(); }
                                    return;
                                }}
                            return;
                        }
                        try {Thread.sleep(random2(80, 116));}
                        catch (InterruptedException e) {e.printStackTrace();}

                    }
                    System.out.println(" | check 4.7 | ");
                    return;
                }

                ActivateRun();

                walk(FurnaceSpot);
                return;
            }

            if(NumberofItemInInventory(GOLD_BAR)>0 && ItemInInventory(AMULET_MOULD) && idleTimeSecs()<idleTimeThreshold
                    &&  NumberofItemInInventory(EMERALD)>0 ){
                int randomInt = random2(1,40);
                if (randomInt == 5)	{RandomMouseMove();}
                return;}

            if(idleTimeSecs()>idleTimeThreshold){
                if (NumberofItemInInventory(GOLD_BAR)==0 || !ItemInInventory(AMULET_MOULD) ||  NumberofItemInInventory(EMERALD)==0)
                {
                    if(openBank()!=null || client.getWidget(WidgetInfo.BANK_ITEM_CONTAINER) != null){
                        if(PointInsideClickableWindow(openBank()) || client.getWidget(WidgetInfo.BANK_ITEM_CONTAINER) != null ){
                            bankInvent();
                            return;}}

                    walk(bankLocation);
                    return;
                }}


        }

        private static final Set<Integer> RUNE_IDS = ImmutableSet.of(ItemID.MIND_RUNE, ItemID.AIR_RUNE);
        private static final Set<Integer> FOOD_IDS = ImmutableSet.of(ItemID.LOBSTER, ItemID.SALMON, ItemID.SWORDFISH);
        private int HPThreshold = 24;
        private static final Set<Integer> TELE_IDS = ImmutableSet.of(ItemID.VARROCK_TELEPORT);

        private Rectangle BankArea = new Rectangle(3094,3492,5,6);
        private WorldPoint BankArea0 = new WorldPoint(3097, 3495, 0);
        private WorldPoint edgeville_bank = new WorldPoint(3094,3492,0);

        private Set<Integer> NPCBankers = ImmutableSet.of(NpcID.BANKER_1028,NpcID.BANKER_1027);
        private Set<Integer> BankBooths = ImmutableSet.of(ObjectID.BANK_BOOTH_10355);
        private Rectangle BankBoothsArea = new Rectangle(3096,3493,5,2);

        private WorldPoint FurnaceSpot0 = new WorldPoint(3109, 3499, 0);
        private Rectangle FurnaceArea = new Rectangle(3106,3496,6,5);

        private Set <Integer> AMULET_MOULD = ImmutableSet.of(ItemID.AMULET_MOULD);
        private int GOLD_BAR = ItemID.GOLD_BAR;
        private int EMERALD = ItemID.EMERALD;
        private int AMULET_MOULD_1 = ItemID.AMULET_MOULD;
        private int FURNACE_ID = ObjectID.FURNACE_16469;
        private int BankBooth = ObjectID.BANK_BOOTH_10355;

    }


}
