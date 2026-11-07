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

import java.io.File;

/**
 * The content of a configuration item, to be downloaded: either a text (an attribute of the
 * configuration holding a long text) or a binary file. Exactly one of {@code text} and
 * {@code file} is set.
 *
 * @param fileName the suggested name of the downloaded file
 * @param text the content of a text item, as UTF-8 bytes
 * @param file the file holding the content of a binary file item
 * @param length the length of the content, in bytes
 */
public record ConfigItem(String fileName, byte[] text, File file, long length) {
}
