package net.xuwu.bettersophisticatedstorage.common;

import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Runtime bridge to Integrated Terminals' portable item-storage terminal.
 *
 * <p>The bridge is deliberately reflective.  Integrated Dynamics changed its capability
 * optional type between the two supported loader versions, while the actual terminal and
 * storage contract stayed the same.  Keeping those classes out of the compile classpath also
 * lets this add-on remain a small, optional compatibility layer.</p>
 */
public final class PortableStorageNetwork
{
    public static final String PORTABLE_TERMINAL_ID = "integratedterminals:terminal_storage_portable";
    private static final String REFINED_STORAGE_WIRELESS_GRID_ID = "refinedstorage:wireless_grid";
    private static final String REFINED_STORAGE_CREATIVE_WIRELESS_GRID_ID =
            "refinedstorage:creative_wireless_grid";

    private static final String TERMINAL_CONTAINER_CLASS =
            "org.cyclops.integratedterminals.inventory.container.ContainerTerminalStorageItem";
    private static final String NETWORK_HELPERS_CLASS =
            "org.cyclops.integrateddynamics.core.helper.NetworkHelpers";
    private static final String INGREDIENT_COMPONENT_CLASS =
            "org.cyclops.commoncapabilities.api.ingredient.IngredientComponent";
    private static final String CURIOS_API_CLASS = "top.theillusivec4.curios.api.CuriosApi";
    private static final String REFINED_STORAGE_API_CLASS =
            "com.refinedmods.refinedstorage.common.api.RefinedStorageApi";
    private static final String REFINED_STORAGE_SLOT_REFERENCE_CLASS =
            "com.refinedmods.refinedstorage.common.api.support.slotreference.SlotReference";
    private static final String REFINED_STORAGE_INVENTORY_SLOT_REFERENCE_CLASS =
            "com.refinedmods.refinedstorage.common.support.slotreference.InventorySlotReference";

    private static final Logger LOGGER = LogUtils.getLogger();
    private static boolean apiFailureReported;
    private static boolean refinedStorageApiFailureReported;

    private PortableStorageNetwork()
    {
    }

    /**
     * Finds the first portable terminal carried by the player and resolves its item-storage
     * channel.  A terminal may be in the normal inventory or in a Curios accessory slot.
     */
    public static Connection find(Player player)
    {
        if (!(player instanceof ServerPlayer serverPlayer))
        {
            return null;
        }

        Connection refinedStorageConnection = findRefinedStorageConnection(serverPlayer);
        if (refinedStorageConnection != null)
        {
            return refinedStorageConnection;
        }

        ItemStack terminal = findIntegratedTerminalsTerminal(serverPlayer);
        if (terminal.isEmpty())
        {
            return null;
        }

        try
        {
            Class<?> containerClass = Class.forName(TERMINAL_CONTAINER_CLASS);
            Object networkOptional = invokeStatic(containerClass, "getNetworkFromItem", terminal);
            Object network = unwrapOptional(networkOptional);
            if (network == null)
            {
                return null;
            }

            Class<?> componentClass = Class.forName(INGREDIENT_COMPONENT_CLASS);
            Field itemStackField = componentClass.getField("ITEMSTACK");
            Object itemStackComponent = itemStackField.get(null);
            if (itemStackComponent == null)
            {
                return null;
            }

            Object ingredientNetwork = findIngredientNetwork(network, itemStackComponent);
            if (ingredientNetwork == null)
            {
                return null;
            }

            Object storage = invokeCompatible(ingredientNetwork, "getChannel", -1);
            if (storage == null)
            {
                storage = invokeCompatible(ingredientNetwork, "getChannelInternal", -1);
            }
            Object matcher = invokeCompatible(itemStackComponent, "getMatcher");
            Object exactMatchNoQuantity = matcher == null
                    ? null : invokeCompatible(matcher, "getExactMatchNoQuantityCondition");
            if (storage == null || matcher == null || exactMatchNoQuantity == null)
            {
                return null;
            }

            return new Connection(storage, matcher, exactMatchNoQuantity);
        }
        catch (Throwable exception)
        {
            reportApiFailure(exception);
            return null;
        }
    }

    public static boolean isPortableTerminal(ItemStack stack)
    {
        if (stack == null || stack.isEmpty())
        {
            return false;
        }
        var key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (key == null)
        {
            return false;
        }
        String itemId = key.toString();
        return PORTABLE_TERMINAL_ID.equals(itemId)
                || REFINED_STORAGE_WIRELESS_GRID_ID.equals(itemId)
                || REFINED_STORAGE_CREATIVE_WIRELESS_GRID_ID.equals(itemId);
    }

    private static boolean isIntegratedTerminalsTerminal(ItemStack stack)
    {
        if (stack == null || stack.isEmpty())
        {
            return false;
        }
        var key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return key != null && PORTABLE_TERMINAL_ID.equals(key.toString());
    }

    static boolean isRefinedStorageWirelessGrid(ItemStack stack)
    {
        if (stack == null || stack.isEmpty())
        {
            return false;
        }
        var key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (key == null)
        {
            return false;
        }
        String itemId = key.toString();
        return REFINED_STORAGE_WIRELESS_GRID_ID.equals(itemId)
                || REFINED_STORAGE_CREATIVE_WIRELESS_GRID_ID.equals(itemId);
    }

    private static ItemStack findIntegratedTerminalsTerminal(ServerPlayer player)
    {
        Inventory inventory = player.getInventory();
        for (int index = 0; index < inventory.getContainerSize(); index++)
        {
            ItemStack stack = inventory.getItem(index);
            if (isIntegratedTerminalsTerminal(stack))
            {
                return stack;
            }
        }

        return findCuriosStack(player, PortableStorageNetwork::isIntegratedTerminalsTerminal);
    }

    /** Supports both Curios' 1.20 helper API and its 1.21 inventory API. */
    static ItemStack findCuriosStack(ServerPlayer player, Predicate<ItemStack> predicate)
    {
        try
        {
            Class<?> curiosApi = Class.forName(CURIOS_API_CLASS);
            Method getCuriosInventory = findMethod(curiosApi, "getCuriosInventory", 1);
            if (getCuriosInventory != null)
            {
                Object handler = unwrapOptional(getCuriosInventory.invoke(null, player));
                if (handler != null)
                {
                    Object result = invokeCompatible(handler, "findFirstCurio", predicate);
                    ItemStack stack = stackFromCuriosResult(unwrapOptional(result), predicate);
                    if (!stack.isEmpty())
                    {
                        return stack;
                    }
                }
            }

            Object helper = invokeStatic(curiosApi, "getCuriosHelper");
            if (helper == null)
            {
                return ItemStack.EMPTY;
            }
            Object result = invokeCompatible(helper, "findFirstCurio", player, predicate);
            return stackFromCuriosResult(unwrapOptional(result), predicate);
        }
        catch (ClassNotFoundException ignored)
        {
            // Curios is optional; normal inventory terminals continue to work without it.
            return ItemStack.EMPTY;
        }
        catch (Throwable ignored)
        {
            // An incompatible Curios build must not disable the normal inventory path.
            return ItemStack.EMPTY;
        }
    }

    private static ItemStack stackFromCuriosResult(Object result, Predicate<ItemStack> predicate)
    {
        if (result instanceof ItemStack stack)
        {
            return predicate.test(stack) ? stack : ItemStack.EMPTY;
        }
        if (result == null)
        {
            return ItemStack.EMPTY;
        }

        try
        {
            Object stack = invokeCompatible(result, "stack");
            if (stack instanceof ItemStack itemStack && predicate.test(itemStack))
            {
                return itemStack;
            }
            Object getterStack = invokeCompatible(result, "getStack");
            if (getterStack instanceof ItemStack itemStack && predicate.test(itemStack))
            {
                return itemStack;
            }
        }
        catch (Throwable ignored)
        {
            // Treat an unavailable accessory handler as no Curios terminal.
        }
        return ItemStack.EMPTY;
    }

    /** Connects to a bound Refined Storage wireless grid without a hard optional dependency. */
    private static Connection findRefinedStorageConnection(ServerPlayer player)
    {
        try
        {
            Class.forName(REFINED_STORAGE_API_CLASS);
        }
        catch (ClassNotFoundException ignored)
        {
            // Forge 1.20.1 normally uses RS 1.x, which has no RS 2 slot-reference API.
            return LegacyRefinedStorageNetwork.find(player);
        }

        Inventory inventory = player.getInventory();
        for (int index = 0; index < inventory.getContainerSize(); index++)
        {
            ItemStack stack = inventory.getItem(index);
            if (!isRefinedStorageWirelessGrid(stack))
            {
                continue;
            }

            try
            {
                Object slotReference = createInventorySlotReference(index);
                Connection connection = connectRefinedStorage(player, stack, slotReference);
                if (connection != null)
                {
                    return connection;
                }
            }
            catch (ClassNotFoundException ignored)
            {
                return null;
            }
            catch (Throwable exception)
            {
                reportRefinedStorageApiFailure(exception);
            }
        }

        ItemStack curiosGrid = findCuriosStack(player, PortableStorageNetwork::isRefinedStorageWirelessGrid);
        if (!curiosGrid.isEmpty())
        {
            try
            {
                Object slotReference = createCuriosSlotReference(player, curiosGrid);
                return connectRefinedStorage(player, curiosGrid, slotReference);
            }
            catch (ClassNotFoundException ignored)
            {
                return null;
            }
            catch (Throwable exception)
            {
                reportRefinedStorageApiFailure(exception);
            }
        }
        return null;
    }

    private static Object createInventorySlotReference(int slotIndex) throws ReflectiveOperationException
    {
        Class<?> referenceClass = Class.forName(REFINED_STORAGE_INVENTORY_SLOT_REFERENCE_CLASS);
        Constructor<?> constructor = referenceClass.getDeclaredConstructor(int.class);
        constructor.setAccessible(true);
        return constructor.newInstance(slotIndex);
    }

    /** Curios has no Refined Storage slot provider in the supported build, so adapt its stack. */
    private static Object createCuriosSlotReference(ServerPlayer player, ItemStack stack)
            throws ReflectiveOperationException
    {
        Class<?> referenceClass = Class.forName(REFINED_STORAGE_SLOT_REFERENCE_CLASS);
        Object inventoryReference = createInventorySlotReference(player.getInventory().selected);
        Object factory = invokeCompatible(inventoryReference, "getFactory");
        if (factory == null)
        {
            Field factoryField = Class.forName(
                    "com.refinedmods.refinedstorage.common.support.slotreference.InventorySlotReferenceFactory")
                    .getField("INSTANCE");
            factory = factoryField.get(null);
        }

        Object slotFactory = factory;
        InvocationHandler handler = (proxy, method, arguments) -> switch (method.getName())
        {
            case "resolve" -> arguments != null && arguments.length == 1 && arguments[0] == player
                    && playerStillHasStack(player, stack) ? Optional.of(stack) : Optional.empty();
            case "getFactory" -> slotFactory;
            case "isDisabledSlot" -> false;
            case "toString" -> "BetterSophisticatedStorageCuriosSlotReference";
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> arguments != null && arguments.length == 1 && proxy == arguments[0];
            default -> null;
        };
        return Proxy.newProxyInstance(referenceClass.getClassLoader(), new Class<?>[]{referenceClass}, handler);
    }

    private static boolean playerStillHasStack(ServerPlayer player, ItemStack expected)
    {
        for (int index = 0; index < player.getInventory().getContainerSize(); index++)
        {
            if (sameStack(player.getInventory().getItem(index), expected))
            {
                return true;
            }
        }
        return !findCuriosStack(player, candidate -> sameStack(candidate, expected)).isEmpty();
    }

    private static boolean sameStack(ItemStack first, ItemStack second)
    {
        if (first == null || first.isEmpty() || second == null || second.isEmpty())
        {
            return false;
        }
        return ItemStack.isSameItemSameTags(first, second);
    }

    private static Connection connectRefinedStorage(ServerPlayer player, ItemStack terminal,
                                                     Object slotReference)
            throws ReflectiveOperationException
    {
        Class<?> apiClass = Class.forName(REFINED_STORAGE_API_CLASS);
        Object api = apiClass.getField("INSTANCE").get(null);
        Object itemHelper = invokeCompatible(api, "getNetworkItemHelper");
        if (itemHelper == null || !Boolean.TRUE.equals(invokeCompatible(itemHelper, "isBound", terminal)))
        {
            return null;
        }

        Object context = invokeCompatible(itemHelper, "createContext", terminal, player, slotReference);
        if (context == null || !Boolean.TRUE.equals(invokeCompatible(context, "isActive")))
        {
            return null;
        }
        Object network = unwrapOptional(invokeCompatible(context, "resolveNetwork"));
        if (network == null || !hasRefinedStoragePermission(player, network, "OPEN"))
        {
            return null;
        }

        Class<?> storageComponentClass = Class.forName(
                "com.refinedmods.refinedstorage.api.network.storage.StorageNetworkComponent");
        Object storage = invokeCompatible(network, "getComponent", storageComponentClass);
        if (storage == null)
        {
            return null;
        }

        Object actor = createRefinedStorageActor(player);
        return new Connection(storage, context, network, actor, player);
    }

    private static Object createRefinedStorageActor(ServerPlayer player) throws ReflectiveOperationException
    {
        try
        {
            Class<?> playerActorClass = Class.forName(
                    "com.refinedmods.refinedstorage.common.api.storage.PlayerActor");
            Constructor<?> constructor = playerActorClass.getConstructor(Player.class);
            return constructor.newInstance(player);
        }
        catch (ClassNotFoundException | NoSuchMethodException ignored)
        {
            Class<?> actorClass = Class.forName("com.refinedmods.refinedstorage.api.storage.Actor");
            return actorClass.getField("EMPTY").get(null);
        }
    }

    private static boolean hasRefinedStoragePermission(ServerPlayer player, Object network, String permissionName)
    {
        try
        {
            Class<?> permissionClass = Class.forName(
                    "com.refinedmods.refinedstorage.common.security.BuiltinPermission");
            Object permission = permissionClass.getField(permissionName).get(null);
            Class<?> helperClass = Class.forName(
                    "com.refinedmods.refinedstorage.common.api.security.SecurityHelper");
            return Boolean.TRUE.equals(invokeStatic(helperClass, "isAllowed", player, permission, network));
        }
        catch (Throwable exception)
        {
            reportRefinedStorageApiFailure(exception);
            return false;
        }
    }

    private static Object refinedStorageAction(boolean simulate) throws ReflectiveOperationException
    {
        Class<?> actionClass = Class.forName("com.refinedmods.refinedstorage.api.core.Action");
        return actionClass.getField(simulate ? "SIMULATE" : "EXECUTE").get(null);
    }

    private static Object refinedStorageItemResource(ItemStack stack) throws ReflectiveOperationException
    {
        Class<?> resourceClass = Class.forName("com.refinedmods.refinedstorage.common.support.resource.ItemResource");
        ItemStack key = stack.copy();
        key.setCount(1);
        return invokeStatic(resourceClass, "ofItemStack", key);
    }

    private static Object findIngredientNetwork(Object network, Object itemStackComponent)
            throws ReflectiveOperationException
    {
        Class<?> helpersClass = Class.forName(NETWORK_HELPERS_CLASS);
        for (Method method : helpersClass.getMethods())
        {
            if (!method.getName().equals("getIngredientNetwork") || method.getParameterCount() != 2)
            {
                continue;
            }

            Class<?> optionalType = method.getParameterTypes()[0];
            Object optionalNetwork;
            if (Optional.class.isAssignableFrom(optionalType))
            {
                optionalNetwork = Optional.of(network);
            }
            else if (optionalType.getName().equals("net.minecraftforge.common.util.LazyOptional"))
            {
                Method of = optionalType.getMethod("of", Supplier.class);
                optionalNetwork = of.invoke(null, (Supplier<Object>) () -> network);
            }
            else
            {
                continue;
            }

            Object result = method.invoke(null, optionalNetwork, itemStackComponent);
            Object unwrapped = unwrapOptional(result);
            if (unwrapped != null)
            {
                return unwrapped;
            }
        }
        return null;
    }

    private static Object unwrapOptional(Object optional) throws ReflectiveOperationException
    {
        if (optional == null)
        {
            return null;
        }
        if (optional instanceof Optional<?> javaOptional)
        {
            return javaOptional.orElse(null);
        }

        Method isPresent = findMethod(optional.getClass(), "isPresent", 0);
        if (isPresent != null && !Boolean.TRUE.equals(isPresent.invoke(optional)))
        {
            return null;
        }

        Method get = findMethod(optional.getClass(), "get", 0);
        if (get != null)
        {
            return get.invoke(optional);
        }

        Method resolve = findMethod(optional.getClass(), "resolve", 0);
        if (resolve != null)
        {
            return unwrapOptional(resolve.invoke(optional));
        }

        Method orElse = optional.getClass().getMethod("orElse", Object.class);
        return orElse.invoke(optional, new Object[]{null});
    }

    static Object invokeStatic(Class<?> type, String name, Object... arguments)
            throws ReflectiveOperationException
    {
        Method method = findCompatibleMethod(type, name, arguments);
        if (method == null || !Modifier.isStatic(method.getModifiers()))
        {
            return null;
        }
        return method.invoke(null, arguments);
    }

    static Object invokeCompatible(Object target, String name, Object... arguments)
            throws ReflectiveOperationException
    {
        if (target == null)
        {
            return null;
        }
        Method method = findCompatibleMethod(target.getClass(), name, arguments);
        return method == null ? null : method.invoke(target, arguments);
    }

    private static Method findMethod(Class<?> type, String name, int parameterCount)
    {
        for (Method method : type.getMethods())
        {
            if (method.getName().equals(name) && method.getParameterCount() == parameterCount)
            {
                return method;
            }
        }
        return null;
    }

    private static Method findCompatibleMethod(Class<?> type, String name, Object... arguments)
    {
        for (Method method : type.getMethods())
        {
            if (!method.getName().equals(name) || method.getParameterCount() != arguments.length)
            {
                continue;
            }

            Class<?>[] parameterTypes = method.getParameterTypes();
            boolean compatible = true;
            for (int index = 0; index < parameterTypes.length; index++)
            {
                if (!isCompatible(parameterTypes[index], arguments[index]))
                {
                    compatible = false;
                    break;
                }
            }
            if (compatible)
            {
                return findPublicDeclaration(type, method);
            }
        }
        return null;
    }

    /** Invoke public API declarations instead of methods declared on package-private impls. */
    private static Method findPublicDeclaration(Class<?> targetType, Method implementationMethod)
    {
        if (Modifier.isPublic(implementationMethod.getDeclaringClass().getModifiers()))
        {
            return implementationMethod;
        }

        Method interfaceMethod = findPublicInterfaceMethod(targetType, implementationMethod.getName(),
                implementationMethod.getParameterTypes());
        if (interfaceMethod != null)
        {
            return interfaceMethod;
        }

        for (Class<?> current = targetType.getSuperclass(); current != null; current = current.getSuperclass())
        {
            if (!Modifier.isPublic(current.getModifiers()))
            {
                continue;
            }
            try
            {
                return current.getMethod(implementationMethod.getName(), implementationMethod.getParameterTypes());
            }
            catch (NoSuchMethodException ignored)
            {
                // Continue until a public declaration is found.
            }
        }
        return implementationMethod;
    }

    private static Method findPublicInterfaceMethod(Class<?> type, String name, Class<?>[] parameterTypes)
    {
        for (Class<?> interfaceType : type.getInterfaces())
        {
            if (Modifier.isPublic(interfaceType.getModifiers()))
            {
                try
                {
                    return interfaceType.getMethod(name, parameterTypes);
                }
                catch (NoSuchMethodException ignored)
                {
                    // The method may be declared by one of the inherited interfaces.
                }
            }
            Method inheritedMethod = findPublicInterfaceMethod(interfaceType, name, parameterTypes);
            if (inheritedMethod != null)
            {
                return inheritedMethod;
            }
        }

        Class<?> superclass = type.getSuperclass();
        return superclass == null ? null : findPublicInterfaceMethod(superclass, name, parameterTypes);
    }

    private static boolean isCompatible(Class<?> parameterType, Object argument)
    {
        if (argument == null)
        {
            return !parameterType.isPrimitive();
        }
        if (!parameterType.isPrimitive())
        {
            return parameterType.isAssignableFrom(argument.getClass());
        }
        return (parameterType == boolean.class && argument instanceof Boolean)
                || (parameterType == byte.class && argument instanceof Byte)
                || (parameterType == short.class && argument instanceof Short)
                || (parameterType == int.class && argument instanceof Integer)
                || (parameterType == long.class && argument instanceof Long)
                || (parameterType == float.class && argument instanceof Float)
                || (parameterType == double.class && argument instanceof Double)
                || (parameterType == char.class && argument instanceof Character);
    }

    private static void reportApiFailure(Throwable exception)
    {
        if (!apiFailureReported)
        {
            apiFailureReported = true;
            LOGGER.warn("Better Sophisticated Storage could not resolve the Integrated Terminals storage API; "
                    + "the sidebar will stay disabled until the required mods are available.", exception);
        }
    }

    static void reportRefinedStorageApiFailure(Throwable exception)
    {
        if (!refinedStorageApiFailureReported)
        {
            refinedStorageApiFailureReported = true;
            LOGGER.warn("Better Sophisticated Storage could not resolve the Refined Storage wireless-grid API; "
                    + "the sidebar will stay disabled for that terminal.", exception);
        }
    }

    /** A safe, item-stack-specific façade over an Integrated Dynamics ingredient channel. */
    public static final class Connection
    {
        private final Object storage;
        private final Object matcher;
        private final Object exactMatchNoQuantity;
        private final Object refinedStorageContext;
        private final Object refinedStorageNetwork;
        private final Object refinedStorageActor;
        private final ServerPlayer refinedStoragePlayer;
        private final LegacyRefinedStorageNetwork legacyRefinedStorage;

        private Connection(Object storage, Object matcher, Object exactMatchNoQuantity)
        {
            this.storage = storage;
            this.matcher = matcher;
            this.exactMatchNoQuantity = exactMatchNoQuantity;
            this.refinedStorageContext = null;
            this.refinedStorageNetwork = null;
            this.refinedStorageActor = null;
            this.refinedStoragePlayer = null;
            this.legacyRefinedStorage = null;
        }

        private Connection(Object storage, Object context, Object network, Object actor, ServerPlayer player)
        {
            this.storage = storage;
            this.matcher = null;
            this.exactMatchNoQuantity = null;
            this.refinedStorageContext = context;
            this.refinedStorageNetwork = network;
            this.refinedStorageActor = actor;
            this.refinedStoragePlayer = player;
            this.legacyRefinedStorage = null;
        }

        Connection(LegacyRefinedStorageNetwork legacyRefinedStorage)
        {
            this.storage = null;
            this.matcher = null;
            this.exactMatchNoQuantity = null;
            this.refinedStorageContext = null;
            this.refinedStorageNetwork = null;
            this.refinedStorageActor = null;
            this.refinedStoragePlayer = null;
            this.legacyRefinedStorage = legacyRefinedStorage;
        }

        public List<StorageEntry> entries()
        {
            if (legacyRefinedStorage != null)
            {
                return legacyRefinedStorage.entries();
            }
            if (refinedStorageContext != null)
            {
                return refinedStorageEntries();
            }

            List<StorageEntry> result = new ArrayList<>();
            if (!(storage instanceof Iterable<?> iterable))
            {
                return result;
            }

            try
            {
                for (Object value : iterable)
                {
                    if (!(value instanceof ItemStack stack) || stack.isEmpty())
                    {
                        continue;
                    }
                    long amount = quantity(stack);
                    if (amount <= 0L)
                    {
                        continue;
                    }
                    ItemStack key = stack.copy();
                    key.setCount(1);
                    mergeEntry(result, key, amount);
                }
            }
            catch (Throwable ignored)
            {
                // A network may be rebuilding its index; show the entries collected so far.
            }
            return result;
        }

        public long amount(ItemStack prototype)
        {
            if (prototype == null || prototype.isEmpty())
            {
                return 0L;
            }
            for (StorageEntry entry : entries())
            {
                if (sameItemAndComponents(entry.stack(), prototype))
                {
                    return entry.amount();
                }
            }
            return 0L;
        }

        public int insert(ItemStack input, boolean simulate)
        {
            if (input == null || input.isEmpty())
            {
                return 0;
            }
            if (legacyRefinedStorage != null)
            {
                return legacyRefinedStorage.insert(input, simulate);
            }
            if (refinedStorageContext != null)
            {
                return refinedStorageInsert(input, simulate);
            }

            try
            {
                Object remainder = invokeCompatible(storage, "insert", input.copy(), simulate);
                if (remainder instanceof ItemStack remainderStack)
                {
                    return Math.max(0, input.getCount() - remainderStack.getCount());
                }
            }
            catch (Throwable ignored)
            {
                // Treat an unavailable network tick as a full remainder.
            }
            return 0;
        }

        public ItemStack extract(ItemStack prototype, long maxAmount, boolean simulate)
        {
            if (prototype == null || prototype.isEmpty() || maxAmount <= 0L)
            {
                return ItemStack.EMPTY;
            }
            if (legacyRefinedStorage != null)
            {
                return legacyRefinedStorage.extract(prototype, maxAmount, simulate);
            }
            if (refinedStorageContext != null)
            {
                return refinedStorageExtract(prototype, maxAmount, simulate);
            }

            int amount = (int) Math.min(Integer.MAX_VALUE, maxAmount);
            ItemStack request = prototype.copy();
            request.setCount(amount);
            try
            {
                Object extracted = invokeCompatible(storage, "extract", request,
                        exactMatchNoQuantity, simulate);
                return extracted instanceof ItemStack stack ? stack : ItemStack.EMPTY;
            }
            catch (Throwable ignored)
            {
                return ItemStack.EMPTY;
            }
        }

        private List<StorageEntry> refinedStorageEntries()
        {
            List<StorageEntry> result = new ArrayList<>();
            try
            {
                Class<?> actorClass = Class.forName("com.refinedmods.refinedstorage.api.storage.Actor");
                Object trackedResources = invokeCompatible(storage, "getResources", actorClass);
                if (!(trackedResources instanceof Iterable<?> iterable))
                {
                    return result;
                }

                for (Object trackedResource : iterable)
                {
                    Object resourceAmount = invokeCompatible(trackedResource, "resourceAmount");
                    if (resourceAmount == null)
                    {
                        continue;
                    }
                    Object resource = invokeCompatible(resourceAmount, "resource");
                    Object amountValue = invokeCompatible(resourceAmount, "amount");
                    if (resource == null || !(amountValue instanceof Number number) || number.longValue() <= 0L)
                    {
                        continue;
                    }
                    Object stackValue = invokeCompatible(resource, "toItemStack", 1L);
                    if (stackValue instanceof ItemStack stack && !stack.isEmpty())
                    {
                        mergeEntry(result, stack, number.longValue());
                    }
                }
            }
            catch (Throwable ignored)
            {
                // A network may be rebuilding its index; keep any entries already collected.
            }
            return result;
        }

        private int refinedStorageInsert(ItemStack input, boolean simulate)
        {
            if (!hasRefinedStoragePermission(refinedStoragePlayer, refinedStorageNetwork, "INSERT"))
            {
                return 0;
            }
            try
            {
                Object resource = refinedStorageItemResource(input);
                Object inserted = invokeCompatible(storage, "insert", resource, (long) input.getCount(),
                        refinedStorageAction(simulate), refinedStorageActor);
                return inserted instanceof Number number
                        ? (int) Math.max(0L, Math.min(input.getCount(), number.longValue())) : 0;
            }
            catch (Throwable ignored)
            {
                return 0;
            }
        }

        private ItemStack refinedStorageExtract(ItemStack prototype, long maxAmount, boolean simulate)
        {
            if (!hasRefinedStoragePermission(refinedStoragePlayer, refinedStorageNetwork, "EXTRACT"))
            {
                return ItemStack.EMPTY;
            }
            try
            {
                Object resource = refinedStorageItemResource(prototype);
                Object extracted = invokeCompatible(storage, "extract", resource,
                        Math.min(Integer.MAX_VALUE, maxAmount), refinedStorageAction(simulate), refinedStorageActor);
                if (!(extracted instanceof Number number) || number.longValue() <= 0L)
                {
                    return ItemStack.EMPTY;
                }
                Object stack = invokeCompatible(resource, "toItemStack", number.longValue());
                return stack instanceof ItemStack itemStack ? itemStack : ItemStack.EMPTY;
            }
            catch (Throwable ignored)
            {
                return ItemStack.EMPTY;
            }
        }

        private long quantity(ItemStack stack)
        {
            try
            {
                Object value = invokeCompatible(matcher, "getQuantity", stack);
                if (value instanceof Number number)
                {
                    return number.longValue();
                }
            }
            catch (Throwable ignored)
            {
                // Fall back to the vanilla stack count below.
            }
            return stack.getCount();
        }

        private static void mergeEntry(List<StorageEntry> entries, ItemStack key, long amount)
        {
            for (int index = 0; index < entries.size(); index++)
            {
                StorageEntry current = entries.get(index);
                if (sameItemAndComponents(current.stack(), key))
                {
                    entries.set(index, new StorageEntry(current.stack(), current.amount() + amount));
                    return;
                }
            }
            entries.add(new StorageEntry(key, amount));
        }

        private static boolean sameItemAndComponents(ItemStack first, ItemStack second)
        {
            if (first == null || second == null || first.isEmpty() || second.isEmpty())
            {
                return first == second || (first != null && second != null && first.isEmpty() && second.isEmpty());
            }
            return ItemStack.isSameItemSameTags(first, second);
        }
    }
}
