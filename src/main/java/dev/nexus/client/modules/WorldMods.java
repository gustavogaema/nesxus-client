package dev.nexus.client.modules;

import dev.nexus.client.module.HasEsp;
import dev.nexus.client.module.HudMod;
import dev.nexus.client.module.Mod;
import dev.nexus.client.render.EspStyle;
import dev.nexus.client.render.Projector;
import dev.nexus.client.render.Render2D;
import dev.nexus.client.scan.BlockScanner;
import dev.nexus.client.scan.BlockScanner.Collector;
import dev.nexus.client.scan.BlockScanner.Interest;
import dev.nexus.client.scan.StorageCache;
import dev.nexus.client.scan.StorageType;
import dev.nexus.client.scan.StorageUser;
import dev.nexus.client.setting.Setting;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.*;

/** World modules: X-Ray (ore ESP), Block ESP, Cave Finder, Storage Finder. */
final class WorldMods {
    private WorldMods() { }

    /** Shared renderer for scanner hits owned by one module. */
    static void drawHits(DrawContext c, Object owner, boolean labels, double inset) {
        Projector p = Projector.I;
        int[] n = {0};
        int max = GlobalEsp.I.maxRender.i();
        BlockScanner.forEach(h -> {
            if (h.it.owner != owner || n[0] >= max) return;
            double dist = Math.sqrt(p.dist2(h.x + 0.5, h.y + 0.5, h.z + 0.5));
            EspStyle s = h.it.style;
            if (!s.allows(dist)) return;
            Projector.Rect r = s.draw(c, p, h.x + inset, h.y, h.z + inset, h.x + 1 - inset, h.y + 1 - inset, h.z + 1 - inset, dist, h.it.rgb);
            if (r == null) return;
            n[0]++;
            if (s.tracer.value) Tracers.queue(r.cx(), r.cy(), s.col(dist, h.it.rgb), 1);
            if (labels) Render2D.smallLabel(c, MinecraftClient.getInstance().textRenderer, h.it.label + " " + (int) dist + "m", r.cx(), r.y0 - 1, h.it.rgb != 0 ? h.it.rgb : s.color.rgb());
        });
    }

    // ------------------------------------------------------------------------------------------------ X-Ray
    private record Ore(String label, int color, boolean on, Block... blocks) { }
    private static final Ore[] ORES = {
            new Ore("Diamond", 0xFF4DEBFF, true, Blocks.DIAMOND_ORE, Blocks.DEEPSLATE_DIAMOND_ORE),
            new Ore("Ancient Debris", 0xFFB5651D, true, Blocks.ANCIENT_DEBRIS),
            new Ore("Emerald", 0xFF2ED573, true, Blocks.EMERALD_ORE, Blocks.DEEPSLATE_EMERALD_ORE),
            new Ore("Gold", 0xFFFFD43B, true, Blocks.GOLD_ORE, Blocks.DEEPSLATE_GOLD_ORE, Blocks.NETHER_GOLD_ORE, Blocks.GILDED_BLACKSTONE),
            new Ore("Iron", 0xFFD8A47F, false, Blocks.IRON_ORE, Blocks.DEEPSLATE_IRON_ORE),
            new Ore("Coal", 0xFF777777, false, Blocks.COAL_ORE, Blocks.DEEPSLATE_COAL_ORE),
            new Ore("Redstone", 0xFFFF3B30, false, Blocks.REDSTONE_ORE, Blocks.DEEPSLATE_REDSTONE_ORE),
            new Ore("Lapis", 0xFF3B6BFF, false, Blocks.LAPIS_ORE, Blocks.DEEPSLATE_LAPIS_ORE),
            new Ore("Copper", 0xFFE07B39, false, Blocks.COPPER_ORE, Blocks.DEEPSLATE_COPPER_ORE),
            new Ore("Nether Quartz", 0xFFF2EDE4, false, Blocks.NETHER_QUARTZ_ORE)};

    static final class XRay extends Mod implements HasEsp, BlockScanner.Source {
        private final List<EspStyle> styles = new ArrayList<>();
        private final Setting.Num minY, maxY, radius;
        private final Setting.Bool hideExposed, labels;

        XRay() {
            super("X-Ray", "Highlights ores through walls (ESP-based: no chunk rebuilds, Sodium safe).", Category.WORLD);
            needsProjection = true;
            Group g = group("Scan");
            minY = g.num("Min Y", -64, -64, 320, 1); maxY = g.num("Max Y", 128, -64, 320, 1);
            radius = g.num("Scan Radius", 5, 1, 12, 1).unit(" chunks");
            hideExposed = g.bool("Hide Exposed Ores", false); hideExposed.desc("Only ores fully buried in rock.");
            labels = g.bool("Labels", false);
            for (Ore o : ORES) styles.add(new EspStyle(group(o.label()), true, o.on(), o.color(), 96, "Box + Fill", false));
            BlockScanner.register(this);
        }
        @Override public List<EspStyle> styles() { return styles; }
        @Override public void collect(Collector c) {
            if (!enabled) return;
            c.radius(radius.i());
            for (int i = 0; i < ORES.length; i++) {
                EspStyle s = styles.get(i);
                if (!s.enabled.value) continue;
                for (Block b : ORES[i].blocks()) c.add(b, new Interest(this, s, 0, minY.i(), maxY.i(), hideExposed.value, ORES[i].label()));
            }
        }
        @Override public void onHud(DrawContext c, float t) { drawHits(c, this, labels.value, 0.0); }
    }

    // --------------------------------------------------------------------------------------------- Block ESP
    static final class BlockEsp extends Mod implements HasEsp, BlockScanner.Source {
        private final EspStyle style;
        private final Setting.Text blocks;
        private final Setting.Num radius;
        private final Setting.Bool labels;
        private String src = null;
        private final Map<Block, Integer> parsed = new LinkedHashMap<>();

        BlockEsp() {
            super("Block ESP", "Highlight any blocks by id. Optional per-block colour: minecraft:obsidian=FF8800", Category.WORLD);
            needsProjection = true;
            Group g = group("Blocks");
            blocks = g.text("Block List", "minecraft:spawner,minecraft:diamond_block=00FFFF");
            radius = g.num("Scan Radius", 4, 1, 12, 1).unit(" chunks");
            labels = g.bool("Labels", false);
            style = new EspStyle(group("Appearance"), false, 0xFFFFA94D, 96, "Box + Fill", false);
            BlockScanner.register(this);
        }
        @Override public List<EspStyle> styles() { return List.of(style); }

        private void parse() {
            if (blocks.value.equals(src)) return;
            src = blocks.value; parsed.clear();
            for (String part : src.split(",")) {
                String s = part.trim(); int rgb = 0;
                int eq = s.indexOf('=');
                if (eq > 0) { try { rgb = Integer.parseInt(s.substring(eq + 1).replace("#", ""), 16); } catch (Exception ignored) { } s = s.substring(0, eq); }
                Identifier id = Identifier.tryParse(s.contains(":") ? s : "minecraft:" + s);
                if (id != null && Registries.BLOCK.containsId(id)) parsed.put(Registries.BLOCK.get(id), rgb);
            }
        }
        @Override public void collect(Collector c) {
            if (!enabled) return;
            parse(); c.radius(radius.i());
            for (Map.Entry<Block, Integer> e : parsed.entrySet())
                c.add(e.getKey(), new Interest(this, style, e.getValue(), -64, 320, false, Registries.BLOCK.getId(e.getKey()).getPath()));
        }
        @Override public void onHud(DrawContext c, float t) { drawHits(c, this, labels.value, 0.0); }
    }

    // ------------------------------------------------------------------------------------------- Cave Finder
    static final class CaveFinder extends Mod implements HasEsp, BlockScanner.Source {
        private final EspStyle style;
        private final Setting.Num minY, maxY, depth, radius;
        CaveFinder() {
            super("Cave Finder", "Marks walkable cave floors that lie well below the surface.", Category.WORLD);
            needsProjection = true;
            Group g = group("Scan");
            minY = g.num("Min Y", -64, -64, 320, 1); maxY = g.num("Max Y", 40, -64, 320, 1);
            depth = g.num("Min Depth Below Surface", 8, 3, 64, 1);
            radius = g.num("Scan Radius", 4, 1, 10, 1).unit(" chunks");
            style = new EspStyle(group("Appearance"), false, 0xFF7CFF9E, 64, "Filled", false);
            BlockScanner.register(this);
        }
        @Override public List<EspStyle> styles() { return List.of(style); }
        @Override public void collect(Collector c) {
            if (!enabled) return;
            c.radius(radius.i());
            BlockScanner.CaveCfg cfg = new BlockScanner.CaveCfg();
            cfg.minY = minY.i(); cfg.maxY = maxY.i(); cfg.depth = depth.i();
            cfg.interest = new Interest(this, style, 0, cfg.minY, cfg.maxY, false, "Cave");
            c.cave(cfg);
        }
        @Override public void onHud(DrawContext c, float t) { drawHits(c, this, false, 0.3); }
    }

    // ----------------------------------------------------------------------------------------- Storage Finder
    static final class StorageFinder extends HudMod implements StorageUser {
        private final Setting.Bool chests, barrels, enders, shulkers, other, tracer;
        private final Setting.Num range, entries;
        private StorageCache.Hit nearest;

        StorageFinder() {
            super("Storage Finder", "Lists nearby storage with distance and coordinates; optional tracer to the nearest.", Category.WORLD, 6, 120);
            Group g = group("Finder");
            chests = g.bool("Chests (+Trapped)", true); barrels = g.bool("Barrels", true); enders = g.bool("Ender Chests", true);
            shulkers = g.bool("Shulker Boxes", true); other = g.bool("Other Storage", false);
            range = g.num("Range", 64, 8, 256, 4).unit("m");
            entries = g.num("Max Entries", 6, 1, 15, 1);
            tracer = g.bool("Tracer To Nearest", true);
        }
        @Override public EnumSet<StorageType> storageTypes() {
            EnumSet<StorageType> s = EnumSet.noneOf(StorageType.class);
            if (chests.value) { s.add(StorageType.CHEST); s.add(StorageType.TRAPPED); }
            if (barrels.value) s.add(StorageType.BARREL);
            if (enders.value) s.add(StorageType.ENDER);
            if (shulkers.value) s.add(StorageType.SHULKER);
            if (other.value) s.addAll(EnumSet.of(StorageType.FURNACE, StorageType.HOPPER, StorageType.DISPENSER, StorageType.BREWING,
                    StorageType.POT, StorageType.BOOKSHELF, StorageType.SPAWNER, StorageType.BEACON));
            return s;
        }
        @Override protected List<HudLine> lines(MinecraftClient mc) {
            EnumSet<StorageType> want = storageTypes();
            Projector p = Projector.I;
            double px = mc.player.getX(), py = mc.player.getY(), pz = mc.player.getZ();
            List<Object[]> found = new ArrayList<>();
            for (StorageCache.Hit h : StorageCache.hits) {
                if (!want.contains(h.type)) continue;
                double d = Math.sqrt((h.cx() - px) * (h.cx() - px) + (h.cy() - py) * (h.cy() - py) + (h.cz() - pz) * (h.cz() - pz));
                if (d <= range.d()) found.add(new Object[]{d, h});
            }
            found.sort(Comparator.comparingDouble(o -> (Double) o[0]));
            nearest = found.isEmpty() ? null : (StorageCache.Hit) found.get(0)[1];
            List<HudLine> out = new ArrayList<>();
            out.add(new HudLine("Storage", found.size() + " nearby"));
            for (int i = 0; i < Math.min(entries.i(), found.size()); i++) {
                StorageCache.Hit h = (StorageCache.Hit) found.get(i)[1];
                out.add(new HudLine(h.type.label, String.format(Locale.ROOT, "%.0fm  %d %d %d", (Double) found.get(i)[0], h.pos.getX(), h.pos.getY(), h.pos.getZ())));
            }
            return out;
        }
        @Override protected void render(DrawContext c, MinecraftClient mc, float t) {
            super.render(c, mc, t);
            Projector p = Projector.I;
            if (tracer.value && nearest != null && p.valid) {
                float[] pt = p.point(nearest.cx(), nearest.cy(), nearest.cz());
                if (pt != null) Tracers.queue(pt[0], pt[1], 0xFFFFFFFF, 1);
            }
        }
    }
}
