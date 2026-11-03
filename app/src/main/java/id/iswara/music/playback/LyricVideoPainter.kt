package id.iswara.music.playback

import android.content.Context
import android.graphics.*
import android.text.*
import androidx.core.content.res.ResourcesCompat
import id.iswara.music.R
import id.iswara.music.model.*

class LyricVideoPainter(context:Context,private val track:Track,private val lines:List<String>,private val timed:List<LyricLine>,private val cover:Bitmap?,private val motion:Boolean) {
    val bitmap=Bitmap.createBitmap(720,1280,Bitmap.Config.ARGB_8888)
    private val face=ResourcesCompat.getFont(context,R.font.plus_jakarta_sans)
    private val canvas=Canvas(bitmap)
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    private fun text(value:String,y:Float,size:Float,color:Int,maxLines:Int=3) {
        val p=TextPaint(Paint.ANTI_ALIAS_FLAG).apply {this.color=color;textSize=size;typeface=Typeface.create(face,Typeface.BOLD)}
        val layout=StaticLayout.Builder.obtain(value,0,value.length,p,624).setMaxLines(maxLines).setEllipsize(TextUtils.TruncateAt.END).setIncludePad(false).setLineSpacing(6f,1.05f).build()
        canvas.save();canvas.translate(48f,y);layout.draw(canvas);canvas.restore()
    }
    fun draw(position:Long,elapsed:Long,duration:Long):Bitmap {
        canvas.drawColor(Color.rgb(21,22,25))
        cover?.let {image ->val scale=maxOf(720f/image.width,1280f/image.height);val w=image.width*scale;val h=image.height*scale;canvas.drawBitmap(image,null,RectF((720-w)/2,(1280-h)/2,(720+w)/2,(1280+h)/2),paint);paint.color=Color.argb(220,12,14,18);canvas.drawRect(0f,0f,720f,1280f,paint)}
        val ink=Color.rgb(250,247,242);val accent=Color.rgb(255,159,116)
        text("iSwara Music",64f,24f,accent,1)
        text(track.title,134f,34f,ink,2);text(track.artist,238f,22f,Color.LTGRAY,1)
        val active=timed.indexOfLast {it.time<=position}
        if(timed.isEmpty()) {
            val value=lines.joinToString("\n\n");var size=42f
            fun layout():StaticLayout {val p=TextPaint(Paint.ANTI_ALIAS_FLAG).apply {color=ink;textSize=size;typeface=Typeface.create(face,Typeface.BOLD)};return StaticLayout.Builder.obtain(value,0,value.length,p,624).setIncludePad(false).setLineSpacing(6f,1.05f).build()}
            var body=layout();while(body.height>690 && size>22f){size-=2;body=layout()}
            canvas.save();canvas.translate(48f,380f);body.draw(canvas);canvas.restore()
        }
        else if(active>=0) {
            if(active>0)text(timed[active-1].text,350f,27f,Color.rgb(160,157,155),2)
            val transition=if(motion)((position-timed[active].time)/250f).coerceIn(0f,1f)else 1f
            text(timed[active].text,480f+(1-transition)*12,46f,ink,5)
            paint.color=accent;canvas.drawRoundRect(48f,452f,48f+64*transition,456f,2f,2f,paint)
            timed.getOrNull(active+1)?.let {text(it.text,825f,27f,Color.rgb(160,157,155),3)}
        }
        paint.color=Color.rgb(100,98,96);canvas.drawRect(48f,1160f,672f,1163f,paint)
        paint.color=accent;canvas.drawRect(48f,1160f,48f+624*(elapsed.toFloat()/duration.coerceAtLeast(1)).coerceIn(0f,1f),1163f,paint)
        text("${elapsed/60000}:${((elapsed/1000)%60).toString().padStart(2,'0')}",1190f,21f,Color.LTGRAY,1)
        return bitmap
    }
    fun close(){bitmap.recycle()}
}
