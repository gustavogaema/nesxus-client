package dev.nexus.client.modules;

import dev.nexus.client.module.Mod;
import dev.nexus.client.render.Projector;
import dev.nexus.client.render.Render2D;
import dev.nexus.client.setting.Setting;
import dev.nexus.client.util.Fx;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.player.PlayerEntity;

/** Tracer lines. Other ESP modules queue lines (their per-category "Tracer" switch); this draws them last. */
final class Tracers extends Mod {
    private static Tracers I;
    private static final int CAP = 1024;
    private static final float[] QX = new float[CAP], QY = new float[CAP];
    private static final int[] QC = new int[CAP], QT = new int[CAP];
    private static int qn;

    private final Setting.Mode origin;
    private final Setting.Num thickness, opacity, range;
    private final Setting.Bool players, monsters, animals;
    private final Setting.Color pc, mc2, ac;

    Tracers() {
        super("Tracers", "Lines from your screen to targets. Also draws every ESP category's Tracer toggle.", Category.VISUALS);
        I = this; needsProjection = true;
        Group g = group("Tracers");
        origin = g.mode("Origin", "Bottom", "Center", "Top");
        thickness = g.num("Thickness", 1, 1, 4, 1);
        opacity = g.num("Opacity", 80, 10, 100, 5).unit("%");
        range = g.num("Own Target Range", 96, 8, 256, 4).unit("m");
        players = g.bool("Players", true); pc = g.color("Player Color", 0xFFFF4D6D);
        monsters = g.bool("Monsters", false); mc2 = g.color("Monster Color", 0xFFFF5555);
        animals = g.bool("Animals", false); ac = g.color("Animal Color", 0xFF55FF55);
    }

    static void queue(float x, float y, int col, int t) {
        if (I == null || !I.enabled || qn >= CAP) return;
        QX[qn] = x; QY[qn] = y; QC[qn] = col; QT[qn] = t; qn++;
    }

    @Override public void onHud(DrawContext c, float t) {
        MinecraftClient mc = mc();
        Projector p = Projector.I;
        float ox = p.w / 2f, oy = switch (origin.get()) { case "Top" -> 0f; case "Center" -> p.h / 2f; default -> (float) p.h; };
        float op = opacity.f() / 100f;
        int th = thickness.i(), n = 0, max = GlobalEsp.I.maxRender.i();
        if (players.value || monsters.value || animals.value) {
            for (Entity e : mc.world.getEntities()) {
                if (e == mc.player || !(e instanceof LivingEntity) || e.isRemoved()) continue;
                int col;
                if (e instanceof PlayerEntity && players.value) col = pc.argb();
                else if (e instanceof Monster && monsters.value) col = mc2.argb();
                else if (e instanceof AnimalEntity && animals.value) col = ac.argb();
                else continue;
                double x = Fx.lx(e, t), y = Fx.ly(e, t) + e.getHeight() / 2, z = Fx.lz(e, t);
                if (Math.sqrt(p.dist2(x, y, z)) > Math.min(range.d(), GlobalEsp.I.maxRange.d())) continue;
                float[] pt = p.point(x, y, z);
                if (pt == null) continue;
                Render2D.line(c, ox, oy, pt[0], pt[1], th, Fx.alpha(col, op), p.w, p.h);
                if (++n >= max) break;
            }
        }
        for (int i = 0; i < qn; i++) Render2D.line(c, ox, oy, QX[i], QY[i], Math.max(th, QT[i]), Fx.alpha(QC[i], op), p.w, p.h);
        qn = 0;
    }
}
