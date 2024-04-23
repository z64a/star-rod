package app.input;

import java.awt.Desktop;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.filefilter.FileFilterUtils;
import org.apache.commons.io.filefilter.IOFileFilter;
import org.apache.commons.io.filefilter.TrueFileFilter;

import app.Directories;
import app.StarRodException;

// unfortunate name, shared with org.apache.commons.io.IOUtils
public class IOUtils
{
	public static Collection<File> getFilesWithExtension(Directories dir, String[] ext, boolean recursive) throws IOException
	{
		return getFilesWithExtension(dir.toFile(), ext, recursive);
	}

	public static Collection<File> getFilesWithExtension(File dir, String ext, boolean recursive) throws IOException
	{
		return getFilesWithExtension(dir, new String[] { ext }, recursive);
	}

	private static Collection<File> getFilesWithExtension(File dir, String[] ext, boolean recursive) throws IOException
	{
		if (!dir.exists())
			throw new IOException("Directory " + dir + " does not exist!");

		for (int i = 0; i < ext.length; i++) {
			if (ext[i].startsWith("."))
				ext[i] = ext[i].substring(1);
		}

		return FileUtils.listFiles(dir, ext, recursive);
	}

	public static File[] getFileWithin(Directories dir, String name, boolean recursive)
	{
		IOFileFilter fileFilter = FileFilterUtils.nameFileFilter(name);
		IOFileFilter directoryFilter = recursive ? TrueFileFilter.INSTANCE : null;

		Collection<File> matches = FileUtils.listFiles(dir.toFile(), fileFilter, directoryFilter);

		File[] matchesArray = new File[matches.size()];
		return matches.toArray(matchesArray);
	}

	public static ByteBuffer getDirectBuffer(File source) throws IOException
	{
		byte[] sourceBytes = FileUtils.readFileToByteArray(source);
		ByteBuffer bb = ByteBuffer.allocateDirect((int) source.length());
		bb.put(sourceBytes);
		bb.flip();
		return bb;
	}

	public static ByteBuffer getDirectBuffer(byte[] array)
	{
		ByteBuffer bb = ByteBuffer.allocateDirect(array.length);
		bb.put(array);
		bb.flip();
		return bb;
	}

	public static String getRelativePath(File dir, File f)
	{
		Path dirPath = Paths.get(dir.getAbsolutePath());
		Path filePath = Paths.get(f.getAbsolutePath());

		String relativePath = dirPath.relativize(filePath).toString();
		return FilenameUtils.separatorsToUnix(relativePath);

		//	return dir.toURI().relativize(f.toURI()).getPath();
	}

	/**
	 * Reads a null terminated ASCII String with a known maximum length from
	 * a RandomAccessFile.The string will only include characters up to the
	 * terminator, but the file position will be advanced to maxLength.
	 */
	public static String readString(RandomAccessFile raf, int maxlength) throws IOException
	{
		StringBuilder sb = new StringBuilder();
		int read = 0;

		for (; read < maxlength; read++) {
			byte b = raf.readByte();

			if (b == (byte) 0)
				break;
			else
				sb.append((char) b);
		}
		// According to docs for RandomAccessFile, no bytes are skipped if this arg <= 0
		raf.skipBytes(maxlength - read - 1);

		return sb.toString();
	}

	/**
	 * Reads a null terminated ASCII String of unknown length from a RandomAccessFile.
	 */
	public static String readString(RandomAccessFile raf) throws IOException
	{
		StringBuilder sb = new StringBuilder();

		while (true) {
			byte b = raf.readByte();

			if (b == (byte) 0)
				break;
			else
				sb.append((char) b);
		}

		return sb.toString();
	}

	/**
	 * Reads a null terminated ASCII String with a known maximum length from
	 * a ByteBuffer.The string will only include characters up to the terminator,
	 * but the ByteBuffer position will be advanced to maxLength.
	 */
	public static String readString(ByteBuffer bb, int maxlength)
	{
		StringBuilder sb = new StringBuilder();
		int read = 0;

		for (; read < maxlength; read++) {
			byte b = bb.get();

			if (b == (byte) 0)
				break;
			else
				sb.append((char) b);
		}

		for (int i = 0; i < maxlength - read - 1; i++)
			if (bb.hasRemaining())
				bb.get();

		return sb.toString();
	}

	/**
	 * Reads a null terminated ASCII String of unknown length from a ByteBuffer.
	 */
	public static String readString(ByteBuffer bb)
	{
		StringBuilder sb = new StringBuilder();

		while (true) {
			byte b = bb.get();

			if (b == (byte) 0)
				break;
			else
				sb.append((char) b);
		}

		return sb.toString();
	}

	/**
	 * Reads a text file into a List of Strings.
	 * Does not modify the lines at all.
	 */
	public static ArrayList<String> readPlainTextFile(File f) throws IOException
	{
		return readPlainTextStream(new FileInputStream(f));
	}

	public static ArrayList<String> readPlainTextStream(InputStream is) throws IOException
	{
		try (BufferedReader in = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
			ArrayList<String> lines = new ArrayList<>();

			String line;
			while ((line = in.readLine()) != null)
				lines.add(line);

			return lines;
		}
	}

	/**
	 * Reads a text file into a List of Lines.
	 * Does not modify the lines at all.
	 */
	public static ArrayList<Line> readPlainInputFile(File f) throws IOException
	{
		return readPlainInputStream(new FileSource(f), new FileInputStream(f));
	}

	public static ArrayList<Line> readPlainInputFile(AbstractSource source, File f) throws IOException
	{
		return readPlainInputStream(source, new FileInputStream(f));
	}

	public static ArrayList<Line> readPlainInputStream(AbstractSource source, InputStream is) throws IOException
	{
		try (BufferedReader in = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
			ArrayList<Line> lines = new ArrayList<>();

			String line;
			int lineNum = 1;
			while ((line = in.readLine()) != null)
				lines.add(new Line(source, lineNum++, line));

			return lines;
		}
	}

	/**
	 * Overloaded variation of {@link IOUtils#readFormattedInputStream(AbstractSource, InputStream, boolean)}
	 * which takes a file, automatically creating the {@link FileSource} and FileInputStream.
	 */
	public static ArrayList<Line> readFormattedInputFile(File inputFile, boolean keepEmptyLines) throws IOException
	{
		return readFormattedInputStream(new FileSource(inputFile), new FileInputStream(inputFile), keepEmptyLines);
	}

	/**
	 * Reads a text file into a List of {@link Line}s. Comments and leading/trailing
	 * whitespace are removed. Empty lines are optionally removed.
	 * {@link Line} line numbers corresponds with line number in the original file,
	 * starting at 1 and including empty lines.
	 */
	public static ArrayList<Line> readFormattedInputStream(AbstractSource source, InputStream is, boolean keepEmptyLines) throws IOException
	{
		try (BufferedReader in = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
			ArrayList<Line> lines = new ArrayList<>();

			String line;
			boolean readingCommentBlock = false;

			int lineNum = 0;
			while ((line = in.readLine()) != null) {
				line = line.trim();
				lineNum++;

				if (readingCommentBlock) {
					int endBlockPos = line.indexOf(EndBlockComment);

					if (endBlockPos < 0) {
						if (keepEmptyLines)
							lines.add(new Line(source, lineNum, ""));
						continue;
					}

					line = line.substring(endBlockPos + 2).trim();
					readingCommentBlock = false;
				}

				CommentMatcher.reset(line);
				line = CommentMatcher.replaceAll("").trim();

				if (!readingCommentBlock) {
					int startBlockPos = line.indexOf(StartBlockComment);
					if (startBlockPos >= 0) {
						line = line.substring(0, startBlockPos).trim();
						readingCommentBlock = true;
					}
				}

				if (!line.isEmpty() || keepEmptyLines) {
					lines.add(new Line(source, lineNum, line));
				}
			}

			return lines;
		}
	}

	/**
	 * Reads a text file into a List of Strings. Comments and leading/trailing
	 * whitespace are removed. Every line in the text file file is added to the
	 * List, including empty lines, which are represented by empty strings.
	 * List index corresponds directly to line number.
	 */
	public static ArrayList<String> readFormattedTextFile(File f) throws IOException
	{
		return readFormattedTextStream(new FileInputStream(f), true);
	}

	public static ArrayList<String> readFormattedTextFile(File f, boolean keepEmptyLines) throws IOException
	{
		return readFormattedTextStream(new FileInputStream(f), keepEmptyLines);
	}

	public static ArrayList<String> readFormattedTextStream(InputStream is) throws IOException
	{
		return readFormattedTextStream(is, true);
	}

	public static final Matcher CommentMatcher = Pattern.compile("/%([\\S\\s]*?)%/|(?<!/)%.*").matcher("");
	public static final String StartBlockComment = "/%";
	public static final String EndBlockComment = "%/";

	public static ArrayList<String> readFormattedTextStream(InputStream is, boolean keepEmptyLines) throws IOException
	{
		try (BufferedReader in = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
			ArrayList<String> lines = new ArrayList<>();

			String line;
			boolean readingCommentBlock = false;

			while ((line = in.readLine()) != null) {
				line = line.trim();

				if (readingCommentBlock) {
					int endBlockPos = line.indexOf(EndBlockComment);

					if (endBlockPos < 0) {
						if (keepEmptyLines)
							lines.add("");
						continue;
					}

					line = line.substring(endBlockPos + 2).trim();
					readingCommentBlock = false;
				}

				CommentMatcher.reset(line);
				line = CommentMatcher.replaceAll("").trim();

				if (!readingCommentBlock) {
					int startBlockPos = line.indexOf(StartBlockComment);
					if (startBlockPos >= 0) {
						line = line.substring(0, startBlockPos).trim();
						readingCommentBlock = true;
					}
				}

				if (!line.isEmpty() || keepEmptyLines) {
					lines.add(line);
				}
			}

			return lines;
		}
	}

	/**
	 * Returns the key-value pair represented by a string of the form 'A = B'.
	 * Throws runtime exceptions for improperly formatted strings.
	 */
	public static String[] getKeyValuePair(File f, String line, int lineNumber)
	{
		if (!line.contains("="))
			throw new InputFileException(f, lineNumber, "Missing assignment: %n%s", line);

		String[] tokens = line.split("\\s*=\\s*");

		if (tokens.length != 2)
			throw new InputFileException(f, lineNumber, "Multiple assignments: %n%s", line);

		return tokens;
	}

	public static HashMap<String, String> readKeyValueFile(File f) throws IOException
	{
		HashMap<String, String> entries = new HashMap<>();
		List<String> lines = IOUtils.readFormattedTextFile(f);
		for (int i = 0; i < lines.size(); i++) {
			String line = lines.get(i);

			if (line.isEmpty())
				continue;

			String[] tokens = IOUtils.getKeyValuePair(f, line, i);
			entries.put(tokens[0].trim(), tokens[1].trim());
		}
		return entries;
	}

	public static PrintWriter getBufferedPrintWriter(String filename) throws FileNotFoundException
	{
		return new PrintWriter(new BufferedWriter(new OutputStreamWriter(
			new FileOutputStream(new File(filename)), StandardCharsets.UTF_8)));
	}

	public static PrintWriter getBufferedPrintWriter(File f) throws FileNotFoundException
	{
		return new PrintWriter(new BufferedWriter(new OutputStreamWriter(
			new FileOutputStream(f), StandardCharsets.UTF_8)));
	}

	public static void writeBufferToFile(ByteBuffer bb, File f) throws IOException
	{
		byte[] bytes = new byte[bb.limit()];
		bb.rewind();
		bb.get(bytes);
		FileUtils.writeByteArrayToFile(f, bytes);
	}

	public static void writeBytesToFile(byte[] bytes, File f) throws IOException
	{
		FileUtils.writeByteArrayToFile(f, bytes);
	}

	public static File touch(Directories dir, String filename)
	{
		return touch(dir + filename);
	}

	public static File touch(String fullname)
	{
		File f = new File(fullname);
		try {
			FileUtils.touch(f);
		}
		catch (IOException e) {
			throw new StarRodException(e);
		}
		return f;
	}

	public static void disposeOrDelete(File f)
	{
		if (Desktop.getDesktop().isSupported(Desktop.Action.MOVE_TO_TRASH))
			Desktop.getDesktop().moveToTrash(f);
		else
			FileUtils.deleteQuietly(f);
	}
}
