package me.luisgamedev.betterhorses.listeners;
import me.luisgamedev.betterhorses.api.BetterHorseKeys;
import org.bukkit.entity.*;
import org.bukkit.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import me.luisgamedev.betterhorses.api.BetterHorsesAPI;
import me.luisgamedev.betterhorses.language.LanguageManager;
import me.luisgamedev.betterhorses.commands.DespawnCommand;
import me.luisgamedev.betterhorses.commands.RespawnCommand;
import me.luisgamedev.betterhorses.commands.HorseNeuterCommand;
import me.luisgamedev.betterhorses.api.events.BetterHorseSpawnEvent;
import me.luisgamedev.betterhorses.api.events.BetterHorseNeuterEvent;
import me.luisgamedev.betterhorses.utils.HorseItemTransaction;
import sun.misc.Unsafe;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.bukkit.inventory.*;
import java.lang.reflect.*;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import static me.luisgamedev.betterhorses.listeners.TraitLifecycleRegressionTest.*;

class HorseItemTransactionRegressionTest {
  public static class AuditLanguage extends LanguageManager {
    public AuditLanguage(){super(null,null);}
    @Override public String getRaw(OfflinePlayer p,String key){return switch(key){case "messages.gender-male"->"male";case "messages.gender-female"->"female";default->key;};}
    @Override public String getFormattedRaw(OfflinePlayer p,String key,Object... replacements){String value=key.equals("messages.horse-item-name")?"%name% %gender%":key;for(int i=0;i+1<replacements.length;i+=2)value=value.replace(String.valueOf(replacements[i]),String.valueOf(replacements[i+1]));return value;}
    @Override public void send(Player p,String key){}
    @Override public void sendFormatted(Player p,String key,Object... replacements){}
  }
  static void prepareApi(Fixture f) throws Exception {
    f.config.set("settings.horse-item-lore-layout",List.of());
    // Exercise the supported invalid-material fallback without a native registry bootstrap.
    f.config.set("settings.horse-item", "TEST_UNKNOWN_MATERIAL");
    f.plugin.language=(AuditLanguage)((Unsafe)field(Unsafe.class,"theUnsafe").get(null)).allocateInstance(AuditLanguage.class);
    ItemFactory factory=proxy(ItemFactory.class,(target,method,args)->switch(method.getName()){
      case "getItemMeta" -> {
        PersistentDataContainer pdc=data(new HashMap<>()); Map<String,Object> values=new HashMap<>();
        yield proxy(ItemMeta.class,(mt,mm,ma)->switch(mm.getName()){
          case "getPersistentDataContainer"->pdc;
          case "clone"->mt;
          case "setDisplayName"->{values.put("name",ma[0]);yield null;}
          case "getDisplayName"->values.get("name");
          default->zero(mm.getReturnType());
        });
      }
      case "equals" -> args[0]==args[1];
      case "isApplicable" -> true;
      case "updateMaterial" -> args[1];
      case "asMetaFor", "ensureServerConversions" -> args[0];
      default -> zero(method.getReturnType());
    });
    Server original=Bukkit.getServer();
    field(Bukkit.class,"server").set(null,proxy(Server.class,(target,method,args)->method.getName().equals("getItemFactory")?factory:method.getName().equals("getOfflinePlayer")?f.player.handle:method.invoke(original,args)));
  }
  static void hookEvent(java.util.function.Consumer<org.bukkit.event.Event> action) throws Exception {
    Server original=Bukkit.getServer();
    org.bukkit.plugin.PluginManager manager=proxy(org.bukkit.plugin.PluginManager.class,(target,method,args)->{
      if(method.getName().equals("callEvent")) action.accept((org.bukkit.event.Event)args[0]);
      return zero(method.getReturnType());
    });
    field(Bukkit.class,"server").set(null,proxy(Server.class,(target,method,args)->method.getName().equals("getPluginManager")?manager:method.invoke(original,args)));
  }

  private static ItemStack horseItem(Fixture f) {
    return BetterHorsesAPI.createHorseItem(20,.2,.7,"male","named",null,null,false,"none",false,10,null);
  }
  @Test void cancelledFullInventoryDropRetainsMountAndItsEquipment() throws Exception {
    try(Fixture f=new Fixture()) {
      prepareApi(f); f.horse.values.put(BetterHorseKeys.GENDER,"male");
      AtomicInteger drops=new AtomicInteger();
      World original=f.world;
      World world=proxy(World.class,(target,method,args)-> {
        if(method.getName().equals("dropItem")) {
          drops.incrementAndGet();
          return proxy(Item.class,(item,im,ia)->im.getName().equals("isValid")?false:zero(im.getReturnType()));
        }
        return method.invoke(original,args);
      });
      field(Fixture.class,"world").set(f,world);
      PlayerInventory full=proxy(PlayerInventory.class,(target,method,args)->method.getName().equals("firstEmpty")?-1:zero(method.getReturnType()));
      Player player=proxy(Player.class,(target,method,args)->method.getName().equals("getInventory")?full:f.player.invoke(target,method,args));
      field(Actor.class,"handle").set(f.player,player);
      assertTrue(DespawnCommand.despawnHorseToItem(player));
      assertTrue(f.horse.valid);
      assertEquals(1,drops.get());
    }
  }
  @Test void fullInventorySuccessfulDropStillRemovesMountExactlyOnce() throws Exception {
    try(Fixture f=new Fixture()) {
      prepareApi(f); f.horse.values.put(BetterHorseKeys.GENDER,"male");
      AtomicInteger drops=new AtomicInteger();
      World original=f.world;
      World world=proxy(World.class,(target,method,args)-> {
        if(method.getName().equals("dropItem")) {
          drops.incrementAndGet();
          return proxy(Item.class,(item,im,ia)->switch(im.getName()) {
            case "isValid" -> true;
            case "getItemStack" -> args[1];
            default -> zero(im.getReturnType());
          });
        }
        return method.invoke(original,args);
      });
      field(Fixture.class,"world").set(f,world);
      PlayerInventory full=proxy(PlayerInventory.class,(target,method,args)->method.getName().equals("firstEmpty")?-1:zero(method.getReturnType()));
      Player player=proxy(Player.class,(target,method,args)->method.getName().equals("getInventory")?full:f.player.invoke(target,method,args));
      field(Actor.class,"handle").set(f.player,player);
      assertTrue(DespawnCommand.despawnHorseToItem(player));
      assertFalse(f.horse.valid);
      assertEquals(1,drops.get());
    }
  }
  @Test void transformedDropKeepsTheMountAndRollsBackTheWrongDelivery() throws Exception {
    try(Fixture f=new Fixture()) {
      prepareApi(f); f.horse.values.put(BetterHorseKeys.GENDER,"male");
      AtomicInteger removals=new AtomicInteger();
      World original=f.world;
      World world=proxy(World.class,(target,method,args)-> {
        if(method.getName().equals("dropItem")) {
          return proxy(Item.class,(item,im,ia)->switch(im.getName()) {
            case "isValid" -> true;
            case "getItemStack" -> new ItemStack(Material.DIAMOND);
            case "remove" -> {removals.incrementAndGet();yield null;}
            default -> zero(im.getReturnType());
          });
        }
        return method.invoke(original,args);
      });
      field(Fixture.class,"world").set(f,world);
      PlayerInventory full=proxy(PlayerInventory.class,(target,method,args)->method.getName().equals("firstEmpty")?-1:zero(method.getReturnType()));
      Player player=proxy(Player.class,(target,method,args)->method.getName().equals("getInventory")?full:f.player.invoke(target,method,args));
      field(Actor.class,"handle").set(f.player,player);
      assertTrue(DespawnCommand.despawnHorseToItem(player));
      assertTrue(f.horse.valid);
      assertEquals(1,removals.get());
    }
  }
  @Test void ordinaryInventoryDeliveryRemovesTheMountAndRetainsItsHorseItem() throws Exception {
    try(Fixture f=new Fixture()) {
      prepareApi(f); f.horse.values.put(BetterHorseKeys.GENDER,"male");
      assertTrue(DespawnCommand.despawnHorseToItem(f.player.handle));
      assertFalse(f.horse.valid);
      assertNotNull(f.held);
      assertTrue(BetterHorsesAPI.isHorseItem(f.held));
      assertEquals(1,f.held.getAmount());
    }
  }
  @Test void failedSourceRemovalRollsBackTheStagedInventoryItem() throws Exception {
    try(Fixture f=new Fixture()) {
      prepareApi(f); f.horse.values.put(BetterHorseKeys.GENDER,"male");
      Horse original=f.horse.handle;
      Horse retained=proxy(Horse.class,(target,method,args)->method.getName().equals("remove")?null:method.invoke(original,args));
      field(Actor.class,"handle").set(f.horse,retained);
      f.player.vehicle=retained;
      assertTrue(DespawnCommand.despawnHorseToItem(f.player.handle));
      assertTrue(f.horse.valid);
      assertNull(f.held);
    }
  }
  @Test void spawnCallbackMovingSourceRollsBackHorseWithoutConsumingOtherItems() throws Exception {
    try(Fixture f=new Fixture()) {
      prepareApi(f); f.held=horseItem(f);
      Actor<Horse> restored=f.actor(Horse.class); f.spawnResult=restored;
      ItemStack[] moved={null};
      hookEvent(event->{if(event instanceof BetterHorseSpawnEvent) {moved[0]=f.held.clone();f.held=new ItemStack(Material.DIAMOND,64);}});
      assertTrue(RespawnCommand.spawnHorseFromItem(f.player.handle));
      assertEquals(1,moved[0].getAmount());
      assertTrue(BetterHorsesAPI.isHorseItem(moved[0]));
      assertFalse(restored.valid);
      assertEquals(Material.DIAMOND,f.held.getType());
      assertEquals(64,f.held.getAmount());
    }
  }
  @Test void neuterCallbackChangingHandCannotOverwriteUnrelatedStack() throws Exception {
    try(Fixture f=new Fixture()) {
      prepareApi(f); f.held=horseItem(f);
      f.config.set("settings.horse-item","SADDLE");
      ItemStack replacement=new ItemStack(Material.DIAMOND,64);
      hookEvent(event->{if(event instanceof BetterHorseNeuterEvent) f.held=replacement;});
      assertTrue(HorseNeuterCommand.handle(f.player.handle));
      assertSame(replacement,f.held);
      assertEquals(64,f.held.getAmount());
    }
  }
  @Test void cancelledSpawnDoesNotConsumeSourceAndNormalSpawnDoes() throws Exception {
    try(Fixture f=new Fixture()) {
      prepareApi(f); f.held=horseItem(f);
      Actor<Horse> restored=f.actor(Horse.class); f.spawnResult=restored;
      hookEvent(event->{if(event instanceof BetterHorseSpawnEvent e)e.setCancelled(true);});
      assertTrue(RespawnCommand.spawnHorseFromItem(f.player.handle));
      assertFalse(restored.valid);assertEquals(1,f.held.getAmount());
      Actor<Horse> next=f.actor(Horse.class); f.spawnResult=next;
      hookEvent(event->{});
      assertTrue(RespawnCommand.spawnHorseFromItem(f.player.handle));
      assertTrue(next.valid);assertEquals(0,f.held.getAmount());
    }
  }
  @Test void explicitItemDenialBlocksSpawnButNormalAirClickStillWorks() throws Exception {
    try(Fixture f=new Fixture()) {
      prepareApi(f);f.config.set("settings.allow-rightclick-spawn",true);f.held=horseItem(f);
      Actor<Horse> restored=f.actor(Horse.class);f.spawnResult=restored;
      var denied=new org.bukkit.event.player.PlayerInteractEvent(f.player.handle,org.bukkit.event.block.Action.RIGHT_CLICK_AIR,f.held,null,org.bukkit.block.BlockFace.SELF,EquipmentSlot.HAND);
      denied.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
      new RightClickListener().onRightClick(denied);
      assertEquals(1,f.held.getAmount());assertTrue(f.events.isEmpty());
      var air=new org.bukkit.event.player.PlayerInteractEvent(f.player.handle,org.bukkit.event.block.Action.RIGHT_CLICK_AIR,f.held,null,org.bukkit.block.BlockFace.SELF,EquipmentSlot.HAND);
      assertTrue(air.isCancelled());
      assertEquals(org.bukkit.event.Event.Result.DEFAULT,air.useItemInHand());
      new RightClickListener().onRightClick(air);
      assertEquals(0,f.held.getAmount());assertTrue(restored.valid);
    }
  }
  @Test void selectedSlotChangesInvalidateTheCommitEvenForMatchingStacks() throws Exception {
    try(Fixture f=new Fixture()) {
      prepareApi(f);f.held=horseItem(f);
      AtomicInteger selected=new AtomicInteger();
      Player original=f.player.handle;
      PlayerInventory inventory=original.getInventory();
      PlayerInventory changing=proxy(PlayerInventory.class,(target,method,args)->method.getName().equals("getHeldItemSlot")?selected.get():method.invoke(inventory,args));
      Player player=proxy(Player.class,(target,method,args)->method.getName().equals("getInventory")?changing:method.invoke(original,args));
      HorseItemTransaction transaction=HorseItemTransaction.capture(f.plugin,player);
      assertNotNull(transaction);assertTrue(transaction.matches());
      selected.set(1);
      assertFalse(transaction.replace(new ItemStack(Material.DIAMOND)));
      assertEquals(Material.SADDLE,f.held.getType());
    }
  }

  @Test void callbackOwnershipTransferCannotReadTheForeignInventoryOrConsumeSource() throws Exception {
    try(Fixture f=new Fixture()) {
      prepareApi(f); f.held=horseItem(f);
      Actor<Horse> restored=f.actor(Horse.class); f.spawnResult=restored;
      AtomicInteger foreignReads=new AtomicInteger();
      Player original=f.player.handle;
      Player player=proxy(Player.class,(target,method,args)-> {
        if(method.getName().equals("getInventory")&&f.foreign.contains(f.player.id))foreignReads.incrementAndGet();
        return method.invoke(original,args);
      });
      hookEvent(event->{if(event instanceof BetterHorseSpawnEvent) {f.foreign.add(f.player.id);f.foreign.add(restored.id);}});
      assertTrue(RespawnCommand.spawnHorseFromItem(player));
      assertEquals(1,f.held.getAmount());assertEquals(0,foreignReads.get());
      f.advance(1);
      assertFalse(restored.valid);
    }
  }

  @Test void nativeSpawnTransferStopsApiRestorationAndCleansUpOnTheNewOwner() throws Exception {
    try(Fixture f=new Fixture()) {
      prepareApi(f); f.held=horseItem(f);
      Actor<Horse> restored=f.actor(Horse.class); f.spawnResult=restored;
      World original=f.world;
      World transferring=proxy(World.class,(target,method,args)-> {
        Object result=method.invoke(original,args);
        if(method.getName().equals("spawn"))f.foreign.add(restored.id);
        return result;
      });
      field(Fixture.class,"world").set(f,transferring);
      assertNull(BetterHorsesAPI.toHorse(f.held,f.player.handle));
      assertEquals(1,f.held.getAmount());
      f.advance(1);
      assertFalse(restored.valid);
    }
  }
  public static class CaptureTransferLanguage extends AuditLanguage {
    String trigger;
    Runnable transfer;
    org.bukkit.configuration.file.FileConfiguration configuration;
    boolean fired;
    @Override public org.bukkit.configuration.file.FileConfiguration getConfig() {return configuration;}
    private void callback(String key) {
      if (!fired && key.equals(trigger)) {fired=true;transfer.run();}
    }
    @Override public String getRaw(OfflinePlayer viewer,String key) {
      callback(key);return super.getRaw(viewer,key);
    }
    @Override public String getFormattedRaw(OfflinePlayer viewer,String key,Object... values) {
      callback(key);return super.getFormattedRaw(viewer,key,values);
    }
  }
  @Test void captureFormattingTransferNeverReadsTheForeignMountOrItsLiveData() throws Exception {
    for(String trigger:List.of("messages.gender-male","messages.horse","messages.horse-item-name","messages.lore-health")) {
      try(Fixture f=new Fixture()) {
        prepareApi(f);f.horse.values.put(BetterHorseKeys.GENDER,"male");
        f.config.set("training.enabled",true);
        f.config.set("settings.horse-item-lore-layout",List.of("health","riding","feeding"));
        CaptureTransferLanguage language=(CaptureTransferLanguage)((Unsafe)field(Unsafe.class,"theUnsafe").get(null)).allocateInstance(CaptureTransferLanguage.class);
        language.configuration=new org.bukkit.configuration.file.YamlConfiguration();
        language.trigger=trigger;language.transfer=()->f.foreign.add(f.horse.id);
        f.plugin.language=language;
        PersistentDataContainer guardedData=proxy(PersistentDataContainer.class,(target,method,args)-> {
          if(f.foreign.contains(f.horse.id))throw new AssertionError("Foreign mount data read after "+trigger+": "+method.getName());
          return method.invoke(f.horse.data,args);
        });
        Horse original=f.horse.handle;
        Horse guarded=proxy(Horse.class,(target,method,args)-> {
          if(f.foreign.contains(f.horse.id)&&!method.getName().equals("getUniqueId"))throw new AssertionError("Foreign mount read after "+trigger+": "+method.getName());
          if(method.getName().equals("getPersistentDataContainer"))return guardedData;
          return method.invoke(original,args);
        });
        field(Actor.class,"handle").set(f.horse,guarded);f.player.vehicle=guarded;
        assertNull(BetterHorsesAPI.toItem(guarded,f.player.handle),trigger);
        assertTrue(language.fired,trigger);
        assertTrue(f.horse.valid,trigger);
      }
    }
  }
  @Test void normalCaptureStillUsesSavedTrainingAndOriginalStats() throws Exception {
    try(Fixture f=new Fixture()) {
      prepareApi(f);f.horse.values.put(BetterHorseKeys.GENDER,"male");
      f.horse.values.put(BetterHorseKeys.TRAINING_RIDING_UNITS,1200.0);
      f.config.set("training.enabled",true);
      f.config.set("settings.horse-item-lore-layout",List.of("health","riding","feeding"));
      CaptureTransferLanguage language=(CaptureTransferLanguage)((Unsafe)field(Unsafe.class,"theUnsafe").get(null)).allocateInstance(CaptureTransferLanguage.class);
      language.configuration=new org.bukkit.configuration.file.YamlConfiguration();
      language.trigger="unused";language.transfer=()->{throw new AssertionError();};
      f.plugin.language=language;
      ItemStack captured=BetterHorsesAPI.toItem(f.horse.handle,f.player.handle);
      assertNotNull(captured);assertTrue(BetterHorsesAPI.isHorseItem(captured));
      var data=captured.getItemMeta().getPersistentDataContainer();
      assertEquals(1200.0,data.get(BetterHorseKeys.TRAINING_RIDING_UNITS,org.bukkit.persistence.PersistentDataType.DOUBLE));
      assertEquals(.2,data.get(BetterHorseKeys.SPEED,org.bukkit.persistence.PersistentDataType.DOUBLE),1e-12);
      assertTrue(f.horse.valid);
    }
  }

}
