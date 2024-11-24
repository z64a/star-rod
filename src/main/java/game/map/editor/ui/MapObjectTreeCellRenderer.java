package game.map.editor.ui;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;

import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTree;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import javax.swing.plaf.UIResource;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeCellRenderer;

import app.Environment;
import game.map.MapObject;
import net.miginfocom.swing.MigLayout;
import util.ui.CircleIcon;

public class MapObjectTreeCellRenderer extends JPanel implements TreeCellRenderer
{
	private JLabel iconLabel;
	private JLabel nameLabel;

	private transient Icon closedIcon;
	private transient Icon leafIcon;
	private transient Icon openIcon;

	private boolean hasInit;

	//XXX BUG: have to explicitly set a size or children with long names will be truncated by parent
	// being renamed. comment out getPreferredSize and use the red border visualization below to see.
	private static final Dimension DEFAULT_SIZE = new Dimension(180, 20);

	@Override
	public Dimension getPreferredSize()
	{
		return DEFAULT_SIZE;
	}

	public MapObjectTreeCellRenderer()
	{
		super();
		iconLabel = new JLabel(Environment.ICON_ERROR, SwingConstants.CENTER);
		nameLabel = new JLabel("placeholder");

		setLayout(new MigLayout("ins 0, fillx"));
		add(iconLabel, "growx");
		add(nameLabel, "growx, pushx");

		//setBorder(BorderFactory.createLineBorder(Color.red));
		setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));

		setOpaque(false);
	}

	@Override
	public void updateUI()
	{
		super.updateUI();

		if (!hasInit || (leafIcon instanceof UIResource)) {
			leafIcon = CircleIcon.instance();
		}
		if (!hasInit || (closedIcon instanceof UIResource)) {
			closedIcon = UIManager.getIcon("Tree.closedIcon");
		}
		if (!hasInit || (openIcon instanceof UIManager)) {
			openIcon = UIManager.getIcon("Tree.openIcon");
		}
	}

	@Override
	public Component getTreeCellRendererComponent(JTree tree, Object obj,
		boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus)
	{
		DefaultMutableTreeNode node = (DefaultMutableTreeNode) obj;

		if (node.getUserObject() instanceof MapObject mobj) {
			if (mobj.allowsChildren())
				iconLabel.setIcon(expanded ? openIcon : closedIcon);
			else
				iconLabel.setIcon(leafIcon);

			if (mobj.hidden)
				nameLabel.setFont(getFont().deriveFont(Font.ITALIC));
			else
				nameLabel.setFont(getFont().deriveFont(Font.PLAIN));

			nameLabel.setText(mobj.toString());
			nameLabel.setForeground(null);
		}
		else {
			iconLabel.setIcon(Environment.ICON_ERROR);
		}

		return this;
	}
}
