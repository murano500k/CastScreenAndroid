package android.renesas.castscreendemo;

import android.app.ActivityOptions;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.RequiresApi;
import android.support.test.InstrumentationRegistry;
import android.support.test.espresso.Espresso;
import android.support.test.espresso.idling.CountingIdlingResource;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.util.Log;
import android.view.Display;
import android.view.Surface;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;

import static android.content.Context.DISPLAY_SERVICE;
import static android.hardware.display.DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR;
import static android.renesas.castscreendemo.Config.CAST_DISPLAY_NAME;
import static android.renesas.castscreendemo.Config.DEFAULT_SCREEN_DPI;
import static android.renesas.castscreendemo.Config.DEFAULT_SCREEN_HEIGHT;
import static android.renesas.castscreendemo.Config.DEFAULT_SCREEN_WIDTH;
import static android.renesas.castscreendemo.Config.DEFAULT_VIDEO_BITRATE;
import static android.renesas.castscreendemo.Config.DEFAULT_VIDEO_FORMAT;

@RunWith(AndroidJUnit4.class)
public class TestCastMaps implements MainActivity.VDCallback{
    private static final String TAG = "TestCastMaps";
    Instrumentation instrumentation;
    private VirtualDisplay mVirtualDisplay;
    private MediaProjection mMediaProjection;
    private MediaProjectionManager mMediaProjectionManager;
    private Context mContext;
    private DisplayManager mDisplayManager;
    private MediaCodec.BufferInfo mVideoBufferInfo;
    private MediaCodec mVideoEncoder;
    private Surface mInputSurface;

    @Rule
    public ActivityTestRule<MainActivity> mActivityRule = new ActivityTestRule<>(
            MainActivity.class);
    private CountingIdlingResource idleResource;

    @Before
    public void prepare() {
        instrumentation= InstrumentationRegistry.getInstrumentation();
        mMediaProjectionManager= (MediaProjectionManager) instrumentation.getContext().getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        mContext=instrumentation.getTargetContext();
        if(mDisplayManager==null) mDisplayManager=(DisplayManager) mContext.getSystemService(DISPLAY_SERVICE);

        idleResource = new CountingIdlingResource("TEST_CAPTURE");
                Espresso.registerIdlingResources(idleResource);

    }

    @Test
    public void testCreateVirtualDisplayMP() {
        MainActivity mainActivity = (MainActivity) instrumentation.startActivitySync(
                new Intent(instrumentation.getTargetContext(), MainActivity.class));
        Log.d(TAG, "testCreateVirtualDisplay() called");
        idleResource.increment();
        mainActivity.getVirtualDisplayIntent(this);
    }
    @RequiresApi(api = Build.VERSION_CODES.O)
    @Test
    public void testCreateVirtualDisplayDM() {
        prepareVideoEncoder();
        mVirtualDisplay = mDisplayManager.createVirtualDisplay(CAST_DISPLAY_NAME, DEFAULT_SCREEN_WIDTH,
                DEFAULT_SCREEN_HEIGHT, DEFAULT_SCREEN_DPI, mInputSurface, DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC, null, null);
        onVirtualDisplayReady(mVirtualDisplay.getDisplay());
    }


    private void prepareVideoEncoder() {
        mVideoBufferInfo = new MediaCodec.BufferInfo();
        MediaFormat format = MediaFormat.createVideoFormat(DEFAULT_VIDEO_FORMAT, DEFAULT_SCREEN_WIDTH, DEFAULT_SCREEN_HEIGHT);
        int frameRate = Config.DEFAULT_VIDEO_FPS;

        // Set some required properties. The media codec may fail if these aren't defined.
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        format.setInteger(MediaFormat.KEY_BIT_RATE, DEFAULT_VIDEO_BITRATE);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, frameRate);
        format.setInteger(MediaFormat.KEY_CAPTURE_RATE, frameRate);
        format.setInteger(MediaFormat.KEY_REPEAT_PREVIOUS_FRAME_AFTER, 1000000 / frameRate);
        format.setInteger(MediaFormat.KEY_CHANNEL_COUNT, 1);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1); // 1 seconds between I-frames

        // Create a MediaCodec encoder and configure it. Get a Surface we can use for recording into.
        try {
            mVideoEncoder = MediaCodec.createByCodecName(Utils.getEncoderName(DEFAULT_VIDEO_FORMAT));

            mVideoEncoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            mInputSurface = mVideoEncoder.createInputSurface();
            mVideoEncoder.start();
        } catch (IOException e) {
            Log.e(TAG, "Failed to initial encoder, e: " + e);
            releaseEncoders();
        }
    }

    @After
    public void releaseEncoders() {
        //mDrainHandler.removeCallbacks(mDrainEncoderRunnable);
        if (mVideoEncoder != null) {
            mVideoEncoder.stop();
            mVideoEncoder.release();
            mVideoEncoder = null;
        }
        if (mInputSurface != null) {
            mInputSurface.release();
            mInputSurface = null;
        }
        if (mMediaProjection != null) {
            mMediaProjection.stop();
            mMediaProjection = null;
        }
        //mResultCode = 0;
        //mResultData = null;
        mVideoBufferInfo = null;
        //mTrackIndex = -1;
    }

    @Override
    public void intentMPReady(Intent intent, int code) {
        Log.d(TAG, "mResultCode: " + code + ", mResultData: " + intent);
        if (code != 0 && intent != null) {
            prepareVideoEncoder();
            mVirtualDisplay = mMediaProjection.createVirtualDisplay(CAST_DISPLAY_NAME, DEFAULT_SCREEN_WIDTH,
                    DEFAULT_SCREEN_HEIGHT, DEFAULT_SCREEN_DPI, VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, mInputSurface,
                    null, null);

        } else {

            return;
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private void onVirtualDisplayReady(Display display) {
        Log.d(TAG, "onVirtualDisplayReady() called with: display = [" + display.getName() + "]");
        Log.d(TAG, "onVirtualDisplayReady() called with: display = [" + display.getDisplayId() + "]");
        Log.d(TAG, "onVirtualDisplayReady() called with: display = [" + display.toString() + "]");
        Intent launchMapsIntent=new Intent();
        launchMapsIntent.setPackage("com.google.android.apps.maps");

        Log.d(TAG, "starting "+launchMapsIntent.getPackage());
        Bundle bundle= ActivityOptions.makeBasic()
                .setLaunchDisplayId(display.getDisplayId())
                .toBundle();

        instrumentation.getTargetContext().startActivity(launchMapsIntent, bundle);
    }

}
