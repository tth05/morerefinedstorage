package com.raoulvdberge.refinedstorage.apiimpl.autocrafting.engine;

import com.raoulvdberge.refinedstorage.api.autocrafting.engine.CraftingTaskReadException;
import com.raoulvdberge.refinedstorage.api.autocrafting.engine.ICraftingRequestInfo;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nullable;

public class CraftingRequestInfo implements ICraftingRequestInfo {
    private static final String NBT_FLUID = "Fluid";
    private static final String NBT_STACK = "Stack";

    private ItemStack item;
    private FluidStack fluid;

    private long quantity;

    public CraftingRequestInfo(NBTTagCompound tag) throws CraftingTaskReadException {
        if (!tag.getBoolean(NBT_FLUID)) {
            item = new ItemStack(tag.getCompoundTag(NBT_STACK));

            if (item.isEmpty()) {
                throw new CraftingTaskReadException("Extractor stack is empty");
            }
        } else {
            fluid = FluidStack.loadFluidStackFromNBT(tag.getCompoundTag(NBT_STACK));

            if (fluid == null) {
                throw new CraftingTaskReadException("Extractor fluid stack is emty");
            }
        }
    }

    public CraftingRequestInfo(ItemStack item, long quantity) {
        this(quantity);
        this.item = item;
    }

    public CraftingRequestInfo(FluidStack fluid, long quantity) {
        this(quantity);
        this.fluid = fluid;
    }

    /**
     * @param quantity the requested quantity, not serialized to nbt
     */
    private CraftingRequestInfo(long quantity) {
        this.quantity = quantity;
    }

    @Nullable
    @Override
    public ItemStack getItem() {
        return item;
    }

    @Nullable
    @Override
    public FluidStack getFluid() {
        return fluid;
    }

    @Override
    public long getQuantity() {
        return this.quantity;
    }

    @Override
    public NBTTagCompound writeToNbt() {
        NBTTagCompound tag = new NBTTagCompound();

        tag.setBoolean(NBT_FLUID, fluid != null);

        if (fluid != null) {
            tag.setTag(NBT_STACK, fluid.writeToNBT(new NBTTagCompound()));
        } else {
            tag.setTag(NBT_STACK, item.writeToNBT(new NBTTagCompound()));
        }

        return tag;
    }
}
