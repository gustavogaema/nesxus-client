package dev.nexus.client.render;

import dev.nexus.client.util.Fx;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;

/**
 * World -> screen projection done in plain maths (camera position, yaw/pitch, FOV). Because ESP is drawn as 2D HUD
 * geometry it never touches the world render pipeline: zero conflicts with Sodium / Iris / shaders, and it works
 * through walls by construction. Only active in first person.
 */
public final class Projector {
    public static final Projector I = new Projector();

    public static final class Rect {
        public float x0, y0, x1, y1;
        public float cx() { return (x0 + x1) * 0.5f; }
        public float cy() { return (y0 + y1) * 0.5f; }
    }

    public final Rect rect = new Rect();
    /** Screen coords of the 8 corners of the last projected box, index bit0=x, bit1=y, bit2=z. */
    public final float[] corners = new float[16];
    public boolean valid;
    public int w, h;
    public double cx, cy, cz;

    private double fx, fy, fz, rx, rz, ux, uy, uz, tanHalf, aspect;
    private float fovMul = 1f;
    private long lastNs = System.nanoTime();
    private final float[] out = new float[2];

    public void update(MinecraftClient mc, int w, int h, float t) {
        ClientPlayerEntity p = mc.player;
        valid = p != null && mc.options.getPerspective().isFirstPerson();
        if (!valid) return;
        this.w = w; this.h = h;
        cx = Fx.lx(p, t); cy = Fx.ly(p, t) + (p.getEyeY() - p.getY()); cz = Fx.lz(p, t);

        double yaw = Math.toRadians(p.getYaw(t)), pitch = Math.toRadians(p.getPitch(t));
        double cp = Math.cos(pitch);
        fx = -Math.sin(yaw) * cp; fy = -Math.sin(pitch); fz = Math.cos(yaw) * cp;
        rx = -Math.cos(yaw); rz = -Math.sin(yaw);
        ux = -rz * fy; uy = rz * fx - rx * fz; uz = rx * fy;

        // Re-create vanilla's dynamic FOV (sprint / speed effects) so boxes stay glued to targets.
        long now = System.nanoTime();
        float dtTicks = Math.min(5f, (now - lastNs) / 5.0e7f);
        lastNs = now;
        float f = 1f;
        if (p.getAbilities().flying) f *= 1.1f;
        float walk = p.getAbilities().getWalkSpeed();
        if (walk > 0) f *= (p.getMovementSpeed() / walk + 1f) / 2f;
        if (Float.isNaN(f) || Float.isInfinite(f)) f = 1f;
        float scale = mc.options.getFovEffectScale().getValue().floatValue();
        f = 1f + (f - 1f) * scale;
        fovMul += (f - fovMul) * (1f - (float) Math.pow(0.5, dtTicks));

        double fov = Math.max(1.0, Math.min(170.0, mc.options.getFov().getValue() * fovMul));
        tanHalf = Math.tan(Math.toRadians(fov) / 2.0);
        aspect = (double) w / Math.max(1, h);
    }

    public double dist2(double x, double y, double z) {
        double dx = x - cx, dy = y - cy, dz = z - cz;
        return dx * dx + dy * dy + dz * dz;
    }

    /** Projects a point. Returns false when behind the camera. Result in {@link #out}-backed array. */
    public boolean project(double x, double y, double z, float[] dst) {
        double dx = x - cx, dy = y - cy, dz = z - cz;
        double depth = dx * fx + dy * fy + dz * fz;
        if (depth < 0.05) return false;
        double px = (dx * rx + dz * rz) / depth;
        double py = (dx * ux + dy * uy + dz * uz) / depth;
        dst[0] = (float) (w * 0.5 + px / (tanHalf * aspect) * w * 0.5);
        dst[1] = (float) (h * 0.5 - py / tanHalf * h * 0.5);
        return true;
    }

    /** Projects a single point into a shared buffer (valid until next call). */
    public float[] point(double x, double y, double z) { return project(x, y, z, out) ? out : null; }

    /** Projects an AABB; null if any corner is behind the camera. Fills {@link #rect} and {@link #corners}. */
    public Rect box(double x0, double y0, double z0, double x1, double y1, double z1) {
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        for (int i = 0; i < 8; i++) {
            double x = (i & 1) == 0 ? x0 : x1, y = (i & 2) == 0 ? y0 : y1, z = (i & 4) == 0 ? z0 : z1;
            if (!project(x, y, z, out)) return null;
            corners[i * 2] = out[0]; corners[i * 2 + 1] = out[1];
            minX = Math.min(minX, out[0]); maxX = Math.max(maxX, out[0]);
            minY = Math.min(minY, out[1]); maxY = Math.max(maxY, out[1]);
        }
        rect.x0 = minX; rect.y0 = minY; rect.x1 = maxX; rect.y1 = maxY;
        return rect;
    }
}
