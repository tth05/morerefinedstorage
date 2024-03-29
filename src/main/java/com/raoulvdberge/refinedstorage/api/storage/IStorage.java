package com.raoulvdberge.refinedstorage.api.storage;

import com.raoulvdberge.refinedstorage.api.util.Action;
import com.raoulvdberge.refinedstorage.api.util.IComparer;
import com.raoulvdberge.refinedstorage.api.util.StackListEntry;
import com.raoulvdberge.refinedstorage.api.util.StackListResult;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Comparator;

public interface IStorage<T> {
    Comparator<IStorage<?>> COMPARATOR = (left, right) -> {
        int compare = Integer.compare(right.getPriority(), left.getPriority());

        return compare != 0 ? compare : Long.compare(right.getStored(), left.getStored());
    };

    /**
     * @return all entries of this storage
     */
    Collection<StackListEntry<T>> getEntries();

    /**
     * Inserts a stack to this storage.
     *
     * @param stack  the stack prototype to insert, do NOT modify
     * @param size   the amount of that prototype that has to be inserted
     * @param action the action
     * @return null if the insert was successful, or a stack with the remainder
     */
    @Nullable
    StackListResult<T> insert(@Nonnull T stack, long size, Action action);

    /**
     * Extracts a stack from this storage.
     * <p>
     * If the stack we found in the system is smaller than the requested size, return that stack anyway.
     *
     * @param stack  a prototype of the stack to extract, do NOT modify
     * @param size   the amount of that prototype that has to be extracted
     * @param flags  the flags to compare on, see {@link IComparer}
     * @param action the action
     * @return null if we didn't extract anything, or a stack with the result
     */
    @Nullable
    StackListResult<T> extract(@Nonnull T stack, long size, int flags, Action action);

    /**
     * @return the amount stored in this storage
     */
    long getStored();

    /**
     * @return the priority of this storage
     */
    int getPriority();

    /**
     * @return the access type of this storage
     */
    AccessType getAccessType();

    /**
     * Returns the delta that needs to be added to the item or fluid storage cache AFTER insertion of the stack.
     *
     * @param storedPreInsertion the amount stored pre insertion
     * @param size               the size of the stack being inserted
     * @param remainder          the remainder that we got back, or null if no remainder was there
     * @return the amount to increase the cache with
     */
    long getCacheDelta(long storedPreInsertion, long size, long remainder);
}
