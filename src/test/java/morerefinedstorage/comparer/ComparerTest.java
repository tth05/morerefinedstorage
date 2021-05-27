package morerefinedstorage.comparer;

import com.raoulvdberge.refinedstorage.api.util.IComparer;
import com.raoulvdberge.refinedstorage.apiimpl.API;
import morerefinedstorage.MinecraftForgeTest;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ComparerTest implements MinecraftForgeTest {

    private final IComparer comparer = API.instance().getComparer();

    @Test
    public void testItemsEqual() {
        ItemStack stack1 = new ItemStack(Items.APPLE, 456567);
        ItemStack stack2 = new ItemStack(Items.APPLE, 12);

        assertTrue(comparer.isEqualNoQuantity(stack1, stack2));
        assertFalse(comparer.isEqual(stack1, stack2, IComparer.COMPARE_NBT | IComparer.COMPARE_DAMAGE | IComparer.COMPARE_QUANTITY));
    }

    @Test
    public void testDamageEqual() {
        ItemStack stack1 = new ItemStack(Items.DIAMOND_AXE);
        ItemStack stack2 = new ItemStack(Items.DIAMOND_AXE);

        assertTrue(comparer.isEqual(stack1, stack2));

        stack1.setItemDamage(1);

        assertFalse(comparer.isEqual(stack1, stack2));
        assertTrue(comparer.isEqual(stack1, stack2, IComparer.COMPARE_NBT | IComparer.COMPARE_QUANTITY));

        stack2.setItemDamage(1);

        assertTrue(comparer.isEqual(stack1, stack2));
    }

    @Test
    public void testNBTEqual() {
        ItemStack stack1 = new ItemStack(Items.DIAMOND_AXE);
        NBTTagCompound compound1 = new NBTTagCompound();
        compound1.setString("t", "1");
        stack1.setTagCompound(compound1);
        ItemStack stack2 = new ItemStack(Items.DIAMOND_AXE);
        NBTTagCompound compound2 = new NBTTagCompound();
        compound2.setString("t", "1");

        assertFalse(comparer.isEqual(stack1, stack2));
        assertTrue(comparer.isEqual(stack1, stack2, IComparer.COMPARE_DAMAGE | IComparer.COMPARE_QUANTITY));

        stack2.setTagCompound(compound2);

        assertTrue(comparer.isEqual(stack1, stack2));
        assertTrue(comparer.isEqual(stack1, stack2, IComparer.COMPARE_DAMAGE | IComparer.COMPARE_QUANTITY));

        stack1.setTagCompound(new NBTTagCompound());

        assertFalse(comparer.isEqual(stack1, stack2));
        assertTrue(comparer.isEqual(stack1, stack2, IComparer.COMPARE_DAMAGE | IComparer.COMPARE_QUANTITY));

        stack2.setTagCompound(null);

        assertTrue(comparer.isEqual(stack1, stack2));
        assertTrue(comparer.isEqual(stack1, stack2, IComparer.COMPARE_DAMAGE | IComparer.COMPARE_QUANTITY));

        stack2.setTagCompound(new NBTTagCompound());

        assertTrue(comparer.isEqual(stack1, stack2));
        assertTrue(comparer.isEqual(stack1, stack2, IComparer.COMPARE_DAMAGE | IComparer.COMPARE_QUANTITY));

        stack1.setTagCompound(null);
        stack2.setTagCompound(null);

        assertTrue(comparer.isEqual(stack1, stack2));
        assertTrue(comparer.isEqual(stack1, stack2, IComparer.COMPARE_DAMAGE | IComparer.COMPARE_QUANTITY));
    }

    //TODO: test capabilities somehow
}
