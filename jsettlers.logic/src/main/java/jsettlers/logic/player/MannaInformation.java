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
package jsettlers.logic.player;

import java.io.Serializable;

import jsettlers.common.movable.EMovableType;
import jsettlers.common.movable.ESoldierType;
import jsettlers.common.movable.ESpellType;
import jsettlers.common.player.ECivilisation;
import jsettlers.common.player.IMannaInformation;

/**
 * @author codingberlin
 */
public class MannaInformation implements Serializable, IMannaInformation {
	private static final long serialVersionUID = -2354905349487371884L;

	private static final EMovableType[] BOWMAN_TYPES    = {EMovableType.BOWMAN_L1, EMovableType.BOWMAN_L2, EMovableType.BOWMAN_L3};
	private static final EMovableType[] SWORDSMAN_TYPES = {EMovableType.SWORDSMAN_L1, EMovableType.SWORDSMAN_L2, EMovableType.SWORDSMAN_L3};
	private static final EMovableType[] PIKEMAN_TYPES   = {EMovableType.PIKEMAN_L1, EMovableType.PIKEMAN_L2, EMovableType.PIKEMAN_L3};

	private static final byte    MAXIMUM_LEVEL               = 2;
	private static final short[] NECESSARY_MANNA_FOR_UPGRADE = {10, 20, 30, 50, 60, 30};

	private final byte[] levelOfTypes = {0, 0, 0};

	private short[] spellUsedCount                 = new short[ESpellType.values().length];
	private short   totalManna                     = 0;
	private short   manna                          = 0;
	private short   numberOfUpgradesExecuted       = 0;
	private boolean isMannaIncreasableByBigTemples = true;

	private final ECivilisation civilisation;

	public MannaInformation(ECivilisation civilisation) {
		this.civilisation = civilisation;
	}

	public void increaseManna() {
		totalManna = ++manna;
	}

	public void increaseMannaByBigTemple() {
		if (isMannaIncreasableByBigTemples) {
			totalManna = manna += 8;
		}
	}

	public void stopFutureManaIncreasingByBigTemple() {
		isMannaIncreasableByBigTemples = false;
	}

	@Override
	public boolean isUpgradePossible(ESoldierType type) {
		return getLevel(type) != MAXIMUM_LEVEL && isMannaAvailableForUpgrade() && noLevelBelow(getLevel(type));
	}

	private boolean noLevelBelow(byte level) {
		for (byte levelOfType : levelOfTypes) {
			if (levelOfType < level) {
				return false;
			}
		}
		return true;
	}

	@Override
	public byte getLevel(ESoldierType type) {
		return levelOfTypes[type.ordinal()];
	}

	public void upgrade(ESoldierType type) {
		if (isUpgradePossible(type)) {
			manna -= NECESSARY_MANNA_FOR_UPGRADE[numberOfUpgradesExecuted];
			numberOfUpgradesExecuted++;
			levelOfTypes[type.ordinal()]++;
		}
	}

	@Override
	public byte getNextUpdateProgressPercent() {
		if (numberOfUpgradesExecuted == NECESSARY_MANNA_FOR_UPGRADE.length) {
			return 0;
		}
		return sanitizePercent((float) manna / NECESSARY_MANNA_FOR_UPGRADE[numberOfUpgradesExecuted]);
	}

	@Override
	public byte getMaximumLevel() {
		return MAXIMUM_LEVEL;
	}

	@Override
	public short getAmountOfManna() {
		return manna;
	}

	@Override
	public short getAmountOfProducedManna() {
		return totalManna;
	}


	@Override
	public short getSpellCost(ESpellType spell) {
		return spell.getMannaCost(spellUsedCount[spell.ordinal()]);
	}

	@Override
	public boolean canUseSpell(ESpellType spell) {
		return spell.availableForCiv(civilisation) && manna >= getSpellCost(spell);
	}

	@Override
	public boolean useSpell(ESpellType spell) {
		if(canUseSpell(spell)) {
			manna -= getSpellCost(spell);
			spellUsedCount[spell.ordinal()]++;
			return true;
		}
		return false;
	}

	private byte sanitizePercent(float percent) {
		if (percent == 0) {
			return 0;
		}
		if (percent > 1) {
			return 100;
		}
		return (byte) (percent * 100);
	}

	public EMovableType getSoldierMovableFor(ESoldierType type) {
		byte levelOfType = getLevel(type);

		switch (type) {
			case BOWMAN:
				return BOWMAN_TYPES[levelOfType];
			case SWORDSMAN:
				return SWORDSMAN_TYPES[levelOfType];
			case PIKEMAN:
				return PIKEMAN_TYPES[levelOfType];
			default:
				throw new IllegalArgumentException("Unknown SoldierType: " + type);
		}
	}

	private boolean isMannaAvailableForUpgrade() {
		return manna >= NECESSARY_MANNA_FOR_UPGRADE[numberOfUpgradesExecuted];
	}

}
