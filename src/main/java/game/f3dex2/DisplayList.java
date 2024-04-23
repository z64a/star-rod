package game.f3dex2;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import app.StarRodException;
import app.input.InvalidInputException;
import game.f3dex2.commands.BranchZ;
import game.f3dex2.commands.CullDL;
import game.f3dex2.commands.FillRect;
import game.f3dex2.commands.GeometryMode;
import game.f3dex2.commands.LoadBlock;
import game.f3dex2.commands.LoadTLUT;
import game.f3dex2.commands.LoadTile;
import game.f3dex2.commands.LoadUCode;
import game.f3dex2.commands.LoadVertex;
import game.f3dex2.commands.ModifyVertex;
import game.f3dex2.commands.MoveMem;
import game.f3dex2.commands.MoveWord;
import game.f3dex2.commands.NewDL;
import game.f3dex2.commands.PopMatrix;
import game.f3dex2.commands.Quad;
import game.f3dex2.commands.SetBuffer;
import game.f3dex2.commands.SetColor;
import game.f3dex2.commands.SetCombine;
import game.f3dex2.commands.SetConvert;
import game.f3dex2.commands.SetFillColor;
import game.f3dex2.commands.SetImg;
import game.f3dex2.commands.SetKeyGB;
import game.f3dex2.commands.SetKeyR;
import game.f3dex2.commands.SetOtherModeH;
import game.f3dex2.commands.SetOtherModeL;
import game.f3dex2.commands.SetPrimColor;
import game.f3dex2.commands.SetPrimDepth;
import game.f3dex2.commands.SetScissor;
import game.f3dex2.commands.SetTile;
import game.f3dex2.commands.SetTileSize;
import game.f3dex2.commands.TexRect;
import game.f3dex2.commands.Texture;
import game.f3dex2.commands.Tri1;
import game.f3dex2.commands.Tri2;
import game.f3dex2.commands.UseMatrix;

public class DisplayList
{
	public static final DisplayList instance = new DisplayList();

	private DisplayList()
	{}

	public static CommandType getCommandForOpcode(int opcode)
	{
		return decodeMap.get(opcode);
	}

	public static void printCmd(String line) throws InvalidInputException
	{
		BaseF3DEX2 cmd = parse(line);
		for (int i : cmd.assemble())
			System.out.printf("%08X ", i);
		System.out.println();
	}

	public static void printLine(int gfx0, int gfx1) throws InvalidInputException
	{
		int opcode = (gfx0 >> 24) & 0xFF;
		CommandType type = decodeMap.get(opcode);
		if (type == null)
			throw new IllegalStateException(String.format("Invalid display command opcode: %X", opcode));
		if (type.size != 2)
			throw new IllegalStateException(String.format("Invalid size for display command %s: %d", type.name(), type.size));
		System.out.println(type.create(gfx0, gfx1).getString());
	}

	public static enum CommandType
	{
		// @formatter:off
		G_NOOP				(0x00, NoArg.class),
		G_VTX				(0x01, LoadVertex.class),
		G_MODIFYVTX			(0x02, ModifyVertex.class),
		G_CULLDL			(0x03, CullDL.class),
		G_BRANCH_Z			(0x04, 4, BranchZ.class),
		G_TRI1				(0x05, Tri1.class),
		G_TRI2				(0x06, Tri2.class),
		G_QUAD				(0x07, Quad.class),
		G_DMA_IO			(0xD6), // no binding
		G_TEXTURE			(0xD7, Texture.class),
		G_POPMTX			(0xD8, PopMatrix.class),
		G_GEOMETRYMODE		(0xD9, GeometryMode.class),
		G_MTX				(0xDA, UseMatrix.class),
		G_MOVEWORD			(0xDB, MoveWord.class),
		G_MOVEMEM			(0xDC, MoveMem.class),
		G_LOAD_UCODE		(0xDD, 4, LoadUCode.class),
		G_DL				(0xDE, NewDL.class),
		G_ENDDL				(0xDF, NoArg.class),
		G_NOOP_RDP			(0xE0, NoArg.class),
		G_RDPHALF_1			(0xE1), // should not be seen by user
		G_SetOtherMode_L	(0xE2, SetOtherModeL.class),
		G_SetOtherMode_H	(0xE3, SetOtherModeH.class),
		G_TEXRECT			(0xE4, 6, TexRect.class),
		G_TEXRECTFLIP		(0xE5, 6, TexRect.class),
		G_RDPLOADSYNC		(0xE6, NoArg.class),
		G_RDPPIPESYNC		(0xE7, NoArg.class),
		G_RDPTILESYNC		(0xE8, NoArg.class),
		G_RDPFULLSYNC		(0xE9, NoArg.class),
		G_SETKEYGB			(0xEA, SetKeyGB.class),
		G_SETKEYR			(0xEB, SetKeyR.class),
		G_SETCONVERT		(0xEC, SetConvert.class),
		G_SETSCISSOR		(0xED, SetScissor.class),
		G_SETPRIMDEPTH		(0xEE, SetPrimDepth.class),
		G_RDPSetOtherMode	(0xEF), // no binding
		G_LOADTLUT			(0xF0, LoadTLUT.class),
		G_RDPHALF_2			(0xF1), // should not be seen by user
		G_SETTILESIZE		(0xF2, SetTileSize.class),
		G_LOADBLOCK			(0xF3, LoadBlock.class),
		G_LOADTILE			(0xF4, LoadTile.class),
		G_SETTILE			(0xF5, SetTile.class),
		G_FILLRECT			(0xF6, FillRect.class),
		G_SETFILLCOLOR		(0xF7, SetFillColor.class),
		G_SETFOGCOLOR		(0xF8, SetColor.class),
		G_SETBLENDCOLOR		(0xF9, SetColor.class),
		G_SETPRIMCOLOR		(0xFA, SetPrimColor.class),
		G_SETENVCOLOR		(0xFB, SetColor.class),
		G_SETCOMBINE		(0xFC, SetCombine.class),
		G_SETIMG			(0xFD, SetImg.class),
		G_SETZIMG			(0xFE, SetBuffer.class),
		G_SETCIMG			(0xFF, SetBuffer.class);
		// @formatter:on

		public final int opcode;
		public final String opName;
		public final boolean noArgs;

		public final int size; // how many words does this command consist of after its been compiled?

		//	private final Constructor<? extends BaseF3DEX2> emptyConstructor;
		private final Constructor<? extends BaseF3DEX2> abConstructor;
		private final Constructor<? extends BaseF3DEX2> listConstructor;

		private CommandType(int opcode)
		{
			this(opcode, 2);
		}

		private CommandType(int opcode, int words)
		{
			this(opcode, words, BaseF3DEX2.class);
		}

		private CommandType(int opcode, Class<? extends BaseF3DEX2> command)
		{
			this(opcode, 2, command);
		}

		private CommandType(int opcode, int words, Class<? extends BaseF3DEX2> command)
		{
			this.opcode = opcode;
			this.opName = name();
			this.size = words;
			this.noArgs = (command == NoArg.class);

			try {
				//		emptyConstructor = command.getConstructor(CommandType.class);
				abConstructor = command.getConstructor(CommandType.class, Integer[].class); //Integer.TYPE, Integer.TYPE );
				listConstructor = command.getConstructor(CommandType.class, String[].class);
			}
			catch (Exception e) {
				throw new IllegalStateException(e.getClass() + " : " + e.getMessage());
			}
		}

		/*
		public BaseF3DEX2 create()
		{
			try {
				return emptyConstructor.newInstance(this);
			} catch (InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
				throw new IllegalStateException(e.getMessage());
			}
		}
		 */

		public BaseF3DEX2 create(Integer ... args) throws InvalidInputException
		{
			try {
				return abConstructor.newInstance(this, args);
			}
			catch (InstantiationException | IllegalAccessException | IllegalArgumentException e) {
				// programming error
				IllegalStateException illegalState = new IllegalStateException(e.getMessage());
				illegalState.setStackTrace(e.getStackTrace());
				throw illegalState;
			}
			catch (InvocationTargetException e) {
				// user error
				Throwable cause = e.getCause();
				if (cause.getClass().isAssignableFrom(InvalidInputException.class))
					throw new InvalidInputException(cause);
				// programming error
				IllegalStateException illegalState = new IllegalStateException(cause.getMessage());
				illegalState.setStackTrace(cause.getStackTrace());
				throw illegalState;
			}
		}

		public BaseF3DEX2 create(String ... list) throws InvalidInputException
		{
			try {
				return listConstructor.newInstance(this, list);
			}
			catch (InstantiationException | IllegalAccessException | IllegalArgumentException e) {
				// programming error
				IllegalStateException illegalState = new IllegalStateException(e.getMessage());
				illegalState.setStackTrace(e.getStackTrace());
				throw illegalState;
			}
			catch (InvocationTargetException e) {
				// user error
				Throwable cause = e.getCause();
				if (cause.getClass().isAssignableFrom(InvalidInputException.class))
					throw new InvalidInputException(cause);
				// programming error
				IllegalStateException illegalState = new IllegalStateException(cause.getMessage());
				illegalState.setStackTrace(cause.getStackTrace());
				throw illegalState;
			}
		}
	}

	private static HashMap<Integer, CommandType> decodeMap = new HashMap<>();
	private static HashMap<String, CommandType> encodeMap = new HashMap<>();
	static {
		decodeMap = new HashMap<>();
		encodeMap = new HashMap<>();
		for (CommandType cmd : CommandType.values()) {
			decodeMap.put(cmd.opcode, cmd);
			encodeMap.put(cmd.opName.toUpperCase(), cmd);
		}
	}

	public static final Pattern NoArgPattern = Pattern.compile("([\\w_]+)\\s*(?:\\(\\s*\\)|\\[\\s*\\])?");
	public static final Pattern RoughPattern = Pattern.compile("([\\w_]+)\\s*\\[([0-9A-Fa-f]+),\\s*([0-9A-Fa-f]+)\\]");
	public static final Pattern FancyPattern = Pattern.compile("([\\w_]+)\\s*\\((.+)\\)");

	public static BaseF3DEX2 parse(String s) throws InvalidInputException
	{
		Matcher m = NoArgPattern.matcher(s);
		if (m.matches()) {
			CommandType cmd = encodeMap.get(m.group(1).toUpperCase());
			if (cmd == null)
				throw new InvalidInputException("Unknown F3DEX2 command: " + m.group(1));
			if (!cmd.noArgs)
				throw new InvalidInputException(m.group(1) + " is missing a parameter list.");

			return cmd.create(0, 0);
		}

		m = RoughPattern.matcher(s);
		if (m.matches()) {
			CommandType cmd = encodeMap.get(m.group(1).toUpperCase());
			if (cmd == null)
				throw new InvalidInputException("Unknown F3DEX2 command: " + m.group(1));
			if (cmd.noArgs)
				throw new InvalidInputException(m.group(1) + " should not have parameters!");

			int A = (int) Long.parseLong(m.group(2), 16);
			int B = (int) Long.parseLong(m.group(3), 16);
			return cmd.create(A, B);
		}

		m = FancyPattern.matcher(s);
		if (m.matches()) {
			CommandType cmd = encodeMap.get(m.group(1).toUpperCase());
			if (cmd == null)
				throw new InvalidInputException("Unknown F3DEX2 command: " + m.group(1));
			if (cmd.noArgs)
				throw new InvalidInputException(m.group(1) + " should not have parameters!");

			String[] args = m.group(2).trim().split("\\s*,\\s*");
			for (String t : args) {
				if (t.isEmpty())
					throw new InvalidInputException("Empty argument for command: " + m.group(1));
			}
			return cmd.create(args);
		}

		throw new InvalidInputException("Could not parse command: " + s); //TODO((s.length() < 50) ? s : (s.substring(0,50) + " ...")));
	}

	// re-orders E1/F1 to always be after the relevant opcode
	public static List<BaseF3DEX2> readList(ByteBuffer buf, int offset)
	{
		buf.position(offset);

		List<BaseF3DEX2> commandList = new ArrayList<>();
		CommandType type = null;

		int[] args = new int[6];

		do {
			int readPos = buf.position();
			int A = buf.getInt();
			int B = buf.getInt();
			int opcode = (A >> 24) & 0xFF;

			switch (opcode) {
				case 0xE4:
				case 0xE5:
					args[0] = A;
					args[1] = B;
					args[2] = buf.getInt();
					args[3] = buf.getInt();
					args[4] = buf.getInt();
					args[5] = buf.getInt();
					break;

				case 0xE1:
					args[0] = buf.getInt();
					args[1] = buf.getInt();
					args[2] = A;
					args[3] = B;

					opcode = (args[0] >> 24) & 0xFF;
					if (opcode != 0x04 && opcode != 0xDD)
						throw new StarRodException("Unexpected display command at %X: %08X", buf.position() - 8, args[0]);
					break;

				default:
					args[0] = A;
					args[1] = B;
					break;
			}

			type = decodeMap.get(opcode);
			BaseF3DEX2 cmd = null;
			try {
				switch (type.size) {
					case 2:
						cmd = type.create(args[0], args[1]);
						break;
					case 4:
						cmd = type.create(args[0], args[1], args[2], args[3]);
						break;
					case 6:
						cmd = type.create(args[0], args[1], args[2], args[3], args[4], args[5]);
						break;
					default:
						throw new IllegalStateException(String.format("Invalid size for display command %s: %d", type.name(), type.size));
				}
			}
			catch (InvalidInputException e) {
				StarRodException sre = new StarRodException("Invalid display list at %X: %n%s", readPos, e.getMessage());
				sre.setStackTrace(e.getStackTrace());
				throw sre;
			}
			commandList.add(cmd);
		}
		while (type != CommandType.G_ENDDL);

		return commandList;
	}
}
