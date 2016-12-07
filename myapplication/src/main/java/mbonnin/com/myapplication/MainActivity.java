package mbonnin.com.myapplication;

import android.net.Uri;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.Renderer;
import com.google.android.exoplayer2.audio.MediaCodecAudioRenderer;
import com.google.android.exoplayer2.mediacodec.MediaCodecSelector;
import com.google.android.exoplayer2.source.dash.DashMediaSource;
import com.google.android.exoplayer2.source.dash.DefaultDashChunkSource;
import com.google.android.exoplayer2.trackselection.AdaptiveVideoTrackSelection;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSourceFactory;
import com.google.android.exoplayer2.video.MediaCodecVideoRenderer;

public class MainActivity extends AppCompatActivity implements SurfaceHolder.Callback {

    private SurfaceView surfaceView;
    private ExoPlayer player;
    private Renderer[] renderers;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        surfaceView = new SurfaceView(this);

        setContentView(surfaceView);

        renderers = new Renderer[2];

        long joiningTime = 5000;
        int maxDroppedFramesToNotify = 50;
        renderers[0] = new MediaCodecVideoRenderer(this, MediaCodecSelector.DEFAULT,
                joiningTime, null, false, null, null,
                maxDroppedFramesToNotify);
        renderers[1] = new MediaCodecAudioRenderer(MediaCodecSelector.DEFAULT);

        DefaultBandwidthMeter bandwidthMeter = new DefaultBandwidthMeter();
        TrackSelection.Factory videoTrackSelectionFactory =
                new AdaptiveVideoTrackSelection.Factory(bandwidthMeter);
        DefaultTrackSelector trackSelector = new DefaultTrackSelector(videoTrackSelectionFactory);

        String uri = "http://www.youtube.com/api/manifest/dash/id/bf5bb2419360daf1/source/youtube?as=fmp4_audio_clear,fmp4_sd_hd_clear&sparams=ip,ipbits,expire,source,id,as&ip=0.0.0.0&ipbits=0&expire=19000000000&signature=51AF5F39AB0CEC3E5497CD9C900EBFEAECCCB5C7.8506521BFC350652163895D4C26DEE124209AA9E&key=ik0";

        DefaultHttpDataSourceFactory manifestDataSourceFactory
                = new DefaultHttpDataSourceFactory("Meetup Demo");
        DefaultHttpDataSourceFactory chunkDataSourceFactory
                = new DefaultHttpDataSourceFactory("Meetup Demo", bandwidthMeter);
        DashMediaSource mediaSource = new DashMediaSource(Uri.parse(uri),
                manifestDataSourceFactory, new DefaultDashChunkSource.Factory(chunkDataSourceFactory),
                null, null);

        surfaceView.getHolder().addCallback(this);

        player = ExoPlayerFactory.newInstance(renderers, trackSelector);
        player.setPlayWhenReady(true);
        player.prepare(mediaSource);
    }

    @Override
    public void surfaceCreated(SurfaceHolder surfaceHolder) {
        setVideoSurfaceInternal(surfaceHolder.getSurface());
    }

    private void setVideoSurfaceInternal(Surface surface) {
        ExoPlayer.ExoPlayerMessage messages = new ExoPlayer.ExoPlayerMessage(renderers[0], C.MSG_SET_SURFACE, surface);
        if (surface == null) {
            // We're replacing a surface. Block to ensure that it's not accessed after the method returns.
            player.blockingSendMessages(messages);
        } else {
            player.sendMessages(messages);
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder surfaceHolder, int i, int i1, int i2) {

    }

    @Override
    public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
        setVideoSurfaceInternal(null);
    }
}
