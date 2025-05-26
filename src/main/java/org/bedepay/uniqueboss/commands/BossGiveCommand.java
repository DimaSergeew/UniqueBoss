package org.bedepay.uniqueboss.commands;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bedepay.uniqueboss.config.ConfigManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class BossGiveCommand implements CommandExecutor, TabCompleter {
    
    private final ConfigManager config;
    
    public BossGiveCommand(ConfigManager config) {
        this.config = config;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("uniqueboss.give")) {
            sender.sendMessage(ChatColor.RED + "У вас нет прав для использования этой команды!");
            return true;
        }
        
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Использование: /bossgive <игрок> <предмет> [количество]");
            sender.sendMessage(ChatColor.YELLOW + "Доступные предметы:");
            sender.sendMessage(ChatColor.GRAY + "• fragment - Осколок Темного Повелителя");
            sender.sendMessage(ChatColor.GRAY + "• elytra - Неломающиеся Крылья Тьмы");
            sender.sendMessage(ChatColor.GRAY + "• sword - Клинок Разрушения");
            sender.sendMessage(ChatColor.GRAY + "• staff - Посох Телепортации");
            sender.sendMessage(ChatColor.GRAY + "• shadowboots - Сапоги Теней");
            sender.sendMessage(ChatColor.GRAY + "• crystal - Кристалл Темной Силы");
            sender.sendMessage(ChatColor.GRAY + "• helmet - Шлем Темного Повелителя");
            sender.sendMessage(ChatColor.GRAY + "• chestplate - Нагрудник Темного Повелителя");
            sender.sendMessage(ChatColor.GRAY + "• leggings - Поножи Темного Повелителя");
            sender.sendMessage(ChatColor.GRAY + "• boots - Сапоги Темного Повелителя");
            return true;
        }
        
        String playerName = args[0];
        String itemType = args[1].toLowerCase();
        int amount = 1;
        
        if (args.length >= 3) {
            try {
                amount = Integer.parseInt(args[2]);
                if (amount <= 0 || amount > 64) {
                    sender.sendMessage(ChatColor.RED + "Количество должно быть от 1 до 64!");
                    return true;
                }
            } catch (NumberFormatException e) {
                sender.sendMessage(ChatColor.RED + "Неверное количество! Используйте число от 1 до 64.");
                return true;
            }
        }
        
        Player targetPlayer = Bukkit.getPlayer(playerName);
        if (targetPlayer == null) {
            sender.sendMessage(ChatColor.RED + "Игрок " + playerName + " не найден или не в сети!");
            return true;
        }
        
        ItemStack item = createBossItem(itemType, amount);
        if (item == null) {
            sender.sendMessage(ChatColor.RED + "Неизвестный предмет: " + itemType);
            return true;
        }
        
        // Выдаем предмет игроку
        if (targetPlayer.getInventory().firstEmpty() != -1) {
            targetPlayer.getInventory().addItem(item);
        } else {
            // Если инвентарь полон, дропаем рядом с игроком
            targetPlayer.getWorld().dropItemNaturally(targetPlayer.getLocation(), item);
            targetPlayer.sendMessage(ChatColor.YELLOW + "Ваш инвентарь полон! Предмет выпал рядом с вами.");
        }
        
        // Сообщения
        String itemDisplayName = getItemDisplayName(itemType);
        targetPlayer.sendMessage(ChatColor.GREEN + "✅ Вы получили " + ChatColor.YELLOW + itemDisplayName + 
                                ChatColor.GREEN + " (x" + amount + ") от администратора!");
        
        sender.sendMessage(ChatColor.GREEN + "✅ Выдан предмет " + ChatColor.YELLOW + itemDisplayName + 
                          ChatColor.GREEN + " (x" + amount + ") игроку " + ChatColor.AQUA + targetPlayer.getName());
        
        // Эффекты
        targetPlayer.getWorld().playSound(targetPlayer.getLocation(), 
            org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.5f, 1.0f);
        targetPlayer.getWorld().spawnParticle(org.bukkit.Particle.TOTEM, 
            targetPlayer.getLocation().add(0, 1, 0), 20, 1, 1, 1, 0.1);
        
        return true;
    }
    
    private ItemStack createBossItem(String itemType, int amount) {
        ItemStack item = null;
        
        switch (itemType) {
            case "fragment":
                item = createDarkFragment();
                break;
            case "elytra":
                item = createUnbreakableElytra();
                break;
            case "sword":
                item = createEnhancedSword();
                break;
            case "staff":
                item = createTeleportStaff();
                break;
            case "shadowboots":
                item = createShadowBoots();
                break;
            case "crystal":
                item = createPowerCrystal();
                break;
            case "helmet":
                item = createDarkLordHelmet();
                break;
            case "chestplate":
                item = createDarkLordChestplate();
                break;
            case "leggings":
                item = createDarkLordLeggings();
                break;
            case "boots":
                item = createDarkLordBoots();
                break;
            default:
                return null;
        }
        
        if (item != null && amount > 1 && item.getType() != Material.ELYTRA && 
            !item.getType().name().contains("HELMET") && !item.getType().name().contains("CHESTPLATE") && 
            !item.getType().name().contains("LEGGINGS") && !item.getType().name().contains("BOOTS") &&
            item.getType() != Material.NETHERITE_SWORD && item.getType() != Material.BLAZE_ROD && 
            item.getType() != Material.END_CRYSTAL) {
            item.setAmount(amount);
        }
        
        return item;
    }
    
    private String getItemDisplayName(String itemType) {
        switch (itemType) {
            case "fragment":
                return "Осколок Темного Повелителя";
            case "elytra":
                return "Неломающиеся Крылья Тьмы";
            case "sword":
                return "Клинок Разрушения";
            case "staff":
                return "Посох Телепортации";
            case "shadowboots":
                return "Сапоги Теней";
            case "crystal":
                return "Кристалл Темной Силы";
            case "helmet":
                return "Шлем Темного Повелителя";
            case "chestplate":
                return "Нагрудник Темного Повелителя";
            case "leggings":
                return "Поножи Темного Повелителя";
            case "boots":
                return "Сапоги Темного Повелителя";
            default:
                return "Неизвестный предмет";
        }
    }
    
    // Методы создания предметов (скопированы из UniqueBossEntity.java)
    
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
        meta.addEnchant(Enchantment.DURABILITY, 10, true);
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
    
    // Методы создания брони (скопированы из ArmorCraftingListener.java)
    
    private ItemStack createDarkLordHelmet() {
        ItemStack helmet = new ItemStack(Material.NETHERITE_HELMET);
        ItemMeta meta = helmet.getItemMeta();
        
        meta.setDisplayName(ChatColor.DARK_RED + "" + ChatColor.BOLD + "👑 ШЛЕМ ТЕМНОГО ПОВЕЛИТЕЛЯ 👑");
        meta.setLore(Arrays.asList(
            ChatColor.GRAY + "Шлем, выкованный из осколков",
            ChatColor.GRAY + "побежденного Темного Повелителя",
            "",
            ChatColor.GOLD + "" + ChatColor.BOLD + "СПОСОБНОСТИ:",
            ChatColor.YELLOW + "🛡️ Защита V",
            ChatColor.YELLOW + "💎 Прочность III",
            ChatColor.YELLOW + "🔧 Починка",
            "",
            ChatColor.LIGHT_PURPLE + "Часть комплекта Темного Повелителя",
            ChatColor.GRAY + "Соберите полный комплект для получения",
            ChatColor.GRAY + "уникальных способностей!",
            "",
            ChatColor.DARK_GRAY + "Уникальный предмет"
        ));
        
        meta.addEnchant(Enchantment.PROTECTION_ENVIRONMENTAL, 5, true);
        meta.addEnchant(Enchantment.DURABILITY, 3, true);
        meta.addEnchant(Enchantment.MENDING, 1, true);
        meta.setCustomModelData(11111);
        
        helmet.setItemMeta(meta);
        return helmet;
    }
    
    private ItemStack createDarkLordChestplate() {
        ItemStack chestplate = new ItemStack(Material.NETHERITE_CHESTPLATE);
        ItemMeta meta = chestplate.getItemMeta();
        
        meta.setDisplayName(ChatColor.DARK_RED + "" + ChatColor.BOLD + "⚡ НАГРУДНИК ТЕМНОГО ПОВЕЛИТЕЛЯ ⚡");
        meta.setLore(Arrays.asList(
            ChatColor.GRAY + "Нагрудник, выкованный из осколков",
            ChatColor.GRAY + "побежденного Темного Повелителя",
            "",
            ChatColor.GOLD + "" + ChatColor.BOLD + "СПОСОБНОСТИ:",
            ChatColor.YELLOW + "🛡️ Защита V",
            ChatColor.YELLOW + "💎 Прочность III", 
            ChatColor.YELLOW + "🔧 Починка",
            "",
            ChatColor.LIGHT_PURPLE + "Часть комплекта Темного Повелителя",
            ChatColor.GRAY + "Соберите полный комплект для получения",
            ChatColor.GRAY + "уникальных способностей!",
            "",
            ChatColor.DARK_GRAY + "Уникальный предмет"
        ));
        
        meta.addEnchant(Enchantment.PROTECTION_ENVIRONMENTAL, 5, true);
        meta.addEnchant(Enchantment.DURABILITY, 3, true);
        meta.addEnchant(Enchantment.MENDING, 1, true);
        meta.setCustomModelData(22222);
        
        chestplate.setItemMeta(meta);
        return chestplate;
    }
    
    private ItemStack createDarkLordLeggings() {
        ItemStack leggings = new ItemStack(Material.NETHERITE_LEGGINGS);
        ItemMeta meta = leggings.getItemMeta();
        
        meta.setDisplayName(ChatColor.DARK_RED + "" + ChatColor.BOLD + "🦵 ПОНОЖИ ТЕМНОГО ПОВЕЛИТЕЛЯ 🦵");
        meta.setLore(Arrays.asList(
            ChatColor.GRAY + "Поножи, выкованные из осколков",
            ChatColor.GRAY + "побежденного Темного Повелителя",
            "",
            ChatColor.GOLD + "" + ChatColor.BOLD + "СПОСОБНОСТИ:",
            ChatColor.YELLOW + "🛡️ Защита V",
            ChatColor.YELLOW + "💎 Прочность III",
            ChatColor.YELLOW + "🔧 Починка",
            "",
            ChatColor.LIGHT_PURPLE + "Часть комплекта Темного Повелителя",
            ChatColor.GRAY + "Соберите полный комплект для получения",
            ChatColor.GRAY + "уникальных способностей!",
            "",
            ChatColor.DARK_GRAY + "Уникальный предмет"
        ));
        
        meta.addEnchant(Enchantment.PROTECTION_ENVIRONMENTAL, 5, true);
        meta.addEnchant(Enchantment.DURABILITY, 3, true);
        meta.addEnchant(Enchantment.MENDING, 1, true);
        meta.setCustomModelData(33333);
        
        leggings.setItemMeta(meta);
        return leggings;
    }
    
    private ItemStack createDarkLordBoots() {
        ItemStack boots = new ItemStack(Material.NETHERITE_BOOTS);
        ItemMeta meta = boots.getItemMeta();
        
        meta.setDisplayName(ChatColor.DARK_RED + "" + ChatColor.BOLD + "👢 САПОГИ ТЕМНОГО ПОВЕЛИТЕЛЯ 👢");
        meta.setLore(Arrays.asList(
            ChatColor.GRAY + "Сапоги, выкованные из осколков",
            ChatColor.GRAY + "побежденного Темного Повелителя",
            "",
            ChatColor.GOLD + "" + ChatColor.BOLD + "СПОСОБНОСТИ:",
            ChatColor.YELLOW + "🛡️ Защита V",
            ChatColor.YELLOW + "💎 Прочность III",
            ChatColor.YELLOW + "🔧 Починка",
            ChatColor.AQUA + "🌊 Подводная ходьба III",
            "",
            ChatColor.LIGHT_PURPLE + "Часть комплекта Темного Повелителя",
            ChatColor.GRAY + "Соберите полный комплект для получения",
            ChatColor.GRAY + "уникальных способностей!",
            "",
            ChatColor.DARK_GRAY + "Уникальный предмет"
        ));
        
        meta.addEnchant(Enchantment.PROTECTION_ENVIRONMENTAL, 5, true);
        meta.addEnchant(Enchantment.DURABILITY, 3, true);
        meta.addEnchant(Enchantment.MENDING, 1, true);
        meta.addEnchant(Enchantment.DEPTH_STRIDER, 3, true);
        meta.setCustomModelData(44444);
        
        boots.setItemMeta(meta);
        return boots;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        
        if (args.length == 1) {
            // Автодополнение имен игроков
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getName().toLowerCase().startsWith(args[0].toLowerCase())) {
                    completions.add(player.getName());
                }
            }
        } else if (args.length == 2) {
            // Автодополнение типов предметов
            String[] items = {"fragment", "elytra", "sword", "staff", "shadowboots", "crystal", 
                             "helmet", "chestplate", "leggings", "boots"};
            for (String item : items) {
                if (item.toLowerCase().startsWith(args[1].toLowerCase())) {
                    completions.add(item);
                }
            }
        } else if (args.length == 3) {
            // Автодополнение количества
            String[] amounts = {"1", "5", "10", "16", "32", "64"};
            for (String amount : amounts) {
                if (amount.startsWith(args[2])) {
                    completions.add(amount);
                }
            }
        }
        
        return completions;
    }
} 