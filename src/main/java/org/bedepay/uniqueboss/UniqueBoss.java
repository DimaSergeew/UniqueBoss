package org.bedepay.uniqueboss;

import org.bukkit.plugin.java.JavaPlugin;
import org.bedepay.uniqueboss.commands.SummonBossCommand;
import org.bedepay.uniqueboss.commands.BossInfoCommand;
import org.bedepay.uniqueboss.commands.KillBossCommand;
import org.bedepay.uniqueboss.commands.ReloadConfigCommand;
import org.bedepay.uniqueboss.commands.EventCommand;
import org.bedepay.uniqueboss.commands.BossGiveCommand;
import org.bedepay.uniqueboss.commands.BossDebugCommand;
import org.bedepay.uniqueboss.listeners.BossListener;
import org.bedepay.uniqueboss.listeners.ArmorCraftingListener;
import org.bedepay.uniqueboss.listeners.ArmorEffectsListener;
import org.bedepay.uniqueboss.listeners.UniqueItemsListener;
import org.bedepay.uniqueboss.config.ConfigManager;
import org.bedepay.uniqueboss.events.BossEventManager;
import org.bedepay.uniqueboss.data.BossDataManager;
import org.bedepay.uniqueboss.boss.UniqueBossManager;

public final class UniqueBoss extends JavaPlugin {
    
    private static UniqueBoss instance;
    private ConfigManager config;
    private UniqueItemsListener uniqueItemsListener;
    private BossEventManager eventManager;
    private ArmorCraftingListener armorCraftingListener;
    private BossDataManager bossDataManager;

    @Override
    public void onEnable() {
        instance = this;
        
        // Инициализация конфига
        config = new ConfigManager(this);
        
        // Инициализация менеджера данных босса
        bossDataManager = new BossDataManager(this);
        
        // Регистрация команд
        registerCommands();
        
        // Регистрация слушателей событий
        registerListeners();
        
        // Запуск автоматической системы ивентов
        if (config.isEventEnabled()) {
            eventManager = new BossEventManager(this, config);
            eventManager.scheduleNextEvent();
            getLogger().info("Автоматическая система ивентов активирована!");
        }
        
        // Восстанавливаем босса если он был активен до перезапуска
        if (bossDataManager.hasSavedBossData()) {
            getLogger().info("Обнаружены данные о боссе до перезапуска. Восстанавливаем босса...");
            bossDataManager.restoreBoss(config);
        }
        
        getLogger().info("UniqueBoss плагин загружен! Используйте /summonboss для вызова босса!");
        getLogger().info("Используйте /bossinfo для получения информации о боссе!");
        getLogger().info("Используйте /killboss для мгновенного убийства босса (только админы)!");
        getLogger().info("НОВОЕ: /bossgive - выдача дропов босса (только админы)!");
        getLogger().info("НОВОЕ: Соберите осколки и создайте уникальную броню на верстаке!");
        getLogger().info("Полный комплект брони дает мощные способности!");
        getLogger().info("НОВОЕ: Уникальные предметы теперь функциональны!");
        getLogger().info("🌟 Посох телепортации - ПКМ для телепортации");
        getLogger().info("👤 Теневые сапоги - приседание для невидимости");
        getLogger().info("💎 Кристалл силы - держите в руке для эффектов");
    }

    @Override
    public void onDisable() {
        // Сохраняем состояние босса если он активен
        if (UniqueBossManager.isBossActive()) {
            getLogger().info("Сохраняем состояние активного босса...");
            bossDataManager.saveBossData();
        }
        
        // Очищаем рецепты брони
        if (armorCraftingListener != null) {
            armorCraftingListener.removeExistingRecipes();
        }
        
        // Очищаем эффекты уникальных предметов при выключении плагина
        if (uniqueItemsListener != null) {
            uniqueItemsListener.cleanup();
        }
        
        // Останавливаем систему автоматических ивентов
        if (eventManager != null) {
            eventManager.shutdown();
        }
        
        getLogger().info("UniqueBoss плагин выгружен!");
    }
    
    public static UniqueBoss getInstance() {
        return instance;
    }
    
    public ConfigManager getConfigManager() {
        return config;
    }
    
    public BossEventManager getEventManager() {
        return eventManager;
    }

    private void registerCommands() {
        this.getCommand("summonboss").setExecutor(new SummonBossCommand(config));
        this.getCommand("bossinfo").setExecutor(new BossInfoCommand());
        this.getCommand("killboss").setExecutor(new KillBossCommand(config));
        this.getCommand("uniqueboss").setExecutor(new ReloadConfigCommand());
        this.getCommand("bossevent").setExecutor(new EventCommand(config));
        
        // Регистрируем команду выдачи дропов с TabCompleter
        BossGiveCommand bossGiveCommand = new BossGiveCommand(config);
        this.getCommand("bossgive").setExecutor(bossGiveCommand);
        this.getCommand("bossgive").setTabCompleter(bossGiveCommand);
        
        // Регистрируем команду отладки
        this.getCommand("bossdebug").setExecutor(new BossDebugCommand(config));
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new BossListener(config), this);
        
        // Регистрируем слушатель крафта брони и рецепты
        armorCraftingListener = new ArmorCraftingListener(config, this);
        getServer().getPluginManager().registerEvents(armorCraftingListener, this);
        armorCraftingListener.registerRecipes();
        
        // Регистрируем слушатель эффектов доспеха и запускаем его
        ArmorEffectsListener armorListener = new ArmorEffectsListener(config);
        getServer().getPluginManager().registerEvents(armorListener, this);
        armorListener.startArmorEffects();
        
        // Регистрируем слушатель уникальных предметов
        uniqueItemsListener = new UniqueItemsListener(this);
        getServer().getPluginManager().registerEvents(uniqueItemsListener, this);
        
        // Регистрируем слушатель восстановления босса из чанков (PDC)
        getServer().getPluginManager().registerEvents(new org.bedepay.uniqueboss.listeners.BossChunkListener(this), this);
    }
}
