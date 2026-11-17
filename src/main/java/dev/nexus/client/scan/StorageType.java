package dev.nexus.client.scan;

import net.minecraft.block.entity.*;

public enum StorageType {
    CHEST("Chest", 0xFFA94D), TRAPPED("Trapped Chest", 0xFF4D4D), BARREL("Barrel", 0xC48A4D), ENDER("Ender Chest", 0xB04DFF),
    SHULKER("Shulker Box", 0xFF7AF0), FURNACE("Furnace", 0x9AA0A6), HOPPER("Hopper", 0x7A7A85),
    DISPENSER("Dispenser / Dropper", 0x6BC5FF), BREWING("Brewing Stand", 0xFFE066), POT("Decorated Pot", 0xD9825B),
    BOOKSHELF("Chiseled Bookshelf", 0xA77B50), SPAWNER("Spawner", 0x4DFFB8), BEACON("Beacon", 0x66FFFF);

    public final String label;
    public final int color;
    StorageType(String l, int c) { label = l; color = c; }

    public static StorageType of(BlockEntity be) {
        if (be instanceof TrappedChestBlockEntity) return TRAPPED;
        if (be instanceof ChestBlockEntity) return CHEST;
        if (be instanceof BarrelBlockEntity) return BARREL;
        if (be instanceof EnderChestBlockEntity) return ENDER;
        if (be instanceof ShulkerBoxBlockEntity) return SHULKER;
        if (be instanceof AbstractFurnaceBlockEntity) return FURNACE;
        if (be instanceof HopperBlockEntity) return HOPPER;
        if (be instanceof DispenserBlockEntity) return DISPENSER;
        if (be instanceof BrewingStandBlockEntity) return BREWING;
        if (be instanceof DecoratedPotBlockEntity) return POT;
        if (be instanceof ChiseledBookshelfBlockEntity) return BOOKSHELF;
        if (be instanceof MobSpawnerBlockEntity) return SPAWNER;
        if (be instanceof BeaconBlockEntity) return BEACON;
        return null;
    }
}
