# Validation — 2026-10-17

Final command: `./gradlew build runClientGameTest` — **BUILD SUCCESSFUL**.

Environment: Minecraft 26.3, Fabric Loader 0.20.0, Fabric API 0.163.0+26.3, Java 25, macOS on Apple M5, OpenGL 4.1. The final release JAR was inspected for metadata, Java bytecode level, shaders, and absence of test classes or reference-client dependencies.

## Automated checks performed

`dockChecks` passed **617,795 assertions**, covering:

- Default values, nondefault file round trips, atomic save cleanup, malformed property syntax, individual invalid fields, nonfinite values, and numeric clamps.
- 28,080 layouts across six logical widths (160–1920), four requested scales, three magnification strengths, three falloff radii, both offhand sides, and fractional selection positions.
- Shelf centering, ordered slots, complete slot/frame/offhand/attack-indicator bounds, vertical fit with shadows and pickup animation, and uniform spacing at zero magnification.

The separate Fabric client test passed in a real local test world, covering:

| Area | Check performed |
| --- | --- |
| Shaders and mixins | Startup loaded all required mixins; the optional surface pipeline and all 16 blur variants compiled successfully. |
| Menu | Opened from the vanilla Options button; rendered all three vanilla settings pages and title-menu preview; kept preview available with dock disabled. |
| Live updates | Toggled dock, changed opacity with a vanilla slider, reset all defaults, and used square/capsule, tinted, clear/frosted and lighting settings during HUD rendering. |
| Navigation and config | Escape returned to Options; config was saved and reloaded; reset defaults took effect immediately. |
| Key binding | Verified unbound default and translated category; rebound through normal KeyMapping APIs; verified normal conflict matching and options.txt persistence; opened settings while disabled. |
| Typing | A bound key left ChatScreen open and caused no deferred settings opening after closing chat. |
| Items | Rendered a damaged sword, 64-count block stack, 16-count pearls, active pearl cooldown, item pickup animation, and offhand shield. |
| Selection border | Completed the floating frame with a bottom border row sampled from the vanilla sprite; verified its black center pixel in a real GUI-scale-4 screenshot with shadows disabled, plus layout inset checks at fractional and maximum magnification. |
| Hands and attack | Rendered both dominant-hand configurations and an active hotbar attack indicator. |
| Auto-hide | Waited for the configured one-second idle delay and slide-out; changing slot revealed the dock. |
| Vanilla restoration | Disabled the dock, asserted its HUD hook was inactive, and captured the restored vanilla hotbar. |
| Scaling and resize | Rendered zero magnification at requested 200% dock scale in a 1280×960 window with GUI scale 4; resized to 640×480 with maximum magnification/falloff; forced a mismatched capture and verified it defers without disabling glass, then verified rendering resumes after resize. |
| Fallback | Forced the same failure flag used after GPU capture failure and rendered rounded translucent fallback geometry. |
| Reload and cleanup | Reloaded resources, waited for the loading overlay to disappear, checked the rebuilt shader pipeline, closed GPU targets, then rendered with recreated targets. Client shutdown completed successfully. |

Screenshots from the final run are under `build/run/clientGameTest/screenshots/`. The default settings, both hand configurations, active cooldown, square/capsule tint masks, large and small windows, hidden dock, vanilla hotbar, fallback, and post-reload HUD were visually inspected for readable UI, clipping and overlap.

Representative captures:

- [Vanilla settings and preview](build/run/clientGameTest/screenshots/0000_settings-default.png)
- [Items, durability, counts, cooldown and offhand](build/run/clientGameTest/screenshots/0004_hud-right-hand.png)
- [Square tinted surface](build/run/clientGameTest/screenshots/0007_hud-square-tinted-clear.png)
- [Capsule tinted surface](build/run/clientGameTest/screenshots/0008_hud-capsule-tinted-frosted.png)
- [GUI scale 4](build/run/clientGameTest/screenshots/0009_hud-zero-magnification-high-scale.png)
- [Vanilla hotbar restored](build/run/clientGameTest/screenshots/0011_hud-vanilla-disabled.png)
- [After resource reload](build/run/clientGameTest/screenshots/0013_hud-after-resource-reload.png)

## Remaining manual coverage

These checks were **not** performed:

- Vulkan or other GPU/driver combinations, shader packs, render replacements, and other mods that also replace the hotbar or rearrange HUD bars.
- Every resource pack/item model, unusually oversized modded item models, and every pixel density or fullscreen/minimize transition.
- Separate visual scenarios for armor, underwater air, multirow health, mount health and jump bars. Their vanilla extraction shares the tested translated HUD group.
- A full process restart with nondefault settings. Nondefault persistence was checked with file round trips and the same load routine used at initialization.
- Real hardware failure/device loss. Fallback was tested by forcing the renderer's failure state.

The code preserves the spectator hotbar by only replacing the normal item-hotbar method; spectator gameplay was not separately exercised.
