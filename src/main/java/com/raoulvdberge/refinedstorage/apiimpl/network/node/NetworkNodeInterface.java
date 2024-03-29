package com.raoulvdberge.refinedstorage.apiimpl.network.node;

import com.raoulvdberge.refinedstorage.RS;
import com.raoulvdberge.refinedstorage.api.network.node.INetworkNode;
import com.raoulvdberge.refinedstorage.api.util.Action;
import com.raoulvdberge.refinedstorage.api.util.IComparer;
import com.raoulvdberge.refinedstorage.apiimpl.API;
import com.raoulvdberge.refinedstorage.apiimpl.storage.externalstorage.StorageExternalItem;
import com.raoulvdberge.refinedstorage.inventory.item.ItemHandlerBase;
import com.raoulvdberge.refinedstorage.inventory.item.ItemHandlerProxy;
import com.raoulvdberge.refinedstorage.inventory.item.ItemHandlerUpgrade;
import com.raoulvdberge.refinedstorage.inventory.listener.ListenerNetworkNode;
import com.raoulvdberge.refinedstorage.item.ItemUpgrade;
import com.raoulvdberge.refinedstorage.tile.config.FilterConfig;
import com.raoulvdberge.refinedstorage.tile.config.IRSFilterConfigProvider;
import com.raoulvdberge.refinedstorage.tile.config.IUpgradeContainer;
import com.raoulvdberge.refinedstorage.util.StackUtils;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.wrapper.CombinedInvWrapper;

import javax.annotation.Nonnull;

public class NetworkNodeInterface extends NetworkNode implements IRSFilterConfigProvider, IUpgradeContainer {
    public static final String ID = "interface";

    private final ItemHandlerBase importItems = new ItemHandlerBase(9, new ListenerNetworkNode(this));

    private final ItemHandlerBase exportItems = new ItemHandlerBase(9, new ListenerNetworkNode(this));

    private final IItemHandler items = new ItemHandlerProxy(importItems, exportItems);

    private final ItemHandlerUpgrade upgrades =
            new ItemHandlerUpgrade(4, new ListenerNetworkNode(this), ItemUpgrade.TYPE_SPEED, ItemUpgrade.TYPE_STACK,
                    ItemUpgrade.TYPE_CRAFTING);

    private final FilterConfig config = new FilterConfig.Builder(this)
            .allowedFilterTypeItems()
            .allowedFilterModeWhitelist()
            .filterSizeNine()
            .compareDamageAndNbt().build();

    private int currentSlot = 0;

    public NetworkNodeInterface(World world, BlockPos pos) {
        super(world, pos);
    }

    @Override
    public int getEnergyUsage() {
        return RS.INSTANCE.config.interfaceUsage + upgrades.getEnergyUsage();
    }

    @Override
    public void updateNetworkNode() {
        super.updateNetworkNode();

        if (network == null || !canUpdate()) {
            return;
        }

        if (currentSlot >= importItems.getSlots()) {
            currentSlot = 0;
        }

        ItemStack slot = importItems.getStackInSlot(currentSlot);

        if (slot.isEmpty()) {
            currentSlot++;
        } else if (ticks % upgrades.getSpeed() == 0) {
            int size = Math.min(slot.getCount(), upgrades.getItemInteractCount());

            ItemStack remainder = network.insertItemTracked(slot, size);

            if (remainder == null) {
                importItems.extractItem(currentSlot, size, false);
            } else if (size - remainder.getCount() > 0) {
                importItems.extractItem(currentSlot, size - remainder.getCount(), false);

                currentSlot++;
            }
        }

        for (int i = 0; i < 9; ++i) {
            ItemStack wanted = this.config.getItemHandler().getStackInSlot(i);
            ItemStack got = exportItems.getStackInSlot(i);

            if (wanted.isEmpty()) {
                if (!got.isEmpty()) {
                    exportItems
                            .setStackInSlot(i, StackUtils.nullToEmpty(network.insertItemTracked(got, got.getCount())));
                }
            } else if (!got.isEmpty() && !API.instance().getComparer().isEqual(wanted, got, this.config.getCompare())) {
                exportItems.setStackInSlot(i, StackUtils.nullToEmpty(network.insertItemTracked(got, got.getCount())));
            } else {
                int delta = got.isEmpty() ? wanted.getCount() : (wanted.getCount() - got.getCount());

                if (delta > 0) {
                    final boolean actingAsStorage = isActingAsStorage();

                    ItemStack result = network.extractItem(wanted, delta, this.config.getCompare() | IComparer.IGNORE_CAPABILITIES, Action.PERFORM, s -> {
                        // If we are not an interface acting as a storage, we can extract from anywhere.
                        if (!actingAsStorage) {
                            return true;
                        }

                        // If we are an interface acting as a storage, we don't want to extract from other interfaces to
                        // avoid stealing from each other.
                        return !(s instanceof StorageExternalItem) ||
                                !((StorageExternalItem) s).isConnectedToInterface();
                    });

                    if (!result.isEmpty()) {
                        if (exportItems.getStackInSlot(i).isEmpty()) {
                            exportItems.setStackInSlot(i, result);
                        } else if (API.instance().getComparer()
                                .isEqualNoQuantity(exportItems.getStackInSlot(i), result)) {
                            exportItems.getStackInSlot(i).grow(result.getCount());

                            // Example: our delta is 5, we extracted 3 items.
                            // That means we still have to autocraft 2 items.
                            delta -= result.isEmpty() ? 0 : result.getCount();

                            if (delta > 0 && upgrades.hasUpgrade(ItemUpgrade.TYPE_CRAFTING)) {
                                network.getCraftingManager()
                                        .request(new SlottedCraftingRequest(this, i), wanted, delta);
                            }
                        } else {
                            network.insertItem(result, result.getCount(), Action.PERFORM);
                        }
                    } else if (upgrades.hasUpgrade(ItemUpgrade.TYPE_CRAFTING)) {
                        network.getCraftingManager()
                                .request(new SlottedCraftingRequest(this, i), wanted, delta);
                    }
                } else if (delta < 0) {
                    ItemStack remainder = network.insertItemTracked(got, Math.abs(delta));

                    if (remainder == null) {
                        exportItems.extractItem(i, Math.abs(delta), false);
                    } else {
                        exportItems.extractItem(i, Math.abs(delta) - remainder.getCount(), false);
                    }
                }
            }
        }
    }

    private boolean isActingAsStorage() {
        for (EnumFacing facing : EnumFacing.VALUES) {
            INetworkNode facingNode = API.instance().getNetworkNodeManager(world).getNode(pos.offset(facing));

            if (facingNode instanceof NetworkNodeExternalStorage &&
                    facingNode.canUpdate() &&
                    ((NetworkNodeExternalStorage) facingNode).getDirection() == facing.getOpposite() &&
                    ((NetworkNodeExternalStorage) facingNode).getConfig().isFilterTypeItem()) {
                return true;
            }
        }

        return false;
    }

    @Override
    public void read(NBTTagCompound tag) {
        super.read(tag);

        StackUtils.readItems(importItems, 0, tag);
        StackUtils.readItems(exportItems, 2, tag);
        StackUtils.readItems(upgrades, 3, tag);
    }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public NBTTagCompound write(NBTTagCompound tag) {
        super.write(tag);

        StackUtils.writeItems(importItems, 0, tag);
        StackUtils.writeItems(exportItems, 2, tag);
        StackUtils.writeItems(upgrades, 3, tag);

        return tag;
    }

    @Override
    public NBTTagCompound writeConfiguration(NBTTagCompound tag) {
        super.writeConfiguration(tag);

        return tag;
    }

    @Override
    public void readConfiguration(NBTTagCompound tag) {
        super.readConfiguration(tag);
    }

    public IItemHandler getImportItems() {
        return importItems;
    }

    public IItemHandler getExportItems() {
        return exportItems;
    }

    public IItemHandler getItems() {
        return items;
    }

    @Override
    public IItemHandler getDrops() {
        return new CombinedInvWrapper(importItems, exportItems, upgrades);
    }

    @Override
    public boolean hasConnectivityState() {
        return true;
    }

    @Override
    public ItemHandlerUpgrade getUpgradeHandler() {
        return this.upgrades;
    }

    @Nonnull
    @Override
    public FilterConfig getConfig() {
        return this.config;
    }

}
