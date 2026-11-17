package dev.nexus.client.modules;

import dev.nexus.client.module.Mod;
import dev.nexus.client.render.LabelStyle;
import dev.nexus.client.render.Projector;
import dev.nexus.client.setting.Setting;
import dev.nexus.client.util.Fx;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Fully customisable nametags: name, health, distance, ping, armour + held items with durability. */
final class Nametags extends Mod {
    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET,
            EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND};
    private final Setting.Bool players, hostile, passive, name, health, dist, ping, armor, held, durability;
    private final Setting.Mode healthStyle;
    private final Setting.Num range, yOffset;
    private final Setting.Color nameColor, distColor;
    private final LabelStyle lab;

    Nametags() {
        super("Nametags", "Custom nametags with health, distance, ping, armour and durability.", Category.VISUALS);
        needsProjection = true;
        Group t = group("Targets");
        players = t.bool("Players", true); hostile = t.bool("Hostile Mobs", false); passive = t.bool("Passive Mobs", false);
        range = t.num("Range", 64, 8, 256, 4).unit("m");
        Group c = group("Content");
        name = c.bool("Name", true); health = c.bool("Health", true);
        healthStyle = c.mode("Health Style", "Number", "Hearts", "Percent");
        dist = c.bool("Distance", true); ping = c.bool("Ping", false);
        armor = c.bool("Armor Icons", true); held = c.bool("Held Items", true); durability = c.bool("Durability %", true);
        yOffset = c.num("Y Offset", 0.2, 0, 2, 0.1);
        Group k = group("Colors");
        nameColor = k.color("Name Color", 0xFFFFFFFF);
        distColor = k.color("Distance Color", 0xFF9FD3FF);
        Group l = group("Label");
        lab = new LabelStyle(l);
    }

    @Override public void onHud(DrawContext c, float t) {
        MinecraftClient mc = mc();
        Projector p = Projector.I;
        TextRenderer tr = mc.textRenderer;
        int n = 0, max = GlobalEsp.I.maxRender.i();
        for (Entity e : mc.world.getEntities()) {
            if (e == mc.player || !(e instanceof LivingEntity le) || e.isRemoved()) continue;
            boolean ok = (e instanceof PlayerEntity && players.value) || (e instanceof Monster && hostile.value) || (e instanceof AnimalEntity && passive.value);
            if (!ok) continue;
            double x = Fx.lx(e, t), y = Fx.ly(e, t), z = Fx.lz(e, t);
            double d = Math.sqrt(p.dist2(x, y + e.getHeight() / 2, z));
            if (d > Math.min(range.d(), GlobalEsp.I.maxRange.d())) continue;
            float[] pt = p.point(x, y + e.getHeight() + yOffset.d(), z);
            if (pt == null) continue;
            float cx = pt[0], by = pt[1];

            List<Text> lines = new ArrayList<>();
            MutableText l1 = Text.empty();
            float frac = Math.max(0f, Math.min(1f, le.getHealth() / Math.max(1f, le.getMaxHealth())));
            if (name.value) l1.append(lab.seg(le.getName().getString(), nameColor.rgb()));
            if (health.value) {
                String h = switch (healthStyle.get()) {
                    case "Hearts" -> String.format(Locale.ROOT, "\u2665%.1f", (le.getHealth() + le.getAbsorptionAmount()) / 2f);
                    case "Percent" -> Math.round(frac * 100) + "%";
                    default -> String.format(Locale.ROOT, "%.1f", le.getHealth() + le.getAbsorptionAmount());
                };
                l1.append(lab.seg((name.value ? " " : "") + h, Fx.healthColor(frac)));
            }
            if (name.value || health.value) lines.add(l1);
            MutableText l2 = Text.empty();
            if (dist.value) l2.append(lab.seg(String.format(Locale.ROOT, "%.0fm", d), distColor.rgb()));
            if (ping.value && e instanceof PlayerEntity pe && mc.getNetworkHandler() != null) {
                PlayerListEntry pl = mc.getNetworkHandler().getPlayerListEntry(pe.getUuid());
                if (pl != null) l2.append(lab.seg((dist.value ? "  " : "") + pl.getLatency() + "ms", lab.textColor.rgb()));
            }
            if (!l2.getString().isEmpty()) lines.add(l2);

            float top = lines.isEmpty() ? by : lab.draw(c, tr, cx, by, d, lines);
            if (armor.value || held.value) {
                List<ItemStack> st = new ArrayList<>();
                for (int i = 0; i < SLOTS.length; i++) {
                    if (i < 4 && !armor.value) continue;
                    if (i >= 4 && !held.value) continue;
                    ItemStack is = le.getEquippedStack(SLOTS[i]);
                    if (!is.isEmpty()) st.add(is);
                }
                if (!st.isEmpty()) row(c, tr, cx, top - 1, st, lab.scale(d));
            }
            if (++n >= max) break;
        }
    }

    private void row(DrawContext c, TextRenderer tr, float cx, float bottom, List<ItemStack> st, float s) {
        float k = s * 0.8f;
        int w = st.size() * 18, h = durability.value ? 26 : 18, x = -w / 2;
        c.getMatrices().pushMatrix();
        c.getMatrices().translate(cx, bottom);
        c.getMatrices().scale(k, k);
        for (ItemStack is : st) {
            c.drawItem(is, x + 1, -h + 1);
            c.drawStackOverlay(tr, is, x + 1, -h + 1);
            if (durability.value && is.isDamageable()) {
                float f = 1f - (float) is.getDamage() / is.getMaxDamage();
                String s2 = Math.round(f * 100) + "%";
                c.getMatrices().pushMatrix();
                c.getMatrices().translate(x + 9, -h + 18);
                c.getMatrices().scale(0.6f, 0.6f);
                c.drawText(tr, s2, -tr.getWidth(s2) / 2, 0, Fx.healthColor(f), true);
                c.getMatrices().popMatrix();
            }
            x += 18;
        }
        c.getMatrices().popMatrix();
    }
}
