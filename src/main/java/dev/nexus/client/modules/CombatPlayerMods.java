package dev.nexus.client.modules;

import dev.nexus.client.gui.Theme;
import dev.nexus.client.module.HasEsp;
import dev.nexus.client.module.HudMod;
import dev.nexus.client.module.Mod;
import dev.nexus.client.render.EspStyle;
import dev.nexus.client.render.LabelStyle;
import dev.nexus.client.render.Projector;
import dev.nexus.client.render.Render2D;
import dev.nexus.client.setting.Setting;
import dev.nexus.client.util.Fx;
import dev.nexus.client.util.Notify;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.DeathScreen;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

import java.util.List;
import java.util.Locale;

/** Combat-category information modules (no automation) and Player-category utilities. */
final class CombatPlayerMods {
    private CombatPlayerMods() { }

    static final class TargetHud extends HudMod {
        private LivingEntity last; private long seen;
        TargetHud() { super("Target HUD", "Health, armour and distance of the entity you are looking at.", Category.COMBAT, 200, 140); }
        @Override protected void render(DrawContext c, MinecraftClient mc, float t) {
            Entity e = mc.targetedEntity;
            long now = System.currentTimeMillis();
            if (e instanceof LivingEntity le) { last = le; seen = now; } else if (now - seen > 2000) last = null;
            if (last == null || last.isRemoved()) return;
            var tr = mc.textRenderer;
            String name = last.getName().getString();
            float frac = Math.max(0f, Math.min(1f, last.getHealth() / Math.max(1f, last.getMaxHealth())));
            String hp = String.format(Locale.ROOT, "%.1f / %.0f", last.getHealth(), last.getMaxHealth());
            int bw = Math.max(110, tr.getWidth(name) + 10);
            begin(c, bw, 36);
            c.drawText(tr, name, 5, 4, textColor.argb(), true);
            c.drawText(tr, String.format(Locale.ROOT, "%.0fm  ARM %d", mc.player.distanceTo(last), last.getArmor()), 5, 14, 0xFF9FD3FF, true);
            Render2D.rrect(c, 5, 26, bw - 10, 5, 2, 0xFF22222B);
            Render2D.rrect(c, 5, 26, Math.max(2, Math.round((bw - 10) * frac)), 5, 2, Fx.healthColor(frac));
            c.drawText(tr, hp, bw - 5 - tr.getWidth(hp), 14, Fx.healthColor(frac), true);
            end(c);
        }
    }

    static final class TargetHighlight extends Mod implements HasEsp {
        private final EspStyle st; private final LabelStyle lab;
        TargetHighlight() {
            super("Target Highlight", "Draws an ESP box around the entity under your crosshair.", Category.COMBAT);
            needsProjection = true;
            Group g = group("Appearance");
            st = new EspStyle(g, false, 0xFFFFFFFF, 128, "Box + Fill", true);
            lab = new LabelStyle(group("Label"));
        }
        @Override public List<EspStyle> styles() { return List.of(st); }
        @Override public void onHud(DrawContext c, float t) {
            MinecraftClient mc = mc();
            Entity e = mc.targetedEntity;
            if (!(e instanceof LivingEntity le) || e.isRemoved()) return;
            Projector p = Projector.I;
            double x = Fx.lx(e, t), y = Fx.ly(e, t), z = Fx.lz(e, t), hw = e.getWidth() / 2.0, hh = e.getHeight();
            double d = Math.sqrt(p.dist2(x, y + hh / 2, z));
            if (!st.allows(d)) return;
            float frac = Math.max(0f, Math.min(1f, le.getHealth() / Math.max(1f, le.getMaxHealth())));
            Projector.Rect r = st.draw(c, p, x - hw, y, z - hw, x + hw, y + hh, z + hw, d, 0);
            if (r != null && st.label.value)
                lab.draw(c, mc.textRenderer, r.cx(), r.y0 - 2, d, List.of(lab.seg(e.getName().getString() + String.format(Locale.ROOT, " %.1f", le.getHealth()), Fx.healthColor(frac))));
        }
    }

    static final class ToggleSprint extends Mod {
        ToggleSprint() { super("Toggle Sprint", "Keeps you sprinting while moving forward.", Category.PLAYER); }
        @Override public void onTick(MinecraftClient mc) {
            var o = mc.options;
            if (o.forwardKey.isPressed() && !o.sneakKey.isPressed() && mc.currentScreen == null) o.sprintKey.setPressed(true);
        }
    }

    static final class DurabilityAlert extends Mod {
        private final Setting.Num threshold, interval;
        DurabilityAlert() {
            super("Durability Alert", "Warns when worn or held gear is nearly broken.", Category.PLAYER);
            Group g = group("Alert");
            threshold = g.num("Threshold", 10, 1, 50, 1).unit("%");
            interval = g.num("Repeat Every", 10, 2, 60, 1).unit(" s");
        }
        @Override public void onTick(MinecraftClient mc) {
            if (mc.player.age % (interval.i() * 20) != 0) return;
            for (EquipmentSlot s : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.MAINHAND}) {
                ItemStack is = mc.player.getEquippedStack(s);
                if (is.isEmpty() || !is.isDamageable()) continue;
                int pct = Math.round(100f * (1f - (float) is.getDamage() / is.getMaxDamage()));
                if (pct <= threshold.i()) Notify.toast("Low durability: " + is.getName().getString() + " (" + pct + "%)");
            }
        }
    }

    static final class DeathPoint extends Mod {
        private int x, y, z; private boolean dead;
        DeathPoint() { super("Death Point", "Prints the coordinates where you died.", Category.PLAYER); }
        @Override public void onTick(MinecraftClient mc) {
            if (mc.currentScreen instanceof DeathScreen) {
                if (!dead) { dead = true; Notify.chat("You died at " + x + " " + y + " " + z); }
            } else { dead = false; x = (int) mc.player.getX(); y = (int) mc.player.getY(); z = (int) mc.player.getZ(); }
        }
    }
}
