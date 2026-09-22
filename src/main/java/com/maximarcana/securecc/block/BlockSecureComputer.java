package com.maximarcana.securecc.block;

import com.maximarcana.securecc.SecureCC;
import com.maximarcana.securecc.SecureConfig;
import com.maximarcana.securecc.tile.TileSecureComputer;
import dan200.computercraft.shared.computer.blocks.BlockComputer;
import dan200.computercraft.shared.computer.blocks.TileComputer;
import dan200.computercraft.shared.computer.blocks.TileComputerBase;
import dan200.computercraft.shared.computer.core.ComputerFamily;
import dan200.computercraft.shared.computer.items.IComputerItem;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;

/**
 * Diamond-tier computer. Behaves like an advanced computer, but tracks an
 * owner and enforces access control (see TileSecureComputer).
 */
public class BlockSecureComputer extends BlockComputer {
    public BlockSecureComputer() {
        setRegistryName(SecureCC.MODID, "secure_computer");
        setTranslationKey(SecureCC.MODID + ".secure_computer");
        // Obsidian-grade: very hard to break, but not indestructible.
        setHardness(SecureConfig.blockHardness);
        setResistance(SecureConfig.blockResistance);
    }

    @Override
    public ComputerFamily getFamily(int meta) {
        return ComputerFamily.Advanced;
    }

    @Override
    public ComputerFamily getFamily(IBlockState state) {
        return ComputerFamily.Advanced;
    }

    @Override
    protected TileComputer createTile(ComputerFamily family) {
        return new TileSecureComputer();
    }

    @Override
    protected ItemStack getItem(TileComputerBase tile) {
        ItemStack stack = new ItemStack(com.maximarcana.securecc.ModItems.SECURE_COMPUTER);
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger(IComputerItem.NBT_ID, tile.getComputerID());
        stack.setTagCompound(tag);
        if (tile.getLabel() != null) stack.setStackDisplayName(tile.getLabel());
        return stack;
    }

    @Override
    public void onBlockPlacedBy(World world, BlockPos pos, IBlockState state,
                               EntityLivingBase placer, ItemStack stack) {
        super.onBlockPlacedBy(world, pos, state, placer, stack);
        if (!world.isRemote && placer instanceof EntityPlayer) {
            TileEntity te = world.getTileEntity(pos);
            if (te instanceof TileSecureComputer) {
                TileSecureComputer tile = (TileSecureComputer) te;
                if (!tile.hasOwner()) tile.setOwner((EntityPlayer) placer);
            }
        }
    }

    @Override
    public boolean removedByPlayer(IBlockState state, World world, BlockPos pos,
                                  EntityPlayer player, boolean willHarvest) {
        if (!world.isRemote) {
            TileEntity te = world.getTileEntity(pos);
            if (te instanceof TileSecureComputer) {
                TileSecureComputer tile = (TileSecureComputer) te;
                if (!tile.canBreak(player)) {
                    player.sendMessage(new TextComponentString(
                            "\u00a7cThis secure computer belongs to " + tile.getOwnerName()
                                    + ". Only the owner (or an op) can break it."));
                    return false;
                }
            }
        }
        return super.removedByPlayer(state, world, pos, player, willHarvest);
    }
}
