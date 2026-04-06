package game.map.shading;

import java.awt.BorderLayout;
import java.util.Objects;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;

import game.map.shading.EditableShadingData.EditableShadingGroup;
import game.map.shading.EditableShadingData.EditableShadingProfile;

public class ShadingTreePanel extends JPanel
{
	private static enum SelectionKind
	{
		NONE,
		ROOT,
		GROUP,
		PROFILE
	}

	private static final class ShadingTreeRoot
	{
		private final String label;

		private ShadingTreeRoot(String label)
		{
			this.label = label;
		}

		@Override
		public String toString()
		{
			return label;
		}

	}

	private final EditableShadingData data;

	private final DefaultMutableTreeNode rootNode;
	private final DefaultTreeModel treeModel;
	private final JTree tree;

	private final JButton createButton;
	private final JButton deleteButton;
	private final JButton duplicateButton;

	private ShadingTreePanel(EditableShadingData data)
	{
		this.data = data;

		rootNode = buildTreeNodes(data);
		treeModel = new DefaultTreeModel(rootNode);
		tree = new JTree(treeModel);

		createButton = new JButton("Create");
		deleteButton = new JButton("Delete");
		duplicateButton = new JButton("Duplicate");

		build();
		bindEvents();
		refreshButtons();

		//FIXME
		//	setMinimumSize(new Dimension(460, 420));
		//	setPreferredSize(new Dimension(560, 520));
	}

	private void build()
	{
		setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

		tree.setRootVisible(true);
		tree.setShowsRootHandles(true);
		tree.expandRow(0);
		tree.setSelectionRow(0);

		add(new JScrollPane(tree), BorderLayout.CENTER);

		JPanel buttonPanel = new JPanel();
		buttonPanel.add(createButton);
		buttonPanel.add(deleteButton);
		buttonPanel.add(duplicateButton);

		add(buttonPanel, BorderLayout.SOUTH);
	}

	private void bindEvents()
	{
		tree.addTreeSelectionListener(this::onTreeSelectionChanged);

		createButton.addActionListener(e -> doCreate());
		deleteButton.addActionListener(e -> doDelete());
		duplicateButton.addActionListener(e -> doDuplicate());
	}

	private void onTreeSelectionChanged(TreeSelectionEvent e)
	{
		refreshButtons();
	}

	private void refreshButtons()
	{
		SelectionKind kind = getSelectionKind();

		createButton.setEnabled(kind == SelectionKind.ROOT || kind == SelectionKind.GROUP);
		deleteButton.setEnabled(kind == SelectionKind.GROUP || kind == SelectionKind.PROFILE);
		duplicateButton.setEnabled(kind == SelectionKind.GROUP || kind == SelectionKind.PROFILE);
	}

	private void doCreate()
	{
		DefaultMutableTreeNode selectedNode = getSelectedNode();
		if (selectedNode == null)
			return;

		Object obj = selectedNode.getUserObject();

		if (obj instanceof ShadingTreeRoot) {
			EditableShadingGroup newGroup = createDefaultGroup();
			data.groups.add(newGroup);

			DefaultMutableTreeNode newGroupNode = new DefaultMutableTreeNode(newGroup);
			treeModel.insertNodeInto(newGroupNode, rootNode, rootNode.getChildCount());

			TreePath path = new TreePath(newGroupNode.getPath());
			tree.scrollPathToVisible(path);
			tree.setSelectionPath(path);
			return;
		}

		if (obj instanceof EditableShadingGroup selectedGroup) {
			EditableShadingProfile newProfile = createDefaultProfile(selectedGroup);
			selectedGroup.profiles.add(newProfile);

			DefaultMutableTreeNode newProfileNode = new DefaultMutableTreeNode(newProfile);
			treeModel.insertNodeInto(newProfileNode, selectedNode, selectedNode.getChildCount());

			TreePath path = new TreePath(newProfileNode.getPath());
			tree.scrollPathToVisible(path);
			tree.setSelectionPath(path);
		}
	}

	private void doDelete()
	{
		DefaultMutableTreeNode selectedNode = getSelectedNode();
		if (selectedNode == null || selectedNode == rootNode)
			return;

		Object obj = selectedNode.getUserObject();

		if (obj instanceof EditableShadingGroup selectedGroup) {
			int result = JOptionPane.showConfirmDialog(
				this,
				"Delete group '" + selectedGroup + "' and all contained profiles?",
				"Delete Group",
				JOptionPane.YES_NO_OPTION,
				JOptionPane.WARNING_MESSAGE
			);

			if (result != JOptionPane.YES_OPTION)
				return;

			int modelIndex = data.groups.indexOf(selectedGroup);
			if (modelIndex >= 0)
				data.groups.remove(modelIndex);

			DefaultMutableTreeNode parent = (DefaultMutableTreeNode) selectedNode.getParent();
			treeModel.removeNodeFromParent(selectedNode);

			TreePath path = new TreePath(parent.getPath());
			tree.setSelectionPath(path);
			return;
		}

		if (obj instanceof EditableShadingProfile selectedProfile) {
			DefaultMutableTreeNode parentNode = (DefaultMutableTreeNode) selectedNode.getParent();
			Object parentObj = parentNode.getUserObject();

			if (parentObj instanceof EditableShadingGroup parentGroup) {
				int result = JOptionPane.showConfirmDialog(
					this,
					"Delete profile '" + selectedProfile + "'?",
					"Delete Profile",
					JOptionPane.YES_NO_OPTION,
					JOptionPane.WARNING_MESSAGE
				);

				if (result != JOptionPane.YES_OPTION)
					return;

				parentGroup.profiles.remove(selectedProfile);
				treeModel.removeNodeFromParent(selectedNode);

				TreePath path = new TreePath(parentNode.getPath());
				tree.setSelectionPath(path);
			}
		}
	}

	private void doDuplicate()
	{
		DefaultMutableTreeNode selectedNode = getSelectedNode();
		if (selectedNode == null || selectedNode == rootNode)
			return;

		Object obj = selectedNode.getUserObject();

		if (obj instanceof EditableShadingGroup selectedGroup) {
			EditableShadingGroup copy = selectedGroup.deepCopy();
			copy.name = makeUniqueGroupName(copy.name);

			DefaultMutableTreeNode parentNode = (DefaultMutableTreeNode) selectedNode.getParent();
			int insertIndex = parentNode.getIndex(selectedNode) + 1;

			data.groups.add(insertIndex, copy);

			DefaultMutableTreeNode copyNode = buildGroupNode(copy);
			treeModel.insertNodeInto(copyNode, parentNode, insertIndex);

			TreePath path = new TreePath(copyNode.getPath());
			tree.scrollPathToVisible(path);
			tree.setSelectionPath(path);
			return;
		}

		if (obj instanceof EditableShadingProfile selectedProfile) {
			DefaultMutableTreeNode parentNode = (DefaultMutableTreeNode) selectedNode.getParent();
			Object parentObj = parentNode.getUserObject();

			if (parentObj instanceof EditableShadingGroup parentGroup) {
				EditableShadingProfile copy = selectedProfile.deepCopy();
				copy.name = makeUniqueProfileName(copy.name);

				int insertIndex = parentNode.getIndex(selectedNode) + 1;
				parentGroup.profiles.add(insertIndex, copy);

				DefaultMutableTreeNode copyNode = new DefaultMutableTreeNode(copy);
				treeModel.insertNodeInto(copyNode, parentNode, insertIndex);

				TreePath path = new TreePath(copyNode.getPath());
				tree.scrollPathToVisible(path);
				tree.setSelectionPath(path);
			}
		}
	}

	private DefaultMutableTreeNode buildTreeNodes(EditableShadingData data)
	{
		DefaultMutableTreeNode root = new DefaultMutableTreeNode(new ShadingTreeRoot("Shading Profiles"));

		for (EditableShadingGroup group : data.groups)
			root.add(buildGroupNode(group));

		return root;
	}

	private DefaultMutableTreeNode buildGroupNode(EditableShadingGroup group)
	{
		DefaultMutableTreeNode groupNode = new DefaultMutableTreeNode(group);
		for (EditableShadingProfile profile : group.profiles)
			groupNode.add(new DefaultMutableTreeNode(profile));
		return groupNode;
	}

	private DefaultMutableTreeNode getSelectedNode()
	{
		Object selected = tree.getLastSelectedPathComponent();
		if (selected instanceof DefaultMutableTreeNode node)
			return node;
		return rootNode;
	}

	private SelectionKind getSelectionKind()
	{
		DefaultMutableTreeNode node = getSelectedNode();
		if (node == null)
			return SelectionKind.NONE;

		Object obj = node.getUserObject();
		if (obj instanceof ShadingTreeRoot)
			return SelectionKind.ROOT;
		if (obj instanceof EditableShadingGroup)
			return SelectionKind.GROUP;
		if (obj instanceof EditableShadingProfile)
			return SelectionKind.PROFILE;

		return SelectionKind.NONE;
	}

	private EditableShadingGroup createDefaultGroup()
	{
		String baseName = "New Group";
		String name = makeUniqueGroupName(baseName);
		return new EditableShadingGroup(name);
	}

	private EditableShadingProfile createDefaultProfile(EditableShadingGroup group)
	{
		EditableShadingProfile profile = new EditableShadingProfile();
		profile.name = makeUniqueProfileName("NewProfile");
		profile.ambient = new int[] { 255, 255, 255 };
		profile.power = 100;
		return profile;
	}

	private String makeUniqueGroupName(String baseName)
	{
		if (baseName == null || baseName.isBlank())
			baseName = "Group";

		String candidate = baseName;
		int suffix = 2;

		while (containsGroupName(candidate)) {
			candidate = baseName + " (" + suffix + ")";
			suffix++;
		}

		return candidate;
	}

	private boolean containsGroupName(String name)
	{
		for (EditableShadingGroup group : data.groups) {
			if (Objects.equals(group.name, name))
				return true;
		}
		return false;
	}

	private String makeUniqueProfileName(String baseName)
	{
		if (baseName == null || baseName.isBlank())
			baseName = "Profile";

		String candidate = baseName;
		int suffix = 2;

		while (data.find(candidate) != null) {
			candidate = baseName + suffix;
			suffix++;
		}

		return candidate;
	}
}
