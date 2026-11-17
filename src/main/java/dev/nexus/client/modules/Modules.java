package dev.nexus.client.modules;

import dev.nexus.client.module.Mods;
import dev.nexus.client.scan.StorageType;

/** Registration order = per-frame draw order (ESP first, tracers last, HUD on top). */
public final class Modules {
    private Modules() { }

    public static void registerAll() {
        // ESP
        Mods.add(new PlayerEsp());
        Mods.add(new EntityEsp());
        Mods.add(new StorageEspModule("Chest ESP", "Highlights chests (double chests merged).", StorageType.CHEST));
        Mods.add(new StorageEspModule("Trapped Chest ESP", "Highlights trapped chests.", StorageType.TRAPPED));
        Mods.add(new StorageEspModule("Barrel ESP", "Highlights barrels.", StorageType.BARREL));
        Mods.add(new StorageEspModule("Ender Chest ESP", "Highlights ender chests.", StorageType.ENDER));
        Mods.add(new StorageEspModule("Shulker Box ESP", "Highlights shulker boxes.", StorageType.SHULKER));
        Mods.add(new StorageEspModule("Other Storage ESP", "Furnaces, hoppers, dispensers, brewing stands, pots, bookshelves, spawners, beacons.",
                StorageType.FURNACE, StorageType.HOPPER, StorageType.DISPENSER, StorageType.BREWING, StorageType.POT,
                StorageType.BOOKSHELF, StorageType.SPAWNER, StorageType.BEACON));
        // World
        Mods.add(new WorldMods.XRay());
        Mods.add(new WorldMods.BlockEsp());
        Mods.add(new WorldMods.CaveFinder());
        // Combat (information only)
        Mods.add(new CombatPlayerMods.TargetHighlight());
        // Visuals
        Mods.add(new Nametags());
        Mods.add(new VisualMods.Fullbright());
        Mods.add(new VisualMods.EntityHighlight());
        Mods.add(new Tracers());               // after every ESP module so queued lines are flushed last
        // Player
        Mods.add(new CombatPlayerMods.ToggleSprint());
        Mods.add(new CombatPlayerMods.DurabilityAlert());
        Mods.add(new CombatPlayerMods.DeathPoint());
        // HUD widgets
        Mods.add(new WorldMods.StorageFinder());
        Mods.add(new CombatPlayerMods.TargetHud());
        Mods.add(on(HudMods.watermark()));
        Mods.add(on(HudMods.fps()));
        Mods.add(on(HudMods.coords()));
        Mods.add(HudMods.direction());
        Mods.add(HudMods.biome());
        Mods.add(HudMods.ping());
        Mods.add(HudMods.speed());
        Mods.add(HudMods.clock());
        Mods.add(on(new HudMods.ArmorHud()));
        Mods.add(new HudMods.NearbyPlayers());
        // Settings
        Mods.add(new Interface());
        Mods.add(new GlobalEsp());
        Mods.add(new ProfilesModule());
    }

    private static <T extends dev.nexus.client.module.Mod> T on(T m) { m.enabled = true; return m; }
}
