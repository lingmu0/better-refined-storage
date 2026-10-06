package net.xuwu.bettersophisticatedstorage;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.common.Mod;

/**
 * Better Sophisticated Storage entry point.
 *
 * <p>The actual storage access is deliberately kept in the server-side
 * {@code common} package so every action is checked against the currently
 * equipped Integrated Terminals portable storage network.</p>
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

