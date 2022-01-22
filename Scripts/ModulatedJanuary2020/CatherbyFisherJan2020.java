package Polished.Scripts.ModulatedJanuary2020;

import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.List;
import java.util.regex.Pattern;

public class CatherbyFisherJan2020 {/*
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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Provides;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Point;
import net.runelite.api.*;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.*;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.Notifier;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.OverlayMenuClicked;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDependency;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.xptracker.XpTrackerPlugin;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.OverlayMenuEntry;

import javax.inject.Inject;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.*;
import java.util.regex.Pattern;

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
        private int currentPlane;

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
            if (client.getLocalPlayer().getAnimation() == -1 && client.getLocalPlayer().getInteracting() == null) {
                idle = true;
            } else {
                idle = false;
            }
            recentlyLoggedIn = false;
            currentPlane = client.getPlane();

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
                if (tree.getRespawnTime() != null && !recentlyLoggedIn && currentPlane == object.getPlane()) {
                    Point max = object.getSceneMaxLocation();
                    Point min = object.getSceneMinLocation();
                    int lenX = max.getX() - min.getX();
                    int lenY = max.getY() - min.getY();
                    log.debug("Adding respawn timer for {} tree at {}", tree, object.getLocalLocation());

                    final int region = client.getLocalPlayer().getWorldLocation().getRegionID();
                    TreeRespawn treeRespawn = new TreeRespawn(tree, lenX, lenY, WorldPoint.fromScene(client, min.getX(), min.getY(), client.getPlane()), Instant.now(), (int) tree.getRespawnTime(region).toMillis());
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
                case HOPPING:
                    respawns.clear();
                case LOADING:
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

        private void moveMouse(Robot robot, Point CanvasPosition, Point MouseTarget, int xRandom, int yRandom) {
            if (Math.abs(CanvasPosition.getX() - MouseTarget.getX()) <= xRandom
                    && Math.abs(CanvasPosition.getY() - MouseTarget.getY()) <= yRandom)
                return;

            double Mouse_Distance_x = Math.abs(CanvasPosition.getX() - MouseTarget.getX());
            double Mouse_Distance_y = Math.abs(CanvasPosition.getY() - MouseTarget.getY());
            double Mouse_Distance_xSq = Math.pow(Mouse_Distance_x, 2);
            double Mouse_Distance_ySq = Math.pow(Mouse_Distance_y, 2);
            double Mouse_Distance = Math.sqrt((Mouse_Distance_xSq + Mouse_Distance_ySq));

            int speed = random2(11, 12);
            if (Mouse_Distance <= Mouse_Speed_Limit_2) {
                speed = random2(10, 11);
                if (Mouse_Distance <= Mouse_Speed_Limit_1) {
                    speed = random2(9, 10);
                }
            }

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
                //  System.out.println("mouse control px py : " + px + " " + py );
            }
        }

        private void Move_Mouse_With_Adjustment(Robot robot, Point CanvasPosition, Point MouseTarget, int xRandom, int yRandom) {
            if (Math.abs(CanvasPosition.getX() - MouseTarget.getX()) <= xRandom
                    && Math.abs(CanvasPosition.getY() + Canvas_Offset_Y - MouseTarget.getY()) <= yRandom)
                return;

            double Mouse_Distance_x = Math.abs(CanvasPosition.getX() - MouseTarget.getX());
            double Mouse_Distance_y = Math.abs(CanvasPosition.getY() - MouseTarget.getY());
            double Mouse_Distance_xSq = Math.pow(Mouse_Distance_x, 2);
            double Mouse_Distance_ySq = Math.pow(Mouse_Distance_y, 2);
            double Mouse_Distance = Math.sqrt((Mouse_Distance_xSq + Mouse_Distance_ySq));

            int speed = random2(11, 12);
            if (Mouse_Distance <= Mouse_Speed_Limit_2) {
                speed = random2(10, 11);
                if (Mouse_Distance <= Mouse_Speed_Limit_1) {
                    speed = random2(9, 10);
                }
            }

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

        int Mouse_Speed_Limit_1 = 300;
        int Mouse_Speed_Limit_2 = 1000;
        int delta_Mouse_Speed_Limit_1 = 8;
        int delta_Mouse_Speed_Limit_2 = 25;
        int Speed_Setting_1 = random2(9, 10);
        int Speed_Setting_2 = random2(10, 11);
        int Speed_Setting_3 = random2(11, 12);

        private Object Move_Mouse_WL_Minimap(Robot robot, Point CanvasPosition, WorldPoint Target_location) {

            if (World_to_MiniMap(Target_location) != null) {
                if (CanvasPosition != null) {

                    double Center_X = World_to_MiniMap(Target_location).getX();
                    double Center_Y = World_to_MiniMap(Target_location).getY();
                    int X_Tolerance = 4;
                    int Y_Tolerance = 4;

                    int XRandom = random2(-X_Tolerance, X_Tolerance);
                    int YRandom = random2(-Y_Tolerance, Y_Tolerance);

                    if (Math.abs(CanvasPosition.getX() - (Center_X + XRandom)) <= X_Tolerance
                            && Math.abs(CanvasPosition.getY() - (Center_Y + YRandom)) <= Y_Tolerance) {
                        return true;
                    }

                    double Mouse_Distance_x = Math.abs(CanvasPosition.getX() - (Center_X + XRandom));
                    double Mouse_Distance_y = Math.abs(CanvasPosition.getY() - (Center_Y + YRandom));
                    double Mouse_Distance_xSq = Math.pow(Mouse_Distance_x, 2);
                    double Mouse_Distance_ySq = Math.pow(Mouse_Distance_y, 2);
                    double Mouse_Distance = Math.sqrt((Mouse_Distance_xSq + Mouse_Distance_ySq));

                    int speed = random2(11, 12);
                    if (Mouse_Distance <= Mouse_Speed_Limit_2) {
                        speed = random2(10, 11);
                        if (Mouse_Distance <= Mouse_Speed_Limit_1) {
                            speed = random2(9, 10);
                        }
                    }

                    Point[] cooardList;
                    double t;    //the time interval
                    double k = .025;
                    cooardList = new Point[4];

                    //set the beginning and end points
                    cooardList[0] = CanvasPosition;

                    int x, y;

                    double px, py;
                    double px_1 = 0, py_1 = 0;
                    for (t = k; t <= 1 + k; t += k) {
                        //use Berstein polynomials

                        if (World_to_MiniMap(Target_location) == null) {
                            return null;
                        }
                        if (!PointInsideMiniMap(World_to_MiniMap(Target_location))) {
                            return null;
                        }

                        Center_X = World_to_MiniMap(Target_location).getX();
                        Center_Y = World_to_MiniMap(Target_location).getY();

                        cooardList[3] = new Point(((int) Center_X + XRandom), ((int) Center_Y + YRandom));

                        int xout = Math.abs(((int) Center_X + XRandom) - CanvasPosition.getX()) / 10;
                        int yout = Math.abs(((int) Center_Y + YRandom) - CanvasPosition.getY()) / 10;

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

                        if (px_1 != 0) {
                            double delta_px = Math.abs(px - px_1);
                            double delta_py = Math.abs(py - py_1);
                            speed = random2(11, 12);
                            if (delta_px <= delta_Mouse_Speed_Limit_2 && delta_py <= delta_Mouse_Speed_Limit_2) {
                                speed = random2(10, 11);
                                if (delta_px <= delta_Mouse_Speed_Limit_1 && delta_py <= delta_Mouse_Speed_Limit_1) {
                                    speed = random2(9, 10);
                                }
                            }
                        }

                        px_1 = px;
                        py_1 = py;

                        //System.out.println("mouse control : " + px + " " + py + " mouse target " + MouseTarget);
                    }
                    return true;
                }
            }
            return false;
        }

        private Object Move_Mouse_WL_Canvas(Robot robot, Point CanvasPosition, WorldPoint Target_location) {

            if (World_to_Canvas(Target_location) != null) {
                if (CanvasPosition != null) {

                    double Center_X = World_to_Canvas(Target_location).getX();
                    double Center_Y = World_to_Canvas(Target_location).getY();
                    int X_Tolerance = 5;
                    int Y_Tolerance = 5;

                    int XRandom = random2(-X_Tolerance, X_Tolerance);
                    int YRandom = random2(-Y_Tolerance, Y_Tolerance);

                    if (Math.abs(CanvasPosition.getX() - (Center_X + XRandom)) <= X_Tolerance
                            && Math.abs(CanvasPosition.getY() - (Center_Y + YRandom)) <= Y_Tolerance) {
                        return true;
                    }

                    double Mouse_Distance_x = Math.abs(CanvasPosition.getX() - (Center_X + XRandom));
                    double Mouse_Distance_y = Math.abs(CanvasPosition.getY() - (Center_Y + YRandom));
                    double Mouse_Distance_xSq = Math.pow(Mouse_Distance_x, 2);
                    double Mouse_Distance_ySq = Math.pow(Mouse_Distance_y, 2);
                    double Mouse_Distance = Math.sqrt((Mouse_Distance_xSq + Mouse_Distance_ySq));

                    int speed = random2(11, 12);
                    if (Mouse_Distance <= Mouse_Speed_Limit_2) {
                        speed = random2(10, 11);
                        if (Mouse_Distance <= Mouse_Speed_Limit_1) {
                            speed = random2(9, 10);
                        }
                    }

                    Point[] cooardList;
                    double t;    //the time interval
                    double k = .025;
                    cooardList = new Point[4];

                    //set the beginning and end points
                    cooardList[0] = CanvasPosition;

                    int x, y;

                    double px, py;
                    double px_1 = 0, py_1 = 0;
                    for (t = k; t <= 1 + k; t += k) {
                        //use Berstein polynomials

                        if (World_to_Canvas(Target_location) == null) {
                            return null;
                        }
                        if (!PointInsideClickableWindow(World_to_Canvas(Target_location))) {
                            return null;
                        }

                        Center_X = World_to_Canvas(Target_location).getX();
                        Center_Y = World_to_Canvas(Target_location).getY();

                        cooardList[3] = new Point(((int) Center_X + XRandom), ((int) Center_Y + YRandom));

                        int xout = Math.abs(((int) Center_X + XRandom) - CanvasPosition.getX()) / 10;
                        int yout = Math.abs(((int) Center_Y + YRandom) - CanvasPosition.getY()) / 10;

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

                        if (px_1 != 0) {
                            double delta_px = Math.abs(px - px_1);
                            double delta_py = Math.abs(py - py_1);
                            speed = random2(11, 12);
                            if (delta_px <= delta_Mouse_Speed_Limit_2 && delta_py <= delta_Mouse_Speed_Limit_2) {
                                speed = random2(10, 11);
                                if (delta_px <= delta_Mouse_Speed_Limit_1 && delta_py <= delta_Mouse_Speed_Limit_1) {
                                    speed = random2(9, 10);
                                }
                            }
                        }

                        px_1 = px;
                        py_1 = py;

                        //System.out.println("mouse control : " + px + " " + py + " mouse target " + MouseTarget);
                    }
                    return true;
                }
            }
            return false;
        }

        private Object Move_Mouse_GameObject(Robot robot, Point CanvasPosition, GameObject gameObject) {

            if (gameObject.getClickbox() != null) {
                if (gameObject.getClickbox().getBounds2D() != null) {
                    if (CanvasPosition != null) {

                        double Center_X = gameObject.getClickbox().getBounds2D().getCenterX();
                        double Center_Y = gameObject.getClickbox().getBounds2D().getCenterY() + Canvas_Offset_Y;
                        double Width = gameObject.getClickbox().getBounds2D().getWidth();
                        double Height = gameObject.getClickbox().getBounds2D().getHeight();
                        int X_Tolerance = (int) Width / 4;
                        int Y_Tolerance = (int) Height / 4;

                        int XRandom = random2(-X_Tolerance, X_Tolerance);
                        int YRandom = random2(-Y_Tolerance, Y_Tolerance);

                        if (Math.abs(CanvasPosition.getX() - (Center_X + XRandom)) <= X_Tolerance
                                && Math.abs(CanvasPosition.getY() - (Center_Y + YRandom)) <= Y_Tolerance) {
                            return true;
                        }

                        double Mouse_Distance_x = Math.abs(CanvasPosition.getX() - (Center_X + XRandom));
                        double Mouse_Distance_y = Math.abs(CanvasPosition.getY() - (Center_Y + YRandom));
                        double Mouse_Distance_xSq = Math.pow(Mouse_Distance_x, 2);
                        double Mouse_Distance_ySq = Math.pow(Mouse_Distance_y, 2);
                        double Mouse_Distance = Math.sqrt((Mouse_Distance_xSq + Mouse_Distance_ySq));

                        int speed = random2(11, 12);
                        if (Mouse_Distance <= Mouse_Speed_Limit_2) {
                            speed = random2(10, 11);
                            if (Mouse_Distance <= Mouse_Speed_Limit_1) {
                                speed = random2(9, 10);
                            }
                        }

                        Point[] cooardList;
                        double t;    //the time interval
                        double k = .025;
                        cooardList = new Point[4];

                        //set the beginning and end points
                        cooardList[0] = CanvasPosition;

                        int x, y;

                        double px, py;
                        double px_1 = 0, py_1 = 0;
                        for (t = k; t <= 1 + k; t += k) {
                            //use Berstein polynomials

                            if (gameObject.getClickbox().getBounds2D() == null) {
                                return null;
                            }
                            Point Mouse_Destination = new Point((int) Center_X + XRandom, (int) Center_Y + YRandom);
                            if (!PointInsideClickableWindow(Mouse_Destination)) {
                                return null;
                            }

                            Center_X = gameObject.getClickbox().getBounds2D().getCenterX();
                            Center_Y = gameObject.getClickbox().getBounds2D().getCenterY() + Canvas_Offset_Y;

                            cooardList[3] = new Point(((int) Center_X + XRandom), ((int) Center_Y + YRandom));

                            int xout = Math.abs(((int) Center_X + XRandom) - CanvasPosition.getX()) / 10;
                            int yout = Math.abs(((int) Center_Y + YRandom) - CanvasPosition.getY()) / 10;

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

                            if (px_1 != 0) {
                                double delta_px = Math.abs(px - px_1);
                                double delta_py = Math.abs(py - py_1);
                                speed = random2(11, 12);
                                if (delta_px <= delta_Mouse_Speed_Limit_2 && delta_py <= delta_Mouse_Speed_Limit_2) {
                                    speed = random2(10, 11);
                                    if (delta_px <= delta_Mouse_Speed_Limit_1 && delta_py <= delta_Mouse_Speed_Limit_1) {
                                        speed = random2(9, 10);
                                    }
                                }
                            }

                            px_1 = px;
                            py_1 = py;

                            //System.out.println("mouse control : " + px + " " + py + " mouse target " + MouseTarget);
                        }
                        return true;
                    }
                }
            }
            return false;
        }

        private Object Move_Mouse_GameObject_Type_2(Robot robot, Point CanvasPosition, GameObject gameObject) {

            if (gameObject.getCanvasTilePoly() != null) {
                if (gameObject.getCanvasTilePoly().getBounds2D() != null) {
                    if (CanvasPosition != null) {

                        double Center_X = gameObject.getCanvasTilePoly().getBounds2D().getCenterX();
                        double Center_Y = gameObject.getCanvasTilePoly().getBounds2D().getCenterY() + Canvas_Offset_Y;
                        double Width = gameObject.getCanvasTilePoly().getBounds2D().getWidth();
                        double Height = gameObject.getCanvasTilePoly().getBounds2D().getHeight();
                        int X_Tolerance = (int) Width / 4;
                        int Y_Tolerance = (int) Height / 4;

                        int XRandom = random2(-X_Tolerance, X_Tolerance);
                        int YRandom = random2(-Y_Tolerance, Y_Tolerance);

                        if (Math.abs(CanvasPosition.getX() - (Center_X + XRandom)) <= X_Tolerance
                                && Math.abs(CanvasPosition.getY() - (Center_Y + YRandom)) <= Y_Tolerance) {
                            return true;
                        }

                        double Mouse_Distance_x = Math.abs(CanvasPosition.getX() - (Center_X + XRandom));
                        double Mouse_Distance_y = Math.abs(CanvasPosition.getY() - (Center_Y + YRandom));
                        double Mouse_Distance_xSq = Math.pow(Mouse_Distance_x, 2);
                        double Mouse_Distance_ySq = Math.pow(Mouse_Distance_y, 2);
                        double Mouse_Distance = Math.sqrt((Mouse_Distance_xSq + Mouse_Distance_ySq));

                        int speed = random2(11, 12);
                        if (Mouse_Distance <= Mouse_Speed_Limit_2) {
                            speed = random2(10, 11);
                            if (Mouse_Distance <= Mouse_Speed_Limit_1) {
                                speed = random2(9, 10);
                            }
                        }

                        Point[] cooardList;
                        double t;    //the time interval
                        double k = .025;
                        cooardList = new Point[4];

                        //set the beginning and end points
                        cooardList[0] = CanvasPosition;

                        int x, y;

                        double px, py;
                        double px_1 = 0, py_1 = 0;
                        for (t = k; t <= 1 + k; t += k) {
                            //use Berstein polynomials

                            if (gameObject.getCanvasTilePoly().getBounds2D() == null) {
                                return null;
                            }
                            Point Mouse_Destination = new Point((int) Center_X + XRandom, (int) Center_Y + YRandom);
                            if (!PointInsideClickableWindow(Mouse_Destination)) {
                                return null;
                            }

                            Center_X = gameObject.getCanvasTilePoly().getBounds2D().getCenterX();
                            Center_Y = gameObject.getCanvasTilePoly().getBounds2D().getCenterY() + Canvas_Offset_Y;

                            cooardList[3] = new Point(((int) Center_X + XRandom), ((int) Center_Y + YRandom));

                            int xout = Math.abs(((int) Center_X + XRandom) - CanvasPosition.getX()) / 10;
                            int yout = Math.abs(((int) Center_Y + YRandom) - CanvasPosition.getY()) / 10;

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

                            if (px_1 != 0) {
                                double delta_px = Math.abs(px - px_1);
                                double delta_py = Math.abs(py - py_1);
                                speed = random2(11, 12);
                                if (delta_px <= delta_Mouse_Speed_Limit_2 && delta_py <= delta_Mouse_Speed_Limit_2) {
                                    speed = random2(10, 11);
                                    if (delta_px <= delta_Mouse_Speed_Limit_1 && delta_py <= delta_Mouse_Speed_Limit_1) {
                                        speed = random2(9, 10);
                                    }
                                }
                            }

                            px_1 = px;
                            py_1 = py;

                            //System.out.println("mouse control : " + px + " " + py + " mouse target " + MouseTarget);
                        }
                        return true;
                    }
                }
            }
            return false;
        }

        private Object Move_Mouse_GameObject_Type_3(Robot robot, Point CanvasPosition, GameObject gameObject) {

            if (gameObject.getClickbox() != null) {
                if (gameObject.getClickbox().getBounds2D() != null) {
                    if (CanvasPosition != null) {

                        double Center_X = gameObject.getClickbox().getBounds2D().getMaxX();
                        double Center_Y = gameObject.getClickbox().getBounds2D().getMaxY() + Canvas_Offset_Y;
                        int XRandom = random2(-50, -40);
                        int YRandom = random2(-35, -25);
                        int X_Tolerance = 5;
                        int Y_Tolerance = 5;

                        if (Math.abs(CanvasPosition.getX() - (Center_X + XRandom)) <= X_Tolerance
                                && Math.abs(CanvasPosition.getY() - (Center_Y + YRandom)) <= Y_Tolerance) {
                            return true;
                        }

                        double Mouse_Distance_x = Math.abs(CanvasPosition.getX() - (Center_X + XRandom));
                        double Mouse_Distance_y = Math.abs(CanvasPosition.getY() - (Center_Y + YRandom));
                        double Mouse_Distance_xSq = Math.pow(Mouse_Distance_x, 2);
                        double Mouse_Distance_ySq = Math.pow(Mouse_Distance_y, 2);
                        double Mouse_Distance = Math.sqrt((Mouse_Distance_xSq + Mouse_Distance_ySq));

                        int speed = random2(11, 12);
                        if (Mouse_Distance <= Mouse_Speed_Limit_2) {
                            speed = random2(10, 11);
                            if (Mouse_Distance <= Mouse_Speed_Limit_1) {
                                speed = random2(9, 10);
                            }
                        }

                        Point[] cooardList;
                        double t;    //the time interval
                        double k = .025;
                        cooardList = new Point[4];

                        //set the beginning and end points
                        cooardList[0] = CanvasPosition;

                        int x, y;

                        double px, py;
                        double px_1 = 0, py_1 = 0;
                        for (t = k; t <= 1 + k; t += k) {
                            //use Berstein polynomials

                            if (gameObject.getClickbox().getBounds2D() == null) {
                                return null;
                            }
                            Point Mouse_Destination = new Point((int) Center_X + XRandom, (int) Center_Y + YRandom);
                            if (!PointInsideClickableWindow(Mouse_Destination)) {
                                return null;
                            }

                            Center_X = gameObject.getClickbox().getBounds2D().getMaxX();
                            Center_Y = gameObject.getClickbox().getBounds2D().getMaxY() + Canvas_Offset_Y;

                            cooardList[3] = new Point(((int) Center_X + XRandom), ((int) Center_Y + YRandom));

                            int xout = Math.abs(((int) Center_X + XRandom) - CanvasPosition.getX()) / 10;
                            int yout = Math.abs(((int) Center_Y + YRandom) - CanvasPosition.getY()) / 10;

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

                            if (px_1 != 0) {
                                double delta_px = Math.abs(px - px_1);
                                double delta_py = Math.abs(py - py_1);
                                speed = random2(11, 12);
                                if (delta_px <= delta_Mouse_Speed_Limit_2 && delta_py <= delta_Mouse_Speed_Limit_2) {
                                    speed = random2(10, 11);
                                    if (delta_px <= delta_Mouse_Speed_Limit_1 && delta_py <= delta_Mouse_Speed_Limit_1) {
                                        speed = random2(9, 10);
                                    }
                                }
                            }

                            px_1 = px;
                            py_1 = py;

                            //System.out.println("mouse control : " + px + " " + py + " mouse target " + MouseTarget);
                        }
                        return true;
                    }
                }
            }
            return false;
        }

        private void moveMouse(Robot robot, Point CanvasPosition, DecorativeObject gameObject) {

            double Width = gameObject.getClickbox().getBounds2D().getWidth();
            double Height = gameObject.getClickbox().getBounds2D().getHeight();
            int X_Tolerance = (int) Width / 4;
            int Y_Tolerance = (int) Height / 4;
            double Center_X = gameObject.getClickbox().getBounds2D().getCenterX();
            double Center_Y = gameObject.getClickbox().getBounds2D().getCenterY() + Canvas_Offset_Y;

            int XRandom = random2(-X_Tolerance, X_Tolerance);
            int YRandom = random2(-Y_Tolerance, Y_Tolerance);

            if (Math.abs(CanvasPosition.getX() - (Center_X + XRandom)) <= XRandom
                    && Math.abs(CanvasPosition.getY() - (Center_Y + YRandom)) <= YRandom)
                return;

            double Mouse_Distance_x = Math.abs(CanvasPosition.getX() - (Center_X + XRandom));
            double Mouse_Distance_y = Math.abs(CanvasPosition.getY() - (Center_Y + YRandom));
            double Mouse_Distance_xSq = Math.pow(Mouse_Distance_x, 2);
            double Mouse_Distance_ySq = Math.pow(Mouse_Distance_y, 2);
            double Mouse_Distance = Math.sqrt((Mouse_Distance_xSq + Mouse_Distance_ySq));

            int speed = random2(11, 12);
            if (Mouse_Distance <= Mouse_Speed_Limit_2) {
                speed = random2(10, 11);
                if (Mouse_Distance <= Mouse_Speed_Limit_1) {
                    speed = random2(9, 10);
                }
            }

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

        private void moveMouse(Robot robot, Point CanvasPosition, GroundObject groundObject) {

            double Width = groundObject.getCanvasTilePoly().getBounds2D().getWidth();
            double Height = groundObject.getCanvasTilePoly().getBounds2D().getHeight();
            int X_Tolerance = (int) Width / 4;
            int Y_Tolerance = (int) Height / 4;
            double Center_X = groundObject.getCanvasTilePoly().getBounds2D().getCenterX();
            double Center_Y = groundObject.getCanvasTilePoly().getBounds2D().getCenterY() + Canvas_Offset_Y;

            int XRandom = random2(-X_Tolerance, X_Tolerance);
            int YRandom = random2(-Y_Tolerance, Y_Tolerance);

            if (Math.abs(CanvasPosition.getX() - (Center_X + XRandom)) <= XRandom
                    && Math.abs(CanvasPosition.getY() - (Center_Y + YRandom)) <= YRandom)
                return;

            double Mouse_Distance_x = Math.abs(CanvasPosition.getX() - (Center_X + XRandom));
            double Mouse_Distance_y = Math.abs(CanvasPosition.getY() - (Center_Y + YRandom));
            double Mouse_Distance_xSq = Math.pow(Mouse_Distance_x, 2);
            double Mouse_Distance_ySq = Math.pow(Mouse_Distance_y, 2);
            double Mouse_Distance = Math.sqrt((Mouse_Distance_xSq + Mouse_Distance_ySq));

            int speed = random2(11, 12);
            if (Mouse_Distance <= Mouse_Speed_Limit_2) {
                speed = random2(10, 11);
                if (Mouse_Distance <= Mouse_Speed_Limit_1) {
                    speed = random2(9, 10);
                }
            }

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

        private void moveMouse(Robot robot, Point CanvasPosition, NPC npc) {

            if (npc.getConvexHull() != null) {
                if (npc.getConvexHull().getBounds2D() != null) {
                    if (CanvasPosition != null) {

                        double Center_X = npc.getConvexHull().getBounds2D().getCenterX();
                        double Center_Y = npc.getConvexHull().getBounds2D().getCenterY() + Canvas_Offset_Y;
                        double Width = npc.getConvexHull().getBounds2D().getWidth();
                        double Height = npc.getConvexHull().getBounds2D().getHeight();
                        int X_Tolerance = (int) Width / 4;
                        int Y_Tolerance = (int) Height / 4;

                        int XRandom = random2(-X_Tolerance, X_Tolerance);
                        int YRandom = random2(-Y_Tolerance, Y_Tolerance);

                        if (Math.abs(CanvasPosition.getX() - (Center_X + XRandom)) <= X_Tolerance
                                && Math.abs(CanvasPosition.getY() - (Center_Y + YRandom)) <= Y_Tolerance) {
                            return;
                        }

                        double Mouse_Distance_x = Math.abs(CanvasPosition.getX() - (Center_X + XRandom));
                        double Mouse_Distance_y = Math.abs(CanvasPosition.getY() - (Center_Y + YRandom));
                        double Mouse_Distance_xSq = Math.pow(Mouse_Distance_x, 2);
                        double Mouse_Distance_ySq = Math.pow(Mouse_Distance_y, 2);
                        double Mouse_Distance = Math.sqrt((Mouse_Distance_xSq + Mouse_Distance_ySq));

                        int speed = random2(11, 12);
                        if (Mouse_Distance <= Mouse_Speed_Limit_2) {
                            speed = random2(10, 11);
                            if (Mouse_Distance <= Mouse_Speed_Limit_1) {
                                speed = random2(9, 10);
                            }
                        }

                        Point[] cooardList;
                        double t;    //the time interval
                        double k = .025;
                        cooardList = new Point[4];

                        //set the beginning and end points
                        cooardList[0] = CanvasPosition;

                        int x, y;

                        double px, py;
                        double px_1 = 0, py_1 = 0;
                        for (t = k; t <= 1 + k; t += k) {
                            //use Berstein polynomials

                            if (npc.getConvexHull() == null) {
                                return;
                            }
                            if (npc.getConvexHull().getBounds2D() == null) {
                                return;
                            }
                            Center_X = npc.getConvexHull().getBounds2D().getCenterX();
                            Center_Y = npc.getConvexHull().getBounds2D().getCenterY() + Canvas_Offset_Y;

                            cooardList[3] = new Point(((int) Center_X + XRandom), ((int) Center_Y + YRandom));

                            int xout = Math.abs(((int) Center_X + XRandom) - CanvasPosition.getX()) / 10;
                            int yout = Math.abs(((int) Center_Y + YRandom) - CanvasPosition.getY()) / 10;

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

                            if (px_1 != 0) {
                                double delta_px = Math.abs(px - px_1);
                                double delta_py = Math.abs(py - py_1);
                                speed = random2(11, 12);
                                if (delta_px <= delta_Mouse_Speed_Limit_2 && delta_py <= delta_Mouse_Speed_Limit_2) {
                                    speed = random2(10, 11);
                                    if (delta_px <= delta_Mouse_Speed_Limit_1 && delta_py <= delta_Mouse_Speed_Limit_1) {
                                        speed = random2(9, 10);
                                    }
                                }
                            }

                            px_1 = px;
                            py_1 = py;

                            //System.out.println("mouse control : " + px + " " + py + " mouse target " + MouseTarget);
                        }
                    }
                }
            }
        }

        private void Move_Mouse_NPC_Type_2 (Robot robot, Point CanvasPosition, NPC npc) {

            if (npc.getCanvasTilePoly() != null) {
                if (npc.getCanvasTilePoly().getBounds2D() != null) {
                    if (CanvasPosition != null) {

                        double Center_X = npc.getCanvasTilePoly().getBounds2D().getCenterX();
                        double Center_Y = npc.getCanvasTilePoly().getBounds2D().getCenterY() + Canvas_Offset_Y;
                        double Width = npc.getCanvasTilePoly().getBounds2D().getWidth();
                        double Height = npc.getCanvasTilePoly().getBounds2D().getHeight();
                        int X_Tolerance = (int) Width / 4;
                        int Y_Tolerance = (int) Height / 4;

                        int XRandom = random2(-X_Tolerance, X_Tolerance);
                        int YRandom = random2(-Y_Tolerance, Y_Tolerance);

                        if (Math.abs(CanvasPosition.getX() - (Center_X + XRandom)) <= X_Tolerance
                                && Math.abs(CanvasPosition.getY() - (Center_Y + YRandom)) <= Y_Tolerance) {
                            return;
                        }

                        double Mouse_Distance_x = Math.abs(CanvasPosition.getX() - (Center_X + XRandom));
                        double Mouse_Distance_y = Math.abs(CanvasPosition.getY() - (Center_Y + YRandom));
                        double Mouse_Distance_xSq = Math.pow(Mouse_Distance_x, 2);
                        double Mouse_Distance_ySq = Math.pow(Mouse_Distance_y, 2);
                        double Mouse_Distance = Math.sqrt((Mouse_Distance_xSq + Mouse_Distance_ySq));

                        int speed = random2(11, 12);
                        if (Mouse_Distance <= Mouse_Speed_Limit_2) {
                            speed = random2(10, 11);
                            if (Mouse_Distance <= Mouse_Speed_Limit_1) {
                                speed = random2(9, 10);
                            }
                        }

                        Point[] cooardList;
                        double t;    //the time interval
                        double k = .025;
                        cooardList = new Point[4];

                        //set the beginning and end points
                        cooardList[0] = CanvasPosition;

                        int x, y;

                        double px, py;
                        double px_1 = 0, py_1 = 0;
                        for (t = k; t <= 1 + k; t += k) {
                            //use Berstein polynomials

                            if (npc.getCanvasTilePoly() == null) {
                                return;
                            }
                            if (npc.getCanvasTilePoly().getBounds2D() == null) {
                                return;
                            }
                            Center_X = npc.getCanvasTilePoly().getBounds2D().getCenterX();
                            Center_Y = npc.getCanvasTilePoly().getBounds2D().getCenterY() + Canvas_Offset_Y;

                            cooardList[3] = new Point(((int) Center_X + XRandom), ((int) Center_Y + YRandom));

                            int xout = Math.abs(((int) Center_X + XRandom) - CanvasPosition.getX()) / 10;
                            int yout = Math.abs(((int) Center_Y + YRandom) - CanvasPosition.getY()) / 10;

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

                            if (px_1 != 0) {
                                double delta_px = Math.abs(px - px_1);
                                double delta_py = Math.abs(py - py_1);
                                speed = random2(11, 12);
                                if (delta_px <= delta_Mouse_Speed_Limit_2 && delta_py <= delta_Mouse_Speed_Limit_2) {
                                    speed = random2(10, 11);
                                    if (delta_px <= delta_Mouse_Speed_Limit_1 && delta_py <= delta_Mouse_Speed_Limit_1) {
                                        speed = random2(9, 10);
                                    }
                                }
                            }

                            px_1 = px;
                            py_1 = py;

                            //System.out.println("mouse control : " + px + " " + py + " mouse target " + MouseTarget);
                        }
                    }
                }
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
                double MinimapX = client.getWidget(net.runelite.api.widgets.WidgetInfo.RESIZABLE_MINIMAP_DRAW_AREA).getBounds().getCenterX();
                double MinimapY = client.getWidget(net.runelite.api.widgets.WidgetInfo.RESIZABLE_MINIMAP_DRAW_AREA).getBounds().getCenterY() + Canvas_Offset_Y;
                double MinimapRadius = 73;
                // 75
                double deltaX = Math.abs(MinimapX - point.getX());
                double deltaY = Math.abs(MinimapY - point.getY());
                double deltaXSQ = Math.pow(deltaX, 2);
                double deltaYSQ = Math.pow(deltaY, 2);
                double Hypotenuse = Math.sqrt(deltaXSQ + deltaYSQ);

                double WorldMinimapX = client.getWidget(net.runelite.api.widgets.WidgetInfo.MINIMAP_WORLDMAP_ORB).getBounds().getCenterX();
                double WorldMinimapY = client.getWidget(net.runelite.api.widgets.WidgetInfo.MINIMAP_WORLDMAP_ORB).getBounds().getCenterY() + Canvas_Offset_Y;
                double WorldMinimapRadius = 18;
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
            Point MouseCanvasPosition = new Point(MouseInfo.getPointerInfo().getLocation().x, MouseInfo.getPointerInfo().getLocation().y);
            return MouseCanvasPosition;
        }

        private Point Mouse_Canvas_Position_Client() {
            Point MouseCanvasPosition = new Point(client.getMouseCanvasPosition().getX(), client.getMouseCanvasPosition().getY() + Canvas_Offset_Y);
            return MouseCanvasPosition;
        }

        private void RandomMouseMove() throws AWTException {
            Robot robot = new Robot();
            Point randomCanvas = new Point(random2(50, 1600), random2(200, 800));
            moveMouse(robot, MouseCanvasPosition(), randomCanvas, 15, 15);
        }

        private Point BankLocation(int index) {
            // row = 1, n = index, column = n , while n>8 , column = n-8 row = row +1
            // row 1 y = 115  // row 2 y = 153  // column 1 x = 420 // column 2 x = 469  // 519  ,  189
            //canvas bank 355,88 	// column spacing of 50, tolerance 23 	// row spacing 37 , tolerance 22
            int bankBaseX = client.getWidget(net.runelite.api.widgets.WidgetInfo.BANK_ITEM_CONTAINER).getCanvasLocation().getX();
            int bankBaseY = client.getWidget(net.runelite.api.widgets.WidgetInfo.BANK_ITEM_CONTAINER).getCanvasLocation().getY();
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

        private List<NPC> GetNPC(int ids) {

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
                    if (ids == npc.getId()) {
                        localNPCs.add(npc);
                    }
                }
            }

            return localNPCs;
        }

        private List<NPC> GetNPC(List<Integer> ids) {

            List<NPC> localNPCs = new ArrayList<>();
            List<NPC> NPCList;

            if (client.getNpcs() == null) {
                return null;
            }

            NPCList = client.getNpcs();

            for (NPC npc : NPCList) {
                if (npc.getWorldLocation().distanceTo2D(client.getLocalPlayer().getWorldLocation()) < 9)
                    System.out.print(" npcs " + npc.getId() + npc.getName() + " " + npc.getWorldLocation());
                {
                    if (ids.contains(npc.getId())) {
                        localNPCs.add(npc);
                    }
                }
            }

            return localNPCs;
        }

        private boolean ItemInInventory(int testID) {

            if (client.getItemContainer(InventoryID.INVENTORY) == null) {
                return false;
            }

            Item[] inventory = client.getItemContainer(InventoryID.INVENTORY).getItems();
            for (Item item : inventory) {
                //System.out.print(" || items id " + item.getId());
                int itemId = item.getId();
                if (testID == itemId) {
                    return true;
                }
            }
            return false;
        }

        private boolean ItemInInventory(List<Integer> testList) {

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
        private List<NPC> activeRandom = new ArrayList<>();

        private Point worldToCanvas(WorldPoint worldpoint) {
            if (LocalPoint.fromWorld(client, worldpoint.getX(), worldpoint.getY()) != null) {
                LocalPoint targetLL = LocalPoint.fromWorld(client, worldpoint);
                if (Perspective.localToCanvas(client, targetLL, client.getPlane()) != null) {
                    Point perspective = Perspective.localToCanvas(client, targetLL, client.getPlane());
                    Point adjustedPerspective = new Point(perspective.getX() + random2(-4, 4),
                            perspective.getY() + Canvas_Offset_Y + random2(-4, 4));
                    return adjustedPerspective;
                }
            }
            return null;
        }

        private Point World_to_Canvas(WorldPoint worldpoint) {
            if (LocalPoint.fromWorld(client, worldpoint.getX(), worldpoint.getY()) != null) {
                LocalPoint targetLL = LocalPoint.fromWorld(client, worldpoint);
                if (Perspective.localToCanvas(client, targetLL, client.getPlane()) != null) {
                    Point perspective = Perspective.localToCanvas(client, targetLL, client.getPlane());
                    Point adjustedPerspective = new Point(perspective.getX(), perspective.getY() + Canvas_Offset_Y);
                    return adjustedPerspective;
                }
            }
            return null;
        }

        private Point worldToMiniMap(WorldPoint worldpoint) {
            LocalPoint targetLL = LocalPoint.fromWorld(client, worldpoint);
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

        private Point World_to_MiniMap(WorldPoint worldpoint) {
            LocalPoint targetLL = LocalPoint.fromWorld(client, worldpoint.getX(), worldpoint.getY());
            if (targetLL != null) {
                Point minimapPerspective = Perspective.localToMinimap(client, targetLL);
                if (minimapPerspective != null) {
                    Point adjustedMinimapPerspective = new Point(minimapPerspective.getX(),
                            minimapPerspective.getY() + Canvas_Offset_Y);
                    return adjustedMinimapPerspective;
                }
            }
            return null;
        }

        private boolean Mouse_Inside_Bounds() throws AWTException, InterruptedException {
            Robot robot = new Robot();
            if (client.getMouseCanvasPosition().getX() == -1 || client.getMouseCanvasPosition().getY() == -1) {
                Thread.sleep(random2(8000, 12000));
                Point Mid_Screen = new Point(random2(600, 800), random2(400, 500));
                moveMouse(robot, MouseCanvasPosition(), Mid_Screen, 5, 5);
                Thread.sleep(random2(1100, 1500));
                return false;
            }
            return true;
        }

        private boolean PointInsideClickableWindow(Point point) {

            if (point != null) {
                double Mouse_X = point.getX();
                double Mouse_Y = point.getY();

                Rectangle ClickableWindow1 = new Rectangle(5, 7 + Canvas_Offset_Y, (1420 - 3), (841 - 26));
                Rectangle RightSideClickableWindow = new Rectangle(1418, 200, (1630 - 1418), (694 - 200));
                Rectangle BottomClickableWindow = new Rectangle(530, 840, (1420 - 530), (1004 - 840));
                Rectangle Bottom_Right_Clickable_Area = new Rectangle(1218, 703, 1434 + 1 - 1218, 970 + 1 - 703);

                if (client.getWidget(net.runelite.api.widgets.WidgetInfo.BANK_ITEM_CONTAINER) != null) {
                    System.out.print(" || PointInsideClickableWindow  client.getWidget(WidgetInfo.BANK_ITEM_CONTAINER) != null ");
                    if (client.getWidget(net.runelite.api.widgets.WidgetInfo.BANK_ITEM_CONTAINER).contains(point)) {
                        return false;
                    }
                }

                if (ClickableWindow1.contains(Mouse_X, Mouse_Y)
                        || RightSideClickableWindow.contains(Mouse_X, Mouse_Y)
                        || BottomClickableWindow.contains(Mouse_X, Mouse_Y)
                        || Bottom_Right_Clickable_Area.contains(Mouse_X, Mouse_Y)

                ) {
                    return true;
                }
            }
            return false;
        }

        private boolean FirstLeftClickOption(String testText) {

            MenuEntry[] options = client.getMenuEntries();
            MenuEntry firstOption;

            if (options.length == 0) {
                return false;
            }

            firstOption = options[options.length - 1];
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

        private boolean First_Left_Click_Option(List<String> test_Text) {

            MenuEntry[] options = client.getMenuEntries();
            MenuEntry firstOption;

            if (options.length == 0) {
                return false;
            }

            firstOption = options[options.length - 1];
            //option=Walk here
            if (firstOption != null) {
                if (firstOption.getOption() != null) {
                    for (String testText : test_Text) {
                        if (firstOption.getOption().equals(testText)) {
                            return true;
                        }
                    }
                }
            }
            return false;
        }

        private boolean Left_Click_Option_Target_ID(int Target_ID_Menu_Option) {

            MenuEntry[] options = client.getMenuEntries();
            MenuEntry firstOption;

            if (options.length == 0) {
                return false;
            }

            firstOption = options[options.length - 1];

            if (firstOption != null) {
                if (firstOption.getOption() != null) {
                    if (firstOption.getIdentifier() == Target_ID_Menu_Option && firstOption.getOption().equals("Take")) {
                        return true;
                    }
                }
            }
            return false;
        }

        private boolean Left_Click_Option_Target_IDs(List<Integer> Target_IDs_Menu_Option) {

            MenuEntry[] options = client.getMenuEntries();
            MenuEntry firstOption;

            if (options.length == 0) {
                return false;
            }

            firstOption = options[options.length - 1];

            if (firstOption != null) {
                if (firstOption.getOption() != null) {
                    if (Target_IDs_Menu_Option.contains(firstOption.getIdentifier()) && firstOption.getOption().equals("Take")) {
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

        private void walk(WorldPoint finalLocation) throws AWTException, InterruptedException {

            Robot robot = new Robot();
            WorldPoint temporaryTarget;
            Point temporaryTargetPerspective;

            if (World_to_Canvas(finalLocation) != null) {
                temporaryTargetPerspective = World_to_Canvas(finalLocation);

                if (temporaryTargetPerspective != null) {
                    if (PointInsideClickableWindow(temporaryTargetPerspective)) {
                        Move_Mouse_WL_Canvas(robot, MouseCanvasPosition(), finalLocation);
                        if (FirstLeftClickOption()) {
                            leftClick(robot);
                            return;
                        }
                        rightClick(robot);
                        Thread.sleep(random2(600, 700));
                        Point rightClickMenu = MenuIndexPosition(MenuIndex("Walk here"), MouseCanvasPosition());
                        moveMouse(robot, MouseCanvasPosition(), rightClickMenu, 5, 5);
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
                    Move_Mouse_WL_Minimap(robot, MouseCanvasPosition(), finalLocation);
                    leftClick(robot);
                    return;
                }
            }

            double multiplier = random2(7, 8) * 0.1;
            int startX = client.getLocalPlayer().getWorldLocation().getX();
            int endX = finalLocation.getX();
            double partwayXD = startX + (endX - startX) * multiplier;
            int partwayX = (int) partwayXD;
            int startY = client.getLocalPlayer().getWorldLocation().getY();
            int endY = finalLocation.getY();
            double partwayYD = startY + (endY - startY) * multiplier;
            int partwayY = (int) partwayYD;
            temporaryTarget = new WorldPoint(partwayX, partwayY, client.getLocalPlayer().getWorldLocation().getPlane());
            temporaryTargetPerspective = worldToMiniMap(temporaryTarget);
            System.out.print(" | Walk Check 3 | ");

            while (!PointInsideMiniMap(temporaryTargetPerspective)) {
                multiplier = random2(7, 8) * 0.1;
                endX = temporaryTarget.getX();
                partwayXD = startX + (endX - startX) * multiplier;
                partwayX = (int) partwayXD;
                endY = temporaryTarget.getY();
                partwayYD = startY + (endY - startY) * multiplier;
                partwayY = (int) partwayYD;
                temporaryTarget = new WorldPoint(partwayX, partwayY, client.getLocalPlayer().getWorldLocation().getPlane());
                temporaryTargetPerspective = worldToMiniMap(temporaryTarget);
                //System.out.println("temporary target iter'" + temporaryTarget);
            }
            //System.out.println("temporary target iter used" + temporaryTarget);
            Move_Mouse_WL_Minimap(robot, MouseCanvasPosition(), temporaryTarget);
            leftClick(robot);
        }

        private final Set<Integer> RANDOM_IDS = ImmutableSet.of(
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
                NpcID.GILES, NpcID.GILES_5441,
                NpcID.FROG_5429
        );

        private NPC randomNPCCheck() {
            List<NPC> activeRandom = new ArrayList<>();
            List<NPC> NPCList;
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

            int invBaseX = client.getWidget(net.runelite.api.widgets.WidgetInfo.INVENTORY).getCanvasLocation().getX();
            int invBaseY = client.getWidget(net.runelite.api.widgets.WidgetInfo.INVENTORY).getCanvasLocation().getY();
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

        private void dismissRandom() throws AWTException, InterruptedException {
            Thread.sleep(random2(620, 700));
            NPC targetRandom = randomNPCCheck();
            Point randomCanvas = NPC_Canvas(targetRandom);
            if (PointInsideClickableWindow(randomCanvas)) {

                Robot robot = new Robot();
                Move_Mouse_NPC_Type_2 (robot, MouseCanvasPosition(), targetRandom);
                rightClick(robot);

                Thread.sleep(random2(620, 700));

                Point select_Dismiss = MenuIndexPosition(MenuIndex("Dismiss"), MouseCanvasPosition());
                moveMouse(robot, MouseCanvasPosition(), select_Dismiss, 4, 4);
                leftClick(robot);
                Thread.sleep(random2(640, 700));

            }
        }

        private Point MenuIndexPosition(int MenuIndex, Point LastRightClick) {

            int RCStartX = LastRightClick.getX();
            int RCStartY = LastRightClick.getY();
            int baseYOffset = 4 + Canvas_Offset_Y;
            int rowOffset = 15;
            int xTolerance = 35;
            int yTolerance = 3;

            int menuY = RCStartY + baseYOffset + (MenuIndex - 1) * rowOffset + random2(-yTolerance, yTolerance);

            if (MenuIndex == 0) {
                menuY = RCStartY + baseYOffset + random2(-yTolerance, yTolerance) - 25;
            }

            int menuX = RCStartX + random2(-xTolerance, xTolerance);
            Point MenuIndexPoint = new Point(menuX, menuY);

            return MenuIndexPoint;

        }

        private int Equipped_Count(int testID) {

            if (client.getItemContainer(InventoryID.EQUIPMENT) == null) {
                return 0;
            }

            for (Item item : client.getItemContainer(InventoryID.EQUIPMENT).getItems()) {
                if (item == null) {
                    continue;
                }

                if (item.getId() == testID) {
                    return item.getQuantity();
                }
            }
            return 0;
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

        private boolean Equipped(List<Integer> test_IDs) {

            if (client.getItemContainer(InventoryID.EQUIPMENT) == null) {
                return false;
            }

            for (Item item : client.getItemContainer(InventoryID.EQUIPMENT).getItems()) {
                if (item == null) {
                    continue;
                }

                if (test_IDs.contains(item.getId())) {
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
                System.out.println(" menu options target " + option.getTarget() + " getIdentifier " + option.getIdentifier());
                if (option.getOption().equals(TargetMenuOption)) {
                    optionIndex = menuSize - optionFromBottom;
                    return optionIndex;
                }
                optionFromBottom = optionFromBottom + 1;
            }
            return 0;
        }

        private int MenuIndex(int TargetMenuOption) {
            if (client.getMenuEntries() == null) {
                return 0;
            }
            MenuEntry menuOptions[] = client.getMenuEntries();
            client.getWidgetPositionsX();
            int menuSize = menuOptions.length;
            int optionFromBottom = 0;
            int optionIndex;
            for (MenuEntry option : menuOptions) {
                System.out.println(" menu options target " + option.getTarget() + " getIdentifier " + option.getIdentifier());
                if (option.getIdentifier() == TargetMenuOption && option.getOption().equals("Take")) {
                    optionIndex = menuSize - optionFromBottom;
                    return optionIndex;
                }
                optionFromBottom = optionFromBottom + 1;
            }
            return 0;
        }

        private int MenuIndex(int TargetMenuOption, String Target_Menu_Text) {
            if (client.getMenuEntries() == null) {
                return 0;
            }
            MenuEntry menuOptions[] = client.getMenuEntries();
            client.getWidgetPositionsX();
            int menuSize = menuOptions.length;
            int optionFromBottom = 0;
            int optionIndex;
            for (MenuEntry option : menuOptions) {
                System.out.println(" menu options target " + option.getTarget() + " getIdentifier " + option.getIdentifier());
                //"Take"
                if (option.getIdentifier() == TargetMenuOption && option.getOption().equals(Target_Menu_Text)) {
                    optionIndex = menuSize - optionFromBottom;
                    return optionIndex;
                }
                optionFromBottom = optionFromBottom + 1;
            }
            return 0;
        }

        private int MenuIndex(List<Integer> TargetMenuOption) {
            if (client.getMenuEntries() == null) {
                return 0;
            }
            MenuEntry menuOptions[] = client.getMenuEntries();
            client.getWidgetPositionsX();
            int menuSize = menuOptions.length;
            int optionFromBottom = 0;
            int optionIndex;
            for (MenuEntry option : menuOptions) {
                System.out.println(" menu options target " + option.getTarget() + " getIdentifier " + option.getIdentifier());
                if (TargetMenuOption.contains(option.getIdentifier()) && option.getOption().equals("Take")) {
                    optionIndex = menuSize - optionFromBottom;
                    return optionIndex;
                }
                optionFromBottom = optionFromBottom + 1;
            }
            return 0;
        }

        private int Menu_Index_1(String TargetMenuOption) {
            if (client.getMenuEntries() == null) {
                return 0;
            }
            MenuEntry menuOptions[] = client.getMenuEntries();
            client.getWidgetPositionsX();
            int menuSize = menuOptions.length;
            int optionFromBottom = 0;
            int optionIndex;
            for (MenuEntry option : menuOptions) {
                String option_no_space = option.getOption().replaceAll("\\s+", "");
                if (option.getOption().replaceAll("\\s+", "").matches(TargetMenuOption.replaceAll("\\s+", ""))) {
                    optionIndex = menuSize - optionFromBottom;
                    return optionIndex;
                }
                optionFromBottom = optionFromBottom + 1;
            }
            return 0;
        }


        private int MenuIndex_old(String TargetMenuOption) {
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

        private List<Tile> Item_Tile_from_tiles(int id) {
            List<Tile> LocalTargets1 = new ArrayList<>();
            Tile[][][] tiles = client.getScene().getTiles();

            for (int l = 0; l < tiles.length; l++) {
                for (int j = 0; j < tiles[l].length; j++) {
                    for (int k = 0; k < tiles[l][j].length; k++) {
                        Tile x = tiles[l][j][k];
                        if (x != null) {
                            if (x.getWorldLocation() != null) {
                                if (client.getLocalPlayer().getWorldLocation().distanceTo(x.getWorldLocation()) < 9) {
                                    if (x.getGroundItems() != null) {
                                        for (TileItem objects1 : x.getGroundItems()) {
                                            if (objects1 != null) {
                                                if (objects1.getId() == id) {
                                                    LocalTargets1.add(x);
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
            return LocalTargets1;
        }

        private Tile Tile_from_WorldPoint(WorldPoint WorldPoint) {
            Tile Target_tile;
            Tile[][][] tiles = client.getScene().getTiles();

            for (int l = 0; l < tiles.length; l++) {
                for (int j = 0; j < tiles[l].length; j++) {
                    for (int k = 0; k < tiles[l][j].length; k++) {
                        Tile x = tiles[l][j][k];
                        if (x != null) {
                            if (x.getWorldLocation() != null) {
                                if (client.getLocalPlayer().getWorldLocation().distanceTo(x.getWorldLocation()) < 9) {
                                    if (x.getWorldLocation().getX() == WorldPoint.getX()
                                            && x.getWorldLocation().getY() == WorldPoint.getY()) {
                                        Target_tile = x;
                                        return Target_tile;
                                    }
                                }
                            }
                        }
                    }
                }
            }
            return null;
        }

        static class Valuable_Item_Tile {

            private int id;
            private Tile tile;

            // Constructor or setter
            public Valuable_Item_Tile(int id, Tile tile) {
                if (id == 0 || tile == null) {
                    return;
                }
                this.id = id;
                this.tile = tile;
            }

            // getters
            public int getId() {
                return this.id;
            }

            public Tile getTile() {
                return this.tile;
            }
        }

        @Inject
        ItemManager itemManager;

        private List<net.runelite.client.plugins.woodcutting.WoodcuttingPlugin.Valuable_Item_Tile> Valuable_Item_Tile_from_tiles() {
            List<net.runelite.client.plugins.woodcutting.WoodcuttingPlugin.Valuable_Item_Tile> LocalTargets1 = new ArrayList<>();
            Tile[][][] tiles = client.getScene().getTiles();

            for (int l = 0; l < tiles.length; l++) {
                for (int j = 0; j < tiles[l].length; j++) {
                    for (int k = 0; k < tiles[l][j].length; k++) {
                        Tile x = tiles[l][j][k];
                        if (x != null) {
                            if (x.getWorldLocation() != null) {
                                if (client.getLocalPlayer().getWorldLocation().distanceTo(x.getWorldLocation()) < 9) {
                                    if (x.getGroundItems() != null) {
                                        for (TileItem objects1 : x.getGroundItems()) {
                                            if (objects1 != null) {
                                                int gePrice = itemManager.getItemPrice(objects1.getId()) * objects1.getQuantity();
                                                //System.out.print(" | item | " + objects1.getId() + " | item ge | " + gePrice );
                                                if (gePrice > GE_Price_Threshold) {
                                                    LocalTargets1.add(new net.runelite.client.plugins.woodcutting.WoodcuttingPlugin.Valuable_Item_Tile(objects1.getId(), x));
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
            return LocalTargets1;
        }

        private List<Tile> Item_Tile_from_tiles_1(int itemID) {
            List<Tile> LocalTargets = new ArrayList<>();
            Tile[][][] tiles = client.getScene().getTiles();

            for (int l = 0; l < tiles.length; l++) {
                for (int j = 0; j < tiles[l].length; j++) {
                    for (int k = 0; k < tiles[l][j].length; k++) {
                        Tile x = tiles[l][j][k];
                        if (x != null) {
                            if (x.getWorldLocation() != null) {
                                if (client.getLocalPlayer().getWorldLocation().distanceTo(x.getWorldLocation()) < 9) {
                                    if (x.getGroundItems() != null) {
                                        for (TileItem ground_Item : x.getGroundItems()) {
                                            if (ground_Item != null) {
                                                if (ground_Item.getId() == itemID) {
                                                    LocalTargets.add(x);
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
            if (!LocalTargets.isEmpty()) {
                List<Tile> LocalTargets_0 = Sort_Tile_List_Distance_from_Player(LocalTargets);
                return LocalTargets_0;
            }
            return null;
        }


        private List<Tile> Item_Tile_from_tiles_1(List<Integer> itemID) {
            List<Tile> LocalTargets = new ArrayList<>();
            Tile[][][] tiles = client.getScene().getTiles();

            for (int l = 0; l < tiles.length; l++) {
                for (int j = 0; j < tiles[l].length; j++) {
                    for (int k = 0; k < tiles[l][j].length; k++) {
                        Tile x = tiles[l][j][k];
                        if (x != null) {
                            if (x.getWorldLocation() != null) {
                                if (client.getLocalPlayer().getWorldLocation().distanceTo(x.getWorldLocation()) < 9) {
                                    if (x.getGroundItems() != null) {
                                        for (TileItem ground_Item : x.getGroundItems()) {
                                            if (ground_Item != null) {
                                                if (itemID.contains(ground_Item.getId())) {
                                                    LocalTargets.add(x);
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
            if (!LocalTargets.isEmpty()) {
                List<Tile> LocalTargets_0 = Sort_Tile_List_Distance_from_Player(LocalTargets);
                return LocalTargets_0;
            }
            return null;
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
                                if (client.getLocalPlayer().getWorldLocation().distanceTo(x.getWorldLocation()) < 10) {
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

        private List<GameObject> Objects_List_from_Tiles(List<Integer> id) {
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
                                            if (id.contains(objects1.getId())) {
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

        private List<DecorativeObject> DecorativeObjectsFromTiles(int id) {
            List<DecorativeObject> LocalTargets1 = new ArrayList<>();
            Tile[][][] tiles = client.getScene().getTiles();

            for (int l = 0; l < tiles.length; l++) {
                for (int j = 0; j < tiles[l].length; j++) {
                    for (int k = 0; k < tiles[l][j].length; k++) {
                        Tile x = tiles[l][j][k];
                        if (x != null) {
                            if (x.getWorldLocation() != null) {
                                if (client.getLocalPlayer().getWorldLocation().distanceTo(x.getWorldLocation()) < 8) {
                                    DecorativeObject objects1 = x.getDecorativeObject();
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

        private List<Tile> Sort_Tile_List_Distance_from_Player(List<Tile> list) {
            if (list.isEmpty()) {
                return null;
            }

            WorldPoint PlayerWL = new WorldPoint(client.getLocalPlayer().getWorldLocation().getX(),
                    client.getLocalPlayer().getWorldLocation().getY(), client.getPlane());
            list.sort(
                    Comparator.comparing(
                            (Tile tile) -> tile.getWorldLocation().distanceTo(PlayerWL))
            );
            return list;
        }

        private java.util.List<NPC> SortListDistanceFromPlayer(java.util.List<NPC> list) {
            if (list.isEmpty()) {
                return null;
            }

            WorldPoint PlayerWL = new WorldPoint(client.getLocalPlayer().getWorldLocation().getX(),
                    client.getLocalPlayer().getWorldLocation().getY(), client.getPlane());
            list.sort(
                    Comparator.comparing(
                            (NPC npc) -> npc.getWorldLocation().distanceTo(PlayerWL))
            );
            return list;
        }

        private List<GameObject> SortObjectListDistanceFromPlayer(List<GameObject> list) {
            if (list.isEmpty()) {
                return null;
            }

            WorldPoint PlayerWL = new WorldPoint(client.getLocalPlayer().getWorldLocation().getX(),
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

            int Energy_Threshold = random2(40, 60);
            if (client.getEnergy() > Energy_Threshold) {
                if (client.getWidget(net.runelite.api.widgets.WidgetInfo.MINIMAP_TOGGLE_RUN_ORB_ICON).getSpriteId() == 1065) {
                    return;
                }

                if (client.getWidget(net.runelite.api.widgets.WidgetInfo.MINIMAP_TOGGLE_RUN_ORB_ICON).getSpriteId() == 1064) {
                    Robot robot = new Robot();
                    double x = client.getWidget(net.runelite.api.widgets.WidgetInfo.MINIMAP_TOGGLE_RUN_ORB_ICON).getBounds().getCenterX();
                    double y = client.getWidget(net.runelite.api.widgets.WidgetInfo.MINIMAP_TOGGLE_RUN_ORB_ICON).getBounds().getCenterY() + Canvas_Offset_Y;
                    int xx = (int) x + random2(-5, 5);
                    int yy = (int) y + random2(-5, 5);
                    Point adjustedAltarPerspective = new Point(xx, yy);

                    moveMouse(robot, MouseCanvasPosition(), adjustedAltarPerspective, 5, 5);
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

        private WallObject Wall_Object_On_Ground(int id) {
            WallObject object;
            Tile[][][] tiles = client.getScene().getTiles();

            for (int l = 0; l < tiles.length; l++) {
                for (int j = 0; j < tiles[l].length; j++) {
                    for (int k = 0; k < tiles[l][j].length; k++) {
                        Tile x = tiles[l][j][k];
                        if (x != null) {
                            if (x.getWorldLocation() != null) {
                                if (client.getLocalPlayer().getWorldLocation().distanceTo(x.getWorldLocation()) < 1) {
                                    if (x.getWallObject() != null) {
                                        WallObject objects1 = x.getWallObject();
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
            return null;
        }

        private Boolean Object_on_Ground_at_Feet() {

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
                                            return true;
                                        }
                                        //  if (x.getGroundObject() != null) {
                                        //     return true;}
                                    }
                                }

                            }
                        }
                    }
                }
            }
            return false;
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

        private void eatFood() throws AWTException, InterruptedException {
            Robot robot = new Robot();
            while (client.getWidget(net.runelite.api.widgets.WidgetInfo.INVENTORY).isHidden()) {

                Thread.sleep(random2(150, 260));

                robot.keyPress(KeyEvent.VK_ESCAPE);

                Thread.sleep(random2(715, 860));

            }
            int foodIndex = InvFoodIndex();
            Point foodInvLocation = InvLocation(foodIndex);
            moveMouse(robot, MouseCanvasPosition(), foodInvLocation, 5, 5);
            Thread.sleep(random2(750, 860));

            if (FirstLeftClickOption("Eat")) {
                leftClick(robot);
                Thread.sleep(random2(715, 860));
            }
        }

        private Point GameObject_Canvas_Alt_1(GameObject Target) {

            if (Target != null) {
                if (Target.getCanvasTilePoly() != null) {
                    Shape objectClickbox = Target.getCanvasTilePoly();
                    double Width = objectClickbox.getBounds2D().getWidth();
                    double Height = objectClickbox.getBounds2D().getHeight();
                    int X_Tolerance = (int) Width / 3;
                    int Y_Tolerance = (int) Height / 3;
                    double Center_X = objectClickbox.getBounds2D().getCenterX();
                    double Center_Y = objectClickbox.getBounds2D().getCenterY();
                    int Point_X = (int) Center_X + random2(-X_Tolerance, X_Tolerance);
                    int Point_Y = (int) Center_Y + random2(-Y_Tolerance, Y_Tolerance) + Canvas_Offset_Y;

                    Point adjustedSpotPerspective = new Point(Point_X, Point_Y);

                    return adjustedSpotPerspective;
                }
            }
            return null;
        }

        private Point GameObject_Canvas(GameObject Target) {

            if (Target != null) {
                if (Target.getClickbox() != null) {
                    if (Target.getClickbox().getBounds2D() != null) {
                        Shape objectClickbox = Target.getClickbox();
                        double Width = objectClickbox.getBounds2D().getWidth();
                        double Height = objectClickbox.getBounds2D().getHeight();
                        int X_Tolerance = (int) Width / 3;
                        int Y_Tolerance = (int) Height / 3;
                        double Center_X = objectClickbox.getBounds2D().getCenterX();
                        double Center_Y = objectClickbox.getBounds2D().getCenterY();
                        int Point_X = (int) Center_X + random2(-X_Tolerance, X_Tolerance);
                        int Point_Y = (int) Center_Y + random2(-Y_Tolerance, Y_Tolerance) + Canvas_Offset_Y;

                        Point adjustedSpotPerspective = new Point(Point_X, Point_Y);

                        return adjustedSpotPerspective;
                    }
                }
            }
            return null;
        }

        private Point Widget_Item_Canvas(Widget Target) {

            if (Target != null) {
                if (Target.getBounds() != null) {
                    Shape objectClickbox = Target.getBounds();
                    double Width = objectClickbox.getBounds2D().getWidth();
                    double Height = objectClickbox.getBounds2D().getHeight();
                    int X_Tolerance = (int) Width / 3;
                    int Y_Tolerance = (int) Height / 3;
                    double Center_X = objectClickbox.getBounds2D().getCenterX();
                    double Center_Y = objectClickbox.getBounds2D().getCenterY();
                    int Point_X = (int) Center_X + random2(-X_Tolerance, X_Tolerance);
                    int Point_Y = (int) Center_Y + random2(-Y_Tolerance, Y_Tolerance) + Canvas_Offset_Y;

                    Point adjustedSpotPerspective = new Point(Point_X, Point_Y);

                    return adjustedSpotPerspective;
                }
            }
            return null;
        }

        private Point Decorative_Object_Canvas(DecorativeObject Target) {

            if (Target != null) {
                if (Target.getClickbox() != null) {
                    Shape objectClickbox = Target.getClickbox();
                    double Width = objectClickbox.getBounds2D().getWidth();
                    double Height = objectClickbox.getBounds2D().getHeight();
                    int X_Tolerance = (int) Width / 3;
                    int Y_Tolerance = (int) Height / 3;
                    double Center_X = objectClickbox.getBounds2D().getCenterX();
                    double Center_Y = objectClickbox.getBounds2D().getCenterY();
                    int Point_X = (int) Center_X + random2(-X_Tolerance, X_Tolerance);
                    int Point_Y = (int) Center_Y + random2(-Y_Tolerance, Y_Tolerance) + Canvas_Offset_Y;

                    Point adjustedSpotPerspective = new Point(Point_X, Point_Y);

                    return adjustedSpotPerspective;
                }
            }
            return null;
        }

        private Point GroundObject_Canvas(GroundObject Target) {

            double Width = Target.getCanvasTilePoly().getBounds2D().getWidth();
            double Height = Target.getCanvasTilePoly().getBounds2D().getHeight();
            int X_Tolerance = (int) Width / 3;
            int Y_Tolerance = (int) Height / 3;
            double Center_X = Target.getCanvasTilePoly().getBounds2D().getCenterX();
            double Center_Y = Target.getCanvasTilePoly().getBounds2D().getCenterY();
            int Point_X = (int) Center_X + random2(-X_Tolerance, X_Tolerance);
            int Point_Y = (int) Center_Y + random2(-Y_Tolerance, Y_Tolerance) + Canvas_Offset_Y;

            Point adjustedSpotPerspective = new Point(Point_X, Point_Y);

            return adjustedSpotPerspective;
        }

        private Point NPC_Canvas(NPC Target) {

            if (Target.getConvexHull() != null) {
                if (Target.getConvexHull().getBounds2D() != null) {
                    double Width = Target.getConvexHull().getBounds2D().getWidth();
                    double Height = Target.getConvexHull().getBounds2D().getHeight();
                    int X_Tolerance = (int) Width / 3;
                    int Y_Tolerance = (int) Height / 3;
                    double Center_X = Target.getConvexHull().getBounds2D().getCenterX();
                    double Center_Y = Target.getConvexHull().getBounds2D().getCenterY();
                    int Point_X = (int) Center_X + random2(-X_Tolerance, X_Tolerance);
                    int Point_Y = (int) Center_Y + random2(-Y_Tolerance, Y_Tolerance) + Canvas_Offset_Y;

                    Point adjustedSpotPerspective = new Point(Point_X, Point_Y);

                    return adjustedSpotPerspective;
                }
            }
            return null;
        }

        private List<Integer> Null_Iventory_Items() throws AWTException, InterruptedException {
            List<Integer> Null_Inventory_Indices = new ArrayList<>();
            Item[] inventory = client.getItemContainer(InventoryID.INVENTORY).getItems();
            int foodIndex = 1;
            for (Item item : inventory) {
                int itemId = item.getId();
                if (itemId == -1) {
                    Null_Inventory_Indices.add(foodIndex);
                }
                foodIndex = foodIndex + 1;
            }
            return Null_Inventory_Indices;
        }

        private List<Integer> Dont_Drop_Iventory_Items(List<Integer> Do_Not_Drop_IDs) throws AWTException, InterruptedException {
            List<Integer> Null_Inventory_Indices = new ArrayList<>();
            Item[] inventory = client.getItemContainer(InventoryID.INVENTORY).getItems();
            int Index = 1;
            for (Item item : inventory) {
                int itemId = item.getId();
                if (Do_Not_Drop_IDs.contains(itemId)) {
                    Null_Inventory_Indices.add(Index);
                }
                Index = Index + 1;
            }
            return Null_Inventory_Indices;
        }

        private void Click_Item_Inv(int itemID) throws AWTException, InterruptedException {

            Robot robot = new Robot();
            while (client.getWidget(net.runelite.api.widgets.WidgetInfo.INVENTORY).isHidden()) {
                Thread.sleep(random2(150, 260));
                robot.keyPress(KeyEvent.VK_ESCAPE);
                Thread.sleep(random2(415, 560));
            }

            int ItemIndex = InvIndex(itemID);
            Point ItemInvLocation = InvLocation(ItemIndex);
            moveMouse(robot, MouseCanvasPosition(), ItemInvLocation, 5, 5);
            Thread.sleep(random2(150, 260));
            leftClick(robot);
            Thread.sleep(random2(415, 560));
        }

        private void Click_Index_Inv(int index) throws AWTException, InterruptedException {
            Robot robot = new Robot();
            while (client.getWidget(net.runelite.api.widgets.WidgetInfo.INVENTORY).isHidden()) {
                robot.keyPress(KeyEvent.VK_ESCAPE);
                Thread.sleep(random2(415, 560));
            }

            Point ItemInvLocation = InvLocation(index);
            moveMouse(robot, MouseCanvasPosition(), ItemInvLocation, 5, 5);
            Thread.sleep(random2(150, 260));
            leftClick(robot);
            Thread.sleep(random2(415, 560));

        }

        private void Close_Bank() throws AWTException, InterruptedException {
            Robot robot = new Robot();
            if (client.getWidget(net.runelite.api.widgets.WidgetInfo.BANK_ITEM_CONTAINER) != null) {

                double Close_Bank_X = client.getWidget(net.runelite.api.widgets.WidgetInfo.BANK_TITLE_BAR).getBounds().getMaxX() - 12;
                double Close_Bank_Y = client.getWidget(net.runelite.api.widgets.WidgetInfo.BANK_TITLE_BAR).getBounds().getCenterY() + Canvas_Offset_Y;
                int Tolerance_X = random2(-5, 5);
                int Tolerance_Y = random2(-5, 5);
                Point TargetAction = new Point((int) Close_Bank_X + Tolerance_X, (int) Close_Bank_Y + Tolerance_Y);
                moveMouse(robot, MouseCanvasPosition(), TargetAction, 5, 5);
                Thread.sleep(random2(650, 860));
                leftClick(robot);
                return;
            }
        }

        private long Mouse_Idle_Ticks() {
            long elapsed_0;
            long elapsed_1;
            int CLIENT_TICK_LENGTH = 20;
            elapsed_0 = client.getMouseIdleTicks();
            elapsed_1 = elapsed_0 * CLIENT_TICK_LENGTH;
            return elapsed_1;
        }

        private boolean Player_Inside_Area(Rectangle Target_Area) {

            if (Target_Area.contains(client.getLocalPlayer().getWorldLocation().getX(), client.getLocalPlayer().getWorldLocation().getY())) {
                return true;
            }
            return false;
        }

        private boolean NPC_Inside_Area(NPC Target_NPC, Rectangle Target_Area) {

            if (Target_Area.contains(Target_NPC.getWorldLocation().getX(), Target_NPC.getWorldLocation().getY())) {
                return true;
            }
            return false;
        }

        private boolean Clicked_within_Target_Location(Rectangle Target_Area) {
            // WorldPoint Target_Location

            if (client.getLocalDestinationLocation() == null) {
                return true;
            }

            if (Target_Area.contains(client.getLocalDestinationLocation().getX(), client.getLocalDestinationLocation().getY())) {
                return true;
            }
            return false;
        }

        private boolean Close_to_Target() {
            // WorldPoint Target_Location

            if (client.getLocalDestinationLocation() == null) {
                return true;
            }

            if (WorldPoint.fromLocal(client, client.getLocalDestinationLocation()).distanceTo(client.getLocalPlayer().getWorldLocation()) <= 1) {
                return true;
            }
            return false;
        }

        private boolean Clicked_within_Final_Target(WorldPoint Target_Location) {

            if (client.getLocalDestinationLocation() == null) {
                return true;
            }
            int Delta_x = Math.abs(client.getLocalDestinationLocation().getX() - Target_Location.getX());
            int Delta_y = Math.abs(client.getLocalDestinationLocation().getY() - Target_Location.getY());
            if (Delta_x <= 3 && Delta_y <= 3) {
                return true;
            }
            return false;
        }

        private List<Tile> marksOfGrace;

        private void marks_Of_Grace() {

            if (Item_Tile_from_tiles(MARK_OF_GRACE) != null) {
                marksOfGrace = Item_Tile_from_tiles(MARK_OF_GRACE);
                return;
            }
            return;
        }

        private boolean Mark_of_Grace_Nearby(Rectangle TargetArea) {
            Tile Mark = marksOfGrace.get(0);
            WorldPoint MarkWL = Mark.getWorldLocation();
            if (TargetArea.contains(MarkWL.getX(), MarkWL.getY())) {
                return true;
            }
            return false;
        }

        private void Click_Walk_Mark_of_grace(Rectangle TargetArea) throws AWTException, InterruptedException {
            Tile Mark = marksOfGrace.get(0);
            WorldPoint MarkWL = Mark.getWorldLocation();
            if (TargetArea.contains(MarkWL.getX(), MarkWL.getY())) {
                if (PointInsideClickableWindow(World_to_Canvas(MarkWL))) {
                    Robot robot = new Robot();
                    Move_Mouse_WL_Canvas(robot, MouseCanvasPosition(), MarkWL);
                    leftClick(robot);
                    Thread.sleep(random2(640, 827));
                    while (idleMovementTimeSecs() < idleMovementTimeThreshold) {
                        Thread.sleep(random2(616, 886));
                    }
                    Thread.sleep(random2(640, 827));
                    return;
                }
                WorldPoint MarkWL1 = new WorldPoint(MarkWL.getX() + random2(-1, 1),
                        MarkWL.getY() + random2(-1, 1), MarkWL.getPlane());
                walk(MarkWL1);
                Thread.sleep(random2(640, 827));
                while (idleMovementTimeSecs() < idleMovementTimeThreshold) {
                    Thread.sleep(random2(616, 886));
                }
                Thread.sleep(random2(640, 827));
                return;
            }
        }

        private void Click_Walk_Tile_Item(Tile tile, int itemID) throws AWTException, InterruptedException {

            WorldPoint MarkWL = tile.getWorldLocation();
            System.out.println(" || GE value Check 2 || ");
            if (PointInsideClickableWindow(World_to_Canvas(MarkWL))) {

                Robot robot = new Robot();
                Move_Mouse_WL_Canvas(robot, MouseCanvasPosition(), MarkWL);
                System.out.println(" || GE value Check 3 || ");
                if (Left_Click_Option_Target_ID(itemID)) {
                    leftClick(robot);
                    Thread.sleep(random2(618, 714));
                    return;
                }

                rightClick(robot);
                Thread.sleep(random2(618, 714));

                Point rightClickMenu = MenuIndexPosition(MenuIndex(itemID), MouseCanvasPosition());
                moveMouse(robot, MouseCanvasPosition(), rightClickMenu, 4, 4);
                leftClick(robot);
                Thread.sleep(random2(618, 714));
                return;

            }
            System.out.println(" || GE value Check 4 || ");
            WorldPoint MarkWL1 = new WorldPoint(MarkWL.getX() + random2(-1, 1),
                    MarkWL.getY() + random2(-1, 1), MarkWL.getPlane());
            walk(MarkWL1);
            Thread.sleep(random2(618, 714));
            return;
        }

        private void Click_Walk_Tile_Item(Tile tile, List<Integer> itemID) throws AWTException, InterruptedException {

            WorldPoint MarkWL = tile.getWorldLocation();

            if (PointInsideClickableWindow(World_to_Canvas(MarkWL))) {

                Robot robot = new Robot();
                Move_Mouse_WL_Canvas(robot, MouseCanvasPosition(), MarkWL);

                if (Left_Click_Option_Target_IDs(itemID)) {
                    leftClick(robot);
                    Thread.sleep(random2(640, 827));
                    while (idleMovementTimeSecs() < idleMovementTimeThreshold) {
                        Thread.sleep(random2(616, 886));
                    }
                    Thread.sleep(random2(640, 827));
                    return;
                }

                if (!FirstLeftClickOption()) {
                    rightClick(robot);
                    Thread.sleep(random2(618, 714));

                    Point rightClickMenu = MenuIndexPosition(MenuIndex(itemID), MouseCanvasPosition());
                    moveMouse(robot, MouseCanvasPosition(), rightClickMenu, 4, 4);
                    leftClick(robot);
                    Thread.sleep(random2(640, 827));
                    while (idleMovementTimeSecs() < idleMovementTimeThreshold) {
                        Thread.sleep(random2(616, 886));
                    }
                    Thread.sleep(random2(640, 827));
                    return;

                }
                return;
            }

            WorldPoint MarkWL1 = new WorldPoint(MarkWL.getX() + random2(-1, 1),
                    MarkWL.getY() + random2(-1, 1), MarkWL.getPlane());
            walk(MarkWL1);
            Thread.sleep(random2(640, 827));
            while (idleMovementTimeSecs() < idleMovementTimeThreshold) {
                Thread.sleep(random2(616, 886));
            }
            Thread.sleep(random2(640, 827));
            return;
        }

        private boolean Click_Target(int TARGET_OBJECT, String Target_text) throws AWTException, InterruptedException {
            Robot robot = new Robot();
            List<GameObject> Targets;
            List<GameObject> Targets0;
            Targets0 = ObjectsFromTiles(TARGET_OBJECT);
            Targets = SortObjectListDistanceFromPlayer(Targets0);

            if (Targets != null) {
                if (Targets.size() != 0) {

                    for (GameObject Target : Targets) {
                        System.out.println(" | check 8 | ");
                        if (Target != null) {
                            System.out.println(" | check 9 | ");
                            if (GameObject_Canvas(Target) != null) {
                                System.out.println(" | check 10 | ");
                                if (PointInsideClickableWindow(GameObject_Canvas(Target))) {
                                    System.out.println(" | check 11 | ");
                                    if (Move_Mouse_GameObject(robot, MouseCanvasPosition(), Target) != null) {
                                        if (FirstLeftClickOption(Target_text)) {
                                            leftClick(robot);
                                            Thread.sleep(random2(640, 827));
                                            while (idleMovementTimeSecs() < idleMovementTimeThreshold || idleTimeSecs() < random2(750, 950)) {
                                                Thread.sleep(random2(616, 886));
                                            }
                                            Thread.sleep(random2(640, 827));
                                            return true;
                                        } else {
                                            rightClick(robot);
                                            Thread.sleep(random2(600, 800));
                                            Point action = MenuIndexPosition(MenuIndex(TARGET_OBJECT, Target_text), MouseCanvasPosition());
                                            moveMouse(robot, MouseCanvasPosition(), action, 4, 4);
                                            leftClick(robot);
                                            Thread.sleep(random2(640, 827));
                                            while (idleMovementTimeSecs() < idleMovementTimeThreshold || idleTimeSecs() < random2(750, 950)) {
                                                Thread.sleep(random2(616, 886));
                                            }
                                            Thread.sleep(random2(640, 827));
                                        }
                                        return true;
                                    }
                                }
                            }
                        }
                    }
                }
            }
            return false;
        }

        private boolean Click_Target_Type_2(int TARGET_OBJECT, String Target_text) throws AWTException, InterruptedException {
            Robot robot = new Robot();
            List<GameObject> Targets;
            List<GameObject> Targets0;
            Targets0 = ObjectsFromTiles(TARGET_OBJECT);
            Targets = SortObjectListDistanceFromPlayer(Targets0);

            if (Targets != null) {
                if (Targets.size() != 0) {

                    for (GameObject Target : Targets) {
                        System.out.println(" | check 8 | ");
                        if (Target != null) {
                            System.out.println(" | check 9 | ");
                            if (GameObject_Canvas(Target) != null) {
                                System.out.println(" | check 10 | ");
                                if (PointInsideClickableWindow(GameObject_Canvas(Target))) {
                                    System.out.println(" | check 11 | ");
                                    if (Move_Mouse_GameObject_Type_2(robot, MouseCanvasPosition(), Target) != null) {
                                        if (FirstLeftClickOption(Target_text)) {
                                            leftClick(robot);
                                            Thread.sleep(random2(640, 827));
                                            while (idleMovementTimeSecs() < idleMovementTimeThreshold || idleTimeSecs() < random2(750, 950)) {
                                                Thread.sleep(random2(616, 886));
                                            }
                                            Thread.sleep(random2(640, 827));
                                            return true;
                                        } else {
                                            rightClick(robot);
                                            Thread.sleep(random2(600, 800));
                                            Point action = MenuIndexPosition(MenuIndex(TARGET_OBJECT, Target_text), MouseCanvasPosition());
                                            moveMouse(robot, MouseCanvasPosition(), action, 4, 4);
                                            leftClick(robot);
                                            Thread.sleep(random2(640, 827));
                                            while (idleMovementTimeSecs() < idleMovementTimeThreshold || idleTimeSecs() < random2(750, 950)) {
                                                Thread.sleep(random2(616, 886));
                                            }
                                            Thread.sleep(random2(640, 827));
                                        }
                                        return true;
                                    }
                                }
                            }
                        }
                    }
                }
            }
            return false;
        }

        private boolean Click_Target_Type_3(int TARGET_OBJECT, String Target_text) throws AWTException, InterruptedException {
            Robot robot = new Robot();
            List<GameObject> Targets;
            List<GameObject> Targets0;
            Targets0 = ObjectsFromTiles(TARGET_OBJECT);
            Targets = SortObjectListDistanceFromPlayer(Targets0);

            if (Targets != null) {
                if (Targets.size() != 0) {

                    for (GameObject Target : Targets) {
                        System.out.println(" | check 8 | ");
                        if (Target != null) {
                            System.out.println(" | check 9 | ");
                            if (GameObject_Canvas(Target) != null) {
                                System.out.println(" | check 10 | ");
                                if (PointInsideClickableWindow(GameObject_Canvas(Target))) {
                                    System.out.println(" | check 11 | ");
                                    if (Move_Mouse_GameObject_Type_3(robot, MouseCanvasPosition(), Target) != null) {
                                        if (FirstLeftClickOption(Target_text)) {
                                            leftClick(robot);
                                            Thread.sleep(random2(640, 827));
                                            while (idleMovementTimeSecs() < idleMovementTimeThreshold || idleTimeSecs() < random2(750, 950)) {
                                                Thread.sleep(random2(616, 886));
                                            }
                                            Thread.sleep(random2(640, 827));
                                            return true;
                                        } else {
                                            rightClick(robot);
                                            Thread.sleep(random2(600, 800));
                                            Point action = MenuIndexPosition(MenuIndex(TARGET_OBJECT, Target_text), MouseCanvasPosition());
                                            moveMouse(robot, MouseCanvasPosition(), action, 4, 4);
                                            leftClick(robot);
                                            Thread.sleep(random2(640, 827));
                                            while (idleMovementTimeSecs() < idleMovementTimeThreshold || idleTimeSecs() < random2(750, 950)) {
                                                Thread.sleep(random2(616, 886));
                                            }
                                            Thread.sleep(random2(640, 827));
                                        }
                                        return true;
                                    }
                                }
                            }
                        }
                    }
                }
            }
            return false;
        }

        private boolean Click_Widget(Widget Target_Widget, String Target_text) throws AWTException, InterruptedException {
            Robot robot = new Robot();

            if (Target_Widget != null) {

                if (Widget_Item_Canvas(Target_Widget) != null) {

                    Point adjustedSpotPerspective = Widget_Item_Canvas(Target_Widget);
                    System.out.println(" | check 11 | ");
                    moveMouse(robot, MouseCanvasPosition(), adjustedSpotPerspective, 5, 5);

                    if (FirstLeftClickOption(Target_text)) {
                        leftClick(robot);
                        Thread.sleep(random2(640, 827));
                        while (idleMovementTimeSecs() < idleMovementTimeThreshold || idleTimeSecs() < random2(750, 950)) {
                            Thread.sleep(random2(616, 886));
                        }
                        Thread.sleep(random2(640, 827));
                        return true;
                    } else {
                        rightClick(robot);
                        Thread.sleep(random2(600, 800));
                        Point action = MenuIndexPosition(MenuIndex(Target_text), MouseCanvasPosition());
                        moveMouse(robot, MouseCanvasPosition(), action, 4, 4);
                        leftClick(robot);
                        Thread.sleep(random2(640, 827));
                        while (idleMovementTimeSecs() < idleMovementTimeThreshold || idleTimeSecs() < random2(750, 950)) {
                            Thread.sleep(random2(616, 886));
                        }
                        Thread.sleep(random2(640, 827));
                    }
                    return true;
                }
            }

            return false;
        }

        private boolean Walk_Target(int TARGET_OBJECT) throws AWTException, InterruptedException {
            List<GameObject> Targets;
            List<GameObject> Targets0;
            Targets0 = ObjectsFromTiles(TARGET_OBJECT);
            Targets = SortObjectListDistanceFromPlayer(Targets0);

            if (Targets != null) {
                if (Targets.size() != 0) {

                    for (GameObject Target : Targets) {
                        System.out.println(" | check 8 | ");
                        if (Target != null) {
                            System.out.println(" | check 16 | ");
                            WorldPoint TargetSpot = new WorldPoint(Target.getWorldLocation().getX() + random2(-1, 1),
                                    Target.getWorldLocation().getY() + random2(-1, 1), Target.getPlane());
                            walk(TargetSpot);
                            Thread.sleep(random2(640, 827));
                            while (idleMovementTimeSecs() < idleMovementTimeThreshold) {
                                Thread.sleep(random2(616, 886));
                            }
                            Thread.sleep(random2(640, 827));

                            return true;
                        }
                    }
                }
            }
            return false;
        }

        private int Shop_Item_Quantity(int Item_ID) {

            Widget[] Shop_Inventory_Widget = client.getWidget(net.runelite.api.widgets.WidgetInfo.SHOP_ITEMS).getChildren();
            if (Shop_Inventory_Widget != null) {
                for (Widget Item : Shop_Inventory_Widget) {
                    if (Item.getItemId() == Item_ID) {
                        int Item_Quantity = Item.getItemQuantity();
                        return Item_Quantity;
                    }
                }
            }
            return 0;
        }

        private Point Shop_Item_Point(int Item_ID) {

            Widget[] Shop_Items_Widget = client.getWidget(net.runelite.api.widgets.WidgetInfo.SHOP_ITEMS).getChildren();
            if (Shop_Items_Widget != null) {
                for (Widget Item : Shop_Items_Widget) {
                    if (Item.getItemId() == Item_ID) {
                        Point Item_Canvas = Widget_Item_Canvas(Item);
                        return Item_Canvas;
                    }
                }
            }
            return null;
        }

        private Point Shop_Inventory_Point(int Item_ID) {

            Widget[] Shop_Inventory_Widget = client.getWidget(net.runelite.api.widgets.WidgetInfo.SHOP_INVENTORY_ITEMS_CONTAINER).getChildren();
            if (Shop_Inventory_Widget != null) {
                for (Widget Item : Shop_Inventory_Widget) {
                    if (Item.getItemId() == Item_ID) {
                        Point Item_Canvas = Widget_Item_Canvas(Item);
                        return Item_Canvas;
                    }
                }
            }
            return null;
        }

        private NPC Open_shop() throws InterruptedException, AWTException {

            while (!checkMovementIdle()) {
                Thread.sleep(1063);
            }

            List<NPC> LocalTarget;
            //List<GameObject> LocalTarget1;

            LocalTarget = GetNPC(JIMINUA);
            //LocalTarget1 = ObjectsFromTiles(BankBooth2);

            //LocalTarget.addAll(LocalTarget1);

            if (LocalTarget.size() != 0) {
                NPC shop = LocalTarget.get(random2(0, LocalTarget.size() - 1));
                Point Shop_Canvas = NPC_Canvas(shop);
                return shop;
            }
            return null;
        }

        private void Shop() throws AWTException, InterruptedException {

            Widget Shop_Widget = client.getWidget(net.runelite.api.widgets.WidgetInfo.SHOP_ITEMS_CONTAINER); // [1]

            while (!checkMovementIdle()) {
                Thread.sleep(random2(422, 689));
            }

            Robot robot = new Robot();

            if (Shop_Widget == null) {
                if (Open_shop() != null) {
                    NPC shop = Open_shop();
                    if (PointInsideClickableWindow(NPC_Canvas(shop))) {
                        while (idleMovementTimeSecs() < random2(615, 784)) {
                            Thread.sleep(random2(422, 689));
                        }
                        moveMouse(robot, MouseCanvasPosition(), shop);

                        Thread.sleep(random2(0, 1));

                        if (FirstLeftClickOption(Shop_text)) {
                            leftClick(robot);
                            Thread.sleep(random2(612, 813));
                            while (!checkMovementIdle()) {
                                Thread.sleep(604);
                            }

                            Thread.sleep(random2(2412, 2913));

                        } else {
                            rightClick(robot);

                            Thread.sleep(random2(400, 600));

                            Point TargetAction = MenuIndexPosition(MenuIndex(Shop_text), MouseCanvasPosition());
                            moveMouse(robot, MouseCanvasPosition(), TargetAction, 4, 4);
                            leftClick(robot);
                            while (!checkMovementIdle()) {
                                Thread.sleep(604);
                            }

                            Thread.sleep(random2(2112, 2713));

                        }
                        return;
                    }
                }
            }

            if (Shop_Widget != null) {

                if (NumberofItemInInventory(PURE_ESSENCE) != 24 && NumberofStackedItemInInventory(COINS) > 2000
                        && NumberofStackedItemInInventory(NOTED_PURE_ESSENCE) > 50 && availableInventory() > 0) {

                    Thread.sleep(random2(50, 80));

                    if (Shop_Item_Quantity(PURE_ESSENCE) < 25) {

                        Point Shop_Inventory_Item_location = Shop_Inventory_Point(NOTED_PURE_ESSENCE);

                        moveMouse(robot, MouseCanvasPosition(), Shop_Inventory_Item_location, 4, 4);

                        Thread.sleep(random2(600, 800));

                        rightClick(robot);

                        Thread.sleep(random2(600, 800));

                        MenuIndexPosition(4, Shop_Inventory_Item_location);

                        Point Sell_10 = MenuIndexPosition(4, Shop_Inventory_Item_location);

                        moveMouse(robot, Shop_Inventory_Item_location, Sell_10, 4, 4);

                        leftClick(robot);

                        Thread.sleep(random2(600, 800));

                        return;

                    }

                    if (Shop_Item_Quantity(PURE_ESSENCE) >= 25) {

                        Point Shop_Item_location = Shop_Item_Point(PURE_ESSENCE);

                        moveMouse(robot, MouseCanvasPosition(), Shop_Item_location, 4, 4);

                        Thread.sleep(random2(600, 800));

                        rightClick(robot);

                        Thread.sleep(random2(600, 800));

                        Point Buy_50 = MenuIndexPosition(5, MouseCanvasPosition());

                        moveMouse(robot, MouseCanvasPosition(), Buy_50, 4, 4);

                        leftClick(robot);

                        Thread.sleep(random2(600, 800));

                    }

                }

            }

        }

        private List<GameObject> List_of_Banks() throws InterruptedException, AWTException {

            while (!checkMovementIdle()) {
                Thread.sleep(1063);
            }

            List<GameObject> LocalTarget;
            List<GameObject> LocalTarget1;

            LocalTarget = ObjectsFromTiles(Target_Bank_1);
            LocalTarget1 = ObjectsFromTiles(Target_Bank_2);

            LocalTarget.addAll(LocalTarget1);

            if (LocalTarget.size() != 0) {
                //	System.out.print( " List_of_Banks(size) " + LocalTarget.size()  );
                return LocalTarget;
            }
            return null;
        }

        private List<GameObject> open_Bank(List<GameObject> LocalTargets) throws InterruptedException {

            while (!checkMovementIdle()) {
                Thread.sleep(1063);
            }
            List<GameObject> banks = new ArrayList<>();
            if (LocalTargets.size() != 0) {
                for (GameObject bank : LocalTargets)
                    if (PointInsideClickableWindow(GameObject_Canvas(bank))) {
                        banks.add(bank);
                    }
            }
            if (banks.size() != 0) {
                return banks;
            }
            return null;
        }

        private void bankInvent() throws AWTException, InterruptedException {
            while (!checkMovementIdle()) {
                Thread.sleep(random2(422, 689));
            }

            Robot robot = new Robot();

            if (client.getWidget(net.runelite.api.widgets.WidgetInfo.BANK_ITEM_CONTAINER) == null) {
                if (List_of_Banks() != null) {
                    if (open_Bank(List_of_Banks()) != null) {
                        List<GameObject> banks_0 = open_Bank(List_of_Banks());
                        if (SortObjectListDistanceFromPlayer(banks_0)!= null){
                            List<GameObject> banks_1 = SortObjectListDistanceFromPlayer(banks_0);
                            for (GameObject bank : banks_1) {
                                System.out.print(" banks_1 " + bank.getId() + " " + bank.getWorldLocation() + " " + bank.toString());
                            }
                            GameObject bank = banks_1.get(random2(0,(banks_1.size()-1)));
                            while (idleMovementTimeSecs() < random2(615, 784)) {
                                Thread.sleep(random2(422, 689));
                            }

                            Move_Mouse_GameObject(robot, MouseCanvasPosition(), bank);

                            if (FirstLeftClickOption(Target_Bank_Text)) {
                                leftClick(robot);
                                Thread.sleep(random2(612, 813));
                                while (!checkMovementIdle()) {
                                    Thread.sleep(604);
                                }

                                Thread.sleep(random2(2412, 2913));

                            } else {

                                rightClick(robot);

                                Thread.sleep(random2(600, 800));

                                Point TargetAction = MenuIndexPosition(MenuIndex(Target_Bank_Text), MouseCanvasPosition());
                                moveMouse(robot, MouseCanvasPosition(), TargetAction, 4, 4);
                                leftClick(robot);
                                while (!checkMovementIdle()) {
                                    Thread.sleep(604);
                                }

                                Thread.sleep(random2(1112, 1713));
                            }
                        }
                    }
                }
            }

            if (client.getWidget(net.runelite.api.widgets.WidgetInfo.BANK_ITEM_CONTAINER) != null) {

                if (availableInventory() < 27 && ItemInInventory(TOOL_WEAPON)) {

                    Point depositInventoryPoint1 = depositInventoryPoint();
                    Thread.sleep(random2(500, 600));
                    moveMouse(robot, MouseCanvasPosition(), depositInventoryPoint1, 4, 4);
                    leftClick(robot);
                    Thread.sleep(random2(600, 750));

                }

                if (!ItemInInventory(TOOL_WEAPON) && availableInventory() < 28) {

                    Point depositInventoryPoint1 = depositInventoryPoint();
                    Thread.sleep(random2(500, 600));
                    moveMouse(robot, MouseCanvasPosition(), depositInventoryPoint1, 4, 4);
                    leftClick(robot);
                    Thread.sleep(random2(600, 750));

                }

                if (getBankItemIndex(TOOL_WEAPON) != 0 && !ItemInInventory(TOOL_WEAPON)) {

                    Thread.sleep(random2(50, 80));
                    Point Banklocation = BankLocation(getBankItemIndex(TOOL_WEAPON));
                    moveMouse(robot, MouseCanvasPosition(), Banklocation, 4, 4);
                    Thread.sleep(random2(650, 800));
                    leftClick(robot);

                }

            }
        }

        double idleMovementTimeThreshold;

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
                                + "  foodInventory() " + foodInventory()
                                //+  " FirstLeftClickOption walk " + FirstLeftClickOption()
                                //+ " NumberofItemInInventory(Feathers " + NumberofItemInInventory(GOLD_BAR)
                                + " random2 01 " + random2(1, 2)
                                + " MouseInfo " + MouseInfo.getPointerInfo().getLocation()
                                + " Mouse_Idle_Ticks " + Mouse_Idle_Ticks()
                        );

                        if (client.isMenuOpen()) {

                            System.out.print(" MenuIndex(talk-to) " + MenuIndex("Talk-to"));
                            System.out.print(" MenuIndexPosition(Walk here) " + MenuIndex("Walk here"));

                            MenuEntry[] Shop = client.getMenuEntries();
                            for (MenuEntry Shop1 : Shop) {
                                if (Shop1 != null) {
                                    System.out.print("shop items " + Shop1.toString());
                                }
                            }
                        }

                        if (marksOfGrace != null) {
                            System.out.println(" marksOfGrace " + marksOfGrace.size());
                        }

                        if (Item_Tile_from_tiles_1(TOOL_WEAPON) != null) {
                            System.out.print(" | " + Item_Tile_from_tiles_1(TOOL_WEAPON).toString());
                        }

                        try {
                            if (Mouse_Inside_Bounds()) {
                                System.out.println(" Mouse_Inside_Bounds ");
                                try {
                                    addon();
                                } catch (AWTException | InterruptedException e) {
                                    e.printStackTrace();
                                }
                            }
                        } catch (AWTException | InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                    System.out.println(" | " + i + " | ");
                    try {
                        Thread.sleep(random2(1298, 1898));
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        });

        boolean Poisoined;
        private int GE_Price_Threshold = 2000;
        int Canvas_Offset_Y = 23;

        private static final Set<Integer> FOOD_IDS = ImmutableSet.of(ItemID.LOBSTER, ItemID.SALMON, ItemID.SWORDFISH, ItemID.MONKFISH);
        private int HPThreshold = 32;
        private static final Set<Integer> TELE_IDS = ImmutableSet.of(ItemID.VARROCK_TELEPORT);

        private int ANTIDOTE4_5952 = ItemID.ANTIDOTE4_5952;
        private int ANTIDOTE3_5954 = ItemID.ANTIDOTE3_5954;
        private int ANTIDOTE2_5956 = ItemID.ANTIDOTE2_5956;
        private int ANTIDOTE1_5958 = ItemID.ANTIDOTE1_5958;
        private Set<Integer> ANTIDOTE = ImmutableSet.of(ANTIDOTE4_5952, ANTIDOTE3_5954, ANTIDOTE2_5956, ANTIDOTE1_5958);

        private int VARROCK_TELEPORT = ItemID.VARROCK_TELEPORT;
        private int RUNE_PICKAXE = ItemID.RUNE_PICKAXE;
        private int STAFF_OF_FIRE = ItemID.STAFF_OF_FIRE;
        private int RUNE_SCIMITAR = ItemID.RUNE_SCIMITAR;
        private int BRINE_SABRE = ItemID.BRINE_SABRE;
        private int RUNE_WARHAMMER = ItemID.RUNE_WARHAMMER;
        private int MITH_CROSSBOW = ItemID.MITH_CROSSBOW;
        private int MITHRIL_BOLTS = ItemID.MITHRIL_BOLTS;
        private int MIRROR_SHIELD = ItemID.MIRROR_SHIELD;

        private int PURE_ESSENCE = ItemID.PURE_ESSENCE;
        private int NOTED_PURE_ESSENCE = ItemID.NOTED_PURE_ESSENCE;
        private int COINS = ItemID.COINS_995;
        int JIMINUA = NpcID.JIMINUA; // shop
        private String Shop_text = "Trade";
        private String Use_Bank_Chest = "Use";

        int MARK_OF_GRACE = ItemID.MARK_OF_GRACE;

        String Bank_Text = "Bank";
        String Attack = "Attack";
        String Pickpocket = "Pickpocket";
        String Mine = "Mine";
        String Dismantle = "Dismantle";
        String Check = "Check";

        private Rectangle Area_1_To_Altar = new Rectangle(2742, 3074, 73, 64);
        private Rectangle Area_2_To_Altar = new Rectangle(2786, 3003, 108, 72);
        private Rectangle Area_3_To_Altar = new Rectangle(2380, 4825, 2423 + 1 - 2380, 4865 + 1 - 4825);

        private Rectangle Area_1_To_Shop = Area_3_To_Altar;
        private Rectangle Area_2_To_Shop = new Rectangle(2819, 3003, 2883 + 1 - 2819, 3070 + 1 - 3003);
        private Rectangle Area_3_To_Shop = new Rectangle(2746, 3069, 2819 + 1 - 2746, 3132 + 1 - 3069);

        private WorldPoint JIMINUA_WL = new WorldPoint(2767, 3120, 0);
        private WorldPoint Inbetween_Shilo = new WorldPoint(2817, 3072, 0);
        private WorldPoint Nature_Ruins_WL = new WorldPoint(2868, 3020, 0);
        private int Nature_Ruins = ObjectID.MYSTERIOUS_RUINS_34821;
        private String Ruins_Text = "Enter";
        private int Nature_Portal = ObjectID.PORTAL_34756;
        private WorldPoint Nature_Portal_WL = new WorldPoint(2400, 4835, 0);
        private String Portal_Text = "Use";
        private WorldPoint Nature_Altar_WL = new WorldPoint(2400, 4839, 0);
        private int Nature_Altar = ObjectID.ALTAR_34768;
        private String Altar_Text = "Craft-rune";

        private int Catherby_Bank_ID_1 = ObjectID.BANK_BOOTH_10355;
        private int Catherby_Bank_ID_2 = ObjectID.BANK_BOOTH_10356;
        private int Yanille_Bank_ID_1 = ObjectID.BANK_BOOTH_10356;
        private int Yanille_Bank_ID_2 = ObjectID.BANK_BOOTH_10355;
        private int AlKharid_Bank_ID = ObjectID.BANK_BOOTH_10355;
        private int CW_Bank_Chest_ID = ObjectID.BANK_CHEST_4483;
        private int Draynor_Bank_ID = ObjectID.BANK_BOOTH_10355;
        private int Zanaris_Bank_ID = ObjectID.BANK_CHEST_26711;
        private int Varrock_East_Bank_ID = ObjectID.BANK_BOOTH_10583;

        private Rectangle MASTER_FARMER_Area = new Rectangle(3072, 3245, 3088 + 1 - 3072, 3259 + 1 - 3245);

        private WorldPoint AlKharid_Range_WL = new WorldPoint(3273, 3180, 0);
        private int Range_ID = ObjectID.RANGE_26181;
        String Cook = "Cook";
        private int RAW_SALMON = ItemID.RAW_SALMON;
        private int RAW_TROUT = ItemID.RAW_TROUT;

        private WorldPoint Catherby_Bank_WL = new WorldPoint(2810, 3441, 0);
        private WorldPoint AlKharid_Bank_WL = new WorldPoint(3270, 3168, 0);
        private WorldPoint Zanaris_Bank_Chests_WL = new WorldPoint(2382, 4458, 0);
        private WorldPoint Yanille_BANK_WL = new WorldPoint(2612, 3094, 0);
        private WorldPoint CW_BANK_WL = new WorldPoint(2443, 3083, 0);
        private Rectangle Yanille_BANK_AREA = new Rectangle(2610, 3091, 2613 + 1 - 2610, 3095 + 1 - 3091);
        private Rectangle CW_BANK_AREA = new Rectangle(2440, 3082, 5, 4);
        private Rectangle Draynor_BANK_AREA = new Rectangle(3092, 3242, 4, 4);
        private WorldPoint MASTER_FARMER_WL = new WorldPoint(3080, 3249 + 1, 0);
        private WorldPoint MASTER_FARMER_WL_2 = new WorldPoint(3078, 3256, 0);
        private WorldPoint Draynor_Bank_WL = new WorldPoint(3092, 3243, 0);

        private static final List<Integer> WILLOW_TREE_IDS = ImmutableList.of(
                ObjectID.WILLOW,
                ObjectID.WILLOW_10829,
                ObjectID.WILLOW_10831,
                ObjectID.WILLOW_10833);
        private WorldPoint Willow_Trees_WL = new WorldPoint(3087, 3236, 0); //3234
        private int RUNE_AXE = ItemID.RUNE_AXE;
        String Chop_down = "Chop down";
        private int SALMON = ItemID.SALMON;
        private int MONKFISH = ItemID.MONKFISH;
        private int TARGET_FOOD = MONKFISH;
        private int COMPOST = ItemID.COMPOST;

        private int KNIFE = ItemID.KNIFE;
        private int LOGS = ItemID.LOGS;
        private int OAK_LOGS = ItemID.OAK_LOGS;
        private int WILLOW_LOGS = ItemID.WILLOW_LOGS;
        private int MAPLE_LOGS = ItemID.MAPLE_LOGS;
        private int YEW_LOGS = ItemID.YEW_LOGS;
        private int TARGET_LOGS = YEW_LOGS;

        private WorldPoint FRUIT_STALL_Thieving_Spot_0 = new WorldPoint(2669, 3310, 0);
        private WorldPoint ThievingSpot0 = FRUIT_STALL_Thieving_Spot_0;
        private Rectangle FRUIT_STALL_Area = new Rectangle(1795, 3606, 1801 + 1 - 1795, 3611 + 1 - 3606);
        private Rectangle ThievingArea = FRUIT_STALL_Area;
        private int FRUIT_STALL = ObjectID.FRUIT_STALL_28823;
        private int Target_Stall = FRUIT_STALL;

        private WorldPoint Yanille_Iron_Spot_0 = new WorldPoint(2627, 3141 - 1, 0);
        private Rectangle Yanille_Iron_Area = new Rectangle(2625, 3138, 2630 + 1 - 2625, 3143 + 1 - 3138);
        private int IRON_ROCKS = ObjectID.ROCKS_11365;

        private WorldPoint Catherby_Fishing_Spot_1 = new WorldPoint(2849, 3430, 0);
        private WorldPoint Catherby_Fishing_Spot_2 = new WorldPoint(2845, 3430, 0);
        private WorldPoint Catherby_Fishing_Spot_3 = new WorldPoint(2855, 3424, 0);
        private Rectangle Catherby_Fishing_Spot_Area = new Rectangle(2830, 3423, 2862 + 1 - 2830, 3432 + 1 - 3423);
        String Cage = "Cage";
        int LOBSTER_SPOT = NpcID.FISHING_SPOT_1519;
        private int LOBSTER_POT = ItemID.LOBSTER_POT;

        private WorldPoint TROPICAL_WIGTAIL_Spot_0 = new WorldPoint(2525, 2937, 0);
        private Rectangle TROPICAL_WIGTAIL_Area = new Rectangle(2522, 2931, 2528 + 1 - 2522, 2940 + 1 - 2934);
        private WorldPoint Copper_Tail_Spot_0 = new WorldPoint(2360, 3589, 0);
        private Rectangle Copper_Tail_Area = new Rectangle(2358, 3586, 2362 + 1 - 2358, 3592 + 1 - 3586);
        private int BIRD_SNARE = ItemID.BIRD_SNARE;
        private int PLACED_SNARE = ObjectID.BIRD_SNARE_9345;
        private int FALLEN_SNARE = ObjectID.BIRD_SNARE;
        private int SNARE_WITH_BIRD_COPPER = ObjectID.BIRD_SNARE_9379;
        private int SNARE_WITH_BIRD_TROPICAL = ObjectID.BIRD_SNARE_9348;

        String Set_trap = "Set-trap";
        private int YOUNG_TREE_9341 = ObjectID.YOUNG_TREE_9341;
        //target tree for trap
        private int NET_TRAP_9343 = ObjectID.NET_TRAP_9343;
        //LAID trap
        private int NET_TRAP_9004 = ObjectID.NET_TRAP_9004;
        //succesful trap
        private int SMALL_FISHING_NET = ItemID.SMALL_FISHING_NET;
        private int ROPE = ItemID.ROPE;
        private WorldPoint GREEN_SALAMANDER_SPOT = new WorldPoint(3537, 3448, 0);
        private Rectangle GREEN_SALAMANDER_Area = new Rectangle(3531, 3445, 3540 + 1 - 3531, 3453 + 1 - 3445);

        int ICE_WARRIOR = NpcID.ICE_WARRIOR;
        int HILL_GIANT = NpcID.HILL_GIANT;
        int HILL_GIANT_2099 = NpcID.HILL_GIANT_2099;
        int HILL_GIANT_2100 = NpcID.HILL_GIANT_2100;
        int HILL_GIANT_2101 = NpcID.HILL_GIANT_2101;
        int HILL_GIANT_2102 = NpcID.HILL_GIANT_2102;
        int COCKATRICE_419 = NpcID.COCKATRICE_419;
        int KALPHITE_WORKER = NpcID.KALPHITE_WORKER;
        int OGRE_2095 = NpcID.OGRE_2095;
        int MOSS_GIANT = NpcID.MOSS_GIANT;
        int MOSS_GIANT_2091 = NpcID.MOSS_GIANT_2091;
        int MOSS_GIANT_2092 = NpcID.MOSS_GIANT_2092;
        int MOSS_GIANT_2093 = NpcID.MOSS_GIANT_2093;
        int OTHERWORLDLY_BEING = NpcID.OTHERWORLDLY_BEING;
        int CAVE_CRAWLER = NpcID.CAVE_CRAWLER;
        int CAVE_CRAWLER_407 = NpcID.CAVE_CRAWLER_407;
        int CAVE_CRAWLER_408 = NpcID.CAVE_CRAWLER_408;
        int CAVE_CRAWLER_409 = NpcID.CAVE_CRAWLER_409;

        private Rectangle HILL_GIANT_Area = new Rectangle(3098, 9822, 3126 + 1 - 3098, 9852 + 1 - 9822);
        private Rectangle COCKATRICE_Area = new Rectangle(2780, 10028, 2806 + 1 - 2780, 10042 + 1 - 10028);
        private Rectangle KALPHITE_WORKER_Area = new Rectangle(3490, 9512, 3514 + 1 - 3490, 9530 + 1 - 9512);
        private Rectangle OGRE_Area = new Rectangle(2577, 9730, 2595 + 1 - 2577, 9750 + 1 - 9730);
        private Rectangle MOSS_GIANT_Area = new Rectangle(2689, 3200, 2706 + 1 - 2689, 3219 + 1 - 3200);
        private Rectangle CAVE_CRAWLER_Area = new Rectangle(2775, 9989, 2808 + 1 - 2775, 10007 + 1 - 9989);
        private Rectangle OtherWordly_Area = new Rectangle(2374, 4414, 2392 + 1 - 2374, 4432 + 1 - 4414);
        private Rectangle ICE_WARRIOR_Area_SafeSpot = new Rectangle(3059, 9583, 3060 + 1 - 3059, 9584 + 1 - 9583);
        private Rectangle ICE_WARRIOR_Area = new Rectangle(3050, 9574, 3066 + 1 - 3050, 9587 + 1 - 9574);

        private WorldPoint ICE_WARRIOR_WL = new WorldPoint(3055, 9579, 0);
        private WorldPoint HILL_GIANT_WL = new WorldPoint(3116, 9840, 0);
        private WorldPoint COCKATRICE_WL = new WorldPoint(2792, 10035, 0);
        private WorldPoint KALPHITE_WORKER_WL = new WorldPoint(3502, 9522, 2);
        private WorldPoint OGRE_Area_WL = new WorldPoint(2585, 9737, 0);
        private WorldPoint MOSS_GIANT_Area_WL = new WorldPoint(2696, 3208, 0);
        private WorldPoint CAVE_CRAWLER_Area_WL = new WorldPoint(2788, 9996, 0);
        private WorldPoint OtherWordly_Area_WL = new WorldPoint(2384, 4425, 0);
        private WorldPoint Bank_Chests_WL = new WorldPoint(2381, 4458, 0);
        private WorldPoint west_varrock_bank = new WorldPoint(3185, 3436, 0);
        private WorldPoint Anvil_WL = new WorldPoint(3188, 3425, 0);
        private Rectangle Anvil_Area = new Rectangle(3185, 3420, 3189 + 1 - 3185, 3428 + 1 - 3420);
        // west varrock

        private int HAMMER = ItemID.HAMMER;
        private int IRON_BAR = ItemID.IRON_BAR;
        String Smith = "Smith";
        private int West_Varrock_Bank_Booth = ObjectID.BANK_BOOTH_34810;
        private int ANVIL_ID = ObjectID.ANVIL_2097;

        private int RIMMINGTON_HOUSE_PORTAL = ObjectID.PORTAL_15478;
        String Portal_text = "Enter";
        private WorldPoint RIMMINGTON_HOUSE_PORTAL_WL = new WorldPoint(2953, 3224, 0);
        // Enter Portal widget 219.1 child 2 "Go to your house (building mode)" // option 2
        private int CHAIR_SPACE = ObjectID.CHAIR_SPACE;
        String Build_Chair_text = "Build";
        // widget N 458 build chair widget // option 1 chair
        private int PLANK = ItemID.PLANK;
        private int OAK_PLANK = ItemID.OAK_PLANK;
        private int STEEL_NAILS = ItemID.STEEL_NAILS;
        private int SAW = ItemID.SAW;
        private int LAW_RUNE = ItemID.LAW_RUNE;
        private int CHAOS_RUNE = ItemID.CHAOS_RUNE;
        private int DUST_BATTLESTAFF = ItemID.DUST_BATTLESTAFF;
        // f6 mage tab
        // f4 equip tab
        private int RING_OF_DUELING1 = ItemID.RING_OF_DUELING1;
        private int RING_OF_DUELING2 = ItemID.RING_OF_DUELING2;
        private int RING_OF_DUELING3 = ItemID.RING_OF_DUELING3;
        private int RING_OF_DUELING4 = ItemID.RING_OF_DUELING4;
        private int RING_OF_DUELING5 = ItemID.RING_OF_DUELING5;
        private int RING_OF_DUELING6 = ItemID.RING_OF_DUELING6;
        private int RING_OF_DUELING7 = ItemID.RING_OF_DUELING7;
        private int RING_OF_DUELING8 = ItemID.RING_OF_DUELING8;
        // Widget Equipment Ring , child [1], 2nd one item id
        String Castle_Wars_Option = "Castle Wars"; // 3rd option
        private int BUILT_CHAIR = ObjectID.CHAIR_6752;
        private int BUILT_OAK_CHAIR = ObjectID.CHAIR_6755;
        private int BUILT_OAK_CHAIR_2 = ObjectID.CHAIR_6756;
        String Remove = "Remove"; // press 1
        private Rectangle CW_TP_AREA = new Rectangle(2438, 3084, 2444 + 1 - 2438, 3094 + 1 - 3084);
        //Widget mage book teleport to house 218.28
        String House_teleport_text = "Outside";

        private Rectangle Varrock_East_Bank_Area = new Rectangle(3250, 3420, 6, 4);
        private WorldPoint Varrock_East_Bank_WL = new WorldPoint(3253, 3420, 0);
        private WorldPoint No_Fire_Door_Tile_WL = new WorldPoint(3253, 3430, 0);
        private WorldPoint Start_Fire_WL = new WorldPoint(3269, 3429, 0);
        private int Ground_Fire_ID = ObjectID.FIRE_26185;
        private int DOOR_11775 = ObjectID.DOOR_11775;
        private Rectangle Fire_Area = new Rectangle(3238, 3428, 3273 + 1 - 3238, 3430 + 1 - 3428);
        private int TINDERBOX = ItemID.TINDERBOX;

        private Rectangle Canifis_Agility_Pre_Obstacle_1_Area = new Rectangle(3505, 3485, 3509 + 1 - 3505, 3490 + 1 - 3485);
        private int Canifis_Obstacle_1_ID = ObjectID.TALL_TREE_14843;
        private String Canifis_Obstacle_1_text = "Climb";
        private WorldPoint Canifis_Obstacle_1_WL = new WorldPoint(3508, 3489, 0);

        private Rectangle Canifis_Agility_Pre_Obstacle_2_Area = new Rectangle(3504, 3491, 3512 + 1 - 3504, 3499 + 1 - 3491);
        private int Canifis_Obstacle_2_ID = ObjectID.GAP_14844;
        private String Canifis_Obstacle_2_text = "Jump";
        private WorldPoint Canifis_Obstacle_2_WL = new WorldPoint(3505, 3498, 2);

        private Rectangle Canifis_Agility_Pre_Obstacle_3_Area = new Rectangle(3495, 3503, 3504 + 1 - 3495, 3507 + 1 - 3503);
        private int Canifis_Obstacle_3_ID = ObjectID.GAP_14845;
        private String Canifis_Obstacle_3_text = "Jump";
        private WorldPoint Canifis_Obstacle_3_WL = new WorldPoint(3497, 3504, 2);

        private Rectangle Canifis_Agility_Pre_Obstacle_4_Area = new Rectangle(3484, 3498, 3493 + 1 - 3484, 3505 + 1 - 3498);
        private int Canifis_Obstacle_4_ID = ObjectID.GAP_14848;
        private String Canifis_Obstacle_4_text = "Jump";
        private WorldPoint Canifis_Obstacle_4_WL = new WorldPoint(3485, 3499, 2);

        private Rectangle Canifis_Agility_Pre_Obstacle_5_Area = new Rectangle(3474, 3491, 3480 + 1 - 3474, 3500 + 1 - 3491);
        private int Canifis_Obstacle_5_ID = ObjectID.GAP_14846;
        private String Canifis_Obstacle_5_text = "Jump";
        private WorldPoint Canifis_Obstacle_5_WL = new WorldPoint(3478, 3492, 3);

        private Rectangle Canifis_Agility_Pre_Obstacle_6_Area = new Rectangle(3477, 3481, 3484 + 1 - 3477, 3487 + 1 - 3481);
        private int Canifis_Obstacle_6_ID = ObjectID.POLEVAULT;
        private String Canifis_Obstacle_6_text = "Vault";
        private WorldPoint Canifis_Obstacle_6_WL = new WorldPoint(3480, 3483, 2);

        private Rectangle Canifis_Agility_Pre_Obstacle_7_Area = new Rectangle(3488, 3468, 3504 + 1 - 3488, 3479 + 1 - 3468);
        private int Canifis_Obstacle_7_ID = ObjectID.GAP_14847;
        private String Canifis_Obstacle_7_text = "Jump";
        private WorldPoint Canifis_Obstacle_7_WL = new WorldPoint(3503, 3476, 3);

        private Rectangle Canifis_Agility_Pre_Obstacle_8_Area = new Rectangle(3508, 3474, 3517 + 1 - 3508, 3484 + 1 - 3474);
        private int Canifis_Obstacle_8_ID = ObjectID.GAP_14897;
        private String Canifis_Obstacle_8_text = "Jump";
        private WorldPoint Canifis_Obstacle_8_WL = new WorldPoint(3510, 3483, 2);

        private int TOOL_WEAPON = LOBSTER_POT;
        private int TOOL_WEAPON_2 = MIRROR_SHIELD;
        private int AMMUNITION = CHAOS_RUNE;
        private int TARGET_OBJECT_1 = RIMMINGTON_HOUSE_PORTAL;
        private WorldPoint TARGET_1_WL = Catherby_Fishing_Spot_1;
        private int TARGET_OBJECT_2 = CHAIR_SPACE;
        private int TARGET_OBJECT_3 = BUILT_OAK_CHAIR_2;
        private final List<Integer> RING_IDS = ImmutableList.of(RING_OF_DUELING1, RING_OF_DUELING2, RING_OF_DUELING3, RING_OF_DUELING4,
                RING_OF_DUELING5, RING_OF_DUELING6, RING_OF_DUELING7, RING_OF_DUELING8);
        private final List<Integer> DONT_DROP_IDS = RING_IDS;
        String Target_text_1 = Cage;
        String Target_text_2 = Build_Chair_text;
        String Target_text_3 = Remove;
        //   Rectangle TargetArea = Anvil_Area;
        private int TARGET_PRIMARY = MAPLE_LOGS;
        //   private int TARGET_SECONDARY = STEEL_NAILS;
        // private WorldPoint Target_WL = Anvil_WL;
        //   private Rectangle Target_Area_1 = Anvil_Area;
        private WorldPoint Target_Bank_WL = Catherby_Bank_WL;
        private int Target_Bank_1 = Catherby_Bank_ID_1;
        private int Target_Bank_2 = Catherby_Bank_ID_2;
        String Target_Bank_Text = Bank_Text;
        int TARGET_1 = LOBSTER_SPOT;
        int TARGET_2 = HILL_GIANT_2099;
        int TARGET_3 = HILL_GIANT_2100;
        int TARGET_4 = HILL_GIANT_2101;
        int TARGET_5 = HILL_GIANT_2102;
        private final List<Integer> TARGET_IDS = ImmutableList.of(TARGET_1);
        private WorldPoint Slayer_WL = OGRE_Area_WL;
        private Rectangle Slayer_Area = OGRE_Area;
        private Rectangle Slayer_Area_2 = ICE_WARRIOR_Area;
        private Rectangle Safe_Spot_Area = ICE_WARRIOR_Area_SafeSpot;

        //--debug --developer-mode

        private void addon() throws AWTException, InterruptedException {
            System.out.println(" || Addon Check 0 || ");
            int HP = client.getBoostedSkillLevel(Skill.HITPOINTS);
            while (foodInventory() > 0 && HP < HPThreshold) {
                eatFood();
                Thread.sleep(random2(450, 610));
                HP = client.getBoostedSkillLevel(Skill.HITPOINTS);
            }

            if (randomNPCCheck() != null) {
                dismissRandom();
            }

            Robot robot = new Robot();

            double ii = i;
            double breakCheck = ii / 83;
            if (breakCheck == (int) breakCheck) {
                System.out.println(" || SLEEPING || ");
                {
                    Thread.sleep(random2(7000, 18000));
                }
            }

            double breakCheck2 = ii / 27;
            if (breakCheck2 == (int) breakCheck2) {
                System.out.println(" | BRIEF BREAK | ");
                {
                    Thread.sleep(random2(2000, 7000));
                }
            }
            System.out.println(" || Addon Check 1 || ");
            double idleTimeThreshold = random2(952, 1267);
            idleMovementTimeThreshold = random2(614, 867);
            double Mouse_Idle_Ticks_Threshold = random2(600, 700);
            //System.out.println(" | check 2 | ");

            while (idleTimeSecs() < idleTimeThreshold || Mouse_Idle_Ticks() < Mouse_Idle_Ticks_Threshold) {
                HP = client.getBoostedSkillLevel(Skill.HITPOINTS);
                if (HP < HPThreshold) {
                    return;
                }
                int randomInt = random2(1, 100);
                if (randomInt == 5) {
                    RandomMouseMove();
                }
                Thread.sleep(random2(618, 820));
                if (randomNPCCheck() != null) {
                    return;
                }
                if (Valuable_Item_Tile_from_tiles().size() != 0 && availableInventory() != 0) {
                    break;
                }
                if (client.getWidget(net.runelite.api.widgets.WidgetInfo.LEVEL_UP) != null) {
                    break;
                }
            }
            System.out.println(" || Addon Check 2 || ");
            ActivateRun();

            if (client.getWidget(net.runelite.api.widgets.WidgetInfo.LEVEL_UP) != null) {

                Thread.sleep(random2(650, 680));

                robot.keyPress(KeyEvent.VK_SPACE);

                Thread.sleep(random2(1522, 2641));

                robot.keyPress(KeyEvent.VK_SPACE);

                Thread.sleep(random2(1100, 1610));

            }

            Poisoined = false;
            int Poison_var = client.getVar(VarPlayer.IS_POISONED);
            if (Poison_var > 0) {
                Poisoined = true;
            }

            if (Poisoined) {
                if (InvIndex(ANTIDOTE1_5958) != 0) {
                    Click_Item_Inv(ANTIDOTE1_5958);
                    Poisoined = false;
                }
                Thread.sleep(random2(418, 620));
            }
            if (Poisoined) {
                if (InvIndex(ANTIDOTE2_5956) != 0) {
                    Click_Item_Inv(ANTIDOTE2_5956);
                    Poisoined = false;
                }
                Thread.sleep(random2(418, 620));
            }
            if (Poisoined) {
                if (InvIndex(ANTIDOTE3_5954) != 0) {
                    Click_Item_Inv(ANTIDOTE3_5954);
                    Poisoined = false;
                }
                Thread.sleep(random2(418, 620));
            }
            if (Poisoined) {
                if (InvIndex(ANTIDOTE4_5952) != 0) {
                    Click_Item_Inv(ANTIDOTE4_5952);
                    Poisoined = false;
                }
                Thread.sleep(random2(418, 620));
            }

            if (Valuable_Item_Tile_from_tiles().size() != 0 && availableInventory() != 0) {
                System.out.println(" || GE value Check 1 || ");
                List<net.runelite.client.plugins.woodcutting.WoodcuttingPlugin.Valuable_Item_Tile> LocalTargets0 = Valuable_Item_Tile_from_tiles();
                net.runelite.client.plugins.woodcutting.WoodcuttingPlugin.Valuable_Item_Tile Target_Item_Tile = LocalTargets0.get(0);
                Tile Target_Tile = Target_Item_Tile.getTile();
                int Target_ItemID = Target_Item_Tile.getId();
                Click_Walk_Tile_Item(Target_Tile, Target_ItemID);
                return;
            }

            System.out.println(" || Addon Check 3 || ");
            WorldPoint TargetSpot0;
            Rectangle TargetArea;
            int Target_Game_Object;
            String Target_Text;

            if (availableInventory() > 0 && ItemInInventory(TOOL_WEAPON)) {
                //&& ItemInInventory(ANTIDOTE)
                System.out.println(" | check 3.1 | ");

                TargetArea = Catherby_Fishing_Spot_Area;
                TargetSpot0 = Catherby_Fishing_Spot_1;
                Target_Text = Target_text_1;

                if (Player_Inside_Area(TargetArea) && client.getPlane() == TargetSpot0.getPlane()) {
                    System.out.println(" | check 3.2 | ");

                    if (GetNPC(TARGET_IDS) != null) {
                        List<NPC> NPC_List0 = GetNPC(TARGET_IDS);
                        List<NPC> NPC_List = SortListDistanceFromPlayer(NPC_List0);
                        System.out.println(" | check 3.3 | ");
                        if (NPC_List != null) {
                            for (NPC target : NPC_List) {
                                if (NPC_Canvas(target) != null) {
                                    System.out.println(" | check 3.6 | ");
                                    if (PointInsideClickableWindow(NPC_Canvas(target))) {
                                        System.out.println(" | check 3.7 | ");
                                        Move_Mouse_NPC_Type_2(robot, MouseCanvasPosition(), target);
                                        if (FirstLeftClickOption(Target_Text)) {
                                            leftClick(robot);
                                            Thread.sleep(random2(600, 800));
                                            if (idleMovementTimeSecs() < idleMovementTimeThreshold) {
                                                Thread.sleep(random2(600, 800));
                                            }
                                            Thread.sleep(random2(600, 800));
                                            return;
                                        }

                                        if (target.getConvexHull().contains(MouseCanvasPosition().getX(), MouseCanvasPosition().getY())) {
                                            rightClick(robot);
                                            Thread.sleep(random2(618, 714));
                                            Point rightClickMenu = MenuIndexPosition(MenuIndex(Target_Text), MouseCanvasPosition());
                                            moveMouse(robot, MouseCanvasPosition(), rightClickMenu, 4, 4);
                                            leftClick(robot);
                                            Thread.sleep(random2(600, 800));
                                            if (idleMovementTimeSecs() < idleMovementTimeThreshold) {
                                                Thread.sleep(random2(600, 800));
                                            }
                                            Thread.sleep(random2(600, 800));
                                            return;
                                        }
                                        return;
                                    }
                                }
                            }
                            System.out.println(" | check 3.8 | ");

                            TargetSpot0 = NPC_List.get(0).getWorldLocation();
                            WorldPoint TargetSpot = new WorldPoint(TargetSpot0.getX() + random2(-1, 1),
                                    TargetSpot0.getY() + random2(-1, 1), TargetSpot0.getPlane());
                            walk(TargetSpot);
                            Thread.sleep(random2(600, 800));
                            if (idleMovementTimeSecs() < idleMovementTimeThreshold) {
                                Thread.sleep(random2(600, 800));
                            }
                            Thread.sleep(random2(600, 800));
                            return;
                        }
                    }
                }

                System.out.println(" | check 4.1 | ");
                TargetSpot0 = Catherby_Fishing_Spot_1;
                WorldPoint TargetSpot = new WorldPoint(TargetSpot0.getX() + random2(-2, 2),
                        TargetSpot0.getY() + random2(-2, 1), TargetSpot0.getPlane());
                if (TargetSpot0.distanceTo(client.getLocalPlayer().getWorldLocation()) > 4 ){
                    walk(TargetSpot);
                    Thread.sleep(random2(600, 800));
                    if (idleMovementTimeSecs() < idleMovementTimeThreshold) {
                        Thread.sleep(random2(600, 800));
                    }
                    Thread.sleep(random2(600, 800));}
                return;
            }

            if (availableInventory() == 0 || !ItemInInventory(TOOL_WEAPON)) {

                if (List_of_Banks() != null) {
                    if (open_Bank(List_of_Banks()) != null) {
                        bankInvent();
                        return;
                    }
                }

                TargetSpot0 = Target_Bank_WL;
                WorldPoint TargetSpot = new WorldPoint(TargetSpot0.getX() + random2(-1, 1),
                        TargetSpot0.getY() + random2(-1, 1), TargetSpot0.getPlane());
                walk(TargetSpot);
                Thread.sleep(random2(1240, 1527));
                if (Clicked_within_Final_Target(TargetSpot)) {
                    while (idleMovementTimeSecs() < idleMovementTimeThreshold) {
                        Thread.sleep(random2(616, 886));
                    }
                }
                return;
            }
        }
    }
