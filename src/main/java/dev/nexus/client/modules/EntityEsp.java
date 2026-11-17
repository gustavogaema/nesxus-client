package dev.nexus.client.modules;

import dev.nexus.client.module.HasEsp;
import dev.nexus.client.module.Mod;
import dev.nexus.client.render.EspStyle;
import dev.nexus.client.render.LabelStyle;
import dev.nexus.client.render.Projector;
import dev.nexus.client.setting.Setting;
import dev.nexus.client.util.Fx;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.MerchantEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.vehicle.VehicleEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Entity ESP: every category has its own colour, style, thickness, range, visibility, tracer and label switch. */
final class EntityEsp extends Mod implements HasEsp {
    enum Cat {
        ANIMALS("Animals", 0xFF55FF55, true), MONSTERS("Monsters", 0xFFFF5555, true), VILLAGERS("Villagers", 0xFFC8A27A, false),
        ITEMS("Dropped Items", 0xFFFFD24A, true), XP("Experience Orbs", 0xFF7CFF3A, false), PROJECTILES("Projectiles", 0xFFFF8A3D, false),
        VEHICLES("Vehicles", 0xFF4DA6FF, false), CRYSTALS("End Crystals", 0xFFFF4DFF, false), STANDS("Armor Stands", 0xFFC9C9C9, false),
        OTHER("Other Living", 0xFF9AA0FF, false);
        final String label; final int color; final boolean on;
        Cat(String l, int c, boolean on) { label = l; color = c; this.on = on; }
    }

    private final Map<Cat, EspStyle> styles = new EnumMap<>(Cat.class);
    private final LabelStyle lab;
    private final Setting.Bool showHealth, showDist;

    EntityEsp() {
        super("Entity ESP", "Animals, monsters, villagers, items, XP orbs and more - each category fully independent.", Category.ESP);
        needsProjection = true;
        Group o = group("Labels");
        showHealth = o.bool("Show Health", true);
        showDist = o.bool("Show Distance", true);
        lab = new LabelStyle(o);
        for (Cat c : Cat.values()) styles.put(c, new EspStyle(group(c.label), true, c.on, c.color, 64, "Box", false));
    }

    @Override public List<EspStyle> styles() { return new ArrayList<>(styles.values()); }

    static Cat classify(Entity e) {
        if (e instanceof PlayerEntity) return null;
        if (e instanceof ItemEntity) return Cat.ITEMS;
        if (e instanceof ExperienceOrbEntity) return Cat.XP;
        if (e instanceof EndCrystalEntity) return Cat.CRYSTALS;
        if (e instanceof ArmorStandEntity) return Cat.STANDS;
        if (e instanceof ProjectileEntity) return Cat.PROJECTILES;
        if (e instanceof VehicleEntity) return Cat.VEHICLES;
        if (e instanceof MerchantEntity) return Cat.VILLAGERS;
        if (e instanceof Monster) return Cat.MONSTERS;
        if (e instanceof AnimalEntity) return Cat.ANIMALS;
        if (e instanceof LivingEntity) return Cat.OTHER;
        return null;
    }

    @Override public void onHud(DrawContext c, float t) {
        MinecraftClient mc = mc();
        Projector p = Projector.I;
        TextRenderer tr = mc.textRenderer;
        int n = 0, max = GlobalEsp.I.maxRender.i();
        for (Entity e : mc.world.getEntities()) {
            if (e == mc.player || e.isRemoved()) continue;
            Cat cat = classify(e);
            if (cat == null) continue;
            EspStyle st = styles.get(cat);
            if (!st.enabled.value) continue;
            double x = Fx.lx(e, t), y = Fx.ly(e, t), z = Fx.lz(e, t);
            double hh = e.getHeight(), hw = e.getWidth() / 2.0;
            double dist = Math.sqrt(p.dist2(x, y + hh / 2, z));
            if (!st.allows(dist) || !st.losOk(e)) continue;
            Projector.Rect r = st.draw(c, p, x - hw, y, z - hw, x + hw, y + hh, z + hw, dist, 0);
            if (r == null) continue;
            float cx = r.cx(), top = r.y0, bottom = r.y1;
            if (st.tracer.value) Tracers.queue(cx, bottom, st.col(dist, 0), 1);
            if (st.label.value) lab.draw(c, tr, cx, top - 2, dist, List.of(label(e, cat, dist)));
            if (++n >= max) break;
        }
    }

    private Text label(Entity e, Cat cat, double dist) {
        MutableText out = Text.empty();
        int tc = lab.textColor.rgb();
        if (e instanceof ItemEntity ie) {
            out.append(lab.seg(ie.getStack().getName().getString() + " x" + ie.getStack().getCount(), tc));
        } else {
            out.append(lab.seg(e.getName().getString(), tc));
            if (showHealth.value && e instanceof LivingEntity le && !(e instanceof ArmorStandEntity))
                out.append(lab.seg(String.format(" %.0f", le.getHealth()), Fx.healthColor(le.getHealth() / Math.max(1f, le.getMaxHealth()))));
        }
        if (showDist.value) out.append(lab.seg(String.format(" %.0fm", dist), 0x9FD3FF));
        return out;
    }
}
