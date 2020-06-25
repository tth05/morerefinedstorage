package com.raoulvdberge.refinedstorage.api.util;

/**
 * Defines how an action is performed.
 */
public enum Action {
    /**
     * Performs the action.
     */
    PERFORM,
    /**
     * Gives back the same return as called with PERFORM, but doesn't mutate the underlying structure.
     */
    SIMULATE;

    /**
     * @return {@link Action#PERFORM} if {@code simulate} is true; {@link Action#SIMULATE} otherwise
     */
    public static Action of(boolean simulate) {
        return simulate ? Action.SIMULATE : Action.PERFORM;
    }
}
