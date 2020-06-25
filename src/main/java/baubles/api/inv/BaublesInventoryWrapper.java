package baubles.api.inv;

import baubles.api.cap.IBaublesItemHandler;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;

import javax.annotation.Nonnull;

public class BaublesInventoryWrapper implements IInventory {
	final IBaublesItemHandler handler;
	final EntityPlayer player;

	public BaublesInventoryWrapper(IBaublesItemHandler handler) {
		super();
		this.handler = handler;
		this.player = null;
	}

	public BaublesInventoryWrapper(IBaublesItemHandler handler, EntityPlayer player) {
		super();
		this.handler = handler;
		this.player = player;
	}

	@Nonnull
    @Override
	public String getName() {
		return "BaublesInventory";
	}

	@Override
	public boolean hasCustomName() {
		return false;
	}

	@Nonnull
    @Override
	public ITextComponent getDisplayName() {
		return new TextComponentString(this.getName());
	}

	@Override
	public int getSizeInventory() {
		return handler.getSlots();
	}

	@Override
	public boolean isEmpty() {
		return false;
	}

	@Nonnull
    @Override
	public ItemStack getStackInSlot(int index) {
		return handler.getStackInSlot(index);
	}

	@Nonnull
    @Override
	public ItemStack decrStackSize(int index, int count) {
		return handler.extractItem(index, count, false);
	}

	@Nonnull
    @Override
	public ItemStack removeStackFromSlot(int index) {
		ItemStack out = this.getStackInSlot(index);
		handler.setStackInSlot(index, ItemStack.EMPTY);
		return out;
	}

	@Override
	public void setInventorySlotContents(int index, @Nonnull ItemStack stack) {
		handler.setStackInSlot(index, stack);
	}

	@Override
	public int getInventoryStackLimit() {
		return 64;
	}

	@Override
	public void markDirty() {
		//NO OP
	}

	@Override
	public boolean isUsableByPlayer(@Nonnull EntityPlayer player) {
		return true;
	}

	@Override
	public void openInventory(@Nonnull EntityPlayer player) {
		//NO OP
	}

	@Override
	public void closeInventory(@Nonnull EntityPlayer player) {
		//NO OP
	}

	@Override
	public boolean isItemValidForSlot(int index, @Nonnull ItemStack stack) {
		return handler.isItemValidForSlot(index, stack, player);
	}

	@Override
	public int getField(int id) {
		return 0;
	}

	@Override
	public void setField(int id, int value) {
		//NO OP
	}

	@Override
	public int getFieldCount() {
		return 0;
	}

	@Override
	public void clear() {
		for (int i = 0; i < this.getSizeInventory(); ++i)
		{
			this.setInventorySlotContents(i, ItemStack.EMPTY);
		}
	}
}
