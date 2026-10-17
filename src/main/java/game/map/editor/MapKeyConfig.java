package game.map.editor;

import common.KeyBinding;
import common.KeyInput;
import common.KeyboardInputConfig;

/** Map Editor shortcuts share bindings with held movement and drawing controls. */
public final class MapKeyConfig extends KeyboardInputConfig
{
	public MapKeyConfig()
	{
		for (EditorShortcut input : EditorShortcut.values()) {
			if (input != EditorShortcut.RESET_OPTIONS)
				addDefault(input);
		}
		finishDefaults();
	}

	public EditorShortcut resolve(int keyCode, int modifiers, boolean playInEditor)
	{
		KeyInput exact = super.resolve(keyCode, modifiers);
		if (exact != null)
			return (EditorShortcut) exact;
		if (playInEditor) {
			// Run's modifier may be held together with a jump/spin binding.
			KeyBinding run = getBinding(EditorShortcut.PLAY_IN_EDITOR_RUN);
			int runModifier = KeyBinding.modifierMask(run.keyCode);
			if (runModifier == 0 || (modifiers & (run.modifiers | runModifier)) != (run.modifiers | runModifier))
				return null;
			KeyInput withoutRun = super.resolve(keyCode, modifiers & ~runModifier);
			if (withoutRun == EditorShortcut.PLAY_IN_EDITOR_JUMP || withoutRun == EditorShortcut.PLAY_IN_EDITOR_SPIN)
				return (EditorShortcut) withoutRun;
		}
		return null;
	}
}
