package dev.nexus.client.module;

import dev.nexus.client.render.EspStyle;
import java.util.List;

/** Implemented by every module that owns ESP styles (used by the colour-preset system). */
public interface HasEsp {
    List<EspStyle> styles();
}
