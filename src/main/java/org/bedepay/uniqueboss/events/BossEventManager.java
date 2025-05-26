package org.bedepay.uniqueboss.events;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.entity.Entity;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bedepay.uniqueboss.UniqueBoss;
import org.bedepay.uniqueboss.boss.UniqueBossManager;
import org.bedepay.uniqueboss.config.ConfigManager;

import java.time.LocalTime;
import java.util.*;

public class BossEventManager {
    
    private final UniqueBoss plugin;
    private final ConfigManager config;
    private final Random random = new Random();
    
    private BukkitTask eventSchedulerTask;
    private BukkitTask reminderTask;
    private BukkitTask inactivityTask;
    
    private long nextSpawnTime;
    private Location bossSpawnLocation;
    private long bossSpawnTime;
    private int reminderCount = 0;
    private boolean removalWarningGiven = false;
    private boolean isForcedSpawn = false; // Флаг принудительного спавна
    
    public BossEventManager(UniqueBoss plugin, ConfigManager config) {
        this.plugin = plugin;
        this.config = config;
        
        if (config.isEventEnabled()) {
            scheduleNextEvent();
            plugin.getLogger().info("🎯 Автоматические ивенты босса включены!");
            plugin.getLogger().info("⏰ Следующий ивент запланирован через " + 
                getTimeUntilNextSpawn() + " минут");
        } else {
            plugin.getLogger().info("⏸️ Автоматические ивенты босса отключены в конфиге");
        }
    }
    
    public void scheduleNextEvent() {
        // Отменяем текущую задачу если есть
        if (eventSchedulerTask != null) {
            eventSchedulerTask.cancel();
        }
        
        // Рассчитываем следующее время спавна
        int minInterval = config.getMinSpawnInterval();
        int maxInterval = config.getMaxSpawnInterval();
        int interval = minInterval + random.nextInt(maxInterval - minInterval + 1);
        
        nextSpawnTime = System.currentTimeMillis() + (interval * 60 * 1000L);
        
        // Запускаем проверку каждую минуту
        eventSchedulerTask = new BukkitRunnable() {
            @Override
            public void run() {
                checkAndSpawnBoss();
            }
        }.runTaskTimer(plugin, 20L * 60L, 20L * 60L); // Каждую минуту
    }
    
    private void checkAndSpawnBoss() {
        // Проверяем время
        if (System.currentTimeMillis() < nextSpawnTime) {
            return;
        }
        
        // Проверяем, не активен ли уже босс
        if (UniqueBossManager.isBossActive()) {
            // Перенесем спавн на позже
            scheduleNextEvent();
            return;
        }
        
        // Проверяем время суток
        if (!isCorrectTimeOfDay()) {
            plugin.getLogger().info("⏰ Отложен спавн босса - неподходящее время суток");
            scheduleNextEvent();
            return;
        }
        
        // Проверяем количество игроков
        int onlinePlayers = Bukkit.getOnlinePlayers().size();
        if (onlinePlayers < config.getMinPlayersOnline()) {
            plugin.getLogger().info("👥 Отложен спавн босса - недостаточно игроков онлайн (" + 
                onlinePlayers + "/" + config.getMinPlayersOnline() + ")");
            scheduleNextEvent();
            return;
        }
        
        // Ищем подходящую локацию
        Location spawnLoc = findSuitableSpawnLocation();
        if (spawnLoc == null) {
            plugin.getLogger().warning("❌ Не удалось найти подходящую локацию для спавна босса");
            scheduleNextEvent();
            return;
        }
        
        // Сбрасываем флаг принудительного спавна для автоматического
        isForcedSpawn = false;
        
        // Спавним босса
        spawnEventBoss(spawnLoc);
    }
    
    private boolean isCorrectTimeOfDay() {
        LocalTime now = LocalTime.now();
        int currentHour = now.getHour();
        
        int startHour = config.getAllowedHourStart();
        int endHour = config.getAllowedHourEnd();
        
        return currentHour >= startHour && currentHour <= endHour;
    }
    
    private Location findSuitableSpawnLocation() {
        List<String> allowedWorlds = config.getAllowedWorlds();
        if (allowedWorlds.isEmpty()) {
            plugin.getLogger().warning("❌ Список разрешенных миров пуст!");
            return null;
        }
        
        plugin.getLogger().info("🔍 Ищем подходящее место для спавна босса...");
        
        // Перебираем разрешенные миры
        for (String worldName : allowedWorlds) {
            World world = Bukkit.getWorld(worldName);
            if (world == null) {
                plugin.getLogger().warning("❌ Мир " + worldName + " не найден!");
                continue;
            }
            
            plugin.getLogger().info("🌍 Ищем место в мире: " + worldName);
            
            // Делаем больше попыток найти место
            for (int attempts = 0; attempts < 100; attempts++) {
                Location loc = findRandomLocationInWorld(world);
                if (loc != null) {
                    // Принудительно загружаем чанк
                    Chunk chunk = loc.getChunk();
                    if (!chunk.isLoaded()) {
                        chunk.load(true);
                        plugin.getLogger().info("📦 Загружен чанк: " + chunk.getX() + ", " + chunk.getZ());
                    }
                    
                    // Проверяем локацию после загрузки чанка
                    if (isLocationSuitable(loc)) {
                        plugin.getLogger().info("✅ Найдено подходящее место: " + 
                            loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ());
                        return loc;
                    }
                }
                
                // Каждые 20 попыток выводим прогресс
                if (attempts % 20 == 0 && attempts > 0) {
                    plugin.getLogger().info("🔄 Попытка " + attempts + "/100 в мире " + worldName);
                }
            }
            
            plugin.getLogger().warning("❌ Не удалось найти место в мире " + worldName + " за 100 попыток");
        }
        
        plugin.getLogger().warning("❌ Не удалось найти подходящую локацию обычным способом!");
        plugin.getLogger().info("🔄 Пробуем альтернативную стратегию - поиск рядом с игроками...");
        
        // Альтернативная стратегия - ищем место рядом с игроками
        for (Player player : Bukkit.getOnlinePlayers()) {
            World world = player.getWorld();
            String worldName = world.getName();
            
            // Проверяем, разрешен ли этот мир
            if (!config.getAllowedWorlds().contains(worldName)) continue;
            
            plugin.getLogger().info("🎯 Ищем место рядом с игроком " + player.getName() + " в мире " + worldName);
            
            Location playerLoc = player.getLocation();
            int minDistance = config.getMinDistanceFromPlayers();
            int maxDistance = config.getMaxDistanceFromPlayers();
            
            for (int attempts = 0; attempts < 50; attempts++) {
                // Ищем место в радиусе от минимального до максимального расстояния
                double angle = random.nextDouble() * 2 * Math.PI;
                double distance = minDistance + random.nextDouble() * (maxDistance - minDistance);
                
                double x = playerLoc.getX() + Math.cos(angle) * distance;
                double z = playerLoc.getZ() + Math.sin(angle) * distance;
                
                // Ищем подходящую высоту
                for (int y = config.getMaxSpawnY(); y >= config.getMinSpawnY(); y--) {
                    Location testLoc = new Location(world, x, y, z);
                    
                    // Загружаем чанк
                    Chunk chunk = testLoc.getChunk();
                    if (!chunk.isLoaded()) {
                        chunk.load(true);
                    }
                    
                    if (isSafeSpawnLocation(testLoc) && isLocationSuitable(testLoc)) {
                        plugin.getLogger().info("✅ Найдено место альтернативным способом: " + 
                            testLoc.getBlockX() + ", " + testLoc.getBlockY() + ", " + testLoc.getBlockZ());
                        return testLoc.add(0.5, 0, 0.5);
                    }
                }
            }
        }
        
        plugin.getLogger().severe("❌ Не удалось найти подходящую локацию даже альтернативным способом!");
        return null;
    }
    
    private Location findRandomLocationInWorld(World world) {
        int searchRadius = config.getSearchRadius();
        int minY = config.getMinSpawnY();
        int maxY = config.getMaxSpawnY();
        
        // Уменьшаем радиус поиска для начала, если слишком большой
        if (searchRadius > 1000) {
            searchRadius = 1000; // Ограничиваем до 1000 блоков
        }
        
        // Случайные координаты в меньшем радиусе вокруг спавна мира
        Location worldSpawn = world.getSpawnLocation();
        int x = worldSpawn.getBlockX() + random.nextInt(searchRadius * 2) - searchRadius;
        int z = worldSpawn.getBlockZ() + random.nextInt(searchRadius * 2) - searchRadius;
        
        // Ищем подходящую Y координату
        for (int y = maxY; y >= minY; y--) { // Начинаем сверху
            Location loc = new Location(world, x + 0.5, y, z + 0.5);
            
            // Принудительно загружаем чанк перед проверкой
            Chunk chunk = loc.getChunk();
            if (!chunk.isLoaded()) {
                chunk.load(true);
            }
            
            if (isSafeSpawnLocation(loc)) {
                return loc;
            }
        }
        
        return null;
    }
    
    private boolean isLocationSuitable(Location loc) {
        // Проверяем расстояние от игроков
        int minDistance = config.getMinDistanceFromPlayers();
        int maxDistance = config.getMaxDistanceFromPlayers();
        
        boolean tooCloseToPlayer = false;
        boolean hasPlayersInWorld = false;
        
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!player.getWorld().equals(loc.getWorld())) continue;
            
            hasPlayersInWorld = true;
            double distance = player.getLocation().distance(loc);
            
            if (distance < minDistance) {
                tooCloseToPlayer = true;
                break; // Слишком близко к игроку
            }
        }
        
        // Если нет игроков в мире, разрешаем спавн
        if (!hasPlayersInWorld) {
            plugin.getLogger().info("🌍 Разрешен спавн в мире без игроков");
            return true;
        }
        
        // Если слишком близко к игроку, запрещаем
        if (tooCloseToPlayer) {
            return false;
        }
        
        // Иначе разрешаем (убираем слишком строгие условия)
        return true;
    }
    
    private boolean isSafeSpawnLocation(Location loc) {
        try {
            Location groundLoc = loc.clone().subtract(0, 1, 0);
            Location feetLoc = loc.clone();
            Location headLoc = loc.clone().add(0, 1, 0);
            Location aboveLoc = loc.clone().add(0, 2, 0);
            
            Material ground = groundLoc.getBlock().getType();
            Material feet = feetLoc.getBlock().getType();
            Material head = headLoc.getBlock().getType();
            Material above = aboveLoc.getBlock().getType();
            
            // Проверяем что есть твердая поверхность
            boolean hasGround = ground.isSolid() && 
                               ground != Material.LAVA && 
                               ground != Material.MAGMA_BLOCK &&
                               ground != Material.CACTUS;
            
            // Проверяем что есть место для босса
            boolean hasSpace = (feet == Material.AIR || feet == Material.CAVE_AIR) && 
                              (head == Material.AIR || head == Material.CAVE_AIR) && 
                              (above == Material.AIR || above == Material.CAVE_AIR);
            
            // Проверяем что не в лаве/воде
            boolean notInDanger = feet != Material.LAVA && 
                                 feet != Material.WATER &&
                                 head != Material.LAVA && 
                                 head != Material.WATER;
            
            boolean isSafe = hasGround && hasSpace && notInDanger;
            
            // Дополнительно проверяем высоту (не слишком высоко в воздухе)
            if (isSafe) {
                // Ищем землю в радиусе 10 блоков вниз
                for (int y = 1; y <= 10; y++) {
                    Location checkLoc = groundLoc.clone().subtract(0, y, 0);
                    if (checkLoc.getBlock().getType().isSolid()) {
                        return true; // Нашли землю недалеко
                    }
                }
                
                // Если земля слишком далеко, все равно разрешаем (босс может летать)
                return true;
            }
            
            return false;
            
        } catch (Exception e) {
            plugin.getLogger().warning("❌ Ошибка при проверке безопасности локации: " + e.getMessage());
            return false;
        }
    }
    
    private void spawnEventBoss(Location location) {
        bossSpawnLocation = location.clone();
        bossSpawnTime = System.currentTimeMillis();
        reminderCount = 0;
        removalWarningGiven = false;
        
        // Спавним босса
        UniqueBossManager.spawnBoss(location, config);
        
        // Объявляем о спавне
        announceSpawn(location);
        
        // Запускаем напоминания
        startReminders();
        
        // Запускаем мониторинг неактивности
        startInactivityMonitoring();
        
        // Планируем следующий ивент (только если не принудительный спавн)
        if (!isForcedSpawn) {
            scheduleNextEvent();
        }
        
        String spawnType = isForcedSpawn ? "ПРИНУДИТЕЛЬНЫЙ" : "АВТОМАТИЧЕСКИЙ";
        long timeoutMinutes = isForcedSpawn ? 60 : config.getInactiveTimeout();
        plugin.getLogger().info("🎯 " + spawnType + " ивент босса запущен в мире " + location.getWorld().getName() + 
            " на координатах " + location.getBlockX() + ", " + location.getBlockY() + ", " + location.getBlockZ() +
            " (время жизни: " + timeoutMinutes + " минут)");
    }
    
    private void announceSpawn(Location location) {
        if (!config.isSpawnAnnouncementEnabled()) return;
        
        String world = location.getWorld().getName();
        String x = String.valueOf(location.getBlockX());
        String y = String.valueOf(location.getBlockY());
        String z = String.valueOf(location.getBlockZ());
        String timeoutMinutes = String.valueOf(config.getInactiveTimeout());
        
        // Глобальные сообщения
        String spawnMsg = config.getEventMessage("spawn_global", "%world%", world);
        String coordsMsg = config.getEventMessage("spawn_coordinates", 
            new String[]{"%x%", "%y%", "%z%"}, 
            new String[]{x, y, z});
        String rewardsMsg = config.getEventMessage("spawn_rewards");
        String warningMsg = config.getEventMessage("spawn_warning", "%time%", timeoutMinutes);
        String instructionsMsg = config.getEventMessage("find_instructions");
        String compassMsg = config.getEventMessage("compass_tip");
        
        // Заголовки
        String title = config.getEventMessage("spawn_global", "%world%", world);
        String subtitle = config.getEventMessage("spawn_coordinates", 
            new String[]{"%x%", "%y%", "%z%"}, 
            new String[]{x, y, z});
        
        for (Player player : Bukkit.getOnlinePlayers()) {
            // Чат сообщения
            player.sendMessage("");
            player.sendMessage(spawnMsg);
            player.sendMessage(rewardsMsg);
            player.sendMessage(warningMsg);
            player.sendMessage("");
            player.sendMessage(instructionsMsg);
            player.sendMessage(compassMsg);
            player.sendMessage("");
            player.sendMessage(coordsMsg); // Координаты в конце сообщения
            player.sendMessage("");
            
            // Заголовок (20 тиков появление, 80 тиков показ, 20 тиков исчезновение = 6 секунд)
            player.sendTitle(title, subtitle, 20, 80, 20);
            
            // Звуки
            player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 0.5f);
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
            
            // Список наград
            showRewardsPreview(player);
        }
    }
    
    private void showRewardsPreview(Player player) {
        player.sendMessage(config.getEventMessage("rewards_preview"));
        player.sendMessage(config.getEventMessage("reward_fragments"));
        player.sendMessage(config.getEventMessage("reward_elytra"));
        player.sendMessage(config.getEventMessage("reward_sword"));
        player.sendMessage(config.getEventMessage("reward_staff"));
        player.sendMessage(config.getEventMessage("reward_boots"));
        player.sendMessage(config.getEventMessage("reward_crystal"));
        player.sendMessage(config.getEventMessage("reward_resources"));
    }
    
    private void startReminders() {
        if (!config.isPeriodicRemindersEnabled()) return;
        
        if (reminderTask != null) {
            reminderTask.cancel();
        }
        
        reminderTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!UniqueBossManager.isBossActive() || reminderCount >= config.getMaxReminders()) {
                    this.cancel();
                    return;
                }
                
                sendReminder();
                reminderCount++;
            }
        }.runTaskTimer(plugin, config.getReminderInterval() * 20L, config.getReminderInterval() * 20L);
    }
    
    private void sendReminder() {
        if (bossSpawnLocation == null) return;
        
        String world = bossSpawnLocation.getWorld().getName();
        String x = String.valueOf(bossSpawnLocation.getBlockX());
        String y = String.valueOf(bossSpawnLocation.getBlockY());
        String z = String.valueOf(bossSpawnLocation.getBlockZ());
        
        long timeLeft = getRemainingTime();
        String timeString = formatTime(timeLeft);
        
        String title = config.getEventMessage("reminder_title");
        String subtitle = config.getEventMessage("reminder_subtitle");
        String chatMsg = config.getEventMessage("reminder_chat", "%world%", world);
        String locationMsg = config.getEventMessage("reminder_location", 
            new String[]{"%x%", "%y%", "%z%"}, 
            new String[]{x, y, z});
        String timeMsg = config.getEventMessage("reminder_time_left", "%time%", timeString);
        
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage("");
            player.sendMessage(chatMsg);
            player.sendMessage(locationMsg);
            player.sendMessage(timeMsg);
            player.sendMessage("");
            
            player.sendTitle(title, subtitle, 10, 40, 10);
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 0.8f);
        }
    }
    
    private void startInactivityMonitoring() {
        if (inactivityTask != null) {
            inactivityTask.cancel();
        }
        
        inactivityTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!UniqueBossManager.isBossActive()) {
                    this.cancel();
                    return;
                }
                
                checkBossActivity();
            }
        }.runTaskTimer(plugin, config.getCheckInterval() * 20L, config.getCheckInterval() * 20L);
    }
    
    private void checkBossActivity() {
        if (UniqueBossManager.getCurrentBoss() == null) {
            plugin.getLogger().info("⚠️ Мониторинг неактивности: босс не найден");
            return;
        }
        
        Entity bossEntity = UniqueBossManager.getCurrentBoss().getEntity();
        if (bossEntity == null) {
            plugin.getLogger().warning("⚠️ Мониторинг неактивности: сущность босса null");
            removeBossForInactivity();
            return;
        }
        
        Location bossLoc = bossEntity.getLocation();
        int checkRadius = config.getCheckRadius();
        
        boolean hasNearbyPlayers = false;
        int playerCount = 0;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getWorld().equals(bossLoc.getWorld()) && 
                player.getLocation().distance(bossLoc) <= checkRadius) {
                hasNearbyPlayers = true;
                playerCount++;
            }
        }
        
        // Если есть игроки рядом - СБРАСЫВАЕМ ТАЙМЕР
        if (hasNearbyPlayers) {
            // КРИТИЧЕСКИ ВАЖНО: Сбрасываем таймер когда игроки приходят
            bossSpawnTime = System.currentTimeMillis();
            
            if (removalWarningGiven) {
                plugin.getLogger().info("✅ Игроки вернулись к боссу (" + playerCount + " игроков) - СБРАСЫВАЕМ ТАЙМЕР");
                
                // Уведомляем игроков об отмене
                for (Player player : Bukkit.getOnlinePlayers()) {
                    player.sendMessage(ChatColor.GREEN + "✅ Игроки вернулись! Таймер неактивности сброшен.");
                }
            } else {
                plugin.getLogger().info("🔄 Игроки рядом с боссом (" + playerCount + " игроков) - таймер сброшен");
            }
            
            removalWarningGiven = false;
            return; // Не удаляем босса
        }
        
        long timeLeft = getRemainingTime();
        plugin.getLogger().info("⏰ Мониторинг неактивности: осталось " + (timeLeft / 1000 / 60) + " минут до удаления");
        
        // Предупреждение о скором удалении
        if (!removalWarningGiven && timeLeft <= config.getWarningTime() * 1000L) {
            sendRemovalWarning(timeLeft);
            removalWarningGiven = true;
        }
        
        // Удаляем босса если время вышло
        if (timeLeft <= 0) {
            plugin.getLogger().info("⏰ Время неактивности босса истекло - удаляем");
            removeBossForInactivity();
        }
    }
    
    private void sendRemovalWarning(long timeLeft) {
        if (!config.isRemovalWarningEnabled()) return;
        
        String timeString = formatTime(timeLeft);
        String title = config.getEventMessage("removal_warning");
        String subtitle = config.getEventMessage("removal_warning_text", "%time%", timeString);
        
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendTitle(title, subtitle, 20, 80, 20);
            player.sendMessage("");
            player.sendMessage(title);
            player.sendMessage(subtitle);
            player.sendMessage("");
            
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
        }
    }
    
    private void removeBossForInactivity() {
        if (UniqueBossManager.getCurrentBoss() != null) {
            plugin.getLogger().info("🔄 Начинаем удаление босса за неактивность...");
            
            // Используем специальный метод для принудительного уничтожения
            UniqueBossManager.getCurrentBoss().forceDestroy();
            
            // Очищаем менеджер босса
            UniqueBossManager.setBossDefeated();
            plugin.getLogger().info("✅ Менеджер босса очищен");
            
            // Объявляем об удалении
            String finalMsg = config.getEventMessage("removal_final");
            String reasonMsg = config.getEventMessage("removal_reason");
            
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.sendMessage("");
                player.sendMessage(finalMsg);
                player.sendMessage(reasonMsg);
                player.sendMessage("");
                
                player.playSound(player.getLocation(), Sound.ENTITY_PHANTOM_DEATH, 1.0f, 0.5f);
            }
            
            plugin.getLogger().info("👻 Босс удален за неактивность");
        } else {
            plugin.getLogger().info("⚠️ Попытка удалить неактивного босса - пропускаем");
        }
        
        // Сбрасываем все флаги
        isForcedSpawn = false;
        removalWarningGiven = false;
        
        // Останавливаем задачи
        stopAllTasks();
        
        // Планируем следующий ивент
        scheduleNextEvent();
    }
    
    private long getRemainingTime() {
        if (bossSpawnTime == 0) return 0;
        
        long elapsed = System.currentTimeMillis() - bossSpawnTime;
        
        // Для принудительного спавна даем больше времени
        long timeoutMinutes = isForcedSpawn ? 60 : config.getInactiveTimeout(); // 60 минут для принудительного
        long timeout = timeoutMinutes * 60 * 1000L;
        
        return Math.max(0, timeout - elapsed);
    }
    
    private String formatTime(long milliseconds) {
        long minutes = milliseconds / (60 * 1000);
        return String.valueOf(minutes);
    }
    
    public long getTimeUntilNextSpawn() {
        if (nextSpawnTime == 0) return 0;
        return Math.max(0, (nextSpawnTime - System.currentTimeMillis()) / (60 * 1000));
    }
    
    public String getBossStatus() {
        if (!UniqueBossManager.isBossActive()) {
            long minutesLeft = getTimeUntilNextSpawn();
            if (minutesLeft <= 0) {
                return "Босс готов к спавну! Ожидаем подходящих условий...";
            }
            return "Следующий ивент через " + minutesLeft + " минут";
        }
        
        long timeLeft = getRemainingTime();
        String timeString = formatTime(timeLeft);
        String spawnType = isForcedSpawn ? " (принудительный)" : "";
        return "Босс активен" + spawnType + ". Времени до исчезновения: " + timeString + " минут";
    }
    
    // Принудительный запуск ивента босса
    public boolean forceSpawnBoss() {
        if (UniqueBossManager.isBossActive()) {
            plugin.getLogger().warning("❌ Попытка принудительного спавна при активном боссе!");
            return false;
        }
        
        plugin.getLogger().info("🔧 ПРИНУДИТЕЛЬНЫЙ СПАВН: Начинаем поиск места для босса...");
        
        // Ищем подходящую локацию
        Location spawnLoc = findSuitableSpawnLocation();
        if (spawnLoc == null) {
            plugin.getLogger().warning("❌ ПРИНУДИТЕЛЬНЫЙ СПАВН: Не удалось найти подходящую локацию!");
            return false;
        }
        
        plugin.getLogger().info("✅ ПРИНУДИТЕЛЬНЫЙ СПАВН: Найдено место - " + 
            spawnLoc.getWorld().getName() + " [" + 
            spawnLoc.getBlockX() + ", " + spawnLoc.getBlockY() + ", " + spawnLoc.getBlockZ() + "]");
        
        // Устанавливаем флаг принудительного спавна
        isForcedSpawn = true;
        
        // Спавним босса с полным функционалом (это устанавливает bossSpawnTime)
        spawnEventBoss(spawnLoc);
        
        plugin.getLogger().info("🎯 ПРИНУДИТЕЛЬНЫЙ СПАВН: Босс успешно заспавнен с увеличенным временем жизни (60 минут)!");
        return true;
    }
    
    public void stopAllTasks() {
        if (eventSchedulerTask != null) {
            eventSchedulerTask.cancel();
        }
        if (reminderTask != null) {
            reminderTask.cancel();
        }
        if (inactivityTask != null) {
            inactivityTask.cancel();
        }
    }
    
    public void shutdown() {
        stopAllTasks();
        plugin.getLogger().info("🛑 Система автоматических ивентов остановлена");
    }
} 