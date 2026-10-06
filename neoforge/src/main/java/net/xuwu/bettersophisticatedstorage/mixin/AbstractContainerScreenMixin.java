package net.xuwu.bettersophisticatedstorage.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.common.NeoForge;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.xuwu.bettersophisticatedstorage.NetworkHandler;
import net.xuwu.bettersophisticatedstorage.client.ClientStorageState;
import net.xuwu.bettersophisticatedstorage.client.SidebarRenderer;
import net.xuwu.bettersophisticatedstorage.client.SidebarPositionStore;
import net.xuwu.bettersophisticatedstorage.client.SidebarDisplayEvent;
import net.xuwu.bettersophisticatedstorage.client.SidebarScreenAccess;
import net.xuwu.bettersophisticatedstorage.client.SidebarSettingsStore;
import net.xuwu.bettersophisticatedstorage.common.NetworkStorageMenuAccess;
import net.xuwu.bettersophisticatedstorage.common.NetworkStorageSlot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

/** Adds the portable-terminal sidebar to vanilla container screens. */
@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin<T extends AbstractContainerMenu> implements SidebarScreenAccess
{
    @Shadow protected int leftPos;
    @Shadow protected int topPos;
    @Shadow protected int imageWidth;
    @Shadow protected int imageHeight;
    @Shadow protected T menu;

    @Unique private EditBox bss$searchBox;
    @Unique private Button bss$playerShiftButton;
    @Unique private Button bss$containerShiftButton;
    @Unique private Button bss$depositContainerButton;
    @Unique private Button bss$depositPlayerButton;
    @Unique private Button bss$sidebarToggleButton;
    @Unique private SidebarDisplayEvent bss$sidebarDisplayEvent;
    @Unique private net.xuwu.bettersophisticatedstorage.client.SidebarScrollWidget bss$scrollWidget;
    @Unique private boolean bss$consumeSidebarMouseRelease;
    @Unique private final List<NetworkStorageSlot> bss$sidebarSlots = new ArrayList<>();
    @Unique private List<ItemStack> bss$lastSidebarView = List.of();
    @Unique private int bss$sidebarX;
    @Unique private int bss$sidebarY;
    @Unique private String bss$sidebarPositionKey = "";
    @Unique private boolean bss$sidebarDragging;
    @Unique private double bss$sidebarDragStartMouseX;
    @Unique private double bss$sidebarDragStartMouseY;
    @Unique private int bss$sidebarDragOffsetX;
    @Unique private int bss$sidebarDragOffsetY;

    @Inject(method = "init", at = @At("TAIL"))
    private void bss$initSidebar(CallbackInfo callbackInfo)
    {
        if (bss$isSidebarExcludedScreen())
        {
            return;
        }

        SidebarSettingsStore.Settings settings = SidebarSettingsStore.get();
        ClientStorageState.setSidebarHidden(settings.sidebarHidden());
        if (settings.sidebarDisabled())
        {
            // Keep the server-side state and the two keyboard actions alive without adding
            // any sidebar widgets to this screen.
            NetworkHandler.requestSnapshot();
            return;
        }

        bss$initializeSidebarPosition();

        int x = bss$getSidebarX();
        int y = bss$getSidebarY();
        int buttonGap = 3;
        int buttonWidth = (SidebarRenderer.WIDTH - 7 - buttonGap) / 2;
        int firstButtonX = x + 3;
        int secondButtonX = firstButtonX + buttonWidth + buttonGap;
        int firstButtonY = y + 20;
        int secondButtonY = firstButtonY + 15;
        int searchHeight = Minecraft.getInstance().font.lineHeight + 5;

        bss$searchBox = bss$addRenderableWidget(new EditBox(
                Minecraft.getInstance().font,
                x + SidebarRenderer.SEARCH_LEFT,
                y + 4,
                SidebarRenderer.getSearchWidth(),
                searchHeight,
                Component.translatable("better_sophisticated_storage.search")
        ));
        bss$searchBox.setMaxLength(200);
        bss$searchBox.setBordered(true);
        bss$searchBox.setVisible(true);
        bss$searchBox.setTextColor(16777215);
        bss$searchBox.setTooltip(Tooltip.create(Component.translatable("better_sophisticated_storage.search")));
        bss$searchBox.setResponder(text -> {
            ClientStorageState.resetScroll();
        });
        SidebarRenderer.updateSearchHint(bss$searchBox);
        bss$searchBox.setValue("");

        bss$playerShiftButton = bss$addRenderableWidget(Button.builder(Component.literal("人×"), button -> NetworkHandler.togglePlayerShift())
                .bounds(firstButtonX, firstButtonY, buttonWidth, 14).build());
        bss$playerShiftButton.setTooltip(Tooltip.create(Component.translatable("better_sophisticated_storage.tooltip.shift_player")));
        bss$containerShiftButton = bss$addRenderableWidget(Button.builder(Component.literal("箱×"), button -> NetworkHandler.toggleContainerShift())
                .bounds(secondButtonX, firstButtonY, buttonWidth, 14).build());
        bss$containerShiftButton.setTooltip(Tooltip.create(Component.translatable("better_sophisticated_storage.tooltip.shift_container")));
        bss$depositContainerButton = bss$addRenderableWidget(Button.builder(Component.literal("存箱"), button -> NetworkHandler.depositContainer())
                .bounds(secondButtonX, secondButtonY, buttonWidth, 14).build());
        bss$depositContainerButton.setTooltip(Tooltip.create(Component.translatable("better_sophisticated_storage.tooltip.deposit_container")));
        bss$depositPlayerButton = bss$addRenderableWidget(Button.builder(Component.literal("存包"), button -> NetworkHandler.depositPlayerInventory())
                .bounds(firstButtonX, secondButtonY, buttonWidth, 14).build());
        bss$depositPlayerButton.setTooltip(Tooltip.create(Component.translatable("better_sophisticated_storage.tooltip.deposit_player")));
        bss$sidebarToggleButton = bss$addRenderableWidget(Button.builder(Component.literal("×"), button -> {
                })
                .bounds(SidebarRenderer.getToggleX(x), y + 4,
                        SidebarRenderer.TOGGLE_WIDTH, searchHeight).build());
        bss$sidebarToggleButton.setTooltip(Tooltip.create(Component.translatable("better_sophisticated_storage.tooltip.hide_sidebar")));
        bss$scrollWidget = bss$addRenderableWidget(new net.xuwu.bettersophisticatedstorage.client.SidebarScrollWidget(
                this,
                x + 7,
                y + SidebarRenderer.getGridTop(),
                SidebarRenderer.WIDTH - 14,
                Math.max(18, SidebarRenderer.getPanelHeight() - SidebarRenderer.getGridTop() - 7)
        ));
        bss$layoutSidebarWidgets();

        bss$sidebarDisplayEvent = new SidebarDisplayEvent((Screen) (Object) this);
        NeoForge.EVENT_BUS.post(bss$sidebarDisplayEvent);

        bss$setWidgetsVisible(false);
        bss$sidebarToggleButton.visible = false;
        bss$sidebarToggleButton.active = false;
        if (bss$isSidebarEnabled())
        {
            bss$rebuildSidebarSlots();
            NetworkHandler.requestSnapshot();
        }
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void bss$prepareSidebarSlots(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo callbackInfo)
    {
        if (!bss$isSidebarExcludedScreen() && bss$isSidebarEnabled() && bss$searchBox != null)
        {
            bss$rebuildSidebarSlots();
            SidebarRenderer.prepareSlots(this);
        }
    }

    @Inject(method = "renderSlot", at = @At("HEAD"), cancellable = true)
    private void bss$skipNativeSidebarSlot(GuiGraphics graphics, Slot slot, CallbackInfo callbackInfo)
    {
        if (slot instanceof NetworkStorageSlot)
        {
            // SidebarRenderer draws the item and long storage amount itself.
            // Do not let vanilla draw a second stack/count layer over it.
            callbackInfo.cancel();
        }
    }

    @Unique
    private <W extends net.minecraft.client.gui.components.events.GuiEventListener
            & net.minecraft.client.gui.components.Renderable
            & net.minecraft.client.gui.narration.NarratableEntry> W bss$addRenderableWidget(W widget)
    {
        ScreenWidgetAccessor screen = (ScreenWidgetAccessor) (Object) this;
        screen.bss$getChildren().add(widget);
        screen.bss$getRenderables().add(widget);
        screen.bss$getNarratables().add(widget);
        return widget;
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void bss$renderSidebar(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo callbackInfo)
    {
        if (!bss$isSidebarExcludedScreen() && bss$isSidebarEnabled() && bss$searchBox != null)
        {
            bss$rebuildSidebarSlots();
            SidebarRenderer.render(this, graphics, mouseX, mouseY, partialTick);
            SidebarRenderer.renderTooltip(this, graphics, mouseX, mouseY);
        }
    }

    /** Let vanilla render the normal item tooltip for the real sidebar slot. */
    @Inject(method = "renderTooltip", at = @At("HEAD"), cancellable = true)
    private void bss$renderStorageTooltip(GuiGraphics graphics, int mouseX, int mouseY,
                                          CallbackInfo callbackInfo)
    {
        if (!bss$isSidebarExcludedScreen() && bss$isSidebarEnabled() && bss$searchBox != null
                && SidebarRenderer.renderStorageTooltip(this, graphics, mouseX, mouseY))
        {
            callbackInfo.cancel();
        }
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void bss$sidebarClick(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> callbackInfo)
    {
        if (!bss$isSidebarExcludedScreen() && bss$isSidebarEnabled() && bss$searchBox != null
                && SidebarRenderer.handleMouseClick(this, mouseX, mouseY, button))
        {
            bss$markSidebarMouseRelease();
            callbackInfo.setReturnValue(true);
        }
    }

    @Inject(method = "mouseDragged", at = @At("HEAD"), cancellable = true)
    private void bss$sidebarDrag(double mouseX, double mouseY, int button, double dragX, double dragY,
                                 CallbackInfoReturnable<Boolean> callbackInfo)
    {
        if (!bss$isSidebarExcludedScreen() && bss$isSidebarEnabled() && bss$searchBox != null
                && SidebarRenderer.handleMouseDrag(this, mouseX, mouseY, button))
        {
            callbackInfo.setReturnValue(true);
        }
    }

    /**
     * Sidebar slots are real menu slots, but their vanilla x/y values cannot represent the
     * sidebar's screen-relative position while the menu is being constructed before init().
     * Map vanilla's hit-test back to the visible real slot instead of using those placeholder
     * coordinates.
     */
    @Inject(method = "findSlot", at = @At("RETURN"), cancellable = true)
    private void bss$findSidebarSlot(double mouseX, double mouseY,
                                     CallbackInfoReturnable<Slot> callbackInfo)
    {
        if (!bss$isSidebarExcludedScreen() && bss$isSidebarEnabled() && bss$searchBox != null)
        {
            NetworkStorageSlot slot = SidebarRenderer.findSlotAt(this, mouseX, mouseY);
            if (slot != null)
            {
                callbackInfo.setReturnValue(slot);
            }
        }
    }

    @Inject(method = "slotClicked", at = @At("HEAD"), cancellable = true)
    private void bss$networkSlotClicked(Slot slot, int slotId, int button, ClickType clickType,
                                         CallbackInfo callbackInfo)
    {
        if (bss$isSidebarExcludedScreen() || !bss$isSidebarEnabled() || bss$searchBox == null)
        {
            return;
        }

        if (slot instanceof NetworkStorageSlot)
        {
            // Never let middle-click/clone mutate a sidebar item; the wheel belongs to paging.
            if (button != 2 && clickType != ClickType.CLONE)
            {
                NetworkHandler.clickSidebarSlot(slotId, button, clickType);
            }
            callbackInfo.cancel();
        }
        else if (clickType == ClickType.QUICK_MOVE && bss$shouldRouteQuickMove(slot))
        {
            // Keep enabled quick-moves authoritative on the server. Some custom menus predict a
            // different destination than the portable-terminal network.
            NetworkHandler.clickSidebarSlot(slotId, button, clickType);
            callbackInfo.cancel();
        }
    }

    @Unique
    private boolean bss$shouldRouteQuickMove(Slot slot)
    {
        if (slot == null || !slot.hasItem() || !ClientStorageState.available()
                || ClientStorageState.isSidebarHidden())
        {
            return false;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null)
        {
            return false;
        }

        SidebarSettingsStore.Settings settings = SidebarSettingsStore.get();
        if (settings.sidebarDisabled())
        {
            return false;
        }
        return slot.container == minecraft.player.getInventory()
                ? settings.playerShift()
                : settings.containerShift();
    }

    @Inject(method = "mouseReleased", at = @At("HEAD"), cancellable = true)
    private void bss$sidebarRelease(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> callbackInfo)
    {
        if (!bss$isSidebarExcludedScreen() && bss$isSidebarEnabled() && bss$searchBox != null
                && SidebarRenderer.handleMouseRelease(this, mouseX, mouseY, button))
        {
            callbackInfo.setReturnValue(true);
        }
    }

    @Unique
    @Override
    public boolean bss$isSidebarExcludedScreen()
    {
        return bss$isBeyondScreen() || bss$isCreativeScreen();
    }

    @Unique
    private boolean bss$isBeyondScreen()
    {
        String className = this.getClass().getName();
        return className.startsWith("com.wintercogs.beyonddimensions.")
                || className.startsWith("org.cyclops.integratedterminals.")
                || className.startsWith("com.refinedmods.refinedstorage.");
    }

    @Unique
    private boolean bss$isCreativeScreen()
    {
        return (Object) this instanceof CreativeModeInventoryScreen;
    }

    @Unique
    private void bss$initializeSidebarPosition()
    {
        bss$sidebarPositionKey = this.getClass().getName() + "|" + menu.getClass().getName()
                + "|" + imageWidth + "x" + imageHeight;
        SidebarPositionStore.Position saved = SidebarPositionStore.get(bss$sidebarPositionKey).orElse(null);
        int x = saved == null ? bss$defaultSidebarX() : leftPos + saved.offsetX();
        int y = saved == null ? topPos + 1 : topPos + saved.offsetY();
        bss$sidebarX = bss$clampSidebarX(x);
        bss$sidebarY = bss$clampSidebarY(y);
    }

    @Unique
    private int bss$defaultSidebarX()
    {
        int margin = 4;
        int gap = 2;
        int screenWidth = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        int leftCandidate = leftPos - SidebarRenderer.WIDTH - gap;
        int rightCandidate = leftPos + imageWidth + gap;
        boolean fitsLeft = leftCandidate >= margin;
        boolean fitsRight = rightCandidate + SidebarRenderer.WIDTH <= screenWidth - margin;
        if (fitsLeft)
        {
            return leftCandidate;
        }
        if (fitsRight)
        {
            return rightCandidate;
        }

        int leftSpace = leftPos - margin;
        int rightSpace = screenWidth - margin - (leftPos + imageWidth);
        return rightSpace > leftSpace ? rightCandidate : leftCandidate;
    }

    @Unique
    private int bss$clampSidebarX(int x)
    {
        int max = Math.max(4, Minecraft.getInstance().getWindow().getGuiScaledWidth()
                - SidebarRenderer.WIDTH - 4);
        return Math.max(4, Math.min(max, x));
    }

    @Unique
    private int bss$clampSidebarY(int y)
    {
        int max = Math.max(4, Minecraft.getInstance().getWindow().getGuiScaledHeight()
                - SidebarRenderer.getPanelHeight() - 4);
        return Math.max(4, Math.min(max, y));
    }

    @Unique
    private void bss$setSidebarPosition(int x, int y)
    {
        bss$sidebarX = bss$clampSidebarX(x);
        bss$sidebarY = bss$clampSidebarY(y);
        bss$layoutSidebarWidgets();
        bss$positionSidebarSlots();
    }

    @Unique
    private void bss$layoutSidebarWidgets()
    {
        if (bss$searchBox == null)
        {
            return;
        }

        int x = bss$sidebarX;
        int y = bss$sidebarY;
        int buttonGap = 3;
        int buttonWidth = (SidebarRenderer.WIDTH - 7 - buttonGap) / 2;
        int firstButtonX = x + 3;
        int secondButtonX = firstButtonX + buttonWidth + buttonGap;
        int firstButtonY = y + 20;
        int secondButtonY = firstButtonY + 15;
        bss$searchBox.setX(x + SidebarRenderer.SEARCH_LEFT);
        bss$searchBox.setY(y + 4);
        bss$playerShiftButton.setX(firstButtonX);
        bss$playerShiftButton.setY(firstButtonY);
        bss$containerShiftButton.setX(secondButtonX);
        bss$containerShiftButton.setY(firstButtonY);
        bss$depositContainerButton.setX(secondButtonX);
        bss$depositContainerButton.setY(secondButtonY);
        bss$depositPlayerButton.setX(firstButtonX);
        bss$depositPlayerButton.setY(secondButtonY);
        bss$sidebarToggleButton.setX(SidebarRenderer.getToggleX(x));
        bss$sidebarToggleButton.setY(y + 4);
        bss$scrollWidget.setX(x + 7);
        bss$scrollWidget.setY(y + SidebarRenderer.getGridTop());
    }

    @Unique
    private void bss$setWidgetsVisible(boolean visible)
    {
        if (bss$searchBox == null)
        {
            return;
        }
        bss$searchBox.visible = visible;
        bss$searchBox.active = visible;
        bss$playerShiftButton.visible = visible;
        bss$playerShiftButton.active = visible;
        bss$containerShiftButton.visible = visible;
        bss$containerShiftButton.active = visible;
        bss$depositContainerButton.visible = visible;
        bss$depositContainerButton.active = visible;
        bss$depositPlayerButton.visible = visible;
        bss$depositPlayerButton.active = visible;
        bss$scrollWidget.visible = visible;
        bss$scrollWidget.active = visible;
    }

    @Override
    public void bss$toggleSidebarVisibility()
    {
        boolean hidden = !ClientStorageState.isSidebarHidden();
        ClientStorageState.setSidebarHidden(hidden);
        NetworkHandler.setSidebarHidden(hidden);
        bss$layoutSidebarWidgets();
        if (hidden && bss$searchBox != null)
        {
            ((net.minecraft.client.gui.screens.Screen) (Object) this).setFocused(null);
            bss$searchBox.setFocused(false);
        }
    }

    @Override
    public EditBox bss$getSearchBox()
    {
        return bss$searchBox;
    }

    @Override
    public SidebarDisplayEvent bss$getSidebarDisplayEvent()
    {
        return bss$sidebarDisplayEvent;
    }

    @Override
    public boolean bss$isSidebarEnabled()
    {
        return bss$sidebarDisplayEvent == null || bss$sidebarDisplayEvent.isSidebarEnabled();
    }

    @Override
    public boolean bss$isSidebarButtonEnabled(SidebarDisplayEvent.ButtonId button)
    {
        return bss$sidebarDisplayEvent == null || bss$sidebarDisplayEvent.isButtonEnabled(button);
    }

    @Override
    public Button bss$getPlayerShiftButton()
    {
        return bss$playerShiftButton;
    }

    @Override
    public Button bss$getContainerShiftButton()
    {
        return bss$containerShiftButton;
    }

    @Override
    public Button bss$getDepositContainerButton()
    {
        return bss$depositContainerButton;
    }

    @Override
    public Button bss$getDepositPlayerButton()
    {
        return bss$depositPlayerButton;
    }

    @Override
    public Button bss$getSidebarToggleButton()
    {
        return bss$sidebarToggleButton;
    }

    @Override
    public net.minecraft.world.item.ItemStack bss$getCarried()
    {
        return menu.getCarried();
    }

    @Override
    public void bss$markSidebarMouseRelease()
    {
        bss$consumeSidebarMouseRelease = true;
    }

    @Override
    public boolean bss$consumeSidebarMouseRelease()
    {
        boolean consume = bss$consumeSidebarMouseRelease;
        bss$consumeSidebarMouseRelease = false;
        return consume;
    }

    @Override
    public boolean bss$isSidebarHidden()
    {
        return ClientStorageState.isSidebarHidden();
    }

    @Override
    public boolean bss$isSidebarDragging()
    {
        return bss$sidebarDragging;
    }

    @Override
    public void bss$beginSidebarDrag(double mouseX, double mouseY)
    {
        bss$sidebarDragging = true;
        bss$sidebarDragStartMouseX = mouseX;
        bss$sidebarDragStartMouseY = mouseY;
        bss$sidebarDragOffsetX = (int) Math.floor(mouseX) - bss$sidebarX;
        bss$sidebarDragOffsetY = (int) Math.floor(mouseY) - bss$sidebarY;
    }

    @Override
    public void bss$dragSidebarTo(double mouseX, double mouseY)
    {
        if (!bss$sidebarDragging)
        {
            return;
        }
        bss$setSidebarPosition(
                (int) Math.floor(mouseX) - bss$sidebarDragOffsetX,
                (int) Math.floor(mouseY) - bss$sidebarDragOffsetY
        );
    }

    @Override
    public boolean bss$endSidebarDrag(double mouseX, double mouseY)
    {
        if (!bss$sidebarDragging)
        {
            return false;
        }

        bss$dragSidebarTo(mouseX, mouseY);
        bss$sidebarDragging = false;
        double deltaX = mouseX - bss$sidebarDragStartMouseX;
        double deltaY = mouseY - bss$sidebarDragStartMouseY;
        boolean moved = deltaX * deltaX + deltaY * deltaY >= 9.0D;
        if (moved)
        {
            SidebarPositionStore.save(bss$sidebarPositionKey,
                    bss$sidebarX - leftPos, bss$sidebarY - topPos);
        }
        return moved;
    }

    @Override
    public int bss$getSidebarX()
    {
        return bss$sidebarX;
    }

    @Override
    public int bss$getSidebarY()
    {
        return bss$sidebarY;
    }

    @Override
    public int bss$getSidebarHeight()
    {
        return Math.max(SidebarRenderer.getPanelHeight(), imageHeight);
    }

    @Override
    public List<NetworkStorageSlot> bss$getSidebarSlots()
    {
        return bss$sidebarSlots;
    }

    @Override
    public void bss$rebuildSidebarSlots()
    {
        if (menu == null || bss$isSidebarExcludedScreen() || !bss$isSidebarEnabled())
        {
            return;
        }

        int slotBaseX = bss$getSidebarX() + 8 - leftPos;
        int slotBaseY = bss$getSidebarY() + SidebarRenderer.getGridTop() + 1 - topPos;
        NetworkStorageMenuAccess access = (NetworkStorageMenuAccess) (Object) menu;
        access.bss$ensureNetworkSlots(slotBaseX, slotBaseY);
        bss$sidebarSlots.clear();
        bss$sidebarSlots.addAll(access.bss$getNetworkSlots());
        bss$positionSidebarSlots();
    }

    @Unique
    private void bss$positionSidebarSlots()
    {
        int slotBaseX = bss$getSidebarX() + 8 - leftPos;
        int slotBaseY = bss$getSidebarY() + SidebarRenderer.getGridTop() + 1 - topPos;
        for (int index = 0; index < bss$sidebarSlots.size(); index++)
        {
            SlotPositionAccessor accessor = (SlotPositionAccessor) (Object) bss$sidebarSlots.get(index);
            accessor.bss$setX(slotBaseX + index % SidebarRenderer.SLOT_COLUMNS * 18);
            accessor.bss$setY(slotBaseY + index / SidebarRenderer.SLOT_COLUMNS * 18);
        }
    }

    @Override
    public void bss$updateSidebarSlots(List<net.xuwu.bettersophisticatedstorage.client.ClientStorageView.Entry> entries)
    {
        int firstEntry = ClientStorageState.scrollRow() * SidebarRenderer.SLOT_COLUMNS;
        int rows = SidebarRenderer.getVisibleRows();
        for (int index = 0; index < bss$sidebarSlots.size(); index++)
        {
            int row = index / SidebarRenderer.SLOT_COLUMNS;
            int entryIndex = firstEntry + index;
            boolean active = ClientStorageState.available() && row < rows;
            NetworkStorageSlot slot = bss$sidebarSlots.get(index);
            net.xuwu.bettersophisticatedstorage.client.ClientStorageView.Entry entry =
                    entryIndex < entries.size() ? entries.get(entryIndex) : null;
            if (entry == null)
            {
                slot.clear();
            }
            else
            {
                slot.update(entryIndex, entry.key(), entry.amount(), active);
            }
        }

        List<ItemStack> viewStacks = new ArrayList<>(bss$sidebarSlots.size());
        for (NetworkStorageSlot slot : bss$sidebarSlots)
        {
            ItemStack key = slot.getKey();
            viewStacks.add(key == null ? ItemStack.EMPTY : key);
        }
        if (!bss$sameStackList(viewStacks, bss$lastSidebarView))
        {
            bss$lastSidebarView = viewStacks.stream().map(ItemStack::copy).toList();
            NetworkHandler.updateSidebarView(viewStacks);
        }
    }

    @Unique
    private static boolean bss$sameStackList(List<ItemStack> first, List<ItemStack> second)
    {
        if (first.size() != second.size())
        {
            return false;
        }
        for (int index = 0; index < first.size(); index++)
        {
            ItemStack left = first.get(index);
            ItemStack right = second.get(index);
            if (left == null || right == null)
            {
                if (left != right)
                {
                    return false;
                }
            }
            else if (left.isEmpty() != right.isEmpty()
                    || (!left.isEmpty() && !bss$sameItemAndComponents(left, right)))
            {
                return false;
            }
        }
        return true;
    }

    @Unique
    private static boolean bss$sameItemAndComponents(ItemStack first, ItemStack second)
    {
        return ItemStack.isSameItemSameComponents(first, second);
    }

}

