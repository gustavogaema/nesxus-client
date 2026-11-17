package dev.nexus.client.scan;

import java.util.EnumSet;

/** Implemented by modules that consume the storage cache; tells it which block-entity types to collect. */
public interface StorageUser {
    EnumSet<StorageType> storageTypes();
}
