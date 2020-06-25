package com.raoulvdberge.refinedstorage.render.collision;

import com.raoulvdberge.refinedstorage.block.BlockBase;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraftforge.client.event.DrawBlockHighlightEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

public class BlockHighlightListener {
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onBlockDrawHighlight(DrawBlockHighlightEvent e) {
        //noinspection ConstantConditions
        if (e.getTarget() == null || e.getTarget().getBlockPos() == null) {
            return;
        }

        EntityPlayer player = e.getPlayer();

        BlockPos pos = e.getTarget().getBlockPos();

        Block b = player.getEntityWorld().getBlockState(pos).getBlock();

        if (!(b instanceof BlockBase)) {
            return;
        }

        BlockBase block = (BlockBase) b;

        IBlockState state = block.getActualState(block.getDefaultState(), player.getEntityWorld(), pos);

        AdvancedRayTraceResult<RayTraceResult> result = AdvancedRayTracer.rayTrace(
            pos,
            AdvancedRayTracer.getStart(player),
            AdvancedRayTracer.getEnd(player),
            block.getCollisions(player.getEntityWorld().getTileEntity(pos), state)
        );

        e.setCanceled(true);

        if (result == null) {
            return;
        }

        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.color(0.0F, 0.0F, 0.0F, 0.4F);
        GlStateManager.glLineWidth(3.0F);
        GlStateManager.disableTexture2D();
        GlStateManager.depthMask(false);

        double d0 = player.lastTickPosX + (player.posX - player.lastTickPosX) * (double) e.getPartialTicks();
        double d1 = player.lastTickPosY + (player.posY - player.lastTickPosY) * (double) e.getPartialTicks();
        double d2 = player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * (double) e.getPartialTicks();

        for (AxisAlignedBB aabb : result.getGroup().getItems()) {
            drawSelectionBoundingBox(aabb.expand(0.0020000000949949026D, 0.0020000000949949026D, 0.0020000000949949026D).offset(-d0, -d1, -d2).offset(pos.getX(), pos.getY(), pos.getZ()));
        }

        GlStateManager.depthMask(true);
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
    }

    private void drawSelectionBoundingBox(AxisAlignedBB aabb) {
        Tessellator tessellator = Tessellator.getInstance();

        BufferBuilder buffer = tessellator.getBuffer();

        buffer.begin(3, DefaultVertexFormats.POSITION);
        buffer.pos(aabb.minX, aabb.minY, aabb.minZ).endVertex();
        buffer.pos(aabb.maxX, aabb.minY, aabb.minZ).endVertex();
        buffer.pos(aabb.maxX, aabb.minY, aabb.maxZ).endVertex();
        buffer.pos(aabb.minX, aabb.minY, aabb.maxZ).endVertex();
        buffer.pos(aabb.minX, aabb.minY, aabb.minZ).endVertex();

        tessellator.draw();

        buffer.begin(3, DefaultVertexFormats.POSITION);
        buffer.pos(aabb.minX, aabb.maxY, aabb.minZ).endVertex();
        buffer.pos(aabb.maxX, aabb.maxY, aabb.minZ).endVertex();
        buffer.pos(aabb.maxX, aabb.maxY, aabb.maxZ).endVertex();
        buffer.pos(aabb.minX, aabb.maxY, aabb.maxZ).endVertex();
        buffer.pos(aabb.minX, aabb.maxY, aabb.minZ).endVertex();

        tessellator.draw();

        buffer.begin(1, DefaultVertexFormats.POSITION);
        buffer.pos(aabb.minX, aabb.minY, aabb.minZ).endVertex();
        buffer.pos(aabb.minX, aabb.maxY, aabb.minZ).endVertex();
        buffer.pos(aabb.maxX, aabb.minY, aabb.minZ).endVertex();
        buffer.pos(aabb.maxX, aabb.maxY, aabb.minZ).endVertex();
        buffer.pos(aabb.maxX, aabb.minY, aabb.maxZ).endVertex();
        buffer.pos(aabb.maxX, aabb.maxY, aabb.maxZ).endVertex();
        buffer.pos(aabb.minX, aabb.minY, aabb.maxZ).endVertex();
        buffer.pos(aabb.minX, aabb.maxY, aabb.maxZ).endVertex();

        tessellator.draw();
    }
}
