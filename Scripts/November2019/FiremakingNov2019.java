package Polished.Scripts.November2019;

import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.Random;

public class FiremakingNov2019 {

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
            tags = {"birds", "nest", "notifications", "overlay", "skilling", "wc"}
    )
    @PluginDependency(XpTrackerPlugin.class)
    public class WoodcuttingPlugin extends Plugin
    {
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
            AddonB_BackgroundRun.start();
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
        public void onOverlayMenuClicked(OverlayMenuClicked overlayMenuClicked)
        {
            OverlayMenuEntry overlayMenuEntry = overlayMenuClicked.getEntry();
            if (overlayMenuEntry.getMenuAction() == MenuAction.RUNELITE_OVERLAY
                    && overlayMenuClicked.getEntry().getOption().equals(WoodcuttingOverlay.WOODCUTTING_RESET)
                    && overlayMenuClicked.getOverlay() == overlay)
            {
                session = null;
            }
        }

        @Subscribe
        public void onGameTick(GameTick gameTick)
        {
            idleTimeSecs();
            idleMovementTimeSecs();
            if (client.getLocalPlayer().getAnimation() == -1) { idle = true; } else { idle = false; }

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
                if (WOOD_CUT_PATTERN.matcher(event.getMessage()).matches())
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

        // ! Check equipped

        private static final Set<Integer> FOOD_IDS = ImmutableSet.of(ItemID.LOBSTER, ItemID.SALMON, ItemID.SWORDFISH);

        private static final Set<Integer> TELE_IDS = ImmutableSet.of(ItemID.VARROCK_TELEPORT);

        private WorldPoint lastPosition;

        private boolean checkMovementIdle() {
            if (lastPosition == null) {
                lastPosition = Objects.requireNonNull(client.getLocalPlayer()).getWorldLocation();
                return false;
            }

            WorldPoint currentPosition = Objects.requireNonNull(client.getLocalPlayer()).getWorldLocation();

            if (lastPosition.getX() == currentPosition.getX() && lastPosition.getY() == currentPosition.getY()) {
                return true;
            }
            lastPosition = currentPosition;
            return false;
        }

        private long startTimeMovement = System.currentTimeMillis();

        private long idleMovementTimeSecs() {
            long elapsedM;
            if (checkMovementIdle()) {
                elapsedM = ((System.currentTimeMillis() - startTimeMovement) / 1000);
                return elapsedM;
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

        private int random2(int lowerLimit, int upperLimit) {
            int rand2;
            if (lowerLimit == upperLimit) {
                return rand2 = 0;
            } else
                rand2 = random.nextInt(upperLimit - lowerLimit) + lowerLimit;
            return rand2;
        }

        private double random2Dbl(double lowerLimit, double upperLimit) {
            return (lowerLimit + (upperLimit - lowerLimit) * random.nextDouble());
        }

        private void RandomMouseMove() throws AWTException {
            Robot robot = new Robot();
            Point randomCanvas = new Point(random2(50, 1600), random2(200, 800));
            Point MouseCanvasPosition = new Point (client.getMouseCanvasPosition().getX(),client.getMouseCanvasPosition().getY()+20);
            moveMouse1(robot, MouseCanvasPosition, randomCanvas, 11, 15, 15);
        }

        private void moveMouse(Robot robot, Point CanvasPosition, Point MouseTarget, int speed, int xRandom, int yRandom) {
            double XDelta = Math.abs(CanvasPosition.getX()-MouseTarget.getX());
            double YDelta = Math.abs(CanvasPosition.getY()-MouseTarget.getY());
            int XDeviationRange = (int) XDelta/(random2(8,15));
            int YDeviationRange = (int) YDelta/(random2(8,15));
            Point partway = new Point(MouseTarget.getX() + random2(-XDeviationRange, XDeviationRange), MouseTarget.getY() + random2(-YDeviationRange, YDeviationRange));
            moveMouse1(robot, CanvasPosition, partway, speed, xRandom, yRandom);
            moveMouse1(robot, partway, MouseTarget, speed / 2, xRandom / 2, yRandom / 2);
        }

        private void moveMouse(Robot robot, Point CanvasPosition, NPC npc, int speed, int xRandom, int yRandom) {
            double XDelta = Math.abs(CanvasPosition.getX()-npc.getCanvasTilePoly().getBounds().getCenterX());
            double YDelta = Math.abs(CanvasPosition.getY()-npc.getCanvasTilePoly().getBounds().getCenterY());
            int XDeviationRange = (int) XDelta/8;
            int YDeviationRange = (int) YDelta/6;
            Point partway = new Point(((int) npc.getCanvasTilePoly().getBounds().getCenterX()) + random2(-XDeviationRange, XDeviationRange),
                    ((int) npc.getCanvasTilePoly().getBounds().getCenterY()) + random2(-YDeviationRange, YDeviationRange));
            moveMouse1(robot, CanvasPosition, partway, speed, xRandom, yRandom);
            moveMouse1(robot, partway, npc, speed / 2, xRandom / 2, yRandom / 2);
        }

        private void moveMouse1(Robot robot, Point CanvasPosition, Point MouseTarget, int speed, int xRandom, int yRandom) {
            if (Math.abs(CanvasPosition.getX() - MouseTarget.getX()) <= xRandom && Math.abs(CanvasPosition.getY() - MouseTarget.getY()) <= yRandom)
                return;

            Point[] cooardList;
            double t;    //the time interval
            double k = .025;
            cooardList = new Point[4];

            //set the beginning and end points
            cooardList[0] = CanvasPosition;
            cooardList[3] = new Point(MouseTarget.getX() + random2(-xRandom, xRandom), MouseTarget.getY() + (random2(-yRandom, yRandom)));

            int xout = (int) (Math.abs(MouseTarget.getX() - CanvasPosition.getX()) / 10);
            int yout = (int) (Math.abs(MouseTarget.getY() - CanvasPosition.getY()) / 10);

            int x = 0, y = 0;

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

        private void moveMouse1(Robot robot, Point CanvasPosition, NPC npc, int speed, int xRandom, int yRandom) {

            int XRandom = random2(-4, 4);
            int YRandom = random2(-4, 4);

            if (Math.abs(CanvasPosition.getX() - (npc.getCanvasTilePoly().getBounds().getCenterX() + XRandom)) <= xRandom
                    && Math.abs(CanvasPosition.getY() - (npc.getCanvasTilePoly().getBounds().getCenterY() + YRandom)) <= yRandom)
                return;

            Point[] cooardList;
            double t;    //the time interval
            double k = .025;
            cooardList = new Point[4];

            //set the beginning and end points
            cooardList[0] = CanvasPosition;
            cooardList[3] = new Point(((int) npc.getCanvasTilePoly().getBounds().getCenterX() + XRandom) + random2(-xRandom, xRandom),
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
            cooardList[1] = new Point(x, y);

            x = ((int) npc.getCanvasTilePoly().getBounds().getCenterX() + XRandom) < CanvasPosition.getX()
                    ? ((int) npc.getCanvasTilePoly().getBounds().getCenterX() + XRandom) + ((xout > 0) ? random2(1, xout) : 1)
                    : ((int) npc.getCanvasTilePoly().getBounds().getCenterX() + XRandom) - ((xout > 0) ? random2(1, xout) : 1);
            y = ((int) npc.getCanvasTilePoly().getBounds().getCenterY() + YRandom) < CanvasPosition.getY()
                    ? ((int) npc.getCanvasTilePoly().getBounds().getCenterY() + YRandom) + ((yout > 0) ? random2(1, yout) : 1)
                    : ((int) npc.getCanvasTilePoly().getBounds().getCenterY() + YRandom) - ((yout > 0) ? random2(1, yout) : 1);
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

        private void ActivateRun() throws AWTException, InterruptedException {
            if (client.getEnergy()==100)
            {

                if((i-lastI)>30){
                    Robot robot = new Robot();
                    double x = 	client.getWidget(WidgetInfo.MINIMAP_RUN_ORB).getBounds().getCenterX();
                    double y = 	client.getWidget(WidgetInfo.MINIMAP_RUN_ORB).getBounds().getCenterY();
                    int xx = (int) x + random2(5,15);
                    int yy = (int) y + random2(20,30);
                    Point adjustedAltarPerspective = new Point (xx,yy);
                    Point MouseCanvasPosition = new Point (client.getMouseCanvasPosition().getX(),client.getMouseCanvasPosition().getY()+20);

                    moveMouse(robot,MouseCanvasPosition,adjustedAltarPerspective,11,5,5);
                    leftClick(robot);
                    Thread.sleep(random2(770, 1186));
                    lastI = i;
                }}}

        private Point MenuIndexPosition(int MenuIndex, Point LastRightClick) {
            int RCStartY = LastRightClick.getY();
            int RCStartX = LastRightClick.getX();
            int baseYOffset = 27;
            int rowOffset = 15;
            int xTolerance = 35;
            int yTolerance = 4;
            int menuY = RCStartY + baseYOffset + (MenuIndex - 1) * rowOffset + random2(-yTolerance, yTolerance);
            int menuX = RCStartX + random2(-xTolerance, xTolerance);
            Point MenuIndexPoint = new Point(menuX, menuY);
            return MenuIndexPoint;
        }

        private Rectangle BankArea = new Rectangle(3250,3420,6,4);
        private WorldPoint BankArea0 = new WorldPoint(3253, 3420, 0);
        private WorldPoint varrock_east_bank = new WorldPoint(3253,3420,0);

        // dead rock 11391
        private WorldPoint StartFire = new WorldPoint(3278+random2(-3,1), 3428+random2(0,1), 0);
        private int GroundFire = ObjectID.FIRE_26185;
        private Rectangle FireArea = new Rectangle(3246,3428,34,3);

        private Set <Integer> Tinderboxes = ImmutableSet.of(ItemID.TINDERBOX);
        private int Logs = ItemID.WILLOW_LOGS;
        private int Tinderbox = ItemID.TINDERBOX;

        private int i = 0;
        private Thread AddonB_BackgroundRun = new Thread(new Runnable() {
            public void run() {
                for (i = 0; i < 800; i++) {

                    System.out.println(i + " | started | ");

                    if ( config.MineAddon() && client.getGameState() == GameState.LOGGED_IN) {

                        System.out.println(i + " | "
                                + " idle time " + idleTimeSecs() + " plane " + client.getPlane()
                                + " idle movement " + checkMovementIdle() + " WL " + client.getLocalPlayer().getWorldLocation()
                                + " canvas " + client.getMouseCanvasPosition() + " animation " + client.getLocalPlayer().getAnimation()
                                + " not moving time " + idleMovementTimeSecs()
                                + " availableInventory " + availableInventory()
                                + " equipped " + Equipped()
                                +  " FirstLeftClickOption walk " + FirstLeftClickOption()
                        );

                        try {			addon();		} catch (AWTException | InterruptedException e) {		e.printStackTrace();		}

                        if (randomNPCCheck() != null) {
                            System.out.println(i + " | OHtext " + randomNPCCheck().getOverheadText());
                        }

                        //  if (client.getSelectedSceneTile().getGameObjects() != null) {
                        //	for(GameObject gameObject : client.getSelectedSceneTile().getGameObjects())
                        //	{if (gameObject!=null){
                        //			System.out.println(i + " | object " + gameObject.getId());
                        //		}}        }

                        // OHtext Milord Vein X I, please speak to me!
                    }
                    try {
                        Thread.sleep(random2(814, 1631));
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        });

        private int lastI = 0;

        private void addon() throws AWTException, InterruptedException {

            WorldPoint bankLocation = new WorldPoint(BankArea0.getX()+random2(-1,1),BankArea0.getY()+random2(0,2),0);
            int HP = client.getBoostedSkillLevel(Skill.HITPOINTS);
            System.out.print(" | HP: " + HP);

            int HPThreshold = 12;
            while (HP < HPThreshold) {
                walk(bankLocation);
                try {
                    Thread.sleep(random2(12000, 30000));
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                HP = client.getBoostedSkillLevel(Skill.HITPOINTS);
            }

            if (randomNPCCheck() != null) {
                dismissRandom();
            }

            if (client.getWidget(WidgetInfo.LEVEL_UP) != null) {
                Robot robot = new Robot();
                try {
                    Thread.sleep(random2(450, 610));
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                robot.keyPress(KeyEvent.VK_SPACE);
                try {
                    Thread.sleep(random2(450, 610));
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                robot.keyPress(KeyEvent.VK_SPACE);
                try {
                    Thread.sleep(random2(1100, 1610));
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
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

            while (idleMovementTimeSecs() < idleTimeThreshold || idleTimeSecs() < idleTimeThreshold )
            {
                if(NumberofItemInInventory(Logs)>0 && ItemInInventory(Tinderboxes) ){
                    int randomInt = random2(1,70);
                    if (randomInt == 5)	{RandomMouseMove();}
                    return;}
                Thread.sleep(random2(816, 1186)); }

            System.out.println(" | check 3 | ");

            if (NumberofItemInInventory(Logs)>0 && ItemInInventory(Tinderboxes) ) {

                if(FireArea.contains(client.getLocalPlayer().getWorldLocation().getX(),client.getLocalPlayer().getWorldLocation().getY())) {
                    if(ObjectsOnGround(GroundFire)==null){
                        LightFire();
                        return;
                    }
                    WorldPoint NextTile = new WorldPoint(client.getLocalPlayer().getWorldLocation().getX()-1,client.getLocalPlayer().getWorldLocation().getY(),client.getPlane() );
                    walk(NextTile);

                }
                walk(StartFire);
            }

            if (NumberofItemInInventory(Logs)==0 || !ItemInInventory(Tinderboxes) )
            {

                if (BankArea.contains(client.getLocalPlayer().getWorldLocation().getX(),client.getLocalPlayer().getWorldLocation().getY()))
                {
                    bankInvent();
                    return;}

                walk(bankLocation);
            }
        }

        private int availableInventory() {
            int availableInventory = 0;

            if(client.getItemContainer(InventoryID.INVENTORY)==null){return 28;}

            if (client.getItemContainer(InventoryID.INVENTORY).getItems().length!=28){
                for (Item item : client.getItemContainer(InventoryID.INVENTORY).getItems()) {
                    if (item.getId() == -1){	availableInventory=availableInventory+1;}}
                availableInventory = availableInventory + 28 - client.getItemContainer(InventoryID.INVENTORY).getItems().length;
                return availableInventory;}

            for (Item item : client.getItemContainer(InventoryID.INVENTORY).getItems()) {
                //System.out.print(" || items id " + item.getId());
                if (item.getId() == -1){	availableInventory=availableInventory+1;	}}
            return availableInventory; 	}

        private GameObject ObjectFromTiles (int id) {
            GameObject object;
            Tile[][][] tiles = client.getScene().getTiles();

            for (int l = 0; l < tiles.length; l++){
                for(int j=0 ; j<tiles[l].length ; j++){
                    for(int k=0 ; k<tiles[l][j].length ; k++){
                        Tile x = tiles[l][j][k];
                        if(x!=null) {
                            if(x.getWorldLocation()!=null) {
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

                            }}}}}
            return null;
        }

        private GameObject ObjectsOnGround (int id) {
            GameObject object;
            Tile[][][] tiles = client.getScene().getTiles();

            for (int l = 0; l < tiles.length; l++){
                for(int j=0 ; j<tiles[l].length ; j++){
                    for(int k=0 ; k<tiles[l][j].length ; k++){
                        Tile x = tiles[l][j][k];
                        if(x!=null) {
                            if(x.getWorldLocation()!=null) {
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

                            }}}}}
            return null;
        }

        private List <GameObject> ObjectsFromTiles (int id) {
            List <GameObject> LocalTargets1 = new ArrayList<>();
            Tile[][][] tiles = client.getScene().getTiles();

            for (int l = 0; l < tiles.length; l++){
                for(int j=0 ; j<tiles[l].length ; j++){
                    for(int k=0 ; k<tiles[l][j].length ; k++){
                        Tile x = tiles[l][j][k];
                        if(x!=null) {
                            if(x.getWorldLocation()!=null) {
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
                    }}}
            return LocalTargets1;
        }

        private Set <Integer> NPCBankers = ImmutableSet.of(NpcID.BANKER_2897,NpcID.BANKER_2898);

        private void bankInvent() throws AWTException, InterruptedException {
            while (!checkMovementIdle()) {
                Thread.sleep(1063);
            }

            //GameObject bank = ObjectFromTiles(ObjectID.BANK_BOOTH_24101);
            List<NPC> bankers = GetNPC(NPCBankers);
            NPC bank = bankers.get(random2(0, bankers.size() - 1));
            double x = bank.getCanvasTilePoly().getBounds().getCenterX();
            double y = bank.getCanvasTilePoly().getBounds().getCenterY();
            int xx = (int) x + random2(-3, 3);
            int yy = (int) y + random2(1, 7);
            Point bankBoothPerspective = new Point(xx, yy);
            Robot robot = new Robot();
            if (PointInsideClickableWindow(bankBoothPerspective) && depositInventoryPoint() == null) {
                Point MouseCanvasPosition = new Point(client.getMouseCanvasPosition().getX(), client.getMouseCanvasPosition().getY() + 20);
                moveMouse(robot, MouseCanvasPosition, bankBoothPerspective, 11, 4, 4);
                rightClick(robot);
                try {
                    Thread.sleep(random2(500, 800));
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                Point selectBank = MenuIndexPosition(MenuIndex("Bank"), bankBoothPerspective);
                moveMouse(robot, bankBoothPerspective, selectBank, 11, 4, 4);
                leftClick(robot);
                try {
                    Thread.sleep(random2(1100, 2400));
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            if (depositInventoryPoint() == null) {
                return;
            }

            if (depositInventoryPoint() != null && availableInventory() < 27) {

                Point depositInventoryPoint1 = depositInventoryPoint();
                try {
                    Thread.sleep(random2(500, 600));
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                Point MouseCanvasPosition = new Point(client.getMouseCanvasPosition().getX(), client.getMouseCanvasPosition().getY() + 20);
                moveMouse(robot, MouseCanvasPosition, depositInventoryPoint1, 11, 4, 4);
                leftClick(robot);
                try {
                    Thread.sleep(random2(600, 750));
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            if (getBankItemIndex(Tinderbox)!=0 && !ItemInInventory(Tinderboxes)) {
                try {
                    Thread.sleep(random2(50, 80));
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                Point Banklocation = BankLocation(getBankItemIndex(Tinderbox));

                net.runelite.api.Point MouseCanvasPosition = new net.runelite.api.Point(client.getMouseCanvasPosition().getX(), client.getMouseCanvasPosition().getY() + 20);

                moveMouse1(robot, MouseCanvasPosition, Banklocation, 11, 4, 4);
                leftClick(robot);
                try {
                    Thread.sleep(random2(500, 800));
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

            }

            if (getBankItemIndex(Logs)!=0 && NumberofItemInInventory(Logs)<26) {
                try {
                    Thread.sleep(random2(50, 80));
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                Point Banklocation = BankLocation(getBankItemIndex(Logs));

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
                Point withdraw = MenuIndexPosition(MenuIndex("Withdraw-All"), Banklocation);

                moveMouse(robot, Banklocation, withdraw, 11, 4, 4);
                leftClick(robot);
            }

        }


        private Point BankLocation(int index) {
            // row = 1, n = index, column = n , while n>8 , column = n-8 row = row +1
            // row 1 y = 115  // row 2 y = 153  // column 1 x = 420 // column 2 x = 469  // 519  ,  189
            //canvas bank 355,88 	// column spacing of 50, tolerance 23 	// row spacing 37 , tolerance 22
            int bankBaseX = client.getWidget(WidgetInfo.BANK_ITEM_CONTAINER).getCanvasLocation().getX();
            int bankBaseY = client.getWidget(WidgetInfo.BANK_ITEM_CONTAINER).getCanvasLocation().getY();
            int n = index;
            int relateBaseX= 75;
            int relateBaseY= 32;
            int columnFactor = 47;
            int rowFactor = 36;
            int row = 1;
            if (n>8){
                while (n>8){n =n-8; row = row + 1;}}
            int column = n;
            int x = bankBaseX + relateBaseX + (column-1)*columnFactor;
            int y = bankBaseY + relateBaseY + (row-1)*rowFactor;
            int xTolerance = x+random2(-7,7);
            int yTolerance = y+random2(-7,7);
            Point itemBankLocation = new Point (xTolerance,yTolerance);
            return itemBankLocation;
        }

        private List <NPC> GetNPC (Set <Integer> ids) {

            List<NPC> localNPCs = new ArrayList<>();
            List<NPC> NPCList;

            if(client.getNpcs()==null){return null;}

            NPCList = client.getNpcs();

            for (NPC npc : NPCList)
            {	if (npc.getWorldLocation().distanceTo2D(client.getLocalPlayer().getWorldLocation()) < 6)
                System.out.print(" npcs " + npc.getId() + npc.getName() + " " + npc.getWorldLocation());
                {	if (ids.contains(npc.getId()))
                {	localNPCs.add(npc);	}}	}

            return localNPCs;
        }

        private Point depositInventoryPoint(){
            if(client.getWidget(12,42)==null){return null;}
            Widget deposit_Inventory_Widget = client.getWidget(12,42);
            int deposit_x = (int)Math.round(random2Dbl(deposit_Inventory_Widget.getBounds().getMinX()+8, deposit_Inventory_Widget.getBounds().getMaxX())-2);
            int deposit_y = (int)Math.round(random2Dbl(deposit_Inventory_Widget.getBounds().getMinY() +23, deposit_Inventory_Widget.getBounds().getMaxY())+17);
            return new Point(deposit_x, deposit_y );}

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

        private boolean Equipped() {

            if (client.getItemContainer(InventoryID.EQUIPMENT) == null) {
                return false;
            }

            for (Item item : client.getItemContainer(InventoryID.EQUIPMENT).getItems()) {
                if (item == null) {
                    continue;
                }

                if (item.getId() == ItemID.MITHRIL_PICKAXE || item.getId() == ItemID.ADAMANT_PICKAXE) {
                    return true;
                }
            }
            return false;
        }

        private boolean ItemInInventory( Set <Integer> testList) {

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

        private int NumberofItemInInventory( int ItemID ) {
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

        private void LightFire() throws AWTException {
            while (client.getWidget(WidgetInfo.INVENTORY).isHidden()) {
                int inventoryIconTopLeftX = client.getWidget(WidgetInfo.RESIZABLE_VIEWPORT_BOTTOM_LINE_INVENTORY_TAB).getBounds().x;
                int inventoryIconTopLeftY = client.getWidget(WidgetInfo.RESIZABLE_VIEWPORT_BOTTOM_LINE_INVENTORY_TAB).getBounds().y;
                int inventoryIconXWidth = (int) client.getWidget(WidgetInfo.RESIZABLE_VIEWPORT_BOTTOM_LINE_INVENTORY_TAB).getBounds().getWidth();
                int inventoryIconYHeight = (int) client.getWidget(WidgetInfo.RESIZABLE_VIEWPORT_BOTTOM_LINE_INVENTORY_TAB).getBounds().getHeight();
                int inventoryIconX = inventoryIconTopLeftX + 3 + random2(0, inventoryIconXWidth - 6);
                int inventoryIconY = inventoryIconTopLeftY + 3 + random2(0, inventoryIconYHeight - 6);
                Point inventoryIcon = new Point(inventoryIconX, inventoryIconY);
                Robot robot = new Robot();
                Point MouseCanvasPosition = new Point(client.getMouseCanvasPosition().getX(), client.getMouseCanvasPosition().getY() + 20);
                moveMouse(robot, MouseCanvasPosition, inventoryIcon, 10, 5, 5);
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

            int TinderboxIndex = InvIndex(Tinderbox);
            Point TinderboxInvLocation = InvLocation(TinderboxIndex);
            Robot robot = new Robot();
            Point MouseCanvasPosition = new Point(client.getMouseCanvasPosition().getX(), client.getMouseCanvasPosition().getY() + 20);
            moveMouse(robot, MouseCanvasPosition, TinderboxInvLocation, 10, 5, 5);
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

            int Index = InvIndex(Logs);
            Point InvLocation = InvLocation(Index);
            MouseCanvasPosition = new Point(client.getMouseCanvasPosition().getX(), client.getMouseCanvasPosition().getY() + 20);
            moveMouse(robot, MouseCanvasPosition, InvLocation, 10, 5, 5);
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

        private int InvIndex( int ItemID ) {
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

        private Point worldToCanvas(WorldPoint worldpoint) {
            if(LocalPoint.fromWorld(client, worldpoint.getX(), worldpoint.getY())!=null){
                LocalPoint targetLL = LocalPoint.fromWorld(client, worldpoint.getX(), worldpoint.getY());
                if(Perspective.localToCanvas(client, targetLL, client.getPlane())!=null){
                    Point perspective = Perspective.localToCanvas(client, targetLL, client.getPlane());
                    Point adjustedPerspective = new Point(perspective.getX() + 1, perspective.getY() - 1);
                    return adjustedPerspective;
                }}
            return null;
        }

        private Point worldToMiniMap(WorldPoint worldpoint) {
            LocalPoint targetLL = LocalPoint.fromWorld(client, worldpoint.getX(), worldpoint.getY());
            if (targetLL != null) {
                Point minimapPerspective = Perspective.localToMinimap(client, targetLL);
                if (minimapPerspective != null) {
                    Point adjustedMinimapPerspective = new Point(minimapPerspective.getX() + 4, minimapPerspective.getY() + 23);
                    return adjustedMinimapPerspective;
                }
            }
            return null;
        }

        private boolean PointInsideClickableWindow (Point point)
        {
            Rectangle ClickableWindow1 = new Rectangle(3, 3, (1420 - 3), (815 - 3));
            Rectangle RightSideClickableWindow = new Rectangle(1418, 180, (1630 - 1418), (664 - 180));
            Rectangle BottomClickableWindow = new Rectangle(530, 813, (1420 - 530), (938 - 813));

            if (ClickableWindow1.contains(point.getX(),point.getY())
                    || RightSideClickableWindow.contains(point.getX(),point.getY())
                    || BottomClickableWindow.contains(point.getX(),point.getY())
            )
            {return true;}
            return false;
        }

        private boolean PointInsideMiniMap (Point point)
        {
            if (point == null){return false;}
            double MinimapX = client.getWidget(WidgetInfo.RESIZABLE_MINIMAP_DRAW_AREA).getBounds().getCenterX();
            double MinimapY = client.getWidget(WidgetInfo.RESIZABLE_MINIMAP_DRAW_AREA).getBounds().getCenterY();
            // -- 75
            double MinimapRadius = 72;
            double deltaX = Math.abs(MinimapX-point.getX());
            double deltaY = Math.abs(MinimapY-point.getY());
            double deltaXSQ = Math.pow(deltaX,2);
            double deltaYSQ = Math.pow(deltaY,2);
            double Hypotenuse = Math.sqrt(deltaXSQ + deltaYSQ);

            double WorldMinimapX = client.getWidget(WidgetInfo.MINIMAP_WORLDMAP_ORB).getBounds().getCenterX();
            double WorldMinimapY = client.getWidget(WidgetInfo.MINIMAP_WORLDMAP_ORB).getBounds().getCenterY();
            double WorldMinimapRadius = 15;
            double deltaX1 = Math.abs(WorldMinimapX-point.getX());
            double deltaY1 = Math.abs(WorldMinimapY-point.getY());
            double deltaXSQ1 = Math.pow(deltaX1,2);
            double deltaYSQ1 = Math.pow(deltaY1,2);
            double Hypotenuse1 = Math.sqrt(deltaXSQ1 + deltaYSQ1);

            if (Hypotenuse < MinimapRadius && Hypotenuse1 >  WorldMinimapRadius )
            {return true;}
            return false;
        }

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

        private NPC randomNPCCheck (){
            List<NPC> activeRandom = new ArrayList<>();
            List<NPC> NPCList;
            if(client.getNpcs()==null){return null;}
            NPCList = client.getNpcs();

            for (NPC npc : NPCList)
            {	if (npc.getWorldLocation().distanceTo2D(client.getLocalPlayer().getWorldLocation()) < 4)
            { System.out.print(" NPCList " + npc.getId() + " , " + npc.getIndex() + " , " + npc.getName() );  }}

            for (NPC npc : NPCList)
            {	if (RANDOM_IDS.contains(npc.getId()))
            {	activeRandom.add(npc);	}}
            if(activeRandom.size()!=0) {
                for (NPC random : activeRandom){
                    if (random.getInteracting()!=null){
                        if (random.getInteracting().getName()!=null){
                            if (random.getInteracting().getName().contains("VEIN")){
                                return random;
                            }}}
                    if (random.getOverheadText()!=null ){
                        if (random.getOverheadText().contains("VEIN")){
                            return random;
                        }}
                }}
            return null;
        }

        private void dismissRandom() throws AWTException {
            NPC targetRandom = randomNPCCheck();
            WorldPoint randomWL = targetRandom.getWorldLocation();
            Point randomCanvas = worldToCanvas(randomWL);
            if (PointInsideClickableWindow(randomCanvas)) {
                Robot robot = new Robot();
                Point MouseCanvasPosition = new Point (client.getMouseCanvasPosition().getX(),client.getMouseCanvasPosition().getY()+20);
                moveMouse(robot, MouseCanvasPosition, randomCanvas, 11, 4, 4);
                rightClick(robot);

                try {
                    Thread.sleep(random2(500, 800));
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                Point selectDismiss = MenuIndexPosition(MenuIndex("Dismiss"), randomCanvas);

                moveMouse(robot, randomCanvas, selectDismiss, 11, 4, 4);
                leftClick(robot);
            }
        }

        private List<GameObject> inverseSortTreeDistanceFromPlayer(List<GameObject> list)
        {
            if (list.isEmpty())
            {
                return null;
            }

            final WorldPoint PlayerWL = new WorldPoint(client.getLocalPlayer().getWorldLocation().getX(),
                    client.getLocalPlayer().getWorldLocation().getY(),client.getPlane());
            list.sort(
                    Comparator.comparing(
                            (GameObject object) -> object.getWorldLocation().distanceTo(PlayerWL))
            );
            return list;
        }

    }

