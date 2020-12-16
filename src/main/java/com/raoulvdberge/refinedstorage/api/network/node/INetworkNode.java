package com.raoulvdberge.refinedstorage.api.network.node;

import com.raoulvdberge.refinedstorage.api.network.INetwork;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Represents a node in the network.
 */
public interface INetworkNode {
    /**
     * @return the energy usage of this node
     */
    int getEnergyUsage();

    /**
     * Returns the stack that is displayed in the controller GUI.
     * Can be an empty stack if no stack should be shown.
     *
     * @return the item stack of this node
     */
    @Nonnull
    ItemStack getItemStack();

    /**
     * Called when this node is connected to a network.
     *
     * @param network the network
     */
    void onConnected(INetwork network);

    /**
     * Called when this node is disconnected from a network.
     *
     * @param network the network
     */
    void onDisconnected(INetwork network);

    /**
     * If a node can be updated typically depends on the redstone configuration.
     *
     * @return true if this node can be treated as updatable, false otherwise
     */
    boolean canUpdate();

    /**
     * Whether or not this node is active independent of the network
     * @return true if it enabled; false otherwise
     */
    default boolean isEnabled() {
        return true;
    }

    /**
     * @return the network, or null if this node is not connected to any network
     */
    @Nullable
    INetwork getNetwork();

    /*
    Disgusting code to keep this compatible with old code that still overrides the old methods. In those case the new
    methods just call the old methods. In all other cases the new methods should be overridden
     */

    default void update() {
        throw new RuntimeException("Override #updateNetworkNode");
    }

    default BlockPos getPos() {
        throw new RuntimeException("Override #getNetworkNodePos");
    }

    default World getWorld() {
        throw new RuntimeException("Override #getNetworkNodeWorld");
    }

    default void markDirty() {
        throw new RuntimeException("Override #markNetworkNodeDirty");
    }

    /**
     * Updates a network node.
     */
    default void updateNetworkNode() {
        update();
    }

    /**
     * @return the position of this network node
     */
    default BlockPos getNetworkNodePos() {
        return getPos();
    }

    /**
     * @return the world of this network node
     */
    default World getNetworkNodeWorld() {
        return getWorld();
    }

    /**
     * Marks this node as dirty for saving.
     */
    default void markNetworkNodeDirty() {
        markDirty();
    }

    /**
     * Writes the network node data to NBT.
     *
     * @param tag the tag
     * @return the written tag
     */
    NBTTagCompound write(NBTTagCompound tag);

    /**
     * @return the id of this node as specified in {@link INetworkNodeRegistry}
     */
    String getId();

    /**
     * @return whether or not this network node's {@link #updateNetworkNode()} method should be called every tick
     */
    default boolean isTickable() {
        return true;
    }
}
