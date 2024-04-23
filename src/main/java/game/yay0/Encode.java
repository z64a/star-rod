package game.yay0;

public abstract interface Encode
{
	public void exec(Yay0Encoder encoder);

	public int getEncodeLength();

	public int getBudgetCost();
}
