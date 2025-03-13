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
import game.sprite.editor.animators.command.Wait;
import game.sprite.editor.animators.keyframe.AnimKeyframe;
import game.sprite.editor.animators.keyframe.GotoKey;
import game.sprite.editor.animators.keyframe.Keyframe;
import game.sprite.editor.animators.keyframe.KeyframeAnimator;
import game.sprite.editor.animators.keyframe.LoopKey;
import game.sprite.editor.animators.keyframe.NotifyKey;
import game.sprite.editor.animators.keyframe.ParentKey;

public class ToCommandsConverter
{
	private final CommandAnimator cmdAnim;
	private final KeyframeAnimator kfAnim;
	private final List<AnimCommand> commands;

	private Keyframe current;
	private boolean currentEmpty;

	public ToCommandsConverter(CommandAnimator cmdAnim, KeyframeAnimator kfAnim)
	{
		this.cmdAnim = cmdAnim;
		this.kfAnim = kfAnim;

		commands = new ArrayList<>();
		current = new Keyframe(kfAnim);

		IdentityHashMap<Keyframe, Label> labelMap = new IdentityHashMap<>();
		ArrayList<Goto> gotos = new ArrayList<>();
		ArrayList<Loop> loops = new ArrayList<>();

		for (AnimKeyframe elem : kfAnim.keyframes) {
			if (elem instanceof Keyframe cur) {
				// create a label for each keyframe, we can remove unused ones in a second pass
				Label lbl = new Label(cmdAnim, cur.name);
				labelMap.put(cur, lbl);
				commands.add(lbl);

				// command state is reset after every Wait, so any non-default values must generate new commands
				if (cur.duration > 0) {
					if (cur.dx != 0 || cur.dy != 0 || cur.dz != 0) {
						commands.add(new SetPosition(cmdAnim, (short) 0, (short) cur.dx, (short) cur.dy, (short) cur.dz));
					}
					if (cur.rx != 0 || cur.ry != 0 || cur.rz != 0) {
						commands.add(new SetRotation(cmdAnim, (short) cur.rx, (short) cur.ry, (short) cur.rz));
					}
					if (cur.sx != 100 || cur.sy != 100 || cur.sz != 100) {
						if (cur.sx == cur.sy && cur.sx == cur.sz) {
							commands.add(new SetScale(cmdAnim, (short) 0, (short) cur.sx));
						}
						else {
							if (cur.sx != 100) {
								commands.add(new SetScale(cmdAnim, (short) 1, (short) cur.sx));
							}
							if (cur.sy != 100) {
								commands.add(new SetScale(cmdAnim, (short) 2, (short) cur.sy));
							}
							if (cur.sz != 100) {
								commands.add(new SetScale(cmdAnim, (short) 1, (short) cur.sz));
							}
						}
					}
				}

				if (cur.setImage) {
					commands.add(new SetImage(cmdAnim, cur.img));
				}

				if (cur.setPalette) {
					commands.add(new SetPalette(cmdAnim, cur.pal));
				}

				if (cur.duration > 0) {
					commands.add(new Wait(cmdAnim, (short) cur.duration));
				}
			}
			else if (elem instanceof ParentKey cur) {
				commands.add(new SetParent(cmdAnim, cur.parent));
			}
			else if (elem instanceof NotifyKey cur) {
				commands.add(new SetNotify(cmdAnim, (short) cur.value));
			}
			else if (elem instanceof GotoKey g2k) {
				Goto go2 = new Goto(cmdAnim, g2k.target);
				commands.add(go2);
				gotos.add(go2);
			}
			else if (elem instanceof LoopKey lk) {
				Loop loop = new Loop(cmdAnim, lk.target, lk.count);
				commands.add(loop);
				loops.add(loop);
			}
		}

		for (Goto go2 : gotos) {
			go2.label = labelMap.get(go2.target);
			if (go2.label != null)
				go2.label.inUse = true;
		}

		for (Loop loop : loops) {
			loop.label = labelMap.get(loop.target);
			if (loop.label != null)
				loop.label.inUse = true;
		}

		commands.removeIf((cmd) -> {
			return ((cmd instanceof Label lbl) && !lbl.inUse);
		});
	}

	public List<AnimCommand> getCommands()
	{
		return commands;
	}
}
