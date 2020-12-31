package com.raoulvdberge.refinedstorage.api.util;

import java.util.UUID;

/**
 * Represents a stack in a stack list.
 *
 * @param <T> the stack type
 */
public class StackListEntry<T> {
    private UUID id;
    private T stack;
    private long count;

    private Unmodifiable<T> unmodifiableView;

    public StackListEntry(T stack, long count) {
        this.stack = stack;
        this.id = UUID.randomUUID();
        this.count = count;

        this.unmodifiableView = new Unmodifiable<>(this);
    }

    public StackListEntry(UUID id, T stack, long count) {
        this.id = id;
        this.stack = stack;
        this.count = count;

        this.unmodifiableView = new Unmodifiable<>(this);
    }

    private StackListEntry() {

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

    /**
     * @return an unmodifiable view of this stacklist entry
     */
    public Unmodifiable<T> asUnmodifiable() {
        return this.unmodifiableView;
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

    private static class Unmodifiable<T> extends StackListEntry<T> {

        private final StackListEntry<T> delegate;

        private Unmodifiable(StackListEntry<T> delegate) {
            this.delegate = delegate;
        }

        @Override
        public T getStack() {
            return delegate.getStack();
        }

        @Override
        public UUID getId() {
            return delegate.getId();
        }

        @Override
        public long getCount() {
            return delegate.count;
        }

        @Override
        public void setCount(long count) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void grow(long count) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void shrink(long count) {
            throw new UnsupportedOperationException();
        }
    }
}
