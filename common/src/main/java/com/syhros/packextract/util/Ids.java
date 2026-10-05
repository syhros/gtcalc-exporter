package com.syhros.packextract.util;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.zip.CRC32;

/** Item and fluid ids and file names. No Minecraft classes, so it is unit tested. */
public final class Ids {

    /** Forge's wildcard damage value: "any variant of this item". */
    public static final int WILDCARD = 32767;

    private Ids() {}

    /**
     * Item id: {@code registryName:meta}, plus {@code #hash} of the NBT when the stack has NBT. Wildcard stacks use
     * {@code *} as the meta.
     */
    public static String item(String registryName, int meta, String nbt) {
        StringBuilder sb = new StringBuilder(registryName).append(':');
        if (meta == WILDCARD) {
            sb.append('*');
        } else {
            sb.append(meta);
        }
        if (nbt != null && !nbt.isEmpty()) {
            sb.append('#').append(nbtHash(nbt));
        }
        return sb.toString();
    }

    /**
     * Item id on 1.13 and later, where items have no meta: the registry name, plus {@code #hash} of the NBT (or of the
     * data components on 1.20.5+) when the stack has any.
     */
    public static String item(String registryName, String nbt) {
        return nbt != null && !nbt.isEmpty() ? registryName + "#" + nbtHash(nbt) : registryName;
    }

    /** Stable 8-hex-digit hash of an NBT string. */
    public static String nbtHash(String nbt) {
        CRC32 crc = new CRC32();
        crc.update(nbt.getBytes(StandardCharsets.UTF_8));
        String hex = Long.toHexString(crc.getValue());
        StringBuilder sb = new StringBuilder();
        for (int i = hex.length(); i < 8; i++) {
            sb.append('0');
        }
        return sb.append(hex).toString();
    }

    /** "gregtech:gt.metaitem.01:3032" to "gregtech_gt.metaitem.01_3032": safe on every file system. */
    public static String fileStem(String id) {
        StringBuilder sb = new StringBuilder(id.length());
        for (int i = 0; i < id.length(); i++) {
            char c = id.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '.' || c == '-') {
                sb.append(c);
            } else if (c == '*') {
                sb.append("any");
            } else {
                sb.append('_');
            }
        }
        String s = sb.toString();
        // Windows reserved names and empty or dot-only names.
        String upper = s.toUpperCase(Locale.ROOT);
        if (s.isEmpty() || s.replace(".", "").isEmpty() || upper.matches("(CON|PRN|AUX|NUL|COM\\d|LPT\\d)(\\..*)?")) {
            s = "_" + s;
        }
        if (s.length() > 150) {
            s = s.substring(0, 140) + "_" + nbtHash(s);
        }
        return s;
    }

    /** Hands out unique file names; case-insensitive so it works on Windows and macOS too. */
    public static final class FileNames {

        private final Set<String> used = new HashSet<String>();

        public String claim(String id, String extension) {
            String stem = fileStem(id);
            String name = stem + extension;
            int n = 2;
            while (!used.add(name.toLowerCase(Locale.ROOT))) {
                name = stem + "_" + n++ + extension;
            }
            return name;
        }
    }
}
