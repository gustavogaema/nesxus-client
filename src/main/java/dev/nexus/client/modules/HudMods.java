package dev.nexus.client.modules;

import dev.nexus.client.config.ConfigManager;
import dev.nexus.client.module.HudMod;
import dev.nexus.client.module.HudMod.HudLine;
import dev.nexus.client.setting.Setting;
import dev.nexus.client.util.Fx;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;

/** Information HUD widgets. Every one is draggable (open the GUI and drag) and has its own ON/OFF switch. */
final class HudMods {
    private HudMods() { }

    static final class TextHud extends HudMod {
        private final Function<MinecraftClient, List<HudLine>> fn;
        TextHud(String n, String d, int x, int y, Function<MinecraftClient, List<HudLine>> fn) { super(n, d, Category.HUD, x, y); this.fn = fn; }
        @Override protected List<HudLine> lines(MinecraftClient mc) { return fn.apply(mc); }
    }

    private static List<HudLine> one(String l, String v) { return List.of(new HudLine(l, v)); }

    static TextHud watermark() { return new TextHud("Watermark", "Client name and profile.", 6, 6, mc -> one("NEXUS", "CLIENT  \u00b7  " + ConfigManager.current)); }
    static TextHud fps() { return new TextHud("FPS", "Frames per second.", 6, 24, mc -> one("FPS", Integer.toString(mc.getCurrentFps()))); }

    static TextHud coords() {
        return new TextHud("Coordinates", "XYZ plus Nether/Overworld conversion.", 6, 42, mc -> {
            var p = mc.player;
            List<HudLine> l = new ArrayList<>();
            l.add(new HudLine("XYZ", String.format(Locale.ROOT, "%.1f %.1f %.1f", p.getX(), p.getY(), p.getZ())));
            if (mc.world.getRegistryKey() == World.NETHER)
                l.add(new HudLine("Overworld", String.format(Locale.ROOT, "%.0f %.0f", p.getX() * 8, p.getZ() * 8)));
            else if (mc.world.getRegistryKey() == World.OVERWORLD)
                l.add(new HudLine("Nether", String.format(Locale.ROOT, "%.0f %.0f", p.getX() / 8, p.getZ() / 8)));
            return l;
        });
    }

    static TextHud biome() {
        return new TextHud("Biome", "Current biome.", 6, 72, mc -> one("Biome",
                Fx.title(mc.world.getBiome(mc.player.getBlockPos()).getKey().map(k -> k.getValue().getPath()).orElse("unknown"))));
    }

    static TextHud direction() {
        return new TextHud("Direction", "Facing direction and yaw.", 6, 90, mc -> {
            Direction d = mc.player.getHorizontalFacing();
            String axis = switch (d) { case NORTH -> "-Z"; case SOUTH -> "+Z"; case EAST -> "+X"; default -> "-X"; };
            return one("Facing", Fx.title(d.asString()) + " (" + axis + ")  " + String.format(Locale.ROOT, "%.0f", MathHelper.wrapDegrees(mc.player.getYaw())));
        });
    }

    static TextHud ping() {
        return new TextHud("Ping", "Latency to the server.", 6, 108, mc -> {
            PlayerListEntry e = mc.getNetworkHandler() == null ? null : mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
            return one("Ping", e == null ? "N/A" : e.getLatency() + " ms");
        });
    }

    static TextHud speed() {
        double[] last = new double[3];
        double[] spd = new double[1];
        int[] tick = {-1};
        return new TextHud("Speed", "Horizontal speed in blocks per second.", 6, 126, mc -> {
            if (tick[0] != mc.player.age) {
                tick[0] = mc.player.age;
                spd[0] = Math.hypot(mc.player.getX() - last[0], mc.player.getZ() - last[2]) * 20.0;
                last[0] = mc.player.getX(); last[2] = mc.player.getZ();
            }
            return one("Speed", String.format(Locale.ROOT, "%.1f b/s", spd[0]));
        });
    }

    static TextHud clock() {
        DateTimeFormatter f = DateTimeFormatter.ofPattern("HH:mm:ss");
        return new TextHud("Clock", "Real time and in-game time.", 6, 144, mc -> {
            long dt = mc.world.getTimeOfDay() % 24000L, h = (dt / 1000 + 6) % 24, m = (dt % 1000) * 60 / 1000;
            return List.of(new HudLine("Time", LocalTime.now().format(f)), new HudLine("Game", String.format("%02d:%02d", h, m)));
        });
    }

    static final class ArmorHud extends HudMod {
        private static final EquipmentSlot[] S = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND};
        private final Setting.Mode show, layout;
        private final Setting.Bool hand, empty;
        ArmorHud() {
            super("Armor Status", "Armour and held-item icons with durability.", Category.HUD, 6, 170);
            Group g = group("Armor");
            show = g.mode("Durability Text", "Percent", "Remaining", "Both");
            layout = g.mode("Layout", "Vertical", "Horizontal");
            hand = g.bool("Include Hands", true); empty = g.bool("Show Empty Slots", false);
        }
        @Override protected void render(DrawContext c, MinecraftClient mc, float t) {
            List<ItemStack> st = new ArrayList<>();
            for (int i = 0; i < S.length; i++) {
                if (i >= 4 && !hand.value) continue;
                ItemStack is = mc.player.getEquippedStack(S[i]);
                if (!is.isEmpty() || (empty.value && i < 4)) st.add(is);
            }
            if (st.isEmpty()) return;
            var tr = mc.textRenderer;
            boolean vert = layout.is("Vertical");
            String[] txt = new String[st.size()];
            int tw = 0;
            for (int i = 0; i < txt.length; i++) {
                ItemStack is = st.get(i);
                if (is.isEmpty() || !is.isDamageable()) txt[i] = is.getCount() > 1 ? "x" + is.getCount() : "";
                else {
                    int rem = is.getMaxDamage() - is.getDamage(), pct = Math.round(100f * rem / is.getMaxDamage());
                    txt[i] = show.is("Percent") ? pct + "%" : show.is("Remaining") ? Integer.toString(rem) : pct + "% " + rem;
                }
                tw = Math.max(tw, tr.getWidth(txt[i]));
            }
            if (vert) begin(c, 24 + tw, st.size() * 18 + 3); else begin(c, st.size() * Math.max(20, tw + 4) + 3, 28);
            int cell = Math.max(20, tw + 4);
            for (int i = 0; i < st.size(); i++) {
                ItemStack is = st.get(i);
                int x = vert ? 4 : 3 + i * cell, y = vert ? 2 + i * 18 : 2;
                c.drawItem(is, x, y);
                float f = is.isDamageable() ? 1f - (float) is.getDamage() / is.getMaxDamage() : 1f;
                if (vert) c.drawText(tr, txt[i], x + 20, y + 4, is.isDamageable() ? Fx.healthColor(f) : textColor.argb(), true);
                else c.drawText(tr, txt[i], x, y + 18, is.isDamageable() ? Fx.healthColor(f) : textColor.argb(), true);
            }
            end(c);
        }
    }

    static final class NearbyPlayers extends HudMod {
        private final Setting.Num range, count;
        NearbyPlayers() {
            super("Nearby Players", "Radar list of players around you.", Category.HUD, 6, 210);
            Group g = group("Radar");
            range = g.num("Range", 128, 16, 512, 8).unit("m"); count = g.num("Max Entries", 6, 1, 15, 1);
        }
        @Override protected List<HudLine> lines(MinecraftClient mc) {
            List<AbstractClientPlayerEntity> l = new ArrayList<>();
            for (AbstractClientPlayerEntity p : mc.world.getPlayers()) if (p != mc.player && mc.player.distanceTo(p) <= range.d()) l.add(p);
            l.sort(Comparator.comparingDouble(p -> mc.player.distanceTo(p)));
            List<HudLine> out = new ArrayList<>();
            out.add(new HudLine("Players", Integer.toString(l.size())));
            for (int i = 0; i < Math.min(count.i(), l.size()); i++) {
                var p = l.get(i);
                out.add(new HudLine(p.getName().getString(), String.format(Locale.ROOT, "%.0fm  %.0fHP", mc.player.distanceTo(p), p.getHealth())));
            }
            return out;
        }
    }
}
