package com.raoulvdberge.refinedstorage.gui;

import com.raoulvdberge.refinedstorage.RS;
import com.raoulvdberge.refinedstorage.api.util.IComparer;
import com.raoulvdberge.refinedstorage.api.util.IFilter;
import com.raoulvdberge.refinedstorage.container.ContainerFilter;
import com.raoulvdberge.refinedstorage.gui.control.SideButtonFilterType;
import com.raoulvdberge.refinedstorage.item.ItemFilter;
import com.raoulvdberge.refinedstorage.network.MessageFilterUpdate;
import com.raoulvdberge.refinedstorage.tile.config.IType;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.client.config.GuiCheckBox;

import javax.annotation.Nonnull;
import java.io.IOException;

public class GuiFilter extends GuiBase {
    private final ItemStack stack;

    private int compare;
    private int mode;
    private boolean modFilter;
    private final String name;
    private int type;

    private GuiCheckBox compareDamage;
    private GuiCheckBox compareNbt;
    private GuiCheckBox toggleModFilter;
    private GuiButton toggleMode;
    private GuiTextField nameField;

    public GuiFilter(ContainerFilter container) {
        super(container, 176, 231);

        this.stack = container.getStack();

        this.compare = ItemFilter.getCompare(container.getStack());
        this.mode = ItemFilter.getMode(container.getStack());
        this.modFilter = ItemFilter.isModFilter(container.getStack());
        this.name = ItemFilter.getName(container.getStack());
        this.type = ItemFilter.getType(container.getStack());
    }

    @Override
    public void init(int x, int y) {
        compareNbt = addCheckBox(x + 7, y + 77, t("gui.refinedstorage:filter.compare_nbt"), (compare & IComparer.COMPARE_NBT) == IComparer.COMPARE_NBT);
        compareDamage = addCheckBox(x + 7 + compareNbt.getButtonWidth() + 4, y + 77, t("gui.refinedstorage:filter.compare_damage"), (compare & IComparer.COMPARE_DAMAGE) == IComparer.COMPARE_DAMAGE);
        compareDamage.visible = type == IType.ITEMS;

        toggleModFilter = addCheckBox(0, y + 71 + 25, t("gui.refinedstorage:filter.mod_filter"), modFilter);
        toggleMode = addButton(x + 7, y + 71 + 21, 0, 20, "");

        updateModeButton(mode);

        nameField = new GuiTextField(0, fontRenderer, x + 34, y + 121, 137 - 6, fontRenderer.FONT_HEIGHT);
        nameField.setText(name);
        nameField.setEnableBackgroundDrawing(false);
        nameField.setVisible(true);
        nameField.setCanLoseFocus(true);
        nameField.setFocused(false);
        nameField.setTextColor(16777215);

        addSideButton(new SideButtonFilterType(this));
    }

    private void updateModeButton(int mode) {
        String text = mode == IFilter.MODE_WHITELIST ? t("sidebutton.refinedstorage:mode.whitelist") : t("sidebutton.refinedstorage:mode.blacklist");

        toggleMode.setWidth(fontRenderer.getStringWidth(text) + 12);
        toggleMode.displayString = text;
        toggleModFilter.x = toggleMode.x + toggleMode.getButtonWidth() + 4;
    }

    @Override
    public void update(int x, int y) {
        //NO OP
    }

    @Override
    public void drawBackground(int x, int y, int mouseX, int mouseY) {
        bindTexture("gui/filter.png");

        drawTexture(x, y, 0, 0, screenWidth, screenHeight);

        nameField.drawTextBox();
    }

    @Override
    public void drawForeground(int mouseX, int mouseY) {
        drawString(7, 7, t("gui.refinedstorage:filter"));
        drawString(7, 137, t("container.inventory"));
    }

    @Override
    protected void keyTyped(char character, int keyCode) throws IOException {
        if (!checkHotbarKeys(keyCode) && nameField.textboxKeyTyped(character, keyCode)) {
            sendUpdate();
        } else {
            super.keyTyped(character, keyCode);
        }
    }

    @Override
    public void mouseClicked(int mouseX, int mouseY, int clickedButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, clickedButton);

        nameField.mouseClicked(mouseX, mouseY, clickedButton);
    }

    @Override
    protected void actionPerformed(@Nonnull GuiButton button) throws IOException {
        super.actionPerformed(button);

        if (button == compareDamage) {
            compare ^= IComparer.COMPARE_DAMAGE;
        } else if (button == compareNbt) {
            compare ^= IComparer.COMPARE_NBT;
        } else if (button == toggleMode) {
            mode = mode == IFilter.MODE_WHITELIST ? IFilter.MODE_BLACKLIST : IFilter.MODE_WHITELIST;

            updateModeButton(mode);
        } else if (button == toggleModFilter) {
            modFilter = !modFilter;
        }

        sendUpdate();
    }

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;

        ItemFilter.setType(stack, type);

        compareDamage.visible = type == IType.ITEMS;
    }

    public void sendUpdate() {
        RS.INSTANCE.network.sendToServer(new MessageFilterUpdate(compare, mode, modFilter, nameField.getText(), type));
    }
}
