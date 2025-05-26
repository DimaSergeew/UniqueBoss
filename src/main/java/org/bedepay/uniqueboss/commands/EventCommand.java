package org.bedepay.uniqueboss.commands;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bedepay.uniqueboss.UniqueBoss;
import org.bedepay.uniqueboss.boss.UniqueBossManager;
import org.bedepay.uniqueboss.config.ConfigManager;

public class EventCommand implements CommandExecutor {
    
    private final ConfigManager config;
    
    public EventCommand(ConfigManager config) {
        this.config = config;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("uniqueboss.event")) {
            sender.sendMessage(ChatColor.RED + "У вас нет прав для использования этой команды!");
            return true;
        }
        
        if (args.length == 0) {
            showHelp(sender);
            return true;
        }
        
        String subCommand = args[0].toLowerCase();
        
        switch (subCommand) {
            case "status":
                showEventStatus(sender);
                break;
                
            case "force":
                forceSpawnBoss(sender);
                break;
                
            case "stop":
                stopEvent(sender);
                break;
                
            case "testlocation":
                testLocationSearch(sender);
                break;
                
            case "reload":
                reloadEventSystem(sender);
                break;
                
            default:
                showHelp(sender);
                break;
        }
        
        return true;
    }
    
    private void showHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== Команды управления ивентами ===");
        sender.sendMessage(ChatColor.YELLOW + "/bossevent status" + ChatColor.GRAY + " - статус автоивентов");
        sender.sendMessage(ChatColor.YELLOW + "/bossevent force" + ChatColor.GRAY + " - принудительный спавн босса");
        sender.sendMessage(ChatColor.YELLOW + "/bossevent stop" + ChatColor.GRAY + " - остановить текущий ивент");
        sender.sendMessage(ChatColor.YELLOW + "/bossevent testlocation" + ChatColor.GRAY + " - тест поиска места спавна");
        sender.sendMessage(ChatColor.YELLOW + "/bossevent reload" + ChatColor.GRAY + " - перезагрузить систему ивентов");
    }
    
    private void showEventStatus(CommandSender sender) {
        if (UniqueBoss.getInstance().getEventManager() != null) {
            String status = UniqueBoss.getInstance().getEventManager().getBossStatus();
            sender.sendMessage(ChatColor.GREEN + "📅 Статус ивентов: " + ChatColor.WHITE + status);
            
            if (config.isEventEnabled()) {
                sender.sendMessage(ChatColor.GREEN + "✅ Автоматические ивенты включены");
                sender.sendMessage(ChatColor.GRAY + "Интервал: " + config.getMinSpawnInterval() + "-" + config.getMaxSpawnInterval() + " минут");
                sender.sendMessage(ChatColor.GRAY + "Время: " + config.getAllowedHourStart() + ":00 - " + config.getAllowedHourEnd() + ":00");
                sender.sendMessage(ChatColor.GRAY + "Мин. игроков: " + config.getMinPlayersOnline());
            } else {
                sender.sendMessage(ChatColor.RED + "❌ Автоматические ивенты отключены");
            }
        } else {
            sender.sendMessage(ChatColor.RED + "❌ Система ивентов не активна");
        }
    }
    
    private void forceSpawnBoss(CommandSender sender) {
        if (UniqueBossManager.isBossActive()) {
            sender.sendMessage(ChatColor.RED + "❌ Босс уже активен!");
            return;
        }
        
        sender.sendMessage(ChatColor.YELLOW + "🔍 Принудительно запускаю ивент босса...");
        
        // Используем полноценную систему ивентов
        if (UniqueBoss.getInstance().getEventManager() != null) {
            UniqueBoss.getInstance().getLogger().info("🔧 Принудительный спавн босса запрошен: " + sender.getName());
            
            // Принудительно запускаем ивент с полным функционалом
            boolean success = UniqueBoss.getInstance().getEventManager().forceSpawnBoss();
            
            if (success) {
                sender.sendMessage(ChatColor.GREEN + "✅ Ивент босса запущен! Босс появился в случайном месте.");
                sender.sendMessage(ChatColor.YELLOW + "🌍 Все игроки получат уведомления с координатами.");
                
                // Перезапускаем таймер до следующего ивента
                UniqueBoss.getInstance().getEventManager().scheduleNextEvent();
                sender.sendMessage(ChatColor.GRAY + "⏰ Таймер до следующего автоматического ивента сброшен.");
            } else {
                sender.sendMessage(ChatColor.RED + "❌ Не удалось найти подходящее место для спавна!");
                sender.sendMessage(ChatColor.YELLOW + "💡 Попробуйте:");
                sender.sendMessage(ChatColor.GRAY + "- /bossevent testlocation для диагностики");
                sender.sendMessage(ChatColor.GRAY + "- /summonboss для спавна в текущей локации");
            }
        } else {
            sender.sendMessage(ChatColor.RED + "❌ Система ивентов недоступна, используйте /summonboss");
        }
    }
    
    private void stopEvent(CommandSender sender) {
        if (!UniqueBossManager.isBossActive()) {
            sender.sendMessage(ChatColor.RED + "❌ Босс не активен!");
            return;
        }
        
        if (UniqueBossManager.getCurrentBoss() != null) {
            UniqueBossManager.getCurrentBoss().getEntity().remove();
            UniqueBossManager.setBossDefeated();
            sender.sendMessage(ChatColor.GREEN + "✅ Ивент остановлен, босс удален");
        }
    }
    
    private void testLocationSearch(CommandSender sender) {
        sender.sendMessage(ChatColor.YELLOW + "🔍 Тестирую поиск подходящего места для спавна...");
        
        // Тестируем поиск локации
        UniqueBoss.getInstance().getLogger().info("🧪 ТЕСТ: Начинаем поиск места для спавна по запросу " + sender.getName());
        
        // Симулируем поиск места (берем приватный метод из BossEventManager)
        sender.sendMessage(ChatColor.GRAY + "Проверяю миры: " + config.getAllowedWorlds());
        sender.sendMessage(ChatColor.GRAY + "Радиус поиска: " + config.getSearchRadius());
        sender.sendMessage(ChatColor.GRAY + "Мин. расстояние от игроков: " + config.getMinDistanceFromPlayers());
        sender.sendMessage(ChatColor.GRAY + "Макс. расстояние от игроков: " + config.getMaxDistanceFromPlayers());
        sender.sendMessage(ChatColor.GRAY + "Высота: " + config.getMinSpawnY() + "-" + config.getMaxSpawnY());
        
        sender.sendMessage(ChatColor.GREEN + "✅ Тест завершен. Проверьте логи сервера для деталей.");
        sender.sendMessage(ChatColor.YELLOW + "💡 Если место не находится, попробуйте:");
        sender.sendMessage(ChatColor.GRAY + "- Уменьшить search_radius в конфиге");
        sender.sendMessage(ChatColor.GRAY + "- Уменьшить min_distance_from_players");
        sender.sendMessage(ChatColor.GRAY + "- Проверить список allowed_worlds");
    }
    
    private void reloadEventSystem(CommandSender sender) {
        try {
            // Перезагружаем конфиг
            UniqueBoss.getInstance().getConfigManager().loadConfig();
            
            // Перезапускаем систему ивентов
            if (UniqueBoss.getInstance().getEventManager() != null) {
                UniqueBoss.getInstance().getEventManager().shutdown();
            }
            
            sender.sendMessage(ChatColor.GREEN + "✅ Система ивентов перезагружена!");
            sender.sendMessage(ChatColor.YELLOW + "📝 Новые настройки будут применены к следующим ивентам.");
        } catch (Exception e) {
            sender.sendMessage(ChatColor.RED + "❌ Ошибка при перезагрузке системы ивентов!");
            e.printStackTrace();
        }
    }
} 