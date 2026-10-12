package com.personal.voyager.dl

import java.io.File
import java.io.RandomAccessFile

/** Title, artist, album and cover for MP3 (ID3v2.3) and M4A (iTunes style atoms). */
object Tags {
    private fun u32(v: Int) = byteArrayOf((v ushr 24).toByte(), (v ushr 16).toByte(), (v ushr 8).toByte(), v.toByte())
    private fun syncsafe(v: Int) =
        byteArrayOf(((v shr 21) and 0x7F).toByte(), ((v shr 14) and 0x7F).toByte(), ((v shr 7) and 0x7F).toByte(), (v and 0x7F).toByte())
    private val latin1 = Charsets.ISO_8859_1

    // ---------------- MP3 ----------------

    private fun textFrame(id: String, text: String): ByteArray {
        val payload = byteArrayOf(1, 0xFF.toByte(), 0xFE.toByte()) + text.toByteArray(Charsets.UTF_16LE)
        return id.toByteArray(Charsets.US_ASCII) + u32(payload.size) + byteArrayOf(0, 0) + payload
    }

    /** Bytes to write at the very start of the .mp3 file. */
    fun id3(title: String, artist: String, album: String, cover: ByteArray?): ByteArray {
        var frames = textFrame("TIT2", title) + textFrame("TPE1", artist) + textFrame("TALB", album)
        if (cover != null) {
            val payload = byteArrayOf(0) + "image/jpeg".toByteArray(Charsets.US_ASCII) + byteArrayOf(0, 3, 0) + cover
            frames += "APIC".toByteArray(Charsets.US_ASCII) + u32(payload.size) + byteArrayOf(0, 0) + payload
        }
        return "ID3".toByteArray(Charsets.US_ASCII) + byteArrayOf(3, 0, 0) + syncsafe(frames.size) + frames
    }

    // ---------------- M4A ----------------

    private fun box(type: ByteArray, payload: ByteArray) = u32(payload.size + 8) + type + payload
    private fun box(type: String, payload: ByteArray) = box(type.toByteArray(latin1), payload)
    private fun atom(name: String) = byteArrayOf(0xA9.toByte()) + name.toByteArray(latin1)
    private fun dataBox(flags: Int, data: ByteArray) = box("data", u32(flags) + u32(0) + data)

    /**
     * Appends udta/meta/ilst inside moov. Only when moov is the last box of the file (what MediaMuxer writes);
     * otherwise it leaves the file untouched and returns false.
     */
    fun mp4(file: File, title: String, artist: String, album: String, cover: ByteArray?): Boolean {
        try {
            RandomAccessFile(file, "rw").use { f ->
                val len = f.length()
                var pos = 0L
                var moovPos = -1L
                var moovSize = 0L
                while (pos + 8 <= len) {
                    f.seek(pos)
                    val raw = f.readInt().toLong() and 0xFFFFFFFFL
                    val type = ByteArray(4)
                    f.readFully(type)
                    if (raw == 1L) return false // 64 bit sizes are not handled
                    val size = if (raw == 0L) len - pos else raw
                    if (size < 8) return false
                    if (String(type, latin1) == "moov") {
                        moovPos = pos
                        moovSize = size
                    }
                    pos += size
                }
                if (moovPos < 0 || moovPos + moovSize != len) return false

                // Skip when moov already has a udta.
                var child = moovPos + 8
                while (child + 8 <= len) {
                    f.seek(child)
                    val sz = f.readInt().toLong() and 0xFFFFFFFFL
                    val t = ByteArray(4)
                    f.readFully(t)
                    if (String(t, latin1) == "udta") return false
                    if (sz < 8) break
                    child += sz
                }

                var items = box(atom("nam"), dataBox(1, title.toByteArray(Charsets.UTF_8))) +
                    box(atom("ART"), dataBox(1, artist.toByteArray(Charsets.UTF_8))) +
                    box(atom("alb"), dataBox(1, album.toByteArray(Charsets.UTF_8)))
                if (cover != null) items += box("covr", dataBox(13, cover))
                val ilst = box("ilst", items)
                val hdlr = box("hdlr", ByteArray(8) + "mdir".toByteArray(latin1) + "appl".toByteArray(latin1) + ByteArray(9))
                val udta = box("udta", box("meta", ByteArray(4) + hdlr + ilst))

                f.seek(moovPos)
                f.writeInt((moovSize + udta.size).toInt())
                f.seek(len)
                f.write(udta)
            }
            return true
        } catch (e: Exception) {
            return false
        }
    }
}
