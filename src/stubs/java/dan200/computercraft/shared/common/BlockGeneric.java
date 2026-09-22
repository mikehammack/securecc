package dan200.computercraft.shared.common;

import net.minecraft.block.Block;
import net.minecraft.block.ITileEntityProvider;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

/**
 * COMPILE-ONLY STUB — never packaged into the jar.
 *
 * The real BlockGeneric (in the CC:Tweaked jar) implements
 * ITileEntityProvider.createNewTileEntity as FINAL under its SRG name
 * (func_149915_a). javac therefore cannot see the interface as
 * implemented when compiling against the real class, and writing our own
 * override is not an option: SpecialSource would rename it to
 * func_149915_a, colliding with the final superclass method, and the
 * class would throw VerifyError at load time.
 *
 * This stub mirrors the real class's overridable surface with MCP names
 * and supplies a concrete createNewTileEntity so javac is satisfied.
 * At runtime the real BlockGeneric is used; its final
 * createNewTileEntity delegates to createTile(int), which our block
 * overrides.
 */
public abstract class BlockGeneric extends Block implements ITileEntityProvider {
    protected BlockGeneric(Material material) {
        super(material);
    }

    protected abstract TileGeneric createTile(IBlockState state);

    protected abstract TileGeneric createTile(int meta);

    @Override
    public TileEntity createNewTileEntity(World world, int meta) {
        return null;
    }
}
