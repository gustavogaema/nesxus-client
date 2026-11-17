package dev.nexus.client.render;

import dev.nexus.client.modules.GlobalEsp;
import dev.nexus.client.module.Mod;
import dev.nexus.client.setting.Setting;
import dev.nexus.client.util.Fx;
import dev.nexus.client.util.Los;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;

/**
 * One fully independent ESP "skin": colour (RGB picker), render style, outline thickness, fill opacity, ranges,
 * visibility rule, tracer and label toggles. Every ESP category owns its own instance, so categories never interfere.
 */
public final class EspStyle {
    public static final String[] STYLES = {"Box", "Corners", "Filled", "Box + Fill", "3D Box"};

    public final Setting.Bool enabled, tracer, label;
    public final Setting.Color color;
    public final Setting.Mode style, visibility;
    public final Setting.Num thickness, fill, range, minRange;

    public EspStyle(Mod.Group g, boolean withToggle, int argb, double rangeDef, String defStyle, boolean labelDef) {
        this(g, withToggle, true, argb, rangeDef, defStyle, labelDef);
    }

    public EspStyle(Mod.Group g, boolean withToggle, boolean enabledDef, int argb, double rangeDef, String defStyle, boolean labelDef) {
        enabled = withToggle ? g.bool("Enabled", enabledDef) : new Setting.Bool("Enabled", true);
        color = g.color("Color", argb);
        style = g.mode("Render Style", STYLES).def(defStyle);
        thickness = g.num("Outline Thickness", 1, 1, 6, 1);
        fill = g.num("Fill Opacity", 25, 0, 100, 5).unit("%");
        range = g.num("Max Range", rangeDef, 8, 512, 4).unit("m");
        minRange = g.num("Min Range", 0, 0, 64, 1).unit("m");
        visibility = g.mode("Visibility", "Always", "Behind Walls Only", "Visible Only");
        tracer = g.bool("Tracer", false);
        label = g.bool("Label", labelDef);
    }

    public boolean allows(double dist) {
        return enabled.value && dist <= Math.min(range.d(), GlobalEsp.I.maxRange.d()) && dist >= minRange.d();
    }

    public boolean losOk(Entity e) {
        int v = visibility.index;
        if (v == 0) return true;
        boolean vis = Los.visible(e);
        return v == 1 ? !vis : vis;
    }

    public boolean losOk(boolean visible) {
        int v = visibility.index;
        return v == 0 || (v == 1 ? !visible : visible);
    }

    /** Final ARGB for a target at a distance (alpha faded if enabled). rgbOverride==0 -> use the picker colour. */
    public int col(double dist, int rgbOverride) {
        int base = rgbOverride != 0 ? (0xFF000000 | rgbOverride) : color.argb();
        return GlobalEsp.I.fade.value ? Fx.alpha(base, Fx.fade(dist, Math.min(range.d(), GlobalEsp.I.maxRange.d()))) : base;
    }

    /** Draws the box for a world AABB. Returns the (shared) screen rect, or null if culled / behind the camera. */
    public Projector.Rect draw(DrawContext c, Projector p, double x0, double y0, double z0,
                               double x1, double y1, double z1, double dist, int rgbOverride) {
        Projector.Rect r = p.box(x0, y0, z0, x1, y1, z1);
        if (r == null || r.x1 < 0 || r.y1 < 0 || r.x0 > p.w || r.y0 > p.h) return null;
        int col = col(dist, rgbOverride);
        int t = Math.max(1, Math.round(thickness.f() * GlobalEsp.I.thicknessScale.f()));
        int ix0 = Math.round(r.x0), iy0 = Math.round(r.y0), ix1 = Math.round(r.x1), iy1 = Math.round(r.y1);
        if (ix1 - ix0 < 3 && iy1 - iy0 < 3) {
            int cx = (ix0 + ix1) / 2, cy = (iy0 + iy1) / 2;
            c.fill(cx - 1, cy - 1, cx + 2, cy + 2, col);
            return r;
        }
        int fillCol = Fx.alpha(col, fill.f() / 100f);
        switch (style.get()) {
            case "Corners" -> Render2D.corners(c, ix0, iy0, ix1, iy1, t, col);
            case "Filled" -> c.fill(ix0, iy0, ix1, iy1, fillCol);
            case "Box + Fill" -> { c.fill(ix0, iy0, ix1, iy1, fillCol); Render2D.outline(c, ix0, iy0, ix1, iy1, t, col); }
            case "3D Box" -> {
                if (fill.value > 0) c.fill(ix0, iy0, ix1, iy1, Fx.alpha(fillCol, 0.5f));
                float[] k = p.corners;
                for (int i = 0; i < 8; i++)
                    for (int bit = 1; bit <= 4; bit <<= 1)
                        if ((i & bit) == 0) {
                            int j = i | bit;
                            Render2D.line(c, k[i * 2], k[i * 2 + 1], k[j * 2], k[j * 2 + 1], t, col, p.w, p.h);
                        }
            }
            default -> Render2D.outline(c, ix0, iy0, ix1, iy1, t, col);
        }
        return r;
    }
}
