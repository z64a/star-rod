package game.map.editor;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.Color;
import java.awt.event.KeyEvent;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import javax.swing.JButton;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Test;

import common.commands.AbstractCommand;
import common.commands.CommandManager;

class PaintManagerTest
{
	@Test
	void typedColorPreviewsImmediatelyAndPastedValuesAreClamped() throws Exception
	{
		SwingUtilities.invokeAndWait(() -> {
			List<int[]> updates = new ArrayList<>();
			Object slider = slider(255, 200, updates);
			JTextField text = GeometryTestSupport.field(slider, "textField");
			text.setText("100");
			for (var listener : text.getKeyListeners())
				listener.keyReleased(new KeyEvent(text, KeyEvent.KEY_RELEASED, 0, 0, KeyEvent.VK_0, '0'));
			assertEquals(100, invoke(slider, "getValue"));
			assertEquals(200, invoke(slider, "getCommittedValue"));
			assertArrayEquals(new int[] { 1, 200, 100 }, updates.get(0));
			text.setText("9999");
			text.postActionEvent();
			assertEquals(255, invoke(slider, "getValue"));
			assertEquals(255, invoke(slider, "getCommittedValue"));
			assertEquals("255", text.getText());
			assertArrayEquals(new int[] { 0, 200, 255 }, updates.get(1));
		});
	}

	@Test
	void redHueEndpointRemainsStableAcrossRepeatedPaintStrokes() throws Exception
	{
		SwingUtilities.invokeAndWait(() -> {
			Object hue = slider(3600, 3600, new ArrayList<>());
			for (int i = 0; i < 4; i++) {
				int result = (int) invokeStatic("getNewHue", hue, 0, 1);
				assertEquals(3600, result);
				int[] rgb = (int[]) invokeStatic("HSLtoRGB", new int[] { result, 1000, 500 });
				assertArrayEquals(new int[] { 255, 0, 0 }, rgb);
			}
		});
	}

	@Test
	void colorUndoRestoresExactHueEvenWhenBothEndpointsAreRed() throws Exception
	{
		SwingUtilities.invokeAndWait(() -> {
			Object oldPanel = staticField("paintVertexTab");
			try {
				Object panel = construct(nested("PaintVertexPanel"), (Consumer<JButton>) button -> {});
				setStaticField("paintVertexTab", panel);
				Object hsl = nested("ColorModel").getEnumConstants()[1];
				invoke(panel, "applyColorModel", hsl);
				invoke(panel, "setSelectedColor", Color.RED);
				Object hue = GeometryTestSupport.field(panel, "channelH");
				invoke(hue, "setValue", 3600);
				int[] oldValues = (int[]) invoke(panel, "getColorValues");
				invoke(hue, "setValue", 0);
				int[] newValues = (int[]) invoke(panel, "getColorValues");
				AbstractCommand command = (AbstractCommand) construct(nested("SetPaintColor"), oldValues, newValues, Color.RED, Color.RED);
				assertTrue(command.shouldExec());
				assertFalse(command.modifiesData());
				CommandManager manager = new CommandManager(10, () -> fail("Paint settings must not dirty the map"));
				manager.executeCommand(command);
				assertEquals(0, invoke(hue, "getValue"));
				manager.action_Undo();
				assertEquals(3600, invoke(hue, "getValue"));
				assertEquals(Color.RED, PaintManager.getSelectedColor());
				manager.action_Redo();
				assertEquals(0, invoke(hue, "getValue"));
				manager.action_Undo();
				// Switching models also crosses the red seam; undo must restore the exact HSL controls.
				Object rgb = nested("ColorModel").getEnumConstants()[0];
				invoke(panel, "applyColorModel", rgb);
				Class<?> commandType = java.util.Arrays.stream(panel.getClass().getDeclaredClasses())
					.filter(type -> type.getSimpleName().equals("SetPaintColorModel")).findFirst().orElseThrow();
				AbstractCommand modelCommand = (AbstractCommand) construct(commandType, panel, hsl, rgb,
					oldValues, invoke(panel, "getColorValues"));
				manager.executeCommand(modelCommand);
				manager.action_Undo();
				assertArrayEquals(oldValues, (int[]) invoke(panel, "getColorValues"));
				assertEquals(3600, invoke(hue, "getValue"));
			}
			finally {
				setStaticField("paintVertexTab", oldPanel);
			}
		});
	}

	private static Object slider(int max, int initial, List<int[]> updates)
	{
		Class<?> listenerType = nested("SliderListener");
		Object listener = Proxy.newProxyInstance(listenerType.getClassLoader(), new Class<?>[] { listenerType }, (proxy, method, args) -> {
			updates.add(new int[] { (boolean) args[1] ? 1 : 0, (int) args[2], (int) args[3] });
			return null;
		});
		return construct(nested("PaintSlider"), "Test", "", listener, 0, max, initial, 100, true);
	}

	private static Class<?> nested(String name)
	{
		for (Class<?> type : PaintManager.class.getDeclaredClasses())
			if (type.getSimpleName().equals(name))
				return type;
		throw new AssertionError(name);
	}

	private static Object construct(Class<?> type, Object ... args)
	{
		try {
			for (Constructor<?> constructor : type.getDeclaredConstructors()) {
				if (constructor.getParameterCount() == args.length) {
					constructor.setAccessible(true);
					return constructor.newInstance(args);
				}
			}
			throw new AssertionError(type);
		}
		catch (ReflectiveOperationException e) {
			throw new AssertionError(e);
		}
	}

	private static Object invoke(Object target, String name, Object ... args)
	{
		return invoke(target.getClass(), target, name, args);
	}

	private static Object invokeStatic(String name, Object ... args)
	{
		return invoke(PaintManager.class, null, name, args);
	}

	private static Object invoke(Class<?> type, Object target, String name, Object ... args)
	{
		try {
			for (Method method : type.getDeclaredMethods()) {
				if (method.getName().equals(name) && method.getParameterCount() == args.length) {
					method.setAccessible(true);
					return method.invoke(target, args);
				}
			}
			throw new AssertionError(name);
		}
		catch (ReflectiveOperationException e) {
			throw new AssertionError(e);
		}
	}

	private static Object staticField(String name)
	{
		try {
			Field field = PaintManager.class.getDeclaredField(name);
			field.setAccessible(true);
			return field.get(null);
		}
		catch (ReflectiveOperationException e) {
			throw new AssertionError(e);
		}
	}

	private static void setStaticField(String name, Object value)
	{
		try {
			Field field = PaintManager.class.getDeclaredField(name);
			field.setAccessible(true);
			field.set(null, value);
		}
		catch (ReflectiveOperationException e) {
			throw new AssertionError(e);
		}
	}
}
