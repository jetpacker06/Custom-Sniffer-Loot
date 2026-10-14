package com.jetpacker06.customsnifferloot;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.arguments.item.ItemInput;
import net.minecraft.commands.arguments.item.ItemParser;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.SetComponentsFunction;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.LootTableLoadEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Scanner;

@Mod(CustomSnifferLoot.MOD_ID)
public class CustomSnifferLoot {
    public static final String MOD_ID = "customsnifferloot";
    public static final String FILENAME = "custom_sniffer_loot.json";

    private static final Logger LOGGER = LogManager.getLogger();

    private static boolean debugMode;

    private record SnifferLootEntry(ItemStack item, int weight) {}

    private static final ArrayList<SnifferLootEntry> lootEntries = new ArrayList<>();

    // 1.21 requires IEventBus and ModContainer passed into the constructor
    public CustomSnifferLoot(IEventBus modEventBus, ModContainer modContainer) {
        NeoForge.EVENT_BUS.register(this);
    }

    private static void debugPrint(Object message) {
        if (debugMode) {
            LOGGER.info(message);
        }
    }

    private static void loadConfig(HolderLookup.Provider lookupProvider) {
        lootEntries.clear(); // Always clear to support /reload
        try {
            File configFile = new File(FMLPaths.GAMEDIR.get() + "/config/" + FILENAME);
            if (!configFile.exists()) {
                configFile.createNewFile();
                try (FileWriter writer = new FileWriter(configFile)) {
                    // Default config template for 1.20.5+ Component syntax
                    writer.write("""
                    {
                      "debugMode": false,
                      "snifferItems": [
                        {
                          "_comment": "Note: removing these entries will remove them from the pool. Alternatively, set weight to 0",
                          "item": "minecraft:pitcher_pod",
                          "weight": 1
                        },
                        {
                          "item": "minecraft:torchflower_seeds",
                          "weight": 1
                        },
                        {
                          "_comment": "Component example 1. Bracket syntax.",
                          "item": "minecraft:diamond_sword[minecraft:enchantments={levels:{\\"minecraft:sharpness\\":5}}]",
                          "weight": 0
                        },
                        {
                          "_comment": "Component example 2. Setting block entity data on a beehive.",
                          "item": "minecraft:beehive[minecraft:block_entity_data={id:\\"minecraft:beehive\\",Bees:[{entity_data:{id:\\"minecraft:bee\\"},ticks_in_hive:600}]}]",
                          "weight": 0
                        }
                      ]
                    }
                    """);
                }
                LOGGER.info("Created " + FILENAME);
            }

            String rawText = readFile(configFile);
            if (rawText.isEmpty()) {
                throw new IllegalStateException("CustomSnifferLoot file empty");
            }

            JsonObject map = new Gson().fromJson(rawText, JsonObject.class);
            if (map.has("debugMode")) {
                debugMode = map.getAsJsonPrimitive("debugMode").getAsBoolean();
            }

            debugPrint("Current sniffer config:");
            debugPrint(new GsonBuilder().setPrettyPrinting().create().toJson(map));

            if (map.has("snifferItems")) {
                for (JsonElement element : map.getAsJsonArray("snifferItems")) {
                    JsonObject obj = element.getAsJsonObject();
                    if (!obj.has("item") || !obj.has("weight")) continue;

                    String entryString = obj.get("item").getAsString();
                    int weight = obj.get("weight").getAsInt();

                    try {
                        ItemStack stack = parseItemStack(entryString, lookupProvider);
                        if (!stack.isEmpty()) {
                            lootEntries.add(new SnifferLootEntry(stack, weight));
                        }
                    } catch (Exception e) {
                        debugPrint("Failed to parse item entry: " + entryString);
                    }
                }
            }

        } catch (Exception e) {
            LOGGER.error("Error reading CustomSnifferLoot configuration", e);
        }
    }

    private static ItemStack parseItemStack(String entry, HolderLookup.Provider lookupProvider) {
        try {
            // Uses 1.21's instantiated item parser that supports components ([enchantments={...}]) natively
            ItemParser parser = new ItemParser(lookupProvider);
            ItemParser.ItemResult result = parser.parse(new StringReader(entry.trim()));

            ItemInput input = new ItemInput(result.item(), result.components());
            return input.createItemStack(1, false);
        } catch (CommandSyntaxException e) {
            debugPrint("Invalid item entry or components for \"" + entry + "\": " + e.getMessage());
            return ItemStack.EMPTY;
        }
    }

    public static String readFile(File file) throws FileNotFoundException {
        try (Scanner reader = new Scanner(file)) {
            StringBuilder builder = new StringBuilder();
            while (reader.hasNextLine()) {
                builder.append(reader.nextLine());
            }
            return builder.toString();
        }
    }

    // Helper to safely apply components without generic wildcard compile errors
    @SuppressWarnings("unchecked")
    private static <T> void applyComponent(LootItem.Builder<?> builder, net.minecraft.core.component.TypedDataComponent<T> component) {
        builder.apply(SetComponentsFunction.setComponent(component.type(), component.value()));
    }

    @SubscribeEvent
    public void registerLootTable(LootTableLoadEvent event) {
        // BuiltInLootTables are ResourceKeys in 1.21, calling .location() gets the string ResourceLocation
        if (!event.getName().equals(BuiltInLootTables.SNIFFER_DIGGING.location())) {
            return;
        }

        // 1.21 NeoForge's LootTableLoadEvent supplies the registry lookup directly
        loadConfig(event.getRegistries());

        debugPrint("Sniffer loot table loading with " + lootEntries.size() + " items");
        LootPool.Builder pool = LootPool.lootPool().setRolls(ConstantValue.exactly(1f));

        boolean hasValidEntry = false;
        for (SnifferLootEntry entry : lootEntries) {
            if (entry.weight <= 0) continue;

            var entryBuilder = LootItem.lootTableItem(entry.item.getItem());

            // 1.21: Iterate through every configured component and apply it to the loot entry
            if (!entry.item.getComponents().isEmpty()) {
                for (net.minecraft.core.component.TypedDataComponent<?> component : entry.item.getComponents()) {
                    applyComponent(entryBuilder, component);
                }
            }

            entryBuilder.setWeight(entry.weight);
            pool.add(entryBuilder);
            hasValidEntry = true;
        }

        if (hasValidEntry) {
            LootTable.Builder newTableBuilder = LootTable.lootTable()
                    .setParamSet(event.getTable().getParamSet())
                    .setRandomSequence(event.getName())
                    .withPool(pool);

            event.setTable(newTableBuilder.build());
        }
    }
}