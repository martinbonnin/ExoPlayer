package com.google.android.exoplayer.hls;

import android.media.MediaExtractor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.SystemClock;
import android.util.Log;

import com.google.android.exoplayer.FormatHolder;
import com.google.android.exoplayer.MediaCodecUtil;
import com.google.android.exoplayer.MediaFormat;
import com.google.android.exoplayer.ParserException;
import com.google.android.exoplayer.SampleHolder;
import com.google.android.exoplayer.SampleSource;
import com.google.android.exoplayer.TrackInfo;
import com.google.android.exoplayer.chunk.ChunkSampleSource;
import com.google.android.exoplayer.parser.aac.AACExtractor;
import com.google.android.exoplayer.parser.h264.H264Utils;
import com.google.android.exoplayer.parser.ts.TSExtractorWithParsers;
import com.google.android.exoplayer.upstream.AESDataSource;
import com.google.android.exoplayer.upstream.BandwidthMeter;
import com.google.android.exoplayer.upstream.DataSource;
import com.google.android.exoplayer.upstream.DataSpec;
import com.google.android.exoplayer.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer.upstream.HttpDataSource;
import com.google.android.exoplayer.util.MimeTypes;
import com.google.android.exoplayer.util.Util;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicLong;
import com.google.android.exoplayer.parser.h264.H264Utils.SPS;

import org.apache.http.protocol.HTTP;

/**
 * Created by martin on 31/07/14.
 */
public class HLSSampleSource implements SampleSource {
  private static final String TAG = "HLSSampleSource";

  private static final int TYPE_VIDEO = 0;
  private static final int TYPE_AUDIO = 1;
  private static final int TYPE_DEBUG = 3;

  private String url;
  private MainPlaylist mainPlaylist;
  private final ArrayList<HLSTrack> trackList = new ArrayList<HLSTrack>();
  boolean prepared;

  private int initialBps;
  private int estimatedBps;
  private int forcedBps;
  private double bpsFraction;
  private int lowThresholdMsec;
  private int highThresholdMsec;
  private MainPlaylist.Entry currentEntry;

  private ArrayList<LinkedList<Object>> list;
  int track2type[] = new int[2];

  private int maxBufferSize;
  private int bufferSize;
  private int bufferMsec;

  private final DefaultBandwidthMeter bandwidthMeter = new DefaultBandwidthMeter();
  private AtomicLong bufferedPts;

  private int sequence;
  //private int lastKnownSequence;
  private ChunkTask chunkTask;
  private String userAgent;
  /* Amount we need to subtract to get timestamps starting at 0 */
  private long ptsOffset;
  /* used to keep track of wrapping from the thread */
  private WrapInfo wrapInfo[] = new WrapInfo[2];

  private boolean endOfStream;
  private Handler eventHandler;
  private EventListener eventListener;
  private int videoStreamType;
  private int audioStreamType;
  private boolean gotStreamTypes;
  private int maxBps;
  private int firstRememberedMediaSequence;
  private int fallbackPlaylistType;
  private List<Long> rememberedChunkStartPositionsMs;
  private ArrayList<Double> rememberedExtinf;

  private HashMap<MainPlaylist.Entry, VariantPlaylistSlot> variantPlaylistsMap = new HashMap<MainPlaylist.Entry, VariantPlaylistSlot>();
  private VariantPlaylistTask variantPlaylistTask;
  private long targetDurationMs;
  private long durationUs;
  private boolean isLive;
  private HashMap<Object, Object> allocatorsMap = new HashMap<Object, Object>();
  private int maxFrameSize;

  public static class WrapInfo {
    long lastPts;
    long offset;
  }

  public static class VariantPlaylistSlot {
    VariantPlaylist playlist;
    long lastUptime;
  }

  public static class Quality {
    public int width;
    public int height;
    public int bps;
    public String name;
  }

  public interface EventListener extends ChunkSampleSource.EventListener {
    void onQualitiesParsed(Quality qualities[]);
    void onChunkStart(Quality quality);
  }

  static class ChunkSentinel {
    MediaFormat mediaFormat;
    MainPlaylist.Entry entry;
  };

  static class HLSTrack {
    public int type;
    public TrackInfo trackInfo;
    public boolean discontinuity;
    public MainPlaylist.Entry readEntry;
  }

  public HLSSampleSource(String url, Handler eventHandler, EventListener listener, String userAgent) {
    this.url = url;
    Runtime rt = Runtime.getRuntime();
    int maxMemoryMB = (int)(rt.maxMemory()/(1024*1024));

    if (maxMemoryMB <= 16) {
      maxBufferSize = 1 * 1024 * 1024;
    } else if (maxMemoryMB <= 24) {
      maxBufferSize = 4 * 1024 * 1024;
    } else if (maxMemoryMB <= 32) {
      maxBufferSize = 8 * 1024 * 1024;
    } else if (maxMemoryMB <= 64) {
      maxBufferSize = 16 * 1024 * 1024;
    } else {
      maxBufferSize = 24 * 1024 * 1024;
    }
    Log.v(TAG, "maxMemoryMB= " + maxMemoryMB + ", maxBufferSize=" + maxBufferSize);

    bpsFraction = 0.75;
    initialBps = -1;
    maxBps = -1;
    forcedBps = -1;
    lowThresholdMsec = 5000;
    highThresholdMsec = 8000;
    list = new ArrayList<LinkedList<Object>>();
    list.add(new LinkedList<Object>());
    list.add(new LinkedList<Object>());
    wrapInfo[Packet.TYPE_AUDIO] = new WrapInfo();
    wrapInfo[Packet.TYPE_VIDEO] = new WrapInfo();
    this.userAgent = userAgent;
    bufferedPts = new AtomicLong();
    this.eventHandler = eventHandler;
    this.eventListener = listener;
    fallbackPlaylistType = VariantPlaylist.TYPE_UNKNOWN;
  }

  public HLSSampleSource(String url) {
    this(url, null, null, "HLS Player");
  }

  public void setMaxBufferSize(int maxBufferSize) {
    this.maxBufferSize = maxBufferSize;
  }

  public void setForcedBps(int bps) {

    this.forcedBps = bps;
  }

  public void setMaxBps(int bps) {

    this.maxBps = bps;
  }

  public void setInitialBps(int bps) {

    this.initialBps = bps;
  }

  /**
   * Tells what do use as a playlist type if the downloaded playlist do not specify it.
   *
   * @param playlistType one of TYPE_EVENT or TYPE_VOD
   */
  public void setFallbackPlaylistType(int playlistType) {
    this.fallbackPlaylistType = playlistType;
  }

  private MainPlaylist.Entry getEntryBelowOrEqual(int bps) {
    for (int i = mainPlaylist.entries.size() - 1; i >= 0; i--) {
      MainPlaylist.Entry entry = mainPlaylist.entries.get(i);
      if (entry.bps <= bps) {
        return entry;
      }
    }

    return mainPlaylist.entries.get(0);
  }

  private MainPlaylist.Entry evaluateNextEntry() {
    if (forcedBps >= 0) {
      // manually set
      return getEntryBelowOrEqual(forcedBps);
    }
    if (estimatedBps <= 0) {
      // first time
      return getEntryBelowOrEqual(initialBps);
    }
    MainPlaylist.Entry idealEntry = getEntryBelowOrEqual((int)((double)estimatedBps * bpsFraction));

    if (idealEntry.bps > currentEntry.bps) {
      if (bufferMsec < highThresholdMsec) {
        // The ideal format is a higher quality, but we have insufficient buffer to
        // safely switch up. Defer switching up for now.
        idealEntry = currentEntry;
      }
    } else {
      // The ideal format is a lower quality, but we have sufficient buffer to defer switching
      // down for now.
      if (bufferMsec > lowThresholdMsec) {
        idealEntry = currentEntry;
      }
    }

    if (maxBps > 0 && idealEntry.bps > maxBps) {
      idealEntry = getEntryBelowOrEqual(maxBps);
    }

    return idealEntry;
  }

  @Override
  public boolean prepare() throws IOException {
    int i = 0;
    if (prepared)
      return true;

    Log.d(TAG, "prepare: " + this.url);
    mainPlaylist = MainPlaylist.parse(this.url); // may throw IOException

    if (mainPlaylist.entries.isEmpty()) {
      Log.d(TAG, String.format("Assuming variant playlist for %s", this.url));
      mainPlaylist = MainPlaylist.createFakeMainPlaylist(this.url);
    }

    // remove audio-only qualities
    mainPlaylist.removeIncompleteQualities();

    maxFrameSize = MediaCodecUtil.maxH264DecodableFrameSize();

    Log.d(TAG, "maxFrameSize:" + maxFrameSize);

    // remove qualities that are too big
    if (maxFrameSize > 0) {
      for (Iterator<MainPlaylist.Entry> it = mainPlaylist.entries.iterator(); it.hasNext(); ) {
        MainPlaylist.Entry entry = it.next();
        if (entry.width > 0 && entry.height > 0 && entry.width * entry.height > maxFrameSize) {
          Log.d(TAG, "removing quality " + entry.width + "x" + entry.height);
          it.remove();
        }
      }
    }

    if (mainPlaylist.entries.isEmpty()) {
      throw new IOException("None of the stream variants are supported by this device");
    }

    for (MainPlaylist.Entry entry: mainPlaylist.entries) {
      variantPlaylistsMap.put(entry, new VariantPlaylistSlot());
    }

    // compute durationSec
    if (mainPlaylist.firstEntry != null) {
      currentEntry = mainPlaylist.firstEntry;
    } else {
      currentEntry = evaluateNextEntry();
    }
    VariantPlaylist variantPlaylist = currentEntry.downloadVariantPlaylist();
    variantPlaylistsMap.get(currentEntry).playlist = variantPlaylist;

    targetDurationMs = variantPlaylist.entries.get(0).durationMs;

    isLive = !variantPlaylist.endList;
    int type = variantPlaylist.type;
    if (type == VariantPlaylist.TYPE_UNKNOWN) {
      type = fallbackPlaylistType;
    }
    if (isLive) {
      switch(type) {
        case VariantPlaylist.TYPE_EVENT:
          // the server will only append files, start from the beginning
          firstRememberedMediaSequence = variantPlaylist.mediaSequence;
          break;
        case VariantPlaylist.TYPE_VOD:
          Log.e(TAG, "it is an error to have a playlist of type VOD with no ENDLIST tag, assume no VOD");
          /* fallthrough */
        case VariantPlaylist.TYPE_UNKNOWN:
        default:
//          // we are live, start close to the realtime position, but a little behind to be able to build a buffer
//          int bufferSequenceCount = variantPlaylist.entries.size() > 3 ? variantPlaylist.entries.size() - 3 : 0;
//          firstRememberedMediaSequence = variantPlaylist.mediaSequence + bufferSequenceCount -1;
          firstRememberedMediaSequence = variantPlaylist.mediaSequence;
          break;
      }
    } else {
      firstRememberedMediaSequence = variantPlaylist.mediaSequence;
    }

    targetDurationMs = variantPlaylist.targetDurationMs;
    if (targetDurationMs <= 0) {
      targetDurationMs = variantPlaylist.entries.get(0).durationMs;
    }
    //lastKnownSequence = firstRememberedMediaSequence - 1;
    rememberVariantPlaylist(variantPlaylist);

    sequence = firstRememberedMediaSequence;
    prepared = true;

    // start downloading, we need to get some information from the first chunks
    continueBuffering(0);

    if (eventListener != null && eventHandler != null) {
      final Quality qualities[] = new Quality[mainPlaylist.entries.size()];
      i = 0;
      for (MainPlaylist.Entry e : mainPlaylist.entries) {
        qualities[i] = new Quality();
        qualities[i].width = e.width;
        qualities[i].height = e.height;
        qualities[i].bps = e.bps;
        qualities[i].name = e.name;
        i++;
      }
      eventHandler.post(new Runnable() {
        @Override
        public void run() {
          eventListener.onQualitiesParsed(qualities);
        }
      });
    }

    boolean found = false;

    // see if there is a pts offset
    while (true) {
      boolean empty = true;
      synchronized (list) {
        for (LinkedList<Object> l : list) {
          for (Object o : l) {
            if (o instanceof Packet) {
              Packet sample =  (Packet)o;
              if (found == false) {
                ptsOffset = sample.pts;
                Log.d(TAG, "found ptsOffset=" + ptsOffset);
                found = true;
              }
            }
          }
          if (!l.isEmpty()) {
            empty = false;
          }
        }
      }

      if (empty && chunkTask == null) {
        break;
      }
      if (found) {
        break;
      }
      try {
        // Make this handlerThread wait a bit before checking again if sample pts is ready
        Thread.sleep(100);
      } catch (InterruptedException e) {
        // ignored
      }
    }

    if (!found) {
      return false;
    }

    i = 0;
    if (audioStreamType != Extractor.STREAM_TYPE_NONE) {
      HLSTrack track = new HLSTrack();
      track.type = Packet.TYPE_AUDIO;
      String mime = (audioStreamType == Extractor.STREAM_TYPE_AAC_ADTS) ? MimeTypes.AUDIO_AAC : MimeTypes.AUDIO_MPEG;
      track.trackInfo = new TrackInfo(mime, durationUs);
      trackList.add(track);
      track2type[i++] = Packet.TYPE_AUDIO;
    }
    if (videoStreamType != Extractor.STREAM_TYPE_NONE) {
      HLSTrack track = new HLSTrack();
      track.type = Packet.TYPE_VIDEO;
      track.trackInfo = new TrackInfo(MimeTypes.VIDEO_H264, durationUs);
      trackList.add(track);
      track2type[i++] = Packet.TYPE_VIDEO;
    }

    return true;
  }

  @Override
  public int getTrackCount() {
    return trackList.size();
  }

  @Override
  public TrackInfo getTrackInfo(int track) {
    return trackList.get(track).trackInfo;
  }

  @Override
  public void enable(int track, long timeUs) {

  }

  @Override
  public void disable(int track) {

  }

  @Override
  public void continueBuffering(long playbackPositionUs) {
    if (chunkTask != null) {
      // we are already loading something
      return;
    }

    if (bufferSize >= maxBufferSize) {
      // we don't want to waste too much memory
      return;
    }

    final long bandwidthMeterEstimate = bandwidthMeter.getEstimate();
    if (bandwidthMeterEstimate != BandwidthMeter.NO_ESTIMATE) {
      estimatedBps = (int) bandwidthMeterEstimate * 8; // HLS playlist is using _bits_ per second
    } else {
      estimatedBps = (int) BandwidthMeter.NO_ESTIMATE;
    }
    bufferMsec = (int)((getBufferedPositionUs() - playbackPositionUs)/1000);

    if (estimatedBps == BandwidthMeter.NO_ESTIMATE) {
      currentEntry = mainPlaylist.firstEntry;
    } else {
      currentEntry = evaluateNextEntry();
    }
    VariantPlaylist variantPlaylist = variantPlaylistsMap.get(currentEntry).playlist;
    if (variantPlaylist == null) {
      kickVariantPlaylistTask();
      // wait for the task to complete
      return;
    }

    if (sequence >= variantPlaylist.mediaSequence + variantPlaylist.entries.size()) {
      if (variantPlaylist.endList) {
        endOfStream = true;
        return;
      } else {
        kickVariantPlaylistTask();
        return;
      }
    } else if (sequence < variantPlaylist.mediaSequence) {
      int newSequence = variantPlaylist.mediaSequence + 1;
      if (variantPlaylist.entries.size() == 0) {
        newSequence = variantPlaylist.mediaSequence;
      }
      Log.d(TAG, String.format("we are behind, skip sequence %d -> %d (%d - %d)",
          sequence, newSequence, variantPlaylist.mediaSequence,
          variantPlaylist.mediaSequence + variantPlaylist.entries.size() - 1));
      sequence = newSequence;
    }

    if (eventListener != null) {
      Quality quality = new Quality();
      quality.width = currentEntry.width;
      quality.height = currentEntry.height;
      quality.bps = currentEntry.bps;
      eventListener.onChunkStart(quality);
    }

    VariantPlaylist.Entry variantEntry = variantPlaylist.entries.get(sequence - variantPlaylist.mediaSequence);

    Chunk chunk = new Chunk();
    chunk.variantEntry = variantEntry;
    chunk.mainEntry = currentEntry;
    chunk.variantPlaylist = variantPlaylist;
    chunk.videoMediaFormat = MediaFormat.createVideoFormat(MimeTypes.VIDEO_H264, MediaFormat.NO_VALUE,
        currentEntry.width, currentEntry.height, null);
    chunkTask = new ChunkTask(chunk);
    chunkTask.execute();
  }

  private void notifyUpstreamError(final IOException e) {
    if (eventHandler != null && eventListener != null) {
      eventHandler.post(new Runnable() {
        @Override
        public void run() {
          eventListener.onUpstreamError(TYPE_VIDEO, e);
        }
      });
    }
  }

  private void notifyConsumptionError(final IOException e) {
    if (eventHandler != null && eventListener != null) {
      eventHandler.post(new Runnable() {
        @Override
        public void run() {
          eventListener.onConsumptionError(TYPE_VIDEO, e);
        }
      });
    }
  }

  /*
   * Internal Conax debugging method
   */

  private void kickVariantPlaylistTask() {
    VariantPlaylistSlot slot = variantPlaylistsMap.get(currentEntry);
    long now = SystemClock.uptimeMillis();
    if (variantPlaylistTask == null && (now - slot.lastUptime > targetDurationMs / 2)) {
      variantPlaylistTask = new VariantPlaylistTask(currentEntry);
      variantPlaylistTask.execute();
      slot.lastUptime = now;
    }
  }

  @Override
  public int readData(int track, long playbackPositionUs, FormatHolder formatHolder, SampleHolder sampleHolder, boolean onlyReadDiscontinuity) throws IOException {
    if (onlyReadDiscontinuity) {
      if (trackList.get(track).discontinuity) {
        trackList.get(track).discontinuity = false;
        return DISCONTINUITY_READ;
      } else {
        return NOTHING_READ;
      }
    }

    synchronized(list) {
      Object o;
      HLSTrack hlsTrack = trackList.get(track);
      try {
        o = list.get(track2type[track]).removeFirst();
        if (o instanceof ChunkSentinel) {
          ChunkSentinel sentinel = (ChunkSentinel)o;
          if (sentinel.entry != trackList.get(track).readEntry) {
            formatHolder.format = sentinel.mediaFormat;
            hlsTrack.readEntry = sentinel.entry;
            return FORMAT_READ;
          } else {
            return NOTHING_READ;
          }
        } else {
          Packet sample = (Packet)o;
          sample.data.limit(sample.data.position());
          if (sample.type == Packet.TYPE_AUDIO) {
            sample.data.position(7);
          } else {
            sample.data.position(0);
          }
          sampleHolder.data.put(sample.data);
          sampleHolder.size = sampleHolder.data.position();
          sampleHolder.timeUs = (sample.pts - ptsOffset) * 1000 / 45;
          sampleHolder.flags = MediaExtractor.SAMPLE_FLAG_SYNC; // Requires API level 16
          bufferSize -= sampleHolder.size;
          /*Log.d(TAG, String.format("%s: read %6d time=%8d (bufferSize=%6d)",
                  sample.type == Packet.TYPE_AUDIO ? "AUDIO":"VIDEO",
                  sampleHolder.size, sampleHolder.timeUs/1000, bufferSize));*/
          return SAMPLE_READ;
        }
      } catch (NoSuchElementException e) {
        if (endOfStream == true) {
          return END_OF_STREAM;
        } else {
          return NOTHING_READ;
        }
      }
    }
  }

  private void clearSamples() {
    for (LinkedList<Object> l : list) {
      Iterator<Object> it = l.iterator();
      while (it.hasNext()) {
        Object o = it.next();
        /*if (o instanceof Packet) {
          ((Packet) o).release();
        }*/
        it.remove();
      }
    }
  }
  @Override
  public long seekToUs(long timeUs) {
    if (chunkTask != null) {
      chunkTask.abort();
    }
    synchronized(list) {
      clearSamples();
      bufferSize = 0;
      bufferMsec = 0;
      bufferedPts.set(timeUs * 45 / 1000 + ptsOffset);
      for (int i = 0; i < wrapInfo.length; i++) {
        // XXX: try to find the appropriate wrapOffset
        wrapInfo[i].lastPts = 0;
        wrapInfo[i].offset = 0;
      }
    }

    int timeMs = (int) (timeUs / 1000);
    int matchingChunkOffset = findMatchingChunkOffset(timeMs);
    int matchingSequence = firstRememberedMediaSequence + matchingChunkOffset;
    long seekTimeMs = rememberedChunkStartPositionsMs.get(matchingChunkOffset);

    Log.d(TAG, "seekTo " + timeMs +
      " => new media sequence = " + matchingSequence + " previous media sequence = " + sequence);

    sequence = matchingSequence;

    for (HLSTrack t : trackList) {
      t.discontinuity = true;
    }

    endOfStream = false;
    return seekTimeMs * 1000L;
  }

  @Override
  public long getBufferedPositionUs() {
    return (bufferedPts.get() - ptsOffset) * 1000 / 45;
  }

  /**
   * Finds the index of the first chunk in the playlist that has a start time of no more than positionMs
   * @param positionMs
   */
  private int findMatchingChunkOffset(final long positionMs) {
    int matchingChunkIndex;
    int insertPosition = Collections.binarySearch(rememberedChunkStartPositionsMs, positionMs);
    if (insertPosition < -1) {
      insertPosition = -insertPosition - 1; // position of first element that is larger
      matchingChunkIndex = insertPosition - 1; // position of first element that is not larger
    } else {
      matchingChunkIndex = insertPosition; // positionMs matches an element exactly
    }

    return matchingChunkIndex;
  }

  @Override
  public void release() {
    if (chunkTask != null) {
      chunkTask.abort();
    }
    clearSamples();

    list.clear();
  }

  private void rememberVariantPlaylist(VariantPlaylist variantPlaylist) {
    durationUs = 0;
    rememberedChunkStartPositionsMs = new ArrayList<Long>(variantPlaylist.entries.size() + 1);

    rememberedChunkStartPositionsMs.add(0L); // first chunk starts at position 0
    for (VariantPlaylist.Entry entry : variantPlaylist.entries) {
      long chunkDurationMs = entry.durationMs;
      long previousChunkPosition = rememberedChunkStartPositionsMs.get(rememberedChunkStartPositionsMs.size() - 1);
      rememberedChunkStartPositionsMs.add(previousChunkPosition + chunkDurationMs);
    }
    // Note: the last element in rememberedChunkStartPositionsMs represents the starting position of
    // the first chunk outside of the playlist, and is the sum of all previous chunk durations.
    long sumOfChunkDurations = rememberedChunkStartPositionsMs.get(rememberedChunkStartPositionsMs.size() - 1);
    durationUs = sumOfChunkDurations * 1000L;

      for (HLSTrack hlsTrack: trackList) {
      hlsTrack.trackInfo.durationUs = durationUs;
    }
  }

  static class Chunk {
    VariantPlaylist variantPlaylist;
    VariantPlaylist.Entry variantEntry;
    MediaFormat videoMediaFormat;
    MainPlaylist.Entry mainEntry;
  }

  class ChunkTask extends AsyncTask<Void, Void, Void>  {
    private final Chunk chunk;
    private Exception exception;
    private boolean aborted;

    public ChunkTask(Chunk chunk) {
      this.chunk = chunk;
    }

    @Override
    protected Void doInBackground(Void... params) {
      HLSSampleSource source = HLSSampleSource.this;
      String variantPlaylistUrl = chunk.variantPlaylist.url;
      VariantPlaylist.Entry variantEntry = chunk.variantEntry;
      String chunkUrl = Util.makeAbsoluteUrl(variantPlaylistUrl, variantEntry.url);
      Log.d(TAG, sequence + ": chunkTask (" + String.format("%8d", bufferSize) + ") " + chunkUrl);
      Uri uri = null;
      MediaFormat audioMediaFormat = null;
      MediaFormat videoMediaFormat = null;

      if (variantEntry.keyEntry != null) {
        String dataUrl = null;
        String keyUrl = null;
        try {
          dataUrl = URLEncoder.encode(chunkUrl, HTTP.UTF_8);
          keyUrl = URLEncoder.encode(variantEntry.keyEntry.uri, HTTP.UTF_8);
        } catch (UnsupportedEncodingException e) {
          Log.wtf(TAG, HTTP.UTF_8 + " charset not available");
          notifyConsumptionError(e);
          exception = e;
          return null;
        }

        String iv = variantEntry.keyEntry.IV;
        if (iv == null) {
          // XXX: is this nextChunkIndex or nextChunkIndex + 1 ?
          iv = Util.normalizeIV(Integer.toHexString(sequence));
        }
        uri = Uri.parse("aes://dummy?dataUrl=" + dataUrl + "&keyUrl=" + keyUrl + "&iv=" + iv);
      } else {
        uri = Uri.parse(chunkUrl);
      }

      DataSpec dataSpec = new DataSpec(uri, variantEntry.offset, variantEntry.length, null);
      DataSource HTTPDataSource = new HttpDataSource(userAgent, null, bandwidthMeter);
      DataSource dataSource = new AESDataSource(userAgent, HTTPDataSource);
      try {
        dataSource.open(dataSpec);
      } catch (IOException e) {
        Log.e(TAG, "Error reading encrypted data source", e);
        notifyUpstreamError(e);
        exception = e;
        return null;
      }

      Extractor extractor = null;
      /*
        try {
          extractor = new TSExtractorNative(dataSource);
        } catch (UnsatisfiedLinkError e) {
          Log.e(TAG, "cannot load TSExtractorNative");
        }
      }*/
      if (extractor == null) {
        try {
          extractor = new TSExtractorWithParsers(dataSource, allocatorsMap);
        } catch (ParserException e) {
          Log.e(TAG, "Error parsing TS", e);
          notifyConsumptionError(e);
          exception = e;
          return null;
        }
      }

      Packet sample;
      while (aborted == false) {
        try {
          sample = extractor.read();
        } catch (ParserException e) {
          Log.e(TAG, "Extractor read error", e);
          notifyConsumptionError(e);
          exception = e;
          break;
        }
        if (sample == null) {
          break;
        }
        synchronized (source.list) {
          if (!aborted) {

          WrapInfo wrapInfo = source.wrapInfo[sample.type];
          if (sample.pts < wrapInfo.lastPts && (wrapInfo.lastPts - sample.pts) > Math.pow(2,31)) {
            Log.d(TAG, "wrap detected");
            wrapInfo.offset += Math.pow(2,32);
          }
          wrapInfo.lastPts = sample.pts;
          sample.pts += wrapInfo.offset;

          if (!gotStreamTypes) {
              audioStreamType = extractor.getStreamType(Packet.TYPE_AUDIO);
              videoStreamType = extractor.getStreamType(Packet.TYPE_VIDEO);
              gotStreamTypes = true;
            }

            if (audioMediaFormat == null && sample.type == Packet.TYPE_AUDIO) {
              if (audioStreamType == Extractor.STREAM_TYPE_AAC_ADTS) {
                AACExtractor.ADTSHeader h = new AACExtractor.ADTSHeader();
                byte header[] = new byte[7];
                int oldPosition = sample.data.position();
                sample.data.position(0);
                sample.data.get(header, 0, 7);
                sample.data.position(oldPosition);
                h.update(new Packet.UnsignedByteArray(header), 0);
                audioMediaFormat = h.toMediaFormat();
              } else {
                // XX: do not hardcode
                Log.w(TAG, "Audio stream type was not AAC ADTS, assuming mp3");
                audioMediaFormat = MediaFormat.createAudioFormat(MimeTypes.AUDIO_MPEG, -1, 2, 44100, null);
              }
              ChunkSentinel sentinel = new ChunkSentinel();
              sentinel.mediaFormat = audioMediaFormat;
              sentinel.entry = chunk.mainEntry;

              list.get(sample.type).add(sentinel);
            } else if (videoMediaFormat == null && sample.type == Packet.TYPE_VIDEO) {
              ChunkSentinel sentinel = new ChunkSentinel();

              SPS sps = new SPS();
              List<byte[]> csd = new ArrayList<byte []>();
              if (H264Utils.extractSPS_PPS(sample.data, sps, csd)) {
                sentinel.mediaFormat = MediaFormat.createVideoFormat(MimeTypes.VIDEO_H264, MediaFormat.NO_VALUE,
                    sps.width, sps.height, csd);
              } else {
                sentinel.mediaFormat = chunk.videoMediaFormat;
              }
              videoMediaFormat = sentinel.mediaFormat;
              sentinel.entry = chunk.mainEntry;
              list.get(Packet.TYPE_VIDEO).add(sentinel);
            }

            list.get(sample.type).add(sample);

            bufferSize += sample.data.position();
            //Log.d(TAG, (sample.type == Packet.TYPE_AUDIO ? "AUDIO" : "VIDEO") + " time=" + (sample.pts/45) + " size=" + sample.data.position());
          }
          source.bufferedPts.set(sample.pts);
        }
      }

      extractor.release();
      try {
        dataSource.close();
      } catch (IOException e) {
        Log.w(TAG, "Could not close data source", e);
      }

      return null;
    }

    public void abort()  {
      synchronized (HLSSampleSource.this.list) {
        aborted = true;
        exception = new Exception("aborted");
      }
    }

    @Override
    protected void onPostExecute(Void dummy) {
      HLSSampleSource source = HLSSampleSource.this;
      if (exception == null) {
        source.sequence++;
      }

      source.chunkTask = null;
    }
  }

  class VariantPlaylistTask extends AsyncTask<Void, Void, Void>  {
    private final MainPlaylist.Entry mainEntry;
    private Exception exception;
    private VariantPlaylist variantPlaylist;

    public VariantPlaylistTask(MainPlaylist.Entry mainEntry) {
      this.mainEntry = mainEntry;
    }

    @Override
    protected Void doInBackground(Void... params) {
      try {
        this.variantPlaylist = mainEntry.downloadVariantPlaylist();
      } catch (ParserException e) {
        notifyConsumptionError(e);
        this.exception = e;
      } catch (IOException e) {
        notifyUpstreamError(e);
        this.exception = e;
      }

      return null;
    }

    @Override
    protected void onPostExecute(Void dummy) {
      HLSSampleSource source = HLSSampleSource.this;
      if (exception == null) {
        source.variantPlaylistsMap.get(currentEntry).playlist = this.variantPlaylist;
        rememberVariantPlaylist(this.variantPlaylist);
      }

      source.variantPlaylistTask = null;
    }
  }
}
