package morerefinedstorage.config;

import com.raoulvdberge.refinedstorage.api.util.IComparer;
import com.raoulvdberge.refinedstorage.tile.config.FilterConfig;
import com.raoulvdberge.refinedstorage.tile.config.FilterMode;
import com.raoulvdberge.refinedstorage.tile.config.FilterType;
import morerefinedstorage.DummyNetworkNode;
import morerefinedstorage.MinecraftForgeTest;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import org.junit.jupiter.api.*;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class FilterConfigTest implements MinecraftForgeTest {

    private DummyNetworkNode node;

    @BeforeEach
    public void createNode() {
        this.node = new DummyNetworkNode();
    }

    @Test
    @Order(1)
    public void testParameterValidation() {
        //no node
        assertThrows(IllegalArgumentException.class, () -> new FilterConfig.Builder(null).build());
        //no initial value
        assertThrows(IllegalArgumentException.class, () -> new FilterConfig.Builder(node).allowedFilterModeBlackAndWhitelist().build());
        assertThrows(IllegalArgumentException.class, () -> new FilterConfig.Builder(node).allowedFilterTypeItemsAndFluids().build());
        //no filter size
        assertThrows(IllegalArgumentException.class, () -> new FilterConfig.Builder(node).allowedFilterTypeItemsAndFluids().filterTypeItems().build());
        assertThrows(IllegalArgumentException.class, () -> new FilterConfig.Builder(node).allowedFilterTypeItemsAndFluids().filterTypeFluids().build());

        //invalid combination of allowed and initial
        assertThrows(IllegalArgumentException.class, () -> new FilterConfig.Builder(node).allowedFilterTypeItems().filterTypeFluids().filterSizeOne().build());
        assertThrows(IllegalArgumentException.class, () -> new FilterConfig.Builder(node).allowedFilterTypeFluids().filterTypeItems().filterSizeOne().build());
        assertThrows(IllegalArgumentException.class, () -> new FilterConfig.Builder(node).allowedFilterModeBlacklist().filterModeWhitelist().build());
        assertThrows(IllegalArgumentException.class, () -> new FilterConfig.Builder(node).allowedFilterModeWhitelist().filterModeBlacklist().build());

        //no allowed value
        assertThrows(IllegalArgumentException.class, () -> new FilterConfig.Builder(node).filterTypeItems().filterSizeOne().build());
        assertThrows(IllegalArgumentException.class, () -> new FilterConfig.Builder(node).filterTypeFluids().filterSizeOne().build());
        assertThrows(IllegalArgumentException.class, () -> new FilterConfig.Builder(node).filterModeBlacklist().build());
        assertThrows(IllegalArgumentException.class, () -> new FilterConfig.Builder(node).filterModeWhitelist().build());

        //valid
        assertDoesNotThrow(() -> new FilterConfig.Builder(node).allowedFilterModeBlackAndWhitelist().filterModeWhitelist().build());
        assertDoesNotThrow(() -> new FilterConfig.Builder(node).allowedFilterTypeItemsAndFluids().filterTypeItems().filterSizeOne().build());
        assertDoesNotThrow(() -> new FilterConfig.Builder(node)
                .allowedFilterModeBlackAndWhitelist()
                .filterModeBlacklist()
                .allowedFilterTypeItemsAndFluids()
                .filterTypeItems()
                .filterSizeNine()
                .compareDamageAndNbt().build());
    }

    @Test
    @Order(2)
    public void testFilterType() {
        FilterConfig cfg1 = new FilterConfig.Builder(node).allowedFilterTypeItemsAndFluids().filterTypeItems().filterSizeOne().build();
        FilterConfig cfg2 = new FilterConfig.Builder(node).allowedFilterTypeItems().filterTypeItems().filterSizeNine().build();
        FilterConfig cfg3 = new FilterConfig.Builder(node).allowedFilterTypeFluids().filterTypeFluids().filterSizeNine().build();

        assertTrue(cfg1.usesFilterType());
        assertTrue(cfg2.usesFilterType());
        assertTrue(cfg3.usesFilterType());

        assertFalse(cfg1.usesFilterMode());
        assertFalse(cfg2.usesFilterMode());
        assertFalse(cfg3.usesFilterMode());

        assertFalse(cfg1.usesCompare());
        assertFalse(cfg2.usesCompare());
        assertFalse(cfg3.usesCompare());

        assertEquals(FilterType.ITEMS, cfg1.getFilterType());
        assertEquals(FilterType.ITEMS, cfg2.getFilterType());
        assertEquals(FilterType.FLUIDS, cfg3.getFilterType());

        assertNotNull(cfg1.getItemHandler());
        assertNotNull(cfg1.getFluidHandler());
        assertEquals(1, cfg1.getItemHandler().getSlots());
        assertEquals(1, cfg1.getFluidHandler().getSlots());

        assertNotNull(cfg2.getItemHandler());
        assertThrows(UnsupportedOperationException.class, cfg2::getFluidHandler);
        assertEquals(9, cfg2.getItemHandler().getSlots());

        assertThrows(UnsupportedOperationException.class, cfg3::getItemHandler);
        assertNotNull(cfg3.getFluidHandler());
        assertEquals(9, cfg3.getFluidHandler().getSlots());

        assertThrows(UnsupportedOperationException.class, () -> cfg2.setFilterType(FilterType.FLUIDS));
        assertThrows(UnsupportedOperationException.class, () -> cfg3.setFilterType(FilterType.ITEMS));

        assertDoesNotThrow(() -> cfg1.setFilterType(FilterType.FLUIDS));
    }

    @Test
    @Order(2)
    public void testFilterMode() {
        FilterConfig cfg1 = new FilterConfig.Builder(node).allowedFilterModeBlackAndWhitelist().filterModeBlacklist().build();
        FilterConfig cfg2 = new FilterConfig.Builder(node).allowedFilterModeBlacklist().filterModeBlacklist().build();
        FilterConfig cfg3 = new FilterConfig.Builder(node).allowedFilterModeWhitelist().filterModeWhitelist().build();

        assertTrue(cfg1.usesFilterMode());
        assertTrue(cfg2.usesFilterMode());
        assertTrue(cfg3.usesFilterMode());

        assertFalse(cfg1.usesFilterType());
        assertFalse(cfg2.usesFilterType());
        assertFalse(cfg3.usesFilterType());

        assertFalse(cfg1.usesCompare());
        assertFalse(cfg2.usesCompare());
        assertFalse(cfg3.usesCompare());

        assertEquals(FilterMode.BLACKLIST, cfg1.getFilterMode());
        assertEquals(FilterMode.BLACKLIST, cfg2.getFilterMode());
        assertEquals(FilterMode.WHITELIST, cfg3.getFilterMode());

        assertThrows(UnsupportedOperationException.class, cfg1::getItemHandler);
        assertThrows(UnsupportedOperationException.class, cfg1::getFluidHandler);

        assertThrows(UnsupportedOperationException.class, cfg2::getItemHandler);
        assertThrows(UnsupportedOperationException.class, cfg2::getFluidHandler);

        assertThrows(UnsupportedOperationException.class, cfg3::getItemHandler);
        assertThrows(UnsupportedOperationException.class, cfg3::getFluidHandler);

        assertThrows(UnsupportedOperationException.class, () -> cfg2.setFilterMode(FilterMode.WHITELIST));
        assertThrows(UnsupportedOperationException.class, () -> cfg3.setFilterMode(FilterMode.BLACKLIST));

        assertDoesNotThrow(() -> cfg1.setFilterMode(FilterMode.WHITELIST));
    }

    @Test
    @Order(3)
    public void testNodeMarkDirty() {
        FilterConfig cfg = new FilterConfig.Builder(node)
                .allowedFilterModeBlackAndWhitelist()
                .filterModeBlacklist()
                .allowedFilterTypeItemsAndFluids()
                .filterTypeItems()
                .filterSizeNine().build();

        cfg.setFilterMode(FilterMode.WHITELIST);
        assertEquals(1, node.markDirtyCallCount);

        cfg.setFilterType(FilterType.FLUIDS);
        assertEquals(2, node.markDirtyCallCount);

        cfg.getItemHandler().setStackInSlot(0, new ItemStack(Items.APPLE, 1));
        assertEquals(3, node.markDirtyCallCount);

        cfg.getFluidHandler().setFluid(0, new FluidStack(FluidRegistry.LAVA, 1));
        assertEquals(4, node.markDirtyCallCount);
    }

    @Test
    @Order(4)
    public void testAcceptsItemAndFluid() {
        FilterConfig cfg1 = new FilterConfig.Builder(node)
                .allowedFilterModeBlackAndWhitelist()
                .filterModeBlacklist()
                .allowedFilterTypeItemsAndFluids()
                .filterTypeItems()
                .filterSizeNine()
                .compareDamageAndNbt().build();

        FilterConfig cfg2 = new FilterConfig.Builder(node)
                .allowedFilterModeBlackAndWhitelist()
                .filterModeBlacklist()
                .allowedFilterTypeFluids()
                .filterTypeFluids()
                .filterSizeNine().build();

        assertTrue(cfg1.acceptsFluid(new FluidStack(FluidRegistry.LAVA, 1)));
        assertTrue(cfg1.acceptsItem(new ItemStack(Items.APPLE, 1)));
        assertFalse(cfg1.acceptsItem(ItemStack.EMPTY));

        assertTrue(cfg2.acceptsFluid(new FluidStack(FluidRegistry.LAVA, 1)));
        assertFalse(cfg2.acceptsItem(new ItemStack(Items.APPLE, 1)));

        cfg1.setFilterType(FilterType.FLUIDS);

        assertTrue(cfg1.acceptsFluid(new FluidStack(FluidRegistry.LAVA, 1)));
        assertTrue(cfg1.acceptsItem(new ItemStack(Items.APPLE, 1)));

        cfg1.setFilterMode(FilterMode.WHITELIST);
        cfg2.setFilterMode(FilterMode.WHITELIST);

        assertFalse(cfg1.acceptsItem(new ItemStack(Items.APPLE, 1)));
        assertFalse(cfg2.acceptsFluid(new FluidStack(FluidRegistry.LAVA, 1)));

        cfg1.getItemHandler().setStackInSlot(5, new ItemStack(Items.APPLE, 5));
        cfg2.getFluidHandler().setFluid(1, new FluidStack(FluidRegistry.LAVA, 1));

        assertTrue(cfg1.acceptsItem(new ItemStack(Items.APPLE, 1)));
        assertTrue(cfg2.acceptsFluid(new FluidStack(FluidRegistry.LAVA, 1)));
        assertFalse(cfg1.acceptsItem(new ItemStack(Items.WOODEN_AXE, 1)));
        assertFalse(cfg2.acceptsFluid(new FluidStack(FluidRegistry.WATER, 1)));

        assertFalse(cfg1.acceptsItem(new ItemStack(Items.APPLE, 1, 5)));

        cfg1.setCompare(IComparer.COMPARE_NBT);

        assertTrue(cfg1.acceptsItem(new ItemStack(Items.APPLE, 1, 5)));
    }

    @Test
    @Order(5)
    public void testListenersAndSupplier() {
        AtomicInteger c = new AtomicInteger(0);
        FilterConfig cfg = new FilterConfig.Builder(node).allowedFilterTypeItemsAndFluids().filterTypeItems().filterSizeNine()
                .onFilterTypeChanged(ft -> c.getAndIncrement())
                .customFilterTypeSupplier((ft) -> FilterType.UNDEFINED).build();

        cfg.setFilterType(FilterType.FLUIDS);
        assertEquals(FilterType.UNDEFINED, cfg.getFilterType());

        FilterConfig cfg2 = new FilterConfig.Builder(node).allowedFilterTypeItemsAndFluids().filterTypeItems().filterSizeNine()
                .onItemFilterChanged(slot -> c.getAndIncrement())
                .onFluidFilterChanged(slot -> c.getAndIncrement()).build();
        cfg2.getItemHandler().setStackInSlot(1, new ItemStack(Items.APPLE, 1));
        cfg2.getFluidHandler().setFluid(1, new FluidStack(FluidRegistry.LAVA, 1));

        assertEquals(3, c.get());
    }

    @Test
    @Order(6)
    public void testWriteToNBT() {
        FilterConfig cfg1 = new FilterConfig.Builder(node)
                .allowedFilterModeBlackAndWhitelist()
                .filterModeBlacklist()
                .allowedFilterTypeItemsAndFluids()
                .filterTypeItems()
                .filterSizeNine()
                .compareDamageAndNbt().build();

        FilterConfig cfg2 = new FilterConfig.Builder(node)
                .allowedFilterTypeFluids()
                .filterTypeFluids()
                .filterSizeNine()
                .compareDamageAndNbt().build();

        cfg1.getFluidHandler().setFluid(0, new FluidStack(FluidRegistry.LAVA, 1));
        cfg1.getItemHandler().setStackInSlot(0, new ItemStack(Items.APPLE, 1));

        cfg2.getFluidHandler().setFluid(0, new FluidStack(FluidRegistry.LAVA, 1));

        NBTTagCompound tag = cfg1.writeToNBT(new NBTTagCompound()).getCompoundTag("config");

        assertEquals(IComparer.COMPARE_NBT | IComparer.COMPARE_DAMAGE, tag.getInteger("compare"));
        assertEquals(FilterMode.BLACKLIST.ordinal(), tag.getInteger("filterMode"));
        assertEquals(FilterType.ITEMS.ordinal(), tag.getInteger("type"));
        assertTrue(tag.hasKey("items"));
        assertTrue(tag.hasKey("fluids"));

        NBTTagCompound tag2 = cfg2.writeToNBT(new NBTTagCompound()).getCompoundTag("config");

        assertEquals(IComparer.COMPARE_NBT | IComparer.COMPARE_DAMAGE, tag.getInteger("compare"));
        assertFalse(tag2.hasKey("filterMode"));
        assertEquals(FilterType.FLUIDS.ordinal(), tag2.getInteger("type"));
        assertFalse(tag2.hasKey("items"));
        assertTrue(tag2.hasKey("fluids"));
    }

    @Test
    public void testReadFromNBT() {
        FilterConfig cfg1 = new FilterConfig.Builder(node)
                .allowedFilterModeBlackAndWhitelist()
                .filterModeBlacklist()
                .allowedFilterTypeItemsAndFluids()
                .filterTypeItems()
                .filterSizeNine()
                .compareDamageAndNbt().build();

        FilterConfig cfg2 = new FilterConfig.Builder(node)
                .allowedFilterTypeFluids()
                .filterTypeFluids()
                .filterSizeNine()
                .compareDamageAndNbt().build();

        cfg1.getFluidHandler().setFluid(0, new FluidStack(FluidRegistry.LAVA, 1));
        cfg1.getItemHandler().setStackInSlot(0, new ItemStack(Items.APPLE, 1));
        cfg1.setFilterType(FilterType.FLUIDS);

        cfg2.getFluidHandler().setFluid(0, new FluidStack(FluidRegistry.WATER, 1));
        cfg2.setCompare(IComparer.COMPARE_NBT);

        assertDoesNotThrow(() -> cfg1.readFromNBT(cfg1.writeToNBT(new NBTTagCompound())));
        assertDoesNotThrow(() -> cfg2.readFromNBT(cfg2.writeToNBT(new NBTTagCompound())));

        assertEquals(FilterType.FLUIDS, cfg1.getFilterType());
        assertEquals(FilterMode.BLACKLIST, cfg1.getFilterMode());
        assertEquals(IComparer.COMPARE_NBT | IComparer.COMPARE_DAMAGE, cfg1.getCompare());
        assertEquals(9, cfg1.getItemHandler().getSlots());
        assertEquals(9, cfg1.getFluidHandler().getSlots());

        cfg1.setFilterMode(FilterMode.WHITELIST);
        assertTrue(cfg1.acceptsItem(new ItemStack(Items.APPLE, 1)));
        assertTrue(cfg1.acceptsFluid(new FluidStack(FluidRegistry.LAVA, 1)));

        assertEquals(FilterType.FLUIDS, cfg2.getFilterType());
        assertEquals(FilterMode.UNDEFINED, cfg2.getFilterMode());
        assertEquals(IComparer.COMPARE_NBT, cfg2.getCompare());
        assertThrows(UnsupportedOperationException.class, cfg2::getItemHandler);
        assertEquals(9, cfg2.getFluidHandler().getSlots());

        assertThrows(UnsupportedOperationException.class, () -> cfg2.setFilterMode(FilterMode.WHITELIST));
        assertFalse(cfg2.acceptsItem(new ItemStack(Items.APPLE, 1)));
        assertTrue(cfg2.acceptsFluid(new FluidStack(FluidRegistry.LAVA, 1)));
    }
}
