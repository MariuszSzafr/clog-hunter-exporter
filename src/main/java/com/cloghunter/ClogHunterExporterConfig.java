package com.cloghunter;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;

@ConfigGroup("cloghunterexporter")
public interface ClogHunterExporterConfig extends Config
{
	@ConfigItem(
		keyName = "showWantedOverlay",
		name = "Show wanted overlay",
		description = "Show the wanted items exported by the Clog Hunter app."
	)
	default boolean showWantedOverlay()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showEmptyWantedOverlay",
		name = "Show empty/import status",
		description = "Show overlay status even when no wanted items are currently imported."
	)
	default boolean showEmptyWantedOverlay()
	{
		return true;
	}

	@ConfigItem(
		keyName = "hideObtainedWantedItems",
		name = "Hide obtained wanted items",
		description = "Hide wanted items that the app says are already obtained."
	)
	default boolean hideObtainedWantedItems()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showDropRatesOnOverlay",
		name = "Show drop rates",
		description = "Show drop-rate text under wanted items when available."
	)
	default boolean showDropRatesOnOverlay()
	{
		return true;
	}

	@Range(
		min = 1,
		max = 50
	)
	@ConfigItem(
		keyName = "maxWantedOverlayItems",
		name = "Max overlay items",
		description = "Maximum number of imported wanted items to show in the overlay."
	)
	default int maxWantedOverlayItems()
	{
		return 12;
	}
}
