package game.sprite.editor;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.swing.DefaultListModel;

public class SpriteAssetCollection<T> implements Iterable<T>
{
	private final LinkedHashMap<String, T> assetMap;
	private final DefaultListModel<T> assetListModel;

	public SpriteAssetCollection()
	{
		this.assetMap = new LinkedHashMap<>();
		this.assetListModel = new DefaultListModel<>();
	}

	public SpriteAssetCollection(Map<String, T> assets)
	{
		this.assetMap = new LinkedHashMap<>(assets);
		this.assetListModel = new DefaultListModel<>();

		for (T asset : assetMap.values()) {
			assetListModel.addElement(asset);
		}
	}

	public void set(Map<String, T> assets)
	{
		clear();

		this.assetMap.putAll(assets);

		for (T asset : assetMap.values()) {
			assetListModel.addElement(asset);
		}
	}

	public void add(String name, T asset)
	{
		if (!assetMap.containsKey(name)) {
			assetMap.put(name, asset);
			assetListModel.addElement(asset);
		}
	}

	public void remove(String name)
	{
		T asset = assetMap.remove(name);
		if (asset != null) {
			assetListModel.removeElement(asset);
		}
	}

	public T get(int index)
	{
		return assetListModel.get(index);
	}

	public T get(String name)
	{
		return assetMap.get(name);
	}

	public boolean containsKey(String name)
	{
		return assetMap.containsKey(name);
	}

	public boolean contains(T asset)
	{
		return assetMap.containsValue(asset);
	}

	public void clear()
	{
		assetMap.clear();
		assetListModel.clear();
	}

	public int size()
	{
		return assetMap.size();
	}

	public DefaultListModel<T> getListModel()
	{
		return assetListModel;
	}

	@Override
	public Iterator<T> iterator()
	{
		return assetMap.values().iterator();
	}
}
