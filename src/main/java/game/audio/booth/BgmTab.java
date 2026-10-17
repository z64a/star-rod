package game.audio.booth;

import java.awt.Component;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;

import game.audio.AudioTables;
import game.audio.bgm.BgmPlayer;
import game.audio.bgm.Song;
import game.audio.engine.AudioEngine;
import game.audio.engine.SoundLibrary;
import net.miginfocom.swing.MigLayout;
import util.Logger;

/** Songs, their compositions, and the proximity mixes used by the game. */
final class BgmTab extends AudioBoothTab
{
	private final SoundLibrary library;
	private final BgmPlayer player;
	private final Map<String, AudioTables.Song> entries = new LinkedHashMap<>();
	private final JList<String> songList;
	private final JComboBox<Integer> compositionBox = new JComboBox<>();
	private final JComboBox<Integer> mixBox = new JComboBox<>();
	private final JComboBox<String> proximityBox = new JComboBox<>(new String[] { "None", "Partial", "Full" });
	private final JCheckBox instantBox = new JCheckBox("Instant");
	private static final int[] PROXIMITY_VOLUMES = { 0, 87, 127 };
	private String selectedName;
	private Song selectedSong;
	private boolean suppressEvents;

	BgmTab(AudioBooth booth, AudioEngine engine, SoundLibrary library)
	{
		super(booth, "BGM", new BgmPlayer(engine, library));
		this.library = library;
		player = (BgmPlayer) getSession();
		DefaultListModel<String> model = new DefaultListModel<>();
		for (AudioTables.Song entry : library.listSongs()) {
			entries.put(entry.name(), entry);
			model.addElement(entry.name());
		}
		songList = new JList<>(model);
		configureList(songList);
		songList.setCellRenderer(new DefaultListCellRenderer() {
			@Override
			public Component getListCellRendererComponent(JList<?> list, Object value, int index,
				boolean selected, boolean focused)
			{
				Component component = super.getListCellRendererComponent(list, value, index, selected, focused);
				if (value instanceof String name)
					setText(formatName(name));
				return component;
			}
		});
		songList.addListSelectionListener(e -> {
			if (!suppressEvents && !e.getValueIsAdjusting() && songList.getSelectedValue() != null)
				selectSong(songList.getSelectedValue());
		});
		compositionBox.addActionListener(e -> {
			if (!suppressEvents)
				playSong();
		});
		mixBox.addActionListener(e -> updateProximity());
		proximityBox.addActionListener(e -> updateProximity());
		instantBox.addActionListener(e -> updateProximity());
		compositionBox.setToolTipText("Choose a composition from this song.");
		mixBox.setToolTipText("Choose a proximity mix; mix 0 is the normal song.");
		proximityBox.setToolTipText("Simulate distance from the source of the proximity music.");
		instantBox.setToolTipText("Apply proximity volume immediately instead of fading.");

		JPanel controls = new JPanel(new MigLayout("ins 0 8 8 8", "[][grow,fill][][grow,fill]", "[][]"));
		controls.add(new JLabel("Composition"));
		controls.add(compositionBox);
		controls.add(new JLabel("Mix"));
		controls.add(mixBox, "wrap");
		controls.add(new JLabel("Proximity"));
		controls.add(proximityBox);
		controls.add(instantBox, "span 2");
		setLayout(new MigLayout("fill, ins 0", "[grow,fill]", "[grow][]"));
		add(createListPanel(songList, model, this::formatName), "grow, push, wrap");
		add(controls, "growx");
		updateChoices();
	}

	private String formatName(String name)
	{
		AudioTables.Song entry = entries.get(name);
		return String.format("%02X  %s  (%s)", entry.id(), name, entry.bgm());
	}

	private void selectSong(String name)
	{
		selectedName = name;
		selectedSong = null;
		try {
			selectedSong = library.readSong(entries.get(name));
			updateChoices();
			playSong();
		}
		catch (RuntimeException e) {
			selectedSong = null;
			updateChoices();
			booth.selectWithoutPlayback(this);
			booth.setStatus("Could not load " + name + ": " + e.getMessage());
			Logger.logfError("Could not load the song %s for the Audio Booth", name);
			Logger.printStackTrace(e);
		}
	}

	private void updateChoices()
	{
		suppressEvents = true;
		compositionBox.removeAllItems();
		mixBox.removeAllItems();
		if (selectedSong != null) {
			for (int i = 0; i < Song.NUM_COMPOSITIONS; i++) {
				if (selectedSong.getComposition(i) != null)
					compositionBox.addItem(i);
			}
			for (int i = 0; i < selectedSong.branchOptions; i++)
				mixBox.addItem(i);
		}
		compositionBox.setEnabled(compositionBox.getItemCount() > 1);
		mixBox.setEnabled(mixBox.getItemCount() > 1);
		proximityBox.setSelectedIndex(0);
		instantBox.setSelected(false);
		suppressEvents = false;
		updateProximity();
	}

	private void playSong()
	{
		Integer composition = (Integer) compositionBox.getSelectedItem();
		if (selectedSong == null || composition == null) {
			booth.selectWithoutPlayback(this);
			booth.setStatus("No playable composition in " + selectedName);
			return;
		}
		if (booth.startPlayback(this, player, () -> {
			player.play(selectedSong, composition);
			applyProximity();
		}))
			booth.setStatus("Playing music " + selectedName + " (composition " + composition + ")");
		else
			booth.setStatus("No playable music in " + selectedName + " (composition " + composition + ")");
	}

	private void updateProximity()
	{
		if (suppressEvents)
			return;
		Integer mix = (Integer) mixBox.getSelectedItem();
		boolean enabled = mix != null && mix != 0;
		proximityBox.setEnabled(enabled);
		instantBox.setEnabled(enabled);
		if (booth.isCurrentSession(player))
			booth.runAudioAction(this::applyProximity);
	}

	private void applyProximity()
	{
		Integer mix = (Integer) mixBox.getSelectedItem();
		if (mix == null || mix == 0)
			player.resetProximityMix();
		else
			player.setProximityMix(mix, PROXIMITY_VOLUMES[proximityBox.getSelectedIndex()], instantBox.isSelected());
	}

	@Override
	public boolean hasSelection()
	{
		return selectedName != null;
	}

	@Override
	public PreparedSelection prepareReload(AudioBoothTab replacement)
	{
		if (!(replacement instanceof BgmTab bgm) || selectedName == null || !bgm.entries.containsKey(selectedName))
			return null;
		String name = selectedName;
		// Read before the old tabs are closed so a failed reload leaves the current song playing.
		Song song = bgm.library.readSong(bgm.entries.get(name));
		Integer composition = (Integer) compositionBox.getSelectedItem();
		Integer mix = (Integer) mixBox.getSelectedItem();
		int proximity = proximityBox.getSelectedIndex();
		boolean instant = instantBox.isSelected();
		return () -> {
			bgm.selectedName = name;
			bgm.selectedSong = song;
			bgm.updateChoices();
			bgm.suppressEvents = true;
			bgm.songList.setSelectedValue(name, true);
			if (composition != null && song.getComposition(composition) != null)
				bgm.compositionBox.setSelectedItem(composition);
			if (mix != null && mix < song.branchOptions)
				bgm.mixBox.setSelectedItem(mix);
			bgm.proximityBox.setSelectedIndex(proximity);
			bgm.instantBox.setSelected(instant);
			bgm.suppressEvents = false;
			bgm.playSong();
			bgm.updateProximity();
		};
	}
}
