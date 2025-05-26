package org.bedepay.uniqueboss.listeners;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;
import org.bedepay.uniqueboss.config.ConfigManager;
import io.papermc.paper.event.server.ServerResourcesReloadedEvent;

import java.util.Arrays;

public class ArmorCraftingListener implements Listener {
    
    private final ConfigManager config;
    private final Plugin plugin;
    
    public ArmorCraftingListener(ConfigManager config, Plugin plugin) {
        this.config = config;
        this.plugin = plugin;
    }
    
    @EventHandler
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        Recipe recipe = event.getRecipe();
        
        if (recipe == null) return;
        
        // КРИТИЧЕСКАЯ ЗАЩИТА: Проверяем не пытается ли игрок использовать наши осколки в чужих рецептах
        if (recipe instanceof ShapedRecipe) {
            ShapedRecipe shapedRecipe = (ShapedRecipe) recipe;
            String key = shapedRecipe.getKey().getKey();
            
            // Если это НЕ наш рецепт, но в сетке есть наши осколки - блокируем!
            if (!key.startsWith("dark_lord_")) {
                CraftingInventory inventory = event.getInventory();
                boolean hasDarkFragments = false;
                
                for (int i = 1; i <= 9; i++) {
                    ItemStack item = inventory.getItem(i);
                    if (isDarkFragment(item)) {
                        hasDarkFragments = true;
                        break;
                    }
                }
                
                if (hasDarkFragments) {
                    // Блокируем чужой рецепт с нашими осколками!
                    event.getInventory().setResult(null);
                    plugin.getLogger().info("🛡️ ЗАЩИТА: Заблокирован рецепт " + key + " с осколками босса");
                    return;
                }
            }
        }
        
        // Проверяем наши кастомные рецепты
        if (recipe instanceof ShapedRecipe) {
            ShapedRecipe shapedRecipe = (ShapedRecipe) recipe;
            String key = shapedRecipe.getKey().getKey();
            
            if (key.startsWith("dark_lord_")) {
                if (config.isVerboseLoggingEnabled()) {
                    plugin.getLogger().info("🔍 DEBUG: Рецепт " + key + " показывается в верстаке");
                }
                
                // ВАЖНО: НЕ блокируем отображение рецепта!
                // Проверки должны быть только в onCraftItem
                
                // Проверяем есть ли призматические осколки вообще
                CraftingInventory inventory = event.getInventory();
                boolean hasPrismarineShards = false;
                
                for (int i = 1; i <= 9; i++) { // Слоты крафта (без результата)
                    ItemStack item = inventory.getItem(i);
                    if (item != null && item.getType() == Material.PRISMARINE_SHARD) {
                        hasPrismarineShards = true;
                        
                        // Проверяем, является ли это нашим осколком
                        if (isDarkFragment(item)) {
                            if (config.isVerboseLoggingEnabled()) {
                                plugin.getLogger().info("✅ DEBUG: Найден валидный осколок в слоте " + i + 
                                    ": " + (item.hasItemMeta() && item.getItemMeta().hasDisplayName() ? 
                                    item.getItemMeta().getDisplayName() : "без названия"));
                            }
                        } else {
                            if (config.isVerboseLoggingEnabled()) {
                                plugin.getLogger().info("⚠️ DEBUG: Найден НЕвалидный призматический осколок в слоте " + i);
                            }
                        }
                    }
                }
                
                if (!hasPrismarineShards && config.isVerboseLoggingEnabled()) {
                    plugin.getLogger().info("ℹ️ DEBUG: Нет призматических осколков в сетке крафта");
                }
            }
        }
    }
    
    @EventHandler
    public void onCraftItem(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        
        Player player = (Player) event.getWhoClicked();
        Recipe recipe = event.getRecipe();
        
        if (recipe instanceof ShapedRecipe) {
            ShapedRecipe shapedRecipe = (ShapedRecipe) recipe;
            String key = shapedRecipe.getKey().getKey();
            
            if (key.startsWith("dark_lord_")) {
                if (config.isVerboseLoggingEnabled()) {
                    plugin.getLogger().info("DEBUG: Игрок " + player.getName() + 
                        " пытается скрафтить " + key);
                }
                handleDarkLordArmorCraft(player, key, event);
            } else {
                // ДОПОЛНИТЕЛЬНАЯ ЗАЩИТА: Запрещаем использовать наши осколки в чужих рецептах
                CraftingInventory inventory = event.getInventory();
                boolean hasDarkFragments = false;
                
                for (int i = 1; i <= 9; i++) {
                    ItemStack item = inventory.getItem(i);
                    if (isDarkFragment(item)) {
                        hasDarkFragments = true;
                        break;
                    }
                }
                
                if (hasDarkFragments) {
                    event.setCancelled(true);
                    player.sendMessage(ChatColor.RED + "❌ Осколки Темного Повелителя можно использовать только для крафта его брони!");
                    plugin.getLogger().info("🛡️ ЗАЩИТА: Игрок " + player.getName() + 
                        " пытался использовать осколки босса в рецепте " + key);
                }
            }
        }
    }
    
    /**
     * КРИТИЧЕСКИ ВАЖНО: Обработчик перезагрузки ресурсов сервера
     * Автоматически перерегистрирует рецепты после /reload или /minecraft:reload
     */
    @EventHandler
    public void onServerResourcesReloaded(ServerResourcesReloadedEvent event) {
        plugin.getLogger().info("🔄 ОБНАРУЖЕНА ПЕРЕЗАГРУЗКА РЕСУРСОВ СЕРВЕРА!");
        plugin.getLogger().info("📋 Причина: " + event.getCause().name());
        plugin.getLogger().info("🔧 Автоматически перерегистрируем рецепты крафта брони...");
        
        // Задержка для стабильности (дожидаемся полной загрузки ресурсов)
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            registerRecipes();
            plugin.getLogger().info("✅ Рецепты крафта брони восстановлены после перезагрузки!");
        }, 40L); // 2 секунды задержки
    }
    
    private void handleDarkLordArmorCraft(Player player, String armorType, CraftItemEvent event) {
        int requiredFragments = getRequiredFragments(armorType);
        
        // ИСПРАВЛЕНИЕ: Считаем осколки в СЕТКЕ КРАФТА, а не в общем инвентаре!
        CraftingInventory craftingInv = event.getInventory();
        int fragmentsInCraftingGrid = countDarkFragmentsInCraftingGrid(craftingInv);
        int playerFragments = countDarkFragments(player); // Для логирования
        
        plugin.getLogger().info("🔨 ПОПЫТКА КРАФТА " + armorType.toUpperCase() + ":");
        plugin.getLogger().info("   👤 Игрок: " + player.getName());
        plugin.getLogger().info("   📋 Требуется осколков: " + requiredFragments);
        plugin.getLogger().info("   🛠️ В сетке крафта: " + fragmentsInCraftingGrid + " валидных осколков");
        plugin.getLogger().info("   💰 В общем инвентаре: " + playerFragments + " валидных осколков");
        
        if (fragmentsInCraftingGrid < requiredFragments) {
            event.setCancelled(true);
            plugin.getLogger().info("❌ КРАФТ ОТМЕНЕН: недостаточно осколков в сетке крафта");
            player.sendMessage(ChatColor.RED + "❌ Недостаточно валидных осколков в сетке крафта! Требуется: " + 
                requiredFragments + ", в сетке: " + fragmentsInCraftingGrid);
            return;
        }
        
        // НЕ убираем осколки вручную - пусть Minecraft сам их использует как обычно!
        // Только проверяем что в сетке достаточно валидных осколков
        
        // Успешный крафт
        String itemName = getArmorDisplayName(armorType);
        player.sendMessage(ChatColor.GREEN + "✅ Успешно создан предмет: " + ChatColor.GOLD + itemName);
        
        // Эффекты успешного крафта
        player.getWorld().playSound(player.getLocation(), 
            org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.5f, 1.0f);
        player.getWorld().spawnParticle(org.bukkit.Particle.TOTEM, 
            player.getLocation().add(0, 1, 0), 20, 1, 1, 1, 0.1);
            
        plugin.getLogger().info("✅ Игрок " + player.getName() + " успешно скрафтил " + itemName + 
            " (использовано " + fragmentsInCraftingGrid + " осколков из сетки)");
    }
    
    private int getRequiredFragments(String armorType) {
        switch (armorType) {
            case "dark_lord_helmet":
                return 6; // Фиксированные значения для стабильности
            case "dark_lord_chestplate":
                return 8;
            case "dark_lord_leggings":
                return 7;
            case "dark_lord_boots":
                return 4;
            default:
                return 8;
        }
    }
    
    private String getArmorDisplayName(String armorType) {
        switch (armorType) {
            case "dark_lord_helmet":
                return "Шлем Темного Повелителя";
            case "dark_lord_chestplate":
                return "Нагрудник Темного Повелителя";
            case "dark_lord_leggings":
                return "Поножи Темного Повелителя";
            case "dark_lord_boots":
                return "Сапоги Темного Повелителя";
            default:
                return "Доспех Темного Повелителя";
        }
    }
    
    private boolean hasEnoughFragments(Player player, String armorType) {
        int required = getRequiredFragments(armorType);
        int available = countDarkFragments(player);
        return available >= required;
    }
    
    /**
     * СОВМЕСТИМАЯ С 1.20.4 проверка осколков
     */
    private boolean isDarkFragment(ItemStack item) {
        if (item == null || item.getType() != Material.PRISMARINE_SHARD) return false;
        if (!item.hasItemMeta()) return false;
        
        ItemMeta meta = item.getItemMeta();
        
        // ОСНОВНАЯ проверка по названию (самый надежный способ)
        if (meta.hasDisplayName()) {
            String displayName = ChatColor.stripColor(meta.getDisplayName());
            if (displayName.contains("ОСКОЛОК ТЕМНОГО ПОВЕЛИТЕЛЯ") || 
                displayName.contains("Осколок Темного Повелителя")) {
                if (config.isVerboseLoggingEnabled()) {
                    plugin.getLogger().info("DEBUG: Найден осколок по названию: " + displayName);
                }
                return true;
            }
        }
        
        // Дополнительная проверка по CustomModelData
        if (meta.hasCustomModelData() && meta.getCustomModelData() == config.getFragmentsCustomModelData()) {
            if (config.isVerboseLoggingEnabled()) {
                plugin.getLogger().info("DEBUG: Найден осколок по CustomModelData: " + config.getFragmentsCustomModelData());
            }
            return true;
        }
        
        return false;
    }
    
    /**
     * НОВЫЙ МЕТОД: Считает валидные осколки только в сетке крафта (слоты 1-9)
     */
    private int countDarkFragmentsInCraftingGrid(CraftingInventory craftingInv) {
        int count = 0;
        plugin.getLogger().info("🔍 АНАЛИЗ ОСКОЛКОВ В СЕТКЕ КРАФТА:");
        
        for (int slot = 1; slot <= 9; slot++) { // Слоты сетки крафта (без результата)
            ItemStack item = craftingInv.getItem(slot);
            if (item != null && item.getType() == Material.PRISMARINE_SHARD) {
                plugin.getLogger().info("📍 Слот крафта " + slot + ": призматический осколок, количество: " + item.getAmount());
                
                if (isDarkFragment(item)) {
                    count += item.getAmount();
                    plugin.getLogger().info("✅ Слот крафта " + slot + ": " + item.getAmount() + " ВАЛИДНЫХ осколков");
                } else {
                    plugin.getLogger().info("❌ Слот крафта " + slot + ": " + item.getAmount() + " НЕВАЛИДНЫХ осколков");
                }
            }
        }
        
        plugin.getLogger().info("📊 ИТОГО в сетке крафта: " + count + " валидных осколков");
        return count;
    }

    private int countDarkFragments(Player player) {
        int count = 0;
        plugin.getLogger().info("🔍 ДЕТАЛЬНЫЙ АНАЛИЗ ОСКОЛКОВ У " + player.getName() + ":");
        
        for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
            ItemStack item = player.getInventory().getItem(slot);
            if (item != null && item.getType() == Material.PRISMARINE_SHARD) {
                plugin.getLogger().info("📍 Слот " + slot + ": найден призматический осколок, количество: " + item.getAmount());
                
                if (isDarkFragment(item)) {
                    count += item.getAmount();
                    plugin.getLogger().info("✅ Слот " + slot + ": " + item.getAmount() + " ВАЛИДНЫХ осколков");
                    
                    // Детали валидного осколка
                    ItemMeta meta = item.getItemMeta();
                    if (meta != null) {
                        if (meta.hasDisplayName()) {
                            plugin.getLogger().info("   📝 DisplayName: " + meta.getDisplayName());
                        }
                        if (meta.hasCustomModelData()) {
                            plugin.getLogger().info("   🎨 CustomModelData: " + meta.getCustomModelData());
                        }
                        if (meta.hasLore()) {
                            plugin.getLogger().info("   📜 Лор: " + meta.getLore().size() + " строк");
                        }
                    }
                } else {
                    plugin.getLogger().info("❌ Слот " + slot + ": " + item.getAmount() + " НЕВАЛИДНЫХ осколков");
                    
                    // Детали невалидного осколка для диагностики
                    ItemMeta meta = item.getItemMeta();
                    if (meta != null) {
                        if (!meta.hasDisplayName()) {
                            plugin.getLogger().info("   ⚠️ Проблема: Нет displayName");
                        } else {
                            String displayName = ChatColor.stripColor(meta.getDisplayName());
                            plugin.getLogger().info("   📝 DisplayName: " + meta.getDisplayName());
                            plugin.getLogger().info("   📝 Очищенное название: " + displayName);
                            
                            if (!displayName.contains("ОСКОЛОК ТЕМНОГО ПОВЕЛИТЕЛЯ") && 
                                !displayName.contains("Осколок Темного Повелителя")) {
                                plugin.getLogger().info("   ❌ Название НЕ содержит требуемый текст");
                            }
                        }
                        
                        if (!meta.hasCustomModelData()) {
                            plugin.getLogger().info("   ⚠️ Проблема: Нет CustomModelData");
                        } else {
                            plugin.getLogger().info("   🎨 CustomModelData: " + meta.getCustomModelData() + 
                                " (требуется: " + config.getFragmentsCustomModelData() + ")");
                            
                            if (meta.getCustomModelData() != config.getFragmentsCustomModelData()) {
                                plugin.getLogger().info("   ❌ CustomModelData НЕ соответствует требуемому");
                            }
                        }
                    } else {
                        plugin.getLogger().info("   ⚠️ Проблема: Нет ItemMeta");
                    }
                }
            }
        }
        
        plugin.getLogger().info("📊 ИТОГО у " + player.getName() + ": " + count + " валидных осколков");
        return count;
    }
    
    private void removeDarkFragments(Player player, int amount) {
        int remaining = amount;
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (isDarkFragment(item)) {
                int itemAmount = item.getAmount();
                if (itemAmount <= remaining) {
                    player.getInventory().setItem(i, null);
                    remaining -= itemAmount;
                } else {
                    item.setAmount(itemAmount - remaining);
                    remaining = 0;
                }
                
                if (remaining <= 0) break;
            }
        }
        plugin.getLogger().info("DEBUG: Удалено " + (amount - remaining) + " осколков у " + player.getName());
    }
    
    // СОВМЕСТИМЫЕ С 1.20.4 методы для создания рецептов
    public void registerRecipes() {
        plugin.getLogger().info("🔧 Начинаем регистрацию рецептов крафта брони Темного Повелителя...");
        plugin.getLogger().info("🎯 Версия: Paper 1.20.4+ совместимая");
        
        try {
            // Удаляем старые рецепты
            removeExistingRecipes();
            
            // Задержка для стабильности в 1.20.4
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                int registered = 0;
                
                // Регистрируем рецепты с детальным логированием
                if (registerHelmetRecipe()) {
                    registered++;
                    plugin.getLogger().info("✅ Шлем зарегистрирован");
                } else {
                    plugin.getLogger().warning("❌ Шлем НЕ зарегистрирован");
                }
                
                if (registerChestplateRecipe()) {
                    registered++;
                    plugin.getLogger().info("✅ Нагрудник зарегистрирован");
                } else {
                    plugin.getLogger().warning("❌ Нагрудник НЕ зарегистрирован");
                }
                
                if (registerLeggingsRecipe()) {
                    registered++;
                    plugin.getLogger().info("✅ Поножи зарегистрированы");
                } else {
                    plugin.getLogger().warning("❌ Поножи НЕ зарегистрированы");
                }
                
                if (registerBootsRecipe()) {
                    registered++;
                    plugin.getLogger().info("✅ Сапоги зарегистрированы");
                } else {
                    plugin.getLogger().warning("❌ Сапоги НЕ зарегистрированы");
                }
                
                plugin.getLogger().info("📊 ИТОГО зарегистрировано: " + registered + "/4 рецептов");
                
                // Проверка через 3 секунды
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    verifyRecipeRegistration();
                }, 60L);
                
            }, 40L); // 2 секунды задержки для 1.20.4
            
        } catch (Exception e) {
            plugin.getLogger().severe("❌ КРИТИЧЕСКАЯ ошибка регистрации рецептов: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private void verifyRecipeRegistration() {
        plugin.getLogger().info("🔍 Проверяем регистрацию рецептов крафта брони...");
        
        String[] recipeKeys = {"dark_lord_helmet", "dark_lord_chestplate", "dark_lord_leggings", "dark_lord_boots"};
        String[] recipeNames = {"Шлем Темного Повелителя", "Нагрудник Темного Повелителя", "Поножи Темного Повелителя", "Сапоги Темного Повелителя"};
        int foundRecipes = 0;
        
        for (int i = 0; i < recipeKeys.length; i++) {
            String key = recipeKeys[i];
            String name = recipeNames[i];
            NamespacedKey namespacedKey = new NamespacedKey(plugin, key);
            Recipe recipe = plugin.getServer().getRecipe(namespacedKey);
            
            if (recipe != null) {
                foundRecipes++;
                plugin.getLogger().info("✅ Рецепт работает: " + name + " (" + key + ")");
            } else {
                plugin.getLogger().warning("❌ Рецепт НЕ найден: " + name + " (" + key + ")");
            }
        }
        
        if (foundRecipes == 4) {
            plugin.getLogger().info("🎉 ВСЕ 4 РЕЦЕПТА БРОНИ УСПЕШНО РАБОТАЮТ!");
            plugin.getLogger().info("💡 Игроки могут крафтить броню из осколков на верстаке");
        } else {
            plugin.getLogger().warning("⚠️ КРИТИЧЕСКАЯ ПРОБЛЕМА: работает только " + foundRecipes + "/4 рецептов!");
            plugin.getLogger().warning("🔧 Попробуйте команду /bossdebug recipes check-registration");
            plugin.getLogger().warning("🔄 Или выполните /reload для перерегистрации");
        }
    }
    
    public void removeExistingRecipes() {
        try {
            String[] recipeNames = {"dark_lord_helmet", "dark_lord_chestplate", "dark_lord_leggings", "dark_lord_boots"};
            int removed = 0;
            
            for (String name : recipeNames) {
                NamespacedKey key = new NamespacedKey(plugin, name);
                if (plugin.getServer().removeRecipe(key)) {
                    removed++;
                    plugin.getLogger().info("🗑️ Удален старый рецепт: " + name);
                }
            }
            
            if (removed > 0) {
                plugin.getLogger().info("🗑️ Удалено " + removed + " старых рецептов");
            }
        } catch (Exception e) {
            plugin.getLogger().warning("⚠️ Ошибка при удалении старых рецептов: " + e.getMessage());
        }
    }
    
    private boolean registerHelmetRecipe() {
        try {
            ItemStack helmet = createDarkLordHelmet();
            NamespacedKey key = new NamespacedKey(plugin, "dark_lord_helmet");
            
            ShapedRecipe recipe = new ShapedRecipe(key, helmet);
            recipe.shape("FFF", "F F", "   ");
            
            // КРИТИЧЕСКОЕ ИСПРАВЛЕНИЕ: Используем только Material для большей совместимости
            recipe.setIngredient('F', Material.PRISMARINE_SHARD);
            
            plugin.getLogger().info("📝 Регистрируем рецепт шлема с Material.PRISMARINE_SHARD");
            return plugin.getServer().addRecipe(recipe);
        } catch (Exception e) {
            plugin.getLogger().severe("❌ Ошибка регистрации шлема: " + e.getMessage());
            return false;
        }
    }
    
    private boolean registerChestplateRecipe() {
        try {
            ItemStack chestplate = createDarkLordChestplate();
            NamespacedKey key = new NamespacedKey(plugin, "dark_lord_chestplate");
            
            ShapedRecipe recipe = new ShapedRecipe(key, chestplate);
            recipe.shape("F F", "FFF", "FFF");
            
            // КРИТИЧЕСКОЕ ИСПРАВЛЕНИЕ: Используем только Material для большей совместимости
            recipe.setIngredient('F', Material.PRISMARINE_SHARD);
            
            plugin.getLogger().info("📝 Регистрируем рецепт нагрудника с Material.PRISMARINE_SHARD");
            return plugin.getServer().addRecipe(recipe);
        } catch (Exception e) {
            plugin.getLogger().severe("❌ Ошибка регистрации нагрудника: " + e.getMessage());
            return false;
        }
    }
    
    private boolean registerLeggingsRecipe() {
        try {
            ItemStack leggings = createDarkLordLeggings();
            NamespacedKey key = new NamespacedKey(plugin, "dark_lord_leggings");
            
            ShapedRecipe recipe = new ShapedRecipe(key, leggings);
            recipe.shape("FFF", "F F", "F F");
            
            // КРИТИЧЕСКОЕ ИСПРАВЛЕНИЕ: Используем только Material для большей совместимости
            recipe.setIngredient('F', Material.PRISMARINE_SHARD);
            
            plugin.getLogger().info("📝 Регистрируем рецепт поножей с Material.PRISMARINE_SHARD");
            return plugin.getServer().addRecipe(recipe);
        } catch (Exception e) {
            plugin.getLogger().severe("❌ Ошибка регистрации поножей: " + e.getMessage());
            return false;
        }
    }
    
    private boolean registerBootsRecipe() {
        try {
            ItemStack boots = createDarkLordBoots();
            NamespacedKey key = new NamespacedKey(plugin, "dark_lord_boots");
            
            ShapedRecipe recipe = new ShapedRecipe(key, boots);
            recipe.shape("   ", "F F", "F F"); // Возвращаем оригинальную форму
            
            // КРИТИЧЕСКОЕ ИСПРАВЛЕНИЕ: Используем только Material для большей совместимости
            recipe.setIngredient('F', Material.PRISMARINE_SHARD);
            
            plugin.getLogger().info("📝 Регистрируем рецепт сапог с Material.PRISMARINE_SHARD (4 осколка)");
            return plugin.getServer().addRecipe(recipe);
        } catch (Exception e) {
            plugin.getLogger().severe("❌ Ошибка регистрации сапог: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * СОВМЕСТИМЫЙ С 1.20.4 метод создания осколков
     */
    private ItemStack createDarkFragment() {
        ItemStack fragment = new ItemStack(Material.PRISMARINE_SHARD);
        ItemMeta meta = fragment.getItemMeta();
        
        if (meta != null) {
            // Простое, ясное название без сложного форматирования
            meta.setDisplayName(ChatColor.DARK_PURPLE + "" + ChatColor.BOLD + "🖤 ОСКОЛОК ТЕМНОГО ПОВЕЛИТЕЛЯ 🖤");
            meta.setCustomModelData(config.getFragmentsCustomModelData());
            
            // Лор для дополнительной идентификации
            meta.setLore(Arrays.asList(
                ChatColor.GRAY + "Материал для крафта брони",
                ChatColor.DARK_GRAY + "Уникальный ингредиент",
                ChatColor.YELLOW + "Поместите в форме доспеха на верстак"
            ));
            
            fragment.setItemMeta(meta);
        }
        
        return fragment;
    }
    
    private ItemStack createDarkLordHelmet() {
        ItemStack helmet = new ItemStack(Material.NETHERITE_HELMET);
        ItemMeta meta = helmet.getItemMeta();
        
        if (meta != null) {
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
        }
        
        return helmet;
    }
    
    private ItemStack createDarkLordChestplate() {
        ItemStack chestplate = new ItemStack(Material.NETHERITE_CHESTPLATE);
        ItemMeta meta = chestplate.getItemMeta();
        
        if (meta != null) {
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
        }
        
        return chestplate;
    }
    
    private ItemStack createDarkLordLeggings() {
        ItemStack leggings = new ItemStack(Material.NETHERITE_LEGGINGS);
        ItemMeta meta = leggings.getItemMeta();
        
        if (meta != null) {
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
        }
        
        return leggings;
    }
    
    private ItemStack createDarkLordBoots() {
        ItemStack boots = new ItemStack(Material.NETHERITE_BOOTS);
        ItemMeta meta = boots.getItemMeta();
        
        if (meta != null) {
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
        }
        
        return boots;
    }
} 