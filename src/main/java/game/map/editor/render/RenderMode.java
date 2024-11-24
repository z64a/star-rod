package game.map.editor.render;

import static org.lwjgl.opengl.GL11.*;

import java.awt.Component;
import java.util.ArrayList;
import java.util.List;

import javax.swing.DefaultListCellRenderer;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;

import net.miginfocom.swing.MigLayout;
import renderer.shaders.RenderState;

public enum RenderMode
{
	//@formatter:off
	NONE					( 0, 0, "None"),

	//	SURF_SOLID_AA_ZB_L0		( 0x00, -100000, "Surf_Solid_AA_ZB_Layer0"),
	SURF_SOLID_AA_ZB		( 0x01, 1000000, "Surface_OPA"),
	SURF_SOLID_ZB			( 0x03, 1000000, "Surface_OPA_No_AA"),
	SURF_SOLID_AA			( 0x04,       0, "Surface_OPA_No_ZB"),
	SURF_XLU_AA_ZB_L1		( 0x11, 8000000, "Surface_XLU_Layer1"),
	SURF_XLU_AA_ZB_L2 		( 0x16, 7500000, "Surface_XLU_Layer2"),		// unused, seems okay
	SURF_XLU_AA_ZB_L3		( 0x22, 6000000, "Surface_XLU_Layer3"),
	SURF_XLU_ZB				( 0x13, 8000000, "Surface_XLU_No_AA"),
	SURF_XLU_AA				( 0x14,       0, "Surface_XLU_No_ZB"),
	//	SURF_XLU_ZB_Z_UPD 		( 0x15, 8000000, "Surf_XLU_ZB + Z_UPD"),	// unused and non-standard -- don't allow this one
	ALPHA_TEST_AA_ZB_2SIDE	( 0x0D, 1000000, "AlphaTest"),
	ALPHA_TEST_AA_ZB_1SIDE	( 0x0F, 1000000, "AlphaTest_OneSided"),
	ALPHA_TEST_AA			( 0x10,       0, "AlphaTest_No_ZB"),
	DECAL_SOLID_AA_ZB		( 0x05, 1000000, "Decal_OPA"),
	DECAL_SOLID_ZB			( 0x07, 1000000, "Decal_OPA_No_AA"), 		// unused, probably okay
	DECAL_XLU_AA_ZB 		( 0x1A, 7000000, "Decal_XLU"),
	DECAL_XLU_ZB			( 0x1C, 7000000, "Decal_XLU_No_AA"),
	INTER_SOLID_AA_ZB		( 0x09, 1000000, "Intersecting_OPA"),
	INTER_XLU_AA_ZB 		( 0x26, 5500000, "Intersecting_XLU"),
	//	SURF_XLU_AA_ZB_Z_UPD 	( 0x29, 8000000, "Surf_XLU_AA_ZB + Z_UPD"),	// unused and non-standard -- don't allow this one
	SURF_CLOUD_ZB 			( 0x2E, 8000000, "Cloud"),
	SURF_CLOUD 				( 0x2F,  700000, "Cloud_No_ZB"),

	SHADOW					( true, 0x20, 6500000, "Shadow");
	//@formatter:on

	public final boolean hidden;
	public final boolean translucent;
	public final int id;
	public final int depth;
	public final String name;

	private RenderMode(int id, int depth, String name)
	{
		this(false, id, depth, name);
	}

	private RenderMode(boolean hidden, int id, int depth, String name)
	{
		this.hidden = hidden;
		this.id = id;
		this.depth = depth;
		this.name = name;
		this.translucent = isTranslucent(id);
	}

	@Override
	public String toString()
	{
		if (this == NONE)
			return "NONE";
		else
			return String.format("%s (0x%02X)", name, id);
	}

	public void setState(int category)
	{
		displayListTable[getRenderIndex(category, id)].load();
	}

	private static boolean isTranslucent(int id)
	{
		switch (id) {
			case 0x11:
			case 0x16:
			case 0x22:
			case 0x1A:
			case 0x14:
			case 0x26:
				return true;

			default:

		}
		return false;
	}

	private static final RenderMode[] EDITOR_MODE_LIST;

	static {
		List<RenderMode> visibleList = new ArrayList<>();
		for (RenderMode mode : RenderMode.values()) {
			if (!mode.hidden)
				visibleList.add(mode);
		}
		EDITOR_MODE_LIST = new RenderMode[visibleList.size()];
		visibleList.toArray(EDITOR_MODE_LIST);
	}

	public static RenderMode[] getEditorModes()
	{
		return EDITOR_MODE_LIST;
	}

	public static class RenderModeComboBoxRenderer extends DefaultListCellRenderer
	{
		@Override
		public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus)
		{
			JPanel panel = new JPanel(new MigLayout("fillx, ins 0"));
			JLabel nameLabel = new JLabel();
			JLabel idLabel = new JLabel();

			if (value != null) {
				if (value instanceof RenderMode renderMode) {
					nameLabel.setText(renderMode.name);

					if (value != NONE)
						idLabel.setText(String.format("0x%02X", renderMode.id));
					else
						idLabel.setText("");
				}
			}

			panel.add(nameLabel, "pushx, growx");
			panel.add(idLabel, "w 12%!");

			if (isSelected) {
				panel.setBackground(list.getSelectionBackground());
				panel.setForeground(list.getSelectionForeground());
			}
			else {
				panel.setBackground(list.getBackground());
				panel.setForeground(list.getForeground());
			}

			return panel;
		}
	}

	public static RenderMode getModeForID(int id)
	{
		for (RenderMode mode : RenderMode.values()) {
			if (id == mode.id)
				return mode;
		}
		return null;
	}

	public static int getRenderIndex(int category, int mode)
	{
		switch (category) {
			default:
			case 1: // missing texure
				switch (mode) {
					default:
						return 0;
					case 3:
						return 1;
					case 4:
						return 0x2E;
					case 5:
						return 2;
					case 7:
						return 3;
					case 9:
						return 4;
					case 0x0D:
						return 6;
					case 0x0F:
						return 7;
					case 0x10:
						return 0x2F;
					case 0x11:
					case 0x16:
					case 0x22:
						return 8;
					case 0x13:
						return 0x0A;
					case 0x14:
						return 0x30;
					case 0x15:
						return 0x0B;
					case 0x1A:
						return 0x0C;
					case 0x1C:
						return 0x0D;
					case 0x26:
						return 0x0E;
					case 0x29:
						return 9;
					case 0x2E:
						return 0x37;
					case 0x2F:
						return 0x38;
				}

			case 2: // standard
				switch (mode) {
					default:
						return 0x10;
					case 3:
						return 0x11;
					case 4:
						return 0x31;
					case 5:
						return 0x12;
					case 7:
						return 0x13;
					case 9:
						return 0x14;
					case 0x0D:
						return 0x16;
					case 0x0F:
						return 0x17;
					case 0x10:
						return 0x32;
					case 0x11:
					case 0x16:
					case 0x22:
						return 0x18;
					case 0x13:
						return 0x1A;
					case 0x14:
						return 0x33;
					case 0x1A:
						return 0x1B;
					case 0x1C:
						return 0x1C;
					case 0x26:
						return 0x1D;
					case 0x29:
						return 0x19;
					case 0x2E:
						return 0x39;
					case 0x2F:
						return 0x3A;
				}
			case 3: // fog enabled
			case 6: // identical to category 3, except has fog color multiplier
				switch (mode) {
					default:
						return 0x1F;
					case 3:
						return 0x20;
					case 4:
						return 0x34;
					case 5:
						return 0x21;
					case 7:
						return 0x22;
					case 9:
						return 0x23;
					case 0x0D:
						return 0x25;
					case 0x0F:
						return 0x26;
					case 0x10:
						return 0x35;
					case 0x11:
					case 0x16:
					case 0x22:
						return 0x27;
					case 0x13:
						return 0x29;
					case 0x14:
						return 0x36;
					case 0x1A:
						return 0x2A;
					case 0x1C:
						return 0x2B;
					case 0x26:
						return 0x2C;
					case 0x29:
						return 0x28;
					case 0x2E:
						return 0x3B;
					case 0x2F:
						return 0x3C;
				}
			case 10:
			case 11: // subset of category 3
				switch (mode) {
					default:
						return 0x1F;
					case 0x5:
						return 0x21;
					case 0x9:
						return 0x23;
					case 0xD:
						return 0x25;
					case 0x2E:
						return 0x3B;
					case 0x2F:
						return 0x3C;
				}
		}
	}

	private static RenderModeDisplayList[] displayListTable = new RenderModeDisplayList[0x3D];
	static {
		displayListTable[0x00] = new RenderModeDisplayList(0x00, 0x000AA40F, 0x003F0604, 0x00220405);
		displayListTable[0x01] = new RenderModeDisplayList(0x01, 0x000AA446, 0x003F0604, 0x00220405);
		displayListTable[0x02] = new RenderModeDisplayList(0x02, 0x000AA5AB, 0x003F0604, 0x00220405);
		displayListTable[0x03] = new RenderModeDisplayList(0x03, 0x000AA5C2, 0x003F0604, 0x00220405);
		displayListTable[0x04] = new RenderModeDisplayList(0x04, 0x000AA48F, 0x003F0604, 0x00220405);
		displayListTable[0x05] = new RenderModeDisplayList(0x05, 0x000AA48F, 0x003F0604, 0x00220405);
		displayListTable[0x06] = new RenderModeDisplayList(0x06, 0x000AA60F, 0x003F0604, 0x00220005);
		displayListTable[0x07] = new RenderModeDisplayList(0x07, 0x000AA60F, 0x003F0604, 0x00220405);
		displayListTable[0x08] = new RenderModeDisplayList(0x08, 0x000A093B, 0x003F0604, 0x00220005);
		displayListTable[0x09] = new RenderModeDisplayList(0x09, 0x000A093F, 0x003F0604, 0x00220005);
		displayListTable[0x0A] = new RenderModeDisplayList(0x0A, 0x000A094A, 0x003F0604, 0x00220005);
		displayListTable[0x0B] = new RenderModeDisplayList(0x0B, 0x000A093F, 0x003F0604, 0x00220005);
		displayListTable[0x0C] = new RenderModeDisplayList(0x0C, 0x000A09BB, 0x003F0604, 0x00220005);
		displayListTable[0x0D] = new RenderModeDisplayList(0x0D, 0x000A09EA, 0x003F0604, 0x00220005);
		displayListTable[0x0E] = new RenderModeDisplayList(0x0E, 0x000A08BB, 0x003F0604, 0x00220005);
		displayListTable[0x0F] = new RenderModeDisplayList(0x0F, null, 0x003F0604, 0x00220005);
		displayListTable[0x10] = new RenderModeDisplayList(0x10, 0x0183240F, 0x003F0604, 0x00220405);
		displayListTable[0x11] = new RenderModeDisplayList(0x11, 0x01832446, 0x003F0604, 0x00220405);
		displayListTable[0x12] = new RenderModeDisplayList(0x12, 0x018325AB, 0x003F0604, 0x00220405);
		displayListTable[0x13] = new RenderModeDisplayList(0x13, 0x018325C2, 0x003F0604, 0x00220405);
		displayListTable[0x14] = new RenderModeDisplayList(0x14, 0x0183248F, 0x003F0604, 0x00220405);
		displayListTable[0x15] = new RenderModeDisplayList(0x15, 0x0183248F, 0x003F0604, 0x00220405);
		displayListTable[0x16] = new RenderModeDisplayList(0x16, 0x0183260F, 0x003F0604, 0x00220005);
		displayListTable[0x17] = new RenderModeDisplayList(0x17, 0x0183260F, 0x003F0604, null);
		displayListTable[0x18] = new RenderModeDisplayList(0x18, 0x0183093B, 0x003F0604, 0x00220005);
		displayListTable[0x19] = new RenderModeDisplayList(0x19, 0x0183093B, 0x003F0604, 0x00220005);
		displayListTable[0x1A] = new RenderModeDisplayList(0x1A, 0x0183094A, 0x003F0604, 0x00220005);
		displayListTable[0x1B] = new RenderModeDisplayList(0x1B, 0x018309BB, 0x003F0604, 0x00220005);
		displayListTable[0x1C] = new RenderModeDisplayList(0x1C, 0x018309CA, 0x003F0604, 0x00220005);
		displayListTable[0x1D] = new RenderModeDisplayList(0x1D, 0x018308BB, 0x003F0604, 0x00220005);
		displayListTable[0x1E] = new RenderModeDisplayList(0x1E, null, 0x003F0604, 0x00220005);
		displayListTable[0x1F] = new RenderModeDisplayList(0x1F, 0xF902240F, 0x003F0604, 0x00230405);
		displayListTable[0x20] = new RenderModeDisplayList(0x20, 0xF9022446, 0x003F0604, 0x00230405);
		displayListTable[0x21] = new RenderModeDisplayList(0x21, 0xF90225AB, 0x003F0604, 0x00230405);
		displayListTable[0x22] = new RenderModeDisplayList(0x22, 0xF90225C2, 0x003F0604, 0x00230405);
		displayListTable[0x23] = new RenderModeDisplayList(0x23, 0xF902248F, 0x003F0604, 0x00230405);
		displayListTable[0x24] = new RenderModeDisplayList(0x24, 0xF902248F, 0x003F0604, 0x00230405);
		displayListTable[0x25] = new RenderModeDisplayList(0x25, 0xF902260F, 0x003F0604, 0x00230005);
		displayListTable[0x26] = new RenderModeDisplayList(0x26, 0xF902260F, 0x003F0604, null);
		displayListTable[0x27] = new RenderModeDisplayList(0x27, 0xF902093B, 0x003F0604, 0x00230005);
		displayListTable[0x28] = new RenderModeDisplayList(0x28, 0xF902093B, 0x003F0604, 0x00230005);
		displayListTable[0x29] = new RenderModeDisplayList(0x29, 0xF902094A, 0x003F0604, 0x00230005);
		displayListTable[0x2A] = new RenderModeDisplayList(0x2A, 0xF90209BB, 0x003F0604, 0x00230005);
		displayListTable[0x2B] = new RenderModeDisplayList(0x2B, 0xF90209CA, 0x003F0604, 0x00230005);
		displayListTable[0x2C] = new RenderModeDisplayList(0x2C, 0xF90208BB, 0x003F0604, 0x00230005);
		displayListTable[0x2D] = new RenderModeDisplayList(0x2D, null, 0x003F0604, 0x00230005);
		displayListTable[0x2E] = new RenderModeDisplayList(0x2E, 0x000AA409, 0x003F0605, 0x00220404);
		displayListTable[0x2F] = new RenderModeDisplayList(0x2F, 0x000AA609, 0x003F0605, 0x00220004);
		displayListTable[0x30] = new RenderModeDisplayList(0x30, 0x000A0839, 0x003F0605, 0x00220004);
		displayListTable[0x31] = new RenderModeDisplayList(0x31, 0x01832409, 0x003F0605, 0x00220404);
		displayListTable[0x32] = new RenderModeDisplayList(0x32, 0x01832609, 0x003F0605, 0x00220004);
		displayListTable[0x33] = new RenderModeDisplayList(0x33, 0x01830839, 0x003F0605, 0x00220004);
		displayListTable[0x34] = new RenderModeDisplayList(0x34, 0xF9022409, 0x003F0605, 0x00230404);
		displayListTable[0x35] = new RenderModeDisplayList(0x35, 0xF9022609, 0x003F0605, 0x00230004);
		displayListTable[0x36] = new RenderModeDisplayList(0x36, 0xF9020839, 0x003F0605, 0x00230004);
		displayListTable[0x37] = new RenderModeDisplayList(0x37, 0x000A096A, 0x003F0604, 0x00220005);
		displayListTable[0x38] = new RenderModeDisplayList(0x38, 0x000A0868, 0x003F0604, 0x00220005);
		displayListTable[0x39] = new RenderModeDisplayList(0x39, 0x0183096A, 0x003F0604, 0x00220005);
		displayListTable[0x3A] = new RenderModeDisplayList(0x3A, 0x01830868, 0x003F0604, 0x00220005);
		displayListTable[0x3B] = new RenderModeDisplayList(0x3B, 0xF902096A, 0x003F0604, 0x00220005);
		displayListTable[0x3C] = new RenderModeDisplayList(0x3C, 0xF9020868, 0x003F0604, 0x00220005);
	}

	private static final class RenderModeDisplayList
	{
		public static final int G_BL_CLR_IN = 0;
		public static final int G_BL_CLR_MEM = 1;
		public static final int G_BL_CLR_BL = 2;
		public static final int G_BL_CLR_FOG = 3;

		public static final int G_BL_A_IN = 0;
		public static final int G_BL_A_FOG = 1;
		public static final int G_BL_A_SHADE = 2;

		public static final int G_BL_1MA = 0;
		public static final int G_BL_A_MEM = 1;
		public static final int G_BL_1 = 2;
		public static final int G_BL_0 = 3;

		public static final int CVG_DST_CLAMP = 0;
		public static final int CVG_DST_WRAP = 1;
		public static final int CVG_DST_FULL = 2;
		public static final int CVG_DST_SAVE = 3;

		public static final int ZMODE_OPA = 0;
		public static final int ZMODE_INTER = 1;
		public static final int ZMODE_XLU = 2;
		public static final int ZMODE_DEC = 3;

		private final int index;

		public boolean AA_EN;
		public boolean Z_CMP;
		public boolean Z_UPD;
		public boolean IM_RD;

		public boolean CLR_ON_CVG;
		public boolean CVG_X_ALPHA;
		public boolean ALPHA_CVG_SEL;
		public boolean FORCE_BL;

		public int CVG_DST;
		public int ZMODE;

		// (P * A + M * B) / (A + B)
		public int[] BL_P = new int[2];
		public int[] BL_M = new int[2];
		public int[] BL_A = new int[2];
		public int[] BL_B = new int[2];

		public RenderModeDisplayList(int index, Integer setOtherModeL, Integer clearGeometryMode, Integer setGeometryMode)
		{
			this.index = index;

			if (setOtherModeL != null) {
				int cycInd = setOtherModeL & 0x1FFF;
				int cycDep = (setOtherModeL >> 13) & 0xFFFF;

				AA_EN = ((cycInd & 1) != 0);
				Z_CMP = ((cycInd & 2) != 0);
				Z_UPD = ((cycInd & 4) != 0);
				IM_RD = ((cycInd & 8) != 0);

				CVG_DST = (cycInd >> 5) & 3;

				CLR_ON_CVG = ((cycInd & 0x10) != 0);
				CVG_X_ALPHA = ((cycInd & 0x200) != 0);
				ALPHA_CVG_SEL = ((cycInd & 0x400) != 0);
				FORCE_BL = ((cycInd & 0x800) != 0);

				ZMODE = (cycInd >> 7) & 3;

				BL_P[0] = (cycDep >> 14) & 3;
				BL_P[1] = (cycDep >> 12) & 3;

				BL_A[0] = (cycDep >> 10) & 3;
				BL_A[1] = (cycDep >> 8) & 3;

				BL_M[0] = (cycDep >> 6) & 3;
				BL_M[1] = (cycDep >> 4) & 3;

				BL_B[0] = (cycDep >> 2) & 3;
				BL_B[1] = (cycDep >> 0) & 3;
			}
		}

		public void load()
		{
			switch (index) {
				/*
				case 0x8:
				case 0x18:
				case 0x30:
				case 0x33:
				 */
				// may also want 26, 2E, 2F for all XLU and CLD modes
				case 0x08: // modes 11,16,22
				case 0x18: // ...
				case 0x27: // ...
				case 0x0C: // mode 1A
				case 0x1B: // ...
				case 0x2A: // ...
				case 0x30: // mode 14
				case 0x33: // ...
				case 0x36: // ...
					RenderState.setDepthWrite(false);
					break;
				default:
					RenderState.setDepthWrite(true);
					break;
			}

			//	RenderState.setDepthWrite(Z_UPD);

			if (Z_CMP) {
				if (ZMODE == ZMODE_DEC)
					RenderState.setDepthFunc(GL_LEQUAL);
				else
					RenderState.setDepthFunc(GL_LESS);
			}

			if (ZMODE == ZMODE_DEC) {
				glPolygonOffset(-1, -1);
				glEnable(GL_POLYGON_OFFSET_FILL);
			}
			else {
				glDisable(GL_POLYGON_OFFSET_FILL);
			}

			int blendSrcFactor;
			int blendDestFactor;

			// fog: G_BL_CLR_FOG, G_BL_A_SHADE, G_BL_CLR_IN, G_BL_1MA
			// hmm...

			if (FORCE_BL && BL_M[1] == G_BL_CLR_MEM) {
				if (BL_A[1] == G_BL_0)
					blendSrcFactor = GL_ZERO;
				else if (ALPHA_CVG_SEL && !CVG_X_ALPHA)
					blendSrcFactor = GL_ONE;
				else
					blendSrcFactor = GL_SRC_ALPHA;

				switch (BL_B[1]) {
					case G_BL_1MA:
						if (blendSrcFactor == GL_SRC_ALPHA)
							blendDestFactor = GL_ONE_MINUS_SRC_ALPHA;
						else if (blendSrcFactor == GL_ONE)
							blendDestFactor = GL_ZERO;
						else
							blendDestFactor = GL_ONE;
						break;
					case G_BL_A_MEM:
						blendDestFactor = GL_DST_ALPHA;
						break;
					case G_BL_1:
						blendDestFactor = GL_ONE;
						break;
					case G_BL_0:
						blendDestFactor = GL_ZERO;
						break;
					default:
						throw new IllegalStateException("Invalid blend mode: " + BL_B[1]);
				}
			}
			else {
				blendSrcFactor = GL_ONE;
				blendDestFactor = GL_ZERO;
			}

			RenderState.setBlendFunc(blendSrcFactor, blendDestFactor);
		}
	}

	public static void resetState()
	{
		RenderState.initDepthWrite();
		RenderState.initDepthFunc();
		RenderState.initBlendFunc();
		glDisable(GL_POLYGON_OFFSET_FILL);
	}

	public static int getFromOtherModelL(int value)
	{
		switch (value) {
			case 0x000AA40F:
			case 0x0183240F:
			case 0xF902240F:
				return 0x01;
			case 0x000AA446:
			case 0x01832446:
			case 0xF9022446:
				return 0x03;
			case 0x000AA409:
			case 0x01832409:
			case 0xF9022409:
				return 0x04;
			case 0x000AA5AB:
			case 0x018325AB:
			case 0xF90225AB:
				return 0x05;
			case 0x000AA5C2:
			case 0x018325C2:
			case 0xF90225C2:
				return 0x07;
			case 0x000AA48F:
			case 0x0183248F:
			case 0xF902248F:
				return 0x09;
			case 0x000AA60F:
			case 0x0183260F:
			case 0xF902260F:
				return 0x0D; // also F
			case 0x000AA609:
			case 0x01832609:
			case 0xF9022609:
				return 0x10;
			case 0x000A093B:
			case 0x0183093B:
			case 0xF902093B:
				return 0x11; // also 16 and 22
			case 0x000A094A:
			case 0x0183094A:
			case 0xF902094A:
				return 0x13;
			case 0x000A0839:
			case 0x01830839:
			case 0xF9020839:
				return 0x14;
			case 0x000A09BB:
			case 0x018309BB:
			case 0xF90209BB:
				return 0x1A;
			case 0x000A09EA:
			case 0x018309CA:
			case 0xF90209CA:
				return 0x1C;
			case 0x000A08BB:
			case 0x018308BB:
			case 0xF90208BB:
				return 0x26;
			case 0x000A096A:
			case 0x0183096A:
			case 0xF902096A:
				return 0x2E;
			case 0x000A0868:
			case 0x01830868:
			case 0xF9020868:
				return 0x2F;
			default:
				return -1;
		}
	}
}
