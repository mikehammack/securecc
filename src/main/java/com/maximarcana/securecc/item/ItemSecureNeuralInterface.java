package com.maximarcana.securecc.item;

import com.maximarcana.securecc.SecureAccess;
import com.maximarcana.securecc.SecureCC;
import com.maximarcana.securecc.SecureGuiHandler;
import dan200.computercraft.ComputerCraft;
import baubles.api.BaublesApi;
import baubles.api.cap.IBaublesItemHandler;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.play.server.SPacketEntityEquipment;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Optional;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.squiddev.plethora.gameplay.neural.ItemComputerHandler;
import org.squiddev.plethora.gameplay.neural.ItemNeuralInterface;
import org.squiddev.plethora.gameplay.neural.NeuralComputer;
import org.squiddev.plethora.gameplay.neural.NeuralHelpers;
import org.squiddev.plethora.utils.Helpers;
import org.squiddev.plethora.utils.TinySlot;

/**
 * Plethora neural interface with SecureCC owner / friend / PIN / policy
 * gating, so players can secure their own neural interface and the ones
 * they put on mobs.
 *
 * <p>The full {@link SecureAccess} state is serialized into the stack NBT
 * under {@value #NBT_KEY}. An interface with no owner behaves like the
 * vanilla one; the first player to craft it, wear it, or put it on a mob
 * becomes its owner. Anyone else is denied at every step:</p>
 * <ul>
 *   <li>equipping it as armor or as a Baubles trinket,</li>
 *   <li>putting it on a mob,</li>
 *   <li>opening a mob's terminal through a neural connector,</li>
 *   <li>running its computer while wearing it (backstop: the computer is
 *   shut down for unauthorized wearers).</li>
 * </ul>
 */
public class ItemSecureNeuralInterface extends ItemNeuralInterface {
    public static final String REGISTRY_NAME = "secure_neural_interface";
    /** Stack NBT key holding the serialized SecureAccess. */
    public static final String NBT_KEY = "SecureCC";

    public ItemSecureNeuralInterface() {
        super();
        // Plethora's constructor registers the name "plethora:neuralInterface"
        // on itself; clear it so we can register under our own mod id.
        // setRegistryName is final in Forge, so this goes through reflection.
        clearRegistryName();
        setRegistryName(SecureCC.MODID, REGISTRY_NAME);
        setTranslationKey(SecureCC.MODID + ".secure_neural_interface");
        setCreativeTab(ComputerCraft.mainCreativeTab);
    }

    /**
     * Hardcoded display name, matching the convention of the secure block
     * items (which also override this instead of relying on the lang file).
     */
    @Override
    public String getItemStackDisplayName(ItemStack stack) {
        return "Secure Neural Interface";
    }

    /**
     * Render the neural interface on mobs wearing it, like Plethora's
     * standard interface. Returns Plethora's interface model directly.
     */
    @Override
    @SideOnly(Side.CLIENT)
    public net.minecraft.client.model.ModelBiped getArmorModel(
            EntityLivingBase entityLiving, ItemStack itemStack,
            net.minecraft.inventory.EntityEquipmentSlot armorSlot,
            net.minecraft.client.model.ModelBiped defaultModel) {
        return org.squiddev.plethora.gameplay.client.ModelInterface.getNormal();
    }

    private static final java.lang.reflect.Field REGISTRY_NAME_FIELD = findRegistryNameField();

    private static java.lang.reflect.Field findRegistryNameField() {
        try {
            java.lang.reflect.Field f = net.minecraftforge.registries.IForgeRegistryEntry.Impl.class
                    .getDeclaredField("registryName");
            f.setAccessible(true);
            return f;
        } catch (NoSuchFieldException e) {
            throw new RuntimeException("SecureCC: could not find IForgeRegistryEntry.Impl#registryName", e);
        }
    }

    private void clearRegistryName() {
        try {
            REGISTRY_NAME_FIELD.set(this, null);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("SecureCC: could not clear inherited registry name", e);
        }
    }

    // ---------- access state ----------

    /**
     * The secure neural interface stack worn by the entity (armor slot or
     * Baubles head slot), or {@link ItemStack#EMPTY} when the entity wears
     * none or wears Plethora's vanilla interface.
     *
     * <p>NB: Plethora's {@code NeuralHelpers.getStack}/{@code getSlot}
     * compare the equipped stack against their own registered item by
     * instance identity, so they never recognize our subclass. We scan the
     * slots directly instead and match with {@code instanceof}.</p>
     */
    public static ItemStack getWornSecureInterface(EntityLivingBase entity) {
        if (entity == null) return ItemStack.EMPTY;
        ItemStack armor = entity.getItemStackFromSlot(NeuralHelpers.ARMOR_SLOT);
        if (!armor.isEmpty() && armor.getItem() instanceof ItemSecureNeuralInterface) {
            return armor;
        }
        if (hasBaubles() && entity instanceof EntityPlayer) {
            IBaublesItemHandler handler =
                    BaublesApi.getBaublesHandler((EntityPlayer) entity);
            if (handler != null) {
                for (int slot = 0; slot < handler.getSlots(); slot++) {
                    ItemStack stack = handler.getStackInSlot(slot);
                    if (!stack.isEmpty()
                            && stack.getItem() instanceof ItemSecureNeuralInterface) {
                        return stack;
                    }
                }
            }
        }
        return ItemStack.EMPTY;
    }

    /** Cached probe for the Baubles API classes (an optional dependency). */
    private static Boolean baublesPresent;

    private static boolean hasBaubles() {
        if (baublesPresent == null) {
            boolean present;
            try {
                Class.forName("baubles.api.BaublesApi");
                present = true;
            } catch (ClassNotFoundException e) {
                present = false;
            }
            baublesPresent = present;
        }
        return baublesPresent;
    }

    /** The access rules for the interface in this stack (unowned when absent). */
    public SecureAccess getAccess(ItemStack stack) {
        SecureAccess access = new SecureAccess();
        NBTTagCompound tag = stack.getTagCompound();
        if (tag != null && tag.hasKey(NBT_KEY, 10)) {
            access.readFromNBT(tag.getCompoundTag(NBT_KEY));
        }
        return access;
    }

    public void saveAccess(ItemStack stack, SecureAccess access) {
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) {
            tag = new NBTTagCompound();
            stack.setTagCompound(tag);
        }
        tag.setTag(NBT_KEY, access.writeToNBT(new NBTTagCompound()));
    }

    /**
     * True when the player may use the interface in this stack. An interface
     * with no owner behaves like the vanilla one.
     */
    public boolean canUse(ItemStack stack, EntityPlayer player) {
        if (player == null) return false;
        SecureAccess access = getAccess(stack);
        if (!access.hasOwner()) return true;
        return access.canUseTerminal(player, player.getEntityWorld());
    }

    /** Claim an unowned interface for the given player (first use wins). */
    public void claim(ItemStack stack, EntityPlayer player) {
        SecureAccess access = getAccess(stack);
        if (!access.hasOwner()) {
            access.setOwner(player);
            saveAccess(stack, access);
        }
    }

    private void deny(EntityPlayer player) {
        player.sendMessage(new TextComponentString(
                "§c[SecureCC] You are not authorized to use this neural interface."));
    }

    /** The worn armor model uses the secure texture, not Plethora's. */
    @Override
    @SideOnly(Side.CLIENT)
    public String getArmorTexture(ItemStack stack, net.minecraft.entity.Entity entity,
                                 net.minecraft.inventory.EntityEquipmentSlot slot, String type) {
        return "securecc:textures/models/secure_neural_interface.png";
    }

    // ---------- equip gating ----------

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        ItemStack stack = player.getHeldItem(hand);
        if (!world.isRemote) {
            if (!canUse(stack, player)) {
                deny(player);
                return new ActionResult<>(EnumActionResult.FAIL, stack);
            }
            claim(stack, player);
        }
        return super.onItemRightClick(world, player, hand);
    }

    @Override
    public boolean itemInteractionForEntity(ItemStack stack, EntityPlayer player,
                                           EntityLivingBase target, EnumHand hand) {
        if (!player.getEntityWorld().isRemote) {
            if (!canUse(stack, player)) {
                deny(player);
                return false;
            }
            claim(stack, player);
        }
        return super.itemInteractionForEntity(stack, player, target, hand);
    }

    @Override
    @Optional.Method(modid = "baubles")
    public boolean canEquip(ItemStack stack, EntityLivingBase player) {
        if (player instanceof EntityPlayer && !player.getEntityWorld().isRemote
                && !canUse(stack, (EntityPlayer) player)) {
            return false;
        }
        return super.canEquip(stack, player);
    }

    // ---------- backstop: an unauthorized wearer gets no computer ----------

    @Override
    public void onArmorTick(World world, EntityPlayer player, ItemStack stack) {
        if (!world.isRemote && !canUse(stack, player)) {
            NeuralComputer computer = ItemComputerHandler.tryGetServer(stack);
            if (computer != null) computer.shutdown();
            return;
        }
        super.onArmorTick(world, player, stack);
    }

    @Override
    @Optional.Method(modid = "baubles")
    public void onWornTick(ItemStack stack, EntityLivingBase player) {
        if (player instanceof EntityPlayer && !player.getEntityWorld().isRemote
                && !canUse(stack, (EntityPlayer) player)) {
            NeuralComputer computer = ItemComputerHandler.tryGetServer(stack);
            if (computer != null) computer.shutdown();
            return;
        }
        super.onWornTick(stack, player);
    }

    // ---------- mob equipping and connector GUI gating ----------

    /**
     * Ensure the worn interface's computer exists and sync the stack's NBT
     * to the player opening the GUI. {@link ItemComputerHandler#getServer}
     * may write a fresh computer ID into the server-side stack; without an
     * explicit equipment sync the client never sees it and the terminal
     * renders blank.
     */
    private static void syncWornInterface(EntityPlayer player, EntityLivingBase target,
                                         ItemStack worn) {
        if (!(player instanceof EntityPlayerMP)) return;
        EntityPlayerMP mp = (EntityPlayerMP) player;
        // The interface lives in the head armor slot or a Baubles slot; the
        // armor-slot case is synced with a vanilla equipment packet.
        if (target.getItemStackFromSlot(NeuralHelpers.ARMOR_SLOT) == worn) {
            mp.connection.sendPacket(new SPacketEntityEquipment(
                    target.getEntityId(), EntityEquipmentSlot.HEAD, worn));
        }
        // Baubles slots sync through the Baubles network on their next
        // tick; the computer ID is already in the server-side NBT.
    }

    @Mod.EventBusSubscriber(modid = SecureCC.MODID)
    public static class Handler {
        private static final ResourceLocation NEURAL_CONNECTOR =
                new ResourceLocation("plethora", "neuralconnector");

        /**
         * Plethora's own entity-interact handler only equips its registered
         * item, so replicate the equip flow for the secure item (with auth).
         */
        @SubscribeEvent
        public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
            if (event.isCanceled() || event.getWorld().isRemote) return;
            ItemStack held = event.getItemStack();
            if (held.isEmpty() || !(held.getItem() instanceof ItemSecureNeuralInterface)) return;
            EntityPlayer player = event.getEntityPlayer();
            ItemSecureNeuralInterface item = (ItemSecureNeuralInterface) held.getItem();
            if (!item.canUse(held, player)) {
                item.deny(player);
                event.setCanceled(true);
                return;
            }
            item.claim(held, player);
            if (Helpers.onEntityInteract(item, player, event.getTarget(), event.getHand())) {
                event.setCanceled(true);
            }
        }

        /**
         * Gate the neural connector GUI: opening a mob's terminal when the
         * mob wears a secure neural interface requires the interface's
         * access. Runs before Plethora's handler so a denied player never
         * sees the screen. Plethora's own handler cannot open its GUI for
         * our subclass (it looks up its own registered item by identity), so
         * an authorized player is served through our own GUI path instead.
         */
        @SubscribeEvent(priority = EventPriority.HIGHEST)
        public static void onEntityInteractGui(PlayerInteractEvent.EntityInteract event) {
            if (event.isCanceled() || event.getWorld().isRemote) return;
            if (!(event.getTarget() instanceof EntityLivingBase)) return;
            ItemStack held = event.getItemStack();
            if (held.isEmpty() || !NEURAL_CONNECTOR.equals(held.getItem().getRegistryName())) return;
            EntityLivingBase target = (EntityLivingBase) event.getTarget();
            ItemStack worn = ItemSecureNeuralInterface.getWornSecureInterface(target);
            if (worn.isEmpty()) return;
            EntityPlayer player = event.getEntityPlayer();
            ItemSecureNeuralInterface item = (ItemSecureNeuralInterface) worn.getItem();
            if (!item.canUse(worn, player)) {
                item.deny(player);
                event.setCanceled(true);
                return;
            }
            NeuralComputer computer =
                    ItemComputerHandler.getServer(worn, target, new TinySlot(worn));
            if (computer != null) computer.turnOn();
            syncWornInterface(player, target, worn);
            SecureGuiHandler.openNeuralEntity(player, target);
            event.setCanceled(true);
        }

        /**
         * Open the player's own neural interface GUI with the neural
         * connector. Plethora's connector handler cannot do this for our
         * subclass (its slot lookup only recognizes its own registered
         * item), so we do the auth check, power the computer on, and open
         * the neural screen through our own GUI path.
         */
        @SubscribeEvent
        public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
            if (event.isCanceled() || event.getWorld().isRemote) return;
            ItemStack held = event.getItemStack();
            if (held.isEmpty() || !NEURAL_CONNECTOR.equals(held.getItem().getRegistryName())) return;
            EntityPlayer player = event.getEntityPlayer();
            ItemStack worn = ItemSecureNeuralInterface.getWornSecureInterface(player);
            if (worn.isEmpty()) return;
            ItemSecureNeuralInterface item = (ItemSecureNeuralInterface) worn.getItem();
            if (!item.canUse(worn, player)) {
                item.deny(player);
                event.setCanceled(true);
                return;
            }
            NeuralComputer computer =
                    ItemComputerHandler.getServer(worn, player, new TinySlot(worn));
            if (computer != null) computer.turnOn();
            syncWornInterface(player, player, worn);
            SecureGuiHandler.openNeuralPlayer(player);
            event.setCanceled(true);
        }
    }
}
