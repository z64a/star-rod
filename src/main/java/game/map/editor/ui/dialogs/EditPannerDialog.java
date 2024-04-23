package game.map.editor.ui.dialogs;

import static game.map.shape.TexturePanner.*;

import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.WindowEvent;
import java.util.function.Consumer;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.WindowConstants;

import app.Environment;
import app.SwingUtils;
import game.map.editor.MapEditor;
import game.map.editor.ui.ScriptManager;
import game.map.shape.TexturePanner;
import game.map.shape.TexturePanner.PannerParams;
import game.map.shape.TexturePanner.SetTexPannerParams;
import net.miginfocom.swing.MigLayout;
import util.ui.LimitedLengthDocument;

public class EditPannerDialog extends JDialog
{
	public static final String FRAME_TITLE = "Texture Panner Properties";

	private TexturePanner selectedPanner;
	private PannerParams originalParams;

	private MaxValueField maxField;

	private JLabel pannerNameLabel;
	private JCheckBox cbPannerGenerate;
	private JCheckBox cbUseTexels;

	private static final String[] SLIDER_LABELS = {
			"Main U", "Main V", "Aux U", "Aux V",
			"Main S", "Main T", "Aux S", "Aux T",
	};

	private PannerSlider[] sliderRate = new PannerSlider[NUM_COORDS];
	private PannerSlider[] sliderInit = new PannerSlider[NUM_COORDS];

	private JSpinner spinnerTick[] = new JSpinner[4];

	private boolean ignoreChanges = false;

	public EditPannerDialog(JFrame parent)
	{
		super(parent);

		JButton selectButton = new JButton("OK");
		SwingUtils.addBorderPadding(selectButton);
		selectButton.addActionListener((e) -> {
			MapEditor.execute(new SetTexPannerParams(selectedPanner, originalParams));
			selectedPanner = null;
			setVisible(false);
		});

		JButton cancelButton = new JButton("Cancel");
		SwingUtils.addBorderPadding(cancelButton);
		cancelButton.addActionListener((e) -> {
			selectedPanner.params.set(originalParams);
			selectedPanner = null;
			ScriptManager.instance().updatePannersTab();
			setVisible(false);
		});

		setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
		addWindowListener(new java.awt.event.WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e)
			{
				// NOTE: if a text field still has focus when this window is closed,
				// this event will be processed *BEFORE* the focusLost event from the TextField.
				// This is why a null check is needed in focusLost.
				selectedPanner.params.set(originalParams);
				selectedPanner = null;
				ScriptManager.instance().updatePannersTab();
				setVisible(false);
			}
		});

		maxField = new MaxValueField();

		for (int i = 0; i < NUM_TRACKS; i++) {
			final int idx = i;

			spinnerTick[i] = new JSpinner(new SpinnerNumberModel(0, 0, 9999, 1));

			spinnerTick[i].addChangeListener((e) -> {
				selectedPanner.params.freq[idx] = (Integer) spinnerTick[idx].getValue();
			});

			SwingUtils.centerSpinnerText(spinnerTick[i]);
		}

		for (int i = 0; i < NUM_COORDS; i++) {
			final int idx = i;
			int rateMax = (i >= MAIN_S) ? 128 : 2000;
			int initMax = (i >= MAIN_S) ? 128 : 2000;

			sliderRate[i] = new PannerSlider(SLIDER_LABELS[i], rateMax, (value) -> {
				setRate(idx, value, spinnerTick[idx % NUM_TRACKS]);
			});

			sliderInit[i] = new PannerSlider(SLIDER_LABELS[i], initMax, (value) -> {
				setInit(idx, value);
			});
		}

		setupTexelMode(false);

		pannerNameLabel = SwingUtils.getLabel("", 14);

		cbPannerGenerate = new JCheckBox(" Always generate");
		cbPannerGenerate.addActionListener((e) -> {
			selectedPanner.params.generate = cbPannerGenerate.isSelected();
			ScriptManager.instance().updatePannersTab();
		});

		cbUseTexels = new JCheckBox(" Use texel units");
		cbUseTexels.addActionListener((e) -> {
			selectedPanner.params.useTexels = cbUseTexels.isSelected();
			setupTexelMode(selectedPanner.params.useTexels);

			ignoreChanges = true;
			PannerParams params = selectedPanner.params;
			maxField.setValue(params.useTexels ? params.maxST : params.maxUV);
			ignoreChanges = false;

			ScriptManager.instance().updatePannersTab();
		});

		setLayout(new MigLayout("ins 16, fill, hidemode 3, wrap"));

		add(pannerNameLabel);
		add(cbUseTexels, "split 2, growx, sg cb");
		add(cbPannerGenerate, "growx, sg cb");

		add(SwingUtils.getLabel("Delay Between Updates", 14), "gaptop 16");
		add(spinnerTick[MAIN_U], "growx, split 4");
		add(spinnerTick[MAIN_V], "growx");
		add(spinnerTick[AUX_U], "growx");
		add(spinnerTick[AUX_V], "growx");

		add(SwingUtils.getLabel("Texture Pan Rate", 14), "gaptop 16");
		add(sliderRate[MAIN_U], "growx");
		add(sliderRate[MAIN_V], "growx");
		add(sliderRate[AUX_U], "growx");
		add(sliderRate[AUX_V], "growx");
		add(sliderRate[MAIN_S], "growx");
		add(sliderRate[MAIN_T], "growx");
		add(sliderRate[AUX_S], "growx");
		add(sliderRate[AUX_T], "growx");

		add(SwingUtils.getLabel("Initial Offset", 14), "gaptop 16");
		add(sliderInit[MAIN_U], "growx");
		add(sliderInit[MAIN_V], "growx");
		add(sliderInit[AUX_U], "growx");
		add(sliderInit[AUX_V], "growx");
		add(sliderInit[MAIN_S], "growx");
		add(sliderInit[MAIN_T], "growx");
		add(sliderInit[AUX_S], "growx");
		add(sliderInit[AUX_T], "growx");

		add(SwingUtils.getLabel("Max Value", 14), "split 2, gaptop 16, w 18%");
		add(maxField, "w 24%, gapbottom 16");

		add(new JPanel(), "split 3, growx, sg but");
		add(cancelButton, "growx, sg but");
		add(selectButton, "growx, sg but");

		pack();
		setResizable(false);

		setTitle(FRAME_TITLE);
		setIconImage(Environment.getDefaultIconImage());
		setLocationRelativeTo(parent);
		setModal(false);
	}

	private void setInit(int channel, int value)
	{
		PannerParams params = selectedPanner.params;

		if (ignoreChanges)
			return;

		params.init[channel] = value;
	}

	private void setRate(int channel, int value, JSpinner spinner)
	{
		PannerParams params = selectedPanner.params;

		if (ignoreChanges)
			return;

		params.rate[channel] = value;

		if (value != 0 && params.freq[channel % NUM_TRACKS] == 0) {
			params.freq[channel % NUM_TRACKS] = 1;
			spinner.setValue(1);
		}
		if (value == 0 && params.freq[channel % NUM_TRACKS] == 1) {
			params.freq[channel % NUM_TRACKS] = 0;
			spinner.setValue(0);
		}
	}

	public void setPanner(TexturePanner t)
	{
		// flush old params if any are saved
		// this can occur if the "Edit" button is clicked while the dialog is already open
		if (selectedPanner != null)
			selectedPanner.params.set(originalParams);

		selectedPanner = t;
		originalParams = t.params.get();

		pannerNameLabel.setText("Texture Panner " + t.panID);
		cbPannerGenerate.setSelected(t.params.generate);
		cbUseTexels.setSelected(t.params.useTexels);

		ignoreChanges = true;

		maxField.setValue(t.params.useTexels ? t.params.maxST : t.params.maxUV);

		for (int i = 0; i < NUM_COORDS; i++) {
			sliderRate[i].setValue(t.params.rate[i]);
			sliderInit[i].setValue(t.params.init[i]);
		}

		for (int i = 0; i < NUM_TRACKS; i++) {
			spinnerTick[i].setValue(t.params.freq[i]);
		}

		setupTexelMode(t.params.useTexels);

		ignoreChanges = false;
	}

	private void setupTexelMode(boolean useTexels)
	{
		for (int i = 0; i < 4; i++) {
			sliderRate[i].setVisible(!useTexels);
			sliderInit[i].setVisible(!useTexels);
		}

		for (int i = 4; i < 8; i++) {
			sliderRate[i].setVisible(useTexels);
			sliderInit[i].setVisible(useTexels);
		}
	}

	private class PannerSlider extends JComponent
	{
		private static enum UpdateMode
		{
			NONE, FROM_SLIDER, FROM_TEXTFIELD, FROM_OUTSIDE
		}

		private UpdateMode update = UpdateMode.NONE;
		private final Consumer<Integer> callback;

		private JTextField textField;
		private JSlider slider;

		private PannerSlider(String lblText, int max, Consumer<Integer> callback)
		{
			this.callback = callback;
			slider = new JSlider(-max, max, 0);
			slider.setMajorTickSpacing(max / 2);
			slider.setMinorTickSpacing(max / 4);
			slider.setPaintTicks(true);

			slider.addChangeListener((e) -> {
				if (update != UpdateMode.NONE)
					return;

				updateValue(UpdateMode.FROM_SLIDER, slider.getValue());
			});

			textField = new JTextField(Integer.toString(0), 5);
			textField.setFont(textField.getFont().deriveFont(12f));
			textField.setHorizontalAlignment(SwingConstants.CENTER);
			SwingUtils.addBorderPadding(textField);

			textField.setDocument(new LimitedLengthDocument(7));

			// document filter might be nicer, but this works
			textField.addKeyListener(new KeyAdapter() {
				@Override
				public void keyReleased(KeyEvent ke)
				{
					if (update != UpdateMode.NONE)
						return;

					String text = textField.getText();
					if (text.isEmpty() || text.equals("-") || text.equals("-0x") || text.equals("0x"))
						return;

					try {
						int value = Integer.decode(text);
						updateValue(UpdateMode.FROM_TEXTFIELD, value);
					}
					catch (NumberFormatException e) {
						textField.setText(Integer.toString(slider.getValue()));
					}
				}
			});

			// things that commit changes from text field
			textField.addFocusListener(new FocusListener() {
				@Override
				public void focusGained(FocusEvent e)
				{}

				@Override
				public void focusLost(FocusEvent e)
				{
					if (selectedPanner != null)
						commitTextField();
				}
			});
			textField.addActionListener((e) -> {
				commitTextField();
			});

			setLayout(new MigLayout("fillx, ins 0"));
			add(SwingUtils.getLabel(lblText, SwingConstants.CENTER, 12), "w 60!");
			add(slider, "w 60%, growy");
			add(textField, "w 80!");
		}

		private void commitTextField()
		{
			String text = textField.getText();
			if (text.isEmpty()) {
				updateValue(UpdateMode.FROM_TEXTFIELD, 0);
				textField.setText("0");
				return;
			}

			try {
				int value = Integer.decode(text);
				updateValue(UpdateMode.FROM_TEXTFIELD, value);
			}
			catch (NumberFormatException n) {
				textField.setText(Integer.toString(slider.getValue()));
			}
		}

		private void setValue(int value)
		{
			update = UpdateMode.FROM_OUTSIDE;
			textField.setText(Integer.toString(value));
			slider.setValue(value);
			update = UpdateMode.NONE;
		}

		private void updateValue(UpdateMode mode, int value)
		{
			update = mode;
			if (mode == UpdateMode.FROM_SLIDER)
				textField.setText(Integer.toString(value));
			else if (mode == UpdateMode.FROM_TEXTFIELD)
				slider.setValue(value);

			callback.accept(value);
			ScriptManager.instance().updatePannersTab();

			update = UpdateMode.NONE;
		}
	}

	private class MaxValueField extends JTextField
	{
		public MaxValueField()
		{
			super(Integer.toString(0), 5);

			setFont(getFont().deriveFont(12f));
			setHorizontalAlignment(SwingConstants.CENTER);
			SwingUtils.addBorderPadding(this);

			setDocument(new LimitedLengthDocument(7));

			// document filter might be nicer, but this works
			addKeyListener(new KeyAdapter() {
				@Override
				public void keyReleased(KeyEvent ke)
				{
					String text = getText();
					if (text.isEmpty() || text.equals("-") || text.equals("-0x") || text.equals("0x"))
						return;

					try {
						int value = Integer.decode(text);
						selectedPanner.params.setMax(value);
					}
					catch (NumberFormatException e) {
						setValue(selectedPanner.params.getMax());
					}
				}
			});

			addFocusListener(new FocusListener() {
				@Override
				public void focusGained(FocusEvent e)
				{}

				@Override
				public void focusLost(FocusEvent e)
				{
					if (selectedPanner != null)
						commitTextField();
				}
			});
			addActionListener((e) -> {
				commitTextField();
			});

		}

		private void commitTextField()
		{
			String text = getText();
			if (text.isEmpty()) {
				setText("0");
				return;
			}

			try {
				int value = Integer.decode(text);
				selectedPanner.params.setMax(value);
			}
			catch (NumberFormatException n) {
				setValue(selectedPanner.params.getMax());
			}
		}

		public void setValue(int value)
		{
			boolean forceHex = (selectedPanner.params == null) || !selectedPanner.params.useTexels;

			if (forceHex)
				setText("0x" + Integer.toString(value, 16));
			else
				setText(Integer.toString(value));
		}
	}
}
