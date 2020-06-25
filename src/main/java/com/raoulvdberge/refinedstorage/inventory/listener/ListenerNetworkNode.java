package com.raoulvdberge.refinedstorage.inventory.listener;

import com.raoulvdberge.refinedstorage.api.network.node.INetworkNode;

import java.util.function.IntConsumer;

public class ListenerNetworkNode implements IntConsumer {
    private final INetworkNode node;

    public ListenerNetworkNode(INetworkNode node) {
        this.node = node;
    }

    @Override
    public void accept(int slot) {
        node.markDirty();
    }
}
