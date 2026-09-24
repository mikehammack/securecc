package baubles.api;

/**
 * Compile-only stub for the Baubles API. Plethora declares
 * {@code ItemNeuralInterface implements IBauble} behind
 * {@code @Optional.Interface}, so javac needs the type to resolve the
 * supertype hierarchy, but it is never packaged into the mod jar and never
 * loaded at runtime (Baubles itself provides the real interface).
 */
public interface IBauble {
}
