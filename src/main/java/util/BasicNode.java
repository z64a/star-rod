package util;

import java.util.LinkedList;
import java.util.List;

public class BasicNode<T>
{
	public T data;
	public List<BasicNode<T>> children;

	public BasicNode(T data)
	{
		this.data = data;
		children = new LinkedList<>();
	}

	public void addChild(BasicNode<T> childNode)
	{
		children.add(childNode);
	}

	public void addChild(BasicNode<T> childNode, int i)
	{
		children.add(i, childNode);
	}

	public void print()
	{
		print("");
	}

	private void print(String prefix)
	{
		System.out.println(prefix + data.toString());
		for (BasicNode<T> child : children)
			child.print(prefix + "    ");
	}
}
