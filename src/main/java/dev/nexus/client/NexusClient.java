package dev.nexus.client;

import dev.nexus.client.config.ConfigManager;
import dev.nexus.client.module.Mod;
import dev.nexus.client.module.Mods;
import dev.nexus.client.modules.GlobalEsp;
import dev.nexus.client.modules.Modules;
import dev.nexus.client.render.Projector;
import dev.nexus.client.scan.BlockScanner;
import dev.nexus.client.scan.StorageCache;
import dev.nexus.client.util.GlowManager;
import dev.nexus.client.util.KeyPoll;
import dev.nexus.client.util.Los;
import dev.nexus.client.util.Notify;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class NexusClient implements ClientModInitializer {
    public static final String VERSION = "1.0.0";
    public static final Logger LOG = LoggerFactory.getLogger("NEXUS");
    private static int tick;

    public static int ticks() { return tick; }

    @Override public void onInitializeClient() {
        Modules.registerAll();
        ConfigManager.init();
        ClientTickEvents.END_CLIENT_TICK.register(NexusClient::onTick);
        HudRenderCallback.EVENT.register((ctx, counter) -> onHud(ctx, counter.getTickProgress(true)));
        ClientLifecycleEvents.CLIENT_STOPPING.register(c -> ConfigManager.saveCurrent());
        LOG.info("NEXUS CLIENT {} loaded with {} modules", VERSION, Mods.all().size());
    }

    private static void onTick(MinecraftClient mc) {
        tick++;
        KeyPoll.poll(mc);
        if (mc.player == null || mc.world == null) return;
        Los.tick(tick);
        StorageCache.tick(mc, tick);
        BlockScanner.tick(mc, tick);
        GlowManager.begin();
        for (Mod m : Mods.all()) {
            if (!m.enabled) continue;
            try { m.onTick(mc); } catch (Throwable t) { fail(m, t); }
        }
        GlowManager.end();
    }

    private static void onHud(DrawContext ctx, float t) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null || mc.options.hudHidden) return;
        Projector.I.update(mc, ctx.getScaledWindowWidth(), ctx.getScaledWindowHeight(), t);
        boolean master = GlobalEsp.I == null || GlobalEsp.I.enabled;
        for (Mod m : Mods.all()) {
            if (!m.enabled) continue;
            if (m.needsProjection && (!Projector.I.valid || !master)) continue;
            try { m.onHud(ctx, t); } catch (Throwable e) { fail(m, e); }
        }
        Notify.render(ctx);
    }

    /** A crashing module is switched off instead of taking the game down. */
    private static void fail(Mod m, Throwable t) {
        LOG.error("Module {} crashed and was disabled", m.name, t);
        m.enabled = false;
        Notify.toast(m.name + " crashed - disabled (see log)");
    }
}
