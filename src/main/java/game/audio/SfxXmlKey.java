package game.audio;

import util.xml.XmlKey;

enum SfxXmlKey implements XmlKey
{
	// @formatter:off
	TAG_SOUNDS                     ("Sounds"),
	TAG_SOUND                      ("Sound"),
	TAG_ROUTING                    ("Routing"),
	TAG_ONE_SHOT                   ("OneShot"),
	TAG_EFFECT                     ("SoundEffect"),
	TAG_TRACKS                     ("Tracks"),
	TAG_TRACK                      ("Track"),
	TAG_SEQUENCE                   ("Sequence"),
	TAG_LABEL                      ("Label"),
	TAG_SPAWNED_EFFECTS            ("SpawnedEffects"),
	TAG_SPAWNED_EFFECT             ("SpawnedEffect"),
	TAG_ENVELOPES                  ("Envelopes"),
	TAG_ENVELOPE                   ("Envelope"),
	TAG_START_LOOP                 ("StartLoop"),
	TAG_END_LOOP                   ("EndLoop"),
	TAG_END                        ("End"),
	TAG_DELAY                      ("Delay"),
	TAG_PLAY                       ("Play"),
	TAG_SET_VOLUME                 ("SetVolume"),
	TAG_SET_PAN                    ("SetPan"),
	TAG_SET_INSTRUMENT             ("SetInstrument"),
	TAG_SET_REVERB                 ("SetReverb"),
	TAG_SET_ENVELOPE               ("SetEnvelope"),
	TAG_COARSE_TUNE                ("CoarseTune"),
	TAG_FINE_TUNE                  ("FineTune"),
	TAG_WAIT_FOR_END               ("WaitForEnd"),
	TAG_PITCH_SWEEP                ("PitchSweep"),
	TAG_WAIT_FOR_RELEASE           ("WaitForRelease"),
	TAG_SET_CURRENT_VOLUME         ("SetCurrentVolume"),
	TAG_VOLUME_RAMP                ("VolumeRamp"),
	TAG_SET_ALTERNATIVE            ("SetAlternative"),
	TAG_STOP                       ("Stop"),
	TAG_JUMP                       ("Jump"),
	TAG_RESTART                    ("Restart"),
	TAG_NOP                        ("Nop"),
	TAG_SET_RANDOM_PITCH           ("SetRandomPitch"),
	TAG_SET_RANDOM_VELOCITY        ("SetRandomVelocity"),
	TAG_SET_RANDOM_PAN             ("SetRandomPan"),
	TAG_SET_PRESS_ENVELOPE         ("SetPressEnvelope"),
	TAG_SPAWN                      ("Spawn"),
	TAG_SET_ALTERNATIVE_VOLUME     ("SetAlternativeVolume"),

	ATTR_NAME                      ("name"),
	ATTR_ID                        ("id"),
	ATTR_SRC                       ("src"),
	ATTR_EMPTY                     ("empty"),
	ATTR_DELETE                    ("delete"),
	ATTR_ALLOCATION                ("allocation"),
	ATTR_MAX_PLAYER                ("maxPlayer"),
	ATTR_PLAYER                    ("player"),
	ATTR_PRIORITY                  ("priority"),
	ATTR_EXCLUSIVE_GROUP           ("exclusiveGroup"),
	ATTR_SLOT                      ("slot"),
	ATTR_WAV                       ("wav"),
	ATTR_ENVELOPE                  ("envelope"),
	ATTR_VOLUME                    ("volume"),
	ATTR_PAN                       ("pan"),
	ATTR_REVERB                    ("reverb"),
	ATTR_PITCH                     ("pitch"),
	ATTR_RANDOM_PITCH              ("randomPitch"),
	ATTR_LOCK_VOLUME               ("lockVolume"),
	ATTR_LOCK_PAN                  ("lockPan"),
	ATTR_LOCK_PITCH                ("lockPitch"),
	ATTR_LOCK_REVERB               ("lockReverb"),
	ATTR_VELOCITY                  ("velocity"),
	ATTR_LENGTH                    ("length"),
	ATTR_PRESET                    ("preset"),
	ATTR_SEMITONES                 ("semitones"),
	ATTR_CENTS                     ("cents"),
	ATTR_TICKS                     ("ticks"),
	ATTR_COUNT                     ("count"),
	ATTR_TYPE                      ("type"),
	ATTR_TARGET                    ("target"),
	ATTR_AMOUNT                    ("amount"),
	ATTR_REF                       ("ref");
	// @formatter:on

	private final String key;

	SfxXmlKey(String key)
	{
		this.key = key;
	}

	@Override
	public String toString()
	{
		return key;
	}

	static SfxXmlKey forTag(String name)
	{
		for (SfxXmlKey key : values()) {
			if (key.name().startsWith("TAG_") && key.key.equals(name))
				return key;
		}
		return null;
	}
}
