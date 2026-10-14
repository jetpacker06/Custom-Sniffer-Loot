package com.jetpacker06.customsnifferloot;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.commands.arguments.item.ItemInput;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.Item;
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
import java.util.Map;
import java.util.Optional;
import java.util.Scanner;

@Mod(CustomSnifferLoot.MOD_ID)
public class CustomSnifferLoot {
    public static final String MOD_ID = "customsnifferloot";
    public static final String FILENAME = "custom_sniffer_loot.json";

    private static final Logger LOGGER = LogManager.getLogger();

    private static boolean debugMode;

    // 1.21.2 FIX: No longer store ItemStacks. Store the raw Item and its component patch!
    private record SnifferLootEntry(Item item, DataComponentPatch components, int weight) {}

    private static final ArrayList<SnifferLootEntry> lootEntries = new ArrayList<>();

    public CustomSnifferLoot(IEventBus modEventBus, ModContainer modContainer) {
        NeoForge.EVENT_BUS.register(this);
        generateDefaultConfig();
    }

    private static void debugPrint(Object message) {
        if (debugMode) {
            LOGGER.info(message);
        }
    }

    private static void generateDefaultConfig() {
        try {
            File configFile = new File(FMLPaths.CONFIGDIR.get().toFile(), FILENAME);
            if (!configFile.exists()) {
                configFile.createNewFile();
                try (FileWriter writer = new FileWriter(configFile)) {
                    writer.write("""
                    {
                      "debugMode": true,
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
                          "_comment": "Component example 1",
                          "item": "minecraft:diamond_sword[minecraft:enchantments={\\"minecraft:sharpness\\":5}]",
                          "weight": 0
                        }
                      ]
                    }
                    """);
                }
                LOGGER.info("Created default config file: " + FILENAME);
            }
        } catch (Exception e) {
            LOGGER.error("Error generating CustomSnifferLoot default configuration", e);
        }
    }

    private static void loadConfig(HolderLookup.Provider lookupProvider) {
        lootEntries.clear();
        try {
            File configFile = new File(FMLPaths.CONFIGDIR.get().toFile(), FILENAME);

            if (!configFile.exists()) {
                generateDefaultConfig();
            }

            String rawText = readFile(configFile);
            if (rawText.isEmpty()) {
                throw new IllegalStateException("CustomSnifferLoot file empty");
            }

            JsonObject map = new Gson().fromJson(rawText, JsonObject.class);
            if (map.has("debugMode")) {
                debugMode = map.getAsJsonPrimitive("debugMode").getAsBoolean();
            }

            if (map.has("snifferItems")) {
                for (JsonElement element : map.getAsJsonArray("snifferItems")) {
                    JsonObject obj = element.getAsJsonObject();
                    if (!obj.has("item") || !obj.has("weight")) continue;

                    String entryString = obj.get("item").getAsString();
                    int weight = obj.get("weight").getAsInt();

                    SnifferLootEntry entry = parseItem(entryString, weight, lookupProvider);
                    if (entry != null) {
                        lootEntries.add(entry);
                    }
                }
            }

        } catch (Exception e) {
            LOGGER.error("Error reading CustomSnifferLoot configuration", e);
        }
    }

    // Parses string but DOES NOT create an ItemStack, avoiding the "Components not bound yet" NPE
    private static SnifferLootEntry parseItem(String entry, int weight, HolderLookup.Provider lookupProvider) {
        try {
            CommandBuildContext ctx = CommandBuildContext.simple(lookupProvider, FeatureFlags.DEFAULT_FLAGS);
            ItemInput input = ItemArgument.item(ctx).parse(new StringReader(entry.trim()));

            // Just grab the raw item and its parsed components
            return new SnifferLootEntry(input.item().value(), input.components(), weight);
        } catch (CommandSyntaxException e) {
            debugPrint("Syntax error in item entry \"" + entry + "\": " + e.getMessage());
            return null;
        } catch (Exception e) {
            LOGGER.error("CRITICAL error parsing item entry: " + entry, e);
            return null;
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

    // Helper method to safely apply wildcards to SetComponentsFunction
    @SuppressWarnings("unchecked")
    private static <T> void applyComponentUnsafe(LootItem.Builder<?> builder, DataComponentType<?> type, Object value) {
        builder.apply(SetComponentsFunction.setComponent((DataComponentType<T>) type, (T) value));
    }

    @SubscribeEvent
    public void registerLootTable(LootTableLoadEvent event) {
        if (!event.getName().equals(BuiltInLootTables.SNIFFER_DIGGING.identifier())) {
            return;
        }

        loadConfig(event.getRegistries());

        debugPrint("Sniffer loot table loading with " + lootEntries.size() + " items");
        LootPool.Builder pool = LootPool.lootPool().setRolls(ConstantValue.exactly(1f));

        boolean hasValidEntry = false;
        for (SnifferLootEntry entry : lootEntries) {
            if (entry.weight <= 0) continue;

            // Start building the item pool entry from the raw Item
            var entryBuilder = LootItem.lootTableItem(entry.item);

            // Directly inject the stored parsed components into the loot table builder!
            if (entry.components != null && !entry.components.isEmpty()) {
                for (Map.Entry<DataComponentType<?>, Optional<?>> mapEntry : entry.components.entrySet()) {
                    // Only apply components that were actually parsed and added (present)
                    if (mapEntry.getValue().isPresent()) {
                        applyComponentUnsafe(entryBuilder, mapEntry.getKey(), mapEntry.getValue().get());
                    }
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