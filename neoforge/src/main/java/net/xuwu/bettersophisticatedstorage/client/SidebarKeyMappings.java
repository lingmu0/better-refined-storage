package net.xuwu.bettersophisticatedstorage.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.xuwu.bettersophisticatedstorage.BetterSophisticatedStorage;
import org.lwjgl.glfw.GLFW;

/** Client key mappings for the two one-click deposit actions. */
@EventBusSubscriber(
        modid = BetterSophisticatedStorage.MODID,
        bus = EventBusSubscriber.Bus.MOD,
        value = Dist.CLIENT
)
public final class SidebarKeyMappings
{
    public static final KeyMapping DEPOSIT_CONTAINER = new KeyMapping(
            "key.better_sophisticated_storage.deposit_container",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_Z,
            "key.categories.better_sophisticated_storage"
    );
    public static final KeyMapping DEPOSIT_PLAYER = new KeyMapping(
            "key.better_sophisticated_storage.deposit_player",
            InputConstants.Type.KEYSYM,
            InputConstants.UNKNOWN.getValue(),
            "key.categories.better_sophisticated_storage"
    );

    private SidebarKeyMappings()
    {
    }

    @SubscribeEvent
    public static void register(RegisterKeyMappingsEvent event)
    {
        event.register(DEPOSIT_CONTAINER);
        event.register(DEPOSIT_PLAYER);
    }
}

