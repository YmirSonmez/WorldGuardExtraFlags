package net.goldtreeservers.worldguardextraflags.listeners;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.util.Location;
import com.sk89q.worldedit.world.World;
import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import com.sk89q.worldguard.session.SessionManager;
import org.bukkit.Material;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.EntityBlockFormEvent;

import com.sk89q.worldguard.protection.flags.StateFlag.State;

import lombok.RequiredArgsConstructor;
import net.goldtreeservers.worldguardextraflags.flags.Flags;

@RequiredArgsConstructor
public class BlockListener implements Listener
{
	private final WorldGuardPlugin worldGuardPlugin;
	private final RegionContainer regionContainer;
	private final SessionManager sessionManager;

	@EventHandler(ignoreCancelled = true)
	public void onEntityBlockFormEvent(EntityBlockFormEvent event)
	{
		BlockState newState = event.getNewState();
		if (newState.getType() == Material.FROSTED_ICE)
		{
			Location location = BukkitAdapter.adapt(newState.getLocation());

			LocalPlayer localPlayer;
			if (event.getEntity() instanceof Player player)
			{
				localPlayer = this.worldGuardPlugin.wrapPlayer(player);
				if (this.sessionManager.hasBypass(localPlayer, (World) location.getExtent()))
				{
					return;
				}
			}
			else
			{
				localPlayer = null;
			}

			if (this.regionContainer.createQuery().queryValue(location, localPlayer, Flags.FROSTWALKER) == State.DENY)
			{
				event.setCancelled(true);
			}
		}
	}

	@EventHandler(ignoreCancelled = true)
	public void onBlockFromTo(BlockFromToEvent event)
	{
		Material type = event.getBlock().getType();

		boolean isWater = type == Material.WATER;
		boolean isLava = type == Material.LAVA;

		if (!isWater && !isLava)
		{
			return;
		}

		// Check where the fluid is flowing TO
		Location toLocation = BukkitAdapter.adapt(event.getToBlock().getLocation());

		if (isWater)
		{
			if (this.regionContainer.createQuery().queryValue(toLocation, null, Flags.PREVENT_WATER_FLOW) == State.ALLOW)
			{
				event.setCancelled(true);
			}
		}
		else
		{
			if (this.regionContainer.createQuery().queryValue(toLocation, null, Flags.PREVENT_LAVA_FLOW) == State.ALLOW)
			{
				event.setCancelled(true);
			}
		}
	}
}
