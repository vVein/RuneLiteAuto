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
package Polished.Scripts.PreNovember2019;

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
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;

import static net.runelite.api.ItemID.AGILITY_ARENA_TICKET;
import static net.runelite.api.Skill.AGILITY;

import net.runelite.api.Point;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.BoostedLevelChanged;
import net.runelite.api.events.ConfigChanged;
import net.runelite.api.events.DecorativeObjectChanged;
import net.runelite.api.events.DecorativeObjectDespawned;
import net.runelite.api.events.DecorativeObjectSpawned;
import net.runelite.api.events.ExperienceChanged;
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
        AddonA_BackgroundRun.start();
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
    public void onExperienceChanged(ExperienceChanged event)
    {
        if (event.getSkill() != AGILITY || !config.showLapCount())
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
    public void onBoostedLevelChanged(BoostedLevelChanged boostedLevelChanged)
    {
        Skill skill = boostedLevelChanged.getSkill();
        if (skill == AGILITY)
        {
            agilityLevel = client.getBoostedSkillLevel(skill);
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
                //+ random2Dbl(-obstacle.getClickbox().getBounds().getWidth()/2,obstacle.getClickbox().getBounds().getWidth()/2);
                double y = obstacle.getClickbox().getBounds().getCenterY() ;
                // + random2Dbl(-obstacle.getClickbox().getBounds().getHeight()/2,obstacle.getClickbox().getBounds().getHeight()/2);
                int xx = (int) x + random2(-2,2);
                int yy = (int) y + random2(-2,2);
                Point adjustedSpotPerspective = new Point (xx,yy);
                System.out.println(" | click obstacle | " + " TargetObstacleWL " + TargetObstacleWL + " plane " + TargetPlane
                        + " client plane " + client.getPlane());

                if(adjustedSpotPerspective.getX()>8 && adjustedSpotPerspective.getX()<1560 && adjustedSpotPerspective.getY()>30
                        && adjustedSpotPerspective.getY()<810) {
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
                int xx = (int) x + random2(-2,2);
                int yy = (int) y + random2(-2,2);
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

    private int i =0;
    private Thread AddonA_BackgroundRun = new Thread(new Runnable() { public void run() {
        for (i =0;i<500;i++){

            System.out.println(i+" | started | ");

            if (config.Addon_A() && client.getGameState() == GameState.LOGGED_IN) {

                System.out.println(i+" | "
                        + " idle time " + idleTimeSecs() + " plane " + client.getPlane()
                        + " idle movement " + checkMovementIdle() + " WL " + client.getLocalPlayer().getWorldLocation()
                        + " canvas " + client.getMouseCanvasPosition() + " animation " + client.getLocalPlayer().getAnimation()
                        + " not moving time " + idleMovementTimeSecs()
                );

                try { addon();} catch (AWTException | InterruptedException e) { e.printStackTrace();	}

                randomNPCCheck();
                if(randomNPCCheck()!=null) {
                    System.out.println(i + " | OHtext " + randomNPCCheck().getOverheadText());
                }

                // OHtext Milord Vein X I, please speak to me!
            }
            try {  Thread.sleep(random2(6000,14000));  } catch (InterruptedException e) { e.printStackTrace(); }
        }}});

    // private int CoursePointPassed;
    private TileObject TargetObstacle;
    private WorldPoint StartingZone_1 = new WorldPoint(2474,3436,0);
    private WorldPoint PreObstacleNet_2 = new WorldPoint(2473,3427,0);
    private WorldPoint PreTreeBranch_3 = new WorldPoint(2473,3423,1);
    private WorldPoint PreBalancingRope_4 = new WorldPoint(2475,3420,2);
    private WorldPoint PreTreeBranch_5 = new WorldPoint(2484,3420,2);
    private WorldPoint PreObstacleNet_6 = new WorldPoint(2486,3422,0);
    private WorldPoint PreObstaclePipe_7 = new WorldPoint(2486,3429,0);
    private WorldPoint ObstaclePipe_7 = new WorldPoint(2486,3429,0);

    //| click obstacle |  TargetObstacleWL WorldPoint(x=2487, y=3432, plane=0) plane 0 client plane 0
    //	| click obstacle |  TargetObstacleWL WorldPoint(x=2484, y=3432, plane=0) plane 0 client plane 0

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
        while(idleMovementTimeSecs()<6){Thread.sleep(random2(816,1186));}
        //System.out.println(" | check 3 | ");

        if (StartingZone_1.distanceTo2D(client.getLocalPlayer().getWorldLocation()) < 5
                && client.getPlane() == StartingZone_1.getPlane() ) {
            System.out.println(" | check zone 1 | ");
            int TargetObstacleID = 23145;
            ClickObstacle(TargetObstacleID);

            return;}

        if( PreObstacleNet_2.distanceTo2D(client.getLocalPlayer().getWorldLocation()) < 5
                && client.getPlane() == PreObstacleNet_2.getPlane() ) {
            System.out.println(" | check zone 2 | ");
            int TargetObstacleID = 23134;
            ClickObstacle(TargetObstacleID);
            return;		}

        if( PreTreeBranch_3.distanceTo2D(client.getLocalPlayer().getWorldLocation()) < 5
                && client.getPlane() == PreTreeBranch_3.getPlane()  ) {
            System.out.println(" | check zone 3 | ");
            int TargetObstacleID = 23559;
            ClickObstacle(TargetObstacleID);
            return;}

        if(PreBalancingRope_4.distanceTo2D(client.getLocalPlayer().getWorldLocation()) < 5
                && client.getPlane() == PreBalancingRope_4.getPlane()  ) {
            System.out.println(" | check zone 4 | ");
            int TargetObstacleID = 23557;
            ClickObstacle(TargetObstacleID);
            return;}

        if( PreTreeBranch_5.distanceTo2D(client.getLocalPlayer().getWorldLocation()) < 5
                && client.getPlane() == PreTreeBranch_5.getPlane()  ) {
            System.out.println(" | check zone 5 | ");
            int TargetObstacleID = 23560;
            ClickObstacle(TargetObstacleID);
            return;}

        if( PreObstacleNet_6.distanceTo2D(client.getLocalPlayer().getWorldLocation()) < 5
                && client.getPlane() == PreObstacleNet_6.getPlane()  ) {
            System.out.println(" | check zone 6 | ");
            int TargetObstacleID = 23135;
            ClickObstacle(TargetObstacleID);
            return;}

        if( PreObstaclePipe_7.distanceTo2D(client.getLocalPlayer().getWorldLocation()) < 5
                && client.getPlane() == PreObstaclePipe_7.getPlane()  ) {
            System.out.println(" | check zone 7 | ");
            int TargetObstacleID = 23138;
            int TargetObstacleID2 = 23139;
            // + 23139
            ClickObstacle(TargetObstacleID, TargetObstacleID2);
            return;}

        if (StartingZone_1.distanceTo2D(client.getLocalPlayer().getWorldLocation()) >= 5
                && client.getPlane() == StartingZone_1.getPlane() ) {
            WorldPoint StartingZoneRandom = new WorldPoint(2474 + random2(-2, +2), 3436 + random2(-2, +2), 0);
            walk(StartingZoneRandom);
            return;
        }

    }

    private Point worldToCanvas(WorldPoint worldpoint){
        LocalPoint targetLL = LocalPoint.fromWorld(client, worldpoint.getX(), worldpoint.getY());
        Point perspective = Perspective.localToCanvas(client, targetLL, client.getPlane());
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

    private final Set<Integer> RANDOM_IDS = ImmutableSet.of(NpcID.BEE_KEEPER, NpcID.BEE_KEEPER_6747,
            NpcID.CAPT_ARNAV, NpcID.NILES, NpcID.MILES, NpcID.GILES, NpcID.SERGEANT_DAMIEN,
            NpcID.DRUNKEN_DWARF, NpcID.FREAKY_FORESTER, NpcID.FROG,NpcID.FROG_5429,NpcID.FROG_479,
            NpcID.FROG_3290, NpcID.FROG_5430,NpcID.FROG_5431,NpcID.FROG_5432,NpcID.FROG_5833,NpcID.FROG_8702,
            NpcID.GENIE,NpcID.GENIE_327,NpcID.GENIE_4738,
            NpcID.EVIL_BOB,
            NpcID.POSTIE_PETE,NpcID.LEO,NpcID.DR_JEKYLL,
            NpcID.MYSTERIOUS_OLD_MAN,NpcID.MYSTERIOUS_OLD_MAN_6742,	NpcID.MYSTERIOUS_OLD_MAN_6750,
            NpcID.MYSTERIOUS_OLD_MAN_6751, NpcID.MYSTERIOUS_OLD_MAN_6752, NpcID.MYSTERIOUS_OLD_MAN_6753,
            NpcID.FLIPPA,NpcID.FLIPPA_6744,
            NpcID.PILLORY_GUARD, NpcID.QUIZ_MASTER, NpcID.RICK_TURPENTINE,NpcID.SANDWICH_LADY,
            NpcID.SECURITY_GUARD,NpcID.STRANGE_PLANT,NpcID.DUNCE);

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

}
