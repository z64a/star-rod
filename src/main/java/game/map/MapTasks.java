package game.map;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;

import org.apache.commons.io.FileUtils;

import app.Directories;
import app.Environment;
import assets.AssetExtractor;
import assets.AssetManager;
import game.map.compiler.CollisionCompiler;
import game.map.compiler.GeometryCompiler;

/** The -DumpMaps and -BuildMaps command line tasks. */
public class MapTasks
{
	private static final String[] OUTPUTS = { "_shape.bin", "_shape.h", "_hit.bin", "_hit.h" };

	/** Whether the first command line argument names a map task. */
	public static boolean isMapTask(String[] args)
	{
		return args.length > 0 && switch (args[0].toUpperCase()) {
			case "-DUMPMAPS", "-BUILDMAPS" -> true;
			default -> false;
		};
	}

	/**
	 * Runs the task named by args[index] and returns the number of arguments it consumed.
	 * Exits with a non-zero status if the task fails.
	 */
	public static int run(String[] args, int index)
	{
		try {
			if (args[index].equalsIgnoreCase("-DumpMaps")) {
				AssetExtractor.extractMaps(AssetManager.getBaseAssetDir());
				return 0;
			}

			if (args.length <= index + 2)
				throw new IOException(args[index] + " expects <out dir> <file listing map sources>");
			build(new File(args[index + 1]), new File(args[index + 2]));
			return 2;
		}
		catch (IOException | RuntimeException e) {
			System.err.println(e.getMessage());
			Environment.exit(1);
			return 0;
		}
	}

	/**
	 * Compiles the map sources listed in listFile, one path per line, into outDir as name_shape.bin and
	 * name_hit.bin, with headers giving the index of each model, collider, and zone. Files whose contents are
	 * unchanged are left untouched, and maps.d lists the files that were read.
	 *
	 * A map is skipped if its stamp shows that this build of Star Rod compiled it from the same source since the
	 * source last changed, so one run is quick however many maps are up to date.
	 */
	public static void build(File outDir, File listFile) throws IOException
	{
		String buildInfo = Environment.readBuildInfo();
		FileUtils.forceMkdir(outDir);

		Set<File> inputs = new TreeSet<>();
		Set<File> outputs = new TreeSet<>();
		for (String line : Files.readAllLines(listFile.toPath(), StandardCharsets.UTF_8)) {
			if (line.isBlank())
				continue;

			File source = new File(line.trim());
			if (!source.isFile())
				throw new IOException(source.getPath() + ": file not found");

			String name = Map.deriveName(source);
			File features = Directories.PROJ_SRC.file(Map.getSourcePath(name) + "features.json");
			inputs.add(source);
			if (features.isFile())
				inputs.add(features);
			for (String suffix : OUTPUTS)
				outputs.add(new File(outDir, name + suffix));

			File stamp = new File(outDir, name + ".stamp");
			String stampText = buildInfo + source.getPath() + "\n";
			if (!isUpToDate(stamp, stampText, outDir, name, source, features)) {
				compile(Map.loadMap(source), outDir);
				FileUtils.writeStringToFile(stamp, stampText, StandardCharsets.UTF_8);
			}
		}

		writeDepfile(new File(outDir, "maps.d"), outputs, inputs);
	}

	private static boolean isUpToDate(File stamp, String stampText, File outDir, String name, File source, File features)
		throws IOException
	{
		if (!stamp.isFile() || !FileUtils.readFileToString(stamp, StandardCharsets.UTF_8).equals(stampText))
			return false;
		if (stamp.lastModified() < source.lastModified())
			return false;
		if (features.isFile() && stamp.lastModified() < features.lastModified())
			return false;
		for (String suffix : OUTPUTS) {
			if (!new File(outDir, name + suffix).isFile())
				return false;
		}
		return true;
	}

	private static void compile(Map map, File outDir) throws IOException
	{
		String name = map.getName();
		File tempDir = Files.createTempDirectory(outDir.toPath(), name).toFile();
		try {
			new GeometryCompiler(map, new File(tempDir, name + "_shape.bin"), new File(tempDir, name + "_shape.h"));
			new CollisionCompiler(map, new File(tempDir, name + "_hit.bin"), new File(tempDir, name + "_hit.h"));

			for (File built : tempDir.listFiles())
				moveIfChanged(built, new File(outDir, built.getName()));
		}
		finally {
			FileUtils.deleteQuietly(tempDir);
		}
	}

	private static void moveIfChanged(File from, File to) throws IOException
	{
		if (to.isFile() && Arrays.equals(Files.readAllBytes(from.toPath()), Files.readAllBytes(to.toPath())))
			return;
		Files.move(from.toPath(), to.toPath(), StandardCopyOption.REPLACE_EXISTING);
	}

	private static void writeDepfile(File depfile, Set<File> outputs, Set<File> inputs) throws IOException
	{
		StringBuilder sb = new StringBuilder();
		for (File output : outputs)
			sb.append(escape(output)).append(" ");
		sb.append(":");
		for (File input : inputs)
			sb.append(" \\\n  ").append(escape(input));
		FileUtils.writeStringToFile(depfile, sb.append("\n").toString(), StandardCharsets.UTF_8);
	}

	private static String escape(File file)
	{
		return file.toPath().normalize().toString().replace(" ", "\\ ");
	}
}
