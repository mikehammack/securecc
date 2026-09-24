package com.maximarcana.securecc.client;

import com.maximarcana.securecc.net.ManageTarget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Client-side cache of the management state received from the server. When
 * fresh data arrives for the target a {@link GuiSecureManage} screen is
 * showing, the screen refreshes in place.
 */
@SideOnly(Side.CLIENT)
public final class ClientManageCache {
    private static final Map<String, Entry> CACHE = new HashMap<String, Entry>();

    public static final class Entry {
        /** Display title; null means "use the default block title". */
        public final String title;
        public final String ownerName;
        public final List<String> friends;
        public final boolean hasPin;
        public final long pinLockoutSeconds;
        public final int policyId;

        Entry(String title, String ownerName, List<String> friends, boolean hasPin,
              long pinLockoutSeconds, int policyId) {
            this.title = title;
            this.ownerName = ownerName;
            this.friends = new ArrayList<String>(friends);
            this.hasPin = hasPin;
            this.pinLockoutSeconds = pinLockoutSeconds;
            this.policyId = policyId;
        }
    }

    public static void apply(ManageTarget target, String title, String ownerName, List<String> friends,
                            boolean hasPin, long pinLockoutSeconds, int policyId) {
        CACHE.put(target.cacheKey(),
                new Entry(title, ownerName, friends, hasPin, pinLockoutSeconds, policyId));
        GuiScreen screen = Minecraft.getMinecraft().currentScreen;
        if (screen instanceof GuiSecureManage) {
            ((GuiSecureManage) screen).maybeRefresh(target);
        }
    }

    public static Entry get(ManageTarget target) {
        return CACHE.get(target.cacheKey());
    }

    private ClientManageCache() {
    }
}
