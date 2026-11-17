package dev.nexus.client.modules;

import dev.nexus.client.NexusClient;
import dev.nexus.client.module.HasEsp;
import dev.nexus.client.module.Mod;
import dev.nexus.client.render.EspStyle;
import dev.nexus.client.render.Projector;
import dev.nexus.client.render.Render2D;
import dev.nexus.client.scan.StorageCache;
import dev.nexus.client.scan.StorageType;
import dev.nexus.client.scan.StorageUser;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

/** One module per storage family (Chest, Trapped Chest, Barrel, Ender Chest, Shulker, Other). All read the shared cache. */
final class StorageEspModule extends Mod implements HasEsp, StorageUser {
    private final EnumSet<StorageType> types;
    private final Map<StorageType, EspStyle> styles = new EnumMap<>(StorageType.class);

    StorageEspModule(String name, String desc, StorageType... ts) {
        super(name, desc, Category.ESP);
        needsProjection = true;
        types = EnumSet.of(ts[0], ts);
        boolean multi = ts.length > 1;
        for (StorageType t : ts)
            styles.put(t, new EspStyle(group(multi ? t.label : "Appearance"), multi, 0xFF000000 | t.color, 96, "Box + Fill", false));
    }

    @Override public EnumSet<StorageType> storageTypes() { return types; }
    @Override public List<EspStyle> styles() { return new ArrayList<>(styles.values()); }

    @Override public void onHud(DrawContext c, float t) {
        MinecraftClient mc = mc();
        Projector p = Projector.I;
        int tick = NexusClient.ticks(), n = 0, max = GlobalEsp.I.maxRender.i();
        for (StorageCache.Hit h : StorageCache.hits) {
            EspStyle s = styles.get(h.type);
            if (s == null || !s.enabled.value) continue;
            double dist = Math.sqrt(p.dist2(h.cx(), h.cy(), h.cz()));
            if (!s.allows(dist)) continue;
            if (s.visibility.index != 0 && !s.losOk(h.visible(tick))) continue;
            Projector.Rect r = s.draw(c, p, h.x0, h.y0, h.z0, h.x1, h.y1, h.z1, dist, 0);
            if (r == null) continue;
            if (s.tracer.value) Tracers.queue(r.cx(), r.cy(), s.col(dist, 0), 1);
            if (s.label.value) Render2D.smallLabel(c, mc.textRenderer, h.type.label + " " + (int) dist + "m", r.cx(), r.y0 - 1, s.color.rgb());
            if (++n >= max) break;
        }
    }
}
