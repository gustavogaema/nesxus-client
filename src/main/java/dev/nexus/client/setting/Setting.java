package dev.nexus.client.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.Locale;
import java.util.function.BooleanSupplier;

/** Base class for every configurable value. Subclasses know how to (de)serialise themselves. */
public abstract class Setting {
    public final String name;
    public String group = "";
    public String description = "";
    public boolean hidden;          // saved, but not drawn in the GUI
    public boolean persist = true;  // false = never written to profiles
    public boolean open;            // GUI: expanded (colour picker)
    public float anim;              // GUI: animation state
    public BooleanSupplier visible = () -> true;

    protected Setting(String name) { this.name = name; }

    public Setting desc(String d) { description = d; return this; }
    public Setting showIf(BooleanSupplier s) { visible = s; return this; }
    public String key() { return group.isEmpty() ? name : group + "/" + name; }

    public abstract JsonElement save();
    public abstract void load(JsonElement e);
    public abstract void reset();

    // ------------------------------------------------------------------ Bool
    public static final class Bool extends Setting {
        public boolean value;
        private final boolean def;
        public Bool(String n, boolean d) { super(n); value = def = d; }
        public boolean get() { return value; }
        @Override public JsonElement save() { return new JsonPrimitive(value); }
        @Override public void load(JsonElement e) { try { value = e.getAsBoolean(); } catch (Exception ignored) { } }
        @Override public void reset() { value = def; }
    }

    // ------------------------------------------------------------------- Num
    public static final class Num extends Setting {
        public double value;
        public final double min, max, step, def;
        public String suffix = "";
        public Num(String n, double d, double min, double max, double step) {
            super(n); this.value = this.def = d; this.min = min; this.max = max; this.step = step;
        }
        public Num unit(String s) { suffix = s; return this; }
        public double d() { return value; }
        public float f() { return (float) value; }
        public int i() { return (int) Math.round(value); }
        public void set(double v) {
            if (step > 0) v = Math.round(v / step) * step;
            value = Math.max(min, Math.min(max, v));
        }
        public String display() {
            String s = step >= 1 ? Integer.toString(i()) : String.format(Locale.ROOT, step < 0.1 ? "%.2f" : "%.1f", value);
            return s + suffix;
        }
        @Override public JsonElement save() { return new JsonPrimitive(value); }
        @Override public void load(JsonElement e) { try { set(e.getAsDouble()); } catch (Exception ignored) { } }
        @Override public void reset() { value = def; }
    }

    // ------------------------------------------------------------------ Mode
    public static final class Mode extends Setting {
        public String[] modes;
        public int index, defIndex;
        public Mode(String n, String... modes) { super(n); this.modes = modes; }
        public Mode def(String s) {
            for (int i = 0; i < modes.length; i++) if (modes[i].equals(s)) index = defIndex = i;
            return this;
        }
        public String get() { return modes[Math.max(0, Math.min(index, modes.length - 1))]; }
        public boolean is(String s) { return get().equals(s); }
        public void next() { index = (index + 1) % modes.length; }
        public void prev() { index = (index - 1 + modes.length) % modes.length; }
        @Override public JsonElement save() { return new JsonPrimitive(get()); }
        @Override public void load(JsonElement e) {
            try { String s = e.getAsString(); for (int i = 0; i < modes.length; i++) if (modes[i].equals(s)) index = i; }
            catch (Exception ignored) { }
        }
        @Override public void reset() { index = defIndex; }
    }

    // ----------------------------------------------------------------- Color
    /** RGB(A) colour stored as HSV so hue is never lost when brightness hits 0. Optional rainbow cycling. */
    public static final class Color extends Setting {
        public float h, s, v;
        public int alpha = 255;
        public boolean rainbow;
        private final int def;
        private int cache;

        public Color(String n, int argb) { super(n); def = argb; setArgb(argb); }

        public void setArgb(int argb) {
            alpha = argb >>> 24;
            float[] hsv = toHsv(argb);
            if (hsv[1] > 0.001f && hsv[2] > 0.001f) h = hsv[0];
            s = hsv[1]; v = hsv[2];
            rebuild();
        }
        public void setRgb(int rgb) { setArgb((alpha << 24) | (rgb & 0xFFFFFF)); }
        public void setHsv(float h, float s, float v) { this.h = h; this.s = s; this.v = v; rebuild(); }
        public void setAlpha(int a) { alpha = Math.max(0, Math.min(255, a)); rebuild(); }
        private void rebuild() { cache = (alpha << 24) | hsvToRgb(h, s, v); }

        /** Full ARGB (rainbow aware). */
        public int argb() {
            if (!rainbow) return cache;
            return (alpha << 24) | hsvToRgb((System.currentTimeMillis() % 4000L) / 4000f, 0.75f, 1f);
        }
        public int rgb() { return argb() & 0xFFFFFF; }

        @Override public JsonElement save() {
            JsonObject o = new JsonObject();
            o.addProperty("argb", String.format("%08X", cache));
            o.addProperty("rainbow", rainbow);
            return o;
        }
        @Override public void load(JsonElement e) {
            try {
                JsonObject o = e.getAsJsonObject();
                setArgb((int) Long.parseLong(o.get("argb").getAsString(), 16));
                rainbow = o.has("rainbow") && o.get("rainbow").getAsBoolean();
            } catch (Exception ignored) { }
        }
        @Override public void reset() { setArgb(def); rainbow = false; }

        public static int hsvToRgb(float h, float s, float v) {
            h = h - (float) Math.floor(h);
            float c = v * s, hh = h * 6f, x = c * (1f - Math.abs(hh % 2f - 1f)), m = v - c, r, g, b;
            switch ((int) hh) {
                case 0: r = c; g = x; b = 0; break;
                case 1: r = x; g = c; b = 0; break;
                case 2: r = 0; g = c; b = x; break;
                case 3: r = 0; g = x; b = c; break;
                case 4: r = x; g = 0; b = c; break;
                default: r = c; g = 0; b = x; break;
            }
            return (Math.round((r + m) * 255) << 16) | (Math.round((g + m) * 255) << 8) | Math.round((b + m) * 255);
        }
        public static float[] toHsv(int rgb) {
            float r = ((rgb >> 16) & 255) / 255f, g = ((rgb >> 8) & 255) / 255f, b = (rgb & 255) / 255f;
            float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b)), d = max - min, h;
            if (d == 0) h = 0;
            else if (max == r) h = ((g - b) / d) % 6f;
            else if (max == g) h = (b - r) / d + 2f;
            else h = (r - g) / d + 4f;
            h /= 6f; if (h < 0) h += 1f;
            return new float[]{h, max == 0 ? 0 : d / max, max};
        }
    }

    // ------------------------------------------------------------------ Text
    public static final class Text extends Setting {
        public String value;
        private final String def;
        public int maxLen = 128;
        public Text(String n, String d) { super(n); value = def = d; }
        @Override public JsonElement save() { return new JsonPrimitive(value); }
        @Override public void load(JsonElement e) { try { value = e.getAsString(); } catch (Exception ignored) { } }
        @Override public void reset() { value = def; }
    }

    // ---------------------------------------------------------------- Action
    public static final class Action extends Setting {
        public final Runnable run;
        public Action(String n, Runnable r) { super(n); run = r; persist = false; }
        @Override public JsonElement save() { return new JsonPrimitive(false); }
        @Override public void load(JsonElement e) { }
        @Override public void reset() { }
    }
}
