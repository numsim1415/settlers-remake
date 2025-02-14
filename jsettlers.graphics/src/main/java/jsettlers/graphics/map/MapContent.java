/*******************************************************************************
 * Copyright (c) 2015 - 2017
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"),
 * to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
 * DEALINGS IN THE SOFTWARE.
 *******************************************************************************/
package jsettlers.graphics.map;

import java.util.BitSet;

import go.graphics.EPrimitiveType;
import java8.util.Optional;

import java.util.Set;
import go.graphics.GLDrawContext;
import go.graphics.UIPoint;
import go.graphics.UnifiedDrawHandle;
import go.graphics.event.GOEvent;
import go.graphics.event.GOEventHandler;
import go.graphics.event.GOKeyEvent;
import go.graphics.event.GOModalEventHandler;
import go.graphics.event.command.EModifier;
import go.graphics.event.command.GOCommandEvent;
import go.graphics.event.mouse.GODrawEvent;
import go.graphics.event.mouse.GOHoverEvent;
import go.graphics.event.mouse.GOPanEvent;
import go.graphics.event.mouse.GOZoomEvent;
import go.graphics.region.RegionContent;
import go.graphics.sound.SoundPlayer;
import go.graphics.text.EFontSize;
import go.graphics.text.TextDrawer;

import jsettlers.common.Color;
import jsettlers.common.CommitInfo;
import jsettlers.common.CommonConstants;
import jsettlers.common.action.Action;
import jsettlers.common.action.EActionType;
import jsettlers.common.action.IAction;
import jsettlers.common.action.PointAction;
import jsettlers.common.action.ScreenChangeAction;
import jsettlers.common.action.SelectAreaAction;
import jsettlers.common.action.ShowConstructionMarksAction;
import jsettlers.common.buildings.BuildingVariant;
import jsettlers.common.buildings.EBuildingType;
import jsettlers.common.images.AnimationSequence;
import jsettlers.common.images.EImageLinkType;
import jsettlers.common.images.ImageLink;
import jsettlers.common.images.OriginalImageLink;
import jsettlers.common.map.EDebugColorModes;
import jsettlers.common.map.IGraphicsGrid;
import jsettlers.common.map.IDirectGridProvider;
import jsettlers.common.map.shapes.IMapArea;
import jsettlers.common.map.shapes.MapRectangle;
import jsettlers.common.mapobject.EMapObjectType;
import jsettlers.common.mapobject.IMapObject;
import jsettlers.common.menu.IMapInterfaceListener;
import jsettlers.common.menu.IStartedGame;
import jsettlers.common.menu.UIState;
import jsettlers.common.action.EMoveToType;
import jsettlers.common.action.MoveToAction;
import jsettlers.common.menu.messages.IMessage;
import jsettlers.common.movable.IGraphicsMovable;
import jsettlers.common.player.IPlayer;
import jsettlers.common.position.FloatRectangle;
import jsettlers.common.position.ShortPoint2D;
import jsettlers.common.selectable.ISelectionSet;
import go.graphics.FramerateComputer;
import jsettlers.common.statistics.IGameTimeProvider;
import jsettlers.common.player.IInGamePlayer;
import jsettlers.common.player.EWinState;
import jsettlers.common.statistics.IntervalTimeRateCalculator;
import jsettlers.graphics.action.ActionFireable;
import jsettlers.graphics.action.ActionHandler;
import jsettlers.graphics.action.ActionThreadBlockingListener;
import jsettlers.graphics.font.FontDrawerFactory;
import jsettlers.graphics.localization.Labels;
import jsettlers.graphics.map.controls.IControls;
import jsettlers.graphics.map.controls.original.OriginalControls;
import jsettlers.graphics.map.draw.Background;
import jsettlers.graphics.map.draw.ImageProvider;
import jsettlers.graphics.map.draw.MapObjectDrawer;
import jsettlers.graphics.messages.Messenger;
import jsettlers.graphics.sound.BackgroundSound;
import jsettlers.graphics.sound.MusicManager;
import jsettlers.graphics.sound.SoundManager;

/**
 * This is the main map content class. It manages the map drawing on the screen region.
 * <p>
 * <h1>The drawing process</h1> The map is drawn in three steps. At first, the background is drawn. After that, it is overlayed with the images for settlers, and other map objects. Then the interface
 * is drawn above everything else.
 * <p>
 * The objects and background are drawn with the map draw context.
 * <p>
 * UI structure:
 * <ul>
 * <li>left minimap decoration</li>
 * <li>right minimap decoration</li>
 * <li>main background
 * <ul>
 * <li>tabs
 * <ol>
 * <li>building tabs</li>
 * <li>goods tabs</li>
 * <li>settlers tabs</li>
 * <li>game tab</li>
 * </ol>
 * </li>
 * </ul>
 * </li>
 * </ul>
 *
 * @author michael
 */
public final class MapContent implements RegionContent, IMapInterfaceListener, ActionFireable, ActionThreadBlockingListener {
	private static final AnimationSequence GOTO_ANIMATION = new AnimationSequence(new OriginalImageLink(EImageLinkType.SETTLER, 3, 1).getName(), 0, 2);

	private final class ZoomEventHandler implements GOModalEventHandler {
		float startZoom = context.getScreen().getZoom();

		@Override
		public void phaseChanged(GOEvent event) {
		}

		@Override
		public void finished(GOEvent event) {
			eventDataChanged(((GOZoomEvent) event).getZoomFactor(), ((GOZoomEvent) event).getPointingPosition());
		}

		@Override
		public void aborted(GOEvent event) {
			eventDataChanged(1, null);
		}

		@Override
		public void eventDataChanged(GOEvent event) {
			eventDataChanged(((GOZoomEvent) event).getZoomFactor(), ((GOZoomEvent) event).getPointingPosition());
		}

		private void eventDataChanged(float zoomFactor, UIPoint p) {
			float newZoom = startZoom * zoomFactor;
			setZoom(newZoom, p);
		}
	}

	private static final int SCREEN_PADDING = 50;
	private static final float OVERDRAW_BOTTOM_PX = 50;
	private static final float MESSAGE_OFFSET_X = 300;
	private static final int MESSAGE_OFFSET_Y = 30;
	private static final long GOTO_MARK_TIME = 1500;
	private static final long DOUBLE_CLICK_TIME = 500;
	/**
	 * Sound ID when we are attacked.
	 */
	private static final int NOTIFY_ATTACKED_SOUND_ID = 80;

	private final IGraphicsGrid map;
	private final IMapObject[] objectsGrid;
	private final IGraphicsMovable[] movableGrid;
	private final BitSet borderGrid;
	private final byte[][] heightGrid;
	private final byte[][] visibleGrid;
	private final short width, height;
	private final boolean isVisibleGridAvailable;

	private final Background background;

	private final MapDrawContext context;

	private final MapObjectDrawer objectDrawer;

	/**
	 * The current connector that connects the outside world to us.
	 */
	private final MapInterfaceConnector connector;

	private final FramerateComputer framerate = new FramerateComputer();

	private final Messenger messenger;
	private final SoundManager soundmanager;
	private final MusicManager musicManager;
	private final BackgroundSound backgroundSound;

	private final ReplaceableTextDrawer textDrawer;
	private final IGameTimeProvider gameTimeProvider;

	private final ETextDrawPosition textDrawPosition;

	/**
	 * The controls that represent the interface.
	 */
	private final IControls controls;

	private FloatRectangle oldScreen;
	private UIPoint mousePosition = new UIPoint(0, 0);

	private int windowWidth = 1;
	private int windowHeight = 1;

	private ShortPoint2D scrollMarker;
	private long scrollMarkerTime;

	private ShortPoint2D moveToMarker;
	private long moveToMarkerTime;

	private String tooltipString = "";

	private EDebugColorModes debugColorMode = EDebugColorModes.NONE;

	private PlacementBuilding placementBuilding;

	private UIPoint currentSelectionAreaEnd;
	private boolean actionThreadIsSlow;
	private long lastSelectPointTime = 0;
	private ShortPoint2D lastSelectPointPos = null;

	private UIPoint currentSelectionAreaStart;
	private IInGamePlayer localPlayer;

	private final IntervalTimeRateCalculator gamespeedCalculator;

	private long getGameTimeNS() {
		return gameTimeProvider.getGameTime()*1000L*1000L;
	}

	public MapContent(IStartedGame game, SoundPlayer soundPlayer, ETextDrawPosition textDrawPosition) {
		this(game, soundPlayer, textDrawPosition,null);
	}

	/**
	 * 
	 * Creates a new map content for the given map.
	 * 
	 * @param game
	 *            The map.
	 * @param soundPlayer
	 *            The player
	 * @param controls
	 * 			  The menus on the side (swing) or on the bottom (android)
	 */
	public MapContent(IStartedGame game, SoundPlayer soundPlayer, ETextDrawPosition textDrawPosition, IControls controls) {
		this.map = game.getMap();
		if(map instanceof IDirectGridProvider) {
			IDirectGridProvider dgp = (IDirectGridProvider) map;
			objectsGrid = dgp.getObjectArray();
			movableGrid = dgp.getMovableArray();
			borderGrid = dgp.getBorderArray();
			heightGrid = dgp.getHeightArray();
			visibleGrid = dgp.getVisibleStatusArray();
			isVisibleGridAvailable = true;
		} else {
			objectsGrid = null;
			movableGrid = null;
			borderGrid = null;
			heightGrid = null;
			visibleGrid = null;
			isVisibleGridAvailable = false;
		}
		width = map.getWidth();
		height = map.getHeight();
		this.localPlayer = game.getInGamePlayer();
		this.gameTimeProvider = game.getGameTimeProvider();
		this.gamespeedCalculator = new IntervalTimeRateCalculator(50*1000*1000L, 20, this::getGameTimeNS);
		this.textDrawPosition = textDrawPosition;
		this.messenger = new Messenger(this.gameTimeProvider);
		this.textDrawer = new ReplaceableTextDrawer();
		this.context = new MapDrawContext(map);
		this.soundmanager = new SoundManager(soundPlayer);
		this.musicManager = new MusicManager(soundPlayer, localPlayer.getCivilisation());
		this.background = new Background(context);

		objectDrawer = new MapObjectDrawer(context, soundmanager, localPlayer);
		backgroundSound = new BackgroundSound(context, soundmanager);
		backgroundSound.start();
		musicManager.startMusic();

		if (controls == null) {
			this.controls = new OriginalControls(this, game);
		} else {
			this.controls = controls;
		}
		this.controls.setDrawContext(this, context);

		this.connector = new MapInterfaceConnector(this);
		this.connector.addListener(this);
	}

	private void resizeTo(int newWindowWidth, int newWindowHeight) {
		windowWidth = newWindowWidth;
		windowHeight = newWindowHeight;
		this.controls.resizeTo(windowWidth, windowHeight);
		reapplyContentSizes();
	}

	private void reapplyContentSizes() {
		this.context.setSize(windowWidth, windowHeight);
	}

	@Override
	public void drawContent(GLDrawContext gl, int newWidth, int newHeight) {
		try {
			framerate.nextFrame();
			gamespeedCalculator.tick();

			// TODO: Do only check once.
			if (textDrawer.getTextDrawer(gl, EFontSize.NORMAL).getWidth("a") == 0) {
				textDrawer.setTextDrawerFactory(new FontDrawerFactory());
			}

			if(isVisibleGridAvailable) objectDrawer.setVisibleGrid(((IDirectGridProvider)map).isFoWEnabled()?((IDirectGridProvider)map).getVisibleStatusArray():null);

			if (newWidth != windowWidth || newHeight != windowHeight) {
				resizeTo(newWidth, newHeight);
			}

			adaptScreenSize();
			this.objectDrawer.nextFrame();

			this.context.begin(gl);
			long start = System.nanoTime();

			FloatRectangle screen = this.context.getScreen().getPosition().bigger(SCREEN_PADDING);
			drawBackground(screen);
			long backgroundDuration = System.nanoTime() - start;

			start = System.nanoTime();
			drawMain(screen);

			if (scrollMarker != null) {
				drawGotoMarker();
			}
			if (moveToMarker != null) {
				drawMoveToMarker();
			}

			this.context.end();
			long foregroundDuration = System.nanoTime() - start;

			start = System.nanoTime();
			gl.clearDepthBuffer();
			gl.setGlobalAttributes(0, 0, 0, 1, 1, 1);
			drawSelectionHint(gl);
			controls.drawAt(gl);
			drawMessages(gl);
			drawWinStateMsg(gl);

			drawFramerateTimeAndHash(gl);

			if (actionThreadIsSlow) {
				drawActionThreadSlow(gl);
			}
			drawTooltip(gl);
			long uiTime = System.nanoTime() - start;

			if (CommonConstants.ENABLE_GRAPHICS_TIMES_DEBUG_OUTPUT) {
				System.out.println("Background: " + backgroundDuration/1000 + "µs, Foreground: " + foregroundDuration/1000 + "µs, UI: " + uiTime/1000 + "µs");
			}
		} catch (Throwable t) {
			System.err.println("Main draw handler cought throwable:");
			t.printStackTrace();
		}
	}

	private void drawGotoMarker() {
		long timeDifference = System.currentTimeMillis() - scrollMarkerTime;
		if (timeDifference > GOTO_MARK_TIME) {
			scrollMarker = null;
		} else {
			ImageLink image = GOTO_ANIMATION.getImageLink(timeDifference < GOTO_MARK_TIME / 2 ? 0 : 1);
			objectDrawer.drawGotoMarker(scrollMarker, ImageProvider.getInstance().getImage(image));
		}
	}

	private void drawMoveToMarker() {
		long timeDifference = System.currentTimeMillis() - moveToMarkerTime;
		if (timeDifference >= GOTO_MARK_TIME) {
			moveToMarker = null;
		} else {
			objectDrawer.drawMoveToMarker(moveToMarker, timeDifference / GOTO_MARK_TIME);
		}
	}

	private float messageAlpha(IMessage m) {
		int age = m.getAge();
		return age < 1500
				? Math.min(1, age / 1000f)
				: Math.max(0, 1f - (float) age / IMessage.MESSAGE_TTL);
	}

	private void drawWinStateMsg(GLDrawContext gl) {
		if(localPlayer == null || localPlayer.getWinState() == EWinState.UNDECIDED) {
			return;
		}
		Color color = localPlayer.getWinState() == EWinState.WON ? Color.GREEN : Color.RED;
		final String msg = Labels.getString("winstate_" + localPlayer.getWinState());

		TextDrawer drawer = textDrawer.getTextDrawer(gl, EFontSize.HEADLINE);

		drawer.drawString((windowWidth - drawer.getWidth(msg)) / 2, windowHeight - 7 * EFontSize.HEADLINE.getSize(), color, msg);
	}

	private void drawMessages(GLDrawContext gl) {
		EFontSize fontSize = EFontSize.HEADLINE;
		TextDrawer drawer = textDrawer.getTextDrawer(gl, fontSize);
		messenger.doTick();

		float offsetY;
		float offsetX;
		float yDirection;
		int messageIndex;
		if(textDrawPosition == ETextDrawPosition.DESKTOP) {
			offsetY = MESSAGE_OFFSET_Y;
			offsetX = MESSAGE_OFFSET_X;
			yDirection = 1;
			messageIndex = 0;
		} else {
			offsetY = windowHeight - MESSAGE_OFFSET_Y*2;
			offsetX = 30;
			yDirection = -1;
			messageIndex = 1;
		}

		for (IMessage m : messenger.getMessages()) {
			float x = offsetX;
			float y = offsetY + messageIndex * (fontSize.getSize()*1.3f) * yDirection;
			float a = messageAlpha(m);
			if (m.getSender() >= 0) {
				String name = getPlayername(m.getSender()) + ":";
				Color color = MapDrawContext.getPlayerColor(m.getSender());
				float width = drawer.getWidth(name);
				for (int i = -1; i < 3; i++) {
					drawer.drawString(x + i, y - 1, new Color(1, 1, 1, a/2), name);
				}
				drawer.drawString(x, y, new Color(color.getRed(), color.getGreen(), color.getBlue(), a), name);
				x += width + 10;
			}

			drawer.drawString(x, y, new Color(1, 1, 1, a), Labels.getString(m.getMessageLabel()));

			messageIndex++;
		}
	}

	private String getPlayername(byte sender) {
		// TODO: Player names
		return "player " + sender;
	}

	private void adaptScreenSize() {
		FloatRectangle newScreen = context.getScreen().getPosition();
		if (!newScreen.equals(oldScreen)) {
			getInterfaceConnector().fireAction(new ScreenChangeAction(context.getScreenArea(), context.getPositionUnder(newScreen.getCenterX(), newScreen.getCenterY())));
		}
		oldScreen = newScreen;
	}

	private UnifiedDrawHandle selectionArea = null;

	private void drawSelectionHint(GLDrawContext gl) {
		if (this.currentSelectionAreaStart != null && this.currentSelectionAreaEnd != null) {

			if(selectionArea == null || !selectionArea.isValid()) {
				selectionArea = gl.createUnifiedDrawCall(4, "selection-area", null, new float[] {0, 0, 1, 0, 1, 1, 0, 1});
			}

			float width = (float)(currentSelectionAreaEnd.getX() - currentSelectionAreaStart.getX());
			float height = (float)(currentSelectionAreaEnd.getY() - currentSelectionAreaStart.getY());
			selectionArea.drawSimple(EPrimitiveType.LineLoop, (float)currentSelectionAreaStart.getX(), (float)currentSelectionAreaStart.getY(), 0, width, height, null, 1);
		}
	}

	private void drawFramerateTimeAndHash(GLDrawContext gl) {
		if (textDrawPosition == ETextDrawPosition.NONE) {
			return;
		}

		String fps = Labels.getString("map-fps", framerate.getRate(), gamespeedCalculator.getRate());
		long gameTime = gameTimeProvider.getGameTime() / 1000;
		String timeString = Labels.getString("map-time", gameTime / 60 / 60, (gameTime / 60) % 60, (gameTime) % 60);

		TextDrawer drawer = textDrawer.getTextDrawer(gl, EFontSize.NORMAL);
		float letterWidth = getLetterWidth(drawer);
		float textLineHeight = getTextLineHeight(drawer);

		float yFirstLine = windowHeight - 1.5f * textLineHeight;
		float ySecondLine = windowHeight - 3.0f * textLineHeight;

		float sideXOffset = 2 * letterWidth;

		float x;
		if(textDrawPosition == ETextDrawPosition.MOBILE) {
			x = sideXOffset;

			drawer.drawString(x, yFirstLine, fps);
			x += fps.length() * letterWidth;

			drawer.drawString(x, yFirstLine, timeString);
			x += timeString.length() * letterWidth;

			if(x > windowWidth) {
				x = sideXOffset;
				drawer.drawString(x, ySecondLine, CommitInfo.COMMIT_HASH_SHORT);
			} else {
				drawer.drawString(x, yFirstLine, CommitInfo.COMMIT_HASH_SHORT);
				x += 9 * letterWidth;
			}
		} else {
			x = windowWidth - (sideXOffset);

			x -= 9 * letterWidth;
			drawer.drawString(x, yFirstLine, CommitInfo.COMMIT_HASH_SHORT);

			x -= timeString.length() * letterWidth;
			drawer.drawString(x, yFirstLine, timeString);

			x -= fps.length() * letterWidth;
			drawer.drawString(x, yFirstLine, fps);
		}
	}

	private float getLetterWidth(TextDrawer drawer) {
		return drawer.getWidth("X");
	}

	private float getTextLineHeight(TextDrawer drawer) {
		return drawer.getHeight("X");
	}

	private void drawTooltip(GLDrawContext gl) {
		if (!tooltipString.isEmpty()) {
			TextDrawer drawer = textDrawer.getTextDrawer(gl, EFontSize.NORMAL);
			drawer.drawString((int) mousePosition.getX(), (int) mousePosition.getY(), tooltipString);
		}
	}

	private void drawActionThreadSlow(GLDrawContext gl) {
		TextDrawer drawer = textDrawer.getTextDrawer(gl, EFontSize.NORMAL);
		String string = Labels.getString("action_firerer_slow");
		float x = windowWidth - drawer.getWidth(string) - 5;
		float y = windowHeight - 3 * drawer.getHeight(string);
		drawer.drawString(x, y, string);
	}

	/**
	 * Draws the main content (buildings, settlers, ...), assuming the context is set up.
	 */
	private void drawMain(FloatRectangle screen) {
		MapRectangle area = this.context.getConverter().getMapForScreen(screen);

		double bottomDrawY = screen.getMinY() - OVERDRAW_BOTTOM_PX;

		boolean linePartiallyVisible = true;
		for(int line = 0; line < area.getHeight() + 50 && linePartiallyVisible; line++) {
			int y = area.getLineY(line);
			if (y < 0) {
				continue;
			}
			if (y >= height) {
				break;
			}
			linePartiallyVisible = false;

			int endX = Math.min(area.getLineEndX(line), width - 1);
			int startX = Math.max(area.getLineStartX(line), 0);
			for(int x = startX; x <= endX; x++) {
				drawTile(x, y);
				if(!linePartiallyVisible) {
					double drawSpaceY = this.context.getConverter().getViewY(x, y, heightGrid == null ? this.context.getHeight(x, y) : heightGrid[x][y]);
					if (drawSpaceY > bottomDrawY) {
						linePartiallyVisible = true;
					}
				}
			}
		}

		if(placementBuilding != null) {
			ShortPoint2D underMouse = this.context.getPositionOnScreen((float) mousePosition.getX(), (float) mousePosition.getY());
			if(0 <= underMouse.x && underMouse.x < width && 0 <= underMouse.y && underMouse.y < height) {
				IMapObject mapObject = map.getVisibleMapObjectsAt(underMouse.x, underMouse.y);

				if (mapObject != null && mapObject.getMapObject(EMapObjectType.CONSTRUCTION_MARK) != null) { // if there is a construction mark
					this.objectDrawer.drawMapObject(underMouse.x, underMouse.y, placementBuilding);
				}
			}
		}

		if(debugColorMode != EDebugColorModes.NONE) {
			drawDebugColors();
		}
	}

	private void drawTile(int x, int y) {
		int tileIndex = x+y*width;

		byte fow = visibleGrid != null && ((IDirectGridProvider)map).isFoWEnabled() ? visibleGrid[x][y] : map.getVisibleStatus(x, y);
		boolean fogClear = fow > CommonConstants.FOG_OF_WAR_EXPLORED;

		IMapObject object = objectsGrid != null && fogClear ? objectsGrid[tileIndex] : map.getVisibleMapObjectsAt(x, y);
		if(object != null) {
			this.objectDrawer.drawMapObject(x, y, object);
		}

		if(y < height - 3) {
			object = objectsGrid != null && fogClear ? objectsGrid[tileIndex+3*width] : map.getVisibleMapObjectsAt(x, y + 3);
			if(object != null) {
				EMapObjectType type = object.getObjectType();
				if(type == EMapObjectType.DOCK) {
					this.objectDrawer.drawDock(x, y + 3, object);
				}
			}
		}

		if(fogClear) {
			IGraphicsMovable movable = movableGrid != null ? movableGrid[tileIndex] : map.getMovableAt(x, y);
			if (movable != null) {
				this.objectDrawer.draw(movable);
			}

			if (borderGrid != null ? borderGrid.get(tileIndex) : map.isBorder(x, y)) {
				IPlayer player = map.getPlayerAt(x, y);
				objectDrawer.drawPlayerBorderObject(x, y, player);
			}
		}
	}

	// @formatter:off
	public static final float[] shape = new float[] {
			0,  4,
			-3,  2,
			-3, -2,
			0, -4,
			0, -4,
			3, -2,
			3,  2,
			0,  4,
	};
	// @formatter:on

	private UnifiedDrawHandle shapeHandle = null;

	private void drawDebugColors() {
		GLDrawContext gl = this.context.getGl();

		if(shapeHandle == null || !shapeHandle.isValid()) shapeHandle = gl.createUnifiedDrawCall(4, "debugshape", null, shape);

		context.getScreenArea().stream().filterBounds(width, height).forEach((x, y) -> {
			int argb = map.getDebugColorAt(x, y, debugColorMode);
			if (argb != 0) {
				int height = context.getHeight(x, y);
				float dx = context.getConverter().getViewX(x, y, height);
				float dy = context.getConverter().getViewY(x, y, height);
				shapeHandle.drawSimple(EPrimitiveType.Quad, dx, dy, .5f, 1, 1, Color.fromShort((short) argb), 1);
			}
		});
	}

	/**
	 * Draws the background.
	 *
	 * @param screen
	 */
	private void drawBackground(FloatRectangle screen) {
		this.background.drawMapContent(this.context, screen);
	}

	@Override
	public void handleEvent(GOEvent event) {
		if (event instanceof GOPanEvent) {
			UIPoint center = ((GOPanEvent) event).getPanCenter();
			if (center == null || !controls.containsPoint(center)) {
				event.setHandler(new PanHandler(this.context.getScreen()));
			}
		} else if (event instanceof GOCommandEvent) {
			GOCommandEvent commandEvent = (GOCommandEvent) event;
			Optional<Action> action = getActionForCommand(commandEvent);
			// also set when action was null, to abort drawing.
			fireActionEvent(event, action);
		} else if (event instanceof GOKeyEvent) {
			Optional<Action> actionForKeyboard = Optional.ofNullable(getActionForKeyboard(((GOKeyEvent) event).getKeyCode()));
			actionForKeyboard.ifPresent(action -> 
				fireActionEvent(event, actionForKeyboard)
			);
		} else if (event instanceof GODrawEvent) {
			GODrawEvent drawEvent = (GODrawEvent) event;
			if (!controls.handleDrawEvent(drawEvent)) {
				handleDraw(drawEvent);
			}
		} else if (event instanceof GOHoverEvent) {
			GOHoverEvent hoverEvent = (GOHoverEvent) event;
			handleHover(hoverEvent);
		} else if (event instanceof GOZoomEvent) {
			GOZoomEvent zoomEvent = (GOZoomEvent) event;
			handleZoom(zoomEvent);
		}
	}

	private void handleZoom(GOZoomEvent zoomEvent) {
		zoomEvent.setHandler(new ZoomEventHandler());
	}

	/**
	 * Zoom in
	 */
	public void zoomIn() {
		if (context != null) {
			float zoom = context.getScreen().getZoom();
			setZoom(zoom * 1.3f, null);
		}
	}

	/**
	 * Zoom out
	 */
	public void zoomOut() {
		if (context != null) {
			float zoom = context.getScreen().getZoom();
			setZoom(zoom / 1.3f, null);
		}
	}

	/**
	 * Zoom to default value
	 */
	public void zoom100() {
		if (context != null) {
			setZoom(1.0f, null);
		}
	}

	private void fireActionEvent(GOEvent event, Optional<Action> action) {
		event.setHandler(new ActionHandler(action, this));
	}

	/**
	 * Gets a action for a keyboard key.
	 *
	 * @param keyCode
	 *            The key name as String.
	 * @return The action that corresponds to the key
	 */
	private static Action getActionForKeyboard(String keyCode) {
		if ("F12".equalsIgnoreCase(keyCode)) {
			return new Action(EActionType.FAST_FORWARD);
		} else if ("P".equalsIgnoreCase(keyCode)
				|| "PAUSE".equalsIgnoreCase(keyCode)) {
			return new Action(EActionType.SPEED_TOGGLE_PAUSE);
		} else if ("BACK".equalsIgnoreCase(keyCode)) {
			return new Action(EActionType.BACK);
		} else if ("+".equals(keyCode) || "]".equals(keyCode)) {
			return new Action(EActionType.SPEED_FASTER);
		} else if ("-".equals(keyCode) || "/".equals(keyCode)) {
			return new Action(EActionType.SPEED_SLOWER);
		} else if (" ".equals(keyCode) || "space".equalsIgnoreCase(keyCode)) {
			return new Action(EActionType.SHOW_MESSAGE);
		} else if ("d".equalsIgnoreCase(keyCode)) {
			return new Action(EActionType.DEBUG_ACTION);
		} else if ("s".equalsIgnoreCase(keyCode)) {
			return new Action(EActionType.STOP_WORKING);
		} else if ("e".equalsIgnoreCase(keyCode)) {
			return new Action(EActionType.TOGGLE_DEBUG);
		} else if ("o".equalsIgnoreCase(keyCode)) {
			return new Action(EActionType.TOGGLE_ORIGINAL_GRAPHICS);
		} else if ("q".equalsIgnoreCase(keyCode)) {
			// TODO: Only show the exit menu.
			return new Action(EActionType.EXIT);
		} else if ("w".equalsIgnoreCase(keyCode)) {
			return new Action(EActionType.TOGGLE_FOG_OF_WAR);
		} else if ("z".equalsIgnoreCase(keyCode)) {
			return new Action(EActionType.SHOW_SELECTION);
		} else if ("n".equalsIgnoreCase(keyCode)) {
			return new Action(EActionType.NEXT_OF_TYPE);
		} else if ("F5".equalsIgnoreCase(keyCode)) {
			return new Action(EActionType.ZOOM_IN);
		} else if ("F6".equalsIgnoreCase(keyCode)) {
			return new Action(EActionType.ZOOM_OUT);
		} else if ("F2".equalsIgnoreCase(keyCode)) {
			return new Action(EActionType.SAVE);
		} else if ("TAB".equalsIgnoreCase(keyCode)) {
			return new Action(EActionType.FILTER_WOUNDED);
		} else if ("DELETE".equalsIgnoreCase(keyCode)) {
			return new Action(EActionType.DESTROY);
		} else if ("ESCAPE".equalsIgnoreCase(keyCode)) {
			return new Action(EActionType.ABORT);
		} else {
			return null;
		}
	}

	private final GOEventHandler hoverHandler = new GOModalEventHandler() {
		@Override
		public void phaseChanged(GOEvent event) {
		}

		@Override
		public void finished(GOEvent event) {
			changeMousePosition(((GOHoverEvent) event).getHoverPosition());
		}

		@Override
		public void aborted(GOEvent event) {
		}

		@Override
		public void eventDataChanged(GOEvent event) {
			changeMousePosition(((GOHoverEvent) event).getHoverPosition());
		}
	};

	private void handleHover(GOHoverEvent hoverEvent) {
		hoverEvent.setHandler(hoverHandler);
	}

	/**
	 * This is called whenever the mouse pointer position changed. Used for tooltips.
	 * 
	 * @param position
	 *            The new mouse position.
	 */
	private void changeMousePosition(UIPoint position) {
		mousePosition = position;

		if (controls.containsPoint(position)) {
			tooltipString = controls.getDescriptionFor(position);
		} else {
			float x = (float) position.getX();
			float y = (float) position.getY();
			ShortPoint2D onMap = this.context.getPositionOnScreen(x, y);
			tooltipString = controls.getMapTooltip(onMap);
		}
		if (tooltipString == null) {
			tooltipString = "";
		}
	}

	private Optional<Action> getActionForCommand(GOCommandEvent commandEvent) {
		UIPoint position = commandEvent.getCommandPosition();
		if (controls.containsPoint(position)) {
			if (commandEvent.isSelecting()) {
				return controls.getActionForSelect(position);
			} else {
				return controls.getActionForMoveTo(position, moveToForCommand(commandEvent));
			}
		} else {
			// handle map click
			return handleCommandOnMap(commandEvent, position);
		}
	}

	private void handleDraw(GODrawEvent drawEvent) {
		handleDrawOnMap(drawEvent);
	}

	private void handleDrawOnMap(GODrawEvent drawEvent) {
		this.currentSelectionAreaStart = drawEvent.getDrawPosition();
		drawEvent.setHandler(this.drawSelectionHandler);
	}

	private final GOEventHandler drawSelectionHandler = new GOModalEventHandler() {
		@Override
		public void phaseChanged(GOEvent event) {
		}

		@Override
		public void finished(GOEvent event) {
			updateSelectionArea(((GODrawEvent) event).getDrawPosition(), true);
		}

		@Override
		public void aborted(GOEvent event) {
			abortSelectionArea();
		}

		@Override
		public void eventDataChanged(GOEvent event) {
			updateSelectionArea(((GODrawEvent) event).getDrawPosition(), false);
		}
	};

	private Optional<Action> handleCommandOnMap(GOCommandEvent commandEvent, UIPoint position) {
		float x = (float) position.getX();
		float y = (float) position.getY();
		ShortPoint2D onMap = this.context.getPositionOnScreen(x, y);
		if (this.context.checkMapCoordinates(onMap.x, onMap.y)) {
			Action action;
			if (commandEvent.isSelecting()) {
				action = handleSelectCommand(onMap);
			} else {
				action = new MoveToAction(moveToForCommand(commandEvent), onMap);
			}
			return Optional.of(action);
		}
		return Optional.empty();
	}

	private static EMoveToType moveToForCommand(GOCommandEvent commandEvent) {
		Set<EModifier> modifiers = commandEvent.getModifiers();
		if (modifiers.contains(EModifier.CTRL)) {
			return EMoveToType.FORCED;
		} else if (modifiers.contains(EModifier.ALT)) {
			return EMoveToType.PATROL;
		} else {
			return EMoveToType.DEFAULT;
		}
		// TODO: Add waypoint with SHIFT
	}

	private Action handleSelectCommand(ShortPoint2D onMap) {
		long currentTime = System.currentTimeMillis();
		if (currentTime - lastSelectPointTime < DOUBLE_CLICK_TIME && onMap.equals(lastSelectPointPos)) {
			lastSelectPointTime = 0;
			return new PointAction(EActionType.SELECT_POINT_TYPE, onMap);
		} else {
			lastSelectPointTime = currentTime;
			lastSelectPointPos = onMap;
			return new PointAction(EActionType.SELECT_POINT, onMap);
		}
	}

	private void abortSelectionArea() {
		this.currentSelectionAreaStart = null;
		this.currentSelectionAreaEnd = null;
	}

	private void updateSelectionArea(UIPoint mousePosition, boolean finished) {
		if (finished && currentSelectionAreaStart != null) {
			int x1 = (int) mousePosition.getX();
			int x2 = (int) this.currentSelectionAreaStart.getX();
			int y1 = (int) mousePosition.getY();
			int y2 = (int) this.currentSelectionAreaStart.getY();

			if (x1 > x2) {
				int temp = x1;
				x1 = x2;
				x2 = temp;
			}
			if (y1 > y2) {
				int temp = y1;
				y1 = y2;
				y2 = temp;
			}
			IMapArea area = this.context.getRectangleOnScreen(x1, y1, x2, y2);
			getInterfaceConnector().fireAction(new SelectAreaAction(area));

			this.currentSelectionAreaStart = null;
			this.currentSelectionAreaEnd = null;
		} else {
			this.currentSelectionAreaEnd = mousePosition;
		}
	}

	/**
	 * Gets the interface connector for the ui.
	 *
	 * @return The connector to access the interface.
	 */
	public MapInterfaceConnector getInterfaceConnector() {
		return this.connector;
	}

	public void setSelection(ISelectionSet selection) {
		controls.displaySelection(selection);
	}

	public void scrollTo(ShortPoint2D point, boolean mark) {
		if (point != null) {
			this.context.scrollTo(point);
			if (mark) {
				scrollMarker = point;
				scrollMarkerTime = System.currentTimeMillis();
			}
		}
	}

	@Override
	public void action(IAction action) {
		controls.action(action);
		switch (action.getActionType()) {
		case TOGGLE_DEBUG:
			debugColorMode = EDebugColorModes.getNextMode(debugColorMode);
			System.out.println("Current debugColorMode: " + debugColorMode);
			break;
		case TOGGLE_ORIGINAL_GRAPHICS:
			context.ENABLE_ORIGINAL = !context.ENABLE_ORIGINAL;
			break;
		case PAN_TO:
			PointAction panAction = (PointAction) action;
			scrollTo(panAction.getPosition(), false);
			break;
		case SCREEN_CHANGE:
			ScreenChangeAction screenAction = (ScreenChangeAction) action;
			controls.setMapViewport(screenAction.getScreenArea(), screenAction.getCenterPosition());
			break;
		case ZOOM_IN:
			if (context.getScreen().getZoom() < 1.1) {
				setZoom(context.getScreen().getZoom() * 2, null);
			}
			break;
		case ZOOM_OUT:
			if (context.getScreen().getZoom() > 0.6) {
				setZoom(context.getScreen().getZoom() / 2, null);
			}
			break;
		case MOVE_TO:
			moveToMarker = ((MoveToAction) action).getPosition();
			moveToMarkerTime = System.currentTimeMillis();
			break;
		case SHOW_MESSAGE:
			scrollTo(messenger.getPosition(), true);
			break;
		case SHOW_CONSTRUCTION_MARK:
			EBuildingType buildingType = ((ShowConstructionMarksAction) action).getBuildingType();
			BuildingVariant buildingVariant = buildingType == null ? null : buildingType.getVariant(localPlayer.getCivilisation());
			placementBuilding = buildingVariant == null ? null : new PlacementBuilding(buildingVariant);
			break;
		case TOGGLE_MUSIC:
			if (musicManager.isRunning()) {
				musicManager.stopMusic();
			} else {
				musicManager.startMusic();
			}
			break;
			// TODO find a good way to do this
		case MUSIC_VOLUME_UP:
			musicManager.setMusicVolume(0.05f, true);
			break;
		case MUSIC_VOLUME_DOWN:
			musicManager.setMusicVolume(-0.05f, true);
			break;
		default:
			break;
		}
	}

	private void setZoom(float newZoom, UIPoint pointingPosition) {
		context.getScreen().setZoom(newZoom, pointingPosition);
		reapplyContentSizes();
	}

	public void addMessage(IMessage message) {
		boolean printMsg;
		synchronized (messenger) {
			printMsg = messenger.addMessage(message);
		}

		if (printMsg) {
			switch (message.getType()) {
			case ATTACKED:
				soundmanager.playSound(NOTIFY_ATTACKED_SOUND_ID, 1);
				break;

			default:
				break;
			}
		}
	}

	public void playSound(int soundId, float volume) {
		soundmanager.playSound(soundId, volume);
	}

	@Override
	public void fireAction(IAction action) {
		IAction fire = controls.replaceAction(action);
		if (fire != null) {
			getInterfaceConnector().fireAction(fire);
		}
	}

	@Override
	public void actionThreadSlow(boolean isBlocking) {
		actionThreadIsSlow = isBlocking;
	}

	@Override
	public void actionThreadCaughtException(Throwable e) {
		// This is currently ignored. TODO: Where to catch exceptions?
	}

	public void stop() {
		musicManager.stopMusic();
		backgroundSound.stop();
		controls.stop();
	}

	void loadUIState(UIState state) {
		if (state == null) {
			return;
		}

		if (state.getStartPoint() != null) {
			scrollTo(state.getStartPoint(), false);
		} else {
			setZoom(state.getZoom(), null);
			context.getScreen().setScreenCenter(state.getScreenCenterX(), state.getScreenCenterY());
		}
	}

	protected UIState getUIState() {
		ScreenPosition screen = context.getScreen();
		return new UIState(screen.getScreenCenterX(), screen.getScreenCenterY(), screen.getZoom());
	}

	public MusicManager getMusicManager() {
		return musicManager;
	}
}
