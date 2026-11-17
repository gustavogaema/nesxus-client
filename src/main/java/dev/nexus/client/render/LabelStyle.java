package dev.nexus.client.render;

import dev.nexus.client.module.Mod;
import dev.nexus.client.setting.Setting;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.MathHelper;

import java.util.List;

/** Shared text-label look: size, background opacity/colour, font style, shadow, distance scaling. */
public final class LabelStyle {
    public final Setting.Num size, bgOpacity;
    public final Setting.Color textColor, bgColor;
    public final Setting.Mode font;
    public final Setting.Bool shadow, rounded, scaleWithDistance;

    public LabelStyle(Mod.Group g) {
        size = g.num("Text Size", 1.0, 0.5, 3.0, 0.1);
        bgOpacity = g.num("Background Opacity", 45, 0, 100, 5).unit("%");
        bgColor = g.color("Background Color", 0xFF000000);
        textColor = g.color("Text Color", 0xFFFFFFFF);
        font = g.mode("Font Style", "Default", "Bold", "Italic", "Bold Italic");
        shadow = g.bool("Text Shadow", true);
        rounded = g.bool("Rounded Background", true);
        scaleWithDistance = g.bool("Scale With Distance", false);
    }

    /** A coloured text segment honouring the chosen font style. */
    public MutableText seg(String s, int rgb) {
        MutableText t = Text.literal(s).setStyle(Style.EMPTY.withColor(TextColor.fromRgb(rgb & 0xFFFFFF)));
        switch (font.get()) {
            case "Bold" -> t.formatted(Formatting.BOLD);
            case "Italic" -> t.formatted(Formatting.ITALIC);
            case "Bold Italic" -> t.formatted(Formatting.BOLD, Formatting.ITALIC);
            default -> { }
        }
        return t;
    }

    public float scale(double dist) {
        float k = scaleWithDistance.value ? MathHelper.clamp(14f / ((float) dist + 6f), 0.55f, 1.4f) : 1f;
        return size.f() * k;
    }

    /** Draws centred lines whose bottom edge sits at (cx, bottom). Returns the screen Y of the label's top edge. */
    public float draw(DrawContext c, TextRenderer tr, float cx, float bottom, double dist, List<Text> lines) {
        if (lines.isEmpty()) return bottom;
        float s = scale(dist);
        int lh = tr.fontHeight + 1, maxW = 0;
        for (Text t : lines) maxW = Math.max(maxW, tr.getWidth(t));
        int totalH = lines.size() * lh;
        c.getMatrices().pushMatrix();
        c.getMatrices().translate(cx, bottom);
        c.getMatrices().scale(s, s);
        int a = (int) (bgOpacity.d() * 2.55);
        if (a > 0) {
            int bg = (a << 24) | (bgColor.rgb());
            if (rounded.value) Render2D.rrect(c, -maxW / 2 - 3, -totalH - 3, maxW + 6, totalH + 4, 3, bg);
            else c.fill(-maxW / 2 - 3, -totalH - 3, maxW / 2 + 3, 1, bg);
        }
        int y = -totalH - 1;
        for (Text t : lines) {
            c.drawText(tr, t, -tr.getWidth(t) / 2, y, 0xFFFFFFFF, shadow.value);
            y += lh;
        }
        c.getMatrices().popMatrix();
        return bottom - (totalH + 3) * s;
    }
}
