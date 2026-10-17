package game.audio;

public final class EnvelopeCommand
{
	public EnvelopeOp op;
	public int value;
	public int durationIndex;

	public EnvelopeCommand(EnvelopeOp op)
	{
		this.op = op;
	}

	public EnvelopeCommand(EnvelopeOp op, int value)
	{
		this(op);
		this.value = value;
	}
}
