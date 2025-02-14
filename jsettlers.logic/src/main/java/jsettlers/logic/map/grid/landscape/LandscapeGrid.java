/*******************************************************************************
 * Copyright (c) 2015 - 2017
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"),
 * to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
 * DEALINGS IN THE SOFTWARE.
 *******************************************************************************/
package jsettlers.logic.map.grid.landscape;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;

import jsettlers.algorithms.partitions.IBlockingProvider;
import jsettlers.algorithms.previewimage.IPreviewImageDataSupplier;
import jsettlers.common.landscape.ELandscapeType;
import jsettlers.common.landscape.EResourceType;
import jsettlers.common.map.IGraphicsBackgroundListener;
import jsettlers.common.map.shapes.HexGridArea;
import jsettlers.common.movable.EDirection;
import jsettlers.common.movable.ESpellType;
import jsettlers.common.position.RelativePoint;
import jsettlers.common.position.ShortPoint2D;
import jsettlers.logic.constants.Constants;
import jsettlers.logic.constants.MatchConstants;
import jsettlers.logic.map.grid.MainGrid;
import jsettlers.logic.map.grid.flags.IProtectedProvider;
import jsettlers.logic.map.grid.flags.IProtectedProvider.IProtectedChangedListener;

/**
 * This grid stores the height and the {@link ELandscapeType} of every position.
 *
 * @author Andreas Eberle
 */
public final class LandscapeGrid implements Serializable, IWalkableGround, IFlattenedResettable, IDebugColorSetable, IProtectedChangedListener, IBlockingProvider {
	private static final long serialVersionUID = -751261669662036484L;

	public static final short SEA_BLOCKED_PARTITION = 0;

	private final byte[][] heightGrid;
	private final byte[] landscapeGrid;
	private final byte[] resourceAmount;
	private final byte[] temporaryFlatened;
	private final byte[] resourceType;
	private final short[] blockedPartitions;

	private final short width;
	private final short height;

	private final IProtectedProvider protectedProvider;
	private final FlattenedResetter flattenedResetter;

	private transient int[] debugColors;
	private transient IGraphicsBackgroundListener backgroundListener;

	public LandscapeGrid(short width, short height, IProtectedProvider protectedProvider) {
		this.width = width;
		this.height = height;
		this.protectedProvider = protectedProvider;
		final int tiles = width * height;
		this.heightGrid = new byte[width][height];
		this.landscapeGrid = new byte[tiles];
		this.resourceAmount = new byte[tiles];
		this.resourceType = new byte[tiles];
		this.temporaryFlatened = new byte[tiles];
		this.blockedPartitions = new short[tiles];

		initDebugColors();

		this.flattenedResetter = new FlattenedResetter(this);
		setBackgroundListener(null);

		protectedProvider.setProtectedChangedListener(this);
	}

	private void readObject(ObjectInputStream ois) throws IOException, ClassNotFoundException {
		ois.defaultReadObject();
		setBackgroundListener(null);

		initDebugColors();
	}

	private void initDebugColors() {
		if (MatchConstants.ENABLE_DEBUG_COLORS) {
			this.debugColors = new int[width * height];
		} else {
			this.debugColors = null;
		}
	}

	public final byte getHeightAt(int x, int y) {
		return heightGrid[x][y];
	}

	public byte[][] getHeightArray() {
		return heightGrid;
	}

	public final ELandscapeType getLandscapeTypeAt(int x, int y) {
		return ELandscapeType.VALUES[landscapeGrid[x + y * width]];
	}

	private boolean isLandscapeOf(int x, int y, ELandscapeType... landscapeTypes) {
		ELandscapeType landscapeType = getLandscapeTypeAt(x, y);
		for (ELandscapeType curr : landscapeTypes) {
			if (landscapeType == curr) {
				return true;
			}
		}
		return false;
	}

	public boolean isHexAreaOfType(int x, int y, int maxRadius, ELandscapeType... landscapeTypes) {
		// inline of
		// int minRadius = 0;
		// int maxRadius = radius;
		// HexGridArea.stream(x, y, minRadius, maxRadius)
		//				.filter((currX, currY) -> !isLandscapeOf(currX, currY, landscapeTypes))
		//				.isEmpty();

		if(!isLandscapeOf(x, y, landscapeTypes)) {
			return false;
		}


		for (int radius = 0; radius <= maxRadius; radius++) {
			for (int direction = 0; direction < EDirection.NUMBER_OF_DIRECTIONS; direction++) {
				for (int step = 0; step < radius; step++) {
					x += HexGridArea.DIRECTION_INCREASE_X[direction];
					y += HexGridArea.DIRECTION_INCREASE_Y[direction];

					if (!isLandscapeOf(x, y, landscapeTypes)) {
						return false;
					}
				}
			}
			y--; // go to next radius / go one NORTH_EAST
		}
		return true;
	}

	@Override
	public final void setDebugColor(int x, int y, int argb) {
		if (MatchConstants.ENABLE_DEBUG_COLORS) {
			debugColors[x + y * width] = argb;
		}
	}

	public final int getDebugColor(int x, int y) {
		if (MatchConstants.ENABLE_DEBUG_COLORS) {
			return debugColors[x + y * width];
		} else {
			return 0;
		}
	}

	public final void resetDebugColors() {
		if (MatchConstants.ENABLE_DEBUG_COLORS) {
			for (int i = 0; i < debugColors.length; i++) {
				debugColors[i] = 0;
			}
		}
	}

	public final boolean canChangeLandscapeTo(int x, int y, ELandscapeType landscapeType) {
		for (EDirection dir : EDirection.VALUES) {
			int nbIndex = (x + dir.gridDeltaX) + (y + dir.gridDeltaY) * width;
			if (nbIndex < 0 || nbIndex >= width * height) continue;

			int dirX = x + dir.gridDeltaX;
			int dirY = y + dir.gridDeltaY;

			if(!ELandscapeType.values()[landscapeGrid[dirX+dirY*width]].isAllowedNeighbor(landscapeType)) return false;
		}
		return true;
	}

	public final void setLandscapeTypeAt(int x, int y, ELandscapeType landscapeType, boolean checked) {
		if(checked && !canChangeLandscapeTo(x, y, landscapeType)) return;

		if (landscapeType == ELandscapeType.FLATTENED && this.landscapeGrid[x + y * width] != ELandscapeType.FLATTENED.ordinal) {
			flattenedResetter.addPosition(x, y);
		}

		this.landscapeGrid[x + y * width] = landscapeType.ordinal;
		backgroundListener.backgroundLineChangedAt(x, y, 1);
	}

	public final void setHeightAt(short x, short y, byte height) {
		this.heightGrid[x][y] = height;
		backgroundListener.backgroundLineChangedAt(x, y, 1);
	}

	public void flattenAndChangeHeightTowards(int x, int y, byte targetHeight) {
		final int index = x + y * width;

		this.heightGrid[x][y] += Math.signum(targetHeight - this.heightGrid[x][y]);
		if(canChangeLandscapeTo(x, y, ELandscapeType.FLATTENED)) {
			setLandscapeTypeAt(x, y, ELandscapeType.FLATTENED, true);
		} else {
			setLandscapeTypeAt(x, y, ELandscapeType.FLATTENED_DESERT, true);
		}
		this.temporaryFlatened[index] = Byte.MAX_VALUE; // cancel the flattening

		backgroundListener.backgroundLineChangedAt(x, y, 1);
	}

	public final void setBackgroundListener(IGraphicsBackgroundListener backgroundListener) {
		if (backgroundListener != null) {
			this.backgroundListener = backgroundListener;
		} else {
			this.backgroundListener = new MainGrid.NullBackgroundListener();
		}
	}

	public final void setResourceAt(short x, short y, EResourceType resourceType, byte amount) {
		this.resourceType[x + y * width] = resourceType.ordinal;
		this.resourceAmount[x + y * width] = (byte) Math.min(amount, Constants.MAX_RESOURCE_AMOUNT_PER_POSITION);
	}

	/**
	 * gets the resource amount at the given position
	 *
	 * @param x
	 * 		x coordinate
	 * @param y
	 * 		y coordinate
	 * @return The amount of resources, where 0 is no resources and @link Byte.MAX_VALUE means full resources.
	 */
	public final byte getResourceAmountAt(int x, int y) {
		return resourceAmount[x + y * width];
	}

	public final EResourceType getResourceTypeAt(int x, int y) {
		return EResourceType.VALUES[resourceType[x + y * width]];
	}

	public int getAmountOfResource(EResourceType resource, Iterable<ShortPoint2D> positions) {
		int amount = 0;
		for (ShortPoint2D position : positions) {
			int index = position.x + position.y * width;
			if (resourceType[index] == resource.ordinal) {
				amount += resourceAmount[index];
			}
		}
		return amount;
	}

	public boolean tryTakingResource(ShortPoint2D position, EResourceType resource) {
		int idx = position.x + position.y * width;
		if (resourceType[idx] == resource.ordinal && resourceAmount[idx] > 0) {
			resourceAmount[idx]--;
			return true;
		} else {
			return false;
		}
	}

	public boolean tryCursingLocation(ShortPoint2D at) {
		int idx = at.x + at.y * width;
		if(landscapeGrid[idx] != ELandscapeType.MOUNTAIN.ordinal) return false;

		setResourceAt(at.x, at.y, EResourceType.values()[resourceType[idx]], (byte) (resourceAmount[idx]*ESpellType.CURSE_MOUNTAIN_RESOURCE_MOD));
		return true;
	}

	public boolean trySummonFish(ShortPoint2D at) {
		int idx = at.x + at.y * width;
		if(landscapeGrid[idx] != ELandscapeType.WATER1.ordinal) return false;

		setResourceAt(at.x, at.y, EResourceType.FISH, (byte) (resourceAmount[idx]+ESpellType.SUMMON_FISH_RESOURCE_ADD));
		return true;
	}

	@Override
	public final void walkOn(int x, int y) {
		int i = x + y * width;
		if (temporaryFlatened[i] < 100) {
			temporaryFlatened[i] += 3;
			if (temporaryFlatened[i] > 20) {
				flatten(x, y);
			}
		}
	}

	/**
	 * Sets the landscape to flattened after a settler walked on it.
	 *
	 * @param x
	 * 		x coordinate
	 * @param y
	 * 		y coordinate
	 */
	private void flatten(int x, int y) {
		if (isHexAreaOfType(x, y, 1, ELandscapeType.GRASS, ELandscapeType.FLATTENED)) {
			setLandscapeTypeAt((short) x, (short) y, ELandscapeType.FLATTENED, true);
		}
	}

	@Override
	public boolean countFlattenedDown(short x, short y) {
		if (protectedProvider.isProtected(x, y)) {
			return true; // remove the position from the unflattener
		}

		final int index = x + y * width;

		byte flattenedValue = temporaryFlatened[index];

		if (flattenedValue == Byte.MAX_VALUE) { // the unflattening has been canceled.
			return true; // tell the flattened resetter that it does not need to work on this pos again.
		}

		// count down the value
		flattenedValue--;
		temporaryFlatened[index] = flattenedValue;
		if (flattenedValue <= -30) { // if the value is smaller than the hysteresis, set it to zero
			temporaryFlatened[index] = 0;
			setLandscapeTypeAt(x, y, ELandscapeType.GRASS, true);
			return true; // tell the flattened resetter that it does not need to work on this pos again.
		} else {
			return false;
		}
	}

	public void setBlockedPartition(short x, short y, short blockedPartition) {
		this.blockedPartitions[x + y * width] = blockedPartition;
	}

	public short getBlockedPartitionAt(int x, int y) {
		return this.blockedPartitions[x + y * width];
	}

	public boolean isBlockedPartition(int x, int y) {
		return isBlocked(x, y);
	}

	@Override
	public boolean isBlocked(int x, int y) {
		return getBlockedPartitionAt(x, y) == 0;
	}

	public IPreviewImageDataSupplier getPreviewImageDataSupplier() {
		return new IPreviewImageDataSupplier() {
			@Override
			public byte getLandscapeHeight(short x, short y) {
				return getHeightAt(x, y);
			}

			@Override
			public ELandscapeType getLandscape(short x, short y) {
				return getLandscapeTypeAt(x, y);
			}
		};
	}

	/**
	 * This method activates the unflattening process. This causes a flattened position to be turned into grass after a while.
	 *
	 * @param x
	 * 		X coordinate of the position.
	 * @param y
	 * 		Y coordinate of the position.
	 */
	private void activateUnflattening(int x, int y) {
		ELandscapeType landscapeType = getLandscapeTypeAt(x, y);
		if (landscapeType != ELandscapeType.FLATTENED && landscapeType != ELandscapeType.FLATTENED_DESERT) {
			return; // do not unflatten mountain or desert.
		}

		this.temporaryFlatened[x + y * width] = (byte) (40 + MatchConstants.random().nextFloat() * 80);
		this.flattenedResetter.addPosition(x, y);
	}

	public boolean isAreaFlattenedAtHeight(ShortPoint2D position, RelativePoint[] positions, byte expectedHeight) {
		for (RelativePoint currPos : positions) {
			int x = currPos.calculateX(position.x);
			int y = currPos.calculateY(position.y);
			int index =x + y * width;

			if (heightGrid[x][y] != expectedHeight || (canChangeLandscapeTo(x, y, ELandscapeType.FLATTENED) && landscapeGrid[index] != ELandscapeType.FLATTENED.ordinal)) {
				return false;
			}
		}
		return true;
	}

	@Override
	public void protectedChanged(int x, int y, boolean newProtectedState) {
		if (!newProtectedState) {
			activateUnflattening(x, y);
		}
	}
}
