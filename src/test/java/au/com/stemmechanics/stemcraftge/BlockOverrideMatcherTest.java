package au.com.stemmechanics.stemcraftge;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class BlockOverrideMatcherTest {
    @Test
    void exactMatchWinsOverEarlierWildcard() {
        List<String> keys = List.of("minecraft:*chest", "minecraft:ender_chest");
        assertEquals("minecraft:ender_chest",
                BlockOverrideMatcher.match("minecraft:ender_chest", keys));
    }

    @Test
    void firstWildcardWinsInConfigurationOrder() {
        List<String> keys = List.of("minecraft:*", "minecraft:*_wall");
        assertEquals("minecraft:*", BlockOverrideMatcher.match("minecraft:cobblestone_wall", keys));
    }

    @Test
    void chestSuffixPatternMatchesPlainAndQualifiedChests() {
        List<String> keys = List.of("minecraft:*chest");
        assertEquals("minecraft:*chest", BlockOverrideMatcher.match("minecraft:chest", keys));
        assertEquals("minecraft:*chest", BlockOverrideMatcher.match("minecraft:trapped_chest", keys));
        assertEquals("minecraft:*chest", BlockOverrideMatcher.match("minecraft:ender_chest", keys));
    }

    @Test
    void underscoreChestPatternDoesNotMatchPlainChest() {
        assertNull(BlockOverrideMatcher.match("minecraft:chest", List.of("minecraft:*_chest")));
    }

    @Test
    void returnsNullWhenNothingMatches() {
        assertNull(BlockOverrideMatcher.match("minecraft:stone", List.of("minecraft:*_wall")));
    }
}
