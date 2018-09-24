/*
 * Copyright (c) 2018 Adrian Siekierka
 *
 * This file is part of Nadeshicodec.
 *
 * Nadeshicodec is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Nadeshicodec is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Nadeshicodec.  If not, see <http://www.gnu.org/licenses/>.
 */

package pl.asie.nadeshicodec.util.oc;

public class OCCommandContext {
	private int currBg, currFg;
	private int contextId = 0;

	public OCCommandContext() {
		currBg = -1;
		currFg = -1;
	}

	public OCCommandContext copy(boolean ignoreId) {
		OCCommandContext ctx = new OCCommandContext();
		ctx.currBg = currBg;
		ctx.currFg = currFg;
		if (!ignoreId) {
			ctx.contextId = contextId;
		}
		return ctx;
	}

	public int getContextId() {
		return contextId;
	}

	public int getCurrBg() {
		return currBg;
	}

	public int getCurrFg() {
		return currFg;
	}

	public void setCurrBg(int currBg) {
		if (this.currBg != currBg) {
			this.currBg = currBg;
			this.contextId++;
		}
	}

	public void setCurrFg(int currFg) {
		if (this.currFg != currFg){
			this.currFg = currFg;
			this.contextId++;
		}
	}
}
