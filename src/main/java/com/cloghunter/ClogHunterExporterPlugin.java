package com.cloghunter;

import java.awt.image.BufferedImage;
import net.runelite.client.util.ImageUtil;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import javax.inject.Inject;
import javax.swing.JButton;
import java.awt.Desktop;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JScrollPane;
import javax.swing.Box;
import javax.swing.BorderFactory;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Font;

import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.PluginPanel;
import java.awt.BorderLayout;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.ItemComposition;
import net.runelite.client.game.ItemManager;
import net.runelite.api.ScriptID;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.util.Text;

@Slf4j
@PluginDescriptor(
	name = "Clog Hunter Exporter"
)
public class ClogHunterExporterPlugin extends Plugin
{
	private static final int COLLECTION_LOG_ACTIVE_TAB_VARBIT_ID = 6905;
	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ItemManager itemManager;

	@Inject
	private ClientToolbar clientToolbar;

	private PluginPanel panel;
	private NavigationButton navButton;
	private JLabel statusLabel;
	private JLabel accountLabel;
	private JLabel categoryLabel;
	private JLabel pageLabel;
	private JLabel countLabel;
	private JLabel pagesLabel;
	private JLabel exportTimeLabel;
	private JTextArea missingPagesArea;
	private JScrollPane missingPagesScroll;
	private JButton openFolderButton;

	private BufferedImage createPanelIcon()
	{
		return ImageUtil.loadImageResource(
				getClass(),
				"/clog_hunter_icon.png"
		);
	}

	private JLabel makeHeader(String text)
	{
		JLabel label = new JLabel(text);
		label.setForeground(new Color(255, 176, 0));
		label.setFont(new Font("SansSerif", Font.BOLD, 14));
		return label;
	}

	private JLabel makeLine(String text)
	{
		JLabel label = new JLabel(text);
		label.setForeground(new Color(220, 220, 220));
		label.setFont(new Font("SansSerif", Font.PLAIN, 12));
		return label;
	}

	private JPanel makeBox()
	{
		JPanel box = new JPanel();
		box.setLayout(new javax.swing.BoxLayout(box, javax.swing.BoxLayout.Y_AXIS));
		box.setBackground(new Color(30, 30, 30));
		box.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createLineBorder(new Color(70, 70, 70)),
				BorderFactory.createEmptyBorder(8, 8, 8, 8)
		));
		return box;
	}

	private JLabel makeCenteredHeader(String text)
	{
		JLabel label = makeHeader(text);
		label.setHorizontalAlignment(SwingConstants.CENTER);
		label.setAlignmentX(0.5f);
		label.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, 24));
		return label;
	}

	private JPanel makeStatRow(String left, JLabel right)
	{
		JPanel row = new JPanel(new BorderLayout());
		row.setBackground(new Color(30, 30, 30));
		row.setBorder(BorderFactory.createEmptyBorder(3, 0, 3, 0));

		JLabel leftLabel = makeLine(left);
		right.setHorizontalAlignment(SwingConstants.RIGHT);

		row.add(leftLabel, BorderLayout.WEST);
		row.add(right, BorderLayout.EAST);

		return row;
	}

	@Override
	protected void startUp() throws Exception
	{
		log.info("=== CLOG HUNTER EXPORTER STARTED ===");

		panel = new PluginPanel(false) {};
		panel.setLayout(new BorderLayout());

		JPanel content = new JPanel();
		content.setLayout(new javax.swing.BoxLayout(content, javax.swing.BoxLayout.Y_AXIS));
		content.setAlignmentX(0.5f);

		statusLabel = makeLine("Status: Waiting");
		accountLabel = makeLine("Account: Unknown");
		categoryLabel = makeLine("Category: Unknown");
		pageLabel = makeLine("Last Page: None");
		countLabel = makeLine("Last Page Items: 0");
		pagesLabel = makeLine("Pages Captured: 0");
		exportTimeLabel = makeLine("Last Export: Never");
		missingPagesArea = new JTextArea("Open collection log");
		missingPagesArea.setEditable(false);
		missingPagesArea.setLineWrap(false);
		missingPagesArea.setBackground(new Color(20, 20, 20));
		missingPagesArea.setForeground(new Color(220, 220, 220));
		missingPagesArea.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
		missingPagesArea.setFont(new Font("SansSerif", Font.PLAIN, 12));

		openFolderButton = new JButton("Open Export Folder");

		openFolderButton.addActionListener(e ->
		{
			try
			{
				Path exportDir = Paths.get(
						System.getProperty("user.home"),
						".runelite",
						"clog-hunter"
				);

				Desktop.getDesktop().open(exportDir.toFile());
			}
			catch (Exception ex)
			{
				log.error("Failed opening export folder", ex);
			}
		});

		JLabel title = makeCenteredHeader("CLOG HUNTER");
		title.setFont(new Font("SansSerif", Font.BOLD, 18));
		content.add(title);

		JPanel statusBox = makeBox();
		statusBox.add(makeCenteredHeader("Export Status"));
		statusBox.add(makeStatRow("Account", accountLabel));
		statusBox.add(makeStatRow("Status", statusLabel));
		statusBox.add(makeStatRow("Captured", pagesLabel));
		statusBox.add(makeStatRow("Category", categoryLabel));
		statusBox.add(makeStatRow("Last Page", pageLabel));
		statusBox.add(makeStatRow("Items", countLabel));
		statusBox.add(makeStatRow("Export", exportTimeLabel));

		content.add(Box.createVerticalStrut(8));
		content.add(statusBox);
		content.add(Box.createVerticalStrut(6));
		JPanel buttonPanel = new JPanel();
		buttonPanel.setBackground(new Color(35, 35, 35));
		buttonPanel.add(openFolderButton);

		content.add(buttonPanel);
		content.add(Box.createVerticalStrut(10));
		JLabel missingHeader = makeCenteredHeader("Unscanned Pages");
		missingHeader.setAlignmentX(0.5f);
		content.add(missingHeader);

		missingPagesScroll = new JScrollPane(missingPagesArea);
		missingPagesScroll.setBorder(BorderFactory.createLineBorder(new Color(60, 60, 60)));
		missingPagesScroll.getViewport().setBackground(new Color(25, 25, 25));

		panel.add(content, BorderLayout.NORTH);
		panel.add(missingPagesScroll, BorderLayout.CENTER);

		navButton = NavigationButton.builder()
				.tooltip("Clog Hunter Exporter")
				.icon(createPanelIcon())
				.panel(panel)
				.build();

		clientToolbar.addNavigation(navButton);
	}

	@Override
	protected void shutDown() throws Exception
	{
		if (navButton != null)
		{
			clientToolbar.removeNavigation(navButton);
		}

		log.debug("Clog Hunter Exporter stopped.");
	}

	@Subscribe
	public void onScriptPostFired(ScriptPostFired scriptPostFired)
	{
		if (scriptPostFired.getScriptId() == ScriptID.COLLECTION_DRAW_LIST)
		{
			clientThread.invokeLater(this::dumpCurrentPage);
		}
	}

	private void dumpCurrentPage()
	{
		Widget itemsContainer = client.getWidget(ComponentID.COLLECTION_LOG_ENTRY_ITEMS);
		Widget pageHead = client.getWidget(ComponentID.COLLECTION_LOG_ENTRY_HEADER);

		String pageName = "Unknown";

		if (pageHead != null && pageHead.getDynamicChildren().length > 0)
		{
			pageName = pageHead.getDynamicChildren()[0].getText();
		}

		//log.info("Current page: {}", pageName);

		if (itemsContainer == null)
		{
			log.info("No collection log page open.");
			return;
		}

		try
		{
			exportCurrentPage();
		}
		catch (Exception e)
		{
			log.error("Failed to export collection log page", e);
		}
	}

	private String getCurrentCategory()
	{
		Widget tabsWidget = client.getWidget(ComponentID.COLLECTION_LOG_TABS);

		if (tabsWidget == null || tabsWidget.getStaticChildren() == null)
		{
			return "Unknown";
		}

		int tabIndex = client.getVarbitValue(COLLECTION_LOG_ACTIVE_TAB_VARBIT_ID);
		Widget[] tabs = tabsWidget.getStaticChildren();

		if (tabIndex < 0 || tabIndex >= tabs.length || tabs[tabIndex] == null)
		{
			return "Unknown";
		}

		return Text.removeTags(tabs[tabIndex].getName());
	}

	private void collectAllPageNames(Widget widget, java.util.Set<String> pages)
	{
		if (widget == null)
		{
			return;
		}

		String text = Text.removeTags(widget.getText()).trim();

		boolean looksLikePageRow =
				!text.isEmpty()
						&& widget.getRelativeX() == 0
						&& widget.getWidth() == 180
						&& widget.getHeight() == 15
						&& !text.startsWith("Check ")
						&& !text.contains("more options");

		if (looksLikePageRow)
		{
			pages.add(text);
		}

		for (Widget child : widget.getDynamicChildren())
		{
			collectAllPageNames(child, pages);
		}

		for (Widget child : widget.getStaticChildren())
		{
			collectAllPageNames(child, pages);
		}

		for (Widget child : widget.getNestedChildren())
		{
			collectAllPageNames(child, pages);
		}
	}

	private String buildMissingPagesText(JsonObject root, JsonObject tabs)
	{
		java.util.Set<String> knownPages = new java.util.TreeSet<>();
		java.util.Set<String> capturedPages = new java.util.HashSet<>();

		JsonObject knownRoot = root.getAsJsonObject("known_pages");

		if (knownRoot == null)
		{
			knownRoot = new JsonObject();
			root.add("known_pages", knownRoot);
		}

		Widget container = client.getWidget(ComponentID.COLLECTION_LOG_CONTAINER);

		if (container != null)
		{
			java.util.Set<String> currentlySeenPages = new java.util.TreeSet<>();
			collectAllPageNames(container, currentlySeenPages);

			for (String page : currentlySeenPages)
			{
				knownRoot.addProperty(page, true);
			}
		}

		for (String pageKey : knownRoot.keySet())
		{
			knownPages.add(pageKey);
		}

		for (String tabKey : tabs.keySet())
		{
			JsonObject tab = tabs.getAsJsonObject(tabKey);

			for (String pageKey : tab.keySet())
			{
				capturedPages.add(pageKey);
			}
		}

		knownPages.removeAll(capturedPages);

		StringBuilder text = new StringBuilder();
		text.append(knownPages.size()).append(" pages missing\n\n");

		for (String page : knownPages)
		{
			text.append(page).append("\n");
		}

		return text.toString();
	}

	private void exportCurrentPage() throws Exception
	{
		Widget itemsContainer = client.getWidget(ComponentID.COLLECTION_LOG_ENTRY_ITEMS);
		Widget pageHead = client.getWidget(ComponentID.COLLECTION_LOG_ENTRY_HEADER);

		if (itemsContainer == null)
		{
			log.info("No collection log page open.");
			return;
		}

		String pageName = "Unknown";

		if (pageHead != null && pageHead.getDynamicChildren().length > 0)
		{
			pageName = pageHead.getDynamicChildren()[0].getText();
		}

		Path exportDir = Paths.get(
				System.getProperty("user.home"),
				".runelite",
				"clog-hunter"
		);

		Files.createDirectories(exportDir);

		Path exportFile = exportDir.resolve("clog_hunter_export.json");

		JsonObject root;

		String playerName = client.getLocalPlayer() != null
				? client.getLocalPlayer().getName()
				: "Unknown";

		if (Files.exists(exportFile))
		{
			String existing = Files.readString(exportFile);
			root = new JsonParser().parse(existing).getAsJsonObject();
		}
		else
		{
			root = new JsonObject();
			root.addProperty("plugin", "Clog Hunter Exporter");
			root.addProperty("version", "0.3");
			root.addProperty("status", "accumulated_export");
			root.addProperty("account", playerName);
			root.addProperty("timestamp", System.currentTimeMillis());
			root.add("tabs", new JsonObject());
		}

		root.addProperty("plugin", "Clog Hunter Exporter");
		root.addProperty("version", "0.3");
		root.addProperty("status", "accumulated_export");
		root.addProperty("account", playerName);
		root.addProperty("timestamp", System.currentTimeMillis());

		JsonObject tabs = root.getAsJsonObject("tabs");

		if (tabs == null)
		{
			tabs = new JsonObject();
			root.add("tabs", tabs);
		}

		String categoryName = getCurrentCategory();

		JsonObject categoryTab = tabs.getAsJsonObject(categoryName);

		if (categoryTab == null)
		{
			categoryTab = new JsonObject();
			tabs.add(categoryName, categoryTab);
		}

		JsonObject page = new JsonObject();
		JsonArray itemArray = new JsonArray();

		for (Widget widgetItem : itemsContainer.getDynamicChildren())
		{
			JsonObject item = new JsonObject();

			int itemId = widgetItem.getItemId();
			ItemComposition itemComposition = itemManager.getItemComposition(itemId);
			String itemName = itemComposition.getMembersName();

			item.addProperty("id", itemId);
			item.addProperty("name", itemName);
			item.addProperty("quantity", widgetItem.getItemQuantity());
			item.addProperty("obtained", widgetItem.getOpacity() == 0);

			itemArray.add(item);
		}

		page.add("items", itemArray);

		categoryTab.add(pageName, page);

		int capturedPages = 0;

		for (String tabKey : tabs.keySet())
		{
			JsonObject tab = tabs.getAsJsonObject(tabKey);
			capturedPages += tab.keySet().size();
		}

		String exportTime = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
		String missingPagesText = buildMissingPagesText(root, tabs);

		final String finalPlayerName = playerName;
		final String finalCategoryName = categoryName;
		final String finalPageName = pageName;
		final int finalItemCount = itemArray.size();
		final int finalCapturedPages = capturedPages;
		final String finalExportTime = exportTime;
		final String finalMissingPagesText = missingPagesText;

		SwingUtilities.invokeLater(() ->
		{
			statusLabel.setText("Recording");
			accountLabel.setText(finalPlayerName);
			categoryLabel.setText(finalCategoryName);
			pageLabel.setText(finalPageName);
			countLabel.setText(String.valueOf(finalItemCount));
			pagesLabel.setText(String.valueOf(finalCapturedPages));
			exportTimeLabel.setText(finalExportTime);
			missingPagesArea.setText(finalMissingPagesText);
			missingPagesArea.setCaretPosition(0);
		});

		Files.writeString(exportFile, root.toString());

		log.debug("Updated accumulated collection log export for page: {}", pageName);
	}

}
