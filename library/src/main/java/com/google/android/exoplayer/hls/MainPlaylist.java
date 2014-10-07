package com.google.android.exoplayer.hls;

import android.util.Log;

import com.google.android.exoplayer.ParserException;
import com.google.android.exoplayer.util.Util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

public class MainPlaylist {

    private static final String TAG = "MainPlaylist";

    public static class Entry implements Comparable<Entry> {
    public String url;
    public String absoluteUrl;
    public int bps;
    public int width;
    public int height;
    public ArrayList<String> codecs;
    public String name;

    public Entry() {
      codecs = new ArrayList<String>();
      width = 0;
      height = 0;
    }
    public Entry(String baseUrl, String url) {
      this();
      this.url = url;
      if (baseUrl != null) {
        this.absoluteUrl = Util.makeAbsoluteUrl(baseUrl, url);
      } else {
        this.absoluteUrl = url;
      }
    }

    @Override
    public int compareTo(Entry another) {
      return bps - another.bps;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      Entry entry = (Entry) o;

      if (bps != entry.bps) return false;
      if (height != entry.height) return false;
      if (width != entry.width) return false;
      if (absoluteUrl != null ? !absoluteUrl.equals(entry.absoluteUrl) : entry.absoluteUrl != null)
          return false;
      if (!codecs.equals(entry.codecs)) return false;
      if (name != null ? !name.equals(entry.name) : entry.name != null) return false;
      if (url != null ? !url.equals(entry.url) : entry.url != null) return false;

      return true;
    }

    @Override
    public int hashCode() {
      int result = url != null ? url.hashCode() : 0;
      result = 31 * result + (absoluteUrl != null ? absoluteUrl.hashCode() : 0);
      result = 31 * result + bps;
      result = 31 * result + width;
      result = 31 * result + height;
      result = 31 * result + codecs.hashCode();
      result = 31 * result + (name != null ? name.hashCode() : 0);
      return result;
    }

    public VariantPlaylist downloadVariantPlaylist() throws IOException {
      VariantPlaylist variantPlaylist;
      URL variantURL = new URL(absoluteUrl);

      InputStream inputStream = getInputStream(variantURL);
      try {
        variantPlaylist = VariantPlaylist.parse(variantURL.toString(), inputStream);
      } finally {
        if (inputStream != null) {
          inputStream.close();
        }
      }

      return variantPlaylist;
    }
  }

  public List<Entry> entries;
  public Entry firstEntry;
  public String url;

  public MainPlaylist() {
    entries = new ArrayList<Entry>();
  }

  static private InputStream getInputStream(URL url) throws IOException {
    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
    connection.setConnectTimeout(8000);
    connection.setReadTimeout(8000);
    connection.setDoOutput(false);
    connection.connect();
    return connection.getInputStream();
  }

    public void removeIncompleteQualities() {
        int codecCount[] = new int[entries.size()];
        int max = -1;
        int i = 0;
        for (Entry entry : entries) {
            codecCount[i] = entry.codecs.size();
            if (codecCount[i] > max) {
                max = codecCount[i];
            }
            i++;
        }

        if (max == 0) {
            // m3u8 did not specify codecs
            return;
        }

        i = 0;
        for (Iterator<Entry> it = entries.iterator(); it.hasNext(); ) {
            Entry entry = it.next();
            if (codecCount[i] < max) {
                Log.d(TAG, "removing playlist " + entry.url);
                it.remove();

                if (entry == firstEntry) {
                  // We've determined the first entry was incomplete, so we need to forget about it.
                  // At this stage, we do not know enough to determine the actual first complete
                  // quality, so the most we can do is just set it to null and expect the caller
                  // to know how to deal with that.
                  firstEntry = null;
                }
            }
            i++;
        }
    }

    public static MainPlaylist createFakeMainPlaylist(String url) {
    MainPlaylist mainPlaylist = new MainPlaylist();
    Entry e = new Entry(null, url);
    e.bps = 424242;
    /*e.codecs.add("mp4a");
    if (!audioOnly) {
      e.codecs.add("avc1");
    }*/
    mainPlaylist.entries.add(e);
    mainPlaylist.url = ".";
    return mainPlaylist;

  }

  public static MainPlaylist parse(String url) throws IOException {
    MainPlaylist mainPlaylist = new MainPlaylist();
    InputStream stream = getInputStream(new URL(url));
    BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
    mainPlaylist.url = url;

    String line = reader.readLine();
    if (line == null) {
      throw new ParserException("empty playlist");
    }
    if (!line.startsWith(M3U8Constants.EXTM3U)) {
      throw new ParserException("no EXTM3U tag");
    }

    Entry e = null;
    while ((line = reader.readLine()) != null) {
      if (line.startsWith(M3U8Constants.EXT_X_STREAM_INF + ":")) {
        if (e == null) {
          e = new Entry();
        }
        HashMap<String, String> attributes = M3U8Utils.parseAtrributeList(line.substring(M3U8Constants.EXT_X_STREAM_INF.length() + 1));
        e.bps = Integer.parseInt(attributes.get("BANDWIDTH"));
        if (attributes.containsKey("RESOLUTION")) {
          String resolution[] = attributes.get("RESOLUTION").split("x");
          e.width = Integer.parseInt(resolution[0]);
          e.height = Integer.parseInt(resolution[1]);
        }
        if (attributes.containsKey("CODECS")) {
          String codecs[] = attributes.get("CODECS").split(",");
          for (String codec : codecs) {
            String [] parts = codec.split("\\.");
            e.codecs.add(parts[0]);
          }
        }
        if (attributes.containsKey("NAME")) {
          e.name = attributes.get("NAME");
        }
        if (e.codecs.size() == 0) {
          // by default, assume chunks contain aac + h264
          e.codecs.add("mp4a");
          e.codecs.add("avc1");
        }
      } else if (e != null && !line.startsWith("#")) {
        e.url = line;
        e.absoluteUrl = Util.makeAbsoluteUrl(url, line);
        mainPlaylist.entries.add(e);
        e = null;
      }
    }

    mainPlaylist.firstEntry = mainPlaylist.entries.size() > 0 ? mainPlaylist.entries.get(0) : null;

    Collections.sort(mainPlaylist.entries);
    stream.close();
    return mainPlaylist;
  }
}
