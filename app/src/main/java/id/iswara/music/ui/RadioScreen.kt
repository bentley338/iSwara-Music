package id.iswara.music.ui

import android.content.Intent
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import id.iswara.music.cloud.*
import kotlinx.coroutines.delay

@Composable fun radioMoodLabel(key:String)=when(key){"chill"->tr("Santai","Chill");"focus"->tr("Fokus","Focus");"pop"->"Pop";"indie"->"Indie";"night"->tr("Malam","Night");"energy"->tr("Energi","Energy");else->tr("Semua","All")}

@Composable fun RadioScreen(vm:MusicViewModel,onLogin:()->Unit,onTogether:()->Unit,onQueue:()->Unit) {
    val account=vm.auth.account.collectAsStateWithLifecycle().value
    val together=vm.together.state.collectAsStateWithLifecycle().value
    val directory=vm.radio.directory.collectAsStateWithLifecycle().value
    val invite=vm.radioInvite.collectAsStateWithLifecycle().value
    val savedName=remember(account?.id){vm.preferences.raw("radioNickname:${account?.id ?: "guest"}")}.collectAsStateWithLifecycle(initialValue=null).value
    var mode by rememberSaveable(account?.id){mutableStateOf("browse")}
    var query by rememberSaveable {mutableStateOf("")}
    var mood by rememberSaveable {mutableStateOf("all")}
    var code by rememberSaveable {mutableStateOf("")}
    var nickname by rememberSaveable(account?.id){mutableStateOf("")}
    val room=together.room
    LaunchedEffect(query,mood){delay(350);vm.radio.discover(query,mood)}
    LaunchedEffect(savedName){if(nickname.isBlank() && !savedName.isNullOrBlank())nickname=savedName.orEmpty()}
    LaunchedEffect(invite){if(invite.isNotBlank()){code=invite;mode="join";vm.radioInvite.value=""}}
    LaunchedEffect(room?.id){if(room?.radio==true)mode="lounge" else if(mode=="lounge" || mode=="edit")mode="browse"}
    androidx.activity.compose.BackHandler(mode!="browse"){mode=if(mode=="edit")"lounge" else "browse"}
    val saveNickname={val uid=account?.id;if(uid!=null)vm.action {vm.preferences.setOwnerValue(uid,"radioNickname:$uid",RadioRules.nickname(nickname))}}
    Box(Modifier.fillMaxSize(),contentAlignment=Alignment.TopCenter) {
    if(mode=="lounge" && room?.radio==true)RadioLounge(vm,{mode="browse"},{mode="edit"},onTogether,onQueue)
    else if(mode in listOf("create","join","edit")) {
        RadioForm(vm,mode,code,nickname,{nickname=it},{code=it},{mode=if(mode=="edit")"lounge" else "browse"},onLogin) {title,description,visibility,category->
            saveNickname()
            when(mode){"create"->vm.together.createRadio(title,description,visibility,category,nickname);"join"->vm.together.joinRadio(code,nickname);else->{vm.radio.update(title,description,visibility,category);mode="lounge"}}
        }
    } else LazyColumn(Modifier.widthIn(max=1000.dp).fillMaxSize().testTag("radio-list"),contentPadding=PaddingValues(bottom=24.dp)) {
        item(key="header") {PageHeading("Radio",tr("Siaran pribadi. Suasana bersama.","Your station. A shared atmosphere.")) {ActionIcon(Icons.Rounded.Refresh,tr("Segarkan siaran","Refresh stations"),{vm.radio.discover(query,mood)})}}
        item(key="go-live") {
            Surface(shape=RoundedCornerShape(24.dp),color=MaterialTheme.colorScheme.primaryContainer,contentColor=MaterialTheme.colorScheme.onPrimaryContainer,modifier=Modifier.fillMaxWidth().padding(horizontal=20.dp)) {
                Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Rounded.Radio,null,Modifier.size(36.dp))
                    Text(tr("Jadi tuan rumah\nuntuk musikmu.","Give your music\na place to meet."),style=MaterialTheme.typography.headlineMedium)
                    Text(tr("Putar pilihanmu, bagikan siaran, dan ngobrol sambil mendengarkan.","Play your picks, share a station, and chat while listening."),style=MaterialTheme.typography.bodyMedium)
                    FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        Button({if(account==null)onLogin()else mode="create"},enabled=room==null && !together.busy){Icon(Icons.Rounded.Podcasts,null);Spacer(Modifier.width(8.dp));Text("Go Live")}
                        OutlinedButton({mode="join"},enabled=room==null && !together.busy){Text(tr("Masukkan kode","Enter a code"))}
                    }
                }
            }
        }
        if(room!=null)item(key="current-room") {
            Surface(onClick={if(room.radio)mode="lounge"else onTogether()},color=MaterialTheme.colorScheme.surfaceContainerHigh,shape=RoundedCornerShape(16.dp),modifier=Modifier.fillMaxWidth().padding(20.dp)) {
                Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    Icon(if(room.radio)Icons.Rounded.Radio else Icons.Rounded.Groups,null,tint=MaterialTheme.colorScheme.primary)
                    Column(Modifier.weight(1f)){Text(room.title,style=MaterialTheme.typography.titleMedium);Text(tr("Kembali ke sesi aktif","Return to your active session"),style=MaterialTheme.typography.bodySmall)}
                    Icon(Icons.Rounded.ChevronRight,null)
                }
            }
        }
        item(key="search") {
            OutlinedTextField(query,{query=it.take(80)},label={Text(tr("Cari siaran atau host","Search stations or hosts"))},leadingIcon={Icon(Icons.Rounded.Search,null)},singleLine=true,shape=RoundedCornerShape(16.dp),modifier=Modifier.fillMaxWidth().padding(horizontal=20.dp,vertical=12.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal=20.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                RadioRules.moods.forEach {key->FilterChip(mood==key,{mood=key},{Text(radioMoodLabel(key))})}
            }
        }
        item(key="stations-title"){SectionTitle(tr("Sedang bersiaran","On air"))}
        if(directory.loading && directory.items.isEmpty())item(key="loading"){LoadingRows()}
        if(directory.error!=null)item(key="error"){EmptyState(tr("Radio belum tersambung","Radio is unavailable"),directory.error,Icons.Rounded.WifiOff,tr("Coba lagi","Retry"),{vm.radio.discover(query,mood)})}
        if(!directory.loading && directory.error==null && directory.items.isEmpty())item(key="empty"){
            EmptyState(tr("Belum ada siaran yang cocok","No matching stations"),tr("Coba suasana lain atau mulai siaranmu sendiri.","Try another mood or start your own station."),Icons.Rounded.Radio)
        }
        items(directory.items,key={it.id}) {channel->RadioStationRow(channel,enabled=room==null && !together.busy){code=channel.code;mode="join"}}
        if(directory.nextCreated!=null && !directory.loading)item(key="more"){OutlinedButton({vm.radio.discover(query,mood,true)},Modifier.fillMaxWidth().padding(20.dp)){Text(tr("Siaran lainnya","More stations"))}}
        if(directory.loading && directory.items.isNotEmpty())item{LinearProgressIndicator(Modifier.fillMaxWidth().padding(20.dp))}
        item(key="private-info"){Text(tr("Siaran privat dibuka melalui kode atau tautan. Masuk dengan email terverifikasi untuk bergabung.","Private stations open with a code or link. Sign in with a verified email to join."),Modifier.padding(20.dp),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
    }
    }
}

@Composable private fun RadioStationRow(channel:RadioChannel,enabled:Boolean,join:()->Unit) {
    Surface(onClick=join,enabled=enabled,color=MaterialTheme.colorScheme.surfaceContainer,shape=RoundedCornerShape(20.dp),modifier=Modifier.fillMaxWidth().padding(horizontal=20.dp,vertical=6.dp)) {
        Row(Modifier.padding(14.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) {
            Art(channel.track?.artwork.orEmpty(),channel.track?.title ?: channel.title,Modifier.size(88.dp))
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Text(channel.title,style=MaterialTheme.typography.titleMedium,maxLines=2,overflow=TextOverflow.Ellipsis)
                Text(channel.host,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=1,overflow=TextOverflow.Ellipsis)
                Text("${channel.members} ${tr("anggota","members")} · ${radioMoodLabel(channel.mood)}",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.primary)
                channel.track?.let {Text(it.title,style=MaterialTheme.typography.bodySmall,maxLines=1,overflow=TextOverflow.Ellipsis)}
            }
            Icon(if(channel.playing)Icons.Rounded.GraphicEq else Icons.Rounded.PauseCircle,null,tint=MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable private fun RadioForm(vm:MusicViewModel,mode:String,code:String,nickname:String,onNickname:(String)->Unit,onCode:(String)->Unit,back:()->Unit,onLogin:()->Unit,submit:(String,String,String,String)->Unit) {
    val account=vm.auth.account.collectAsStateWithLifecycle().value
    val state=vm.together.state.collectAsStateWithLifecycle().value
    val lounge=vm.radio.lounge.collectAsStateWithLifecycle().value
    var title by rememberSaveable(mode,state.room?.id){mutableStateOf(if(mode=="edit")state.room?.title.orEmpty()else "Radio pribadiku")}
    var description by rememberSaveable(mode,state.room?.id){mutableStateOf(if(mode=="edit")lounge.description else "")}
    var visibility by rememberSaveable(mode,state.room?.id){mutableStateOf(if(mode=="edit")lounge.visibility else "public")}
    var mood by rememberSaveable(mode,state.room?.id){mutableStateOf(if(mode=="edit")lounge.mood else "all")}
    LazyColumn(Modifier.fillMaxSize().imePadding().testTag("radio-form"),contentPadding=PaddingValues(bottom=24.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item {PageHeading(if(mode=="join")tr("Gabung siaran","Join a station")else if(mode=="edit")tr("Atur siaran","Station settings")else tr("Mulai siaranmu","Start your station"),action={ActionIcon(Icons.Rounded.Close,tr("Tutup formulir siaran","Close station form"),back)})}
        if(account==null)item{EmptyState(tr("Masuk untuk bersiaran","Sign in for Radio"),tr("Email terverifikasi diperlukan untuk membuat dan mengikuti siaran.","A verified email is required to create and join stations."),Icons.Rounded.Person,tr("Buka akun","Open account"),onLogin)}
        else {
            if(mode!="edit")item {OutlinedTextField(nickname,{onNickname(it.filterNot(Char::isISOControl).take(32))},label={Text(tr("Nama tampilan di lounge","Lounge display name"))},singleLine=true,modifier=Modifier.fillMaxWidth().padding(horizontal=20.dp))}
            if(mode=="join")item {
                OutlinedTextField(code,{onCode(TogetherRules.code(it.substringAfter("iswara://radio/",it)))},label={Text(tr("Kode atau tautan siaran","Station code or link"))},singleLine=true,modifier=Modifier.fillMaxWidth().padding(horizontal=20.dp))
            } else {
                item {OutlinedTextField(title,{title=it.take(80)},label={Text(tr("Nama siaran","Station name"))},singleLine=true,modifier=Modifier.fillMaxWidth().padding(horizontal=20.dp))}
                item {OutlinedTextField(description,{description=it.take(240)},label={Text(tr("Tentang siaranmu","About your station"))},maxLines=3,modifier=Modifier.fillMaxWidth().padding(horizontal=20.dp))}
                item {SettingChoice(tr("Siapa yang bisa menemukan?","Who can discover it?"),visibility,listOf("public" to tr("Publik","Public"),"private" to tr("Privat lewat kode","Private with a code"))){visibility=it}}
                item {SettingChoice(tr("Suasana siaran","Station mood"),mood,RadioRules.moods.map {it to radioMoodLabel(it)}){mood=it}}
                item {Text(if(visibility=="public")tr("Nama siaran, nama tampilan host, dan lagu tampil di daftar publik. Komentar hanya untuk anggota.","Your station, host display name, and track appear in the public directory. Chat is for members.")else tr("Bagikan kode hanya kepada orang yang ingin kamu undang.","Share the code only with people you want to invite."),Modifier.padding(horizontal=20.dp),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
            }
            state.error?.let {item{Text(it,Modifier.padding(horizontal=20.dp),color=MaterialTheme.colorScheme.error)}}
            item {
                Button({submit(title,description,visibility,mood)},enabled=!state.busy && if(mode=="join")code.length==12 && nickname.isNotBlank()else title.isNotBlank() && (mode=="edit" || nickname.isNotBlank()),modifier=Modifier.fillMaxWidth().padding(horizontal=20.dp)) {
                    if(state.busy){CircularProgressIndicator(Modifier.size(18.dp),strokeWidth=2.dp);Spacer(Modifier.width(8.dp))}
                    Text(if(mode=="join")tr("Gabung dan dengarkan","Join and listen")else if(mode=="edit")tr("Simpan siaran","Save station")else "Go Live")
                }
            }
            item {Text(tr("32 akun per siaran, maksimal 6 jam. Antrean streaming diputar di perangkat masing-masing; jaringan dan sumber audio bisa memengaruhi keselarasan.","32 accounts per station, up to 6 hours. Each device plays the shared streaming queue; network and audio sources can affect alignment."),Modifier.padding(20.dp),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
        }
    }
}

@Composable private fun RadioLounge(vm:MusicViewModel,browse:()->Unit,edit:()->Unit,onTogether:()->Unit,onQueue:()->Unit) {
    val session=vm.together.state.collectAsStateWithLifecycle().value
    val lounge=vm.radio.lounge.collectAsStateWithLifecycle().value
    val account=vm.auth.account.collectAsStateWithLifecycle().value
    val player=vm.playback.state.collectAsStateWithLifecycle().value
    val room=session.room ?: return
    val host=room.host==account?.id
    val context=LocalContext.current
    val motion=LocalAppMotion.current
    var comment by rememberSaveable(room.id,account?.id){mutableStateOf("")}
    var leave by remember {mutableStateOf(false)}
    var menu by remember {mutableStateOf(false)}
    var moderate by remember {mutableStateOf<RadioMessage?>(null)}
    var block by remember {mutableStateOf<RadioMessage?>(null)}
    val chat=rememberLazyListState()
    var follow by remember(room.id){mutableStateOf(true)}
    LaunchedEffect(lounge.sent){if(lounge.sentText==comment)comment=""}
    LaunchedEffect(lounge.messages.lastOrNull()?.id){if(follow && lounge.messages.isNotEmpty()){if(motion)chat.animateScrollToItem(lounge.messages.size)else chat.scrollToItem(lounge.messages.size)}}
    Column(Modifier.fillMaxSize().imePadding().testTag("radio-lounge")) {
        Row(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=4.dp),verticalAlignment=Alignment.CenterVertically) {
            Icon(Icons.Rounded.Radio,null,tint=MaterialTheme.colorScheme.primary,modifier=Modifier.padding(8.dp))
            Column(Modifier.weight(1f)){Text(room.title,style=MaterialTheme.typography.titleMedium,maxLines=1,overflow=TextOverflow.Ellipsis);Text("${room.members} ${tr("anggota","members")} · ${if(session.realtime)tr("Terhubung","Connected")else tr("Menghubungkan ulang","Reconnecting")}",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
            Box {ActionIcon(Icons.Rounded.MoreVert,tr("Opsi siaran","Station options"),{menu=true});DropdownMenu(menu,{menu=false}) {
                DropdownMenuItem({Text(tr("Jelajah siaran","Browse stations"))},{menu=false;browse()})
                if(host)DropdownMenuItem({Text(tr("Atur siaran","Station settings"))},{menu=false;edit()})
                DropdownMenuItem({Text(if(host)tr("Akhiri siaran","End station")else tr("Keluar siaran","Leave station"))},{menu=false;leave=true})
            }}
        }
        Surface(color=MaterialTheme.colorScheme.surfaceContainer,shape=RoundedCornerShape(16.dp),modifier=Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=4.dp)) {
            Row(Modifier.padding(10.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                Art(player.track?.artwork.orEmpty(),player.track?.title.orEmpty(),Modifier.size(54.dp))
                Column(Modifier.weight(1f)){Text(player.track?.title ?: tr("Antrean masih kosong","The queue is empty"),style=MaterialTheme.typography.titleSmall,maxLines=2,overflow=TextOverflow.Ellipsis);Text(player.track?.artist.orEmpty(),style=MaterialTheme.typography.bodySmall,maxLines=1,overflow=TextOverflow.Ellipsis)}
                ActionIcon(if(player.playing)Icons.Rounded.Pause else Icons.Rounded.PlayArrow,if(host)tr("Putar atau jeda siaran","Play or pause station")else tr("Jeda perangkat ini","Pause this device"),{if(host)vm.playback.toggle()else vm.together.toggleLocal()},enabled=room.tracks.isNotEmpty() && (host || room.playing || session.localPaused))
                if(host)ActionIcon(Icons.Rounded.SkipNext,tr("Lagu berikutnya di siaran","Next station track"),vm.playback::next,enabled=room.index<room.tracks.lastIndex)
            }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal=12.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            AssistChip({context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,"${room.title}\niswara://radio/${room.code}"),null))},{Text(tr("Undang","Invite"))},leadingIcon={Icon(Icons.Rounded.Share,null,Modifier.size(18.dp))})
            AssistChip(onQueue,{Text(tr("Antrean","Queue"))},leadingIcon={Icon(Icons.Rounded.QueueMusic,null,Modifier.size(18.dp))})
            AssistChip(onTogether,{Text(tr("Tambah lagu","Add tracks"))},leadingIcon={Icon(Icons.Rounded.Add,null,Modifier.size(18.dp))})
            FilterChip(follow,{follow=!follow},{Text(tr("Ikuti obrolan","Follow chat"))})
        }
        if(session.waitingHost)Text(tr("Menunggu host kembali; musik dijeda.","Waiting for the host; playback is paused."),Modifier.padding(horizontal=20.dp),style=MaterialTheme.typography.bodySmall)
        RadioReactionBurst(lounge.messages.lastOrNull {it.kind=="reaction" && !it.removed})
        if(lounge.loading)LinearProgressIndicator(Modifier.fillMaxWidth())
        LazyColumn(Modifier.weight(1f),state=chat,contentPadding=PaddingValues(horizontal=16.dp,vertical=8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            item {if(lounge.messages.isNotEmpty() && lounge.olderAvailable)TextButton({follow=false;vm.radio.older()},enabled=!lounge.olderLoading){Text(tr("Pesan sebelumnya","Earlier messages"))}}
            if(!lounge.loading && lounge.messages.isEmpty())item{EmptyState(tr("Sapa teman satu frekuensi","Say hello to your listeners"),tr("Kirim komentar atau reaksi sambil menikmati musik.","Send a comment or reaction while enjoying the music."),Icons.Rounded.Forum)}
            items(lounge.messages,key={it.id}) {message->
                Surface(color=if(message.sender==account?.id)MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,shape=RoundedCornerShape(14.dp),modifier=Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(start=12.dp,top=10.dp,bottom=10.dp,end=2.dp),verticalAlignment=Alignment.Top) {
                        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                            Text(message.nickname,style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.primary)
                            Text(if(message.removed)tr("Pesan dihapus","Message removed")else if(message.kind=="reaction")RadioRules.reactions[message.body].orEmpty()else message.body,style=if(message.kind=="reaction" && !message.removed)MaterialTheme.typography.headlineMedium else MaterialTheme.typography.bodyMedium)
                        }
                        if(!message.removed && (lounge.moderator || message.sender==account?.id))ActionIcon(Icons.Rounded.MoreHoriz,tr("Opsi pesan","Message options"),{moderate=message})
                    }
                }
            }
        }
        lounge.error?.let {Text(it,Modifier.padding(horizontal=16.dp),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.error)}
        if(lounge.pending && !lounge.sending)Row {TextButton(vm.radio::retry){Text(tr("Kirim ulang","Retry sending"))};TextButton(vm.radio::discard){Text(tr("Batalkan pesan","Discard message"))}}
        Row(Modifier.fillMaxWidth().padding(horizontal=12.dp),horizontalArrangement=Arrangement.SpaceEvenly) {
            RadioRules.reactions.forEach {(id,emoji)->TextButton({vm.radio.post("reaction",id)},enabled=!lounge.pending && !lounge.sending){Text(emoji,style=MaterialTheme.typography.titleLarge)}}
        }
        Row(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(comment,{comment=it.take(280)},label={Text(tr("Tulis komentar","Write a comment"))},singleLine=true,modifier=Modifier.weight(1f),shape=RoundedCornerShape(20.dp))
            FilledIconButton({vm.radio.post("comment",comment)},enabled=comment.isNotBlank() && !lounge.pending && !lounge.sending,modifier=Modifier.size(48.dp)) {if(lounge.sending)CircularProgressIndicator(Modifier.size(20.dp),color=MaterialTheme.colorScheme.onPrimary,strokeWidth=2.dp)else Icon(Icons.Rounded.Send,tr("Kirim komentar","Send comment"))}
        }
    }
    if(leave)AlertDialog(onDismissRequest={leave=false},title={Text(if(host)tr("Akhiri siaran?","End station?")else tr("Keluar siaran?","Leave station?"))},text={Text(tr("Antrean pribadimu dipulihkan dalam keadaan jeda.","Your personal queue will be restored paused."))},confirmButton={TextButton({leave=false;vm.together.leave();browse()}){Text(if(host)tr("Akhiri siaran","End station")else tr("Keluar","Leave"))}},dismissButton={TextButton({leave=false}){Text(tr("Batal","Cancel"))}})
    moderate?.let {message->AlertDialog(onDismissRequest={moderate=null},title={Text(message.nickname)},text={Text(message.body.take(120))},confirmButton={TextButton({vm.radio.moderate("remove",message);moderate=null}){Text(tr("Hapus pesan","Remove message"))}},dismissButton={Row {if(lounge.moderator && message.sender!=room.host && message.sender!=account?.id)TextButton({block=message;moderate=null}){Text(tr("Batasi anggota","Restrict member"))};TextButton({moderate=null}){Text(tr("Tutup","Close"))}}})}
    block?.let {message->AlertDialog(onDismissRequest={block=null},title={Text(tr("Batasi anggota ini?","Restrict this member?"))},text={Text(tr("Anggota ini dikeluarkan dan tidak dapat bergabung lagi ke siaran ini.","This member will be removed and cannot rejoin this station."))},confirmButton={TextButton({vm.radio.moderate("ban",message);block=null}){Text(tr("Batasi","Restrict"))}},dismissButton={TextButton({block=null}){Text(tr("Batal","Cancel"))}})}
}

@Composable private fun RadioReactionBurst(message:RadioMessage?) {
    val motion=LocalAppMotion.current
    var show by remember {mutableStateOf(false)}
    LaunchedEffect(message?.id){if(message!=null && motion){show=true;delay(1800);show=false}}
    AnimatedVisibility(show && motion,enter=fadeIn(tween(180))+scaleIn(tween(220)),exit=fadeOut(tween(150))) {
        Text("${message?.nickname.orEmpty()}  ${RadioRules.reactions[message?.body].orEmpty()}",Modifier.fillMaxWidth().padding(horizontal=20.dp,vertical=6.dp),style=MaterialTheme.typography.titleMedium,color=MaterialTheme.colorScheme.primary)
    }
}
