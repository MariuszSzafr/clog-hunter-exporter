package com.cloghunter;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class ClogHunterExporterPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(com.cloghunter.ClogHunterExporterPlugin.class);
		RuneLite.main(args);
	}
}