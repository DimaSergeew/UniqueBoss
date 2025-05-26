package org.bedepay.uniqueboss.commands;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bedepay.uniqueboss.boss.UniqueBossManager;
import org.bedepay.uniqueboss.config.ConfigManager;

public class KillBossCommand implements CommandExecutor {
    
    private final ConfigManager config;
    
    public KillBossCommand(ConfigManager config) {
        this.config = config;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("uniqueboss.kill")) {
            sender.sendMessage(config.getMessage("commands.no_permission"));
            return true;
        }
        
        if (!UniqueBossManager.isBossActive()) {
            sender.sendMessage(config.getMessage("commands.boss_not_active"));
            return true;
        }
        
        if (UniqueBossManager.getCurrentBoss() != null) {
            // Используем forceDestroy() для админской команды (без наград)
            UniqueBossManager.getCurrentBoss().forceDestroy();
            UniqueBossManager.setBossDefeated();
            
            sender.sendMessage(config.getMessage("commands.boss_killed"));
            
            // Если команду использовал игрок, дополнительно сообщаем
            if (sender instanceof Player) {
                Player player = (Player) sender;
                player.sendMessage(ChatColor.GRAY + "Использована админская команда для убийства босса.");
            }
        }
        
        return true;
    }
} 