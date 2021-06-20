package com.raoulvdberge.refinedstorage.apiimpl.network.readerwriter;

import com.raoulvdberge.refinedstorage.api.network.INetwork;
import com.raoulvdberge.refinedstorage.api.network.node.INetworkNode;
import com.raoulvdberge.refinedstorage.api.network.readerwriter.*;
import com.raoulvdberge.refinedstorage.apiimpl.API;
import net.minecraft.nbt.NBTTagCompound;

import java.util.*;
import java.util.stream.Collectors;

public class ReaderWriterChannel implements IReaderWriterChannel {

    private static final String NBT_HANDLER = "Handler_%s";

    private final String name;
    private final INetwork network;

    private final List<IWriter> cachedWriters = new ArrayList<>();
    private final List<IReader> cachedReaders = new ArrayList<>();

    private final List<IReaderWriterHandler> handlers = new ArrayList<>();

    public ReaderWriterChannel(String name, INetwork network) {
        this.name = name;
        this.network = network;
        this.handlers.addAll(API.instance().getReaderWriterHandlerRegistry().all().stream().map(f -> f.create(null)).collect(Collectors.toList()));
        this.network.getNodeGraph().addListener(() -> {
            this.cachedReaders.clear();
            this.cachedWriters.clear();

            for (INetworkNode node : network.getNodeGraph().allActualNodes()) {
                if (node instanceof IReader && name.equals(((IReader) node).getChannel())) {
                    this.cachedReaders.add((IReader) node);
                } else if (node instanceof IWriter && name.equals(((IWriter) node).getChannel())) {
                    this.cachedWriters.add((IWriter) node);
                }
            }
        });
    }

    @Override
    public List<IReaderWriterHandler> getHandlers() {
        return handlers;
    }

    @Override
    public void onNodeAdded(INetworkNode node) {
        if (node instanceof IReader) {
            if (!this.cachedReaders.contains(node))
                this.cachedReaders.add((IReader) node);
        } else if (node instanceof IWriter) {
            if (!this.cachedWriters.contains(node))
                this.cachedWriters.add((IWriter) node);
        } else {
            throw new IllegalArgumentException("Not a reader or writer");
        }
    }

    @Override
    public void onNodeRemoved(INetworkNode node) {
        if (node instanceof IReader) {
            this.cachedReaders.remove(node);
        } else if (node instanceof IWriter) {
            this.cachedWriters.remove(node);
        } else {
            throw new IllegalArgumentException("Not a reader or writer");
        }
    }

    @Override
    public void invalidate() {
        new ArrayList<>(this.cachedWriters).forEach(w -> w.setChannel(""));
        new ArrayList<>(this.cachedReaders).forEach(r -> r.setChannel(""));
    }

    @Override
    public List<IReader> getReaders() {
        return this.cachedReaders;
    }

    @Override
    public List<IWriter> getWriters() {
        return this.cachedWriters;
    }

    @Override
    public NBTTagCompound writeToNbt(NBTTagCompound tag) {
        for (IReaderWriterHandler handler : handlers) {
            tag.setTag(String.format(NBT_HANDLER, handler.getId()), handler.writeToNbt(new NBTTagCompound()));
        }

        return tag;
    }

    @Override
    public void readFromNbt(NBTTagCompound tag) {
        for (IReaderWriterHandler handler : handlers) {
            String id = String.format(NBT_HANDLER, handler.getId());

            if (tag.hasKey(id)) {
                IReaderWriterHandlerFactory factory = API.instance().getReaderWriterHandlerRegistry().get(id);

                if (factory != null) {
                    handlers.add(factory.create(tag.getCompoundTag(id)));
                }
            }
        }
    }
}
