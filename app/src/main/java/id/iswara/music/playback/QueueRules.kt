package id.iswara.music.playback

import id.iswara.music.model.Track

object QueueRules {
    val modes=listOf("balanced" to "Seimbang","familiar" to "Lebih akrab","discovery" to "Temukan baru")
    fun smart(seed:Track,existing:List<Track>,related:List<Track>,recent:List<String> = emptyList(),favorites:List<Track> = emptyList(),history:List<Track> = emptyList(),mode:String="balanced",limit:Int=15):List<Track> {
        fun artist(t:Track)=id.iswara.music.source.SourceMatchRules.clean(id.iswara.music.source.LyricsRules.cleanArtist(t.artist))
        fun identity(t:Track)=artist(t)+":"+id.iswara.music.source.SourceMatchRules.clean(id.iswara.music.source.LyricsRules.cleanTitle(t.title,t.artist))
        val old=(existing+listOf(seed)).map(::identity).toMutableSet();val excluded=existing.map {it.id}.toSet()+recent+seed.id
        val favoriteIds=favorites.map {it.id}.toSet();val known=history.map {it.id}.toSet();val affinity=(favorites+history.take(40)).groupingBy(::artist).eachCount()
        val versions=Regex("(?i)\\b(live|cover|remix|slowed|sped|karaoke|instrumental)\\b")
        val seedVersions=versions.findAll(seed.title).map {it.value.lowercase()}.toSet()
        val candidates=related.distinctBy {it.id}.filter {!it.local && it.id !in excluded && it.duration in 30..1800 && identity(it) !in old && versions.findAll(it.title).map {m->m.value.lowercase()}.toSet()==seedVersions}.toMutableList()
        val rank=candidates.mapIndexed {i,t->t.id to i}.toMap();val result=mutableListOf<Track>();val counts=mutableMapOf<String,Int>();var last=existing.lastOrNull()?.let(::artist) ?: artist(seed)
        repeat(limit.coerceIn(0,50)) {
            val pool=candidates.filter {artist(it)!=last}.ifEmpty {candidates}
            val choice=pool.maxByOrNull {t->
                val a=artist(t);val familiar=(affinity[a] ?: 0).coerceAtMost(5)/5.0
                2.0/(1+(rank[t.id] ?: 0)*.12)+when(mode){"familiar"->familiar*1.2+(if(t.id in favoriteIds).4 else 0.0);"discovery"->(if(t.id !in known).8 else -.8)-familiar*.3;else->familiar*.3+(if(t.id !in known).25 else 0.0)}-(counts[a] ?: 0)*1.1-(if(a==last)1.5 else 0.0)
            } ?: return result
            candidates.remove(choice)
            if(old.add(identity(choice))){result+=choice;last=artist(choice);counts[last]=(counts[last] ?: 0)+1}
            candidates.removeAll {identity(it) in old}
        }
        return result
    }
    fun radio(existing: List<Track>, related: List<Track>, recent: List<String> = emptyList(), limit: Int = 15): List<Track> {
        val excluded = existing.map { it.id }.toSet() + recent
        return related.asSequence().filter { !it.local && it.id !in excluded && it.duration in 30..1800 }
            .distinctBy { it.id }.take(limit).toList()
    }
    fun append(existing: List<Track>, incoming: List<Track>, duplicates: Boolean): List<Track> =
        if (duplicates) incoming else incoming.distinctBy { it.id }.filter { track -> existing.none { it.id == track.id } }
}
