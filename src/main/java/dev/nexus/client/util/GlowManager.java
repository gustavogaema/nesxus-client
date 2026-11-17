package dev.nexus.client.util;

import net.minecraft.entity.Entity;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * Client-side vanilla glow outlines. Modules call mark() every tick for entities they want outlined; entities that
 * were not marked this tick get un-glowed automatically, so any number of modules can share it without conflicts.
 */
public final class GlowManager {
    private static final Map<Integer, Entity> GLOWING = new HashMap<>();
    private static final Set<Integer> MARKED = new HashSet<>();
    private GlowManager() { }

    public static void begin() { MARKED.clear(); }

    public static void mark(Entity e) {
        MARKED.add(e.getId());
        GLOWING.put(e.getId(), e);
        e.setGlowing(true);
    }

    public static void end() {
        Iterator<Map.Entry<Integer, Entity>> it = GLOWING.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, Entity> en = it.next();
            if (!MARKED.contains(en.getKey())) {
                if (!en.getValue().isRemoved()) en.getValue().setGlowing(false);
                it.remove();
            }
        }
    }
}
