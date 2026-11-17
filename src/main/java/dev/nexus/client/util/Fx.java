package dev.nexus.client.util;

import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

public final class Fx {
    private Fx() { }

    public static double lx(Entity e, float t) { return MathHelper.lerp((double) t, e.lastRenderX, e.getX()); }
    public static double ly(Entity e, float t) { return MathHelper.lerp((double) t, e.lastRenderY, e.getY()); }
    public static double lz(Entity e, float t) { return MathHelper.lerp((double) t, e.lastRenderZ, e.getZ()); }

    /** Alpha multiplier that fades ESP with distance. */
    public static float fade(double dist, double range) {
        return (float) Math.max(0.3, 1.0 - dist / Math.max(1.0, range) * 0.7);
    }

    public static int mix(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int aa = (int) (((a >>> 24) & 255) * (1 - t) + ((b >>> 24) & 255) * t);
        int rr = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int gg = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bb = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return (aa << 24) | (rr << 16) | (gg << 8) | bb;
    }

    public static int alpha(int argb, float mul) {
        int a = Math.max(0, Math.min(255, Math.round(((argb >>> 24) & 255) * mul)));
        return (a << 24) | (argb & 0xFFFFFF);
    }

    /** 1.0 = green, 0.5 = yellow, 0.0 = red. */
    public static int healthColor(float f) {
        f = Math.max(0f, Math.min(1f, f));
        int r = f > 0.5f ? Math.round((1f - f) * 2f * 255f) : 255;
        int g = f > 0.5f ? 255 : Math.round(f * 2f * 255f);
        return 0xFF000000 | (r << 16) | (g << 8) | 0x30;
    }

    public static String keyName(int k) {
        if (k < 0) return "NONE";
        if (k >= GLFW.GLFW_KEY_F1 && k <= GLFW.GLFW_KEY_F25) return "F" + (k - GLFW.GLFW_KEY_F1 + 1);
        switch (k) {
            case GLFW.GLFW_KEY_RIGHT_SHIFT: return "RSHIFT";
            case GLFW.GLFW_KEY_LEFT_SHIFT: return "LSHIFT";
            case GLFW.GLFW_KEY_RIGHT_CONTROL: return "RCTRL";
            case GLFW.GLFW_KEY_LEFT_CONTROL: return "LCTRL";
            case GLFW.GLFW_KEY_RIGHT_ALT: return "RALT";
            case GLFW.GLFW_KEY_LEFT_ALT: return "LALT";
            case GLFW.GLFW_KEY_SPACE: return "SPACE";
            case GLFW.GLFW_KEY_TAB: return "TAB";
            case GLFW.GLFW_KEY_INSERT: return "INSERT";
            case GLFW.GLFW_KEY_HOME: return "HOME";
            case GLFW.GLFW_KEY_END: return "END";
            case GLFW.GLFW_KEY_PAGE_UP: return "PGUP";
            case GLFW.GLFW_KEY_PAGE_DOWN: return "PGDN";
            case GLFW.GLFW_KEY_UP: return "UP";
            case GLFW.GLFW_KEY_DOWN: return "DOWN";
            case GLFW.GLFW_KEY_LEFT: return "LEFT";
            case GLFW.GLFW_KEY_RIGHT: return "RIGHT";
            case GLFW.GLFW_KEY_KP_0: return "NUM0";
            default:
                String n = GLFW.glfwGetKeyName(k, 0);
                return n != null ? n.toUpperCase() : "KEY" + k;
        }
    }

    public static String title(String s) {
        StringBuilder b = new StringBuilder();
        for (String p : s.replace('_', ' ').split(" ")) {
            if (p.isEmpty()) continue;
            if (b.length() > 0) b.append(' ');
            b.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));
        }
        return b.toString();
    }
}
