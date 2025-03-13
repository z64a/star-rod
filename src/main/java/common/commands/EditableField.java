package common.commands;

import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

public class EditableField<T>
{
	public static final class StandardBoolName implements Function<Boolean, String>
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
		public String apply(Boolean value)
		{
			if (reversed)
				return (value ? "Disable " : "Enable ") + propertyName;
			else
				return (value ? "Enable " : "Disable ") + propertyName;
		}
	}

	private boolean enabled;
	private T value;

	private String commandName = "Edit Value";
	private Function<T, String> nameAssigner = null;
	private BiFunction<T, T, Boolean> shouldExec = null;
	private Consumer<Object> setCallback;

	private static boolean enableCallbacks = false;

	public static void setCallbacksEnabled(boolean enabled)
	{
		enableCallbacks = enabled;
	}

	private EditableField(T initialValue)
	{
		value = initialValue;
	}

	public static class EditableFieldFactory<S>
	{
		private EditableField<S> field;

		public static <S> EditableFieldFactory<S> create(S initialValue)
		{
			return new EditableFieldFactory<>(new EditableField<>(initialValue));
		}

		private EditableFieldFactory(EditableField<S> editor)
		{
			this.field = editor;
		}

		public EditableFieldFactory<S> setName(String s)
		{
			field.commandName = s;
			return this;
		}

		public EditableFieldFactory<S> setName(Function<S, String> nameFunction)
		{
			field.nameAssigner = nameFunction;
			return this;
		}

		public EditableFieldFactory<S> setCallback(Consumer<Object> callback)
		{
			field.setCallback = callback;
			return this;
		}

		public EditableFieldFactory<S> setShouldExec(BiFunction<S, S, Boolean> shouldExec)
		{
			field.shouldExec = shouldExec;
			return this;
		}

		public EditableField<S> build()
		{
			return field;
		}
	}

	public T get()
	{
		return value;
	}

	public void set(T newValue)
	{
		set(newValue, true);
	}

	public void setAndEnable(T newValue)
	{
		set(newValue, true);
		enabled = true;
	}

	public void copy(EditableField<T> other)
	{
		set(other.value, true);
		enabled = other.enabled;
	}

	public void set(T newValue, boolean fireCallbacks)
	{
		value = newValue;

		if (fireCallbacks && enableCallbacks && setCallback != null)
			setCallback.accept(value);
	}

	public void fireCallbacks()
	{
		if (enableCallbacks && setCallback != null)
			setCallback.accept(value);
	}

	public AbstractCommand mutator(T newValue)
	{
		return new MutateFieldCommand<>(this, newValue);
	}

	private static class MutateFieldCommand<S> extends AbstractCommand
	{
		private final EditableField<S> field;
		private final S oldValue;
		private final S newValue;

		public MutateFieldCommand(EditableField<S> field, S value)
		{
			super((field.nameAssigner == null) ? field.commandName : field.nameAssigner.apply(value));
			this.field = field;
			oldValue = field.get();
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
			field.set(newValue);
		}

		@Override
		public void undo()
		{
			super.undo();
			field.set(oldValue);
		}
	}

	public boolean isEnabled()
	{
		return enabled;
	}

	public void setEnabled(boolean enabled)
	{
		this.enabled = enabled;
	}

	public AbstractCommand enabler(boolean enabled)
	{
		return new SetFieldEnabledCommand<>(this, enabled);
	}

	private static class SetFieldEnabledCommand<S> extends AbstractCommand
	{
		private final EditableField<S> field;
		private final boolean oldValue;
		private final boolean newValue;

		public SetFieldEnabledCommand(EditableField<S> field, boolean enabled)
		{
			super(enabled ? "Enable Field" : "Disable Field");
			this.field = field;
			oldValue = field.enabled;
			newValue = enabled;
		}

		@Override
		public boolean shouldExec()
		{
			return newValue != oldValue;
		}

		@Override
		public void exec()
		{
			super.exec();
			field.enabled = newValue;
			field.fireCallbacks();
		}

		@Override
		public void undo()
		{
			super.undo();
			field.enabled = oldValue;
			field.fireCallbacks();
		}
	}

	@Override
	public int hashCode()
	{
		return (value == null) ? 0 : this.value.hashCode();
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
		EditableField<?> other = (EditableField<?>) obj;
		if (!Objects.equals(value, other.value)) {
			return false;
		}
		return true;
	}

	@Override
	public String toString()
	{
		return (value == null) ? null : value.toString();
	}
}
