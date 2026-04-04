package net.goldtreeservers.worldguardextraflags.scoreboard;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public class PlayerCollisionManager
{
	private static final Team.Option COLLISION_OPTION = Team.Option.COLLISION_RULE;
	private static final String TEAM_PREFIX = "wgefpc_";

	private final Map<UUID, PlayerCollisionOverride> playerOverrides = new HashMap<>();
	private final Map<TeamKey, TeamOverrideState> teamOverrides = new HashMap<>();

	public void updatePlayer(Player player, Boolean collisionsEnabled)
	{
		if (collisionsEnabled == null)
		{
			this.clearPlayer(player);
			return;
		}

		Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
		String entry = player.getName();
		Team currentTeam = scoreboard.getEntryTeam(entry);
		PlayerCollisionOverride currentOverride = this.playerOverrides.get(player.getUniqueId());

		if (this.matchesCurrentState(currentOverride, scoreboard, currentTeam, collisionsEnabled))
		{
			player.setCollidable(collisionsEnabled);

			if (!currentOverride.trackingOnly)
			{
				Team team = scoreboard.getTeam(currentOverride.teamName);
				if (team != null)
				{
					team.setOption(COLLISION_OPTION, currentOverride.desiredStatus);
				}
			}

			return;
		}

		this.clearPlayer(player);
		this.applyOverride(player, collisionsEnabled, scoreboard, scoreboard.getEntryTeam(entry));
	}

	public void refreshTrackedPlayers()
	{
		if (this.playerOverrides.isEmpty())
		{
			return;
		}

		for (Player player : Bukkit.getOnlinePlayers())
		{
			PlayerCollisionOverride override = this.playerOverrides.get(player.getUniqueId());
			if (override != null)
			{
				this.updatePlayer(player, override.collisionsEnabled);
			}
		}
	}

	public void clearPlayer(Player player)
	{
		PlayerCollisionOverride override = this.playerOverrides.remove(player.getUniqueId());
		if (override == null)
		{
			return;
		}

		player.setCollidable(override.originalCollidable);

		if (override.trackingOnly)
		{
			return;
		}

		TeamKey teamKey = new TeamKey(override.scoreboard, override.teamName);
		TeamOverrideState teamState = this.teamOverrides.get(teamKey);
		Team team = override.scoreboard.getTeam(override.teamName);

		if (team != null && override.createdTeam && team.hasEntry(player.getName()))
		{
			team.removeEntry(player.getName());
		}

		if (teamState == null)
		{
			if (team != null && override.createdTeam && team.getEntries().isEmpty())
			{
				team.unregister();
			}

			return;
		}

		teamState.references--;
		if (teamState.references > 0)
		{
			return;
		}

		if (team != null)
		{
			team.setOption(COLLISION_OPTION, teamState.originalStatus);

			if (override.createdTeam && team.getEntries().isEmpty())
			{
				team.unregister();
			}
		}

		this.teamOverrides.remove(teamKey);
	}

	public void shutdown()
	{
		for (Player player : Bukkit.getOnlinePlayers())
		{
			this.clearPlayer(player);
		}

		this.playerOverrides.clear();
		this.teamOverrides.clear();
	}

	private boolean matchesCurrentState(PlayerCollisionOverride currentOverride, Scoreboard scoreboard, Team currentTeam, boolean collisionsEnabled)
	{
		if (currentOverride == null || currentOverride.scoreboard != scoreboard || currentOverride.collisionsEnabled != collisionsEnabled)
		{
			return false;
		}

		if (collisionsEnabled && currentTeam == null)
		{
			return currentOverride.trackingOnly;
		}

		if (currentTeam == null)
		{
			return false;
		}

		return !currentOverride.trackingOnly && Objects.equals(currentOverride.teamName, currentTeam.getName());
	}

	private void applyOverride(Player player, boolean collisionsEnabled, Scoreboard scoreboard, Team currentTeam)
	{
		boolean originalCollidable = player.isCollidable();
		player.setCollidable(collisionsEnabled);

		if (collisionsEnabled && currentTeam == null)
		{
			this.playerOverrides.put(player.getUniqueId(), new PlayerCollisionOverride(scoreboard, null, collisionsEnabled, null, false, true, originalCollidable));
			return;
		}

		Team team = currentTeam;
		boolean createdTeam = false;
		if (team == null)
		{
			createdTeam = true;

			String teamName = this.getManagedTeamName(player.getUniqueId());
			team = scoreboard.getTeam(teamName);
			if (team == null)
			{
				team = scoreboard.registerNewTeam(teamName);
			}

			if (!team.hasEntry(player.getName()))
			{
				team.addEntry(player.getName());
			}
		}

		Team.OptionStatus desiredStatus = collisionsEnabled ? Team.OptionStatus.ALWAYS : Team.OptionStatus.NEVER;
		TeamKey teamKey = new TeamKey(scoreboard, team.getName());
		TeamOverrideState teamState = this.teamOverrides.get(teamKey);
		if (teamState == null)
		{
			teamState = new TeamOverrideState(team.getOption(COLLISION_OPTION), 0);
			this.teamOverrides.put(teamKey, teamState);
		}

		teamState.references++;
		team.setOption(COLLISION_OPTION, desiredStatus);

		this.playerOverrides.put(player.getUniqueId(), new PlayerCollisionOverride(scoreboard, team.getName(), collisionsEnabled, desiredStatus, createdTeam, false, originalCollidable));
	}

	private String getManagedTeamName(UUID uuid)
	{
		return TEAM_PREFIX + uuid.toString().replace("-", "").substring(0, 8);
	}

	private record TeamKey(Scoreboard scoreboard, String teamName)
	{
	}

	private static final class TeamOverrideState
	{
		private final Team.OptionStatus originalStatus;
		private int references;

		private TeamOverrideState(Team.OptionStatus originalStatus, int references)
		{
			this.originalStatus = originalStatus;
			this.references = references;
		}
	}

	private static final class PlayerCollisionOverride
	{
		private final Scoreboard scoreboard;
		private final String teamName;
		private final boolean collisionsEnabled;
		private final Team.OptionStatus desiredStatus;
		private final boolean createdTeam;
		private final boolean trackingOnly;
		private final boolean originalCollidable;

		private PlayerCollisionOverride(Scoreboard scoreboard, String teamName, boolean collisionsEnabled, Team.OptionStatus desiredStatus, boolean createdTeam, boolean trackingOnly, boolean originalCollidable)
		{
			this.scoreboard = scoreboard;
			this.teamName = teamName;
			this.collisionsEnabled = collisionsEnabled;
			this.desiredStatus = desiredStatus;
			this.createdTeam = createdTeam;
			this.trackingOnly = trackingOnly;
			this.originalCollidable = originalCollidable;
		}
	}
}
