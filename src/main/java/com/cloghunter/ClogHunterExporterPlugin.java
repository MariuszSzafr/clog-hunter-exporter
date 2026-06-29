package com.cloghunter;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.inject.Provides;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.image.BufferedImage;
import java.awt.datatransfer.StringSelection;
import java.awt.Toolkit;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import javax.inject.Inject;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.ItemComposition;
import net.runelite.api.ScriptID;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.LinkBrowser;
import net.runelite.client.util.Text;

@Slf4j
@PluginDescriptor(
		name = "Clog Hunter"
)
public class ClogHunterExporterPlugin extends Plugin
{
	private static final int COLLECTION_LOG_ACTIVE_TAB_VARBIT_ID = 6905;
	@Inject
	private Client client;

	@Inject
	private Gson gson;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ItemManager itemManager;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private ClogHunterOverlay wantedOverlay;

	@Inject
	private ClogHunterExporterConfig config;

	private static final String EXPORT_FILE_NAME = "clog_hunter_export.json";
	private static final String WANTED_IMPORT_FILE_NAME = "clog_hunter_wanted.json";
	private static final String STATUS_FILE_NAME = "clog_hunter_status.json";
	private static final String WANTED_MEMORY_FILE_NAME = "clog_hunter_wanted_memory.json";
	private static final int WANTED_IMPORT_CHECK_TICKS = 2;

	private volatile List<WantedItem> importedWantedItems = Collections.emptyList();
	private volatile String wantedImportSourceName = "No app import";
	private volatile String wantedImportStatus = "Waiting for app import";
	private long wantedImportLastModified = -1L;
	private int wantedImportTickCounter = 0;

	private PluginPanel panel;
	private NavigationButton navButton;
	private JLabel statusLabel;
	private JLabel accountLabel;
	private JLabel categoryLabel;
	private JLabel pageLabel;
	private JLabel countLabel;
	private JLabel pagesLabel;
	private JLabel exportTimeLabel;
	private JLabel wantedImportLabel;
	private JPanel scanCardsPanel;
	private JScrollPane scanCardsScroll;
	private volatile List<String> missingScanPages = Collections.emptyList();
	private volatile String nextScanPage = "";
	private volatile String nextScanCategory = "Unknown";
	private volatile String scanCategoryName = "Unknown";
	private volatile String scanLastPageName = "None";
	private volatile int scanCapturedPageCount = 0;
	private JButton openFolderButton;

	@Provides
	ClogHunterExporterConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(ClogHunterExporterConfig.class);
	}

	public List<WantedItem> getImportedWantedItems()
	{
		return importedWantedItems;
	}

	public String getWantedImportSourceName()
	{
		return wantedImportSourceName;
	}

	public String getWantedImportStatus()
	{
		return wantedImportStatus;
	}

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

private JLabel makeCenteredHeader(String text)
	{
		JLabel label = makeHeader(text);
		label.setHorizontalAlignment(SwingConstants.CENTER);
		label.setAlignmentX(0.5f);
		label.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, 24));
		return label;
	}

@Override
	protected void startUp() throws Exception
	{
		log.info("=== CLOG HUNTER EXPORTER STARTED ===");

		panel = new PluginPanel(false) {};
		panel.setLayout(new BorderLayout());

		panel.setBackground(new Color(25, 25, 25));

		JPanel content = new JPanel();
		content.setLayout(new javax.swing.BoxLayout(content, javax.swing.BoxLayout.Y_AXIS));
		content.setAlignmentX(0.5f);
		content.setBackground(new Color(25, 25, 25));
		content.setBorder(BorderFactory.createEmptyBorder(10, 12, 6, 12));

		statusLabel = makeLine("Waiting");
		accountLabel = makeLine("Unknown");
		categoryLabel = makeLine("Unknown");
		pageLabel = makeLine("None");
		countLabel = makeLine("0");
		pagesLabel = makeLine("0");
		exportTimeLabel = makeLine("Never");
		wantedImportLabel = makeLine("Waiting");
		openFolderButton = new JButton("Open Export Folder");

		openFolderButton.addActionListener(e ->
		{
			try
			{
				Path exportDir = getPublicExportDir();
				Files.createDirectories(exportDir);

				String pathText = exportDir.toAbsolutePath().toString();

				Toolkit.getDefaultToolkit()
						.getSystemClipboard()
						.setContents(new StringSelection(pathText), null);

				LinkBrowser.browse(exportDir.toUri().toString());

				client.addChatMessage(
						ChatMessageType.GAMEMESSAGE,
						"",
						"Clog Hunter export folder path copied to clipboard.",
						null
				);
			}
			catch (Exception ex)
			{
				log.error("Failed opening export folder", ex);

				client.addChatMessage(
						ChatMessageType.GAMEMESSAGE,
						"",
						"Clog Hunter could not open the export folder.",
						null
				);
			}
		});

		JLabel title = makeCenteredHeader("CLOG HUNTER");
		title.setFont(new Font("SansSerif", Font.BOLD, 19));
		title.setForeground(new Color(255, 176, 0));
		content.add(title);
		content.add(Box.createVerticalStrut(14));

		content.add(makeValueCard("#ffb000", "Account Name", accountLabel));
		content.add(Box.createVerticalStrut(12));
		content.add(makeValueCard("#57d65a", "Wanted Items", wantedImportLabel));

		content.add(Box.createVerticalStrut(26));
		JLabel scanHeader = makeCenteredHeader("Manual Scan Helper");
		scanHeader.setFont(new Font("SansSerif", Font.BOLD, 17));
		scanHeader.setAlignmentX(0.5f);
		content.add(scanHeader);
		content.add(Box.createVerticalStrut(14));

		scanCardsPanel = new JPanel();
		scanCardsPanel.setLayout(new javax.swing.BoxLayout(scanCardsPanel, javax.swing.BoxLayout.Y_AXIS));
		scanCardsPanel.setBackground(new Color(25, 25, 25));
		scanCardsPanel.setBorder(BorderFactory.createEmptyBorder(4, 12, 16, 12));

		scanCardsScroll = new JScrollPane(scanCardsPanel);
		scanCardsScroll.setBorder(BorderFactory.createEmptyBorder());
		scanCardsScroll.getViewport().setBackground(new Color(25, 25, 25));
		scanCardsScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);

		refreshScanCards(Collections.emptyList(), 0, "Unknown", "None");

		panel.add(content, BorderLayout.NORTH);
		panel.add(scanCardsScroll, BorderLayout.CENTER);

		JPanel bottomToolsPanel = buildBottomToolsPanel();
		panel.add(bottomToolsPanel, BorderLayout.SOUTH);

		navButton = NavigationButton.builder()
				.tooltip("Clog Hunter Exporter")
				.icon(createPanelIcon())
				.panel(panel)
				.build();

		clientToolbar.addNavigation(navButton);
		overlayManager.add(wantedOverlay);

		clientThread.invokeLater(() -> loadWantedItemsFromApp(true));
	}

	@Override
	protected void shutDown() throws Exception
	{
		if (navButton != null)
		{
			clientToolbar.removeNavigation(navButton);
		}

		overlayManager.remove(wantedOverlay);

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

	@Subscribe
	public void onGameTick(GameTick gameTick)
	{
		wantedImportTickCounter++;

		if (wantedImportTickCounter >= WANTED_IMPORT_CHECK_TICKS)
		{
			wantedImportTickCounter = 0;
			loadWantedItemsFromApp(false);
		}
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		String message = clean(event.getMessage());
		String lowerMessage = message.toLowerCase(Locale.ROOT);
		String lowerPrefix = "new item added to your collection log:";

		int prefixIndex = lowerMessage.indexOf(lowerPrefix);

		if (prefixIndex == -1)
		{
			return;
		}

		String itemName = message.substring(prefixIndex + lowerPrefix.length()).trim();

		if (itemName.endsWith("."))
		{
			itemName = itemName.substring(0, itemName.length() - 1).trim();
		}

		if (itemName.isEmpty())
		{
			return;
		}

		writeChatWantedCompletionStatus(itemName, message);
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

private List<String> buildMissingPagesList(JsonObject root, JsonObject tabs)
	{
		JsonObject knownByCategory = root.getAsJsonObject("known_pages_by_category");

		if (knownByCategory == null)
		{
			knownByCategory = new JsonObject();
			root.add("known_pages_by_category", knownByCategory);
		}

		Widget container = client.getWidget(ComponentID.COLLECTION_LOG_CONTAINER);

		if (container != null)
		{
			collectPageNamesByCategory(container, knownByCategory, 0);
		}

		List<String> missing = new ArrayList<>();

		for (String category : collectionCategoryOrder())
		{
			JsonObject knownCategory = knownByCategory.getAsJsonObject(category);

			if (knownCategory == null)
			{
				continue;
			}

			Set<String> capturedPages = new HashSet<>();
			JsonObject capturedCategory = tabs == null ? null : tabs.getAsJsonObject(category);

			if (capturedCategory != null)
			{
				for (String pageKey : capturedCategory.keySet())
				{
					capturedPages.add(pageKey);
				}
			}

			for (String page : knownCategory.keySet())
			{
				if (!capturedPages.contains(page))
				{
					missing.add(category + " • " + page);
				}
			}
		}

		return missing;
	}

	private void collectPageNamesByCategory(Widget widget, JsonObject knownByCategory, int depth)
	{
		if (widget == null || depth > 30)
		{
			return;
		}

		if (looksLikeCleanCollectionPageRow(widget))
		{
			String pageName = guessWidgetLabelDeep(widget, 0).trim();
			String category = collectionCategoryFromPageRowChildId(widget.getId() & 0xFFFF);

			if (!pageName.isEmpty() && !category.startsWith("Unknown"))
			{
				JsonObject categoryRoot = knownByCategory.getAsJsonObject(category);

				if (categoryRoot == null)
				{
					categoryRoot = new JsonObject();
					knownByCategory.add(category, categoryRoot);
				}

				if (!categoryRoot.has(pageName))
				{
					categoryRoot.addProperty(pageName, true);
				}
			}
		}

		collectPageNamesByCategoryChildren(widget.getStaticChildren(), knownByCategory, depth + 1);
		collectPageNamesByCategoryChildren(widget.getDynamicChildren(), knownByCategory, depth + 1);
		collectPageNamesByCategoryChildren(widget.getNestedChildren(), knownByCategory, depth + 1);
	}

	private void collectPageNamesByCategoryChildren(Widget[] children, JsonObject knownByCategory, int depth)
	{
		if (children == null)
		{
			return;
		}

		for (Widget child : children)
		{
			if (child != null)
			{
				collectPageNamesByCategory(child, knownByCategory, depth);
			}
		}
	}

	private String[] collectionCategoryOrder()
	{
		return new String[] {"Bosses", "Raids", "Clues", "Minigames", "Other"};
	}

	private void updateScanState(List<String> missingPages, int capturedPages, String categoryName, String pageName)
	{
		List<String> safeMissing = missingPages == null
				? Collections.emptyList()
				: Collections.unmodifiableList(new ArrayList<>(missingPages));

		missingScanPages = safeMissing;
		nextScanPage = safeMissing.isEmpty() ? "" : scanPageName(safeMissing.get(0));
		nextScanCategory = safeMissing.isEmpty() ? "Unknown" : scanPageCategory(safeMissing.get(0));
		scanCapturedPageCount = Math.max(0, capturedPages);
		scanCategoryName = categoryName == null || categoryName.trim().isEmpty() ? "Unknown" : categoryName;
		scanLastPageName = pageName == null || pageName.trim().isEmpty() ? "None" : pageName;

		refreshScanCards(safeMissing, scanCapturedPageCount, scanCategoryName, scanLastPageName);
	}

	public List<String> getMissingScanPages()
	{
		return missingScanPages;
	}

	public int getMissingScanPageCount()
	{
		return missingScanPages.size();
	}

	public String getNextScanPage()
	{
		return nextScanPage == null ? "" : nextScanPage;
	}

	public String getNextScanCategory()
	{
		return nextScanCategory == null ? "Unknown" : nextScanCategory;
	}

	public int getScanCapturedPageCount()
	{
		return scanCapturedPageCount;
	}

	public String getScanCategoryName()
	{
		return scanCategoryName == null ? "Unknown" : scanCategoryName;
	}

	public String getScanLastPageName()
	{
		return scanLastPageName == null ? "None" : scanLastPageName;
	}

	public boolean hasScanPagesRemaining()
	{
		return getMissingScanPageCount() > 0;
	}

	private JPanel makeValueCard(String accentHex, String title, JLabel valueLabel)
	{
		JPanel outer = new JPanel(new BorderLayout());
		outer.setBackground(new Color(34, 34, 34));
		outer.setAlignmentX(0.5f);
		outer.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createLineBorder(new Color(76, 76, 76)),
				BorderFactory.createEmptyBorder(0, 0, 0, 0)
		));
		outer.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, 58));

		JPanel accent = new JPanel();
		accent.setBackground(Color.decode(accentHex));
		accent.setPreferredSize(new java.awt.Dimension(5, 58));

		JPanel body = new JPanel(new BorderLayout());
		body.setBackground(new Color(34, 34, 34));
		body.setBorder(BorderFactory.createEmptyBorder(13, 13, 13, 13));

		JLabel titleLabel = new JLabel(title);
		titleLabel.setForeground(new Color(238, 238, 238));
		titleLabel.setFont(new Font("SansSerif", Font.BOLD, 13));

		valueLabel.setForeground(new Color(245, 245, 245));
		valueLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
		valueLabel.setHorizontalAlignment(SwingConstants.RIGHT);

		body.add(titleLabel, BorderLayout.WEST);
		body.add(valueLabel, BorderLayout.EAST);

		outer.add(accent, BorderLayout.WEST);
		outer.add(body, BorderLayout.CENTER);

		return outer;
	}

	private JPanel buildBottomToolsPanel()
	{
		JPanel panel = new JPanel();
		panel.setLayout(new javax.swing.BoxLayout(panel, javax.swing.BoxLayout.Y_AXIS));
		panel.setBackground(new Color(25, 25, 25));
		panel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

		openFolderButton.setAlignmentX(0.5f);
		openFolderButton.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, 30));
		openFolderButton.setFocusPainted(false);

		panel.add(openFolderButton);

		return panel;
	}

	private JPanel makePrettyCard(String accentHex, String title, String line1, String line2)
	{
		JPanel outer = new JPanel(new BorderLayout());
		outer.setBackground(new Color(34, 34, 34));
		outer.setAlignmentX(0.5f);
		outer.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createLineBorder(new Color(78, 78, 78)),
				BorderFactory.createEmptyBorder(0, 0, 0, 0)
		));
		outer.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, 88));

		JPanel accent = new JPanel();
		accent.setBackground(Color.decode(accentHex));
		accent.setPreferredSize(new java.awt.Dimension(5, 88));

		JPanel textWrap = new JPanel();
		textWrap.setLayout(new javax.swing.BoxLayout(textWrap, javax.swing.BoxLayout.Y_AXIS));
		textWrap.setBackground(new Color(34, 34, 34));
		textWrap.setBorder(BorderFactory.createEmptyBorder(13, 14, 12, 14));

		JLabel titleLabel = new JLabel(title);
		titleLabel.setForeground(new Color(242, 242, 242));
		titleLabel.setFont(new Font("SansSerif", Font.BOLD, 14));

		JLabel line1Label = new JLabel(line1);
		line1Label.setForeground(new Color(224, 224, 224));
		line1Label.setFont(new Font("SansSerif", Font.PLAIN, 12));

		JLabel line2Label = new JLabel(line2);
		line2Label.setForeground(new Color(165, 165, 165));
		line2Label.setFont(new Font("SansSerif", Font.PLAIN, 11));

		textWrap.add(titleLabel);
		textWrap.add(Box.createVerticalStrut(6));
		textWrap.add(line1Label);
		textWrap.add(Box.createVerticalStrut(2));
		textWrap.add(line2Label);

		outer.add(accent, BorderLayout.WEST);
		outer.add(textWrap, BorderLayout.CENTER);

		return outer;
	}

	private String shortCardText(String text, int maxLength)
	{
		if (text == null)
		{
			return "";
		}

		if (text.length() <= maxLength)
		{
			return text;
		}

		return text.substring(0, maxLength - 1) + "…";
	}

	private String scanPageCategory(String entry)
	{
		if (entry == null)
		{
			return "Unknown";
		}

		int split = entry.indexOf(" • ");

		if (split <= 0)
		{
			return "Unknown";
		}

		return entry.substring(0, split).trim();
	}

	private String scanPageName(String entry)
	{
		if (entry == null)
		{
			return "";
		}

		int split = entry.indexOf(" • ");

		if (split < 0 || split + 3 >= entry.length())
		{
			return entry.trim();
		}

		return entry.substring(split + 3).trim();
	}

	private void refreshScanCards(List<String> missingPages, int capturedPages, String categoryName, String pageName)
	{
		if (scanCardsPanel == null)
		{
			return;
		}

		List<String> safeMissing = missingPages == null ? Collections.emptyList() : new ArrayList<>(missingPages);

		SwingUtilities.invokeLater(() ->
		{
			scanCardsPanel.removeAll();

			if (safeMissing.isEmpty() && capturedPages <= 0)
			{
				scanCardsPanel.add(makePrettyCard(
						"#6a6aff",
						"Open Collection Log",
						"Waiting for page data",
						"Open a collection-log page to begin."
				));
			}
			else if (safeMissing.isEmpty())
			{
				scanCardsPanel.add(makePrettyCard(
						"#57d65a",
						"Scan Complete",
						capturedPages + " pages captured",
						"No unscanned pages detected."
				));
			}
			else
			{
				String nextEntry = safeMissing.get(0);
				String nextCategory = scanPageCategory(nextEntry);
				String nextPage = scanPageName(nextEntry);

				scanCardsPanel.add(makePrettyCard(
						"#5aa3ff",
						"Scan Progress",
						capturedPages + " captured  •  " + safeMissing.size() + " remaining",
						"Last scanned: " + shortCardText(pageName, 24)
				));

				scanCardsPanel.add(Box.createVerticalStrut(16));
				scanCardsPanel.add(makePrettyCard(
						"#ffb000",
						"Next Page",
						shortCardText(nextPage, 30),
						nextCategory + "  •  open this page manually"
				));
			}

			scanCardsPanel.revalidate();
			scanCardsPanel.repaint();
		});
	}


	private Path getRootDir()
	{
		return Paths.get(
				System.getProperty("user.home"),
				".runelite",
				"clog-hunter"
		);
	}

	private Path getPublicExportDir()
	{
		return getRootDir()
				.resolve("exports")
				.resolve(getCurrentAccountFolderName());
	}

	private String getCurrentAccountFolderName()
	{
		String accountName = client.getLocalPlayer() == null
				? "Unknown"
				: client.getLocalPlayer().getName();

		return safePathName(accountName);
	}

	private String safePathName(String value)
	{
		String cleaned = value == null ? "" : value.trim();

		if (cleaned.isEmpty())
		{
			return "Unknown";
		}

		cleaned = cleaned.replaceAll("[\\\\/:*?\\\"<>|]", "_");
		cleaned = cleaned.replaceAll("\\s+", " ").trim();

		return cleaned.isEmpty() ? "Unknown" : cleaned;
	}

	private Path getSyncDir()
	{
		return getRootDir().resolve("sync");
	}

	private Path getExportFile()
	{
		return getPublicExportDir().resolve(EXPORT_FILE_NAME);
	}

	private Path getWantedImportFile()
	{
		return getSyncDir().resolve(WANTED_IMPORT_FILE_NAME);
	}

	private Path getStatusFile()
	{
		return getSyncDir().resolve(STATUS_FILE_NAME);
	}

	private Path getWantedMemoryFile()
	{
		return getSyncDir().resolve(WANTED_MEMORY_FILE_NAME);
	}


	private String getString(JsonObject object, String key, String defaultValue)
	{
		if (object == null || !object.has(key) || object.get(key).isJsonNull())
		{
			return defaultValue;
		}

		try
		{
			return object.get(key).getAsString();
		}
		catch (Exception ex)
		{
			return defaultValue;
		}
	}

	private int getInt(JsonObject object, String key, int defaultValue)
	{
		if (object == null || !object.has(key) || object.get(key).isJsonNull())
		{
			return defaultValue;
		}

		try
		{
			return object.get(key).getAsInt();
		}
		catch (Exception ex)
		{
			return defaultValue;
		}
	}

	private boolean getBoolean(JsonObject object, String key, boolean defaultValue)
	{
		if (object == null || !object.has(key) || object.get(key).isJsonNull())
		{
			return defaultValue;
		}

		try
		{
			return object.get(key).getAsBoolean();
		}
		catch (Exception ex)
		{
			return defaultValue;
		}
	}

	private Set<Integer> getIntSet(JsonObject object, String key)
	{
		Set<Integer> ids = new HashSet<>();

		if (object == null || !object.has(key) || !object.get(key).isJsonArray())
		{
			return ids;
		}

		for (JsonElement element : object.getAsJsonArray(key))
		{
			try
			{
				ids.add(element.getAsInt());
			}
			catch (Exception ignored)
			{
				// Ignore malformed IDs instead of breaking the overlay.
			}
		}

		return ids;
	}

	private String getItemNameFromCache(int itemId, String defaultValue)
	{
		if (itemId <= 0)
		{
			return defaultValue;
		}

		if (!client.isClientThread())
		{
			return defaultValue;
		}

		try
		{
			ItemComposition itemComposition = itemManager.getItemComposition(itemId);
			String name = itemComposition.getMembersName();

			if (name != null && !name.trim().isEmpty())
			{
				return name;
			}
		}
		catch (Exception ignored)
		{
			// Use the JSON name/fallback if the cache lookup is unavailable.
		}

		return defaultValue;
	}

	private void setWantedImportStatus(String status)
	{
		wantedImportStatus = status;

		if (wantedImportLabel != null)
		{
			SwingUtilities.invokeLater(() -> wantedImportLabel.setText(status));
		}
	}

	private void loadWantedItemsFromApp(boolean force)
	{
		Path wantedFile = getWantedImportFile();

		try
		{
			if (!Files.exists(wantedFile))
			{
				importedWantedItems = Collections.emptyList();
				wantedImportSourceName = "No app import";
				wantedImportLastModified = -1L;
				setWantedImportStatus("0");
				return;
			}

			long modified = Files.getLastModifiedTime(wantedFile).toMillis();

			if (!force && modified == wantedImportLastModified)
			{
				return;
			}

			String json = Files.readString(wantedFile);
			JsonObject root = new JsonParser().parse(json).getAsJsonObject();
			rememberWantedProfile(root);
			Set<Integer> obtainedIds = getIntSet(root, "obtained_ids");
			List<WantedItem> loaded = new ArrayList<>();

			if (root.has("items") && root.get("items").isJsonArray())
			{
				for (JsonElement element : root.getAsJsonArray("items"))
				{
					if (!element.isJsonObject())
					{
						continue;
					}

					JsonObject item = element.getAsJsonObject();
					int itemId = getInt(item, "id", -1);
					boolean obtained = getBoolean(item, "obtained", obtainedIds.contains(itemId));

					if (config.hideObtainedWantedItems() && obtained)
					{
						continue;
					}

					String fallbackName = itemId > 0 ? "Item " + itemId : "Unknown item";
					String itemName = getString(item, "name", fallbackName);
					itemName = getItemNameFromCache(itemId, itemName);
					int quantity = getInt(item, "quantity", 0);
					String activity = getString(item, "activity", "");
					String category = getString(item, "category", "");
					String dropRate = getString(item, "drop_rate", getString(item, "rate", ""));

					loaded.add(new WantedItem(itemId, itemName, quantity, activity, category, dropRate, obtained));
				}
			}

			if (loaded.isEmpty() && root.has("wanted_ids") && root.get("wanted_ids").isJsonArray())
			{
				for (JsonElement element : root.getAsJsonArray("wanted_ids"))
				{
					int itemId;

					try
					{
						itemId = element.getAsInt();
					}
					catch (Exception ignored)
					{
						continue;
					}

					if (config.hideObtainedWantedItems() && obtainedIds.contains(itemId))
					{
						continue;
					}

					String itemName = getItemNameFromCache(itemId, "Item " + itemId);
					loaded.add(new WantedItem(itemId, itemName, 0, "", "", "", false));
				}
			}

			List<WantedItem> uniqueLoaded = dedupeWantedItemsByName(loaded);

			importedWantedItems = Collections.unmodifiableList(uniqueLoaded);
			wantedImportSourceName = getString(root, "profile", getString(root, "account", "App import"));

			String importedAccountName = getString(root, "account", wantedImportSourceName);

			if (importedAccountName.trim().isEmpty())
			{
				importedAccountName = wantedImportSourceName;
			}

			final String finalImportedAccountName = importedAccountName;

			if (accountLabel != null)
			{
				SwingUtilities.invokeLater(() -> accountLabel.setText(finalImportedAccountName));
			}

			wantedImportLastModified = modified;
			setWantedImportStatus(String.valueOf(uniqueLoaded.size()));
		}
		catch (Exception ex)
		{
			log.warn("Failed reading Clog Hunter wanted import file: {}", wantedFile, ex);
			setWantedImportStatus("Import error");
		}
	}

	private List<WantedItem> dedupeWantedItemsByName(List<WantedItem> items)
	{
		List<WantedItem> unique = new ArrayList<>();
		Set<String> seenNames = new HashSet<>();

		for (WantedItem item : items)
		{
			if (item == null)
			{
				continue;
			}

			String key = normalizeItemName(item.getName());

			// If somehow the item has no name, do not merge it with other unnamed items.
			// Just keep it.
			if (key.isEmpty())
			{
				unique.add(item);
				continue;
			}

			if (seenNames.contains(key))
			{
				continue;
			}

			seenNames.add(key);
			unique.add(item);
		}

		return unique;
	}

	private void rememberWantedProfile(JsonObject wantedRoot)
	{
		try
		{
			String profile = getString(wantedRoot, "profile", "");
			String account = getString(wantedRoot, "account", "");

			if (profile.trim().isEmpty())
			{
				profile = account;
			}

			if (profile.trim().isEmpty())
			{
				return;
			}

			String profileKey = normalizeProfileKey(account, profile);

			JsonObject memoryRoot;

			Path memoryFile = getWantedMemoryFile();

			if (Files.exists(memoryFile))
			{
				try
				{
					memoryRoot = new JsonParser().parse(Files.readString(memoryFile)).getAsJsonObject();
				}
				catch (Exception ex)
				{
					memoryRoot = new JsonObject();
				}
			}
			else
			{
				memoryRoot = new JsonObject();
			}

			memoryRoot.addProperty("source", "Clog Hunter RuneLite Plugin");
			memoryRoot.addProperty("version", 1);
			memoryRoot.addProperty("updated", System.currentTimeMillis());

			JsonObject profiles = memoryRoot.getAsJsonObject("profiles");

			if (profiles == null)
			{
				profiles = new JsonObject();
				memoryRoot.add("profiles", profiles);
			}

			JsonObject profileData = new JsonObject();
			profileData.addProperty("profile", profile);
			profileData.addProperty("account", account);
			profileData.addProperty("updated", System.currentTimeMillis());

			if (wantedRoot.has("wanted_ids") && wantedRoot.get("wanted_ids").isJsonArray())
			{
				profileData.add("wanted_ids", wantedRoot.getAsJsonArray("wanted_ids").deepCopy());
			}
			else
			{
				profileData.add("wanted_ids", new JsonArray());
			}

			if (wantedRoot.has("items") && wantedRoot.get("items").isJsonArray())
			{
				profileData.add("items", wantedRoot.getAsJsonArray("items").deepCopy());
			}
			else
			{
				profileData.add("items", new JsonArray());
			}

			profiles.add(profileKey, profileData);

			Files.createDirectories(getSyncDir());
			Path tmpFile = memoryFile.resolveSibling(memoryFile.getFileName().toString() + ".tmp");

			Files.writeString(tmpFile, gson.toJson(memoryRoot));
			Files.move(tmpFile, memoryFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
		}
		catch (Exception ex)
		{
			log.warn("Failed remembering Clog Hunter wanted profile", ex);
		}
	}

	private JsonArray intSetToJsonArray(Set<Integer> values)
	{
		JsonArray arr = new JsonArray();
		ArrayList<Integer> sorted = new ArrayList<>(values);
		Collections.sort(sorted);

		for (Integer value : sorted)
		{
			arr.add(value);
		}

		return arr;
	}

	private String clean(String value)
	{
		if (value == null)
		{
			return "";
		}

		return Text.removeTags(value)
				.replace("<br>", " ")
				.trim();
	}

	private void rebuildRootItemSummaries(JsonObject root)
	{
		Set<Integer> knownIds = new java.util.TreeSet<>();
		Set<Integer> obtainedIds = new java.util.TreeSet<>();
		Set<Integer> missingIds = new java.util.TreeSet<>();

		JsonObject tabs = root.getAsJsonObject("tabs");

		if (tabs != null)
		{
			for (String tabKey : tabs.keySet())
			{
				JsonObject tab = tabs.getAsJsonObject(tabKey);

				if (tab == null)
				{
					continue;
				}

				for (String pageKey : tab.keySet())
				{
					JsonObject page = tab.getAsJsonObject(pageKey);

					if (page == null || !page.has("items") || !page.get("items").isJsonArray())
					{
						continue;
					}

					for (JsonElement element : page.getAsJsonArray("items"))
					{
						if (!element.isJsonObject())
						{
							continue;
						}

						JsonObject item = element.getAsJsonObject();
						int itemId = getInt(item, "id", -1);

						if (itemId <= 0)
						{
							continue;
						}

						boolean obtained = getBoolean(item, "obtained", false);

						knownIds.add(itemId);

						if (obtained)
						{
							obtainedIds.add(itemId);
						}
						else
						{
							missingIds.add(itemId);
						}
					}
				}
			}
		}

		// If the same item appears on multiple pages and is obtained anywhere,
		// do not count it as missing globally.
		missingIds.removeAll(obtainedIds);

		root.add("known_item_ids", intSetToJsonArray(knownIds));
		root.add("obtained_ids", intSetToJsonArray(obtainedIds));
		root.add("missing_ids", intSetToJsonArray(missingIds));

		root.addProperty("known_item_count", knownIds.size());
		root.addProperty("obtained_count", obtainedIds.size());
		root.addProperty("missing_count", missingIds.size());
	}

	private void writeChatWantedCompletionStatus(String unlockedItemName, String rawMessage)
	{
		String unlockedKey = normalizeItemName(unlockedItemName);
		String account = client.getLocalPlayer() == null ? "Unknown" : client.getLocalPlayer().getName();

		JsonArray events = new JsonArray();
		Set<String> eventKeys = new HashSet<>();

		// First: try to match the found item against remembered wanted profiles.
		try
		{
			Path memoryFile = getWantedMemoryFile();

			if (Files.exists(memoryFile))
			{
				JsonObject memoryRoot = new JsonParser().parse(Files.readString(memoryFile)).getAsJsonObject();
				JsonObject profiles = memoryRoot.getAsJsonObject("profiles");

				if (profiles != null)
				{
					for (String profileKey : profiles.keySet())
					{
						JsonObject profileData = profiles.getAsJsonObject(profileKey);

						String rememberedAccount = getString(profileData, "account", "");
						String rememberedProfile = getString(profileData, "profile", "");

						if (!normalizeItemName(rememberedAccount).equals(normalizeItemName(account)))
						{
							continue;
						}

						if (!profileData.has("items") || !profileData.get("items").isJsonArray())
						{
							continue;
						}

						for (JsonElement element : profileData.getAsJsonArray("items"))
						{
							if (!element.isJsonObject())
							{
								continue;
							}

							JsonObject wantedItem = element.getAsJsonObject();

							int itemId = getInt(wantedItem, "id", -1);
							String wantedName = getString(wantedItem, "name", "");

							if (itemId <= 0)
							{
								continue;
							}

							if (!normalizeItemName(wantedName).equals(unlockedKey))
							{
								continue;
							}

							String eventKey = normalizeProfileKey(account, rememberedProfile) + "::" + itemId;

							if (eventKeys.contains(eventKey))
							{
								continue;
							}

							eventKeys.add(eventKey);

							JsonObject event = buildFoundItemEvent(
									account,
									rememberedProfile,
									itemId,
									wantedName.isEmpty() ? unlockedItemName : wantedName,
									getString(wantedItem, "activity", ""),
									getString(wantedItem, "category", ""),
									"chat_message_wanted_memory",
									rawMessage
							);

							events.add(event);
						}
					}
				}
			}
		}
		catch (Exception ex)
		{
			log.warn("Failed matching collection-log chat unlock against wanted memory for {}", unlockedItemName, ex);
		}

		// If no remembered wanted profile matched, write a generic found-name event.
		// The app can resolve this by name using clog_base.json and account_name.
		if (events.size() == 0)
		{
			JsonObject genericEvent = buildFoundItemEvent(
					account,
					"",
					-1,
					unlockedItemName,
					"",
					"",
					"chat_message_found_name",
					rawMessage
			);

			events.add(genericEvent);
		}

		appendStatusEvents(events);

		String exportTime = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));

		SwingUtilities.invokeLater(() ->
		{
			statusLabel.setText("Chat Sync");
			exportTimeLabel.setText(exportTime);
		});

		client.addChatMessage(
				ChatMessageType.GAMEMESSAGE,
				"",
				"Clog Hunter recorded collection-log unlock: " + unlockedItemName,
				null
		);
	}

	private JsonObject buildFoundItemEvent(
			String account,
			String profile,
			int itemId,
			String itemName,
			String activity,
			String category,
			String reason,
			String message
	)
	{
		long now = System.currentTimeMillis();

		JsonObject event = new JsonObject();

		event.addProperty(
				"event_id",
				normalizeItemName(account)
						+ "|"
						+ normalizeItemName(profile)
						+ "|"
						+ itemId
						+ "|"
						+ normalizeItemName(itemName)
						+ "|"
						+ normalizeItemName(reason)
		);
		event.addProperty("account", account == null ? "" : account);
		event.addProperty("profile", profile == null ? "" : profile);
		event.addProperty("item_id", itemId);
		event.addProperty("item_name", itemName == null ? "" : itemName);
		event.addProperty("activity", activity == null ? "" : activity);
		event.addProperty("category", category == null ? "" : category);
		event.addProperty("reason", reason == null ? "unknown" : reason);
		event.addProperty("updated", now);

		if (message != null && !message.trim().isEmpty())
		{
			event.addProperty("message", message);
		}

		return event;
	}

	private void appendStatusEvents(JsonArray newEvents)
	{
		if (newEvents == null || newEvents.size() == 0)
		{
			return;
		}

		try
		{
			Files.createDirectories(getSyncDir());

			Path statusFile = getStatusFile();

			JsonObject statusRoot;

			if (Files.exists(statusFile))
			{
				try
				{
					statusRoot = new JsonParser().parse(Files.readString(statusFile)).getAsJsonObject();
				}
				catch (Exception ex)
				{
					statusRoot = new JsonObject();
				}
			}
			else
			{
				statusRoot = new JsonObject();
			}

			statusRoot.addProperty("source", "Clog Hunter RuneLite Plugin");
			statusRoot.addProperty("version", 2);
			statusRoot.addProperty("updated", System.currentTimeMillis());

			JsonArray events;

			if (statusRoot.has("events") && statusRoot.get("events").isJsonArray())
			{
				events = statusRoot.getAsJsonArray("events");
			}
			else
			{
				events = new JsonArray();
				statusRoot.add("events", events);
			}

			Set<String> existingEventIds = new HashSet<>();

			for (JsonElement existingElement : events)
			{
				if (!existingElement.isJsonObject())
				{
					continue;
				}

				String existingEventId = getString(existingElement.getAsJsonObject(), "event_id", "");

				if (!existingEventId.isEmpty())
				{
					existingEventIds.add(existingEventId);
				}
			}

			for (JsonElement element : newEvents)
			{
				if (!element.isJsonObject())
				{
					continue;
				}

				JsonObject event = element.getAsJsonObject();
				String eventId = getString(event, "event_id", "");

				if (!eventId.isEmpty() && existingEventIds.contains(eventId))
				{
					continue;
				}

				events.add(event);

				if (!eventId.isEmpty())
				{
					existingEventIds.add(eventId);
				}
			}

			Path tmpFile = statusFile.resolveSibling(statusFile.getFileName().toString() + ".tmp");

			Files.writeString(tmpFile, gson.toJson(statusRoot));
			Files.move(tmpFile, statusFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
		}
		catch (Exception ex)
		{
			log.warn("Failed appending Clog Hunter status events", ex);
		}
	}

	private String normalizeItemName(String value)
	{
		if (value == null)
		{
			return "";
		}

		return clean(value)
				.toLowerCase(Locale.ROOT)
				.replace('\u00A0', ' ')
				.replaceAll("\\s+", " ")
				.trim();
	}

	private String normalizeProfileKey(String account, String profile)
	{
		return normalizeItemName(account) + "::" + normalizeItemName(profile);
	}

	private void writeWantedCompletionStatus(JsonObject exportRoot)
	{
		Set<Integer> obtainedIds = getIntSet(exportRoot, "obtained_ids");
		JsonArray events = new JsonArray();
		Set<String> eventKeys = new HashSet<>();

		Path wantedFile = getWantedImportFile();

		String fallbackAccount = client.getLocalPlayer() == null
				? "Unknown"
				: client.getLocalPlayer().getName();

		try
		{
			if (!Files.exists(wantedFile))
			{
				return;
			}

			String json = Files.readString(wantedFile);
			JsonObject wantedRoot = new JsonParser().parse(json).getAsJsonObject();

			Set<Integer> wantedIds = getIntSet(wantedRoot, "wanted_ids");

			String account = getString(wantedRoot, "account", fallbackAccount);
			String profile = getString(wantedRoot, "profile", account);

			if (account.trim().isEmpty())
			{
				account = fallbackAccount;
			}

			if (profile.trim().isEmpty())
			{
				profile = account;
			}

			if (wantedRoot.has("items") && wantedRoot.get("items").isJsonArray())
			{
				for (JsonElement element : wantedRoot.getAsJsonArray("items"))
				{
					if (!element.isJsonObject())
					{
						continue;
					}

					JsonObject wantedItem = element.getAsJsonObject();

					int itemId = getInt(wantedItem, "id", -1);

					if (itemId <= 0 || !wantedIds.contains(itemId) || !obtainedIds.contains(itemId))
					{
						continue;
					}

					String eventKey = normalizeProfileKey(account, profile) + "::" + itemId + "::page_scan";

					if (eventKeys.contains(eventKey))
					{
						continue;
					}

					eventKeys.add(eventKey);

					events.add(buildFoundItemEvent(
							account,
							profile,
							itemId,
							getString(wantedItem, "name", getItemNameFromCache(itemId, "Item " + itemId)),
							getString(wantedItem, "activity", ""),
							getString(wantedItem, "category", ""),
							"page_scan_wanted",
							null
					));
				}
			}

			for (Integer itemId : wantedIds)
			{
				if (!obtainedIds.contains(itemId))
				{
					continue;
				}

				String eventKey = normalizeProfileKey(account, profile) + "::" + itemId + "::page_scan";

				if (eventKeys.contains(eventKey))
				{
					continue;
				}

				eventKeys.add(eventKey);

				events.add(buildFoundItemEvent(
						account,
						profile,
						itemId,
						getItemNameFromCache(itemId, "Item " + itemId),
						"",
						"",
						"page_scan_wanted",
						null
				));
			}
		}
		catch (Exception ex)
		{
			log.warn("Failed comparing wanted items against collection log export", ex);
			return;
		}

		appendStatusEvents(events);
	}

private String guessWidgetLabel(Widget widget)
	{
		String text = clean(widget.getText());

		if (!text.isEmpty())
		{
			return text;
		}

		String name = clean(widget.getName());

		if (!name.isEmpty())
		{
			return name;
		}

		return "";
	}

private boolean looksLikeCleanCollectionPageRow(Widget widget)
	{
		if (widget == null)
		{
			return false;
		}

		int groupId = widget.getId() >>> 16;

		if (groupId != 621 || widget.getItemId() > 0)
		{
			return false;
		}

		String label = guessWidgetLabelDeep(widget, 0);

		if (label.trim().isEmpty())
		{
			return false;
		}

		String lower = label.toLowerCase(Locale.ROOT);

		if (lower.equals("bosses") || lower.equals("raids") || lower.equals("clues") || lower.equals("minigames") || lower.equals("other"))
		{
			return false;
		}

		if (lower.startsWith("check ") || lower.contains("more options") || lower.equals("overview") || lower.equals("view log"))
		{
			return false;
		}

		boolean rowSized =
				widget.getRelativeX() == 0
						&& widget.getWidth() >= 120
						&& widget.getWidth() <= 220
						&& widget.getHeight() >= 10
						&& widget.getHeight() <= 25;

		if (!rowSized)
		{
			return false;
		}

		String[] actions = widget.getActions();

		if (actions == null || actions.length == 0)
		{
			return false;
		}

		for (String action : actions)
		{
			String cleanedAction = clean(action).toLowerCase(Locale.ROOT);

			if (!cleanedAction.isEmpty())
			{
				return true;
			}
		}

		return false;
	}

	private String guessWidgetLabelDeep(Widget widget, int depth)
	{
		if (widget == null || depth > 4)
		{
			return "";
		}

		String label = guessWidgetLabel(widget);

		if (!label.trim().isEmpty())
		{
			return label;
		}

		String childLabel = guessWidgetLabelFromChildren(widget.getStaticChildren(), depth + 1);

		if (!childLabel.trim().isEmpty())
		{
			return childLabel;
		}

		childLabel = guessWidgetLabelFromChildren(widget.getDynamicChildren(), depth + 1);

		if (!childLabel.trim().isEmpty())
		{
			return childLabel;
		}

		return guessWidgetLabelFromChildren(widget.getNestedChildren(), depth + 1);
	}

	private String guessWidgetLabelFromChildren(Widget[] children, int depth)
	{
		if (children == null)
		{
			return "";
		}

		for (Widget child : children)
		{
			String label = guessWidgetLabelDeep(child, depth);

			if (!label.trim().isEmpty())
			{
				return label;
			}
		}

		return "";
	}

private String collectionCategoryFromPageRowChildId(int childId)
	{
		if (childId == 11)
		{
			return "Bosses";
		}

		if (childId == 15)
		{
			return "Raids";
		}

		if (childId == 32)
		{
			return "Clues";
		}

		if (childId == 27)
		{
			return "Minigames";
		}

		if (childId == 34)
		{
			return "Other";
		}

		return "Unknown child " + childId;
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

		Path exportDir = getPublicExportDir();

		Files.createDirectories(exportDir);

		Path exportFile = getExportFile();

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

		Set<Integer> pageObtainedIds = new java.util.TreeSet<>();
		Set<Integer> pageMissingIds = new java.util.TreeSet<>();

		for (Widget widgetItem : itemsContainer.getDynamicChildren())
		{
			int itemId = widgetItem.getItemId();

			if (itemId <= 0)
			{
				continue;
			}

			JsonObject item = new JsonObject();

			String itemName = getItemNameFromCache(itemId, "Item " + itemId);

			int quantity = widgetItem.getItemQuantity();
			int opacity = widgetItem.getOpacity();

			// Collection log item widgets:
			// opacity 0   = obtained
			// opacity 175 = missing / greyed out
			boolean obtained = opacity == 0;

			item.addProperty("id", itemId);
			item.addProperty("name", itemName);
			item.addProperty("quantity", quantity);
			item.addProperty("opacity", opacity);
			item.addProperty("obtained", obtained);

			if (obtained)
			{
				pageObtainedIds.add(itemId);
			}
			else
			{
				pageMissingIds.add(itemId);
			}

			itemArray.add(item);
		}

		page.add("items", itemArray);
		page.add("obtained_ids", intSetToJsonArray(pageObtainedIds));
		page.add("missing_ids", intSetToJsonArray(pageMissingIds));
		page.addProperty("obtained_count", pageObtainedIds.size());
		page.addProperty("missing_count", pageMissingIds.size());

		categoryTab.add(clean(pageName), page);

		// Rebuild root-level account summaries after every page scan.
		rebuildRootItemSummaries(root);

		// Write RuneLite -> app sync file for wanted items that became obtained.
		writeWantedCompletionStatus(root);

		int capturedPages = 0;

		for (String tabKey : tabs.keySet())
		{
			JsonObject tab = tabs.getAsJsonObject(tabKey);
			capturedPages += tab.keySet().size();
		}

		String exportTime = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
		List<String> missingPages = buildMissingPagesList(root, tabs);

		final String finalPlayerName = playerName;
		final String finalCategoryName = categoryName;
		final String finalPageName = clean(pageName);
		final int finalItemCount = itemArray.size();
		final int finalCapturedPages = capturedPages;
		final String finalExportTime = exportTime;
		final List<String> finalMissingPages = missingPages;

		SwingUtilities.invokeLater(() ->
		{
			statusLabel.setText("Recording");
			accountLabel.setText(finalPlayerName);
			categoryLabel.setText(finalCategoryName);
			pageLabel.setText(finalPageName);
			countLabel.setText(String.valueOf(finalItemCount));
			pagesLabel.setText(String.valueOf(finalCapturedPages));
			exportTimeLabel.setText(finalExportTime);
		});

		updateScanState(finalMissingPages, finalCapturedPages, finalCategoryName, finalPageName);

		Files.writeString(exportFile, gson.toJson(root));

		log.debug("Updated accumulated collection log export for page: {}", pageName);
	}

}