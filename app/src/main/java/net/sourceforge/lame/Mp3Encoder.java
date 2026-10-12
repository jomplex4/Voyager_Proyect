package net.sourceforge.lame;

import net.sourceforge.lame.mp3.Lame;
import net.sourceforge.lame.mp3.MPEGMode;

/** Minimal CBR MP3 encoder wrapper around the pure-Java LAME port (no javax.sound). */
public final class Mp3Encoder {
    private final Lame lame = new Lame();
    private final int channels;
    private float[] left = new float[0];
    private float[] right = new float[0];

    public Mp3Encoder(int channels, int sampleRate, int bitrateKbps, int quality) {
        if (channels < 1 || channels > 2) throw new IllegalArgumentException("channels");
        this.channels = channels;
        if (sampleRate < 32000 && bitrateKbps > 160) bitrateKbps = 160;
        lame.getFlags().setInNumChannels(channels);
        lame.getFlags().setInSampleRate(sampleRate);
        lame.getFlags().setMode(channels == 1 ? MPEGMode.MONO : MPEGMode.STEREO);
        lame.getFlags().setBitRate(bitrateKbps);
        lame.getFlags().setQuality(quality);
        lame.getId3().init(lame.getFlags());
        lame.getFlags().setWriteId3tagAutomatic(false);
        lame.getFlags().setFindReplayGain(false);
        int rc = lame.initParams();
        if (rc < 0) throw new IllegalArgumentException("LAME init failed: " + rc);
    }

    /** pcm: interleaved 16-bit samples; count = number of shorts to use. Returns bytes written. */
    public int encode(short[] pcm, int offset, int count, byte[] out) {
        int frames = count / channels;
        if (frames <= 0) return 0;
        if (left.length < frames) {
            left = new float[frames];
            right = new float[frames];
        }
        int p = offset;
        if (channels == 2) {
            for (int i = 0; i < frames; i++) {
                left[i] = pcm[p++] * 65536f;
                right[i] = pcm[p++] * 65536f;
            }
        } else {
            for (int i = 0; i < frames; i++) {
                float v = pcm[p++] * 65536f;
                left[i] = v;
                right[i] = v;
            }
        }
        int r = lame.encodeBuffer(left, right, frames, out);
        if (r < 0) throw new IllegalStateException("LAME encode error: " + r);
        return r;
    }

    public int finish(byte[] out) {
        int r = lame.encodeFlush(out);
        if (r < 0) throw new IllegalStateException("LAME flush error: " + r);
        return r;
    }

    public void close() {
        lame.close();
    }
}
