package net.goldtreeservers.worldguardextraflags.util;

public final class PaperCollisionSupport
{
	private PaperCollisionSupport()
	{
	}

	public static Boolean isPlayerCollisionGloballyEnabled()
	{
		try
		{
			Class<?> globalConfigurationClass = Class.forName("io.papermc.paper.configuration.GlobalConfiguration");
			Object globalConfiguration = globalConfigurationClass.getMethod("get").invoke(null);
			Object collisions = globalConfigurationClass.getField("collisions").get(globalConfiguration);
			return collisions.getClass().getField("enablePlayerCollisions").getBoolean(collisions);
		}
		catch (Throwable ignored)
		{
			return null;
		}
	}
}
