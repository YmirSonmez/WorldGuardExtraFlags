package net.goldtreeservers.worldguardextraflags.scoreboard;

import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.logging.Logger;

final class TabCollisionBridge
{
    private final Object tabApiInstance;
    private final Method getPlayer;
    private final Object nameTagManager;
    private final Method setCollisionRule;

    TabCollisionBridge(Logger logger) throws Exception
    {
        Class<?> tabApiClass = Class.forName("me.neznamy.tab.api.TabAPI");
        this.tabApiInstance = tabApiClass.getMethod("getInstance").invoke(null);

        this.getPlayer = tabApiInstance.getClass().getMethod("getPlayer", UUID.class);

        Object nameTagMgr = tabApiInstance.getClass().getMethod("getNameTagManager").invoke(tabApiInstance);
        if (nameTagMgr == null)
        {
            throw new Exception("TAB NameTagManager is null — nametags feature may be disabled in TAB config.");
        }
        this.nameTagManager = nameTagMgr;

        Class<?> tabPlayerClass = Class.forName("me.neznamy.tab.api.TabPlayer");
        this.setCollisionRule = nameTagMgr.getClass().getMethod("setCollisionRule", tabPlayerClass, Boolean.class);
    }

    void setCollision(Player player, Boolean enabled) throws Exception
    {
        Object tabPlayer = getPlayer.invoke(tabApiInstance, player.getUniqueId());
        if (tabPlayer == null)
        {
            return;
        }
        setCollisionRule.invoke(nameTagManager, tabPlayer, enabled);
    }
}
