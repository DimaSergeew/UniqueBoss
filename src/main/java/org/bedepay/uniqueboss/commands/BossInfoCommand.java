package org.bedepay.uniqueboss.commands;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.Location;
import org.bedepay.uniqueboss.boss.UniqueBossManager;
import org.bedepay.uniqueboss.UniqueBoss;
import org.bedepay.uniqueboss.config.ConfigManager;

public class BossInfoCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Эту команду может использовать только игрок!");
            return true;
        }
        
        Player player = (Player) sender;
        
        if (!UniqueBossManager.isBossActive()) {
            player.sendMessage(ChatColor.YELLOW + "В данный момент босс не активен.");
            
            // Показываем информацию об автоматических ивентах
            if (UniqueBoss.getInstance().getEventManager() != null) {
                String status = UniqueBoss.getInstance().getEventManager().getBossStatus();
                player.sendMessage(ChatColor.GRAY + "📅 " + status);
                
                // Показываем информацию о возможных наградах
                player.sendMessage("");
                player.sendMessage(ChatColor.GOLD + "🏆 ВОЗМОЖНЫЕ НАГРАДЫ С БОССА:");
                
                // Динамическая информация об осколках
                ConfigManager config = UniqueBoss.getInstance().getConfigManager();
                int minFragments = config.getFragmentsMinAmount();
                int maxFragments = config.getFragmentsMaxAmount();
                String fragmentsRange = minFragments + "-" + maxFragments;
                
                if (config.isExtraFragmentsEnabled()) {
                    int extraMin = config.getExtraFragmentsMinAmount();
                    int extraMax = config.getExtraFragmentsMaxAmount();
                    int extraChance = config.getExtraFragmentsChance();
                    fragmentsRange += " + " + extraMin + "-" + extraMax + " (" + extraChance + "% шанс)";
                }
                
                if (config.isPlayerCountBonusEnabled()) {
                    double multiplier = config.getPlayerCountBonusMultiplier();
                    int maxPlayers = config.getPlayerCountBonusMaxPlayers();
                    fragmentsRange += " + бонус за команду (до " + (int)(multiplier * maxPlayers * 100) + "%)";
                }
                
                player.sendMessage(ChatColor.DARK_PURPLE + "🖤 " + fragmentsRange + " Осколков Темного Повелителя");
                player.sendMessage(ChatColor.LIGHT_PURPLE + "🦇 Неломающиеся Крылья Тьмы " + ChatColor.GREEN + "(15% шанс)");
                player.sendMessage(ChatColor.RED + "⚔ Клинок Разрушения " + ChatColor.GREEN + "(20% шанс)");
                player.sendMessage(ChatColor.DARK_PURPLE + "🌟 Посох Телепортации " + ChatColor.GREEN + "(10% шанс)");
                player.sendMessage(ChatColor.DARK_GRAY + "👤 Сапоги Теней " + ChatColor.GREEN + "(12% шанс)");
                player.sendMessage(ChatColor.LIGHT_PURPLE + "💎 Кристалл Темной Силы " + ChatColor.GREEN + "(8% шанс)");
                player.sendMessage(ChatColor.YELLOW + "💰 Алмазы, Изумруды, Незерит и другие ресурсы");
            } else {
                player.sendMessage(ChatColor.GRAY + "Используйте /summonboss для вызова Темного Повелителя!");
            }
            
            return true;
        }
        
        // Информация о боссе
        if (UniqueBossManager.getCurrentBoss() != null) {
            int phase = UniqueBossManager.getCurrentBoss().getCurrentPhase();
            LivingEntity bossEntity = (LivingEntity) UniqueBossManager.getCurrentBoss().getEntity();
            double health = bossEntity.getHealth();
            double maxHealth = bossEntity.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH).getValue();
            double healthPercent = (health / maxHealth) * 100;
            
            double distance = player.getLocation().distance(bossEntity.getLocation());
            
            // Координаты босса
            Location bossLoc = bossEntity.getLocation();
            String worldName = bossLoc.getWorld().getName();
            int bossX = bossLoc.getBlockX();
            int bossY = bossLoc.getBlockY();
            int bossZ = bossLoc.getBlockZ();
            
            player.sendMessage(ChatColor.GOLD + "═══════════════════════════════");
            player.sendMessage(ChatColor.DARK_RED + "⚡ ИНФОРМАЦИЯ О ТЕМНОМ ПОВЕЛИТЕЛЕ ⚡");
            player.sendMessage(ChatColor.GOLD + "═══════════════════════════════");
            player.sendMessage(ChatColor.YELLOW + "Текущая фаза: " + getPhaseInfo(phase));
            player.sendMessage(ChatColor.RED + "Здоровье: " + String.format("%.0f", health) + 
                             ChatColor.GRAY + "/" + String.format("%.0f", maxHealth) + 
                             ChatColor.GREEN + " (" + String.format("%.1f", healthPercent) + "%)");
            player.sendMessage(ChatColor.AQUA + "Мир: " + ChatColor.WHITE + worldName);
            player.sendMessage(ChatColor.LIGHT_PURPLE + "Координаты босса: " + ChatColor.WHITE + 
                             bossX + ", " + bossY + ", " + bossZ);
            player.sendMessage(ChatColor.BLUE + "Расстояние до босса: " + String.format("%.1f", distance) + " блоков");
            
            if (distance <= 50) {
                player.sendMessage(ChatColor.GREEN + "✓ Вы находитесь в зоне действия боссбара");
            } else {
                player.sendMessage(ChatColor.RED + "✗ Подойдите ближе чтобы видеть боссбар (< 50 блоков)");
            }
            
            player.sendMessage(ChatColor.GOLD + "═══════════════════════════════");
            
            // Информация об автоматических ивентах если босс активен
            if (UniqueBoss.getInstance().getEventManager() != null) {
                String eventStatus = UniqueBoss.getInstance().getEventManager().getBossStatus();
                player.sendMessage(ChatColor.AQUA + "📅 Статус ивента: " + ChatColor.WHITE + eventStatus);
                player.sendMessage(ChatColor.GOLD + "═══════════════════════════════");
            }
            
            // Информация о способностях текущей фазы
            player.sendMessage(ChatColor.DARK_PURPLE + "Способности текущей фазы:");
            switch (phase) {
                case 1:
                    player.sendMessage(ChatColor.GRAY + "• Огненные шары");
                    player.sendMessage(ChatColor.GRAY + "• Призыв скелетов");
                    player.sendMessage(ChatColor.GRAY + "• Притягивание игроков");
                    break;
                case 2:
                    player.sendMessage(ChatColor.GRAY + "• Все способности фазы 1 (ускоренные)");
                    player.sendMessage(ChatColor.GRAY + "• Телепортация с взрывом");
                    player.sendMessage(ChatColor.GRAY + "• Магические снаряды");
                    player.sendMessage(ChatColor.GRAY + "• Земляные шипы");
                    break;
                case 3:
                    player.sendMessage(ChatColor.GRAY + "• Все предыдущие способности (максимально ускоренные)");
                    player.sendMessage(ChatColor.RED + "• МЕТЕОРИТНЫЙ ДОЖДЬ");
                    player.sendMessage(ChatColor.YELLOW + "• Ослепляющая вспышка");
                    break;
            }
        }
        
        return true;
    }
    
    private String getPhaseInfo(int phase) {
        switch (phase) {
            case 1:
                return ChatColor.GREEN + "1 (Пробуждение)";
            case 2:
                return ChatColor.YELLOW + "2 (Ярость)";
            case 3:
                return ChatColor.RED + "3 (ФИНАЛ)";
            default:
                return ChatColor.GRAY + "Неизвестная";
        }
    }
} 