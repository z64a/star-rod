package game.map.editor.common;

import java.awt.AWTException;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.PointerInfo;
import java.awt.Robot;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.awt.image.BufferedImage;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import javax.swing.SwingUtilities;

import util.Logger;

public class MouseInput implements MouseListener, MouseWheelListener
{
	private static final double HOLD_INTERVAL = 100.0; // time for a click to become a hold in milliseconds
	private static final double EXTRA_MMB_HOLD_INTERVAL = 50.0; // middle mouse clicks are slower

	private final Component comp;
	private final Cursor hiddenCursor;

	private BlockingQueue<QueuedMouseEvent> mouseEvents = new LinkedBlockingQueue<>();
	private BlockingQueue<MouseWheelEvent> wheelEvents = new LinkedBlockingQueue<>();

	private boolean hasLocation = false;
	private int lastPosX = 0;
	private int lastPosY = 0;

	private int height;

	private int frameDX = 0;
	private int frameDY = 0;
	private int frameDW = 0;

	private Robot robot;
	private boolean grabbed;

	private boolean[] holding = new boolean[4];
	private boolean[] isButtonDown = new boolean[4]; // click or hold

	private double[] lastClick = {
			Double.NEGATIVE_INFINITY,
			Double.NEGATIVE_INFINITY,
			Double.NEGATIVE_INFINITY,
			Double.NEGATIVE_INFINITY
	};

	private enum MouseEventType
	{
		PRESS,
		RELEASE,
		CLICK,
		ENTER,
		EXIT
	}

	private enum MouseButton
	{
		OTHER(0),
		LEFT(1),
		RIGHT(2),
		MIDDLE(3);

		public final int id;

		private MouseButton(int id)
		{
			this.id = id;
		}
	}

	private static class QueuedMouseEvent
	{
		private final double time;
		private final MouseEventType type;
		private final MouseButton button;

		private QueuedMouseEvent(MouseEventType type, MouseEvent data)
		{
			time = System.nanoTime() / 1e6;
			this.type = type;
			switch (data.getButton()) {
				case MouseEvent.BUTTON1:
					button = MouseButton.LEFT;
					break;
				case MouseEvent.BUTTON3:
					button = MouseButton.RIGHT;
					break;
				case MouseEvent.BUTTON2:
					button = MouseButton.MIDDLE;
					break;
				default:
					button = MouseButton.OTHER;
					break;
			}
		}
	}

	public static interface MouseManagerListener
	{
		public default void moveMouse(int frameDX, int frameDY)
		{}

		public default void mouseEnter()
		{}

		public default void mouseExit()
		{}

		public default void clickLMB()
		{}

		public default void releaseLMB()
		{}

		public default void startHoldingLMB()
		{}

		public default void stopHoldingLMB()
		{}

		public default void clickRMB()
		{}

		public default void releaseRMB()
		{}

		public default void startHoldingRMB()
		{}

		public default void stopHoldingRMB()
		{}

		public default void clickMMB()
		{}

		public default void releaseMMB()
		{}

		public default void startHoldingMMB()
		{}

		public default void stopHoldingMMB()
		{}
	}

	@Override
	public void mousePressed(MouseEvent e)
	{
		mouseEvents.add(new QueuedMouseEvent(MouseEventType.PRESS, e));
	}

	@Override
	public void mouseReleased(MouseEvent e)
	{
		mouseEvents.add(new QueuedMouseEvent(MouseEventType.RELEASE, e));
	}

	@Override
	public void mouseClicked(MouseEvent e)
	{
		mouseEvents.add(new QueuedMouseEvent(MouseEventType.CLICK, e));
	}

	@Override
	public void mouseEntered(MouseEvent e)
	{
		mouseEvents.add(new QueuedMouseEvent(MouseEventType.ENTER, e));
	}

	@Override
	public void mouseExited(MouseEvent e)
	{
		mouseEvents.add(new QueuedMouseEvent(MouseEventType.EXIT, e));
	}

	@Override
	public void mouseWheelMoved(MouseWheelEvent e)
	{
		wheelEvents.add(e);
	}

	public MouseInput(Component comp)
	{
		this.comp = comp;
		this.hiddenCursor = comp.getToolkit().createCustomCursor(
			new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB),
			new Point(),
			null);
		try {
			robot = new Robot();
		}
		catch (AWTException e) {
			robot = null;
			Logger.printStackTrace(e);
		}

		comp.addMouseListener(this);
		comp.addMouseWheelListener(this);
	}

	public void setGrabbed(boolean value)
	{
		grabbed = value;
		comp.setCursor(grabbed ? hiddenCursor : Cursor.getDefaultCursor());
	}

	public boolean isGrabbed()
	{ return grabbed; }

	public void update(MouseManagerListener listener, boolean hasFocus)
	{
		height = comp.getHeight();

		PointerInfo pointerInfo = MouseInfo.getPointerInfo();
		if (pointerInfo == null)
			return;

		Point curPos = MouseInfo.getPointerInfo().getLocation();
		SwingUtilities.convertPointFromScreen(curPos, comp);

		boolean hadLocation = hasLocation;
		hasLocation = comp.contains(curPos);

		if (hasLocation) {
			int posX = (int) Math.round(curPos.getX());
			int posY = (int) Math.round(curPos.getY());

			frameDX = (hadLocation && hasLocation) ? posX - lastPosX : 0;
			frameDY = (hadLocation && hasLocation) ? lastPosY - posY : 0; // sense is reversed

			if (hadLocation && grabbed && robot != null) {
				Point loc = comp.getLocationOnScreen();
				robot.mouseMove(loc.x + lastPosX, loc.y + lastPosY);
			}
			else {
				lastPosX = posX;
				lastPosY = posY;
			}
		}
		else {
			int posX = (int) Math.round(curPos.getX());
			int posY = (int) Math.round(curPos.getY());

			frameDX = 0;
			frameDY = 0;

			lastPosX = posX;
			lastPosY = posY;

			grabbed = false;
		}

		if (grabbed) {
			// consume all events while grabbed
			wheelEvents.clear();
			mouseEvents.clear();
			return;
		}

		listener.moveMouse(frameDX, frameDY);

		frameDW = 0;
		while (!wheelEvents.isEmpty()) {
			MouseWheelEvent evt = wheelEvents.poll();
			frameDW -= evt.getUnitsToScroll();
		}

		// each type of event for each button can only occur once per frame
		boolean[] clicked = new boolean[4];
		boolean[] pressed = new boolean[4];
		boolean[] released = new boolean[4];

		if (hasFocus) {
			// handle all events
			while (!mouseEvents.isEmpty()) {
				QueuedMouseEvent evt = mouseEvents.poll();
				int butID = evt.button.id;

				switch (evt.type) {
					case CLICK:
						if (evt.button == MouseButton.OTHER)
							continue;

						if (!clicked[butID]) {
							/*
							switch(evt.button)
							{
							case LEFT:		listener.clickLMB(); break;
							case RIGHT:		listener.clickRMB(); break;
							case MIDDLE:	listener.clickMMB(); break;
							case OTHER:
							}
							lastClick[butID] = currentTime;
							 */
						}
						clicked[butID] = true;
						break;

					case PRESS:
						if (evt.button == MouseButton.OTHER)
							continue;

						if (!pressed[butID]) {
							switch (evt.button) {
								case LEFT:
									listener.clickLMB();
									break;
								case RIGHT:
									listener.clickRMB();
									break;
								case MIDDLE:
									listener.clickMMB();
									break;
								case OTHER:
							}
							lastClick[butID] = evt.time;
							isButtonDown[butID] = true;
						}
						pressed[butID] = true;
						break;

					case RELEASE:
						if (evt.button == MouseButton.OTHER)
							continue;

						if (!released[butID]) {
							if (holding[butID]) {
								switch (evt.button) {
									case LEFT:
										listener.stopHoldingLMB();
										break;
									case RIGHT:
										listener.stopHoldingRMB();
										break;
									case MIDDLE:
										listener.stopHoldingMMB();
										break;
									case OTHER:
								}
								holding[butID] = false;
							}
							else {
								switch (evt.button) {
									case LEFT:
										listener.releaseLMB();
										break;
									case RIGHT:
										listener.releaseRMB();
										break;
									case MIDDLE:
										listener.releaseMMB();
										break;
									case OTHER:
								}
							}
							isButtonDown[butID] = false;
						}
						released[butID] = true;
						break;
					case ENTER:
						listener.mouseEnter();
						break;
					case EXIT:
						listener.mouseExit();
						setGrabbed(false);
						break;
				}
			}

			// poll for mouse hold
			double currentTime = System.nanoTime() / 1e6;
			for (int i = 1; i < 4; i++) {
				double holdInterval = HOLD_INTERVAL;
				if (i == MouseButton.MIDDLE.id)
					holdInterval += EXTRA_MMB_HOLD_INTERVAL;

				if (!holding[i] && isButtonDown[i] && currentTime - lastClick[i] > holdInterval) {
					switch (i) {
						case 1:
							listener.startHoldingLMB();
							break;
						case 2:
							listener.startHoldingRMB();
							break;
						case 3:
							listener.startHoldingMMB();
							break;
					}
					holding[i] = true;
				}
			}
		}
		else // !hasFocus
		{
			while (!mouseEvents.isEmpty()) {
				QueuedMouseEvent evt = mouseEvents.poll();
				switch (evt.type) {
					case ENTER:
						listener.mouseEnter();
						break;
					case EXIT:
						// tab out
						listener.mouseExit();
						setGrabbed(false);
						break;
					default:
						// ignore others
				}
			}

			reset(listener);
		}
	}

	public void reset(MouseManagerListener listener)
	{
		for (int i = 0; i < 4; i++) {
			if (isButtonDown[i]) {
				if (holding[i]) {
					if (i == MouseButton.LEFT.id)
						listener.stopHoldingLMB();
					else if (i == MouseButton.RIGHT.id)
						listener.stopHoldingRMB();
					else if (i == MouseButton.MIDDLE.id)
						listener.stopHoldingMMB();
					holding[i] = false;
				}
				else {
					if (i == MouseButton.LEFT.id)
						listener.releaseLMB();
					else if (i == MouseButton.RIGHT.id)
						listener.releaseRMB();
					else if (i == MouseButton.MIDDLE.id)
						listener.releaseMMB();
				}
				isButtonDown[i] = false;
			}
		}

		setGrabbed(false);
	}

	public boolean isHoldingLMB()
	{ return holding[MouseButton.LEFT.id]; }

	public boolean isHoldingRMB()
	{ return holding[MouseButton.RIGHT.id]; }

	public boolean isHoldingMMB()
	{ return holding[MouseButton.MIDDLE.id]; }

	public boolean hasLocation()
	{
		return hasLocation;
	}

	public int getPosX()
	{ return lastPosX; }

	public int getPosY()
	{ return height - lastPosY; }

	public int getFrameDX()
	{ return frameDX; }

	public int getFrameDY()
	{ return frameDY; }

	public int getFrameDW()
	{ return frameDW; }
}
