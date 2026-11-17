package dev.nexus.client.modules;

import dev.nexus.client.gui.ClickGuiScreen;
import dev.nexus.client.module.Mod;
import dev.nexus.client.setting.Setting;
import org.lwjgl.glfw.GLFW;

/** The GUI module: its keybind (default Right Shift) opens the Nexus interface. Holds all theme settings. */
public final class Interface extends Mod {
    public static Interface I;
    private static final String[] NAMES = {"Custom", "Nexus Purple", "Cyber Cyan", "Crimson", "Emerald", "Amber", "Rose", "Ice"};
    private static final int[] COLORS = {0, 0xFF7C5CFF, 0xFF00D4FF, 0xFFFF3B5C, 0xFF2ED573, 0xFFFFB020, 0xFFFF6BA9, 0xFF8FD3FF};

    public final Setting.Color accent;
    public final Setting.Mode preset;
    public final Setting.Num radius, animSpeed, bgOpacity;
    public final Setting.Bool tooltips, toasts;
    private int lastPreset = 1;

    Interface() {
        super("Interface", "Theme, accent colour, animations. Keybind opens the GUI.", Category.SETTINGS);
        I = this; alwaysOn = true; key = GLFW.GLFW_KEY_RIGHT_SHIFT;
        accent = general.color("Accent Color", 0xFF7C5CFF);
        preset = general.mode("Accent Preset", NAMES).def("Nexus Purple");
        radius = general.num("Corner Radius", 4, 0, 8, 1).unit("px");
        animSpeed = general.num("Animation Speed", 1.0, 0.5, 3.0, 0.25).unit("x");
        bgOpacity = general.num("Panel Opacity", 94, 40, 100, 2).unit("%");
        tooltips = general.bool("Tooltips", true);
        toasts = general.bool("Toast Notifications", true);
        general.action("Reset Window Layout", () -> ClickGuiScreen.get().resetLayout());
    }

    /** Called by the GUI every frame: applies a preset when the user picks one. */
    public void frame() {
        if (preset.index != lastPreset) {
            lastPreset = preset.index;
            if (COLORS[lastPreset] != 0) { accent.setArgb(COLORS[lastPreset]); accent.rainbow = false; }
        }
    }

    @Override public void toggle() {
        var mc = mc();
        if (mc.currentScreen == null) mc.setScreen(ClickGuiScreen.get());
    }
}
