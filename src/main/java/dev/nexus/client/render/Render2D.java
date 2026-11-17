package dev.nexus.client.render;

import net.minecraft.client.gui.DrawContext;

/** 2D drawing primitives built on DrawContext.fill (batched by the vanilla GUI renderer). */
public final class Render2D {
    private Render2D() { }

    /** Rounded rectangle via scan-line fills (r rows per corner). */
    public static void rrect(DrawContext c, int x, int y, int w, int h, int r, int col) {
        if ((col >>> 24) == 0 || w <= 0 || h <= 0) return;
        r = Math.min(r, Math.min(w, h) / 2);
        if (r <= 0) { c.fill(x, y, x + w, y + h, col); return; }
        c.fill(x, y + r, x + w, y + h - r, col);
        for (int i = 0; i < r; i++) {
            double dy = r - (i + 0.5);
            int inset = r - (int) Math.round(Math.sqrt(r * r - dy * dy));
            c.fill(x + inset, y + i, x + w - inset, y + i + 1, col);
            c.fill(x + inset, y + h - i - 1, x + w - inset, y + h - i, col);
        }
    }

    public static void outline(DrawContext c, int x0, int y0, int x1, int y1, int t, int col) {
        c.fill(x0, y0, x1, y0 + t, col);
        c.fill(x0, y1 - t, x1, y1, col);
        c.fill(x0, y0 + t, x0 + t, y1 - t, col);
        c.fill(x1 - t, y0 + t, x1, y1 - t, col);
    }

    public static void corners(DrawContext c, int x0, int y0, int x1, int y1, int t, int col) {
        int l = Math.max(3, Math.min(x1 - x0, y1 - y0) / 3);
        c.fill(x0, y0, x0 + l, y0 + t, col);       c.fill(x0, y0, x0 + t, y0 + l, col);
        c.fill(x1 - l, y0, x1, y0 + t, col);       c.fill(x1 - t, y0, x1, y0 + l, col);
        c.fill(x0, y1 - t, x0 + l, y1, col);       c.fill(x0, y1 - l, x0 + t, y1, col);
        c.fill(x1 - l, y1 - t, x1, y1, col);       c.fill(x1 - t, y1 - l, x1, y1, col);
    }

    // ---- lines: clipped to the screen, then emitted as run-length rectangles (min(dx,dy)+1 fills per line)
    private static final float[] CL = new float[4];

    private static boolean clip(float x0, float y0, float x1, float y1, int sw, int sh) {
        float t0 = 0, t1 = 1, dx = x1 - x0, dy = y1 - y0;
        float[] p = {-dx, dx, -dy, dy}, q = {x0, sw - x0, y0, sh - y0};
        for (int i = 0; i < 4; i++) {
            if (p[i] == 0) { if (q[i] < 0) return false; }
            else {
                float t = q[i] / p[i];
                if (p[i] < 0) { if (t > t1) return false; if (t > t0) t0 = t; }
                else { if (t < t0) return false; if (t < t1) t1 = t; }
            }
        }
        CL[0] = x0 + t0 * dx; CL[1] = y0 + t0 * dy; CL[2] = x0 + t1 * dx; CL[3] = y0 + t1 * dy;
        return true;
    }

    public static void line(DrawContext c, float x0, float y0, float x1, float y1, int t, int col, int sw, int sh) {
        if (!clip(x0, y0, x1, y1, sw, sh)) return;
        x0 = CL[0]; y0 = CL[1]; x1 = CL[2]; y1 = CL[3];
        boolean steep = Math.abs(y1 - y0) > Math.abs(x1 - x0);
        if (steep) { float a = x0; x0 = y0; y0 = a; a = x1; x1 = y1; y1 = a; }
        if (x0 > x1) { float a = x0; x0 = x1; x1 = a; a = y0; y0 = y1; y1 = a; }
        int r0 = (int) Math.floor(Math.min(y0, y1)), r1 = (int) Math.floor(Math.max(y0, y1));
        float m = (x1 - x0) == 0 ? 0 : (y1 - y0) / (x1 - x0);
        for (int r = r0; r <= r1; r++) {
            float lo, hi;
            if (r0 == r1 || m == 0) { lo = x0; hi = x1; }
            else {
                float ya = Math.max(r, Math.min(y0, y1)), yb = Math.min(r + 1, Math.max(y0, y1));
                float xa = x0 + (ya - y0) / m, xb = x0 + (yb - y0) / m;
                lo = Math.max(x0, Math.min(xa, xb)); hi = Math.min(x1, Math.max(xa, xb));
            }
            int a = (int) Math.floor(lo), b = (int) Math.floor(hi) + 1;
            if (steep) c.fill(r, a, r + t, b, col); else c.fill(a, r, b, r + t, col);
        }
    }

    /** Small shadowed label with a translucent backing, centred on cx with its bottom edge at `bottom`. */
    public static void smallLabel(DrawContext c, net.minecraft.client.font.TextRenderer tr, String text, float cx, float bottom, int rgb) {
        float s = 0.8f;
        int w = tr.getWidth(text);
        c.getMatrices().pushMatrix();
        c.getMatrices().translate(cx, bottom);
        c.getMatrices().scale(s, s);
        rrect(c, -w / 2 - 2, -tr.fontHeight - 2, w + 4, tr.fontHeight + 3, 2, 0x70000000);
        c.drawText(tr, text, -w / 2, -tr.fontHeight, 0xFF000000 | rgb, true);
        c.getMatrices().popMatrix();
    }
}
