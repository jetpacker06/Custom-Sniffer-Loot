# Custom Sniffer Loot

> **Created by jetpacker06**

![Minecraft Forge](https://img.shields.io/badge/Minecraft-Forge-orange)
![Environment](https://img.shields.io/badge/Side-Server--Side%20Only-blue)

Custom Sniffer Loot lets you fully customize what Minecraft Sniffers dig up without writing datapacks. Easily add vanilla or modded items, assign custom drop weights, and attach full NBT data to any loot entry.

---

## ⚙️ How to Configure

After running the mod once, edit the generated configuration file found at:
`config/custom_sniffer_loot.json`

### Example Configuration

```json
{
  "debugMode": false,
  "snifferItems": [
    {
      "item": "minecraft:pitcher_pod",
      "weight": 1
    },
    {
      "item": "minecraft:torchflower_seeds",
      "weight": 1
    },
    {
      "item": "minecraft:diamond_sword{Enchantments:[{id:\"minecraft:sharpness\",lvl:5s}]}",
      "weight": 0
    }
  ]
}
```

### Options Breakdown

* **`item`**: The item resource ID (`namespace:item_id`), with optional NBT enclosed in `{}`.
* **`weight`**: The drop probability weight (higher numbers increase the likelihood; `0` disables the item).
* **`debugMode`**: Set to `true` to print configuration outputs to the game logs.

> [!TIP]
> You do not need to restart the game to apply changes! Simply run `/reload` in-game to reload the config file immediately.
