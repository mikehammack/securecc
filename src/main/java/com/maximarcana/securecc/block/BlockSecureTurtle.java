package com.maximarcana.securecc.block;

import com.maximarcana.securecc.SecureCC;
import com.maximarcana.securecc.SecureConfig;
import com.maximarcana.securecc.ModItems;
import com.maximarcana.securecc.tile.TileSecureTurtle;
import dan200.computercraft.shared.computer.blocks.TileComputerBase;
import dan200.computercraft.shared.computer.core.ComputerFamily;
import dan200.computercraft.shared.turtle.blocks.BlockTurtle;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;

/**
 * Diamond-tier turtle. Behaves like an advanced turtle, but tracks an
 * owner and enforces access control (see TileSecureTurtle). The terminal
 * gate lives in SecureEventHandler; breaking is gated here.
 */
public class BlockSecureTurtle extends BlockTurtle {
    public BlockSecureTurtle() {
        setRegistryName(SecureCC.MODID, "secure_turtle");
        setTranslationKey(SecureCC.MODID + ".secure_turtle");
        // Obsidian-grade: very hard to break, but not indestructible.
        setHardness(SecureConfig.blockHardness);
        setResistance(SecureConfig.blockResistance);
    }

    @Override
    protected TileComputerBase createTile(ComputerFamily family) {
        return new TileSecureTurtle();
    }

    /**
     * CC's drop path funnels through getItem (same signature as
     * BlockComputerBase's). Stamp the owner so breaking and replacing the
     * turtle does not reset ownership. Declared public (widened) so it
     * compiles regardless of the parent's visibility.
     */
    @Override
    public ItemStack getItem(TileComputerBase tile) {
        ItemStack vanilla = super.getItem(tile);
        // Return our secure turtle item, not the vanilla one, preserving
        // the computer ID and other NBT so the turtle keeps its identity.
        ItemStack secure = new ItemStack(ModItems.SECURE_TURTLE);
        if (vanilla.hasTagCompound()) {
            secure.setTagCompound(vanilla.getTagCompound().copy());
        }
        if (tile instanceof TileSecureTurtle) {
            ((TileSecureTurtle) tile).getSecureAccess().stampOwnerOnto(secure);
        }
        return secure;
    }

    @Override
    public void onBlockPlacedBy(World world, BlockPos pos, IBlockState state,
                               EntityLivingBase placer, ItemStack stack) {
        super.onBlockPlacedBy(world, pos, state, placer, stack);
        if (!world.isRemote && placer instanceof EntityPlayer) {
            TileEntity te = world.getTileEntity(pos);
            if (te instanceof TileSecureTurtle) {
                TileSecureTurtle tile = (TileSecureTurtle) te;
                if (!tile.hasOwner()
                        && !tile.getSecureAccess().restoreOwnerFromStack(stack)) {
                    tile.setOwner((EntityPlayer) placer);
                }
            }
        }
    }

    @Override
    public boolean removedByPlayer(IBlockState state, World world, BlockPos pos,
                                  EntityPlayer player, boolean willHarvest) {
        if (!world.isRemote) {
            TileEntity te = world.getTileEntity(pos);
            if (te instanceof TileSecureTurtle) {
                TileSecureTurtle tile = (TileSecureTurtle) te;
                if (!tile.canBreak(player)) {
                    player.sendMessage(new TextComponentString(
                            "\u00a7cThis secure turtle belongs to " + tile.getOwnerName()
                                    + ". Only the owner (or an op) can break it."));
                    return false;
                }
            }
        }
        return super.removedByPlayer(state, world, pos, player, willHarvest);
    }
}
