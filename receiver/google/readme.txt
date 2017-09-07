 
As for networked display:

Attached sample (not final)  networked virtual display app. This project could be built and ran from Android Studio w/o platform signature permissions). 
As a high level idea - I use ADB as network protocol, I used VLC media player on receiving side (desktop machine) for simplicity, it requires some setup steps before running.
The instructions are in receiver.py file:

This receiver was tested only on Ubuntu.

$ sudo apt-get install vlc

IMPORTANT: you need to configure VLC player:
  Open VLC media player
  Go to Tools -> Preferences -> Switch to All (at the bottom)
  Input / Codecs -> Demuxers -> Select H.264

Configure Android device by enabling USB debugging.

$ adb forward tcp:5152 tcp:5151
$ python receiver.py