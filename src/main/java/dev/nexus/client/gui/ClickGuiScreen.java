package dev.nexus.client.gui;

import com.google.gson.JsonObject;
import dev.nexus.client.config.ConfigManager;
import dev.nexus.client.module.HudMod;
import dev.nexus.client.module.Mod;
import dev.nexus.client.module.Mods;
import dev.nexus.client.modules.Interface;
import dev.nexus.client.render.Render2D;
import dev.nexus.client.setting.Setting;
import dev.nexus.client.util.Fx;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Nexus GUI: one draggable, collapsible, scrollable window per category; per-module ON/OFF pill, keybind, grouped
 * settings, HSV+RGB+alpha colour pickers, live search, tooltips and animated everything.
 * Immediate-mode: every frame registers hit boxes while drawing; clicks are resolved against that list (top-most first).
 */
public final class ClickGuiScreen extends Screen {
    private static ClickGuiScreen inst;
    public static ClickGuiScreen get() { if (inst == null) inst = new ClickGuiScreen(); return inst; }

    private static final int W = 176, HEAD = 22, ROW = 18, PICK_H = 146;
    private static final int[] PRESETS = {0xFF7C5CFF, 0xFF00D4FF, 0xFFFF3B5C, 0xFF2ED573, 0xFFFFB020, 0xFFFF6BA9, 0xFFFFFFFF, 0xFF000000};

    private interface Handler { boolean on(double mx, double my, int btn); }
    private interface Dragger { void to(double mx, double my); }
    private record Hit(int x0, int y0, int x1, int y1, int[] clip, Handler h, String tip) { }

    private static final class Win {
        String title; Mod.Category cat; int x, y; boolean collapsed; float scroll, contentH = 200, shownH;
    }

    private final List<Win> wins = new ArrayList<>();
    private final Win search = new Win();
    private final List<Hit> hits = new ArrayList<>();
    private int[] clip = new int[4];
    private int mx, my;
    private float alpha, dt, speed = 1f, open;
    private long last = System.nanoTime();
    private String query = "";
    private Setting.Text focus;
    private Mod binding;
    private Dragger drag;
    private Win dragWin;
    private double offX, offY;
    private boolean layoutDone;

    private ClickGuiScreen() {
        super(Text.literal("Nexus"));
        for (Mod.Category c : Mod.Category.values()) { Win w = new Win(); w.title = c.label; w.cat = c; wins.add(w); }
        search.title = "Search";
    }

    // ------------------------------------------------------------------------------------------ lifecycle
    @Override protected void init() {
        if (!layoutDone) layout(false);
        open = 0;
        for (Mod m : Mods.all()) m.onGuiOpen();
    }

    public void resetLayout() { layoutDone = false; if (width > 0) layout(true); }

    private void layout(boolean force) {
        JsonObject saved = force ? new JsonObject() : ConfigManager.gui();
        int n = wins.size();
        int step = n > 1 ? Math.max(24, Math.min(W + 8, (width - 16 - W) / (n - 1))) : 0;
        for (int i = 0; i < n; i++) {
            Win w = wins.get(i);
            w.x = 8 + i * step; w.y = 34;
            if (saved.has(w.title)) {
                JsonObject o = saved.getAsJsonObject(w.title);
                w.x = o.get("x").getAsInt(); w.y = o.get("y").getAsInt(); w.collapsed = o.get("c").getAsBoolean();
            }
        }
        search.x = Math.max(8, width / 2 - W / 2); search.y = 32;
        layoutDone = true;
    }

    @Override public void removed() {
        JsonObject g = ConfigManager.gui();
        for (Win w : wins) {
            JsonObject o = new JsonObject();
            o.addProperty("x", w.x); o.addProperty("y", w.y); o.addProperty("c", w.collapsed);
            g.add(w.title, o);
        }
        ConfigManager.saveGui();
        ConfigManager.saveCurrent();
    }

    @Override public boolean shouldPause() { return false; }
    @Override public void renderBackground(DrawContext g, int a, int b, float d) { }

    // ------------------------------------------------------------------------------------------- helpers
    private int c(int argb) { return Fx.alpha(argb, alpha); }
    private void setClip(int x0, int y0, int x1, int y1) { clip[0] = x0; clip[1] = y0; clip[2] = x1; clip[3] = y1; }
    private void hit(int x, int y, int w, int h, Handler hd, String tip) { hits.add(new Hit(x, y, x + w, y + h, clip.clone(), hd, tip)); }
    private boolean inside(Hit h, double x, double y) {
        return x >= h.x0 && x < h.x1 && y >= h.y0 && y < h.y1 && x >= h.clip[0] && x < h.clip[2] && y >= h.clip[1] && y < h.clip[3];
    }
    private boolean hov(int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h && mx >= clip[0] && mx < clip[2] && my >= clip[1] && my < clip[3];
    }
    private float k() { return 1f - (float) Math.exp(-16f * dt * speed); }
    private static double clamp01(double v) { return Math.max(0, Math.min(1, v)); }
    private void front(Win w) { if (wins.remove(w)) wins.add(w); }

    private void pill(DrawContext g, int x, int y, float t) {
        Render2D.rrect(g, x, y, 20, 10, 5, c(Fx.mix(0xFF2C2C36, Theme.accent(), t)));
        Render2D.rrect(g, x + 1 + Math.round(t * 10), y + 1, 8, 8, 4, c(0xFFFFFFFF));
    }

    // --------------------------------------------------------------------------------------------- render
    @Override public void render(DrawContext g, int mouseX, int mouseY, float delta) {
        mx = mouseX; my = mouseY;
        long now = System.nanoTime();
        dt = Math.min(0.1f, (now - last) / 1e9f); last = now;
        Interface.I.frame();
        speed = (float) Interface.I.animSpeed.d();
        open += (1f - open) * (1f - (float) Math.exp(-12f * dt * speed));
        alpha = open;
        hits.clear();
        setClip(0, 0, width, height);

        g.fill(0, 0, width, height, ((int) (0x99 * alpha) << 24) | 0x050508);
        renderTop(g);
        // HUD widget outlines (drag them when no window is under the cursor)
        for (Mod m : Mods.all())
            if (m instanceof HudMod hm && hm.enabled)
                Render2D.outline(g, (int) hm.x.d() - 1, (int) hm.y.d() - 1, (int) (hm.x.d() + hm.w * hm.scale.d()) + 1,
                        (int) (hm.y.d() + hm.h * hm.scale.d()) + 1, 1, c(Fx.alpha(Theme.accent(), 0.6f)));

        if (query.isEmpty()) for (Win w : new ArrayList<>(wins)) renderWin(g, w, Mods.in(w.cat), w.title);
        else { List<Mod> r = Mods.search(query); renderWin(g, search, r, "Search  (" + r.size() + ")"); }

        if (Interface.I.tooltips.value) {
            for (int i = hits.size() - 1; i >= 0; i--) {
                Hit h = hits.get(i);
                if (!inside(h, mx, my)) continue;
                if (h.tip != null && !h.tip.isEmpty()) {
                    int tw = textRenderer.getWidth(h.tip) + 10;
                    int tx = Math.min(width - tw - 4, mx + 10), ty = Math.min(height - 18, my + 12);
                    Render2D.rrect(g, tx, ty, tw, 15, 3, c(0xF0000000 | 0x1C1C24));
                    g.drawText(textRenderer, h.tip, tx + 5, ty + 4, c(Theme.TEXT), false);
                }
                break;
            }
        }
    }

    private void renderTop(DrawContext g) {
        int acc = Theme.accent();
        g.drawText(textRenderer, Text.literal("NEXUS").formatted(net.minecraft.util.Formatting.BOLD), 10, 10, c(acc), true);
        g.drawText(textRenderer, "CLIENT  v1.0", 10 + textRenderer.getWidth("NEXUS") + 6, 10, c(Theme.DIM), true);
        int sw = 220, sx = width / 2 - sw / 2;
        Render2D.rrect(g, sx - 1, 7, sw + 2, 18, 5, c(Fx.alpha(acc, focus == null ? 0.7f : 0.2f)));
        Render2D.rrect(g, sx, 8, sw, 16, 4, c(0xFF14141A));
        boolean cursor = focus == null && (System.currentTimeMillis() / 500) % 2 == 0;
        String shown = query.isEmpty() ? "Search modules and settings..." : query + (cursor ? "_" : "");
        g.drawText(textRenderer, shown, sx + 8, 12, c(query.isEmpty() ? Theme.DIM : Theme.TEXT), false);
        String prof = "Profile: " + ConfigManager.current;
        g.drawText(textRenderer, prof, width - 10 - textRenderer.getWidth(prof), 10, c(Theme.DIM), true);
        String hint = "L-click: toggle   R-click: settings   M-click: keybind   Drag headers/HUD widgets   Esc: close";
        g.drawText(textRenderer, hint, width / 2 - textRenderer.getWidth(hint) / 2, height - 11, c(0xFF5A5A6A), false);
        setClip(0, 0, width, height);
        hit(sx, 8, sw, 16, (a, b, btn) -> { focus = null; return true; }, null);
    }

    private void renderWin(DrawContext g, Win w, List<Mod> mods, String title) {
        int x = w.x, y = w.y + Math.round((1f - alpha) * 14f);
        int r = Theme.radius(), acc = Theme.accent();
        float target = w.collapsed ? 0 : Math.min(w.contentH, Math.max(40, height - w.y - HEAD - 12));
        w.shownH += (target - w.shownH) * (1f - (float) Math.exp(-14f * dt));
        int bodyH = Math.round(w.shownH), totalH = HEAD + bodyH;

        Render2D.rrect(g, x - 1, y - 1, W + 2, totalH + 2, r + 1, c(Fx.alpha(acc, 0.35f)));
        Render2D.rrect(g, x, y, W, totalH, r, c(Theme.bg()));
        Render2D.rrect(g, x, y, W, HEAD, r, c(Theme.HEAD));
        if (bodyH > 0) g.fill(x, y + HEAD - r, x + W, y + HEAD, c(Theme.HEAD));
        g.fill(x + 1, y + HEAD - 1, x + W - 1, y + HEAD, c(Fx.alpha(acc, 0.8f)));
        g.drawText(textRenderer, title, x + 9, y + 7, c(Theme.TEXT), false);
        String ind = w.collapsed ? "+" : "-";
        g.drawText(textRenderer, ind, x + W - 12, y + 7, c(Theme.DIM), false);

        setClip(0, 0, width, height);
        hit(x, y, W, HEAD, (a, b, btn) -> {
            front(w);
            if (btn == 1) { w.collapsed = !w.collapsed; return true; }
            dragWin = w; offX = a - w.x; offY = b - w.y; return true;
        }, null);
        setClip(x, y + HEAD, x + W, y + HEAD + bodyH);
        hit(x, y + HEAD, W, bodyH, (a, b, btn) -> { front(w); return true; }, null);
        if (bodyH <= 2) return;

        w.scroll = Math.max(0, Math.min(w.scroll, Math.max(0, w.contentH - bodyH)));
        g.enableScissor(x, y + HEAD, x + W, y + HEAD + bodyH);
        int start = y + HEAD + 2 - Math.round(w.scroll), cy = start;
        if (mods.isEmpty() && w == search) g.drawText(textRenderer, "No matches", x + 10, cy + 4, c(Theme.DIM), false);
        for (Mod m : mods) cy = drawModule(g, m, x, cy);
        w.contentH = cy - start + 6 + (mods.isEmpty() ? 16 : 0);
        g.disableScissor();
        if (w.contentH > bodyH) {
            int th = Math.max(12, bodyH * bodyH / (int) w.contentH);
            int ty = y + HEAD + Math.round((bodyH - th) * (w.scroll / Math.max(1f, w.contentH - bodyH)));
            Render2D.rrect(g, x + W - 3, ty, 2, th, 1, c(Fx.alpha(acc, 0.7f)));
        }
    }

    // ---------------------------------------------------------------------------------------------- module
    private int drawModule(DrawContext g, Mod m, int x, int y) {
        boolean hov = hov(x + 3, y, W - 6, ROW - 1);
        float k = k();
        m.animHover += ((hov ? 1f : 0f) - m.animHover) * k;
        m.animOn += ((m.enabled ? 1f : 0f) - m.animOn) * k;
        m.animOpen += ((m.expanded ? 1f : 0f) - m.animOpen) * k;
        int acc = Theme.accent(), rr = Math.max(0, Theme.radius() - 1);
        if (m.animHover > 0.01f) Render2D.rrect(g, x + 3, y, W - 6, ROW - 1, rr, c(((int) (0x1C * m.animHover) << 24) | 0xFFFFFF));
        if (m.animOn > 0.01f) {
            Render2D.rrect(g, x + 3, y, W - 6, ROW - 1, rr, c(Fx.alpha(acc, 0.22f * m.animOn)));
            g.fill(x + 3, y + 3, x + 5, y + ROW - 4, c(Fx.alpha(acc, m.animOn)));
        }
        g.drawText(textRenderer, m.name, x + 10, y + 5, c(Fx.mix(Theme.DIM, Theme.TEXT, Math.max(m.animOn, m.animHover * 0.6f))), false);
        int right = x + W - 8;
        if (!m.alwaysOn) { pill(g, x + W - 30, y + 4, m.animOn); right = x + W - 36; }
        else g.drawText(textRenderer, m.expanded ? "-" : "+", x + W - 14, y + 5, c(Theme.DIM), false);
        if (m.key > 0 && !m.alwaysOn) {
            String kn = Fx.keyName(m.key);
            g.drawText(textRenderer, kn, right - textRenderer.getWidth(kn), y + 5, c(Fx.alpha(acc, 0.9f)), false);
        }
        hit(x + 3, y, W - 6, ROW - 1, (a, b, btn) -> {
            if (btn == 0) { if (m.alwaysOn) m.expanded = !m.expanded; else m.toggle(); }
            else if (btn == 1) m.expanded = !m.expanded;
            else if (btn == 2) binding = m;
            return true;
        }, m.description);
        y += ROW;

        if (m.animOpen > 0.01f) {
            int full = settingsHeight(m), shown = Math.round(full * m.animOpen);
            int[] saved = clip.clone();
            setClip(Math.max(saved[0], x), Math.max(saved[1], y), Math.min(saved[2], x + W), Math.min(saved[3], y + shown));
            g.enableScissor(x, y, x + W, y + shown);
            int sy = y;
            sy = drawBind(g, m, x, sy);
            for (Mod.Group gr : m.groups) {
                if (!gr.name.isEmpty()) sy = drawGroup(g, gr, x, sy);
                if (gr.name.isEmpty() || gr.open)
                    for (Setting s : gr.settings) if (!s.hidden && s.visible.getAsBoolean()) sy = drawSetting(g, s, x, sy);
            }
            g.disableScissor();
            clip = saved;
            y += shown;
        }
        return y;
    }

    private int settingsHeight(Mod m) {
        int h = 16 + 4;
        for (Mod.Group gr : m.groups) {
            if (!gr.name.isEmpty()) h += 16;
            if (gr.name.isEmpty() || gr.open)
                for (Setting s : gr.settings) if (!s.hidden && s.visible.getAsBoolean()) h += heightOf(s);
        }
        return h;
    }

    private int heightOf(Setting s) {
        if (s instanceof Setting.Num) return 22;
        if (s instanceof Setting.Text) return 28;
        if (s instanceof Setting.Action) return 18;
        if (s instanceof Setting.Color) return 16 + Math.round(PICK_H * s.anim);
        return 16;
    }

    private int drawBind(DrawContext g, Mod m, int x, int y) {
        g.drawText(textRenderer, "Keybind", x + 10, y + 4, c(Theme.DIM), false);
        String t = binding == m ? "Press a key..." : "[" + Fx.keyName(m.key) + "]";
        int col = binding == m ? Theme.accent() : Theme.TEXT;
        g.drawText(textRenderer, t, x + W - 10 - textRenderer.getWidth(t), y + 4, c(col), false);
        hit(x + 3, y, W - 6, 16, (a, b, btn) -> { if (btn == 1) m.key = -1; else binding = m; return true; }, "Click to bind, right-click to clear");
        return y + 16;
    }

    private int drawGroup(DrawContext g, Mod.Group gr, int x, int y) {
        Render2D.rrect(g, x + 5, y + 1, W - 10, 14, 3, c(0x18FFFFFF));
        g.drawText(textRenderer, gr.name, x + 11, y + 4, c(Fx.alpha(Theme.accent(), 0.95f)), false);
        g.drawText(textRenderer, gr.open ? "-" : "+", x + W - 14, y + 4, c(Theme.DIM), false);
        hit(x + 5, y + 1, W - 10, 14, (a, b, btn) -> { gr.open = !gr.open; return true; }, null);
        return y + 16;
    }

    // --------------------------------------------------------------------------------------------- settings
    private int drawSetting(DrawContext g, Setting s, int x, int y) {
        final int px = x + 9, pw = W - 18;
        float k = k();
        boolean h = hov(x + 3, y, W - 6, heightOf(s) >= 22 ? 22 : 16);
        int lc = Fx.mix(Theme.DIM, Theme.TEXT, h ? 1f : 0f);

        if (s instanceof Setting.Bool b) {
            s.anim += ((b.value ? 1f : 0f) - s.anim) * k;
            g.drawText(textRenderer, s.name, px, y + 4, c(lc), false);
            pill(g, x + W - 30, y + 3, s.anim);
            hit(x + 3, y, W - 6, 16, (a, bb, btn) -> { b.value = !b.value; return true; }, s.description);
            return y + 16;
        }
        if (s instanceof Setting.Num n) {
            g.drawText(textRenderer, s.name, px, y + 3, c(lc), false);
            String v = n.display();
            g.drawText(textRenderer, v, px + pw - textRenderer.getWidth(v), y + 3, c(Theme.accent()), false);
            double f = (n.value - n.min) / (n.max - n.min);
            Render2D.rrect(g, px, y + 15, pw, 3, 1, c(Theme.TRACK));
            Render2D.rrect(g, px, y + 15, Math.max(2, (int) (pw * f)), 3, 1, c(Theme.accent()));
            Render2D.rrect(g, px + (int) (pw * f) - 2, y + 13, 5, 7, 2, c(0xFFFFFFFF));
            hit(x + 3, y, W - 6, 22, (a, bb, btn) -> {
                if (btn == 1) { n.reset(); return true; }
                Dragger d = (mx2, my2) -> n.set(n.min + clamp01((mx2 - px) / pw) * (n.max - n.min));
                d.to(a, bb); drag = d; return true;
            }, s.description.isEmpty() ? "Right-click to reset" : s.description);
            return y + 22;
        }
        if (s instanceof Setting.Mode m) {
            g.drawText(textRenderer, s.name, px, y + 4, c(lc), false);
            String v = m.get();
            g.drawText(textRenderer, v, px + pw - textRenderer.getWidth(v), y + 4, c(Theme.accent()), false);
            hit(x + 3, y, W - 6, 16, (a, bb, btn) -> { if (btn == 1) m.prev(); else m.next(); return true; }, s.description);
            return y + 16;
        }
        if (s instanceof Setting.Color cs) return drawColor(g, cs, x, y, lc);
        if (s instanceof Setting.Text t) {
            g.drawText(textRenderer, s.name, px, y + 2, c(lc), false);
            boolean f = focus == t;
            Render2D.rrect(g, px - 1, y + 11, pw + 2, 15, 3, c(f ? Fx.alpha(Theme.accent(), 0.8f) : 0xFF2A2A34));
            Render2D.rrect(g, px, y + 12, pw, 13, 3, c(Theme.FIELD));
            String v = t.value;
            while (v.length() > 0 && textRenderer.getWidth(v) > pw - 10) v = v.substring(1);
            boolean cur = f && (System.currentTimeMillis() / 500) % 2 == 0;
            g.drawText(textRenderer, v + (cur ? "_" : ""), px + 4, y + 15, c(Theme.TEXT), false);
            hit(x + 3, y, W - 6, 28, (a, bb, btn) -> { focus = t; binding = null; return true; }, s.description);
            return y + 28;
        }
        if (s instanceof Setting.Action ac) {
            boolean hv = hov(px, y + 1, pw, 14);
            Render2D.rrect(g, px, y + 1, pw, 14, 3, c(hv ? Fx.alpha(Theme.accent(), 0.55f) : 0xFF22222B));
            g.drawText(textRenderer, s.name, px + pw / 2 - textRenderer.getWidth(s.name) / 2, y + 4, c(Theme.TEXT), false);
            hit(px, y + 1, pw, 14, (a, bb, btn) -> { ac.run.run(); return true; }, s.description);
            return y + 18;
        }
        return y;
    }

    private int drawColor(DrawContext g, Setting.Color cs, int x, int y, int lc) {
        final int px = x + 9, pw = W - 18;
        cs.anim += ((cs.open ? 1f : 0f) - cs.anim) * k();
        g.drawText(textRenderer, cs.name, px, y + 4, c(lc), false);
        Render2D.rrect(g, px + pw - 24, y + 2, 24, 12, 3, c(0xFF2A2A34));
        Render2D.rrect(g, px + pw - 23, y + 3, 22, 10, 2, c(cs.argb() | 0xFF000000));
        String hex = String.format("#%06X", cs.rgb());
        g.drawText(textRenderer, hex, px + pw - 30 - textRenderer.getWidth(hex), y + 4, c(Theme.DIM), false);
        hit(x + 3, y, W - 6, 16, (a, b, btn) -> { cs.open = !cs.open; return true; }, "Click to open the colour picker");
        y += 16;
        if (cs.anim > 0.01f) {
            int shown = Math.round(PICK_H * cs.anim);
            int[] saved = clip.clone();
            setClip(Math.max(saved[0], x), Math.max(saved[1], y), Math.min(saved[2], x + W), Math.min(saved[3], y + shown));
            g.enableScissor(x, y, x + W, y + shown);
            picker(g, cs, x, y);
            g.disableScissor();
            clip = saved;
            y += shown;
        }
        return y;
    }

    private void picker(DrawContext g, Setting.Color cs, int x, int y) {
        final int px = x + 9, pw = W - 18, cols = pw / 2, fw = cols * 2;
        int cy = y + 2;
        // saturation / value square
        final int svY = cy, svH = 52;
        for (int i = 0; i < cols; i++) {
            float sat = i / (float) (cols - 1);
            g.fillGradient(px + i * 2, svY, px + i * 2 + 2, svY + svH, c(0xFF000000 | Setting.Color.hsvToRgb(cs.h, sat, 1f)), c(0xFF000000));
        }
        int cx = px + Math.round(cs.s * (fw - 1)), cyy = svY + Math.round((1f - cs.v) * (svH - 1));
        g.fill(cx - 3, cyy - 3, cx + 4, cyy + 4, c(0xFFFFFFFF));
        g.fill(cx - 2, cyy - 2, cx + 3, cyy + 3, c(0xFF000000 | cs.rgb()));
        hit(px, svY, fw, svH, (a, b, btn) -> {
            Dragger d = (m1, m2) -> cs.setHsv(cs.h, (float) clamp01((m1 - px) / fw), (float) (1 - clamp01((m2 - svY) / (double) svH)));
            d.to(a, b); drag = d; return true;
        }, null);
        cy += svH + 4;
        // hue
        final int hY = cy;
        for (int i = 0; i < cols; i++) g.fill(px + i * 2, hY, px + i * 2 + 2, hY + 8, c(0xFF000000 | Setting.Color.hsvToRgb(i / (float) cols, 1f, 1f)));
        g.fill(px + Math.round(cs.h * (fw - 2)), hY - 1, px + Math.round(cs.h * (fw - 2)) + 2, hY + 9, c(0xFFFFFFFF));
        hit(px, hY, fw, 8, (a, b, btn) -> {
            Dragger d = (m1, m2) -> cs.setHsv((float) clamp01((m1 - px) / fw), cs.s, cs.v);
            d.to(a, b); drag = d; return true;
        }, "Hue");
        cy += 12;
        // alpha
        final int aY = cy;
        g.fill(px, aY, px + fw, aY + 8, c(0xFF2A2A30));
        for (int i = 0; i < cols; i++) g.fill(px + i * 2, aY, px + i * 2 + 2, aY + 8, c(((int) (255f * i / cols) << 24) | cs.rgb()));
        g.fill(px + Math.round(cs.alpha / 255f * (fw - 2)), aY - 1, px + Math.round(cs.alpha / 255f * (fw - 2)) + 2, aY + 9, c(0xFFFFFFFF));
        hit(px, aY, fw, 8, (a, b, btn) -> {
            Dragger d = (m1, m2) -> cs.setAlpha((int) Math.round(clamp01((m1 - px) / fw) * 255));
            d.to(a, b); drag = d; return true;
        }, "Opacity");
        cy += 12;
        // RGB sliders
        String[] names = {"R", "G", "B"};
        int[] tint = {0xFFFF5555, 0xFF55FF55, 0xFF5599FF};
        for (int ch = 0; ch < 3; ch++) {
            final int chan = ch, ry = cy, shift = 16 - 8 * ch;
            int val = (cs.rgb() >> shift) & 255;
            g.drawText(textRenderer, names[ch], px, ry + 1, c(tint[ch]), false);
            int tx = px + 10, tw = pw - 34;
            Render2D.rrect(g, tx, ry + 3, tw, 4, 2, c(Theme.TRACK));
            Render2D.rrect(g, tx, ry + 3, Math.max(2, tw * val / 255), 4, 2, c(tint[ch]));
            String vs = Integer.toString(val);
            g.drawText(textRenderer, vs, px + pw - textRenderer.getWidth(vs), ry + 1, c(Theme.DIM), false);
            hit(tx - 2, ry, tw + 4, 10, (a, b, btn) -> {
                Dragger d = (m1, m2) -> {
                    int v = (int) Math.round(clamp01((m1 - tx) / (double) tw) * 255);
                    int rgb = cs.rgb();
                    rgb = (rgb & ~(255 << shift)) | (v << shift);
                    cs.rainbow = false;
                    cs.setRgb(rgb);
                };
                d.to(a, b); drag = d; return true;
            }, null);
            cy += 11;
        }
        cy += 3;
        // presets
        for (int i = 0; i < PRESETS.length; i++) {
            final int col = PRESETS[i], sx = px + i * 18;
            Render2D.rrect(g, sx, cy, 16, 10, 3, c(0xFF2A2A34));
            Render2D.rrect(g, sx + 1, cy + 1, 14, 8, 2, c(col));
            hit(sx, cy, 16, 10, (a, b, btn) -> { cs.rainbow = false; cs.setRgb(col & 0xFFFFFF); return true; }, "Preset");
        }
        cy += 14;
        // rainbow
        g.drawText(textRenderer, "Rainbow", px, cy + 2, c(Theme.DIM), false);
        pill(g, px + pw - 20, cy, cs.rainbow ? 1f : 0f);
        hit(px, cy, pw, 12, (a, b, btn) -> { cs.rainbow = !cs.rainbow; return true; }, "Cycle through all hues");
    }

    // ----------------------------------------------------------------------------------------------- input
    @Override public boolean mouseClicked(Click click, boolean doubled) {
        double x = click.x(), y = click.y();
        int btn = click.button();
        focus = null; binding = null;
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit h = hits.get(i);
            if (inside(h, x, y) && h.h.on(x, y, btn)) return true;
        }
        if (btn == 0)
            for (Mod m : Mods.all())
                if (m instanceof HudMod hm && hm.enabled && hm.hit(x, y)) {
                    double ox = x - hm.x.d(), oy = y - hm.y.d();
                    drag = (a, b) -> { hm.x.set(a - ox); hm.y.set(b - oy); };
                    return true;
                }
        return super.mouseClicked(click, doubled);
    }

    @Override public boolean mouseDragged(Click click, double dx, double dy) {
        if (drag != null) { drag.to(click.x(), click.y()); return true; }
        if (dragWin != null) {
            dragWin.x = (int) Math.max(-W + 30, Math.min(width - 30, click.x() - offX));
            dragWin.y = (int) Math.max(0, Math.min(height - HEAD, click.y() - offY));
            return true;
        }
        return super.mouseDragged(click, dx, dy);
    }

    @Override public boolean mouseReleased(Click click) {
        drag = null; dragWin = null;
        return super.mouseReleased(click);
    }

    @Override public boolean mouseScrolled(double x, double y, double hAmount, double vAmount) {
        List<Win> list = query.isEmpty() ? wins : List.of(search);
        for (int i = list.size() - 1; i >= 0; i--) {
            Win w = list.get(i);
            int bodyH = Math.round(w.shownH);
            if (x >= w.x && x < w.x + W && y >= w.y && y < w.y + HEAD + bodyH) {
                w.scroll = Math.max(0, Math.min(w.scroll - (float) vAmount * 16f, Math.max(0, w.contentH - bodyH)));
                return true;
            }
        }
        return super.mouseScrolled(x, y, hAmount, vAmount);
    }

    @Override public boolean charTyped(CharInput in) {
        String s = new String(Character.toChars(in.codepoint()));
        if (s.isEmpty() || s.charAt(0) < 32) return false;
        if (focus != null) { if (focus.value.length() < focus.maxLen) focus.value += s; return true; }
        if (binding == null && query.length() < 32) { query += s; return true; }
        return false;
    }

    @Override public boolean keyPressed(KeyInput in) {
        int k = in.key();
        if (binding != null) {
            if (k == GLFW.GLFW_KEY_ESCAPE) { }
            else if (k == GLFW.GLFW_KEY_BACKSPACE || k == GLFW.GLFW_KEY_DELETE) binding.key = -1;
            else binding.key = k;
            binding = null;
            return true;
        }
        if (focus != null) {
            if (k == GLFW.GLFW_KEY_ESCAPE || k == GLFW.GLFW_KEY_ENTER || k == GLFW.GLFW_KEY_KP_ENTER) focus = null;
            else if (k == GLFW.GLFW_KEY_BACKSPACE && !focus.value.isEmpty()) focus.value = focus.value.substring(0, focus.value.length() - 1);
            else if (k == GLFW.GLFW_KEY_V && (in.modifiers() & GLFW.GLFW_MOD_CONTROL) != 0) {
                String clip2 = GLFW.glfwGetClipboardString(client.getWindow().getHandle());
                if (clip2 != null) focus.value = (focus.value + clip2.replace("\n", "")).substring(0, Math.min(focus.maxLen, (focus.value + clip2).length()));
            }
            return true;
        }
        if (k == GLFW.GLFW_KEY_ESCAPE) { if (!query.isEmpty()) query = ""; else close(); return true; }
        if (k == GLFW.GLFW_KEY_BACKSPACE && !query.isEmpty()) { query = query.substring(0, query.length() - 1); return true; }
        if (k == Interface.I.key && query.isEmpty()) { close(); return true; }
        return super.keyPressed(in);
    }
}
