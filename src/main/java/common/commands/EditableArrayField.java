package common.commands;

import java.util.Arrays;
import java.util.function.BiFunction;
import java.util.function.Consumer;

public class EditableArrayField<T>
{
	public static final class StandardBoolName implements BiFunction<Integer, Boolean, String>
	{
		private final String propertyName;
		private final boolean reversed;

		public StandardBoolName(String propertyName)
		{
			this(propertyName, false);
		}

		public StandardBoolName(String propertyName, boolean reversed)
		{
			this.propertyName = propertyName;
			this.reversed = reversed;
		}

		@Override
		public String apply(Integer index, Boolean value)
		{
			if (reversed)
				return (value ? "Disable " : "Enable ") + propertyName + " " + index;
			else
				return (value ? "Enable " : "Disable ") + propertyName + " " + index;
		}
	}

	private Object[] values;

	private String commandName = "Edit Value";
	private BiFunction<Integer, T, String> nameAssigner = null;
	private BiFunction<T, T, Boolean> shouldExec = null;
	private Consumer<Object> setCallback;

	private EditableArrayField(int size)
	{
		values = new Object[size];
	}

	public EditableArrayField(int size, T initialValue)
	{
		values = new Object[size];
		for (int i = 0; i < size; i++)
			values[i] = initialValue;
	}

	public static class EditableArrayFieldFactory<S>
	{
		private EditableArrayField<S> field;

		public static <S> EditableArrayFieldFactory<S> create(int size)
		{
			return new EditableArrayFieldFactory<>(new EditableArrayField<S>(size));
		}

		public static <S> EditableArrayFieldFactory<S> create(int size, S initialValue)
		{
			return new EditableArrayFieldFactory<>(new EditableArrayField<>(size, initialValue));
		}

		private EditableArrayFieldFactory(EditableArrayField<S> editor)
		{
			this.field = editor;
		}

		public EditableArrayFieldFactory<S> setName(String s)
		{
			field.commandName = s;
			return this;
		}

		public EditableArrayFieldFactory<S> setName(BiFunction<Integer, S, String> nameFunction)
		{
			field.nameAssigner = nameFunction;
			return this;
		}

		public EditableArrayFieldFactory<S> setCallback(Consumer<Object> callback)
		{
			field.setCallback = callback;
			return this;
		}

		public EditableArrayFieldFactory<S> setShouldExec(BiFunction<S, S, Boolean> shouldExec)
		{
			field.shouldExec = shouldExec;
			return this;
		}

		public EditableArrayField<S> build()
		{
			return field;
		}
	}

	public int length()
	{
		return values.length;
	}

	@SuppressWarnings("unchecked")
	public T get(int index)
	{
		return (T) values[index];
	}

	public void set(int index, T newValue)
	{
		values[index] = newValue;
		if (setCallback != null)
			setCallback.accept(get(index));
	}

	public void set(int index, EditableArrayField<T> other)
	{
		set(index, other.get(index));
	}

	public AbstractCommand mutator(int index, T newValue)
	{
		return new MutateFieldCommand<>(this, index, newValue);
	}

	private static class MutateFieldCommand<S> extends AbstractCommand
	{
		private final EditableArrayField<S> field;
		private final int index;
		private final S oldValue;
		private final S newValue;

		public MutateFieldCommand(EditableArrayField<S> field, int index, S value)
		{
			super((field.nameAssigner == null) ? field.commandName : field.nameAssigner.apply(index, value));
			this.field = field;
			this.index = index;
			oldValue = field.get(index);
			newValue = value;
		}

		@Override
		public boolean shouldExec()
		{
			if (field.shouldExec != null)
				return field.shouldExec.apply(oldValue, newValue);
			else if (newValue == null)
				return oldValue != null;
			else
				return !newValue.equals(oldValue);
		}

		@Override
		public void exec()
		{
			super.exec();
			field.set(index, newValue);
		}

		@Override
		public void undo()
		{
			super.undo();
			field.set(index, oldValue);
		}
	}

	@Override
	public int hashCode()
	{
		return (values == null) ? 0 : Arrays.hashCode(values);
	}

	@Override
	public boolean equals(Object obj)
	{
		// two fields are equal if their values are equal
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		EditableArrayField<?> other = (EditableArrayField<?>) obj;
		if (values == null) {
			if (other.values != null)
				return false;
		}
		else if (!Arrays.equals(values, other.values))
			return false;
		return true;
	}
}
