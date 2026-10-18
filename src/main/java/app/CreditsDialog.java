package app;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Insets;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.KeyStroke;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.event.HyperlinkEvent;
import javax.swing.plaf.FontUIResource;
import javax.swing.text.BadLocationException;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.HTMLEditorKit;

import app.splash.SplashPhotos;

/** Offline notices and artwork provenance, using the same sources as the release. */
public final class CreditsDialog extends JDialog
{

	private static CreditsDialog instance;

	@FunctionalInterface
	private interface Document
	{
		String read() throws IOException;
	}

	private record Credit(String title, String author, boolean html, Document document)
	{
		private Credit(String title, boolean html, Document document)
		{
			this(title, "", html, document);
		}

		@Override
		public String toString()
		{
			return title;
		}
	}

	private record FontCredit(String family, String creator, String download, String licenseSource, String licensePath)
	{}

	private static final class ReadingPane extends JEditorPane
	{
		private final HTMLEditorKit htmlKit = new HTMLEditorKit();

		private ReadingPane()
		{
			setEditorKit(htmlKit);
			setEditable(false);
			putClientProperty(HONOR_DISPLAY_PROPERTIES, true);
			setMargin(new Insets(18, 22, 18, 22));
		}

		@Override
		public void updateUI()
		{
			super.updateUI();
			Font font = UIManager.getFont("Label.font");
			if (font != null)
				setFont(new FontUIResource(font.deriveFont(Math.max(14f, font.getSize2D()))));
			styleDocument();
		}

		private void showDocument(String html) throws IOException
		{
			HTMLDocument document = (HTMLDocument) htmlKit.createDefaultDocument();
			// Resources have already been decoded. An HTML charset directive must not
			// trigger a restart, which JEditorPane.setText handles with only an error beep.
			document.putProperty("IgnoreCharsetDirective", Boolean.TRUE);
			try {
				htmlKit.read(new StringReader(html), document, 0);
			}
			catch (BadLocationException e) {
				throw new IOException("Could not render this notice", e);
			}
			setEditorKit(htmlKit);
			setDocument(document);
			styleDocument();
			setCaretPosition(0);
		}

		private void showError(String message)
		{
			setContentType("text/plain");
			setText("Could not load this notice.\n\n" + message);
			setCaretPosition(0);
		}

		private void styleDocument()
		{
			if (!(getDocument() instanceof HTMLDocument document))
				return;
			Color link = UIManager.getColor("Component.linkColor");
			if (link == null)
				link = getForeground();
			var styles = document.getStyleSheet();
			styles.addRule("body { margin: 0; }");
			styles.addRule("h1 { font-size: 22pt; margin-top: 0; margin-bottom: 12px; }");
			styles.addRule("h2 { font-size: 17pt; margin-top: 24px; margin-bottom: 8px; }");
			styles.addRule("h3 { font-size: 15pt; margin-top: 18px; margin-bottom: 8px; }");
			styles.addRule("p { margin-top: 0; margin-bottom: 14px; }");
			styles.addRule("li { margin-bottom: 6px; }");
			styles.addRule(".notice { margin-top: 12px; margin-bottom: 18px; }");
			if (link != null)
				styles.addRule(String.format("a { color: #%02x%02x%02x; text-decoration: underline; }", link.getRed(), link.getGreen(), link.getBlue()));
		}
	}

	private static final class CreditRenderer extends JPanel implements ListCellRenderer<Credit>
	{
		private final DefaultListCellRenderer defaults = new DefaultListCellRenderer();
		private final JLabel title = new JLabel();
		private final JLabel author = new JLabel("", SwingConstants.RIGHT);

		private CreditRenderer()
		{
			super(new BorderLayout(12, 0));
			add(title, BorderLayout.CENTER);
			add(author, BorderLayout.EAST);
		}

		@Override
		public Component getListCellRendererComponent(JList<? extends Credit> list, Credit credit, int index, boolean selected, boolean focused)
		{
			Component style = defaults.getListCellRendererComponent(list, credit.title, index, selected, focused);
			setBackground(style.getBackground());
			setBorder(BorderFactory.createCompoundBorder(defaults.getBorder(), BorderFactory.createEmptyBorder(2, 6, 2, 6)));
			title.setText(credit.title);
			author.setText(credit.author);
			title.setFont(list.getFont());
			author.setFont(list.getFont());
			title.setForeground(style.getForeground());
			author.setForeground(style.getForeground());
			setToolTipText(credit.author.isEmpty() ? credit.title : credit.title + " (" + credit.author + ")");
			getAccessibleContext().setAccessibleName(getToolTipText());
			return this;
		}
	}

	public static void showDialog(Component parent)
	{
		if (instance == null || !instance.isDisplayable())
			instance = new CreditsDialog(parent);
		instance.setVisible(true);
		instance.toFront();
	}

	private CreditsDialog(Component parent)
	{
		super(parent instanceof Window ? (Window) parent : parent == null ? null : SwingUtilities.getWindowAncestor(parent),
			"Licenses & Credits", ModalityType.MODELESS);
		JTabbedPane tabs = new JTabbedPane();
		tabs.addTab("Libraries", createBrowser(libraries()));
		tabs.addTab("Splash Artwork", createBrowser(artwork()));
		tabs.addTab("Fonts", createBrowser(fonts()));
		tabs.addTab("Java Runtime", createBrowser(runtime()));

		JButton close = new JButton("Close");
		close.addActionListener(e -> dispose());
		JPanel buttons = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT));
		buttons.add(close);
		add(tabs, BorderLayout.CENTER);
		add(buttons, BorderLayout.SOUTH);
		setIconImage(Environment.getDefaultIconImage());
		setDefaultCloseOperation(DISPOSE_ON_CLOSE);
		getRootPane().setDefaultButton(close);
		getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("ESCAPE"), "close");
		getRootPane().getActionMap().put("close", new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent e)
			{
				dispose();
			}
		});
		setMinimumSize(new Dimension(640, 440));
		setSize(980, 700);
		setLocationRelativeTo(parent);
	}

	private JSplitPane createBrowser(List<Credit> credits)
	{
		DefaultListModel<Credit> model = new DefaultListModel<>();
		credits.forEach(model::addElement);
		JList<Credit> list = new JList<>(model) {
			@Override
			public boolean getScrollableTracksViewportWidth()
			{
				return true;
			}
		};
		list.setCellRenderer(new CreditRenderer());
		list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		ReadingPane content = new ReadingPane();
		content.addHyperlinkListener(e -> {
			if (e.getEventType() != HyperlinkEvent.EventType.ACTIVATED)
				return;
			String target = e.getDescription();
			if (target == null)
				return;
			openLink(target);
		});
		list.addListSelectionListener(e -> {
			Credit credit = list.getSelectedValue();
			if (e.getValueIsAdjusting() || credit == null)
				return;
			try {
				String text = credit.document.read();
				content.showDocument(credit.html ? readableHtml(text) : page(credit.title, paragraphs(text)));
			}
			catch (IOException ex) {
				content.showError(ex.getMessage());
			}
		});
		JScrollPane navigation = new JScrollPane(list);
		navigation.setMinimumSize(new Dimension(180, 100));
		JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, navigation, new JScrollPane(content));
		split.setDividerLocation(300);
		split.setResizeWeight(0.3);
		if (!model.isEmpty())
			list.setSelectedIndex(0);
		return split;
	}

	private static Credit resource(String title, String path)
	{
		return new Credit(title, path.endsWith(".html"), () -> readResource(path));
	}

	private static String readResource(String path) throws IOException
	{
		try (InputStream stream = CreditsDialog.class.getResourceAsStream(path)) {
			if (stream == null)
				throw new IOException("Missing bundled notice: " + path);
			String text = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
			return text.startsWith("\uFEFF") ? text.substring(1) : text;
		}
	}

	private static List<Credit> libraries()
	{
		List<Credit> credits = new ArrayList<>();
		try {
			for (String line : readResource("/licenses/index.tsv").lines().toList()) {
				String[] fields = line.split("\t", 2);
				if (fields.length == 2)
					credits.add(resource(libraryTitle(fields[0]), "/licenses/" + fields[1]));
			}
		}
		catch (IOException e) {
			credits.add(new Credit("License notices unavailable", false, () -> "License resources have not been generated for this development build.\n"
				+ "Run Gradle's processResources task or use a packaged release.\n\n" + e.getMessage()));
		}
		return credits;
	}

	private static List<Credit> artwork()
	{
		List<Credit> credits = new ArrayList<>();
		credits.add(new Credit("Original splash", "clover", true, () -> page("Original Star Rod splash",
			"<p>Artwork by <b>clover</b></p><p><img width='400' src='"
				+ CreditsDialog.class.getResource("/splash/classic.png") + "'></p>")));
		credits.add(new Credit("Photography license", true, () -> page("Splash photography",
			"<p>These photographs are used under the Pexels License. Select a photograph to see its creator and original source.</p>"
				+ "<p>" + link("https://www.pexels.com/legal-pages/license/", "Read the Pexels License") + "<br>"
				+ link("https://www.pexels.com/terms-of-service/", "Read the Pexels terms of service") + "</p>"
				+ paragraphs(java.util.Arrays.stream(paragraphBlocks(readResource("/splash/photos/LICENSE.txt")))
					.filter(paragraph -> paragraph.startsWith("Pexels permits")).findFirst()
					.orElseThrow(() -> new IOException("Missing photography license summary")))
				+ "<p>The photographers do not endorse Star Rod. Their photos are decorative splash backgrounds.</p>")));
		try {
			for (SplashPhotos.Photo photo : SplashPhotos.catalog()) {
				String title = photo.filename().replace(".jpg", "").replace('-', ' ');
				credits.add(new Credit(title, photo.photographer(), true, () -> "<html><h2>" + escape(title)
					+ "</h2><p>Photography by <b>" + escape(photo.photographer()) + "</b></p>"
					+ "<p><a href='" + escape(photo.source()) + "'>View original photograph on Pexels</a></p>"
					+ "<p><img width='440' src='" + CreditsDialog.class.getResource("/splash/photos/" + photo.filename()) + "'></p>"
					+ "<p>Used under the <a href='https://www.pexels.com/legal-pages/license/'>Pexels License</a>.</p></html>"));
			}
		}
		catch (IOException e) {
			credits.add(new Credit("Photography catalog unavailable", false, () -> e.getMessage()));
		}
		return credits;
	}

	private static List<Credit> fonts()
	{
		List<Credit> credits = new ArrayList<>();
		try {
			List<FontCredit> fonts = fontCatalog();
			credits.add(new Credit("Font credits", true, () -> {
				StringBuilder body = new StringBuilder(
					"<p>The splash screens use these typefaces, shared by their creators under the SIL Open Font License 1.1.</p>"
						+ "<p>Select a font to read its license and open the original download or upstream license.</p>");
				body.append("<table width='100%' cellpadding='8' cellspacing='0'>");
				for (FontCredit font : fonts)
					body.append("<tr><td width='30%' valign='top'><b>").append(escape(font.family))
						.append("</b></td><td valign='top'>").append(escape(font.creator)).append("</td></tr>");
				body.append("</table>");
				return page("Splash fonts", body.toString());
			}));
			for (FontCredit font : fonts) {
				credits.add(new Credit(font.family, true, () -> page(font.family,
					"<p>Created by <b>" + escape(font.creator) + "</b></p><p>SIL Open Font License 1.1</p>"
						+ "<h2>Sources</h2><p>" + link(font.download, "Original font download") + "<br>"
						+ link(font.licenseSource, "Upstream license and copyright notice") + "</p>"
						+ "<h2>License</h2>" + paragraphs(readResource(font.licensePath)))));
			}
		}
		catch (IOException e) {
			credits.add(new Credit("Font credits unavailable", false, () -> e.getMessage()));
		}
		return credits;
	}

	private static List<FontCredit> fontCatalog() throws IOException
	{
		Map<String, String> creators = new LinkedHashMap<>();
		for (String line : readResource("/splash/fonts/README.txt").lines().toList()) {
			int colon = line.indexOf(':');
			if (colon > 0)
				creators.put(line.substring(0, colon), line.substring(colon + 1).trim());
		}
		List<FontCredit> fonts = new ArrayList<>();
		for (String line : readResource("/splash/fonts/SOURCES.txt").lines().toList()) {
			if (line.isBlank() || line.startsWith("#"))
				continue;
			String[] fields = line.split("\\s*\\|\\s*");
			if (fields.length != 4 || !creators.containsKey(fields[0]))
				throw new IOException("Incomplete font credit: " + line);
			String slug = fields[0].toLowerCase(java.util.Locale.ROOT).replace(" ", "");
			fonts.add(new FontCredit(fields[0], creators.get(fields[0]), fields[2], fields[3], "/splash/fonts/" + slug + "-OFL.txt"));
		}
		return fonts;
	}

	private static List<Credit> runtime()
	{
		List<Credit> credits = new ArrayList<>();
		Path legal = Path.of(System.getProperty("java.home"), "legal").toAbsolutePath().normalize();
		try (var paths = Files.walk(legal)) {
			paths.filter(Files::isRegularFile).sorted().forEach(path -> credits.add(
				new Credit(runtimeTitle(legal, path), true, () -> runtimeDocument(legal, path))));
		}
		catch (IOException e) {
			credits.add(new Credit("Runtime notices", false, () -> "This Java installation does not provide readable notices in:\n" + legal));
		}
		return credits;
	}

	private static String runtimeTitle(Path legal, Path path)
	{
		return path.getFileName() + " (" + legal.relativize(path.getParent()) + ")";
	}

	private static String runtimeDocument(Path legal, Path path) throws IOException
	{
		Path root = legal.toAbsolutePath().normalize();
		Path current = path.toAbsolutePath().normalize();
		Set<Path> visited = new HashSet<>();
		Pattern referencePattern = Pattern.compile("(?is)^\\s*Please see\\s+([^\\r\\n]+)\\s*$");
		// A reference file is an alias for the other notice, not a separate page.
		while (visited.add(current)) {
			if (!current.startsWith(root))
				throw new IOException("The referenced notice is outside the runtime's legal directory.");
			String text = Files.readString(current);
			var reference = referencePattern.matcher(text);
			if (reference.matches()) {
				current = current.getParent().resolve(reference.group(1).strip().replace('\\', '/')).normalize();
				continue;
			}
			if (current.toString().endsWith(".html"))
				return text;
			return page(current.getFileName().toString(), current.toString().endsWith(".md") ? runtimeMarkdown(text) : paragraphs(text));
		}
		throw new IOException("Circular license reference: " + root.relativize(current));
	}

	private static String escape(String text)
	{
		return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("'", "&#39;").replace("\"", "&quot;");
	}

	private static String page(String title, String body)
	{
		return "<html><body><h1>" + escape(title) + "</h1>" + body + "</body></html>";
	}

	private static String link(String url, String label)
	{
		return "<a href='" + escape(url) + "'>" + escape(label) + "</a>";
	}

	private static String paragraphs(String text)
	{
		StringBuilder html = new StringBuilder();
		for (String paragraph : paragraphBlocks(text))
			html.append("<p>").append(escape(paragraph).replaceAll("\\R", "<br>")).append("</p>");
		return html.toString();
	}

	private static String[] paragraphBlocks(String text)
	{
		// Normalize first: two \\R matches can otherwise split a single CRLF pair.
		return text.replace("\r\n", "\n").replace('\r', '\n').strip().split("\n\\s*\n");
	}

	private static String readableHtml(String html)
	{
		// Use the current theme's type and colors, and let long license lines wrap.
		String styled = html.replaceAll("(?is)<\\?xml\\b.*?\\?>", "").replaceAll("(?is)<style\\b[^>]*>.*?</style>", "");
		return Pattern.compile("(?is)<pre\\b[^>]*>(.*?)</pre>").matcher(styled).replaceAll(
			match -> java.util.regex.Matcher.quoteReplacement("<div class='notice'>" + match.group(1).replaceAll("\\R", "<br>") + "</div>"));
	}

	private static String libraryTitle(String title)
	{
		return title.replace(" / META-INF/", ": ").replace(" / ", ": ")
			.replaceAll("(?i): license(?:\\.txt)?$", ": License").replaceAll("(?i): notice(?:\\.txt)?$", ": Notice")
			.replace(": about.html", ": About");
	}

	private void openLink(String url)
	{
		try {
			URI uri = URI.create(url);
			if (!"https".equalsIgnoreCase(uri.getScheme()) && !"http".equalsIgnoreCase(uri.getScheme()))
				return;
			Desktop.getDesktop().browse(uri);
		}
		catch (Exception e) {
			JOptionPane.showMessageDialog(this, "Could not open this link:\n" + url, "Open Link", JOptionPane.ERROR_MESSAGE);
		}
	}

	private static String runtimeMarkdown(String markdown)
	{
		// Runtime notices use headings and fenced license blocks. Keep their text,
		// but display the license as wrapping prose instead of a wide code block.
		StringBuilder html = new StringBuilder();
		StringBuilder paragraph = new StringBuilder();
		boolean code = false;
		for (String line : markdown.lines().toList()) {
			String trimmed = line.trim();
			boolean fence = trimmed.startsWith("```") || trimmed.equals("<pre>") || trimmed.equals("</pre>");
			boolean heading = !code && trimmed.matches("#{1,6} .*");
			if (trimmed.isEmpty() || fence || heading) {
				if (!paragraph.isEmpty()) {
					html.append(paragraphs(paragraph.toString()));
					paragraph.setLength(0);
				}
				if (fence)
					code = !code;
				else if (heading)
					html.append("<h2>").append(escape(trimmed.replaceFirst("^#{1,6} ", ""))).append("</h2>");
			}
			else {
				paragraph.append(line).append('\n');
			}
		}
		return html.append(paragraphs(paragraph.toString())).toString();
	}
}
