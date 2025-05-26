package org.bedepay.uniqueboss.data;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bedepay.uniqueboss.boss.UniqueBossManager;
import org.bedepay.uniqueboss.config.ConfigManager;

import java.io.File;
import java.io.IOException;

public class BossDataManager {
    
    private final Plugin plugin;
    private final File dataFile;
    private FileConfiguration dataConfig;
    
    public BossDataManager(Plugin plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "boss_data.yml");
        loadDataFile();
    }
    
    private void loadDataFile() {
        if (!dataFile.exists()) {
            try {
                dataFile.getParentFile().mkdirs();
                dataFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Не удалось создать файл данных босса: " + e.getMessage());
            }
        }
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
    }
    
    private void saveDataFile() {
        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Не удалось сохранить файл данных босса: " + e.getMessage());
        }
    }
    
    /**
     * Проверяет, есть ли сохраненные данные о боссе
     */
    public boolean hasSavedBossData() {
        return dataConfig.getBoolean("boss.active", false);
    }
    
    /**
     * Сохраняет данные активного босса
     */
    public void saveBossData() {
        if (!UniqueBossManager.isBossActive()) {
            return;
        }
        
        try {
            // Получаем данные босса
            Location bossLoc = UniqueBossManager.getCurrentBoss().getEntity().getLocation();
            int phase = UniqueBossManager.getCurrentBoss().getCurrentPhase();
            double health = ((org.bukkit.entity.LivingEntity) UniqueBossManager.getCurrentBoss().getEntity()).getHealth();
            
            // Сохраняем в конфиг
            dataConfig.set("boss.active", true);
            dataConfig.set("boss.world", bossLoc.getWorld().getName());
            dataConfig.set("boss.x", bossLoc.getX());
            dataConfig.set("boss.y", bossLoc.getY());
            dataConfig.set("boss.z", bossLoc.getZ());
            dataConfig.set("boss.yaw", bossLoc.getYaw());
            dataConfig.set("boss.pitch", bossLoc.getPitch());
            dataConfig.set("boss.phase", phase);
            dataConfig.set("boss.health", health);
            dataConfig.set("boss.save_time", System.currentTimeMillis());
            
            saveDataFile();
            plugin.getLogger().info("Данные босса сохранены успешно!");
            
        } catch (Exception e) {
            plugin.getLogger().severe("Ошибка при сохранении данных босса: " + e.getMessage());
        }
    }
    
    /**
     * Восстанавливает босса из сохраненных данных
     */
    public void restoreBoss(ConfigManager config) {
        if (!hasSavedBossData()) {
            return;
        }
        
        try {
            // Получаем сохраненные данные
            String worldName = dataConfig.getString("boss.world");
            double x = dataConfig.getDouble("boss.x");
            double y = dataConfig.getDouble("boss.y");
            double z = dataConfig.getDouble("boss.z");
            float yaw = (float) dataConfig.getDouble("boss.yaw");
            float pitch = (float) dataConfig.getDouble("boss.pitch");
            int savedPhase = dataConfig.getInt("boss.phase", 1);
            double savedHealth = dataConfig.getDouble("boss.health");
            long saveTime = dataConfig.getLong("boss.save_time");
            
            // Проверяем, не слишком ли давно был сохранен босс (не более 1 часа)
            long timeDiff = System.currentTimeMillis() - saveTime;
            if (timeDiff > 3600000) { // 1 час в миллисекундах
                plugin.getLogger().info("Данные босса устарели (сохранены более часа назад). Очищаем данные.");
                clearBossData();
                return;
            }
            
            // Получаем мир
            World world = Bukkit.getWorld(worldName);
            if (world == null) {
                plugin.getLogger().warning("Мир " + worldName + " не найден. Не удалось восстановить босса.");
                clearBossData();
                return;
            }
            
            // Создаем локацию
            Location spawnLoc = new Location(world, x, y, z, yaw, pitch);
            
            // Ждем немного после запуска сервера, затем восстанавливаем босса
            new BukkitRunnable() {
                @Override
                public void run() {
                    try {
                        // Спавним босса
                        UniqueBossManager.spawnBoss(spawnLoc, config);
                        
                        // Ждем еще немного, чтобы босс полностью инициализировался
                        new BukkitRunnable() {
                            @Override
                            public void run() {
                                if (UniqueBossManager.isBossActive()) {
                                    // Восстанавливаем здоровье
                                    org.bukkit.entity.LivingEntity bossEntity = 
                                        (org.bukkit.entity.LivingEntity) UniqueBossManager.getCurrentBoss().getEntity();
                                    bossEntity.setHealth(Math.min(savedHealth, bossEntity.getMaxHealth()));
                                    
                                    // Принудительно устанавливаем фазу если нужно
                                    if (savedPhase > 1) {
                                        // Здесь можно добавить логику для принудительной смены фазы
                                        // Пока что босс сам определит фазу по здоровью
                                    }
                                    
                                    plugin.getLogger().info("Босс успешно восстановлен! " +
                                        "Фаза: " + savedPhase + ", Здоровье: " + savedHealth);
                                    
                                    // Объявляем о восстановлении босса
                                    for (org.bukkit.entity.Player player : Bukkit.getOnlinePlayers()) {
                                        player.sendMessage(org.bukkit.ChatColor.GOLD + 
                                            "🔄 Темный Повелитель восстановлен после перезапуска сервера!");
                                        player.sendMessage(org.bukkit.ChatColor.RED + 
                                            "Местоположение: " + org.bukkit.ChatColor.YELLOW + worldName + 
                                            org.bukkit.ChatColor.GRAY + " [" + org.bukkit.ChatColor.WHITE + 
                                            (int)x + ", " + (int)y + ", " + (int)z + org.bukkit.ChatColor.GRAY + "]");
                                    }
                                    
                                    // Очищаем сохраненные данные
                                    clearBossData();
                                } else {
                                    plugin.getLogger().warning("Не удалось восстановить босса - спавн не удался");
                                    clearBossData();
                                }
                            }
                        }.runTaskLater(plugin, 20L); // Ждем 1 секунду
                        
                    } catch (Exception e) {
                        plugin.getLogger().severe("Ошибка при восстановлении босса: " + e.getMessage());
                        clearBossData();
                    }
                }
            }.runTaskLater(plugin, 60L); // Ждем 3 секунды после старта сервера
            
        } catch (Exception e) {
            plugin.getLogger().severe("Критическая ошибка при восстановлении босса: " + e.getMessage());
            clearBossData();
        }
    }
    
    /**
     * Очищает сохраненные данные босса
     */
    public void clearBossData() {
        dataConfig.set("boss", null);
        saveDataFile();
        plugin.getLogger().info("Данные босса очищены.");
    }
} 