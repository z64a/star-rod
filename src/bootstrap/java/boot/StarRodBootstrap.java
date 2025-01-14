package boot;

import java.awt.Desktop;
import java.awt.GraphicsEnvironment;
import java.net.URI;

import javax.swing.JOptionPane;

public class StarRodBootstrap
{
	public static void main(String[] args)
	{
		boolean isCommandLine = args.length > 0 || GraphicsEnvironment.isHeadless();

		String version = System.getProperty("java.version");
		if (getMajorVersion(version) < 17) {
			showDownloadMessage(isCommandLine, version);
			System.exit(-1);
		}

		try {
			launchApp(isCommandLine, args);
		}
		catch (Exception e) {
			System.err.println("Exception during bootstrap: " + e.getMessage());
			e.printStackTrace();
		}
	}

	private static int getMajorVersion(String version)
	{
		String[] parts = version.split("\\.");
		if (parts[0].equals("1")) {
			// Java 8 or earlier: format "1.x"
			return Integer.parseInt(parts[1]);
		}
		else {
			// Java 9 or later: format "x"
			return Integer.parseInt(parts[0]);
		}
	}

	private static void launchApp(boolean isCommandLine, String[] args) throws Exception
	{
		String javaHome = System.getProperty("java.home");
		String javaExec = javaHome + "/bin/java";

		String jarPath = StarRodBootstrap.class
			.getProtectionDomain()
			.getCodeSource()
			.getLocation()
			.toURI()
			.getPath();

		ProcessBuilder processBuilder;
		if (args.length > 0) {
			processBuilder = new ProcessBuilder(
				javaExec,
				"-cp",
				jarPath,
				"app.StarRodMain",
				String.join(" ", args).trim());
		}
		else {
			processBuilder = new ProcessBuilder(
				javaExec,
				"-cp",
				jarPath,
				"app.StarRodMain");
		}

		processBuilder.inheritIO();
		Process process = processBuilder.start();
		process.waitFor();
	}

	private static void showDownloadMessage(boolean isCommandLine, String version)
	{
		String downloadUrl = "https://www.oracle.com/java/technologies/downloads/";

		if (isCommandLine) {
			System.out.println("Java 17 or later is required to run this application.");
			System.out.println("Your current version: " + version);
			System.out.println("Download the latest version from:");
			System.out.println(downloadUrl);
		}
		else {

			String errorMsg = "<html><body>"
				+ "Java 17 or later is required to run this application.<br>"
				+ "Your current version: <b>" + version + "</b><br><br>"
				+ "Download the latest version from:<br>"
				+ "<a href='" + downloadUrl + "'>" + downloadUrl + "</a>"
				+ "</body></html>";

			int result = JOptionPane.showOptionDialog(
				null,
				errorMsg,
				"Java Version Error",
				JOptionPane.DEFAULT_OPTION,
				JOptionPane.ERROR_MESSAGE,
				null,
				new String[] { "Open Link", "Close" },
				"Open Link"
			);

			if (result == 0) {
				openLink(downloadUrl);
			}
		}
	}

	private static void openLink(String url)
	{
		try {
			if (Desktop.isDesktopSupported()) {
				Desktop.getDesktop().browse(new URI(url));
			}
			else {
				System.err.println("Desktop is not supported. Open this link manually: " + url);
			}
		}
		catch (Exception e) {
			System.err.println("Failed to open link: " + e.getMessage());
		}
	}
}
