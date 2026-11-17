package dev.nexus.client.gui;

import dev.nexus.client.modules.Interface;

/** Central colour palette. The accent comes from the Interface module (RGB picker / presets / rainbow). */
public final class Theme {
    public static final int HEAD = 0xFF181820, TEXT = 0xFFE8E8F0, DIM = 0xFF8E8EA0, TRACK = 0xFF22222B, FIELD = 0xFF1A1A22;
    private Theme() { }

    public static int accent() { return Interface.I == null ? 0xFF7C5CFF : (Interface.I.accent.argb() | 0xFF000000); }
    public static int radius() { return Interface.I == null ? 4 : Interface.I.radius.i(); }
    public static int bg() {
        int a = Interface.I == null ? 240 : (int) (Interface.I.bgOpacity.d() * 2.55);
        return (a << 24) | 0x101016;
    }
}
