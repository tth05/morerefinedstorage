package com.raoulvdberge.refinedstorage.apiimpl.network.node;

import com.raoulvdberge.refinedstorage.api.network.node.INetworkNode;

import java.util.Objects;

public class SlottedCraftingRequest {
    private final INetworkNode node;
    private final int slot;

    public SlottedCraftingRequest(INetworkNode node, int slot) {
        this.node = node;
        this.slot = slot;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        SlottedCraftingRequest that = (SlottedCraftingRequest) o;

        return slot == that.slot && Objects.equals(node, that.node);
    }

    @Override
    public int hashCode() {
        return Objects.hash(node, slot);
    }
}
