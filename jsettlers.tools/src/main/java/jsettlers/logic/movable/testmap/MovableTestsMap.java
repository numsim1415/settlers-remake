/*******************************************************************************
 * Copyright (c) 2015 - 2018
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
package jsettlers.logic.movable.testmap;

import java.util.LinkedList;

import java8.util.Objects;
import jsettlers.algorithms.path.IPathCalculatable;
import jsettlers.algorithms.path.Path;
import jsettlers.algorithms.path.astar.BucketQueueAStar;
import jsettlers.algorithms.path.astar.IAStarPathMap;
import jsettlers.common.Color;
import jsettlers.common.CommonConstants;
import jsettlers.common.landscape.ELandscapeType;
import jsettlers.common.landscape.EResourceType;
import jsettlers.common.map.EDebugColorModes;
import jsettlers.common.map.IGraphicsBackgroundListener;
import jsettlers.common.map.IGraphicsGrid;
import jsettlers.common.map.partition.IPartitionData;
import jsettlers.common.mapobject.EMapObjectType;
import jsettlers.common.mapobject.IMapObject;
import jsettlers.common.material.EMaterialType;
import jsettlers.common.material.ESearchType;
import jsettlers.common.movable.EDirection;
import jsettlers.common.movable.IGraphicsMovable;
import jsettlers.common.player.IPlayer;
import jsettlers.common.position.ShortPoint2D;
import jsettlers.logic.constants.MatchConstants;
import jsettlers.logic.map.grid.partition.manager.manageables.IManageableBearer;
import jsettlers.logic.map.grid.partition.manager.manageables.IManageableBricklayer;
import jsettlers.logic.map.grid.partition.manager.manageables.IManageableDigger;
import jsettlers.logic.map.grid.partition.manager.manageables.IManageableWorker;
import jsettlers.logic.map.grid.partition.manager.materials.interfaces.IMaterialOffer;
import jsettlers.logic.map.grid.partition.manager.materials.interfaces.IMaterialRequest;
import jsettlers.logic.map.grid.partition.manager.materials.offers.EOfferPriority;
import jsettlers.logic.movable.interfaces.AbstractMovableGrid;
import jsettlers.logic.movable.interfaces.IAttackable;
import jsettlers.logic.movable.interfaces.IAttackableMovable;
import jsettlers.logic.movable.interfaces.ILogicMovable;
import jsettlers.logic.objects.stack.StackMapObject;
import jsettlers.logic.player.Player;

public class MovableTestsMap implements IGraphicsGrid, IAStarPathMap {

	private final short  width;
	private final short  height;
	private final Player defaultPlayer;

	private final ILogicMovable    movableMap[][];
	private final EMaterialType    materialTypeMap[][];
	private final byte             materialAmountMap[][];
	private final BucketQueueAStar aStar;

	public MovableTestsMap(int width, int height, Player defaultPlayer) {
		this.width = (short) width;
		this.height = (short) height;
		this.defaultPlayer = defaultPlayer;

		this.movableMap = new ILogicMovable[width][height];
		this.materialTypeMap = new EMaterialType[width][height];
		this.materialAmountMap = new byte[width][height];

		aStar = new BucketQueueAStar(this, this.width, this.height);
	}

	@Override
	public short getHeight() {
		return height;
	}

	@Override
	public short getWidth() {
		return width;
	}

	@Override
	public IGraphicsMovable getMovableAt(int x, int y) {
		return movableMap[x][y];
	}

	@Override
	public IMapObject getVisibleMapObjectsAt(int x, int y) {
		if (materialTypeMap[x][y] != null && materialAmountMap[x][y] > 0) {
			return new StackMapObject(materialTypeMap[x][y], materialAmountMap[x][y]);
		} else {
			return null;
		}
	}

	@Override
	public byte getVisibleHeightAt(int x, int y) {
		return 0;
	}

	@Override
	public ELandscapeType getVisibleLandscapeTypeAt(int x, int y) {
		return ELandscapeType.GRASS;
	}

	@Override
	public int getDebugColorAt(int x, int y, EDebugColorModes debugColorMode) {
		return -1;
	}

	@Override
	public boolean isBorder(int x, int y) {
		return false;
	}

	@Override
	public IPlayer getPlayerAt(int x, int y) {
		return IPlayer.DEFAULT_DUMMY_PLAYER0;
	}

	@Override
	public byte getVisibleStatus(int x, int y) {
		return CommonConstants.FOG_OF_WAR_VISIBLE;
	}

	@Override
	public void setBackgroundListener(IGraphicsBackgroundListener backgroundListener) {
	}

	private final AbstractMovableGrid movableGrid = new AbstractMovableGrid() {
		private static final long serialVersionUID = 610513829074598238L;

		@Override
		public void leavePosition(ShortPoint2D position, ILogicMovable movable) {
			if (movableMap[position.x][position.y] == movable) {
				movableMap[position.x][position.y] = null;
			}
		}

		@Override
		public boolean hasNoMovableAt(int x, int y) {
			return isInBounds(x, y) && movableMap[x][y] == null;
		}

		@Override
		public boolean isFreePosition(int x, int y) {
			return isInBounds(x, y) && !isBlocked(x, y) && movableMap[x][y] == null;
		}

		@Override
		public boolean isInBounds(int x, int y) {
			return 0 <= x && x < width && 0 <= y && y < height;
		}

		@Override
		public Path calculatePathTo(IPathCalculatable pathRequester, ShortPoint2D targetPos) {
			return aStar.findPath(pathRequester, targetPos);
		}

		@Override
		public Path calculatePathTo(IPathCalculatable pathCalculatable, ShortPoint2D targetPos, ShortPoint2D startPos) {
			return aStar.findPath(pathCalculatable, targetPos, startPos);
		}

		@Override
		public void addJobless(IManageableBearer bearer) {
			if (!materials.isEmpty()) {
				ShortPoint2D source = materials.pop();
				final ShortPoint2D targetPos = new ShortPoint2D(MatchConstants.random().nextInt(width), MatchConstants.random().nextInt(height));
				bearer.deliver(materialTypeMap[source.x][source.y], new IMaterialOffer() {
					@Override
					public void distributionAccepted() {
					}

					@Override
					public void distributionAborted() {
					}

					@Override
					public void offerTaken() {
					}

					@Override
					public boolean isStillValid(EOfferPriority minimumAcceptedPriority) {
						return true;
					}

					@Override
					public ShortPoint2D getPosition() {
						return source;
					}
				}, new IMaterialRequest() {

					@Override
					public ShortPoint2D getPosition() {
						return targetPos;
					}

					@Override
					public boolean isActive() {
						return true;
					}

					@Override
					public EOfferPriority getMinimumAcceptedOfferPriority() {
						return EOfferPriority.LOWEST;
					}

					@Override
					public void deliveryFulfilled() {
					}

					@Override
					public void deliveryAccepted() {
					}

					@Override
					public void deliveryAborted() {
					}
				});
			}
		}

		private final LinkedList<ShortPoint2D> materials = new LinkedList<>();

		@Override
		public boolean takeMaterial(ShortPoint2D pos, EMaterialType materialType) {
			if (materialTypeMap[pos.x][pos.y] == materialType && materialAmountMap[pos.x][pos.y] > 0) {
				materialAmountMap[pos.x][pos.y]--;
				return true;
			} else {
				return false;
			}
		}

		@Override
		public EMaterialType takeMaterial(ShortPoint2D pos) {
			if (materialAmountMap[pos.x][pos.y] > 0) {
				materialAmountMap[pos.x][pos.y]--;
				return materialTypeMap[pos.x][pos.y];
			} else {
				return null;
			}
		}

		@Override
		public boolean dropMaterial(ShortPoint2D pos, EMaterialType materialType, boolean offer, boolean forced) {
			materialTypeMap[pos.x][pos.y] = materialType;
			materialAmountMap[pos.x][pos.y]++;

			materials.add(pos);

			return true;
		}

		@Override
		public ILogicMovable getMovableAt(int x, int y) {
			return movableMap[x][y];
		}

		@Override
		public boolean isBlocked(int x, int y) {
			return false;
		}

		@Override
		public void addJobless(IManageableWorker worker) {
		}

		@Override
		public void addJobless(IManageableDigger digger) {
		}

		@Override
		public EDirection getDirectionOfSearched(ShortPoint2D position, ESearchType searchType) {
			return null;
		}

		@Override
		public EMaterialType popToolProductionRequest(ShortPoint2D pos) {
			return null;
		}

		@Override
		public void placePigAt(ShortPoint2D pos, boolean place) {
		}

		@Override
		public boolean hasPigAt(ShortPoint2D position) {
			return false;
		}

		@Override
		public boolean isPigAdult(ShortPoint2D position) {
			return false;
		}

		@Override
		public boolean feedDonkeyAt(ShortPoint2D position) {
			return false;
		}

		@Override
		public void placeSmoke(ShortPoint2D position, EMapObjectType type, short smokeDuration) {
		}

		@Override
		public boolean canPushMaterial(ShortPoint2D position) {
			return false;
		}

		@Override
		public boolean canTakeMaterial(ShortPoint2D position, EMaterialType material) {
			return false;
		}

		@Override
		public byte getHeightAt(ShortPoint2D position) {
			return 0;
		}

		@Override
		public boolean isMarked(ShortPoint2D position) {
			return false;
		}

		@Override
		public void setMarked(ShortPoint2D position, boolean marked) {
		}

		@Override
		public Path searchDijkstra(IPathCalculatable pathCalculateable, short centerX, short centerY, short radius, ESearchType searchType) {
			return null;
		}

		@Override
		public Path searchInArea(IPathCalculatable pathCalculateable, short centerX, short centerY, short radius, ESearchType searchType) {
			return null;
		}

		@Override
		public void addJobless(IManageableBricklayer bricklayer) {
		}

		@Override
		public void changeHeightTowards(int x, int y, byte targetHeight) {
		}

		@Override
		public void setLandscape(int x, int y, ELandscapeType grass) {
		}

		@Override
		public boolean isValidPosition(IPathCalculatable pathCalculatable, int x, int y) {
			return isInBounds(x, y) && !isBlocked(x, y) && (!pathCalculatable.needsPlayersGround() || Objects.equals(pathCalculatable.getPlayer(), MovableTestsMap.this.getPlayerAt(x, y)));
		}

		@Override
		public boolean isProtected(int x, int y) {
			return false;
		}

		@Override
		public boolean isBlockedOrProtected(int x, int y) {
			return isBlocked(x, y) || isProtected(x, y);
		}

		@Override
		public boolean fitsSearchType(IPathCalculatable pathCalculateable, int x, int y, ESearchType searchType) {
			return false;
		}

		@Override
		public boolean executeSearchType(ILogicMovable movable, ShortPoint2D position, ESearchType searchType) {
			return false;
		}

		@Override
		public void changePlayerAt(ShortPoint2D pos, Player player) {
		}

		@Override
		public void removeJobless(IManageableBearer bearer) {
		}

		@Override
		public void removeJobless(IManageableWorker worker) {
		}

		@Override
		public void removeJobless(IManageableDigger digger) {
		}

		@Override
		public void removeJobless(IManageableBricklayer bricklayer) {
		}

		@Override
		public boolean canChangeLandscapeTo(int x, int y, ELandscapeType type) {
			return false;
		}

		@Override
		public ELandscapeType getLandscapeTypeAt(int x, int y) {
			return ELandscapeType.GRASS;
		}

		@Override
		public IAttackable getEnemyInSearchArea(ShortPoint2D centerPos, IAttackable movable, short minSearchRadius, short maxSearchRadius, boolean includeTowers) {
			return null;
		}

		@Override
		public void enterPosition(ShortPoint2D position, ILogicMovable movable, boolean informFullArea) {
			movableMap[position.x][position.y] = movable;
		}

		@Override
		public void notifyAttackers(ShortPoint2D position, IAttackableMovable movable, boolean informFullArea) {
		}

		@Override
		public void addSelfDeletingMapObject(ShortPoint2D position, EMapObjectType mapObjectType, float duration, Player player) {
		}

		@Override
		public void addSelfDeletingMapObject(ShortPoint2D point, int sound, int animation, float duration, Player player) {
		}

		@Override
		public void addEyeMapObject(ShortPoint2D position, short radius, float duration, Player player) {
		}

		@Override
		public ShortPoint2D calcDecentralizeVector(short x, short y) {
			return new ShortPoint2D(0, 0);
		}

		@Override
		public void addArrowObject(ShortPoint2D attackedPos, ShortPoint2D shooterPos, byte shooterPlayerId, float hitStrength) {
		}

		@Override
		public Player getPlayerAt(ShortPoint2D position) {
			return defaultPlayer;
		}

		@Override
		public boolean isValidNextPathPosition(IPathCalculatable pathCalculatable, ShortPoint2D nextPos, ShortPoint2D targetPos) {
			return isValidPosition(pathCalculatable, nextPos.x, nextPos.y);
		}

		@Override
		public int getWidth() {
			return width;
		}

		@Override
		public int getHeight() {
			return height;
		}

		@Override
		public boolean isWater(int x, int y) {
			return getLandscapeTypeAt(x, y).isWater;
		}

		@Override
		public boolean tryTakingResource(ShortPoint2D position, EResourceType resource) {
			return false;
		}

		@Override
		public boolean tryCursingLocation(ShortPoint2D position) {
			return false;
		}

		@Override
		public boolean trySummonFish(ShortPoint2D position) {
			return false;
		}
	};

	public AbstractMovableGrid getMovableGrid() {
		return movableGrid;
	}

	// ==================== IAStarPathMap ==============================================================

	@Override
	public boolean isBlocked(IPathCalculatable requester, int x, int y) {
		return false;
	}

	@Override
	public float getCost(IPathCalculatable requester, int sx, int sy, int tx, int ty) {
		return 1;
	}

	@Override
	public void markAsOpen(int x, int y) {
	}

	@Override
	public void markAsClosed(int x, int y) {
	}

	@Override
	public void setDebugColor(int x, int y, Color color) {
	}

	@Override
	public short getBlockedPartition(int x, int y) {
		return 1;
	}

	@Override
	public IPartitionData getPartitionData(int x, int y) {
		return null;
	}

	@Override
	public boolean isBuilding(int x, int y) {
		return false;
	}
}
