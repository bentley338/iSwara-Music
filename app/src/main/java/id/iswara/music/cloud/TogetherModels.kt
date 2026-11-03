package id.iswara.music.cloud

import id.iswara.music.model.Track

data class ListeningRoom(val id:String,val code:String,val title:String,val host:String,val tracks:List<Track>,val index:Int,val position:Long,val playing:Boolean,val revision:Long,val changed:Long,val hostSeen:Long,val expires:Long,val closed:Boolean,val members:Int,val radio:Boolean=false)
data class TogetherState(val room:ListeningRoom?=null,val busy:Boolean=false,val error:String?=null,val realtime:Boolean=false,val localPaused:Boolean=false,val waitingHost:Boolean=false,val latency:Long=0)
object TogetherRules {
    fun code(text:String):String = text.substringAfter("iswara://room/",text).substringBefore('?').uppercase(java.util.Locale.ROOT).filter(Char::isLetterOrDigit).take(12)
    fun valid(track:Track)=!track.local && track.id.matches(Regex("[A-Za-z0-9_-]{11}"))
    fun position(room:ListeningRoom,serverNow:Long):Long {
        val limit=room.tracks.getOrNull(room.index)?.duration?.takeIf {it>0}?.coerceAtMost(86400)?.times(1000) ?: 86_400_000L
        return (room.position+(if(room.playing)(serverNow-room.changed).coerceIn(0,86_400_000) else 0)).coerceIn(0,limit)
    }
    fun hostMissing(room:ListeningRoom,serverNow:Long)=serverNow-room.hostSeen>30_000
}
