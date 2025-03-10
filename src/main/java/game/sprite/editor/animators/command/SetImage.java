package game.sprite.editor.animators.command;

import static game.sprite.SpriteKey.*;

import java.awt.Component;
import java.awt.Dimension;
import java.util.List;

import javax.swing.ComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JPanel;

import org.w3c.dom.Element;

import app.SwingUtils;
import common.commands.AbstractCommand;
import game.sprite.Sprite;
import game.sprite.SpriteRaster;
import game.sprite.editor.Editable;
import game.sprite.editor.SpriteEditor;
import game.sprite.editor.animators.BlankArrowUI;
import game.sprite.editor.animators.SpriteRasterRenderer;
import net.miginfocom.swing.MigLayout;
import util.xml.XmlWrapper.XmlReader;
import util.xml.XmlWrapper.XmlTag;
import util.xml.XmlWrapper.XmlWriter;

//1VVV
// set image -- FFF is valid value for "no image" (may actually need to be < 100`)
public class SetImage extends AnimCommand
{
	public SpriteRaster img;

	// used during deserialization
	public transient String imgName = null;
	public transient int imgIndex = -1;

	public SetImage(CommandAnimator animator)
	{
		this(animator, (short) 0);
	}

	public SetImage(CommandAnimator animator, short s0)
	{
		super(animator);

		Sprite sprite = animator.comp.parentAnimation.parentSprite;

		// FFF is valid, so sign extend (implicit cast to int)
		int id = (s0 << 20) >> 20;
		if (id < 0 || id >= sprite.rasters.size())
			img = null;
		else
			img = sprite.rasters.get(id);
	}

	public SetImage(CommandAnimator animator, SpriteRaster img)
	{
		super(animator);

		this.img = img;
	}

	@Override
	public void toXML(XmlWriter xmw)
	{
		XmlTag tag = xmw.createTag(TAG_CMD_SET_IMG, true);

		if (SpriteEditor.instance().optOutputNames) {
			if (img == null)
				xmw.addAttribute(tag, ATTR_NAME, "");
			else
				xmw.addAttribute(tag, ATTR_NAME, img.name);
		}
		else {
			if (img == null)
				xmw.addHex(tag, ATTR_INDEX, -1);
			else
				xmw.addHex(tag, ATTR_INDEX, img.getIndex());
		}

		xmw.printTag(tag);
	}

	@Override
	public void fromXML(XmlReader xmr, Element elem)
	{
		if (xmr.hasAttribute(elem, ATTR_NAME))
			imgName = xmr.getAttribute(elem, ATTR_NAME);
		else if (xmr.hasAttribute(elem, ATTR_INDEX))
			imgIndex = xmr.readHex(elem, ATTR_INDEX);
	}

	@Override
	public SetImage copy()
	{
		SetImage clone = new SetImage(animator);
		clone.img = img;
		return clone;
	}

	@Override
	public AdvanceResult apply()
	{
		owner.sr = img;
		owner.sp = null;
		return AdvanceResult.NEXT;
	}

	@Override
	public String getName()
	{
		return "Set Raster";
	}

	@Override
	public String toString()
	{
		if (img == null)
			return "Clear Raster";
		else if (img.deleted)
			return "Raster: " + img.name + " (missing)";
		else
			return "Raster: " + img.name;
	}

	@Override
	public int length()
	{
		return 1;
	}

	@Override
	public Component getPanel()
	{
		SetImagePanel.instance().bind(this);
		return SetImagePanel.instance();
	}

	@Override
	public void addTo(List<Short> seq)
	{
		int id = (img == null) ? -1 : img.getIndex();
		seq.add((short) (0x1000 | (id & 0xFFF)));
	}

	@Override
	public void addEditableDownstream(List<Editable> downstream)
	{
		if (img != null)
			downstream.add(img);
	}

	protected static class SetImagePanel extends JPanel
	{
		private static SetImagePanel instance;
		private SetImage cmd;

		private JComboBox<SpriteRaster> imageComboBox;
		private boolean ignoreChanges = false;

		protected static SetImagePanel instance()
		{
			if (instance == null)
				instance = new SetImagePanel();
			return instance;
		}

		private SetImagePanel()
		{
			super(new MigLayout(CommandAnimatorEditor.PANEL_LAYOUT_PROPERTIES));

			imageComboBox = new JComboBox<>();
			imageComboBox.setUI(new BlankArrowUI());

			SpriteRasterRenderer renderer = new SpriteRasterRenderer();
			renderer.setMinimumSize(new Dimension(80, 80));
			renderer.setPreferredSize(new Dimension(80, 80));
			imageComboBox.setRenderer(renderer);
			imageComboBox.setMaximumRowCount(5);
			imageComboBox.addActionListener((e) -> {
				if (!ignoreChanges) {
					SpriteRaster img = (SpriteRaster) imageComboBox.getSelectedItem();
					SpriteEditor.execute(new SetCommandImage(cmd, img));
				}
			});

			JButton btnChoose = new JButton("Select");
			SwingUtils.addBorderPadding(btnChoose);

			btnChoose.addActionListener((e) -> {
				Sprite sprite = cmd.owner.parentAnimation.parentSprite;
				SpriteRaster raster = SpriteEditor.instance().promptForRaster(sprite);
				if (raster != null)
					SpriteEditor.execute(new SetCommandImage(cmd, raster));
			});

			JButton btnClear = new JButton("Clear");
			SwingUtils.addBorderPadding(btnClear);

			btnClear.addActionListener((e) -> {
				SpriteEditor.execute(new SetCommandImage(cmd, null));
			});

			add(SwingUtils.getLabel("Set Raster", 14), "gapbottom 4");
			add(imageComboBox, "w 60%, h 120!");
			add(btnChoose, "split 2, growx");
			add(btnClear, "growx");
		}

		private void bind(SetImage cmd)
		{
			this.cmd = cmd;

			ignoreChanges = true;
			imageComboBox.setSelectedItem(cmd.img);
			ignoreChanges = false;
		}

		protected void setModel(ComboBoxModel<SpriteRaster> model)
		{
			ignoreChanges = true;
			//	Object lastSelected = imageComboBox.getSelectedItem();
			imageComboBox.setModel(model);
			//	imageComboBox.setSelectedItem(lastSelected);
			ignoreChanges = false;
		}

		private class SetCommandImage extends AbstractCommand
		{
			private final SetImage cmd;
			private final SpriteRaster next;
			private final SpriteRaster prev;

			private SetCommandImage(SetImage cmd, SpriteRaster next)
			{
				super("Set Raster");

				this.cmd = cmd;
				this.next = next;
				this.prev = cmd.img;
			}

			@Override
			public void exec()
			{
				super.exec();

				cmd.img = next;

				ignoreChanges = true;
				imageComboBox.setSelectedItem(next);
				ignoreChanges = false;

				cmd.incrementModified();
				CommandAnimatorEditor.repaintCommandList();
			}

			@Override
			public void undo()
			{
				super.undo();

				cmd.img = prev;

				ignoreChanges = true;
				imageComboBox.setSelectedItem(prev);
				ignoreChanges = false;

				cmd.decrementModified();
				CommandAnimatorEditor.repaintCommandList();
			}
		}
	}
}
