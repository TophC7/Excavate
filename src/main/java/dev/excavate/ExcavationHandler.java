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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.player.UseItemOnBlockEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import xyz.kwahson.core.config.SafeConfig;

@EventBusSubscriber(modid = ExcavateMod.MOD_ID)
public class ExcavationHandler {
    private record AreaUseData(BlockState modifiedState, Direction.Axis axis, Integer levelEvent) {}

    // Both maps are only accessed from the server main thread
    // (LeftClickBlock, BreakEvent, ServerTick, PlayerLoggedOut all fire there).
    private static final HashMap<UUID, Direction> clickedFace = new HashMap<>();
    private static final Set<UUID> excavatingPlayers = new HashSet<>();
    private static final ThreadLocal<Integer> useOnReentryDepth = ThreadLocal.withInitial(() -> 0);

    private static boolean configValidated = false;

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (!configValidated && ExcavateConfig.SPEC.isLoaded()) {
            configValidated = true;
            SafeConfig.validateOrReset(ExcavateMod.MOD_ID, ExcavateConfig.SPEC,
                    "common",
                    ExcavateConfig.AUTO_REPLANT,
                    ExcavateConfig.AREA_HOE_TILLING,
                    ExcavateConfig.AREA_SHOVEL_PATHING,
                    ExcavateConfig.AREA_AXE_ACTIONS);
        }
    }

    // reset so config is re-validated on next server start within the same JVM
    // (singleplayer: quit world -> new world without restarting)
    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        configValidated = false;
        clickedFace.clear();
        excavatingPlayers.clear();
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
    public static void onUseItemOnBlock(UseItemOnBlockEvent event) {
        if (event.getUsePhase() != UseItemOnBlockEvent.UsePhase.ITEM_AFTER_BLOCK) return;
        if (useOnReentryDepth.get() > 0) return;

        Player player = event.getPlayer();
        if (player == null || player.isCrouching()) return;

        Level level = event.getLevel();
        ItemStack tool = event.getItemStack();
        int enchantLevel = ExcavateMod.getExcavationLevel(level, tool);
        if (enchantLevel <= 0) return;

        Direction face = event.getFace();
        if (face == null) return;

        BlockPos origin = event.getPos();
        Vec3 localHit = getLocalHit(event.getUseOnContext().getClickLocation(), origin);
        AreaUseData originUse = getAreaUse(level, origin, player, event.getHand(), tool, face, localHit,
                event.getUseOnContext().isInside(), true);
        if (originUse == null) return;

        InteractionResult originResult = runVanillaUseOn(tool, event.getUseOnContext());
        event.cancelWithResult(toItemInteractionResult(originResult));

        if (!originResult.consumesAction()) return;
        if (!(level instanceof ServerLevel serverLevel) || tool.isEmpty()) return;

        applyAreaUse(serverLevel, player, event.getHand(), tool, origin, face, localHit,
                event.getUseOnContext().isInside(), enchantLevel, originUse.axis());
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
        int enchantLevel = ExcavateMod.getExcavationLevel(level, tool);
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

                if (!canAreaBreak(level, target, tool)) continue;

                BlockState state = level.getBlockState(target);
                Block.dropResources(state, level, target, level.getBlockEntity(target), player, tool);
                level.destroyBlock(target, false);

                if (player instanceof ServerPlayer serverPlayer) {
                    tool.hurtAndBreak(1, serverPlayer, serverPlayer.getEquipmentSlotForItem(tool));
                }

                if (tool.isEmpty()) return;
            }
        }
    }

    private static void applyAreaUse(
            ServerLevel level, Player player, InteractionHand hand, ItemStack tool,
            BlockPos origin, Direction face, Vec3 localHit, boolean inside,
            int enchantLevel, Direction.Axis axis) {

        for (int a = -enchantLevel; a <= enchantLevel; a++) {
            for (int b = -enchantLevel; b <= enchantLevel; b++) {
                if (a == 0 && b == 0) continue;

                BlockPos target = offsetFromFace(origin, axis, a, b);
                if (!applyAreaUseAt(level, player, hand, tool, target, face, localHit, inside)) continue;

                tool.hurtAndBreak(1, player, LivingEntity.getSlotForHand(hand));
                if (tool.isEmpty()) return;
            }
        }
    }

    private static boolean applyAreaUseAt(
            ServerLevel level, Player player, InteractionHand hand, ItemStack tool,
            BlockPos target, Direction face, Vec3 localHit, boolean inside) {

        AreaUseData areaUse = getAreaUse(level, target, player, hand, tool, face, localHit, inside, false);
        if (areaUse == null) return false;

        level.setBlock(target, areaUse.modifiedState(), 11);
        level.gameEvent(GameEvent.BLOCK_CHANGE, target, GameEvent.Context.of(player, areaUse.modifiedState()));
        if (areaUse.levelEvent() != null) {
            level.levelEvent(player, areaUse.levelEvent(), target, 0);
        }
        return true;
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

                if (!canAreaHarvest(level, target)) continue;

                BlockState state = level.getBlockState(target);
                CropBlock crop = (CropBlock) state.getBlock();

                // collect drops and seed type before destroying the block
                ItemStack seedStack = crop.getCloneItemStack(level, target, state);
                List<ItemStack> drops = Block.getDrops(state, level, target,
                        level.getBlockEntity(target), player, tool);
                level.destroyBlock(target, false);

                // replant by consuming one seed from the drops
                if (replant && !seedStack.isEmpty()) {
                    Item seedType = seedStack.getItem();
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
     * Whether a block at the given position would be area-mined.
     * Used by both the server handler and client highlight renderer.
     */
    static boolean canAreaBreak(Level level, BlockPos target, ItemStack tool) {
        BlockState state = level.getBlockState(target);
        if (state.isAir()) return false;
        if (state.getDestroySpeed(level, target) < 0) return false;
        return tool.isCorrectToolForDrops(state);
    }

    /**
     * Whether a crop at the given position would be area-harvested.
     * Used by both the server handler and client highlight renderer.
     */
    static boolean canAreaHarvest(Level level, BlockPos target) {
        BlockState state = level.getBlockState(target);
        return state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state);
    }

    static boolean canAreaUse(
            Level level, BlockPos target, Player player, InteractionHand hand, ItemStack tool,
            Direction face, Vec3 localHit, boolean inside) {

        return getAreaUse(level, target, player, hand, tool, face, localHit, inside, true) != null;
    }

    static Direction.Axis getAreaUseAxis(
            Level level, BlockPos target, Player player, InteractionHand hand, ItemStack tool,
            Direction face, Vec3 localHit, boolean inside) {

        AreaUseData areaUse = getAreaUse(level, target, player, hand, tool, face, localHit, inside, true);
        return areaUse != null ? areaUse.axis() : null;
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

    private static AreaUseData getAreaUse(
            Level level, BlockPos target, Player player, InteractionHand hand, ItemStack tool,
            Direction face, Vec3 localHit, boolean inside, boolean simulate) {

        UseOnContext context = createAreaUseContext(level, player, hand, tool, target, face, localHit, inside);
        BlockState state = level.getBlockState(target);

        if (context.getItemInHand().getItem() instanceof ShovelItem) {
            if (!SafeConfig.getBool(ExcavateConfig.AREA_SHOVEL_PATHING, true)) return null;
            if (context.getClickedFace() == Direction.DOWN) return null;

            BlockState flattened = state.getToolModifiedState(
                    context, net.neoforged.neoforge.common.ItemAbilities.SHOVEL_FLATTEN, simulate);
            if (flattened == null || !level.getBlockState(target.above()).isAir()) return null;
            return new AreaUseData(flattened, Direction.Axis.Y, null);
        }

        if (context.getItemInHand().getItem() instanceof HoeItem) {
            if (!SafeConfig.getBool(ExcavateConfig.AREA_HOE_TILLING, true)) return null;
            BlockState tilled = state.getToolModifiedState(
                    context, net.neoforged.neoforge.common.ItemAbilities.HOE_TILL, simulate);
            return tilled != null
                    ? new AreaUseData(tilled, Direction.Axis.Y, null)
                    : null;
        }

        if (context.getItemInHand().getItem() instanceof AxeItem) {
            if (!SafeConfig.getBool(ExcavateConfig.AREA_AXE_ACTIONS, true)) return null;
            if (playerHasShieldUseIntent(player, hand)) return null;

            BlockState stripped = state.getToolModifiedState(
                    context, net.neoforged.neoforge.common.ItemAbilities.AXE_STRIP, simulate);
            if (stripped != null) {
                return new AreaUseData(stripped, face.getAxis(), null);
            }

            BlockState scraped = state.getToolModifiedState(
                    context, net.neoforged.neoforge.common.ItemAbilities.AXE_SCRAPE, simulate);
            if (scraped != null) {
                return new AreaUseData(scraped, face.getAxis(), 3005);
            }

            BlockState waxOff = state.getToolModifiedState(
                    context, net.neoforged.neoforge.common.ItemAbilities.AXE_WAX_OFF, simulate);
            if (waxOff != null) {
                return new AreaUseData(waxOff, face.getAxis(), 3004);
            }
        }

        return null;
    }

    private static InteractionResult runVanillaUseOn(ItemStack tool, UseOnContext context) {
        useOnReentryDepth.set(useOnReentryDepth.get() + 1);
        try {
            return tool.useOn(context);
        } finally {
            useOnReentryDepth.set(useOnReentryDepth.get() - 1);
        }
    }

    private static ItemInteractionResult toItemInteractionResult(InteractionResult result) {
        return switch (result) {
            case SUCCESS, SUCCESS_NO_ITEM_USED -> ItemInteractionResult.SUCCESS;
            case CONSUME -> ItemInteractionResult.CONSUME;
            case CONSUME_PARTIAL -> ItemInteractionResult.CONSUME_PARTIAL;
            case PASS -> ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
            case FAIL -> ItemInteractionResult.FAIL;
        };
    }

    private static boolean playerHasShieldUseIntent(Player player, InteractionHand hand) {
        return hand == InteractionHand.MAIN_HAND
                && player.getOffhandItem().is(Items.SHIELD)
                && !player.isSecondaryUseActive();
    }

    private static UseOnContext createAreaUseContext(
            Level level, Player player, InteractionHand hand, ItemStack tool,
            BlockPos target, Direction face, Vec3 localHit, boolean inside) {

        Vec3 clickLocation = new Vec3(
                target.getX() + localHit.x,
                target.getY() + localHit.y,
                target.getZ() + localHit.z
        );
        BlockHitResult hit = new BlockHitResult(clickLocation, face, target, inside);
        return new UseOnContext(level, player, hand, tool, hit);
    }

    private static Vec3 getLocalHit(Vec3 hitLocation, BlockPos origin) {
        return new Vec3(
                hitLocation.x - origin.getX(),
                hitLocation.y - origin.getY(),
                hitLocation.z - origin.getZ()
        );
    }
}
