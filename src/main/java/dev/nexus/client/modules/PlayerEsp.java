package dev.nexus.client.modules;

import dev.nexus.client.module.HasEsp;
import dev.nexus.client.module.Mod;
import dev.nexus.client.render.EspStyle;
import dev.nexus.client.render.LabelStyle;
import dev.nexus.client.render.Projector;
import dev.nexus.client.setting.Setting;
import dev.nexus.client.util.Fx;
import dev.nexus.client.util.GlowManager;
import dev.nexus.client.util.Notify;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.PlayerListEntry;
if (sound.value) mc.player.playSound(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Player ESP: boxes, health bar, custom text format, friends, glow outline and range alerts. */
final class PlayerEsp extends Mod implements HasEsp {
    private static final Pattern TOKEN = Pattern.compile("\\{(\\w+)}");

    private final EspStyle st;
    private final LabelStyle lab;
    private final Setting.Mode colorMode;
    private final Setting.Bool healthBar, glow, detect, sound;
    private final Setting.Color friendColor, nameColor, distColor;
    private final Setting.Text friends, format;
    private final Setting.Num detectRange;
    private final Set<UUID> known = new HashSet<>();
    private String friendSrc = "";
    private Set<String> friendSet = Set.of();

    PlayerEsp() {
        super("Player ESP", "Boxes, health bars, names and distance for players; entry alerts and glow outlines.", Category.ESP);
        needsProjection = true;
        Group a = group("Appearance");
        st = new EspStyle(a, false, 0xFFFF4D6D, 128, "Corners", true);
        colorMode = a.mode("Color Mode", "Fixed", "Health", "Distance");
        healthBar = a.bool("Health Bar", true);
        glow = a.bool("Outline (Glow)", false);
        glow.desc("Vanilla glow outline - visible through walls.");
        Group t = group("Text");
        format = t.text("Text Format", "{name} {health} {dist}");
        format.desc("Tokens: {name} {health} {max} {pct} {dist} {ping} {armor}");
        nameColor = t.color("Name Color", 0xFFFFFFFF);
        distColor = t.color("Distance Color", 0xFF9FD3FF);
        lab = new LabelStyle(t);
        Group f = group("Friends");
        friends = f.text("Friend Names", "");
        friends.desc("Comma separated player names.");
        friendColor = f.color("Friend Color", 0xFF4DFF88);
        Group d = group("Detection");
        detect = d.bool("Alert On Entry", false);
        detectRange = d.num("Alert Range", 64, 8, 256, 4).unit("m");
        sound = d.bool("Alert Sound", true);
    }

    @Override public List<EspStyle> styles() { return List.of(st); }

    private boolean isFriend(String n) {
        if (!friends.value.equals(friendSrc)) {
            friendSrc = friends.value;
            Set<String> s = new HashSet<>();
            for (String p : friendSrc.split(",")) if (!p.isBlank()) s.add(p.trim().toLowerCase(Locale.ROOT));
            friendSet = s;
        }
        return friendSet.contains(n.toLowerCase(Locale.ROOT));
    }

    @Override public void onDisable() { known.clear(); }

    @Override public void onTick(MinecraftClient mc) {
        if (!glow.value && !detect.value) { known.clear(); return; }
        Set<UUID> now = new HashSet<>();
        for (AbstractClientPlayerEntity p : mc.world.getPlayers()) {
            if (p == mc.player) continue;
            double d = mc.player.distanceTo(p);
            if (glow.value && st.allows(d) && st.losOk(p)) GlowManager.mark(p);
            if (detect.value && d <= detectRange.d()) {
                now.add(p.getUuid());
                if (!known.contains(p.getUuid())) {
                    String msg = p.getName().getString() + " entered range (" + (int) d + "m)";
                    Notify.toast(msg); Notify.chat(msg);
                    if (sound.value) mc.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f));
                }
            }
        }
        known.clear(); known.addAll(now);
    }

    @Override public void onHud(DrawContext c, float t) {
        MinecraftClient mc = mc();
        Projector p = Projector.I;
        TextRenderer tr = mc.textRenderer;
        int n = 0, max = GlobalEsp.I.maxRender.i();
        for (AbstractClientPlayerEntity pl : mc.world.getPlayers()) {
            if (pl == mc.player || pl.isRemoved()) continue;
            double x = Fx.lx(pl, t), y = Fx.ly(pl, t), z = Fx.lz(pl, t);
            double hh = pl.getHeight(), hw = pl.getWidth() / 2.0;
            double dist = Math.sqrt(p.dist2(x, y + hh / 2, z));
            if (!st.allows(dist) || !st.losOk(pl)) continue;

            String name = pl.getName().getString();
            boolean friend = isFriend(name);
            float frac = Math.max(0f, Math.min(1f, pl.getHealth() / Math.max(1f, pl.getMaxHealth())));
            int override = friend ? friendColor.rgb() : switch (colorMode.get()) {
                case "Health" -> Fx.healthColor(frac) & 0xFFFFFF;
                case "Distance" -> Fx.mix(0xFFFF3B30, 0xFF34C759, (float) Math.min(1.0, dist / 64.0)) & 0xFFFFFF;
                default -> 0;
            };
            Projector.Rect r = st.draw(c, p, x - hw, y, z - hw, x + hw, y + hh, z + hw, dist, override);
            if (r == null) continue;
            float rx0 = r.x0, ry0 = r.y0, ry1 = r.y1, rcx = r.cx(), rcy = r.cy();

            if (healthBar.value) {
                int bx = Math.round(rx0) - 4, top = Math.round(ry0), bot = Math.round(ry1);
                c.fill(bx - 1, top - 1, bx + 2, bot + 1, 0xAA000000);
                c.fill(bx, bot - Math.round((bot - top) * frac), bx + 1, bot, Fx.healthColor(frac));
            }
            if (st.tracer.value) Tracers.queue(rcx, ry1, st.col(dist, override), 1);
            if (st.label.value) lab.draw(c, tr, rcx, ry0 - 2, dist, List.of(buildText(mc, pl, dist, friend, frac)));
            if (++n >= max) break;
        }
    }

    private Text buildText(MinecraftClient mc, AbstractClientPlayerEntity pl, double dist, boolean friend, float frac) {
        MutableText out = Text.empty();
        String f = format.value;
        Matcher m = TOKEN.matcher(f);
        int last = 0;
        while (m.find()) {
            if (m.start() > last) out.append(lab.seg(f.substring(last, m.start()), lab.textColor.rgb()));
            out.append(token(mc, m.group(1), pl, dist, friend, frac, m.group()));
            last = m.end();
        }
        if (last < f.length()) out.append(lab.seg(f.substring(last), lab.textColor.rgb()));
        return out;
    }

    private Text token(MinecraftClient mc, String k, AbstractClientPlayerEntity pl, double dist, boolean friend, float frac, String raw) {
        switch (k) {
            case "name": return lab.seg(pl.getName().getString(), friend ? friendColor.rgb() : nameColor.rgb());
            case "health": return lab.seg(String.format(Locale.ROOT, "%.1f", pl.getHealth() + pl.getAbsorptionAmount()), Fx.healthColor(frac));
            case "max": return lab.seg(String.format(Locale.ROOT, "%.0f", pl.getMaxHealth()), lab.textColor.rgb());
            case "pct": return lab.seg(Math.round(frac * 100) + "%", Fx.healthColor(frac));
            case "dist": return lab.seg(String.format(Locale.ROOT, "%.0fm", dist), distColor.rgb());
            case "armor": return lab.seg(Integer.toString(pl.getArmor()), lab.textColor.rgb());
            case "ping": {
                PlayerListEntry e = mc.getNetworkHandler() == null ? null : mc.getNetworkHandler().getPlayerListEntry(pl.getUuid());
                return lab.seg((e == null ? 0 : e.getLatency()) + "ms", lab.textColor.rgb());
            }
            default: return lab.seg(raw, lab.textColor.rgb());
        }
    }
}
