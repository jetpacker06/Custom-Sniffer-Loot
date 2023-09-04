package com.jetpacker06.customsnifferloot;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.LootTableLoadEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Scanner;

@Mod(CustomSnifferLoot.MOD_ID)
@Mod.EventBusSubscriber(modid = CustomSnifferLoot.MOD_ID)
public class CustomSnifferLoot {
    public static final String MOD_ID = "customsnifferloot";
    public static final String FILENAME = "custom_sniffer_loot.json";

    private static final Logger LOGGER = LogManager.getLogger();

    private static boolean debugMode;
    private static boolean disablePitcherPod;
    private static boolean disableTorchflower;
    private static final ArrayList<Item> items = new ArrayList<>();

    public CustomSnifferLoot() {
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::setup);
        MinecraftForge.EVENT_BUS.register(this);
    }

    private static void debugPrint(Object message) {
        if (debugMode) {
            LOGGER.info("CSL: ");
            LOGGER.info(message);
        }
    }

    private void setup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            System.out.println("hhhh");
            try {
                // Search for the file; if it doesn't exist then create it with the default values
                File configFile = new File(FMLPaths.GAMEDIR.get() + "/config/" + FILENAME);
                if (!configFile.exists()) {
                    @SuppressWarnings("unused")
                    boolean created = configFile.createNewFile();
                    FileWriter writer = new FileWriter(configFile);
                    // write a Json object containing the default values:
                    // debugMode: false,
                    // disablePitcherPod: false,
                    // disableTorchflower: false,
                    // customSnifferItems: []
                    writer.write("{\n  \"debugMode\": false,\n  \"disablePitcherPod\": false,\n  \"disableTorchflower\": false,\n  \"customSnifferItems\": []\n}");
                    writer.close();
                    System.out.println("Created " + FILENAME);
                }
                else {
                    System.out.println("Reading " + FILENAME);
                }
                // Read the file
                Scanner reader = new Scanner(configFile);
                StringBuilder rawText = new StringBuilder();
                while (reader.hasNextLine()) {
                    rawText.append(reader.nextLine());
                }
                if (rawText.toString().equals("")) {
                    rawText = new StringBuilder("{}");
                }
                // Make variables from the file's contents
                JsonObject map = new Gson().fromJson(rawText.toString(), JsonObject.class);
                debugMode = map.getAsJsonPrimitive("debugMode").getAsBoolean();
                disablePitcherPod = map.getAsJsonPrimitive("disablePitcherPod").getAsBoolean();
                disableTorchflower = map.getAsJsonPrimitive("disableTorchflower").getAsBoolean();
                debugPrint("CustomSnifferLoot debug mode is enabled, disable it in the config.");
                debugPrint("Current sniffer config:");
                debugPrint(new GsonBuilder().setPrettyPrinting().create().toJson(map));
                // Read the list of items
                debugPrint("Beginning to read items:");
                for (JsonElement element : map.getAsJsonArray("customSnifferItems")) {
                    Item itemToAdd = ForgeRegistries.ITEMS.getValue(new ResourceLocation(element.getAsString()));
                    if (itemToAdd == null) {
                        debugPrint("Item \"" + element.getAsString() + "\" does not exist, skipping it.");
                        debugPrint("Be sure to use the format: \"minecraft:stick\"");
                    }
                    else {
                        items.add(itemToAdd);
                    }
                }

            } catch (Exception e) {
                LOGGER.info("There was an error involving CustomSnifferLoot's configuration :(");
                e.printStackTrace();
            }
        });
    }

    @SubscribeEvent
    public void registerLootTable(LootTableLoadEvent event) {
        if (!event.getTable().getLootTableId().equals(BuiltInLootTables.SNIFFER_DIGGING)) {
            return;
        }
        System.out.println("Sniffer loot table loading");
        LootTable.Builder table = LootTable.lootTable();
        if (!disablePitcherPod) {
            table.withPool(LootPool.lootPool().setRolls(ConstantValue.exactly(1.0f)).add(LootItem.lootTableItem(Items.PITCHER_POD)));
        }
        if (!disableTorchflower) {
            table.withPool(LootPool.lootPool().setRolls(ConstantValue.exactly(1.0f)).add(LootItem.lootTableItem(Items.TORCHFLOWER_SEEDS)));
        }
        for (Item item : items) {
            table.withPool(LootPool.lootPool().setRolls(ConstantValue.exactly(1.0f)).add(LootItem.lootTableItem(item)));
        }
        event.setTable(table.build());
    }
}
