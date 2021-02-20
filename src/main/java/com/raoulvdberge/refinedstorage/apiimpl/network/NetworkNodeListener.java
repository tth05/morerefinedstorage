package com.raoulvdberge.refinedstorage.apiimpl.network;

import com.raoulvdberge.refinedstorage.api.network.node.INetworkNode;
import com.raoulvdberge.refinedstorage.api.network.node.INetworkNodeProxy;
import com.raoulvdberge.refinedstorage.api.network.security.Permission;
import com.raoulvdberge.refinedstorage.apiimpl.API;
import com.raoulvdberge.refinedstorage.capability.CapabilityNetworkNodeProxy;
import com.raoulvdberge.refinedstorage.util.WorldUtils;
import java.util.Iterator;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.event.world.BlockEvent.BreakEvent;
import net.minecraftforge.event.world.BlockEvent.EntityPlaceEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.Phase;
import net.minecraftforge.fml.common.gameevent.TickEvent.WorldTickEvent;

public class NetworkNodeListener {


    @SubscribeEvent
    public void onWorldTick(WorldTickEvent e) {
        if (!e.world.isRemote && e.phase == Phase.END) {
            e.world.profiler.startSection("network node ticking");

            for (INetworkNode node : API.instance().getNetworkNodeManager(e.world).allTickable()) {
                node.updateNetworkNode();
            }

            e.world.profiler.endSection();
        }
    }

    @SubscribeEvent
    public void onBlockPlace(EntityPlaceEvent e) {
        if (!e.getWorld().isRemote && e.getEntity() instanceof EntityPlayer) {
            TileEntity placed = e.getWorld().getTileEntity(e.getPos());
            if (placed != null && placed.hasCapability(CapabilityNetworkNodeProxy.NETWORK_NODE_PROXY_CAPABILITY, null)) {
                EnumFacing[] var3 = EnumFacing.VALUES;

                for (EnumFacing facing : var3) {
                    TileEntity side = e.getWorld().getTileEntity(e.getBlockSnapshot().getPos().offset(facing));
                    if (side != null && side.hasCapability(CapabilityNetworkNodeProxy.NETWORK_NODE_PROXY_CAPABILITY, facing.getOpposite())) {
                        INetworkNodeProxy<?> nodeProxy = (INetworkNodeProxy) side.getCapability(CapabilityNetworkNodeProxy.NETWORK_NODE_PROXY_CAPABILITY, facing.getOpposite());
                        INetworkNode node = nodeProxy.getNode();
                        if (node.getNetwork() != null && !node.getNetwork().getSecurityManager().hasPermission(Permission.BUILD, (EntityPlayer) e.getEntity())) {
                            WorldUtils.sendNoPermissionMessage((EntityPlayer) e.getEntity());
                            e.setCanceled(true);
                            return;
                        }
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public void onBlockBreak(BreakEvent e) {
        if (!e.getWorld().isRemote) {
            TileEntity tile = e.getWorld().getTileEntity(e.getPos());
            if (tile != null && tile.hasCapability(CapabilityNetworkNodeProxy.NETWORK_NODE_PROXY_CAPABILITY, null)) {
                INetworkNodeProxy<?> nodeProxy = (INetworkNodeProxy)tile.getCapability(CapabilityNetworkNodeProxy.NETWORK_NODE_PROXY_CAPABILITY, null);
                INetworkNode node = nodeProxy.getNode();
                if (node.getNetwork() != null && !node.getNetwork().getSecurityManager().hasPermission(Permission.BUILD, e.getPlayer())) {
                    WorldUtils.sendNoPermissionMessage(e.getPlayer());
                    e.setCanceled(true);
                }
            }
        }
    }
}
