package dev.nexus.client.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.HashMap;
import java.util.Map;

/** Cached line-of-sight tests (refreshed every few ticks) so "visible only / hidden only" modes stay cheap. */
public final class Los {
    private static final Map<Integer, Boolean> CACHE = new HashMap<>();
    private Los() { }

    public static void tick(int tick) { if (tick % 5 == 0) CACHE.clear(); }

    public static boolean visible(Entity e) {
        Boolean b = CACHE.get(e.getId());
        if (b == null) {
            b = visible(new Vec3d(e.getX(), e.getY() + e.getHeight() * 0.5, e.getZ()));
            CACHE.put(e.getId(), b);
        }
        return b;
    }

    public static boolean visible(Vec3d target) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return true;
        HitResult r = mc.world.raycast(new RaycastContext(mc.player.getEyePos(), target,
                RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player));
        return r.getType() == HitResult.Type.MISS;
    }
}
