--[[
rin: nadeshicodec video player
Copyright (c) 2016, 2017, 2018 asie

Permission is hereby granted, free of charge, to any person obtaining a copy of
this software and associated documentation files (the "Software"), to deal in
the Software without restriction, including without limitation the rights to
use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies
of the Software, and to permit persons to whom the Software is furnished to do
so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
]]

local args = {...}
local component = require("component")
local computer = require("computer")
local event = require("event")
local gpu = component.gpu
local unicode = require("unicode")
local keyboard = require("keyboard")
local text = require("text")
local drive = component.drive

local pal = {}
local quad = {}
local strs = {}

quad[0] = " "
for i=1,255 do
	local dat = (i & 0x01) << 7
	dat = dat | (i & 0x02) >> 1 << 6
	dat = dat | (i & 0x04) >> 2 << 5
	dat = dat | (i & 0x08) >> 3 << 2
	dat = dat | (i & 0x10) >> 4 << 4
	dat = dat | (i & 0x20) >> 5 << 1
	dat = dat | (i & 0x40) >> 6 << 3
	dat = dat | (i & 0x80) >> 7
	quad[i] = unicode.char(0x2800 | dat)
end

strs[1] = {}
strs[1][0] = quad[0]
strs[1][1] = quad[255]

for i=2,160 do
	strs[i] = {}
	strs[i][0] = strs[i-1][0] .. strs[1][0]
	strs[i][1] = strs[i-1][1] .. strs[1][1]
end

for i=0,255 do
	if (i < 16) then
		pal[i] = (i * 15) << 16 | (i * 15) << 8 | (i * 15)
		gpu.setPaletteColor(i, pal[i])
	else
		local j = i - 16
		local b = math.floor((j % 5) * 255 / 4.0)
		local g = math.floor((math.floor(j / 5.0) % 8) * 255 / 7.0)
		local r = math.floor((math.floor(j / 40.0) % 6) * 255 / 5.0)
		pal[i] = r << 16 | g << 8 | b
	end
end

local maxBufSize = 1536+256
local buf = {}
local sec = 1
local sectorsReadTick = 0
local drawcallsTick = 0
local bufi = 1

local function addToBuf()
	table.insert(buf, drive.readSector(sec))
	sec = sec + 1
	sectorsReadTick = sectorsReadTick + 1
end

local function r8()
	if (#buf > 0) and (bufi > #(buf[1])) then
		table.remove(buf, 1)
		bufi = 1
	end

	if (#buf == 0) then
		addToBuf()
		if (#buf == 0) then
			return 0
		end
	end

	local v = string.byte(buf[1], bufi, bufi) & 255
	bufi = bufi + 1
	return v
end

local function r16()
	local x = r8()
	return x | (r8() << 8)
end

for i=1,maxBufSize do
	addToBuf()
end

local curr_bg = 0
local curr_fg = 0
local frame = 0

r8()
local frame_w = r8()
local frame_h = r8()
local frame_offset = computer.uptime()
local r_player = component.record_player

gpu.setResolution(frame_w, frame_h)

local function set_bg(v)
	if curr_bg ~= v then
		drawcallsTick = drawcallsTick + 2
		gpu.setBackground(pal[v], false)
		curr_bg = v
	end
end

local function set_fg(v)
	if curr_fg ~= v then
		drawcallsTick = drawcallsTick + 2
		gpu.setForeground(pal[v], false)
		curr_fg = v	
	end
end

local function cmd_fill(x,y,w,h,c)
	local chr = 0
	if curr_fg == c then
		chr = 1
	elseif curr_bg == c  then
		chr = 0
	else
		set_bg(c)
	end
	
	if w >= 2 and h >= 2 then
		drawcallsTick = drawcallsTick + 2
		gpu.fill(x+1,y+1,w,h,quad[chr*255])
	elseif w > 1 then
		drawcallsTick = drawcallsTick + 1
		gpu.set(x+1,y+1,strs[w][chr])
	else
		drawcallsTick = drawcallsTick + 1
		gpu.set(x+1,y+1,strs[h][chr],true)
	end
end

local function cmd_set(vertical,x,y,bg,fg,qs)
	local invert = 0
	if bg == curr_bg and fg == curr_fg then
		-- ok
	elseif bg == curr_fg and fg == curr_bg then
		invert = 255
	elseif bg == curr_bg then
		set_fg(fg)
	elseif fg == curr_bg then
		set_fg(bg)
		invert = 255
	elseif fg == curr_fg then
		set_bg(bg)
	elseif bg == curr_fg then
		set_bg(fg)
		invert = 255
	else
		set_bg(bg)
		set_fg(fg)
	end
	local qso = {}
	for i=1,#qs do
		qso[i] = quad[qs[i] ~ invert]
	end

	gpu.set(x+1,y+1,table.concat(qso),vertical)
	drawcallsTick = drawcallsTick + 1
end

while true do
	local cmd = r8(file)
	if cmd == 0x10 then
		local x = r8(file)		
		local y = r8(file)		
		local w = r8(file)		
		local h = r8(file)		
		local c = r8(file)
		cmd_fill(x,y,w,h,c)
	elseif cmd == 0x18 then
		local x = r8(file)		
		local y = r8(file)		
		local w = r8(file)		
		local c = r8(file)
		cmd_fill(x,y,w,1,c)
	elseif cmd == 0x19 then
		local x = r8(file)		
		local y = r8(file)		
		local h = r8(file)		
		local c = r8(file)
		cmd_fill(x,y,1,h,c)
	elseif cmd == 0x12 or cmd == 0x13 then
		local vertical = (cmd == 0x13)
		local x = r8(file)		
		local y = r8(file)		
		local w = r8(file)		
		local bg = r8(file)		
		local fg = r8(file)
		local qs = {}
		for i=1,w do
			qs[i] = r8(file)
		end
		cmd_set(vertical,x,y,bg,fg,qs)
	elseif cmd == 0x22 or cmd == 0x23 then
		local vertical = (cmd == 0x23)
		local x = r8(file)	
		local y = r8(file)	
		local bg = r8(file)	
		local fg = r8(file)
		local qs = {}
		local adding = true
		while adding do
			local cmd = r8(file)
			if cmd == 0x00 then adding = false
			elseif cmd >= 0xA1 and cmd <= 0xFF then
				local v = r8(file)
				for i=1,(cmd-0xA0) do table.insert(qs,v) end
			elseif cmd >= 0x01 and cmd <= 0xA0 then
				for i=1,cmd do
					table.insert(qs,r8(file))
				end
			end
		end
		cmd_set(vertical,x,y,bg,fg,qs)
	elseif cmd == 0x01 then
		if frame == 1 then
			if r_player ~= nil then
				r_player.stop()
				os.sleep(0.1)
				r_player.play()
				os.sleep(0.25)
			end
			frame_offset = computer.uptime() + 0.05
		else
			frame_offset = frame_offset + 0.05
		end
		frame = frame + 1

		--print(drawcallsTick)
		while computer.uptime() <= frame_offset do
			if (sectorsReadTick == 0) and (#buf < maxBufSize) then
				local bufAddCost = 10
				while drawcallsTick < (255-bufAddCost) do
					addToBuf()
					drawcallsTick = drawcallsTick + bufAddCost
				end
			end
			os.sleep(0.05)
			sectorsReadTick = 0
			drawcallsTick = 0
		end
	else
		error("unknown opcode " .. cmd)
	end
end
