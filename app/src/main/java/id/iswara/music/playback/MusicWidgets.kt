@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package id.iswara.music.playback

import android.app.PendingIntent
import android.appwidget.*
import android.content.*
import android.widget.RemoteViews
import android.graphics.*
import android.provider.Settings
import android.view.View
import id.iswara.music.ui.ArtworkTools
import androidx.core.content.ContextCompat
import androidx.media3.session.*
import id.iswara.music.*
import id.iswara.music.R
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

open class MusicWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) { MusicWidgets.render(context, MusicWidgets.lastTrack, MusicWidgets.lastPlaying) }
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action?.takeIf { it.startsWith("id.iswara.widget.") } ?: return
        val result = goAsync()
        val future = MediaController.Builder(context, SessionToken(context, ComponentName(context, PlaybackService::class.java))).buildAsync()
        future.addListener({
            val controller = runCatching { future.get() }.getOrNull()
            if (controller == null) { result.finish(); return@addListener }
            CoroutineScope(Dispatchers.Main).launch {
                try {
                    when (action.substringAfterLast('.')) {
                        "next" -> if (controller.hasNextMediaItem()) { controller.seekToNextMediaItem(); if (controller.playbackState == 1) controller.prepare() }
                        "previous" -> if (controller.currentPosition > 3000) controller.seekTo(0) else if (controller.hasPreviousMediaItem()) controller.seekToPreviousMediaItem()
                        "toggle" -> if (controller.playWhenReady && controller.playerError == null && controller.playbackState != androidx.media3.common.Player.STATE_ENDED) controller.pause() else {
                            if (controller.mediaItemCount == 0) {
                                val app = context.applicationContext as IswaraApp
                                val snapshot = app.preferences.queue(app.library.scope.value).first()
                                if (snapshot.tracks.isNotEmpty()) controller.setMediaItems(snapshot.tracks.map { it.mediaItem() }, snapshot.index.coerceIn(snapshot.tracks.indices), snapshot.position)
                            }
                            if (controller.playbackState == androidx.media3.common.Player.STATE_ENDED) controller.seekTo(0)
                            controller.prepare(); controller.play()
                        }
                    }
                    MusicWidgets.render(context, controller.currentMediaItem?.track(), controller.playWhenReady)
                } finally { controller.release(); result.finish() }
            }
        }, ContextCompat.getMainExecutor(context))
    }
}
class RetroMusicWidget : MusicWidget()
class VinylMusicWidget : MusicWidget()

object MusicWidgets {
    val types = listOf(MusicWidget::class.java, RetroMusicWidget::class.java, VinylMusicWidget::class.java)
    var lastTrack: id.iswara.music.model.Track? = null
        private set
    var lastPlaying = false
        private set
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private var coverJob:Job?=null
    private var coverKey=""
    private var cover:Bitmap?=null
    private var tools:ArtworkTools?=null
    private var motion=true
    fun motion(context:Context,enabled:Boolean) {motion=enabled;render(context,lastTrack,lastPlaying)}
    fun layout(style:Int,playing:Boolean=false)=when(style) {1->R.layout.music_widget_retro;2->if(playing)R.layout.music_widget_vinyl_play else R.layout.music_widget_vinyl;else->R.layout.music_widget}
    fun render(context: Context, track: id.iswara.music.model.Track?, playing: Boolean) {
        lastTrack=track;lastPlaying=playing
        val manager=AppWidgetManager.getInstance(context)
        if(types.none {manager.getAppWidgetIds(ComponentName(context,it)).isNotEmpty()})return
        val key=track?.artwork.orEmpty()
        if(coverKey!=key) {
            coverJob?.cancel();coverKey=key;cover=null
            if(key.isNotBlank())coverJob=scope.launch {
                val image=(tools ?: ArtworkTools(context.applicationContext).also {tools=it}).bitmap(key)
                if(coverKey==key) {cover=image;render(context,lastTrack,lastPlaying)}
            }
        }
        types.forEachIndexed {style,type ->
            val ids=manager.getAppWidgetIds(ComponentName(context,type))
            if(ids.isNotEmpty())runCatching {manager.updateAppWidget(ids,views(context,style,track,playing))}
        }
    }
    fun views(context:Context,style:Int,track:id.iswara.music.model.Track?=lastTrack,playing:Boolean=lastPlaying):RemoteViews {
        val animate=playing && motion && Settings.Global.getFloat(context.contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f)>0f
        val view=RemoteViews(context.packageName,layout(style,animate))
        view.setTextViewText(R.id.widget_title,track?.title ?: "iSwara Music")
        view.setTextViewText(R.id.widget_artist,track?.artist ?: "Putar musikmu")
        view.setTextViewText(R.id.widget_brand,when(style){1->"iSwara / CLASSIC";2->"iSwara / VINYL";else->"iSwara Music"})
        val image=cover.takeIf {track?.artwork==coverKey}
        if(style==2) {
            val disc=disc(image)
            view.setImageViewBitmap(R.id.widget_art,disc)
            if(animate)listOf(R.id.widget_disc_1,R.id.widget_disc_2,R.id.widget_disc_3).forEachIndexed {index,id ->
                val matrix=Matrix().apply {postRotate((index+1)*90f)}
                view.setImageViewBitmap(id,Bitmap.createBitmap(disc,0,0,disc.width,disc.height,matrix,true))
            }
        } else if(image!=null) view.setImageViewBitmap(R.id.widget_art,Bitmap.createScaledBitmap(image,128,128,true))
        else view.setImageViewResource(R.id.widget_art,R.drawable.ic_logo)
        view.setImageViewResource(R.id.widget_toggle,if(playing)android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play)
        view.setContentDescription(R.id.widget_toggle,if(playing)"Jeda musik" else "Putar musik")
        val open=PendingIntent.getActivity(context,100,Intent(context,MainActivity::class.java),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        view.setOnClickPendingIntent(R.id.widget_title,open)
        view.setOnClickPendingIntent(R.id.widget_art,open)
        listOf("previous" to R.id.widget_previous,"toggle" to R.id.widget_toggle,"next" to R.id.widget_next).forEachIndexed {index,pair ->
            val intent=Intent(context,types[style]).setAction("id.iswara.widget.${pair.first}")
            view.setOnClickPendingIntent(pair.second,PendingIntent.getBroadcast(context,style*10+index,intent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        }
        return view
    }
    private fun disc(cover:Bitmap?):Bitmap {
        val bitmap=Bitmap.createBitmap(192,192,Bitmap.Config.ARGB_8888)
        val canvas=Canvas(bitmap);val paint=Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color=Color.rgb(12,12,15);canvas.drawCircle(96f,96f,94f,paint)
        paint.style=Paint.Style.STROKE;paint.strokeWidth=1f;paint.color=Color.rgb(49,49,54)
        for(radius in 46..88 step 7)canvas.drawCircle(96f,96f,radius.toFloat(),paint)
        paint.style=Paint.Style.FILL;paint.color=Color.rgb(255,137,84);canvas.drawCircle(96f,96f,40f,paint)
        if(cover!=null) {canvas.save();canvas.clipPath(Path().apply {addCircle(96f,96f,39f,Path.Direction.CW)});val size=minOf(cover.width,cover.height);canvas.drawBitmap(cover,Rect((cover.width-size)/2,(cover.height-size)/2,(cover.width+size)/2,(cover.height+size)/2),Rect(57,57,135,135),paint);canvas.restore()}
        paint.color=Color.rgb(255,190,140);canvas.drawCircle(160f,96f,4f,paint)
        paint.color=Color.rgb(12,12,15);canvas.drawCircle(96f,96f,6f,paint)
        return bitmap
    }
}
