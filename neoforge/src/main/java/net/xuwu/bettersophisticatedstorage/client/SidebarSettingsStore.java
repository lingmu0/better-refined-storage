package net.xuwu.bettersophisticatedstorage.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

/** Persists client-side sidebar preferences that are sent to the server when a screen opens. */
public final class SidebarSettingsStore
{
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "better_sophisticated_storage-settings.json";

    private static boolean loaded;
    private static boolean playerShift;
    private static boolean containerShift;
    private static boolean sidebarHidden;
    private static boolean sidebarDisabled;
    private static boolean disableConflictingKeys;

    private SidebarSettingsStore()
    {
    }

    public static synchronized Settings get()
    {
        loadIfNeeded();
        return new Settings(playerShift, containerShift, sidebarHidden, sidebarDisabled,
                disableConflictingKeys);
    }

    public static synchronized void set(boolean playerShiftEnabled, boolean containerShiftEnabled)
    {
        set(playerShiftEnabled, containerShiftEnabled, sidebarHidden(), sidebarDisabled(),
                disableConflictingKeys());
    }

    public static synchronized void set(boolean playerShiftEnabled, boolean containerShiftEnabled,
                                        boolean sidebarHiddenEnabled, boolean sidebarDisabledEnabled)
    {
        set(playerShiftEnabled, containerShiftEnabled, sidebarHiddenEnabled, sidebarDisabledEnabled,
                disableConflictingKeys());
    }

    public static synchronized void set(boolean playerShiftEnabled, boolean containerShiftEnabled,
                                        boolean sidebarHiddenEnabled, boolean sidebarDisabledEnabled,
                                        boolean disableConflictingKeysEnabled)
    {
        loadIfNeeded();
        playerShift = playerShiftEnabled;
        containerShift = containerShiftEnabled;
        sidebarHidden = sidebarHiddenEnabled;
        sidebarDisabled = sidebarDisabledEnabled;
        disableConflictingKeys = disableConflictingKeysEnabled;
        writeFile();
    }

    public static synchronized void setSidebarHidden(boolean hidden)
    {
        loadIfNeeded();
        sidebarHidden = hidden;
        writeFile();
    }

    public static synchronized void setSidebarDisabled(boolean disabled)
    {
        loadIfNeeded();
        sidebarDisabled = disabled;
        writeFile();
    }

    public static synchronized void setDisableConflictingKeys(boolean disabled)
    {
        loadIfNeeded();
        disableConflictingKeys = disabled;
        writeFile();
    }

    public static synchronized boolean sidebarHidden()
    {
        loadIfNeeded();
        return sidebarHidden;
    }

    public static synchronized boolean sidebarDisabled()
    {
        loadIfNeeded();
        return sidebarDisabled;
    }

    public static synchronized boolean disableConflictingKeys()
    {
        loadIfNeeded();
        return disableConflictingKeys;
    }

    private static void loadIfNeeded()
    {
        if (loaded)
        {
            return;
        }
        loaded = true;

        Path file = settingsFile();
        if (!Files.isRegularFile(file))
        {
            return;
        }

        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8))
        {
            JsonElement parsed = JsonParser.parseReader(reader);
            if (!parsed.isJsonObject())
            {
                return;
            }
            JsonObject root = parsed.getAsJsonObject();
            if (root.has("playerShift"))
            {
                playerShift = root.get("playerShift").getAsBoolean();
            }
            if (root.has("containerShift"))
            {
                containerShift = root.get("containerShift").getAsBoolean();
            }
            if (root.has("sidebarHidden"))
            {
                sidebarHidden = root.get("sidebarHidden").getAsBoolean();
            }
            if (root.has("sidebarDisabled"))
            {
                sidebarDisabled = root.get("sidebarDisabled").getAsBoolean();
            }
            if (root.has("disableConflictingKeys"))
            {
                disableConflictingKeys = root.get("disableConflictingKeys").getAsBoolean();
            }
        }
        catch (IOException | RuntimeException exception)
        {
            LOGGER.warn("Could not read Better Refined Storage sidebar settings from {}", file, exception);
        }
    }

    private static void writeFile()
    {
        Path file = settingsFile();
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        JsonObject root = new JsonObject();
        root.addProperty("version", 3);
        root.addProperty("playerShift", playerShift);
        root.addProperty("containerShift", containerShift);
        root.addProperty("sidebarHidden", sidebarHidden);
        root.addProperty("sidebarDisabled", sidebarDisabled);
        root.addProperty("disableConflictingKeys", disableConflictingKeys);

        try
        {
            Files.createDirectories(file.getParent());
            Files.writeString(temporary, GSON.toJson(root), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            try
            {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            }
            catch (AtomicMoveNotSupportedException ignored)
            {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        }
        catch (IOException exception)
        {
            LOGGER.warn("Could not save Better Refined Storage sidebar settings to {}", file, exception);
        }
    }

    private static Path settingsFile()
    {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("config").resolve(FILE_NAME);
    }

    public record Settings(boolean playerShift, boolean containerShift,
                           boolean sidebarHidden, boolean sidebarDisabled,
                           boolean disableConflictingKeys)
    {
    }
}

