package org.bedepay.uniqueboss.listeners;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;
import org.bedepay.uniqueboss.config.ConfigManager;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

public class ArmorEffectsListener implements Listener {
    
    private final Random random = new Random();
    private final ConfigManager config;
    private final Set<Player> fullSetPlayers = new HashSet<>();
    
    public ArmorEffectsListener(ConfigManager config) {
        this.config = config;
    }
    
    @EventHandler
    public void onPlayerAttack(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player)) return;
        if (!(event.getEntity() instanceof LivingEntity)) return;
        
        Player attacker = (Player) event.getDamager();
        LivingEntity victim = (LivingEntity) event.getEntity();
        
        // Проверяем, носит ли игрок полный комплект
        if (!isWearingFullDarkLordSet(attacker)) return;
        
        // Молния при атаке
        if (random.nextInt(100) < config.getLightningStrikeChance()) {
            victim.getWorld().strikeLightningEffect(victim.getLocation());
            victim.damage(6.0);
            attacker.sendActionBar(config.getMessage("armor.lightning_strike"));
            
            // Эффекты молнии
            victim.getWorld().spawnParticle(org.bukkit.Particle.ELECTRIC_SPARK, 
                victim.getLocation().add(0, 1, 0), 30, 1, 1, 1, 0.1);
        }
        
        // Урон по области
        double areaDamage = config.getAreaDamageOnHit();
        if (areaDamage > 0) {
            for (Entity nearbyEntity : victim.getNearbyEntities(4, 4, 4)) {
                if (nearbyEntity instanceof LivingEntity && nearbyEntity != attacker) {
                    LivingEntity nearby = (LivingEntity) nearbyEntity;
                    nearby.damage(areaDamage);
                    
                    // Эффекты урона по области
                    nearby.getWorld().spawnParticle(org.bukkit.Particle.REDSTONE, 
                        nearby.getLocation().add(0, 1, 0), 10, 0.5, 0.5, 0.5, 
                        new org.bukkit.Particle.DustOptions(org.bukkit.Color.MAROON, 1.5f));
                }
            }
            
            if (victim.getNearbyEntities(4, 4, 4).size() > 0) {
                attacker.sendActionBar(config.getMessage("armor.area_damage"));
            }
        }
    }
    
    @EventHandler
    public void onPlayerDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        if (!(event.getDamager() instanceof LivingEntity)) return;
        
        Player victim = (Player) event.getEntity();
        LivingEntity attacker = (LivingEntity) event.getDamager();
        
        // Проверяем, носит ли игрок полный комплект
        if (!isWearingFullDarkLordSet(victim)) return;
        
        // Телепортация при получении урона
        if (random.nextInt(100) < config.getTeleportOnDamageChance()) {
            teleportToSafeLocation(victim);
            victim.sendActionBar(config.getMessage("armor.teleport_escape"));
            
            // Эффекты телепортации
            victim.getWorld().spawnParticle(org.bukkit.Particle.PORTAL, 
                victim.getLocation(), 30, 1, 1, 1, 0.3);
            victim.getWorld().playSound(victim.getLocation(), 
                org.bukkit.Sound.ENTITY_ENDERMAN_TELEPORT, 1.5f, 1.0f);
        }
    }
    
    private void teleportToSafeLocation(Player player) {
        Location currentLoc = player.getLocation();
        
        // Ищем безопасное место в радиусе 10 блоков
        for (int attempts = 0; attempts < 10; attempts++) {
            double angle = random.nextDouble() * 2 * Math.PI;
            double distance = 5 + random.nextDouble() * 5; // 5-10 блоков
            
            double x = currentLoc.getX() + Math.cos(angle) * distance;
            double z = currentLoc.getZ() + Math.sin(angle) * distance;
            double y = currentLoc.getY();
            
            Location teleportLoc = new Location(currentLoc.getWorld(), x, y, z);
            
            // Проверяем, безопасно ли место
            if (isSafeLocation(teleportLoc)) {
                player.teleport(teleportLoc);
                return;
            }
        }
        
        // Если не нашли безопасное место, телепортируем на 8 блоков вверх
        player.teleport(currentLoc.add(0, 8, 0));
    }
    
    private boolean isSafeLocation(Location loc) {
        // Проверяем, что есть место для игрока и нет лавы/воды
        Location feet = loc.clone();
        Location head = loc.clone().add(0, 1, 0);
        
        return feet.getBlock().getType() == Material.AIR && 
               head.getBlock().getType() == Material.AIR &&
               feet.clone().subtract(0, 1, 0).getBlock().getType().isSolid();
    }
    
    private boolean isWearingFullDarkLordSet(Player player) {
        ItemStack helmet = player.getInventory().getHelmet();
        ItemStack chestplate = player.getInventory().getChestplate();
        ItemStack leggings = player.getInventory().getLeggings();
        ItemStack boots = player.getInventory().getBoots();
        
        return isDarkLordPiece(helmet, 11111) &&
               isDarkLordPiece(chestplate, 22222) &&
               isDarkLordPiece(leggings, 33333) &&
               isDarkLordPiece(boots, 44444);
    }
    
    private boolean isDarkLordPiece(ItemStack item, int customModelData) {
        if (item == null) return false;
        if (!item.hasItemMeta()) return false;
        
        ItemMeta meta = item.getItemMeta();
        return meta.hasCustomModelData() && meta.getCustomModelData() == customModelData;
    }
    
    // Постоянные эффекты от ношения доспеха
    public void startArmorEffects() {
        Plugin plugin = org.bukkit.Bukkit.getPluginManager().getPlugin("UniqueBoss");
        
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : org.bukkit.Bukkit.getOnlinePlayers()) {
                    boolean wasWearingFullSet = fullSetPlayers.contains(player);
                    boolean isWearingFullSetNow = isWearingFullDarkLordSet(player);
                    
                    if (isWearingFullSetNow) {
                        if (!wasWearingFullSet) {
                            // Игрок только что надел полный комплект
                            fullSetPlayers.add(player);
                            player.sendTitle("", config.getMessage("armor.full_set_equipped"), 5, 40, 10);
                            
                            // Эффекты при надевании полного комплекта
                            player.getWorld().spawnParticle(org.bukkit.Particle.TOTEM, 
                                player.getLocation().add(0, 1, 0), 30, 1, 1, 1, 0.1);
                            player.getWorld().playSound(player.getLocation(), 
                                org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.5f, 1.0f);
                        }
                        
                        // Постоянные эффекты полного комплекта
                        if (config.isFullSetFireResistance()) {
                            player.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 60, 0, true, false));
                        }
                        
                        if (config.isFullSetWaterBreathing()) {
                            player.addPotionEffect(new PotionEffect(PotionEffectType.WATER_BREATHING, 60, 0, true, false));
                        }
                        
                        if (config.getFullSetSpeed() > 0) {
                            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 60, config.getFullSetSpeed() - 1, true, false));
                        }
                        
                        // Сила 2 (эффект уровня 1 = Сила II)
                        player.addPotionEffect(new PotionEffect(PotionEffectType.INCREASE_DAMAGE, 60, 1, true, false));
                        
                        if (config.isFullSetNightVision()) {
                            player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 400, 0, true, false));
                        }
                        
                        // Небольшие визуальные эффекты каждые 3 секунды
                        if (System.currentTimeMillis() % 3000 < 50) {
                            player.getWorld().spawnParticle(org.bukkit.Particle.SPELL_WITCH, 
                                player.getLocation().add(0, 1, 0), 5, 0.5, 0.5, 0.5, 0.05);
                            player.getWorld().spawnParticle(org.bukkit.Particle.REDSTONE, 
                                player.getLocation().add(0, 1, 0), 3, 0.3, 0.3, 0.3, 
                                new org.bukkit.Particle.DustOptions(org.bukkit.Color.PURPLE, 1.0f));
                        }
                    } else if (wasWearingFullSet) {
                        // Игрок снял часть комплекта
                        fullSetPlayers.remove(player);
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 20L); // Каждую секунду
    }
} 