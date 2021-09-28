package com.raoulvdberge.refinedstorage.item.wrench;

import com.raoulvdberge.refinedstorage.RS;
import com.raoulvdberge.refinedstorage.api.network.node.INetworkNode;
import com.raoulvdberge.refinedstorage.api.network.security.Permission;
import com.raoulvdberge.refinedstorage.apiimpl.network.node.ICoverable;
import com.raoulvdberge.refinedstorage.apiimpl.network.node.NetworkNode;
import com.raoulvdberge.refinedstorage.apiimpl.network.node.cover.Cover;
import com.raoulvdberge.refinedstorage.apiimpl.network.node.cover.CoverManager;
import com.raoulvdberge.refinedstorage.block.BlockCable;
import com.raoulvdberge.refinedstorage.item.ItemBase;
import com.raoulvdberge.refinedstorage.item.ItemCover;
import com.raoulvdberge.refinedstorage.item.info.ItemInfo;
import com.raoulvdberge.refinedstorage.render.IModelRegistration;
import com.raoulvdberge.refinedstorage.render.collision.AdvancedRayTraceResult;
import com.raoulvdberge.refinedstorage.render.collision.AdvancedRayTracer;
import com.raoulvdberge.refinedstorage.tile.TileNode;
import com.raoulvdberge.refinedstorage.tile.config.IRSFilterConfigProvider;
import com.raoulvdberge.refinedstorage.tile.config.IUpgradeContainer;
import com.raoulvdberge.refinedstorage.tile.config.FilterConfig;
import com.raoulvdberge.refinedstorage.tile.config.RedstoneMode;
import com.raoulvdberge.refinedstorage.util.WorldUtils;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.InventoryHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nonnull;

public class ItemWrench extends ItemBase {
    public ItemWrench() {
        super(new ItemInfo(RS.ID, "wrench"));

        setMaxStackSize(1);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerModels(IModelRegistration modelRegistration) {
        modelRegistration.setModel(this, 0, new ModelResourceLocation(info.getId(), "inventory"));
    }

    @Nonnull
    @Override
    public EnumActionResult onItemUse(@Nonnull EntityPlayer player, @Nonnull World world, @Nonnull BlockPos pos, @Nonnull EnumHand hand, @Nonnull EnumFacing facing, float hitX, float hitY, float hitZ) {
        if (hand == EnumHand.OFF_HAND) {
            return EnumActionResult.FAIL;
        }

        addDefaultMode(player.getHeldItemMainhand());

        if (world.isRemote) {
            return EnumActionResult.SUCCESS;
        }

        TileEntity tile = world.getTileEntity(pos);

        IBlockState state = world.getBlockState(pos);
        Block block = state.getBlock();

        if (!(tile instanceof TileNode<?>)) {
            return EnumActionResult.FAIL;
        }

        INetworkNode node = ((TileNode<?>) tile).getNode();

        if (node.getNetwork() != null &&
                !node.getNetwork().getSecurityManager().hasPermission(Permission.BUILD, player)) {
            WorldUtils.sendNoPermissionMessage(player);

            return EnumActionResult.FAIL;
        }

        WrenchMode mode = WrenchMode.valueOf(player.getHeldItemMainhand().getTagCompound().getString("mode"));

        if (mode == WrenchMode.COVER && block instanceof BlockCable && node instanceof ICoverable) {
            CoverManager manager = ((ICoverable) ((TileNode<?>) tile).getNode()).getCoverManager();

            @SuppressWarnings("deprecation")
            AdvancedRayTraceResult<?> result = AdvancedRayTracer.rayTrace(
                    pos,
                    AdvancedRayTracer.getStart(player),
                    AdvancedRayTracer.getEnd(player),
                    ((BlockCable) block).getCollisions(tile, block.getActualState(state, world, pos))
            );

            if (result != null && result.getGroup().getDirection() != null) {
                EnumFacing facingSelected = result.getGroup().getDirection();

                Cover targetCover = manager.getCover(facingSelected);
                if (targetCover != null) {
                    ItemStack cover = targetCover.getType().createStack();

                    ItemCover.setItem(cover, targetCover.getStack());

                    manager.setCover(facingSelected, null);

                    WorldUtils.updateBlock(world, pos);

                    InventoryHelper.spawnItemStack(world, pos.getX(), pos.getY(), pos.getZ(), cover);

                    return EnumActionResult.SUCCESS;
                }
            }
        } else {
            NBTTagCompound tag = player.getHeldItemMainhand().getTagCompound();
            if (mode == WrenchMode.COPY) {
                if (node instanceof NetworkNode) {
                    tag.setString("redstoneMode", ((NetworkNode) node).getRedstoneMode().name());
                }

                //upgrades
                if (node instanceof IUpgradeContainer) {
                    tag.setTag("upgrades", IUpgradeContainer.writeToNBT((IUpgradeContainer) node, new NBTTagCompound()));
                }
                if (node instanceof IRSFilterConfigProvider) {
                    ((IRSFilterConfigProvider) node).getConfig().writeToNBT(tag);
                }

                player.sendMessage(new TextComponentTranslation("misc.refinedstorage:wrench.copied"));
            } else if (mode == WrenchMode.PASTE && !tag.isEmpty()) {
                TextComponentString args = new TextComponentString("");

                if (node instanceof NetworkNode && tag.hasKey("redstoneMode")) {
                    ((NetworkNode) node).setRedstoneMode(RedstoneMode.valueOf(tag.getString("redstoneMode")));
                    args.appendText(args.getSiblings().size() == 0 ? "" : ", ").appendSibling(new TextComponentTranslation("sidebutton.refinedstorage:redstone_mode"));
                }
                //upgrades
                if (node instanceof IUpgradeContainer && tag.hasKey("upgrades")) {
                    boolean success = IUpgradeContainer.readFromNBT((IUpgradeContainer) node, player, tag.getCompoundTag("upgrades"));

                    args.appendText(args.getSiblings().size() == 0 ? "" : ", ").appendSibling(
                            new TextComponentTranslation("misc.refinedstorage:wrench.pasted.upgrades").setStyle(new Style().setColor(success ? TextFormatting.WHITE : TextFormatting.RED))
                    );
                }

                if (node instanceof IRSFilterConfigProvider) {
                    FilterConfig config = ((IRSFilterConfigProvider) node).getConfig();
                    NBTTagCompound configTag = tag.getCompoundTag("config");
                    //type
                    if (config.usesFilterType() && configTag.hasKey("type")) {
                        args.appendText(args.getSiblings().size() == 0 ? "" : ", ").appendSibling(new TextComponentTranslation("misc.refinedstorage:wrench.pasted.type"));
                    }
                    //compareable
                    if (config.usesCompare() && configTag.hasKey("compare")) {
                        args.appendText(args.getSiblings().size() == 0 ? "" : ", ").appendSibling(new TextComponentTranslation("misc.refinedstorage:wrench.pasted.compareable"));
                    }

                    config.readFromNBT(tag);
                }

                if (!args.getSiblings().isEmpty())
                    player.sendMessage(new TextComponentTranslation("misc.refinedstorage:wrench.pasted", args));
            } else if (mode == WrenchMode.ROTATE) {
                block.rotateBlock(world, pos, player.getHorizontalFacing().getOpposite());
            }
        }

        return EnumActionResult.SUCCESS;
    }

    public static void addDefaultMode(ItemStack stack) {
        NBTTagCompound tag;
        if (stack.getTagCompound() == null)
            tag = new NBTTagCompound();
        else
            tag = stack.getTagCompound();

        if (!tag.hasKey("mode"))
            tag.setString("mode", WrenchMode.COVER.name());

        stack.setTagCompound(tag);
    }
}
