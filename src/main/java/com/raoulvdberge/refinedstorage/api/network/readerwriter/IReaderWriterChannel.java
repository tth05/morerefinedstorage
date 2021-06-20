package com.raoulvdberge.refinedstorage.api.network.readerwriter;

import com.raoulvdberge.refinedstorage.api.network.node.INetworkNode;
import net.minecraft.nbt.NBTTagCompound;

import java.util.List;

/**
 * Represents a reader writer channel.
 */
public interface IReaderWriterChannel {
    /**
     * @return the handlers that this channel has
     */
    List<IReaderWriterHandler> getHandlers();

    /**
     * @return a list of readers using this channel
     */
    List<IReader> getReaders();

    /**
     * @return a list of writers using this channel
     */
    List<IWriter> getWriters();

    /**
     * Called when a node is added to this channel
     */
    void onNodeAdded(INetworkNode node);

    /**
     * Called when a node is removed from this channel
     */
    void onNodeRemoved(INetworkNode node);

    void invalidate();

    /**
     * Writes this channel to NBT.
     *
     * @param tag the tag to write to
     * @return the written tag
     */
    NBTTagCompound writeToNbt(NBTTagCompound tag);

    /**
     * Reads this channel from NBT.
     *
     * @param tag the tag to read from
     */
    void readFromNbt(NBTTagCompound tag);
}
