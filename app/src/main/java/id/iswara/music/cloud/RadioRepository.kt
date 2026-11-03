package id.iswara.music.cloud

import id.iswara.music.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

data class RadioChannel(val id:String,val code:String,val title:String,val description:String,val mood:String,val host:String,val playing:Boolean,val members:Int,val track:Track?,val created:String)
data class RadioMessage(val id:Long,val sender:String,val nickname:String,val kind:String,val body:String,val removed:Boolean)
data class RadioDirectory(val items:List<RadioChannel> = emptyList(),val loading:Boolean=false,val error:String?=null,val nextCreated:String?=null,val nextId:String?=null)
data class RadioLounge(val room:String="",val owner:String="",val description:String="",val visibility:String="public",val mood:String="all",val nickname:String="",val host:String="",val messages:List<RadioMessage> = emptyList(),val moderator:Boolean=false,val loading:Boolean=false,val sending:Boolean=false,val error:String?=null,val pending:Boolean=false,val sent:Long=0,val sentText:String?=null,val olderLoading:Boolean=false,val olderAvailable:Boolean=true)

object RadioRules {
    val moods=listOf("all","chill","focus","pop","indie","night","energy")
    val reactions=mapOf("heart" to "♥","fire" to "🔥","clap" to "👏","music" to "♫","wow" to "✨")
    fun nickname(value:String)=value.trim().filterNot(Char::isISOControl).take(32)
    fun message(value:String)=value.trim().filterNot(Char::isISOControl).take(280)
    fun merge(old:List<RadioMessage>,fresh:List<RadioMessage>)=(old+fresh).associateBy {it.id}.values.sortedBy {it.id}.takeLast(200)
    fun decodeMessage(row:JsonObject)=RadioMessage(row.text("id").toLong(),row.text("sender_id"),row.text("nickname"),row.text("kind"),if(row.flag("removed"))"" else row.text("body"),row.flag("removed"))
}

@Singleton class RadioRepository @Inject constructor(private val auth:AuthRepository,val together:TogetherRepository) {
    val directory=MutableStateFlow(RadioDirectory())
    val lounge=MutableStateFlow(RadioLounge())
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private var searchJob:Job?=null
    private var query="";private var mood="all"
    private val dirty=AtomicBoolean(true)
    private data class Pending(val owner:String,val room:String,val kind:String,val text:String,val operation:String)
    private var pending:Pending?=null
    init {
        scope.launch {auth.account.map {it?.id}.distinctUntilChanged().collect {searchJob?.cancel();directory.value=RadioDirectory()}}
        scope.launch {together.loungeChanges.collect {dirty.set(true)}}
        scope.launch {together.loungeRecords.collect {record->
            val current=lounge.value
            if(record.text("room_id")==current.room && context(current.room,current.owner))runCatching {RadioRules.decodeMessage(record)}.getOrNull()?.let {message->lounge.update {it.copy(messages=RadioRules.merge(it.messages,listOf(message)))}}
        }}
        scope.launch {
            combine(auth.account.map {it?.id},together.state.map {it.room?.takeIf {room->room.radio}?.id}.distinctUntilChanged()) {owner,room->owner to room}
                .distinctUntilChanged().collectLatest {(owner,room)->
                    pending=null
                    lounge.value=RadioLounge(room=room.orEmpty(),owner=owner.orEmpty(),loading=room!=null)
                    if(owner==null || room==null)return@collectLatest
                    dirty.set(true)
                    var last=0L
                    while(currentCoroutineContext().isActive) {
                        val now=android.os.SystemClock.elapsedRealtime()
                        if(dirty.getAndSet(false) || now-last>(if(together.state.value.realtime)12_000 else 3000)) {
                            last=now
                            try {fetch(room,owner)}catch(e:CancellationException){throw e}catch(e:Exception) {if(context(room,owner))lounge.update {it.copy(loading=false,error=failure(e))}}
                        }
                        delay(750)
                    }
                }
        }
    }
    private fun context(room:String,owner:String)=auth.account.value?.id==owner && together.state.value.room?.id==room && together.state.value.room?.radio==true
    private suspend fun rpc(name:String,body:JsonObject,room:String,owner:String):JsonElement {
        check(context(room,owner)) {"Siaran atau akun berubah. Buka lounge lagi."}
        val jwt=auth.token();check(context(room,owner))
        val response=auth.request("/rest/v1/rpc/$name",body,jwt)
        check(context(room,owner)) {"Siaran atau akun berubah."}
        return response
    }
    private fun failure(e:Exception)=if(e is AccountServiceException && e.status==404)"Radio belum tersedia di server. Coba lagi nanti." else e.message?.take(180) ?: "Radio belum tersambung. Coba lagi."
    fun discover(text:String=query,category:String=mood,more:Boolean=false) {
        if(more && directory.value.loading)return
        val sameQuery=text.trim().take(80)==query && category==mood
        query=text.trim().take(80);mood=category.takeIf {it in RadioRules.moods} ?: "all"
        searchJob?.cancel()
        val before=if(more)directory.value.nextCreated else null
        val beforeId=if(more)directory.value.nextId else null
        if(more && before==null)return
        val old=if(more || sameQuery)directory.value.items else emptyList()
        directory.value=RadioDirectory(old,loading=true)
        val requestQuery=query;val requestMood=mood
        searchJob=scope.launch {
            try {
                val data=withTimeout(20_000) {auth.request("/rest/v1/rpc/discover_radio",buildJsonObject {
                    put("p_query",requestQuery);put("p_mood",requestMood)
                    if(before!=null && beforeId!=null){put("p_before",before);put("p_before_id",beforeId)}
                }).jsonObject}
                val items=(data["items"] as? JsonArray).orEmpty().mapNotNull {value->runCatching {val row=value.jsonObject;RadioChannel(row.text("id"),row.text("code"),row.text("title"),row.text("description"),row.text("mood"),row.text("host_name"),row.flag("playing"),row.text("members").toIntOrNull() ?: 0,row["track"]?.takeIf {it is JsonObject}?.let {AppJson.decodeFromJsonElement<Track>(it)},row.text("created_at"))}.getOrNull()}
                directory.value=RadioDirectory((if(more)old+items else items).distinctBy {it.id},nextCreated=data["next_created"]?.jsonPrimitive?.contentOrNull,nextId=data["next_id"]?.jsonPrimitive?.contentOrNull)
            } catch(e:TimeoutCancellationException){directory.value=RadioDirectory(old,error="Pencarian siaran terlalu lama. Coba lagi.")}
            catch(e:CancellationException){throw e}catch(e:Exception){directory.value=RadioDirectory(old,error=failure(e))}
        }
    }
    private suspend fun fetch(room:String,owner:String,before:Long?=null) {
        val data=rpc("radio_state",buildJsonObject {put("p_room",room);before?.let {put("p_before",it)}},room,owner).jsonObject
        val fresh=(data["messages"] as? JsonArray).orEmpty().mapNotNull {runCatching {RadioRules.decodeMessage(it.jsonObject)}.getOrNull()}
        if(!context(room,owner))return
        lounge.update {old->old.copy(description=data.text("description"),visibility=data.text("visibility"),mood=data.text("mood"),nickname=data.text("nickname"),host=data.text("host_name"),
            messages=RadioRules.merge(old.messages,fresh),moderator=data.flag("can_moderate"),loading=false,error=null,olderLoading=false,olderAvailable=if(before!=null)fresh.size==50 else old.olderAvailable)}
    }
    fun refresh(){dirty.set(true)}
    fun older() {
        val state=lounge.value
        if(state.olderLoading || !state.olderAvailable || state.messages.isEmpty())return
        lounge.update {it.copy(olderLoading=true)}
        scope.launch {try {fetch(state.room,state.owner,state.messages.first().id)}catch(e:CancellationException){throw e}catch(e:Exception){if(context(state.room,state.owner))lounge.update {it.copy(olderLoading=false,error=failure(e))}}}
    }
    fun post(kind:String,text:String) {
        val current=lounge.value
        if(current.sending || pending!=null)return
        val clean=RadioRules.message(text)
        if(clean.isBlank() || kind !in setOf("comment","reaction") || kind=="reaction" && clean !in RadioRules.reactions)return
        pending=Pending(current.owner,current.room,kind,clean,UUID.randomUUID().toString())
        sendPending()
    }
    fun retry()=sendPending()
    fun discard(){if(!lounge.value.sending){pending=null;lounge.update {it.copy(pending=false,error=null)}}}
    private fun sendPending() {
        val value=pending ?: return
        if(lounge.value.sending)return
        lounge.update {it.copy(sending=true,pending=true,error=null)}
        scope.launch {
            try {
                rpc("radio_post",buildJsonObject {put("p_room",value.room);put("p_kind",value.kind);put("p_body",value.text);put("p_operation",value.operation)},value.room,value.owner)
                if(pending==value)pending=null
                lounge.update {it.copy(sending=false,pending=false,sent=it.sent+1,sentText=if(value.kind=="comment")value.text else null)}
                dirty.set(true)
            }catch(e:CancellationException){throw e}catch(e:Exception) {
                if(context(value.room,value.owner))lounge.update {it.copy(sending=false,pending=true,error=failure(e))}
            }
        }
    }
    fun update(title:String,description:String,visibility:String,mood:String) {
        val state=lounge.value
        scope.launch {try {
            rpc("radio_update",buildJsonObject {put("p_room",state.room);put("p_title",title.take(80));put("p_description",description.take(240));put("p_visibility",visibility);put("p_mood",mood)},state.room,state.owner)
            together.refresh();dirty.set(true)
        }catch(e:CancellationException){throw e}catch(e:Exception){if(context(state.room,state.owner))lounge.update {it.copy(error=failure(e))}}}
    }
    fun moderate(action:String,message:RadioMessage) {
        val state=lounge.value
        scope.launch {try {
            rpc("radio_moderate",buildJsonObject {put("p_room",state.room);put("p_action",action);put("p_message",message.id);put("p_target",message.sender)},state.room,state.owner)
            if(action=="remove")lounge.update {it.copy(messages=it.messages.map {row->if(row.id==message.id)row.copy(body="",removed=true)else row})}
            dirty.set(true);together.refresh()
        }catch(e:CancellationException){throw e}catch(e:Exception){if(context(state.room,state.owner))lounge.update {it.copy(error=failure(e))}}}
    }
}
