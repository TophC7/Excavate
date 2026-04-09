package dev.excavate;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import xyz.kwahson.core.config.SafeConfig;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderHighlightEvent;

@EventBusSubscriber(modid = ExcavateMod.MOD_ID, value = Dist.CLIENT)
public class ExcavationHighlightRenderer {

    @SubscribeEvent
    public static void onRenderHighlight(RenderHighlightEvent.Block event) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mc.level == null) return;

        if (player.isCrouching()) return;
        if (!SafeConfig.getBool(ExcavateConfig.SHOW_HIGHLIGHT, true)) return;

        ItemStack tool = player.getMainHandItem();
        int enchantLevel = ExcavateMod.getExcavationLevel(player.level(), tool);
        if (enchantLevel <= 0) return;

        BlockPos origin = event.getTarget().getBlockPos();
        BlockState targetState = mc.level.getBlockState(origin);
        Direction face = event.getTarget().getDirection();
        Vec3 localHit = new Vec3(
                event.getTarget().getLocation().x - origin.getX(),
                event.getTarget().getLocation().y - origin.getY(),
                event.getTarget().getLocation().z - origin.getZ()
        );

        boolean isCrop = targetState.getBlock() instanceof CropBlock crop && crop.isMaxAge(targetState);
        boolean isAreaUse = ExcavationHandler.canAreaUse(
                mc.level, origin, player, InteractionHand.MAIN_HAND, tool, face, localHit, event.getTarget().isInside());
        Direction.Axis areaUseAxis = isAreaUse
                ? ExcavationHandler.getAreaUseAxis(
                        mc.level, origin, player, InteractionHand.MAIN_HAND, tool, face, localHit, event.getTarget().isInside())
                : null;

        if (!isCrop && !isAreaUse && !tool.isCorrectToolForDrops(targetState)) return;

        Direction.Axis axis = isCrop ? Direction.Axis.Y : (isAreaUse ? areaUseAxis : face.getAxis());

        Camera camera = event.getCamera();
        Vec3 cam = camera.getPosition();
        PoseStack poseStack = event.getPoseStack();
        VertexConsumer buffer = event.getMultiBufferSource().getBuffer(RenderType.lines());

        poseStack.pushPose();

        // highlight the origin block
        renderBlockOutline(poseStack, buffer, origin, cam);

        // highlight each surrounding block that would actually be affected
        for (int a = -enchantLevel; a <= enchantLevel; a++) {
            for (int b = -enchantLevel; b <= enchantLevel; b++) {
                if (a == 0 && b == 0) continue;

                BlockPos target = isCrop
                        ? origin.offset(a, 0, b)
                        : ExcavationHandler.offsetFromFace(origin, axis, a, b);
                if (isCrop) {
                    if (!ExcavationHandler.canAreaHarvest(mc.level, target)) continue;
                } else if (isAreaUse) {
                    if (!ExcavationHandler.canAreaUse(
                            mc.level, target, player, InteractionHand.MAIN_HAND, tool, face, localHit, event.getTarget().isInside())) {
                        continue;
                    }
                } else {
                    if (!ExcavationHandler.canAreaBreak(mc.level, target, tool)) continue;
                }

                renderBlockOutline(poseStack, buffer, target, cam);
            }
        }

        poseStack.popPose();
        event.setCanceled(true);
    }

    private static void renderBlockOutline(
            PoseStack poseStack, VertexConsumer buffer, BlockPos pos, Vec3 cam) {
        LevelRenderer.renderLineBox(
                poseStack, buffer,
                pos.getX() - cam.x, pos.getY() - cam.y, pos.getZ() - cam.z,
                pos.getX() + 1 - cam.x, pos.getY() + 1 - cam.y, pos.getZ() + 1 - cam.z,
                1.0F, 1.0F, 1.0F, 0.6F
        );
    }
}
