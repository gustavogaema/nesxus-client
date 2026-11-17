package dev.nexus.client.modules;

import dev.nexus.client.module.Mod;
import dev.nexus.client.setting.Setting;
import dev.nexus.client.util.GlowManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.MerchantEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.ItemEntity;

/** Fullbright (client-side night vision, no gamma hacks) and Entity Highlighting (vanilla glow outlines). */
final class VisualMods {
    private VisualMods() { }

    static final class Fullbright extends Mod {
        private boolean applied;
        Fullbright() { super("Fullbright", "Maximum brightness everywhere (client-side night vision, no icon).", Category.VISUALS); }
        @Override public void onTick(MinecraftClient mc) {
            var cur = mc.player.getStatusEffect(StatusEffects.NIGHT_VISION);
            if (cur == null || cur.getDuration() < 4000) {
                mc.player.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, 100000, 0, false, false, false));
                applied = true;
            }
        }
        @Override public void onDisable() {
            var mc = mc();
            if (applied && mc.player != null) mc.player.removeStatusEffect(StatusEffects.NIGHT_VISION);
            applied = false;
        }
    }

    static final class EntityHighlight extends Mod {
        private final Setting.Bool players, monsters, animals, villagers, items, target;
        private final Setting.Num range;
        EntityHighlight() {
            super("Entity Highlight", "Vanilla glow outline on chosen entity types, visible through walls.", Category.VISUALS);
            Group g = group("Targets");
            players = g.bool("Players", true); monsters = g.bool("Monsters", false); animals = g.bool("Animals", false);
            villagers = g.bool("Villagers", false); items = g.bool("Dropped Items", false); target = g.bool("Crosshair Target", false);
            range = g.num("Range", 64, 8, 256, 4).unit("m");
        }
        @Override public void onTick(MinecraftClient mc) {
            double r = Math.min(range.d(), GlobalEsp.I.maxRange.d());
            for (Entity e : mc.world.getEntities()) {
                if (e == mc.player || e.isRemoved() || mc.player.distanceTo(e) > r) continue;
                boolean on = (e instanceof PlayerEntity && players.value) || (e instanceof Monster && monsters.value)
                        || (e instanceof AnimalEntity && animals.value) || (e instanceof MerchantEntity && villagers.value)
                        || (e instanceof ItemEntity && items.value) || (target.value && e == mc.targetedEntity);
                if (on) GlowManager.mark(e);
            }
        }
    }
}
