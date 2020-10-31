package net.runelite.client.plugins.autoAgility;

import lombok.Getter;
import net.runelite.api.ObjectID;
import net.runelite.api.coords.WorldPoint;

import java.awt.*;

public enum AutoAgilityCourses {

    FALADOR1( new Rectangle(3034, 3339, 3038 + 1 - 3034, 3341 + 1 - 3339), ObjectID.ROUGH_WALL_14898, "Climb", new WorldPoint(3036, 3340, 0),"decorative"),
    FALADOR2( new Rectangle(3036, 3342, 3040 + 1 - 3036, 3343 + 1 - 3342), ObjectID.TIGHTROPE_14899, "Cross", new WorldPoint(3040, 3343, 3),"ground"),
    FALADOR3( new Rectangle(3044, 3341, 3051 + 1 - 3044, 3349 + 1 - 3341), ObjectID.HAND_HOLDS_14901, "Cross", new WorldPoint(3050, 3350, 3),"game"),
    FALADOR4( new Rectangle(3047, 3356, 3050 + 1 - 3047, 3358 + 1 - 3356), ObjectID.GAP_14903, "Jump", new WorldPoint(3048, 3359, 3),"game"),
    FALADOR5( new Rectangle(3045, 3361, 3048 + 1 - 3045, 3367 + 1 - 3361), ObjectID.GAP_14904, "Jump", new WorldPoint(3044, 3361, 3),"game"),
    FALADOR6( new Rectangle(3034, 3361, 3041 + 1 - 3034, 3364 + 1 - 3361), ObjectID.TIGHTROPE_14905, "Cross", new WorldPoint(3034, 3362, 3),"game"),
    FALADOR7( new Rectangle(3026, 3352, 3029 + 1 - 3026, 3355 + 1 - 3352), ObjectID.TIGHTROPE_14911, "Cross", new WorldPoint(3026, 3353, 3),"ground"),
    FALADOR8( new Rectangle(3009, 3353, 3021 + 1 - 3009, 3358 + 1 - 3353), ObjectID.GAP_14919, "Jump", new WorldPoint(3017, 3352, 3),"game"),
    FALADOR9( new Rectangle(3016, 3343, 3022 + 1 - 3016, 3349 + 1 - 3343), ObjectID.LEDGE_14920, "Jump", new WorldPoint(3015, 3346, 3),"game"),
    FALADOR10( new Rectangle(3011, 3343, 3015 + 1 - 3011, 3347 + 1 - 3343), ObjectID.LEDGE_14921, "Jump", new WorldPoint(3012, 3343, 3),"game"),
    FALADOR11( new Rectangle(3009, 3335, 3013 + 1 - 3009, 3343 + 1 - 3335), ObjectID.LEDGE_14922, "Jump", new WorldPoint(3013, 3334, 3),"game"),
    FALADOR12( new Rectangle(3012, 3331, 3018 + 1 - 3012, 3334 + 1 - 3331), ObjectID.LEDGE_14924, "Jump", new WorldPoint(3018, 3333, 3),"game"),
    FALADOR13( new Rectangle(3019, 3332, 3024 + 1 - 3019, 3335 + 1 - 3332), ObjectID.EDGE_14925, "Jump", new WorldPoint(3025, 3334, 3),"game"),

    GNOME1(new Rectangle(2471, 3435, 7, 4), ObjectID.LOG_BALANCE_23145, "Walk-across", new WorldPoint(2474, 3437, 0),"ground"),
    GNOME2(new Rectangle(2470, 3425, 8, 6), ObjectID.OBSTACLE_NET_23134, "Climb-over", new WorldPoint(2473, 3427, 0),"game"),
    GNOME3(new Rectangle(2471, 3422, 6, 3), ObjectID.TREE_BRANCH_23559, "Climb", new WorldPoint(2473, 3423, 1),"game"),
    GNOME4(new Rectangle(2472, 3418, 6, 4), ObjectID.BALANCING_ROPE_23557, "Walk-on", new WorldPoint(2475, 3420, 2),"ground"),
    GNOME5(new Rectangle(2483, 3418, 6, 4), ObjectID.TREE_BRANCH_23560, "Climb-down", new WorldPoint(2484, 3420, 2),"game"),
    GNOME6A(new Rectangle(2482, 3418, 8, 9), ObjectID.OBSTACLE_NET_23134, "Climb-over", new WorldPoint(2486, 3424, 0),"game"),
    GNOME6B(new Rectangle(2482, 3418, 8, 9), ObjectID.OBSTACLE_NET_23135, "Climb-over", new WorldPoint(2486, 3424, 0),"game"),
    GNOME7(new Rectangle(2482, 3427, 8, 6), ObjectID.OBSTACLE_PIPE_23138, "Squeeze-through", new WorldPoint(2486, 3429, 0),"game"),

    SEERS1(new Rectangle(2728, 3486, 3, 4), ObjectID.WALL_14927,"Climb-up",new WorldPoint(2729,3489-1,0),"decorative"),
    SEERS2 (new Rectangle(2721,3490,10,8),	ObjectID.GAP_14928,	"Jump",	new WorldPoint(2720,3494,3),	"game"),
    SEERS3 (new Rectangle(2704,3487,11,12),	ObjectID.TIGHTROPE_14932,	"Cross",	new WorldPoint(2710,3489,2),	"ground"),
    SEERS4 (new Rectangle(2709,3476,8,7),	ObjectID.GAP_14929,	"Jump",	new WorldPoint(2712,3476,2),	"game"),
    SEERS5 (new Rectangle(2699,3469,18,8),	ObjectID.GAP_14930,	"Jump",	new WorldPoint(2702,3468,3),	"game"),
    SEERS6 (new Rectangle(2690,3459,14,8),	ObjectID.EDGE_14931,	"Jump",	new WorldPoint(2703,3463,2),	"game"),

    VARROCK1(new Rectangle(3220, 3412, 6, 6),	ObjectID.ROUGH_WALL_14412,"Climb",new WorldPoint(3222+1,3414,0),"decorative"),
    VARROCK2 (new Rectangle(3214,3410,6,10),	ObjectID.CLOTHES_LINE,"Cross",new WorldPoint(3214,3414,3),	"game"),
    VARROCK3 (new Rectangle(3200,3414,9,4),	ObjectID.GAP_14414,	"Leap",	new WorldPoint(3201,3416,3),	"game"),
    VARROCK4 (new Rectangle(3193,3416,5,2),	ObjectID.WALL_14832,"Balance",new WorldPoint(3194,3416,1),	"game"),
    VARROCK5 (new Rectangle(3192,3402,7,5),	ObjectID.GAP_14833,	"Leap",	new WorldPoint(3194,3402,3),	"game"),
    VARROCK6a (new Rectangle(3182,3382,20,17),ObjectID.GAP_14834,	"Leap",	new WorldPoint(3208,3398,3),	"game"),
    VARROCK6b (new Rectangle(3202,3395,7,9),	ObjectID.GAP_14834,	"Leap",	new WorldPoint(3208,3398,3),	"game"),
    VARROCK7 (new Rectangle(3218,3393,15,10),	ObjectID.GAP_14835,	"Leap",	new WorldPoint(3232,3399+2,3),	"game"),
    VARROCK8 (new Rectangle(3236,3403,5,6),	ObjectID.LEDGE_14836,"Hurdle",new WorldPoint(3238,3408,3),	"game"),
    VARROCK9 (new Rectangle(3236,3410,5,6),	ObjectID.EDGE,	"Jump-off",	new WorldPoint(3238,3415,3),	"game");

    public static Rectangle seers_Pathing_1 = new Rectangle(2703,3458,12,10);
    public static Rectangle seers_Pathing_Target_1 = new Rectangle(2724,3464,5,4);

    @Getter
    private final Rectangle pre_Obstacle_Area;

    @Getter
    private final int obstacle_ID;

    @Getter
    private final String obstacle_text;

    @Getter
    private final WorldPoint obstacle_World_Location;

    @Getter
    private final String obstacle_Type;

    AutoAgilityCourses(Rectangle pre_Obstacle_Area, int obstacle_ID, String obstacle_text, WorldPoint obstacle_World_Location, String obstacle_Type)
    {
        this.pre_Obstacle_Area = pre_Obstacle_Area;
        this.obstacle_ID = obstacle_ID;
        this.obstacle_text = obstacle_text;
        this.obstacle_World_Location = obstacle_World_Location;
        this.obstacle_Type = obstacle_Type;
    }

}
