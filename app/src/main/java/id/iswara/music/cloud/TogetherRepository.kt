package id.iswara.music.cloud

import android.os.SystemClock
import id.iswara.music.BuildConfig
import id.iswara.music.data.LibraryRepository
import id.iswara.music.data.Preferences
import id.iswara.music.model.*
import id.iswara.music.playback.PlaybackConnection
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import okhttp3.*
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton class TogetherRepository @Inject constructor(private val auth:AuthRepository,private val playback:PlaybackConnection,private val preferences:Preferences,private val library:LibraryRepository) {
    val state=MutableStateFlow(TogetherState())
    val messages=MutableSharedFlow<String>(extraBufferCapacity=8)
    val loungeChanges=MutableSharedFlow<Unit>(extraBufferCapacity=1)
    val loungeRecords=MutableSharedFlow<JsonObject>(extraBufferCapacity=32,onBufferOverflow=kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST)
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private val mutex=Mutex()
    private val client=OkHttpClient.Builder().pingInterval(25,java.util.concurrent.TimeUnit.SECONDS).build()
    private var owner="";@Volatile private var generation=0;@Volatile private var socket:WebSocket?=null;private var worker:Job?=null
    private var personal:QueueSnapshot?=null;private var serverAnchor=0L;private var elapsedAnchor=0L
    private var lastPublish=0L;private var lastPoll=0L;private var lastApply=0L;private var lastSocket=0L;private var lastHeartbeat=0L
    private var createOperation:Pair<String,String>?=null
    private var createKind=""
    private var lastControl=0;private var desiredPlaying=false;private var pending:JsonObject?=null
    private val dirty=AtomicBoolean(false);private val refs=AtomicInteger(1)
    init {scope.launch {auth.account.map {it?.id}.distinctUntilChanged().collect {id -> if(owner.isNotBlank() && owner!=id) detach("Akun berubah; denger bareng dihentikan.")}}}
    private fun now()=SystemClock.elapsedRealtime()
    private fun serverNow()=serverAnchor+(now()-elapsedAnchor)
    private fun snapshot():QueueSnapshot {val p=playback.state.value;return QueueSnapshot(p.queue,p.index,p.position,p.shuffle,p.repeat,p.order)}
    private suspend fun rpc(name:String,body:JsonObject,uid:String=owner):JsonObject {
        check(auth.account.value?.id==uid) {"Masuk untuk menggunakan denger bareng."}
        val epoch=generation
        val token=auth.token();check(auth.account.value?.id==uid)
        val start=now()
        val result=auth.request("/rest/v1/rpc/$name",body,token).jsonObject
        if(epoch!=generation)throw CancellationException("Room berubah")
        check(auth.account.value?.id==uid) {"Akun berubah."}
        val duration=(now()-start).coerceAtLeast(0)
        state.update {it.copy(latency=duration)}
        val data=(result["state"] as? JsonObject) ?: result
        (data["server_ms"] as? JsonPrimitive)?.longOrNull?.let {serverAnchor=it+duration/2;elapsedAnchor=now()}
        return result
    }
    private fun decode(data:JsonObject)=ListeningRoom(data.text("id"),data.text("code"),data.text("title"),data.text("host_id"),AppJson.decodeFromString<List<Track>>(data["queue"].toString()),data.text("current_index").toIntOrNull() ?: 0,data.text("position_ms").toLongOrNull() ?: 0,data.text("playing").toBoolean(),data.text("revision").toLong(),data.text("changed_ms").toLong(),data.text("host_seen_ms").toLong(),data.text("expires_ms").toLong(),data.flag("closed"),data.text("members").toIntOrNull() ?: 1,data.flag("radio"))
    private fun accept(data:JsonObject) {val room=decode((data["state"] as? JsonObject) ?: data);val old=state.value.room;if(old==null || old.id==room.id && room.revision>=old.revision) state.update {it.copy(room=room,error=null)}}
    private fun action(block:suspend ()->Unit) {scope.launch {try {block()} catch(e:CancellationException){throw e} catch(e:Exception){state.update {it.copy(error=if(e is AccountServiceException && e.status==404)"Listen Together belum tersedia di server." else e.message ?: "Room belum tersambung.",busy=false)}}}}
    fun create(title:String)=action { createInternal(title,null) }
    fun createRadio(title:String,description:String,visibility:String,mood:String,nickname:String)=action {
        createInternal(title,buildJsonObject {put("p_description",description.take(240));put("p_visibility",visibility);put("p_mood",mood);put("p_nickname",nickname.take(32))})
    }
    private suspend fun createInternal(title:String,radio:JsonObject?) {
        if(state.value.room!=null || state.value.busy)return
        state.update {it.copy(busy=true,error=null)}
        val uid=auth.account.value?.id ?: error("Masuk dahulu untuk membuat room.")
        val kind=if(radio==null)"room" else "radio"
        if(createKind!=kind){createOperation=null;createKind=kind}
        val operation=createOperation?.takeIf {it.first==uid}?.second ?: UUID.randomUUID().toString().also {createOperation=uid to it}
        val result=rpc(if(radio==null)"create_listening_room" else "create_radio",buildJsonObject {put("p_title",title.trim().take(80));put("p_operation",operation);radio?.forEach {put(it.key,it.value)}},uid)
        val initial=snapshot();val wasPlaying=playback.state.value.let {it.playRequested && (it.playing || it.loading)}
        enter(uid,result)
        val indices=initial.tracks.indices.filter {TogetherRules.valid(initial.tracks[it])}.take(200)
        val shared=indices.map {initial.tracks[it]};val index=indices.indexOf(initial.index)
        val saved=QueueSnapshot(shared,index.coerceAtLeast(0),if(index>=0)initial.position else 0)
        val play=wasPlaying && index>=0
        playback.applyTogether(saved,play,true)
        createOperation=null
        try {publish(saved,play);apply(force=true)} finally {startWorker();state.update {it.copy(busy=false)}}
    }
    fun joinRadio(code:String,nickname:String)=action {
        if(state.value.room!=null || state.value.busy)return@action
        state.update {it.copy(busy=true,error=null)}
        val uid=auth.account.value?.id ?: error("Masuk dahulu untuk bergabung.")
        val result=rpc("join_radio",buildJsonObject {put("p_code",TogetherRules.code(code.substringAfter("iswara://radio/",code)));put("p_nickname",nickname.take(32))},uid)
        enter(uid,result);apply(force=true);startWorker();state.update {it.copy(busy=false)}
    }
    fun join(code:String)=action {
        if(state.value.room!=null || state.value.busy)return@action
        state.update {it.copy(busy=true,error=null)}
        val uid=auth.account.value?.id ?: error("Masuk dahulu untuk bergabung.")
        val result=rpc("join_listening_room",buildJsonObject {put("p_code",TogetherRules.code(code))},uid)
        enter(uid,result);apply(force=true);startWorker();state.update {it.copy(busy=false)}
    }
    private suspend fun enter(uid:String,data:JsonObject) {
        check(uid==auth.account.value?.id && uid==library.scope.value)
        personal=snapshot();preferences.saveQueue(uid,personal!!)
        owner=uid;generation++;lastSocket=now()-15_001;accept(data)
        preferences.set("lastTogether:$uid",state.value.room!!.code)
        playback.enterTogether(if(state.value.room!!.host==uid) "host" else "member")
        playback.onRoomPersonalPlay={detach("Keluar dari room untuk memutar pilihanmu.",notifyServer=true)}
        playback.onRoomEnqueue={track,next -> enqueue(listOf(track),next)}
        playback.onRoomToggle={toggleLocal()}
    }
    private fun startWorker() {
        val epoch=generation
        worker=scope.launch {delay(500);while(isActive && epoch==generation) {
            try {mutex.withLock {tick(epoch)}} catch(e:CancellationException){throw e} catch(e:AccountServiceException) {
                if(e.status in listOf(401,403)) {detach("Akses room berakhir.");break} else state.update {it.copy(error="Koneksi room terganggu. Mencoba kembali.")}
            } catch(_:Exception){state.update {it.copy(error="Koneksi room terganggu. Mencoba kembali.")}}
            delay(500)
        }}
    }
    private suspend fun tick(epoch:Int) {
        val room=state.value.room ?: return
        if(room.closed || serverNow()>=room.expires){detach("Room sudah berakhir.");return}
        val time=now()
        if(!state.value.realtime && time-lastSocket>15_000) connect(epoch,room.id)
        if(time-lastHeartbeat>25_000) {lastHeartbeat=time;val jwt=auth.token();send("phoenix","heartbeat",buildJsonObject {});send("realtime:iswara-${room.id}","access_token",buildJsonObject {put("access_token",jwt)})}
        if(dirty.getAndSet(false) || time-lastPoll>(if(state.value.realtime)15_000 else 3000)) {
            val previous=state.value.room
            val data=rpc("listening_state",buildJsonObject {put("p_room",room.id)});if(epoch!=generation)return;accept(data);lastPoll=now()
            if(previous?.tracks?.map {it.id}!=state.value.room?.tracks?.map {it.id}) apply(force=true)
        }
        val current=state.value.room ?: return
        if(current.host==owner) {
            val p=playback.state.value
            val hostPlaying=p.playRequested && (p.playing || p.loading)
            val changed=p.queue.map {it.id}!=current.tracks.map {it.id} || p.index!=current.index || hostPlaying!=current.playing || kotlin.math.abs(p.position-TogetherRules.position(current,serverNow()))>1800 || lastControl!=playback.roomControl.value
            if(changed || time-lastPublish>8000) {publish(snapshot(),hostPlaying);lastControl=playback.roomControl.value}
        } else {
            val p=playback.state.value
            if(desiredPlaying && !p.playRequested && !p.loading && time-lastApply>2500 && !state.value.waitingHost) state.update {it.copy(localPaused=true)}
            if(state.value.localPaused && p.playRequested && !desiredPlaying)state.update {it.copy(localPaused=false)}
            apply()
        }
    }
    private suspend fun publish(saved:QueueSnapshot,playing:Boolean) {
        val room=state.value.room ?: return
        check(saved.tracks.all(TogetherRules::valid)) {"File lokal tidak dapat dibagikan ke room."}
        val data=rpc("listening_command",buildJsonObject {put("p_room",room.id);put("p_action","state");put("p_operation",UUID.randomUUID().toString());put("p_tracks",AppJson.parseToJsonElement(AppJson.encodeToString(saved.tracks.take(200))));put("p_index",saved.index.coerceIn(0,(saved.tracks.size-1).coerceAtLeast(0)));put("p_position",saved.position);put("p_playing",playing);put("p_expected",room.revision)})
        accept(data);lastPublish=now();lastPoll=now()
        if(data.flag("conflict")){apply(force=true);messages.tryEmit("Antrean diperbarui anggota. Kontrol host bisa diulangi.")}
    }
    private fun apply(force:Boolean=false) {
        val room=state.value.room ?: return
        val waiting=room.host!=owner && TogetherRules.hostMissing(room,serverNow())
        state.update {it.copy(waitingHost=waiting)}
        val play=room.playing && !state.value.localPaused && !waiting && !room.closed
        val position=TogetherRules.position(room,serverNow())
        val p=playback.state.value
        val replace=force || p.queue.map {it.id}!=room.tracks.map {it.id} || p.index!=room.index
        if(replace || p.playing!=play && !p.loading || kotlin.math.abs(p.position-position)>1800 && !p.loading) {
            lastApply=now();desiredPlaying=play
            playback.applyTogether(QueueSnapshot(room.tracks,room.index,position),play,replace)
        }
    }
    fun toggleLocal() {val room=state.value.room ?: return;if(!room.playing && !state.value.localPaused){messages.tryEmit("Menunggu host memutar musik.");return};state.update {it.copy(localPaused=!it.localPaused)};apply(force=false)}
    fun enqueue(tracks:List<Track>,next:Boolean=false)=action {
        require(tracks.isNotEmpty() && tracks.size<=20 && tracks.all(TogetherRules::valid)) {"Tambahkan 1-20 lagu streaming."}
        mutex.withLock {
            check(pending==null) {"Selesaikan penambahan sebelumnya dahulu."}
            val room=state.value.room ?: error("Bergabung ke room dahulu.")
            pending=buildJsonObject {put("p_room",room.id);put("p_action",if(next && room.host==owner)"enqueue_next" else "enqueue");put("p_operation",UUID.randomUUID().toString());put("p_tracks",AppJson.parseToJsonElement(AppJson.encodeToString(tracks)))}
            flushPending()
        }
    }
    fun retryEnqueue()=action {mutex.withLock {flushPending()}}
    private suspend fun flushPending() {val body=pending ?: return;try {val response=rpc("listening_command",body);accept(response);pending=null;apply(force=false);messages.tryEmit("Lagu ditambahkan ke antrean room.")} catch(e:AccountServiceException){if(e.status in 400..499)pending=null;throw e}}
    fun refresh() {dirty.set(true)}
    fun hasPending()=pending!=null
    fun leave() {detach(null,notifyServer=true)}
    private fun detach(message:String?,notifyServer:Boolean=false) {
        val old=state.value.room;val uid=owner
        generation++;worker?.cancel();worker=null;socket?.close(1000,"leave");socket=null;pending=null
        playback.onRoomPersonalPlay=null;playback.onRoomEnqueue=null;playback.onRoomToggle=null;playback.exitTogether()
        state.value=TogetherState(error=message);owner="";personal=null;desiredPlaying=false
        if(message!=null)messages.tryEmit(message)
        if(notifyServer && old!=null)scope.launch {runCatching {if(auth.account.value?.id==uid) auth.request("/rest/v1/rpc/leave_listening_room",buildJsonObject {put("p_room",old.id)},auth.token())}}
    }
    private fun send(topic:String,event:String,payload:JsonObject) {socket?.send(buildJsonObject {put("topic",topic);put("event",event);put("payload",payload);put("ref",refs.incrementAndGet().toString());put("join_ref","1")}.toString())}
    private suspend fun connect(epoch:Int,id:String) {
        lastSocket=now();val token=auth.token();if(epoch!=generation)return
        socket?.close(1000,"reconnect")
        val url=BuildConfig.SUPABASE_URL.replaceFirst("https://","wss://").trimEnd('/')+"/realtime/v1/websocket?apikey="+java.net.URLEncoder.encode(BuildConfig.SUPABASE_ANON_KEY,"UTF-8")+"&vsn=1.0.0"
        val topic="realtime:iswara-$id"
        socket=client.newWebSocket(Request.Builder().url(url).build(),object:WebSocketListener() {
            override fun onOpen(ws:WebSocket,response:Response) {ws.send(buildJsonObject {put("topic",topic);put("event","phx_join");put("ref","1");put("join_ref","1");put("payload",buildJsonObject {put("access_token",token);put("config",buildJsonObject {put("private",false);put("broadcast",buildJsonObject {put("self",false)});put("presence",buildJsonObject {put("enabled",false)});put("postgres_changes",buildJsonArray {
                add(buildJsonObject {put("event","UPDATE");put("schema","public");put("table","listening_rooms");put("filter","id=eq.$id")})
                if(state.value.room?.radio==true)for(table in listOf("radio_messages","radio_channels"))add(buildJsonObject {put("event","*");put("schema","public");put("table",table);put("filter","room_id=eq.$id")})
            })})})}.toString())}
            override fun onMessage(ws:WebSocket,text:String) {if(epoch!=generation || ws!==socket || text.length>1_000_000)return;runCatching {val data=AppJson.parseToJsonElement(text).jsonObject
                if(data.text("event")=="phx_reply" && data.text("ref")=="1")state.update {it.copy(realtime=(data["payload"] as? JsonObject)?.let {p -> p.text("status")=="ok" && ((p["response"] as? JsonObject)?.get("postgres_changes") as? JsonArray)?.isNotEmpty()==true}==true)}
                if(data.text("event")=="system") {val p=data["payload"] as? JsonObject;if(p?.text("extension")=="postgres_changes")state.update {it.copy(realtime=p.text("status")=="ok")}}
                if(data.text("event")=="postgres_changes") {
                    val payload=data["payload"] as? JsonObject
                    val table=(payload?.get("data") as? JsonObject)?.text("table").orEmpty()
                    if(table=="listening_rooms" || table.isBlank())dirty.set(true) else {
                        loungeChanges.tryEmit(Unit)
                        if(table=="radio_messages")((payload?.get("data") as? JsonObject)?.get("record") as? JsonObject)?.let(loungeRecords::tryEmit)
                    }
                }
                if(data.text("event") in listOf("phx_error","phx_close"))state.update {it.copy(realtime=false)}
            }}
            override fun onFailure(ws:WebSocket,t:Throwable,response:Response?) {if(epoch==generation && ws===socket)state.update {it.copy(realtime=false)}}
            override fun onClosed(ws:WebSocket,code:Int,reason:String) {if(epoch==generation && ws===socket)state.update {it.copy(realtime=false)}}
        })
    }
}
