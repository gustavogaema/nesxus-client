# NEXUS CLIENT (Fabric, Minecraft Java 1.21.11, client-side)

Build: `gradle wrapper` (or copy a wrapper from the Fabric example mod), then `./gradlew build`.
Jar: `build/libs/nexus-client-1.0.0.jar`. Check versions in `gradle.properties` against https://fabricmc.net/develop.

Open the GUI with **Right Shift** (rebindable in Settings > Interface). Configs: `config/nexus/`.

## Design
- ESP is drawn as 2D HUD geometry from a self-computed camera projection (`render/Projector`). It never touches the
  world render pipeline, so it is compatible with Sodium/Iris and is inherently see-through. First person only.
- Every ESP category owns an independent `EspStyle` (RGB/HSV picker + alpha + rainbow, style, thickness, fill,
  min/max range, visibility rule, tracer, label). Any number of ESP modules run simultaneously.
- Block scanning (X-Ray / Block ESP / Cave Finder) is shared and time-budgeted (default 1 ms/tick), palette pre-checked.
- Storage scan is shared and throttled. LOS checks are cached.
- Glow outlines use vanilla client-side glowing via a ref-counted `GlowManager`.
- Keybinds are polled from GLFW (no vanilla KeyBinding registration).

## Known limits
- X-Ray is ESP-based (highlight through walls); it does not make blocks transparent.
- Glow outline colour is the vanilla team colour (white by default).
- Vanilla nametags are not hidden (needs a mixin); Nexus nametags are drawn above them.
- Font choice = Default/Bold/Italic/Bold Italic (no custom font resources).
- Third-person camera disables ESP.
- Written without access to the 1.21.11 jars: see "Likely API fixes" if compilation complains.

## Likely API fixes on first compile (1.21.9+ churn)
- `gui/ClickGuiScreen`: `Click`, `KeyInput`, `CharInput` imports/accessors (`click.x()/y()/button()`, `in.key()/modifiers()/codepoint()`).
- `DrawContext.getMatrices()` as `Matrix3x2fStack` (`pushMatrix/popMatrix/translate/scale`).
- `HudRenderCallback` (deprecated in favour of `HudElementRegistry`, still present).
- `scan/StorageCache`: `ChestBlock.getFacing`, `ChestBlock.CHEST_TYPE`.
- `render/Projector`: `getFovEffectScale`, `getPerspective().isFirstPerson()`.
