package net.xuwu.bettersophisticatedstorage.client;

import net.minecraft.client.gui.components.EditBox;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.xuwu.bettersophisticatedstorage.BetterSophisticatedStorage;
import net.xuwu.bettersophisticatedstorage.NetworkHandler;

/** Consumes sidebar wheel input before inventory-wheel transfer mods can handle it. */
@EventBusSubscriber(modid = BetterSophisticatedStorage.MODID, value = Dist.CLIENT)
public final class SidebarClientEvents
{
    private SidebarClientEvents()
    {
    }

    @SubscribeEvent
    public static void onClientLoggingIn(ClientPlayerNetworkEvent.LoggingIn event)
    {
        // Packet sequence numbers and the storage view belong to one server connection.
        // Clear them before the first snapshot of a new world/server is accepted.
        ClientStorageState.clear();
    }

    @SubscribeEvent
    public static void onClientLoggingOut(ClientPlayerNetworkEvent.LoggingOut event)
    {
        ClientStorageState.clear();
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onMouseScrolled(ScreenEvent.MouseScrolled.Pre event)
    {
        if (event.getScreen() instanceof SidebarScreenAccess host
                && host.bss$getSearchBox() != null
                && SidebarRenderer.handleScroll(
                        host, event.getMouseX(), event.getMouseY(), event.getScrollDeltaY()))
        {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onKeyPressed(ScreenEvent.KeyPressed.Pre event)
    {
        if (!(event.getScreen() instanceof SidebarScreenAccess host)
                || host.bss$isSidebarExcludedScreen())
        {
            return;
        }

        // The JSON option deliberately removes the sidebar widgets but keeps the two
        // keyboard actions available on every compatible container screen.
        boolean shortcutOnly = SidebarSettingsStore.sidebarDisabled();
        if (!shortcutOnly && (host.bss$getSearchBox() == null
                || !host.bss$isSidebarEnabled() || host.bss$isSidebarHidden()))
        {
            return;
        }

        if (!shortcutOnly)
        {
            EditBox sidebarSearchBox = host.bss$getSearchBox();
            if (sidebarSearchBox.isFocused() && sidebarSearchBox.active && sidebarSearchBox.visible)
            {
                // The sidebar search box keeps the key for text editing, while the screen must not
                // dispatch it to inventory or any other UI shortcut (for example, the E key).
                sidebarSearchBox.keyPressed(event.getKeyCode(), event.getScanCode(), event.getModifiers());
                event.setCanceled(true);
                return;
            }
        }

        if (!ClientStorageState.available()
                // Other mods' search boxes only suppress this mod's deposit shortcuts.
                || TextInputFocusTracker.isTextInputFocused(event.getScreen()))
        {
            return;
        }

        boolean disableConflictingKeys = SidebarSettingsStore.disableConflictingKeys();

        if (SidebarKeyMappings.DEPOSIT_CONTAINER.matches(event.getKeyCode(), event.getScanCode())
                && (shortcutOnly
                || host.bss$isSidebarButtonEnabled(SidebarDisplayEvent.ButtonId.DEPOSIT_CONTAINER)))
        {
            if (shortcutOnly)
            {
                NetworkHandler.depositContainerShortcut();
            }
            else
            {
                NetworkHandler.depositContainer();
            }
            if (disableConflictingKeys)
            {
                event.setCanceled(true);
            }
            return;
        }
        if (SidebarKeyMappings.DEPOSIT_PLAYER.matches(event.getKeyCode(), event.getScanCode())
                && (shortcutOnly
                || host.bss$isSidebarButtonEnabled(SidebarDisplayEvent.ButtonId.DEPOSIT_PLAYER)))
        {
            if (shortcutOnly)
            {
                NetworkHandler.depositPlayerInventoryShortcut();
            }
            else
            {
                NetworkHandler.depositPlayerInventory();
            }
            if (disableConflictingKeys)
            {
                event.setCanceled(true);
            }
        }
    }
}

