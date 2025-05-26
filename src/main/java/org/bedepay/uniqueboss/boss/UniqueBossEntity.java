package org.bedepay.uniqueboss.boss;

import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.bukkit.plugin.Plugin;
import org.bukkit.Bukkit;
import org.bukkit.enchantments.Enchantment;
import org.bedepay.uniqueboss.config.ConfigManager;

import java.util.*;

public class UniqueBossEntity {
    
    private final Location spawnLocation;
    private Wither bossEntity;
    private int currentPhase = 1;
    private int tickCounter = 0;
    private boolean isAlive = true;
    private final Random random = new Random();
    private BossBar bossBar;
    private final ConfigManager config;
    
    // Система отслеживания задач для предотвращения утечек памяти
    private final List<BukkitTask> activeTasks = new ArrayList<>();
    
    // Для идентификации босса через PDC
    private static final org.bukkit.NamespacedKey BOSS_KEY = new org.bukkit.NamespacedKey(
        org.bukkit.Bukkit.getPluginManager().getPlugin("UniqueBoss"), "unique_boss");
    private static final org.bukkit.NamespacedKey BOSS_SPAWN_LOCATION_KEY = new org.bukkit.NamespacedKey(
        org.bukkit.Bukkit.getPluginManager().getPlugin("UniqueBoss"), "boss_spawn_location");
    
    // Переменная для отслеживания здоровья
    private double lastKnownHealth;
    
    // Переменные для провокаций в чат
    private long lastTauntTime = 0;
    private String lastAttacker = null;
    private boolean lowHealthTauntSent = false;
    
    public UniqueBossEntity(Location location, ConfigManager config) {
        this.spawnLocation = location.clone();
        this.config = config;
        this.lastKnownHealth = config.getPhase1Health();
    }
    
    public void spawn() {
        if (spawnLocation.getWorld() == null) return;
        
        Plugin plugin = Bukkit.getPluginManager().getPlugin("UniqueBoss");
        plugin.getLogger().info("🌟 Спавн Темного Повелителя...");
        plugin.getLogger().info("📍 Локация: " + spawnLocation.getBlockX() + ", " + 
            spawnLocation.getBlockY() + ", " + spawnLocation.getBlockZ() + " в мире " + spawnLocation.getWorld().getName());
        
        // СИСТЕМА СЛОЖНОСТИ: Логируем уровень сложности
        plugin.getLogger().info("⚔️ Уровень сложности: " + config.getDifficultyLevel() + " (" + config.getDifficultyName() + ")");
        plugin.getLogger().info("💪 Модификаторы: Здоровье x" + config.getDifficultyHealthMultiplier() + 
            ", Скорость способностей x" + config.getDifficultyAbilitiesSpeedMultiplier() + 
            ", Дроп x" + config.getDifficultyDropsMultiplier());
        
        if (config.isBossLifecycleLoggingEnabled()) {
            plugin.getLogger().info("🔧 DEBUG: Начинаем спавн босса...");
            plugin.getLogger().info("   Локация: " + spawnLocation);
            plugin.getLogger().info("   Мир: " + spawnLocation.getWorld().getName());
            plugin.getLogger().info("   Чанк загружен: " + spawnLocation.getChunk().isLoaded());
        }
        
        // Создаем Wither как основу босса
        try {
            bossEntity = (Wither) spawnLocation.getWorld().spawnEntity(spawnLocation, EntityType.WITHER);
            
            if (config.isBossLifecycleLoggingEnabled()) {
                plugin.getLogger().info("✅ DEBUG: Сущность создана успешно:");
                plugin.getLogger().info("   Entity ID: " + bossEntity.getEntityId());
                plugin.getLogger().info("   UUID: " + bossEntity.getUniqueId());
                plugin.getLogger().info("   isValid: " + bossEntity.isValid());
                plugin.getLogger().info("   isDead: " + bossEntity.isDead());
                plugin.getLogger().info("   Здоровье: " + bossEntity.getHealth());
                plugin.getLogger().info("   Локация: " + bossEntity.getLocation());
                plugin.getLogger().info("   Мир: " + bossEntity.getWorld().getName());
            }
            
        } catch (Exception e) {
            plugin.getLogger().severe("💥 КРИТИЧЕСКАЯ ОШИБКА: Не удалось создать сущность босса!");
            e.printStackTrace();
            return;
        }
        
        // КРИТИЧЕСКИ ВАЖНО: Сохраняем в PDC информацию о том, что это наш босс
        try {
            org.bedepay.uniqueboss.listeners.BossChunkListener.markAsUniqueBoss(bossEntity, spawnLocation);
            
            if (config.isPdcOperationsLoggingEnabled()) {
                plugin.getLogger().info("✅ DEBUG: PDC метки сохранены для босса " + bossEntity.getEntityId());
            }
            
            // НОВОЕ: Принудительно загружаем чанк босса
            org.bukkit.Chunk bossChunk = spawnLocation.getChunk();
            if (!bossChunk.isLoaded()) {
                bossChunk.load(true);
                plugin.getLogger().info("🔧 Принудительно загрузили чанк босса при спавне: [" + 
                    bossChunk.getX() + ", " + bossChunk.getZ() + "]");
            }
            if (!bossChunk.isForceLoaded()) {
                bossChunk.setForceLoaded(true);
                plugin.getLogger().info("🔧 Установили принудительную загрузку чанка босса при спавне: [" + 
                    bossChunk.getX() + ", " + bossChunk.getZ() + "]");
            }
            
        } catch (Exception e) {
            plugin.getLogger().severe("💥 ОШИБКА: Не удалось сохранить PDC метки!");
            e.printStackTrace();
        }
        
        // Настраиваем босса
        try {
            setupBoss();
            
            if (config.isBossLifecycleLoggingEnabled()) {
                plugin.getLogger().info("✅ DEBUG: Настройка босса завершена:");
                plugin.getLogger().info("   isValid после настройки: " + bossEntity.isValid());
                plugin.getLogger().info("   isDead после настройки: " + bossEntity.isDead());
                plugin.getLogger().info("   Здоровье после настройки: " + bossEntity.getHealth());
                plugin.getLogger().info("   Эффекты: " + bossEntity.getActivePotionEffects().size());
            }
            
        } catch (Exception e) {
            plugin.getLogger().severe("💥 ОШИБКА: Не удалось настроить босса!");
            e.printStackTrace();
        }
        
        // Создаем бossbar
        try {
            createBossBar();
            
            if (config.isBossLifecycleLoggingEnabled()) {
                plugin.getLogger().info("✅ DEBUG: BossBar создан");
            }
            
        } catch (Exception e) {
            plugin.getLogger().severe("💥 ОШИБКА: Не удалось создать BossBar!");
            e.printStackTrace();
        }
        
        // Запускаем основной цикл поведения босса
        try {
            if (config.isBossLifecycleLoggingEnabled()) {
                plugin.getLogger().info("🔄 DEBUG: Запускаем AI босса...");
                plugin.getLogger().info("   isValid перед AI: " + bossEntity.isValid());
                plugin.getLogger().info("   isDead перед AI: " + bossEntity.isDead());
            }
            
            startBossAI();
            
        } catch (Exception e) {
            plugin.getLogger().severe("💥 ОШИБКА: Не удалось запустить AI босса!");
            e.printStackTrace();
        }
        
        // Звуковые и визуальные эффекты появления
        try {
            spawnEffects();
            
            if (config.isBossLifecycleLoggingEnabled()) {
                plugin.getLogger().info("✅ DEBUG: Эффекты спавна запущены");
            }
            
        } catch (Exception e) {
            plugin.getLogger().severe("💥 ОШИБКА: Не удалось запустить эффекты спавна!");
            e.printStackTrace();
        }
        
        // Запускаем постоянные визуальные эффекты
        try {
            startAmbientEffects();
            
            if (config.isBossLifecycleLoggingEnabled()) {
                plugin.getLogger().info("✅ DEBUG: Амбиентные эффекты запущены");
            }
            
        } catch (Exception e) {
            plugin.getLogger().severe("💥 ОШИБКА: Не удалось запустить амбиентные эффекты!");
            e.printStackTrace();
        }
        
        if (config.isBossLifecycleLoggingEnabled()) {
            plugin.getLogger().info("🎉 DEBUG: Спавн босса полностью завершен:");
            plugin.getLogger().info("   Entity ID: " + bossEntity.getEntityId());
            plugin.getLogger().info("   isValid: " + bossEntity.isValid());
            plugin.getLogger().info("   isDead: " + bossEntity.isDead());
            plugin.getLogger().info("   Здоровье: " + bossEntity.getHealth());
            plugin.getLogger().info("   isAlive флаг: " + isAlive);
        }
        
        plugin.getLogger().info("✅ Босс создан с PDC меткой в чанке [" + 
            spawnLocation.getChunk().getX() + ", " + spawnLocation.getChunk().getZ() + "] - " +
            "чанк будет автоматически восстановлен при загрузке");
        
        // Глобальные сообщения и эффекты
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getWorld().equals(spawnLocation.getWorld())) {
                // Основные сообщения спавна
                player.sendTitle(config.getBossSpawnTitle(), config.getBossSpawnSubtitle(), 20, 80, 20);
                
                // НОВОЕ: Информация о сложности босса
                String difficultyMsg = config.getMessage("boss.difficulty_announcement")
                    .replace("%level%", String.valueOf(config.getDifficultyLevel()))
                    .replace("%name%", config.getDifficultyName());
                    
                String modifiersMsg = config.getMessage("boss.difficulty_modifiers")
                    .replace("%health%", String.format("%.1f", config.getDifficultyHealthMultiplier()))
                    .replace("%damage%", String.format("%.1f", config.getDifficultyDamageMultiplier()))
                    .replace("%minions%", String.format("%.1f", config.getDifficultyMinionsCountMultiplier()));
                
                player.sendMessage(difficultyMsg);
                player.sendMessage(modifiersMsg);
            }
        }
    }
    

    
    private void setupBoss() {
        bossEntity.setCustomName(ChatColor.DARK_RED + "⚡ ТЕМНЫЙ ПОВЕЛИТЕЛЬ ⚡");
        bossEntity.setCustomNameVisible(true);
        bossEntity.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(config.getPhase1Health());
        bossEntity.setHealth(config.getPhase1Health());
        bossEntity.setRemoveWhenFarAway(false);
        
        // ОТКЛЮЧАЕМ стандартный боссбар Wither
        if (bossEntity.getBossBar() != null) {
            bossEntity.getBossBar().removeAll();
            bossEntity.getBossBar().setVisible(false);
        }
        
        // Добавляем эффекты (БЕЗ РЕГЕНЕРАЦИИ!)
        bossEntity.addPotionEffect(new PotionEffect(PotionEffectType.DAMAGE_RESISTANCE, Integer.MAX_VALUE, 1));
        bossEntity.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, Integer.MAX_VALUE, 0));
        bossEntity.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, Integer.MAX_VALUE, 0)); // Светится
        
        // КРИТИЧЕСКИ ВАЖНО: Блокируем способность к самовосстановлению
        // Добавляем слабость чтобы снизить регенерацию
        bossEntity.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, Integer.MAX_VALUE, 0, true, false));
        
        // Также устанавливаем флаг для Wither чтобы он не восстанавливался
        bossEntity.setInvulnerable(false); // Убеждаемся что он не неуязвим
    }
    
    private void createBossBar() {
        bossBar = Bukkit.createBossBar(
            ChatColor.DARK_RED + "⚡ ТЕМНЫЙ ПОВЕЛИТЕЛЬ ⚡",
            BarColor.RED,
            BarStyle.SEGMENTED_10
        );
        bossBar.setProgress(1.0);
    }
    
    private void updateBossBarPlayers() {
        // Очищаем старых игроков
        bossBar.removeAll();
        
        // Добавляем ВСЕ ближайших игроков (в радиусе 50 блоков) - без фильтрации по режиму игры!
        List<Player> nearbyPlayers = getNearbyPlayersForBossBar(50);
        for (Player player : nearbyPlayers) {
            bossBar.addPlayer(player);
        }
    }
    
    /**
     * Получает список ближайших игроков для боссбара 
     * Фильтрует по настройкам конфига: show_bossbar_all_modes
     */
    private List<Player> getNearbyPlayersForBossBar(double radius) {
        List<Player> nearbyPlayers = new ArrayList<>();
        for (Entity entity : bossEntity.getNearbyEntities(radius, radius, radius)) {
            if (entity instanceof Player) {
                Player player = (Player) entity;
                
                // Исключаем спектаторов (если настройка отключена)
                if (player.getGameMode() == org.bukkit.GameMode.SPECTATOR && !config.shouldAttackSpectators()) {
                    continue; // Пропускаем игроков в режиме наблюдателя (если настройка отключена)
                }
                
                // Исключаем невидимых игроков (vanish плагины)
                if (config.shouldIgnoreVanished() && !player.getCanPickupItems() && player.isInvisible()) {
                    continue; // Возможно игрок в vanish
                }
                
                // Проверяем настройку показа боссбара всем режимам
                if (!config.shouldShowBossBarAllModes()) {
                    // Если настройка отключена - показываем только Survival и Adventure
                    if (player.getGameMode() != org.bukkit.GameMode.SURVIVAL && 
                        player.getGameMode() != org.bukkit.GameMode.ADVENTURE) {
                        continue; // Пропускаем Creative и другие режимы
                    }
                }
                
                // Если настройка включена - показываем всем (кроме исключений выше)
                nearbyPlayers.add(player);
            }
        }
        return nearbyPlayers;
    }
    
    private void startAmbientEffects() {
        Plugin plugin = Bukkit.getPluginManager().getPlugin("UniqueBoss");
        
        BukkitTask ambientTask = new BukkitRunnable() {
            private int ambientTick = 0;
            
            @Override
            public void run() {
                if (bossEntity == null || !bossEntity.isValid() || bossEntity.isDead()) {
                    cancel(); // Прекращаем если босс мертв
                    return;
                }
                
                ambientTick++;
                
                // Темные энергетические кольца каждые 3 секунды
                if (ambientTick % 60 == 0) {
                    createDarkEnergyCircle(bossEntity.getLocation());
                }
                
                // Случайные частицы каждую секунду
                if (ambientTick % 20 == 0) {
                    Location bossLoc = bossEntity.getLocation();
                    World world = bossEntity.getWorld();
                    
                    double multiplier = config.getParticlesMultiplier();
                    
                    // Темные частицы вокруг босса
                    world.spawnParticle(Particle.SPELL_WITCH, 
                        bossLoc.clone().add(random.nextGaussian() * 3, random.nextGaussian() * 3, random.nextGaussian() * 3), 
                        (int)(3 * multiplier), 0.5, 0.5, 0.5, 0.02);
                    
                    // Случайный зловещий звук каждые 10 секунд
                    if (ambientTick % 200 == 0 && random.nextInt(100) < 30) {
                        Sound[] evilSounds = {
                            Sound.ENTITY_WITHER_AMBIENT,
                            Sound.ENTITY_ENDERMAN_STARE,
                            Sound.AMBIENT_CAVE
                        };
                        Sound randomSound = evilSounds[random.nextInt(evilSounds.length)];
                        world.playSound(bossLoc, randomSound, 0.8f, 0.7f + random.nextFloat() * 0.6f);
                    }
                    
                    // Красные глаза каждые 5 секунд
                    if (ambientTick % 100 == 0) {
                        world.spawnParticle(Particle.REDSTONE, 
                            bossLoc.clone().add(0, 2.5, 0), 
                            (int)(8 * multiplier), 0.3, 0.1, 0.3, 
                            new Particle.DustOptions(org.bukkit.Color.RED, 1.5f));
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
        
        // Регистрируем задачу
        registerTask(ambientTask);
    }
    
    private void createDarkEnergyCircle(Location center) {
        World world = center.getWorld();
        double radius = 3.0;
        int points = 16;
        
        for (int i = 0; i < points; i++) {
            double angle = 2 * Math.PI * i / points;
            double x = center.getX() + radius * Math.cos(angle);
            double z = center.getZ() + radius * Math.sin(angle);
            Location particleLoc = new Location(world, x, center.getY() + 0.5, z);
            
            world.spawnParticle(Particle.SPELL_WITCH, particleLoc, 1, 0, 0, 0, 0);
        }
    }
    
    private void spawnEffects() {
        World world = spawnLocation.getWorld();
        
        // ЭПИЧЕСКИЕ эффекты появления
        
        // Мощные взрывы в несколько этапов
        for (int i = 0; i < 3; i++) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    world.createExplosion(spawnLocation.clone().add(
                        random.nextDouble() * 4 - 2, 
                        random.nextDouble() * 2, 
                        random.nextDouble() * 4 - 2
                    ), 4.0f, false, false);
                }
            }.runTaskLater(Bukkit.getPluginManager().getPlugin("UniqueBoss"), i * 10L);
        }
        
        // Звуковая симфония появления
        world.playSound(spawnLocation, Sound.ENTITY_WITHER_SPAWN, 2.0f, 0.5f);
        world.playSound(spawnLocation, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 2.0f, 0.8f);
        world.playSound(spawnLocation, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.5f, 0.3f);
        world.playSound(spawnLocation, Sound.AMBIENT_CAVE, 1.0f, 0.5f);
        
        // Эффекты частиц (оптимизированы)
        double multiplier = config.getParticlesMultiplier();
        world.spawnParticle(Particle.EXPLOSION_LARGE, spawnLocation, (int)(8 * multiplier), 2, 2, 2, 0.1);
        world.spawnParticle(Particle.SMOKE_LARGE, spawnLocation, (int)(30 * multiplier), 3, 3, 3, 0.1);
        world.spawnParticle(Particle.SPELL_WITCH, spawnLocation, (int)(20 * multiplier), 4, 4, 4, 0.2);
        world.spawnParticle(Particle.REDSTONE, spawnLocation, (int)(25 * multiplier), 3, 3, 3, 
            new Particle.DustOptions(org.bukkit.Color.MAROON, 2.0f));
        
        // Молнии вокруг в красивом порядке
        for (int i = 0; i < 8; i++) {
            double angle = 2 * Math.PI * i / 8;
            Location lightningLoc = spawnLocation.clone().add(
                Math.cos(angle) * 8,
                0,
                Math.sin(angle) * 8
            );
            
            new BukkitRunnable() {
                @Override
                public void run() {
                    world.strikeLightningEffect(lightningLoc);
                    world.spawnParticle(Particle.ELECTRIC_SPARK, lightningLoc, 
                        (int)(20 * multiplier), 1, 2, 1, 0.1);
                }
            }.runTaskLater(Bukkit.getPluginManager().getPlugin("UniqueBoss"), i * 5L);
        }
        
        // Темная волна расходится от босса
        createDarkWave(spawnLocation, 15.0);
        
        // Сообщение только ближайшим игрокам
        List<Player> nearbyPlayers = getNearbyPlayers(100);
        for (Player player : nearbyPlayers) {
            player.sendTitle(ChatColor.DARK_RED + "💀 БОСС ПОЯВИЛСЯ! 💀", 
                ChatColor.RED + "Темный Повелитель пробудился!", 20, 60, 20);
            
            // Эффект тряски экрана
            player.playSound(player.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 0.5f);
        }
    }
    
    private void createDarkWave(Location center, double maxRadius) {
        World world = center.getWorld();
        Plugin plugin = Bukkit.getPluginManager().getPlugin("UniqueBoss");
        double multiplier = config.getParticlesMultiplier();
        
        new BukkitRunnable() {
            double radius = 0;
            
            @Override
            public void run() {
                if (radius > maxRadius) {
                    this.cancel();
                    return;
                }
                
                int points = (int) (radius * 4 * multiplier); // Уменьшаем количество точек
                for (int i = 0; i < points; i++) {
                    double angle = 2 * Math.PI * i / points;
                    double x = center.getX() + radius * Math.cos(angle);
                    double z = center.getZ() + radius * Math.sin(angle);
                    Location particleLoc = new Location(world, x, center.getY() + 0.2, z);
                    
                    world.spawnParticle(Particle.SMOKE_LARGE, particleLoc, 1, 0, 0, 0, 0.02);
                    world.spawnParticle(Particle.REDSTONE, particleLoc, 1, 0, 0, 0, 
                        new Particle.DustOptions(org.bukkit.Color.BLACK, 1.0f));
                }
                
                radius += 0.5;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }
    
    private void startBossAI() {
        Plugin plugin = Bukkit.getPluginManager().getPlugin("UniqueBoss");
        
        // Главный цикл поведения босса (каждый тик)
        BukkitTask mainAiTask = new BukkitRunnable() {
            private int debugTick = 0;
            
            @Override
            public void run() {
                // Увеличиваем счетчик тиков и проверяем состояние босса
                tickCounter++;
                debugTick++;
                
                // Детальное логирование раз в 20 тиков (1 секунда)
                if (config.isBossLifecycleLoggingEnabled() && debugTick % 20 == 0) {
                    plugin.getLogger().info("🔄 DEBUG AI Tick " + debugTick + ": " + 
                        (bossEntity != null && bossEntity.isValid() && !bossEntity.isDead() ? "(boss alive)" : "(boss dead/invalid)"));
                }
                
                // Проверяем валидность босса каждый тик
                if (bossEntity == null || !bossEntity.isValid() || bossEntity.isDead()) {
                    if (config.isBossLifecycleLoggingEnabled()) {
                        plugin.getLogger().info("⚠️ DEBUG: bossEntity.isValid() = " + 
                            (bossEntity != null ? bossEntity.isValid() : "null") + " (tick " + debugTick + ")");
                        
                        if (bossEntity != null) {
                            plugin.getLogger().info("   bossEntity.isDead() = " + bossEntity.isDead());
                            plugin.getLogger().info("   bossEntity class = " + bossEntity.getClass().getSimpleName());
                        }
                    }
                    
                    if (isAlive) {
                        plugin.getLogger().warning("💀 Сущность босса стала невалидной или мертвой на тике " + debugTick + " - обрабатываем смерть");
                        handleBossDeath();
                    }
                    cancel(); // Прекращаем выполнение задачи
                    return;
                }
                
                // Обновляем бossbar для игроков рядом
                updateBossBarPlayers();
                updateBossBarDisplay();
                
                // Ограничиваем высоту полета босса
                limitBossHeight();
                
                // Предотвращаем регенерацию здоровья
                preventRegeneration();
                
                // Система тонких проверок здоровья для смены фаз
                updatePhase();
                
                // Проверяем провокации в чат
                if (tickCounter % 40 == 0) { // Каждые 2 секунды
                    sendRandomTaunt();
                    checkLowHealthTaunts();
                }
                
                // Выполняем способности фазы каждые 3 секунды (60 тиков)
                if (tickCounter % 60 == 0) {
                    executePhaseAbilities();
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
        
        // Регистрируем задачу для автоматической отмены
        registerTask(mainAiTask);
    }
    
    private void preventRegeneration() {
        if (bossEntity == null || bossEntity.isDead()) return;
        
        // СИСТЕМА СЛОЖНОСТИ: На экстремальном уровне разрешаем регенерацию
        if (config.hasDifficultyBossRegeneration()) {
            // Медленная регенерация на высоких уровнях сложности
            double currentHealth = bossEntity.getHealth();
            double maxHealth = bossEntity.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
            
            if (currentHealth < maxHealth) {
                double healAmount = maxHealth * 0.001; // 0.1% в тик
                bossEntity.setHealth(Math.min(maxHealth, currentHealth + healAmount));
                
                // Эффекты регенерации
                if (tickCounter % 40 == 0) { // Каждые 2 секунды
                    World world = bossEntity.getWorld();
                    Location loc = bossEntity.getLocation();
                    world.spawnParticle(Particle.HEART, loc.add(0, 2, 0), 5, 1, 1, 1, 0.1);
                    world.spawnParticle(Particle.VILLAGER_HAPPY, loc, 10, 2, 2, 2, 0.1);
                }
            }
        } else {
            // Стандартное поведение - убираем регенерацию
            bossEntity.removePotionEffect(PotionEffectType.REGENERATION);
        }
    }
    
    private void limitBossHeight() {
        Location bossLoc = bossEntity.getLocation();
        double groundY = spawnLocation.getY();
        
        // Если босс слишком высоко, принудительно опускаем его
        if (bossLoc.getY() > groundY + config.getMaxFlightHeight()) {
            Location newLoc = bossLoc.clone();
            newLoc.setY(groundY + config.getMaxFlightHeight() - 1);
            bossEntity.teleport(newLoc);
            
            // Добавляем гравитацию
            bossEntity.setVelocity(new Vector(0, -0.8, 0));
        }
        
        // УМЕНЬШАЕМ дистанцию возврата для более "домашнего" поведения
        double maxDistance = 30.0; // Было 50, стало 30
        if (bossLoc.distance(spawnLocation) > maxDistance) {
            Location returnLoc = spawnLocation.clone().add(0, 2, 0);
            bossEntity.teleport(returnLoc);
            
            // Эффекты принудительного возврата
            bossEntity.getWorld().playSound(bossEntity.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.5f, 0.8f);
            bossEntity.getWorld().spawnParticle(Particle.PORTAL, bossEntity.getLocation(), 30);
            
            Plugin plugin = Bukkit.getPluginManager().getPlugin("UniqueBoss");
            plugin.getLogger().info("🏠 Босс вернулся к месту спавна (дистанция > " + maxDistance + ")");
        }
        
        // НОВОЕ: Если нет игроков рядом, босс зависает в воздухе на месте спавна
        List<Player> nearbyPlayers = getNearbyPlayers(50);
        if (nearbyPlayers.isEmpty()) {
            // Никого нет рядом - зависаем на месте спавна
            Location hoverLoc = spawnLocation.clone().add(0, 5, 0);
            if (bossLoc.distance(hoverLoc) > 5) {
                bossEntity.teleport(hoverLoc);
                // Убираем скорость чтобы не улетал
                bossEntity.setVelocity(new Vector(0, 0, 0));
            }
        }
    }
    
    private void updatePhase() {
        double currentHealth = bossEntity.getHealth();
        double maxHealth = bossEntity.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
        double healthPercentage = currentHealth / maxHealth;
        
        int newPhase = currentPhase;
        
        if (healthPercentage <= 0.3 && currentPhase < 3) {
            newPhase = 3;
        } else if (healthPercentage <= 0.6 && currentPhase < 2) {
            newPhase = 2;
        }
        
        if (newPhase != currentPhase) {
            transitionToPhase(newPhase);
        }
    }
    
    private void transitionToPhase(int newPhase) {
        currentPhase = newPhase;
        World world = bossEntity.getWorld();
        
        // ЭПИЧЕСКИЕ эффекты перехода фазы
        createPhaseTransitionEffects(newPhase);
        
        // Провокационные сообщения при смене фазы
        sendPhaseTaunt(newPhase);
        
        List<Player> nearbyPlayers = getNearbyPlayers(50);
        
        switch (currentPhase) {
            case 2:
                bossEntity.setCustomName(ChatColor.DARK_PURPLE + "⚡ ТЕМНЫЙ ПОВЕЛИТЕЛЬ ⚡ " + ChatColor.RED + "[ФАЗА 2]");
                bossBar.setTitle(ChatColor.DARK_PURPLE + "⚡ ТЕМНЫЙ ПОВЕЛИТЕЛЬ ⚡ " + ChatColor.RED + "[ФАЗА 2]");
                bossBar.setColor(BarColor.PURPLE);
                
                // Сообщение только ближайшим игрокам
                for (Player player : nearbyPlayers) {
                    player.sendTitle(ChatColor.DARK_PURPLE + "⚡ ФАЗА 2 ⚡", 
                        ChatColor.RED + "Магическая ярость!", 10, 40, 10);
                }
                // Добавляем скорость (БЕЗ РЕГЕНЕРАЦИИ!)
                bossEntity.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 1));
                break;
                
            case 3:
                bossEntity.setCustomName(ChatColor.DARK_RED + "⚡ ТЕМНЫЙ ПОВЕЛИТЕЛЬ ⚡ " + ChatColor.GOLD + "[ФИНАЛЬНАЯ ФАЗА]");
                bossBar.setTitle(ChatColor.DARK_RED + "⚡ ТЕМНЫЙ ПОВЕЛИТЕЛЬ ⚡ " + ChatColor.GOLD + "[ФИНАЛЬНАЯ ФАЗА]");
                bossBar.setColor(BarColor.RED);
                
                for (Player player : nearbyPlayers) {
                    player.sendTitle(ChatColor.DARK_RED + "💀 ФИНАЛЬНАЯ ФАЗА! 💀", 
                        ChatColor.GOLD + "Последняя битва!", 10, 50, 10);
                }
                // Добавляем еще больше скорости (БЕЗ РЕГЕНЕРАЦИИ!)
                bossEntity.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 2));
                break;
        }
    }
    
    private void createPhaseTransitionEffects(int phase) {
        World world = bossEntity.getWorld();
        Location bossLoc = bossEntity.getLocation();
        
        // Мощный взрыв при переходе
        world.createExplosion(bossLoc, 5.0f, false, false);
        
        // Звуки перехода
        world.playSound(bossLoc, Sound.ENTITY_WITHER_AMBIENT, 2.0f, 0.5f);
        world.playSound(bossLoc, Sound.ENTITY_ENDER_DRAGON_GROWL, 2.0f, 0.8f);
        world.playSound(bossLoc, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.5f, 1.0f);
        
        switch (phase) {
            case 2:
                // Фиолетовые эффекты для фазы 2
                world.spawnParticle(Particle.SPELL_WITCH, bossLoc, 100, 4, 4, 4, 0.3);
                world.spawnParticle(Particle.PORTAL, bossLoc, 50, 3, 3, 3, 0.2);
                world.spawnParticle(Particle.REDSTONE, bossLoc, 30, 3, 3, 3, 
                    new Particle.DustOptions(org.bukkit.Color.PURPLE, 2.0f));
                
                // Кольца магической энергии
                createMagicRings(bossLoc, org.bukkit.Color.PURPLE);
                break;
                
            case 3:
                // Огненные эффекты для финальной фазы
                world.spawnParticle(Particle.FLAME, bossLoc, 80, 4, 4, 4, 0.3);
                world.spawnParticle(Particle.LAVA, bossLoc, 40, 3, 3, 3, 0.2);
                world.spawnParticle(Particle.SMOKE_LARGE, bossLoc, 60, 4, 4, 4, 0.2);
                world.spawnParticle(Particle.REDSTONE, bossLoc, 50, 4, 4, 4, 
                    new Particle.DustOptions(org.bukkit.Color.RED, 2.5f));
                
                // Огненные кольца
                createMagicRings(bossLoc, org.bukkit.Color.RED);
                
                // Дополнительные молнии
                for (int i = 0; i < 5; i++) {
                    Location lightningLoc = bossLoc.clone().add(
                        random.nextDouble() * 8 - 4,
                        0,
                        random.nextDouble() * 8 - 4
                    );
                    world.strikeLightningEffect(lightningLoc);
                }
                break;
        }
    }
    
    private void createMagicRings(Location center, Color color) {
        World world = center.getWorld();
        Plugin plugin = Bukkit.getPluginManager().getPlugin("UniqueBoss");
        
        // Создаем 3 кольца разного размера
        for (int ring = 1; ring <= 3; ring++) {
            final double ringRadius = ring * 2.0;
            final int ringIndex = ring;
            
            new BukkitRunnable() {
                int ticks = 0;
                
                @Override
                public void run() {
                    if (ticks > 20) { // 1 секунда
                        this.cancel();
                        return;
                    }
                    
                    int points = 20;
                    for (int i = 0; i < points; i++) {
                        double angle = 2 * Math.PI * i / points;
                        double x = center.getX() + ringRadius * Math.cos(angle);
                        double z = center.getZ() + ringRadius * Math.sin(angle);
                        double y = center.getY() + 0.5 + Math.sin(ticks * 0.3) * 0.5; // Волнообразное движение
                        
                        Location particleLoc = new Location(world, x, y, z);
                        world.spawnParticle(Particle.REDSTONE, particleLoc, 1, 0, 0, 0, 
                            new Particle.DustOptions(color, 1.5f));
                    }
                    
                    ticks++;
                }
            }.runTaskTimer(plugin, ringIndex * 5L, 1L);
        }
    }
    
    private void executePhaseAbilities() {
        switch (currentPhase) {
            case 1:
                phaseOneAbilities();
                break;
            case 2:
                phaseTwoAbilities();
                break;
            case 3:
                phaseThreeAbilities();
                break;
        }
        
        // УНИКАЛЬНЫЕ СПОСОБНОСТИ ТОЛЬКО ДЛЯ 5 УРОВНЯ СЛОЖНОСТИ (ЭКСТРЕМАЛЬНЫЙ)
        if (config.hasDifficultyUniqueAbilities()) {
            executeExtremeAbilities();
        }
    }
    
    private void phaseOneAbilities() {
        // Каждые 5 секунд - обычная атака
        if (tickCounter % 100 == 0) {
            fireballBarrage();
        }
        
        // Каждые 8 секунд - призыв скелетов
        if (tickCounter % 160 == 0) {
            summonMinions();
        }
        
        // Каждые 12 секунд - притягивание игроков
        if (tickCounter % 240 == 0) {
            pullPlayersAttack();
        }
    }
    
    private void phaseTwoAbilities() {
        // Все способности первой фазы, но чаще
        if (tickCounter % 80 == 0) {
            fireballBarrage();
        }
        
        if (tickCounter % 120 == 0) {
            summonMinions();
        }
        
        if (tickCounter % 180 == 0) {
            pullPlayersAttack();
        }
        
        // Новая способность - телепортация
        if (tickCounter % 200 == 0) {
            teleportStrike();
        }
        
        // Магические снаряды
        if (tickCounter % 60 == 0) {
            magicMissiles();
        }
        
        // Земляные шипы
        if (tickCounter % 140 == 0) {
            earthSpikes();
        }
    }
    
    private void phaseThreeAbilities() {
        // Все предыдущие способности еще чаще
        if (tickCounter % 60 == 0) {
            fireballBarrage();
        }
        
        if (tickCounter % 100 == 0) {
            summonMinions();
        }
        
        if (tickCounter % 120 == 0) {
            pullPlayersAttack();
        }
        
        if (tickCounter % 150 == 0) {
            teleportStrike();
        }
        
        if (tickCounter % 40 == 0) {
            magicMissiles();
        }
        
        if (tickCounter % 100 == 0) {
            earthSpikes();
        }
        
        // Финальные способности
        if (tickCounter % 300 == 0) {
            meteorStorm();
        }
        
        // Ослепляющая вспышка
        if (tickCounter % 180 == 0) {
            blindingFlash();
        }
        
        // НОВАЯ УНИКАЛЬНАЯ АТАКА - Душераздирающий крик
        if (tickCounter % 220 == 0) {
            soulScream();
        }
    }
    
    // 🔥 УНИКАЛЬНЫЕ СПОСОБНОСТИ ДЛЯ 5 УРОВНЯ СЛОЖНОСТИ (ЭКСТРЕМАЛЬНЫЙ) 🔥
    private void executeExtremeAbilities() {
        // 💀 СМЕРТЕЛЬНЫЙ ВЗГЛЯД ТЕМНОГО ПОВЕЛИТЕЛЯ - каждые 15 секунд (300 тиков)
        if (tickCounter % 300 == 0) {
            deathGaze();
        }
        
        // ⚡ ЦЕПНАЯ МОЛНИЯ ХАОСА - каждые 18 секунд (360 тиков) 
        if (tickCounter % 360 == 0) {
            chaosLightning();
        }
        
        // 🌀 ВИХРЬ ТЬМЫ - каждые 25 секунд (500 тиков)
        if (tickCounter % 500 == 0) {
            darknessVortex();
        }
    }
    
    // НОВЫЕ УНИКАЛЬНЫЕ СПОСОБНОСТИ
    
    private void pullPlayersAttack() {
        List<Player> nearbyPlayers = getNearbyPlayers(25.0); // 25 блоков радиус
        if (nearbyPlayers.isEmpty()) return;
        
        // Звук подготовки атаки
        World world = bossEntity.getWorld();
        Location bossLoc = bossEntity.getLocation();
        world.playSound(bossLoc, Sound.ENTITY_ENDER_DRAGON_GROWL, 2.0f, 0.3f);
        
        // Эффекты подготовки
        double multiplier = config.getParticlesMultiplier();
        world.spawnParticle(Particle.PORTAL, bossLoc.clone().add(0, 2, 0), 
            (int)(100 * multiplier), 3, 3, 3, 0.5);
        
        // Притягивающая атака через 1.5 секунды
        Plugin plugin = Bukkit.getPluginManager().getPlugin("UniqueBoss");
        BukkitTask pullTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (bossEntity == null || bossEntity.isDead()) {
                    cancel();
                    return;
                }
                
                for (Player player : nearbyPlayers) {
                    if (player.getLocation().distance(bossEntity.getLocation()) <= 25.0) {
                        Vector direction = bossEntity.getLocation().toVector().subtract(player.getLocation().toVector()).normalize();
                        Vector pullForce = direction.multiply(2.0); // Сила притяжения
                        player.setVelocity(pullForce);
                        
                        // Эффекты притяжения
                        world.spawnParticle(Particle.REDSTONE, player.getLocation().clone().add(0, 1, 0), 
                            (int)(20 * multiplier), 1, 1, 1, 
                            new Particle.DustOptions(org.bukkit.Color.PURPLE, 1.5f));
                    }
                }
                
                // Звук притяжения
                world.playSound(bossLoc, Sound.ENTITY_ENDERMAN_TELEPORT, 1.5f, 1.2f);
            }
        }.runTaskLater(plugin, 30L);
        
        // Регистрируем задачу
        registerTask(pullTask);
    }
    
    private void earthSpikes() {
        List<Player> nearbyPlayers = getNearbyPlayers(20);
        if (nearbyPlayers.isEmpty()) return;
        
        World world = bossEntity.getWorld();
        Location bossLoc = bossEntity.getLocation();
        
        // МОЩНЫЕ предварительные эффекты
        double multiplier = config.getParticlesMultiplier();
        world.spawnParticle(Particle.BLOCK_CRACK, bossLoc, (int)(100 * multiplier), 4, 1, 4, 
            Material.STONE.createBlockData());
        world.spawnParticle(Particle.SMOKE_LARGE, bossLoc, (int)(60 * multiplier), 3, 1, 3, 0.1);
        world.spawnParticle(Particle.REDSTONE, bossLoc, (int)(50 * multiplier), 3, 1, 3,
            new Particle.DustOptions(org.bukkit.Color.GRAY, 2.0f));
        
        // Звуки земли
        world.playSound(bossLoc, Sound.ENTITY_ENDER_DRAGON_GROWL, 2.0f, 0.3f);
        world.playSound(bossLoc, Sound.BLOCK_GRAVEL_BREAK, 2.0f, 0.5f);
        world.playSound(bossLoc, Sound.ENTITY_RAVAGER_ROAR, 1.5f, 0.8f);
        
        // Предупреждение игрокам
        for (Player player : nearbyPlayers) {
            player.sendTitle(ChatColor.GOLD + "⛰️ ЗЕМЛЯНЫЕ ШИПЫ! ⛰️", 
                ChatColor.RED + "Земля восстает под ногами!", 10, 30, 10);
        }
        
        // Через 2 секунды создаем шипы
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : nearbyPlayers) {
                    if (player.isOnline()) {
                        Location spikeLoc = player.getLocation().clone();
                        
                        // Предварительные эффекты в месте шипов
                        world.spawnParticle(Particle.BLOCK_CRACK, spikeLoc, (int)(30 * multiplier), 2, 0.5, 2,
                            Material.COBBLESTONE.createBlockData());
                        world.spawnParticle(Particle.CLOUD, spikeLoc, (int)(20 * multiplier), 1, 0.5, 1, 0.1);
                        world.playSound(spikeLoc, Sound.BLOCK_STONE_BREAK, 2.0f, 0.8f);
                        
                        // Создаем "шип" из блоков
                        for (int y = 0; y < 4; y++) {
                            Location blockLoc = spikeLoc.clone().add(0, y, 0);
                            if (blockLoc.getBlock().getType() == Material.AIR) {
                                blockLoc.getBlock().setType(Material.COBBLESTONE);
                                
                                // Эффекты появления каждого блока
                                world.spawnParticle(Particle.BLOCK_CRACK, blockLoc, (int)(10 * multiplier), 0.5, 0.5, 0.5,
                                    Material.COBBLESTONE.createBlockData());
                                
                                // Удаляем блок через 3 секунды
                                new BukkitRunnable() {
                                    @Override
                                    public void run() {
                                        if (blockLoc.getBlock().getType() == Material.COBBLESTONE) {
                                            // Эффекты исчезновения
                                            world.spawnParticle(Particle.BLOCK_CRACK, blockLoc, (int)(15 * multiplier), 0.5, 0.5, 0.5,
                                                Material.COBBLESTONE.createBlockData());
                                            world.playSound(blockLoc, Sound.BLOCK_STONE_BREAK, 1.0f, 1.2f);
                                            
                                            blockLoc.getBlock().setType(Material.AIR);
                                        }
                                    }
                                }.runTaskLater(Bukkit.getPluginManager().getPlugin("UniqueBoss"), 60L);
                            }
                        }
                        
                        // Урон игроку
                        player.damage(6.0);
                        
                        // Дополнительные эффекты вокруг игрока
                        world.spawnParticle(Particle.CRIT, spikeLoc.clone().add(0, 2, 0), (int)(30 * multiplier), 1, 1, 1, 0.1);
                        world.spawnParticle(Particle.REDSTONE, spikeLoc.clone().add(0, 1, 0), (int)(20 * multiplier), 1, 1, 1,
                            new Particle.DustOptions(org.bukkit.Color.ORANGE, 1.5f));
                        world.playSound(spikeLoc, Sound.BLOCK_ANVIL_LAND, 1.0f, 1.5f);
                    }
                }
            }
        }.runTaskLater(Bukkit.getPluginManager().getPlugin("UniqueBoss"), 40L);
    }
    
    private void blindingFlash() {
        List<Player> nearbyPlayers = getNearbyPlayers(30);
        if (nearbyPlayers.isEmpty()) return;
        
        World world = bossEntity.getWorld();
        Location bossLoc = bossEntity.getLocation();
        
        // МОЩНЫЕ предварительные эффекты
        double multiplier = config.getParticlesMultiplier();
        world.spawnParticle(Particle.FLASH, bossLoc, (int)(50 * multiplier), 3, 3, 3, 0.1);
        world.spawnParticle(Particle.FIREWORKS_SPARK, bossLoc, (int)(80 * multiplier), 3, 3, 3, 0.2);
        world.spawnParticle(Particle.REDSTONE, bossLoc, (int)(60 * multiplier), 3, 3, 3,
            new Particle.DustOptions(org.bukkit.Color.WHITE, 2.0f));
        world.spawnParticle(Particle.END_ROD, bossLoc, (int)(40 * multiplier), 2, 2, 2, 0.1);
        
        // Звуки подготовки
        world.playSound(bossLoc, Sound.BLOCK_BEACON_ACTIVATE, 2.0f, 1.5f);
        world.playSound(bossLoc, Sound.ENTITY_FIREWORK_ROCKET_TWINKLE, 2.0f, 2.0f);
        world.playSound(bossLoc, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.5f, 2.0f);
        
        // Предупреждение
        for (Player player : nearbyPlayers) {
            player.sendTitle(ChatColor.YELLOW + "💥 ОСЛЕПЛЯЮЩАЯ ВСПЫШКА! 💥", 
                ChatColor.GOLD + "Темный Повелитель готовит атаку!", 10, 40, 10);
        }
        
        // Через 2 секунды - МОЩНАЯ вспышка
        new BukkitRunnable() {
            @Override
            public void run() {
                // ЭПИЧЕСКИЕ эффекты вспышки
                world.spawnParticle(Particle.FLASH, bossLoc, (int)(200 * multiplier), 15, 15, 15, 0.3);
                world.spawnParticle(Particle.FIREWORKS_SPARK, bossLoc, (int)(150 * multiplier), 10, 10, 10, 0.5);
                world.spawnParticle(Particle.END_ROD, bossLoc, (int)(100 * multiplier), 8, 8, 8, 0.3);
                world.spawnParticle(Particle.REDSTONE, bossLoc, (int)(80 * multiplier), 8, 8, 8,
                    new Particle.DustOptions(org.bukkit.Color.YELLOW, 3.0f));
                
                // Создаем волну света
                createLightWave(bossLoc, 40.0);
                
                // МОЩНЫЕ звуки вспышки
                world.playSound(bossLoc, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 3.0f, 2.0f);
                world.playSound(bossLoc, Sound.ENTITY_FIREWORK_ROCKET_BLAST_FAR, 2.5f, 2.0f);
                world.playSound(bossLoc, Sound.BLOCK_BEACON_DEACTIVATE, 2.0f, 2.0f);
                world.playSound(bossLoc, Sound.ENTITY_GENERIC_EXPLODE, 2.0f, 1.5f);
                
                // Ослепляем всех ближайших игроков
                for (Player player : nearbyPlayers) {
                    if (player.isOnline() && player.getLocation().distance(bossEntity.getLocation()) <= 35) {
                        player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 100, 2)); // 5 секунд слепоты
                        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 60, 1)); // 3 секунды медлительности
                        player.addPotionEffect(new PotionEffect(PotionEffectType.CONFUSION, 80, 0)); // Дезориентация
                        
                        // Индивидуальные эффекты на игроке
                        Location playerLoc = player.getLocation();
                        world.spawnParticle(Particle.FLASH, playerLoc, (int)(30 * multiplier), 2, 2, 2, 0.1);
                        world.spawnParticle(Particle.FIREWORKS_SPARK, playerLoc, (int)(20 * multiplier), 1, 1, 1, 0.1);
                        
                        player.sendTitle(ChatColor.WHITE + "ОСЛЕПЛЕНЫ!", 
                            ChatColor.GRAY + "Вы ничего не видите!", 10, 80, 20);
                        
                        // Звук для каждого игрока
                        world.playSound(playerLoc, Sound.ENTITY_FIREWORK_ROCKET_TWINKLE_FAR, 1.5f, 2.0f);
                    }
                }
            }
        }.runTaskLater(Bukkit.getPluginManager().getPlugin("UniqueBoss"), 40L);
    }
    
    private void createLightWave(Location center, double maxRadius) {
        World world = center.getWorld();
        Plugin plugin = Bukkit.getPluginManager().getPlugin("UniqueBoss");
        
        new BukkitRunnable() {
            double radius = 0;
            int ticks = 0;
            
            @Override
            public void run() {
                if (ticks > 30 || radius > maxRadius) { // 1.5 секунды или maxRadius блоков
                    this.cancel();
                    return;
                }
                
                int points = (int) (radius * 3);
                for (int i = 0; i < points; i++) {
                    double angle = 2 * Math.PI * i / points;
                    double x = center.getX() + radius * Math.cos(angle);
                    double z = center.getZ() + radius * Math.sin(angle);
                    Location particleLoc = new Location(world, x, center.getY() + 0.5, z);
                    
                    world.spawnParticle(Particle.FLASH, particleLoc, 1, 0, 0, 0, 0);
                    world.spawnParticle(Particle.REDSTONE, particleLoc, 1, 0, 0, 0, 
                        new Particle.DustOptions(org.bukkit.Color.WHITE, 1.5f));
                    
                    // Дополнительные эффекты каждые несколько точек
                    if (i % 5 == 0) {
                        world.spawnParticle(Particle.FIREWORKS_SPARK, particleLoc, 2, 0.1, 0.1, 0.1, 0.05);
                    }
                }
                
                radius += 1.5;
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }
    
    // Способности босса
    private void fireballBarrage() {
        World world = bossEntity.getWorld();
        List<Player> nearbyPlayers = getNearbyPlayers(20);
        Location bossLoc = bossEntity.getLocation();
        
        // КРАСИВЫЕ предварительные эффекты
        world.spawnParticle(Particle.FLAME, bossLoc, 100, 3, 3, 3, 0.2);
        world.spawnParticle(Particle.SMOKE_LARGE, bossLoc, 50, 2, 2, 2, 0.1);
        world.spawnParticle(Particle.LAVA, bossLoc, 30, 2, 2, 2, 0.1);
        world.playSound(bossLoc, Sound.ENTITY_GHAST_WARN, 2.0f, 0.5f);
        
        // ТОЛЬКО title предупреждение игрокам
        for (Player player : nearbyPlayers) {
            player.sendTitle("", ChatColor.RED + "🔥 ОГНЕННЫЙ ЗАЛП! 🔥", 5, 20, 5);
        }
        
        // Через полсекунды стреляем
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : nearbyPlayers) {
                    if (player.isOnline()) {
                        Vector direction = player.getLocation().toVector().subtract(bossEntity.getLocation().toVector()).normalize();
                        Fireball fireball = world.spawn(bossEntity.getEyeLocation(), Fireball.class);
                        fireball.setDirection(direction);
                        fireball.setYield(2.0f);
                        
                        // Эффекты для каждого файрбола
                        world.spawnParticle(Particle.FLAME, bossEntity.getEyeLocation(), 10, 0.5, 0.5, 0.5, 0.1);
                    }
                }
                
                world.playSound(bossLoc, Sound.ENTITY_GHAST_SHOOT, 2.0f, 0.8f);
                world.playSound(bossLoc, Sound.ENTITY_BLAZE_SHOOT, 1.5f, 1.0f);
            }
        }.runTaskLater(Bukkit.getPluginManager().getPlugin("UniqueBoss"), 10L);
    }
    
    private void summonMinions() {
        World world = bossEntity.getWorld();
        Location bossLoc = bossEntity.getLocation();
        
        // СИСТЕМА СЛОЖНОСТИ: Применяем модификатор количества миньонов
        int baseMinions = 3;
        int actualMinions = Math.max(1, (int)(baseMinions * config.getDifficultyMinionsCountMultiplier()));
        
        // ЭПИЧЕСКИЕ эффекты призыва
        double multiplier = config.getParticlesMultiplier();
        world.spawnParticle(Particle.SMOKE_LARGE, bossLoc, (int)(80 * multiplier), 4, 2, 4, 0.1);
        world.spawnParticle(Particle.SPELL_WITCH, bossLoc, (int)(60 * multiplier), 3, 2, 3, 0.2);
        world.spawnParticle(Particle.REDSTONE, bossLoc, (int)(40 * multiplier), 3, 2, 3, 
            new Particle.DustOptions(org.bukkit.Color.MAROON, 2.0f));
        
        // Звуки призыва
        world.playSound(bossLoc, Sound.ENTITY_WITHER_AMBIENT, 2.0f, 0.5f);
        world.playSound(bossLoc, Sound.ENTITY_ZOMBIE_VILLAGER_CONVERTED, 1.5f, 0.3f);
        
        for (int i = 0; i < actualMinions; i++) {
            final int index = i;
            new BukkitRunnable() {
                @Override
                public void run() {
                    Location spawnLoc = bossLoc.clone().add(
                        random.nextDouble() * 6 - 3,
                        1,
                        random.nextDouble() * 6 - 3
                    );
                    
                    // Эффекты в точке призыва
                    world.spawnParticle(Particle.SMOKE_LARGE, spawnLoc, (int)(20 * multiplier), 1, 1, 1, 0.1);
                    world.spawnParticle(Particle.REDSTONE, spawnLoc, (int)(15 * multiplier), 1, 1, 1,
                        new Particle.DustOptions(org.bukkit.Color.BLACK, 1.5f));
                    world.createExplosion(spawnLoc, 1.5f, false, false);
                    
                    Skeleton skeleton = (Skeleton) world.spawnEntity(spawnLoc, EntityType.SKELETON);
                    skeleton.setCustomName(ChatColor.RED + "💀 Прислужник Темного Повелителя 💀");
                    skeleton.setCustomNameVisible(true);
                    skeleton.getEquipment().setHelmet(new ItemStack(Material.IRON_HELMET));
                    skeleton.getEquipment().setItemInMainHand(new ItemStack(Material.BOW));
                    
                    // Добавляем эффекты прислужникам
                    skeleton.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 1));
                    skeleton.addPotionEffect(new PotionEffect(PotionEffectType.INCREASE_DAMAGE, Integer.MAX_VALUE, 0));
                    skeleton.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, Integer.MAX_VALUE, 0));
                    
                    world.playSound(spawnLoc, Sound.ENTITY_SKELETON_AMBIENT, 1.5f, 0.5f);
                }
            }.runTaskLater(Bukkit.getPluginManager().getPlugin("UniqueBoss"), index * 10L);
        }
        
        // ТОЛЬКО title сообщение игрокам
        List<Player> nearbyPlayers = getNearbyPlayers(50);
        for (Player player : nearbyPlayers) {
            player.sendTitle("", ChatColor.RED + "💀 ПРИЗЫВ НЕЖИТИ! 💀", 5, 30, 5);
        }
    }
    
    private void teleportStrike() {
        List<Player> nearbyPlayers = getNearbyPlayers(30);
        if (nearbyPlayers.isEmpty()) return;
        
        Player target = nearbyPlayers.get(random.nextInt(nearbyPlayers.size()));
        Location oldLoc = bossEntity.getLocation().clone();
        Location targetLoc = target.getLocation().clone().add(0, 2, 0);
        World world = bossEntity.getWorld();
        
        // ПРЕДУПРЕЖДЕНИЕ ЦЕЛИ
        target.sendTitle(ChatColor.DARK_PURPLE + "⚡ ТЕЛЕПОРТ АТАКА! ⚡", 
            ChatColor.RED + "Босс телепортируется к вам!", 10, 30, 10);
        
        // Эффекты в старом месте
        world.spawnParticle(Particle.PORTAL, oldLoc, 100, 2, 2, 2, 0.3);
        world.spawnParticle(Particle.SPELL_WITCH, oldLoc, 50, 2, 2, 2, 0.2);
        world.spawnParticle(Particle.SMOKE_LARGE, oldLoc, 40, 2, 2, 2, 0.1);
        world.playSound(oldLoc, Sound.ENTITY_ENDERMAN_TELEPORT, 2.0f, 0.8f);
        world.playSound(oldLoc, Sound.ENTITY_ENDER_DRAGON_FLAP, 1.5f, 1.2f);
        
        // Телепортация через секунду
        new BukkitRunnable() {
            @Override
            public void run() {
                // Телепортация
                bossEntity.teleport(targetLoc);
                
                // МОЩНЫЕ эффекты появления
                world.spawnParticle(Particle.EXPLOSION_LARGE, targetLoc, 20, 3, 3, 3, 0.1);
                world.spawnParticle(Particle.PORTAL, targetLoc, 80, 3, 3, 3, 0.2);
                world.spawnParticle(Particle.ELECTRIC_SPARK, targetLoc, 60, 2, 2, 2, 0.1);
                world.spawnParticle(Particle.REDSTONE, targetLoc, 50, 3, 3, 3,
                    new Particle.DustOptions(org.bukkit.Color.PURPLE, 2.0f));
                
                // Взрыв и звуки
                world.createExplosion(targetLoc, 4.0f, false, false);
                world.playSound(targetLoc, Sound.ENTITY_GENERIC_EXPLODE, 2.0f, 1.0f);
                world.playSound(targetLoc, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.5f, 1.2f);
                world.playSound(targetLoc, Sound.ENTITY_WITHER_BREAK_BLOCK, 2.0f, 0.8f);
                
                // Молния для драматизма
                world.strikeLightningEffect(targetLoc);
            }
        }.runTaskLater(Bukkit.getPluginManager().getPlugin("UniqueBoss"), 20L);
    }
    
    private void magicMissiles() {
        World world = bossEntity.getWorld();
        List<Player> nearbyPlayers = getNearbyPlayers(25);
        Location bossLoc = bossEntity.getLocation();
        
        // Подготовительные эффекты
        world.spawnParticle(Particle.SPELL_WITCH, bossLoc, 80, 3, 3, 3, 0.3);
        world.spawnParticle(Particle.ENCHANTMENT_TABLE, bossLoc, 60, 2, 2, 2, 0.2);
        world.spawnParticle(Particle.REDSTONE, bossLoc, 40, 3, 3, 3,
            new Particle.DustOptions(org.bukkit.Color.BLUE, 2.0f));
        
        world.playSound(bossLoc, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 2.0f, 0.5f);
        world.playSound(bossLoc, Sound.ENTITY_EVOKER_PREPARE_ATTACK, 1.5f, 1.0f);
        
        // Предупреждение
        for (Player player : nearbyPlayers) {
            player.sendTitle("", ChatColor.BLUE + "🌟 МАГИЧЕСКАЯ АТАКА! 🌟", 5, 20, 5);
        }
        
        // Запуск ракет через полсекунды
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : nearbyPlayers) {
                    if (player.isOnline()) {
                        // Создаем энергетический снаряд
                        DragonFireball missile = world.spawn(bossEntity.getEyeLocation(), DragonFireball.class);
                        Vector direction = player.getLocation().toVector().subtract(bossEntity.getLocation().toVector()).normalize();
                        missile.setDirection(direction);
                        
                        // Эффекты запуска каждой ракеты
                        world.spawnParticle(Particle.SPELL_WITCH, bossEntity.getEyeLocation(), 15, 0.5, 0.5, 0.5, 0.1);
                        world.spawnParticle(Particle.REDSTONE, bossEntity.getEyeLocation(), 10, 0.3, 0.3, 0.3,
                            new Particle.DustOptions(org.bukkit.Color.BLUE, 1.5f));
                    }
                }
                
                world.playSound(bossLoc, Sound.ENTITY_BLAZE_SHOOT, 2.0f, 1.2f);
                world.playSound(bossLoc, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1.5f, 1.5f);
            }
        }.runTaskLater(Bukkit.getPluginManager().getPlugin("UniqueBoss"), 10L);
    }
    
    private void meteorStorm() {
        World world = bossEntity.getWorld();
        List<Player> nearbyPlayers = getNearbyPlayers(30);
        Location bossLoc = bossEntity.getLocation();
        
        // ЭПИЧЕСКИЕ подготовительные эффекты
        world.spawnParticle(Particle.FLAME, bossLoc, 150, 5, 5, 5, 0.3);
        world.spawnParticle(Particle.LAVA, bossLoc, 100, 4, 4, 4, 0.2);
        world.spawnParticle(Particle.SMOKE_LARGE, bossLoc, 120, 4, 4, 4, 0.2);
        world.spawnParticle(Particle.REDSTONE, bossLoc, 80, 4, 4, 4,
            new Particle.DustOptions(org.bukkit.Color.ORANGE, 2.5f));
        
        // Мощные звуки предупреждения
        world.playSound(bossLoc, Sound.ENTITY_WITHER_SHOOT, 2.0f, 0.3f);
        world.playSound(bossLoc, Sound.ENTITY_ENDER_DRAGON_GROWL, 2.0f, 0.5f);
        world.playSound(bossLoc, Sound.AMBIENT_CAVE, 2.0f, 0.3f);
        
        // Сообщение только ближайшим игрокам
        for (Player player : nearbyPlayers) {
            player.sendTitle(ChatColor.DARK_RED + "" + ChatColor.BOLD + "☄ МЕТЕОРИТНЫЙ ДОЖДЬ! ☄", 
                ChatColor.GOLD + "Ищите укрытие!", 10, 60, 10);
        }
        
        // Создаем 12 метеоритов с интервалом
        for (int i = 0; i < 12; i++) {
            final int meteorIndex = i;
            new BukkitRunnable() {
                @Override
                public void run() {
                    for (Player player : nearbyPlayers) {
                        if (player.isOnline()) {
                            Location meteorLoc = player.getLocation().clone().add(
                                random.nextDouble() * 12 - 6,
                                25,
                                random.nextDouble() * 12 - 6
                            );
                            
                            // Предупредительные эффекты в небе
                            world.spawnParticle(Particle.FLAME, meteorLoc, 30, 2, 2, 2, 0.1);
                            world.spawnParticle(Particle.SMOKE_LARGE, meteorLoc, 20, 1, 1, 1, 0.05);
                            
                            Fireball meteor = world.spawn(meteorLoc, Fireball.class);
                            meteor.setDirection(new Vector(0, -1, 0));
                            meteor.setYield(3.5f);
                            meteor.setIsIncendiary(false); // Отключаем создание огня
                            
                            // Звук падающего метеорита
                            world.playSound(meteorLoc, Sound.ENTITY_FIREWORK_ROCKET_TWINKLE, 1.0f, 0.5f);
                        }
                    }
                    
                    // Звук каждого запуска метеорита
                    if (meteorIndex % 3 == 0) {
                        world.playSound(bossLoc, Sound.ENTITY_WITHER_SHOOT, 1.5f, 0.8f);
                    }
                }
            }.runTaskLater(Bukkit.getPluginManager().getPlugin("UniqueBoss"), i * 8L);
        }
    }
    
    private void soulScream() {
        List<Player> nearbyPlayers = getNearbyPlayers(40);
        if (nearbyPlayers.isEmpty()) return;
        
        World world = bossEntity.getWorld();
        Location bossLoc = bossEntity.getLocation();
        
        // МОЩНЫЕ предварительные эффекты
        world.spawnParticle(Particle.SOUL, bossLoc, 200, 5, 5, 5, 0.3);
        world.spawnParticle(Particle.SOUL_FIRE_FLAME, bossLoc, 150, 4, 4, 4, 0.2);
        world.spawnParticle(Particle.SMOKE_LARGE, bossLoc, 100, 4, 4, 4, 0.2);
        world.spawnParticle(Particle.REDSTONE, bossLoc, 80, 4, 4, 4,
            new Particle.DustOptions(org.bukkit.Color.fromRGB(50, 0, 50), 2.5f));
        
        // Звуки подготовки
        world.playSound(bossLoc, Sound.ENTITY_WITHER_AMBIENT, 3.0f, 0.3f);
        world.playSound(bossLoc, Sound.ENTITY_VEX_AMBIENT, 2.0f, 0.5f);
        world.playSound(bossLoc, Sound.ENTITY_PHANTOM_AMBIENT, 2.5f, 0.3f);
        world.playSound(bossLoc, Sound.AMBIENT_SOUL_SAND_VALLEY_MOOD, 2.0f, 0.8f);
        
        // Предупреждение игрокам
        for (Player player : nearbyPlayers) {
            player.sendTitle(ChatColor.DARK_PURPLE + "" + ChatColor.BOLD + "💀 ДУШЕРАЗДИРАЮЩИЙ КРИК! 💀", 
                ChatColor.RED + "Готовится ужасающая атака!", 10, 60, 20);
        }
        
        // Через 3 секунды - МОЩНЫЙ крик
        new BukkitRunnable() {
            @Override
            public void run() {
                // ЭПИЧЕСКИЕ эффекты крика
                world.spawnParticle(Particle.SOUL, bossLoc, 500, 20, 20, 20, 0.5);
                world.spawnParticle(Particle.SOUL_FIRE_FLAME, bossLoc, 300, 15, 15, 15, 0.4);
                world.spawnParticle(Particle.SMOKE_LARGE, bossLoc, 200, 12, 12, 12, 0.3);
                world.spawnParticle(Particle.SPELL_WITCH, bossLoc, 150, 10, 10, 10, 0.3);
                world.spawnParticle(Particle.REDSTONE, bossLoc, 100, 10, 10, 10,
                    new Particle.DustOptions(org.bukkit.Color.fromRGB(100, 0, 100), 3.0f));
                
                // Создаем душевные волны
                createSoulWaves(bossLoc, 50.0);
                
                // МОЩНЕЙШИЕ звуки крика
                world.playSound(bossLoc, Sound.ENTITY_WITHER_DEATH, 3.0f, 0.1f);
                world.playSound(bossLoc, Sound.ENTITY_ENDER_DRAGON_DEATH, 2.5f, 0.3f);
                world.playSound(bossLoc, Sound.ENTITY_VEX_DEATH, 3.0f, 0.2f);
                world.playSound(bossLoc, Sound.ENTITY_PHANTOM_DEATH, 2.0f, 0.4f);
                world.playSound(bossLoc, Sound.AMBIENT_SOUL_SAND_VALLEY_MOOD, 3.0f, 0.5f);
                
                // Воздействие на всех ближайших игроков
                for (Player player : nearbyPlayers) {
                    if (player.isOnline() && player.getLocation().distance(bossEntity.getLocation()) <= 45) {
                        double distance = player.getLocation().distance(bossEntity.getLocation());
                        
                        // Урон зависит от расстояния (ближе = больше урона)
                        double damage = Math.max(2.0, 12.0 - (distance * 0.3));
                        player.damage(damage);
                        
                        // Негативные эффекты
                        player.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 100, 1)); // 5 секунд иссушения
                        player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 120, 1)); // 6 секунд слабости
                        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 80, 2)); // 4 секунды сильного замедления
                        player.addPotionEffect(new PotionEffect(PotionEffectType.CONFUSION, 100, 1)); // 5 секунд дезориентации
                        player.addPotionEffect(new PotionEffect(PotionEffectType.HUNGER, 200, 2)); // 10 секунд голода
                        
                        // Индивидуальные эффекты на игроке
                        Location playerLoc = player.getLocation();
                        world.spawnParticle(Particle.SOUL, playerLoc, 50, 2, 2, 2, 0.1);
                        world.spawnParticle(Particle.SOUL_FIRE_FLAME, playerLoc, 30, 1, 2, 1, 0.1);
                        world.spawnParticle(Particle.SMOKE_LARGE, playerLoc, 20, 1, 1, 1, 0.05);
                        
                        // Отбрасываем игрока от босса
                        Vector knockback = player.getLocation().toVector().subtract(bossEntity.getLocation().toVector()).normalize();
                        knockback.multiply(2.0); // Сила отбрасывания
                        knockback.setY(0.8); // Поднимаем вверх
                        player.setVelocity(knockback);
                        
                        player.sendTitle(ChatColor.DARK_RED + "💀 ДУШЕВНАЯ БОЛЬ! 💀", 
                            ChatColor.GRAY + "Крик разрывает вашу душу!", 10, 80, 20);
                        
                        // Звук для каждого игрока
                        world.playSound(playerLoc, Sound.ENTITY_VEX_HURT, 2.0f, 0.5f);
                        world.playSound(playerLoc, Sound.ENTITY_PHANTOM_HURT, 1.5f, 0.8f);
                    }
                }
            }
        }.runTaskLater(Bukkit.getPluginManager().getPlugin("UniqueBoss"), 60L); // 3 секунды задержки
    }
    
    private void createSoulWaves(Location center, double maxRadius) {
        World world = center.getWorld();
        Plugin plugin = Bukkit.getPluginManager().getPlugin("UniqueBoss");
        
        // Создаем 3 волны с разной скоростью
        for (int wave = 1; wave <= 3; wave++) {
            final int waveNumber = wave;
            final double waveSpeed = 1.0 + (wave * 0.5); // Каждая волна быстрее предыдущей
            
            new BukkitRunnable() {
                double radius = 0;
                int ticks = 0;
                
                @Override
                public void run() {
                    if (ticks > 60 || radius > maxRadius) { // 3 секунды или maxRadius блоков
                        this.cancel();
                        return;
                    }
                    
                    int points = (int) (radius * 4);
                    for (int i = 0; i < points; i++) {
                        double angle = 2 * Math.PI * i / points;
                        double x = center.getX() + radius * Math.cos(angle);
                        double z = center.getZ() + radius * Math.sin(angle);
                        double y = center.getY() + 0.5 + Math.sin(ticks * 0.2) * 0.3; // Волнообразное движение
                        
                        Location particleLoc = new Location(world, x, y, z);
                        
                        // Разные частицы для разных волн
                        switch (waveNumber) {
                            case 1:
                                world.spawnParticle(Particle.SOUL, particleLoc, 1, 0, 0, 0, 0);
                                break;
                            case 2:
                                world.spawnParticle(Particle.SOUL_FIRE_FLAME, particleLoc, 1, 0, 0, 0, 0);
                                break;
                            case 3:
                                world.spawnParticle(Particle.REDSTONE, particleLoc, 1, 0, 0, 0, 
                                    new Particle.DustOptions(org.bukkit.Color.fromRGB(75, 0, 75), 1.5f));
                                break;
                        }
                    }
                    
                    radius += waveSpeed;
                    ticks++;
                }
            }.runTaskTimer(plugin, waveNumber * 5L, 1L); // Небольшая задержка между волнами
        }
    }
    
    // 💀 СМЕРТЕЛЬНЫЙ ВЗГЛЯД ТЕМНОГО ПОВЕЛИТЕЛЯ - уникальная атака 5 уровня
    private void deathGaze() {
        List<Player> nearbyPlayers = getNearbyPlayers(30.0);
        if (nearbyPlayers.isEmpty()) return;
        
        World world = bossEntity.getWorld();
        Location bossLoc = bossEntity.getLocation();
        double multiplier = config.getParticlesMultiplier();
        
        // ПРЕДУПРЕЖДЕНИЕ ИГРОКАМ
        for (Player player : nearbyPlayers) {
            player.sendTitle(ChatColor.DARK_RED + "💀 СМЕРТЕЛЬНЫЙ ВЗГЛЯД! 💀", 
                ChatColor.RED + "Темный Повелитель направил на вас взгляд смерти!", 10, 60, 10);
            player.playSound(player.getLocation(), Sound.AMBIENT_SOUL_SAND_VALLEY_MOOD, 2.0f, 0.3f);
        }
        
        // Эффекты подготовки
        world.spawnParticle(Particle.SOUL, bossLoc.clone().add(0, 2, 0), 
            (int)(200 * multiplier), 5, 3, 5, 0.3);
        world.spawnParticle(Particle.REDSTONE, bossLoc, (int)(100 * multiplier), 3, 3, 3,
            new Particle.DustOptions(org.bukkit.Color.BLACK, 3.0f));
        
        world.playSound(bossLoc, Sound.ENTITY_WITHER_AMBIENT, 3.0f, 0.1f);
        world.playSound(bossLoc, Sound.AMBIENT_SOUL_SAND_VALLEY_ADDITIONS, 2.0f, 0.5f);
        
        // Через 3 секунды наносим урон всем в прямой видимости
        Plugin plugin = Bukkit.getPluginManager().getPlugin("UniqueBoss");
        BukkitTask gazeTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (bossEntity == null || bossEntity.isDead()) {
                    cancel();
                    return;
                }
                
                for (Player player : nearbyPlayers) {
                    if (!player.isOnline()) continue;
                    
                    Location playerLoc = player.getLocation();
                    // Проверяем прямую видимость
                    if (bossLoc.distance(playerLoc) <= 30.0 && 
                        world.rayTraceBlocks(bossLoc, playerLoc.toVector().subtract(bossLoc.toVector()), 30.0) == null) {
                        
                        // СМЕРТЕЛЬНЫЙ УРОН (учитывает сложность)
                        double baseDamage = 12.0;
                        double damage = baseDamage * config.getDifficultyDamageMultiplier();
                        player.damage(damage);
                        
                        // Накладываем негативные эффекты
                        player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 200, 2));
                        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 100, 1));
                        player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 60, 0));
                        
                        // Луч смерти к игроку
                        createDeathRay(bossLoc, playerLoc);
                        
                        player.sendMessage(ChatColor.DARK_RED + "💀 Взгляд Темного Повелителя пронзил вашу душу!");
                        player.playSound(playerLoc, Sound.ENTITY_WITHER_HURT, 2.0f, 0.3f);
                    }
                }
                
                // Финальный взрыв темной энергии
                world.createExplosion(bossLoc, 6.0f, false, false);
                world.spawnParticle(Particle.SOUL_FIRE_FLAME, bossLoc, (int)(150 * multiplier), 6, 4, 6, 0.3);
            }
        }.runTaskLater(plugin, 60L); // 3 секунды
        
        registerTask(gazeTask);
    }
    
    // ⚡ ЦЕПНАЯ МОЛНИЯ ХАОСА
    private void chaosLightning() {
        List<Player> nearbyPlayers = getNearbyPlayers(35.0);
        if (nearbyPlayers.isEmpty()) return;
        
        World world = bossEntity.getWorld();
        Location bossLoc = bossEntity.getLocation();
        double multiplier = config.getParticlesMultiplier();
        
        // Предупреждение
        for (Player player : nearbyPlayers) {
            player.sendTitle(ChatColor.YELLOW + "⚡ ЦЕПНАЯ МОЛНИЯ! ⚡", 
                ChatColor.RED + "Хаос электричества заполняет воздух!", 10, 40, 10);
        }
        
        world.playSound(bossLoc, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 3.0f, 0.8f);
        world.spawnParticle(Particle.ELECTRIC_SPARK, bossLoc.clone().add(0, 5, 0), 
            (int)(300 * multiplier), 8, 8, 8, 0.5);
        
        // Создаем цепную молнию через 1.5 секунды
        Plugin plugin = Bukkit.getPluginManager().getPlugin("UniqueBoss");
        BukkitTask lightningTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (bossEntity == null || bossEntity.isDead()) {
                    cancel();
                    return;
                }
                
                // Бьем молнией в случайного игрока
                if (!nearbyPlayers.isEmpty()) {
                    Player firstTarget = nearbyPlayers.get(random.nextInt(nearbyPlayers.size()));
                    Location currentLoc = firstTarget.getLocation();
                    
                    // Цепь из 5 молний
                    for (int i = 0; i < 5; i++) {
                        world.strikeLightning(currentLoc);
                        
                        // Урон и эффекты
                        for (Entity entity : world.getNearbyEntities(currentLoc, 4, 4, 4)) {
                            if (entity instanceof Player) {
                                Player player = (Player) entity;
                                double damage = 8.0 * config.getDifficultyDamageMultiplier();
                                player.damage(damage);
                                player.addPotionEffect(new PotionEffect(PotionEffectType.CONFUSION, 100, 1));
                                
                                player.sendMessage(ChatColor.YELLOW + "⚡ Вас поразила цепная молния хаоса!");
                            }
                        }
                        
                        // Ищем следующую цель рядом с текущей
                        Player nextTarget = null;
                        double minDistance = Double.MAX_VALUE;
                        for (Player player : nearbyPlayers) {
                            if (player != firstTarget && player.getLocation().distance(currentLoc) < 15.0) {
                                double distance = player.getLocation().distance(currentLoc);
                                if (distance < minDistance) {
                                    minDistance = distance;
                                    nextTarget = player;
                                }
                            }
                        }
                        
                        if (nextTarget != null) {
                            currentLoc = nextTarget.getLocation();
                        } else {
                            // Если нет цели рядом, бьем в случайное место
                            currentLoc = currentLoc.clone().add(
                                random.nextDouble() * 10 - 5,
                                0,
                                random.nextDouble() * 10 - 5
                            );
                        }
                        
                        // Пауза между ударами
                        try {
                            Thread.sleep(300);
                        } catch (InterruptedException e) {
                            break;
                        }
                    }
                }
            }
        }.runTaskLater(plugin, 30L);
        
        registerTask(lightningTask);
    }
    
    // 🌀 ВИХРЬ ТЬМЫ
    private void darknessVortex() {
        List<Player> nearbyPlayers = getNearbyPlayers(40.0);
        if (nearbyPlayers.isEmpty()) return;
        
        World world = bossEntity.getWorld();
        Location centerLoc = bossEntity.getLocation().clone().add(0, 10, 0);
        double multiplier = config.getParticlesMultiplier();
        
        // Предупреждение
        for (Player player : nearbyPlayers) {
            player.sendTitle(ChatColor.DARK_PURPLE + "🌀 ВИХРЬ ТЬМЫ! 🌀", 
                ChatColor.RED + "Темный вихрь засасывает все живое!", 10, 80, 10);
        }
        
        world.playSound(centerLoc, Sound.ENTITY_WITHER_AMBIENT, 3.0f, 0.1f);
        world.playSound(centerLoc, Sound.AMBIENT_SOUL_SAND_VALLEY_LOOP, 2.0f, 0.3f);
        
        // Создаем вихрь на 8 секунд
        Plugin plugin = Bukkit.getPluginManager().getPlugin("UniqueBoss");
        BukkitTask vortexTask = new BukkitRunnable() {
            private int ticks = 0;
            
            @Override
            public void run() {
                if (ticks > 160 || bossEntity == null || bossEntity.isDead()) { // 8 секунд
                    cancel();
                    return;
                }
                
                // Вращающиеся частицы вихря
                int particles = (int)(200 * multiplier);
                for (int i = 0; i < particles; i++) {
                    double angle = (ticks * 0.3 + i * 360.0 / particles) * Math.PI / 180.0;
                    double radius = 15.0 - (ticks * 0.05); // Сужающийся вихрь
                    if (radius < 3.0) radius = 3.0;
                    
                    double x = centerLoc.getX() + radius * Math.cos(angle);
                    double z = centerLoc.getZ() + radius * Math.sin(angle);
                    double y = centerLoc.getY() - ticks * 0.1; // Опускающийся вихрь
                    
                    Location particleLoc = new Location(world, x, y, z);
                    world.spawnParticle(Particle.SOUL, particleLoc, 1, 0, 0, 0, 0);
                    world.spawnParticle(Particle.SMOKE_LARGE, particleLoc, 1, 0, 0, 0, 0);
                }
                
                // Притягиваем игроков к центру и наносим урон
                for (Player player : nearbyPlayers) {
                    if (!player.isOnline()) continue;
                    
                    Location playerLoc = player.getLocation();
                    double distance = playerLoc.distance(centerLoc);
                    
                    if (distance <= 20.0) {
                        // Притягиваем к центру
                        Vector direction = centerLoc.toVector().subtract(playerLoc.toVector()).normalize();
                        Vector pullForce = direction.multiply(0.8);
                        player.setVelocity(pullForce);
                        
                        // Урон каждые 20 тиков (1 секунда)
                        if (ticks % 20 == 0) {
                            double damage = 3.0 * config.getDifficultyDamageMultiplier();
                            player.damage(damage);
                            
                            // Негативные эффекты в центре вихря
                            if (distance <= 5.0) {
                                player.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 40, 1));
                                player.addPotionEffect(new PotionEffect(PotionEffectType.CONFUSION, 60, 2));
                            }
                        }
                        
                        // Эффекты вокруг игрока
                        world.spawnParticle(Particle.REDSTONE, playerLoc.clone().add(0, 1, 0), 
                            (int)(10 * multiplier), 1, 1, 1, 
                            new Particle.DustOptions(org.bukkit.Color.PURPLE, 1.5f));
                    }
                }
                
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
        
        registerTask(vortexTask);
    }
    
    // Вспомогательный метод для создания луча смерти
    private void createDeathRay(Location from, Location to) {
        World world = from.getWorld();
        Vector direction = to.toVector().subtract(from.toVector()).normalize();
        double distance = from.distance(to);
        double multiplier = config.getParticlesMultiplier();
        
        // Создаем луч из частиц
        for (double d = 0; d < distance; d += 0.5) {
            Location rayLoc = from.clone().add(direction.clone().multiply(d));
            world.spawnParticle(Particle.REDSTONE, rayLoc, (int)(5 * multiplier), 0.1, 0.1, 0.1,
                new Particle.DustOptions(org.bukkit.Color.BLACK, 2.0f));
            world.spawnParticle(Particle.SOUL, rayLoc, (int)(3 * multiplier), 0.1, 0.1, 0.1, 0);
        }
        
        world.playSound(from, Sound.ENTITY_WITHER_SHOOT, 1.5f, 0.3f);
    }
    
    private List<Player> getNearbyPlayers(double radius) {
        List<Player> nearbyPlayers = new ArrayList<>();
        for (Entity entity : bossEntity.getNearbyEntities(radius, radius, radius)) {
            if (entity instanceof Player) {
                Player player = (Player) entity;
                
                // КРИТИЧЕСКИ ВАЖНО: Исключаем игроков в режиме наблюдателя и творческом режиме
                if (player.getGameMode() == org.bukkit.GameMode.SPECTATOR && !config.shouldAttackSpectators()) {
                    continue; // Пропускаем игроков в режиме наблюдателя (если настройка отключена)
                }
                
                if (player.getGameMode() == org.bukkit.GameMode.CREATIVE && !config.shouldAttackCreative()) {
                    continue; // Пропускаем игроков в творческом режиме (если настройка отключена)
                }
                
                // Исключаем невидимых игроков (vanish плагины)
                if (config.shouldIgnoreVanished() && !player.getCanPickupItems() && player.isInvisible()) {
                    continue; // Возможно игрок в vanish
                }
                
                nearbyPlayers.add(player);
            }
        }
        return nearbyPlayers;
    }
    
    private void updateBossBarDisplay() {
        double currentHealth = bossEntity.getHealth();
        double maxHealth = bossEntity.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
        double healthPercentage = currentHealth / maxHealth;
        
        bossBar.setProgress(healthPercentage);
        
        String phaseName = getPhaseDisplayName();
        String title = ChatColor.DARK_RED + "⚡ ТЕМНЫЙ ПОВЕЛИТЕЛЬ ⚡ " + phaseName;
        bossBar.setTitle(title);
    }
    
    private String getPhaseDisplayName() {
        switch (currentPhase) {
            case 1:
                return ChatColor.GREEN + "[ФАЗА 1]";
            case 2:
                return ChatColor.YELLOW + "[ФАЗА 2]";
            case 3:
                return ChatColor.RED + "[ФИНАЛ]";
            default:
                return "";
        }
    }
    
    private void handleBossDeath() {
        if (!isAlive) return; // Предотвращаем двойное выполнение
        
        Plugin plugin = Bukkit.getPluginManager().getPlugin("UniqueBoss");
        plugin.getLogger().info("💀 СМЕРТЬ БОССА: Начинаем обработку...");
        
        // КРИТИЧЕСКИ ВАЖНО: Отменяем все активные задачи СРАЗУ
        cancelAllTasks();
        
        isAlive = false;
        World world = spawnLocation.getWorld();
        
        // PDC система автоматически сохранит данные босса
        
        // Убираем боссбар
        bossBar.removeAll();
        
        // Сохраняем место смерти босса
        Location deathLoc = bossEntity.getLocation().clone();
        
        // КРИТИЧЕСКИ ВАЖНО: Принудительно удаляем сущность босса
        if (bossEntity != null && !bossEntity.isDead()) {
            bossEntity.remove();
        }
        
        // Объявляем о победе только ближайшим
        List<Player> nearbyPlayers = getNearbyPlayers(100);
        for (Player player : nearbyPlayers) {
            player.sendTitle(ChatColor.GOLD + "🎉 ТЕМНЫЙ ПОВЕЛИТЕЛЬ ПОВЕРЖЕН! 🎉", 
                ChatColor.GREEN + "Приготовьтесь получить награды!", 20, 80, 20);
        }
        
        // НАЧИНАЕМ ЭПИЧЕСКУЮ АНИМАЦИЮ СМЕРТИ (если включена)
        if (config.isDeathAnimationEnabled()) {
            startDeathAnimation(deathLoc, world);
        }
        
        // ДРОПАЕМ НАГРАДЫ И ОПЫТ ЧЕРЕЗ НАСТРОЕННОЕ ВРЕМЯ (после анимации)
        int delayTicks = config.isDeathAnimationEnabled() ? config.getDeathAnimationDuration() : 40; // 2 секунды минимум
        new BukkitRunnable() {
            @Override
            public void run() {
                // Финальное предупреждение о дропе
                List<Player> currentNearbyPlayers = getNearbyPlayers(100);
                for (Player player : currentNearbyPlayers) {
                    player.sendTitle(ChatColor.YELLOW + "🎁 ПОЛУЧЕНИЕ НАГРАД! 🎁", 
                        ChatColor.GREEN + "Награды Темного Повелителя материализуются!", 10, 60, 10);
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 2.0f, 1.5f);
                }
                
                // Теперь безопасно дропаем награды
                dropRewards(deathLoc);
                dropExperience(deathLoc);
            }
        }.runTaskLater(Bukkit.getPluginManager().getPlugin("UniqueBoss"), delayTicks);
        
        // Сразу очищаем ссылку на босса (было 10 секунд - слишком долго)
        new BukkitRunnable() {
            @Override
            public void run() {
                UniqueBossManager.setBossDefeated();
                
                // Убираем принудительную загрузку чанка
                try {
                    org.bukkit.Chunk bossChunk = deathLoc.getChunk();
                    if (bossChunk.isForceLoaded()) {
                        bossChunk.setForceLoaded(false);
                        Plugin plugin = Bukkit.getPluginManager().getPlugin("UniqueBoss");
                        plugin.getLogger().info("🔧 Убрали принудительную загрузку чанка после смерти босса: [" + 
                            bossChunk.getX() + ", " + bossChunk.getZ() + "]");
                    }
                } catch (Exception e) {
                    Plugin plugin = Bukkit.getPluginManager().getPlugin("UniqueBoss");
                    plugin.getLogger().warning("⚠️ Ошибка при снятии принудительной загрузки чанка: " + e.getMessage());
                }
            }
        }.runTaskLater(Bukkit.getPluginManager().getPlugin("UniqueBoss"), 40L); // 2 секунды вместо 10
    }
    
    private void startDeathAnimation(Location deathLoc, World world) {
        Plugin plugin = Bukkit.getPluginManager().getPlugin("UniqueBoss");
        double multiplier = config.getParticlesMultiplier();
        
        // ФАЗА 1: Мощные взрывы и первоначальные эффекты (0-3 секунды)
        
        // Звуки смерти
        world.playSound(deathLoc, Sound.ENTITY_WITHER_DEATH, 3.0f, 0.3f);
        world.playSound(deathLoc, Sound.ENTITY_ENDER_DRAGON_DEATH, 2.5f, 0.5f);
        world.playSound(deathLoc, Sound.AMBIENT_CAVE, 2.0f, 0.3f);
        
        // Серия мощных взрывов
        for (int i = 0; i < 8; i++) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    Location explosionLoc = deathLoc.clone().add(
                        random.nextDouble() * 8 - 4,
                        random.nextDouble() * 3,
                        random.nextDouble() * 8 - 4
                    );
                    world.createExplosion(explosionLoc, 4.0f, false, false);
                    
                    // Эффекты при каждом взрыве
                    world.spawnParticle(Particle.SOUL, explosionLoc, (int)(50 * multiplier), 2, 2, 2, 0.2);
                    world.spawnParticle(Particle.SMOKE_LARGE, explosionLoc, (int)(30 * multiplier), 1.5, 1.5, 1.5, 0.1);
                }
            }.runTaskLater(plugin, i * 8L);
        }
        
        // ФАЗА 2: Душевные эффекты и темная энергия (3-5 секунд)
        new BukkitRunnable() {
            private int ticks = 0;
            
            @Override
            public void run() {
                if (ticks > 40) { // 2 секунды
                    this.cancel();
                    return;
                }
                
                // Пульсирующие душевные эффекты
                world.spawnParticle(Particle.SOUL, deathLoc.clone().add(0, 3, 0), 
                    (int)(100 * multiplier), 4, 3, 4, 0.3);
                world.spawnParticle(Particle.SOUL_FIRE_FLAME, deathLoc.clone().add(0, 4, 0), 
                    (int)(80 * multiplier), 3, 2, 3, 0.2);
                world.spawnParticle(Particle.SPELL_WITCH, deathLoc.clone().add(0, 2, 0), 
                    (int)(60 * multiplier), 3, 2, 3, 0.2);
                
                // Круги темной энергии
                if (ticks % 10 == 0) {
                    createDarkEnergyCircle(deathLoc.clone().add(0, 1, 0));
                    world.playSound(deathLoc, Sound.ENTITY_VEX_DEATH, 2.0f, 0.5f);
                }
                
                ticks++;
            }
        }.runTaskTimer(plugin, 60L, 1L); // Начинается через 3 секунды
        
        // ФАЗА 3: Финальная имплозия и исчезновение (5-7 секунд)
        new BukkitRunnable() {
            @Override
            public void run() {
                // Мощная имплозия
                world.playSound(deathLoc, Sound.ENTITY_GENERIC_EXPLODE, 3.0f, 0.1f);
                world.playSound(deathLoc, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 2.0f, 2.0f);
                
                // Огромный взрыв частиц
                world.spawnParticle(Particle.EXPLOSION_LARGE, deathLoc, (int)(200 * multiplier), 8, 8, 8, 0.3);
                world.spawnParticle(Particle.SOUL, deathLoc, (int)(300 * multiplier), 10, 10, 10, 0.5);
                world.spawnParticle(Particle.PORTAL, deathLoc, (int)(150 * multiplier), 6, 6, 6, 0.3);
                world.spawnParticle(Particle.REDSTONE, deathLoc, (int)(100 * multiplier), 8, 8, 8,
                    new Particle.DustOptions(org.bukkit.Color.BLACK, 3.0f));
                
                // Финальная волна исчезновения
                createDeathWave(deathLoc, 20.0);
                
                // Молнии вокруг
                for (int i = 0; i < 12; i++) {
                    double angle = 2 * Math.PI * i / 12;
                    Location lightningLoc = deathLoc.clone().add(
                        Math.cos(angle) * 6,
                        0,
                        Math.sin(angle) * 6
                    );
                    world.strikeLightningEffect(lightningLoc);
                }
            }
        }.runTaskLater(plugin, 100L); // Через 5 секунд
        
        // ФАЗА 4: Затухание и подготовка к дропу (7-8 секунд)
        new BukkitRunnable() {
            @Override
            public void run() {
                world.playSound(deathLoc, Sound.UI_TOAST_CHALLENGE_COMPLETE, 2.0f, 1.0f);
                world.playSound(deathLoc, Sound.ENTITY_PLAYER_LEVELUP, 1.5f, 1.2f);
                
                // Последние красивые эффекты перед дропом
                world.spawnParticle(Particle.TOTEM, deathLoc.clone().add(0, 5, 0), 
                    (int)(100 * multiplier), 5, 3, 5, 0.2);
                world.spawnParticle(Particle.FIREWORKS_SPARK, deathLoc.clone().add(0, 6, 0), 
                    (int)(80 * multiplier), 4, 2, 4, 0.1);
            }
        }.runTaskLater(plugin, 140L); // Через 7 секунд
    }
    
    private void createDeathWave(Location center, double maxRadius) {
        World world = center.getWorld();
        Plugin plugin = Bukkit.getPluginManager().getPlugin("UniqueBoss");
        double multiplier = config.getParticlesMultiplier();
        
        new BukkitRunnable() {
            double radius = 0;
            
            @Override
            public void run() {
                if (radius > maxRadius) {
                    this.cancel();
                    return;
                }
                
                int points = (int) (radius * 4 * multiplier);
                for (int i = 0; i < points; i++) {
                    double angle = 2 * Math.PI * i / points;
                    double x = center.getX() + radius * Math.cos(angle);
                    double z = center.getZ() + radius * Math.sin(angle);
                    Location particleLoc = new Location(world, x, center.getY() + 0.5, z);
                    
                    world.spawnParticle(Particle.SOUL, particleLoc, 1, 0, 0, 0, 0);
                    world.spawnParticle(Particle.REDSTONE, particleLoc, 1, 0, 0, 0, 
                        new Particle.DustOptions(org.bukkit.Color.fromRGB(50, 0, 50), 2.0f));
                }
                
                radius += 1.0;
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }
    
    private void dropRewards(Location deathLoc) {
        World world = deathLoc.getWorld();
        
        // Список уникальных наград
        List<ItemStack> rewards = createUniqueRewards();
        
        // КРАСИВАЯ анимация дропа с задержками и эффектами
        for (int i = 0; i < rewards.size(); i++) {
            final ItemStack reward = rewards.get(i);
            final int index = i;
            
            new BukkitRunnable() {
                @Override
                public void run() {
                    // Случайная позиция в радиусе 8 блоков
                    double angle = 2 * Math.PI * index / rewards.size(); // Равномерное распределение по кругу
                    double radius = 3.0 + random.nextDouble() * 5.0; // Радиус от 3 до 8 блоков
                    
                    Location dropLoc = deathLoc.clone().add(
                        Math.cos(angle) * radius,
                        3 + random.nextDouble() * 2, // Высота от 3 до 5 блоков
                        Math.sin(angle) * radius
                    );
                    
                    // Предварительные эффекты в точке появления
                    world.spawnParticle(Particle.PORTAL, dropLoc, 20, 0.5, 0.5, 0.5, 0.1);
                    world.spawnParticle(Particle.SPELL_WITCH, dropLoc, 15, 0.3, 0.3, 0.3, 0.1);
                    world.spawnParticle(Particle.FIREWORKS_SPARK, dropLoc, 10, 0.2, 0.2, 0.2, 0.05);
                    world.spawnParticle(Particle.TOTEM, dropLoc, 8, 0.3, 0.3, 0.3, 0.05);
                    
                    // Звук появления предмета
                    world.playSound(dropLoc, Sound.ENTITY_ITEM_PICKUP, 1.5f, 0.8f + (index * 0.1f));
                    world.playSound(dropLoc, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 1.2f);
                    
                    // Дропаем предмет
                    world.dropItemNaturally(dropLoc, reward);
                    
                    // Дополнительные эффекты после дропа
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            world.spawnParticle(Particle.VILLAGER_HAPPY, dropLoc, 12, 1, 1, 1, 0.1);
                            world.spawnParticle(Particle.REDSTONE, dropLoc, 8, 0.5, 0.5, 0.5, 0,
                                new Particle.DustOptions(org.bukkit.Color.YELLOW, 1.5f));
                        }
                    }.runTaskLater(Bukkit.getPluginManager().getPlugin("UniqueBoss"), 10L);
                }
            }.runTaskLater(Bukkit.getPluginManager().getPlugin("UniqueBoss"), index * 8L); // Задержка между предметами
        }
        
        // Финальные звуки дропа
        new BukkitRunnable() {
            @Override
            public void run() {
                world.playSound(deathLoc, Sound.UI_TOAST_CHALLENGE_COMPLETE, 2.0f, 1.0f);
                world.playSound(deathLoc, Sound.ENTITY_PLAYER_LEVELUP, 2.0f, 1.2f);
                
                // Финальная волна эффектов
                world.spawnParticle(Particle.FIREWORKS_SPARK, deathLoc, 100, 8, 3, 8, 0.2);
                world.spawnParticle(Particle.TOTEM, deathLoc, 50, 6, 2, 6, 0.1);
            }
        }.runTaskLater(Bukkit.getPluginManager().getPlugin("UniqueBoss"), rewards.size() * 8L + 20L);
    }
    
    private List<ItemStack> createUniqueRewards() {
        List<ItemStack> rewards = new ArrayList<>();
        
        // ОСНОВНЫЕ ОСКОЛКИ ТЕМНОГО ПОВЕЛИТЕЛЯ (диапазон)
        int minFragments = config.getFragmentsMinAmount();
        int maxFragments = config.getFragmentsMaxAmount();
        int baseFragments = minFragments + random.nextInt(maxFragments - minFragments + 1);
        
        // БОНУС ЗА КОЛИЧЕСТВО ИГРОКОВ
        if (config.isPlayerCountBonusEnabled()) {
            List<Player> nearbyPlayers = getNearbyPlayers(config.getPlayerCountBonusRadius());
            int playerCount = Math.min(nearbyPlayers.size(), config.getPlayerCountBonusMaxPlayers());
            double bonusMultiplier = playerCount * config.getPlayerCountBonusMultiplier();
            int playerBonus = (int) Math.floor(baseFragments * bonusMultiplier);
            baseFragments += playerBonus;
            
            if (playerBonus > 0) {
                // Уведомляем игроков о бонусе
                for (Player player : nearbyPlayers) {
                    player.sendMessage(ChatColor.GOLD + "🎉 Бонус за команду: +" + playerBonus + " осколков!");
                }
            }
        }
        
        // Добавляем основные осколки
        for (int i = 0; i < baseFragments; i++) {
            rewards.add(createDarkFragment());
        }
        
        // ДОПОЛНИТЕЛЬНЫЕ ОСКОЛКИ (с шансом)
        if (config.isExtraFragmentsEnabled() && random.nextInt(100) < config.getExtraFragmentsChance()) {
            int extraMin = config.getExtraFragmentsMinAmount();
            int extraMax = config.getExtraFragmentsMaxAmount();
            int extraAmount = extraMin + random.nextInt(extraMax - extraMin + 1);
            
            for (int i = 0; i < extraAmount; i++) {
                rewards.add(createDarkFragment());
            }
            
            // Уведомляем о дополнительных осколках
            List<Player> nearbyPlayers = getNearbyPlayers(50);
            for (Player player : nearbyPlayers) {
                player.sendMessage(ChatColor.LIGHT_PURPLE + "✨ Удача! Дополнительно +" + extraAmount + " осколков!");
            }
        }
        
        // РЕДКИЕ ПРЕДМЕТЫ (с шансом)
        
        // Неломающиеся элитры
        if (random.nextInt(100) < config.getUnbreakableElytraChance()) {
            rewards.add(createUnbreakableElytra());
        }
        
        // Усиленный меч
        if (random.nextInt(100) < config.getEnhancedSwordChance()) {
            rewards.add(createEnhancedSword());
        }
        
        // Посох телепортации
        if (random.nextInt(100) < config.getTeleportStaffChance()) {
            rewards.add(createTeleportStaff());
        }
        
        // Теневые сапоги
        if (random.nextInt(100) < config.getShadowBootsChance()) {
            rewards.add(createShadowBoots());
        }
        
        // Кристалл силы
        if (random.nextInt(100) < config.getPowerCrystalChance()) {
            rewards.add(createPowerCrystal());
        }
        
        // СТАНДАРТНЫЕ ЦЕННЫЕ РЕСУРСЫ
        rewards.add(new ItemStack(Material.DIAMOND, 32));
        rewards.add(new ItemStack(Material.EMERALD, 16));
        rewards.add(new ItemStack(Material.GOLD_INGOT, 48));
        rewards.add(new ItemStack(Material.NETHERITE_INGOT, 8));
        rewards.add(new ItemStack(Material.ANCIENT_DEBRIS, 4));
        
        // ЯЙЦА МОБОВ (с шансом)
        if (config.isMobEggsEnabled() && random.nextInt(100) < config.getMobEggsChance()) {
            int minAmount = config.getMobEggsMinAmount();
            int maxAmount = config.getMobEggsMaxAmount();
            int eggAmount = minAmount + random.nextInt(maxAmount - minAmount + 1);
            
            for (int i = 0; i < eggAmount; i++) {
                ItemStack mobEgg = createRandomMobEgg();
                if (mobEgg != null) {
                    rewards.add(mobEgg);
                }
            }
            
            // Уведомляем игроков о яйцах мобов
            List<Player> nearbyPlayers = getNearbyPlayers(50);
            for (Player player : nearbyPlayers) {
                player.sendMessage(ChatColor.LIGHT_PURPLE + "🥚 Удача! Выпало " + eggAmount + " яиц мобов!");
            }
        }
        
        return rewards;
    }
    
    private ItemStack createRandomMobEgg() {
        // Список доступных яиц мобов (исключаем призывателей и других опасных мобов)
        Material[] mobEggs = {
            Material.COW_SPAWN_EGG,
            Material.PIG_SPAWN_EGG,
            Material.SHEEP_SPAWN_EGG,
            Material.CHICKEN_SPAWN_EGG,
            Material.HORSE_SPAWN_EGG,
            Material.WOLF_SPAWN_EGG,
            Material.CAT_SPAWN_EGG,
            Material.VILLAGER_SPAWN_EGG,
            Material.IRON_GOLEM_SPAWN_EGG,
            Material.ZOMBIE_SPAWN_EGG,
            Material.SKELETON_SPAWN_EGG,
            Material.SPIDER_SPAWN_EGG,
            Material.CREEPER_SPAWN_EGG,
            Material.ENDERMAN_SPAWN_EGG,
            Material.BLAZE_SPAWN_EGG,
            Material.GHAST_SPAWN_EGG,
            Material.MAGMA_CUBE_SPAWN_EGG,
            Material.SLIME_SPAWN_EGG,
            Material.WITCH_SPAWN_EGG,
            Material.ZOMBIFIED_PIGLIN_SPAWN_EGG,
            Material.WITHER_SKELETON_SPAWN_EGG,
            Material.GUARDIAN_SPAWN_EGG,
            Material.ELDER_GUARDIAN_SPAWN_EGG,
            Material.SHULKER_SPAWN_EGG,
            Material.PHANTOM_SPAWN_EGG,
            Material.DROWNED_SPAWN_EGG,
            Material.HUSK_SPAWN_EGG,
            Material.STRAY_SPAWN_EGG,
            Material.VEX_SPAWN_EGG,
            Material.VINDICATOR_SPAWN_EGG,
            Material.PILLAGER_SPAWN_EGG,
            Material.RAVAGER_SPAWN_EGG
        };
        
        Material selectedEgg = mobEggs[random.nextInt(mobEggs.length)];
        ItemStack eggItem = new ItemStack(selectedEgg);
        
        // Добавляем красивое описание
        ItemMeta meta = eggItem.getItemMeta();
        if (meta != null) {
            meta.setLore(Arrays.asList(
                ChatColor.GRAY + "Редкое яйцо моба, полученное",
                ChatColor.GRAY + "с Темного Повелителя",
                "",
                ChatColor.YELLOW + "✨ Призовите этого моба!",
                "",
                ChatColor.DARK_GRAY + "Трофей босса"
            ));
            eggItem.setItemMeta(meta);
        }
        
        return eggItem;
    }
    
    private ItemStack createDarkFragment() {
        ItemStack fragment = new ItemStack(Material.PRISMARINE_SHARD);
        ItemMeta meta = fragment.getItemMeta();
        
        meta.setDisplayName(ChatColor.DARK_PURPLE + "" + ChatColor.BOLD + "🖤 ОСКОЛОК ТЕМНОГО ПОВЕЛИТЕЛЯ 🖤");
        meta.setLore(Arrays.asList(
            ChatColor.GRAY + "Осколок побежденного Темного Повелителя",
            ChatColor.GRAY + "Пульсирует остаточной темной энергией",
            "",
            ChatColor.YELLOW + "" + ChatColor.BOLD + "КРАФТ БРОНИ:",
            ChatColor.GOLD + "Используйте эти осколки на верстаке",
            ChatColor.GOLD + "для создания уникальной брони:",
            "",
            ChatColor.LIGHT_PURPLE + "• Шлем Темного Повелителя (6 осколков)",
            ChatColor.LIGHT_PURPLE + "• Нагрудник Темного Повелителя (8 осколков)",
            ChatColor.LIGHT_PURPLE + "• Поножи Темного Повелителя (7 осколков)",
            ChatColor.LIGHT_PURPLE + "• Сапоги Темного Повелителя (5 осколков)",
            "",
            ChatColor.GREEN + "Полный комплект дает уникальные способности!",
            "",
            ChatColor.DARK_GRAY + "Уникальный материал"
        ));
        
        // КРИТИЧЕСКИ ВАЖНО: CustomModelData для распознавания в рецептах!
        meta.setCustomModelData(config.getFragmentsCustomModelData());
        fragment.setItemMeta(meta);
        return fragment;
    }
    
    private ItemStack createUnbreakableElytra() {
        ItemStack elytra = new ItemStack(Material.ELYTRA);
        ItemMeta meta = elytra.getItemMeta();
        
        meta.setDisplayName(ChatColor.DARK_PURPLE + "" + ChatColor.BOLD + "🦇 НЕЛОМАЮЩИЕСЯ КРЫЛЬЯ ТЬМЫ 🦇");
        meta.setLore(Arrays.asList(
            ChatColor.GRAY + "Элитры, созданные из теневой материи",
            ChatColor.GRAY + "Темного Повелителя",
            "",
            ChatColor.GOLD + "" + ChatColor.BOLD + "СПОСОБНОСТИ:",
            ChatColor.GREEN + "✓ Бесконечная прочность",
            ChatColor.GREEN + "✓ Автовосстановление",
            ChatColor.YELLOW + "🚀 Лончер при взлете",
            ChatColor.LIGHT_PURPLE + "🎆 Настраиваемые автофейерверки",
            ChatColor.AQUA + "✨ Магические эффекты полета",
            "",
            ChatColor.DARK_PURPLE + "\"Летайте с силой бесконечных фейерверков!\"",
            "",
            ChatColor.DARK_GRAY + "Легендарный предмет"
        ));
        
        meta.addEnchant(Enchantment.MENDING, 1, true);
        meta.addEnchant(Enchantment.DURABILITY, 10, true); // Высокая прочность
        meta.setUnbreakable(true);
        meta.setCustomModelData(99001);
        
        elytra.setItemMeta(meta);
        return elytra;
    }
    
    private ItemStack createEnhancedSword() {
        ItemStack sword = new ItemStack(Material.NETHERITE_SWORD);
        ItemMeta meta = sword.getItemMeta();
        
        meta.setDisplayName(ChatColor.DARK_RED + "" + ChatColor.BOLD + "⚔ КЛИНОК РАЗРУШЕНИЯ ⚔");
        meta.setLore(Arrays.asList(
            ChatColor.GRAY + "Меч, закаленный в темной энергии",
            ChatColor.GRAY + "Темного Повелителя",
            "",
            ChatColor.GOLD + "" + ChatColor.BOLD + "СПОСОБНОСТИ:",
            ChatColor.RED + "⚔ Острота VI",
            ChatColor.RED + "🔥 Аспект огня III",
            ChatColor.RED + "💀 Жатва III",
            ChatColor.GREEN + "🔧 Починка",
            ChatColor.BLUE + "💎 Прочность IV",
            "",
            ChatColor.DARK_PURPLE + "\"Смерть следует за каждым ударом\"",
            "",
            ChatColor.DARK_GRAY + "Легендарное оружие"
        ));
        
        meta.addEnchant(Enchantment.DAMAGE_ALL, 6, true);
        meta.addEnchant(Enchantment.FIRE_ASPECT, 3, true);
        meta.addEnchant(Enchantment.LOOT_BONUS_MOBS, 3, true);
        meta.addEnchant(Enchantment.MENDING, 1, true);
        meta.addEnchant(Enchantment.DURABILITY, 4, true);
        meta.setCustomModelData(99002);
        
        sword.setItemMeta(meta);
        return sword;
    }
    
    private ItemStack createTeleportStaff() {
        ItemStack staff = new ItemStack(Material.BLAZE_ROD);
        ItemMeta meta = staff.getItemMeta();
        
        meta.setDisplayName(ChatColor.DARK_PURPLE + "" + ChatColor.BOLD + "🌟 ПОСОХ ТЕЛЕПОРТАЦИИ 🌟");
        meta.setLore(Arrays.asList(
            ChatColor.GRAY + "Магический посох с силой",
            ChatColor.GRAY + "пространственного перемещения",
            "",
            ChatColor.GOLD + "" + ChatColor.BOLD + "СПОСОБНОСТИ:",
            ChatColor.LIGHT_PURPLE + "ПКМ - Телепортация на 20 блоков",
            ChatColor.YELLOW + "Перезарядка: 35 секунд",
            ChatColor.GREEN + "✓ Неразрушимость",
            "",
            ChatColor.DARK_PURPLE + "\"Пространство подчиняется вашей воле\"",
            "",
            ChatColor.DARK_GRAY + "Магический артефакт"
        ));
        
        meta.setUnbreakable(true);
        meta.setCustomModelData(99003);
        
        staff.setItemMeta(meta);
        return staff;
    }
    
    private ItemStack createShadowBoots() {
        ItemStack boots = new ItemStack(Material.NETHERITE_BOOTS);
        ItemMeta meta = boots.getItemMeta();
        
        meta.setDisplayName(ChatColor.DARK_GRAY + "" + ChatColor.BOLD + "👤 САПОГИ ТЕНЕЙ 👤");
        meta.setLore(Arrays.asList(
            ChatColor.GRAY + "Сапоги, сотканные из чистой тени",
            "",
            ChatColor.GOLD + "" + ChatColor.BOLD + "СПОСОБНОСТИ:",
            ChatColor.GRAY + "👻 При приседании - невидимость (8 сек)",
            ChatColor.BLUE + "💨 Постоянная скорость III",
            ChatColor.GREEN + "🦎 Бесшумное передвижение",
            ChatColor.YELLOW + "🛡️ Защита IV",
            ChatColor.GREEN + "🔧 Починка",
            "",
            ChatColor.DARK_PURPLE + "\"Станьте одним с тенями\"",
            "",
            ChatColor.DARK_GRAY + "Легендарная обувь"
        ));
        
        meta.addEnchant(Enchantment.PROTECTION_ENVIRONMENTAL, 4, true);
        meta.addEnchant(Enchantment.MENDING, 1, true);
        meta.addEnchant(Enchantment.DURABILITY, 3, true);
        meta.setCustomModelData(99004);
        
        boots.setItemMeta(meta);
        return boots;
    }
    
    private ItemStack createPowerCrystal() {
        ItemStack crystal = new ItemStack(Material.END_CRYSTAL);
        ItemMeta meta = crystal.getItemMeta();
        
        meta.setDisplayName(ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "💎 КРИСТАЛЛ ТЕМНОЙ СИЛЫ 💎");
        meta.setLore(Arrays.asList(
            ChatColor.GRAY + "Концентрированная сущность",
            ChatColor.GRAY + "силы Темного Повелителя",
            "",
            ChatColor.GOLD + "" + ChatColor.BOLD + "СПОСОБНОСТИ:",
            ChatColor.RED + "🔥 При ношении в руке:",
            ChatColor.YELLOW + "• Сила II",
            ChatColor.YELLOW + "• Сопротивление урону I",
            ChatColor.YELLOW + "• Регенерация I",
            ChatColor.YELLOW + "• Ночное зрение",
            "",
            ChatColor.DARK_PURPLE + "\"Источник неиссякаемой силы\"",
            "",
            ChatColor.DARK_GRAY + "Уникальный артефакт"
        ));
        
        meta.setCustomModelData(99005);
        crystal.setItemMeta(meta);
        return crystal;
    }
    
    private void dropExperience(Location deathLoc) {
        World world = deathLoc.getWorld();
        
        // УВЕЛИЧИВАЕМ количество опыта до 20000 (больше чем было)
        int totalExp = 20000;
        
        // ЭПИЧЕСКИЙ фонтан опыта с красивой анимацией
        for (int i = 0; i < 50; i++) { // Еще больше орбов опыта
            final int index = i;
            new BukkitRunnable() {
                @Override
                public void run() {
                    int expToDrop = 300 + random.nextInt(500); // Случайное количество опыта в орбе
                    
                    // Создаем спиральную анимацию дропа
                    double angle = 2 * Math.PI * index / 50; // Равномерное распределение
                    double radius = 2.0 + (index * 0.15); // Увеличивающийся радиус спирали
                    double height = 4.0 + random.nextDouble() * 3; // Высота от 4 до 7 блоков
                    
                    Location expLoc = deathLoc.clone().add(
                        Math.cos(angle) * radius,
                        height,
                        Math.sin(angle) * radius
                    );
                    
                    // Предварительные эффекты опыта
                    world.spawnParticle(Particle.VILLAGER_HAPPY, expLoc, 20, 0.5, 0.5, 0.5, 0.1);
                    world.spawnParticle(Particle.ENCHANTMENT_TABLE, expLoc, 15, 0.3, 0.3, 0.3, 0.1);
                    world.spawnParticle(Particle.FIREWORKS_SPARK, expLoc, 10, 0.2, 0.2, 0.2, 0.05);
                    world.spawnParticle(Particle.REDSTONE, expLoc, 8, 0.3, 0.3, 0.3, 0,
                        new Particle.DustOptions(org.bukkit.Color.LIME, 1.5f));
                    
                    // Звуки появления опыта
                    world.playSound(expLoc, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 0.8f + (index * 0.02f));
                    world.playSound(expLoc, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.8f, 1.5f);
                    
                    ExperienceOrb expOrb = (ExperienceOrb) world.spawnEntity(expLoc, EntityType.EXPERIENCE_ORB);
                    expOrb.setExperience(expToDrop);
                    
                    // Добавляем эффектную скорость для красивого фонтана
                    Vector velocity = new Vector(
                        Math.cos(angle) * 0.3, // Направление от центра
                        0.5 + random.nextDouble() * 0.8, // Высокий подъем
                        Math.sin(angle) * 0.3
                    );
                    expOrb.setVelocity(velocity);
                    
                    // Дополнительные эффекты через секунду
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            if (!expOrb.isDead()) {
                                Location orbLoc = expOrb.getLocation();
                                world.spawnParticle(Particle.VILLAGER_HAPPY, orbLoc, 5, 0.2, 0.2, 0.2, 0.05);
                                world.spawnParticle(Particle.REDSTONE, orbLoc, 3, 0.1, 0.1, 0.1, 0,
                                    new Particle.DustOptions(org.bukkit.Color.GREEN, 1.0f));
                            }
                        }
                    }.runTaskLater(Bukkit.getPluginManager().getPlugin("UniqueBoss"), 20L);
                }
            }.runTaskLater(Bukkit.getPluginManager().getPlugin("UniqueBoss"), index * 3L); // Каждые 0.15 секунды
        }
        
        // ЭПИЧЕСКИЕ центральные эффекты опыта
        new BukkitRunnable() {
            private int ticks = 0;
            
            @Override
            public void run() {
                if (ticks > 100) { // 5 секунд
                    this.cancel();
                    return;
                }
                
                // Пульсирующие эффекты в центре
                if (ticks % 10 == 0) {
                    world.spawnParticle(Particle.VILLAGER_HAPPY, deathLoc.clone().add(0, 2, 0), 30, 3, 2, 3, 0.1);
                    world.spawnParticle(Particle.ENCHANTMENT_TABLE, deathLoc.clone().add(0, 3, 0), 20, 2, 1, 2, 0.1);
                    world.spawnParticle(Particle.FIREWORKS_SPARK, deathLoc.clone().add(0, 4, 0), 15, 1.5, 0.5, 1.5, 0.1);
                }
                
                // Кольца опыта
                if (ticks % 20 == 0) {
                    createExperienceRings(deathLoc);
                }
                
                // Звуковое сопровождение
                if (ticks % 30 == 0) {
                    world.playSound(deathLoc, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 2.0f, 0.5f);
                    world.playSound(deathLoc, Sound.ENTITY_PLAYER_LEVELUP, 1.5f, 1.0f);
                }
                
                ticks++;
            }
        }.runTaskTimer(Bukkit.getPluginManager().getPlugin("UniqueBoss"), 0L, 1L);
        
        // Финальные звуки опыта
        new BukkitRunnable() {
            @Override
            public void run() {
                world.playSound(deathLoc, Sound.ENTITY_PLAYER_LEVELUP, 3.0f, 1.5f);
                world.playSound(deathLoc, Sound.UI_TOAST_CHALLENGE_COMPLETE, 2.5f, 1.8f);
                
                // Финальная волна опыта
                world.spawnParticle(Particle.VILLAGER_HAPPY, deathLoc.clone().add(0, 5, 0), 200, 10, 5, 10, 0.3);
                world.spawnParticle(Particle.FIREWORKS_SPARK, deathLoc.clone().add(0, 6, 0), 100, 8, 3, 8, 0.2);
            }
        }.runTaskLater(Bukkit.getPluginManager().getPlugin("UniqueBoss"), 200L); // Через 10 секунд
    }
    
    private void createExperienceRings(Location center) {
        World world = center.getWorld();
        
        // Создаем 2 кольца разного размера
        for (int ring = 1; ring <= 2; ring++) {
            final double ringRadius = ring * 3.0;
            final double ringHeight = center.getY() + 1.0 + (ring * 0.5);
            
            int points = 20;
            for (int i = 0; i < points; i++) {
                double angle = 2 * Math.PI * i / points;
                double x = center.getX() + ringRadius * Math.cos(angle);
                double z = center.getZ() + ringRadius * Math.sin(angle);
                
                Location particleLoc = new Location(world, x, ringHeight, z);
                world.spawnParticle(Particle.VILLAGER_HAPPY, particleLoc, 1, 0, 0, 0, 0);
                world.spawnParticle(Particle.REDSTONE, particleLoc, 1, 0, 0, 0, 0,
                    new Particle.DustOptions(org.bukkit.Color.YELLOW, 1.0f));
            }
        }
    }
    
    // Геттеры и служебные методы
    public Entity getEntity() {
        return bossEntity;
    }
    
    public boolean isAlive() {
        return isAlive && bossEntity != null && !bossEntity.isDead();
    }
    
    public int getCurrentPhase() {
        return currentPhase;
    }
    
    public BossBar getBossBar() {
        return bossBar;
    }
    
    /**
     * Принудительно уничтожает босса (используется системой неактивности)
     * НЕ дропает награды и НЕ проигрывает анимацию смерти
     */
    public void forceDestroy() {
        isAlive = false;
        
        // Отменяем все активные задачи для предотвращения утечек памяти
        cancelAllTasks();
        
        // Удаляем bossbar
        if (bossBar != null) {
            bossBar.removeAll();
        }
        
        // Снимаем принудительную загрузку чанка
        if (bossEntity != null) {
            try {
                org.bukkit.Chunk bossChunk = bossEntity.getLocation().getChunk();
                if (bossChunk.isForceLoaded()) {
                    bossChunk.setForceLoaded(false);
                    Plugin plugin = Bukkit.getPluginManager().getPlugin("UniqueBoss");
                    plugin.getLogger().info("🔧 Сняли принудительную загрузку чанка при уничтожении босса: [" + 
                        bossChunk.getX() + ", " + bossChunk.getZ() + "]");
                }
            } catch (Exception e) {
                Plugin plugin = Bukkit.getPluginManager().getPlugin("UniqueBoss");
                plugin.getLogger().warning("⚠️ Не удалось снять принудительную загрузку чанка: " + e.getMessage());
            }
        }
        
        // Принудительно удаляем сущность
        if (bossEntity != null && !bossEntity.isDead()) {
            bossEntity.remove();
        }
        
        // Уведомляем игроков
        List<Player> nearbyPlayers = getNearbyPlayers(100);
        for (Player player : nearbyPlayers) {
            player.sendTitle(ChatColor.YELLOW + "⏰ БОСС ИСЧЕЗ", 
                ChatColor.GRAY + "Темный Повелитель покинул мир", 10, 40, 10);
        }

        Bukkit.getPluginManager().getPlugin("UniqueBoss").getLogger()
            .info("🔥 Босс принудительно уничтожен системой неактивности");
        
        // PDC метки останутся в сущности автоматически
    }
    
    /**
     * Устанавливает существующую сущность как босса (для восстановления из PDC)
     */
    public void setBossEntity(Wither existingEntity) {
        this.bossEntity = existingEntity;
    }
    
    /**
     * Восстанавливает состояние босса из сохраненных данных
     */
    public void restoreFromSave(double savedHealth, int savedPhase) {
        if (bossEntity == null) {
            throw new IllegalStateException("Нельзя восстановить босса без сущности!");
        }
        
        Plugin plugin = Bukkit.getPluginManager().getPlugin("UniqueBoss");
        plugin.getLogger().info("🔄 Восстанавливаем состояние босса:");
        plugin.getLogger().info("   Здоровье: " + savedHealth);
        plugin.getLogger().info("   Фаза: " + savedPhase);
        
        // Настраиваем сущность
        setupBoss();
        
        // Восстанавливаем здоровье
        double maxHealth = bossEntity.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
        if (savedHealth > 0 && savedHealth <= maxHealth) {
            bossEntity.setHealth(savedHealth);
            lastKnownHealth = savedHealth;
        } else {
            // Если сохраненное здоровье некорректно - используем максимальное
            bossEntity.setHealth(maxHealth);
            lastKnownHealth = maxHealth;
        }
        
        // Восстанавливаем фазу
        if (savedPhase >= 1 && savedPhase <= 3) {
            currentPhase = savedPhase;
            
            // Применяем эффекты фазы
            if (currentPhase >= 2) {
                bossEntity.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 1));
            }
            if (currentPhase >= 3) {
                bossEntity.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 2));
            }
        } else {
            currentPhase = 1;
        }
        
        // Создаем бossbar
        createBossBar();
        updateBossBarDisplay();
        
        // Запускаем основной цикл поведения босса
        startBossAI();
        
        // Запускаем постоянные визуальные эффекты
        startAmbientEffects();
        
        plugin.getLogger().info("✅ Состояние босса восстановлено:");
        plugin.getLogger().info("   Здоровье: " + bossEntity.getHealth() + "/" + maxHealth);
        plugin.getLogger().info("   Фаза: " + currentPhase);
        plugin.getLogger().info("   Эффекты: " + bossEntity.getActivePotionEffects().size());
    }

    
    // ================================
    // СИСТЕМА ПРОВОКАЦИЙ В ЧАТ
    // ================================
    
    private void sendRandomTaunt() {
        if (!config.isChatTauntsEnabled()) return;
        
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastTauntTime < config.getTauntInterval() * 1000) return;
        
        if (random.nextInt(100) >= config.getTauntChance()) return;
        
        List<Player> nearbyPlayers = getNearbyPlayers(config.getTauntRadius());
        if (nearbyPlayers.isEmpty()) return;
        
        lastTauntTime = currentTime;
        
        // 60% шанс на общую провокацию, 40% на персональную
        if (random.nextInt(100) < 60) {
            sendGeneralTaunt();
        } else {
            Player target = nearbyPlayers.get(random.nextInt(nearbyPlayers.size()));
            sendTargetedTaunt(target.getName());
        }
    }
    
    private void sendGeneralTaunt() {
        List<String> taunts = config.getGeneralTaunts();
        if (taunts.isEmpty()) return;
        
        String taunt = taunts.get(random.nextInt(taunts.size()));
        taunt = ChatColor.translateAlternateColorCodes('&', taunt);
        
        // Отправляем всем игрокам в радиусе
        List<Player> nearbyPlayers = getNearbyPlayers(config.getTauntRadius());
        for (Player player : nearbyPlayers) {
            player.sendMessage(taunt);
        }
        
        // Эффекты при общей провокации
        World world = bossEntity.getWorld();
        Location bossLoc = bossEntity.getLocation();
        world.playSound(bossLoc, Sound.ENTITY_WITHER_AMBIENT, 1.5f, 0.8f);
        
        double multiplier = config.getParticlesMultiplier();
        world.spawnParticle(Particle.SPELL_WITCH, bossLoc.clone().add(0, 2, 0), 
            (int)(15 * multiplier), 1, 1, 1, 0.1);
    }
    
    private void sendTargetedTaunt(String playerName) {
        List<String> taunts = config.getTargetedTaunts();
        if (taunts.isEmpty()) return;
        
        String taunt = taunts.get(random.nextInt(taunts.size()));
        taunt = taunt.replace("%player%", playerName);
        taunt = ChatColor.translateAlternateColorCodes('&', taunt);
        
        // Отправляем всем игрокам в радиусе
        List<Player> nearbyPlayers = getNearbyPlayers(config.getTauntRadius());
        for (Player player : nearbyPlayers) {
            player.sendMessage(taunt);
        }
        
        // Эффекты при персональной провокации
        World world = bossEntity.getWorld();
        Location bossLoc = bossEntity.getLocation();
        world.playSound(bossLoc, Sound.ENTITY_WITHER_HURT, 1.0f, 1.2f);
        
        double multiplier = config.getParticlesMultiplier();
        world.spawnParticle(Particle.REDSTONE, bossLoc.clone().add(0, 2, 0), 
            (int)(10 * multiplier), 1, 1, 1, 
            new Particle.DustOptions(org.bukkit.Color.RED, 1.5f));
    }
    
    public void onDamageReceived(String attackerName) {
        lastAttacker = attackerName;
        
        // 20% шанс на провокацию при получении урона
        if (random.nextInt(100) < 20) {
            sendDamageTaunt(attackerName);
        }
    }
    
    private void sendDamageTaunt(String attackerName) {
        List<String> taunts = config.getDamageTaunts();
        if (taunts.isEmpty()) return;
        
        String taunt = taunts.get(random.nextInt(taunts.size()));
        taunt = taunt.replace("%attacker%", attackerName);
        taunt = ChatColor.translateAlternateColorCodes('&', taunt);
        
        // Отправляем всем игрокам в радиусе
        List<Player> nearbyPlayers = getNearbyPlayers(config.getTauntRadius());
        for (Player player : nearbyPlayers) {
            player.sendMessage(taunt);
        }
        
        // Звук злости
        World world = bossEntity.getWorld();
        world.playSound(bossEntity.getLocation(), Sound.ENTITY_WITHER_HURT, 2.0f, 0.5f);
    }
    
    private void checkLowHealthTaunts() {
        if (lowHealthTauntSent) return;
        
        double currentHealth = bossEntity.getHealth();
        double maxHealth = bossEntity.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH).getValue();
        double healthPercent = currentHealth / maxHealth;
        
        // При здоровье ниже 25%
        if (healthPercent <= 0.25) {
            lowHealthTauntSent = true;
            sendLowHealthTaunt();
        }
    }
    
    private void sendLowHealthTaunt() {
        List<String> taunts = config.getLowHealthTaunts();
        if (taunts.isEmpty()) return;
        
        String taunt = taunts.get(random.nextInt(taunts.size()));
        taunt = ChatColor.translateAlternateColorCodes('&', taunt);
        
        // Отправляем всем игрокам в радиусе
        List<Player> nearbyPlayers = getNearbyPlayers(config.getTauntRadius());
        for (Player player : nearbyPlayers) {
            player.sendMessage(taunt);
        }
        
        // Мощные звуковые эффекты
        World world = bossEntity.getWorld();
        Location bossLoc = bossEntity.getLocation();
        world.playSound(bossLoc, Sound.ENTITY_WITHER_DEATH, 1.5f, 1.5f);
        world.playSound(bossLoc, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 0.8f);
    }
    
    public void sendPhaseTaunt(int phase) {
        List<String> taunts;
        
        if (phase == 2) {
            taunts = config.getPhase2Taunts();
        } else if (phase == 3) {
            taunts = config.getPhase3Taunts();
        } else {
            return;
        }
        
        if (taunts.isEmpty()) return;
        
        String taunt = taunts.get(random.nextInt(taunts.size()));
        taunt = ChatColor.translateAlternateColorCodes('&', taunt);
        
        // Отправляем всем игрокам в радиусе
        List<Player> nearbyPlayers = getNearbyPlayers(config.getTauntRadius());
        for (Player player : nearbyPlayers) {
            player.sendMessage(taunt);
        }
        
        // Звук смены фазы
        World world = bossEntity.getWorld();
        world.playSound(bossEntity.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 2.0f, 0.3f);
    }
    
    /**
     * Регистрирует задачу для автоматической отмены при уничтожении босса
     */
    private void registerTask(BukkitTask task) {
        activeTasks.add(task);
    }
    
    /**
     * Отменяет все активные задачи для предотвращения утечек памяти
     */
    private void cancelAllTasks() {
        for (BukkitTask task : activeTasks) {
            if (task != null && !task.isCancelled()) {
                task.cancel();
            }
        }
        activeTasks.clear();
        
        if (config.isBossLifecycleLoggingEnabled()) {
            Plugin plugin = Bukkit.getPluginManager().getPlugin("UniqueBoss");
            plugin.getLogger().info("🧹 DEBUG: Отменено всех задач: " + activeTasks.size());
        }
    }
} 

