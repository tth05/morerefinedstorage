package com.raoulvdberge.refinedstorage.gui;

import com.google.common.primitives.Ints;
import com.raoulvdberge.refinedstorage.api.util.IComparer;
import com.raoulvdberge.refinedstorage.container.ContainerDetector;
import com.raoulvdberge.refinedstorage.gui.control.SideButtonCompare;
import com.raoulvdberge.refinedstorage.gui.control.SideButtonDetectorMode;
import com.raoulvdberge.refinedstorage.gui.control.SideButtonType;
import com.raoulvdberge.refinedstorage.tile.TileDetector;
import com.raoulvdberge.refinedstorage.tile.data.TileDataManager;
import net.minecraft.client.gui.GuiTextField;

import java.io.IOException;

public class GuiDetector extends GuiBase {
    private GuiTextField amount;

    public GuiDetector(ContainerDetector container) {
        super(container, 176, 137);
    }

    @Override
    public void init(int x, int y) {
        addSideButton(new SideButtonType(this, TileDetector.TYPE));

        addSideButton(new SideButtonDetectorMode(this));

        addSideButton(new SideButtonCompare(this, TileDetector.COMPARE, IComparer.COMPARE_DAMAGE));
        addSideButton(new SideButtonCompare(this, TileDetector.COMPARE, IComparer.COMPARE_NBT));

        amount = new GuiTextField(0, fontRenderer, x + 41 + 1, y + 23 + 1, 50, fontRenderer.FONT_HEIGHT);
        amount.setText(String.valueOf(TileDetector.AMOUNT.getValue()));
        amount.setEnableBackgroundDrawing(false);
        amount.setVisible(true);
        amount.setTextColor(16777215);
        amount.setCanLoseFocus(true);
        amount.setFocused(false);
    }

    @Override
    public void update(int x, int y) {
        //NO OP
    }

    @Override
    public void drawBackground(int x, int y, int mouseX, int mouseY) {
        bindTexture("gui/detector.png");

        drawTexture(x, y, 0, 0, screenWidth, screenHeight);

        amount.drawTextBox();
    }

    @Override
    public void drawForeground(int mouseX, int mouseY) {
        drawString(7, 7, t("gui.refinedstorage:detector"));
        drawString(7, 43, t("container.inventory"));
    }

    @Override
    protected void keyTyped(char character, int keyCode) throws IOException {
        if (!checkHotbarKeys(keyCode) && amount.textboxKeyTyped(character, keyCode)) {
            Integer result = Ints.tryParse(amount.getText());

            if (result != null) {
                TileDataManager.setParameter(TileDetector.AMOUNT, result);
            }
        } else {
            super.keyTyped(character, keyCode);
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);

        amount.mouseClicked(mouseX, mouseY, mouseButton);
    }

    public GuiTextField getAmount() {
        return amount;
    }
}
