package dev.nexus.client.util;

import dev.nexus.client.gui.Theme;
import dev.nexus.client.render.Render2D;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;

/** Small animated toasts + chat messages. */
public final class Notify {
    private record Toast(String text, long start, long end) { }
    private static final List<Toast> TOASTS = new ArrayList<>();
    private Notify() { }

    public static void toast(String s) {
        long now = System.currentTimeMillis();
        TOASTS.add(new Toast(s, now, now + 2600));
        if (TOASTS.size() > 5) TOASTS.remove(0);
    }

    public static void chat(String s) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.inGameHud == null) return;
        mc.inGameHud.getChatHud().addMessage(Text.literal("[Nexus] ").formatted(Formatting.LIGHT_PURPLE)
                .append(Text.literal(s).formatted(Formatting.WHITE)));
    }

    public static void info(String s) { toast(s); chat(s); }

    public static void render(DrawContext c) {
        if (TOASTS.isEmpty()) return;
        long now = System.currentTimeMillis();
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        TOASTS.removeIf(t -> now > t.end());
        int y = c.getScaledWindowHeight() - 34;
        for (int i = TOASTS.size() - 1; i >= 0; i--) {
            Toast t = TOASTS.get(i);
            float in = Math.min(1f, (now - t.start()) / 220f), out = Math.min(1f, (t.end() - now) / 350f);
            float a = Math.min(in, out), slide = (1f - in) * 24f;
            int w = tr.getWidth(t.text()) + 18, x = (int) (c.getScaledWindowWidth() - w - 8 + slide);
            Render2D.rrect(c, x, y, w, 18, 4, Fx.alpha(0xF0141419, a));
            c.fill(x + 3, y + 4, x + 5, y + 14, Fx.alpha(Theme.accent(), a));
            c.drawText(tr, t.text(), x + 10, y + 5, Fx.alpha(0xFFFFFFFF, a), true);
            y -= 22;
        }
    }
}
