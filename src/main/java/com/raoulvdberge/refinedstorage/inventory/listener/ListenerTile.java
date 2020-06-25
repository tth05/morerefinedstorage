package com.raoulvdberge.refinedstorage.inventory.listener;

import net.minecraft.tileentity.TileEntity;

import java.util.function.IntConsumer;

public class ListenerTile implements IntConsumer {
    private final TileEntity tile;

    public ListenerTile(TileEntity tile) {
        this.tile = tile;
    }

    @Override
    public void accept(int slot) {
        tile.markDirty();
    }
}
