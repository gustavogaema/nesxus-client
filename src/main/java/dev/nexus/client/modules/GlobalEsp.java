package dev.nexus.client.modules;

import dev.nexus.client.module.HasEsp;
import dev.nexus.client.module.Mod;
import dev.nexus.client.module.Mods;
import dev.nexus.client.render.EspStyle;
import dev.nexus.client.setting.Setting;
import dev.nexus.client.util.Notify;

/** Global ESP configuration: master switch (module toggle), shared limits, scan budget and colour presets. */
public final class GlobalEsp extends Mod {
    public static GlobalEsp I;

    private static final String[] PRESET_NAMES = {"Neon", "Pastel", "Classic", "High Contrast", "Monochrome"};
    private static final int[][] PRESETS = {
            {0xFF2D95, 0x00E5FF, 0xB6FF00, 0xFFB000, 0x8F5BFF, 0xFF4D4D, 0x00FF9C},
            {0xFFB3C6, 0xB5E2FA, 0xC9F2C7, 0xFFE5A0, 0xD7BCE8, 0xFFC8A2, 0xA8E6CF},
            {0x55FF55, 0xFF5555, 0x55FFFF, 0xFFFF55, 0xFF55FF, 0xFFAA00, 0x5555FF},
            {0xFFFFFF, 0xFFE100, 0x00B7FF, 0xFF3B30, 0x34C759, 0xFF2D95, 0xFF9500},
            {0xFFFFFF, 0xD0D0D0, 0xA0A0A0, 0xE8E8E8, 0xC0C0C0, 0xF4F4F4, 0xB8B8B8}};

    public final Setting.Num maxRange, thicknessScale, maxRender, scanInterval, budget, blockRescan;
    public final Setting.Bool fade;
    private final Setting.Mode preset;

    GlobalEsp() {
        super("Global ESP", "Master switch + shared limits for every ESP module (all can run at once).", Category.SETTINGS);
        I = this; enabled = true;
        Group g = group("Limits");
        maxRange = g.num("Max Range (global cap)", 256, 16, 512, 8).unit("m");
        maxRender = g.num("Max Drawn Per Module", 300, 20, 1500, 20);
        thicknessScale = g.num("Outline Thickness Scale", 1.0, 0.5, 3.0, 0.5);
        fade = g.bool("Fade With Distance", true);
        Group p = group("Performance");
        scanInterval = p.num("Storage Scan Interval", 10, 2, 40, 1).unit(" ticks");
        budget = p.num("Block Scan Budget", 1.0, 0.25, 4.0, 0.25).unit(" ms");
        blockRescan = p.num("Block Rescan Interval", 5, 1, 30, 1).unit(" s");
        Group c = group("Color Presets");
        preset = c.mode("Palette", PRESET_NAMES);
        c.action("Apply Palette To All ESP", this::apply);
    }

    private void apply() {
        int[] pal = PRESETS[preset.index];
        int i = 0;
        for (Mod m : Mods.all())
            if (m instanceof HasEsp h) for (EspStyle s : h.styles()) s.color.setRgb(pal[i++ % pal.length]);
        Notify.toast("Applied " + preset.get() + " palette to " + i + " ESP styles");
    }
}
