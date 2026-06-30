package com.cloghunter;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.util.List;
import javax.inject.Inject;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

class ClogHunterOverlay extends OverlayPanel
{
	private final ClogHunterExporterPlugin plugin;
	private final ClogHunterExporterConfig config;
	private static final Color GOLD = new Color(255, 176, 0);
	private static final Color GREEN = new Color(87, 214, 90);
	private static final Color MUTED = new Color(180, 180, 180);

	@Inject
	ClogHunterOverlay(ClogHunterExporterPlugin plugin, ClogHunterExporterConfig config)
	{
		this.plugin = plugin;
		this.config = config;
		setPosition(OverlayPosition.TOP_LEFT);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		panelComponent.getChildren().clear();
		panelComponent.setPreferredSize(new Dimension(210, 0));

		boolean renderedSomething = false;

		if (plugin.hasScanPagesRemaining())
		{
			renderedSomething = true;
			String category = plugin.getNextScanCategory();
			String nextPage = plugin.getNextScanPage();
			int pagesLeft = plugin.getMissingScanPageCount();
			int captured = plugin.getScanCapturedPageCount();

			panelComponent.getChildren().add(TitleComponent.builder()
					.text("Manual Scan")
					.color(GOLD)
					.build());

			if (category != null && !category.trim().isEmpty() && !"Unknown".equals(category))
			{
				panelComponent.getChildren().add(LineComponent.builder()
						.left(category)
						.build());
			}

			panelComponent.getChildren().add(LineComponent.builder()
					.left("Next Page")
					.build());

			panelComponent.getChildren().add(LineComponent.builder()
					.left(shortOverlayText(nextPage, 28))
					.build());

			panelComponent.getChildren().add(LineComponent.builder()
					.left("Pages Left: " + pagesLeft)
					.build());

			panelComponent.getChildren().add(LineComponent.builder()
					.left("Captured: " + captured)
					.build());

			panelComponent.getChildren().add(LineComponent.builder()
					.left("Open next page manually")
					.build());
		}

		if (config.showWantedOverlay())
		{
			List<WantedItem> wantedItems = plugin.getImportedWantedItems();

			if (wantedItems.isEmpty())
			{
				if (config.showEmptyWantedOverlay())
				{
					renderedSomething = true;

					panelComponent.getChildren().add(TitleComponent.builder()
							.text("Clog Hunter Wanted")
							.color(GOLD)
							.build());

					panelComponent.getChildren().add(LineComponent.builder()
							.left(plugin.getWantedImportStatus())
							.leftColor(MUTED)
							.build());
				}
			}
			else
			{
				renderedSomething = true;

				panelComponent.getChildren().add(TitleComponent.builder()
						.text("Clog Hunter Wanted")
						.color(GOLD)
						.build());

				panelComponent.getChildren().add(LineComponent.builder()
						.left(plugin.getWantedImportSourceName())
						.right(wantedItems.size() + " items")
						.leftColor(MUTED)
						.rightColor(GREEN)
						.build());

				int limit = Math.min(wantedItems.size(), config.maxWantedOverlayItems());

				for (int i = 0; i < limit; i++)
				{
					WantedItem item = wantedItems.get(i);

					panelComponent.getChildren().add(LineComponent.builder()
							.left(shorten(item.getName(), 30))
							.leftColor(new Color(230, 230, 230))
							.build());

					if (config.showDropRatesOnOverlay() && item.getDropRate() != null && !item.getDropRate().trim().isEmpty())
					{
						panelComponent.getChildren().add(LineComponent.builder()
								.left("  " + shorten(item.getDropRate(), 28))
								.leftColor(MUTED)
								.build());
					}
				}

				if (wantedItems.size() > limit)
				{
					panelComponent.getChildren().add(LineComponent.builder()
							.left("+" + (wantedItems.size() - limit) + " more")
							.leftColor(MUTED)
							.build());
				}
			}
		}

		if (!renderedSomething)
		{
			return null;
		}

		return super.render(graphics);
	}

	private String shortOverlayText(String text, int maxLength)
	{
		if (text == null)
		{
			return "";
		}

		text = text.trim();

		if (text.length() <= maxLength)
		{
			return text;
		}

		return text.substring(0, Math.max(0, maxLength - 1)) + "…";
	}

	private String shorten(String text, int maxLength)
	{
		if (text == null || text.length() <= maxLength)
		{
			return text;
		}

		return text.substring(0, maxLength - 1) + "…";
	}
}
