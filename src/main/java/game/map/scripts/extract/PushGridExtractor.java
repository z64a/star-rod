package game.map.scripts.extract;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import game.map.marker.GridComponent;
import game.map.marker.GridOccupant;
import game.map.marker.GridOccupant.OccupantType;
import game.map.marker.Marker;
import game.map.marker.Marker.MarkerType;

public class PushGridExtractor
{
	private static final Matcher FullMatcher = Pattern.compile(
		"([ \\t]+)(Call\\(CreatePushBlockGrid, (?!GEN_).+\\)" +
			"(?:\\s*Call\\((?:SetPushBlock|FillPushBlockX|FillPushBlockZ|SetPushBlockFallEffect), .+\\))+)")
		.matcher("");

	private static final Matcher CallMatcher = Pattern.compile("Call\\((.+)\\)").matcher("");

	protected static void findAndReplace(Extractor extractor)
	{
		String workingText = extractor.getFileText();
		StringBuilder out = null;
		FullMatcher.reset(workingText);

		boolean modified = false;
		while (FullMatcher.find()) {
			modified = true;
			if (out == null)
				out = new StringBuilder(extractor.getFileText().length());

			String indent = FullMatcher.group(1).replaceAll("[\r\n]", "");
			String[] lines = FullMatcher.group(2).split("\r?\n");

			StringBuilder newLines = new StringBuilder();
			Marker m = null;
			String genName = "???";

			for (String line : lines) {
				String cleanLine = line.replaceAll("\\s+", "");
				CallMatcher.reset(cleanLine);

				if (!CallMatcher.matches())
					throw new IllegalStateException();

				String[] args = CallMatcher.group(1).split(",");

				GridComponent grid;
				OccupantType type;
				int start, end;
				int x, y, z;

				switch (args[0]) {
					case "CreatePushBlockGrid":
						String markerName = extractor.getNextName("PushBlocks");
						genName = extractor.getGenName(markerName);
						x = Integer.decode(args[4]);
						y = Integer.decode(args[5]);
						z = Integer.decode(args[6]);
						m = new Marker(markerName, MarkerType.BlockGrid, x, y, z, 0);
						extractor.addMarker(m);

						grid = m.gridComponent;
						grid.gridIndex.set(Integer.decode(args[1]));
						grid.gridSizeX.set(Integer.decode(args[2]));
						grid.gridSizeZ.set(Integer.decode(args[3]));
						grid.gridSpacing.set(25);

						newLines.append(String.format("%sCall(CreatePushBlockGrid, %s_GRID_PARAMS)", indent, genName));
						newLines.append(String.format("%n%s%s_GRID_CONTENT", indent, genName));
						break;
					case "SetPushBlock":
						// Call(SetPushBlock, 0, 13, 9, PUSH_GRID_BLOCK)
						if ("PUSH_GRID_BLOCK".equals(args[4]))
							type = OccupantType.Block;
						else
							type = OccupantType.Obstruction;

						x = Integer.decode(args[2]);
						z = Integer.decode(args[3]);

						grid = m.gridComponent;
						grid.gridOccupants.add(new GridOccupant(grid.gridOccupants, x, z, type));
						break;
					case "FillPushBlockX":
						// Call(FillPushBlockX, 0, gridX, startZ, endZ, PUSH_GRID_OBSTRUCTION)
						if ("PUSH_GRID_BLOCK".equals(args[5]))
							type = OccupantType.Block;
						else
							type = OccupantType.Obstruction;

						x = Integer.decode(args[2]);
						start = Integer.decode(args[3]);
						end = Integer.decode(args[4]);

						grid = m.gridComponent;
						for (int i = start; i <= end; i++) {
							grid.gridOccupants.add(new GridOccupant(grid.gridOccupants, x, i, type));
						}
						break;
					case "FillPushBlockZ":
						// Call(FillPushBlockZ, 0, gridZ, startX, endX, PUSH_GRID_OBSTRUCTION)
						if ("PUSH_GRID_BLOCK".equals(args[5]))
							type = OccupantType.Block;
						else
							type = OccupantType.Obstruction;

						z = Integer.decode(args[2]);
						start = Integer.decode(args[3]);
						end = Integer.decode(args[4]);

						grid = m.gridComponent;
						for (int i = start; i <= end; i++) {
							grid.gridOccupants.add(new GridOccupant(grid.gridOccupants, i, z, type));
						}
						break;
					case "SetPushBlockFallEffect":
						m.gridComponent.gridUseGravity.set(true);
						newLines.append("\n").append(line);
						break;
				}
			}

			FullMatcher.appendReplacement(out, newLines.toString());
		}

		if (modified) {
			FullMatcher.appendTail(out);
			extractor.setFileText(out.toString());
		}
	}
}
