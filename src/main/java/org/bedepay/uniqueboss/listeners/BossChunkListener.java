package org.bedepay.uniqueboss.listeners;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Wither;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.Location;
import org.bukkit.World;
import org.bedepay.uniqueboss.UniqueBoss;
import org.bedepay.uniqueboss.boss.UniqueBossManager;
import org.bedepay.uniqueboss.boss.UniqueBossEntity;

/**
 * Слушатель для восстановления босса из PDC при загрузке чанков
 * ИСПРАВЛЕНО: Безопасное восстановление без мгновенной смерти
 */
public class BossChunkListener implements Listener {
    
    private final UniqueBoss plugin;
    private static final NamespacedKey BOSS_KEY = new NamespacedKey(
        Bukkit.getPluginManager().getPlugin("UniqueBoss"), "unique_boss");
    private static final NamespacedKey BOSS_SPAWN_LOCATION_KEY = new NamespacedKey(
        Bukkit.getPluginManager().getPlugin("UniqueBoss"), "boss_spawn_location");
    private static final NamespacedKey BOSS_HEALTH_KEY = new NamespacedKey(
        Bukkit.getPluginManager().getPlugin("UniqueBoss"), "boss_health");
    private static final NamespacedKey BOSS_PHASE_KEY = new NamespacedKey(
        Bukkit.getPluginManager().getPlugin("UniqueBoss"), "boss_phase");
    
    // Защита от слишком частых вызовов EntitiesLoadEvent
    private long lastEntitiesLoadEvent = 0;
    private static final long ENTITIES_LOAD_COOLDOWN = 1000; // 1 секунда
    private int ignoredEventsCount = 0; // Счетчик игнорированных событий
    private long lastSpamLogTime = 0;
    private static final long SPAM_LOG_INTERVAL = 10000; // Логируем спам раз в 10 секунд
    
    public BossChunkListener(UniqueBoss plugin) {
        this.plugin = plugin;
    }
    
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        // Защита от слишком частых вызовов
        long currentTime = System.currentTimeMillis();
        boolean isSpamming = (currentTime - lastEntitiesLoadEvent) < ENTITIES_LOAD_COOLDOWN;
        
        // Получаем конфиг для логирования
        org.bedepay.uniqueboss.config.ConfigManager config = 
            ((org.bedepay.uniqueboss.UniqueBoss) plugin).getConfigManager();
        
        // Если это спам вызовов - просто увеличиваем счетчик и игнорируем
        if (isSpamming && UniqueBossManager.isBossActive()) {
            ignoredEventsCount++;
            
            // Логируем статистику спама раз в 10 секунд
            if ((currentTime - lastSpamLogTime) > SPAM_LOG_INTERVAL) {
                if (config.isEntityEventsLoggingEnabled()) {
                    plugin.getLogger().info("📊 СПАМ СТАТИСТИКА: Проигнорировано " + ignoredEventsCount + 
                        " вызовов EntitiesLoadEvent за последние " + (SPAM_LOG_INTERVAL / 1000) + " секунд");
                }
                ignoredEventsCount = 0;
                lastSpamLogTime = currentTime;
            }
            return;
        }
        
        lastEntitiesLoadEvent = currentTime;
        
        // Обычное логирование только для НЕ-спам событий
        if (config.isEntityEventsLoggingEnabled()) {
            plugin.getLogger().info("🔄 DEBUG: EntitiesLoadEvent (НЕ спам):");
            plugin.getLogger().info("   Мир: " + event.getWorld().getName());
            plugin.getLogger().info("   Количество сущностей: " + event.getEntities().size());
            plugin.getLogger().info("   Активен ли босс: " + UniqueBossManager.isBossActive());
            
            // Проверяем есть ли Wither среди загруженных
            long witherCount = event.getEntities().stream()
                .filter(e -> e instanceof Wither)
                .count();
            plugin.getLogger().info("   Wither'ов среди сущностей: " + witherCount);
        }
        
        // КРИТИЧЕСКИ ВАЖНО: НЕ трогаем активных боссов!
        if (UniqueBossManager.isBossActive()) {
            if (config.isEntityEventsLoggingEnabled()) {
                plugin.getLogger().info("   РЕШЕНИЕ: Босс уже активен - проверяем PDC метки но НЕ восстанавливаем");
            }
            
            // Принудительно загружаем чанк босса чтобы предотвратить его исчезновение
            UniqueBossEntity currentBoss = UniqueBossManager.getCurrentBoss();
            if (currentBoss != null && currentBoss.getEntity() != null) {
                try {
                    org.bukkit.Chunk bossChunk = currentBoss.getEntity().getLocation().getChunk();
                    if (!bossChunk.isLoaded()) {
                        bossChunk.load(true);
                        plugin.getLogger().info("🔧 Принудительно загрузили чанк босса: [" + 
                            bossChunk.getX() + ", " + bossChunk.getZ() + "]");
                    }
                    // Принудительно делаем чанк всегда загруженным
                    if (!bossChunk.isForceLoaded()) {
                        bossChunk.setForceLoaded(true);
                        plugin.getLogger().info("🔧 Установили принудительную загрузку чанка босса: [" + 
                            bossChunk.getX() + ", " + bossChunk.getZ() + "]");
                    }
                } catch (Exception e) {
                    plugin.getLogger().warning("⚠️ Ошибка при загрузке чанка босса: " + e.getMessage());
                }
            }
            
            // Если босс уже активен - просто проверяем PDC метки но НЕ восстанавливаем
            for (Entity entity : event.getEntities()) {
                if (entity instanceof Wither && isUniqueBoss(entity)) {
                    Entity currentBossEntity = UniqueBossManager.getCurrentBoss().getEntity();
                    
                    if (config.isEntityEventsLoggingEnabled()) {
                        plugin.getLogger().info("   Найден Wither с PDC меткой: " + entity.getEntityId());
                        plugin.getLogger().info("   Текущий босс Entity ID: " + 
                            (currentBossEntity != null ? currentBossEntity.getEntityId() : "null"));
                    }
                    
                    if (currentBossEntity != null && currentBossEntity.equals(entity)) {
                        // Это тот же активный босс - всё нормально
                        if (config.isEntityEventsLoggingEnabled()) {
                            plugin.getLogger().info("   ✅ Это тот же активный босс - оставляем как есть");
                        }
                    } else {
                        // Это другая сущность с PDC меткой - удаляем дубликат
                        plugin.getLogger().warning("⚠️ Найден дублирующий босс при активном боссе - удаляем дубликат: " + entity.getEntityId());
                        entity.remove();
                    }
                }
            }
            return;
        }
        
        if (config.isEntityEventsLoggingEnabled()) {
            plugin.getLogger().info("   РЕШЕНИЕ: Босс НЕ активен - ищем боссов для восстановления");
        }
        
        // Босс не активен - ищем сохраненных боссов для восстановления
        for (Entity entity : event.getEntities()) {
            if (entity instanceof Wither && isUniqueBoss(entity)) {
                Wither witherEntity = (Wither) entity;
                
                plugin.getLogger().info("🔍 Обнаружен босс с PDC при загрузке чанка:");
                plugin.getLogger().info("   Entity ID: " + entity.getEntityId());
                plugin.getLogger().info("   Локация: " + entity.getLocation());
                plugin.getLogger().info("   Здоровье: " + witherEntity.getHealth());
                plugin.getLogger().info("   Активен ли уже босс: " + UniqueBossManager.isBossActive());
                
                // Восстанавливаем босса
                restoreBossFromServerRestart(witherEntity);
            }
        }
    }
    
    /**
     * Проверяет, является ли сущность нашим уникальным боссом
     */
    public static boolean isUniqueBoss(Entity entity) {
        if (!(entity instanceof Wither)) {
            return false;
        }
        
        org.bedepay.uniqueboss.UniqueBoss pluginInstance = 
            (org.bedepay.uniqueboss.UniqueBoss) Bukkit.getPluginManager().getPlugin("UniqueBoss");
        org.bedepay.uniqueboss.config.ConfigManager config = pluginInstance.getConfigManager();
        
        try {
            PersistentDataContainer pdc = entity.getPersistentDataContainer();
            Byte bossMarker = pdc.get(BOSS_KEY, PersistentDataType.BYTE);
            
            boolean isOurBoss = bossMarker != null && bossMarker == 1;
            
            if (config.isPdcOperationsLoggingEnabled()) {
                pluginInstance.getLogger().info("🔍 DEBUG: Проверка PDC метки:");
                pluginInstance.getLogger().info("   Entity ID: " + entity.getEntityId());
                pluginInstance.getLogger().info("   PDC метка: " + bossMarker);
                pluginInstance.getLogger().info("   Является нашим боссом: " + isOurBoss);
            }
            
            return isOurBoss;
            
        } catch (Exception e) {
            if (config.isPdcOperationsLoggingEnabled()) {
                pluginInstance.getLogger().warning("⚠️ Ошибка при проверке PDC: " + e.getMessage());
            }
            return false;
        }
    }
    
    /**
     * Помечает сущность как уникального босса в PDC
     */
    public static void markAsUniqueBoss(Wither bossEntity, Location spawnLocation) {
        org.bedepay.uniqueboss.UniqueBoss pluginInstance = 
            (org.bedepay.uniqueboss.UniqueBoss) Bukkit.getPluginManager().getPlugin("UniqueBoss");
        org.bedepay.uniqueboss.config.ConfigManager config = pluginInstance.getConfigManager();
        
        if (config.isPdcOperationsLoggingEnabled()) {
            pluginInstance.getLogger().info("🏷️ DEBUG: Сохраняем PDC метки для босса:");
            pluginInstance.getLogger().info("   Entity ID: " + bossEntity.getEntityId());
            pluginInstance.getLogger().info("   Спавн локация: " + spawnLocation);
            pluginInstance.getLogger().info("   Чанк: [" + spawnLocation.getChunk().getX() + ", " + spawnLocation.getChunk().getZ() + "]");
        }
        
        try {
            PersistentDataContainer pdc = bossEntity.getPersistentDataContainer();
            
            // Помечаем как нашего босса
            pdc.set(BOSS_KEY, PersistentDataType.BYTE, (byte) 1);
            
            // Сохраняем координаты спавна
            String locationString = spawnLocation.getWorld().getName() + ";" + 
                spawnLocation.getX() + ";" + 
                spawnLocation.getY() + ";" + 
                spawnLocation.getZ();
            pdc.set(BOSS_SPAWN_LOCATION_KEY, PersistentDataType.STRING, locationString);
            
            // Сохраняем начальное здоровье и фазу
            pdc.set(BOSS_HEALTH_KEY, PersistentDataType.DOUBLE, bossEntity.getHealth());
            pdc.set(BOSS_PHASE_KEY, PersistentDataType.INTEGER, 1);
            
            if (config.isPdcOperationsLoggingEnabled()) {
                pluginInstance.getLogger().info("   ✅ PDC метки сохранены:");
                pluginInstance.getLogger().info("     Босс метка: " + pdc.get(BOSS_KEY, PersistentDataType.BYTE));
                pluginInstance.getLogger().info("     Локация: " + pdc.get(BOSS_SPAWN_LOCATION_KEY, PersistentDataType.STRING));
                pluginInstance.getLogger().info("     Здоровье: " + pdc.get(BOSS_HEALTH_KEY, PersistentDataType.DOUBLE));
                pluginInstance.getLogger().info("     Фаза: " + pdc.get(BOSS_PHASE_KEY, PersistentDataType.INTEGER));
            }
            
        } catch (Exception e) {
            pluginInstance.getLogger().severe("💥 ОШИБКА: Не удалось сохранить PDC метки!");
            e.printStackTrace();
        }
        
        pluginInstance.getLogger().info("🏷️ PDC данные сохранены для босса: " + bossEntity.getEntityId() + 
            " в чанке [" + spawnLocation.getChunk().getX() + ", " + spawnLocation.getChunk().getZ() + "]");
    }
    
    /**
     * Обновляет PDC данные босса (здоровье, фаза)
     */
    public static void updateBossPDC(Wither bossEntity, double health, int phase) {
        org.bedepay.uniqueboss.UniqueBoss pluginInstance = 
            (org.bedepay.uniqueboss.UniqueBoss) Bukkit.getPluginManager().getPlugin("UniqueBoss");
        org.bedepay.uniqueboss.config.ConfigManager config = pluginInstance.getConfigManager();
        
        if (config.isPdcOperationsLoggingEnabled()) {
            pluginInstance.getLogger().info("🔄 DEBUG: Обновляем PDC данные босса:");
            pluginInstance.getLogger().info("   Entity ID: " + bossEntity.getEntityId());
            pluginInstance.getLogger().info("   Новое здоровье: " + health);
            pluginInstance.getLogger().info("   Новая фаза: " + phase);
        }
        
        try {
            PersistentDataContainer pdc = bossEntity.getPersistentDataContainer();
            pdc.set(BOSS_HEALTH_KEY, PersistentDataType.DOUBLE, health);
            pdc.set(BOSS_PHASE_KEY, PersistentDataType.INTEGER, phase);
            
            if (config.isPdcOperationsLoggingEnabled()) {
                pluginInstance.getLogger().info("   ✅ PDC данные обновлены");
            }
            
        } catch (Exception e) {
            pluginInstance.getLogger().warning("⚠️ Не удалось обновить PDC данные: " + e.getMessage());
            if (config.isPdcOperationsLoggingEnabled()) {
                e.printStackTrace();
            }
        }
    }
    
    /**
     * Восстанавливает босса после перезапуска сервера (НЕ при телепортации игрока!)
     */
    private void restoreBossFromServerRestart(Wither witherEntity) {
        plugin.getLogger().info("🔄 Восстанавливаем босса после перезапуска сервера...");
        
        // Получаем сохраненные данные из PDC
        Location spawnLocation = getSpawnLocationFromPDC(witherEntity);
        if (spawnLocation == null) {
            plugin.getLogger().warning("⚠️ Не удалось восстановить координаты спавна из PDC - удаляем сущность");
            witherEntity.remove();
            return;
        }
        
        double savedHealth = getSavedHealthFromPDC(witherEntity);
        int savedPhase = getSavedPhaseFromPDC(witherEntity);
        
        plugin.getLogger().info("📋 Данные для восстановления:");
        plugin.getLogger().info("   Координаты: " + spawnLocation);
        plugin.getLogger().info("   Здоровье: " + savedHealth);
        plugin.getLogger().info("   Фаза: " + savedPhase);
        
        // ПРОСТОЕ РЕШЕНИЕ: Удаляем старую сущность и создаем новую
        try {
            witherEntity.remove();
            
            // Небольшая задержка для удаления
            new BukkitRunnable() {
                @Override
                public void run() {
                    // Спавним нового босса
                    UniqueBossManager.spawnBoss(spawnLocation, plugin.getConfigManager());
                    
                    // Восстанавливаем здоровье если нужно
                    if (UniqueBossManager.isBossActive()) {
                        UniqueBossEntity boss = UniqueBossManager.getCurrentBoss();
                        if (boss != null && boss.getEntity() instanceof Wither) {
                            Wither newBossEntity = (Wither) boss.getEntity();
                            if (savedHealth > 0 && savedHealth < newBossEntity.getHealth()) {
                                newBossEntity.setHealth(savedHealth);
                            }
                        }
                    }
                    
                    plugin.getLogger().info("✅ Босс восстановлен после перезапуска сервера!");
                }
            }.runTaskLater(plugin, 3L);
            
        } catch (Exception e) {
            plugin.getLogger().severe("💥 Ошибка при восстановлении босса:");
            e.printStackTrace();
        }
    }
    
    /**
     * Получает координаты места спавна из PDC
     */
    private Location getSpawnLocationFromPDC(Wither witherEntity) {
        org.bedepay.uniqueboss.config.ConfigManager config = 
            ((org.bedepay.uniqueboss.UniqueBoss) plugin).getConfigManager();
        
        try {
            PersistentDataContainer pdc = witherEntity.getPersistentDataContainer();
            String locationString = pdc.get(BOSS_SPAWN_LOCATION_KEY, PersistentDataType.STRING);
            
            if (config.isPdcOperationsLoggingEnabled()) {
                plugin.getLogger().info("🔍 DEBUG: Восстанавливаем локацию из PDC:");
                plugin.getLogger().info("   Raw данные: " + locationString);
            }
            
            if (locationString == null) {
                if (config.isPdcOperationsLoggingEnabled()) {
                    plugin.getLogger().info("   ❌ Локация не найдена в PDC");
                }
                return null;
            }
            
            String[] parts = locationString.split(";");
            if (parts.length != 4) {
                if (config.isPdcOperationsLoggingEnabled()) {
                    plugin.getLogger().info("   ❌ Некорректный формат локации: " + parts.length + " частей");
                }
                return null;
            }
            
            World world = Bukkit.getWorld(parts[0]);
            if (world == null) {
                if (config.isPdcOperationsLoggingEnabled()) {
                    plugin.getLogger().info("   ❌ Мир не найден: " + parts[0]);
                }
                return null;
            }
            
            double x = Double.parseDouble(parts[1]);
            double y = Double.parseDouble(parts[2]);
            double z = Double.parseDouble(parts[3]);
            
            Location location = new Location(world, x, y, z);
            
            if (config.isPdcOperationsLoggingEnabled()) {
                plugin.getLogger().info("   ✅ Локация восстановлена: " + location);
            }
            
            return location;
            
        } catch (Exception e) {
            if (config.isPdcOperationsLoggingEnabled()) {
                plugin.getLogger().warning("   ❌ Ошибка при восстановлении локации: " + e.getMessage());
                e.printStackTrace();
            }
            return null;
        }
    }
    
    /**
     * Получает сохраненное здоровье из PDC
     */
    private double getSavedHealthFromPDC(Wither witherEntity) {
        PersistentDataContainer pdc = witherEntity.getPersistentDataContainer();
        
        if (pdc.has(BOSS_HEALTH_KEY, PersistentDataType.DOUBLE)) {
            return pdc.get(BOSS_HEALTH_KEY, PersistentDataType.DOUBLE);
        }
        
        // Если нет сохраненного здоровья - возвращаем текущее
        return witherEntity.getHealth();
    }
    
    /**
     * Получает сохраненную фазу из PDC
     */
    private int getSavedPhaseFromPDC(Wither witherEntity) {
        PersistentDataContainer pdc = witherEntity.getPersistentDataContainer();
        
        if (pdc.has(BOSS_PHASE_KEY, PersistentDataType.INTEGER)) {
            return pdc.get(BOSS_PHASE_KEY, PersistentDataType.INTEGER);
        }
        
        // Если нет сохраненной фазы - возвращаем первую
        return 1;
    }
} 