/*******************************************************************************
 * Copyright (c) 2015
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
package jsettlers.graphics.map.controls.original;

import java8.util.Optional;

import go.graphics.GLDrawContext;
import go.graphics.UIPoint;
import go.graphics.event.GOEvent;
import go.graphics.event.GOModalEventHandler;
import go.graphics.event.mouse.GODrawEvent;
import jsettlers.common.map.shapes.MapRectangle;
import jsettlers.common.action.EActionType;
import jsettlers.common.action.EMoveToType;
import jsettlers.common.action.IAction;
import jsettlers.common.menu.IStartedGame;
import jsettlers.common.action.MoveToAction;
import jsettlers.common.player.IInGamePlayer;
import jsettlers.common.position.FloatRectangle;
import jsettlers.common.position.ShortPoint2D;
import jsettlers.common.selectable.ISelectionSet;
import jsettlers.common.action.Action;
import jsettlers.graphics.action.ActionFireable;
import jsettlers.graphics.action.ChangePanelAction;
import jsettlers.graphics.action.ExecutableAction;
import jsettlers.common.action.PointAction;
import jsettlers.graphics.map.MapContent;
import jsettlers.graphics.map.MapDrawContext;
import jsettlers.graphics.map.controls.IControls;
import jsettlers.graphics.map.controls.original.panel.MainPanel;
import jsettlers.graphics.map.controls.original.panel.content.ContentType;
import jsettlers.graphics.map.controls.original.panel.content.MessageContent;
import jsettlers.graphics.map.controls.original.panel.selection.PeopleSelectionContent;
import jsettlers.graphics.map.controls.original.panel.selection.BuildingSelectionContent;
import jsettlers.graphics.map.controls.original.panel.selection.PriestSelectionContent;
import jsettlers.graphics.map.controls.original.panel.selection.ShipSelectionContent;
import jsettlers.graphics.map.controls.original.panel.selection.SoldierSelectionContent;
import jsettlers.graphics.map.controls.original.panel.selection.SpecialistSelectionContent;
import jsettlers.graphics.map.minimap.Minimap;
import jsettlers.graphics.map.minimap.MinimapMode;
import jsettlers.graphics.ui.Button;
import jsettlers.graphics.ui.UIPanel;

/**
 * This is the entry class for the {@link OriginalControls} map overlay.
 * 
 * @author Michael Zangl
 *
 */
public class OriginalControls implements IControls {
	private static final int DEFAULT_LAYOUT_SIZE = 480;

	private final MinimapMode minimapSettings = new MinimapMode();
	private final MainPanel mainPanel;
	private final Button chatButton;

	private ControlPanelLayoutProperties layoutProperties;
	private UIPanel uiBase;
	private Minimap minimap;
	private boolean lastSelectionWasNull = true;
	private MapDrawContext context;
	private final IInGamePlayer player;

	/**
	 * Creates a new {@link OriginalControls} overlay.
	 *  @param parentMapContent
	 *            The {@link MapContent} this controls belongs to
	 * @param game
	 */
	public OriginalControls(MapContent parentMapContent, IStartedGame game) {
		layoutProperties = ControlPanelLayoutProperties.getLayoutPropertiesFor(DEFAULT_LAYOUT_SIZE);
		final MiniMapLayoutProperties miniMap = layoutProperties.miniMap;
		mainPanel = new MainPanel(parentMapContent, game);
		player = game.getInGamePlayer();

		chatButton = new Button(
				new ShowChatAction(), miniMap.IMAGELINK_BUTTON_CHAT_ACTIVE, miniMap.IMAGELINK_BUTTON_CHAT_INACTIVE, "");

		uiBase = createInterface();
		mainPanel.layoutPanel(layoutProperties);
	}

	private UIPanel createInterface() {
		UIPanel panel = new UIPanel();

		addMiniMap(panel);

		mainPanel.setBackground(layoutProperties.IMAGELINK_MAIN);
		panel.addChild(mainPanel, 0, 0, layoutProperties.miniMap.RIGHT_DECORATION_LEFT, layoutProperties.MAIN_PANEL_TOP);

		UIPanel rightDecoration = new UIPanel();
		rightDecoration.setBackground(layoutProperties.IMAGELINK_DECORATION_RIGHT);
		panel.addChild(
				rightDecoration,
				layoutProperties.miniMap.RIGHT_DECORATION_LEFT,
				0,
				layoutProperties.miniMap.RIGHT_DECORATION_LEFT + layoutProperties.RIGHT_DECORATION_WIDTH,
				layoutProperties.MAIN_PANEL_TOP);

		return panel;
	}

	private void addMiniMap(UIPanel panel) {
		MiniMapLayoutProperties miniMap = layoutProperties.miniMap;
		UIPanel minimapBackgroundLeft = new UIPanel();
		minimapBackgroundLeft.setBackground(miniMap.LEFT_DECORATION);
		minimapBackgroundLeft.addChild(
				chatButton,
				miniMap.BUTTON_CHAT_LEFT,
				miniMap.BUTTON_CHAT_TOP - miniMap.BUTTON_HEIGHT,
				miniMap.BUTTON_CHAT_LEFT + miniMap.BUTTON_WIDTH,
				miniMap.BUTTON_CHAT_TOP);
		minimapBackgroundLeft.addChild(
				new MinimapOccupiedButton(minimapSettings),
				miniMap.BUTTON_FEATURES_LEFT,
				miniMap.BUTTON_FEATURES_TOP - miniMap.BUTTON_HEIGHT,
				miniMap.BUTTON_FEATURES_LEFT + miniMap.BUTTON_WIDTH,
				miniMap.BUTTON_FEATURES_TOP);
		minimapBackgroundLeft.addChild(
				new MinimapSettlersButton(minimapSettings),
				miniMap.BUTTON_SETTLERS_LEFT,
				miniMap.BUTTON_SETTLERS_TOP - miniMap.BUTTON_HEIGHT,
				miniMap.BUTTON_SETTLERS_LEFT + miniMap.BUTTON_WIDTH,
				miniMap.BUTTON_SETTLERS_TOP);
		minimapBackgroundLeft.addChild(
				new MinimapBuildingButton(minimapSettings),
				miniMap.BUTTON_BUILDINGS_LEFT,
				miniMap.BUTTON_BUILDINGS_TOP - miniMap.BUTTON_HEIGHT,
				miniMap.BUTTON_BUILDINGS_LEFT + miniMap.BUTTON_WIDTH,
				miniMap.BUTTON_BUILDINGS_TOP);

		UIPanel minimapBackgroundRight = new UIPanel();
		minimapBackgroundRight.setBackground(miniMap.RIGHT_DECORATION);

		panel.addChild(minimapBackgroundLeft, 0, layoutProperties.MAIN_PANEL_TOP, miniMap.RIGHT_DECORATION_LEFT, 1);
		panel.addChild(minimapBackgroundRight, miniMap.RIGHT_DECORATION_LEFT, layoutProperties.MAIN_PANEL_TOP, 1, 1);
	}

	@Override
	public void resizeTo(float newWidth, float newHeight) {
		ControlPanelLayoutProperties newLayoutProperties = ControlPanelLayoutProperties.getLayoutPropertiesFor(newHeight);
		if (!newLayoutProperties.equals(layoutProperties)) {
			layoutProperties = newLayoutProperties;
			uiBase = createInterface();
			mainPanel.layoutPanel(layoutProperties);
		}
		int width = (int) (layoutProperties.ASPECT_RATIO * newHeight);
		this.uiBase.setPosition(new FloatRectangle(0, 0, width, newHeight));

		minimap.setSize(
				(int) Math.ceil(layoutProperties.miniMap.MAP_WIDTH * width),
				(int) Math.ceil(layoutProperties.miniMap.MAP_HEIGHT * (1 - layoutProperties.MAIN_PANEL_TOP) * newHeight));
	}

	@Override
	public void drawAt(GLDrawContext gl) {
		if (minimap != null) {
			minimap.draw(gl, getMinimapLeft(), getMinimapBottom());
		}
		uiBase.drawAt(gl); // Frame decoration should be drawn after and over the edges of the minimap.
	}

	private float getMinimapLeft() {
		return layoutProperties.miniMap.MAP_LEFT * uiBase.getPosition().getWidth();
	}

	private float getMinimapBottom() {
		return (layoutProperties.MAIN_PANEL_TOP + (1f - layoutProperties.MAIN_PANEL_TOP) * layoutProperties.miniMap.MAP_BOTTOM)
				* uiBase.getPosition().getHeight();
	}

	@Override
	public boolean containsPoint(UIPoint position) {
		float width = uiBase.getPosition().getWidth();
		float uicenter = layoutProperties.miniMap.RIGHT_DECORATION_LEFT * width;
		return position.getX() < Math.max(uicenter, getMinimapOffset(position.getY()) + uicenter);
	}

	/**
	 * Gets the X-Offset of the minimap at the given y position.
	 *
	 * @param y
	 *            The y position
	 * @return The x offset for that row.
	 */
	private double getMinimapOffset(double y) {
		float width = uiBase.getPosition().getWidth();
		float height = uiBase.getPosition().getHeight();
		float m = 1 / (1 - layoutProperties.miniMap.RIGHT_DECORATION_LEFT) / width * (1 - layoutProperties.MAIN_PANEL_TOP) * height;
		return y / m - layoutProperties.MAIN_PANEL_TOP * height / m;
	}

	@Override
	public Optional<Action> getActionForMoveTo(UIPoint position, EMoveToType moveToType) {
		return getActionFor(position, (relativex, relativey) -> getMoveToForMinimap(relativex, relativey, moveToType));
	}
	
	@Override
	public Optional<Action> getActionForSelect(UIPoint position) {
		return getActionFor(position, this::getScrollForMinimap);
	}
	
	@FunctionalInterface
	private interface PositionToAction {
		Optional<Action> positionToAction(float relativex, float relativey);
	}
	
	private Optional<Action> getActionFor(UIPoint position, PositionToAction minimapPositionToAction) {
		float relativex = (float) position.getX() / this.uiBase.getPosition().getWidth();
		float relativey = (float) position.getY() / this.uiBase.getPosition().getHeight();
		Optional<Action> action;
		if (minimap != null && relativey > layoutProperties.MAIN_PANEL_TOP && getMinimapOffset(position.getY()) < position.getX()) {
			action = minimapPositionToAction.positionToAction(relativex, relativey);
			startMapPosition = null; // to prevent it from jumping back.
		} else {
			action = uiBase.getAction(relativex, relativey);
		}
		if (action.isPresent()
				&& action.get().getActionType() == EActionType.CHANGE_PANEL) {
			mainPanel.setContent(((ChangePanelAction) action.get()).getContent());
			return Optional.empty();
		} else {
			return action;
		}
	}

	/**
	 * Gets the action for a click on the minimap.
	 * 
	 * @param relativex
	 *            The position on the minimap.
	 * @param relativey
	 *            The position on the minimap.
	 * @return the action for that point or <code>null</code> for no action.
	 */
	private Optional<Action> getScrollForMinimap(float relativex, float relativey) {
		return computeClickPosition(relativex, relativey).map(clickPosition -> new PointAction(EActionType.PAN_TO, clickPosition));
	}
	
	private Optional<Action> getMoveToForMinimap(float relativex, float relativey, EMoveToType moveTo) {
		return computeClickPosition(relativex, relativey).map(clickPosition -> new MoveToAction(moveTo, clickPosition));
	}

	private Optional<ShortPoint2D> computeClickPosition(float relativex, float relativey) {
		float minimapx = (relativex - layoutProperties.miniMap.MAP_LEFT)
				/ layoutProperties.miniMap.MAP_WIDTH;
		float minimapy = ((relativey - layoutProperties.MAIN_PANEL_TOP)
				/ (1 - layoutProperties.MAIN_PANEL_TOP) - layoutProperties.miniMap.MAP_BOTTOM)
				/ layoutProperties.miniMap.MAP_HEIGHT;
		return Optional.ofNullable(minimap.getClickPositionIfOnMap(minimapx, minimapy));
	}

	@Override
	public String getDescriptionFor(UIPoint position) {
		float relativex = (float) position.getX() / this.uiBase.getPosition().getWidth();
		float relativey = (float) position.getY() / this.uiBase.getPosition().getHeight();
		return uiBase.getDescription(relativex, relativey);
	}

	@Override
	public void setMapViewport(MapRectangle screenArea, ShortPoint2D centerPosition) {
		if (minimap != null) {
			minimap.setMapViewport(screenArea);
		}
		if (context != null) {
			mainPanel.setMapViewport(screenArea, centerPosition, context.getMap());
		}
	}

	@Override
	public void action(IAction action) {
	}

	private ShortPoint2D startMapPosition;

	@Override
	public boolean handleDrawEvent(GODrawEvent event) {
		if (!containsPoint(event.getDrawPosition())) {
			return false;
		}

		Optional<Action> action = getActionForDraw(event);
		if (action.isPresent() && context != null && minimap != null) {
			float y = context.getScreen().getHeight() / 2;
			float x = context.getScreen().getWidth() / 2;
			startMapPosition = context.getPositionOnScreen(x, y);
			event.setHandler(new DrawMinimapHandler());
			return true;
		} else {
			return false;
		}
	}

	private Optional<Action> getActionForDraw(GODrawEvent event) {
		UIPoint position = event.getDrawPosition();

		float width = this.uiBase.getPosition().getWidth();
		float relativex = (float) position.getX() / width;
		float height = this.uiBase.getPosition().getHeight();
		float relativey = (float) position.getY() / height;

		return getScrollForMinimap(relativex, relativey);
	}

	/**
	 * This should one day display the chat.
	 */
	private final class ShowChatAction extends ExecutableAction {
		private final MessageContent messageContent = new MessageContent(
				"This is not yet implemented.",
				"Cancel",
				new ExecutableAction() {
					@Override
					public void execute() {
						mainPanel.setContent(ContentType.BUILD_NORMAL);
						chatButton.setActive(false);
					}
				},
				"Ok",
				new ExecutableAction() {
					@Override
					public void execute() {
						mainPanel.setContent(ContentType.BUILD_NORMAL);
						chatButton.setActive(false);
					}
				});

		@Override
		public void execute() {
			mainPanel.setContent(messageContent);
			chatButton.setActive(true); // TODO needs to be unset when content changes.
		}
	}

	/**
	 * This class handles a draw action (mouse pressed and moved) on the mini map.
	 * 
	 * @author Michael Zangl
	 */
	private class DrawMinimapHandler implements GOModalEventHandler {
		@Override
		public void phaseChanged(GOEvent event) {
		}

		@Override
		public void finished(GOEvent event) {
			eventDataChanged(event);
		}

		@Override
		public void aborted(GOEvent event) {
			if (startMapPosition != null) {
				minimap.getContext().scrollTo(startMapPosition);
			}
		}

		@Override
		public void eventDataChanged(GOEvent event) {
			getActionForDraw((GODrawEvent) event)
					.filter(action -> action.getActionType() == EActionType.PAN_TO)
					.ifPresent(action -> minimap.getContext().scrollTo(
							((PointAction) action).getPosition()));
		}

	}

	@Override
	public void displaySelection(ISelectionSet selection) {
		if (selection == null || selection.getSize() == 0) {
			if (!lastSelectionWasNull) {
				lastSelectionWasNull = true;
				if (mainPanel.isSelectionActive()) {
					mainPanel.setContent(ContentType.EMPTY);
				}
			} // else: nothing to do
		} else {
			lastSelectionWasNull = false;

			switch (selection.getSelectionType()) {
			case PEOPLE:
				mainPanel.setContent(new PeopleSelectionContent(player, selection));
				break;
			case SOLDIERS:
				mainPanel.setContent(new SoldierSelectionContent(selection));
				break;
			case SPECIALISTS:
				mainPanel.setContent(new SpecialistSelectionContent(selection));
				break;
			case PRIESTS:
				mainPanel.setContent(new PriestSelectionContent(selection));
				break;
			case SHIPS:
				mainPanel.setContent(new ShipSelectionContent(selection));
				break;
			case BUILDING:
				mainPanel.setContent(new BuildingSelectionContent(selection));
				break;
			default:
				System.err
						.println("got Selection but couldn't handle it! SelectionType: "
								+ selection.getSelectionType());
				break;
			}
		}
	}

	@Override
	public void setDrawContext(ActionFireable actionFireable, MapDrawContext context) {
		this.context = context;
		this.minimap = new Minimap(context, minimapSettings);
	}

	@Override
	public IAction replaceAction(IAction action) {
		return mainPanel.catchAction(action);
	}

	@Override
	public String getMapTooltip(ShortPoint2D point) {
		return null;
	}

	@Override
	public void stop() {
		minimap.stop();
	}
}
