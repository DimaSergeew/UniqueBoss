package org.bedepay.uniqueboss.commands;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bedepay.uniqueboss.boss.UniqueBossManager;
import org.bedepay.uniqueboss.config.ConfigManager;

public class SummonBossCommand implements CommandExecutor {
    
    private final ConfigManager config;
    
    public SummonBossCommand(ConfigManager config) {
        this.config = config;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(config.getMessage("commands.player_only"));
            return true;
        }
        
        Player player = (Player) sender;
        
        if (!player.hasPermission("uniqueboss.summon")) {
            player.sendMessage(config.getMessage("commands.no_permission"));
            return true;
        }
        
        // Проверяем, не активен ли уже босс
        if (UniqueBossManager.isBossActive()) {
            player.sendMessage(config.getMessage("commands.boss_already_active"));
            return true;
        }
        
        // Вызываем босса
        UniqueBossManager.spawnBoss(player.getLocation(), config);
        
        // Отправляем сообщение ВСЕМ игрокам сервера с координатами
        Location loc = player.getLocation();
        String worldName = loc.getWorld().getName();
        int x = loc.getBlockX();
        int y = loc.getBlockY();
        int z = loc.getBlockZ();
        
        String message1 = config.getBossSpawnTitle();
        String message2 = ChatColor.RED + "Местоположение: " + ChatColor.YELLOW + worldName + 
                         ChatColor.GRAY + " [" + ChatColor.WHITE + x + ", " + y + ", " + z + ChatColor.GRAY + "]";
        String message3 = config.getBossSpawnSubtitle();
        
        // Отправляем всем игрокам на сервере
        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            onlinePlayer.sendMessage(message1);
            onlinePlayer.sendMessage(message2);
            onlinePlayer.sendMessage(message3);
        }
        
        return true;
    }
} 