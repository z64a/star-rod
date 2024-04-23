package app;

import java.awt.Dimension;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JProgressBar;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;

import net.miginfocom.swing.MigLayout;

public class RomValidator
{
	// properties of the correct ROM: USA v1.0
	private static final int LENGTH = 0x2800000;
	// big endian
	private static final int CRC1_Z64 = 0x65EEE53A;
	private static final int CRC2_Z64 = 0xED7D733C;
	// wordswapped
	private static final int CRC1_V64 = 0xEE653AE5;
	private static final int CRC2_V64 = 0x7DED3C73;
	// byteswapped
	private static final int CRC1_N64 = 0x3AE5EE65;
	private static final int CRC2_N64 = 0x3C737DED;
	// wordswapped + byteswapped
	private static final int CRC1_X64 = 0xE53A65EE;
	private static final int CRC2_X64 = 0x733CED7D;

	private static final String MD5 = "A722F8161FF489943191330BF8416496";

	public static File validateROM(File f) throws IOException
	{
		if (f.length() != LENGTH) {
			SwingUtils.showFramedMessageDialog(null,
				"Selected file is not the correct size.",
				"ROM Validation Error",
				JOptionPane.ERROR_MESSAGE);
			return null;
		}

		JDialog pleaseWait = new JDialog((JDialog) null);
		pleaseWait.setLocationRelativeTo(null);
		pleaseWait.setTitle("Please Wait");
		pleaseWait.setIconImage(Environment.getDefaultIconImage());

		JProgressBar progressBar = new JProgressBar();
		progressBar.setIndeterminate(true);

		pleaseWait.setLayout(new MigLayout("fill"));
		pleaseWait.add(new JLabel("Validating selected ROM..."), "wrap");
		pleaseWait.add(progressBar, "growx");

		pleaseWait.setMinimumSize(new Dimension(240, 80));
		pleaseWait.pack();
		pleaseWait.setResizable(false);
		pleaseWait.setVisible(false);

		RandomAccessFile raf = new RandomAccessFile(f, "r");

		raf.seek(0x10);
		int crc1 = raf.readInt();
		int crc2 = raf.readInt();

		if (crc1 != CRC1_Z64) {
			boolean v64 = (crc1 == CRC1_V64 && crc2 == CRC2_V64);
			boolean n64 = (crc1 == CRC1_N64 && crc2 == CRC2_N64);
			boolean x64 = (crc1 == CRC1_X64 && crc2 == CRC2_X64);

			if (v64 || n64 || x64) {
				// just need to byteswap
				SwingUtils.showFramedMessageDialog(null,
					"Selected ROM has incorrect byte order.\nA corrected copy will be made.",
					"ROM Validation Warning",
					JOptionPane.WARNING_MESSAGE);

				pleaseWait.setVisible(true);

				String path = f.getAbsolutePath();
				path = FilenameUtils.removeExtension(path);
				/*
				String extension = path.substring(path.lastIndexOf("."));
				path = path.substring(0, path.lastIndexOf("."));

				if(extension.equals("n64"))
					swapped = new File(path + ".z64");
				else
					swapped = new File(path + "_swapped" + extension);
				*/

				File swapped = new File(path + "_fixed.z64");

				if (v64)
					wordswap(f, swapped);
				else if (n64)
					byteswap(f, swapped);
				else if (x64)
					fullswap(f, swapped);

				f = swapped;

				raf.close();
				raf = new RandomAccessFile(f, "r");
				raf.seek(0x10);
				crc1 = raf.readInt();
				crc2 = raf.readInt();
			}
			else {
				pleaseWait.setVisible(false);
				SwingUtils.showFramedMessageDialog(null,
					"Incorrect ROM or version, CRC does not match.",
					"ROM Validation Failure",
					JOptionPane.ERROR_MESSAGE);
				raf.close();
				return null;
			}
		}

		pleaseWait.setVisible(true);

		if (crc1 == CRC1_Z64 && crc2 == CRC2_Z64) {
			// now compute the checksum
			if (!verifyCRCs(raf)) {
				pleaseWait.setVisible(false);
				SwingUtils.showFramedMessageDialog(null,
					"ROM data does not match CRC values!",
					"ROM Validation Failure",
					JOptionPane.ERROR_MESSAGE);
				return null;
			}
		}
		else {
			pleaseWait.setVisible(false);
			SwingUtils.showFramedMessageDialog(null,
				"Incorrect ROM or version, CRC does not match.",
				"ROM Validation Failure",
				JOptionPane.ERROR_MESSAGE);
			raf.close();
			return null;
		}

		raf.close();

		try {
			byte[] fileBytes = FileUtils.readFileToByteArray(f);
			MessageDigest digest = MessageDigest.getInstance("MD5");

			digest.update(fileBytes);
			byte[] hashedBytes = digest.digest();

			StringBuilder sb = new StringBuilder(2 * hashedBytes.length);
			for (byte b : hashedBytes)
				sb.append(String.format("%02X", b));

			String fileMD5 = sb.toString();
			if (!fileMD5.equals(MD5)) {
				pleaseWait.setVisible(false);
				SwingUtils.showFramedMessageDialog(null,
					"MD5 hash does not match!",
					"ROM Validation Failure",
					JOptionPane.ERROR_MESSAGE);
				return null;
			}
		}
		catch (NoSuchAlgorithmException e) {
			SwingUtils.showFramedMessageDialog(null,
				"Missing MD5 hash algorithm, could not complete validation!",
				"ROM Validation Warning",
				JOptionPane.WARNING_MESSAGE);
		}

		pleaseWait.setVisible(false);
		pleaseWait.dispose();

		return f;
	}

	// v64 -> z64
	private static void wordswap(File in, File out) throws IOException
	{
		byte[] file = FileUtils.readFileToByteArray(in);
		byte[] swapped = new byte[file.length];

		for (int i = 0; i < file.length; i += 2) {
			swapped[i + 0] = file[i + 1];
			swapped[i + 1] = file[i + 0];
		}

		FileUtils.writeByteArrayToFile(out, swapped);
	}

	// n64 -> z64
	private static void byteswap(File in, File out) throws IOException
	{
		byte[] file = FileUtils.readFileToByteArray(in);
		byte[] swapped = new byte[file.length];

		for (int i = 0; i < file.length; i += 4) {
			swapped[i + 0] = file[i + 3];
			swapped[i + 1] = file[i + 2];
			swapped[i + 2] = file[i + 1];
			swapped[i + 3] = file[i + 0];
		}

		FileUtils.writeByteArrayToFile(out, swapped);
	}

	// x64 -> z64
	private static void fullswap(File in, File out) throws IOException
	{
		byte[] file = FileUtils.readFileToByteArray(in);
		byte[] swapped = new byte[file.length];

		for (int i = 0; i < file.length; i += 4) {
			swapped[i + 0] = file[i + 2];
			swapped[i + 1] = file[i + 3];
			swapped[i + 2] = file[i + 0];
			swapped[i + 3] = file[i + 1];
		}

		FileUtils.writeByteArrayToFile(out, swapped);
	}

	/**
	 * Checks the two CRC values used by the N64 boot chip to verify the integrity of
	 * the ROM (0x10 and 0x14). Paper Mario uses the CIC-NUS-6103 boot chip, so we must
	 * use the corresponding algorithm to calculate the new CRCs. Reproducing the correct
	 * unsigned integer arithmetic is tricky and leads to this ugly code. But it works.
	 * @throws IOException
	 */
	private static boolean verifyCRCs(RandomAccessFile raf) throws IOException
	{
		long t1, t2, t3;
		long t4, t5, t6;
		t1 = t2 = t3 = t4 = t5 = t6 = 0xA3886759;

		long r, d;

		for (int i = 0x00001000; i < 0x00101000; i += 4) {
			raf.seek(i);
			d = raf.readInt() & 0xFFFFFFFFL;
			if (((t6 + d) & 0xFFFFFFFFL) < (t6 & 0xFFFFFFFFL))
				t4++;
			t6 += d;
			t3 ^= d;

			r = ((d << (d & 0x1F)) | (d >> (32L - (d & 0x1F)))) & 0xFFFFFFFFL;

			t5 += r;
			if ((t2 & 0xFFFFFFFFL) > (d & 0xFFFFFFFFL))
				t2 ^= r;
			else
				t2 ^= t6 ^ d;

			t1 += t5 ^ d;
		}

		int crc1 = (int) ((t6 ^ t4) + t3);
		int crc2 = (int) ((t5 ^ t2) + t1);

		return (crc1 == CRC1_Z64 && crc2 == CRC2_Z64);
	}
}
