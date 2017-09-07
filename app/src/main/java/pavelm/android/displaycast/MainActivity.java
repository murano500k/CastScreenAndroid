///*
// * Copyright (C) 2017 The Android Open Source Project
// *
// * Licensed under the Apache License, Version 2.0 (the "License");
// * you may not use this file except in compliance with the License.
// * You may obtain a copy of the License at
// *
// *      http://www.apache.org/licenses/LICENSE-2.0
// *
// * Unless required by applicable law or agreed to in writing, software
// * distributed under the License is distributed on an "AS IS" BASIS,
// * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// * See the License for the specific language governing permissions and
// * limitations under the License.
// */
//
//package pavelm.android.displaycast;
//
//import android.hardware.display.DisplayManager;
//import android.hardware.display.DisplayManager.DisplayListener;
//import android.hardware.display.VirtualDisplay;
//import android.hardware.display.VirtualDisplay.Callback;
//import android.media.MediaCodec;
//import android.media.MediaCodecInfo;
//import android.media.MediaFormat;
//import android.os.Bundle;
//import android.os.Handler;
//import android.renesas.castscreendemo.DummyPresentation;
//import android.renesas.castscreendemo.R;
//import android.renesas.castscreendemo.Utils;
//import android.support.v7.app.AppCompatActivity;
//import android.util.Log;
//import android.view.Display;
//import android.view.Surface;
//import android.widget.TextView;
//
//import java.io.IOException;
//import java.io.OutputStream;
//import java.net.ServerSocket;
//import java.net.Socket;
//import java.nio.ByteBuffer;
//
//public class MainActivity extends AppCompatActivity {
//
//    static final String TAG = "DisplayCast";
//
//    private DisplayManager mDisplayManager;
//    private VirtualDisplay mVirtualDisplay;
//    private DummyPresentation mPresentation;
//    private Surface mInputSurface;
//    private MediaCodec.BufferInfo mVideoBufferInfo;
//    private MediaCodec mVideoEncoder;
//
//    private TextView mLogView;
//
//    private static final String DISPLAY_NAME = "DisplayCast";
//    private static final int WIDTH = 1280;
//    private static final int HEIGHT = 720;
//    private static final int DPI = 320;
//    private static final int FPS = 25;
//    private static final int BITRATE = 6144000;
//    private static final int PORT = 5151;
//    private static final String MEDIA_FORMAT_MIMETYPE = MediaFormat.MIMETYPE_VIDEO_AVC;
//
//    @Override
//    protected void onCreate(Bundle savedInstanceState) {
//        super.onCreate(savedInstanceState);
//        setContentView(R.layout.activity_main);
//
//        mLogView = (TextView) findViewById(R.id.log);
//
//        mDisplayManager = getSystemService(DisplayManager.class);
//
//        createDummyDisplay();
//        createServerSocket();
//    }
//
//    private void createDummyDisplay() {
//        prepareVideoEncoder();
//        log("Encoder ready");
//        mVirtualDisplay = mDisplayManager.createVirtualDisplay(DISPLAY_NAME, WIDTH, HEIGHT, DPI,
//                mInputSurface, 0 /* flags */, new Callback() {
//                    @Override
//                    public void onResumed() {
//                        Log.i(TAG, "VirtualDisplay resumed");
//                    }
//
//                    @Override
//                    public void onPaused() {
//                        Log.i(TAG, "VirtualDisplay paused");
//                    }
//
//                    @Override
//                    public void onStopped() {
//                        Log.i(TAG, "VirtualDisplay stopped");
//                    }
//                }, null /* handler */);
//        mDisplayManager.registerDisplayListener(new DisplayListener() {
//            @Override
//            public void onDisplayAdded(int i) {
//                final Display display = mDisplayManager.getDisplay(i);
//                if (display != null && DISPLAY_NAME.equals(display.getName())) {
//                    onVirtualDisplayReady(display);
//                }
//            }
//
//            @Override public void onDisplayRemoved(int i) {}
//
//            @Override public void onDisplayChanged(int i) {}
//        }, new Handler());
//    }
//
//    private void log(final String text) {
//        Log.i(TAG, "Log: " + text);
//        mLogView.post(new Runnable() {
//            @Override
//            public void run() {
//                mLogView.setText(text + "\n" + mLogView.getText());
//            }
//        });
//    }
//
//    private void onVirtualDisplayReady(Display display) {
//        log("Background virtual display ready");
//        // We can just run an activity instead since Android O.
//        // See ActivityOptions.setLaunchDisplayId
//        mPresentation = new DummyPresentation(this, display);
//        mPresentation.show();
//        log("Presentation ready");
//    }
//
//    private void prepareVideoEncoder() {
//        mVideoBufferInfo = new MediaCodec.BufferInfo();
//        MediaFormat format = MediaFormat.createVideoFormat(MEDIA_FORMAT_MIMETYPE, WIDTH, HEIGHT);
//
//        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
//        //format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar);
//        format.setInteger(MediaFormat.KEY_BIT_RATE, BITRATE);
//        format.setInteger(MediaFormat.KEY_FRAME_RATE, FPS);
//        format.setInteger(MediaFormat.KEY_CAPTURE_RATE, FPS);
//        format.setInteger(MediaFormat.KEY_REPEAT_PREVIOUS_FRAME_AFTER, 1000000 / FPS);
//        format.setInteger(MediaFormat.KEY_CHANNEL_COUNT, 1);
//        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1); // 1 second between I-frames
//
//        try {
//            //mVideoEncoder = MediaCodec.createEncoderByType(MEDIA_FORMAT_MIMETYPE);
//            mVideoEncoder = MediaCodec.createByCodecName(Utils.getEncoderName(format));
//            mVideoEncoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
//            mInputSurface = mVideoEncoder.createInputSurface();
//            mVideoEncoder.start();
//        } catch (IOException e) {
//            throw new RuntimeException(e);
//        }
//    }
//
//
//    private void encoderLoop(OutputStream outputStream) {
//        if (outputStream == null) {
//            return;
//        }
//        log("Encoder started");
//        while (true) {
//            int bufferIndex = mVideoEncoder.dequeueOutputBuffer(mVideoBufferInfo, 0);
//
//            if (bufferIndex < 0) {
//                Log.w(TAG, "Failed to deque a buffer, index: " + bufferIndex);
//            } else {
//                ByteBuffer encodedData = mVideoEncoder.getOutputBuffer(bufferIndex);
//                if (encodedData == null) {
//                    throw new RuntimeException("couldn't fetch buffer at index " + bufferIndex);
//                }
//
//                if (mVideoBufferInfo.size != 0) {
//                    encodedData.position(mVideoBufferInfo.offset);
//                    encodedData.limit(mVideoBufferInfo.offset + mVideoBufferInfo.size);
//                    try {
//                        byte[] b = new byte[encodedData.remaining()];
//                        encodedData.get(b);
//                        outputStream.write(b);
//                    } catch (IOException e) {
//                        Log.e(TAG, "Failed to write data to socket, stop casting");
//                        e.printStackTrace();
//                        stopScreenCapture();
//                        break;
//                    }
//                }
//
//                mVideoEncoder.releaseOutputBuffer(bufferIndex, false);
//
//                if ((mVideoBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
//                    break;
//                }
//            }
//        }
//
//        log("Encoder finished");
//    }
//
//    private void stopScreenCapture() {
//        releaseEncoders();
//        if (mVirtualDisplay == null) {
//            return;
//        }
//        mVirtualDisplay.release();
//        mVirtualDisplay = null;
//    }
//
//    private void releaseEncoders() {
//        if (mVideoEncoder != null) {
//            mVideoEncoder.stop();
//            mVideoEncoder.release();
//            mVideoEncoder = null;
//        }
//        if (mInputSurface != null) {
//            mInputSurface.release();
//            mInputSurface = null;
//        }
//        mVideoBufferInfo = null;
//    }
//
//    private boolean createServerSocket() {
//        log("creating server socket and starting the loop...");
//
//        new Thread() {
//
//            @Override
//            public void run() {
//                ServerSocket serverSocket = null;
//                try {
//                    serverSocket = new ServerSocket(PORT);
//                } catch (IOException e) {
//                    Log.e(TAG, "Failed to create server socket or server socket error", e);
//                    return;
//                }
//
//                while (serverSocket != null) {
//                    Socket socket;
//                    try {
//                        socket = serverSocket.accept();
//                        if (socket != null) {
//                            OutputStream stream = socket.getOutputStream();
//                            encoderLoop(stream);
//                        }
//                    } catch (IOException e) { }
//                }
//
//            }
//        }.start();
//
//
//        return true;
//    }
//
//
//}
