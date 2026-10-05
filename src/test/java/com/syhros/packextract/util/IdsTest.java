package com.syhros.packextract.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class IdsTest {

    @Test
    void plainWildcardAndNbtIds() {
        assertEquals("minecraft:log:0", Ids.item("minecraft:log", 0, null));
        assertEquals("minecraft:log:*", Ids.item("minecraft:log", Ids.WILDCARD, ""));
        String withNbt = Ids.item("minecraft:potion", 8193, "{CustomPotionEffects:[]}");
        assertTrue(withNbt.matches("minecraft:potion:8193#[0-9a-f]{8}"), withNbt);
        assertEquals(withNbt, Ids.item("minecraft:potion", 8193, "{CustomPotionEffects:[]}"));
        assertNotEquals(withNbt, Ids.item("minecraft:potion", 8193, "{x:1b}"));
    }

    @Test
    void fileStemsAreSafe() {
        assertEquals("gregtech_gt.metaitem.01_3032", Ids.fileStem("gregtech:gt.metaitem.01:3032"));
        assertEquals("minecraft_log_any", Ids.fileStem("minecraft:log:*"));
        assertEquals("a_b_c_d", Ids.fileStem("a/b\\c d"));
        assertEquals("_CON", Ids.fileStem("CON"));
        assertEquals("_..", Ids.fileStem(".."));
        String longId = "mod:" + new String(new char[300]).replace('\0', 'x');
        assertTrue(Ids.fileStem(longId).length() <= 150);
    }

    @Test
    void fileNamesAreUniqueIgnoringCase() {
        Ids.FileNames names = new Ids.FileNames();
        assertEquals("a_b.png", names.claim("a:b", ".png"));
        assertEquals("a_b_2.png", names.claim("a_b", ".png"));
        assertEquals("A_B_3.png", names.claim("A:B", ".png"));
    }
}
