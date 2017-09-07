#!/usr/bin/env python


"""

Copyright (C) 2017 The Android Open Source Project

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.


This receiver was tested only on Ubuntu.

$ sudo apt-get install vlc

IMPORTANT: you need to configure VLC player:
  Open VLC media player
  Go to Tools -> Preferences -> Switch to All (at the bottom)
  Input / Codecs -> Demuxers -> Select H.264

Configure Android device by enabling USB debugging.

$ adb forward tcp:5152 tcp:5151
$ python receiver.py

"""

from subprocess import Popen, PIPE, STDOUT
import socket

PORT = 5152

def displayVideoStream(proc, port):
    sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    sock.connect(('localhost', port))
    try:
        while True:
            data = sock.recv(1024)
            if data == None or len(data) <= 0:
                break
            proc.stdin.write(data)

    finally:
        sock.close()

if __name__ == "__main__":
    p = Popen(['vlc','-vvv', ':demux=h264', '-'], stdin=PIPE, stdout=PIPE)
    try:
        displayVideoStream(p, PORT)
    finally:
        p.kill()
