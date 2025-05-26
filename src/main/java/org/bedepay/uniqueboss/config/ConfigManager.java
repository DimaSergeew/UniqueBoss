package org.bedepay.uniqueboss.config;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;
import java.util.List;

/**
 * Менеджер конфигурации для плагина UniqueBoss
 * Версия 2.0 - Оптимизированная структура
 */
public class ConfigManager {
    
    private final Plugin plugin;
    private FileConfiguration config;
    
    public ConfigManager(Plugin plugin) {
        this.plugin = plugin;
        loadConfig();
    }
    
    public void loadConfig() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        config = plugin.getConfig();
    }
    
    // ===================================
    // ОСНОВНЫЕ НАСТРОЙКИ БОССА
    // ===================================
    
    public int getDifficultyLevel() {
        return config.getInt("boss.difficulty_level", 3);
    }
    
    public String getDifficultyName() {
        String levelPath = "difficulty_settings.level_" + getDifficultyLevel() + ".name";
        return config.getString(levelPath, "Нормальный");
    }
    
    // Здоровье босса (с учётом сложности)
    public double getPhase1Health() {
        double base = config.getDouble("boss.health.phase_1", 2000);
        return base * getDifficultyHealthMultiplier();
    }
    
    public double getPhase2Health() {
        double base = config.getDouble("boss.health.phase_2", 1600);
        return base * getDifficultyHealthMultiplier();
    }
    
    public double getPhase3Health() {
        double base = config.getDouble("boss.health.phase_3", 1200);
        return base * getDifficultyHealthMultiplier();
    }
    
    // Способности (кулдауны в тиках, с учётом сложности)
    public int getFireballCooldown() {
        int base = config.getInt("boss.abilities.fireball", 5) * 20; // секунды в тики
        return (int)(base / getDifficultyAbilitiesSpeedMultiplier());
    }
    
    public int getSummonMinionsCooldown() {
        int base = config.getInt("boss.abilities.summon_minions", 8) * 20;
        return (int)(base / getDifficultyAbilitiesSpeedMultiplier());
    }
    
    public int getPullPlayersCooldown() {
        int base = config.getInt("boss.abilities.pull_players", 12) * 20;
        return (int)(base / getDifficultyAbilitiesSpeedMultiplier());
    }
    
    public int getTeleportCooldown() {
        int base = config.getInt("boss.abilities.teleport", 10) * 20;
        return (int)(base / getDifficultyAbilitiesSpeedMultiplier());
    }
    
    public int getMagicMissilesCooldown() {
        int base = config.getInt("boss.abilities.magic_missiles", 3) * 20;
        return (int)(base / getDifficultyAbilitiesSpeedMultiplier());
    }
    
    public int getEarthSpikesCooldown() {
        int base = config.getInt("boss.abilities.earth_spikes", 7) * 20;
        return (int)(base / getDifficultyAbilitiesSpeedMultiplier());
    }
    
    public int getMeteorStormCooldown() {
        int base = config.getInt("boss.abilities.meteor_storm", 15) * 20;
        return (int)(base / getDifficultyAbilitiesSpeedMultiplier());
    }
    
    public int getBlindingFlashCooldown() {
        int base = config.getInt("boss.abilities.blinding_flash", 9) * 20;
        return (int)(base / getDifficultyAbilitiesSpeedMultiplier());
    }
    
    public int getSoulScreamCooldown() {
        int base = config.getInt("boss.abilities.soul_scream", 11) * 20;
        return (int)(base / getDifficultyAbilitiesSpeedMultiplier());
    }
    
    // Ограничения поведения
    public double getMaxFlightHeight() {
        return config.getDouble("boss.movement_limits.max_flight_height", 20);
    }
    
    public double getMaxDistanceFromSpawn() {
        return config.getDouble("boss.movement_limits.max_distance_from_spawn", 50);
    }
    
    // Правила атак
    public boolean shouldAttackSpectators() {
        return config.getBoolean("boss.attack_rules.spectators", false);
    }
    
    public boolean shouldAttackCreative() {
        return config.getBoolean("boss.attack_rules.creative", false);
    }
    
    public boolean shouldIgnoreVanished() {
        return !config.getBoolean("boss.attack_rules.vanished", false);
    }
    
    public boolean shouldShowBossBarAllModes() {
        return config.getBoolean("boss.bossbar.show_all_gamemodes", true);
    }
    
    // ===================================
    // СИСТЕМА НАГРАД
    // ===================================
    
    public int getFragmentsMinAmount() {
        List<Integer> amounts = config.getIntegerList("rewards.fragments.base_amount");
        if (amounts.size() >= 1) {
            return Math.max(1, (int)(amounts.get(0) * getDifficultyDropsMultiplier()));
        }
        return 1;
    }
    
    public int getFragmentsMaxAmount() {
        List<Integer> amounts = config.getIntegerList("rewards.fragments.base_amount");
        if (amounts.size() >= 2) {
            return Math.max(1, (int)(amounts.get(1) * getDifficultyDropsMultiplier()));
        }
        return 3;
    }
    
    public boolean isExtraFragmentsEnabled() {
        return config.getInt("rewards.fragments.extra_chance", 30) > 0;
    }
    
    public int getExtraFragmentsChance() {
        return config.getInt("rewards.fragments.extra_chance", 30);
    }
    
    public int getExtraFragmentsMinAmount() {
        List<Integer> amounts = config.getIntegerList("rewards.fragments.extra_amount");
        if (amounts.size() >= 1) {
            return amounts.get(0);
        }
        return 1;
    }
    
    public int getExtraFragmentsMaxAmount() {
        List<Integer> amounts = config.getIntegerList("rewards.fragments.extra_amount");
        if (amounts.size() >= 2) {
            return amounts.get(1);
        }
        return 2;
    }
    
    // Бонус за команду
    public boolean isPlayerCountBonusEnabled() {
        return config.getBoolean("rewards.fragments.team_bonus.enabled", true);
    }
    
    public int getPlayerCountBonusRadius() {
        return config.getInt("rewards.fragments.team_bonus.radius", 60);
    }
    
    public double getPlayerCountBonusMultiplier() {
        return config.getDouble("rewards.fragments.team_bonus.bonus_per_player", 5) / 100.0;
    }
    
    public int getPlayerCountBonusMaxPlayers() {
        return config.getInt("rewards.fragments.team_bonus.max_players", 10);
    }
    
    // Редкие предметы
    public int getUnbreakableElytraChance() {
        return config.getInt("rewards.rare_items.unbreakable_elytra", 10);
    }
    
    public int getEnhancedSwordChance() {
        return config.getInt("rewards.rare_items.enhanced_sword", 15);
    }
    
    public int getTeleportStaffChance() {
        return config.getInt("rewards.rare_items.teleport_staff", 10);
    }
    
    public int getShadowBootsChance() {
        return config.getInt("rewards.rare_items.shadow_boots", 10);
    }
    
    public int getPowerCrystalChance() {
        return config.getInt("rewards.rare_items.power_crystal", 8);
    }
    
    // Яйца мобов
    public boolean isMobEggsEnabled() {
        return config.getBoolean("rewards.mob_eggs.enabled", true);
    }
    
    public int getMobEggsChance() {
        return config.getInt("rewards.mob_eggs.chance", 20);
    }
    
    public int getMobEggsMinAmount() {
        List<Integer> amounts = config.getIntegerList("rewards.mob_eggs.amount");
        if (amounts.size() >= 1) {
            return amounts.get(0);
        }
        return 1;
    }
    
    public int getMobEggsMaxAmount() {
        List<Integer> amounts = config.getIntegerList("rewards.mob_eggs.amount");
        if (amounts.size() >= 2) {
            return amounts.get(1);
        }
        return 3;
    }
    
    // ===================================
    // БРОНЯ ТЕМНОГО ПОВЕЛИТЕЛЯ
    // ===================================
    
    public int getHelmetCraftCost() {
        return config.getInt("armor.craft_cost.helmet", 6);
    }
    
    public int getChestplateCraftCost() {
        return config.getInt("armor.craft_cost.chestplate", 8);
    }
    
    public int getLeggingsCraftCost() {
        return config.getInt("armor.craft_cost.leggings", 7);
    }
    
    public int getBootsCraftCost() {
        return config.getInt("armor.craft_cost.boots", 4);
    }
    
    // Эффекты полного комплекта
    public int getFullSetDamageResistance() {
        return config.getInt("armor.full_set_effects.damage_resistance", 3);
    }
    
    public boolean isFullSetFireResistance() {
        return config.getBoolean("armor.full_set_effects.fire_resistance", true);
    }
    
    public boolean isFullSetWaterBreathing() {
        return config.getBoolean("armor.full_set_effects.water_breathing", true);
    }
    
    public int getFullSetSpeed() {
        return config.getInt("armor.full_set_effects.speed", 2);
    }
    
    public int getFullSetStrength() {
        return config.getInt("armor.full_set_effects.strength", 1);
    }
    
    public int getFullSetRegeneration() {
        return config.getInt("armor.full_set_effects.regeneration", 1);
    }
    
    public boolean isFullSetNightVision() {
        return config.getBoolean("armor.full_set_effects.night_vision", true);
    }
    
    // Специальные способности
    public int getLightningStrikeChance() {
        return config.getInt("armor.special_abilities.lightning_strike_chance", 15);
    }
    
    public int getTeleportOnDamageChance() {
        return config.getInt("armor.special_abilities.teleport_on_damage_chance", 20);
    }
    
    public double getAreaDamageOnHit() {
        return config.getDouble("armor.special_abilities.area_damage", 4.0);
    }
    
    public boolean isBootsDepthStrider() {
        return config.getBoolean("armor.special_abilities.boots_depth_strider", true);
    }
    
    // ===================================
    // УНИКАЛЬНЫЕ ПРЕДМЕТЫ
    // ===================================
    
    public int getTeleportStaffCooldown() {
        return config.getInt("unique_items.teleport_staff.cooldown", 35);
    }
    
    public int getTeleportStaffDistance() {
        return config.getInt("unique_items.teleport_staff.distance", 20);
    }
    
    public int getEnhancedSwordPullCooldown() {
        return config.getInt("unique_items.enhanced_sword.pull_cooldown", 30);
    }
    
    public int getEnhancedSwordPullDistance() {
        return config.getInt("unique_items.enhanced_sword.pull_distance", 10);
    }
    
    public double getEnhancedSwordPullStrength() {
        return config.getDouble("unique_items.enhanced_sword.pull_strength", 3.0);
    }
    
    public int getShadowBootsInvisibilityDelay() {
        return config.getInt("unique_items.shadow_boots.invisibility_delay", 2) * 20; // секунды в тики
    }
    
    public int getDarkElytraFireworkInterval() {
        return config.getInt("unique_items.dark_elytra.firework_interval", 5);
    }
    
    public int getDarkElytraFireworkPower() {
        return 3; // Константа
    }
    
    public double getDarkElytraLaunchVelocity() {
        return 1.2; // Константа
    }
    
    public int getDarkElytraRepairInterval() {
        return config.getInt("unique_items.dark_elytra.repair_interval", 3);
    }
    
    public int getDarkElytraRepairAmount() {
        return config.getInt("unique_items.dark_elytra.repair_amount", 50);
    }
    
    public double getDarkElytraMaxSpeed() {
        return config.getDouble("unique_items.dark_elytra.max_speed", 3.0);
    }
    
    // ===================================
    // АВТОМАТИЧЕСКИЕ СОБЫТИЯ
    // ===================================
    
    public boolean isEventEnabled() {
        return config.getBoolean("events.enabled", true);
    }
    
    public int getMinSpawnInterval() {
        List<Integer> interval = config.getIntegerList("events.schedule.interval_hours");
        if (interval.size() >= 1) {
            return interval.get(0) * 60; // часы в минуты
        }
        return 120;
    }
    
    public int getMaxSpawnInterval() {
        List<Integer> interval = config.getIntegerList("events.schedule.interval_hours");
        if (interval.size() >= 2) {
            return interval.get(1) * 60; // часы в минуты
        }
        return 240;
    }
    
    public int getAllowedHourStart() {
        List<Integer> time = config.getIntegerList("events.schedule.allowed_time");
        if (time.size() >= 1) {
            return time.get(0);
        }
        return 8;
    }
    
    public int getAllowedHourEnd() {
        List<Integer> time = config.getIntegerList("events.schedule.allowed_time");
        if (time.size() >= 2) {
            return time.get(1);
        }
        return 22;
    }
    
    public int getMinPlayersOnline() {
        return config.getInt("events.schedule.min_players_online", 3);
    }
    
    public List<String> getAllowedWorlds() {
        return config.getStringList("events.spawn_locations.allowed_worlds");
    }
    
    public int getSearchRadius() {
        return config.getInt("events.spawn_locations.search_radius", 1000);
    }
    
    public int getMinDistanceFromPlayers() {
        List<Integer> distance = config.getIntegerList("events.spawn_locations.distance_from_players");
        if (distance.size() >= 1) {
            return distance.get(0);
        }
        return 50;
    }
    
    public int getMaxDistanceFromPlayers() {
        List<Integer> distance = config.getIntegerList("events.spawn_locations.distance_from_players");
        if (distance.size() >= 2) {
            return distance.get(1);
        }
        return 300;
    }
    
    public int getMinSpawnY() {
        List<Integer> height = config.getIntegerList("events.spawn_locations.spawn_height");
        if (height.size() >= 1) {
            return height.get(0);
        }
        return 50;
    }
    
    public int getMaxSpawnY() {
        List<Integer> height = config.getIntegerList("events.spawn_locations.spawn_height");
        if (height.size() >= 2) {
            return height.get(1);
        }
        return 150;
    }
    
    public int getInactiveTimeout() {
        return config.getInt("events.removal.inactive_timeout", 30);
    }
    
    public int getCheckRadius() {
        return config.getInt("events.removal.check_radius", 100);
    }
    
    public int getCheckInterval() {
        return 60; // Константа - 60 секунд
    }
    
    public boolean isSpawnAnnouncementEnabled() {
        return config.getBoolean("events.notifications.spawn_announcement", true);
    }
    
    public int getTitleDuration() {
        return 60; // Константа
    }
    
    public boolean isPeriodicRemindersEnabled() {
        return config.getBoolean("events.notifications.periodic_reminders", true);
    }
    
    public int getReminderInterval() {
        return config.getInt("events.notifications.reminder_interval", 5);
    }
    
    public int getMaxReminders() {
        return config.getInt("events.notifications.max_reminders", 6);
    }
    
    public boolean isRemovalWarningEnabled() {
        return true; // Всегда включено
    }
    
    public int getWarningTime() {
        return config.getInt("events.removal.warning_time", 5);
    }
    
    public int getLocalNotificationRadius() {
        return config.getInt("events.notifications.local_radius", 200);
    }
    
    // ===================================
    // ВИЗУАЛЬНЫЕ ЭФФЕКТЫ
    // ===================================
    
    public double getParticlesMultiplier() {
        return config.getDouble("effects.particles_multiplier", 0.5);
    }
    
    public boolean isAmbientEffectsEnabled() {
        return config.getBoolean("effects.ambient_effects", true);
    }
    
    public boolean isPhaseTransitionEffectsEnabled() {
        return config.getBoolean("effects.phase_transition_effects", true);
    }
    
    public boolean isDeathEffectsEnabled() {
        return config.getBoolean("effects.death_effects", true);
    }
    
    public int getDeathAnimationDuration() {
        return config.getInt("effects.death_animation.duration", 8) * 20; // секунды в тики
    }
    
    public boolean isDeathAnimationEnabled() {
        return config.getBoolean("effects.death_animation.enabled", true);
    }
    
    // ===================================
    // ПРОВОКАЦИИ БОССА
    // ===================================
    
    public boolean isChatTauntsEnabled() {
        return config.getBoolean("chat_taunts.enabled", true);
    }
    
    public int getTauntInterval() {
        return config.getInt("chat_taunts.interval", 45);
    }
    
    public int getTauntChance() {
        return config.getInt("chat_taunts.chance", 60);
    }
    
    public int getTauntRadius() {
        return config.getInt("chat_taunts.radius", 50);
    }
    
    public List<String> getGeneralTaunts() {
        return config.getStringList("messages.taunts.general");
    }
    
    public List<String> getTargetedTaunts() {
        return config.getStringList("messages.taunts.targeted");
    }
    
    public List<String> getDamageTaunts() {
        return config.getStringList("messages.taunts.on_damage");
    }
    
    public List<String> getLowHealthTaunts() {
        return config.getStringList("messages.taunts.low_health");
    }
    
    public List<String> getPhase2Taunts() {
        return List.of(); // Пустой список, не используется
    }
    
    public List<String> getPhase3Taunts() {
        return List.of(); // Пустой список, не используется
    }
    
    // ===================================
    // СООБЩЕНИЯ
    // ===================================
    
    public String getMessage(String path) {
        return ChatColor.translateAlternateColorCodes('&', config.getString("messages." + path, ""));
    }
    
    public String getMessage(String path, String placeholder, String value) {
        String message = getMessage(path);
        return message.replace("%" + placeholder + "%", value);
    }
    
    public String getBossSpawnTitle() {
        return getMessage("boss.spawn_title");
    }
    
    public String getBossSpawnSubtitle() {
        return getMessage("boss.spawn_subtitle");
    }
    
    public String getBossDeathTitle() {
        return getMessage("boss.death_title");
    }
    
    public String getBossDeathSubtitle() {
        return getMessage("boss.death_subtitle");
    }
    
    public String getPhase2Title() {
        return getMessage("boss.phase_2_title");
    }
    
    public String getPhase2Subtitle() {
        return getMessage("boss.phase_2_subtitle");
    }
    
    public String getPhase3Title() {
        return getMessage("boss.phase_3_title");
    }
    
    public String getPhase3Subtitle() {
        return getMessage("boss.phase_3_subtitle");
    }
    
    public String getEventMessage(String path) {
        return getMessage("events." + path);
    }
    
    public String getEventMessage(String path, String placeholder, String value) {
        return getMessage("events." + path, placeholder, value);
    }
    
    public String getEventMessage(String path, String[] placeholders, String[] values) {
        String message = getEventMessage(path);
        for (int i = 0; i < placeholders.length && i < values.length; i++) {
            message = message.replace("%" + placeholders[i] + "%", values[i]);
        }
        return message;
    }
    
    // ===================================
    // ТЕХНИЧЕСКИЕ НАСТРОЙКИ
    // ===================================
    
    public boolean isVerboseLoggingEnabled() {
        return config.getBoolean("technical.debug_logging", false);
    }
    
    public boolean isBossLifecycleLoggingEnabled() {
        return config.getBoolean("technical.debug_logging", false);
    }
    
    public boolean isPdcOperationsLoggingEnabled() {
        return config.getBoolean("technical.debug_logging", false);
    }
    
    public boolean isEntityEventsLoggingEnabled() {
        return config.getBoolean("technical.debug_logging", false);
    }
    
    public int getFragmentsCustomModelData() {
        return config.getInt("technical.fragments_model_data", 77777);
    }
    
    // ===================================
    // СИСТЕМА СЛОЖНОСТИ
    // ===================================
    
    public double getDifficultyHealthMultiplier() {
        String levelPath = "difficulty_settings.level_" + getDifficultyLevel() + ".health_multiplier";
        return config.getDouble(levelPath, 1.0);
    }
    
    public double getDifficultyDamageMultiplier() {
        String levelPath = "difficulty_settings.level_" + getDifficultyLevel() + ".damage_multiplier";
        return config.getDouble(levelPath, 1.0);
    }
    
    public double getDifficultyAbilitiesSpeedMultiplier() {
        String levelPath = "difficulty_settings.level_" + getDifficultyLevel() + ".abilities_speed";
        return config.getDouble(levelPath, 1.0);
    }
    
    public double getDifficultyDropsMultiplier() {
        String levelPath = "difficulty_settings.level_" + getDifficultyLevel() + ".drops_multiplier";
        return config.getDouble(levelPath, 1.0);
    }
    
    public double getDifficultyMinionsCountMultiplier() {
        String levelPath = "difficulty_settings.level_" + getDifficultyLevel() + ".minions_multiplier";
        return config.getDouble(levelPath, 1.0);
    }
    
    public boolean hasDifficultyAdditionalEffects() {
        String levelPath = "difficulty_settings.level_" + getDifficultyLevel() + ".additional_effects";
        return config.getBoolean(levelPath, false);
    }
    
    public boolean hasDifficultyBossRegeneration() {
        String levelPath = "difficulty_settings.level_" + getDifficultyLevel() + ".boss_regeneration";
        return config.getBoolean(levelPath, false);
    }
    
    public boolean hasDifficultyEnhancedAI() {
        String levelPath = "difficulty_settings.level_" + getDifficultyLevel() + ".enhanced_ai";
        return config.getBoolean(levelPath, false);
    }
    
    public boolean hasDifficultyUniqueAbilities() {
        String levelPath = "difficulty_settings.level_" + getDifficultyLevel() + ".unique_abilities";
        return config.getBoolean(levelPath, false);
    }
} 