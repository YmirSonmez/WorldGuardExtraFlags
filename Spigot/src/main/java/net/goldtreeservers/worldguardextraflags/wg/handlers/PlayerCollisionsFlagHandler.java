package net.goldtreeservers.worldguardextraflags.wg.handlers;

import com.sk89q.worldedit.bukkit.BukkitPlayer;
import com.sk89q.worldedit.util.Location;
import com.sk89q.worldedit.world.World;
import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.session.MoveType;
import com.sk89q.worldguard.session.Session;
import com.sk89q.worldguard.session.handler.FlagValueChangeHandler;
import com.sk89q.worldguard.session.handler.Handler;
import net.goldtreeservers.worldguardextraflags.WorldGuardExtraFlagsPlugin;
import net.goldtreeservers.worldguardextraflags.flags.Flags;
import org.bukkit.entity.Player;

public class PlayerCollisionsFlagHandler extends FlagValueChangeHandler<Boolean>
{
	public static final Factory FACTORY()
	{
		return new Factory();
	}

	public static class Factory extends Handler.Factory<PlayerCollisionsFlagHandler>
	{
		@Override
		public PlayerCollisionsFlagHandler create(Session session)
		{
			return new PlayerCollisionsFlagHandler(session);
		}
	}

	protected PlayerCollisionsFlagHandler(Session session)
	{
		super(session, Flags.PLAYER_COLLISIONS);
	}

	@Override
	protected void onInitialValue(LocalPlayer player, ApplicableRegionSet set, Boolean value)
	{
		this.handleValue(player, player.getWorld(), value);
	}

	@Override
	protected boolean onSetValue(LocalPlayer player, Location from, Location to, ApplicableRegionSet toSet, Boolean currentValue, Boolean lastValue, MoveType moveType)
	{
		this.handleValue(player, (World) to.getExtent(), currentValue);
		return true;
	}

	@Override
	protected boolean onAbsentValue(LocalPlayer player, Location from, Location to, ApplicableRegionSet toSet, Boolean lastValue, MoveType moveType)
	{
		this.handleValue(player, (World) to.getExtent(), null);
		return true;
	}

	private void handleValue(LocalPlayer player, World world, Boolean value)
	{
		Player bukkitPlayer = ((BukkitPlayer) player).getPlayer();
		if (!this.getSession().getManager().hasBypass(player, world) && value != null)
		{
			WorldGuardExtraFlagsPlugin.getPlugin().getPlayerCollisionManager().updatePlayer(bukkitPlayer, value);
			return;
		}

		WorldGuardExtraFlagsPlugin.getPlugin().getPlayerCollisionManager().updatePlayer(bukkitPlayer, null);
	}
}
