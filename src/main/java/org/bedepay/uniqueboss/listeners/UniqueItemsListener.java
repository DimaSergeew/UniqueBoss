package org.bedepay.uniqueboss.listeners;

import org.bukkit.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.bedepay.uniqueboss.config.ConfigManager;
import org.bedepay.uniqueboss.UniqueBoss;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class UniqueItemsListener implements Listener {
    
    private final Plugin plugin;
    private final ConfigManager config;
    private final Map<Player, Long> teleportCooldowns = new HashMap<>();
    private final Map<Player, Long> swordPullCooldowns = new HashMap<>();
    private final Map<Player, Long> shadowBootsCooldowns = new HashMap<>();
    private final Set<Player> powerCrystalHolders = new HashSet<>();
    private final Set<Player> shadowBootsWearers = new HashSet<>();
    private final Map<Player, BukkitRunnable> activeFireworkSystems = new HashMap<>();
    
    public UniqueItemsListener(Plugin plugin) {
        this.plugin = plugin;
        this.config = UniqueBoss.getInstance().getConfigManager();
        startPowerCrystalEffects();
        startShadowBootsEffects();
    }
    
    // ===============================
    // ПОСОХ ТЕЛЕПОРТАЦИИ (customModelData: 99003)
    // ===============================
    
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_AIR && 
            event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        
        if (isTeleportStaff(item)) {
            handleTeleportStaff(player);
            event.setCancelled(true);
        } else if (isEnhancedSword(item)) {
            handleEnhancedSwordPull(player);
            event.setCancelled(true);
        }
    }
    
    private boolean isTeleportStaff(ItemStack item) {
        if (item == null || item.getType() != Material.BLAZE_ROD) return false;
        if (!item.hasItemMeta()) return false;
        
        ItemMeta meta = item.getItemMeta();
        return meta.hasCustomModelData() && meta.getCustomModelData() == 99003;
    }
    
    private boolean isEnhancedSword(ItemStack item) {
        if (item == null || item.getType() != Material.NETHERITE_SWORD) return false;
        if (!item.hasItemMeta()) return false;
        
        ItemMeta meta = item.getItemMeta();
        return meta.hasCustomModelData() && meta.getCustomModelData() == 99002;
    }
    
    private void handleTeleportStaff(Player player) {
        // Проверяем кулдаун
        Long lastUse = teleportCooldowns.get(player);
        long currentTime = System.currentTimeMillis();
        
        long cooldownMs = config.getTeleportStaffCooldown() * 1000L;
        if (lastUse != null && currentTime - lastUse < cooldownMs) {
            long remainingTime = config.getTeleportStaffCooldown() - (currentTime - lastUse) / 1000;
            player.sendActionBar(ChatColor.RED + "⏱ Перезарядка: " + remainingTime + " сек.");
            return;
        }
        
        // Находим точку телепортации
        Location teleportLoc = findTeleportLocation(player);
        if (teleportLoc == null) {
            player.sendActionBar(ChatColor.RED + "❌ Невозможно телепортироваться в это место!");
            return;
        }
        
        Location originalLoc = player.getLocation();
        
        // Эффекты ДО телепортации
        World world = player.getWorld();
        world.spawnParticle(Particle.PORTAL, originalLoc, 30, 1, 1, 1, 0.3);
        world.spawnParticle(Particle.SPELL_WITCH, originalLoc, 20, 0.5, 0.5, 0.5, 0.1);
        world.playSound(originalLoc, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.2f);
        
        // Телепортация
        player.teleport(teleportLoc);
        
        // Эффекты ПОСЛЕ телепортации
        world.spawnParticle(Particle.PORTAL, teleportLoc, 30, 1, 1, 1, 0.3);
        world.spawnParticle(Particle.SPELL_WITCH, teleportLoc, 20, 0.5, 0.5, 0.5, 0.1);
        world.spawnParticle(Particle.FIREWORKS_SPARK, teleportLoc, 15, 1, 1, 1, 0.05);
        world.playSound(teleportLoc, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 0.8f);
        
        player.sendActionBar(ChatColor.LIGHT_PURPLE + "🌟 Телепортация выполнена!");
        
        // Устанавливаем кулдаун
        teleportCooldowns.put(player, currentTime);
    }
    
    // ===============================
    // КЛИНОК РАЗРУШЕНИЯ (customModelData: 99002)
    // ===============================
    
    private void handleEnhancedSwordPull(Player player) {
        // Проверяем кулдаун
        Long lastUse = swordPullCooldowns.get(player);
        long currentTime = System.currentTimeMillis();
        long cooldownMs = config.getEnhancedSwordPullCooldown() * 1000L;
        
        if (lastUse != null && currentTime - lastUse < cooldownMs) {
            long remainingTime = config.getEnhancedSwordPullCooldown() - (currentTime - lastUse) / 1000;
            player.sendActionBar(ChatColor.RED + "⏱ Притягивание: " + remainingTime + " сек.");
            return;
        }
        
        // Находим цель, на которую смотрит игрок
        LivingEntity target = findPullTarget(player);
        if (target == null) {
            player.sendActionBar(ChatColor.RED + "❌ Нет цели для притягивания!");
            return;
        }
        
        // Притягиваем цель
        Vector pullDirection = player.getLocation().toVector().subtract(target.getLocation().toVector()).normalize();
        pullDirection.multiply(config.getEnhancedSwordPullStrength());
        pullDirection.setY(Math.max(0.5, pullDirection.getY())); // Минимальный подъем
        
        target.setVelocity(pullDirection);
        
        // Эффекты притягивания
        World world = player.getWorld();
        Location playerLoc = player.getLocation();
        Location targetLoc = target.getLocation();
        
        // Линия частиц между игроком и целью
        createPullEffectLine(playerLoc, targetLoc, world);
        
        // Звуки и эффекты
        world.playSound(playerLoc, Sound.ENTITY_EVOKER_CAST_SPELL, 1.5f, 0.8f);
        world.playSound(targetLoc, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.2f);
        world.spawnParticle(Particle.SPELL_WITCH, targetLoc, 30, 1, 1, 1, 0.2);
        world.spawnParticle(Particle.REDSTONE, targetLoc, 20, 1, 1, 1, 
            new Particle.DustOptions(org.bukkit.Color.PURPLE, 2.0f));
        
        player.sendActionBar(ChatColor.DARK_PURPLE + "⚔ Цель притянута темной силой!");
        
        // Урон цели
        target.damage(4.0, player);
        
        // Устанавливаем кулдаун
        swordPullCooldowns.put(player, currentTime);
    }
    
    private LivingEntity findPullTarget(Player player) {
        // Сначала пробуем точный rayTrace
        RayTraceResult result = player.getWorld().rayTraceEntities(
            player.getEyeLocation(),
            player.getEyeLocation().getDirection(),
            config.getEnhancedSwordPullDistance(),
            entity -> entity instanceof LivingEntity && entity != player
        );
        
        if (result != null && result.getHitEntity() instanceof LivingEntity) {
            return (LivingEntity) result.getHitEntity();
        }
        
        // Если точный rayTrace не нашел цель, ищем ближайшую сущность в конусе взгляда
        Vector lookDirection = player.getEyeLocation().getDirection();
        double maxDistance = config.getEnhancedSwordPullDistance();
        LivingEntity closestEntity = null;
        double closestDistance = maxDistance;
        
        for (Entity entity : player.getNearbyEntities(maxDistance, maxDistance, maxDistance)) {
            if (!(entity instanceof LivingEntity) || entity == player) continue;
            
            Location entityLoc = entity.getLocation();
            Vector toEntity = entityLoc.toVector().subtract(player.getEyeLocation().toVector()).normalize();
            
            // Проверяем угол между направлением взгляда и направлением к сущности
            double angle = lookDirection.angle(toEntity);
            if (angle <= Math.toRadians(30)) { // 30 градусов конус
                double distance = player.getEyeLocation().distance(entityLoc);
                if (distance < closestDistance) {
                    closestEntity = (LivingEntity) entity;
                    closestDistance = distance;
                }
            }
        }
        
        return closestEntity;
    }
    
    private void createPullEffectLine(Location start, Location end, World world) {
        Vector direction = end.toVector().subtract(start.toVector());
        double distance = direction.length();
        direction.normalize();
        
        for (double i = 0; i < distance; i += 0.5) {
            Location particleLoc = start.clone().add(direction.clone().multiply(i));
            world.spawnParticle(Particle.REDSTONE, particleLoc, 1, 0, 0, 0,
                new Particle.DustOptions(org.bukkit.Color.PURPLE, 1.0f));
        }
    }
    
    private Location findTeleportLocation(Player player) {
        // Находим точку, куда смотрит игрок, на расстоянии до 20 блоков
        RayTraceResult result = player.getWorld().rayTraceBlocks(
            player.getEyeLocation(), 
            player.getEyeLocation().getDirection(), 
            config.getTeleportStaffDistance()
        );
        
        Location targetLoc;
        if (result != null && result.getHitBlock() != null) {
            // Телепортируемся НА блок
            targetLoc = result.getHitBlock().getLocation().add(0.5, 1, 0.5);
        } else {
            // Телепортируемся на максимальное расстояние в направлении взгляда
            Vector direction = player.getEyeLocation().getDirection().normalize();
            targetLoc = player.getLocation().add(direction.multiply(config.getTeleportStaffDistance()));
            targetLoc.setY(findSafeY(targetLoc));
        }
        
        // Проверяем безопасность места
        if (isSafeForTeleport(targetLoc)) {
            return targetLoc;
        }
        
        return null;
    }
    
    private double findSafeY(Location loc) {
        World world = loc.getWorld();
        int startY = Math.max(1, (int) loc.getY());
        
        // Ищем твердый блок снизу
        for (int y = startY; y >= world.getMinHeight(); y--) {
            if (world.getBlockAt(loc.getBlockX(), y, loc.getBlockZ()).getType().isSolid()) {
                return y + 1; // На блок выше твердого
            }
        }
        
        return loc.getY(); // Возвращаем исходную высоту
    }
    
    private boolean isSafeForTeleport(Location loc) {
        World world = loc.getWorld();
        
        // Проверяем, что есть место для игрока (2 блока высоты)
        Location feet = loc.clone();
        Location head = loc.clone().add(0, 1, 0);
        Location ground = loc.clone().subtract(0, 1, 0);
        
        return feet.getBlock().getType() == Material.AIR && 
               head.getBlock().getType() == Material.AIR &&
               ground.getBlock().getType().isSolid() &&
               !isLavaOrVoid(feet);
    }
    
    private boolean isLavaOrVoid(Location loc) {
        return loc.getY() < loc.getWorld().getMinHeight() + 5 ||
               loc.getBlock().getType() == Material.LAVA ||
               loc.getBlock().getType() == Material.FIRE;
    }
    
    // ===============================
    // НЕЛОМАЮЩИЕСЯ КРЫЛЬЯ ТЬМЫ (customModelData: 99001)
    // ===============================
    
    @EventHandler
    public void onElytraGlide(EntityToggleGlideEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        
        Player player = (Player) event.getEntity();
        
        if (event.isGliding() && isWearingDarkElytra(player)) {
            // Игрок начал полет с крыльями тьмы - даем лончер и запускаем автоматические фейерверки
            
            // ЛОНЧЕР - подкидываем игрока вверх для старта полета
            Vector launchVelocity = player.getVelocity().clone();
            launchVelocity.setY(config.getDarkElytraLaunchVelocity()); // Настраиваемая сила лончера
            player.setVelocity(launchVelocity);
            
            // Эффекты лончера
            World world = player.getWorld();
            Location playerLoc = player.getLocation();
            world.spawnParticle(Particle.CLOUD, playerLoc, 30, 1, 0.5, 1, 0.1);
            world.spawnParticle(Particle.FIREWORKS_SPARK, playerLoc, 20, 0.5, 0.5, 0.5, 0.1);
            world.playSound(playerLoc, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1.0f, 1.2f);
            
            // Сообщение игроку
            int fireworkInterval = config.getDarkElytraFireworkInterval();
            player.sendActionBar(ChatColor.LIGHT_PURPLE + "🚀 Крылья Тьмы активированы! Автоматические фейерверки каждые " + fireworkInterval + " сек");
            
            // Запускаем систему автоматических фейерверков
            startAutomaticFireworks(player);
        } else if (!event.isGliding()) {
            // Остановка системы фейерверков при приземлении
            stopAutomaticFireworks(player);
        }
    }
    
    private boolean isWearingDarkElytra(Player player) {
        ItemStack chestplate = player.getInventory().getChestplate();
        if (chestplate == null || chestplate.getType() != Material.ELYTRA) return false;
        if (!chestplate.hasItemMeta()) return false;
        
        ItemMeta meta = chestplate.getItemMeta();
        return meta.hasCustomModelData() && meta.getCustomModelData() == 99001;
    }
    
    private void startAutomaticFireworks(Player player) {
        // Останавливаем предыдущую систему, если она была
        stopAutomaticFireworks(player);
        
        World world = player.getWorld();
        int fireworkIntervalTicks = config.getDarkElytraFireworkInterval() * 20; // Секунды в тики
        int repairIntervalTicks = config.getDarkElytraRepairInterval() * 20; // Секунды в тики
        
        BukkitRunnable fireworkSystem = new BukkitRunnable() {
            private int tickCount = 0;
            
            @Override
            public void run() {
                if (!player.isOnline() || !player.isGliding() || !isWearingDarkElytra(player)) {
                    this.cancel();
                    activeFireworkSystems.remove(player);
                    return;
                }
                
                tickCount++;
                
                // Каждые N секунд создаем и автоматически используем фейерверк
                if (tickCount % fireworkIntervalTicks == 0) {
                    createAndUseFirework(player);
                }
                
                // Автопочинка элитр
                if (tickCount % repairIntervalTicks == 0) {
                    repairElytra(player);
                }
                
                // Красивые эффекты каждые 2 секунды
                if (tickCount % 40 == 0) {
                    Location loc = player.getLocation();
                    world.spawnParticle(Particle.PORTAL, loc, 8, 0.5, 0.5, 0.5, 0.05);
                    world.spawnParticle(Particle.SPELL_WITCH, loc, 5, 0.3, 0.3, 0.3, 0.02);
                    world.playSound(loc, Sound.ENTITY_PHANTOM_FLAP, 0.3f, 1.5f);
                }
            }
        };
        
        fireworkSystem.runTaskTimer(plugin, 0L, 1L);
        activeFireworkSystems.put(player, fireworkSystem);
    }
    
    private void stopAutomaticFireworks(Player player) {
        BukkitRunnable system = activeFireworkSystems.remove(player);
        if (system != null && !system.isCancelled()) {
            system.cancel();
        }
    }
    
    private void createAndUseFirework(Player player) {
        // РЕАЛЬНОЕ УСКОРЕНИЕ как от фейерверка
        Vector currentVelocity = player.getVelocity();
        Vector lookDirection = player.getLocation().getDirection();
        
        // Вычисляем ускорение на основе силы фейерверка из конфига
        double fireworkPower = config.getDarkElytraFireworkPower();
        double accelerationMultiplier = 0.5 + (fireworkPower * 0.3); // 0.8 для уровня 1, 1.4 для уровня 3
        
        // Добавляем ускорение в направлении взгляда игрока
        Vector acceleration = lookDirection.clone().multiply(accelerationMultiplier);
        
        // Смешиваем с текущей скоростью для плавности
        Vector newVelocity = currentVelocity.clone().add(acceleration);
        
        // Ограничиваем максимальную скорость (настраиваемо)
        double maxSpeed = config.getDarkElytraMaxSpeed();
        if (newVelocity.length() > maxSpeed) {
            newVelocity = newVelocity.normalize().multiply(maxSpeed);
        }
        
        // Применяем новую скорость
        player.setVelocity(newVelocity);
        
        // Эффекты автоматического фейерверка
        Location loc = player.getLocation();
        World world = player.getWorld();
        
        // Создаем красивый визуальный фейерверк
        org.bukkit.entity.Firework visualFirework = world.spawn(loc, org.bukkit.entity.Firework.class);
        org.bukkit.inventory.meta.FireworkMeta fireworkMeta = visualFirework.getFireworkMeta();
        fireworkMeta.setPower(1); // Короткий полет для быстрого взрыва
        
        org.bukkit.FireworkEffect effect = org.bukkit.FireworkEffect.builder()
            .with(org.bukkit.FireworkEffect.Type.STAR)
            .withColor(org.bukkit.Color.PURPLE, org.bukkit.Color.BLACK)
            .withFade(org.bukkit.Color.GRAY)
            .trail(true)
            .flicker(true)
            .build();
        fireworkMeta.addEffect(effect);
        visualFirework.setFireworkMeta(fireworkMeta);
        
        // Направляем визуальный фейерверк назад от игрока
        Vector fireworkDirection = lookDirection.clone().multiply(-0.2);
        visualFirework.setVelocity(fireworkDirection);
        
        // Быстро детонируем для эффекта
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!visualFirework.isDead()) {
                    visualFirework.detonate();
                }
            }
        }.runTaskLater(plugin, 8L);
        
        // Дополнительные эффекты ускорения
        world.spawnParticle(Particle.FIREWORKS_SPARK, loc, 25, 0.8, 0.8, 0.8, 0.15);
        world.spawnParticle(Particle.PORTAL, loc, 15, 1, 1, 1, 0.1);
        world.spawnParticle(Particle.FLAME, loc, 12, 0.5, 0.5, 0.5, 0.05);
        
        // Звуки фейерверка
        world.playSound(loc, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1.2f, 1.2f);
        world.playSound(loc, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 0.8f, 1.5f);
        
        player.sendActionBar(ChatColor.DARK_PURPLE + "🎆 Автоматический фейерверк уровня " + 
                           config.getDarkElytraFireworkPower() + " - ускорение получено!");
    }
    

    
    private void repairElytra(Player player) {
        ItemStack elytra = player.getInventory().getChestplate();
        if (elytra != null && elytra.getType() == Material.ELYTRA) {
            if (elytra.getDurability() > 0) {
                int repairAmount = config.getDarkElytraRepairAmount();
                elytra.setDurability((short) Math.max(0, elytra.getDurability() - repairAmount));
                
                World world = player.getWorld();
                world.spawnParticle(Particle.VILLAGER_HAPPY, player.getLocation(), 5, 0.5, 0.5, 0.5, 0.05);
                world.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.5f, 2.0f);
            }
        }
    }
    
    // ===============================
    // ТЕНЕВЫЕ САПОГИ (customModelData: 99004)
    // ===============================
    
    @EventHandler
    public void onPlayerSneak(PlayerToggleSneakEvent event) {
        Player player = event.getPlayer();
        
        if (isWearingShadowBoots(player) && event.isSneaking()) {
            // Проверяем кулдаун
            long currentTime = System.currentTimeMillis();
            Long lastUsed = shadowBootsCooldowns.get(player);
            
            if (lastUsed != null && (currentTime - lastUsed) < 1000) {
                // Кулдаун еще не прошел (менее секунды)
                return;
            }
            
            // Игрок начал приседать - даем эффекты на 8 секунд
            activateShadowMode(player);
            shadowBootsCooldowns.put(player, currentTime);
        }
    }
    
    private boolean isWearingShadowBoots(Player player) {
        ItemStack boots = player.getInventory().getBoots();
        if (boots == null || boots.getType() != Material.NETHERITE_BOOTS) return false;
        if (!boots.hasItemMeta()) return false;
        
        ItemMeta meta = boots.getItemMeta();
        return meta.hasCustomModelData() && meta.getCustomModelData() == 99004;
    }
    
    private void activateShadowMode(Player player) {
        // Эффекты активации (8 секунд = 160 тиков)
        player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 160, 0, true, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 160, 2, true, false)); // Скорость III
        
        // Визуальные эффекты
        World world = player.getWorld();
        world.spawnParticle(Particle.SMOKE_LARGE, player.getLocation().add(0, 1, 0), 20, 0.5, 1, 0.5, 0.05);
        world.spawnParticle(Particle.SPELL_WITCH, player.getLocation().add(0, 1, 0), 15, 0.3, 0.5, 0.3, 0.1);
        world.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.5f, 1.5f);
        
        player.sendActionBar(ChatColor.DARK_GRAY + "👤 Режим теней активирован на 8 секунд!");
    }
    
    // ===============================
    // КРИСТАЛЛ СИЛЫ (customModelData: 99005)  
    // ===============================
    
    @EventHandler
    public void onItemHeld(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        
        // Проверяем новый предмет
        new BukkitRunnable() {
            @Override
            public void run() {
                ItemStack newItem = player.getInventory().getItem(event.getNewSlot());
                ItemStack oldItem = player.getInventory().getItem(event.getPreviousSlot());
                
                boolean hadCrystal = isPowerCrystal(oldItem);
                boolean hasCrystal = isPowerCrystal(newItem);
                
                if (!hadCrystal && hasCrystal) {
                    // Взял кристалл в руку
                    activatePowerCrystal(player);
                } else if (hadCrystal && !hasCrystal) {
                    // Убрал кристалл из руки
                    deactivatePowerCrystal(player);
                }
            }
        }.runTaskLater(plugin, 1L);
    }
    
    @EventHandler
    public void onItemDrop(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        ItemStack droppedItem = event.getItemDrop().getItemStack();
        
        if (isPowerCrystal(droppedItem)) {
            deactivatePowerCrystal(player);
        }
    }
    
    @EventHandler
    public void onItemSwap(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        
        new BukkitRunnable() {
            @Override
            public void run() {
                ItemStack mainHand = player.getInventory().getItemInMainHand();
                ItemStack offHand = player.getInventory().getItemInOffHand();
                
                boolean hadCrystal = powerCrystalHolders.contains(player);
                boolean hasCrystal = isPowerCrystal(mainHand) || isPowerCrystal(offHand);
                
                if (!hadCrystal && hasCrystal) {
                    activatePowerCrystal(player);
                } else if (hadCrystal && !hasCrystal) {
                    deactivatePowerCrystal(player);
                }
            }
        }.runTaskLater(plugin, 1L);
    }
    
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        
        Player player = (Player) event.getWhoClicked();
        
        // Проверяем если кликнули в слот offhand (40) или основную руку (player.getInventory().getHeldItemSlot())
        if (event.getSlot() == 40 || event.getSlot() == player.getInventory().getHeldItemSlot()) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    ItemStack mainHand = player.getInventory().getItemInMainHand();
                    ItemStack offHand = player.getInventory().getItemInOffHand();
                    
                    boolean hadCrystal = powerCrystalHolders.contains(player);
                    boolean hasCrystal = isPowerCrystal(mainHand) || isPowerCrystal(offHand);
                    
                    if (!hadCrystal && hasCrystal) {
                        activatePowerCrystal(player);
                    } else if (hadCrystal && !hasCrystal) {
                        deactivatePowerCrystal(player);
                    }
                }
            }.runTaskLater(plugin, 1L); // Задержка в 1 тик чтобы инвентарь обновился
        }
    }
    
    private boolean isPowerCrystal(ItemStack item) {
        if (item == null || item.getType() != Material.END_CRYSTAL) return false;
        if (!item.hasItemMeta()) return false;
        
        ItemMeta meta = item.getItemMeta();
        return meta.hasCustomModelData() && meta.getCustomModelData() == 99005;
    }
    
    private void activatePowerCrystal(Player player) {
        powerCrystalHolders.add(player);
        
        // Визуальные эффекты активации
        World world = player.getWorld();
        world.spawnParticle(Particle.REDSTONE, player.getLocation().add(0, 1, 0), 20, 0.5, 1, 0.5, 
            new Particle.DustOptions(org.bukkit.Color.PURPLE, 1.5f));
        world.spawnParticle(Particle.SPELL_WITCH, player.getLocation().add(0, 1, 0), 15, 0.3, 0.5, 0.3, 0.1);
        world.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 1.2f);
        
        player.sendActionBar(ChatColor.LIGHT_PURPLE + "💎 Кристалл силы активирован!");
    }
    
    private void deactivatePowerCrystal(Player player) {
        powerCrystalHolders.remove(player);
        
        // Убираем все эффекты кристалла
        player.removePotionEffect(PotionEffectType.INCREASE_DAMAGE);
        player.removePotionEffect(PotionEffectType.DAMAGE_RESISTANCE);
        player.removePotionEffect(PotionEffectType.REGENERATION);
        player.removePotionEffect(PotionEffectType.NIGHT_VISION);
        
        player.sendActionBar(ChatColor.GRAY + "💎 Кристалл силы деактивирован");
    }
    
    // Постоянные эффекты кристалла силы
    private void startPowerCrystalEffects() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : powerCrystalHolders) {
                    if (player.isOnline()) {
                        // Проверяем, что кристалл все еще в руке
                        ItemStack mainHand = player.getInventory().getItemInMainHand();
                        ItemStack offHand = player.getInventory().getItemInOffHand();
                        
                        if (isPowerCrystal(mainHand) || isPowerCrystal(offHand)) {
                            // Применяем эффекты
                            player.addPotionEffect(new PotionEffect(PotionEffectType.INCREASE_DAMAGE, 60, 1, true, false)); // Сила II
                            player.addPotionEffect(new PotionEffect(PotionEffectType.DAMAGE_RESISTANCE, 60, 0, true, false)); // Сопротивление I
                            player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 60, 0, true, false)); // Регенерация I
                            player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 400, 0, true, false)); // Ночное зрение
                            
                            // Небольшие визуальные эффекты каждые 5 секунд
                            if (System.currentTimeMillis() % 5000 < 50) {
                                World world = player.getWorld();
                                world.spawnParticle(Particle.REDSTONE, player.getLocation().add(0, 1, 0), 5, 0.3, 0.5, 0.3,
                                    new Particle.DustOptions(org.bukkit.Color.PURPLE, 1.0f));
                                world.spawnParticle(Particle.SPELL_WITCH, player.getLocation().add(0, 1, 0), 3, 0.2, 0.3, 0.2, 0.05);
                            }
                        } else {
                            // Кристалл больше не в руке - деактивируем
                            deactivatePowerCrystal(player);
                        }
                    }
                }
                
                // Очищаем отключившихся игроков
                powerCrystalHolders.removeIf(player -> !player.isOnline());
            }
        }.runTaskTimer(plugin, 0L, 20L); // Каждую секунду
    }
    
    // Постоянные эффекты сапог теней
    private void startShadowBootsEffects() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : org.bukkit.Bukkit.getOnlinePlayers()) {
                    boolean wasWearing = shadowBootsWearers.contains(player);
                    boolean isWearing = isWearingShadowBoots(player);
                    
                    if (isWearing) {
                        if (!wasWearing) {
                            shadowBootsWearers.add(player);
                            player.sendActionBar(ChatColor.DARK_GRAY + "👤 Сапоги теней активированы!");
                        }
                        
                        // Постоянная скорость 3
                        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 60, 2, true, false));
                        
                        // Небольшие визуальные эффекты каждые 5 секунд
                        if (System.currentTimeMillis() % 5000 < 50) {
                            World world = player.getWorld();
                            world.spawnParticle(Particle.SMOKE_LARGE, player.getLocation().add(0, 0.1, 0), 2, 0.2, 0.05, 0.2, 0.02);
                        }
                    } else if (wasWearing) {
                        shadowBootsWearers.remove(player);
                        player.sendActionBar(ChatColor.GRAY + "👤 Сапоги теней деактивированы");
                    }
                }
                
                // Очищаем отключившихся игроков
                shadowBootsWearers.removeIf(player -> !player.isOnline());
            }
        }.runTaskTimer(plugin, 0L, 20L); // Каждую секунду
    }
    
    // Метод для очистки при отключении плагина
    public void cleanup() {
        // Убираем все эффекты с игроков
        for (Player player : powerCrystalHolders) {
            deactivatePowerCrystal(player);
        }
        
        // Останавливаем все системы автоматических фейерверков
        for (BukkitRunnable system : activeFireworkSystems.values()) {
            if (!system.isCancelled()) {
                system.cancel();
            }
        }
        
        powerCrystalHolders.clear();
        teleportCooldowns.clear();
        shadowBootsCooldowns.clear();
        shadowBootsWearers.clear();
        swordPullCooldowns.clear();
        activeFireworkSystems.clear();
    }
} 
