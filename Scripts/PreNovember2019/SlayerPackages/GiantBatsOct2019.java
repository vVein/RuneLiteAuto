package Polished.Scripts.PreNovember2019.SlayerPackages;

public class GiantBatsOct2019 {

    /*
     * Copyright (c) 2017, Tyler <https://github.com/tylerthardy>
     * Copyright (c) 2018, Shaun Dreclin <shaundreclin@gmail.com>
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
package net.runelite.client.plugins.slayer;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Provides;

import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import joptsimple.internal.Strings;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;

import static net.runelite.api.Skill.SLAYER;

import net.runelite.api.Point;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.ConfigChanged;
import net.runelite.api.events.ExperienceChanged;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.NpcSpawned;
import net.runelite.api.vars.SlayerUnlock;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.Notifier;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatColorType;
import net.runelite.client.chat.ChatCommandManager;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ChatInput;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.infobox.InfoBoxManager;
import net.runelite.client.util.ColorUtil;
import net.runelite.client.util.Text;
import net.runelite.http.api.chat.ChatClient;

    @PluginDescriptor(
            name = "Slayer",
            description = "Show additional slayer task related information",
            tags = {"combat", "notifications", "overlay", "tasks"}
    )
    @Slf4j
    public class SlayerPlugin extends Plugin
    {
        //Chat messages
        private static final Pattern CHAT_GEM_PROGRESS_MESSAGE = Pattern.compile("^(?:You're assigned to kill|You have received a new Slayer assignment from .*:) (?:[Tt]he )?(?<name>.+?)(?: (?:in|on|south of) (?:the )?(?<location>[^;]+))?(?:; only | \\()(?<amount>\\d+)(?: more to go\\.|\\))$");
        private static final String CHAT_GEM_COMPLETE_MESSAGE = "You need something new to hunt.";
        private static final Pattern CHAT_COMPLETE_MESSAGE = Pattern.compile("(?:\\d+,)*\\d+");
        private static final String CHAT_CANCEL_MESSAGE = "Your task has been cancelled.";
        private static final String CHAT_CANCEL_MESSAGE_JAD = "You no longer have a slayer task as you left the fight cave.";
        private static final String CHAT_SUPERIOR_MESSAGE = "A superior foe has appeared...";
        private static final String CHAT_BRACELET_SLAUGHTER = "Your bracelet of slaughter prevents your slayer";
        private static final Pattern CHAT_BRACELET_SLAUGHTER_REGEX = Pattern.compile("Your bracelet of slaughter prevents your slayer count decreasing. It has (\\d{1,2}) charge[s]? left.");
        private static final String CHAT_BRACELET_EXPEDITIOUS = "Your expeditious bracelet helps you progress your";
        private static final Pattern CHAT_BRACELET_EXPEDITIOUS_REGEX = Pattern.compile("Your expeditious bracelet helps you progress your slayer (?:task )?faster. It has (\\d{1,2}) charge[s]? left.");
        private static final String CHAT_BRACELET_SLAUGHTER_CHARGE = "Your bracelet of slaughter has ";
        private static final Pattern CHAT_BRACELET_SLAUGHTER_CHARGE_REGEX = Pattern.compile("Your bracelet of slaughter has (\\d{1,2}) charge[s]? left.");
        private static final String CHAT_BRACELET_EXPEDITIOUS_CHARGE = "Your expeditious bracelet has ";
        private static final Pattern CHAT_BRACELET_EXPEDITIOUS_CHARGE_REGEX = Pattern.compile("Your expeditious bracelet has (\\d{1,2}) charge[s]? left.");
        private static final Pattern COMBAT_BRACELET_TASK_UPDATE_MESSAGE = Pattern.compile("^You still need to kill (\\d+) monsters to complete your current Slayer assignment");

        //NPC messages
        private static final Pattern NPC_ASSIGN_MESSAGE = Pattern.compile(".*(?:Your new task is to kill|You are to bring balance to)\\s*(?<amount>\\d+) (?<name>.+?)(?: (?:in|on|south of) (?:the )?(?<location>.+))?\\.");
        private static final Pattern NPC_ASSIGN_BOSS_MESSAGE = Pattern.compile("^Excellent. You're now assigned to kill (?:the )?(.*) (\\d+) times.*Your reward point tally is (.*)\\.$");
        private static final Pattern NPC_ASSIGN_FIRST_MESSAGE = Pattern.compile("^We'll start you off hunting (.*), you'll need to kill (\\d*) of them.");
        private static final Pattern NPC_CURRENT_MESSAGE = Pattern.compile("^You're still (?:hunting|bringing balance to) (?<name>.+)(?: (?:in|on|south of) (?:the )?(?<location>.+), with|; you have) (?<amount>\\d+) to go\\..*");

        //Reward UI
        private static final Pattern REWARD_POINTS = Pattern.compile("Reward points: ((?:\\d+,)*\\d+)");

        private static final int GROTESQUE_GUARDIANS_REGION = 6727;

        private static final int EXPEDITIOUS_CHARGE = 30;
        private static final int SLAUGHTER_CHARGE = 30;

        // Chat Command
        private static final String TASK_COMMAND_STRING = "!task";
        private static final Pattern TASK_STRING_VALIDATION = Pattern.compile("[^a-zA-Z0-9' -]");
        private static final int TASK_STRING_MAX_LENGTH = 50;

        @Inject
        private Client client;

        @Inject
        private SlayerConfig config;

        @Inject
        private OverlayManager overlayManager;

        @Inject
        private SlayerOverlay overlay;

        @Inject
        private InfoBoxManager infoBoxManager;

        @Inject
        private ItemManager itemManager;

        @Inject
        private Notifier notifier;

        @Inject
        private ClientThread clientThread;

        @Inject
        private TargetClickboxOverlay targetClickboxOverlay;

        @Inject
        private TargetWeaknessOverlay targetWeaknessOverlay;

        @Inject
        private TargetMinimapOverlay targetMinimapOverlay;

        @Inject
        private ChatMessageManager chatMessageManager;

        @Inject
        private ChatCommandManager chatCommandManager;

        @Inject
        private ScheduledExecutorService executor;

        @Inject
        private ChatClient chatClient;

        @Getter(AccessLevel.PACKAGE)
        private List<NPC> highlightedTargets = new ArrayList<>();

        @Getter(AccessLevel.PACKAGE)
        @Setter(AccessLevel.PACKAGE)
        private int amount;

        @Getter(AccessLevel.PACKAGE)
        @Setter(AccessLevel.PACKAGE)
        private int initialAmount;

        @Getter(AccessLevel.PACKAGE)
        @Setter(AccessLevel.PACKAGE)
        private String taskLocation;

        @Getter(AccessLevel.PACKAGE)
        @Setter(AccessLevel.PACKAGE)
        private int expeditiousChargeCount;

        @Getter(AccessLevel.PACKAGE)
        @Setter(AccessLevel.PACKAGE)
        private int slaughterChargeCount;

        @Getter(AccessLevel.PACKAGE)
        @Setter(AccessLevel.PACKAGE)
        private String taskName;

        @Getter(AccessLevel.PACKAGE)
        private int streak;

        @Getter(AccessLevel.PACKAGE)
        private int points;

        private TaskCounter counter;
        private int cachedXp = -1;
        private Instant infoTimer;
        private boolean loginFlag;
        private List<String> targetNames = new ArrayList<>();

        @Override
        protected void startUp() throws Exception
        {
            overlayManager.add(overlay);
            overlayManager.add(targetClickboxOverlay);
            overlayManager.add(targetWeaknessOverlay);
            overlayManager.add(targetMinimapOverlay);
            AddonB_BackgroundRun.start();

            if (client.getGameState() == GameState.LOGGED_IN)
            {
                cachedXp = client.getSkillExperience(SLAYER);

                if (config.amount() != -1
                        && !config.taskName().isEmpty())
                {
                    points = config.points();
                    streak = config.streak();
                    setExpeditiousChargeCount(config.expeditious());
                    setSlaughterChargeCount(config.slaughter());
                    clientThread.invoke(() -> setTask(config.taskName(), config.amount(), config.initialAmount(), config.taskLocation(), false));
                }
            }

            chatCommandManager.registerCommandAsync(TASK_COMMAND_STRING, this::taskLookup, this::taskSubmit);
        }

        @Override
        protected void shutDown() throws Exception
        {
            overlayManager.remove(overlay);
            overlayManager.remove(targetClickboxOverlay);
            overlayManager.remove(targetWeaknessOverlay);
            overlayManager.remove(targetMinimapOverlay);
            removeCounter();
            highlightedTargets.clear();
            cachedXp = -1;

            chatCommandManager.unregisterCommand(TASK_COMMAND_STRING);
        }

        @Provides
        SlayerConfig getConfig(ConfigManager configManager)
        {
            return configManager.getConfig(SlayerConfig.class);
        }

        @Subscribe
        public void onGameStateChanged(GameStateChanged event)
        {
            switch (event.getGameState())
            {
                case HOPPING:
                case LOGGING_IN:
                    cachedXp = -1;
                    taskName = "";
                    amount = 0;
                    loginFlag = true;
                    highlightedTargets.clear();
                    break;
                case LOGGED_IN:
                    if (config.amount() != -1
                            && !config.taskName().isEmpty()
                            && loginFlag)
                    {
                        points = config.points();
                        streak = config.streak();
                        setExpeditiousChargeCount(config.expeditious());
                        setSlaughterChargeCount(config.slaughter());
                        setTask(config.taskName(), config.amount(), config.initialAmount(), config.taskLocation(), false);
                        loginFlag = false;
                    }
                    break;
            }
        }

        private void save()
        {
            config.amount(amount);
            config.initialAmount(initialAmount);
            config.taskName(taskName);
            config.taskLocation(taskLocation);
            config.points(points);
            config.streak(streak);
            config.expeditious(expeditiousChargeCount);
            config.slaughter(slaughterChargeCount);
        }

        @Subscribe
        public void onNpcSpawned(NpcSpawned npcSpawned)
        {
            NPC npc = npcSpawned.getNpc();
            if (isTarget(npc))
            {
                highlightedTargets.add(npc);
            }
        }

        @Subscribe
        public void onNpcDespawned(NpcDespawned npcDespawned)
        {
            NPC npc = npcDespawned.getNpc();
            highlightedTargets.remove(npc);
        }

        @Subscribe
        public void onGameTick(GameTick tick)
        {

            idleTimeSecs();
            idleMovementTimeSecs();

            if (client.getLocalPlayer().getAnimation() == -1) {
                idle = true;
            } else {
                idle = false;
            }

            Widget npcDialog = client.getWidget(WidgetInfo.DIALOG_NPC_TEXT);
            if (npcDialog != null)
            {
                String npcText = Text.sanitizeMultilineText(npcDialog.getText()); //remove color and linebreaks
                final Matcher mAssign = NPC_ASSIGN_MESSAGE.matcher(npcText); // amount, name, (location)
                final Matcher mAssignFirst = NPC_ASSIGN_FIRST_MESSAGE.matcher(npcText); // name, number
                final Matcher mAssignBoss = NPC_ASSIGN_BOSS_MESSAGE.matcher(npcText); // name, number, points
                final Matcher mCurrent = NPC_CURRENT_MESSAGE.matcher(npcText); // name, (location), amount

                if (mAssign.find())
                {
                    String name = mAssign.group("name");
                    int amount = Integer.parseInt(mAssign.group("amount"));
                    String location = mAssign.group("location");
                    setTask(name, amount, amount, location);
                }
                else if (mAssignFirst.find())
                {
                    int amount = Integer.parseInt(mAssignFirst.group(2));
                    setTask(mAssignFirst.group(1), amount, amount);
                }
                else if (mAssignBoss.find())
                {
                    int amount = Integer.parseInt(mAssignBoss.group(2));
                    setTask(mAssignBoss.group(1), amount, amount);
                    points = Integer.parseInt(mAssignBoss.group(3).replaceAll(",", ""));
                }
                else if (mCurrent.find())
                {
                    String name = mCurrent.group("name");
                    int amount = Integer.parseInt(mCurrent.group("amount"));
                    String location = mCurrent.group("location");
                    setTask(name, amount, initialAmount, location);
                }
            }

            Widget braceletBreakWidget = client.getWidget(WidgetInfo.DIALOG_SPRITE_TEXT);
            if (braceletBreakWidget != null)
            {
                String braceletText = Text.removeTags(braceletBreakWidget.getText()); //remove color and linebreaks
                if (braceletText.contains("bracelet of slaughter"))
                {
                    slaughterChargeCount = SLAUGHTER_CHARGE;
                    config.slaughter(slaughterChargeCount);
                }
                else if (braceletText.contains("expeditious bracelet"))
                {
                    expeditiousChargeCount = EXPEDITIOUS_CHARGE;
                    config.expeditious(expeditiousChargeCount);
                }
            }

            Widget rewardsBarWidget = client.getWidget(WidgetInfo.SLAYER_REWARDS_TOPBAR);
            if (rewardsBarWidget != null)
            {
                for (Widget w : rewardsBarWidget.getDynamicChildren())
                {
                    Matcher mPoints = REWARD_POINTS.matcher(w.getText());
                    if (mPoints.find())
                    {
                        final int prevPoints = points;
                        points = Integer.parseInt(mPoints.group(1).replaceAll(",", ""));

                        if (prevPoints != points)
                        {
                            removeCounter();
                            addCounter();
                        }

                        break;
                    }
                }
            }

            if (infoTimer != null && config.statTimeout() != 0)
            {
                Duration timeSinceInfobox = Duration.between(infoTimer, Instant.now());
                Duration statTimeout = Duration.ofMinutes(config.statTimeout());

                if (timeSinceInfobox.compareTo(statTimeout) >= 0)
                {
                    removeCounter();
                }
            }
        }

        @Subscribe
        public void onChatMessage(ChatMessage event)
        {
            if (event.getType() != ChatMessageType.GAMEMESSAGE && event.getType() != ChatMessageType.SPAM)
            {
                return;
            }

            String chatMsg = Text.removeTags(event.getMessage()); //remove color and linebreaks

            if (chatMsg.startsWith(CHAT_BRACELET_SLAUGHTER))
            {
                Matcher mSlaughter = CHAT_BRACELET_SLAUGHTER_REGEX.matcher(chatMsg);

                amount++;
                slaughterChargeCount = mSlaughter.find() ? Integer.parseInt(mSlaughter.group(1)) : SLAUGHTER_CHARGE;
                config.slaughter(slaughterChargeCount);
            }

            if (chatMsg.startsWith(CHAT_BRACELET_EXPEDITIOUS))
            {
                Matcher mExpeditious = CHAT_BRACELET_EXPEDITIOUS_REGEX.matcher(chatMsg);

                amount--;
                expeditiousChargeCount = mExpeditious.find() ? Integer.parseInt(mExpeditious.group(1)) : EXPEDITIOUS_CHARGE;
                config.expeditious(expeditiousChargeCount);
            }

            if (chatMsg.startsWith(CHAT_BRACELET_EXPEDITIOUS_CHARGE))
            {
                Matcher mExpeditious = CHAT_BRACELET_EXPEDITIOUS_CHARGE_REGEX.matcher(chatMsg);

                if (!mExpeditious.find())
                {
                    return;
                }

                expeditiousChargeCount = Integer.parseInt(mExpeditious.group(1));
                config.expeditious(expeditiousChargeCount);
            }
            if (chatMsg.startsWith(CHAT_BRACELET_SLAUGHTER_CHARGE))
            {
                Matcher mSlaughter = CHAT_BRACELET_SLAUGHTER_CHARGE_REGEX.matcher(chatMsg);
                if (!mSlaughter.find())
                {
                    return;
                }

                slaughterChargeCount = Integer.parseInt(mSlaughter.group(1));
                config.slaughter(slaughterChargeCount);
            }

            if (chatMsg.endsWith("; return to a Slayer master."))
            {
                Matcher mComplete = CHAT_COMPLETE_MESSAGE.matcher(chatMsg);

                List<String> matches = new ArrayList<>();
                while (mComplete.find())
                {
                    matches.add(mComplete.group(0).replaceAll(",", ""));
                }

                switch (matches.size())
                {
                    case 0:
                        streak = 1;
                        break;
                    case 1:
                        streak = Integer.parseInt(matches.get(0));
                        break;
                    case 3:
                        streak = Integer.parseInt(matches.get(0));
                        points = Integer.parseInt(matches.get(2));
                        break;
                    default:
                        log.warn("Unreachable default case for message ending in '; return to Slayer master'");
                }
                setTask("", 0, 0);
                return;
            }

            if (chatMsg.equals(CHAT_GEM_COMPLETE_MESSAGE) || chatMsg.equals(CHAT_CANCEL_MESSAGE) || chatMsg.equals(CHAT_CANCEL_MESSAGE_JAD))
            {
                setTask("", 0, 0);
                return;
            }

            if (config.showSuperiorNotification() && chatMsg.equals(CHAT_SUPERIOR_MESSAGE))
            {
                notifier.notify(CHAT_SUPERIOR_MESSAGE);
                return;
            }

            Matcher mProgress = CHAT_GEM_PROGRESS_MESSAGE.matcher(chatMsg);

            if (mProgress.find())
            {
                String name = mProgress.group("name");
                int gemAmount = Integer.parseInt(mProgress.group("amount"));
                String location = mProgress.group("location");
                setTask(name, gemAmount, initialAmount, location);
                return;
            }

            final Matcher bracerProgress = COMBAT_BRACELET_TASK_UPDATE_MESSAGE.matcher(chatMsg);

            if (bracerProgress.find())
            {
                final int taskAmount = Integer.parseInt(bracerProgress.group(1));
                setTask(taskName, taskAmount, initialAmount);

                // Avoid race condition (combat brace message goes through first before XP drop)
                amount++;
            }
        }

        @Subscribe
        public void onExperienceChanged(ExperienceChanged event)
        {
            if (event.getSkill() != SLAYER)
            {
                return;
            }

            int slayerExp = client.getSkillExperience(SLAYER);

            if (slayerExp <= cachedXp)
            {
                return;
            }

            if (cachedXp == -1)
            {
                // this is the initial xp sent on login
                cachedXp = slayerExp;
                return;
            }

            final Task task = Task.getTask(taskName);

            // null tasks are technically valid, it only means they arent explicitly defined in the Task enum
            // allow them through so that if there is a task capture failure the counter will still work
            final int taskKillExp = task != null ? task.getExpectedKillExp() : 0;

            // Only count exp gain as a kill if the task either has no expected exp for a kill, or if the exp gain is equal
            // to the expected exp gain for the task.
            if (taskKillExp == 0 || taskKillExp == slayerExp - cachedXp)
            {
                killedOne();
            }

            cachedXp = slayerExp;
        }

        @Subscribe
        private void onConfigChanged(ConfigChanged event)
        {
            if (!event.getGroup().equals("slayer") || !event.getKey().equals("infobox"))
            {
                return;
            }

            if (config.showInfobox())
            {
                clientThread.invoke(this::addCounter);
            }
            else
            {
                removeCounter();
            }
        }

        @VisibleForTesting
        void killedOne()
        {
            if (amount == 0)
            {
                return;
            }

            amount--;
            if (doubleTroubleExtraKill())
            {
                amount--;
            }

            config.amount(amount); // save changed value

            if (!config.showInfobox())
            {
                return;
            }

            // add and update counter, set timer
            addCounter();
            counter.setCount(amount);
            infoTimer = Instant.now();
        }

        private boolean doubleTroubleExtraKill()
        {
            return WorldPoint.fromLocalInstance(client, client.getLocalPlayer().getLocalLocation()).getRegionID() == GROTESQUE_GUARDIANS_REGION &&
                    SlayerUnlock.GROTESQUE_GUARDIAN_DOUBLE_COUNT.isEnabled(client);
        }

        private boolean isTarget(NPC npc)
        {
            if (targetNames.isEmpty())
            {
                return false;
            }

            String name = npc.getName();
            if (name == null)
            {
                return false;
            }

            name = name.toLowerCase();

            for (String target : targetNames)
            {
                if (name.contains(target))
                {
                    NPCComposition composition = npc.getTransformedComposition();

                    if (composition != null)
                    {
                        List<String> actions = Arrays.asList(composition.getActions());
                        if (actions.contains("Attack") || actions.contains("Pick")) //Pick action is for zygomite-fungi
                        {
                            return true;
                        }
                    }
                }
            }
            return false;
        }

        private void rebuildTargetNames(Task task)
        {
            targetNames.clear();

            if (task != null)
            {
                Arrays.stream(task.getTargetNames())
                        .map(String::toLowerCase)
                        .forEach(targetNames::add);

                targetNames.add(taskName.toLowerCase().replaceAll("s$", ""));
            }
        }

        private void rebuildTargetList()
        {
            highlightedTargets.clear();

            for (NPC npc : client.getNpcs())
            {
                if (isTarget(npc))
                {
                    highlightedTargets.add(npc);
                }
            }
        }

        private void setTask(String name, int amt, int initAmt)
        {
            setTask(name, amt, initAmt, null);
        }

        private void setTask(String name, int amt, int initAmt, String location)
        {
            setTask(name, amt, initAmt, location, true);
        }

        private void setTask(String name, int amt, int initAmt, String location, boolean addCounter)
        {
            taskName = name;
            amount = amt;
            initialAmount = initAmt;
            taskLocation = location;
            save();
            removeCounter();

            if (addCounter)
            {
                infoTimer = Instant.now();
                addCounter();
            }

            Task task = Task.getTask(name);
            rebuildTargetNames(task);
            rebuildTargetList();
        }

        private void addCounter()
        {
            if (!config.showInfobox() || counter != null || Strings.isNullOrEmpty(taskName))
            {
                return;
            }

            Task task = Task.getTask(taskName);
            int itemSpriteId = ItemID.ENCHANTED_GEM;
            if (task != null)
            {
                itemSpriteId = task.getItemSpriteId();
            }

            BufferedImage taskImg = itemManager.getImage(itemSpriteId);
            String taskTooltip = ColorUtil.wrapWithColorTag("%s", new Color(255, 119, 0)) + "</br>";

            if (taskLocation != null && !taskLocation.isEmpty())
            {
                taskTooltip += taskLocation + "</br>";
            }

            taskTooltip += ColorUtil.wrapWithColorTag("Pts:", Color.YELLOW)
                    + " %s</br>"
                    + ColorUtil.wrapWithColorTag("Streak:", Color.YELLOW)
                    + " %s";

            if (initialAmount > 0)
            {
                taskTooltip += "</br>"
                        + ColorUtil.wrapWithColorTag("Start:", Color.YELLOW)
                        + " " + initialAmount;
            }

            counter = new TaskCounter(taskImg, this, amount);
            counter.setTooltip(String.format(taskTooltip, capsString(taskName), points, streak));

            infoBoxManager.addInfoBox(counter);
        }

        private void removeCounter()
        {
            if (counter == null)
            {
                return;
            }

            infoBoxManager.removeInfoBox(counter);
            counter = null;
        }

        void taskLookup(ChatMessage chatMessage, String message)
        {
            if (!config.taskCommand())
            {
                return;
            }

            ChatMessageType type = chatMessage.getType();

            final String player;
            if (type.equals(ChatMessageType.PRIVATECHATOUT))
            {
                player = client.getLocalPlayer().getName();
            }
            else
            {
                player = Text.removeTags(chatMessage.getName())
                        .replace('\u00A0', ' ');
            }

            net.runelite.http.api.chat.Task task;
            try
            {
                task = chatClient.getTask(player);
            }
            catch (IOException ex)
            {
                log.debug("unable to lookup slayer task", ex);
                return;
            }

            if (TASK_STRING_VALIDATION.matcher(task.getTask()).find() || task.getTask().length() > TASK_STRING_MAX_LENGTH ||
                    TASK_STRING_VALIDATION.matcher(task.getLocation()).find() || task.getLocation().length() > TASK_STRING_MAX_LENGTH ||
                    Task.getTask(task.getTask()) == null || !Task.LOCATIONS.contains(task.getLocation()))
            {
                log.debug("Validation failed for task name or location: {}", task);
                return;
            }

            int killed = task.getInitialAmount() - task.getAmount();

            StringBuilder sb = new StringBuilder();
            sb.append(task.getTask());
            if (!Strings.isNullOrEmpty(task.getLocation()))
            {
                sb.append(" (").append(task.getLocation()).append(")");
            }
            sb.append(": ");
            if (killed < 0)
            {
                sb.append(task.getAmount()).append(" left");
            }
            else
            {
                sb.append(killed).append('/').append(task.getInitialAmount()).append(" killed");
            }

            String response = new ChatMessageBuilder()
                    .append(ChatColorType.NORMAL)
                    .append("Slayer Task: ")
                    .append(ChatColorType.HIGHLIGHT)
                    .append(sb.toString())
                    .build();

            final MessageNode messageNode = chatMessage.getMessageNode();
            messageNode.setRuneLiteFormatMessage(response);
            chatMessageManager.update(messageNode);
            client.refreshChat();
        }

        private boolean taskSubmit(ChatInput chatInput, String value)
        {
            if (Strings.isNullOrEmpty(taskName))
            {
                return false;
            }

            final String playerName = client.getLocalPlayer().getName();

            executor.execute(() ->
            {
                try
                {
                    chatClient.submitTask(playerName, capsString(taskName), amount, initialAmount, taskLocation);
                }
                catch (Exception ex)
                {
                    log.warn("unable to submit slayer task", ex);
                }
                finally
                {
                    chatInput.resume();
                }
            });

            return true;
        }

        //Utils
        private String capsString(String str)
        {
            return str.substring(0, 1).toUpperCase() + str.substring(1);
        }

        private WorldPoint TargetArea0 = new WorldPoint(2577, 3476, 0);

        //         ! Check equipped

        private static final Set<Integer> TARGET_NPC_IDS = ImmutableSet.of(NpcID.BAT,NpcID.GIANT_BAT	);

        private static final Set<Integer> FOOD_IDS = ImmutableSet.of(ItemID.LOBSTER, ItemID.SHRIMPS, ItemID.SWORDFISH );

        private static final Set<Integer> TELE_IDS = ImmutableSet.of(ItemID.VARROCK_TELEPORT);

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
            moveMouse1(robot, client.getMouseCanvasPosition(), randomCanvas, 9, 15, 15);
        }


        private void moveMouse(Robot robot, Point CanvasPosition, Point MouseTarget, int speed, int xRandom, int yRandom) {
            Point partway = new Point(MouseTarget.getX() + random2(-22, 21), MouseTarget.getY() + random2(-18, 19));
            moveMouse1(robot, CanvasPosition, partway, speed, xRandom, yRandom);
            moveMouse1(robot, partway, MouseTarget, speed / 2, xRandom / 2, yRandom / 2);
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

        private int i = 0;
        private Thread AddonB_BackgroundRun = new Thread(new Runnable() {
            public void run() {
                for (i = 0; i < 800; i++) {

                    System.out.println(i + " | started | ");

                    if (config.SlayerAddon() && client.getGameState() == GameState.LOGGED_IN) {

                        System.out.println(i + " | "
                                + " idle time " + idleTimeSecs() + " plane " + client.getPlane()
                                + " idle movement " + checkMovementIdle() + " WL " + client.getLocalPlayer().getWorldLocation()
                                + " canvas " + client.getMouseCanvasPosition() + " animation " + client.getLocalPlayer().getAnimation()
                                + " not moving time " + idleMovementTimeSecs() + " InvFoodIndex " + InvFoodIndex ()
                                + " foodInventory " + foodInventory() + " InvTeleIndex " + InvTeleIndex()
                        );

                        try { addon();	} catch (AWTException | InterruptedException e) { e.printStackTrace();	}

                        randomNPCCheck();
                        if (randomNPCCheck() != null) {
                            System.out.println(i + " | OHtext " + randomNPCCheck().getOverheadText());
                        }

                        // OHtext Milord Vein X I, please speak to me!
                    }
                    try {
                        Thread.sleep(random2(3000, 6000));
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        });

        private void addon() throws AWTException, InterruptedException {

            int HP = client.getBoostedSkillLevel(Skill.HITPOINTS);
            System.out.print(" | HP: " + HP);
            if(foodInventory()==0){
                VarrockTele();
                return;}
            int HPThreshold = 14;
            while (HP<HPThreshold){
                System.out.print(" | Eating | " + foodInventory() + " | ");
                eatFood();
                try { Thread.sleep(random2(450, 610));	}
                catch (InterruptedException e) { e.printStackTrace(); }
                HP = client.getBoostedSkillLevel(Skill.HITPOINTS);
            }
            List<NPC> localTarget = new ArrayList<>();
            List<NPC> listClone = new ArrayList<>(client.getNpcs());

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
            double factorCheck = ii / 100;
            if (factorCheck == (int) factorCheck) {
                //if(BreakTimer()>360){
                System.out.print(" || SLEEPING || ");
                {
                    try {
                        Thread.sleep(random2(60000, 120000));
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
            //System.out.println(" | check 2 | ");
            while (idleMovementTimeSecs() < 6) {
                Thread.sleep(random2(816, 1186));
            }
            //System.out.println(" | check 3 | ");

            if(foodInventory()>0 && Equipped() && idleTimeSecs() > 5){
                WorldPoint TargetArea = new WorldPoint(TargetArea0.getX()+random2(-3,3),
                        TargetArea0.getY()+random2(-3,3),0);
                //System.out.println(" | check 4 | ");
                if(Math.abs(client.getLocalPlayer().getWorldLocation().getX()-TargetArea.getX())>9 ||
                        Math.abs(client.getLocalPlayer().getWorldLocation().getY()-TargetArea.getY())>9
                ){
                    //System.out.println(" | check 5 | ");
                    walk(TargetArea);
                    return;
                }
                //System.out.print(" | check 6 | ");
                for (NPC npc : listClone) {
                    int npcId = npc.getId();
                    //System.out.println(" |      treespots  .  " + object.getId() + " , " + object.getWorldLocation());
                    if (TARGET_NPC_IDS.contains(npcId) && npc.getWorldLocation().distanceTo2D(TargetArea) <= 7
                            && idleTimeSecs()>5 && npc.getAnimation()==-1)
                    { localTarget.add(npc); }}
                //	System.out.println(" |      local trees  .  " + localTarget.size());
                if (localTarget.size() != 0 ){
                    localTarget = inverseSortTreeDistanceFromPlayer(localTarget);

                    for (NPC npc : localTarget){

                        double x = 	npc.getCanvasTilePoly().getBounds().getCenterX();
                        //+ random2Dbl(-obstacle.getClickbox().getBounds().getWidth()/2,obstacle.getClickbox().getBounds().getWidth()/2);
                        double y = npc.getCanvasTilePoly().getBounds().getCenterY();
                        // + random2Dbl(-obstacle.getClickbox().getBounds().getHeight()/2,obstacle.getClickbox().getBounds().getHeight()/2);
                        int xx = (int) x + random2(-2,2);
                        int yy = (int) y + random2(-2,2);
                        Point adjustedSpotPerspective = new Point (xx,yy);
                        Point PreviousMousePoint;
                        //	System.out.println(" | click obstacle | " + " TargetObstacleWL " );

                        if(adjustedSpotPerspective.getX()>8 && adjustedSpotPerspective.getX()<1500 && adjustedSpotPerspective.getY()>30
                                && adjustedSpotPerspective.getY()<810) {

                            WorldPoint NPCWL1 = npc.getWorldLocation();
                            try {Thread.sleep(random2(500, 550));}
                            catch (InterruptedException e) {e.printStackTrace();}
                            WorldPoint NPCWL2 = npc.getWorldLocation();

                            if(Math.abs(NPCWL1.getX()-NPCWL2.getX())<1 && Math.abs(NPCWL1.getY()-NPCWL2.getY())<1){

                                Robot robot = new Robot();
                                moveMouse(robot, client.getMouseCanvasPosition(), adjustedSpotPerspective, 10, 5, 5);
                                PreviousMousePoint = adjustedSpotPerspective;
                                try {Thread.sleep(random2(3, 7));}
                                catch (InterruptedException e) {e.printStackTrace();}

                                x = npc.getCanvasTilePoly().getBounds().getCenterX();
                                y = npc.getCanvasTilePoly().getBounds().getCenterY();
                                xx = (int) x + random2(-2, 2);
                                yy = (int) y + random2(-2, 2);
                                adjustedSpotPerspective = new Point(xx, yy);

                                if (Math.abs(PreviousMousePoint.getX()-adjustedSpotPerspective.getX())<6
                                        || Math.abs(PreviousMousePoint.getY()-adjustedSpotPerspective.getY())<6) {
                                    leftClick(robot);
                                    return;
                                }
                            }	}	}}
            }
            if (idleTimeSecs()<=5 ){
                int randomInt = random2(1,20);
                if (randomInt == 5)	{RandomMouseMove();}
                return;}
            if (localTarget.size() == 0 && foodInventory()!=0 ) {
                int randomInt = random2(1,20);
                if (randomInt == 5)	{RandomMouseMove();}
                return;}
        }


        private boolean Equipped() {

            for (Item item : client.getItemContainer(InventoryID.EQUIPMENT).getItems())
            {
                if (item == null)
                {
                    continue;
                }

                if (item.getId() == ItemID.IRON_KNIFE) {
                    return true;
                }
            }
            return false;
        }

        private Point InvLocation(int index) {

            int invBaseX = client.getWidget(WidgetInfo.INVENTORY).getCanvasLocation().getX();
            int invBaseY = client.getWidget(WidgetInfo.INVENTORY).getCanvasLocation().getY();
            int n = index;
            int relateBaseX= 14;
            int relateBaseY= 45;
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

        private int foodInventory() {
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

        private void VarrockTele () throws AWTException {
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
            int TeleTab = InvTeleIndex();
            Point TeleInvLocation = InvLocation(TeleTab);
            Robot robot = new Robot();
            moveMouse(robot,client.getMouseCanvasPosition(),TeleInvLocation,10,5,5);
            try {  Thread.sleep(random2(150,260));  } catch (InterruptedException e) { e.printStackTrace();}
            leftClick(robot);
            try {  Thread.sleep(random2(415,560));  } catch (InterruptedException e) { e.printStackTrace();}
        }

        private int InvTeleIndex () {
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

        private int InvFoodIndex () {
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

        private Point worldToCanvas(WorldPoint worldpoint) {
            LocalPoint targetLL = LocalPoint.fromWorld(client, worldpoint.getX(), worldpoint.getY());
            Point perspective = Perspective.localToCanvas(client, targetLL, client.getPlane());
            Point adjustedPerspective = new Point(perspective.getX() + 1, perspective.getY() - 1);
            return adjustedPerspective;
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

        // NPCList ID: 5437 Index: 30019 Name: Miles  NPCList ID: 5437 Index: 30019 Name: Miles115 | started |

        private NPC randomNPCCheck() {
            List<NPC> activeRandom = new ArrayList<>();
            List<NPC> NPCList;
            if (client.getNpcs() == null) {
                return null;
            }
            NPCList = client.getNpcs();

            for (NPC npc : NPCList) {
                if (npc.getWorldLocation().distanceTo2D(client.getLocalPlayer().getWorldLocation()) < 4) {
                    System.out.print(" || NPCList ID: " + npc.getId() + " Index: " + npc.getIndex() + " Name: " + npc.getName());
                }
            }

            for (NPC npc : NPCList) {
                if (RANDOM_IDS.contains(npc.getId())) {
                    activeRandom.add(npc);
                }
            }
            if (activeRandom.size() != 0) {
                for (NPC random : activeRandom) {
                    if (random.getOverheadText() != null) {
                        if (random.getOverheadText().contains("Vein")) {
                            NPC TargetRandom = random;
                            return random;
                        }
                    }
                }
            }
            return null;
        }

        private void dismissRandom() throws AWTException {
            NPC targetRandom = randomNPCCheck();
            WorldPoint randomWL = targetRandom.getWorldLocation();
            Point randomCanvas = worldToCanvas(randomWL);
            if (randomCanvas.getX() > 8 && randomCanvas.getX() < 1550 && randomCanvas.getY() > 30 && randomCanvas.getY() < 810) {
                Robot robot = new Robot();
                moveMouse(robot, client.getMouseCanvasPosition(), randomCanvas, 11, 4, 4);
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

        private List<NPC> inverseSortTreeDistanceFromPlayer(List<NPC> localTarget)
        {

            if (localTarget.isEmpty())
            {
                return null;
            }

            final LocalPoint cameraPoint = new LocalPoint(client.getCameraX(), client.getCameraY());
            localTarget.sort(
                    Comparator.comparing(
                            // Negate to have the furthest first
                            (NPC npc) -> npc.getLocalLocation().distanceTo(cameraPoint))
                            // Order by position
                            .thenComparing(NPC::getLocalLocation, Comparator.comparing(LocalPoint::getX)
                                    .thenComparing(LocalPoint::getY))
                            // And then by id
                            .thenComparing(NPC::getId)
            );
            return localTarget;
        }

    }




}
