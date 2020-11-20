package com.raoulvdberge.refinedstorage.inventory.item;

import com.raoulvdberge.refinedstorage.RSItems;
import com.raoulvdberge.refinedstorage.inventory.item.validator.ItemValidatorBasic;
import com.raoulvdberge.refinedstorage.item.ItemUpgrade;

import javax.annotation.Nullable;
import java.util.function.Consumer;

public class ItemHandlerUpgrade extends ItemHandlerBase {

    private int energyUsage = -1;

    public ItemHandlerUpgrade(int size, @Nullable Consumer<Integer> listener, int... supportedUpgrades) {
        super(size, listener, new ItemValidatorBasic[supportedUpgrades.length]);

        for (int i = 0; i < supportedUpgrades.length; ++i) {
            this.validators[i] = new ItemValidatorBasic(RSItems.UPGRADE, supportedUpgrades[i]);
        }
    }

    @Override
    protected void onContentsChanged(int slot) {
        super.onContentsChanged(slot);
        updateEnergyUsage();
    }

    public int getSpeed() {
        return getSpeed(9, 2);
    }

    public int getSpeed(int speed, int speedIncrease) {
        for (int i = 0; i < getSlots(); ++i) {
            if (!getStackInSlot(i).isEmpty() && getStackInSlot(i).getItemDamage() == ItemUpgrade.TYPE_SPEED) {
                speed -= speedIncrease;
            }
        }

        return speed;
    }

    public boolean hasUpgrade(int type) {
        return getUpgradeCount(type) > 0;
    }

    public int getUpgradeCount(int type) {
        int upgrades = 0;

        for (int i = 0; i < getSlots(); ++i) {
            if (!getStackInSlot(i).isEmpty() && getStackInSlot(i).getItemDamage() == type) {
                upgrades++;
            }
        }

        return upgrades;
    }

    private void updateEnergyUsage() {
        this.energyUsage = 0;

        for (int i = 0; i < getSlots(); ++i) {
            this.energyUsage += ItemUpgrade.getEnergyUsage(getStackInSlot(i));
        }
    }

    public int getEnergyUsage() {
        if (this.energyUsage == -1)
            updateEnergyUsage();

        return this.energyUsage;
    }

    public int getFortuneLevel() {
        int maxFortune = 0;

        for (int i = 0; i < getSlots(); ++i) {
            if (!getStackInSlot(i).isEmpty()) {
                int fortune = ItemUpgrade.getFortuneLevel(getStackInSlot(i));

                if (fortune > maxFortune) {
                    maxFortune = fortune;
                }
            }
        }

        return maxFortune;
    }

    public int getItemInteractCount() {
        return hasUpgrade(ItemUpgrade.TYPE_STACK) ? 64 : 1;
    }

    @Override
    public int getSlotLimit(int slot) {
        return 1;
    }
}
