package com.raoulvdberge.refinedstorage.api.network;

import com.raoulvdberge.refinedstorage.api.autocrafting.ICraftingManager;
import com.raoulvdberge.refinedstorage.api.energy.IEnergy;
import com.raoulvdberge.refinedstorage.api.network.grid.handler.IFluidGridHandler;
import com.raoulvdberge.refinedstorage.api.network.grid.handler.IItemGridHandler;
import com.raoulvdberge.refinedstorage.api.network.item.INetworkItemHandler;
import com.raoulvdberge.refinedstorage.api.network.readerwriter.IReaderWriterManager;
import com.raoulvdberge.refinedstorage.api.network.security.ISecurityManager;
import com.raoulvdberge.refinedstorage.api.storage.IStorage;
import com.raoulvdberge.refinedstorage.api.storage.IStorageCache;
import com.raoulvdberge.refinedstorage.api.storage.IStorageTracker;
import com.raoulvdberge.refinedstorage.api.util.Action;
import com.raoulvdberge.refinedstorage.api.util.IComparer;
import com.raoulvdberge.refinedstorage.api.util.StackListResult;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.function.Predicate;

/**
 * Represents a network, usually is a controller.
 */
public interface INetwork {
    /**
     * @return the energy usage per tick of this network
     */
    int getEnergyUsage();

    /**
     * @return the position of this network in the world
     */
    BlockPos getPosition();

    /**
     * @return true if this network is able to run (usually corresponds to the redstone configuration), false otherwise
     */
    boolean canRun();

    /**
     * @return a graph of connected nodes to this network
     */
    INetworkNodeGraph getNodeGraph();

    /**
     * @return the {@link ISecurityManager} of this network
     */
    ISecurityManager getSecurityManager();

    /**
     * @return the {@link ICraftingManager} of this network
     */
    ICraftingManager getCraftingManager();

    /**
     * @return the {@link IEnergy} of this network
     */
    IEnergy getEnergy();

    /**
     * @return the {@link IItemGridHandler} of this network
     */
    IItemGridHandler getItemGridHandler();

    /**
     * @return the {@link IFluidGridHandler} of this network
     */
    IFluidGridHandler getFluidGridHandler();

    /**
     * @return the {@link INetworkItemHandler} of this network
     */
    INetworkItemHandler getNetworkItemHandler();

    /**
     * @return the {@link IStorageCache<ItemStack>} of this network
     */
    IStorageCache<ItemStack> getItemStorageCache();

    /**
     * @return the {@link IStorageCache<FluidStack>} of this network
     */
    IStorageCache<FluidStack> getFluidStorageCache();

    /**
     * @return the {@link IReaderWriterManager} of this network
     */
    IReaderWriterManager getReaderWriterManager();

    /**
     * Inserts an item in this network.
     *
     * @param stack  the stack prototype to insert, do NOT modify
     * @param size   the amount of that prototype that has to be inserted
     * @param action the action
     * @return null if the insert was successful, or a stack with the remainder
     *
     * @deprecated use {@link #insertItem(ItemStack, long, Action)}
     */
    @Nullable
    @Deprecated
    default ItemStack insertItem(@Nonnull ItemStack stack, int size, Action action) {
        StackListResult<ItemStack> result = insertItem(stack, (long) size, action);

        if (result == null)
            return null;

        return result.getFixedStack();
    }

    /**
     * Inserts an item in this network.
     *
     * @param stack  the stack prototype to insert, do NOT modify
     * @param size   the amount of that prototype that has to be inserted
     * @param action the action
     * @return null if the insert was successful, or a stack with the remainder
     */
    @Nullable
    StackListResult<ItemStack> insertItem(@Nonnull ItemStack stack, long size, Action action);

    /**
     * Inserts an item and notifies the crafting manager of the incoming item.
     *
     * @param stack the stack prototype to insert, do NOT modify
     * @param size  the amount of that prototype that has to be inserted
     * @return null if the insert was successful, or a stack with the remainder
     */
    @Nullable
    default ItemStack insertItemTracked(@Nonnull ItemStack stack, int size) {
        ItemStack stackCopy = stack.copy();
        stackCopy.setCount(size);
        getCraftingManager().track(stackCopy);

        if (stackCopy.isEmpty()) {
            return ItemStack.EMPTY;
        }

        return insertItem(stack, size, Action.PERFORM);
    }

    /**
     * Extracts an item from this network.
     *
     * @param stack  the prototype of the stack to extract, do NOT modify
     * @param size   the amount of that prototype that has to be extracted
     * @param flags  the flags to compare on, see {@link IComparer}
     * @param action the action
     * @param filter a filter for the storage
     * @return null if we didn't extract anything, or a stack with the result
     *
     * @deprecated use {@link #extractItem(ItemStack, long, int, Action, Predicate)}
     */
    @Nonnull
    @Deprecated
    default ItemStack extractItem(@Nonnull ItemStack stack, int size, int flags, Action action, Predicate<IStorage<ItemStack>> filter) {
        StackListResult<ItemStack> result = extractItem(stack, (long) size, flags, action, filter);

        if (result == null)
            return ItemStack.EMPTY;

        return result.getFixedStack();
    }

    /**
     * Extracts an item from this network.
     *
     * @param stack  the prototype of the stack to extract, do NOT modify
     * @param size   the amount of that prototype that has to be extracted
     * @param flags  the flags to compare on, see {@link IComparer}
     * @param action the action
     * @return null if we didn't extract anything, or a stack with the result
     *
     * @deprecated use {@link #extractItem(ItemStack, long, int, Action)}
     */
    @Nonnull
    @Deprecated
    default ItemStack extractItem(@Nonnull ItemStack stack, int size, int flags, Action action) {
        return extractItem(stack, size, flags, action, s -> true);
    }

    /**
     * Extracts an item from this network.
     *
     * @param stack  the prototype of the stack to extract, do NOT modify
     * @param size   the amount of that prototype that has to be extracted
     * @param action the action
     * @return null if we didn't extract anything, or a stack with the result
     *
     * @deprecated use {@link #extractItem(ItemStack, long, Action)}
     */
    @Nonnull
    @Deprecated
    default ItemStack extractItem(@Nonnull ItemStack stack, int size, Action action) {
        return extractItem(stack, size, IComparer.COMPARE_DAMAGE | IComparer.COMPARE_NBT, action);
    }

    /**
     * Extracts an item from this network.
     *
     * @param stack  the prototype of the stack to extract, do NOT modify
     * @param size   the amount of that prototype that has to be extracted
     * @param flags  the flags to compare on, see {@link IComparer}
     * @param action the action
     * @param filter a filter for the storage
     * @return null if we didn't extract anything, or a stack with the result
     */
    @Nullable
    StackListResult<ItemStack> extractItem(@Nonnull ItemStack stack, long size, int flags, Action action, Predicate<IStorage<ItemStack>> filter);

    /**
     * Extracts an item from this network.
     *
     * @param stack  the prototype of the stack to extract, do NOT modify
     * @param size   the amount of that prototype that has to be extracted
     * @param flags  the flags to compare on, see {@link IComparer}
     * @param action the action
     * @return null if we didn't extract anything, or a stack with the result
     */
    @Nullable
    default StackListResult<ItemStack> extractItem(@Nonnull ItemStack stack, long size, int flags, Action action) {
        return extractItem(stack, size, flags, action, s -> true);
    }

    /**
     * Extracts an item from this network.
     *
     * @param stack  the prototype of the stack to extract, do NOT modify
     * @param size   the amount of that prototype that has to be extracted
     * @param action the action
     * @return null if we didn't extract anything, or a stack with the result
     */
    @Nullable
    default StackListResult<ItemStack> extractItem(@Nonnull ItemStack stack, long size, Action action) {
        return extractItem(stack, size, IComparer.COMPARE_DAMAGE | IComparer.COMPARE_NBT, action);
    }

    /**
     * Inserts a fluid in this network.
     *
     * @param stack  the stack prototype to insert, do NOT modify
     * @param size   the amount of that prototype that has to be inserted
     * @param action the action
     * @return null if the insert was successful, or a stack with the remainder
     *
     * @deprecated use {@link #insertFluid(FluidStack, long, Action)}
     */
    @Nullable
    @Deprecated
    default FluidStack insertFluid(@Nonnull FluidStack stack, int size, Action action) {
        StackListResult<FluidStack> result = insertFluid(stack, (long) size, action);

        if (result == null)
            return null;

        return result.getFixedStack();
    }

    /**
     * Inserts a fluid in this network.
     *
     * @param stack  the stack prototype to insert, do NOT modify
     * @param size   the amount of that prototype that has to be inserted
     * @param action the action
     * @return null if the insert was successful, or a stack with the remainder
     */
    @Nullable
    StackListResult<FluidStack> insertFluid(@Nonnull FluidStack stack, long size, Action action);

    /**
     * Inserts a fluid and notifies the crafting manager of the incoming fluid.
     *
     * @param stack the stack prototype to insert, do NOT modify
     * @param size  the amount of that prototype that has to be inserted
     * @return null if the insert was successful, or a stack with the remainder
     */
    @Nullable
    default FluidStack insertFluidTracked(@Nonnull FluidStack stack, int size) {
        FluidStack stackCopy = stack.copy();
        stackCopy.amount = size;
        getCraftingManager().track(stackCopy);

        if (stackCopy.amount < 1) {
            return null;
        }

        return insertFluid(stack, size, Action.PERFORM);
    }

    /**
     * Extracts a fluid from this network.
     *
     * @param stack  the prototype of the stack to extract, do NOT modify
     * @param size   the amount of that prototype that has to be extracted
     * @param flags  the flags to compare on, see {@link IComparer}
     * @param action the action
     * @return null if we didn't extract anything, or a stack with the result
     *
     * @deprecated use {@link #extractFluid(FluidStack, long, int, Action, Predicate)}
     */
    @Nullable
    @Deprecated
    default FluidStack extractFluid(@Nonnull FluidStack stack, int size, int flags, Action action, Predicate<IStorage<FluidStack>> filter) {
        StackListResult<FluidStack> result = extractFluid(stack, (long) size, flags, action, filter);

        if (result == null)
            return null;

        return result.getFixedStack();
    }

    /**
     * Extracts a fluid from this network.
     *
     * @param stack  the prototype of the stack to extract, do NOT modify
     * @param size   the amount of that prototype that has to be extracted
     * @param flags  the flags to compare on, see {@link IComparer}
     * @param action the action
     * @return null if we didn't extract anything, or a stack with the result
     *
     * @deprecated use {@link #extractFluid(FluidStack, long, int, Action)}
     */
    @Nullable
    @Deprecated
    default FluidStack extractFluid(FluidStack stack, int size, int flags, Action action) {
        return extractFluid(stack, size, flags, action, s -> true);
    }

    /**
     * Extracts a fluid from this network.
     *
     * @param stack  the prototype of the stack to extract, do NOT modify
     * @param size   the amount of that prototype that has to be extracted
     * @param action the action
     * @return null if we didn't extract anything, or a stack with the result
     *
     * @deprecated use {@link #extractFluid(FluidStack, long, Action)}
     */
    @Nullable
    @Deprecated
    default FluidStack extractFluid(FluidStack stack, int size, Action action) {
        return extractFluid(stack, size, IComparer.COMPARE_NBT, action);
    }

    /**
     * Extracts a fluid from this network.
     *
     * @param stack  the prototype of the stack to extract, do NOT modify
     * @param size   the amount of that prototype that has to be extracted
     * @param flags  the flags to compare on, see {@link IComparer}
     * @param action the action
     * @return null if we didn't extract anything, or a stack with the result
     */
    @Nullable
    StackListResult<FluidStack> extractFluid(@Nonnull FluidStack stack, long size, int flags, Action action, Predicate<IStorage<FluidStack>> filter);

    /**
     * Extracts a fluid from this network.
     *
     * @param stack  the prototype of the stack to extract, do NOT modify
     * @param size   the amount of that prototype that has to be extracted
     * @param flags  the flags to compare on, see {@link IComparer}
     * @param action the action
     * @return null if we didn't extract anything, or a stack with the result
     */
    @Nullable
    default StackListResult<FluidStack> extractFluid(FluidStack stack, long size, int flags, Action action) {
        return extractFluid(stack, size, flags, action, s -> true);
    }

    /**
     * Extracts a fluid from this network.
     *
     * @param stack  the prototype of the stack to extract, do NOT modify
     * @param size   the amount of that prototype that has to be extracted
     * @param action the action
     * @return null if we didn't extract anything, or a stack with the result
     */
    @Nullable
    default StackListResult<FluidStack> extractFluid(FluidStack stack, long size, Action action) {
        return extractFluid(stack, size, IComparer.COMPARE_NBT, action);
    }

    /**
     * @return the storage tracker for items
     */
    IStorageTracker<ItemStack> getItemStorageTracker();

    /**
     * @return the storage tracker for fluids
     */
    IStorageTracker<FluidStack> getFluidStorageTracker();

    /**
     * @return the world where this network is in
     */
    World world();
}
