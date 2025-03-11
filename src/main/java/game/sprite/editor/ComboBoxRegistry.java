package game.sprite.editor;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import javax.swing.DefaultComboBoxModel;

/**
 * This slightly awkward workaround communicates changes in disparate parts of the editor:
 * When raster or palette list models change, any combo box models which derive from them must
 * also be changed, without messing up their current selections or firing spurious events.
 *
 * A supplier for the content of the new models is provided by the SpriteEditor at construction
 * while callbacks for setting selection are provided by each CommandComboBox during registration.
 */
public class ComboBoxRegistry<T>
{
	private static class ComboBoxEntry<T>
	{
		private final CommandComboBox<T> box;
		private final Runnable selectCallback;
		private final boolean needsNull;

		private ComboBoxEntry(CommandComboBox<T> box, boolean needsNull, Runnable selectCallback)
		{
			this.box = box;
			this.selectCallback = selectCallback;
			this.needsNull = needsNull;
		}
	}

	private final Supplier<Iterable<T>> contentSupplier;
	private final List<ComboBoxEntry<T>> entries;

	public ComboBoxRegistry(Supplier<Iterable<T>> contentSupplier)
	{
		this.contentSupplier = contentSupplier;

		entries = new ArrayList<>();
	}

	public void register(CommandComboBox<T> box, boolean needsNull, Runnable selectCallback)
	{
		entries.add(new ComboBoxEntry<T>(box, needsNull, selectCallback));
	}

	public void lockBoxes()
	{
		for (ComboBoxEntry<T> e : entries) {
			e.box.incrementIgnoreChanges();
		}
	}

	public void unlockBoxes()
	{
		for (ComboBoxEntry<T> e : entries) {
			e.box.decrementIgnoreChanges();
		}
	}

	public void updateModels(boolean keepSelection)
	{
		for (ComboBoxEntry<T> e : entries) {
			e.box.incrementIgnoreChanges();

			DefaultComboBoxModel<T> model = new DefaultComboBoxModel<>();
			if (e.needsNull) {
				model.addElement(null);
			}

			Iterable<T> content = contentSupplier.get();
			if (content != null) {
				for (T item : content) {
					model.addElement(item);
				}
			}

			e.box.setModel(model);

			if (keepSelection)
				e.selectCallback.run();

			e.box.decrementIgnoreChanges();
		}
	}
}
