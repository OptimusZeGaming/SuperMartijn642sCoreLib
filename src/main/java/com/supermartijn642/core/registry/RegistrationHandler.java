package com.supermartijn642.core.registry;

import com.supermartijn642.core.CoreLib;
import com.supermartijn642.core.block.BaseBlockEntityType;
import com.supermartijn642.core.gui.BaseContainerType;
import com.supermartijn642.core.recipe.condition.RecipeConditionSerializer;
import net.minecraft.block.Block;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.item.Item;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionType;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundEvent;
import net.minecraftforge.common.crafting.IConditionFactory;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.eventhandler.IContextSetter;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.EntityEntry;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Created 14/07/2022 by SuperMartijn642
 */
public class RegistrationHandler {

    @Mod.EventBusSubscriber(modid = "supermartijn642corelib")
    private static final class Events {

        private static void handleRegistry(Registries.Registry<?> registry, Object event){
            REGISTRATION_HELPER_MAP.values().forEach(handler -> {
                ModContainer modContainer = Loader.instance().getActiveModList().stream().filter(container -> handler.owningMod.equals(container.getModId())).findAny().orElse(null);
                if(modContainer != null && event instanceof IContextSetter){
                    ModContainer old = Loader.instance().activeModContainer();
                    Loader.instance().setActiveModContainer(modContainer);
                    ((IContextSetter)event).setModContainer(modContainer);

                    handler.handleRegisterEvent(registry);

                    ((IContextSetter)event).setModContainer(old);
                    Loader.instance().setActiveModContainer(old);
                }else
                    handler.handleRegisterEvent(registry);
            });
        }

        @SubscribeEvent
        public static void handleBlockRegistryEvent(RegistryEvent.Register<Block> event){
            handleRegistry(Registries.BLOCKS, event);
        }

        @SubscribeEvent
        public static void handleItemRegistryEvent(RegistryEvent.Register<Item> event){
            handleRegistry(Registries.ITEMS, event);
        }

        @SubscribeEvent
        public static void handleMobEffectRegistryEvent(RegistryEvent.Register<Potion> event){
            handleRegistry(Registries.MOB_EFFECTS, event);
        }

        @SubscribeEvent
        public static void handleEnchantmentRegistryEvent(RegistryEvent.Register<Enchantment> event){
            handleRegistry(Registries.ENCHANTMENTS, event);
        }

        @SubscribeEvent
        public static void handleEntityRegistryEvent(RegistryEvent.Register<EntityEntry> event){
            handleRegistry(Registries.ENTITY_TYPES, event);
        }

        @SubscribeEvent
        public static void handleFluidRegistryEvent(FluidRegistry.FluidRegisterEvent event){
            handleRegistry(Registries.FLUIDS, event);
        }
    }

    /**
     * Contains one registration helper per modid
     */
    private static final Map<String,RegistrationHandler> REGISTRATION_HELPER_MAP = new HashMap<>();

    /**
     * Get a registration handler for a given modid. This will always return one unique registration handler per modid.
     * @param modid modid of the mod registering entries
     * @return a unique registration handler for the given modid
     */
    public static synchronized RegistrationHandler get(String modid){
        if(!RegistryUtil.isValidNamespace(modid))
            throw new IllegalArgumentException("Modid '" + modid + "' must only contain characters [a-z0-9_.-]!");
        String activeMod = Loader.instance().activeModContainer() == null ? null : Loader.instance().activeModContainer().getModId();
        boolean validActiveMod = activeMod != null && !activeMod.equals("minecraft") && !activeMod.equals("forge");
        if(validActiveMod){
            if(!activeMod.equals(modid))
                CoreLib.LOGGER.warn("Mod '" + Loader.instance().activeModContainer().getName() + "' is requesting registration helper for different modid '" + modid + "'!");
        }else if(modid.equals("minecraft") || modid.equals("forge"))
            CoreLib.LOGGER.warn("Mod is requesting registration helper for modid '" + modid + "'!");

        return REGISTRATION_HELPER_MAP.computeIfAbsent(modid, id -> new RegistrationHandler(validActiveMod ? activeMod : modid, modid));
    }

    private final String owningMod;
    private final String modid;
    private final Map<Registries.Registry<?>,Map<ResourceLocation,Supplier<?>>> entryMap = new HashMap<>();
    private final Map<Registries.Registry<?>,List<Consumer<Helper<?>>>> callbacks = new HashMap<>();
    private final Set<Registries.Registry<?>> encounteredEvents = new HashSet<>();

    private RegistrationHandler(String owningMod, String modid){
        this.owningMod = owningMod;
        this.modid = modid;
    }

    public void registerBlock(String identifier, Supplier<Block> block){
        this.addEntry(Registries.BLOCKS, identifier, block);
    }

    public void registerBlock(String identifier, Block block){
        this.addEntry(Registries.BLOCKS, identifier, () -> block);
    }

    public void registerBlockOverride(String namespace, String identifier, Supplier<Block> block){
        this.addEntry(Registries.BLOCKS, namespace, identifier, block);
    }

    public void registerBlockOverride(String namespace, String identifier, Block block){
        this.addEntry(Registries.BLOCKS, namespace, identifier, () -> block);
    }

    public void registerBlockCallback(Consumer<Helper<Block>> callback){
        this.addCallback(Registries.BLOCKS, callback);
    }

    public void registerFluid(String identifier, Supplier<Fluid> fluid){
        this.addEntry(Registries.FLUIDS, identifier, fluid);
    }

    public void registerFluid(String identifier, Fluid fluid){
        this.addEntry(Registries.FLUIDS, identifier, () -> fluid);
    }

    public void registerFluidOverride(String namespace, String identifier, Supplier<Fluid> fluid){
        this.addEntry(Registries.FLUIDS, namespace, identifier, fluid);
    }

    public void registerFluidOverride(String namespace, String identifier, Fluid fluid){
        this.addEntry(Registries.FLUIDS, namespace, identifier, () -> fluid);
    }

    public void registerFluidCallback(Consumer<Helper<Fluid>> callback){
        this.addCallback(Registries.FLUIDS, callback);
    }

    public void registerItem(String identifier, Supplier<Item> item){
        this.addEntry(Registries.ITEMS, identifier, item);
    }

    public void registerItem(String identifier, Item item){
        this.addEntry(Registries.ITEMS, identifier, () -> item);
    }

    public void registerItemOverride(String namespace, String identifier, Supplier<Item> item){
        this.addEntry(Registries.ITEMS, namespace, identifier, item);
    }

    public void registerItemOverride(String namespace, String identifier, Item item){
        this.addEntry(Registries.ITEMS, namespace, identifier, () -> item);
    }

    public void registerItemCallback(Consumer<Helper<Item>> callback){
        this.addCallback(Registries.ITEMS, callback);
    }

    public void registerMobEffect(String identifier, Supplier<Potion> effect){
        this.addEntry(Registries.MOB_EFFECTS, identifier, effect);
    }

    public void registerMobEffect(String identifier, Potion effect){
        this.addEntry(Registries.MOB_EFFECTS, identifier, () -> effect);
    }

    public void registerMobEffectOverride(String namespace, String identifier, Supplier<Potion> effect){
        this.addEntry(Registries.MOB_EFFECTS, namespace, identifier, effect);
    }

    public void registerMobEffectOverride(String namespace, String identifier, Potion effect){
        this.addEntry(Registries.MOB_EFFECTS, namespace, identifier, () -> effect);
    }

    public void registerMobEffectCallback(Consumer<Helper<Potion>> callback){
        this.addCallback(Registries.MOB_EFFECTS, callback);
    }

    public void registerSoundEvent(String identifier, Supplier<SoundEvent> sound){
        this.addEntry(Registries.SOUND_EVENTS, identifier, sound);
    }

    public void registerSoundEvent(String identifier, SoundEvent sound){
        this.addEntry(Registries.SOUND_EVENTS, identifier, () -> sound);
    }

    public void registerSoundEventOverride(String namespace, String identifier, Supplier<SoundEvent> sound){
        this.addEntry(Registries.SOUND_EVENTS, namespace, identifier, sound);
    }

    public void registerSoundEventOverride(String namespace, String identifier, SoundEvent sound){
        this.addEntry(Registries.SOUND_EVENTS, namespace, identifier, () -> sound);
    }

    public void registerSoundEventCallback(Consumer<Helper<SoundEvent>> callback){
        this.addCallback(Registries.SOUND_EVENTS, callback);
    }

    public void registerPotion(String identifier, Supplier<PotionType> potion){
        this.addEntry(Registries.POTIONS, identifier, potion);
    }

    public void registerPotion(String identifier, PotionType potion){
        this.addEntry(Registries.POTIONS, identifier, () -> potion);
    }

    public void registerPotionOverride(String namespace, String identifier, Supplier<PotionType> potion){
        this.addEntry(Registries.POTIONS, namespace, identifier, potion);
    }

    public void registerPotionOverride(String namespace, String identifier, PotionType potion){
        this.addEntry(Registries.POTIONS, namespace, identifier, () -> potion);
    }

    public void registerPotionCallback(Consumer<Helper<PotionType>> callback){
        this.addCallback(Registries.POTIONS, callback);
    }

    public void registerEnchantment(String identifier, Supplier<Enchantment> enchantment){
        this.addEntry(Registries.ENCHANTMENTS, identifier, enchantment);
    }

    public void registerEnchantment(String identifier, Enchantment enchantment){
        this.addEntry(Registries.ENCHANTMENTS, identifier, () -> enchantment);
    }

    public void registerEnchantmentOverride(String namespace, String identifier, Supplier<Enchantment> enchantment){
        this.addEntry(Registries.ENCHANTMENTS, namespace, identifier, enchantment);
    }

    public void registerEnchantmentOverride(String namespace, String identifier, Enchantment enchantment){
        this.addEntry(Registries.ENCHANTMENTS, namespace, identifier, () -> enchantment);
    }

    public void registerEnchantmentCallback(Consumer<Helper<Enchantment>> callback){
        this.addCallback(Registries.ENCHANTMENTS, callback);
    }

    public void registerEntityType(String identifier, Supplier<EntityEntry> entityType){
        this.addEntry(Registries.ENTITY_TYPES, identifier, entityType);
    }

    public void registerEntityType(String identifier, EntityEntry entityType){
        this.addEntry(Registries.ENTITY_TYPES, identifier, () -> entityType);
    }

    public void registerEntityTypeOverride(String namespace, String identifier, Supplier<EntityEntry> entityType){
        this.addEntry(Registries.ENTITY_TYPES, namespace, identifier, entityType);
    }

    public void registerEntityTypeOverride(String namespace, String identifier, EntityEntry entityType){
        this.addEntry(Registries.ENTITY_TYPES, namespace, identifier, () -> entityType);
    }

    public void registerEntityTypeCallback(Consumer<Helper<EntityEntry>> callback){
        this.addCallback(Registries.ENTITY_TYPES, callback);
    }

    public void registerBlockEntityType(String identifier, Supplier<BaseBlockEntityType<?>> blockEntityType){
        this.addEntry(Registries.BLOCK_ENTITY_TYPES, identifier, blockEntityType);
    }

    public void registerBlockEntityType(String identifier, BaseBlockEntityType<?> blockEntityType){
        this.addEntry(Registries.BLOCK_ENTITY_TYPES, identifier, () -> blockEntityType);
    }

    public void registerBlockEntityTypeOverride(String namespace, String identifier, Supplier<BaseBlockEntityType<?>> blockEntityType){
        this.addEntry(Registries.BLOCK_ENTITY_TYPES, namespace, identifier, blockEntityType);
    }

    public void registerBlockEntityTypeOverride(String namespace, String identifier, BaseBlockEntityType<?> blockEntityType){
        this.addEntry(Registries.BLOCK_ENTITY_TYPES, namespace, identifier, () -> blockEntityType);
    }

    public void registerBlockEntityTypeCallback(Consumer<Helper<BaseBlockEntityType<?>>> callback){
        this.addCallback(Registries.BLOCK_ENTITY_TYPES, callback);
    }

    public void registerBlockEntityClass(String identifier, Class<? extends TileEntity> blockEntityType){
        this.addEntry(Registries.BLOCK_ENTITY_CLASSES, identifier, () -> blockEntityType);
    }

    public void registerBlockEntityClassOverride(String namespace, String identifier, Class<? extends TileEntity> blockEntityType){
        this.addEntry(Registries.BLOCK_ENTITY_CLASSES, namespace, identifier, () -> blockEntityType);
    }

    public void registerBlockEntityClassCallback(Consumer<Helper<Class<? extends TileEntity>>> callback){
        this.addCallback(Registries.BLOCK_ENTITY_CLASSES, callback);
    }

    public void registerMenuType(String identifier, Supplier<BaseContainerType<?>> menuType){
        this.addEntry(Registries.MENU_TYPES, identifier, menuType);
    }

    public void registerMenuType(String identifier, BaseContainerType<?> menuType){
        this.addEntry(Registries.MENU_TYPES, identifier, () -> menuType);
    }

    public void registerMenuTypeOverride(String namespace, String identifier, Supplier<BaseContainerType<?>> menuType){
        this.addEntry(Registries.MENU_TYPES, namespace, identifier, menuType);
    }

    public void registerMenuTypeOverride(String namespace, String identifier, BaseContainerType<?> menuType){
        this.addEntry(Registries.MENU_TYPES, namespace, identifier, () -> menuType);
    }

    public void registerMenuTypeCallback(Consumer<Helper<BaseContainerType<?>>> callback){
        this.addCallback(Registries.MENU_TYPES, callback);
    }

    public void registerConditionSerializer(String identifier, Supplier<IConditionFactory> recipeSerializer){
        this.addEntry(Registries.RECIPE_CONDITION_SERIALIZERS, identifier, recipeSerializer);
    }

    public void registerConditionSerializer(String identifier, IConditionFactory recipeSerializer){
        this.addEntry(Registries.RECIPE_CONDITION_SERIALIZERS, identifier, () -> recipeSerializer);
    }

    public void registerConditionSerializerOverride(String namespace, String identifier, Supplier<IConditionFactory> recipeSerializer){
        this.addEntry(Registries.RECIPE_CONDITION_SERIALIZERS, namespace, identifier, recipeSerializer);
    }

    public void registerConditionSerializerOverride(String namespace, String identifier, IConditionFactory recipeSerializer){
        this.addEntry(Registries.RECIPE_CONDITION_SERIALIZERS, namespace, identifier, () -> recipeSerializer);
    }

    public void registerConditionSerializerCallback(Consumer<Helper<IConditionFactory>> callback){
        this.addCallback(Registries.RECIPE_CONDITION_SERIALIZERS, callback);
    }

    public void registerRecipeConditionSerializer(String identifier, Supplier<RecipeConditionSerializer<?>> recipeSerializer){
        this.registerConditionSerializer(identifier, () -> RecipeConditionSerializer.createForgeConditionSerializer(new ResourceLocation(this.modid, identifier), recipeSerializer.get()));
    }

    public void registerRecipeConditionSerializer(String identifier, RecipeConditionSerializer<?> recipeSerializer){
        this.registerConditionSerializer(identifier, () -> RecipeConditionSerializer.createForgeConditionSerializer(new ResourceLocation(this.modid, identifier), recipeSerializer));
    }

    public void registerRecipeConditionSerializerOverride(String namespace, String identifier, Supplier<RecipeConditionSerializer<?>> recipeSerializer){
        this.registerConditionSerializerOverride(namespace, identifier, () -> RecipeConditionSerializer.createForgeConditionSerializer(new ResourceLocation(namespace, identifier), recipeSerializer.get()));
    }

    public void registerRecipeConditionSerializerOverride(String namespace, String identifier, RecipeConditionSerializer<?> recipeSerializer){
        this.registerConditionSerializerOverride(namespace, identifier, () -> RecipeConditionSerializer.createForgeConditionSerializer(new ResourceLocation(namespace, identifier), recipeSerializer));
    }

    public void registerRecipeConditionSerializerCallback(Consumer<Helper<RecipeConditionSerializer<?>>> callback){
        this.registerConditionSerializerCallback(helper -> callback.accept(new Helper<RecipeConditionSerializer<?>>(null) {
            @Override
            public <X extends RecipeConditionSerializer<?>> X register(String identifier, X object){
                helper.register(identifier, RecipeConditionSerializer.createForgeConditionSerializer(new ResourceLocation(RegistrationHandler.this.modid, identifier), object));
                return object;
            }

            @Override
            public <X extends RecipeConditionSerializer<?>> X registerOverride(String namespace, String identifier, X object){
                helper.register(namespace, identifier, RecipeConditionSerializer.createForgeConditionSerializer(new ResourceLocation(namespace, identifier), object));
                return object;
            }
        }));
    }

    private <T> void addEntry(Registries.Registry<T> registry, String identifier, Supplier<T> entry){
        this.addEntry(registry, this.modid, identifier, entry);
    }

    private <T> void addEntry(Registries.Registry<T> registry, String namespace, String identifier, Supplier<T> entry){
        if(this.encounteredEvents.contains(registry))
            throw new IllegalStateException("Cannot register new entries after RegisterEvent has been fired!");
        if(!RegistryUtil.isValidNamespace(namespace))
            throw new IllegalArgumentException("Namespace '" + namespace + "' must only contain characters [a-z0-9_.-]!");
        if(!RegistryUtil.isValidPath(identifier))
            throw new IllegalArgumentException("Identifier '" + identifier + "' must only contain characters [a-z0-9_./-]!");
        if(entry == null)
            throw new IllegalArgumentException("Entry supplier for '" + namespace + ":" + identifier + "' must not be null!");

        ResourceLocation fullIdentifier = new ResourceLocation(namespace, identifier);
        Map<ResourceLocation,Supplier<?>> entries = this.entryMap.computeIfAbsent(registry, o -> new HashMap<>());
        if(entries.containsKey(fullIdentifier))
            throw new RuntimeException("Duplicate entry '" + fullIdentifier + "' for registry of type '" + registry.getValueClass().getName() + "'!");

        entries.put(fullIdentifier, entry);
    }

    private <T> void addCallback(Registries.Registry<T> registry, Consumer<Helper<T>> callback){
        if(this.encounteredEvents.contains(registry))
            throw new IllegalStateException("Cannot register callbacks after RegisterEvent has been fired!");
        if(callback == null)
            throw new IllegalArgumentException("Registration callback must not be null!");

        //noinspection unchecked,rawtypes
        this.callbacks.computeIfAbsent(registry, o -> new ArrayList<>()).add((Consumer)callback);
    }

    private void handleRegisterEvent(Registries.Registry<?> registry){
        this.handleRegistry(registry);
        for(Registries.Registry<?> otherRegistry : Registries.REGISTRATION_ORDER_MAP.getOrDefault(registry, Collections.emptyList()))
            this.handleRegistry(otherRegistry);
    }

    private void handleRegistry(Registries.Registry<?> registry){
        this.encounteredEvents.add(registry);

        // Register entries
        if(this.entryMap.containsKey(registry))
            this.registerEntries(registry);

        // Call callbacks
        if(this.callbacks.containsKey(registry))
            this.callCallbacks(registry);
    }

    @SuppressWarnings("unchecked")
    private <T> void registerEntries(Registries.Registry<T> registry){
        Map<ResourceLocation,Supplier<?>> entries = this.entryMap.get(registry);
        for(Map.Entry<ResourceLocation,Supplier<?>> entry : entries.entrySet()){
            T object = (T)entry.getValue().get();
            registry.register(entry.getKey(), object);
        }
    }

    private void callCallbacks(Registries.Registry<?> registry){
        Helper<?> helper = new Helper<>(registry);
        List<Consumer<Helper<?>>> callbacks = this.callbacks.get(registry);
        for(Consumer<Helper<?>> callback : callbacks)
            callback.accept(helper);
    }

    public class Helper<T> {

        private final Registries.Registry<T> registry;

        public Helper(Registries.Registry<T> registry){
            this.registry = registry;
        }

        public <X extends T> X register(String identifier, X object){
            this.register(RegistrationHandler.this.modid, identifier, object);
            return object;
        }

        public <X extends T> X registerOverride(String namespace, String identifier, X object){
            this.register(namespace, identifier, object);
            return object;
        }

        private void register(String namespace, String identifier, T object){
            if(!RegistryUtil.isValidNamespace(namespace))
                throw new IllegalArgumentException("Namespace '" + namespace + "' must only contain characters [a-z0-9_.-]!");
            if(!RegistryUtil.isValidPath(identifier))
                throw new IllegalArgumentException("Identifier '" + identifier + "' must only contain characters [a-z0-9_./-]!");

            ResourceLocation fullIdentifier = new ResourceLocation(namespace, identifier);
            Map<ResourceLocation,Supplier<?>> entries = RegistrationHandler.this.entryMap.computeIfAbsent(this.registry, o -> new HashMap<>());
            if(entries.containsKey(fullIdentifier))
                throw new RuntimeException("Duplicate entry '" + fullIdentifier + "' for registry of type '" + this.registry.getValueClass().getName() + "'!");

            this.registry.register(fullIdentifier, object);
        }
    }
}