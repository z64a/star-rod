package game.sprite.editor.animators;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;

import game.sprite.editor.animators.command.AnimCommand;
import game.sprite.editor.animators.command.CommandAnimator;
import game.sprite.editor.animators.command.Goto;
import game.sprite.editor.animators.command.Label;
import game.sprite.editor.animators.command.Loop;
import game.sprite.editor.animators.command.SetImage;
import game.sprite.editor.animators.command.SetNotify;
import game.sprite.editor.animators.command.SetPalette;
import game.sprite.editor.animators.command.SetParent;
import game.sprite.editor.animators.command.SetPosition;
import game.sprite.editor.animators.command.SetRotation;
import game.sprite.editor.animators.command.SetScale;
import game.sprite.editor.animators.command.SetUnknown;
import game.sprite.editor.animators.command.Wait;
import game.sprite.editor.animators.keyframe.AnimKeyframe;
import game.sprite.editor.animators.keyframe.GotoKey;
import game.sprite.editor.animators.keyframe.Keyframe;
import game.sprite.editor.animators.keyframe.KeyframeAnimator;
import game.sprite.editor.animators.keyframe.LoopKey;
import game.sprite.editor.animators.keyframe.NotifyKey;
import game.sprite.editor.animators.keyframe.ParentKey;
import util.Logger;

public class ToKeyframesConverter
{
	private final CommandAnimator cmdAnim;
	private final KeyframeAnimator kfAnim;
	private final List<AnimKeyframe> keyframes;

	private Keyframe current;
	private boolean currentEmpty;
	private int keyframeCount;

	public ToKeyframesConverter(CommandAnimator cmdAnim, KeyframeAnimator kfAnim)
	{
		this.cmdAnim = cmdAnim;
		this.kfAnim = kfAnim;

		keyframes = new ArrayList<>();
		keyframeCount = 0;

		current = new Keyframe(kfAnim);
		current.name = "";

		IdentityHashMap<Label, Keyframe> labelMap = new IdentityHashMap<>();
		ArrayList<GotoKey> gotos = new ArrayList<>();
		ArrayList<LoopKey> loops = new ArrayList<>();

		for (AnimCommand cmd : cmdAnim.commands) {
			if (cmd instanceof Label lbl) {
				if (current.name.isEmpty())
					current.name = lbl.name;
				labelMap.put(lbl, current);
			}
			else if (cmd instanceof Wait wait) {
				current.duration = wait.count;

				// wait command signifies end of current keyframe
				advanceCurrent();
			}
			else if (cmd instanceof Goto go2) {
				if (!currentEmpty) {
					Logger.logError("Incomplete keyframe interrupted by Goto");
					advanceCurrent(); // flush the incomplete keyframe
				}
				GotoKey g2k = new GotoKey(kfAnim, go2.label);
				gotos.add(g2k);
				keyframes.add(g2k);
			}
			else if (cmd instanceof Loop loop) {
				if (!currentEmpty) {
					Logger.logError("Incomplete keyframe interrupted by Loop");
					advanceCurrent(); // flush the incomplete keyframe
				}
				LoopKey lk = new LoopKey(kfAnim, loop.label, loop.count);
				loops.add(lk);
				keyframes.add(lk);
			}
			else if (cmd instanceof SetPosition setPos) {
				current.unknown = setPos.unknown;
				current.dx = setPos.x;
				current.dy = setPos.y;
				current.dz = setPos.z;
				currentEmpty = false;
			}
			else if (cmd instanceof SetRotation setRot) {
				current.rx = setRot.x;
				current.ry = setRot.y;
				current.rz = setRot.z;
				currentEmpty = false;
			}
			else if (cmd instanceof SetScale setScale) {
				switch (setScale.mode) {
					case UNIFORM:
						current.sx = setScale.percent;
						current.sy = setScale.percent;
						current.sz = setScale.percent;
						break;
					case X:
						current.sx = setScale.percent;
						break;
					case Y:
						current.sy = setScale.percent;
						break;
					case Z:
						current.sz = setScale.percent;
						break;
				}
				currentEmpty = false;
			}
			else if (cmd instanceof SetImage setImg) {
				current.img = setImg.img;
				current.setImage = true;
				currentEmpty = false;
			}
			else if (cmd instanceof SetPalette setPal) {
				current.pal = setPal.pal;
				current.setPalette = true;
				currentEmpty = false;
			}
			else if (cmd instanceof SetParent setParent) {
				keyframes.add(new ParentKey(kfAnim, setParent.parent));
			}
			else if (cmd instanceof SetNotify setNotify) {
				keyframes.add(new NotifyKey(kfAnim, setNotify.value));
			}
			else if (cmd instanceof SetUnknown) {
				// unsupported
			}
		}

		if (!currentEmpty) {
			Logger.logError("Animation ends with incomplete keyframe");
			keyframes.add(current);
		}

		for (GotoKey go2 : gotos) {
			go2.target = labelMap.get(go2.label);
		}

		for (LoopKey loop : loops) {
			loop.target = labelMap.get(loop.label);
		}
	}

	private void advanceCurrent()
	{
		keyframes.add(current);

		keyframeCount++;
		if (current.name.isEmpty()) {
			current.name = "Keyframe " + keyframeCount;
		}

		current = new Keyframe(kfAnim);
		current.name = "";
		currentEmpty = true;
	}

	public List<AnimKeyframe> getKeyframes()
	{
		return keyframes;
	}
}
