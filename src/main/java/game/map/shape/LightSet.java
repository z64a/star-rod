package game.map.shape;

import static game.map.MapKey.*;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.List;

import org.w3c.dom.Element;

import game.map.editor.commands.AbstractCommand;
import game.map.scripts.LightingPanel;
import util.identity.IdentityArrayList;
import util.xml.XmlWrapper.XmlReader;
import util.xml.XmlWrapper.XmlSerializable;
import util.xml.XmlWrapper.XmlTag;
import util.xml.XmlWrapper.XmlWriter;

public class LightSet implements Iterable<Light>, XmlSerializable
{
	public String name;
	public int[] ambient = new int[3];

	public static final int MAX_LIGHTS = 7;
	private IdentityArrayList<Light> lightList;

	public transient int io_listIndex; // used for serialization
	public transient int c_address; // for compiler

	public LightSet deepCopy()
	{
		LightSet copy = new LightSet();
		copy.name = name;
		copy.ambient[0] = ambient[0];
		copy.ambient[1] = ambient[1];
		copy.ambient[2] = ambient[2];
		copy.lightList = new IdentityArrayList<>(MAX_LIGHTS);
		for (Light light : lightList)
			copy.lightList.add(light.deepCopy(copy));
		return copy;
	}

	public static LightSet read(XmlReader xmr, Element lightsetElem)
	{
		LightSet set = new LightSet();
		set.fromXML(xmr, lightsetElem);
		return set;
	}

	@Override
	public void fromXML(XmlReader xmr, Element lightsetElem)
	{
		name = xmr.getAttribute(lightsetElem, ATTR_LIGHTS_NAME);
		int packed = xmr.readInt(lightsetElem, ATTR_LIGHTS_A);
		ambient[0] = (packed >> 24) & 0xFF;
		ambient[1] = (packed >> 16) & 0xFF;
		ambient[2] = (packed >> 8) & 0xFF;

		List<Element> lightElements = xmr.getTags(lightsetElem, TAG_LIGHT);
		lightList = new IdentityArrayList<>(MAX_LIGHTS);

		for (Element lightElem : lightElements) {
			int data[] = xmr.readHexArray(lightElem, ATTR_LIGHT_V, 3);
			Light light = new Light(this, data);
			lightList.add(light);
		}
	}

	@Override
	public void toXML(XmlWriter xmw)
	{
		XmlTag lightsetTag = xmw.createTag(TAG_LIGHTSET, false);
		xmw.addAttribute(lightsetTag, ATTR_LIGHTS_NAME, name);

		int packed = ((ambient[0] & 0xFF) << 24) | ((ambient[1] & 0xFF) << 16) | ((ambient[2] & 0xFF) << 8);
		xmw.addInt(lightsetTag, ATTR_LIGHTS_A, packed);
		xmw.openTag(lightsetTag);

		for (Light light : lightList) {
			XmlTag lightTag = xmw.createTag(TAG_LIGHT, true);
			xmw.addHexArray(lightTag, ATTR_LIGHT_V, light.getPacked());
			xmw.printTag(lightTag);
		}

		xmw.closeTag(lightsetTag);
	}

	@Override
	public boolean equals(Object o)
	{
		return this == o;
	}

	@Override
	public int hashCode()
	{
		return System.identityHashCode(this);
	}

	// when we want to reduce light sets to remove duplicates, we need a real implementation
	// equals and hashcode. so we put lightsets in a wrapper class and operate on the wrappers.
	public static class LightSetDigest
	{
		public final LightSet lights;

		public LightSetDigest(LightSet lights)
		{
			this.lights = lights;
		}

		@Override
		public boolean equals(Object o)
		{
			if (o == this)
				return true;
			if (o == null)
				return false;
			if (o.getClass() != this.getClass())
				return false;

			LightSetDigest other = (LightSetDigest) o;
			LightSet otherLights = other.lights;

			if (!lights.name.equals(otherLights.name))
				return false;
			if (lights.ambient[0] != otherLights.ambient[0])
				return false;
			if (lights.ambient[1] != otherLights.ambient[1])
				return false;
			if (lights.ambient[2] != otherLights.ambient[2])
				return false;

			if (lights.lightList.size() != otherLights.lightList.size())
				return false;

			for (int i = 0; i < lights.lightList.size(); i++) {
				Light light = lights.lightList.get(i);
				Light otherLight = otherLights.lightList.get(i);
				if (!light.equals(otherLight))
					return false;
			}

			return true;
		}

		@Override
		public int hashCode()
		{
			final int prime = 31;
			int result = 1;
			result = prime * result + lights.ambient[0];
			result = prime * result + lights.ambient[1];
			result = prime * result + lights.ambient[2];
			result = prime * result + ((lights.lightList == null) ? 0 : lights.lightList.hashCode());
			return prime * result + ((lights.name == null) ? 0 : lights.name.hashCode());
		}
	}

	@Override
	public Iterator<Light> iterator()
	{
		return lightList.iterator();
	}

	public int getLightCount()
	{
		return lightList.size();
	}

	public static LightSet createEmptySet()
	{
		LightSet empty = new LightSet();
		empty.lightList = new IdentityArrayList<>(MAX_LIGHTS);
		empty.name = "Lights_None";
		return empty;
	}

	public void get(ByteBuffer bb, int numLights)
	{
		int packed = bb.getInt();
		int duplicate = bb.getInt();

		assert (packed == duplicate);
		assert ((packed & 0xFF) == 0);

		ambient[0] = (packed >> 24) & 0xFF;
		ambient[1] = (packed >> 16) & 0xFF;
		ambient[2] = (packed >> 8) & 0xFF;

		lightList = new IdentityArrayList<>(MAX_LIGHTS);

		for (int i = 0; i < numLights; i++)
			lightList.add(new Light(this, bb));
	}

	public void write(RandomAccessFile raf) throws IOException
	{
		int packed = ((ambient[0] & 0xFF) << 24) | ((ambient[1] & 0xFF) << 16) | ((ambient[2] & 0xFF) << 8);
		raf.writeInt(packed);
		raf.writeInt(packed);

		for (Light light : lightList)
			light.write(raf);

		if (lightList.isEmpty()) {
			raf.writeInt(0);
			raf.writeInt(0);
			raf.writeInt(0);
			raf.writeInt(0);
		}

		// alignment
		raf.writeInt(0);
		raf.writeInt(0);
	}

	@Override
	public String toString()
	{
		return name;
	}

	public void print()
	{
		System.out.println(name + " has " + lightList.size() + " lights:");
		for (Light light : lightList) {
			System.out.printf("%08X %08X %08X%n",
				light.color[0], light.color[1], light.color[2]);
		}
	}

	public static final class SetLightingName extends AbstractCommand
	{
		private final LightSet lightSet;
		private final String oldName;
		private final String newName;

		public SetLightingName(LightSet lightSet, String s)
		{
			super("Set Lighting Name");
			this.lightSet = lightSet;
			oldName = lightSet.name;
			newName = s;
		}

		@Override
		public boolean shouldExec()
		{
			return !(newName.isEmpty() || oldName.equals(newName));
		}

		@Override
		public void exec()
		{
			super.exec();
			lightSet.name = newName;
			LightingPanel.instance().setLights(lightSet);
		}

		@Override
		public void undo()
		{
			super.undo();
			lightSet.name = oldName;
			LightingPanel.instance().setLights(lightSet);
		}
	}

	public static final class SetAmbientChannel extends AbstractCommand
	{
		private final LightSet lightSet;
		private final int index;
		private final int oldValue;
		private final int newValue;

		public SetAmbientChannel(LightSet lightSet, int channel, int val)
		{
			super("Set Ambient Light Color");
			this.lightSet = lightSet;
			index = channel;
			oldValue = lightSet.ambient[index];
			newValue = val;
		}

		@Override
		public boolean shouldExec()
		{
			return oldValue != newValue;
		}

		@Override
		public void exec()
		{
			super.exec();
			lightSet.ambient[index] = newValue;
			LightingPanel.instance().setLights(lightSet);
		}

		@Override
		public void undo()
		{
			super.undo();
			lightSet.ambient[index] = oldValue;
			LightingPanel.instance().setLights(lightSet);
		}
	}

	public static final class SetAmbientColor extends AbstractCommand
	{
		private final LightSet lightSet;
		private final int[] oldValues;
		private final int[] newValues;

		public SetAmbientColor(LightSet lightSet, int R, int G, int B)
		{
			super("Set Ambient Light Color");
			this.lightSet = lightSet;
			oldValues = new int[] { lightSet.ambient[0], lightSet.ambient[1], lightSet.ambient[2] };
			newValues = new int[] { R, G, B };
		}

		@Override
		public boolean shouldExec()
		{
			return oldValues[0] != newValues[0] || oldValues[1] != newValues[1] || oldValues[2] != newValues[2];
		}

		@Override
		public void exec()
		{
			super.exec();
			lightSet.ambient[0] = newValues[0];
			lightSet.ambient[1] = newValues[1];
			lightSet.ambient[2] = newValues[2];
			LightingPanel.instance().setLights(lightSet);
		}

		@Override
		public void undo()
		{
			super.undo();
			lightSet.ambient[0] = oldValues[0];
			lightSet.ambient[1] = oldValues[1];
			lightSet.ambient[2] = oldValues[2];
			LightingPanel.instance().setLights(lightSet);
		}
	}

	public static final class MoveLightUp extends AbstractCommand
	{
		private final LightSet lightSet;
		private final Light light;
		private final int initialPos;

		public MoveLightUp(Light light)
		{
			super("Move Light Up");
			this.lightSet = light.parent;
			this.light = light;
			initialPos = lightSet.lightList.indexOf(light);
		}

		@Override
		public boolean shouldExec()
		{
			return initialPos > 0;
		}

		@Override
		public void exec()
		{
			super.exec();
			lightSet.lightList.remove(light);
			lightSet.lightList.add(initialPos - 1, light);
			LightingPanel.instance().setLights(lightSet);
		}

		@Override
		public void undo()
		{
			super.undo();
			lightSet.lightList.remove(light);
			lightSet.lightList.add(initialPos, light);
			LightingPanel.instance().setLights(lightSet);
		}
	}

	public static final class MoveLightDown extends AbstractCommand
	{
		private final LightSet lightSet;
		private final Light light;
		private final int initialPos;

		public MoveLightDown(Light light)
		{
			super("Move Light Down");
			this.lightSet = light.parent;
			this.light = light;
			initialPos = lightSet.lightList.indexOf(light);
		}

		@Override
		public boolean shouldExec()
		{
			return initialPos < lightSet.lightList.size() - 1;
		}

		@Override
		public void exec()
		{
			super.exec();
			lightSet.lightList.remove(light);
			lightSet.lightList.add(initialPos + 1, light);
			LightingPanel.instance().setLights(lightSet);
		}

		@Override
		public void undo()
		{
			super.undo();
			lightSet.lightList.remove(light);
			lightSet.lightList.add(initialPos, light);
			LightingPanel.instance().setLights(lightSet);
		}
	}

	public static final class CreateLight extends AbstractCommand
	{
		private final LightSet lightSet;
		private final Light light;

		public CreateLight(LightSet lightSet)
		{
			super("Create Light");
			this.lightSet = lightSet;
			this.light = new Light(lightSet);
		}

		@Override
		public boolean shouldExec()
		{
			return lightSet.getLightCount() < MAX_LIGHTS;
		}

		@Override
		public void exec()
		{
			super.exec();
			lightSet.lightList.add(light);
			LightingPanel.instance().setLights(lightSet);
		}

		@Override
		public void undo()
		{
			super.undo();
			lightSet.lightList.remove(light);
			LightingPanel.instance().setLights(lightSet);
		}
	}

	public static final class DeleteLight extends AbstractCommand
	{
		private final LightSet lightSet;
		private final Light light;
		private final int initialPos;

		public DeleteLight(Light light)
		{
			super("Remove Light");
			this.lightSet = light.parent;
			this.light = light;
			initialPos = lightSet.lightList.indexOf(light);
		}

		@Override
		public void exec()
		{
			super.exec();
			lightSet.lightList.remove(light);
			LightingPanel.instance().setLights(lightSet);
		}

		@Override
		public void undo()
		{
			super.undo();
			lightSet.lightList.add(initialPos, light);
			LightingPanel.instance().setLights(lightSet);
		}
	}
}
