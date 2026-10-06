package net.xuwu.bettersophisticatedstorage;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.common.Mod;

/**
 * Better Refined Storage entry point (keeps the original internal mod id for compatibility).
 *
 * <p>The actual storage access is deliberately kept in the server-side
 * {@code common} package so every action is checked against the player's
 * connected Refined Storage wireless grid.</p>
 */
@Mod(BetterSophisticatedStorage.MODID)
public final class BetterSophisticatedStorage
{
    public static final String MODID = "better_sophisticated_storage";

    public BetterSophisticatedStorage()
    {
        NetworkHandler.register();
    }

    @SuppressWarnings("removal")
    public static ResourceLocation id(String path)
    {
        return new ResourceLocation(MODID, path);
    }
}

