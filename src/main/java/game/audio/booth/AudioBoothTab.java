package game.audio.booth;

import java.awt.Dimension;
import java.util.function.Function;

import javax.swing.DefaultListModel;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import app.SwingUtils;
import game.audio.engine.PlaybackSession;
import net.miginfocom.swing.MigLayout;
import util.ui.FilteredListModel;

abstract class AudioBoothTab extends JPanel
{
	@FunctionalInterface
	interface PreparedSelection
	{
		void restore();
	}

	protected final AudioBooth booth;
	private final String title;
	private final PlaybackSession session;

	protected AudioBoothTab(AudioBooth booth, String title, PlaybackSession session)
	{
		this.booth = booth;
		this.title = title;
		this.session = session;
	}

	public final String getTitle()
	{
		return title;
	}

	public final PlaybackSession getSession()
	{
		return session;
	}

	public final void attach()
	{
		session.attach();
	}

	public final void stop()
	{
		session.stop();
	}

	public final void close()
	{
		session.close();
	}

	public abstract boolean hasSelection();

	public abstract PreparedSelection prepareReload(AudioBoothTab replacement);

	public void updatePlaybackState(PlaybackSession currentSession)
	{}

	protected static void configureList(JList<?> list)
	{
		list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		list.setVisibleRowCount(16);
	}

	protected static JPanel createUnfilteredListPanel(JList<?> list, String title, JLabel countLabel)
	{
		JScrollPane scrollPane = new JScrollPane(list);
		scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		scrollPane.setWheelScrollingEnabled(true);
		scrollPane.setPreferredSize(new Dimension(250, 300));

		JLabel header = SwingUtils.getLabel(title, 12);
		countLabel.setHorizontalAlignment(SwingConstants.RIGHT);

		JPanel panel = new JPanel(new MigLayout("fill, ins 8", "[grow,fill][]", "[][grow]"));
		panel.add(header);
		panel.add(countLabel, "wrap");
		panel.add(scrollPane, "span, grow, push");
		return panel;
	}

	protected static <T> JPanel createListPanel(JList<T> list, DefaultListModel<T> sourceModel,
		Function<T, String> filterText)
	{
		FilteredListModel<T> filteredModel = new FilteredListModel<>(sourceModel);
		list.setModel(filteredModel);

		JTextField filterField = new JTextField();
		filterField.setMargin(SwingUtils.TEXTBOX_INSETS);
		SwingUtils.addBorderPadding(filterField);

		JLabel countLabel = new JLabel();
		countLabel.setHorizontalAlignment(SwingConstants.RIGHT);
		setAssetCount(countLabel, sourceModel.size(), sourceModel.size());

		Runnable updateFilter = () -> updateListFilter(
			filteredModel, filterField, countLabel, filterText);
		filterField.getDocument().addDocumentListener(new DocumentListener() {
			@Override
			public void changedUpdate(DocumentEvent e)
			{
				updateFilter.run();
			}

			@Override
			public void insertUpdate(DocumentEvent e)
			{
				updateFilter.run();
			}

			@Override
			public void removeUpdate(DocumentEvent e)
			{
				updateFilter.run();
			}
		});

		JScrollPane scrollPane = new JScrollPane(list);
		scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		scrollPane.setWheelScrollingEnabled(true);
		scrollPane.setPreferredSize(new Dimension(520, 300));

		JPanel filterControls = new JPanel(new MigLayout("ins 0, fillx", "[][grow,fill]", "[]"));
		filterControls.add(SwingUtils.getLabel("Filter:", 12));
		filterControls.add(filterField, "growx");

		JPanel filterPanel = new JPanel(new MigLayout("ins 0, fillx", "[66.666%,fill][grow,fill]", "[]"));
		filterPanel.add(filterControls, "growx");
		filterPanel.add(countLabel, "growx");

		JPanel panel = new JPanel(new MigLayout("fill, ins 8", "[grow,fill]", "[][grow]"));
		panel.add(filterPanel, "growx, wrap");
		panel.add(scrollPane, "grow, push");
		return panel;
	}

	@SuppressWarnings("unchecked")
	private static <T> void updateListFilter(FilteredListModel<T> model, JTextField filterField,
		JLabel countLabel, Function<T, String> filterText)
	{
		String filter = filterField.getText().toUpperCase();
		model.setFilter((element) -> filterText.apply((T) element)
			.toUpperCase().contains(filter));
		setAssetCount(countLabel, model.getSize(), model.getSource().getSize());
	}

	private static void setAssetCount(JLabel label, int visible, int total)
	{
		if (total == 0)
			label.setText("No assets found");
		else if (visible == total)
			label.setText(String.format("%d assets", total));
		else
			label.setText(String.format("%d of %d assets", visible, total));
	}
}
