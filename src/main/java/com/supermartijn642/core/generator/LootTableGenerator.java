package com.supermartijn642.core.generator;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.supermartijn642.core.loot_table.BinomialNumberProvider;
import com.supermartijn642.core.loot_table.SurvivesExplosionLootCondition;
import com.supermartijn642.core.loot_table.ToolMatchLootCondition;
import com.supermartijn642.core.registry.Registries;
import net.minecraft.advancements.critereon.EnchantmentPredicate;
import net.minecraft.advancements.critereon.ItemPredicate;
import net.minecraft.advancements.critereon.MinMaxBounds;
import net.minecraft.advancements.critereon.NBTPredicate;
import net.minecraft.block.Block;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.init.Enchantments;
import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.storage.loot.*;
import net.minecraft.world.storage.loot.conditions.LootCondition;
import net.minecraft.world.storage.loot.conditions.LootConditionManager;
import net.minecraft.world.storage.loot.functions.EnchantWithLevels;
import net.minecraft.world.storage.loot.functions.LootFunction;
import net.minecraft.world.storage.loot.functions.LootFunctionManager;
import net.minecraft.world.storage.loot.functions.SetCount;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Created 20/08/2022 by SuperMartijn642
 */
public abstract class LootTableGenerator extends ResourceGenerator {

    private static final Gson GSON = LootTableManager.GSON_INSTANCE;

    private final Map<ResourceLocation,LootTableBuilder> lootTables = new HashMap<>();

    public LootTableGenerator(String modid, ResourceCache cache){
        super(modid, cache);
    }

    @Override
    public void save(){
        // Loop over all loot tables
        for(LootTableBuilder lootTableBuilder : this.lootTables.values()){
            JsonObject json = new JsonObject();
            // Functions
            if(!lootTableBuilder.functions.isEmpty()){
                JsonArray functionsJson = new JsonArray();
                for(LootFunction function : lootTableBuilder.functions)
                    functionsJson.add(GSON.toJsonTree(function));
                json.add("functions", functionsJson);
            }
            // Pools
            int poolNameCounter = 1;
            if(!lootTableBuilder.pools.isEmpty()){
                JsonArray poolsJson = new JsonArray();
                // Loop over all pools
                for(LootPoolBuilder pool : lootTableBuilder.pools){
                    JsonObject poolJson = new JsonObject();
                    // Name
                    poolJson.addProperty("name", pool.name == null || pool.name.isEmpty() ? "pool" + poolNameCounter++ : pool.name);
                    // Rolls
                    if(pool.rolls instanceof BinomialNumberProvider){
                        JsonObject rollsJson = new JsonObject();
                        rollsJson.addProperty("type", "supermartijn642corelib:binomial");
                        rollsJson.addProperty("n", ((BinomialNumberProvider)pool.rolls).getN());
                        rollsJson.addProperty("p", ((BinomialNumberProvider)pool.rolls).getP());
                        poolJson.add("rolls", rollsJson);
                    }else
                        poolJson.add("rolls", GSON.toJsonTree(pool.rolls));
                    // Bonus rolls
                    if(pool.bonusRolls instanceof BinomialNumberProvider){
                        JsonObject rollsJson = new JsonObject();
                        rollsJson.addProperty("type", "supermartijn642corelib:binomial");
                        rollsJson.addProperty("n", ((BinomialNumberProvider)pool.bonusRolls).getN());
                        rollsJson.addProperty("p", ((BinomialNumberProvider)pool.bonusRolls).getP());
                        poolJson.add("bonus_rolls", rollsJson);
                    }else if(pool.bonusRolls.getMin() != 0 || pool.bonusRolls.getMax() != 0)
                        poolJson.add("bonus_rolls", GSON.toJsonTree(pool.bonusRolls));
                    // Conditions
                    if(!pool.conditions.isEmpty()){
                        JsonArray conditionsJson = new JsonArray();
                        for(LootCondition condition : pool.conditions)
                            conditionsJson.add(GSON.toJsonTree(condition));
                        poolJson.add("conditions", conditionsJson);
                    }
                    // Functions
                    if(!pool.functions.isEmpty()){
                        JsonArray functionsJson = new JsonArray();
                        for(LootFunction function : pool.functions)
                            functionsJson.add(GSON.toJsonTree(function));
                        poolJson.add("functions", functionsJson);
                    }
                    // Entries
                    if(pool.entries.isEmpty())
                        throw new RuntimeException("Loot table '" + lootTableBuilder.identifier + "' has loot pool without any entries!");
                    JsonArray entriesJson = new JsonArray();
                    for(LootEntry entry : pool.entries)
                        entriesJson.add(GSON.toJsonTree(entry));
                    poolJson.add("entries", entriesJson);

                    poolsJson.add(poolJson);
                }
                json.add("pools", poolsJson);
            }

            // Save the object to the cache
            ResourceLocation identifier = lootTableBuilder.identifier;
            this.cache.saveJsonResource(ResourceType.ASSET, json, identifier.getResourceDomain(), "loot_tables", identifier.getResourcePath());
        }
    }

    /**
     * Gets a loot table builder for the given identifier. The returned loot table builder may be a new loot table builder or an existing one if requested before.
     * @param identifier resource location of the loot table
     */
    protected LootTableBuilder lootTable(ResourceLocation identifier){
        this.cache.trackToBeGeneratedResource(ResourceType.ASSET, identifier.getResourceDomain(), "loot_tables", identifier.getResourcePath(), ".json");
        return this.lootTables.computeIfAbsent(identifier, LootTableBuilder::new);
    }

    /**
     * Gets a loot table builder for the given namespace and path. The returned loot table builder may be a new loot table builder or an existing one if requested before.
     * @param namespace namespace of the loot table
     * @param path      path of the loot table
     */
    protected LootTableBuilder lootTable(String namespace, String path){
        return this.lootTable(new ResourceLocation(namespace, path));
    }

    /**
     * Gets a loot table builder for the given block. The returned loot table builder may be a new loot table builder or an existing one if requested before.
     * @param block block to create the loot table for
     */
    protected LootTableBuilder lootTable(Block block){
        ResourceLocation identifier = Registries.BLOCKS.getIdentifier(block);
        return this.lootTable(new ResourceLocation(identifier.getResourceDomain(), "blocks/" + identifier.getResourcePath()));
    }

    /**
     * Creates a basic loot table for the given block to drop itself when broken.
     * @param block block to create the loot table for
     */
    protected LootTableBuilder dropSelf(Block block){
        return this.lootTable(block).pool(poolBuilder -> poolBuilder.survivesExplosionCondition().itemEntry(block));
    }

    /**
     * Creates a basic loot table for the given to drop itself when broken with a silk touch tool.
     * @param block block to create the loot table for
     */
    protected LootTableBuilder dropSelfWhenSilkTouch(Block block){
        return this.lootTable(block).pool(poolBuilder -> poolBuilder.hasEnchantmentCondition(Enchantments.SILK_TOUCH).itemEntry(block));
    }

    @Override
    public String getName(){
        return this.modName + " Loot Table Generator";
    }

    public static class LootTableBuilder {

        protected final ResourceLocation identifier;
        private final List<LootPoolBuilder> pools = new ArrayList<>();
        private final List<LootFunction> functions = new ArrayList<>();

        protected LootTableBuilder(ResourceLocation identifier){
            this.identifier = identifier;
        }

        /**
         * Constructs a new loot pool for this loot table.
         * @param poolBuilderConsumer consumer to build the loot pool
         */
        public LootTableBuilder pool(Consumer<LootPoolBuilder> poolBuilderConsumer){
            LootPoolBuilder poolBuilder = new LootPoolBuilder();
            poolBuilderConsumer.accept(poolBuilder);
            this.pools.add(poolBuilder);
            return this;
        }

        /**
         * Adds the given item function to this loot table.
         * @param function item function to be added
         */
        public LootTableBuilder function(LootFunction function){
            if(!LootFunctionManager.CLASS_TO_SERIALIZER_MAP.containsKey(function.getClass()))
                throw new IllegalArgumentException("Cannot use unregistered item function '" + function + "'!");

            this.functions.add(function);
            return this;
        }
    }

    public static class LootPoolBuilder {

        private final List<LootCondition> conditions = new ArrayList<>();
        private final List<LootFunction> functions = new ArrayList<>();
        private final List<LootEntry> entries = new ArrayList<>();
        private RandomValueRange rolls = new RandomValueRange(1);
        private RandomValueRange bonusRolls = new RandomValueRange(0);
        private String name;

        protected LootPoolBuilder(){
        }

        /**
         * Sets the number provider for the number of rolls for this loot pool.
         * @param provider number provider for number of rolls
         */
        public LootPoolBuilder rolls(RandomValueRange provider){
            this.rolls = provider;
            return this;
        }

        /**
         * Sets the number provider for the number of rolls to a constant with the given value.
         * @param rolls number of rolls
         */
        public LootPoolBuilder constantRolls(int rolls){
            return this.rolls(new RandomValueRange(rolls));
        }

        /**
         * Sets the number provider for the number of rolls to a uniform chance between the given minimum and maximum.
         * @param min minimum number of rolls
         * @param max maximum number of rolls
         */
        public LootPoolBuilder uniformRolls(int min, int max){
            return this.rolls(new RandomValueRange(min, max));
        }

        /**
         * Sets the number provider for the number of rolls to a binomial distribution with the given chance and attempts.
         * @param n number of attempts
         * @param p chance that an attempt succeeds
         */
        public LootPoolBuilder binomialRolls(int n, int p){
            return this.rolls(new BinomialNumberProvider(n, p));
        }

        /**
         * Sets the number provider for the number of bonus rolls for this loot pool.
         * @param provider number provider for number of bonus rolls
         */
        public LootPoolBuilder bonusRolls(RandomValueRange provider){
            this.bonusRolls = provider;
            return this;
        }

        /**
         * Sets the number provider for the number of bonus rolls to a constant with the given value.
         * @param rolls number of bonus rolls
         */
        public LootPoolBuilder constantBonusRolls(int rolls){
            return this.bonusRolls(new RandomValueRange(rolls));
        }

        /**
         * Sets the number provider for the number of bonus rolls to a uniform chance between the given minimum and maximum.
         * @param min minimum number of bonus rolls
         * @param max maximum number of bonus rolls
         */
        public LootPoolBuilder uniformBonusRolls(int min, int max){
            return this.bonusRolls(new RandomValueRange(min, max));
        }

        /**
         * Sets the number provider for the number of bonus rolls to a binomial distribution with the given chance and attempts.
         * @param n number of attempts
         * @param p chance that an attempt succeeds
         */
        public LootPoolBuilder binomialBonusRolls(int n, int p){
            return this.bonusRolls(new BinomialNumberProvider(n, p));
        }

        /**
         * Sets the name for this loot pool.
         * @param name name for the pool
         */
        public LootPoolBuilder name(String name){
            this.name = name;
            return this;
        }

        /**
         * Adds the given item condition to this loot pool.
         * @param condition condition to be added
         */
        public LootPoolBuilder condition(LootCondition condition){
            if(!LootConditionManager.CLASS_TO_SERIALIZER_MAP.containsKey(condition.getClass()))
                throw new IllegalArgumentException("Cannot use unregistered loot pool condition '" + condition + "'!");

            this.conditions.add(condition);
            return this;
        }

        /**
         * Adds a survives explosion condition to this loot pool
         */
        public LootPoolBuilder survivesExplosionCondition(){
            return this.condition(new SurvivesExplosionLootCondition());
        }

        /**
         * Adds a condition for the used tool to have the given enchantment.
         * @param enchantment enchantment required
         * @param minLevel    minimum level of the enchantment (inclusive)
         * @param maxLevel    maximum level of the enchantment (inclusive)
         */
        public LootPoolBuilder hasEnchantmentCondition(Enchantment enchantment, int minLevel, int maxLevel){
            EnchantmentPredicate enchantmentPredicate = new EnchantmentPredicate(enchantment, new MinMaxBounds((float)minLevel, (float)maxLevel));
            ItemPredicate itemPredicate = new ItemPredicate(null, null, MinMaxBounds.UNBOUNDED, MinMaxBounds.UNBOUNDED, new EnchantmentPredicate[]{enchantmentPredicate}, null, NBTPredicate.ANY);
            return this.condition(new ToolMatchLootCondition(itemPredicate));
        }

        /**
         * Adds a condition for the used tool to have the given enchantment.
         * @param enchantment enchantment required
         * @param minLevel    minimum level of the enchantment
         */
        public LootPoolBuilder hasEnchantmentCondition(Enchantment enchantment, int minLevel){
            EnchantmentPredicate enchantmentPredicate = new EnchantmentPredicate(enchantment, new MinMaxBounds((float)minLevel, null));
            ItemPredicate itemPredicate = new ItemPredicate(null, null, MinMaxBounds.UNBOUNDED, MinMaxBounds.UNBOUNDED, new EnchantmentPredicate[]{enchantmentPredicate}, null, NBTPredicate.ANY);
            return this.condition(new ToolMatchLootCondition(itemPredicate));
        }

        /**
         * Adds a condition for the used tool to have the given enchantment.
         * @param enchantment enchantment required
         */
        public LootPoolBuilder hasEnchantmentCondition(Enchantment enchantment){
            return this.hasEnchantmentCondition(enchantment, 1);
        }

        /**
         * Adds an entry to this loot pool.
         * @param entry entry to be added
         */
        public LootPoolBuilder entry(LootEntry entry){
            this.entries.add(entry);
            return this;
        }

        /**
         * Adds an empty entry to this loot pool.
         * @param weight weight of the entry
         */
        public LootPoolBuilder emptyEntry(int weight){
            return this.entry(new LootEntryEmpty(weight, 0, new LootCondition[0], null));
        }

        /**
         * Adds an empty entry to this loot pool.
         */
        public LootPoolBuilder emptyEntry(){
            return this.emptyEntry(1);
        }

        /**
         * Adds an item entry to this loot pool.
         * @param item   item to be added as an entry
         * @param weight weight of the entry
         */
        public LootPoolBuilder itemEntry(Item item, int weight){
            return this.entry(new LootEntryItem(item, weight, 0, new LootFunction[0], new LootCondition[0], null));
        }

        /**
         * Adds an item entry to this loot pool.
         * @param block  block to be added as an entry
         * @param weight weight of the entry
         */
        public LootPoolBuilder itemEntry(Block block, int weight){
            return this.itemEntry(Item.getItemFromBlock(block), weight);
        }

        /**
         * Adds an item entry to this loot pool.
         * @param item item to be added as an entry
         */
        public LootPoolBuilder itemEntry(Item item){
            return this.itemEntry(item, 1);
        }

        /**
         * Adds an item entry to this loot pool.
         * @param block block to be added as an entry
         */
        public LootPoolBuilder itemEntry(Block block){
            return this.itemEntry(Item.getItemFromBlock(block), 1);
        }

        /**
         * Adds an item entry to this loot pool.
         * @param item   item to be added as an entry
         * @param count  the number of items in the item stack
         * @param weight weight of the entry
         */
        public LootPoolBuilder itemEntry(Item item, int count, int weight){
            return this.entry(new LootEntryItem(item, weight, 0, new LootFunction[]{new SetCount(new LootCondition[0], new RandomValueRange(count))}, new LootCondition[0], null));
        }

        /**
         * Adds an item entry to this loot pool.
         * @param block  block to be added as an entry
         * @param count  the number of items in the item stack
         * @param weight weight of the entry
         */
        public LootPoolBuilder itemEntry(Block block, int count, int weight){
            return this.itemEntry(Item.getItemFromBlock(block), count, weight);
        }

        /**
         * Adds an item entry to this loot pool.
         * @param item   item to be added as an entry
         * @param min    the minimum size of the item stack
         * @param max    the maximum size of the item stack
         * @param weight weight of the entry
         */
        public LootPoolBuilder itemEntry(Item item, int min, int max, int weight){
            return this.entry(new LootEntryItem(item, weight, 0, new LootFunction[]{new SetCount(new LootCondition[0], new RandomValueRange(min, max))}, new LootCondition[0], null));
        }

        /**
         * Adds an item entry to this loot pool.
         * @param block  block to be added as an entry
         * @param min    the minimum size of the item stack
         * @param max    the maximum size of the item stack
         * @param weight weight of the entry
         */
        public LootPoolBuilder itemEntry(Block block, int min, int max, int weight){
            return this.itemEntry(Item.getItemFromBlock(block), min, max, weight);
        }

        /**
         * Adds an item entry which will be enchanted.
         * @param item        item to be enchanted
         * @param levels      the number of levels the item will be enchanted with
         * @param allowCurses whether the items may be enchanted with curses
         * @param weight      weight of the entry
         */
        public LootPoolBuilder enchantedItemEntry(Item item, int levels, boolean allowCurses, int weight){
            EnchantWithLevels function = new EnchantWithLevels(new LootCondition[0], new RandomValueRange(levels), allowCurses);
            return this.entry(new LootEntryItem(item, weight, 0, new LootFunction[]{function}, new LootCondition[0], null));
        }

        /**
         * Adds an item entry which will be enchanted.
         * @param block       block to be enchanted
         * @param levels      the number of levels the item will be enchanted with
         * @param allowCurses whether the items may be enchanted with curses
         * @param weight      weight of the entry
         */
        public LootPoolBuilder enchantedItemEntry(Block block, int levels, boolean allowCurses, int weight){
            return this.enchantedItemEntry(Item.getItemFromBlock(block), levels, allowCurses, weight);
        }

        /**
         * Adds an item entry which will be enchanted.
         * @param item        item to be enchanted
         * @param minLevels   the minimum number of levels the item will be enchanted with
         * @param maxLevels   the maximum number of levels the item will be enchanted with
         * @param allowCurses whether the items may be enchanted with curses
         * @param weight      weight of the entry
         */
        public LootPoolBuilder enchantedItemEntry(Item item, int minLevels, int maxLevels, boolean allowCurses, int weight){
            EnchantWithLevels function = new EnchantWithLevels(new LootCondition[0], new RandomValueRange(minLevels, maxLevels), allowCurses);
            return this.entry(new LootEntryItem(item, weight, 0, new LootFunction[]{function}, new LootCondition[0], null));
        }

        /**
         * Adds an item entry which will be enchanted.
         * @param block       block to be enchanted
         * @param minLevels   the minimum number of levels the item will be enchanted with
         * @param maxLevels   the maximum number of levels the item will be enchanted with
         * @param allowCurses whether the items may be enchanted with curses
         * @param weight      weight of the entry
         */
        public LootPoolBuilder enchantedItemEntry(Block block, int minLevels, int maxLevels, boolean allowCurses, int weight){
            return this.enchantedItemEntry(Item.getItemFromBlock(block), minLevels, maxLevels, allowCurses, weight);
        }

        /**
         * Adds an item entry to this loot pool.
         * @param item item to be added as an entry
         */
        public LootPoolBuilder itemEntry(ResourceLocation item){
            if(!Registries.ITEMS.hasIdentifier(item))
                throw new IllegalArgumentException("Could not find any item registered under '" + item + "'!");

            return this.itemEntry(Registries.ITEMS.getValue(item));
        }

        /**
         * Adds an item entry to this loot pool.
         * @param namespace  namespace of the item to be added as an entry
         * @param identifier path of the item to be added as an entry
         */
        public LootPoolBuilder itemEntry(String namespace, String identifier){
            return this.itemEntry(new ResourceLocation(namespace, identifier));
        }

        /**
         * Adds a loot table entry to this loot pool.
         * @param lootTable loot table to be added as an entry
         * @param weight    weight of the entry
         */
        public LootPoolBuilder lootTableEntry(ResourceLocation lootTable, int weight){
            return this.entry(new LootEntryTable(lootTable, weight, 0, new LootCondition[0], null));
        }

        /**
         * Adds a loot table entry to this loot pool.
         * @param lootTable loot table to be added as an entry
         */
        public LootPoolBuilder lootTableEntry(ResourceLocation lootTable){
            return this.lootTableEntry(lootTable, 1);
        }

        /**
         * Adds a loot table entry to this loot pool.
         * @param namespace namespace of the loot table to be added as an entry
         * @param path      path of the loot table to be added as an entry
         * @param weight    weight of the entry
         */
        public LootPoolBuilder lootTableEntry(String namespace, String path, int weight){
            return this.lootTableEntry(new ResourceLocation(namespace, path), weight);
        }

        /**
         * Adds a loot table entry to this loot pool.
         * @param namespace namespace of the loot table to be added as an entry
         * @param path      path of the loot table to be added as an entry
         */
        public LootPoolBuilder lootTableEntry(String namespace, String path){
            return this.lootTableEntry(new ResourceLocation(namespace, path));
        }

        /**
         * Adds an item function to this loot table.
         * @param function item function to be added
         */
        public LootPoolBuilder function(LootFunction function){
            if(!LootFunctionManager.CLASS_TO_SERIALIZER_MAP.containsKey(function.getClass()))
                throw new IllegalArgumentException("Cannot use unregistered item function '" + function + "'!");

            this.functions.add(function);
            return this;
        }
    }
}
