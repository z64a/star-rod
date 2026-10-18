package game.audio;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;

import app.Environment;
import app.input.InputFileException;

/**
 * The ambient sounds and the radio of a project. In papermario-dx, every ambient sequence in the mseq directory is an
 * ambient sound named after its file, except radio_0 to radio_3, which are the stations of the radio; other projects
 * list them in ambient_sounds.xml.
 */
public class AmbientSounds
{
	public static final String DIR = "mseq";
	/** Ambient IDs below the radio's are the vanilla resource slots; IDs above it are added to the INIT resource list. */
	public static final int RADIO_ID = 0x10;
	public static final int RADIO_STATIONS = 4;
	public static final String RADIO_NAME = "radio";

	public static final int MAX_ID = 0xFF;

	public record Sound(String name, int id, String mseq, File source)
	{}

	/**
	 * mseq holds the MSEQ file each radio player plays. The bank is null in papermario-dx, where the build finds it from
	 * the samples the stations play.
	 */
	public record Radio(String name, String bank, String[] mseq, File source)
	{}

	/** In order of ID. */
	public final List<Sound> sounds = new ArrayList<>();
	/** The radio, or null if there is none. */
	public Radio radio;

	AmbientSounds()
	{}

	public static AmbientSounds read(AudioLayers layers)
	{
		return Environment.isDX() ? readFiles(layers) : AudioTables.readAmbient(layers);
	}

	/** The name of the MSEQ file that radio player plays in papermario-dx. */
	public static String radioStation(int player)
	{
		return RADIO_NAME + "_" + player;
	}

	/** papermario-dx's ambient sounds, whose IDs are in the order of their names. */
	private static AmbientSounds readFiles(AudioLayers layers)
	{
		Set<String> names = new TreeSet<>();
		for (String file : layers.listFiles(DIR, ".xml"))
			names.add(file.substring(0, file.length() - ".xml".length()));
		for (String file : layers.listFiles(DIR, Mseq.EXTENSION))
			names.add(file.substring(0, file.length() - Mseq.EXTENSION.length()));

		String[] radio = new String[RADIO_STATIONS];
		int stations = 0;
		for (int player = 0; player < RADIO_STATIONS; player++) {
			if (names.remove(radioStation(player))) {
				radio[player] = radioStation(player) + Mseq.EXTENSION;
				stations++;
			}
		}
		if (stations != 0 && stations != RADIO_STATIONS)
			throw new InputFileException(new File(DIR), "the radio needs all of %s to %s, but some are missing",
				radioStation(0), radioStation(RADIO_STATIONS - 1));

		AmbientSounds ambient = new AmbientSounds();
		if (stations != 0)
			ambient.radio = new Radio(RADIO_NAME, null, radio, new File(DIR));
		int id = 0;
		for (String name : names) {
			File source = source(layers, name);
			if (name.equals(RADIO_NAME))
				throw new InputFileException(source, "%s is the name of the radio; rename the file", RADIO_NAME);
			if (id == RADIO_ID)
				id++;
			if (id > MAX_ID)
				throw new InputFileException(source, "there are too many ambient sounds; IDs go up to %X", MAX_ID);
			ambient.sounds.add(new Sound(AudioNames.fileStem(source), id++, name + Mseq.EXTENSION, source));
		}
		return ambient;
	}

	/**
	 * The bank file the radio loads, such as "SPC3.bk", or null if there is no radio. In papermario-dx, it is the bank
	 * that the stations play their samples from, given each sample's bank.
	 */
	public String radioBank(AudioLayers layers, Function<String, String> bankOfSample)
	{
		if (radio == null)
			return null;
		if (radio.bank() != null)
			return radio.bank();

		Set<String> owners = new TreeSet<>();
		for (String station : radio.mseq()) {
			String name = station.substring(0, station.length() - Mseq.EXTENSION.length());
			File xml = layers.getFile(DIR + "/" + name + ".xml");
			if (xml == null)
				throw new InputFileException(source(layers, name),
					"the radio's stations must be XML files, so the build can find the bank they play");
			for (Mseq.Command c : Mseq.read(xml).commands()) {
				if (c.op() != Mseq.Op.SetInstrument)
					continue;
				String bank = bankOfSample.apply(c.wav());
				if (bank == null)
					throw new InputFileException(xml, "SetInstrument: no bank has a sample named %s", c.wav());
				owners.add(bank);
			}
		}
		if (owners.size() != 1)
			throw new InputFileException(new File(DIR), "the radio's stations must all play samples from one bank, "
				+ "but they play samples from %s", owners.isEmpty() ? "none" : String.join(", ", owners));
		return owners.iterator().next() + ".bk";
	}

	private static File source(AudioLayers layers, String name)
	{
		File xml = layers.getFile(DIR + "/" + name + ".xml");
		return xml != null ? xml : layers.getFile(DIR + "/" + name + Mseq.EXTENSION);
	}
}
