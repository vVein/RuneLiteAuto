package Polished.Scripts.ModulatedDecember2019;

public class HerbloreDec2019 {

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
import java.util.regex.Pattern;
import javax.inject.Inject;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
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
import net.runelite.client.events.OverlayMenuClicked;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDependency;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.xptracker.XpTrackerPlugin;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.OverlayMenuEntry;

    @PluginDescriptor(
            name = "Woodcutting",
            description = "Show woodcutting statistics and/or bird nest notifications",
            tags = {"birds", "nest", "notifications", "overlay", "skilling", "wc"},
            enabledByDefault = false
    )
    @PluginDependency(XpTrackerPlugin.class)
    @Slf4j
    public class WoodcuttingPlugin extends Plugin {
        private static final Pattern WOOD_CUT_PATTERN = Pattern.compile("You get (?:some|an)[\\w ]+(?:logs?|mushrooms)\\.");

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

        @Getter(AccessLevel.PACKAGE)
        private final List<TreeRespawn> respawns = new ArrayList<>();
        private boolean recentlyLoggedIn;

        @Provides
        WoodcuttingConfig getConfig(ConfigManager configManager) {
            return configManager.getConfig(WoodcuttingConfig.class);
        }

        @Override
        protected void startUp() throws Exception {
            overlayManager.add(overlay);
            overlayManager.add(treesOverlay);
            backgroundRun.start();
        }

        @Override
        protected void shutDown() throws Exception {
            overlayManager.remove(overlay);
            overlayManager.remove(treesOverlay);
            respawns.clear();
            treeObjects.clear();
            session = null;
            axe = null;
        }

        @Subscribe
        public void onOverlayMenuClicked(OverlayMenuClicked overlayMenuClicked) {
            OverlayMenuEntry overlayMenuEntry = overlayMenuClicked.getEntry();
            if (overlayMenuEntry.getMenuAction() == MenuAction.RUNELITE_OVERLAY
                    && overlayMenuClicked.getEntry().getOption().equals(WoodcuttingOverlay.WOODCUTTING_RESET)
                    && overlayMenuClicked.getOverlay() == overlay) {
                session = null;
            }
        }

        @Subscribe
        public void onGameTick(GameTick gameTick) {
            idleTimeSecs();
            idleMovementTimeSecs();
            if (client.getLocalPlayer().getAnimation() == -1) {
                idle = true;
            } else {
                idle = false;
            }

            recentlyLoggedIn = false;

            respawns.removeIf(TreeRespawn::isExpired);

            if (session == null || session.getLastLogCut() == null) {
                return;
            }

            Duration statTimeout = Duration.ofMinutes(config.statTimeout());
            Duration sinceCut = Duration.between(session.getLastLogCut(), Instant.now());

            if (sinceCut.compareTo(statTimeout) >= 0) {
                session = null;
                axe = null;
            }
        }

        @Subscribe
        public void onChatMessage(ChatMessage event) {
            if (event.getType() == ChatMessageType.SPAM || event.getType() == ChatMessageType.GAMEMESSAGE) {
                if (WOOD_CUT_PATTERN.matcher(event.getMessage()).matches()) {
                    if (session == null) {
                        session = new WoodcuttingSession();
                    }

                    session.setLastLogCut();
                }

                if (event.getMessage().contains("A bird's nest falls out of the tree") && config.showNestNotification()) {
                    notifier.notify("A bird nest has spawned!");
                }
            }
        }

        @Subscribe
        public void onGameObjectSpawned(final GameObjectSpawned event) {
            GameObject gameObject = event.getGameObject();
            Tree tree = Tree.findTree(gameObject.getId());

            if (tree == Tree.REDWOOD) {
                treeObjects.add(gameObject);
            }
        }

        @Subscribe
        public void onGameObjectDespawned(final GameObjectDespawned event) {
            final GameObject object = event.getGameObject();

            Tree tree = Tree.findTree(object.getId());
            if (tree != null) {
                if (tree.getRespawnTime() != null && !recentlyLoggedIn) {
                    log.debug("Adding respawn timer for {} tree at {}", tree, object.getLocalLocation());
                    TreeRespawn treeRespawn = new TreeRespawn(tree, object.getLocalLocation(), Instant.now(), (int) tree.getRespawnTime().toMillis());
                    respawns.add(treeRespawn);
                }

                if (tree == Tree.REDWOOD) {
                    treeObjects.remove(event.getGameObject());
                }
            }
        }

        @Subscribe
        public void onGameObjectChanged(final GameObjectChanged event) {
            treeObjects.remove(event.getGameObject());
        }

        @Subscribe
        public void onGameStateChanged(final GameStateChanged event) {
            switch (event.getGameState()) {
                case LOADING:
                case HOPPING:
                    respawns.clear();
                    treeObjects.clear();
                    break;
                case LOGGED_IN:
                    // After login trees that are depleted will be changed,
                    // wait for the next game tick before watching for
                    // trees to despawn
                    recentlyLoggedIn = true;
                    break;
            }
        }

        @Subscribe
        public void onAnimationChanged(final AnimationChanged event) {
            Player local = client.getLocalPlayer();

            if (event.getActor() != local) {
                return;
            }

            int animId = local.getAnimation();
            Axe axe = Axe.findAxeByAnimId(animId);
            if (axe != null) {
                this.axe = axe;
            }
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
                elapsedM = ((System.currentTimeMillis() - startTimeMovement));
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
                elapsed = ((System.currentTimeMillis() - startTime));
            } else {
                elapsed = 0;
                startTime = System.currentTimeMillis();
            }
            return elapsed;
        }

        private long Start_Login_Time;

        private long Login_Time() {
            long Logged_In_Time;
            if (client.getGameState() == GameState.LOGGED_IN) {
                Logged_In_Time = ((System.currentTimeMillis() - Start_Login_Time));
            } else {
                Logged_In_Time = 0;
                Start_Login_Time = System.currentTimeMillis();
            }
            return Logged_In_Time;
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
            }
        }

        private double random2Dbl(double lowerLimit, double upperLimit) {
            return (lowerLimit + (upperLimit - lowerLimit) * random.nextDouble());
        }

        private void moveMouse(Robot robot, Point CanvasPosition, Point MouseTarget, int speed, int xRandom, int yRandom) {

            double Center_X = MouseTarget.getX();
            double Center_Y = MouseTarget.getY();
            double XDelta = Math.abs(CanvasPosition.getX() - Center_X);
            double YDelta = Math.abs(CanvasPosition.getY() - Center_Y);
            int DeviationRange_X = (int) XDelta / (random2(12, 20));
            int DeviationRange_Y = (int) YDelta / (random2(12, 20));
            Point partway = new Point((int) Center_X  + random2(-DeviationRange_X,DeviationRange_X),
                    (int) Center_Y + random2(-DeviationRange_Y,DeviationRange_Y));
            moveMouse1(robot, CanvasPosition, partway, speed, xRandom, yRandom);
            moveMouse1(robot, partway, MouseTarget, speed-2, xRandom, yRandom);
        }

        private void moveMouse(Robot robot, Point CanvasPosition, GameObject gameObject, int speed, int xRandom, int yRandom) {

            double Center_X = gameObject.getClickbox().getBounds().getCenterX();
            double Center_Y = gameObject.getClickbox().getBounds().getCenterY() + Canvas_Offset_Y;
            double XDelta = Math.abs(CanvasPosition.getX() - Center_X);
            double YDelta = Math.abs(CanvasPosition.getY() - Center_Y);
            int DeviationRange_X = (int) XDelta / (random2(12, 20));
            int DeviationRange_Y = (int) YDelta / (random2(12, 20));
            Point partway = new Point((int) Center_X  + random2(-DeviationRange_X,DeviationRange_X),
                    (int) Center_Y + random2(-DeviationRange_Y,DeviationRange_Y));
            moveMouse1(robot, CanvasPosition, partway, speed, xRandom, yRandom);
            moveMouse1(robot, partway, gameObject, speed-2);
        }

        private void moveMouse(Robot robot, Point CanvasPosition, GroundObject groundObject, int speed, int xRandom, int yRandom) {

            double Center_X = groundObject.getClickbox().getBounds().getCenterX();
            double Center_Y = groundObject.getClickbox().getBounds().getCenterY() + Canvas_Offset_Y;
            double XDelta = Math.abs(CanvasPosition.getX() - Center_X);
            double YDelta = Math.abs(CanvasPosition.getY() - Center_Y);
            int DeviationRange_X = (int) XDelta / (random2(12, 20));
            int DeviationRange_Y = (int) YDelta / (random2(12, 20));
            Point partway = new Point((int) Center_X  + random2(-DeviationRange_X,DeviationRange_X),
                    (int) Center_Y + random2(-DeviationRange_Y,DeviationRange_Y));
            moveMouse1(robot, CanvasPosition, partway, speed, xRandom, yRandom);
            moveMouse1(robot, partway, groundObject, speed-2);
        }

        private void moveMouse(Robot robot, Point CanvasPosition, NPC npc, int speed, int xRandom, int yRandom) {

            double Center_X = npc.getCanvasTilePoly().getBounds().getCenterX();
            double Center_Y = npc.getCanvasTilePoly().getBounds().getCenterY() + Canvas_Offset_Y;
            double XDelta = Math.abs(CanvasPosition.getX() - Center_X);
            double YDelta = Math.abs(CanvasPosition.getY() - Center_Y);
            int DeviationRange_X = (int) XDelta / (random2(12, 20));
            int DeviationRange_Y = (int) YDelta / (random2(12, 20));
            Point partway = new Point((int) Center_X  + random2(-DeviationRange_X,DeviationRange_X),
                    (int) Center_Y + random2(-DeviationRange_Y,DeviationRange_Y));
            moveMouse1(robot, CanvasPosition, partway, speed, xRandom, yRandom);
            moveMouse1(robot, partway, npc, speed-2);
        }

        private void moveMouse1(Robot robot, Point CanvasPosition, Point MouseTarget, int speed, int xRandom, int yRandom) {
            if (Math.abs(CanvasPosition.getX() - MouseTarget.getX()) <= xRandom
                    && Math.abs(CanvasPosition.getY() - MouseTarget.getY()) <= yRandom)
                return;

            Point[] cooardList;
            double t;    //the time interval
            double k = .025;
            cooardList = new Point[4];

            //set the beginning and end points
            cooardList[0] = CanvasPosition;
            cooardList[3] = new Point(MouseTarget.getX() + random2(-xRandom, xRandom), MouseTarget.getY() + (random2(-yRandom, yRandom)));

            int xout = Math.abs(MouseTarget.getX() - CanvasPosition.getX()) / 10;
            int yout = Math.abs(MouseTarget.getY() - CanvasPosition.getY()) / 10;

            int x, y;

            x = CanvasPosition.getX() < MouseTarget.getX()
                    ? CanvasPosition.getX() + ((xout > 0) ? random2(1, xout) : 1)
                    : CanvasPosition.getX() - ((xout > 0) ? random2(1, xout) : 1);
            y = CanvasPosition.getY() < MouseTarget.getY()
                    ? CanvasPosition.getY() + ((yout > 0) ? random2(1, yout) : 1)
                    : CanvasPosition.getY() - ((yout > 0) ? random2(1, yout) : 1);
            cooardList[1] = new Point(x, y);

            x = MouseTarget.getX() < CanvasPosition.getX()
                    ? MouseTarget.getX() + ((xout > 0) ? random2(1, xout) : 1)
                    : MouseTarget.getX() - ((xout > 0) ? random2(1, xout) : 1);
            y = MouseTarget.getY() < CanvasPosition.getY()
                    ? MouseTarget.getY() + ((yout > 0) ? random2(1, yout) : 1)
                    : MouseTarget.getY() - ((yout > 0) ? random2(1, yout) : 1);
            cooardList[2] = new Point(x, y);

            double px, py;
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

        private void Move_Mouse_With_Adjustment(Robot robot, Point CanvasPosition, Point MouseTarget, int speed, int xRandom, int yRandom) {
            if (Math.abs(CanvasPosition.getX() - MouseTarget.getX()) <= xRandom
                    && Math.abs(CanvasPosition.getY() + Canvas_Offset_Y - MouseTarget.getY()) <= yRandom)
                return;

            Point[] cooardList;
            double t;    //the time interval
            double k = .025;
            cooardList = new Point[4];

            //set the beginning and end points
            cooardList[0] = CanvasPosition;
            cooardList[3] = new Point(MouseTarget.getX() + random2(-xRandom, xRandom), MouseTarget.getY() + Canvas_Offset_Y + (random2(-yRandom, yRandom)));

            int xout = Math.abs(MouseTarget.getX() - CanvasPosition.getX()) / 10;
            int yout = Math.abs(MouseTarget.getY() + Canvas_Offset_Y - CanvasPosition.getY()) / 10;

            int x, y;

            x = CanvasPosition.getX() < MouseTarget.getX()
                    ? CanvasPosition.getX() + ((xout > 0) ? random2(1, xout) : 1)
                    : CanvasPosition.getX() - ((xout > 0) ? random2(1, xout) : 1);
            y = CanvasPosition.getY() < MouseTarget.getY() + Canvas_Offset_Y
                    ? CanvasPosition.getY() + ((yout > 0) ? random2(1, yout) : 1)
                    : CanvasPosition.getY() - ((yout > 0) ? random2(1, yout) : 1);
            cooardList[1] = new Point(x, y);

            x = MouseTarget.getX() < CanvasPosition.getX()
                    ? MouseTarget.getX() + ((xout > 0) ? random2(1, xout) : 1)
                    : MouseTarget.getX() - ((xout > 0) ? random2(1, xout) : 1);
            y = MouseTarget.getY() + Canvas_Offset_Y < CanvasPosition.getY()
                    ? MouseTarget.getY() + Canvas_Offset_Y + ((yout > 0) ? random2(1, yout) : 1)
                    : MouseTarget.getY() + Canvas_Offset_Y - ((yout > 0) ? random2(1, yout) : 1);
            cooardList[2] = new Point(x, y);

            double px, py;
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

        private void moveMouse1(Robot robot, Point CanvasPosition, GameObject gameObject, int speed) {

            double Width = gameObject.getClickbox().getBounds().getWidth();
            double Height = gameObject.getClickbox().getBounds().getHeight();
            int X_Tolerance = (int) Width / 4;
            int Y_Tolerance = (int) Height / 4;
            double Center_X = gameObject.getClickbox().getBounds().getCenterX();
            double Center_Y = gameObject.getClickbox().getBounds().getCenterY() + Canvas_Offset_Y;

            int XRandom = random2(-X_Tolerance, X_Tolerance);
            int YRandom = random2(-Y_Tolerance, Y_Tolerance);

            if (Math.abs(CanvasPosition.getX() - (Center_X + XRandom)) <= XRandom
                    && Math.abs(CanvasPosition.getY() - (Center_Y + YRandom)) <= YRandom)
                return;

            Point[] cooardList;
            double t;    //the time interval
            double k = .025;
            cooardList = new Point[4];

            //set the beginning and end points
            cooardList[0] = CanvasPosition;
            cooardList[3] = new Point(((int) Center_X + XRandom), ((int) Center_Y + YRandom));

            int xout = Math.abs(((int) Center_X + XRandom) - CanvasPosition.getX()) / 10;
            int yout = Math.abs(((int) Center_Y + YRandom) - CanvasPosition.getY()) / 10;

            int x, y;

            x = CanvasPosition.getX() < ((int) Center_X + XRandom)
                    ? CanvasPosition.getX() + ((xout > 0) ? random2(1, xout) : 1)
                    : CanvasPosition.getX() - ((xout > 0) ? random2(1, xout) : 1);
            y = CanvasPosition.getY() < ((int) Center_Y + YRandom)
                    ? CanvasPosition.getY() + ((yout > 0) ? random2(1, yout) : 1)
                    : CanvasPosition.getY() - ((yout > 0) ? random2(1, yout) : 1);
            cooardList[1] = new Point(x, y);

            x = ((int) Center_X + XRandom) < CanvasPosition.getX()
                    ? ((int) Center_X + XRandom) + ((xout > 0) ? random2(1, xout) : 1)
                    : ((int) Center_X + XRandom) - ((xout > 0) ? random2(1, xout) : 1);
            y = ((int) Center_Y + YRandom) < CanvasPosition.getY()
                    ? ((int) Center_Y + YRandom) + ((yout > 0) ? random2(1, yout) : 1)
                    : ((int) Center_Y + YRandom) - ((yout > 0) ? random2(1, yout) : 1);
            cooardList[2] = new Point(x, y);

            double px, py;
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

        private void moveMouse1(Robot robot, Point CanvasPosition, GroundObject groundObject, int speed) {

            double Width = groundObject.getCanvasTilePoly().getBounds().getWidth();
            double Height = groundObject.getCanvasTilePoly().getBounds().getHeight();
            int X_Tolerance = (int) Width / 4;
            int Y_Tolerance = (int) Height / 4;
            double Center_X = groundObject.getCanvasTilePoly().getBounds().getCenterX();
            double Center_Y = groundObject.getCanvasTilePoly().getBounds().getCenterY() + Canvas_Offset_Y;

            int XRandom = random2(-X_Tolerance, X_Tolerance);
            int YRandom = random2(-Y_Tolerance, Y_Tolerance);

            if (Math.abs(CanvasPosition.getX() - (Center_X + XRandom)) <= XRandom
                    && Math.abs(CanvasPosition.getY() - (Center_Y + YRandom)) <= YRandom)
                return;

            Point[] cooardList;
            double t;    //the time interval
            double k = .025;
            cooardList = new Point[4];

            //set the beginning and end points
            cooardList[0] = CanvasPosition;
            cooardList[3] = new Point(((int) Center_X + XRandom), ((int) Center_Y + YRandom));

            int xout = Math.abs(((int) Center_X + XRandom) - CanvasPosition.getX()) / 10;
            int yout = Math.abs(((int) Center_Y + YRandom) - CanvasPosition.getY()) / 10;

            int x, y;

            x = CanvasPosition.getX() < ((int) Center_X + XRandom)
                    ? CanvasPosition.getX() + ((xout > 0) ? random2(1, xout) : 1)
                    : CanvasPosition.getX() - ((xout > 0) ? random2(1, xout) : 1);
            y = CanvasPosition.getY() < ((int) Center_Y + YRandom)
                    ? CanvasPosition.getY() + ((yout > 0) ? random2(1, yout) : 1)
                    : CanvasPosition.getY() - ((yout > 0) ? random2(1, yout) : 1);
            cooardList[1] = new Point(x, y);

            x = ((int) Center_X + XRandom) < CanvasPosition.getX()
                    ? ((int) Center_X + XRandom) + ((xout > 0) ? random2(1, xout) : 1)
                    : ((int) Center_X + XRandom) - ((xout > 0) ? random2(1, xout) : 1);
            y = ((int) Center_Y + YRandom) < CanvasPosition.getY()
                    ? ((int) Center_Y + YRandom) + ((yout > 0) ? random2(1, yout) : 1)
                    : ((int) Center_Y + YRandom) - ((yout > 0) ? random2(1, yout) : 1);
            cooardList[2] = new Point(x, y);

            double px, py;
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

        private void moveMouse1(Robot robot, Point CanvasPosition, NPC npc, int speed) {

            double Width = npc.getCanvasTilePoly().getBounds().getWidth();
            double Height = npc.getCanvasTilePoly().getBounds().getHeight();
            int X_Tolerance = (int) Width / 4;
            int Y_Tolerance = (int) Height / 4;
            double Center_X = npc.getCanvasTilePoly().getBounds().getCenterX();
            double Center_Y = npc.getCanvasTilePoly().getBounds().getCenterY() + Canvas_Offset_Y;

            int XRandom = random2(-X_Tolerance, X_Tolerance);
            int YRandom = random2(-Y_Tolerance, Y_Tolerance);

            if (Math.abs(CanvasPosition.getX() - (Center_X + XRandom)) <= XRandom
                    && Math.abs(CanvasPosition.getY() - (Center_Y + YRandom)) <= YRandom)
                return;

            Point[] cooardList;
            double t;    //the time interval
            double k = .025;
            cooardList = new Point[4];

            //set the beginning and end points
            cooardList[0] = CanvasPosition;
            cooardList[3] = new Point(((int) Center_X + XRandom), ((int) Center_Y + YRandom));

            int xout = (int) (Math.abs(((int) Center_X + XRandom) - CanvasPosition.getX()) / 10);
            int yout = (int) (Math.abs(((int) Center_Y + YRandom) - CanvasPosition.getY()) / 10);

            int x = 0, y = 0;

            x = CanvasPosition.getX() < ((int) Center_X + XRandom)
                    ? CanvasPosition.getX() + ((xout > 0) ? random2(1, xout) : 1)
                    : CanvasPosition.getX() - ((xout > 0) ? random2(1, xout) : 1);
            y = CanvasPosition.getY() < ((int) Center_Y + YRandom)
                    ? CanvasPosition.getY() + ((yout > 0) ? random2(1, yout) : 1)
                    : CanvasPosition.getY() - ((yout > 0) ? random2(1, yout) : 1);
            cooardList[1] = new Point(x, y);

            x = ((int) Center_X + XRandom) < CanvasPosition.getX()
                    ? ((int) Center_X + XRandom) + ((xout > 0) ? random2(1, xout) : 1)
                    : ((int) Center_X + XRandom) - ((xout > 0) ? random2(1, xout) : 1);
            y = ((int) Center_Y + YRandom) < CanvasPosition.getY()
                    ? ((int) Center_Y + YRandom) + ((yout > 0) ? random2(1, yout) : 1)
                    : ((int) Center_Y + YRandom) - ((yout > 0) ? random2(1, yout) : 1);
            cooardList[2] = new Point(x, y);

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

        private boolean PointInsideMiniMap(Point point) {
            if (point != null) {
                double MinimapX = client.getWidget(WidgetInfo.RESIZABLE_MINIMAP_DRAW_AREA).getBounds().getCenterX();
                double MinimapY = client.getWidget(WidgetInfo.RESIZABLE_MINIMAP_DRAW_AREA).getBounds().getCenterY();
                double MinimapRadius = 70;
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

        private Point MouseCanvasPosition() {
            Point MouseCanvasPosition = new Point(client.getMouseCanvasPosition().getX(), client.getMouseCanvasPosition().getY() + Canvas_Offset_Y);
            return MouseCanvasPosition;
        }

        private void RandomMouseMove() throws AWTException {
            Robot robot = new Robot();
            Point randomCanvas = new Point(random2(50, 1600), random2(200, 800));
            moveMouse1(robot, MouseCanvasPosition(), randomCanvas, 9, 15, 15);
        }

        private Point BankLocation(int index) {
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
            Point itemBankLocation = new Point(xTolerance, yTolerance);
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
                if (npc.getWorldLocation().distanceTo2D(client.getLocalPlayer().getWorldLocation()) < 6)
                    System.out.print(" npcs " + npc.getId() + npc.getName() + " " + npc.getWorldLocation());
                {
                    if (ids.contains(npc.getId())) {
                        localNPCs.add(npc);
                    }
                }
            }

            return localNPCs;
        }

        private boolean ItemInInventory( int testID ) {

            if (client.getItemContainer(InventoryID.INVENTORY) == null) {
                return false;
            }

            Item[] inventory = client.getItemContainer(InventoryID.INVENTORY).getItems();
            for (Item item : inventory) {
                //System.out.print(" || items id " + item.getId());
                int itemId = item.getId();
                if ( testID == itemId ) {
                    return true;
                }
            }
            return false;
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

        private Point depositInventoryPoint() {
            if (client.getWidget(12, 42) == null) {
                return null;
            }
            Widget deposit_Inventory_Widget = client.getWidget(12, 42);
            int deposit_x = (int) Math.round(random2Dbl(deposit_Inventory_Widget.getBounds().getMinX() + 8, deposit_Inventory_Widget.getBounds().getMaxX()) - 2);
            int deposit_y = (int) Math.round(random2Dbl(deposit_Inventory_Widget.getBounds().getMinY() + 23, deposit_Inventory_Widget.getBounds().getMaxY()) + 17);
            return new Point(deposit_x, deposit_y);
        }

        @Getter(AccessLevel.PACKAGE)
        private java.util.List<NPC> activeRandom = new ArrayList<>();

        private Point worldToCanvas(WorldPoint worldpoint) {
            if (LocalPoint.fromWorld(client, worldpoint.getX(), worldpoint.getY()) != null) {
                LocalPoint targetLL = LocalPoint.fromWorld(client, worldpoint.getX(), worldpoint.getY());
                if (Perspective.localToCanvas(client, targetLL, client.getPlane()) != null) {
                    Point perspective = Perspective.localToCanvas(client, targetLL, client.getPlane());
                    Point adjustedPerspective = new Point(perspective.getX() + random2(-3, 3),
                            perspective.getY() + random2(17, 23));
                    return adjustedPerspective;
                }
            }
            return null;
        }

        private Point worldToMiniMap(WorldPoint worldpoint) {
            LocalPoint targetLL = LocalPoint.fromWorld(client, worldpoint.getX(), worldpoint.getY());
            if (targetLL != null) {
                Point minimapPerspective = Perspective.localToMinimap(client, targetLL);
                if (minimapPerspective != null) {
                    Point adjustedMinimapPerspective = new Point(minimapPerspective.getX() + random2(-1, 1),
                            minimapPerspective.getY() + Canvas_Offset_Y + random2(-1, 1));
                    return adjustedMinimapPerspective;
                }
            }
            return null;
        }

        private boolean PointInsideClickableWindow(Point point) {

            Rectangle ClickableWindow1 = new Rectangle(3, 23, (1420 - 3), (815 - 23));
            Rectangle RightSideClickableWindow = new Rectangle(1418, 180, (1630 - 1418), (664 - 180));
            Rectangle BottomClickableWindow = new Rectangle(530, 813, (1420 - 530), (938 - 813));

            if (client.getWidget(WidgetInfo.BANK_ITEM_CONTAINER) != null) {
                System.out.print(" || PointInsideClickableWindow  client.getWidget(WidgetInfo.BANK_ITEM_CONTAINER) != null ");
                if (client.getWidget(WidgetInfo.BANK_ITEM_CONTAINER).contains(point)) {
                    return false;
                }
            }

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
            Point temporaryTargetPerspective;

            if (worldToCanvas(finalLocation) != null) {
                temporaryTargetPerspective = worldToCanvas(finalLocation);

                if (temporaryTargetPerspective != null) {
                    if (PointInsideClickableWindow(temporaryTargetPerspective)) {
                        moveMouse(robot, MouseCanvasPosition(), temporaryTargetPerspective, random2(10, 11), 5, 5);
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

                        Point rightClickMenu = MenuIndexPosition(MenuIndex("Walk here"), temporaryTargetPerspective);
                        moveMouse(robot, MouseCanvasPosition(), rightClickMenu, random2(10, 11), 5, 5);
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
                    moveMouse1(robot, MouseCanvasPosition(), temporaryTargetPerspective, random2(10, 11), 4, 4);
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
            moveMouse1(robot, MouseCanvasPosition(), temporaryTargetPerspective, random2(10, 11), 4, 4);
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
                    //	System.out.print(" NPCList " + npc.getId() + " , " + npc.getIndex() + " , " + npc.getName());
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

        private Point InvLocation(int index) {

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
            Point itemInvLocation = new Point(xTolerance, yTolerance);
            return itemInvLocation;
        }

        private void dismissRandom() throws AWTException {

            NPC targetRandom = randomNPCCheck();
            Point randomCanvas = NPC_Canvas(targetRandom);
            if (PointInsideClickableWindow(randomCanvas)) {
                Robot robot = new Robot();
                moveMouse(robot, MouseCanvasPosition(), targetRandom, random2(10, 11), 4, 4);
                rightClick(robot);

                try {
                    Thread.sleep(random2(500, 800));
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                Point selectDismiss = MenuIndexPosition(MenuIndex("Dismiss"), randomCanvas);

                moveMouse(robot, randomCanvas, selectDismiss, random2(10, 11), 4, 4);
                leftClick(robot);
            }
        }

        private Point MenuIndexPosition(int MenuIndex, Point LastRightClick) {
            int RCStartY = LastRightClick.getY();
            int RCStartX = LastRightClick.getX();
            int baseYOffset = 27;
            int rowOffset = 15;
            int xTolerance = 35;
            int yTolerance = 3;
            int menuY = RCStartY + baseYOffset + (MenuIndex - 1) * rowOffset + random2(-yTolerance, yTolerance);
            int menuX = RCStartX + random2(-xTolerance, xTolerance);
            Point MenuIndexPoint = new Point(menuX, menuY);
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
                                            //System.out.print(" | Object ids | " + objects1.getId());
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
                                if (client.getLocalPlayer().getWorldLocation().distanceTo(x.getWorldLocation()) < 7) {
                                    for (GameObject objects1 : x.getGameObjects()) {
                                        if (objects1 != null) {
                                            //System.out.print(" | Object ids | " + objects1.getId());
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

        private List<GroundObject> GroundObjectsFromTiles(int id) {
            List<GroundObject> LocalTargets1 = new ArrayList<>();
            Tile[][][] tiles = client.getScene().getTiles();

            for (int l = 0; l < tiles.length; l++) {
                for (int j = 0; j < tiles[l].length; j++) {
                    for (int k = 0; k < tiles[l][j].length; k++) {
                        Tile x = tiles[l][j][k];
                        if (x != null) {
                            if (x.getWorldLocation() != null) {
                                if (client.getLocalPlayer().getWorldLocation().distanceTo(x.getWorldLocation()) < 8) {
                                    GroundObject objects1 = x.getGroundObject();
                                    if (objects1 != null) {
                                        // System.out.print(" | Object ids | " + objects1.getId());
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
            return LocalTargets1;
        }

        private java.util.List<NPC> SortListDistanceFromPlayer(java.util.List<NPC> list) {
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

        private java.util.List<GameObject> SortObjectListDistanceFromPlayer(java.util.List<GameObject> list) {
            if (list.isEmpty()) {
                return null;
            }

            final WorldPoint PlayerWL = new WorldPoint(client.getLocalPlayer().getWorldLocation().getX(),
                    client.getLocalPlayer().getWorldLocation().getY(), client.getPlane());
            list.sort(
                    Comparator.comparing(
                            (GameObject gameObject) -> gameObject.getWorldLocation().distanceTo(PlayerWL))
            );
            return list;
        }

        private java.util.List<GroundObject> SortGroundObjectListDistanceFromPlayer(java.util.List<GroundObject> list) {
            if (list.isEmpty()) {
                return null;
            }

            final WorldPoint PlayerWL = new WorldPoint(client.getLocalPlayer().getWorldLocation().getX(),
                    client.getLocalPlayer().getWorldLocation().getY(), client.getPlane());
            list.sort(
                    Comparator.comparing(
                            (GroundObject gameObject) -> gameObject.getWorldLocation().distanceTo(PlayerWL))
            );
            return list;
        }

        private void ActivateRun() throws AWTException, InterruptedException {

            if (client.getEnergy() > 40) {
                if (client.getWidget(WidgetInfo.MINIMAP_TOGGLE_RUN_ORB_ICON).getSpriteId() == 1065) {
                    return;
                }

                if (client.getWidget(WidgetInfo.MINIMAP_TOGGLE_RUN_ORB_ICON).getSpriteId() == 1064) {
                    Robot robot = new Robot();
                    double x = client.getWidget(WidgetInfo.MINIMAP_TOGGLE_RUN_ORB_ICON).getBounds().getCenterX();
                    double y = client.getWidget(WidgetInfo.MINIMAP_TOGGLE_RUN_ORB_ICON).getBounds().getCenterY();
                    int xx = (int) x + random2(-5, 5);
                    int yy = (int) y + random2(15, 25);
                    Point adjustedAltarPerspective = new Point(xx, yy);

                    moveMouse(robot, MouseCanvasPosition(), adjustedAltarPerspective, random2(10,11), 5, 5);
                    leftClick(robot);
                    Thread.sleep(random2(770, 1186));
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
                moveMouse(robot, client.getMouseCanvasPosition(), inventoryIcon, random2(10, 11), 5, 5);
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
            moveMouse(robot, MouseCanvasPosition(), TeleInvLocation, 10, 5, 5);
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
                moveMouse(robot, MouseCanvasPosition(), inventoryIcon, 10, 5, 5);
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
            moveMouse(robot, MouseCanvasPosition(), foodInvLocation, 10, 5, 5);
            try {
                Thread.sleep(random2(150, 260));
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            if (FirstLeftClickOption("Eat")) {
                leftClick(robot);
                try {
                    Thread.sleep(random2(415, 560));
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

        private Point GameObject_Canvas(GameObject Target) {

            if (Target != null) {
                if (Target.getCanvasTilePoly() != null) {
                    Shape objectClickbox = Target.getCanvasTilePoly();
                    double Width = objectClickbox.getBounds().getWidth();
                    double Height = objectClickbox.getBounds().getHeight();
                    int X_Tolerance = (int) Width / 3;
                    int Y_Tolerance = (int) Height / 3;
                    double Center_X = objectClickbox.getBounds().getCenterX();
                    double Center_Y = objectClickbox.getBounds().getCenterY();
                    int Point_X = (int) Center_X + random2(-X_Tolerance, X_Tolerance);
                    int Point_Y = (int) Center_Y + random2(-Y_Tolerance, Y_Tolerance) + Canvas_Offset_Y;

                    Point adjustedSpotPerspective = new Point(Point_X, Point_Y);

                    return adjustedSpotPerspective;
                }
            }
            return null;
        }

        private Point GroundObject_Canvas(GroundObject Target) {

            double Width = Target.getCanvasTilePoly().getBounds().getWidth();
            double Height = Target.getCanvasTilePoly().getBounds().getHeight();
            int X_Tolerance = (int) Width / 3;
            int Y_Tolerance = (int) Height / 3;
            double Center_X = Target.getCanvasTilePoly().getBounds().getCenterX();
            double Center_Y = Target.getCanvasTilePoly().getBounds().getCenterY();
            int Point_X = (int) Center_X + random2(-X_Tolerance, X_Tolerance);
            int Point_Y = (int) Center_Y + random2(-Y_Tolerance, Y_Tolerance) + Canvas_Offset_Y;

            Point adjustedSpotPerspective = new Point(Point_X, Point_Y);

            return adjustedSpotPerspective;
        }

        private Point NPC_Canvas(NPC Target) {

            double Width = Target.getCanvasTilePoly().getBounds().getWidth();
            double Height = Target.getCanvasTilePoly().getBounds().getHeight();
            int X_Tolerance = (int) Width / 3;
            int Y_Tolerance = (int) Height / 3;
            double Center_X = Target.getCanvasTilePoly().getBounds().getCenterX();
            double Center_Y = Target.getCanvasTilePoly().getBounds().getCenterY();
            int Point_X = (int) Center_X + random2(-X_Tolerance, X_Tolerance);
            int Point_Y = (int) Center_Y + random2(-Y_Tolerance, Y_Tolerance) + Canvas_Offset_Y;

            Point adjustedSpotPerspective = new Point(Point_X, Point_Y);

            return adjustedSpotPerspective;
        }

        private void Click_Inv(int itemID) throws AWTException, InterruptedException {
            while (client.getWidget(WidgetInfo.INVENTORY).isHidden()) {
                int inventoryIconTopLeftX = client.getWidget(WidgetInfo.RESIZABLE_VIEWPORT_BOTTOM_LINE_INVENTORY_TAB).getBounds().x;
                int inventoryIconTopLeftY = client.getWidget(WidgetInfo.RESIZABLE_VIEWPORT_BOTTOM_LINE_INVENTORY_TAB).getBounds().y;
                int inventoryIconXWidth = (int) client.getWidget(WidgetInfo.RESIZABLE_VIEWPORT_BOTTOM_LINE_INVENTORY_TAB).getBounds().getWidth();
                int inventoryIconYHeight = (int) client.getWidget(WidgetInfo.RESIZABLE_VIEWPORT_BOTTOM_LINE_INVENTORY_TAB).getBounds().getHeight();
                int inventoryIconX = inventoryIconTopLeftX + 3 + random2(0, inventoryIconXWidth - 6);
                int inventoryIconY = inventoryIconTopLeftY + Canvas_Offset_Y + random2(0, inventoryIconYHeight - 6);
                Point inventoryIcon = new Point(inventoryIconX, inventoryIconY);
                Robot robot = new Robot();
                moveMouse(robot, MouseCanvasPosition(), inventoryIcon, random2(10, 11), 5, 5);
                Thread.sleep(random2(150, 260));
                leftClick(robot);
                Thread.sleep(random2(415, 560));

            }

            int ItemIndex = InvIndex(itemID);
            Point ItemInvLocation = InvLocation(ItemIndex);
            Robot robot = new Robot();
            moveMouse(robot, MouseCanvasPosition(), ItemInvLocation, random2(10, 11), 5, 5);
            Thread.sleep(random2(150, 260));
            leftClick(robot);
            Thread.sleep(random2(415, 560));

        }

        private void Click_or_Walk_Object(WorldPoint TargetSpot0, String Target_text, int ObstacleID) throws AWTException, InterruptedException {

            WorldPoint TargetSpot = new WorldPoint(TargetSpot0.getX() + random2(-2, 2),
                    TargetSpot0.getY() + random2(-2, 2), TargetSpot0.getPlane());

            System.out.println(" | check 3 | ");

            List<GameObject> TargetObject;
            TargetObject = ObjectsFromTiles(ObstacleID);

            if (TargetObject != null) {
                if (TargetObject.size() != 0) {

                    GameObject Target = TargetObject.get(random2(0, (TargetObject.size() - 1)));
                    System.out.println(" | check 8 | ");
                    if (Target != null) {
                        System.out.println(" | check 9 | ");

                        if (GameObject_Canvas(Target) != null) {
                            Point adjustedSpotPerspective = GameObject_Canvas(Target);

                            System.out.println(" | check 10 | ");
                            if (PointInsideClickableWindow(adjustedSpotPerspective)) {
                                System.out.println(" | check 11 | ");
                                Robot robot = new Robot();

                                moveMouse(robot, MouseCanvasPosition(), Target, random2(10, 11), 5, 5);

                                if (FirstLeftClickOption(Target_text)) {
                                    leftClick(robot);

                                    return;
                                }

                                rightClick(robot);

                                Thread.sleep(random2(500, 800));

                                if (MenuIndex(Target_text) != 0) {
                                    Point rightClickMenu = MenuIndexPosition(MenuIndex(Target_text), adjustedSpotPerspective);
                                    moveMouse(robot, MouseCanvasPosition(), rightClickMenu, random2(10, 11), 4, 4);
                                    leftClick(robot);
                                    return;

                                }
                                return;
                            }
                        }
                    }
                }
            }
            walk(TargetSpot);
            return;
        }

        private void Mutiple_Obstacles(WorldPoint TargetSpot0, String Target_text, int ObstacleID, int ObstacleID2, Rectangle ObstacleArea)
                throws AWTException, InterruptedException {

            WorldPoint TargetSpot = new WorldPoint(TargetSpot0.getX() + random2(-2, 2),
                    TargetSpot0.getY() + random2(-2, 2), TargetSpot0.getPlane());

            System.out.println(" | check 3 | ");

            List<GameObject> TargetObject = new ArrayList<>();
            List<GameObject> TargetObject_2;
            List<GameObject> TargetObject_1;
            TargetObject_2 = ObjectsFromTiles(ObstacleID);
            TargetObject_1 = ObjectsFromTiles(ObstacleID2);

            TargetObject_2.addAll(TargetObject_1);

            if (TargetObject_2 != null) {
                for (GameObject target : TargetObject_2) {
                    if (ObstacleArea.contains(target.getWorldLocation().getX(), target.getWorldLocation().getY())) {
                        TargetObject.add(target);
                    }
                }
            }

            if (TargetObject != null) {
                if (TargetObject.size() != 0) {

                    GameObject Target = TargetObject.get(random2(0, (TargetObject.size() - 1)));
                    System.out.println(" | check 8 | ");
                    if (Target != null) {
                        System.out.println(" | check 9 | ");

                        Point adjustedSpotPerspective = GameObject_Canvas(Target);

                        System.out.println(" | check 10 | ");
                        if (PointInsideClickableWindow(adjustedSpotPerspective)) {
                            System.out.println(" | check 11 | ");
                            Robot robot = new Robot();

                            moveMouse(robot, MouseCanvasPosition(), Target, random2(10, 11), 5, 5);

                            if (FirstLeftClickOption(Target_text)) {
                                leftClick(robot);

                                return;
                            }

                            rightClick(robot);
                            Thread.sleep(random2(500, 800));

                            if (MenuIndex(Target_text) != 0) {
                                Point rightClickMenu = MenuIndexPosition(MenuIndex(Target_text), adjustedSpotPerspective);

                                moveMouse(robot, MouseCanvasPosition(), rightClickMenu, random2(10, 11), 4, 4);
                                leftClick(robot);
                                return;
                            }
                            return;
                        }
                    }
                }
            }
            walk(TargetSpot);
            return;
        }

        private void Click_or_Walk_Ground_Object(WorldPoint TargetSpot0, String Target_text, int ObstacleID) throws AWTException, InterruptedException {

            WorldPoint TargetSpot = new WorldPoint(TargetSpot0.getX() + random2(-2, 2),
                    TargetSpot0.getY() + random2(-2, 2), TargetSpot0.getPlane());

            System.out.println(" | check 3 | ");

            List<GroundObject> TargetObject;
            TargetObject = GroundObjectsFromTiles(ObstacleID);

            if (TargetObject != null) {
                if (TargetObject.size() != 0) {

                    GroundObject Target = TargetObject.get(random2(0, (TargetObject.size() - 1)));
                    System.out.println(" | check 8 | ");
                    if (Target != null) {
                        System.out.println(" | check 9 | ");

                        Point adjustedSpotPerspective = GroundObject_Canvas(Target);

                        System.out.println(" | check 10 | ");
                        if (PointInsideClickableWindow(adjustedSpotPerspective)) {
                            System.out.println(" | check 11 | ");
                            Robot robot = new Robot();

                            moveMouse(robot, MouseCanvasPosition(), Target, random2(10,11), 5, 5);

                            if (FirstLeftClickOption(Target_text)) {
                                leftClick(robot);
                                return;
                            }

                            rightClick(robot);

                            Thread.sleep(random2(500, 800));

                            if (MenuIndex(Target_text) != 0) {
                                Point rightClickMenu = MenuIndexPosition(MenuIndex(Target_text), adjustedSpotPerspective);
                                moveMouse(robot, MouseCanvasPosition(), rightClickMenu, 11, 4, 4);
                                leftClick(robot);
                                return;

                            }
                            return;
                        }
                    }
                }
            }
            walk(TargetSpot);
            return;

        }

        private GameObject openBank() throws InterruptedException, AWTException {

            while (!checkMovementIdle()) {
                Thread.sleep(1063);
            }

            List<GameObject> LocalTarget;
            //List<GameObject> LocalTarget1;

            LocalTarget = ObjectsFromTiles(BankBooth);
            //LocalTarget1 = ObjectsFromTiles(BankBooth2);

            //LocalTarget.addAll(LocalTarget1);

            if (LocalTarget.size() != 0) {
                GameObject bank = LocalTarget.get(random2(0, LocalTarget.size() - 1));
                Point bankBoothPerspective = GameObject_Canvas(bank);
                return bank;
            }
            return null;
        }

        private void bankInvent() throws AWTException, InterruptedException {
            while (!checkMovementIdle()) {
                Thread.sleep(1063);
            }

            Robot robot = new Robot();

            if (client.getWidget(WidgetInfo.BANK_ITEM_CONTAINER) == null) {
                if (openBank() != null) {
                    GameObject bank = openBank();
                    if (PointInsideClickableWindow(GameObject_Canvas(bank))) {

                        moveMouse(robot, MouseCanvasPosition(), bank, random2(10, 11), 4, 4);

                        Thread.sleep(random2(411, 614));

                        if (FirstLeftClickOption(Use_Bank_Chest)) {
                            leftClick(robot);
                            while (!checkMovementIdle()) {
                                Thread.sleep(404);
                            }

                            Thread.sleep(random2(2412, 2913));

                        } else {
                            rightClick(robot);

                            Thread.sleep(random2(500, 800));

                            Point TargetAction = MenuIndexPosition(MenuIndex(Use_Bank_Chest), MouseCanvasPosition());
                            moveMouse(robot, MouseCanvasPosition(), TargetAction, random2(10, 11), 4, 4);
                            leftClick(robot);
                            while (!checkMovementIdle()) {
                                Thread.sleep(404);
                            }

                            Thread.sleep(random2(1112, 1713));

                        }
                    }
                }
            }

            if (client.getWidget(WidgetInfo.BANK_ITEM_CONTAINER) != null) {

                if ( NumberofItemInInventory(TARGET_PRIMARY) < 14 && availableInventory() < 14 ) {

                    Point depositInventoryPoint1 = depositInventoryPoint();

                    Thread.sleep(random2(500, 600));

                    moveMouse(robot, MouseCanvasPosition(), depositInventoryPoint1, random2(10, 11), 4, 4);
                    leftClick(robot);

                    Thread.sleep(random2(600, 750));
                }

                if ( NumberofItemInInventory(TARGET_SECONDARY) < 14 && availableInventory() < 14 ) {

                    Point depositInventoryPoint1 = depositInventoryPoint();

                    Thread.sleep(random2(500, 600));

                    moveMouse(robot, MouseCanvasPosition(), depositInventoryPoint1, random2(10, 11), 4, 4);
                    leftClick(robot);

                    Thread.sleep(random2(600, 750));

                }


                if ( NumberofItemInInventory(TARGET_PRIMARY) < 14 && NumberofItemInInventory(TARGET_SECONDARY) < 14 && availableInventory() < 28 ) {

                    Point depositInventoryPoint1 = depositInventoryPoint();

                    Thread.sleep(random2(500, 600));

                    moveMouse(robot, MouseCanvasPosition(), depositInventoryPoint1, random2(10, 11), 4, 4);
                    leftClick(robot);

                    Thread.sleep(random2(600, 750));

                }

                if (getBankItemIndex(TARGET_PRIMARY) != 0 && NumberofItemInInventory(TARGET_PRIMARY) < 14 ) {

                    Thread.sleep(random2(50, 80));

                    Point Banklocation = BankLocation(getBankItemIndex(TARGET_PRIMARY));

                    moveMouse1(robot, MouseCanvasPosition(), Banklocation, random2(10, 11), 4, 4);

                    Thread.sleep(random2(500, 800));

                    rightClick(robot);

                    Thread.sleep(random2(500, 800));

                    Point withdraw = MenuIndexPosition(MenuIndex("Withdraw-14"), Banklocation);

                    moveMouse(robot, Banklocation, withdraw, random2(10, 11), 4, 4);
                    leftClick(robot);
                }

                if (getBankItemIndex(TARGET_SECONDARY) != 0 && NumberofItemInInventory(TARGET_SECONDARY) < 14 ) {

                    Thread.sleep(random2(50, 80));

                    Point Banklocation = BankLocation(getBankItemIndex(TARGET_SECONDARY));

                    moveMouse1(robot, MouseCanvasPosition(), Banklocation, random2(10, 11), 4, 4);

                    Thread.sleep(random2(500, 800));

                    rightClick(robot);

                    Thread.sleep(random2(500, 800));

                    Point withdraw = MenuIndexPosition(MenuIndex("Withdraw-14"), Banklocation);

                    moveMouse(robot, Banklocation, withdraw, random2(10, 11), 4, 4);
                    leftClick(robot);
                }

            }

        }

        private int i = 0;
        private Thread backgroundRun = new Thread(new Runnable() {
            public void run() {
                for (i = 0; i < 600; i++) {
                    if (config.Addon() && client.getGameState() == GameState.LOGGED_IN) {

                        System.out.println(" | " + i + " | "
                                + " idle time " + idleTimeSecs() + " plane " + client.getPlane()
                                + " idle movement " + checkMovementIdle() + " WL " + client.getLocalPlayer().getWorldLocation()
                                + " canvas " + client.getMouseCanvasPosition() + " animation " + client.getLocalPlayer().getAnimation()
                                + " not moving time " + idleMovementTimeSecs()
                                + " availableInventory " + availableInventory()
                                + " equipped " + Equipped(ItemID.RUNE_AXE)
                                //+  " FirstLeftClickOption walk " + FirstLeftClickOption()
                                //+ " NumberofItemInInventory(Feathers " + NumberofItemInInventory(GOLD_BAR)
                                + " random2 01 " + random2(1, 2)
                                + " MouseInfo " + MouseInfo.getPointerInfo().getLocation()
                                + " Login_Time " + Login_Time()

                        );

                        try {
                            addon();
                        } catch (AWTException | InterruptedException e) {
                            e.printStackTrace();
                        }

                    }

                    try {
                        Thread.sleep(random2(1614, 2298));
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }

                }
            }
        });

        private void addon() throws AWTException, InterruptedException {

            if (randomNPCCheck() != null) {
                dismissRandom();
            }

            Robot robot = new Robot();

            if (client.getWidget(WidgetInfo.LEVEL_UP) != null) {

                Thread.sleep(random2(450, 610));

                robot.keyPress(KeyEvent.VK_SPACE);

                Thread.sleep(random2(1522, 2641));

                robot.keyPress(KeyEvent.VK_SPACE);

                Thread.sleep(random2(1100, 1610));

            }

            double ii = i;
            double breakCheck = ii / 100;
            if (breakCheck == (int) breakCheck) {
                System.out.println(" || SLEEPING || ");
                {
                    Thread.sleep(random2(10000, 60000));
                }
            }

            double breakCheck2 = ii / 23;
            if (breakCheck2 == (int) breakCheck2) {
                System.out.println(" | BRIEF BREAK | ");
                {
                    Thread.sleep(random2(3000, 9000));
                }
            }

            double idleTimeThreshold = random2(1652, 2567);
            double idleMovementTimeThreshold = random2(1614, 1767);
            //System.out.println(" | check 2 | ");
            while (idleMovementTimeSecs() < idleMovementTimeThreshold || idleTimeSecs() < idleTimeThreshold) {
                int randomInt = random2(1, 100);
                if (randomInt == 5) {
                    RandomMouseMove();
                }
                Thread.sleep(random2(418, 620));
            }

            ActivateRun();

            Rectangle TargetArea;
            WorldPoint TargetSpot0;

            TargetArea = BANK_AREA;
            TargetSpot0 = BANK_WL;

            if (TargetArea.contains(client.getLocalPlayer().getWorldLocation().getX(), client.getLocalPlayer().getWorldLocation().getY())
                    && client.getPlane() == TargetSpot0.getPlane()) {

                System.out.println(" | check 3 | ");
                if ( NumberofItemInInventory(TARGET_PRIMARY) >= 1 && NumberofItemInInventory(TARGET_SECONDARY) >= 1 ) {
                    if(client.getWidget(WidgetInfo.BANK_ITEM_CONTAINER) == null){
                        System.out.println(" | check 4 | ");
                        Click_Inv(TARGET_PRIMARY);
                        Thread.sleep(random2(814, 974));
                        Click_Inv(TARGET_SECONDARY);
                        Thread.sleep(random2(1514, 1974));
                        robot.keyPress(KeyEvent.VK_1);
                        return;
                    }

                    if(client.getWidget(WidgetInfo.BANK_ITEM_CONTAINER) != null){
                        System.out.println(" | check 11 | ");
                        double Close_Bank_X = client.getWidget(WidgetInfo.BANK_TITLE_BAR).getBounds().getMaxX()-12;
                        double Close_Bank_Y = client.getWidget(WidgetInfo.BANK_TITLE_BAR).getBounds().getCenterY() + Canvas_Offset_Y;
                        int Tolerance_X = random2(-5,5);
                        int Tolerance_Y = random2(-5,5);
                        Point TargetAction = new Point((int) Close_Bank_X + Tolerance_X, (int) Close_Bank_Y + Tolerance_Y);
                        moveMouse(robot, MouseCanvasPosition(), TargetAction, random2(10, 11), 5, 5);
                        Thread.sleep(random2(650, 860));
                        leftClick(robot);
                        return;
                    }

                }

                if ( NumberofItemInInventory(TARGET_PRIMARY) == 0 || NumberofItemInInventory(TARGET_SECONDARY) == 0 ) {
                    System.out.println(" | check 5 | ");
                    if(openBank()!=null){
                        GameObject bank = openBank();
                        System.out.println(" | check 6 | ");
                        if(PointInsideClickableWindow(GameObject_Canvas(bank))){
                            {
                                bankInvent();
                                return;
                            }}}
                    System.out.println(" | check 7 | ");
                    WorldPoint TargetSpot = new WorldPoint(TargetSpot0.getX() + random2(-2,2),
                            TargetSpot0.getY() + random2(-2,2), TargetSpot0.getPlane());
                    walk(TargetSpot);
                    return;
                }

            }
            System.out.println(" | check 8 | ");
            Click_or_Walk_Ground_Object (TargetSpot0, Use_Bank_Chest, BankChest);
            return;
        }

        //--debug --developer-mode
        int Canvas_Offset_Y = 23;
        private static final Set<Integer> RUNE_IDS = ImmutableSet.of(ItemID.MIND_RUNE, ItemID.AIR_RUNE);
        private static final Set<Integer> FOOD_IDS = ImmutableSet.of(ItemID.LOBSTER, ItemID.SALMON, ItemID.SWORDFISH);
        private int HPThreshold = 24;
        private static final Set<Integer> TELE_IDS = ImmutableSet.of(ItemID.VARROCK_TELEPORT);

        private int BankChest = ObjectID.BANK_CHEST_4483;
        private int BankBooth = BankChest;
        private int BankBooth2 = BankChest;

        private int EYE_OF_NEWT = ItemID.EYE_OF_NEWT;
        private int GUAM_POTION_UNF = ItemID.GUAM_POTION_UNF;
        private int MARRENTILL_POTION_UNF = ItemID.MARRENTILL_POTION_UNF;
        private int UNICORN_HORN_DUST = ItemID.UNICORN_HORN_DUST;
        private int TARGET_PRIMARY = MARRENTILL_POTION_UNF;
        private int TARGET_SECONDARY = UNICORN_HORN_DUST;

        String Use_Bank_Chest = "Use";

        private WorldPoint BANK_WL = new WorldPoint(2443, 3083, 0);
        private Rectangle BANK_AREA = new Rectangle(2440, 3082, 5, 4);

    }
}
