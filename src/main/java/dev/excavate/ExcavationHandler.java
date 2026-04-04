package dev.excavate;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import xyz.kwahson.core.config.SafeConfig;

@EventBusSubscriber(modid = ExcavateMod.MOD_ID)
public class ExcavationHandler {

    // tracks which block face the player clicked so we know what plane to expand
    private static final HashMap<UUID, Direction> clickedFace = new HashMap<>();

    // per-player recursion guard so simultaneous players don't block each other
    private static final Set<UUID> excavatingPlayers = new HashSet<>();

    private static boolean configValidated = false;

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        // validateOrReset detects corruption via sentinel reads, but its file cleanup
        // currently targets -client.toml; this mod uses COMMON (-common.toml).
        // Sentinel protection still works, SafeConfig wrappers handle the rest.
        if (!configValidated && ExcavateConfig.SPEC.isLoaded()) {
            configValidated = true;
            SafeConfig.validateOrReset(ExcavateMod.MOD_ID, ExcavateConfig.SPEC,
                    ExcavateConfig.AUTO_REPLANT);
        }
    }

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        // only track on server side to avoid concurrent writes from client thread
        if (event.getLevel().isClientSide()) return;
        if (event.getFace() != null) {
            clickedFace.put(event.getEntity().getUUID(), event.getFace());
        }
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        clickedFace.remove(id);
        excavatingPlayers.remove(id);
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUUID();

        if (excavatingPlayers.contains(playerId)) return;
        if (player.isCrouching()) return;

        Level level = player.level();
        if (!(level instanceof ServerLevel serverLevel)) return;

        ItemStack tool = player.getMainHandItem();
        int enchantLevel = ExcavateMod.getExcavationLevel(level, player);
        if (enchantLevel <= 0) return;

        Direction face = clickedFace.get(playerId);
        if (face == null) return;

        BlockPos origin = event.getPos();
        BlockState originState = serverLevel.getBlockState(origin);

        // crop harvesting -- cancel the event so we handle the origin block too
        // (otherwise the origin crop would not be replanted when auto-replant is on)
        if (originState.getBlock() instanceof CropBlock originCrop && originCrop.isMaxAge(originState)) {
            event.setCanceled(true);
            excavatingPlayers.add(playerId);
            try {
                harvestCrops(serverLevel, player, tool, origin, enchantLevel);
            } finally {
                excavatingPlayers.remove(playerId);
            }
            return;
        }

        // normal excavation -- tool must be correct for the targeted block
        if (!tool.isCorrectToolForDrops(originState)) return;

        excavatingPlayers.add(playerId);
        try {
            breakArea(serverLevel, player, tool, origin, face, enchantLevel);
        } finally {
            excavatingPlayers.remove(playerId);
        }
    }

    private static void breakArea(
            ServerLevel level, Player player, ItemStack tool,
            BlockPos origin, Direction face, int enchantLevel) {

        Direction.Axis faceAxis = face.getAxis();

        for (int a = -enchantLevel; a <= enchantLevel; a++) {
            for (int b = -enchantLevel; b <= enchantLevel; b++) {
                if (a == 0 && b == 0) continue;

                BlockPos target = offsetFromFace(origin, faceAxis, a, b);
                BlockState state = level.getBlockState(target);

                if (state.isAir()) continue;

                float hardness = state.getDestroySpeed(level, target);
                if (hardness < 0) continue;

                if (!tool.isCorrectToolForDrops(state)) continue;

                Block.dropResources(state, level, target, level.getBlockEntity(target), player, tool);
                level.destroyBlock(target, false);

                if (player instanceof ServerPlayer serverPlayer) {
                    tool.hurtAndBreak(1, serverPlayer, serverPlayer.getEquipmentSlotForItem(tool));
                }

                if (tool.isEmpty()) return;
            }
        }
    }

    /**
     * Harvests mature crops in a horizontal area including the origin.
     * Always expands on the X/Z plane since crops sit on flat farmland.
     * The BreakEvent is cancelled so this method handles the origin block too,
     * ensuring it gets replanted when auto-replant is enabled.
     * Consumes one seed per crop from the drops to replant.
     */
    private static void harvestCrops(
            ServerLevel level, Player player, ItemStack tool,
            BlockPos origin, int enchantLevel) {

        boolean replant = SafeConfig.getBool(ExcavateConfig.AUTO_REPLANT, false);

        for (int dx = -enchantLevel; dx <= enchantLevel; dx++) {
            for (int dz = -enchantLevel; dz <= enchantLevel; dz++) {
                BlockPos target = origin.offset(dx, 0, dz);
                BlockState state = level.getBlockState(target);

                if (!(state.getBlock() instanceof CropBlock crop)) continue;
                if (!crop.isMaxAge(state)) continue;

                // collect drops and seed type before destroying the block
                Item seedType = crop.getCloneItemStack(level, target, state).getItem();
                List<ItemStack> drops = Block.getDrops(state, level, target,
                        level.getBlockEntity(target), player, tool);
                level.destroyBlock(target, false);

                // replant by consuming one seed from the drops
                if (replant) {
                    boolean seeded = false;
                    for (ItemStack drop : drops) {
                        if (drop.is(seedType)) {
                            drop.shrink(1);
                            seeded = true;
                            break;
                        }
                    }
                    if (seeded) {
                        level.setBlock(target, crop.getStateForAge(0), Block.UPDATE_ALL);
                    }
                }

                // drop remaining items
                for (ItemStack drop : drops) {
                    if (!drop.isEmpty()) {
                        Block.popResource(level, target, drop);
                    }
                }

                if (player instanceof ServerPlayer serverPlayer) {
                    tool.hurtAndBreak(1, serverPlayer, serverPlayer.getEquipmentSlotForItem(tool));
                }

                if (tool.isEmpty()) return;
            }
        }
    }

    /**
     * Offsets a position along the two axes perpendicular to the mined face.
     * If looking at a NORTH/SOUTH face, expands along X and Y.
     */
    static BlockPos offsetFromFace(BlockPos origin, Direction.Axis faceAxis, int a, int b) {
        return switch (faceAxis) {
            case X -> origin.offset(0, a, b);
            case Y -> origin.offset(a, 0, b);
            case Z -> origin.offset(a, b, 0);
        };
    }
}
