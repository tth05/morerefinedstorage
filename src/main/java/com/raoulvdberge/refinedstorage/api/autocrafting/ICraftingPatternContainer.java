package com.raoulvdberge.refinedstorage.api.autocrafting;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

/**
 * Represents a network node that contains crafting patterns.
 */
public interface ICraftingPatternContainer {
    /**
     * @deprecated Use {@link #getUpdateInterval()} and/or {@link #getMaximumSuccessfulCraftingUpdates()}
     * @return the amount of speed upgrades in the container
     */
    @Deprecated
    default int getSpeedUpgradeCount() {
        return 0;
    }

    /**
     * Returns the interval of when a crafting step with a pattern in this container can update.
     * Minimum value is 0 (each tick).
     * <p>
     * Note: rather than maxing out the update interval, implementors might want to balance around {@link #getMaximumSuccessfulCraftingUpdates()}.
     * This method merely speeds up the update rate, it might be more interesting to increase the output rate in {@link #getMaximumSuccessfulCraftingUpdates()}.
     *
     * @return the update interval
     */
    default int getUpdateInterval() {
        return 10;
    }

    /**
     * Returns the amount of successful crafting updates that this container can have per crafting step update.
     * If this limit is reached, crafting patterns from this container won't be able to update until the next
     * eligible crafting step update interval from {@link #getUpdateInterval()}.
     *
     * @return the maximum amount of successful crafting updates
     */
    default int getMaximumSuccessfulCraftingUpdates() {
        return 1;
    }

    /**
     * @return the inventory that this container is connected to, or null if no inventory is present
     */
    @Nullable
    IItemHandler getConnectedInventory();

    /**
     * @return the fluid inventory that this container is connected to, or null if no fluid inventory is present
     */
    @Nullable
    IFluidHandler getConnectedFluidInventory();

    /**
     * @return the tile that this container is connected to, or null if no tile is present
     */
    @Nullable
    TileEntity getConnectedTile();

    /**
     * @return the tile that this container is facing
     */
    TileEntity getFacingTile();

    /**
     * @return the direction to the facing tile
     */
    EnumFacing getDirection();

    /**
     * @return the patterns stored in this container
     */
    List<ICraftingPattern> getPatterns();

    /**
     * @return the pattern inventory, or null if no pattern inventory is present
     */
    @Nullable
    IItemHandlerModifiable getPatternInventory();

    /**
     * The name of this container for categorizing in the Crafting Manager GUI.
     * Can be a localized or unlocalized name.
     * If it's unlocalized, it will automatically localize the name.
     *
     * @return the name of this container
     */
    String getName();

    /**
     * @return the position of this container
     */
    BlockPos getPosition();

    /**
     * Containers may be daisy-chained together.  If this container points to
     * another one, gets the root container in the chain.  If containers are
     * not daisy-chained, returns this container.  If there was a container
     * loop, returns null.
     *
     * @return the root pattern container
     */
    @Nullable
    ICraftingPatternContainer getRootContainer();

    /**
     * @return the UUID of this container
     */
    UUID getUuid();

    /**
     * Unlocks this container if it is locked
     */
    default void unlock() {
    }

    /**
     * @return true if the connected inventory is locked for processing patterns, false otherwise
     */
    default boolean isLocked() {
        return false;
    }

    /**
     * Called when this container is used by a processing pattern to insert items or fluids in the connected inventory.
     */
    default void onUsedForProcessing() {
    }

    /**
     * @return the current crafter mode, used by autocrafting
     */
    default CrafterMode getCrafterMode() {
        return CrafterMode.IGNORE;
    }

    enum CrafterMode {
        IGNORE,
        SIGNAL_UNLOCKS_AUTOCRAFTING,
        SIGNAL_LOCKS_AUTOCRAFTING,
        PULSE_INSERTS_NEXT_SET;

        public static CrafterMode getById(int id) {
            if (id >= 0 && id < values().length) {
                return values()[id];
            }

            return IGNORE;
        }
    }
}
