package dev.nexus.client.module;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.nexus.client.setting.Setting;
import dev.nexus.client.util.Notify;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.List;

/** A feature with an ON/OFF switch, a keybind and any number of grouped settings. */
public abstract class Mod {
    public enum Category {
        COMBAT("Combat"), VISUALS("Visuals"), ESP("ESP"), WORLD("World"), PLAYER("Player"), HUD("HUD"), SETTINGS("Settings");
        public final String label;
        Category(String l) { label = l; }
    }

    /** A named, collapsible block of settings. An empty name = no header. */
    public static final class Group {
        public final String name;
        public boolean open;
        public final List<Setting> settings = new ArrayList<>();
        Group(String name) { this.name = name; }

        private <T extends Setting> T add(T s) { s.group = name; settings.add(s); return s; }
        public Setting.Bool bool(String n, boolean d) { return add(new Setting.Bool(n, d)); }
        public Setting.Num num(String n, double d, double min, double max, double step) { return add(new Setting.Num(n, d, min, max, step)); }
        public Setting.Mode mode(String n, String... modes) { return add(new Setting.Mode(n, modes)); }
        public Setting.Color color(String n, int argb) { return add(new Setting.Color(n, argb)); }
        public Setting.Text text(String n, String d) { return add(new Setting.Text(n, d)); }
        public Setting.Action action(String n, Runnable r) { return add(new Setting.Action(n, r)); }
    }

    public final String name, description;
    public final Category category;
    public boolean enabled, defaultEnabled;
    public boolean alwaysOn;         // settings-only modules (no ON/OFF switch)
    public boolean needsProjection;  // 2D-ESP modules: need a valid camera projection
    public int key = -1;
    public final List<Group> groups = new ArrayList<>();
    protected final Group general;

    // GUI animation state
    public boolean expanded;
    public float animHover, animOn, animOpen;

    protected Mod(String name, String description, Category category) {
        this.name = name; this.description = description; this.category = category;
        general = group("");
    }

    protected static MinecraftClient mc() { return MinecraftClient.getInstance(); }

    public final Group group(String name) {
        for (Group g : groups) if (g.name.equals(name)) return g;
        Group g = new Group(name); groups.add(g); return g;
    }

    // ---- lifecycle hooks
    public void onEnable() { }
    public void onDisable() { }
    public void onTick(MinecraftClient mc) { }
    public void onHud(DrawContext ctx, float tickProgress) { }
    public void onGuiOpen() { }

    public void toggle() { setEnabled(!enabled, true); }

    public void setEnabled(boolean v, boolean notify) {
        if (alwaysOn || v == enabled) return;
        enabled = v;
        try { if (v) onEnable(); else onDisable(); } catch (Throwable t) { dev.nexus.client.NexusClient.LOG.error("Module {} failed", name, t); }
        if (notify) Notify.toast(name + (v ? " enabled" : " disabled"));
    }

    public Iterable<Setting> settings() {
        List<Setting> all = new ArrayList<>();
        for (Group g : groups) all.addAll(g.settings);
        return all;
    }

    public void resetDefaults() {
        for (Setting s : settings()) s.reset();
        setEnabled(defaultEnabled, false);
        if (!alwaysOn) key = -1;
    }

    // ---- persistence
    public JsonObject save() {
        JsonObject o = new JsonObject();
        if (!alwaysOn) o.addProperty("enabled", enabled);
        o.addProperty("key", key);
        JsonObject s = new JsonObject();
        for (Setting st : settings()) if (st.persist) s.add(st.key(), st.save());
        o.add("settings", s);
        return o;
    }

    public void load(JsonObject o) {
        if (o.has("enabled")) setEnabled(o.get("enabled").getAsBoolean(), false);
        if (o.has("key")) key = o.get("key").getAsInt();
        if (o.has("settings")) {
            JsonObject s = o.getAsJsonObject("settings");
            for (Setting st : settings()) {
                JsonElement e = s.get(st.key());
                if (e != null && st.persist) st.load(e);
            }
        }
    }
}
