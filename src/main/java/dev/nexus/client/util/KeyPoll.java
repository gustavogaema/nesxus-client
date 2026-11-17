package dev.nexus.client.util;

import dev.nexus.client.module.Mod;
import dev.nexus.client.module.Mods;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;

/**
 * Polls module keybinds straight from GLFW. This avoids vanilla KeyBinding registration (whose API changed several
 * times in 1.21.x) and lets every module have its own bind that is editable inside the Nexus GUI.
 */
public final class KeyPoll {
    private static final boolean[] PREV = new boolean[GLFW.GLFW_KEY_LAST + 1];
    private KeyPoll() { }

    public static void poll(MinecraftClient mc) {
        long win = mc.getWindow().getHandle();
        boolean free = mc.currentScreen == null;
        for (Mod m : Mods.all()) {
            int k = m.key;
            if (k <= 0 || k > GLFW.GLFW_KEY_LAST) continue;
            boolean now = GLFW.glfwGetKey(win, k) == GLFW.GLFW_PRESS;
            if (now && !PREV[k] && free) m.toggle();
            PREV[k] = now;
        }
    }
}
