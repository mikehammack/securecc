package com.maximarcana.securecc.block;

import com.maximarcana.securecc.ISecureTile;
import com.maximarcana.securecc.SecureAccess;
import com.maximarcana.securecc.SecureCC;
import com.maximarcana.securecc.SecureConfig;
import com.maximarcana.securecc.tile.TileSecureModem;
import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.api.peripheral.IPeripheralProvider;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Secure wireless modem.
 *
 * Provides the "secure_modem" peripheral to adjacent computers and talks
 * to every other same-owner secure modem in range over a private,
 * AES-encrypted network. Vanilla modems cannot see, read, or inject
 * traffic. Ownership is claimed on placement; only the owner (or an op)
 * can break it.
 */
public class BlockSecureModem extends BlockContainer
        implements IPeripheralProvider {

    public BlockSecureModem() {
        super(Material.IRON);
        setRegistryName(SecureCC.MODID, "secure_modem");
        setTranslationKey(SecureCC.MODID + ".secure_modem");
        setHardness(SecureConfig.blockHardness);
        setResistance(SecureConfig.blockResistance);
    }

    @Override
    public TileEntity createNewTileEntity(World world, int meta) {
        return new TileSecureModem();
    }

    // ---------- Peripheral ----------

    @Override
    public IPeripheral getPeripheral(World world, BlockPos pos, EnumFacing side) {
        TileEntity te = world.getTileEntity(pos);
        if (!(te instanceof TileSecureModem)) {
            return null;
        }
        // Peripheral confinement: only our own secure hardware may wrap the
        // secure modem. CC gives us no computer context here, so we check
        // the tile on the queried side: vanilla computers/turtles and CC
        // wired modems are not ISecureTile and get nothing back, which makes
        // peripheral.wrap return nil and hides the modem from
        // peripheral.find. Ownership and the security manager stay the trust
        // anchor for who may place and configure secure hardware.
        TileEntity neighbor = world.getTileEntity(pos.offset(side));
        if (!(neighbor instanceof ISecureTile)) {
            return null;
        }
        return ((TileSecureModem) te).getPeripheral(side);
    }

    // ---------- Placement / breaking ----------

    @Override
    public void onBlockPlacedBy(World world, BlockPos pos, IBlockState state,
                               EntityLivingBase placer, ItemStack stack) {
        super.onBlockPlacedBy(world, pos, state, placer, stack);
        if (!world.isRemote && placer instanceof EntityPlayer) {
            TileEntity te = world.getTileEntity(pos);
            if (te instanceof TileSecureModem) {
                TileSecureModem tile = (TileSecureModem) te;
                SecureAccess access = tile.getSecureAccess();
                if (!access.hasOwner()
                        && !access.restoreOwnerFromStack(stack)) {
                    access.setOwner((EntityPlayer) placer);
                }
            }
        }
    }

    // Break protection is handled centrally in SecureEventHandler.onBlockBreak
    // (BlockEvent.BreakEvent), which covers all ISecureTile blocks.

    @Override
    public EnumBlockRenderType getRenderType(IBlockState state) {
        return EnumBlockRenderType.MODEL;
    }
}
