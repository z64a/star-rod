package game.sprite.editor;

import java.util.ArrayList;
import java.util.List;

public interface Editable
{
	default EditableData getEditableData()
	{
		throw new UnsupportedOperationException("getEditableHelper not implemented!");
	}

	/**
	 * Add to a list of all Editables which depend on this one
	 * @param downstream list of Editables
	 */
	public void addEditableDownstream(List<Editable> downstream);

	/**
	 * Check for any reportable errors
	 * @return either <code>null</code> when no error is present
	 * 	       or a brief non-empty description when one is found
	 */
	public String checkErrorMsg();

	/**
	 * Determine whether this Editable or any of its children have been modified
	 * Applies recursively to all Editables downstream from this one
	 * @return modified status
	 */
	default boolean checkModified(long time)
	{
		return getEditableData().checkModified(time);
	}

	/**
	 * Increase modified state for this Editable
	 */
	default void incrementModified()
	{
		getEditableData().incrementModified();
	}

	/**
	 * Decrease modified state for this Editable
	 */
	default void decrementModified()
	{
		getEditableData().decrementModified();
	}

	/**
	 * Clear modified state on this editable and all of its children
	 * Applies recursively to all Editables downstream from this one
	 */
	default void clearModified()
	{
		getEditableData().clearModified();
	}

	/**
	 * Determine whether this Editable or any of its children have errors
	 * Applies recursively to all Editables downstream from this one
	 * @return modified status
	 */
	default boolean isModified()
	{
		return getEditableData().isModified();
	}

	/**
	 * Determine whether this Editable or any of its children have errors
	 * Applies recursively to all Editables downstream from this one
	 * @return error message
	 */
	default String checkErrors(long time)
	{
		return getEditableData().checkErrors(time);
	}

	/**
	 * Report whether this Editable or any of its children have errors
	 * @return error status
	 */
	default boolean hasError()
	{
		return getEditableData().hasError();
	}

	/**
	 * Report error message from this Editable or any of its children
	 * @return error message
	 */
	default String getErrorMsg()
	{
		return getEditableData().getErrorMsg();
	}

	/**
	 * Storage class for this interface, an instance of which must be created in each
	 * subclass which implements it
	 */
	public static class EditableData
	{
		private Editable editable;

		private boolean prevModified;
		private boolean childModified;
		private int thisModified;

		private boolean hadError;
		private String thisError;
		private String childError;

		private long lastCheckError = -1;
		private long lastCheckModified = -1;

		public EditableData(Editable editable)
		{
			this.editable = editable;
			prevModified = false;
		}

		private boolean isModified()
		{
			return (thisModified > 0) || childModified;
		}

		private void incrementModified()
		{
			thisModified++;
			assert (thisModified > 0);
		}

		private void decrementModified()
		{
			assert (thisModified > 0);
			thisModified--;
		}

		private void clearModified()
		{
			thisModified = 0;
			childModified = false;
			prevModified = false;

			// get all downstream editables
			List<Editable> downstream = new ArrayList<>();
			editable.addEditableDownstream(downstream);

			// clear downstream modified status
			for (Editable editable : downstream)
				editable.clearModified();

			notifyEditor(editable);
		}

		private boolean checkModified(long time)
		{
			// only check modified status for this editable once per frame
			if (lastCheckModified == time)
				return isModified();
			lastCheckModified = time;

			// get all downstream editables
			List<Editable> downstream = new ArrayList<>();
			editable.addEditableDownstream(downstream);

			// check whether anything downstream was modified
			childModified = false;
			for (Editable editable : downstream)
				childModified |= editable.checkModified(time);

			boolean modified = isModified();

			// if the state has changes, notify the editor so the UI can be updated
			if (modified != prevModified)
				notifyEditor(editable);

			prevModified = modified;

			return modified;
		}

		private String checkErrors(long time)
		{
			// only check error status for this editable once per frame
			if (lastCheckError == time)
				return getErrorMsg();
			lastCheckError = time;

			// get all downstream editables
			List<Editable> downstream = new ArrayList<>();
			editable.addEditableDownstream(downstream);

			// check whether any children have errors
			childError = null;
			for (Editable editable : downstream) {
				String childResult = editable.checkErrors(time);
				if (childError == null && childResult != null) {
					childError = childResult;
				}
			}

			// determine parent error message
			thisError = editable.checkErrorMsg();

			boolean hasError = hasError();

			// if the state has changes, notify the editor so the UI can be updated
			if (hadError != hasError) {
				notifyEditor(editable);
				SpriteEditor.instance().postEditableError(editable);
			}

			hadError = hasError;

			return getErrorMsg();
		}

		private String getErrorMsg()
		{
			if (thisError != null)
				return thisError;
			else
				return childError;
		}

		private boolean hasError()
		{
			return (thisError != null) || (childError != null);
		}

		private void notifyEditor(Editable editable)
		{
			SpriteEditor.instance().notifyEditableChanged(editable.getClass());
		}
	}
}
