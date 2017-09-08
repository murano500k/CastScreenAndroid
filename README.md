## Requirments
* Gstreamer 1.0 with H264 decoder (h264parse, avdec_h264) 
```
$ apt install libgstreamer1.0-0 gstreamer1.0-plugins-base gstreamer1.0-plugins-good gstreamer1.0-plugins-bad \
gstreamer1.0-plugins-ugly gstreamer1.0-libav gstreamer1.0-doc gstreamer1.0-tools
```

### Via WiFi
1. Launch receiver
```
$ cd receiver
$ python cs_receiver.py
$ adb install renesas_castscreen.apk
```
2. Open CastScreen APP
3. Wait the receiver to appear on the list
4. Select the receiver
5. Tap **Start** on right corner

### Via USB
1. Enable debug mode on the Android device
2. Make sure adb is available on your PC
3. Open CastScreen APP
4. Select **Server mode**
5. Tap **Start** on right corner
6. Launch receiver
```
$ cd receiver
$ adb forward tcp:53516 tcp:53515
$ python cs_receiver_conn.py
```
