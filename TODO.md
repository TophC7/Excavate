# Excavate — TODO

## Block Break Permission Check

Currently, area-mined blocks bypass protection mods (FTB Chunks, Cadmus, etc.).
The center block goes through NeoForge's `BreakEvent` pipeline, but surrounding
blocks are destroyed directly via `level.destroyBlock()` — no permission check.

### The problem

A player at the edge of a claimed area can mine into someone else's claim.
The surrounding blocks get removed without asking the protection mod if it's allowed.

### The fix

Replace raw `level.destroyBlock()` with `ServerPlayer.gameMode.destroyBlock(target)`
for each surrounding block. This goes through the full server-side pipeline:

1. Fires `BlockEvent.BreakEvent` per block
2. Protection mods can cancel individual blocks
3. Only blocks that pass all checks get destroyed

### Complications

- `gameMode.destroyBlock()` fires our own `onBlockBreak` handler — the per-player
  `excavatingPlayers` guard already handles this correctly
- Drop handling changes: `gameMode.destroyBlock()` handles drops internally,
  so we'd remove the manual `Block.dropResources()` call
- Tool damage: `gameMode.destroyBlock()` may apply its own durability loss,
  need to avoid double-damaging the tool
- Need to test that Fortune/Silk Touch still apply correctly through this path

## Highlight Per-Block Accuracy

The renderer draws one big rectangle for the entire radius. Blocks that are air,
wrong material, or unbreakable still appear inside the highlight. Consider
rendering per-block outlines for only the blocks that would actually break.
More expensive but more accurate — or accept the tradeoff and treat it as an
area indicator.

## Tool Durability Scaling for Area Mining

Currently 1 durability per block in the area. At Excavation II (5x5), that's 24
extra blocks per swing — a netherite pickaxe still lasts 80+ full swings. Consider
a durability multiplier per enchantment level, or a flat extra cost per area swing.
Same applies to crop harvesting where vanilla doesn't consume durability at all
for breaking crops.

## SafeConfig.validateOrReset for COMMON Configs

`validateOrReset` hardcodes `-client.toml` for file cleanup. Excavate uses COMMON
config (`-common.toml`), so the corrupted file rename is a no-op. Fix in KwahsCore:
accept a `ModConfig.Type` parameter or derive the filename from the spec.

## Ideas

- Excavation 3, with odd way to acquire
- Some enchantment that softens the damage when using excavation