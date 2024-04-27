package util;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;

import org.apache.commons.io.FileUtils;

import util.Logger.Message;

public class LogFile implements Logger.Listener, AutoCloseable
{
	private final PrintWriter pw;
	private final File logFile;

	public LogFile(File logFile, boolean append) throws IOException
	{
		FileUtils.touch(logFile);
		this.logFile = logFile;
		OutputStream buffer = new BufferedOutputStream(new FileOutputStream(logFile, append));
		pw = new PrintWriter(buffer);
		Logger.addListener(this);
	}

	public File getFile()
	{
		return logFile;
	}

	@Override
	public void post(Message msg)
	{
		pw.println(msg.text);
		pw.flush();
	}

	@Override
	public void close()
	{
		Logger.removeListener(this);
		pw.close();
	}
}
