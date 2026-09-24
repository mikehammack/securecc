package net.minecraft.client.renderer.block.model;

import net.minecraft.client.renderer.vertex.VertexFormat;

/**
 * COMPILE-ONLY STUB — never packaged into the jar.
 *
 * Forge's 1.12.2 binary patches add {@code BakedQuad.getFormat()} at runtime
 * (CC:Tweaked 1.89.2's turtle renderer calls it, and it works in-game), but
 * the method is not visible to this compile toolchain — the same situation
 * as {@code Block.getDrops} and {@code EntityPlayer.openGui}. This stub
 * mirrors the touched surface with MCP names so javac is satisfied; at
 * runtime the real Forge-patched BakedQuad is used. The method is not in
 * the MCP→SRG map, so reobfuscation leaves the call site untouched, exactly
 * like CC:Tweaked's own call.
 */
public class BakedQuad {
    public VertexFormat getFormat() {
        return null;
    }

    public boolean hasTintIndex() {
        return false;
    }

    public int getTintIndex() {
        return -1;
    }
}
