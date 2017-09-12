## Requirments
* Gstreamer 1.0 with H264 decoder (h264parse, avdec_h264) 
```
$ sudo apt install libgstreamer1.0-0 gstreamer1.0-plugins-base gstreamer1.0-plugins-good gstreamer1.0-plugins-bad \
gstreamer1.0-plugins-ugly gstreamer1.0-libav gstreamer1.0-doc gstreamer1.0-tools
```

### Via WiFi
```
$ cd receiver
$ python cs_receiver.py
$ adb install renesas_castscreen.apk
$ ./start_castscreen_with_ip.sh [HOST_IP] #Start screencast to virtual display
$ ./start_maps_virtual.sh [VIRTUAL_DISPLAY_ID]  #Launch google maps on virtual display
```

### Via USB
```
$ cd receiver
$ adb forward tcp:53516 tcp:53515
$ python cs_receiver_conn.py
$ adb install renesas_castscreen.apk
$ ./start_castscreen_with_ip.sh [HOST_IP] #Start screencast to virtual display
$ ./start_maps_virtual.sh [VIRTUAL_DISPLAY_ID]  #Launch google maps on virtual display
```