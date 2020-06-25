package com.raoulvdberge.refinedstorage.container;

import com.raoulvdberge.refinedstorage.apiimpl.autocrafting.CraftingPattern;
import com.raoulvdberge.refinedstorage.apiimpl.network.node.NetworkNodeCrafter;
import com.raoulvdberge.refinedstorage.apiimpl.network.node.NetworkNodeCrafterManager;
import com.raoulvdberge.refinedstorage.container.slot.SlotCrafterManager;
import com.raoulvdberge.refinedstorage.gui.IResizableDisplay;
import com.raoulvdberge.refinedstorage.gui.grid.filtering.GridFilterParser;
import com.raoulvdberge.refinedstorage.gui.grid.stack.GridStackItem;
import com.raoulvdberge.refinedstorage.gui.grid.stack.IGridStack;
import com.raoulvdberge.refinedstorage.inventory.item.ItemHandlerBase;
import com.raoulvdberge.refinedstorage.item.ItemPattern;
import com.raoulvdberge.refinedstorage.tile.TileCrafterManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IContainerListener;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;
import net.minecraftforge.items.IItemHandlerModifiable;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

public class ContainerCrafterManager extends ContainerBase {
    public static class CrafterManagerListener implements IContainerListener {
        private final EntityPlayerMP base;
        private boolean receivedContainerData;

        public CrafterManagerListener(EntityPlayerMP base) {
            this.base = base;
        }

        public EntityPlayerMP getPlayer() {
            return base;
        }

        @Override
        public void sendAllContents(@Nonnull Container container, @Nonnull NonNullList<ItemStack> items) {
            if (receivedContainerData) {
                base.sendAllContents(container, items);
            }
        }

        @Override
        public void sendSlotContents(@Nonnull Container container, int slotInd, @Nonnull ItemStack stack) {
            if (receivedContainerData) {
                base.sendSlotContents(container, slotInd, stack);
            }
        }

        public void setReceivedContainerData() {
            receivedContainerData = true;
        }

        @Override
        public void sendWindowProperty(@Nonnull Container container, int varToUpdate, int newValue) {
            base.sendWindowProperty(container, varToUpdate, newValue);
        }

        @Override
        public void sendAllWindowProperties(@Nonnull Container container, @Nonnull IInventory inventory) {
            base.sendAllWindowProperties(container, inventory);
        }
    }

    private final IResizableDisplay display;
    private final NetworkNodeCrafterManager crafterManager;
    private Map<String, Integer> containerData;
    private final Map<String, IItemHandlerModifiable> dummyInventories = new HashMap<>();
    private final Map<String, Integer> headings = new HashMap<>();
    private int rows;

    @Override
    public void addListener(@Nonnull IContainerListener listener) {
        if (listener instanceof EntityPlayerMP) {
            listener = new CrafterManagerListener((EntityPlayerMP) listener);
        }

        super.addListener(listener);
    }

    public List<IContainerListener> getListeners() {
        return listeners;
    }

    public ContainerCrafterManager(TileCrafterManager crafterManager, EntityPlayer player, IResizableDisplay display) {
        super(crafterManager, player);

        this.display = display;
        this.crafterManager = crafterManager.getNode();

        if (!player.world.isRemote) {
            addPlayerInventory(8, display.getYPlayerInventory());

            if (crafterManager.getNode().getNetwork() != null) {
                for (Map.Entry<String, List<IItemHandlerModifiable>> entry : crafterManager.getNode().getNetwork().getCraftingManager().getNamedContainers().entrySet()) {
                    for (IItemHandlerModifiable handler : entry.getValue()) {
                        for (int i = 0; i < handler.getSlots(); ++i) {
                            addSlotToContainer(new SlotCrafterManager(handler, i, 0, 0, true, display, this.crafterManager));
                        }
                    }
                }
            }
        }
    }

    public void initSlots(@Nullable Map<String, Integer> newContainerData) {
        if (newContainerData == null) { // We resized
            if (containerData == null) { // No container data received yet, do nothing..
                return;
            }
        } else {
            containerData = newContainerData; // Received container data

            dummyInventories.clear();
        }

        this.inventorySlots.clear();
        this.inventoryItemStacks.clear();
        this.headings.clear();

        rows = 0;

        addPlayerInventory(8, display.getYPlayerInventory());

        int y = 19 + 18 - display.getCurrentOffset() * 18;
        int x = 8;

        List<Predicate<IGridStack>> filters = GridFilterParser.getFilters(null, display.getSearchFieldText(), Collections.emptyList());

        for (Map.Entry<String, Integer> category : containerData.entrySet()) {
            IItemHandlerModifiable dummy;

            if (newContainerData == null) { // We're only resizing, get the previous inventory...
                dummy = dummyInventories.get(category.getKey());
            } else {
                dummy = new ItemHandlerBase(category.getValue()) {
                    @Override
                    public int getSlotLimit(int slot) {
                        return 1;
                    }

                    @Nonnull
                    @Override
                    public ItemStack insertItem(int slot, @Nonnull ItemStack stack, boolean simulate) {
                        if (NetworkNodeCrafter.isValidPatternInSlot(getPlayer().getEntityWorld(), stack)) {
                            return super.insertItem(slot, stack, simulate);
                        }

                        return stack;
                    }
                };
                dummyInventories.put(category.getKey(), dummy);
            }

            boolean foundItemsInCategory = false;

            int yHeading = y - 19;

            int slotFound = 0;
            for (int slot = 0; slot < category.getValue(); ++slot) {
                boolean visible = true;

                if (!display.getSearchFieldText().trim().isEmpty()) {
                    ItemStack stack = dummy.getStackInSlot(slot);

                    if (stack.isEmpty()) {
                        visible = false;
                    } else {
                        CraftingPattern pattern = ItemPattern.getPatternFromCache(crafterManager.getWorld(), stack);

                        visible = false;

                        for (ItemStack output : pattern.getOutputs()) {
                            GridStackItem outputConverted = new GridStackItem(output);

                            for (Predicate<IGridStack> filter : filters) {
                                if (filter.test(outputConverted)) {
                                    visible = true;
                                    break;
                                }
                            }
                        }
                    }
                }

                addSlotToContainer(new SlotCrafterManager(dummy, slot, x, y, visible, display, crafterManager));

                if (visible) {
                    foundItemsInCategory = true;

                    x += 18;

                    // Don't increase y level if we are on our last slot row (otherwise we do y += 18 * 3)
                    if ((slotFound + 1) % 9 == 0 && slot + 1 < category.getValue()) {
                        x = 8;
                        y += 18;
                        rows++;
                    }

                    slotFound++;
                }
            }

            if (foundItemsInCategory) {
                headings.put(category.getKey(), yHeading);

                x = 8;
                y += 18 * 2;
                rows += 2; // Heading and first row
            }
        }
    }

    public Map<String, Integer> getHeadings() {
        return headings;
    }

    public int getRows() {
        return rows;
    }

    @Nonnull
    @Override
    public ItemStack transferStackInSlot(@Nonnull EntityPlayer player, int index) {
        ItemStack stack = ItemStack.EMPTY;

        Slot slot = getSlot(index);

        if (slot.getHasStack()) {
            stack = slot.getStack();

            if (index < 9 * 4) {
                if (!mergeItemStack(stack, 9 * 4, inventorySlots.size(), false)) {
                    return ItemStack.EMPTY;
                }
            } else if (!mergeItemStack(stack, 0, 9 * 4, false)) {
                return ItemStack.EMPTY;
            }

            if (stack.getCount() == 0) {
                slot.putStack(ItemStack.EMPTY);
            } else {
                slot.onSlotChanged();
            }
        }

        return stack;
    }
}
