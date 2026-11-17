package dev.nexus.client.module;

import dev.nexus.client.setting.Setting;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Registry of every module. */
public final class Mods {
    private static final List<Mod> ALL = new ArrayList<>();
    private Mods() { }

    public static void add(Mod m) {
        m.defaultEnabled = m.enabled;
        // Modules with a single named settings group start with that group expanded.
        int named = 0; Mod.Group only = null;
        for (Mod.Group g : m.groups) if (!g.name.isEmpty()) { named++; only = g; }
        if (named == 1) only.open = true;
        ALL.add(m);
    }

    public static List<Mod> all() { return ALL; }

    public static List<Mod> in(Mod.Category c) {
        List<Mod> out = new ArrayList<>();
        for (Mod m : ALL) if (m.category == c) out.add(m);
        return out;
    }

    @SuppressWarnings("unchecked")
    public static <T extends Mod> T get(Class<T> c) {
        for (Mod m : ALL) if (c.isInstance(m)) return (T) m;
        return null;
    }

    /** Search by module name, description, category, group and setting names. Name matches rank first. */
    public static List<Mod> search(String q) {
        String s = q.toLowerCase(Locale.ROOT).trim();
        List<Mod> first = new ArrayList<>(), rest = new ArrayList<>();
        if (s.isEmpty()) return first;
        for (Mod m : ALL) {
            String n = m.name.toLowerCase(Locale.ROOT);
            if (n.contains(s)) { first.add(m); continue; }
            boolean hit = m.description.toLowerCase(Locale.ROOT).contains(s) || m.category.label.toLowerCase(Locale.ROOT).contains(s);
            if (!hit) for (Mod.Group g : m.groups) {
                if (g.name.toLowerCase(Locale.ROOT).contains(s)) { hit = true; break; }
                for (Setting st : g.settings) if (st.name.toLowerCase(Locale.ROOT).contains(s)) { hit = true; break; }
                if (hit) break;
            }
            if (hit) rest.add(m);
        }
        first.addAll(rest);
        return first;
    }
}
