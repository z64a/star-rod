package game.f3dex2;

import app.input.InvalidInputException;
import game.f3dex2.DisplayList.CommandType;

public class BaseF3DEX2
{
	public final CommandType type;
	public final int opField;
	public int segment = 0x80000000;

	private int A, B;

	public BaseF3DEX2(CommandType cmd, Integer[] args, int numArgs) throws InvalidInputException
	{
		this.type = cmd;
		this.opField = cmd.opcode << 24;

		if (numArgs >= 0 && args.length != numArgs)
			throw new InvalidInputException("%s has incorrect number of arguments: %d (expected %d)", getName(), args.length, numArgs);
	}

	public BaseF3DEX2(CommandType cmd, String[] params, int numParams) throws InvalidInputException
	{
		this.type = cmd;
		this.opField = cmd.opcode << 24;

		if (numParams >= 0 && params.length != numParams)
			throw new InvalidInputException("%s has incorrect number of parameters: %d (expected %d)", getName(), params.length, numParams);
	}

	// these values should be those from [ ... ] -- so the opcodes must be removed
	public BaseF3DEX2(CommandType cmd, Integer ... args) throws InvalidInputException
	{
		this(cmd, args, 2);

		A = args[0];
		B = args[1];
	}

	public BaseF3DEX2(CommandType cmd, String ... params)
	{
		throw new UnsupportedOperationException("Can't parse parameters for " + cmd.name());
	}

	public String getName()
	{
		return type.opName;
	}

	public void setSegment(int segment)
	{
		this.segment = segment;
	}

	// return the final version with opcodes
	public int[] assemble()
	{
		int[] encoded = new int[2];
		encoded[0] = A | opField;
		encoded[1] = B;
		return encoded;
	}

	/*
	// return bit representation for unimplemented commands
	private final int[] pack()
	{
		int[] encoded = new int[2];
		encoded[0] = A & 0x00FFFFFF;
		encoded[1] = B;
		return encoded;
	}
	*/

	public String getString()
	{
		/*
		int[] encoded = pack();
		StringBuilder sb = new StringBuilder(String.format("%-16s [%06X", getName(), encoded[0]));
		for(int i = 1; i < encoded.length; i++)
			sb.append(String.format(", %08X", encoded[i]));
		sb.append("]");
		return sb.toString();
		*/

		return String.format("%-16s [%06X, %08X]", getName(), A & 0x00FFFFFF, B);
	}
}
