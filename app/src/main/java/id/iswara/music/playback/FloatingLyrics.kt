package id.iswara.music.playback

import android.app.KeyguardManager
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.PowerManager
import android.provider.Settings
import android.view.*
import android.widget.*
import androidx.media3.common.Player
import dagger.hilt.android.qualifiers.ApplicationContext
import id.iswara.music.IswaraApp
import id.iswara.music.data.LibraryRepository
import id.iswara.music.data.Preferences
import id.iswara.music.source.Lyrics
import id.iswara.music.source.LyricsRepository
import id.iswara.music.ui.ListeningTools
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton class FloatingLyrics @Inject constructor(@ApplicationContext private val context:Context,private val preferences:Preferences,private val lyrics:LyricsRepository,private val library:LibraryRepository) {
    private var scope:CoroutineScope?=null
    private var view:View?=null
    private val windows=context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    fun start(player:Player) {
        stop()
        val jobs=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate);scope=jobs
        jobs.launch {
            combine(preferences.raw("floatingLyrics"),preferences.raw("floatingSize"),preferences.raw("floatingOpacity"),library.scope) {enabled,size,opacity,owner -> listOf(enabled,size,opacity,owner)}.collectLatest {options ->
                remove()
                if(options[0]!="true" || !Settings.canDrawOverlays(context))return@collectLatest
                val owner=options[3].orEmpty()
                val density=context.resources.displayMetrics.density
                val width=context.resources.displayMetrics.widthPixels
                val height=context.resources.displayMetrics.heightPixels
                val params=WindowManager.LayoutParams(minOf((340*density).toInt(),width-(24*density).toInt()).coerceAtLeast(100),WindowManager.LayoutParams.WRAP_CONTENT,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,PixelFormat.TRANSLUCENT).apply {
                    gravity=Gravity.TOP or Gravity.START
                    x=(preferences.raw("floatingX").first()?.toIntOrNull() ?: (12*density).toInt()).coerceIn(0,(width-this.width).coerceAtLeast(0))
                    y=(preferences.raw("floatingY").first()?.toIntOrNull() ?: (140*density).toInt()).coerceIn(0,(height-160*density).toInt().coerceAtLeast(0))
                }
                val root=LinearLayout(context).apply {
                    orientation=LinearLayout.VERTICAL
                    setPadding((12*density).toInt(),0,(8*density).toInt(),(14*density).toInt())
                    background=GradientDrawable().apply {setColor(Color.argb(options[2]?.toIntOrNull()?.coerceIn(160,250) ?: 235,21,22,25));cornerRadius=20*density}
                }
                val header=LinearLayout(context).apply {gravity=Gravity.CENTER_VERTICAL}
                val title=TextView(context).apply {textSize=12f;setTextColor(Color.rgb(255,171,133));maxLines=1;ellipsize=android.text.TextUtils.TruncateAt.END;contentDescription="Geser untuk memindahkan lirik"}
                header.addView(title,LinearLayout.LayoutParams(0,(48*density).toInt(),1f))
                val close=Button(context).apply {text="×";textSize=22f;setTextColor(Color.WHITE);setBackgroundColor(Color.TRANSPARENT);contentDescription="Tutup lirik melayang";setOnClickListener {jobs.launch {preferences.set("floatingLyrics","false")}}}
                header.addView(close,LinearLayout.LayoutParams((48*density).toInt(),(48*density).toInt()))
                root.addView(header)
                val label=TextView(context).apply {textSize=options[1]?.toFloatOrNull()?.coerceIn(16f,26f) ?: 20f;setTextColor(Color.WHITE);gravity=Gravity.CENTER;maxLines=4;ellipsize=android.text.TextUtils.TruncateAt.END;setPadding(0,0,(4*density).toInt(),0);importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_YES}
                root.addView(label)
                var startX=0f;var startY=0f;var initialX=0;var initialY=0;var moved=false
                val drag=View.OnTouchListener {target,event ->
                    when(event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> {startX=event.rawX;startY=event.rawY;initialX=params.x;initialY=params.y;moved=false;true}
                        MotionEvent.ACTION_MOVE -> {
                            moved=moved || kotlin.math.abs(event.rawX-startX)>8*density || kotlin.math.abs(event.rawY-startY)>8*density
                            params.x=(initialX+event.rawX-startX).toInt().coerceIn(0,(width-params.width).coerceAtLeast(0))
                            params.y=(initialY+event.rawY-startY).toInt().coerceIn(0,(height-root.height).coerceAtLeast(0))
                            runCatching {windows.updateViewLayout(root,params)};true
                        }
                        MotionEvent.ACTION_UP -> {if(moved)jobs.launch {preferences.set("floatingX",params.x.toString());preferences.set("floatingY",params.y.toString())} else target.performClick();true}
                        else -> true
                    }
                }
                title.setOnTouchListener(drag);label.setOnTouchListener(drag)
                view=root
                if(runCatching {windows.addView(root,params)}.isFailure){remove();return@collectLatest}
                var id:String?=null;var song:Lyrics?=null;var load:Job?=null;var offset=0L
                try {
                    coroutineScope {
                        while(isActive && Settings.canDrawOverlays(context)) {
                            val metrics=context.resources.displayMetrics
                            val boundedWidth=minOf((340*metrics.density).toInt(),metrics.widthPixels-(24*metrics.density).toInt()).coerceAtLeast(100)
                            val boundedX=params.x.coerceIn(0,(metrics.widthPixels-boundedWidth).coerceAtLeast(0))
                            val boundedY=params.y.coerceIn(0,(metrics.heightPixels-root.height.coerceAtLeast((120*metrics.density).toInt())).coerceAtLeast(0))
                            if(params.width!=boundedWidth || params.x!=boundedX || params.y!=boundedY) {
                                params.width=boundedWidth;params.x=boundedX;params.y=boundedY;runCatching {windows.updateViewLayout(root,params)}
                            }
                            val track=player.currentMediaItem?.track()
                            val awake=context.getSystemService(PowerManager::class.java).isInteractive
                            val locked=context.getSystemService(KeyguardManager::class.java).isKeyguardLocked
                            root.visibility=if(player.isPlaying && awake && !locked && (context.applicationContext as? IswaraApp)?.visible!=true)View.VISIBLE else View.GONE
                            if(id!=track?.id) {
                                id=track?.id;song=null;load?.cancel();offset=0
                                if(track!=null)load=launch {
                                    launch {preferences.raw("lyricsOffset:$owner:${track.id}").collect {offset=it?.toLongOrNull() ?: 0}}
                                    while(isActive) {song=runCatching {lyrics.get(track)}.getOrNull();delay(15_000)}
                                }
                            }
                            title.text=track?.title ?: "iSwara Music"
                            val lines=song?.lines.orEmpty();val index=ListeningTools.activeLine(lines,player.currentPosition,offset)
                            label.text=lines.getOrNull(index)?.text?.takeIf {it.isNotBlank()} ?: when {song?.instrumental==true->"Instrumental";song!=null && lines.isEmpty()->"Lirik tersinkron belum tersedia";else->track?.artist.orEmpty()}
                            delay(300)
                        }
                        load?.cancel()
                    }
                } finally {load?.cancel();remove()}
            }
        }
    }
    private fun remove() {view?.let {runCatching {windows.removeView(it)}};view=null}
    fun stop(){scope?.cancel();scope=null;remove()}
}
