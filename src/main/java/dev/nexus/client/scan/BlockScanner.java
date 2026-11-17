package dev.nexus.client.scan;

import dev.nexus.client.modules.GlobalEsp;
import dev.nexus.client.render.EspStyle;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;

import java.util.*;

/**
 * Time-budgeted chunk scanner shared by X-Ray, Block ESP and Cave Finder. Each tick it scans the stalest chunks until
 * the per-tick budget (default 1 ms) is used up, so CPU usage stays flat no matter how many ore types are active.
 * Palette pre-checks (ChunkSection#hasAny) skip sections that contain none of the wanted blocks.
 */
public final class BlockScanner {
    public static final class Interest {
        public final Object owner; public final EspStyle style; public final int rgb; // rgb 0 = style colour
        public final int minY, maxY; public final boolean hideExposed; public final String label;
        public Interest(Object owner, EspStyle style, int rgb, int minY, int maxY, boolean hideExposed, String label) {
            this.owner = owner; this.style = style; this.rgb = rgb; this.minY = minY; this.maxY = maxY;
            this.hideExposed = hideExposed; this.label = label;
        }
    }

    public static final class CaveCfg {
        public Interest interest; public int minY, maxY, depth;
    }

    public static final class Hit {
        public final int x, y, z; public final Interest it;
        Hit(int x, int y, int z, Interest it) { this.x = x; this.y = y; this.z = z; this.it = it; }
    }

    /** Collector handed to every ScanSource each rebuild. */
    public static final class Collector {
        private final Map<Block, List<Interest>> map = new HashMap<>();
        int radius = 0; CaveCfg cave;
        public void add(Block b, Interest i) { map.computeIfAbsent(b, k -> new ArrayList<>(1)).add(i); }
        public void radius(int r) { radius = Math.max(radius, r); }
        public void cave(CaveCfg c) { cave = c; }
    }

    public interface Source { void collect(Collector c); }

    private static final class Chunk {
        final int cx, cz; long last = Long.MIN_VALUE; List<Hit> hits = new ArrayList<>();
        Chunk(int x, int z) { cx = x; cz = z; }
    }

    private static Map<Block, List<Interest>> interests = new HashMap<>();
    private static CaveCfg cave;
    private static int radius;
    private static int signature;
    private static final Map<Long, Chunk> CHUNKS = new HashMap<>();
    private static List<Chunk> ordered = new ArrayList<>();
    private static final List<Source> SOURCES = new ArrayList<>();

    private BlockScanner() { }

    public static void register(Source s) { SOURCES.add(s); }

    public static boolean active() { return !interests.isEmpty() || cave != null; }

    public static void tick(MinecraftClient mc, int tick) {
        if (tick % 10 == 0) rebuild(mc);
        if (!active()) return;
        long now = tick;
        int pcx = mc.player.getBlockPos().getX() >> 4, pcz = mc.player.getBlockPos().getZ() >> 4;
        if (tick % 20 == 0 || ordered.isEmpty()) refreshOrder(pcx, pcz);

        long deadline = System.nanoTime() + (long) (GlobalEsp.I.budget.d() * 1_000_000.0);
        long rescan = Math.max(20, GlobalEsp.I.blockRescan.i() * 20L);
        ClientWorld w = mc.world;
        BlockPos.Mutable mp = new BlockPos.Mutable();
        for (Chunk c : ordered) {
            if (now - c.last < rescan) continue;
            WorldChunk wc = w.getChunkManager().getWorldChunk(c.cx, c.cz);
            if (wc == null) continue;
            c.hits = scan(w, wc, mp);
            c.last = now;
            if (System.nanoTime() > deadline) break;
        }
    }

    private static void rebuild(MinecraftClient mc) {
        Collector col = new Collector();
        for (Source s : SOURCES) s.collect(col);
        interests = col.map; cave = col.cave; radius = Math.min(col.radius, mc.options.getViewDistance().getValue());
        int sig = 17;
        for (Map.Entry<Block, List<Interest>> e : interests.entrySet())
            for (Interest i : e.getValue()) sig = sig * 31 + e.getKey().hashCode() + i.minY * 7 + i.maxY * 13 + (i.hideExposed ? 1 : 0);
        if (cave != null) sig = sig * 31 + cave.minY * 5 + cave.maxY * 11 + cave.depth;
        if (sig != signature) { signature = sig; for (Chunk c : CHUNKS.values()) c.last = Long.MIN_VALUE; }
        if (!active()) { CHUNKS.clear(); ordered = new ArrayList<>(); }
    }

    private static void refreshOrder(int pcx, int pcz) {
        CHUNKS.keySet().removeIf(k -> {
            int x = (int) (k >> 32), z = (int) (long) k;
            return Math.abs(x - pcx) > radius + 1 || Math.abs(z - pcz) > radius + 1;
        });
        List<Chunk> list = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++)
            for (int dz = -radius; dz <= radius; dz++) {
                int x = pcx + dx, z = pcz + dz;
                long key = ((long) x << 32) | (z & 0xFFFFFFFFL);
                list.add(CHUNKS.computeIfAbsent(key, k -> new Chunk(x, z)));
            }
        list.sort(Comparator.comparingInt(c -> (c.cx - pcx) * (c.cx - pcx) + (c.cz - pcz) * (c.cz - pcz)));
        ordered = list;
    }

    private static List<Hit> scan(ClientWorld w, WorldChunk ch, BlockPos.Mutable mp) {
        List<Hit> hits = new ArrayList<>();
        ChunkSection[] secs = ch.getSectionArray();
        int bottom = ch.getBottomY(), bx = ch.getPos().x << 4, bz = ch.getPos().z << 4, caveCount = 0;
        for (int si = 0; si < secs.length; si++) {
            ChunkSection s = secs[si];
            if (s == null || s.isEmpty()) continue;
            int baseY = bottom + si * 16;
            if (!interests.isEmpty() && s.hasAny(st -> interests.containsKey(st.getBlock()))) {
                for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
                    BlockState st = s.getBlockState(x, y, z);
                    List<Interest> li = interests.get(st.getBlock());
                    if (li == null) continue;
                    int wy = baseY + y;
                    for (Interest i : li) {
                        if (wy < i.minY || wy > i.maxY) continue;
                        if (i.hideExposed && exposed(w, bx + x, wy, bz + z, mp)) continue;
                        hits.add(new Hit(bx + x, wy, bz + z, i));
                    }
                }
            }
            if (cave != null && baseY + 15 >= cave.minY && baseY <= cave.maxY && caveCount < 24) {
                for (int x = 1; x < 16 && caveCount < 24; x += 3) for (int z = 1; z < 16 && caveCount < 24; z += 3) {
                    int surface = ch.sampleHeightmap(Heightmap.Type.WORLD_SURFACE, x, z);
                    for (int y = 1; y < 15; y++) {
                        int wy = baseY + y;
                        if (wy < cave.minY || wy > cave.maxY || wy > surface - cave.depth) continue;
                        if (s.getBlockState(x, y, z).isAir() && s.getBlockState(x, y + 1, z).isAir()
                                && s.getBlockState(x, y - 1, z).isOpaque()) {
                            hits.add(new Hit(bx + x, wy, bz + z, cave.interest));
                            caveCount++;
                            break;
                        }
                    }
                }
            }
        }
        return hits;
    }

    private static boolean exposed(ClientWorld w, int x, int y, int z, BlockPos.Mutable mp) {
        return !w.getBlockState(mp.set(x + 1, y, z)).isOpaque() || !w.getBlockState(mp.set(x - 1, y, z)).isOpaque()
                || !w.getBlockState(mp.set(x, y + 1, z)).isOpaque() || !w.getBlockState(mp.set(x, y - 1, z)).isOpaque()
                || !w.getBlockState(mp.set(x, y, z + 1)).isOpaque() || !w.getBlockState(mp.set(x, y, z - 1)).isOpaque();
    }

    /** Visits hits nearest-chunk-first. */
    public static void forEach(java.util.function.Consumer<Hit> f) {
        for (Chunk c : ordered) for (Hit h : c.hits) f.accept(h);
    }
}
