package org.bedepay.uniqueboss.commands;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bedepay.uniqueboss.UniqueBoss;

public class ReloadConfigCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("uniqueboss.reload")) {
            sender.sendMessage(ChatColor.RED + "У вас нет прав для использования этой команды!");
            return true;
        }
        
        try {
            // Перезагружаем конфиг
            UniqueBoss.getInstance().getConfigManager().loadConfig();
            
            // Перезапускаем ивент-систему с новыми настройками
            if (UniqueBoss.getInstance().getEventManager() != null) {
                UniqueBoss.getInstance().getEventManager().scheduleNextEvent();
                sender.sendMessage(ChatColor.AQUA + "🔄 Перезапущена система автоматических ивентов с новыми настройками");
            }
            
            sender.sendMessage(ChatColor.GREEN + "✅ Конфигурация плагина UniqueBoss перезагружена!");
            sender.sendMessage(ChatColor.YELLOW + "📝 Новые настройки применены ко всем системам.");
        } catch (Exception e) {
            sender.sendMessage(ChatColor.RED + "❌ Ошибка при перезагрузке конфигурации!");
            sender.sendMessage(ChatColor.RED + "Проверьте синтаксис файла config.yml");
            e.printStackTrace();
        }
        
        return true;
    }
} 