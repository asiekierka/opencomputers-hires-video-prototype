--[[
cpdrive: unmanaged drive copy tool
Copyright (c) 2018 asie

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
local drive = component.drive

print("Copying \"" .. args[1] .. "\" to drive...")

local file = io.open(args[1], "rb")
local d = true
local sec = 0

while d do
	local f = file:read(512)
	if f == nil then d = false
	else
		sec = sec + 1
		drive.writeSector(sec, f)
		if ((sec % 16) == 0) then
			print("Sector " .. sec)
		end
	end
end

print("Copied!")
