package org.bedepay.uniqueboss.boss;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Wither;
import org.bedepay.uniqueboss.config.ConfigManager;

public class UniqueBossManager {
    
    private static UniqueBossEntity currentBoss = null;
    
    public static boolean isBossActive() {
        return currentBoss != null && currentBoss.isAlive();
    }
    
    public static void spawnBoss(Location location, ConfigManager config) {
        if (isBossActive()) {
            return;
        }
        
        currentBoss = new UniqueBossEntity(location, config);
        currentBoss.spawn();
    }
    
    public static UniqueBossEntity getCurrentBoss() {
        return currentBoss;
    }
    
    public static void setBossDefeated() {
        currentBoss = null;
    }
    
    public static void setCurrentBoss(UniqueBossEntity boss) {
        currentBoss = boss;
    }
    
    public static boolean isBossEntity(Entity entity) {
        if (currentBoss == null) return false;
        return currentBoss.getEntity() != null && 
               currentBoss.getEntity().equals(entity);
    }
} 