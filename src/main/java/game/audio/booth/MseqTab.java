package game.audio.booth;

import java.awt.Component;
import java.util.HashMap;
import java.util.Map;

import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JList;
import javax.swing.JPanel;

import app.SwingUtils;
import game.audio.Mseq;
import game.audio.engine.AudioEngine;
import game.audio.engine.MseqPlayer;
import game.audio.engine.PlaybackSession;
import game.audio.engine.SoundLibrary;
import net.miginfocom.swing.MigLayout;
import util.Logger;

/** Lists the ambient sequences (MSEQ) of the project. */
final class MseqTab extends AudioBoothTab
{
	private final MseqPlayer player;
	private final JList<String> nameList;
	private final JButton rampButton;
	private final Map<String, Mseq> mseqs = new HashMap<>();
	private final Map<String, String> failures = new HashMap<>();

	private String selectedName;
	private boolean suppressEvents;

	MseqTab(AudioBooth booth, AudioEngine engine, SoundLibrary library)
	{
		super(booth, "MSEQ", new MseqPlayer(engine, library));
		player = (MseqPlayer) getSession();

		Map<String, String> uses = library.listMseqs();
		DefaultListModel<String> model = new DefaultListModel<>();
		for (String name : uses.keySet()) {
			try {
				mseqs.put(name, library.readMseq(name));
			}
			catch (RuntimeException e) {
				failures.put(name, e.getMessage());
				Logger.logfError("Could not load the ambient sequence %s for the Audio Booth", name);
				Logger.printStackTrace(e);
			}
			model.addElement(name);
		}

		nameList = new JList<>(model);
		configureList(nameList);
		nameList.setCellRenderer(new DefaultListCellRenderer() {
			@Override
			public Component getListCellRendererComponent(JList<?> list, Object value, int index,
				boolean isSelected, boolean cellHasFocus)
			{
				Component component = super.getListCellRendererComponent(
					list, value, index, isSelected, cellHasFocus);
				if (value instanceof String name) {
					setText(formatName(name, uses.get(name)));
					setToolTipText(failures.get(name));
					if (!isSelected && failures.containsKey(name))
						setForeground(SwingUtils.getRedTextColor());
				}
				return component;
			}
		});
		nameList.addListSelectionListener((e) -> {
			if (suppressEvents || e.getValueIsAdjusting())
				return;
			String selected = nameList.getSelectedValue();
			if (selected != null)
				selectMseq(selected);
		});

		JPanel listPanel = createListPanel(nameList, model, (name) -> formatName(name, uses.get(name)));
		rampButton = new JButton("Trigger Ramps");
		SwingUtils.addBorderPadding(rampButton);
		rampButton.setToolTipText("Start this MSEQ's track tune and volume ramps.");
		rampButton.setEnabled(false);
		rampButton.addActionListener((e) -> triggerRamps());
		JPanel rampPanel = new JPanel(new MigLayout("ins 0 8 8 8", "[]", "[]"));
		rampPanel.add(rampButton);

		setLayout(new MigLayout("fill, ins 0", "[grow,fill]", "[grow][]"));
		add(listPanel, "grow, push, wrap");
		add(rampPanel, "growx");
	}

	private void selectMseq(String name)
	{
		selectedName = name;
		if (mseqs.containsKey(name)) {
			playMseq();
		}
		else {
			booth.selectWithoutPlayback(this);
			booth.setStatus("Could not load " + name + ": " + failures.get(name));
		}
	}

	private void playMseq()
	{
		Mseq mseq = mseqs.get(selectedName);
		if (mseq == null)
			return;
		booth.startPlayback(this, player, () -> player.setMseq(mseq, selectedName));
		booth.setStatus("Playing ambience " + selectedName);
	}

	private void triggerRamps()
	{
		if (!booth.isCurrentSession(player) || selectedName == null)
			return;
		booth.runAudioAction(() -> player.triggerTrackRamps());
		booth.setStatus("Triggered track ramps for " + selectedName);
	}

	@Override
	public boolean hasSelection()
	{
		return selectedName != null;
	}

	@Override
	public PreparedSelection prepareReload(AudioBoothTab replacement)
	{
		if (!(replacement instanceof MseqTab mseqTab) || selectedName == null || !mseqTab.mseqs.containsKey(selectedName))
			return null;
		String restoredName = selectedName;
		return () -> mseqTab.restoreSelection(restoredName);
	}

	private void restoreSelection(String name)
	{
		suppressEvents = true;
		nameList.setSelectedValue(name, true);
		suppressEvents = false;
		selectedName = name;
		playMseq();
	}

	@Override
	public void updatePlaybackState(PlaybackSession currentSession)
	{
		Mseq mseq = mseqs.get(selectedName);
		rampButton.setEnabled(currentSession == player && mseq != null && !mseq.ramps().isEmpty() && player.isPlaying());
	}

	private String formatName(String name, String use)
	{
		String text = name;
		if (use != null)
			text += "  " + use;
		Mseq mseq = mseqs.get(name);
		if (mseq != null && !mseq.ramps().isEmpty())
			text += " [R]";
		return text;
	}
}
