package com.raoulvdberge.refinedstorage.gui;

import com.google.common.collect.Lists;
import com.raoulvdberge.refinedstorage.RS;
import com.raoulvdberge.refinedstorage.api.autocrafting.preview.ICraftingPreviewElement;
import com.raoulvdberge.refinedstorage.api.render.IElementDrawer;
import com.raoulvdberge.refinedstorage.api.render.IElementDrawers;
import com.raoulvdberge.refinedstorage.apiimpl.autocrafting.preview.CraftingPreviewElementError;
import com.raoulvdberge.refinedstorage.apiimpl.autocrafting.preview.CraftingPreviewElementFluidStack;
import com.raoulvdberge.refinedstorage.apiimpl.autocrafting.preview.CraftingPreviewElementItemStack;
import com.raoulvdberge.refinedstorage.gui.control.Scrollbar;
import com.raoulvdberge.refinedstorage.network.MessageCraftingCancel;
import com.raoulvdberge.refinedstorage.network.MessageGridCraftingStart;
import com.raoulvdberge.refinedstorage.util.RenderUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fml.client.FMLClientHandler;
import org.lwjgl.input.Keyboard;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class GuiCraftingPreview extends GuiBase {
    public class CraftingPreviewElementDrawers extends ElementDrawers {
        private final IElementDrawer<Integer> overlayDrawer = (x, y, colour) -> {
            GlStateManager.color(1, 1, 1, 1);
            GlStateManager.disableLighting();

            drawRect(x, y, x + 73, y + 29, colour);
        };

        @Override
        public IElementDrawer<Integer> getOverlayDrawer() {
            return overlayDrawer;
        }
    }

    private static final int VISIBLE_ROWS = 5;

    private final List<ICraftingPreviewElement<?>> stacks;
    private final GuiScreen parent;

    private final UUID craftingTaskId;
    private final int quantity;
    private final long calculationTime;

    private GuiButton startButton;
    private GuiButton cancelButton;

    private CraftingPreviewElementItemStack hoveringStack;
    private CraftingPreviewElementFluidStack hoveringFluid;

    private final IElementDrawers drawers = new CraftingPreviewElementDrawers();

    private final boolean fluids;

    public GuiCraftingPreview(GuiScreen parent, List<ICraftingPreviewElement<?>> stacks, UUID craftingTaskId,
                              long calculationTime,
                              int quantity, boolean fluids) {
        super(new Container() {
            @Override
            public boolean canInteractWith(@Nonnull EntityPlayer player) {
                return false;
            }
        }, 254, 201);

        this.stacks = new ArrayList<>(stacks);
        this.parent = parent;

        this.craftingTaskId = craftingTaskId;
        this.quantity = quantity;
        this.calculationTime = calculationTime;
        this.fluids = fluids;

        this.scrollbar = new Scrollbar(235, 20, 12, 149);
    }

    @Override
    public void init(int x, int y) {
        cancelButton = addButton(x + 55, y + 201 - 20 - 7, 50, 20, t("gui.cancel"));
        startButton = addButton(x + 129, y + 201 - 20 - 7, 50, 20, t("misc.refinedstorage:start"));
        startButton.enabled = stacks.stream().noneMatch(ICraftingPreviewElement::hasMissing) && !hasError();
    }

    @Override
    public void update(int x, int y) {
        if (scrollbar != null) {
            scrollbar.setEnabled(getRows() > VISIBLE_ROWS);
            scrollbar.setMaxOffset(getRows() - VISIBLE_ROWS);
        }
    }

    private boolean hasError() {
        return stacks.size() == 1 && stacks.get(0) instanceof CraftingPreviewElementError;
    }

    @Override
    public void drawBackground(int x, int y, int mouseX, int mouseY) {
        bindTexture("gui/crafting_preview.png");

        drawTexture(x, y, 0, 0, screenWidth, screenHeight);

        if (hasError()) {
            drawRect(x + 7, y + 20, x + 228, y + 169, 0xFFDBDBDB);
        }
    }

    @Override
    public void drawForeground(int mouseX, int mouseY) {
        drawString(7, 7, t("gui.refinedstorage:crafting_preview"));

        float scale = fontRenderer.getUnicodeFlag() ? 1F : 0.5F;
        //draw calculation time
        if (calculationTime != -1) {
            GlStateManager.pushMatrix();
            GlStateManager.scale(scale, scale, 1);
            drawString(RenderUtils.getOffsetOnScale(7, 0.5f),
                    RenderUtils.getOffsetOnScale(175, 0.5f),
                    t("gui.refinedstorage:crafting_preview.calculation_time") + " " + TextFormatting.DARK_GREEN +
                            calculationTime + "ms");
            GlStateManager.popMatrix();
        }

        int x = 7;
        int y = 15;

        if (hasError()) {
            GlStateManager.pushMatrix();
            GlStateManager.scale(scale, scale, 1);

            drawString(RenderUtils.getOffsetOnScale(x + 5, scale), RenderUtils.getOffsetOnScale(y + 11, scale),
                    t("gui.refinedstorage:crafting_preview.error"));

            drawString(RenderUtils.getOffsetOnScale(x + 5, scale), RenderUtils.getOffsetOnScale(y + 21, scale),
                    t("gui.refinedstorage:crafting_preview.error.too_complex.0"));
            drawString(RenderUtils.getOffsetOnScale(x + 5, scale), RenderUtils.getOffsetOnScale(y + 31, scale),
                    t("gui.refinedstorage:crafting_preview.error.too_complex.1"));

            GlStateManager.popMatrix();
        } else {
            int slot = scrollbar != null ? (scrollbar.getOffset() * 3) : 0;

            RenderHelper.enableGUIStandardItemLighting();
            GlStateManager.enableDepth();

            this.hoveringStack = null;
            this.hoveringFluid = null;

            for (int i = 0; i < 3 * 5; ++i) {
                if (slot < stacks.size()) {
                    ICraftingPreviewElement<?> stack = stacks.get(slot);

                    stack.draw(x, y + 5, drawers);

                    if (inBounds(x + 5, y + 7, 16, 16, mouseX, mouseY)) {
                        this.hoveringStack = stack.getId().equals(CraftingPreviewElementItemStack.ID) ?
                                (CraftingPreviewElementItemStack) stack : null;

                        if (this.hoveringStack == null) {
                            this.hoveringFluid = stack.getId().equals(CraftingPreviewElementFluidStack.ID) ?
                                    (CraftingPreviewElementFluidStack) stack : null;
                        }
                    }
                }

                if ((i + 1) % 3 == 0) {
                    x = 7;
                    y += 30;
                } else {
                    x += 74;
                }

                slot++;
            }
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        super.drawScreen(mouseX, mouseY, partialTicks);

        if (hoveringStack != null) {
            List<String> textLines = hoveringStack.getElement().getTooltip(Minecraft.getMinecraft().player,
                    Minecraft.getMinecraft().gameSettings.advancedItemTooltips ?
                            ITooltipFlag.TooltipFlags.ADVANCED :
                            ITooltipFlag.TooltipFlags.NORMAL);

            List<String> smallTextLines = Lists.newArrayList(
                    t(hoveringStack.hasMissing() ? "gui.refinedstorage:crafting_preview.missing" :
                            "gui.refinedstorage:crafting_preview.to_craft", hoveringStack.getToCraft()),
                    t("gui.refinedstorage:crafting_preview.available", hoveringStack.getAvailable()));

            RenderUtils.drawTooltipWithSmallText(textLines, smallTextLines, RS.INSTANCE.config.detailedTooltip,
                    hoveringStack.getElement(), mouseX, mouseY, screenWidth, screenHeight, fontRenderer);
        } else if (hoveringFluid != null) {
            List<String> textLines = Lists.newArrayList(hoveringFluid.getElement().getLocalizedName());

            List<String> smallTextLines = Lists.newArrayList(
                    t(hoveringFluid.hasMissing() ? "gui.refinedstorage:crafting_preview.missing" :
                            "gui.refinedstorage:crafting_preview.to_craft", hoveringFluid.getToCraft()),
                    t("gui.refinedstorage:crafting_preview.available", hoveringFluid.getAvailable()));

            RenderUtils.drawTooltipWithSmallText(textLines, smallTextLines, RS.INSTANCE.config.detailedTooltip,
                            ItemStack.EMPTY, mouseX, mouseY, screenWidth, screenHeight, fontRenderer);
        }
    }

    @Override
    protected void keyTyped(char character, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_RETURN && startButton.enabled) {
            startRequest();
        } else if (keyCode == Keyboard.KEY_ESCAPE) {
            close();
        } else {
            super.keyTyped(character, keyCode);
        }
    }

    @Override
    protected void actionPerformed(@Nonnull GuiButton button) throws IOException {
        super.actionPerformed(button);

        if (button.id == startButton.id) {
            startRequest();
        } else if (button.id == cancelButton.id) {
            close();
        }
    }

    private void startRequest() {
        RS.INSTANCE.network.sendToServer(new MessageGridCraftingStart(craftingTaskId, quantity, fluids));

        FMLClientHandler.instance().showGuiScreen(parent);
    }

    private int getRows() {
        return Math.max(0, (int) Math.ceil((float) stacks.size() / 3F));
    }

    private void close() {
        RS.INSTANCE.network.sendToServer(new MessageCraftingCancel(craftingTaskId));
        FMLClientHandler.instance().showGuiScreen(parent);
    }
}
