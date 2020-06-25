package com.raoulvdberge.refinedstorage.gui;

import com.raoulvdberge.refinedstorage.container.ContainerNetworkTransmitter;
import com.raoulvdberge.refinedstorage.gui.control.SideButtonRedstoneMode;
import com.raoulvdberge.refinedstorage.tile.TileNetworkTransmitter;

public class GuiNetworkTransmitter extends GuiBase {
    private final TileNetworkTransmitter networkTransmitter;

    public GuiNetworkTransmitter(ContainerNetworkTransmitter container, TileNetworkTransmitter networkTransmitter) {
        super(container, 176, 137);

        this.networkTransmitter = networkTransmitter;
    }

    @Override
    public void init(int x, int y) {
        addSideButton(new SideButtonRedstoneMode(this, TileNetworkTransmitter.REDSTONE_MODE));
    }

    @Override
    public void update(int x, int y) {
        //NO OP
    }

    @Override
    public void drawBackground(int x, int y, int mouseX, int mouseY) {
        bindTexture("gui/network_transmitter.png");

        drawTexture(x, y, 0, 0, screenWidth, screenHeight);
    }

    @Override
    public void drawForeground(int mouseX, int mouseY) {
        drawString(7, 7, t("gui.refinedstorage:network_transmitter"));

        String distance;

        if (networkTransmitter.getNode().getNetworkCard().getStackInSlot(0).isEmpty()) {
            distance = t("gui.refinedstorage:network_transmitter.missing_card");
        } else if (TileNetworkTransmitter.RECEIVER_DIMENSION.getValue() != networkTransmitter.getWorld().provider.getDimension()) {
            distance = t("gui.refinedstorage:network_transmitter.dimension", TileNetworkTransmitter.RECEIVER_DIMENSION.getValue());
        } else if (TileNetworkTransmitter.DISTANCE.getValue() != -1) {
            distance = t("gui.refinedstorage:network_transmitter.distance", TileNetworkTransmitter.DISTANCE.getValue());
        } else {
            distance = t("gui.refinedstorage:network_transmitter.missing_card");
        }

        drawString(51, 24, distance);
        drawString(7, 42, t("container.inventory"));
    }
}
