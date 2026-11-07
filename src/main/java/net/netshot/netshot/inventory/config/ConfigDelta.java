/**
 * Copyright 2013-present Netshot
 * 
 * This file is part of Netshot project.
 * 
 * Netshot is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * Netshot is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with Netshot.  If not, see <http://www.gnu.org/licenses/>.
 */
package net.netshot.netshot.inventory.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.github.difflib.patch.AbstractDelta;

/**
 * One difference between two versions of a configuration item.
 *
 * @param type the type of difference
 * @param originalPosition the position of the difference in the original lines
 * @param revisedPosition the position of the difference in the revised lines
 * @param originalLines the original lines
 * @param revisedLines the revised lines
 * @param preContext the (up to 3) original lines before the difference
 * @param postContext the (up to 3) original lines after the difference
 * @param hierarchy the (indentation-based) hierarchy of the original lines the difference is in
 */
public record ConfigDelta(
	Type type,
	int originalPosition,
	int revisedPosition,
	List<String> originalLines,
	List<String> revisedLines,
	List<String> preContext,
	List<String> postContext,
	List<LineWithPosition> hierarchy) {

	/**
	 * Type of difference.
	 */
	public enum Type {
		CHANGE,
		DELETE,
		INSERT;
	}

	/**
	 * A line of a configuration with its number (position).
	 *
	 * @param line the line
	 * @param position the position
	 */
	public record LineWithPosition(String line, int position) {
	}

	/**
	 * Builds a difference from a delta computed by the diff library.
	 *
	 * @param delta the delta
	 * @param allOldLines all the old lines
	 * @param oldLineParents the parents of the old lines (see {@link Config#getLineParents})
	 * @return the difference
	 */
	public static ConfigDelta of(AbstractDelta<String> delta, List<String> allOldLines, int[] oldLineParents) {
		Type type;
		switch (delta.getType()) {
			case INSERT:
				type = Type.INSERT;
				break;
			case DELETE:
				type = Type.DELETE;
				break;
			case CHANGE:
			default:
				type = Type.CHANGE;
		}
		int originalPosition = delta.getSource().getPosition();
		List<String> originalLines = delta.getSource().getLines();
		int revisedPosition = delta.getTarget().getPosition();
		List<String> revisedLines = delta.getTarget().getLines();
		List<String> preContext = allOldLines.subList(Math.max(originalPosition - 3, 0), originalPosition);
		List<String> postContext = allOldLines.subList(Math.min(originalPosition + originalLines.size(), allOldLines.size() - 1),
			Math.min(originalPosition + originalLines.size() + 3, allOldLines.size() - 1));
		List<LineWithPosition> hierarchy = new ArrayList<>();
		int p = originalPosition;
		if (p < oldLineParents.length) {
			p = oldLineParents[p];
			while (p >= 0) {
				hierarchy.add(new LineWithPosition(allOldLines.get(p), p));
				p = oldLineParents[p];
			}
		}
		Collections.reverse(hierarchy);
		return new ConfigDelta(type, originalPosition, revisedPosition, originalLines, revisedLines,
			preContext, postContext, hierarchy);
	}
}
