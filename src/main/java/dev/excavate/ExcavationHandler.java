package dev.excavate;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

@EventBusSubscriber(modid = ExcavateMod.MOD_ID)
public class ExcavationHandler {

    // tracks which block face the player clicked so we know what plane to expand
    private static final HashMap<UUID, Direction> clickedFace = new HashMap<>();

    // per-player recursion guard so simultaneous players don't block each other
    private static final Set<UUID> excavatingPlayers = new HashSet<>();

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
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

        // only excavate if the tool is correct for the block we actually targeted
        BlockState originState = serverLevel.getBlockState(origin);
        if (!tool.isCorrectToolForDrops(originState)) return;

        int radius = enchantLevel;

        excavatingPlayers.add(playerId);
        try {
            breakArea(serverLevel, player, tool, origin, face, radius);
        } finally {
            excavatingPlayers.remove(playerId);
        }
    }

    private static void breakArea(
            ServerLevel level, Player player, ItemStack tool,
            BlockPos origin, Direction face, int radius) {

        Direction.Axis faceAxis = face.getAxis();

        for (int a = -radius; a <= radius; a++) {
            for (int b = -radius; b <= radius; b++) {
                if (a == 0 && b == 0) continue;

                BlockPos target = offsetFromFace(origin, faceAxis, a, b);
                BlockState state = level.getBlockState(target);

                if (state.isAir()) continue;

                float hardness = state.getDestroySpeed(level, target);
                if (hardness < 0) continue;

                if (!tool.isCorrectToolForDrops(state)) continue;

                // drop each block's OWN loot
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
     * Offsets a position along the two axes perpendicular to the mined face
     * aka if we're looking at a NORTH/SOUTH face, we expand along X and Y
     */
    static BlockPos offsetFromFace(BlockPos origin, Direction.Axis faceAxis, int a, int b) {
        return switch (faceAxis) {
            case X -> origin.offset(0, a, b);
            case Y -> origin.offset(a, 0, b);
            case Z -> origin.offset(a, b, 0);
        };
    }
}
