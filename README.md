# Excavate

Area mining enchantment for NeoForge 1.21.1.

![Mod preview, Excavation II](public/preview.webp)

**Excavation I** affects a 3x3 area.
**Excavation II** affects a 5x5 area.
Crouch to use single-block interactions.

## How it works

- Enchant any pickaxe, axe, shovel, or hoe
- Mine a block and the surrounding area breaks as well
- Hoe tilling, shovel pathing, and axe stripping also affect the surrounding area
- Each block drops its own correct loot
- Only blocks your tool can actually mine are affected
- Tool takes durability per block broken and stops if it breaks
- Unbreakable blocks (bedrock, etc.) are never affected

### Crop harvesting

Hoes with Excavation harvest mature crops in an area. Auto-replant is
enabled by default and one seed is consumed from each crop's drops to replant it.
If no seed drops, that crop is left empty. You can toggle auto-replant in the config screen.

## Configuration

Open the config screen from the Mods menu. Settings:

| Setting              | Scope  | Default | Description                                                  |
| -------------------- | ------ | ------- | ------------------------------------------------------------ |
| Show Highlight       | Client | On      | Show the area outline preview                                |
| Auto-Replant         | Common | On      | Replant crops automatically when area-harvesting             |
| Area Hoe Tilling     | Common | On      | Let hoes till dirtlike blocks in an area                     |
| Area Shovel Pathing  | Common | On      | Let shovels create dirt paths in an area                     |
| Area Axe Actions     | Common | On      | Let axes strip logs and apply other right-click axe actions in an area |

## Getting the enchantment

- **Enchanting table** - rare (same weight as Fortune)
- **Villager trades** - available from librarians
- **Commands** - `/enchant @s excavate:excavation 2`

## Compatibility

- Works with any modded tool that uses vanilla tool tags
- Supports [Enchantment Descriptions](https://www.curseforge.com/minecraft/mc-mods/enchantment-descriptions) for in-game tooltip text
- Requires NeoForge 21.1.219+

## License

GPL-3.0-or-later
