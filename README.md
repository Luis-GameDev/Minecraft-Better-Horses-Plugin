# 🐴 BetterHorses – Advanced Horse Breeding & Storage Plugin

**BetterHorses** is a Minecraft plugin that completely reworks the vanilla horse system by introducing **realistic genetics, mutations, breeding mechanics, and item-based horse storage**. Designed with realism and flexibility in mind, it’s perfect for survival servers, RPGs, or just better vanilla gameplay.

![1fbf8a99-e548-4b89-934b-9b5479828b7d](https://github.com/user-attachments/assets/45bc889b-419c-4bce-8957-99d857c79a00)

---

### ✨ Features

- 🧬 **Genetics & Mutation**  
  Horses pass down **Health, Speed, and Jump Strength** to their offspring.  
  A configurable **mutation factor** adds random variation to keep things interesting and realistic.

- 👫 **Gender System**  
  Every horse has a **gender** (♂ / ♀) assigned at spawn or birth.
  Two horses of the same gender cannot breed

- 🎒 **/horse despawn**  
  Converts a **tamed horse you're riding** into a **saddle item** that stores:
  - All core stats (including current HP)
  - Gender
  - Owner UUID
  - Saddle & Armor
  - Color & Style
  - Custom name (if set)
    
  A horse can only be despawned by it's owner.

- 🧲 **/horse spawn**  
  Spawns a horse **identical to the original** using the stored data in the saddle item.

- 🛠 **/horse create <health> <speed> <jump> [gender] [name]**  
  Administrator command to generate a custom horse item with your own stats and optional name, requires OP. Bypasses maximum stats.

---

### ⚙️ Configuration

Inside your `config.yml`:

```yaml
mutation-factor: 0.05  # Mutation strength (e.g. ±0.05)

max-stats:
  health: 300.0
  speed: 0.4
  jump: 1.2
```

- `mutation-factor` controls how strong the mutations are. 
- `max-stats` defines the maximum allowed values horses can reach via breeding.

---

### 🧩 Requirements

- Minecraft (Paper) `1.20.4+`
- Java `17+`

---

### 🚀 Installation

1. Download the `BetterHorses-1.0.jar` from this repo.

2. Place it into your server’s `plugins/` folder and restart the server to generate the config file.

3. Adjust the values in the `config.yml` file.

---

### 📚 Commands

| Command                                  | Description                                 | Permission                          |
|------------------------------------------|---------------------------------------------|-------------------------------------|
| `/horse spawn`                           | Spawn a horse using the item                | `betterhorses.spawn`                |
| `/horse despawn`                         | Turn the horse you're riding into an item   | `betterhorses.despawn`              |
| `/horse create`                          | Spawn a custom horse item                   | `betterhorses.create`               |
| `/horse create 100 1.0 2.0 male Zeus`    | Superhorse with custom name "Zeus"          | `betterhorses.create`               |

---

### 🧠 Plugin API

BetterHorses includes a simple developer API that allows other plugins to create horse items programmatically – no command needed.

#### 🔧 Method

```java
ItemStack horse = BetterHorsesAPI.createHorseItem(
    double health,
    double speed,
    double jump, 
    String gender, // "male" or "female"
    String name, // name of the horse
    Player owner, // owner of the horse
    Inventory targetInventory // target inventory where the horse item should be added to
    boolean dropIfFull // weather or not the horse item will be dropped to the ground if the inventory is full
);
```

---

### 📌 Roadmap Ideas

- [ ] Breeding cooldowns
- [ ] Custom breeds / bloodlines

---

