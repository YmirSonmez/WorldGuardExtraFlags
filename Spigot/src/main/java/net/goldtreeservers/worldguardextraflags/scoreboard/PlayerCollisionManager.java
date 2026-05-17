package net.goldtreeservers.worldguardextraflags.scoreboard;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

public final class PlayerCollisionManager
{
    private static final Team.Option COLLISION_OPTION = Team.Option.COLLISION_RULE;
    private static final String TEAM_PREFIX = "wgefpc_";

    private final TabCollisionBridge tabBridge;

    // Per-player original collidable state to restore on exit
    private final Map<UUID, Boolean> originalCollidable = new HashMap<>();

    // Scoreboard team tracking (used only when TAB is absent)
    private final Map<UUID, PlayerState> playerStates = new HashMap<>();
    private final Map<TeamKey, TeamState> teamStates = new HashMap<>();

    public PlayerCollisionManager(Logger logger)
    {
        TabCollisionBridge bridge = null;
        if (Bukkit.getPluginManager().getPlugin("TAB") != null)
        {
            try
            {
                bridge = new TabCollisionBridge(logger);
            }
            catch (Exception e)
            {
                logger.warning("[PlayerCollision] TAB detected but API init failed: " + e.getMessage()
                        + " — falling back to scoreboard team approach.");
            }
        }
        this.tabBridge = bridge;
    }

    public void updatePlayer(Player player, Boolean enabled)
    {
        if (enabled == null)
        {
            clearPlayer(player);
            return;
        }

        UUID uuid = player.getUniqueId();

        if (!originalCollidable.containsKey(uuid))
        {
            originalCollidable.put(uuid, player.isCollidable());
        }

        player.setCollidable(enabled);

        if (tabBridge != null)
        {
            try
            {
                tabBridge.setCollision(player, enabled);
            }
            catch (Exception ignored)
            {
            }
        }
        else
        {
            updateScoreboard(player, uuid, enabled);
        }
    }

    public void clearPlayer(Player player)
    {
        UUID uuid = player.getUniqueId();

        Boolean original = originalCollidable.remove(uuid);
        if (original != null)
        {
            player.setCollidable(original);
        }

        if (tabBridge != null)
        {
            try
            {
                tabBridge.setCollision(player, null);
            }
            catch (Exception ignored)
            {
            }
        }
        else
        {
            releaseScoreboard(player, uuid);
        }
    }

    public void shutdown()
    {
        for (Player player : Bukkit.getOnlinePlayers())
        {
            clearPlayer(player);
        }
        originalCollidable.clear();
        playerStates.clear();
        teamStates.clear();
    }

    // ── scoreboard team approach (no TAB) ────────────────────────────

    private void updateScoreboard(Player player, UUID uuid, boolean enabled)
    {
        Scoreboard board = mainBoard();
        PlayerState current = playerStates.get(uuid);

        if (current != null && current.board == board && current.enabled == enabled
                && isTeamIntact(current, player.getName()))
        {
            enforceTeam(current);
            return;
        }

        if (current != null)
        {
            releaseTeam(player.getName(), current);
        }

        acquireTeam(player, uuid, enabled, board);
    }

    private void releaseScoreboard(Player player, UUID uuid)
    {
        PlayerState state = playerStates.remove(uuid);
        if (state != null)
        {
            releaseTeam(player.getName(), state);
        }
    }

    private void acquireTeam(Player player, UUID uuid, boolean enabled, Scoreboard board)
    {
        Team currentTeam = board.getEntryTeam(player.getName());

        if (enabled && currentTeam == null)
        {
            // No team needed for enabled collision — default is ALWAYS
            playerStates.put(uuid, new PlayerState(board, null, true, null, false));
            return;
        }

        boolean createdTeam = false;
        if (currentTeam == null)
        {
            String name = managedTeamName(uuid);
            currentTeam = board.getTeam(name);
            if (currentTeam == null)
            {
                currentTeam = board.registerNewTeam(name);
            }
            if (!currentTeam.hasEntry(player.getName()))
            {
                currentTeam.addEntry(player.getName());
            }
            createdTeam = true;
        }

        String teamName = currentTeam.getName();
        Team.OptionStatus desired = enabled ? Team.OptionStatus.ALWAYS : Team.OptionStatus.NEVER;

        TeamKey key = new TeamKey(board, teamName);
        TeamState teamState = teamStates.get(key);
        if (teamState == null)
        {
            teamState = new TeamState(currentTeam.getOption(COLLISION_OPTION));
            teamStates.put(key, teamState);
        }

        teamState.refs++;
        currentTeam.setOption(COLLISION_OPTION, desired);

        playerStates.put(uuid, new PlayerState(board, teamName, enabled, desired, createdTeam));
    }

    private void releaseTeam(String playerName, PlayerState state)
    {
        if (state.teamName == null)
        {
            return;
        }

        TeamKey key = new TeamKey(state.board, state.teamName);
        TeamState teamState = teamStates.get(key);
        Team team = state.board.getTeam(state.teamName);

        if (state.createdTeam && team != null && team.hasEntry(playerName))
        {
            team.removeEntry(playerName);
        }

        if (teamState == null)
        {
            if (state.createdTeam && team != null && team.getEntries().isEmpty())
            {
                team.unregister();
            }
            return;
        }

        teamState.refs--;
        if (teamState.refs > 0)
        {
            return;
        }

        if (team != null)
        {
            team.setOption(COLLISION_OPTION, teamState.originalStatus);
            if (state.createdTeam && team.getEntries().isEmpty())
            {
                team.unregister();
            }
        }

        teamStates.remove(key);
    }

    private static void enforceTeam(PlayerState state)
    {
        if (state.teamName == null)
        {
            return;
        }
        Team team = state.board.getTeam(state.teamName);
        if (team != null)
        {
            team.setOption(COLLISION_OPTION, state.desired);
        }
    }

    private static boolean isTeamIntact(PlayerState state, String playerName)
    {
        if (state.teamName == null)
        {
            return true;
        }
        Team team = state.board.getTeam(state.teamName);
        return team != null && team.hasEntry(playerName);
    }

    private static Scoreboard mainBoard()
    {
        return Bukkit.getScoreboardManager().getMainScoreboard();
    }

    private static String managedTeamName(UUID uuid)
    {
        return TEAM_PREFIX + uuid.toString().replace("-", "").substring(0, 8);
    }

    // ── data types ───────────────────────────────────────────────────

    private record TeamKey(Scoreboard board, String teamName) {}

    private record PlayerState(
            Scoreboard board,
            String teamName,           // null = no team needed (tracking-only)
            boolean enabled,
            Team.OptionStatus desired, // null = tracking-only
            boolean createdTeam
    ) {}

    private static final class TeamState
    {
        final Team.OptionStatus originalStatus;
        int refs;

        TeamState(Team.OptionStatus originalStatus)
        {
            this.originalStatus = originalStatus;
        }
    }
}
