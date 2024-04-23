package app.config;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.yaml.snakeyaml.Yaml;

import util.Logger;

@Deprecated
public class SplatConfig
{
	public List<File> assetDirectories;

	private List<SplatSegment> segments;

	private static enum SplatSegmentType
	{
		C,
		SEG_DATA,
		SEG_RODATA,
		HASM,
		CODE,
		DATA,
		BIN,
		YAY0,
		VTX,
		CI4,
		CI8,
		I4,
		IA4,
		IA8,
		RGBA16,
		RGBA32,
		PAL,
		UNK
	}

	public static class SplatSegment
	{
		public final int addr;
		public final boolean isOverlay;

		public final String name;
		private final SplatSegmentType type;
		private final String typeName;
		private final List<String> files;

		private final int treeDepth;
		private final SplatSegment parent;
		private final List<SplatSegment> subsegments;

		@SuppressWarnings("unchecked")
		private SplatSegment(SplatSegment parent, Map<String, Object> segmentMap)
		{
			this.parent = parent;
			subsegments = new ArrayList<>();
			treeDepth = parent == null ? 0 : parent.treeDepth + 1;

			addr = getInt(segmentMap, "vram");
			isOverlay = getBool(segmentMap, "overlay");
			name = getString(segmentMap.get("name"));
			typeName = getString(segmentMap.get("type"));
			type = getType(typeName);

			files = (List<String>) segmentMap.get("files");

			Object obj = segmentMap.get("subsegments");
			if (obj != null) {
				for (Object elem : (List<Object>) obj) {
					SplatSegment subsegment = parseNode(this, elem);
					if (subsegment != null) {
						subsegments.add(subsegment);
					}
				}
			}
		}

		private <T> T tryGetIndex(List<T> list, int index)
		{
			if (list.size() <= index)
				return null;
			else
				return list.get(index);
		}

		public SplatSegment(SplatSegment parent, List<Object> segmentList)
		{
			this.parent = parent;
			subsegments = new ArrayList<>();
			treeDepth = parent == null ? 0 : parent.treeDepth + 1;

			String typeName = (String) tryGetIndex(segmentList, 1);
			this.typeName = (typeName == null) ? "" : typeName;
			type = getType(typeName);

			files = new ArrayList<>();

			Object nameObj = tryGetIndex(segmentList, 2);
			this.name = (nameObj == null) ? "" : nameObj.toString();

			if (parent != null && parent.addr != 0) {
				this.addr = parent.addr;
				this.isOverlay = parent.isOverlay;
			}
			else {
				this.addr = 0;
				this.isOverlay = false;
			}
		}
	}

	private static String getString(Object obj)
	{
		if (obj == null)
			return "";

		return (String) obj;
	}

	private static int getInt(Map<String, Object> map, String name)
	{
		Object obj = map.get(name);
		if (obj == null)
			return 0;

		if (obj instanceof Long)
			return (int) (long) (Long) obj;
		else
			return (Integer) obj;
	}

	private static boolean getBool(Map<String, Object> map, String name)
	{
		Object obj = map.get(name);
		if (obj == null)
			return false;

		return (Boolean) obj;
	}

	private static SplatSegmentType getType(String s)
	{
		if (s == null)
			return SplatSegmentType.UNK;

		switch (s.toLowerCase()) {
			case "c":
				return SplatSegmentType.C;
			case ".data":
				return SplatSegmentType.SEG_DATA;
			case ".rodata":
				return SplatSegmentType.SEG_RODATA;
			case "code":
				return SplatSegmentType.CODE;
			case "hasm":
				return SplatSegmentType.HASM;
			case "data":
				return SplatSegmentType.DATA;
			case "bin":
				return SplatSegmentType.BIN;
			case "yay0":
				return SplatSegmentType.YAY0;
			case "vtx":
				return SplatSegmentType.VTX;
			case "ci4":
				return SplatSegmentType.CI4;
			case "ci8":
				return SplatSegmentType.CI8;
			case "i4":
				return SplatSegmentType.I4;
			case "ia4":
				return SplatSegmentType.IA4;
			case "ia8":
				return SplatSegmentType.IA8;
			case "rgba16":
				return SplatSegmentType.RGBA16;
			case "rgba32":
				return SplatSegmentType.RGBA32;
			case "palette":
				return SplatSegmentType.PAL;
			default:
				return SplatSegmentType.UNK;
		}
	}

	@SuppressWarnings("unchecked")
	private static SplatSegment parseNode(SplatSegment parent, Object node)
	{
		SplatSegment segment = null;

		if (node instanceof Map) {
			Map<String, Object> segmentMap = (Map<String, Object>) node;
			segment = new SplatSegment(parent, segmentMap);
		}
		else if (node instanceof List) {
			List<Object> segmentList = (List<Object>) node;
			if (segmentList.size() > 1) {
				if (segmentList.get(0) instanceof Integer) {
					segment = new SplatSegment(parent, segmentList);
				}
				else {
					Logger.logWarning("Unknown segment: " + node.toString());
				}
			}

		}
		else {
			Logger.logWarning("Unknown segment: " + node.toString());
		}

		return segment;
	}

	@SuppressWarnings("unchecked")
	public SplatConfig(File directory, File splatFile) throws IOException
	{
		Map<String, Object> topLevelMap = new Yaml().load(new FileInputStream(splatFile));

		List<String> assetDirNames = (List<String>) topLevelMap.get("asset_stack");

		File assetsDir = new File(directory, "assets");

		assetDirectories = new ArrayList<>();
		for (String dirName : assetDirNames) {
			assetDirectories.add(new File(assetsDir, dirName));
		}

		List<Object> segmentNodes = (List<Object>) topLevelMap.get("segments");
		segments = new ArrayList<>();

		for (Object node : segmentNodes) {
			SplatSegment segment = parseNode(null, node);
		}
	}
}
