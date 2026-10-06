package net.xuwu.bettersophisticatedstorage.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.xuwu.bettersophisticatedstorage.NetworkHandler;
import net.xuwu.bettersophisticatedstorage.common.NetworkStorageSlot;
import net.xuwu.bettersophisticatedstorage.common.StorageSnapshot;

import java.util.List;

/** Renders a compact searchable storage view beside supported container screens. */
public final class SidebarRenderer
{
    public static final int SLOT_COLUMNS = 5;
    public static final int WIDTH = SLOT_COLUMNS * 18 + 14;
    public static final int SEARCH_LEFT = 4;
    public static final int TOGGLE_WIDTH = 12;
    public static final int CONTROL_GAP = 3;
    private static final int GRID_TOP = 54;
    private static final int SLOT_HEIGHT = 18;
    private static final int PANEL_BOTTOM_HEIGHT = 7;
    public static final int MAX_VISIBLE_ROWS = 8;

    private static final ClientStorageView STORAGE_VIEW = new ClientStorageView();
    private static String lastSearch = "";

    private SidebarRenderer()
    {
    }

    public static int getPanelHeight()
    {
        return GRID_TOP + visibleRows() * SLOT_HEIGHT + PANEL_BOTTOM_HEIGHT;
    }

    public static int getGridTop()
    {
        return GRID_TOP;
    }

    public static int getVisibleRows()
    {
        return visibleRows();
    }

    public static int getSearchWidth()
    {
        return WIDTH - SEARCH_LEFT - TOGGLE_WIDTH - CONTROL_GAP * 2;
    }

    public static int getToggleX(int sidebarX)
    {
        return sidebarX + WIDTH - TOGGLE_WIDTH - CONTROL_GAP;
    }

    public static void prepareSlots(SidebarScreenAccess host)
    {
        updateSearchHint(host.bss$getSearchBox());
        if (!host.bss$isSidebarEnabled() || !ClientStorageState.available() || host.bss$isSidebarHidden())
        {
            host.bss$updateSidebarSlots(List.of());
            return;
        }

        List<ClientStorageView.Entry> entries = entries(host);
        syncScrollState(host.bss$getSearchBox().getValue(), entries.size());
        host.bss$updateSidebarSlots(entries);
    }

    public static void updateSearchHint(EditBox search)
    {
        if (search == null)
        {
            return;
        }
        Font font = Minecraft.getInstance().font;
        String text = Component.translatable("better_sophisticated_storage.search").getString();
        // Suggestions are drawn after entered text; a hint belongs only to the empty search field.
        search.setSuggestion(null);
        search.setHint(Component.literal(SidebarText.ellipsize(text,
                Math.max(0, search.getInnerWidth() - 1), font::width)));
    }

    public static void render(SidebarScreenAccess host, GuiGraphics graphics, int mouseX, int mouseY,
                              float partialTick)
    {
        StorageSnapshot snapshot = ClientStorageState.snapshot();
        if (!host.bss$isSidebarEnabled() || !snapshot.available())
        {
            host.bss$updateSidebarSlots(List.of());
            setWidgetsVisible(host, false);
            host.bss$getSidebarToggleButton().visible = false;
            host.bss$getSidebarToggleButton().active = false;
            return;
        }

        syncWidgets(host);
        if (host.bss$isSidebarHidden())
        {
            host.bss$updateSidebarSlots(List.of());
            renderWidgets(host, graphics, mouseX, mouseY, partialTick);
            return;
        }

        List<ClientStorageView.Entry> entries = entries(host);
        int rows = visibleRows();
        syncScrollState(host.bss$getSearchBox().getValue(), entries.size());
        host.bss$updateSidebarSlots(entries);

        int x = host.bss$getSidebarX();
        int y = host.bss$getSidebarY();
        Font font = Minecraft.getInstance().font;
        drawBackground(graphics, x, y, rows);
        String networkName = snapshot.networkName().isEmpty()
                ? Component.translatable("better_sophisticated_storage.network").getString()
                : snapshot.networkName();
        graphics.drawString(font, trim(font, networkName, 50), x + 5, y + 7, 0xFF404040, false);

        int firstEntry = ClientStorageState.scrollRow() * SLOT_COLUMNS;
        for (int row = 0; row < rows; row++)
        {
            for (int col = 0; col < SLOT_COLUMNS; col++)
            {
                int entryIndex = firstEntry + row * SLOT_COLUMNS + col;
                int slotX = x + 8 + col * 18;
                int slotY = y + GRID_TOP + row * SLOT_HEIGHT + 1;
                if (isHovered(x, y, mouseX, mouseY, row, col))
                {
                    graphics.fill(slotX, slotY, slotX + 16, slotY + 16, 0x80FFFFFF);
                }

                int slotIndex = row * SLOT_COLUMNS + col;
                if (slotIndex >= host.bss$getSidebarSlots().size()
                        || entryIndex < 0 || entryIndex >= entries.size())
                {
                    continue;
                }
                NetworkStorageSlot slot = host.bss$getSidebarSlots().get(slotIndex);
                if (!slot.hasItem())
                {
                    continue;
                }
                ItemStack renderStack = slot.copyViewStack();
                graphics.renderItem(renderStack, slotX, slotY);
                // Vanilla raises decorations above the item model (Z + 200) and restores the pose.
                graphics.renderItemDecorations(font, renderStack, slotX, slotY,
                        SidebarText.amountLabel(slot.getStoredAmount()));
            }
        }
        renderWidgets(host, graphics, mouseX, mouseY, partialTick);
    }

    public static boolean handleMouseClick(SidebarScreenAccess host, double mouseX, double mouseY, int button)
    {
        if (!host.bss$isSidebarEnabled())
        {
            return false;
        }
        EditBox search = host.bss$getSearchBox();
        if (search == null)
        {
            return false;
        }

        Button toggle = host.bss$getSidebarToggleButton();
        if (button == 0 && toggle.visible && toggle.active && toggle.isMouseOver(mouseX, mouseY))
        {
            if (search.isFocused())
            {
                ((Screen) (Object) host).setFocused(null);
                search.setFocused(false);
            }
            host.bss$beginSidebarDrag(mouseX, mouseY);
            return true;
        }

        if (search.visible && search.active && search.isMouseOver(mouseX, mouseY))
        {
            if (button == 0 || button == 1)
            {
                Screen screen = (Screen) (Object) host;
                screen.setFocused(search);
                search.setFocused(true);
                if (button == 0)
                {
                    search.mouseClicked(mouseX, mouseY, button);
                }
                else
                {
                    search.setValue("");
                }
                return true;
            }
            return false;
        }

        if (search.isFocused())
        {
            ((Screen) (Object) host).setFocused(null);
            search.setFocused(false);
        }

        NetworkStorageSlot slot = findSlotAt(host, mouseX, mouseY);
        if (slot != null && (button == 0 || button == 1))
        {
            ClickType clickType = Screen.hasShiftDown() ? ClickType.QUICK_MOVE : ClickType.PICKUP;
            NetworkHandler.clickSidebarSlot(slot.index, button, clickType);
            return true;
        }
        return false;
    }

    public static boolean handleMouseDrag(SidebarScreenAccess host, double mouseX, double mouseY, int button)
    {
        if (!host.bss$isSidebarEnabled() || button != 0 || !host.bss$isSidebarDragging())
        {
            return false;
        }
        host.bss$dragSidebarTo(mouseX, mouseY);
        return true;
    }

    public static boolean handleMouseRelease(SidebarScreenAccess host, double mouseX, double mouseY, int button)
    {
        if (button == 0 && host.bss$isSidebarDragging())
        {
            boolean dragged = host.bss$endSidebarDrag(mouseX, mouseY);
            host.bss$consumeSidebarMouseRelease();
            if (!dragged)
            {
                host.bss$toggleSidebarVisibility();
            }
            return true;
        }
        return host.bss$consumeSidebarMouseRelease();
    }

    public static boolean handleScroll(SidebarScreenAccess host, double mouseX, double mouseY, double scrollAmount)
    {
        if (!host.bss$isSidebarEnabled() || !ClientStorageState.available()
                || host.bss$isSidebarHidden() || scrollAmount == 0.0D)
        {
            return false;
        }
        int x = host.bss$getSidebarX();
        int y = host.bss$getSidebarY();
        int rows = visibleRows();
        if (mouseX < x || mouseX >= x + WIDTH || mouseY < y + GRID_TOP
                || mouseY >= y + GRID_TOP + rows * SLOT_HEIGHT)
        {
            return false;
        }

        int totalRows = (entries(host).size() + SLOT_COLUMNS - 1) / SLOT_COLUMNS;
        int maxScroll = Math.max(0, totalRows - rows);
        int direction = scrollAmount > 0.0D ? -1 : 1;
        ClientStorageState.setScrollRow(Math.max(0, Math.min(maxScroll,
                ClientStorageState.scrollRow() + direction)));
        return true;
    }

    public static void renderTooltip(SidebarScreenAccess host, GuiGraphics graphics, int mouseX, int mouseY)
    {
        renderButtonTooltip(host, mouseX, mouseY);
    }

    /** Lets the normal container tooltip renderer show the item under the real sidebar slot. */
    public static boolean renderStorageTooltip(SidebarScreenAccess host, GuiGraphics graphics,
                                               int mouseX, int mouseY)
    {
        return false;
    }

    private static void renderButtonTooltip(SidebarScreenAccess host, int mouseX, int mouseY)
    {
        Button[] buttons = {host.bss$getPlayerShiftButton(), host.bss$getContainerShiftButton(),
                host.bss$getDepositContainerButton(), host.bss$getDepositPlayerButton(),
                host.bss$getSidebarToggleButton()};
        for (Button button : buttons)
        {
            if (button.visible && button.isMouseOver(mouseX, mouseY) && button.getTooltip() != null)
            {
                ((Screen) (Object) host).setTooltipForNextRenderPass(
                        button.getTooltip().toCharSequence(Minecraft.getInstance()));
                return;
            }
        }
    }

    private static void drawBackground(GuiGraphics graphics, int x, int y, int rows)
    {
        int height = GRID_TOP + rows * SLOT_HEIGHT + PANEL_BOTTOM_HEIGHT;
        // Match Better Beyond Dimensions' vanilla panel frame without depending on its textures.
        graphics.fill(x, y, x + WIDTH, y + height, 0xFF000000);
        graphics.fill(x + 1, y + 1, x + WIDTH - 1, y + height - 3, 0xFFF1F1F1);
        graphics.fill(x + 2, y + 2, x + WIDTH - 2, y + height - 4, 0xFFC6C6C6);
        graphics.fill(x + 1, y + height - 3, x + WIDTH - 1, y + height - 1, 0xFF555555);
        for (int row = 0; row < rows; row++)
        {
            int rowY = y + GRID_TOP + row * SLOT_HEIGHT;
            for (int col = 0; col < SLOT_COLUMNS; col++)
            {
                int cellX = x + 7 + col * SLOT_HEIGHT;
                // An 18px vanilla recess surrounds the 16px item area; diagonal corners stay gray.
                graphics.fill(cellX, rowY, cellX + 18, rowY + 18, 0xFF8B8B8B);
                graphics.fill(cellX, rowY, cellX + 17, rowY + 1, 0xFF373737);
                graphics.fill(cellX, rowY + 1, cellX + 1, rowY + 17, 0xFF373737);
                graphics.fill(cellX + 17, rowY + 1, cellX + 18, rowY + 18, 0xFFFFFFFF);
                graphics.fill(cellX + 1, rowY + 17, cellX + 17, rowY + 18, 0xFFFFFFFF);
            }
        }
    }

    private static void syncWidgets(SidebarScreenAccess host)
    {
        boolean visible = !host.bss$isSidebarHidden();
        setWidgetsVisible(host, visible);
        Button toggle = host.bss$getSidebarToggleButton();
        toggle.visible = true;
        configureButton(host, toggle, SidebarDisplayEvent.ButtonId.SIDEBAR_TOGGLE,
                Component.literal(host.bss$isSidebarHidden() ? "+" : "×"),
                Component.translatable(host.bss$isSidebarHidden()
                        ? "better_sophisticated_storage.tooltip.show_sidebar"
                        : "better_sophisticated_storage.tooltip.hide_sidebar"));
        StorageSnapshot snapshot = ClientStorageState.snapshot();
        configureButton(host, host.bss$getPlayerShiftButton(), SidebarDisplayEvent.ButtonId.PLAYER_SHIFT,
                Component.translatable("better_sophisticated_storage.button.shift_player",
                        snapshot.shiftPlayerInventory() ? "✓" : "×"),
                Component.translatable("better_sophisticated_storage.tooltip.shift_player"));
        configureButton(host, host.bss$getContainerShiftButton(), SidebarDisplayEvent.ButtonId.CONTAINER_SHIFT,
                Component.translatable("better_sophisticated_storage.button.shift_container",
                        snapshot.shiftContainer() ? "✓" : "×"),
                Component.translatable("better_sophisticated_storage.tooltip.shift_container"));
        configureButton(host, host.bss$getDepositContainerButton(), SidebarDisplayEvent.ButtonId.DEPOSIT_CONTAINER,
                Component.translatable("better_sophisticated_storage.button.deposit_container"),
                shortcutTooltip("better_sophisticated_storage.tooltip.deposit_container",
                        SidebarKeyMappings.DEPOSIT_CONTAINER));
        configureButton(host, host.bss$getDepositPlayerButton(), SidebarDisplayEvent.ButtonId.DEPOSIT_PLAYER,
                Component.translatable("better_sophisticated_storage.button.deposit_player"),
                shortcutTooltip("better_sophisticated_storage.tooltip.deposit_player",
                        SidebarKeyMappings.DEPOSIT_PLAYER));
    }

    private static Component shortcutTooltip(String tooltipKey, KeyMapping mapping)
    {
        return Component.translatable(tooltipKey)
                .append(Component.literal("\n"))
                .append(Component.translatable("better_sophisticated_storage.tooltip.shortcut",
                        mapping.getTranslatedKeyMessage()));
    }

    private static void configureButton(SidebarScreenAccess host, Button button,
                                        SidebarDisplayEvent.ButtonId buttonId,
                                        Component defaultMessage, Component defaultTooltip)
    {
        SidebarDisplayEvent event = host.bss$getSidebarDisplayEvent();
        Component message = event == null ? null : event.getButtonMessage(buttonId);
        Component tooltip = event == null ? null : event.getButtonTooltip(buttonId);
        button.setMessage(message == null ? defaultMessage : message);
        button.setTooltip(Tooltip.create(tooltip == null ? defaultTooltip : tooltip));
        button.active = button.visible && host.bss$isSidebarButtonEnabled(buttonId);
    }

    private static void setWidgetsVisible(SidebarScreenAccess host, boolean visible)
    {
        host.bss$getSearchBox().visible = visible;
        host.bss$getSearchBox().active = visible;
        host.bss$getPlayerShiftButton().visible = visible;
        host.bss$getPlayerShiftButton().active = visible;
        host.bss$getContainerShiftButton().visible = visible;
        host.bss$getContainerShiftButton().active = visible;
        host.bss$getDepositContainerButton().visible = visible;
        host.bss$getDepositContainerButton().active = visible;
        host.bss$getDepositPlayerButton().visible = visible;
        host.bss$getDepositPlayerButton().active = visible;
    }

    private static void renderWidgets(SidebarScreenAccess host, GuiGraphics graphics, int mouseX, int mouseY,
                                      float partialTick)
    {
        host.bss$getSidebarToggleButton().render(graphics, mouseX, mouseY, partialTick);
        if (host.bss$isSidebarHidden())
        {
            return;
        }
        host.bss$getSearchBox().render(graphics, mouseX, mouseY, partialTick);
        host.bss$getPlayerShiftButton().render(graphics, mouseX, mouseY, partialTick);
        host.bss$getContainerShiftButton().render(graphics, mouseX, mouseY, partialTick);
        host.bss$getDepositContainerButton().render(graphics, mouseX, mouseY, partialTick);
        host.bss$getDepositPlayerButton().render(graphics, mouseX, mouseY, partialTick);
    }

    private static List<ClientStorageView.Entry> entries(SidebarScreenAccess host)
    {
        return STORAGE_VIEW.entries(ClientStorageState.snapshot(), host.bss$getSearchBox().getValue());
    }

    public static NetworkStorageSlot findSlotAt(SidebarScreenAccess host, double mouseX, double mouseY)
    {
        if (!host.bss$isSidebarEnabled() || !ClientStorageState.available() || host.bss$isSidebarHidden())
        {
            return null;
        }
        int storageIndex = cellIndexAt(host, mouseX, mouseY);
        if (storageIndex < 0)
        {
            return null;
        }
        int visualIndex = storageIndex - ClientStorageState.scrollRow() * SLOT_COLUMNS;
        List<NetworkStorageSlot> slots = host.bss$getSidebarSlots();
        return visualIndex < 0 || visualIndex >= slots.size() ? null : slots.get(visualIndex);
    }

    private static int cellIndexAt(SidebarScreenAccess host, double mouseX, double mouseY)
    {
        int x = host.bss$getSidebarX();
        int y = host.bss$getSidebarY();
        int rows = visibleRows();
        if (mouseX < x + 7 || mouseX >= x + WIDTH - 7
                || mouseY < y + GRID_TOP || mouseY >= y + GRID_TOP + rows * SLOT_HEIGHT)
        {
            return -1;
        }
        int col = (int) ((mouseX - (x + 7)) / 18.0D);
        int row = (int) ((mouseY - (y + GRID_TOP)) / 18.0D);
        return col < 0 || col >= SLOT_COLUMNS || row < 0 || row >= rows
                ? -1 : (ClientStorageState.scrollRow() + row) * SLOT_COLUMNS + col;
    }

    private static boolean isHovered(int x, int y, double mouseX, double mouseY, int row, int col)
    {
        int slotX = x + 8 + col * 18;
        int slotY = y + GRID_TOP + row * SLOT_HEIGHT + 1;
        return mouseX >= slotX - 1 && mouseX < slotX + 17
                && mouseY >= slotY - 1 && mouseY < slotY + 17;
    }

    private static int visibleRows()
    {
        return MAX_VISIBLE_ROWS;
    }

    private static void syncScrollState(String search, int entryCount)
    {
        if (!search.equals(lastSearch))
        {
            ClientStorageState.resetScroll();
            lastSearch = search;
        }
        int maxScroll = Math.max(0, (entryCount + SLOT_COLUMNS - 1) / SLOT_COLUMNS - visibleRows());
        ClientStorageState.setScrollRow(Math.min(ClientStorageState.scrollRow(), maxScroll));
    }

    private static String trim(Font font, String value, int maxWidth)
    {
        if (font.width(value) <= maxWidth)
        {
            return value;
        }
        String shortened = value;
        while (shortened.length() > 1 && font.width(shortened + "…") > maxWidth)
        {
            shortened = shortened.substring(0, shortened.length() - 1);
        }
        return shortened + "…";
    }
}
