package dev.nexus.client.scan;

import dev.nexus.client.modules.GlobalEsp;
import dev.nexus.client.module.Mod;
import dev.nexus.client.module.Mods;
import dev.nexus.client.util.Los;
import net.minecraft.block.BlockState;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.enums.ChestType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/** One shared, throttled scan of loaded block entities; every storage ESP / finder reads from it. */
public final class StorageCache {
    public static final class Hit {
        public final StorageType type;
        public final BlockPos pos;
        public final double x0, y0, z0, x1, y1, z1;
        private boolean vis = true;
        private int visTick = -100;
        Hit(StorageType t, BlockPos p, Box b) { type = t; pos = p; x0 = b.minX; y0 = b.minY; z0 = b.minZ; x1 = b.maxX; y1 = b.maxY; z1 = b.maxZ; }
        public double cx() { return (x0 + x1) / 2; }
        public double cy() { return (y0 + y1) / 2; }
        public double cz() { return (z0 + z1) / 2; }
        public boolean visible(int tick) {
            if (tick - visTick > 10) { vis = Los.visible(new Vec3d(cx(), cy(), cz())); visTick = tick; }
            return vis;
        }
    }

    public static List<Hit> hits = new ArrayList<>();
    private StorageCache() { }

    public static void tick(MinecraftClient mc, int tick) {
        EnumSet<StorageType> want = EnumSet.noneOf(StorageType.class);
        for (Mod m : Mods.all()) if (m.enabled && m instanceof StorageUser u) want.addAll(u.storageTypes());
        if (want.isEmpty()) { if (!hits.isEmpty()) hits = new ArrayList<>(); return; }
        if (tick % Math.max(2, GlobalEsp.I.scanInterval.i()) != 0) return;

        ClientWorld w = mc.world;
        int r = (int) Math.min(mc.options.getViewDistance().getValue(), Math.ceil(GlobalEsp.I.maxRange.d() / 16.0)) + 1;
        int pcx = mc.player.getBlockPos().getX() >> 4, pcz = mc.player.getBlockPos().getZ() >> 4;
        List<Hit> out = new ArrayList<>();
        for (int cx = pcx - r; cx <= pcx + r; cx++)
            for (int cz = pcz - r; cz <= pcz + r; cz++) {
                WorldChunk ch = w.getChunkManager().getWorldChunk(cx, cz);
                if (ch == null) continue;
                for (BlockEntity be : ch.getBlockEntities().values()) {
                    StorageType t = StorageType.of(be);
                    if (t == null || !want.contains(t)) continue;
                    BlockPos pos = be.getPos();
                    BlockState st = be.getCachedState();
                    VoxelShape shape = st.getOutlineShape(w, pos);
                    Box box = shape.isEmpty() ? new Box(pos) : shape.getBoundingBox().offset(pos);
                    // Merge double chests into one box (emit only for the LEFT half).
                    if (be instanceof net.minecraft.block.entity.ChestBlockEntity && st.contains(ChestBlock.CHEST_TYPE)) {
                        ChestType ct = st.get(ChestBlock.CHEST_TYPE);
                        if (ct == ChestType.RIGHT) continue;
                        if (ct == ChestType.LEFT) {
                            Direction d = ChestBlock.getFacing(st);
                            box = box.union(box.offset(d.getOffsetX(), 0, d.getOffsetZ()));
                        }
                    }
                    out.add(new Hit(t, pos, box));
                }
            }
        hits = out;
    }
}
