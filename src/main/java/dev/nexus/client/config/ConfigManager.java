package dev.nexus.client.config;

import com.google.gson.*;
import dev.nexus.client.NexusClient;
import dev.nexus.client.module.Mod;
import dev.nexus.client.module.Mods;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** JSON persistence: config/nexus/profiles/*.json, state.json (active profile), gui.json (window layout). */
public final class ConfigManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Path dir, profiles;
    public static String current = "default";
    private static JsonObject gui = new JsonObject();

    private ConfigManager() { }

    public static void init() {
        dir = FabricLoader.getInstance().getConfigDir().resolve("nexus");
        profiles = dir.resolve("profiles");
        try { Files.createDirectories(profiles); } catch (IOException e) { NexusClient.LOG.error("Config dir", e); }
        JsonObject st = read(dir.resolve("state.json"));
        if (st != null && st.has("profile")) current = st.get("profile").getAsString();
        JsonObject g = read(dir.resolve("gui.json"));
        if (g != null) gui = g;
        if (!loadProfile(current)) { current = "default"; saveProfile("default"); }
    }

    public static JsonObject snapshot() {
        JsonObject root = new JsonObject(), mods = new JsonObject();
        for (Mod m : Mods.all()) mods.add(m.name, m.save());
        root.addProperty("version", 1);
        root.add("modules", mods);
        return root;
    }

    public static boolean loadProfile(String n) {
        JsonObject o = read(profiles.resolve(safe(n) + ".json"));
        if (o == null || !o.has("modules")) return false;
        JsonObject mods = o.getAsJsonObject("modules");
        for (Mod m : Mods.all()) {
            JsonElement e = mods.get(m.name);
            if (e != null && e.isJsonObject()) { try { m.load(e.getAsJsonObject()); } catch (Exception ex) { NexusClient.LOG.warn("Bad config for {}", m.name, ex); } }
        }
        current = n; writeState();
        return true;
    }

    public static void saveProfile(String n) { write(profiles.resolve(safe(n) + ".json"), snapshot()); current = n; writeState(); }
    public static void saveCurrent() { if (dir != null) saveProfile(current); }

    public static boolean delete(String n) {
        try { return Files.deleteIfExists(profiles.resolve(safe(n) + ".json")); } catch (IOException e) { return false; }
    }

    public static List<String> list() {
        List<String> out = new ArrayList<>();
        if (profiles == null) return out;
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(profiles, "*.json")) {
            for (Path p : ds) { String f = p.getFileName().toString(); out.add(f.substring(0, f.length() - 5)); }
        } catch (IOException ignored) { }
        Collections.sort(out);
        return out;
    }

    public static JsonObject gui() { return gui; }
    public static void saveGui() { if (dir != null) write(dir.resolve("gui.json"), gui); }

    private static void writeState() { JsonObject o = new JsonObject(); o.addProperty("profile", current); write(dir.resolve("state.json"), o); }
    private static String safe(String n) { String s = n.replaceAll("[^A-Za-z0-9_\\- ]", "_").trim(); return s.isEmpty() ? "default" : s; }

    private static JsonObject read(Path p) {
        try { if (!Files.exists(p)) return null; return JsonParser.parseString(Files.readString(p, StandardCharsets.UTF_8)).getAsJsonObject(); }
        catch (Exception e) { NexusClient.LOG.warn("Could not read {}", p, e); return null; }
    }
    private static void write(Path p, JsonObject o) {
        try { Files.writeString(p, GSON.toJson(o), StandardCharsets.UTF_8); } catch (IOException e) { NexusClient.LOG.error("Could not write {}", p, e); }
    }
}
