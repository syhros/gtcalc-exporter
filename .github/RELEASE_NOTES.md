Pack Extract exports every item, fluid, icon, tag (or ore dictionary entry) and recipe of a modpack, including GregTech recipes with their chanced outputs, as JSON and CSV that [gtcalc.app](https://gtcalc.app) can load.

## Pick the jar for your Minecraft version

| Minecraft | Loader | Jar |
|---|---|---|
| 1.7.10 | Forge (GT New Horizons) | `packextract-<version>.jar` |
| 1.12.2 | Forge | `packextract-forge-1.12.2-<version>.jar` |
| 1.20.1 | Forge | `packextract-forge-1.20.1-<version>.jar` |
| 1.21.1 | NeoForge | `packextract-neoforge-1.21.1-<version>.jar` |

## Use it

1. Put the jar in the instance's `mods` folder. It is client-side only; servers do not need it.
2. Start the game and join any world (single player is fine).
3. Type `/packextract` in chat (`/packextract noimages` skips the images). A progress screen shows each step.
4. When it finishes, click **Open folder**. The export is in `<instance>/.minecraft/pack-extract/<pack name>-<date>/`.

## What it exports

- Items (every variant from the registry, creative tabs, JEI or NEI, and the ore dictionary), fluids, and a 64 px image of each
- Tags (1.13+) or the ore dictionary (1.7.10, 1.12.2)
- Crafting and furnace recipes
- GregTech recipes: GregTech 5 (GTNH), GregTech CEu on 1.12.2, and GregTech CEu Modern on 1.20.1 and 1.21.1, with chances, EU/t, duration, coil heat and circuits
- On 1.20.1 and 1.21.1, every other mod's recipe type (Create, Mekanism, Thermal...) in `recipes/other.json`

Every version is tested on each release: an automatic export runs in a real game client with JEI, GregTech and other popular mods installed. See the [README](https://github.com/syhros/gtcalc-exporter#readme) for the file formats.
