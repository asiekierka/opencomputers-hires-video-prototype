# Nadeshicodec

320x200 OC video converter project dump. Apologies for no real documentation, but it does have a GUI.

## Workspace

1. Download the correct platform binary JAR for FFMpeg for your platform [here](http://artifactory.movingblocks.net/artifactory/gradle-libs/org/bytedeco/javacpp-presets/ffmpeg/4.0.1-1.4.2/) and put it in libs/.
2. ./gradlew build
3. java -jar build/libs/Nadeshicodec-all.jar

## Usage

* cpdrive.lua will put a file on an unmanaged hard drive.
* rin.lua will play a video from an unmanaged hard drive.

As 320x200 videos are I/O-heavy, they need to be copied to a drive running in unmanaged mode!
