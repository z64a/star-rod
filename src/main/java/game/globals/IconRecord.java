package game.globals;

import static app.Directories.EXT_PNG;
import static game.globals.IconRecordKey.ATTR_NAME;
import static game.globals.IconRecordKey.TAG_ICON;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;
import javax.swing.Icon;
import javax.swing.ImageIcon;

import org.apache.commons.io.FilenameUtils;
import org.w3c.dom.Element;

import app.IconResource;
import assets.AssetHandle;
import assets.AssetManager;
import assets.AssetSubdir;
import game.globals.editor.GlobalsRecord;
import util.xml.XmlWrapper.XmlReader;

public class IconRecord extends GlobalsRecord
{
	public final String name;
	public final boolean exists;

	public BufferedImage fullsize;
	public Icon largeIcon;
	public Icon smallIcon;

	public IconRecord(AssetHandle source) throws IOException
	{
		String base = FilenameUtils.removeExtension(source.assetPath);
		name = base.substring("/icon/".length());
		exists = source.exists();

		if (exists) {
			BufferedImage bimg = ImageIO.read(source);
			fullsize = bimg;

			if (bimg.getWidth() > 32 || bimg.getHeight() > 32)
				largeIcon = new ImageIcon(resizeImage(bimg, 32));
			else
				largeIcon = new ImageIcon(bimg);

			if (bimg.getWidth() > 16 || bimg.getHeight() > 16)
				smallIcon = new ImageIcon(resizeImage(bimg, 16));
			else
				smallIcon = new ImageIcon(bimg);
		}
		else {
			largeIcon = IconResource.CROSS_24;
			smallIcon = IconResource.CROSS_16;
		}
	}

	private static BufferedImage resizeImage(BufferedImage src, int targetSize)
	{
		if (targetSize <= 0) {
			return src; //this can't be resized
		}
		int targetWidth = targetSize;
		int targetHeight = targetSize;
		float ratio = ((float) src.getHeight() / (float) src.getWidth());
		if (ratio <= 1) { //square or landscape-oriented image
			targetHeight = (int) Math.ceil(targetWidth * ratio);
		}
		else { //portrait image
			targetWidth = Math.round(targetHeight / ratio);
		}
		BufferedImage bi = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g2d = bi.createGraphics();
		g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		g2d.drawImage(src, 0, 0, targetWidth, targetHeight, null);
		g2d.dispose();
		return bi;
	}

	@Override
	public String getIdentifier()
	{
		return name;
	}

	@Override
	public String getFilterableString()
	{
		return name;
	}

	@Override
	public boolean canDeleteFromList()
	{
		return false;
	}

	public static List<IconRecord> readXML(File xmlFile) throws IOException
	{
		List<IconRecord> icons = new ArrayList<>();

		XmlReader xmr = new XmlReader(xmlFile);
		Element rootElem = xmr.getRootElement();

		List<Element> iconElems = xmr.getRequiredTags(rootElem, TAG_ICON);
		for (Element iconElem : iconElems) {
			String path = xmr.getAttribute(iconElem, ATTR_NAME);

			AssetHandle ah = AssetManager.get(AssetSubdir.ICON, path + EXT_PNG);
			icons.add(new IconRecord(ah));
		}

		return icons;
	}
}
