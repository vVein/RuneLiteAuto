package Polished.Scripts.November2019;

public class CanifisAgilityNov2019 {

    /*
     * Copyright (c) 2018, Adam <Adam@sigterm.info>
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
package net.runelite.client.plugins.agility;

import com.google.common.collect.ImmutableSet;
import com.google.inject.Provides;

import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.*;
import java.util.List;
import javax.inject.Inject;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;

import static net.runelite.api.ItemID.AGILITY_ARENA_TICKET;
import static net.runelite.api.Skill.AGILITY;

import net.runelite.api.Point;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ConfigChanged;
import net.runelite.api.events.DecorativeObjectChanged;
import net.runelite.api.events.DecorativeObjectDespawned;
import net.runelite.api.events.DecorativeObjectSpawned;
import net.runelite.api.events.GameObjectChanged;
import net.runelite.api.events.GameObjectDespawned;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.GroundObjectChanged;
import net.runelite.api.events.GroundObjectDespawned;
import net.runelite.api.events.GroundObjectSpawned;
import net.runelite.api.events.ItemDespawned;
import net.runelite.api.events.ItemSpawned;
import net.runelite.api.events.StatChanged;
import net.runelite.api.events.WallObjectChanged;
import net.runelite.api.events.WallObjectDespawned;
import net.runelite.api.events.WallObjectSpawned;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.Notifier;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.AgilityShortcut;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.infobox.InfoBoxManager;

    @PluginDescriptor(
            name = "Agility",
            description = "Show helpful information about agility courses and obstacles",
            tags = {"grace", "marks", "overlay", "shortcuts", "skilling", "traps"}
    )
    @Slf4j
    public class AgilityPlugin extends Plugin
    {
        private static final int AGILITY_ARENA_REGION_ID = 11157;

        @Getter
        private final Map<TileObject, Obstacle> obstacles = new HashMap<>();

        @Getter
        private final List<Tile> marksOfGrace = new ArrayList<>();

        @Inject
        private OverlayManager overlayManager;

        @Inject
        private AgilityOverlay agilityOverlay;

        @Inject
        private LapCounterOverlay lapCounterOverlay;

        @Inject
        private Notifier notifier;

        @Inject
        private Client client;

        @Inject
        private InfoBoxManager infoBoxManager;

        @Inject
        private AgilityConfig config;

        @Inject
        private ItemManager itemManager;

        @Getter
        private AgilitySession session;

        private int lastAgilityXp;
        private WorldPoint lastArenaTicketPosition;

        @Getter
        private int agilityLevel;

        @Provides
        AgilityConfig getConfig(ConfigManager configManager)
        {
            return configManager.getConfig(AgilityConfig.class);
        }

        @Override
        protected void startUp() throws Exception
        {
            overlayManager.add(agilityOverlay);
            overlayManager.add(lapCounterOverlay);
            agilityLevel = client.getBoostedSkillLevel(Skill.AGILITY);
            //	AddonA_BackgroundRun.start();
        }

        @Override
        protected void shutDown() throws Exception
        {
            overlayManager.remove(agilityOverlay);
            overlayManager.remove(lapCounterOverlay);
            marksOfGrace.clear();
            obstacles.clear();
            session = null;
            agilityLevel = 0;
        }

        @Subscribe
        public void onGameStateChanged(GameStateChanged event)
        {
            switch (event.getGameState())
            {
                case HOPPING:
                case LOGIN_SCREEN:
                    session = null;
                    lastArenaTicketPosition = null;
                    removeAgilityArenaTimer();
                    break;
                case LOADING:
                    marksOfGrace.clear();
                    obstacles.clear();
                    break;
                case LOGGED_IN:
                    if (!isInAgilityArena())
                    {
                        lastArenaTicketPosition = null;
                        removeAgilityArenaTimer();
                    }
                    break;
            }
        }

        @Subscribe
        public void onConfigChanged(ConfigChanged event)
        {
            if (!config.showAgilityArenaTimer())
            {
                removeAgilityArenaTimer();
            }
        }

        @Subscribe
        public void onStatChanged(StatChanged statChanged)
        {
            if (statChanged.getSkill() != AGILITY)
            {
                return;
            }

            agilityLevel = statChanged.getBoostedLevel();

            if (!config.showLapCount())
            {
                return;
            }

            // Determine how much EXP was actually gained
            int agilityXp = client.getSkillExperience(AGILITY);
            int skillGained = agilityXp - lastAgilityXp;
            lastAgilityXp = agilityXp;

            // Get course
            Courses course = Courses.getCourse(client.getLocalPlayer().getWorldLocation().getRegionID());
            if (course == null
                    || (course.getCourseEndWorldPoints().length == 0
                    ? Math.abs(course.getLastObstacleXp() - skillGained) > 1
                    : Arrays.stream(course.getCourseEndWorldPoints()).noneMatch(wp -> wp.equals(client.getLocalPlayer().getWorldLocation()))))
            {
                return;
            }

            if (session != null && session.getCourse() == course)
            {
                session.incrementLapCount(client);
            }
            else
            {
                session = new AgilitySession(course);
                // New course found, reset lap count and set new course
                session.resetLapCount();
                session.incrementLapCount(client);
            }
        }

        @Subscribe
        public void onItemSpawned(ItemSpawned itemSpawned)
        {
            if (obstacles.isEmpty())
            {
                return;
            }

            final TileItem item = itemSpawned.getItem();
            final Tile tile = itemSpawned.getTile();

            if (item.getId() == ItemID.MARK_OF_GRACE)
            {
                marksOfGrace.add(tile);
            }
        }

        @Subscribe
        public void onItemDespawned(ItemDespawned itemDespawned)
        {
            final Tile tile = itemDespawned.getTile();
            marksOfGrace.remove(tile);
        }

        @Subscribe
        public void onGameTick(GameTick tick)
        {

            idleTimeSecs();
            idleMovementTimeSecs();

            if(client.getLocalPlayer().getAnimation()==-1){
                idle = true;
            }
            else{idle = false;}

            if (isInAgilityArena())
            {
                // Hint arrow has no plane, and always returns the current plane
                WorldPoint newTicketPosition = client.getHintArrowPoint();
                WorldPoint oldTickPosition = lastArenaTicketPosition;

                lastArenaTicketPosition = newTicketPosition;

                if (oldTickPosition != null && newTicketPosition != null
                        && (oldTickPosition.getX() != newTicketPosition.getX() || oldTickPosition.getY() != newTicketPosition.getY()))
                {
                    log.debug("Ticked position moved from {} to {}", oldTickPosition, newTicketPosition);

                    if (config.notifyAgilityArena())
                    {
                        notifier.notify("Ticket location changed");
                    }

                    if (config.showAgilityArenaTimer())
                    {
                        showNewAgilityArenaTimer();
                    }
                }
            }
        }

        private boolean isInAgilityArena()
        {
            Player local = client.getLocalPlayer();
            if (local == null)
            {
                return false;
            }

            WorldPoint location = local.getWorldLocation();
            return location.getRegionID() == AGILITY_ARENA_REGION_ID;
        }

        private void removeAgilityArenaTimer()
        {
            infoBoxManager.removeIf(infoBox -> infoBox instanceof AgilityArenaTimer);
        }

        private void showNewAgilityArenaTimer()
        {
            removeAgilityArenaTimer();
            infoBoxManager.addInfoBox(new AgilityArenaTimer(this, itemManager.getImage(AGILITY_ARENA_TICKET)));
        }

        @Subscribe
        public void onGameObjectSpawned(GameObjectSpawned event)
        {
            onTileObject(event.getTile(), null, event.getGameObject());
        }

        @Subscribe
        public void onGameObjectChanged(GameObjectChanged event)
        {
            onTileObject(event.getTile(), event.getPrevious(), event.getGameObject());
        }

        @Subscribe
        public void onGameObjectDespawned(GameObjectDespawned event)
        {
            onTileObject(event.getTile(), event.getGameObject(), null);
        }

        @Subscribe
        public void onGroundObjectSpawned(GroundObjectSpawned event)
        {
            onTileObject(event.getTile(), null, event.getGroundObject());
        }

        @Subscribe
        public void onGroundObjectChanged(GroundObjectChanged event)
        {
            onTileObject(event.getTile(), event.getPrevious(), event.getGroundObject());
        }

        @Subscribe
        public void onGroundObjectDespawned(GroundObjectDespawned event)
        {
            onTileObject(event.getTile(), event.getGroundObject(), null);
        }

        @Subscribe
        public void onWallObjectSpawned(WallObjectSpawned event)
        {
            onTileObject(event.getTile(), null, event.getWallObject());
        }

        @Subscribe
        public void onWallObjectChanged(WallObjectChanged event)
        {
            onTileObject(event.getTile(), event.getPrevious(), event.getWallObject());
        }

        @Subscribe
        public void onWallObjectDespawned(WallObjectDespawned event)
        {
            onTileObject(event.getTile(), event.getWallObject(), null);
        }

        @Subscribe
        public void onDecorativeObjectSpawned(DecorativeObjectSpawned event)
        {
            onTileObject(event.getTile(), null, event.getDecorativeObject());
        }

        @Subscribe
        public void onDecorativeObjectChanged(DecorativeObjectChanged event)
        {
            onTileObject(event.getTile(), event.getPrevious(), event.getDecorativeObject());
        }

        @Subscribe
        public void onDecorativeObjectDespawned(DecorativeObjectDespawned event)
        {
            onTileObject(event.getTile(), event.getDecorativeObject(), null);
        }

        private void onTileObject(Tile tile, TileObject oldObject, TileObject newObject)
        {
            obstacles.remove(oldObject);

            if (newObject == null)
            {
                return;
            }

            if (Obstacles.COURSE_OBSTACLE_IDS.contains(newObject.getId()) ||
                    (Obstacles.TRAP_OBSTACLE_IDS.contains(newObject.getId())
                            && Obstacles.TRAP_OBSTACLE_REGIONS.contains(newObject.getWorldLocation().getRegionID())))
            {
                obstacles.put(newObject, new Obstacle(tile, null));
            }

            if (Obstacles.SHORTCUT_OBSTACLE_IDS.containsKey(newObject.getId()))
            {
                AgilityShortcut closestShortcut = null;
                int distance = -1;

                // Find the closest shortcut to this object
                for (AgilityShortcut shortcut : Obstacles.SHORTCUT_OBSTACLE_IDS.get(newObject.getId()))
                {
                    if (shortcut.getWorldLocation() == null)
                    {
                        closestShortcut = shortcut;
                        break;
                    }
                    else
                    {
                        int newDistance = shortcut.getWorldLocation().distanceTo2D(newObject.getWorldLocation());
                        if (closestShortcut == null || newDistance < distance)
                        {
                            closestShortcut = shortcut;
                            distance = newDistance;
                        }
                    }
                }

                if (closestShortcut != null)
                {
                    obstacles.put(newObject, new Obstacle(tile, closestShortcut));
                }
            }
        }

        private WorldPoint lastPosition;

        private boolean checkMovementIdle ()
        {
            if (lastPosition == null)
            {
                lastPosition = Objects.requireNonNull(client.getLocalPlayer()).getWorldLocation();
                return false;
            }

            WorldPoint currentPosition = Objects.requireNonNull(client.getLocalPlayer()).getWorldLocation();

            if (lastPosition.getX() == currentPosition.getX() && lastPosition.getY() == currentPosition.getY() ) {
                {return true;}
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
            cooardList = new Point[4];

            //set the beginning and end points
            cooardList[0] = CanvasPosition;
            cooardList[3] = new Point(MouseTarget.getX()+random2(-xRandom,xRandom),MouseTarget.getY()+(random2(-yRandom,yRandom)));

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

        private List <TileObject> AgilityObstacle (int index){
            List <TileObject> LocalObstacles = new ArrayList<>();

            for (TileObject obstacle : obstacles.keySet())
            {
                if (obstacle.getWorldLocation().distanceTo2D(client.getLocalPlayer().getWorldLocation()) < 7) {
                    System.out.println(" LocalObstacles " + obstacle.getId() + " Wl " + obstacle.getWorldLocation()
                            //	+ " getClickbox " + obstacle.getClickbox().getBounds().getCenterX()
                            //	+ " CanvasPoly " + obstacle.getCanvasTilePoly().getBounds().getCenterX()

                    );}
                if(obstacle.getId() == index){
                    LocalObstacles.add(obstacle);
                }

                if (LocalObstacles.size() != 0)
                {
                    final LocalPoint cameraPoint = new LocalPoint(client.getCameraX(), client.getCameraY());
                    LocalObstacles.sort(
                            Comparator.comparing(
                                    // Negate to have the furthest first
                                    (TileObject tileObject) -> tileObject.getLocalLocation().distanceTo(cameraPoint))
                                    // Order by position
                                    .thenComparing(TileObject::getLocalLocation, Comparator.comparing(LocalPoint::getX)
                                            .thenComparing(LocalPoint::getY))
                                    // And then by id
                                    .thenComparing(TileObject::getId) );

                    return LocalObstacles;
                }
            }
            return null;
        }

        private Point AgilityObstacleCanvasPoint (int index){
            List <TileObject> LocalObstacles = new ArrayList<>();

            for (TileObject obstacle : obstacles.keySet())
            {
                if(obstacle.getId() == index && obstacle.getClickbox()!=null){
                    double x = obstacle.getClickbox().getBounds().getCenterX();
                    double y = obstacle.getClickbox().getBounds().getCenterY();
                    int xx = (int) x;
                    int yy = (int) y;
                    Point adjustedSpotPerspective = new Point (xx,yy);
                    return adjustedSpotPerspective;
                }	}return null;}

        private List <TileObject> AgilityObstacle (int index1, int index2){
            List <TileObject> LocalObstacles = new ArrayList<>();
            obstacles.forEach((object, obstacle) ->
            {
                if (object.getWorldLocation().distanceTo2D(client.getLocalPlayer().getWorldLocation()) < 7) {
                    System.out.println(" LocalObstacles " + object.getId() + " Wl " + object.getWorldLocation()
                            //	+ " getClickbox " + obstacle.getClickbox().getBounds().getCenterX()
                            //	+ " CanvasPoly " + obstacle.getCanvasTilePoly().getBounds().getCenterX()

                    );}
                if(object.getId() == index1 || object.getId() == index2){
                    LocalObstacles.add(object);
                }
            });

            if (LocalObstacles.size() != 0)
            {
                final LocalPoint cameraPoint = new LocalPoint(client.getCameraX(), client.getCameraY());
                LocalObstacles.sort(
                        Comparator.comparing(
                                // Negate to have the furthest first
                                (TileObject tileObject) -> tileObject.getLocalLocation().distanceTo(cameraPoint))
                                // Order by position
                                .thenComparing(TileObject::getLocalLocation, Comparator.comparing(LocalPoint::getX)
                                        .thenComparing(LocalPoint::getY))
                                // And then by id
                                .thenComparing(TileObject::getId) );

                return LocalObstacles;
            }

            return null;
        }

        private void ClickObstacle (int TargetObstacleID) throws AWTException {
            List <TileObject> ObstacleList = AgilityObstacle(TargetObstacleID);
            for (TileObject obstacle : ObstacleList){
                WorldPoint TargetObstacleWL= obstacle.getWorldLocation();
                int TargetPlane = obstacle.getPlane();

                if (TargetObstacleWL.distanceTo2D(client.getLocalPlayer().getWorldLocation()) < 10) {

                    double x = obstacle.getClickbox().getBounds().getCenterX();
                    double y = obstacle.getClickbox().getBounds().getCenterY() ;
                    int xx = (int) x + random2(-3,3);
                    int yy = (int) y + random2(1,7);
                    Point adjustedSpotPerspective = new Point (xx,yy);
                    System.out.println(" | click obstacle | " + " TargetObstacleWL " + TargetObstacleWL + " plane " + TargetPlane
                            + " client plane " + client.getPlane());

                    if(adjustedSpotPerspective.getX()>8 && adjustedSpotPerspective.getX()<1560 && adjustedSpotPerspective.getY()>30
                            && adjustedSpotPerspective.getY()<810 ) {
                        Robot robot = new Robot();
                        moveMouse(robot,client.getMouseCanvasPosition(),adjustedSpotPerspective,10,5,5);
                        leftClick(robot);
                    }	}	}}

        private void ClickObstacle (int TargetObstacleID, int TargetObstacleID2) throws AWTException {
            List <TileObject> ObstacleList = AgilityObstacle(TargetObstacleID, TargetObstacleID2);
            for (TileObject obstacle : ObstacleList){
                WorldPoint TargetObstacleWL= obstacle.getWorldLocation();
                int TargetPlane = obstacle.getPlane();

                if (TargetObstacleWL.distanceTo2D(client.getLocalPlayer().getWorldLocation()) < 10) {

                    double x = obstacle.getClickbox().getBounds().getCenterX();
                    //+ random2Dbl(-obstacle.getClickbox().getBounds().getWidth()/2,obstacle.getClickbox().getBounds().getWidth()/2);
                    double y = obstacle.getClickbox().getBounds().getCenterY() ;
                    // + random2Dbl(-obstacle.getClickbox().getBounds().getHeight()/2,obstacle.getClickbox().getBounds().getHeight()/2);
                    int xx = (int) x + random2(-3,3);
                    int yy = (int) y + random2(1,7);
                    Point adjustedSpotPerspective = new Point (xx,yy);
                    System.out.println(" | click obstacle | " + " TargetObstacleWL " + TargetObstacleWL + " plane " + TargetPlane
                            + " client plane " + client.getPlane());

                    if(adjustedSpotPerspective.getX()>8 && adjustedSpotPerspective.getX()<1560 && adjustedSpotPerspective.getY()>30
                            && adjustedSpotPerspective.getY()<810) {
                        Robot robot = new Robot();
                        moveMouse(robot,client.getMouseCanvasPosition(),adjustedSpotPerspective,10,5,5);
                        leftClick(robot);
                        return;
                    }	}	}}

        private void ClickObscureObstacle (int TargetObstacleID) throws AWTException {
            List <TileObject> ObstacleList = AgilityObstacle(TargetObstacleID);
            for (TileObject obstacle : ObstacleList){
                WorldPoint TargetObstacleWL= obstacle.getWorldLocation();
                int TargetPlane = obstacle.getPlane();

                if (TargetObstacleWL.distanceTo2D(client.getLocalPlayer().getWorldLocation()) < 10) {

                    double x = obstacle.getClickbox().getBounds().getMaxX();
                    double y = obstacle.getClickbox().getBounds().getMaxY() ;
                    int xx = (int) x + random2(-40,-32);
                    int yy = (int) y + random2(-20,-12);
                    Point adjustedSpotPerspective = new Point (xx,yy);
                    System.out.println(" | click obstacle | " + " TargetObstacleWL " + TargetObstacleWL + " plane " + TargetPlane
                            + " client plane " + client.getPlane());

                    if(adjustedSpotPerspective.getX()>8 && adjustedSpotPerspective.getX()<1560 && adjustedSpotPerspective.getY()>30
                            && adjustedSpotPerspective.getY()<810 ) {
                        Robot robot = new Robot();
                        moveMouse(robot,client.getMouseCanvasPosition(),adjustedSpotPerspective,10,5,5);
                        leftClick(robot);
                    }	}	}}

        private int i =0;
        private Thread AddonA_BackgroundRun = new Thread(new Runnable() { public void run() {
            for (i =0;i<750;i++){

                System.out.println(i+" | started | ");

                if (config.AgilityAddon() && client.getGameState() == GameState.LOGGED_IN) {

                    System.out.println(i+" | "
                            + " idle time " + idleTimeSecs() + " plane " + client.getPlane()
                            + " idle movement " + checkMovementIdle() + " WL " + client.getLocalPlayer().getWorldLocation()
                            + " canvas " + client.getMouseCanvasPosition() + " animation " + client.getLocalPlayer().getAnimation()
                            + " not moving time " + idleMovementTimeSecs()
                    );

                    if (obstacles.size()!=0){
                        for (TileObject obstacle : obstacles.keySet())
                        {  if (obstacle.getWorldLocation().distanceTo2D(client.getLocalPlayer().getWorldLocation()) < 3) {
                            System.out.println(" LocalObstacles " + obstacle.getId() + " Wl " + obstacle.getWorldLocation()
                            );}}}

                    try { addon();} catch (AWTException | InterruptedException e) { e.printStackTrace();	}

                    randomNPCCheck();
                    if(randomNPCCheck()!=null) {
                        System.out.println(i + " | OHtext " + randomNPCCheck().getOverheadText());
                    }

                    // OHtext Milord Vein X I, please speak to me!
                }
                try {  Thread.sleep(random2(4000,6500));  } catch (InterruptedException e) { e.printStackTrace(); }
            }}});

        // private int CoursePointPassed;
        private TileObject TargetObstacle;

        private WorldPoint PreTree_1 = new WorldPoint(3507,3488,0);
        int object1 = 14843;
        private WorldPoint PostTree_1p = new WorldPoint(3506,3492,2);

        private Rectangle PostTree_1 = new Rectangle (3505,3492,6,7);
        int object2 = 14844;
        private WorldPoint PreGap_2 = new WorldPoint(3506,3497,2);

        private WorldPoint PostGap_2p = new WorldPoint(3502,3504,2);

        private Rectangle PostGap_2 = new Rectangle (3497,3504,8,3);
        int object3 = 14845;
        private WorldPoint PreGap_3 = new WorldPoint(3498,3504,2);
        private WorldPoint PostGap_3p = new WorldPoint(3492,3504,2);

        private Rectangle PostGap_3 = new Rectangle (3486,3499,7,6);
        int object4 = 14848;
        private WorldPoint PreGap_4 = new WorldPoint(3487,3499,2);
        private WorldPoint PostGap_4p = new WorldPoint(3479,3499,3);

        private Rectangle PostGap_4 = new Rectangle (3475,3492,5,8);
        int object5 = 14846;
        private WorldPoint PreGap_5 = new WorldPoint(3478,3493,3);

        private WorldPoint PostGap_5p = new WorldPoint(3478,3486,2);

        private Rectangle PostGap_5 = new Rectangle (3477,3481,8,7);

        private WorldPoint PreVault_6 = new WorldPoint(3479,3483,2);
        int object6 = 14894;
        private WorldPoint PostVault_6p = new WorldPoint(3489,3476,3);

        private Rectangle PostVault_6 = new Rectangle (3488,3469,17,10);

        private WorldPoint PreGap_7 = new WorldPoint(3502,3476,3);
        int object7 = 14847;
        private WorldPoint PostGap_7p = new WorldPoint(3510,3476,2);

        private Rectangle PostGap_7 = new Rectangle (3508,3475,9,10);

        private WorldPoint PreGap_8 = new WorldPoint(3510,3482,2);
        int object8 = 14897;
        private WorldPoint PostGap_8p = new WorldPoint(3510,3485,0);

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

            if (marksOfGrace.size()!=0)
            {
                Tile Mark = marksOfGrace.get(0);
                WorldPoint MarkWL = Mark.getWorldLocation();
                if(MarkWL.distanceTo2D(client.getLocalPlayer().getWorldLocation())<6)
                {
                    Point adjustedSpotPerspective = worldToCanvas(MarkWL);
                    if(adjustedSpotPerspective.getX()>8 && adjustedSpotPerspective.getX()<1500 && adjustedSpotPerspective.getY()>20
                            && adjustedSpotPerspective.getY()<810) {
                        Robot robot = new Robot();
                        moveMouse(robot,client.getMouseCanvasPosition(),adjustedSpotPerspective,10,5,5);
                        leftClick(robot);
                        try {  Thread.sleep(random2(1100,1610));  } catch (InterruptedException e) { e.printStackTrace(); }
                    }}
            }

            double ii = (double) i;
            double factorCheck = ii/100;
            if( factorCheck == (int)factorCheck ){
                //if(BreakTimer()>360){
                System.out.print(" || SLEEPING || ");
                {try {  Thread.sleep(random2(3500,9000));  } catch (InterruptedException e) { e.printStackTrace(); }}}
            //System.out.println(" | check 2 | ");
            while(idleMovementTimeSecs()<6){Thread.sleep(random2(816,1186));}
            //System.out.println(" | check 3 | ");

            if (PreTree_1.distanceTo2D(client.getLocalPlayer().getWorldLocation()) < 5
                    && client.getPlane() == PreTree_1.getPlane() ) {
                System.out.println(" | check zone 1 | ");
                int TargetObstacleID = object1;
                ClickObscureObstacle(TargetObstacleID);
                return;}

            if (PostTree_1.contains(client.getLocalPlayer().getWorldLocation().getX(),
                    client.getLocalPlayer().getWorldLocation().getY())
                    && client.getPlane() == PostTree_1p.getPlane() ) {
                int TargetObstacleID = object2;
                System.out.println(" | check 1 zone 2 | ");
                if(AgilityObstacleCanvasPoint(TargetObstacleID)!=null){

                    Point obstacleCanvas = AgilityObstacleCanvasPoint(TargetObstacleID);
                    System.out.println(" | check 2 zone 2 | ");
                    if( obstacleCanvas.getX()>8 && obstacleCanvas.getX()<1500 && obstacleCanvas.getY()>20
                            && obstacleCanvas.getY()<810 && client.getPlane() == PreGap_2.getPlane() ) {
                        System.out.println(" | check 3 zone 2 | ");

                        ClickObstacle(TargetObstacleID);
                        return;		}}

                WorldPoint StartingZoneRandom = new WorldPoint(PreGap_2.getX() + random2(-2, +2),
                        PreGap_2.getY() + random2(-2, +2), 0);
                walk(StartingZoneRandom);
                return;     }

            if (PostGap_2.contains(client.getLocalPlayer().getWorldLocation().getX(),
                    client.getLocalPlayer().getWorldLocation().getY())
                    && client.getPlane() == PostGap_2p.getPlane() ) {
                int TargetObstacleID = object3;
                if(AgilityObstacleCanvasPoint(TargetObstacleID)!=null){
                    Point obstacleCanvas = AgilityObstacleCanvasPoint(TargetObstacleID);
                    if( obstacleCanvas.getX()>8 && obstacleCanvas.getX()<1500 && obstacleCanvas.getY()>20
                            && obstacleCanvas.getY()<810
                            && client.getPlane() == PreGap_3.getPlane()  ) {
                        System.out.println(" | check zone 3 | ");

                        ClickObstacle(TargetObstacleID);
                        return;}}

                WorldPoint StartingZoneRandom = new WorldPoint(PreGap_3.getX() + random2(-2, +2),
                        PreGap_3.getY() + random2(-2, +2), 0);
                walk(StartingZoneRandom);
                return;     }

            if (PostGap_3.contains(client.getLocalPlayer().getWorldLocation().getX(),
                    client.getLocalPlayer().getWorldLocation().getY())
                    && client.getPlane() == PostGap_3p.getPlane() ) {
                System.out.println(" | check 1 zone 4 | ");
                int TargetObstacleID = object4;
                if(AgilityObstacleCanvasPoint(TargetObstacleID)!=null){
                    System.out.println(" | check 2 zone 4 | ");
                    Point obstacleCanvas = AgilityObstacleCanvasPoint(TargetObstacleID);
                    if( obstacleCanvas.getX()>8 && obstacleCanvas.getX()<1500 && obstacleCanvas.getY()>20
                            && obstacleCanvas.getY()<810
                            && client.getPlane() == PreGap_4.getPlane()  ) {
                        System.out.println(" | check 3a zone 4 | ");

                        ClickObstacle(TargetObstacleID);
                        return;}}
                System.out.println(" | check 3b zone 4 | ");
                WorldPoint StartingZoneRandom = new WorldPoint(PreGap_4.getX() + random2(-1, +1),
                        PreGap_4.getY() + random2(-2, +2), 0);
                walk(StartingZoneRandom);
                return;     }

            if (PostGap_4.contains(client.getLocalPlayer().getWorldLocation().getX(),
                    client.getLocalPlayer().getWorldLocation().getY())
                    && client.getPlane() == PostGap_4p.getPlane() ) {
                System.out.println(" | check 1 zone 5 | ");
                int TargetObstacleID = object5;
                if(AgilityObstacleCanvasPoint(TargetObstacleID)!=null){
                    System.out.println(" | check 2 zone 5 | ");
                    Point obstacleCanvas = AgilityObstacleCanvasPoint(TargetObstacleID);
                    if( obstacleCanvas.getX()>8 && obstacleCanvas.getX()<1500 && obstacleCanvas.getY()>20
                            && obstacleCanvas.getY()<810
                            && client.getPlane() == PreGap_5.getPlane()  ) {
                        System.out.println(" | check 3a zone 5 | ");

                        ClickObstacle(TargetObstacleID);
                        return;}}
                System.out.println(" | check 3b zone 5 | ");
                WorldPoint StartingZoneRandom = new WorldPoint(PreGap_5.getX() + random2(-2, +2),
                        PreGap_5.getY() + random2(-2, +2), 0);
                walk(StartingZoneRandom);
                return;     }

            if (PostGap_5.contains(client.getLocalPlayer().getWorldLocation().getX(),
                    client.getLocalPlayer().getWorldLocation().getY())
                    && client.getPlane() == PostGap_5p.getPlane() ) {
                System.out.println(" | check 1 zone 6 | ");
                int TargetObstacleID = object6;
                if(AgilityObstacleCanvasPoint(TargetObstacleID)!=null){
                    System.out.println(" | check 2 zone 6 | ");
                    Point obstacleCanvas = AgilityObstacleCanvasPoint(TargetObstacleID);
                    if( obstacleCanvas.getX()>8 && obstacleCanvas.getX()<1500 && obstacleCanvas.getY()>20
                            && obstacleCanvas.getY()<810
                            && client.getPlane() == PreVault_6.getPlane()  ) {
                        System.out.println(" | check 3a zone 6 | ");

                        ClickObstacle(TargetObstacleID);
                        return;}}

                WorldPoint StartingZoneRandom = new WorldPoint(PreVault_6.getX() + random2(-2, +2),
                        PreVault_6.getY() + random2(-2, +2), 0);
                walk(StartingZoneRandom);
                return;     }

            if (PostVault_6.contains(client.getLocalPlayer().getWorldLocation().getX(),
                    client.getLocalPlayer().getWorldLocation().getY())
                    && client.getPlane() == PostVault_6p.getPlane() ) {
                int TargetObstacleID = object7;
                if(AgilityObstacleCanvasPoint(TargetObstacleID)!=null){
                    Point obstacleCanvas = AgilityObstacleCanvasPoint(TargetObstacleID);
                    if( obstacleCanvas.getX()>8 && obstacleCanvas.getX()<1500 && obstacleCanvas.getY()>20
                            && obstacleCanvas.getY()<810
                            && client.getPlane() == PreGap_7.getPlane()  ) {
                        System.out.println(" | check zone 7 | ");

                        // + 23139
                        ClickObstacle(TargetObstacleID);
                        return;}}

                WorldPoint StartingZoneRandom = new WorldPoint(PreGap_7.getX() + random2(-2, +2),
                        PreGap_7.getY() + random2(-2, +2), 0);
                walk(StartingZoneRandom);
                return;     }

            if (PostGap_7.contains(client.getLocalPlayer().getWorldLocation().getX(),
                    client.getLocalPlayer().getWorldLocation().getY())
                    && client.getPlane() == PostGap_7p.getPlane() ) {
                int TargetObstacleID = object8;
                if(AgilityObstacleCanvasPoint(TargetObstacleID)!=null){
                    Point obstacleCanvas = AgilityObstacleCanvasPoint(TargetObstacleID);
                    if( obstacleCanvas.getX()>8 && obstacleCanvas.getX()<1500 && obstacleCanvas.getY()>20
                            && obstacleCanvas.getY()<810
                            && client.getPlane() == PreGap_8.getPlane()  ) {
                        System.out.println(" | check zone 8 | ");

                        // + 23139
                        ClickObstacle(TargetObstacleID);
                        return;}}

                WorldPoint StartingZoneRandom = new WorldPoint(PreGap_8.getX() + random2(-2, +2),
                        PreGap_8.getY() + random2(-2, +2), 0);
                walk(StartingZoneRandom);
                return;     }

            if (PostGap_8p.distanceTo2D(client.getLocalPlayer().getWorldLocation()) < 3
                    && client.getPlane() == PostGap_8p.getPlane() ) {
                WorldPoint StartingZoneRandom = new WorldPoint(PreTree_1.getX() + random2(-2, +2),
                        PreTree_1.getY() + random2(-2, +2), 0);
                walk(StartingZoneRandom);
                return;     }

            if (PreTree_1.distanceTo2D(client.getLocalPlayer().getWorldLocation()) >= 5
                    && client.getPlane() == PreTree_1.getPlane() ) {
                WorldPoint StartingZoneRandom = new WorldPoint(PreTree_1.getX() + random2(-2, +2),
                        PreTree_1.getY() + random2(-2, +2), 0);
                walk(StartingZoneRandom);
                return;	}

        }

        private Point worldToCanvas(WorldPoint worldpoint){
            LocalPoint targetLL = LocalPoint.fromWorld(client, worldpoint.getX(), worldpoint.getY());
            Point perspective = Perspective.localToCanvas(client, targetLL, client.getPlane());
            Point adjustedPerspective = new Point(perspective.getX() + random2(-2,2) + 0, perspective.getY() + random2(-2,2) + 15);
            return adjustedPerspective;}

        private Point worldToMiniMap(WorldPoint worldpoint) {
            LocalPoint targetLL = LocalPoint.fromWorld(client, worldpoint.getX(), worldpoint.getY());
            if (targetLL != null) {
                Point minimapPerspective = Perspective.localToMinimap(client, targetLL);
                if (minimapPerspective != null) {
                    Point adjustedMinimapPerspective = new Point(minimapPerspective.getX() + random2(-1,1) +4,
                            minimapPerspective.getY() + random2(-1,1) + 23);
                    return adjustedMinimapPerspective;
                }}
            return null; }

        private void walk(WorldPoint finalLocation) throws AWTException {
            Robot robot = new Robot();
            int walkX;
            int walk;
            int walkPlane;
            WorldPoint temporaryTarget;
            temporaryTarget = finalLocation;

            Point temporaryTargetPerspective = worldToCanvas(temporaryTarget);
            System.out.println("temporary target 1st " + temporaryTarget + " , " + temporaryTargetPerspective);

            if (temporaryTargetPerspective != null) {
                if (temporaryTargetPerspective.getX() < 1500 && temporaryTargetPerspective.getX() > 10 && temporaryTargetPerspective.getY() > 30
                        && temporaryTargetPerspective.getY() < 830) {

                    moveMouse(robot, client.getMouseCanvasPosition(), temporaryTargetPerspective, 10, 5, 5);
                    leftClick(robot);
                    return;
                }
            }

            temporaryTargetPerspective = worldToMiniMap(temporaryTarget);
            if (temporaryTargetPerspective != null) {
                if (temporaryTargetPerspective.getX() > 1500 && temporaryTargetPerspective.getX() < 1620 && temporaryTargetPerspective.getY() > 30
                        && temporaryTargetPerspective.getY() < 130) {

                    moveMouse(robot, client.getMouseCanvasPosition(), temporaryTargetPerspective, 10, 5, 5);
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

            while (temporaryTargetPerspective.getX() <= 1500 || temporaryTargetPerspective.getX() >= 1620
                    || temporaryTargetPerspective.getY() <= 30 || temporaryTargetPerspective.getY() >= 130) {

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
            { System.out.println(" NPCList " + npc.getId() + " , " + npc.getIndex() + " , " + npc.getName() );  }}

            for (NPC npc : NPCList)
            {	if (RANDOM_IDS.contains(npc.getId()))
            {	activeRandom.add(npc);	}}
            if(activeRandom.size()!=0) {
                for (NPC random : activeRandom){
                    if (random.getInteracting()!=null){
                        if (random.getInteracting().getName()!=null){
                            if (random.getInteracting().getName().contains("Vein")){
                                return random;
                            }}}
                    if (random.getOverheadText()!=null ){
                        if (random.getOverheadText().contains("Vein")){
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


    }


}
