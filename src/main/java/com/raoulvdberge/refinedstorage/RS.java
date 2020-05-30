package com.raoulvdberge.refinedstorage;

import com.raoulvdberge.refinedstorage.command.CommandCreateDisk;
import com.raoulvdberge.refinedstorage.item.ItemCover;
import com.raoulvdberge.refinedstorage.proxy.ProxyCommon;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.Mod.Instance;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;

@Mod(modid = RS.ID, version = RS.VERSION, acceptedMinecraftVersions = "[1.12.2,1.13)", guiFactory = RS.GUI_FACTORY, /*updateJSON = RS.UPDATE_JSON, */dependencies = RS.DEPENDENCIES)
public final class RS {
    static {
        FluidRegistry.enableUniversalBucket();
    }

    public static final String ID = "refinedstorage";
    public static final String VERSION = "@version@";

    public static final String GUI_FACTORY = "com.raoulvdberge.refinedstorage.gui.config.ModGuiFactory";
    public static final String DEPENDENCIES = "after:forge@[14.23.3.2694,);";

    @SidedProxy(clientSide = "com.raoulvdberge.refinedstorage.proxy.ProxyClient", serverSide = "com.raoulvdberge.refinedstorage.proxy.ProxyCommon")
    public static ProxyCommon PROXY;

    @Instance
    public static RS INSTANCE;

    public RSConfig config;
    public final SimpleNetworkWrapper network = NetworkRegistry.INSTANCE.newSimpleChannel(ID);
    public final CreativeTabs tab = new CreativeTabs(ID) {
        @Override
        public ItemStack createIcon() {
            return new ItemStack(RSItems.STORAGE_HOUSING);
        }
    };
    public final CreativeTabs coversTab = new CreativeTabs(ID + ".covers") {
        @Override
        public ItemStack createIcon() {
            ItemStack stack = new ItemStack(RSItems.COVER);

            ItemCover.setItem(stack, new ItemStack(Blocks.STONE));

            return stack;
        }
    };

    @EventHandler
    public void preInit(FMLPreInitializationEvent e) {
        config = new RSConfig(null, e.getSuggestedConfigurationFile());

        PROXY.preInit(e);
    }

    @EventHandler
    public void init(FMLInitializationEvent e) {
        PROXY.init(e);
    }

    @EventHandler
    public void postInit(FMLPostInitializationEvent e) {
        PROXY.postInit(e);
    }

    @EventHandler
    public void onServerStarting(FMLServerStartingEvent e) {
        e.registerServerCommand(new CommandCreateDisk());
    }
}
