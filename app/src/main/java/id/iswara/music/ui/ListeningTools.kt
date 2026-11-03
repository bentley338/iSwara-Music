package id.iswara.music.ui

import id.iswara.music.model.LyricLine
import id.iswara.music.model.Track
import java.util.Locale

object ListeningTools {
    fun activeLine(lines: List<LyricLine>, position: Long, offset: Long): Int {
        val time = (position.coerceIn(0,Long.MAX_VALUE-15_000) + offset.coerceIn(-15_000,15_000))
        var low = 0; var high = lines.lastIndex; var result = -1
        while (low <= high) {
            val mid = (low + high).ushr(1)
            if (lines[mid].time <= time) { result = mid; low = mid+1 } else high = mid-1
        }
        return result
    }
    fun lyricSeek(time: Long, offset: Long, duration: Long): Long =
        (time.coerceIn(0,Long.MAX_VALUE-15_000) - offset.coerceIn(-15_000,15_000)).coerceIn(0,if(duration > 0) duration else Long.MAX_VALUE)
    fun queueMatches(queue: List<Track>, order: List<Int>, query: String): List<Int> {
        val words = query.trim().lowercase(Locale.ROOT).split(Regex("\\s+")).filter(String::isNotEmpty)
        return order.filter { index -> index in queue.indices && words.all { word -> "${queue[index].title} ${queue[index].artist}".lowercase(Locale.ROOT).contains(word) } }
    }
    fun offsetLabel(offset: Long): String = String.format(Locale.ROOT,"%+.1f",offset / 1000.0)
}
