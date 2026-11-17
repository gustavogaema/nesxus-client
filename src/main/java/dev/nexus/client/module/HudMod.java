package dev.nexus.client.module;

import dev.nexus.client.gui.Theme;
import dev.nexus.client.render.Render2D;
import dev.nexus.client.setting.Setting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

import java.util.List;

/** Base for draggable HUD widgets. Position is stored in (hidden) settings and edited by dragging in the GUI. */
public abstract class HudMod extends Mod {
    public record HudLine(String label, String value) { }

    public final Setting.Num x, y, scale, bgAlpha;
    public final Setting.Bool bg, accentBar, accentLabels;
    public final Setting.Color textColor;
    public int w = 60, h = 12;

    protected HudMod(String n, String d, Category c, int dx, int dy) {
        super(n, d, c);
        Group g = group("Style");
        x = g.num("X", dx, 0, 8192, 1); x.hidden = true;
        y = g.num("Y", dy, 0, 8192, 1); y.hidden = true;
        scale = g.num("Scale", 1.0, 0.5, 2.5, 0.1);
        bg = g.bool("Background", true);
        bgAlpha = g.num("Background Opacity", 55, 0, 100, 5).unit("%");
        accentBar = g.bool("Accent Bar", true);
        accentLabels = g.bool("Accent Labels", true);
        textColor = g.color("Text Color", 0xFFFFFFFF);
    }

    @Override public final void onHud(DrawContext ctx, float t) {
        MinecraftClient mc = mc();
        if (mc.player == null || mc.world == null) return;
        render(ctx, mc, t);
    }

    protected void render(DrawContext c, MinecraftClient mc, float t) { draw(c, lines(mc)); }
    protected List<HudLine> lines(MinecraftClient mc) { return List.of(); }

    public boolean hit(double mx, double my) {
        return mx >= x.d() && my >= y.d() && mx <= x.d() + w * scale.d() && my <= y.d() + h * scale.d();
    }

    protected void begin(DrawContext c, int bw, int bh) {
        w = bw; h = bh;
        float s = scale.f();
        x.set(Math.min(x.d(), Math.max(0, c.getScaledWindowWidth() - w * s)));
        y.set(Math.min(y.d(), Math.max(0, c.getScaledWindowHeight() - h * s)));
        c.getMatrices().pushMatrix();
        c.getMatrices().translate(x.f(), y.f());
        c.getMatrices().scale(s, s);
        if (bg.value) Render2D.rrect(c, 0, 0, w, h, 3, ((int) (bgAlpha.d() * 2.55) << 24) | 0x0B0B10);
        if (accentBar.value) c.fill(0, 3, 2, h - 3, Theme.accent());
    }
    protected void end(DrawContext c) { c.getMatrices().popMatrix(); }

    protected void draw(DrawContext c, List<HudLine> lines) {
        if (lines.isEmpty()) return;
        TextRenderer tr = mc().textRenderer;
        int lw = 0;
        for (HudLine l : lines) lw = Math.max(lw, tr.getWidth(l.label()) + (l.label().isEmpty() ? 0 : 4) + tr.getWidth(l.value()));
        begin(c, lw + 9, lines.size() * 10 + 4);
        int yy = 3, lc = accentLabels.value ? Theme.accent() : textColor.argb();
        for (HudLine l : lines) {
            int xx = 5;
            if (!l.label().isEmpty()) { c.drawText(tr, l.label(), xx, yy, lc, true); xx += tr.getWidth(l.label()) + 4; }
            c.drawText(tr, l.value(), xx, yy, textColor.argb(), true);
            yy += 10;
        }
        end(c);
    }
}
