package morerefinedstorage.stacklist;

import com.raoulvdberge.refinedstorage.api.util.IComparer;
import com.raoulvdberge.refinedstorage.api.util.StackListEntry;
import com.raoulvdberge.refinedstorage.api.util.StackListResult;
import com.raoulvdberge.refinedstorage.apiimpl.API;
import com.raoulvdberge.refinedstorage.apiimpl.util.StackListItem;
import morerefinedstorage.MinecraftForgeTest;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Collection;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ItemStackListTest implements MinecraftForgeTest {

    private StackListItem list;

    @BeforeEach
    public void createStackList() {
        list = new StackListItem();
    }

    @Test
    @Order(1)
    public void testStackListResult() {
        StackListResult<ItemStack> result = new StackListResult<>(new ItemStack(Items.APPLE, 1), -1);
        assertEquals(result.getCount(), 1);
        assertEquals(result.getFixedStack().getCount(), 1);

        result = new StackListResult<>(new ItemStack(Items.APPLE, 1), (long) Integer.MAX_VALUE * 2);

        assertEquals(result.getCount(), (long) Integer.MAX_VALUE * 2);
        assertEquals(result.getFixedStack().getCount(), Integer.MAX_VALUE);
    }

    @ParameterizedTest
    @ValueSource(longs = {1, 50, 1000000, Integer.MAX_VALUE, (long) Integer.MAX_VALUE * 2})
    @Order(2)
    public void testGetEntry(long count) {
        ItemStack stack = new ItemStack(Items.DIAMOND_PICKAXE, 1, 50);
        stack.addEnchantment(Enchantment.getEnchantmentByID(20), 1);
        ItemStack stack2 = new ItemStack(Items.DIAMOND_PICKAXE, 1, 500);

        list.add(stack, count);
        StackListEntry<ItemStack> entry = list.getEntry(stack, IComparer.COMPARE_NBT | IComparer.COMPARE_DAMAGE);
        assertEquals(count, entry.getCount());
        assertTrue(API.instance().getComparer().isEqualNoQuantity(entry.getStack(), stack));

        list.add(stack2, count);
        entry = list.getEntry(stack2, IComparer.COMPARE_NBT | IComparer.COMPARE_DAMAGE);
        assertEquals(count, entry.getCount());
        assertTrue(API.instance().getComparer().isEqualNoQuantity(entry.getStack(), stack2));

        list.remove(stack2, count);
        entry = list.getEntry(stack2, IComparer.COMPARE_NBT | IComparer.COMPARE_DAMAGE);
        assertNull(entry);

        entry = list.getEntry(stack2, IComparer.COMPARE_DAMAGE);
        assertNull(entry);

        entry = list.getEntry(stack2, IComparer.COMPARE_NBT);
        assertNull(entry);

        entry = list.getEntry(stack2, 0);
        assertTrue(API.instance().getComparer().isEqualNoQuantity(entry.getStack(), stack));
    }

    @Test
    @Order(3)
    public void testAddInvalidStack() {
        ItemStack stack = new ItemStack(Items.APPLE, -1);

        assertTrue(stack.isEmpty());
        assertThrows(IllegalArgumentException.class, () -> {
            list.add(stack, 5);
        });
    }

    @ParameterizedTest
    @ValueSource(longs = {1, 50, 1000000, Integer.MAX_VALUE, (long) Integer.MAX_VALUE * 2})
    @Order(4)
    public void testAddStack(long count) {
        ItemStack stack = new ItemStack(Items.APPLE, 1);

        StackListResult<ItemStack> result = list.add(stack, count);

        assertNotEquals(result.getStack(), stack);
        assertTrue(API.instance().getComparer().isEqualNoQuantity(result.getStack(), stack));
        assertEquals(count, result.getCount());
        assertEquals(count, list.getEntry(stack, IComparer.COMPARE_NBT | IComparer.COMPARE_DAMAGE).getCount());
    }

    @Test
    @Order(5)
    public void testAddDifferentStacks() {
        ItemStack stack1 = new ItemStack(Items.APPLE, 1);
        stack1.setItemDamage(1);
        ItemStack stack2 = new ItemStack(Items.APPLE, 2);
        NBTTagCompound nbt = new NBTTagCompound();
        nbt.setInteger("hey", 1);
        stack2.setTagCompound(nbt);
        ItemStack stack3 = new ItemStack(Items.APPLE, 3);

        list.add(stack1, stack1.getCount());
        list.add(stack2, stack2.getCount());
        list.add(stack3, stack3.getCount());

        assertEquals(3, list.getStacks().size());
    }

    @Test
    @Order(6)
    public void testAddEqualStacksShouldMerge() {
        ItemStack stack1 = new ItemStack(Items.APPLE, 1);
        NBTTagCompound nbt1 = new NBTTagCompound();
        nbt1.setInteger("hey", 1);
        stack1.setTagCompound(nbt1);
        ItemStack stack2 = new ItemStack(Items.APPLE, 2);
        NBTTagCompound nbt2 = new NBTTagCompound();
        nbt2.setInteger("hey", 1);
        stack2.setTagCompound(nbt2);

        list.add(stack1, stack1.getCount());
        list.add(stack2, stack2.getCount());

        assertEquals(1, list.getStacks().size());
        assertEquals(3, list.getEntry(stack1, IComparer.COMPARE_NBT | IComparer.COMPARE_DAMAGE).getCount());
    }

    @Test
    @Order(7)
    public void testRemoveNonExistentStack() {
        ItemStack stack = new ItemStack(Items.APPLE, 1);
        StackListResult<ItemStack> result = list.remove(stack, 10);

        assertNull(result);
        assertEquals(0, list.getStacks().size());
    }

    @ParameterizedTest
    @ValueSource(longs = {1, 50, 1000000, Integer.MAX_VALUE, (long) Integer.MAX_VALUE * 2})
    @Order(8)
    public void testRemoveStack(long count) {
        ItemStack stack = new ItemStack(Items.APPLE, 1);
        list.add(stack, count * 2);

        StackListResult<ItemStack> result = list.remove(stack, count - 1);

        assertNotNull(result);
        assertTrue(API.instance().getComparer().isEqualNoQuantity(result.getStack(), stack));
        assertEquals(count - 1, result.getCount());
        assertEquals(1, list.getStacks().size());
        assertEquals(count + 1, list.getEntry(stack, IComparer.COMPARE_NBT | IComparer.COMPARE_DAMAGE).getCount());
    }

    @Test
    @Order(9)
    public void testRemoveStackMoreThanAvailable() {
        ItemStack stack = new ItemStack(Items.APPLE, 1);
        list.add(stack, 10);

        StackListResult<ItemStack> result = list.remove(stack, 15);

        assertNotNull(result);
        assertTrue(API.instance().getComparer().isEqualNoQuantity(result.getStack(), stack));
        assertEquals(10, result.getCount());
        assertEquals(0, list.getStacks().size());
        assertNull(list.getEntry(stack, IComparer.COMPARE_NBT | IComparer.COMPARE_DAMAGE));
    }

    @Test
    @Order(10)
    public void testClearCounts() {
        ItemStack stack1 = new ItemStack(Items.APPLE, 1);
        stack1.setItemDamage(1);
        ItemStack stack2 = new ItemStack(Items.APPLE, 2);
        NBTTagCompound nbt = new NBTTagCompound();
        nbt.setInteger("hey", 1);
        stack2.setTagCompound(nbt);
        ItemStack stack3 = new ItemStack(Items.APPLE, 3);

        list.add(stack1, stack1.getCount());
        list.add(stack2, stack2.getCount());
        list.add(stack3, stack3.getCount());

        assertEquals(3, list.getStacks().size());
        for (StackListEntry<ItemStack> stack : list.getStacks()) {
            assertTrue(stack.getCount() > 0);
        }

        list.clearCounts();

        assertEquals(3, list.getStacks().size());
        for (StackListEntry<ItemStack> stack : list.getStacks()) {
            assertEquals(0, stack.getCount());
        }
    }

    @Test
    @Order(11)
    public void testClearEmpty() {
        ItemStack stack1 = new ItemStack(Items.APPLE, 1);
        stack1.setItemDamage(1);
        ItemStack stack2 = new ItemStack(Items.APPLE, 2);
        NBTTagCompound nbt = new NBTTagCompound();
        nbt.setInteger("hey", 1);
        stack2.setTagCompound(nbt);
        ItemStack stack3 = new ItemStack(Items.APPLE, 3);

        list.add(stack1, stack1.getCount());
        list.add(stack2, stack2.getCount());
        list.add(stack3, stack3.getCount());

        assertEquals(3, list.getStacks().size());
        for (StackListEntry<ItemStack> stack : list.getStacks()) {
            assertTrue(stack.getCount() > 0);
        }

        list.clearCounts();

        assertEquals(3, list.getStacks().size());
        for (StackListEntry<ItemStack> stack : list.getStacks()) {
            assertEquals(0, stack.getCount());
        }

        list.add(stack3, 1);
        list.clearEmpty();

        assertEquals(1, list.getStacks().size());
        assertTrue(API.instance().getComparer().isEqualNoQuantity(list.getEntry(stack3, IComparer.COMPARE_NBT | IComparer.COMPARE_DAMAGE).getStack(), stack3));
    }

    @Test
    @Order(12)
    public void testGetByUUID() {
        ItemStack stack = new ItemStack(Items.APPLE);

        StackListResult<ItemStack> result = list.add(stack);

        StackListEntry<ItemStack> entry = list.get(result.getId());

        assertNull(list.get(new UUID(0, 25)));
        assertNotNull(entry);
        assertTrue(API.instance().getComparer().isEqualNoQuantity(entry.getStack(), stack));
    }

    @Test
    @Order(13)
    public void testEntryNotModifiable() {
        ItemStack stack = new ItemStack(Items.APPLE);

        StackListResult<ItemStack> result = list.add(stack);

        StackListEntry<ItemStack> entry = list.get(result.getId());
        assertNotNull(entry);
        assertThrows(UnsupportedOperationException.class, () -> entry.setCount(56));
        assertThrows(UnsupportedOperationException.class, () -> entry.grow(56));
        assertThrows(UnsupportedOperationException.class, () -> entry.shrink(56));

        StackListEntry<ItemStack> entry2 = list.getEntry(stack, IComparer.COMPARE_NBT | IComparer.COMPARE_DAMAGE);
        assertNotNull(entry2);
        assertThrows(UnsupportedOperationException.class, () -> entry2.setCount(56));
        assertThrows(UnsupportedOperationException.class, () -> entry2.grow(56));
        assertThrows(UnsupportedOperationException.class, () -> entry2.shrink(56));

        Collection<StackListEntry<ItemStack>> stacks = list.getStacks();
        assertNotNull(stacks);
        assertThrows(UnsupportedOperationException.class, () -> stacks.add(new StackListEntry<>(stack, 2)));
    }
}
