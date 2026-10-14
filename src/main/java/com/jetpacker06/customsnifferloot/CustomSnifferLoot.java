package com.jetpacker06.customsnifferloot;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.SetNbtFunction;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.LootTableLoadEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.IExtensionPoint;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.network.NetworkConstants;
import net.minecraftforge.registries.ForgeRegistries;
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

    public CustomSnifferLoot() {
        MinecraftForge.EVENT_BUS.register(this);

        // Tells Forge that clients don't need this mod to connect to a server that has it
        ModLoadingContext.get().registerExtensionPoint(
                IExtensionPoint.DisplayTest.class,
                () -> new IExtensionPoint.DisplayTest(
                        () -> NetworkConstants.IGNORESERVERONLY,
                        (remoteVersion, isFromServer) -> true
                )
        );
    }

    private static void debugPrint(Object message) {
        if (debugMode) {
            LOGGER.info(message);
        }
    }

    private static void loadConfig() {
        lootEntries.clear(); // Always clear to support /reload
        try {
            File configFile = new File(FMLPaths.GAMEDIR.get() + "/config/" + FILENAME);
            if (!configFile.exists()) {
                configFile.createNewFile();
                try (FileWriter writer = new FileWriter(configFile)) {
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
                              "_comment": "NBT example 1. Any other NBT tags work here too.",
                              "item": "minecraft:diamond_sword{Enchantments:[{id:\\"minecraft:sharpness\\",lvl:5s}]}",
                              "weight": 0
                            },
                            {
                              "_comment": "NBT example 2",
                              "item": "minecraft:beehive{BlockEntityTag:{Bees:[{EntityData:{id:\\"minecraft:bee\\"},TicksInHive:600}]}}",
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
                        ItemStack stack = parseItemStack(entryString);
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

    private static ItemStack parseItemStack(String entry) {
        int nbtIndex = entry.indexOf('{');
        String itemId = nbtIndex != -1 ? entry.substring(0, nbtIndex).trim() : entry.trim();
        String nbtString = nbtIndex != -1 ? entry.substring(nbtIndex).trim() : null;

        // ResourceLocation.tryParse avoids crashing on bad formatting/invalid namespaces
        ResourceLocation rl = ResourceLocation.tryParse(itemId);
        if (rl == null) {
            debugPrint("Invalid resource location syntax: \"" + itemId + "\"");
            return ItemStack.EMPTY;
        }

        Item item = ForgeRegistries.ITEMS.getValue(rl);
        if (item == null || item == Items.AIR) {
            debugPrint("Modded/vanilla item \"" + itemId + "\" does not exist in ForgeRegistries.ITEMS, skipping.");
            return ItemStack.EMPTY;
        }

        ItemStack stack = new ItemStack(item);
        if (nbtString != null) {
            try {
                CompoundTag tag = TagParser.parseTag(nbtString);
                stack.setTag(tag);
            } catch (CommandSyntaxException e) {
                debugPrint("Invalid NBT tag for item \"" + entry + "\": " + e.getMessage());
            }
        }
        return stack;
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

    @SubscribeEvent
    public void registerLootTable(LootTableLoadEvent event) {
        if (!event.getTable().getLootTableId().equals(BuiltInLootTables.SNIFFER_DIGGING)) {
            return;
        }

        loadConfig();

        debugPrint("Sniffer loot table loading with " + lootEntries.size() + " items");
        LootPool.Builder pool = LootPool.lootPool().setRolls(ConstantValue.exactly(1f));

        boolean hasValidEntry = false;
        for (SnifferLootEntry entry : lootEntries) {
            if (entry.weight <= 0) continue;

            var entryBuilder = LootItem.lootTableItem(entry.item.getItem());
            if (entry.item.hasTag()) {
                entryBuilder.apply(SetNbtFunction.setTag(entry.item.getTag()));
            }
            entryBuilder.setWeight(entry.weight);
            pool.add(entryBuilder);
            hasValidEntry = true;
        }
        if (hasValidEntry) {
            event.setTable(LootTable.lootTable().withPool(pool).build());
        }
    }
}