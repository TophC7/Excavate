# Excavate — TODO

## Block Break Permission Check

Currently, area-mined blocks bypass protection mods (FTB Chunks, Cadmus, etc.).
The center block goes through NeoForge's `BreakEvent` pipeline, but surrounding
blocks are destroyed directly via `level.destroyBlock()` — no permission check.

### The problem

A player at the edge of a claimed area can mine into someone else's claim.
The surrounding blocks get removed without asking the protection mod if it's allowed.

Beyond protection, surrounding blocks generate **no events at all** — no XP orbs,
no advancement triggers, no block break statistics, and no mod interop (other mods
listening to `BreakEvent` see nothing for the surrounding blocks).

### The fix

Replace raw `level.destroyBlock()` with `ServerPlayer.gameMode.destroyBlock(target)`
for each surrounding block. This goes through the full server-side pipeline:

1. Fires `BlockEvent.BreakEvent` per block
2. Protection mods can cancel individual blocks
3. Only blocks that pass all checks get destroyed
4. XP, stats, and advancements fire correctly

### Complications

- `gameMode.destroyBlock()` fires our own `onBlockBreak` handler — the per-player
  `excavatingPlayers` guard already handles this correctly
- Drop handling changes: `gameMode.destroyBlock()` handles drops internally,
  so we'd remove the manual `Block.dropResources()` call
- Tool damage: `gameMode.destroyBlock()` may apply its own durability loss,
  need to avoid double-damaging the tool
- Need to test that Fortune/Silk Touch still apply correctly through this path

## ~~Highlight Per-Block Accuracy~~ ✓

Renderer now iterates each position and checks the same filters as the server-side
mining logic (air, unbreakable, wrong tool, immature crops). Only blocks that would
actually break get an outline.

## Tool Durability Scaling for Area Mining

Currently 1 durability per block in the area. At Excavation II (5x5), that's 24
extra blocks per swing — a netherite pickaxe still lasts 80+ full swings. Consider
a durability multiplier per enchantment level, or a flat extra cost per area swing.
Same applies to crop harvesting where vanilla doesn't consume durability at all
for breaking crops.

## ~~SafeConfig.validateOrReset for COMMON Configs~~ ✓

Fixed in KwahsCore 0.3.0 — `validateOrReset` now accepts a `configType` string.
Excavate passes `"common"` to target the correct `-common.toml` file.

## Highlight Color Config

The outline color is hardcoded white at 60% alpha. Since there's already a "General >
Visual" config section, add a color/alpha option so players can customize the highlight.

## Ideas

- Excavation 3, with odd way to acquire
- Some enchantment that softens the damage when using excavation