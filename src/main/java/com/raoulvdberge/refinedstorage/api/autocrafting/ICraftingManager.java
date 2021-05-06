package com.raoulvdberge.refinedstorage.api.autocrafting;

import com.raoulvdberge.refinedstorage.api.autocrafting.craftingmonitor.ICraftingMonitorListener;
import com.raoulvdberge.refinedstorage.api.autocrafting.task.ICraftingTask;
import com.raoulvdberge.refinedstorage.api.util.IComparer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.items.IItemHandlerModifiable;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.function.Predicate;

/**
 * The crafting manager handles the storing, updating, adding and deleting of crafting tasks in a network.
 */
public interface ICraftingManager {
    /**
     * @return the crafting tasks in this network, do NOT modify this
     */
    Collection<ICraftingTask> getTasks();

    /**
     * Returns a crafting task by id.
     *
     * @param id the id
     * @return the task, or null if no task was found for the given id
     */
    @Nullable
    ICraftingTask getTask(UUID id);

    /**
     * @return named crafting pattern containers
     */
    Map<String, List<IItemHandlerModifiable>> getNamedContainers();

    /**
     * Adds a crafting task.
     *
     * @param task the task to add
     */
    void add(@Nonnull ICraftingTask task);

    /**
     * Cancels a crafting task.
     *
     * @param id the id of the task to cancel, or null to cancel all
     */
    void cancel(@Nullable UUID id);

    /**
     * Creates a crafting task for a given stack, but doesn't add it to the list.
     *
     * @param stack    the stack to craft
     * @param quantity the quantity to craft
     * @return the crafting task, or null if no pattern was found for the given stack
     */
    @Nullable
    ICraftingTask create(ItemStack stack, long quantity);

    /**
     * Creates a crafting task for a given stack, but doesn't add it to the list.
     *
     * @param stack    the stack to craft
     * @param quantity the quantity to craft
     * @return the crafting task, or null if no pattern was found for the given stack
     */
    @Nullable
    ICraftingTask create(FluidStack stack, long quantity);

    /**
     * @deprecated Use {@link #request(Object, ItemStack, long)}
     */
    @Nullable
    @Deprecated
    default ICraftingTask request(ItemStack stack, int amount) {
        return request(null, stack, amount);
    }

    /**
     * @deprecated Use {@link #request(Object, FluidStack, long)}
     */
    @Nullable
    @Deprecated
    default ICraftingTask request(FluidStack stack, int amount) {
        return request(null, stack, amount);
    }

    /**
     * Schedules a crafting task if the task isn't scheduled yet.
     *
     * @param source the source
     * @param stack  the stack
     * @param amount the amount of items to request
     * @return the crafting task created, or null if no task is created
     */
    @Nullable
    ICraftingTask request(Object source, ItemStack stack, long amount);

    /**
     * Schedules a crafting task if the task isn't scheduled yet.
     *
     * @param source the source
     * @param stack  the stack
     * @param amount the mB of the fluid to request
     * @return the crafting task created, or null if no task is created
     */
    @Nullable
    ICraftingTask request(Object source, FluidStack stack, long amount);

    /**
     * Tracks an incoming stack.
     *
     * @param stack the stack
     */
    void track(ItemStack stack);

    /**
     * Tracks an incoming stack.
     *
     * @param stack the stack
     */
    void track(FluidStack stack);

    /**
     * @return the crafting patterns in this network
     */
    Set<ICraftingPattern> getPatterns();

    /**
     * Rebuilds the pattern list.
     */
    void rebuild();

    /**
     * Return a crafting pattern which produces the given {@link ItemStack}.
     *
     * @param pattern the stack to get a pattern for
     * @param flags comparison flags
     * @param filter a filter which should return {@code false} if the given pattern should be ignored.
     * @return the crafting pattern, or null if none is found
     */
    ICraftingPattern getPattern(ItemStack pattern, int flags, Predicate<ICraftingPattern> filter);

    /**
     * Return a crafting pattern which produces the given {@link ItemStack}.
     *
     * @param pattern the stack to get a pattern for
     * @param flags comparison flags
     * @return the crafting pattern, or null if none is found
     */
    @Nullable
    default ICraftingPattern getPattern(ItemStack pattern, int flags) {
        return getPattern(pattern, flags, p -> true);
    }

    /**
     * @see #getPattern(ItemStack, int)
     */
    @Nullable
    default ICraftingPattern getPattern(ItemStack pattern) {
        return getPattern(pattern, IComparer.COMPARE_NBT | IComparer.COMPARE_DAMAGE);
    }

    /**
     * @see #getPattern(ItemStack, int, Predicate)
     */
    @Nullable
    default ICraftingPattern getPattern(ItemStack pattern, Predicate<ICraftingPattern> filter) {
        return getPattern(pattern, IComparer.COMPARE_NBT | IComparer.COMPARE_DAMAGE, filter);
    }

    /**
     * Return a crafting pattern which produces the given {@link ItemStack}.
     *
     * @param pattern the stack to get a pattern for
     * @param filter a filter which should return {@code false} if the given pattern should be ignored.
     * @return the crafting pattern, or null if none is found
     */
    ICraftingPattern getPattern(FluidStack pattern, Predicate<ICraftingPattern> filter);

    /**
     * @see #getPattern(FluidStack, Predicate)
     */
    @Nullable
    default ICraftingPattern getPattern(FluidStack pattern) {
        return getPattern(pattern, p -> true);
    }

    /**
     * Updates the tasks in this manager.
     */
    void update();

    /**
     * @param tag the tag to read from
     */
    void readFromNbt(NBTTagCompound tag);

    /**
     * @param tag the tag to write to
     * @return the written tag
     */
    NBTTagCompound writeToNbt(NBTTagCompound tag);

    /**
     * @param listener the listener
     */
    void addListener(ICraftingMonitorListener listener);

    /**
     * @param listener the listener
     */
    void removeListener(ICraftingMonitorListener listener);

    /**
     * Calls all {@link ICraftingMonitorListener}s.
     */
    void onTaskChanged();

    /**
     * @param pattern to look for
     * @return a LinkedHashSet with all container that have this pattern
     */

    Set<ICraftingPatternContainer> getAllContainer(ICraftingPattern pattern);
}
