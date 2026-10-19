package game.audio;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import game.audio.SfxArchive.Allocation;
import game.audio.SfxArchive.Command;
import game.audio.SfxArchive.Definition;
import game.audio.SfxArchive.Empty;
import game.audio.SfxArchive.Envelope;
import game.audio.SfxArchive.Label;
import game.audio.SfxArchive.Node;
import game.audio.SfxArchive.OneShot;
import game.audio.SfxArchive.Op;
import game.audio.SfxArchive.Routing;
import game.audio.SfxArchive.Sequence;
import game.audio.SfxArchive.Sound;
import game.audio.SfxArchive.SpawnedEffect;
import game.audio.SfxArchive.Track;

public final class SfxValidator
{
	private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_.-]*");

	public static List<String> validate(SfxArchive archive)
	{
		return new Validation(archive).run();
	}

	private static boolean inRange(int value, int min, int max)
	{
		return value >= min && value <= max;
	}

	private static final class Validation
	{
		private final SfxArchive archive;
		private final List<String> errors = new ArrayList<>();
		private final List<String> warnings = new ArrayList<>();
		private final Set<String> envelopeNames = new HashSet<>();
		private File file;

		private Validation(SfxArchive archive)
		{
			this.archive = archive;
		}

		private List<String> run()
		{
			if (archive == null)
				throw new SfxFormatException("SFX archive is null");

			validateEnvelopes();
			validateSounds();

			if (!errors.isEmpty())
				throw new SfxFormatException(String.join("\n", errors));

			return List.copyOf(warnings);
		}

		private void validateEnvelopes()
		{
			for (int i = 0; i < archive.envelopes.size(); i++) {
				Envelope envelope = archive.envelopes.get(i);
				String context = "Envelope #" + i;
				if (envelope == null) {
					error(context + " is null");
					continue;
				}

				file = envelope.file;
				context = "Envelope " + printable(envelope.name);
				validateIdentifier(envelope.name, context + " name");
				if (envelope.name != null && !envelopeNames.add(envelope.name))
					error(context + " is defined more than once");

				validateEnvelopeCommands(envelope, context);
			}
		}

		private void validateEnvelopeCommands(Envelope envelope, String context)
		{
			String problem = EnvelopeProgram.validate(envelope.commands);
			if (problem != null)
				error(context + ": " + problem);
		}

		private void validateSounds()
		{
			Set<Integer> ids = new HashSet<>();
			Map<String, Integer> names = new HashMap<>();

			for (Map.Entry<Integer, Sound> entry : archive.sounds.entrySet()) {
				Integer mapID = entry.getKey();
				Sound sound = entry.getValue();
				if (mapID == null) {
					error("Sound map contains a null ID");
					continue;
				}
				if (sound == null) {
					error(String.format("Sound %04X is null", mapID));
					continue;
				}

				file = sound.file;
				String context = String.format("Sound %04X (%s)", sound.id, printable(sound.name));
				if (mapID != sound.id)
					error(String.format("Sound map key %04X disagrees with its sound ID %04X", mapID, sound.id));
				if (!ids.add(sound.id))
					error(String.format("Sound ID %04X is defined more than once", sound.id));
				if (SfxArchive.slotOf(sound.id) == null)
					error(String.format("Sound ID %04X is not addressable by the engine", sound.id));

				validateIdentifier(sound.name, context + " primary name");
				registerSoundName(names, sound.name, sound.id, "primary name");

				validateSourcePath(sound.source, context);
				validateSound(sound, context);
			}
		}

		private void registerSoundName(Map<String, Integer> names, String name, int id, String kind)
		{
			if (name == null)
				return;
			Integer previous = names.putIfAbsent(name, id);
			if (previous != null)
				error(String.format("Sound %04X %s %s duplicates a name already owned by %04X",
					id, kind, name, previous));
		}

		private void validateSourcePath(String source, String context)
		{
			if (source == null)
				return;
			if (source.isBlank()) {
				error(context + " has an empty source path");
				return;
			}
			if (source.indexOf('\\') >= 0)
				error(context + " source path must use forward slashes: " + source);
			if (source.startsWith("/") || source.matches("^[A-Za-z]:.*"))
				error(context + " source path must be relative: " + source);
			for (String component : source.split("/", -1)) {
				if (component.equals(".."))
					error(context + " source path may not traverse outside the asset root: " + source);
				else if (component.isEmpty() || component.equals("."))
					error(context + " source path is not normalized: " + source);
			}
		}

		private void validateSound(Sound sound, String context)
		{
			if (sound.isEmpty()) {
				if (sound.routing != null)
					error(context + " is empty and must not have routing");
				if (!sound.spawnedEffects.isEmpty())
					error(context + " is empty and must not contain spawned effects");
				return;
			}

			Set<String> spawnedNames = collectSpawnedNames(sound, context);
			validateSpawnCycles(sound, context);

			if (SfxArchive.slotOf(sound.id) == SfxArchive.Slot.DIRECT) {
				if (sound.routing != null)
					error(context + " is in a direct slot and must not have routing");
				if (!sound.spawnedEffects.isEmpty())
					error(context + " is in a direct table and cannot contain spawned effects");
				if (!sound.canInlineOneShot()) {
					error(context + " is in a direct table and must contain exactly one OneShot track");
				}
				else {
					OneShot oneShot = (OneShot) sound.tracks.get(0).definition;
					if (!SfxBinary.isCompactRepresentable(oneShot))
						error(context + " is not exactly representable by four-byte COMPACT mode");
				}
			}
			else if (sound.routing == null) {
				error(context + " is pointer-backed and requires routing");
			}

			validateTrackSet(sound.tracks, sound.routing, context, spawnedNames);

			for (int i = 0; i < sound.spawnedEffects.size(); i++) {
				SpawnedEffect spawned = sound.spawnedEffects.get(i);
				if (spawned == null)
					continue;
				String spawnedContext = context + " spawned effect " + printable(spawned.name);
				if (spawned.routing == null)
					error(spawnedContext + " requires routing");
				validateTrackSet(spawned.tracks, spawned.routing, spawnedContext, spawnedNames);
			}
		}

		private Set<String> collectSpawnedNames(Sound sound, String context)
		{
			Set<String> result = new HashSet<>();
			for (int i = 0; i < sound.spawnedEffects.size(); i++) {
				SpawnedEffect spawned = sound.spawnedEffects.get(i);
				if (spawned == null) {
					error(context + " spawned effect #" + i + " is null");
					continue;
				}
				validateIdentifier(spawned.name, context + " spawned effect name");
				if (spawned.name != null && !result.add(spawned.name))
					error(context + " defines spawned effect " + spawned.name + " more than once");
			}
			return result;
		}

		private void validateSpawnCycles(Sound sound, String context)
		{
			Map<String, SpawnedEffect> effects = new HashMap<>();
			for (SpawnedEffect spawned : sound.spawnedEffects) {
				if (spawned != null && spawned.name != null)
					effects.putIfAbsent(spawned.name, spawned);
			}

			Set<String> complete = new HashSet<>();
			List<String> active = new ArrayList<>();
			for (SpawnedEffect spawned : sound.spawnedEffects) {
				if (spawned != null && spawned.name != null)
					validateSpawnCycles(spawned.name, effects, complete, active, context);
			}
		}

		private void validateSpawnCycles(String name, Map<String, SpawnedEffect> effects,
			Set<String> complete, List<String> active, String context)
		{
			if (complete.contains(name))
				return;

			int cycleStart = active.indexOf(name);
			if (cycleStart >= 0) {
				StringBuilder cycle = new StringBuilder();
				for (int i = cycleStart; i < active.size(); i++) {
					if (cycle.length() > 0)
						cycle.append(" -> ");
					cycle.append(active.get(i));
				}
				cycle.append(" -> ").append(name);
				error(context + " has recursive spawned-effect cycle: " + cycle);
				return;
			}

			SpawnedEffect spawned = effects.get(name);
			if (spawned == null)
				return;

			active.add(name);
			for (String reference : collectSpawnReferences(spawned)) {
				if (effects.containsKey(reference))
					validateSpawnCycles(reference, effects, complete, active, context);
			}
			active.remove(active.size() - 1);
			complete.add(name);
		}

		private List<String> collectSpawnReferences(SpawnedEffect spawned)
		{
			List<String> references = new ArrayList<>();
			for (Track track : spawned.tracks) {
				if (track == null || !(track.definition instanceof Sequence sequence))
					continue;
				for (Node node : sequence.nodes) {
					if (node instanceof Command command && command.op == Op.SPAWN
						&& command.ref != null && !references.contains(command.ref)) {
						references.add(command.ref);
					}
				}
			}
			return references;
		}

		private void validateTrackSet(List<Track> tracks, Routing routing, String context, Set<String> spawnedNames)
		{
			int count = tracks.size();
			if (count != 1 && count != 2 && count != 4 && count != 8) {
				error(context + " must contain exactly 1, 2, 4, or 8 track slots, found " + count);
			}

			boolean anyDefinition = false;
			for (int i = 0; i < tracks.size(); i++) {
				Track track = tracks.get(i);
				if (track == null) {
					error(context + " track slot " + i + " is null");
					continue;
				}
				if (track.slot != i)
					error(context + " track slots must be contiguous and ordered; expected " + i + " but found " + track.slot);
				if (track.definition == null) {
					error(context + " track " + track.slot + " has no definition");
					continue;
				}
				if (!(track.definition instanceof Empty))
					anyDefinition = true;
			}
			if (!tracks.isEmpty() && !anyDefinition)
				error(context + " has only empty tracks; use an empty Sound definition instead");

			validateRouting(routing, tracks, context);
			for (Track track : tracks) {
				if (track != null && track.definition != null)
					validateDefinition(track.definition, context + " track " + track.slot, spawnedNames);
			}
		}

		private void validateRouting(Routing routing, List<Track> tracks, String context)
		{
			if (routing == null)
				return;
			if (routing.allocation == null) {
				error(context + " routing has no allocation mode");
				return;
			}

			range(routing.exclusiveGroup, 0, 3, context + " routing exclusiveGroup");
			if (routing.allocation == Allocation.DYNAMIC) {
				range(routing.maxPlayer, 0, 7, context + " routing maxPlayer");
				range(routing.priority, 0, 3, context + " routing priority");
				if (tracks.size() > 1 && routing.maxPlayer != 7)
					error(context + " uses dynamic polyphony and requires maxPlayer=7");
				for (Track track : tracks) {
					if (track != null && (track.player != null || track.priority != null))
						error(context + " uses dynamic allocation, so track-level player and priority must be omitted");
				}
				return;
			}

			if (tracks.size() <= 1) {
				range(routing.player, 0, 7, context + " routing player");
				range(routing.priority, 0, 3, context + " routing priority");
				for (Track track : tracks) {
					if (track != null && (track.player != null || track.priority != null))
						error(context + " is fixed mono, so player and priority belong on Routing, not Track");
				}
				return;
			}

			boolean foundEmpty = false;
			for (Track track : tracks) {
				if (track == null || track.definition == null)
					continue;
				if (track.definition instanceof Empty) {
					foundEmpty = true;
					if (track.player != null || track.priority != null)
						error(context + " empty fixed-poly track " + track.slot + " must omit player and priority");
				}
				else {
					if (foundEmpty)
						error(context + " fixed-poly empty tracks must be trailing");
					if (track.player == null)
						error(context + " fixed-poly track " + track.slot + " requires player");
					else
						range(track.player, 0, 7, context + " track " + track.slot + " player");
					if (track.priority == null)
						error(context + " fixed-poly track " + track.slot + " requires priority");
					else
						range(track.priority, 0, 3, context + " track " + track.slot + " priority");
				}
			}
		}

		private void validateDefinition(Definition definition, String context, Set<String> spawnedNames)
		{
			if (definition instanceof Empty)
				return;
			if (definition instanceof OneShot oneShot) {
				validateOneShot(oneShot, context);
				return;
			}
			if (definition instanceof Sequence sequence) {
				validateSequence(sequence, context, spawnedNames);
				return;
			}
			error(context + " has an unknown definition type");
		}

		private void validateOneShot(OneShot oneShot, String context)
		{
			range(oneShot.bank, 0, 255, context + " OneShot bank");
			range(oneShot.patch, 0, 255, context + " OneShot patch");
			range(oneShot.volume, 0, 255, context + " OneShot volume");
			range(oneShot.pan, 0, 255, context + " OneShot pan");
			range(oneShot.reverb, 0, 255, context + " OneShot reverb");
			range(oneShot.pitch, 0, 127, context + " OneShot pitch");
			if (!inRange(oneShot.randomPitch, 0, 120) || (oneShot.randomPitch & 7) != 0)
				error(context + " OneShot randomPitch must be a multiple of 8 from 0 through 120");
			if (inRange(oneShot.pan, 128, 255))
				warn(context + " OneShot pan exceeds the normal authored range of 0-127");
		}

		private void validateSequence(Sequence sequence, String context, Set<String> spawnedNames)
		{
			if (sequence.nodes.isEmpty()
				|| !(sequence.nodes.get(0) instanceof Label entryLabel)
				|| !entryLabel.name().equals(Sequence.START_LABEL)) {
				error(context + " Sequence must begin with its implicit start label");
			}
			Set<String> labels = new HashSet<>();
			for (int i = 0; i < sequence.nodes.size(); i++) {
				Node node = sequence.nodes.get(i);
				if (node == null) {
					error(context + " Sequence node " + i + " is null");
					continue;
				}
				if (node instanceof Label label) {
					validateIdentifier(label.name(), context + " label");
					if (label.name() != null && !labels.add(label.name()))
						error(context + " defines label " + label.name() + " more than once");
				}
			}

			boolean loopActive = false;
			for (int i = 0; i < sequence.nodes.size(); i++) {
				Node node = sequence.nodes.get(i);
				if (node instanceof Command command) {
					validateCommand(command, context + " command " + i, labels, spawnedNames);
					if (command.op == Op.START_LOOP) {
						if (loopActive)
							error(context + " command " + i + " starts a nested loop, but the engine has one sequence loop register");
						loopActive = true;
					}
					else if (command.op == Op.END_LOOP) {
						if (!loopActive)
							error(context + " command " + i + " ends a loop before one is started");
						loopActive = false;
					}
				}
			}
			if (loopActive)
				error(context + " has a StartLoop without a matching EndLoop");
		}

		private void validateCommand(Command command, String context, Set<String> labels, Set<String> spawnedNames)
		{
			if (command.op == null) {
				error(context + " has no operation");
				return;
			}

			switch (command.op) {
				case END:
				case WAIT_FOR_END:
				case END_LOOP:
				case WAIT_FOR_RELEASE:
				case STOP:
				case RESTART:
					break;
				case NOP:
					warn(context + " contains behaviorally inert Nop");
					break;
				case DELAY:
					range(command.a, 1, 2167, context + " ticks");
					break;
				case PLAY:
					range(command.a, 0, 87, context + " pitch");
					range(command.b, 0, 127, context + " velocity");
					range(command.c, 0, 16575, context + " length");
					break;
				case SET_VOLUME:
				case SET_REVERB:
				case SET_ENVELOPE:
				case FINE_TUNE:
				case SET_CURRENT_VOLUME:
				case SET_RANDOM_PITCH:
				case SET_RANDOM_VELOCITY:
				case SET_RANDOM_PAN:
				case SET_ALTERNATIVE_VOLUME:
					range(command.a, 0, 255, context + " value");
					break;
				case SET_PAN:
					range(command.a, 0, 255, context + " value");
					if (inRange(command.a, 128, 255))
						warn(context + " pan exceeds the normal authored range of 0-127");
					break;
				case SET_INSTRUMENT:
					range(command.a, 0, 255, context + " bank");
					range(command.b, 0, 255, context + " patch");
					break;
				case COARSE_TUNE:
					range(command.a, -128, 127, context + " semitones");
					break;
				case PITCH_SWEEP:
					range(command.a, 0, 65535, context + " ticks");
					range(command.b, 0, 127, context + " pitch");
					break;
				case START_LOOP:
					range(command.a, 0, 255, context + " count");
					break;
				case VOLUME_RAMP:
					range(command.a, 0, 65535, context + " ticks");
					range(command.b, 0, 255, context + " value");
					break;
				case SET_ALTERNATIVE:
					range(command.a, 1, 3, context + " type");
					validateLabelReference(command.ref, context + " target", labels);
					break;
				case JUMP:
					validateLabelReference(command.ref, context + " target", labels);
					break;
				case SET_PRESS_ENVELOPE:
					validateEnvelopeReference(command.ref, context);
					break;
				case SPAWN:
					validateSpawnReference(command.ref, context, spawnedNames);
					break;
			}
		}

		private void validateLabelReference(String reference, String context, Set<String> labels)
		{
			if (reference == null || reference.isBlank()) {
				error(context + " is missing");
				return;
			}
			validateIdentifier(reference, context);
			if (!labels.contains(reference))
				error(context + " does not exist: " + reference);
		}

		private void validateEnvelopeReference(String reference, String context)
		{
			if (reference == null)
				return; // clearing the custom envelope is meaningful
			validateIdentifier(reference, context + " envelope reference");
			if (!envelopeNames.contains(reference))
				error(context + " references missing envelope " + reference);
		}

		private void validateSpawnReference(String reference, String context, Set<String> spawnedNames)
		{
			if (reference == null || reference.isBlank()) {
				error(context + " spawn reference is missing");
				return;
			}
			validateIdentifier(reference, context + " spawn reference");
			if (!spawnedNames.contains(reference))
				error(context + " references missing spawned effect " + reference);
		}

		private void validateIdentifier(String value, String context)
		{
			if (value == null || !IDENTIFIER.matcher(value).matches())
				error(context + " must match [A-Za-z_][A-Za-z0-9_.-]*: " + printable(value));
		}

		private void range(int value, int min, int max, String context)
		{
			if (!inRange(value, min, max))
				error(context + " must be in " + min + "-" + max + ", found " + value);
		}

		private void error(String message)
		{
			errors.add(file == null ? message : file.toPath().normalize() + ": " + message);
		}

		private void warn(String message)
		{
			warnings.add(message);
		}

		private static String printable(String value)
		{
			return value == null ? "<null>" : value;
		}
	}

	private SfxValidator()
	{}
}
