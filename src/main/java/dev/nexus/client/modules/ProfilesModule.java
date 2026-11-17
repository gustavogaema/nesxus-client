package dev.nexus.client.modules;

import dev.nexus.client.config.ConfigManager;
import dev.nexus.client.module.Mod;
import dev.nexus.client.module.Mods;
import dev.nexus.client.setting.Setting;
import dev.nexus.client.util.Notify;

import java.util.List;

/** Saved configurations: save, load, create, delete, reset. */
final class ProfilesModule extends Mod {
    private final Setting.Mode sel;
    private final Setting.Text name;

    ProfilesModule() {
        super("Profiles", "Save and load complete configurations (modules, colours, ranges, keybinds).", Category.SETTINGS);
        alwaysOn = true;
        sel = general.mode("Profile", "default"); sel.persist = false;
        name = general.text("New Profile Name", ""); name.persist = false; name.maxLen = 32;
        general.action("Load Selected", () -> {
            if (ConfigManager.loadProfile(sel.get())) Notify.toast("Loaded profile '" + sel.get() + "'");
            else Notify.toast("Profile not found");
        });
        general.action("Save Over Selected", () -> { ConfigManager.saveProfile(sel.get()); Notify.toast("Saved '" + sel.get() + "'"); });
        general.action("Save As New (use name above)", () -> {
            String n = name.value.trim();
            if (n.isEmpty()) { Notify.toast("Type a profile name first"); return; }
            ConfigManager.saveProfile(n); refresh(); Notify.toast("Created profile '" + n + "'"); name.value = "";
        });
        general.action("Delete Selected", () -> {
            if (sel.get().equals("default")) { Notify.toast("The default profile cannot be deleted"); return; }
            ConfigManager.delete(sel.get()); refresh(); Notify.toast("Profile deleted");
        });
        general.action("Reset Everything To Defaults", () -> {
            for (Mod m : Mods.all()) m.resetDefaults();
            Notify.toast("All settings reset");
        });
    }

    @Override public void onGuiOpen() { refresh(); }

    void refresh() {
        List<String> l = ConfigManager.list();
        if (l.isEmpty()) l.add("default");
        sel.modes = l.toArray(new String[0]);
        sel.index = Math.max(0, l.indexOf(ConfigManager.current));
    }
}
