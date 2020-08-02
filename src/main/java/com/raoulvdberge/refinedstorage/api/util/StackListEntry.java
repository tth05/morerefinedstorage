package com.raoulvdberge.refinedstorage.api.util;

import java.util.UUID;

/**
 * Represents a stack in a stack list.
 *
 * @param <T> the stack type
 */
public class StackListEntry<T> {
    private final UUID id;
    private final T stack;
    private long count;

    public StackListEntry(T stack, long count) {
        this.stack = stack;
        this.id = UUID.randomUUID();
        this.count = count;
    }

    public StackListEntry(UUID id, T stack, long count) {
        this.id = id;
        this.stack = stack;
        this.count = count;
    }

    /**
     * The unique id of the entry.
     * This id is NOT persisted, nor does it hold any relation to the contained stack.
     * It is randomly generated.
     *
     * @return the id
     */
    public UUID getId() {
        return id;
    }

    /**
     * @return the stack
     */
    public T getStack() {
        return stack;
    }

    /**
     * @return the amount
     */
    public long getCount() {
        return count;
    }

    public void setCount(long count) {
        this.count = count;
    }

    public void grow(long count) {
        this.count += count;
    }

    public void shrink(long count) {
        this.count -= count;
    }
}
