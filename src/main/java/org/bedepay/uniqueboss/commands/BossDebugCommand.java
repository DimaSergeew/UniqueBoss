package org.bedepay.uniqueboss.commands;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Wither;
import org.bedepay.uniqueboss.boss.UniqueBossManager;
import org.bedepay.uniqueboss.boss.UniqueBossEntity;
import org.bedepay.uniqueboss.config.ConfigManager;

/**
 * Упрощенная команда отладки для администраторов
 * Содержит только основные функции управления боссом
 */
public class BossDebugCommand implements CommandExecutor {

    private final ConfigManager config;

    public BossDebugCommand(ConfigManager config) {
        this.config = config;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("uniqueboss.debug")) {
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
                showBossStatus(sender);
                break;
                
            case "teleport":
                if (sender instanceof Player) {
                    teleportToBoss((Player) sender);
                } else {
                    sender.sendMessage(ChatColor.RED + "Только игроки могут использовать эту команду!");
                }
                break;
                
            case "heal":
                healBoss(sender);
                break;
                
            case "difficulty":
                handleDifficultyCommand(sender, args);
                break;
                
            default:
                showHelp(sender);
                break;
        }

        return true;
    }

    private void showHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== Команды управления боссом ===");
        sender.sendMessage(ChatColor.YELLOW + "/bossdebug status" + ChatColor.GRAY + " - статус босса");
        sender.sendMessage(ChatColor.YELLOW + "/bossdebug teleport" + ChatColor.GRAY + " - телепорт к боссу");
        sender.sendMessage(ChatColor.YELLOW + "/bossdebug heal" + ChatColor.GRAY + " - восстановить здоровье босса");
        sender.sendMessage(ChatColor.YELLOW + "/bossdebug difficulty <1-5>" + ChatColor.GRAY + " - изменить сложность");
        sender.sendMessage(ChatColor.YELLOW + "/bossdebug testdrop" + ChatColor.GRAY + " - тест дропа наград (яйца мобов)");
    }

    private void showBossStatus(CommandSender sender) {
        if (!UniqueBossManager.isBossActive()) {
            sender.sendMessage(ChatColor.RED + "❌ Босс в данный момент неактивен");
            return;
        }

        UniqueBossEntity boss = UniqueBossManager.getCurrentBoss();
        Entity entity = boss.getEntity();
        
        if (entity == null || !entity.isValid()) {
            sender.sendMessage(ChatColor.RED + "❌ Босс существует, но его сущность недоступна");
            return;
        }

        sender.sendMessage(ChatColor.GOLD + "=== Статус Темного Повелителя ===");
        sender.sendMessage(ChatColor.GREEN + "✅ Босс активен и валиден");
        sender.sendMessage(ChatColor.YELLOW + "📍 Фаза: " + ChatColor.WHITE + boss.getCurrentPhase() + "/3");
        
        if (entity instanceof Wither) {
            Wither wither = (Wither) entity;
            double health = wither.getHealth();
            double maxHealth = wither.getMaxHealth();
            double healthPercent = (health / maxHealth) * 100;
            
            sender.sendMessage(ChatColor.YELLOW + "❤️ Здоровье: " + ChatColor.WHITE + 
                String.format("%.1f/%.1f (%.1f%%)", health, maxHealth, healthPercent));
        }
        
        Location loc = entity.getLocation();
        sender.sendMessage(ChatColor.YELLOW + "🌍 Позиция: " + ChatColor.WHITE + 
            String.format("%s: %.1f, %.1f, %.1f", 
                loc.getWorld().getName(), loc.getX(), loc.getY(), loc.getZ()));
        
        sender.sendMessage(ChatColor.YELLOW + "⚔️ Сложность: " + ChatColor.WHITE + 
            config.getDifficultyLevel() + " (" + config.getDifficultyName() + ")");
    }

    private void teleportToBoss(Player player) {
        if (!UniqueBossManager.isBossActive()) {
            player.sendMessage(ChatColor.RED + "❌ Босс в данный момент неактивен");
            return;
        }

        UniqueBossEntity boss = UniqueBossManager.getCurrentBoss();
        Entity entity = boss.getEntity();
        
        if (entity == null || !entity.isValid()) {
            player.sendMessage(ChatColor.RED + "❌ Сущность босса недоступна для телепортации");
            return;
        }

        Location bossLoc = entity.getLocation();
        Location safeLoc = bossLoc.clone().add(0, 5, 0); // Телепорт на 5 блоков выше босса
        
        player.teleport(safeLoc);
        player.sendMessage(ChatColor.GREEN + "✅ Телепорт к Темному Повелителю выполнен!");
        player.sendMessage(ChatColor.YELLOW + "📍 Фаза " + boss.getCurrentPhase() + "/3");
    }

    private void healBoss(CommandSender sender) {
        if (!UniqueBossManager.isBossActive()) {
            sender.sendMessage(ChatColor.RED + "❌ Босс в данный момент неактивен");
            return;
        }

        UniqueBossEntity boss = UniqueBossManager.getCurrentBoss();
        Entity entity = boss.getEntity();
        
        if (entity == null || !entity.isValid() || !(entity instanceof Wither)) {
            sender.sendMessage(ChatColor.RED + "❌ Сущность босса недоступна");
            return;
        }

        Wither wither = (Wither) entity;
        wither.setHealth(wither.getMaxHealth());
        
        sender.sendMessage(ChatColor.GREEN + "✅ Здоровье босса полностью восстановлено!");
        sender.sendMessage(ChatColor.YELLOW + "❤️ Новое здоровье: " + wither.getHealth() + "/" + wither.getMaxHealth());
    }

    private void handleDifficultyCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.YELLOW + "⚔️ Текущая сложность: " + 
                ChatColor.WHITE + config.getDifficultyLevel() + " (" + config.getDifficultyName() + ")");
            sender.sendMessage(ChatColor.GRAY + "Использование: /bossdebug difficulty <1-5>");
            sender.sendMessage(ChatColor.GRAY + "1=Легкий, 2=Простой, 3=Нормальный, 4=Сложный, 5=Экстремальный");
            return;
        }

        try {
            int newLevel = Integer.parseInt(args[1]);
            
            if (newLevel < 1 || newLevel > 5) {
                sender.sendMessage(ChatColor.RED + "❌ Уровень сложности должен быть от 1 до 5!");
                return;
            }

            // Обновляем конфиг (требует перезагрузки для применения)
            sender.sendMessage(ChatColor.YELLOW + "⚠️ Изменение сложности требует перезагрузки конфига или перезапуска сервера");
            sender.sendMessage(ChatColor.GRAY + "Установите в config.yml: boss.difficulty_level: " + newLevel);
            sender.sendMessage(ChatColor.GRAY + "Затем выполните: /uniqueboss reload");
            
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "❌ Некорректный уровень сложности: " + args[1]);
        }
    }
    
    private void testBossDrop(Player player) {
        player.sendMessage(ChatColor.GOLD + "🧪 Тестирование дропа босса...");
        
        // Создаем тестовый объект босса для вызова метода создания наград
        UniqueBossEntity testBoss = new UniqueBossEntity(player.getLocation(), config);
        
        try {
            // Используем рефлексию для доступа к приватному методу createUniqueRewards
            java.lang.reflect.Method method = UniqueBossEntity.class.getDeclaredMethod("createUniqueRewards");
            method.setAccessible(true);
            
            @SuppressWarnings("unchecked")
            java.util.List<org.bukkit.inventory.ItemStack> rewards = 
                (java.util.List<org.bukkit.inventory.ItemStack>) method.invoke(testBoss);
            
            player.sendMessage(ChatColor.GREEN + "✅ Сгенерировано " + rewards.size() + " наград:");
            
            // Подсчитываем яйца мобов
            int mobEggCount = 0;
            for (org.bukkit.inventory.ItemStack item : rewards) {
                if (item.getType().name().endsWith("_SPAWN_EGG")) {
                    mobEggCount++;
                    player.sendMessage(ChatColor.LIGHT_PURPLE + "  🥚 " + item.getType().name() + " x" + item.getAmount());
                }
            }
            
            if (mobEggCount == 0) {
                player.sendMessage(ChatColor.YELLOW + "⚠️ Яйца мобов не выпали в этом тесте");
                player.sendMessage(ChatColor.GRAY + "Это нормально, шанс выпадения: " + config.getMobEggsChance() + "%");
            } else {
                player.sendMessage(ChatColor.GREEN + "✅ Всего яиц мобов: " + mobEggCount);
            }
            
            // Отдаем игроку награды для проверки
            Location dropLoc = player.getLocation().add(0, 1, 0);
            for (org.bukkit.inventory.ItemStack reward : rewards) {
                player.getWorld().dropItemNaturally(dropLoc, reward);
            }
            
            player.sendMessage(ChatColor.AQUA + "📦 Все награды выброшены рядом с вами!");
            
        } catch (Exception e) {
            player.sendMessage(ChatColor.RED + "❌ Ошибка при тестировании дропа: " + e.getMessage());
            e.printStackTrace();
        }
    }
} 