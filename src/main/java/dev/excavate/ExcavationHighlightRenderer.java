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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
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
        if (player == null) return;

        if (player.isCrouching()) return;

        int level = ExcavateMod.getExcavationLevel(player.level(), player);
        if (level <= 0) return;

        // only show the area highlight if the tool can mine the targeted block
        BlockPos origin = event.getTarget().getBlockPos();
        BlockState targetState = mc.level.getBlockState(origin);
        ItemStack tool = player.getMainHandItem();
        if (!tool.isCorrectToolForDrops(targetState)) return;
        Direction face = event.getTarget().getDirection();
        int radius = level;

        AABB area = buildAreaBox(origin, face.getAxis(), radius);

        Camera camera = event.getCamera();
        Vec3 cam = camera.getPosition();

        PoseStack poseStack = event.getPoseStack();
        VertexConsumer buffer = event.getMultiBufferSource().getBuffer(RenderType.lines());

        poseStack.pushPose();
        LevelRenderer.renderLineBox(
                poseStack, buffer,
                area.minX - cam.x, area.minY - cam.y, area.minZ - cam.z,
                area.maxX - cam.x, area.maxY - cam.y, area.maxZ - cam.z,
                1.0F, 1.0F, 1.0F, 0.6F
        );
        poseStack.popPose();

        event.setCanceled(true);
    }

    /**
     * Builds an AABB covering the full mining area
     * 1 block deep along the face axis, (2*radius+1) wide on each perpendicular axis
     */
    private static AABB buildAreaBox(BlockPos origin, Direction.Axis faceAxis, int radius) {
        int ox = origin.getX(), oy = origin.getY(), oz = origin.getZ();

        return switch (faceAxis) {
            case X -> new AABB(ox, oy - radius, oz - radius, ox + 1, oy + radius + 1, oz + radius + 1);
            case Y -> new AABB(ox - radius, oy, oz - radius, ox + radius + 1, oy + 1, oz + radius + 1);
            case Z -> new AABB(ox - radius, oy - radius, oz, ox + radius + 1, oy + radius + 1, oz + 1);
        };
    }
}
