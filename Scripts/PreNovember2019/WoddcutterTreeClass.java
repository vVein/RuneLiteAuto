package Polished.Scripts.PreNovember2019;

import java.util.Map;

public class WoddcutterTreeClass/*
 * Copyright (c) 2018, Mantautas Jurksa <https://github.com/Juzzed>
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

        import com.google.common.collect.ImmutableMap;
        import java.util.Map;
        import lombok.Getter;

        import static net.runelite.api.ObjectID.*;

@Getter
enum Tree
{
    REDWOOD_TREE_SPAWN(REDWOOD, REDWOOD_29670),
    TREE_SPAWN(TREE,TREE_1277,TREE_1278,TREE_1279,TREE_1280,TREE_1301,TREE_1303,TREE_1304,TREE_1330,TREE_1331,TREE_1332,TREE_2409),
    OAK_TREE_SPAWN(OAK_TREE,OAK_TREE_4540,OAK,OAK_8463,OAK_8464,OAK_8465,OAK_8466,OAK_8467,OAK_9734,OAK_10820,OAK_20806),
    WILLOW_TREE_SPAWN(WILLOW,WILLOW_TREE,WILLOW_TREE_4541,WILLOW_TREE_8481,WILLOW_TREE_8482,WILLOW_TREE_8483,WILLOW_TREE_8484,
            WILLOW_TREE_8485,WILLOW_TREE_8486,WILLOW_TREE_8487,WILLOW_TREE_8488,WILLOW_10829,WILLOW_10831,WILLOW_10833 );

    private final int[] treeIds;

    Tree(int... treeIds)
    {
        this.treeIds = treeIds;
    }

    private static final Map<Integer, Tree> TREES;

    static
    {
        ImmutableMap.Builder<Integer, Tree> builder = new ImmutableMap.Builder<>();

        for (Tree tree : values())
        {
            for (int treeId : tree.treeIds)
            {
                builder.put(treeId, tree);
            }
        }

        TREES = builder.build();
    }

    static Tree findTree(int objectId)
    {
        return TREES.get(objectId);
    }
} {
}
