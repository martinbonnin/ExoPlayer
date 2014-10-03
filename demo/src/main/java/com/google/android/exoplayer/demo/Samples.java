/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.android.exoplayer.demo;

/**
 * Holds statically defined sample definitions.
 */
/* package */ class Samples {

  public static class Sample {

    public final String name;
    public final String contentId;
    public final String uri;
    public final int type;
    public final boolean isEncypted;
    public final boolean fullPlayer;

    public Sample(String name, String contentId, String uri, int type, boolean isEncrypted,
        boolean fullPlayer) {
      this.name = name;
      this.contentId = contentId;
      this.uri = uri;
      this.type = type;
      this.isEncypted = isEncrypted;
      this.fullPlayer = fullPlayer;
    }

  }

  public static final Sample[] SIMPLE = new Sample[] {
    new Sample("Google Glass (DASH)", "bf5bb2419360daf1",
        "http://www.youtube.com/api/manifest/dash/id/bf5bb2419360daf1/source/youtube?"
        + "as=fmp4_audio_clear,fmp4_sd_hd_clear&sparams=ip,ipbits,expire,as&ip=0.0.0.0&"
        + "ipbits=0&expire=19000000000&signature=255F6B3C07C753C88708C07EA31B7A1A10703C8D."
        + "2D6A28B21F921D0B245CDCF36F7EB54A2B5ABFC2&key=ik0", DemoUtil.TYPE_DASH_VOD, false,
        false),
    new Sample("Google Play (DASH)", "3aa39fa2cc27967f",
        "http://www.youtube.com/api/manifest/dash/id/3aa39fa2cc27967f/source/youtube?"
        + "as=fmp4_audio_clear,fmp4_sd_hd_clear&sparams=ip,ipbits,expire,as&ip=0.0.0.0&ipbits=0&"
        + "expire=19000000000&signature=7181C59D0252B285D593E1B61D985D5B7C98DE2A."
        + "5B445837F55A40E0F28AACAA047982E372D177E2&key=ik0", DemoUtil.TYPE_DASH_VOD, false,
        false),
    new Sample("Super speed (SmoothStreaming)", "uid:ss:superspeed",
        "http://playready.directtaps.net/smoothstreaming/SSWSS720H264/SuperSpeedway_720.ism",
        DemoUtil.TYPE_SS_VOD, false, false),
    new Sample("Dizzy (Misc)", "uid:misc:dizzy",
        "http://html5demos.com/assets/dizzy.mp4", DemoUtil.TYPE_OTHER, false, false),
  };

  public static final Sample[] YOUTUBE_DASH_MP4 = new Sample[] {
    new Sample("Google Glass", "bf5bb2419360daf1",
        "http://www.youtube.com/api/manifest/dash/id/bf5bb2419360daf1/source/youtube?"
        + "as=fmp4_audio_clear,fmp4_sd_hd_clear&sparams=ip,ipbits,expire,as&ip=0.0.0.0&"
        + "ipbits=0&expire=19000000000&signature=255F6B3C07C753C88708C07EA31B7A1A10703C8D."
        + "2D6A28B21F921D0B245CDCF36F7EB54A2B5ABFC2&key=ik0", DemoUtil.TYPE_DASH_VOD, false,
        true),
    new Sample("Google Play", "3aa39fa2cc27967f",
        "http://www.youtube.com/api/manifest/dash/id/3aa39fa2cc27967f/source/youtube?"
        + "as=fmp4_audio_clear,fmp4_sd_hd_clear&sparams=ip,ipbits,expire,as&ip=0.0.0.0&ipbits=0&"
        + "expire=19000000000&signature=7181C59D0252B285D593E1B61D985D5B7C98DE2A."
        + "5B445837F55A40E0F28AACAA047982E372D177E2&key=ik0", DemoUtil.TYPE_DASH_VOD, false,
        true),
  };

  public static final Sample[] YOUTUBE_DASH_WEBM = new Sample[] {
    new Sample("Google Glass", "bf5bb2419360daf1",
        "http://www.youtube.com/api/manifest/dash/id/bf5bb2419360daf1/source/youtube?"
        + "as=fmp4_audio_clear,webm2_sd_hd_clear&sparams=ip,ipbits,expire,as&ip=0.0.0.0&ipbits=0&"
        + "expire=19000000000&signature=A3EC7EE53ABE601B357F7CAB8B54AD0702CA85A7."
        + "446E9C38E47E3EDAF39E0163C390FF83A7944918&key=ik0", DemoUtil.TYPE_DASH_VOD, false, true),
    new Sample("Google Play", "3aa39fa2cc27967f",
        "http://www.youtube.com/api/manifest/dash/id/3aa39fa2cc27967f/source/youtube?"
        + "as=fmp4_audio_clear,webm2_sd_hd_clear&sparams=ip,ipbits,expire,as&ip=0.0.0.0&ipbits=0&"
        + "expire=19000000000&signature=B752B262C6D7262EC4E4EB67901E5D8F7058A81D."
        + "C0358CE1E335417D9A8D88FF192F0D5D8F6DA1B6&key=ik0", DemoUtil.TYPE_DASH_VOD, false, true),
  };

  public static final Sample[] SMOOTHSTREAMING = new Sample[] {
    new Sample("Super speed", "uid:ss:superspeed",
        "http://playready.directtaps.net/smoothstreaming/SSWSS720H264/SuperSpeedway_720.ism",
        DemoUtil.TYPE_SS_VOD, false, true),
    new Sample("Super speed (PlayReady)", "uid:ss:pr:superspeed",
        "http://playready.directtaps.net/smoothstreaming/SSWSS720H264PR/SuperSpeedway_720.ism",
        DemoUtil.TYPE_SS_VOD, true, true),
  };

  public static final Sample[] WIDEVINE_GTS = new Sample[] {
    new Sample("WV: HDCP not specified", "d286538032258a1c",
        "http://www.youtube.com/api/manifest/dash/id/d286538032258a1c/source/youtube?"
        + "as=fmp4_audio_cenc,fmp4_sd_hd_cenc&sparams=ip,ipbits,expire,as&ip=0.0.0.0&ipbits=0"
        + "&expire=19000000000&signature=41EA40A027A125A16292E0A5E3277A3B5FA9B938."
        + "0BB075C396FFDDC97E526E8F77DC26FF9667D0D6&key=ik0", DemoUtil.TYPE_DASH_VOD, true, true),
    new Sample("WV: HDCP not required", "48fcc369939ac96c",
        "http://www.youtube.com/api/manifest/dash/id/48fcc369939ac96c/source/youtube?"
        + "as=fmp4_audio_cenc,fmp4_sd_hd_cenc&sparams=ip,ipbits,expire,as&ip=0.0.0.0&ipbits=0"
        + "&expire=19000000000&signature=315911BDCEED0FB0C763455BDCC97449DAAFA9E8."
        + "5B41E2EB411F797097A359D6671D2CDE26272373&key=ik0", DemoUtil.TYPE_DASH_VOD, true, true),
    new Sample("WV: HDCP required", "e06c39f1151da3df",
        "http://www.youtube.com/api/manifest/dash/id/e06c39f1151da3df/source/youtube?"
        + "as=fmp4_audio_cenc,fmp4_sd_hd_cenc&sparams=ip,ipbits,expire,as&ip=0.0.0.0&ipbits=0"
        + "&expire=19000000000&signature=A47A1E13E7243BD567601A75F79B34644D0DC592."
        + "B09589A34FA23527EFC1552907754BB8033870BD&key=ik0", DemoUtil.TYPE_DASH_VOD, true, true),
    new Sample("WV: Secure video path required", "0894c7c8719b28a0",
        "http://www.youtube.com/api/manifest/dash/id/0894c7c8719b28a0/source/youtube?"
        + "as=fmp4_audio_cenc,fmp4_sd_hd_cenc&sparams=ip,ipbits,expire,as&ip=0.0.0.0&ipbits=0"
        + "&expire=19000000000&signature=2847EE498970F6B45176766CD2802FEB4D4CB7B2."
        + "A1CA51EC40A1C1039BA800C41500DD448C03EEDA&key=ik0", DemoUtil.TYPE_DASH_VOD, true, true),
    new Sample("WV: HDCP + secure video path required", "efd045b1eb61888a",
        "http://www.youtube.com/api/manifest/dash/id/efd045b1eb61888a/source/youtube?"
        + "as=fmp4_audio_cenc,fmp4_sd_hd_cenc&sparams=ip,ipbits,expire,as&ip=0.0.0.0&ipbits=0"
        + "&expire=19000000000&signature=61611F115EEEC7BADE5536827343FFFE2D83D14F."
        + "2FDF4BFA502FB5865C5C86401314BDDEA4799BD0&key=ik0", DemoUtil.TYPE_DASH_VOD, true, true),
    new Sample("WV: 30s license duration", "f9a34cab7b05881a",
        "http://www.youtube.com/api/manifest/dash/id/f9a34cab7b05881a/source/youtube?"
        + "as=fmp4_audio_cenc,fmp4_sd_hd_cenc&sparams=ip,ipbits,expire,as&ip=0.0.0.0&ipbits=0"
        + "&expire=19000000000&signature=88DC53943385CED8CF9F37ADD9E9843E3BF621E6."
        + "22727BB612D24AA4FACE4EF62726F9461A9BF57A&key=ik0", DemoUtil.TYPE_DASH_VOD, true, true),
  };

  public static final Sample[] HLS = new Sample[] {
    new Sample("L'ecole du pouvoir - 720p", "uid:misc:dailymotion_long", "http://vid2.cf.dmcdn.net/sec(a17e8c0f2d5a0a7c0c5d5363bf024568)/video/600/257/128752006_mp4_h264_aac_hq.m3u8",
        DemoUtil.TYPE_HLS, false, true),
    new Sample("Tour de france - adaptive", "uid:misc:dailymotion", "http://www.dailymotion.com/cdn/manifest/video/x20w6of.m3u8?auth=1410854598-2560-irsxfgcg-8b70f9f192d150bfa64e1a29fca1dcd5",
        DemoUtil.TYPE_HLS, false, true),
    new Sample("Tour de france - 184p", "uid:misc:dailymotionhls184p", "http://vid2.cf.dmcdn.net/sec(e83834383dc2d0cbfa8b28a7bf9498fd)/video/999/334/122433999_mp4_h264_aac_ld.m3u8",
        DemoUtil.TYPE_HLS, false, true),
    new Sample("Tour de france - 720p", "uid:misc:dailymotionhls720p", "http://vid2.cf.dmcdn.net/sec(5f9ee920e654b39b6f23a376d122f202)/video/999/334/122433999_mp4_h264_aac_hd.m3u8",
        DemoUtil.TYPE_HLS, false, true),
    new Sample("Audio-only - mp3", "uid:misc:mpeg-audio", "https://api.beatgasm.com/v1/play/121.m3u8?type=user&type_id=2407&X-Auth-Token=1e800bce-5da2-410e-86fd-cf3471aa4795",
        DemoUtil.TYPE_HLS_AUDIO_ONLY, false, true),
    new Sample("Audio-only2 - mp3", "uid:misc:mpeg-audio2", "https://api.beatgasm.com/v2/play/song/7758.m3u8?type=user&type_id=3032&X-Auth-Token=748be789-2116-4763-a4db-ea2a5725c69d",
        DemoUtil.TYPE_HLS_AUDIO_ONLY, false, true),
    new Sample("Audio-only3 - mp3", "uid:misc:mpeg-audio3", "https://api.beatgasm.com/v2/play/song/7484.m3u8?type=user&type_id=3032&X-Auth-Token=748be789-2116-4763-a4db-ea2a5725c69d",
        DemoUtil.TYPE_HLS_AUDIO_ONLY, false, true),
    new Sample("Apple-bipbop", "uid:misc:bipbop", "https://devimages.apple.com.edgekey.net/streaming/examples/bipbop_4x3/bipbop_4x3_variant.m3u8",
        DemoUtil.TYPE_HLS, false, true),
    new Sample("Apple-bipbop-all", "uid:misc:bipbop-all", "http://devimages.apple.com/iphone/samples/bipbop/bipbopall.m3u8",
        DemoUtil.TYPE_HLS, false, true),
    new Sample("Bipbop basic", "uid:misc:bipbop-basic", "http://playertest.longtailvideo.com/adaptive/bipbop/bipbop.m3u8",
        DemoUtil.TYPE_HLS, false, true),
    new Sample("Adaptive stream", "uid:misc:bipbop-adaptive", "http://content.jwplatform.com/manifests/vM7nH0Kl.m3u8",
        DemoUtil.TYPE_HLS, false, true),
    new Sample("Encrypted stream", "uid:misc:hls-encrypted", "http://playertest.longtailvideo.com/adaptive/oceans_aes/oceans_aes.m3u8",
        DemoUtil.TYPE_HLS, true, true),
    new Sample("CEA-608 captions", "uid:misc:hls-captions", "http://now.video.nfl.com/i/captiontest/closedcaptiontest_,350k,550k,.mp4.csmil/master.m3u8",
        DemoUtil.TYPE_HLS, false, true)
  };

  public static final Sample[] MISC = new Sample[] {
    new Sample("Dizzy", "uid:misc:dizzy", "http://html5demos.com/assets/dizzy.mp4",
        DemoUtil.TYPE_OTHER, false, true),
    new Sample("Tour de france - mp4 184p 2", "uid:misc:dailmotion-mp4184p", "http://192.168.48.171/184p_2.mp4",
        DemoUtil.TYPE_OTHER, false, true),
    new Sample("Tour de france - mp4 184p", "uid:misc:dailmotion-mp4184p", "http://192.168.48.171/184p.mp4",
        DemoUtil.TYPE_OTHER, false, true),
    new Sample("Tour de france - mp4 720p", "uid:misc:dailmotion-mp4720p", "http://www.dailymotion.com/cdn/H264-1280x720/video/x20w6of.mp4?auth=1410854598-2560-z6kb2tel-c8430f2843d7230c9e6622ba1920c77d",
        DemoUtil.TYPE_OTHER, false, true),
    new Sample("Dizzy (https->http redirect)", "uid:misc:dizzy2", "https://goo.gl/MtUDEj",
        DemoUtil.TYPE_OTHER, false, true),
  };

  private Samples() {}

}
