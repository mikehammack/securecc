package com.maximarcana.securecc.client;

import com.maximarcana.securecc.Policy;
import com.maximarcana.securecc.net.AddFriendMessage;
import com.maximarcana.securecc.net.RemoveFriendMessage;
import com.maximarcana.securecc.net.RequestManageMessage;
import com.maximarcana.securecc.net.SecureNetwork;
import com.maximarcana.securecc.net.SetPinMessage;
import com.maximarcana.securecc.net.SetPolicyMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiSlot;
import net.minecraft.client.gui.GuiTextField;
import com.maximarcana.securecc.net.ManageTarget;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.ArrayList;
import java.util.List;

/**
 * Management screen for a secure block. The block owner shift-right-clicks
 * a secure block to open it; the state is pulled from the server on open and
 * after every change.
 *
 * Tabs: Friends (add/remove), PIN (set/change/remove, lockout state),
 * Policy (the block's single access policy).
 */
@SideOnly(Side.CLIENT)
public class GuiSecureManage extends GuiScreen {
    private static final int TAB_FRIENDS = 0;
    private static final int TAB_PIN = 1;
    private static final int TAB_POLICY = 2;

    private final ManageTarget target;
    private int tab = TAB_FRIENDS;
    private String title;

    // Shared
    private GuiButton doneButton;
    private GuiButton tabFriendsButton;
    private GuiButton tabPinButton;
    private GuiButton tabPolicyButton;

    // Friends tab
    private GuiTextField nameField;
    private GuiButton addButton;
    private GuiButton removeButton;
    private FriendList list;
    private String ownerName = "?";
    private List<String> friends = new ArrayList<String>();
    private int selected = -1;

    // PIN tab
    private GuiTextField pinField;
    private GuiButton setPinButton;
    private GuiButton removePinButton;
    private boolean hasPin;
    private long pinLockoutSeconds;

    // Policy tab
    private final List<GuiButton> policyButtons = new ArrayList<GuiButton>();
    private int policyId = Policy.LOCK.id;

    public GuiSecureManage(ManageTarget target) {
        this.target = target;
        this.title = null;
    }

    /** Backwards-compatible block constructor. */
    public GuiSecureManage(int dimension, net.minecraft.util.math.BlockPos pos) {
        this(ManageTarget.forBlock(dimension, pos));
    }

    @Override
    public void initGui() {
        int cx = this.width / 2;
        this.buttonList.clear();
        this.policyButtons.clear();
        // Drop widgets from the previous tab so they stop receiving input.
        this.nameField = null;
        this.pinField = null;
        this.list = null;
        this.addButton = null;
        this.removeButton = null;
        this.setPinButton = null;
        this.removePinButton = null;

        // Tab row: three buttons, evenly spaced with real gaps.
        this.tabFriendsButton = new GuiButton(10, cx - 114, 44, 72, 20, "Friends");
        this.tabPinButton = new GuiButton(11, cx - 36, 44, 72, 20, "PIN");
        this.tabPolicyButton = new GuiButton(12, cx + 42, 44, 72, 20, "Policy");
        this.buttonList.add(this.tabFriendsButton);
        this.buttonList.add(this.tabPinButton);
        this.buttonList.add(this.tabPolicyButton);

        if (this.tab == TAB_FRIENDS) {
            // Name field and Add share a row; Remove sits under the list.
            this.nameField = new GuiTextField(0, this.fontRenderer, cx - 100, 88, 132, 20);
            this.nameField.setMaxStringLength(16);
            this.addButton = new GuiButton(1, cx + 36, 88, 64, 20, "Add");
            this.removeButton = new GuiButton(2, cx - 100, this.height - 56, 200, 20,
                    "Remove selected");
            this.buttonList.add(this.addButton);
            this.buttonList.add(this.removeButton);
            this.list = new FriendList(this.mc, this.width, this.height,
                    116, this.height - 62, 20);
        } else if (this.tab == TAB_PIN) {
            this.pinField = new PasswordField(0, this.fontRenderer, cx - 100, 112, 200, 20);
            this.pinField.setMaxStringLength(16);
            this.setPinButton = new GuiButton(3, cx - 100, 138, 98, 20, "Set PIN");
            this.removePinButton = new GuiButton(4, cx + 2, 138, 98, 20, "Remove PIN");
            this.buttonList.add(this.setPinButton);
            this.buttonList.add(this.removePinButton);
        } else {
            Policy[] policies = Policy.values();
            for (int i = 0; i < policies.length; i++) {
                GuiButton b = new GuiButton(20 + i, cx - 100, 80 + i * 26, 200, 20,
                        policyLabel(policies[i]));
                this.policyButtons.add(b);
                this.buttonList.add(b);
            }
        }

        this.doneButton = new GuiButton(5, cx - 100, this.height - 28, 200, 20, "Done");
        this.buttonList.add(this.doneButton);

        this.refreshButtons();
        SecureNetwork.CHANNEL.sendToServer(new RequestManageMessage(this.target));
    }

    /** Called by the client cache when fresh data arrives for our target. */
    public void maybeRefresh(ManageTarget target) {
        if (!this.target.equals(target)) return;
        ClientManageCache.Entry entry = ClientManageCache.get(target);
        if (entry == null) return;
        this.title = entry.title;
        this.ownerName = entry.ownerName;
        this.friends = new ArrayList<String>(entry.friends);
        this.selected = -1;
        this.hasPin = entry.hasPin;
        this.pinLockoutSeconds = entry.pinLockoutSeconds;
        this.policyId = entry.policyId;
        for (int i = 0; i < this.policyButtons.size(); i++) {
            this.policyButtons.get(i).displayString = policyLabel(Policy.values()[i]);
        }
        this.refreshButtons();
    }

    private String policyLabel(Policy policy) {
        String label = policy.display;
        return policy.id == this.policyId ? "\u00a7a\u00bb " + label + " \u00ab" : label;
    }

    private static String policyHint(Policy policy) {
        switch (policy) {
            case STAY_RUNNING:
                return "Friends can use the terminal any time; the computer keeps running.";
            case LOCK:
                return "Friends can use the terminal while the owner is online.";
            case PIN:
                return "Friends (owner online), or anyone who knows the PIN.";
            case FRIENDS:
                return "Only the owner and friends can use the terminal.";
            case SHUTDOWN:
                return "Friends can use the terminal while the owner is online; otherwise it shuts down.";
            default:
                return "";
        }
    }

    private void refreshButtons() {
        this.tabFriendsButton.enabled = this.tab != TAB_FRIENDS;
        this.tabPinButton.enabled = this.tab != TAB_PIN;
        this.tabPolicyButton.enabled = this.tab != TAB_POLICY;
        if (this.removeButton != null) {
            this.removeButton.enabled = this.selected >= 0 && this.selected < this.friends.size();
        }
        if (this.removePinButton != null) {
            this.removePinButton.enabled = this.hasPin;
        }
    }

    private void switchTab(int tab) {
        if (this.tab == tab) return;
        this.tab = tab;
        this.initGui();
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        int id = button.id;
        if (id == 5) {
            this.mc.displayGuiScreen(null);
        } else if (id == 10) {
            this.switchTab(TAB_FRIENDS);
        } else if (id == 11) {
            this.switchTab(TAB_PIN);
        } else if (id == 12) {
            this.switchTab(TAB_POLICY);
        } else if (id == 1) {
            String name = this.nameField.getText().trim();
            // Inline validation (do not touch SecureAccess here: it references
            // server-only classes and must never be loaded on the client).
            if (name.matches("[A-Za-z0-9_]{1,16}")) {
                SecureNetwork.CHANNEL.sendToServer(new AddFriendMessage(this.target, name));
                this.nameField.setText("");
            }
        } else if (id == 2) {
            if (this.selected >= 0 && this.selected < this.friends.size()) {
                SecureNetwork.CHANNEL.sendToServer(
                        new RemoveFriendMessage(this.target, this.friends.get(this.selected)));
            }
        } else if (id == 3) {
            String pin = this.pinField.getText().trim();
            if (!pin.isEmpty()) {
                SecureNetwork.CHANNEL.sendToServer(new SetPinMessage(this.target, pin));
                this.pinField.setText("");
            }
        } else if (id == 4) {
            SecureNetwork.CHANNEL.sendToServer(new SetPinMessage(this.target, ""));
        } else if (id >= 20 && id < 20 + Policy.values().length) {
            Policy policy = Policy.values()[id - 20];
            SecureNetwork.CHANNEL.sendToServer(new SetPolicyMessage(this.target, policy.id));
        }
    }

    @Override
    public void updateScreen() {
        if (this.nameField != null) this.nameField.updateCursorCounter();
        if (this.pinField != null) this.pinField.updateCursorCounter();
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (this.nameField != null && this.nameField.textboxKeyTyped(typedChar, keyCode)) return;
        if (this.pinField != null && this.pinField.textboxKeyTyped(typedChar, keyCode)) return;
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        if (this.nameField != null) this.nameField.mouseClicked(mouseX, mouseY, mouseButton);
        if (this.pinField != null) this.pinField.mouseClicked(mouseX, mouseY, mouseButton);
        // NB: GuiSlot has no mouseClicked/mouseReleased in 1.12.2; list
        // selection is driven entirely through handleMouseInput() below.
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        if (this.list != null) this.list.handleMouseInput();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        int cx = this.width / 2;
        // NB: the strings below are hardcoded rather than pulled from the
        // lang file: en_us.lang was not resolving on the client (raw keys
        // shown), and a security screen must never render unreadable.
        String titleText = this.title != null ? this.title : "Secure Block";
        this.drawCenteredString(this.fontRenderer, titleText, cx, 12, 0xFFFFFF);
        this.drawCenteredString(this.fontRenderer, "Owner: " + this.ownerName,
                cx, 28, 0xAAAAAA);

        if (this.tab == TAB_FRIENDS) {
            this.drawCenteredString(this.fontRenderer, "Player name:", cx, 76, 0xAAAAAA);
            if (this.nameField != null) this.nameField.drawTextBox();
            if (this.list != null) this.list.drawScreen(mouseX, mouseY, partialTicks);
            if (this.friends.isEmpty() && this.list != null) {
                this.drawCenteredString(this.fontRenderer, "No friends yet.",
                        cx, 116 + (this.height - 62 - 116) / 2, 0x777777);
            }
        } else if (this.tab == TAB_PIN) {
            String status = this.hasPin ? "PIN is set" : "No PIN set";
            this.drawCenteredString(this.fontRenderer, status, cx, 78,
                    this.hasPin ? 0x55FF55 : 0xAAAAAA);
            String lockout = this.pinLockoutSeconds > 0
                    ? "PIN locked out: " + this.pinLockoutSeconds + "s remaining"
                    : "No PIN lockout";
            this.drawCenteredString(this.fontRenderer, lockout, cx, 92,
                    this.pinLockoutSeconds > 0 ? 0xFF5555 : 0x777777);
            if (this.pinField != null) this.pinField.drawTextBox();
            this.drawCenteredString(this.fontRenderer,
                    "Minimum 4 characters. Anyone with the PIN can use this", cx, 166, 0x777777);
            this.drawCenteredString(this.fontRenderer,
                    "under the PIN policy.", cx, 176, 0x777777);
        } else {
            Policy current = Policy.byId(this.policyId);
            this.drawCenteredString(this.fontRenderer, "Access policy:", cx, 68, 0xAAAAAA);
            this.drawCenteredString(this.fontRenderer, policyHint(current), cx,
                    80 + Policy.values().length * 26 + 8, 0xAAAAAA);
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    private class FriendList extends GuiSlot {
        FriendList(Minecraft mc, int width, int height, int top, int bottom, int slotHeight) {
            super(mc, width, height, top, bottom, slotHeight);
        }

        @Override
        protected int getSize() {
            return friends.size();
        }

        @Override
        protected void elementClicked(int slotIndex, boolean isDoubleClick, int mouseX, int mouseY) {
            selected = slotIndex;
            refreshButtons();
        }

        @Override
        protected boolean isSelected(int slotIndex) {
            return slotIndex == selected;
        }

        @Override
        protected void drawBackground() {
        }

        @Override
        protected void drawSlot(int slotIndex, int xPos, int yPos, int heightIn,
                                int mouseXIn, int mouseYIn, float partialTicks) {
            GuiSecureManage.this.drawCenteredString(GuiSecureManage.this.fontRenderer,
                    friends.get(slotIndex), FriendList.this.width / 2, yPos + 4, 0xFFFFFF);
        }

        @Override
        public int getListWidth() {
            return 200;
        }
    }

    /**
     * A text field that masks its contents with bullets while drawing, so a
     * PIN being typed is not readable on screen. The real text (and the
     * caret/selection, which sit at length-identical positions) is restored
     * immediately after drawing, so editing behaves exactly like a normal
     * field.
     */
    private static class PasswordField extends GuiTextField {
        PasswordField(int id, FontRenderer fontRenderer, int x, int y, int w, int h) {
            super(id, fontRenderer, x, y, w, h);
        }

        @Override
        public void drawTextBox() {
            String real = getText();
            int cursor = getCursorPosition();
            int selectionEnd = getSelectionEnd();
            StringBuilder mask = new StringBuilder(real.length());
            for (int i = 0; i < real.length(); i++) mask.append('*');
            setText(mask.toString());
            setCursorPosition(cursor);
            setSelectionPos(selectionEnd);
            super.drawTextBox();
            setText(real);
            setCursorPosition(cursor);
            setSelectionPos(selectionEnd);
        }
    }
}
