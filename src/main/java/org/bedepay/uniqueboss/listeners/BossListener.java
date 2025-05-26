package org.bedepay.uniqueboss.listeners;

import org.bukkit.ChatColor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bedepay.uniqueboss.boss.UniqueBossManager;
import org.bedepay.uniqueboss.listeners.BossChunkListener;
import org.bedepay.uniqueboss.config.ConfigManager;

public class BossListener implements Listener {

    private final ConfigManager config;
    
    public BossListener(ConfigManager config) {
        this.config = config;
    }

    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        Entity entity = event.getEntity();
        
        // Проверяем, атакуют ли босса
        if (UniqueBossManager.isBossEntity(entity)) {
            if (event.getDamager() instanceof Player) {
                Player player = (Player) event.getDamager();
                
                // КРИТИЧЕСКИ ВАЖНО: Игроки в неподходящих режимах не должны наносить урон
                if (player.getGameMode() == org.bukkit.GameMode.SPECTATOR) {
                    event.setCancelled(true); // Наблюдатели НИКОГДА не могут атаковать
                    return;
                }
                
                if (player.getGameMode() == org.bukkit.GameMode.CREATIVE && !config.shouldAttackCreative()) {
                    event.setCancelled(true); // Творческий режим не может атаковать (если настройка отключена)
                    return;
                }
                
                // Уменьшаем урон в творческом режиме если атаки разрешены
                if (player.getGameMode() == org.bukkit.GameMode.CREATIVE && config.shouldAttackCreative()) {
                    event.setDamage(event.getDamage() * 0.1); // Очень мало урона
                }
                
                // Уменьшаем урон по боссу (делаем его более стойким) - только для выживания
                if (player.getGameMode() == org.bukkit.GameMode.SURVIVAL || 
                    player.getGameMode() == org.bukkit.GameMode.ADVENTURE) {
                    double damage = event.getDamage();
                    event.setDamage(damage * 0.7); // Уменьшаем урон на 30%
                }
                
                // Уведомляем босса о получении урона для провокаций
                if (UniqueBossManager.getCurrentBoss() != null) {
                    UniqueBossManager.getCurrentBoss().onDamageReceived(player.getName());
                    
                    int phase = UniqueBossManager.getCurrentBoss().getCurrentPhase();
                    double health = ((org.bukkit.entity.LivingEntity) UniqueBossManager.getCurrentBoss().getEntity()).getHealth();
                    double maxHealth = 1000.0; // Начальное здоровье
                    
                    String healthPercent = String.format("%.1f", (health / maxHealth) * 100);
                    
                    player.sendActionBar(ChatColor.RED + "⚔ Атакуете Темного Повелителя " + 
                        ChatColor.YELLOW + "[Фаза " + phase + "] " + 
                        ChatColor.GREEN + healthPercent + "% HP");
                }
            }
        }
    }
    
    @EventHandler
    public void onEntityTarget(EntityTargetEvent event) {
        Entity entity = event.getEntity();
        
        // Проверяем, таргетит ли босс кого-то
        if (UniqueBossManager.isBossEntity(entity) || BossChunkListener.isUniqueBoss(entity)) {
            // КРИТИЧЕСКИ ВАЖНО: Босс должен атаковать ТОЛЬКО игроков
            if (!(event.getTarget() instanceof Player)) {
                // Если босс пытается атаковать не игрока - отменяем
                event.setCancelled(true);
                
                // Принудительно находим ближайшего игрока для атаки
                Player nearestPlayer = findNearestPlayer(entity.getLocation());
                if (nearestPlayer != null && nearestPlayer.getLocation().distance(entity.getLocation()) <= 50) {
                    // Устанавливаем цель на ближайшего игрока (специально для Wither)
                    if (entity instanceof org.bukkit.entity.Wither) {
                        ((org.bukkit.entity.Wither) entity).setTarget(nearestPlayer);
                    }
                }
                return;
            }
            
            // Если босс таргетит игрока - проверяем режим игры
            if (event.getTarget() instanceof Player) {
                Player player = (Player) event.getTarget();
                
                // КРИТИЧЕСКИ ВАЖНО: Не атакуем игроков в неподходящих режимах
                if (player.getGameMode() == org.bukkit.GameMode.SPECTATOR && !config.shouldAttackSpectators()) {
                    event.setCancelled(true); // Отменяем таргет на наблюдателя
                    
                    // Ищем другую цель
                    Player nearestPlayer = findNearestPlayer(entity.getLocation());
                    if (nearestPlayer != null && nearestPlayer.getLocation().distance(entity.getLocation()) <= 50) {
                        if (entity instanceof org.bukkit.entity.Wither) {
                            ((org.bukkit.entity.Wither) entity).setTarget(nearestPlayer);
                        }
                    }
                    return;
                }
                
                if (player.getGameMode() == org.bukkit.GameMode.CREATIVE && !config.shouldAttackCreative()) {
                    event.setCancelled(true); // Отменяем таргет на творческий режим
                    
                    // Ищем другую цель
                    Player nearestPlayer = findNearestPlayer(entity.getLocation());
                    if (nearestPlayer != null && nearestPlayer.getLocation().distance(entity.getLocation()) <= 50) {
                        if (entity instanceof org.bukkit.entity.Wither) {
                            ((org.bukkit.entity.Wither) entity).setTarget(nearestPlayer);
                        }
                    }
                    return;
                }
                
                // Если игрок подходящий - показываем предупреждение
                player.sendTitle(
                    ChatColor.DARK_RED + "⚠ ОПАСНОСТЬ! ⚠",
                    ChatColor.RED + "Темный Повелитель смотрит на вас!",
                    10, 40, 15
                );
                
                // Звук предупреждения
                player.playSound(player.getLocation(), 
                    org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 0.5f);
            }
        }
    }
    
    /**
     * Находит ближайшего игрока к указанной локации
     * ИСКЛЮЧАЕТ игроков в режиме наблюдателя и творческом режиме
     */
    private Player findNearestPlayer(org.bukkit.Location location) {
        Player nearest = null;
        double minDistance = Double.MAX_VALUE;
        
        for (Player player : org.bukkit.Bukkit.getOnlinePlayers()) {
            // КРИТИЧЕСКИ ВАЖНО: Исключаем игроков в неподходящих режимах
            if (player.getGameMode() == org.bukkit.GameMode.SPECTATOR && !config.shouldAttackSpectators()) {
                continue; // Пропускаем наблюдателей (если настройка отключена)
            }
            
            if (player.getGameMode() == org.bukkit.GameMode.CREATIVE && !config.shouldAttackCreative()) {
                continue; // Пропускаем творческий режим (если настройка отключена)
            }
            
            // Исключаем невидимых игроков (vanish плагины)
            if (config.shouldIgnoreVanished() && !player.getCanPickupItems() && player.isInvisible()) {
                continue; // Возможно игрок в vanish
            }
            
            if (player.getWorld().equals(location.getWorld())) {
                double distance = player.getLocation().distance(location);
                if (distance < minDistance) {
                    minDistance = distance;
                    nearest = player;
                }
            }
        }
        
        return nearest;
    }
    
    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        Entity entity = event.getEntity();
        
        // Проверяем, умер ли босс
        if (UniqueBossManager.isBossEntity(entity)) {
            // Очищаем обычный дроп (мы сами управляем наградами)
            event.getDrops().clear();
            event.setDroppedExp(0);
            
            // Логируем смерть босса для отладки
            org.bukkit.Bukkit.getPluginManager().getPlugin("UniqueBoss").getLogger()
                .info("💀 Обнаружена смерть сущности босса, дроп очищен. AI обработает смерть.");
            
            // Менеджер босса сам обработает смерть через AI цикл
        }
    }
    
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        // Если босс активен, добавляем игрока к боссбару если он рядом
        if (UniqueBossManager.isBossActive() && UniqueBossManager.getCurrentBoss() != null) {
            Player player = event.getPlayer();
            
            // Проверяем через небольшую задержку (игрок должен полностью загрузиться)
            org.bukkit.Bukkit.getScheduler().runTaskLater(
                org.bukkit.Bukkit.getPluginManager().getPlugin("UniqueBoss"), 
                () -> {
                    if (player.isOnline() && UniqueBossManager.getCurrentBoss() != null) {
                        double distance = player.getLocation().distance(
                            UniqueBossManager.getCurrentBoss().getEntity().getLocation()
                        );
                        
                        if (distance <= 50) {
                            UniqueBossManager.getCurrentBoss().getBossBar().addPlayer(player);
                        }
                    }
                }, 
                20L // 1 секунда задержки
            );
        }
    }
    
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // Убираем игрока из боссбара
        if (UniqueBossManager.isBossActive() && UniqueBossManager.getCurrentBoss() != null) {
            UniqueBossManager.getCurrentBoss().getBossBar().removePlayer(event.getPlayer());
        }
    }
} 