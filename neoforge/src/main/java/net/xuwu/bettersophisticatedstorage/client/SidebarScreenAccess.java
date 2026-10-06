package net.xuwu.bettersophisticatedstorage.client;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.world.item.ItemStack;
import net.xuwu.bettersophisticatedstorage.common.NetworkStorageSlot;

import java.util.List;

/** Accessors implemented by the container-screen mixin. */
public interface SidebarScreenAccess
{
    EditBox bss$getSearchBox();
    SidebarDisplayEvent bss$getSidebarDisplayEvent();
    boolean bss$isSidebarEnabled();
    boolean bss$isSidebarExcludedScreen();
    boolean bss$isSidebarButtonEnabled(SidebarDisplayEvent.ButtonId button);

    Button bss$getPlayerShiftButton();

    Button bss$getContainerShiftButton();

    Button bss$getDepositContainerButton();

    Button bss$getDepositPlayerButton();

    Button bss$getSidebarToggleButton();

    ItemStack bss$getCarried();

    void bss$markSidebarMouseRelease();

    boolean bss$consumeSidebarMouseRelease();

    boolean bss$isSidebarHidden();

    boolean bss$isSidebarDragging();

    void bss$beginSidebarDrag(double mouseX, double mouseY);

    void bss$dragSidebarTo(double mouseX, double mouseY);

    boolean bss$endSidebarDrag(double mouseX, double mouseY);

    void bss$toggleSidebarVisibility();

    int bss$getSidebarX();

    int bss$getSidebarY();

    int bss$getSidebarHeight();

    List<NetworkStorageSlot> bss$getSidebarSlots();

    void bss$rebuildSidebarSlots();

    void bss$updateSidebarSlots(List<ClientStorageView.Entry> entries);

}

