# 🐴 BetterHorses – Advanced Horse Breeding & Storage Plugin

**BetterHorses** is a Minecraft plugin that completely reworks the vanilla horse system by introducing **realistic genetics, mutations, breeding mechanics, and item-based horse storage**. Designed with realism and flexibility in mind, it’s perfect for survival servers, RPGs, or just better vanilla gameplay.

![1fbf8a99-e548-4b89-934b-9b5479828b7d](https://github.com/user-attachments/assets/45bc889b-419c-4bce-8957-99d857c79a00)

If you have any questions or need help feel free to join my discord server: https://discord.gg/gBEKWGanM7

---

### ✨ Features

- 🧬 **Genetics & Mutation**  
  Horses pass down **Health, Speed, and Jump Strength** to their offspring.  
  A configurable **mutation factor** adds random variation to keep things interesting and realistic.

- 👫 **Gender System**  
  Every horse has a **gender** (♂ / ♀) assigned at spawn or birth.
  Two horses of the same gender cannot breed

- 🔥 **Horse Traits (Abilities)**  
  Horses can be born or created with **special traits** that provide passive or active effects such as:
  - `Hellmare` – Leaves a trail of fire when the ability is used
  - `Fireheart` – The horse and it's rider is immune to fire
  - `Feather Hooves` – The horse and it's rider can glide
  - `Frost Hooves` – Freezes water below the horse
  - `Dashboost` - Increases the horses movement speed significantly for a short amount of time when the ability is used
  - `Kickback` - The horse knocks away all enemies when the ability is used
  - `Ghosthorse` - You and the horse enter the realm of the undead and become invisible for a short amount of time when the ability is used

  Active traits can be used by pressing F (or the swap hands keybind).
  
  All traits are **fully configurable and toggleable** in the `config.yml`:
  - Enable or disable the whole trait-feature
  - Set trait chance
  - Enable/disable individual traits
  - Define duration, cooldown, radius, intensity and more


- 🎒 **/horse despawn**  
  Converts a **tamed horse you're riding** into a **saddle item** that stores:
  - All core stats (including current HP)
  - Gender
  - Owner UUID
  - Saddle & Armor
  - Color & Style
  - Trait
  - Custom name (if set)
    
  A horse can only be despawned by it's owner.

- 🧲 **/horse spawn**  
  Spawns a horse **identical to the original** using the stored data in the saddle item.

- 🛠 **/horsecreate [health] [speed] [jump] [gender] [name] [trait]**  
  Administrator command to generate a custom horse item with your own stats, name, and optional **specific trait**.  
  If no trait is provided, one is selected randomly based on configured chances, traits can only be selected if enabled in the config.  

---

### ⚙️ Configuration

Inside your `config.yml`:

```yaml
# BetterHorses config

# How strong the mutation effect is (e.g. 0.05 = ±0.05)
mutation-factor:
  health: 3.0
  speed: 0.05
  jump: 0.05

# Max stats a horse can reach
max-stats:
  health: 300.0
  speed: 0.4
  jump: 1.2

# Horse Abilities
traits:
  enabled: true

  hellmare:
    enabled: true
    chance: 0.005 # 0.5%
    duration: 5
    cooldown: 90
    radius: 3 # Radius of the fire below the horse

  fireheart:
    enabled: true
    chance: 0.01 # 1%

  dashboost:
    enabled: true
    chance: 0.01 # 1%
    duration: 10
    cooldown: 30

  featherhooves:
    enabled: true
    chance: 0.01 # 1%

  frosthooves:
    enabled: true
    chance: 0.005 # 0.5%
    radius: 3 # Radius of the ice below the horse

  kickback:
    enabled: true
    chance: 0.005 # 0.5%
    radius: 4
    strength: 6
    cooldown: 20

  ghosthorse:
    enabled: true
    chance: 0.005 # 0.5%
    duration: 5
    cooldown: 30
```

---

### 🧩 Requirements

- Minecraft (Paper) `1.20.4+`
- Java `17+`

---

### 🚀 Installation

1. Download the `BetterHorses-2.0.jar` from this repo.

2. Place it into your server’s `plugins/` folder and restart the server to generate the config file.

3. Adjust the values in the `config.yml` file.

---

### 📚 Commands

| Command                                  | Description                                 | Permission                          |
|------------------------------------------|---------------------------------------------|-------------------------------------|
| `/horse spawn`                           | Spawn a horse using the item                | `betterhorses.base`                |
| `/horse despawn`                         | Turn the horse you're riding into an item   | `betterhorses.base`              |
| `/horsecreate`                           | Spawn a custom horse item                   | `betterhorses.create`               |
| `/horsecreate 100 1.0 2.0 male Zeus`     | Create superhorse with custom name "Zeus"   | `betterhorses.create`               |
| `/horsecreate 80 0.3 1.1 female Flare hellmare` | Create horse with fixed trait `hellmare` | `betterhorses.create`               |

---

### 🧠 Plugin API

BetterHorses includes a simple developer API that allows other plugins to create horse items programmatically – no command needed.

#### 🔧 Method

```java
ItemStack horse = BetterHorsesAPI.createHorseItem(
    double health,
    double speed,
    double jump, 
    String gender,      // "male" or "female"
    String name,        // name of the horse
    Player owner,       // owner of the horse
    Inventory inventory,// inventory to place the item into
    boolean dropIfFull, // drop if inventory is full
    String trait        // optional trait name, or null
);
```

---

### 📌 Roadmap Ideas

- [ ] Breeding cooldowns
- [ ] Genetic features

---

