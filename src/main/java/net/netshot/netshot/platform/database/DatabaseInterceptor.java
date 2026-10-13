/**
 * Copyright 2013-2025 Netshot
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
package net.netshot.netshot.platform.database;

import java.io.Serializable;
import java.util.Date;

import org.apache.commons.lang3.ArrayUtils;
import org.hibernate.Interceptor;
import org.hibernate.type.Type;

class DatabaseInterceptor implements Interceptor {

	@Override
	public boolean onFlushDirty(Object entity, Serializable id, Object[] currentState, Object[] previousState,
		String[] propertyNames, Type[] types) {
		int indexOf = ArrayUtils.indexOf(propertyNames, "changeDate");
		if (indexOf != ArrayUtils.INDEX_NOT_FOUND) {
			currentState[indexOf] = new Date(1000 * (System.currentTimeMillis() / 1000));
			return true;
		}
		return false;
	}

	@Override
	public boolean onSave(Object entity, Serializable id, Object[] state, String[] propertyNames, Type[] types) {
		int indexOf = ArrayUtils.indexOf(propertyNames, "changeDate");
		if (indexOf != ArrayUtils.INDEX_NOT_FOUND) {
			if (state[indexOf] == null) {
				state[indexOf] = new Date(1000 * (System.currentTimeMillis() / 1000));
			}
			return true;
		}
		return false;
	}

}
