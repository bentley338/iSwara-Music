package id.iswara.music.ui

import android.content.Context
import android.content.Intent
import android.graphics.*
import android.util.LruCache
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import id.iswara.music.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton class ArtworkTools @Inject constructor(@ApplicationContext private val context: Context) {
    private val cache = LruCache<String, Bitmap>(8)
    private val client = OkHttpClient.Builder().callTimeout(8, java.util.concurrent.TimeUnit.SECONDS).build()
    suspend fun bitmap(url: String): Bitmap? = withContext(Dispatchers.IO) {
        if (url.isBlank()) return@withContext null
        cache.get(url)?.let { return@withContext it }
        runCatching {
            client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                check(response.isSuccessful)
                val bytes = java.io.ByteArrayOutputStream()
                response.body!!.byteStream().use { input -> val buffer = ByteArray(8192); while (true) { val count = input.read(buffer); if (count < 0) break; check(bytes.size() + count <= 2_000_000); bytes.write(buffer, 0, count) } }
                val data = bytes.toByteArray()
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }; BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
                val options = BitmapFactory.Options().apply { inSampleSize = (maxOf(bounds.outWidth, bounds.outHeight) / 512).coerceAtLeast(1) }
                BitmapFactory.decodeByteArray(data, 0, data.size, options)?.also { cache.put(url, it) }
            }
        }.getOrNull()
    }
    private val palettes = LruCache<String, ArtworkPalette>(32)
    suspend fun palette(url: String): ArtworkPalette? {
        palettes.get(url)?.let { return it }
        val image = bitmap(url) ?: return null
        return withContext(Dispatchers.Default) {
            val samples = ArrayList<Int>(2304)
            val insetX = image.width / 16; val insetY = image.height / 16
            for (y in insetY until image.height - insetY step maxOf(1, image.height / 48))
                for (x in insetX until image.width - insetX step maxOf(1, image.width / 48)) samples += image.getPixel(x, y)
            ArtworkColors.extract(samples.toIntArray())?.also { palettes.put(url, it) }
        }
    }
    suspend fun color(url: String): Int? = palette(url)?.dominant
    fun lyricPages(lines:List<String>,story:Boolean):List<List<String>> {
        require(lines.isNotEmpty())
        val paint=android.text.TextPaint(Paint.ANTI_ALIAS_FLAG).apply {textSize=if(story)64f else 54f;typeface=Typeface.create(androidx.core.content.res.ResourcesCompat.getFont(context,id.iswara.music.R.font.plus_jakarta_sans),Typeface.BOLD)}
        val maxHeight=if(story)1150 else 675
        fun layout(text:String)=android.text.StaticLayout.Builder.obtain(text,0,text.length,paint,936).setIncludePad(false).setLineSpacing(8f,1.05f).build()
        val pages=mutableListOf<List<String>>();var page=mutableListOf<String>()
        for(line in lines.filter {it.isNotBlank()}) {
            var remaining=line
            while(remaining.isNotEmpty()) {
                val candidate=page+remaining
                if(layout(candidate.joinToString("\n\n")).height<=maxHeight){page.add(remaining);remaining=""}
                else if(page.isNotEmpty()){pages+=page;page=mutableListOf()}
                else {val text=layout(remaining);var visual=0;while(visual<text.lineCount && text.getLineBottom(visual)<=maxHeight)visual++
                    val cut=text.getLineEnd((visual-1).coerceAtLeast(0)).coerceIn(1,remaining.length)
                    pages+=listOf(remaining.take(cut));remaining=remaining.drop(cut)
                }
            }
        }
        if(page.isNotEmpty())pages+=page
        require(pages.isNotEmpty());return pages
    }
    fun shareCards(files:List<File>):Intent {
        require(files.isNotEmpty())
        if(files.size==1)return shareCard(files[0])
        val uris=ArrayList(files.map {FileProvider.getUriForFile(context,context.packageName+".files",it)})
        return Intent(Intent.ACTION_SEND_MULTIPLE).setType("image/png").putParcelableArrayListExtra(Intent.EXTRA_STREAM,uris).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION).apply {clipData=android.content.ClipData.newRawUri("Kartu lirik",uris[0]).also {data->uris.drop(1).forEach {data.addItem(android.content.ClipData.Item(it))}}}
    }
    suspend fun lyricCardFile(track: Track, lines: List<String>, style: String = "charcoal", story: Boolean = false): File = withContext(Dispatchers.IO) {
        require(lines.isNotEmpty() && lines.all { it.isNotBlank() && it.length <= 12000 }) { "Pilih sedikitnya satu baris lirik." }
        val height = if (story) 1920 else 1350
        val output = Bitmap.createBitmap(1080, height, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(output)
            val paper = style == "paper"
            val ink = if (paper) Color.rgb(34,29,26) else Color.rgb(250,247,242)
            val accent = if (paper) Color.rgb(164,63,23) else Color.rgb(255,159,116)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            val image = bitmap(track.artwork)
            canvas.drawColor(if (paper) Color.rgb(244,235,219) else Color.rgb(21,22,25))
            if (style == "cover" && image != null) {
                val scale = maxOf(1080f/image.width,height.toFloat()/image.height)
                val w=image.width*scale; val h=image.height*scale
                canvas.drawBitmap(image,null,RectF((1080-w)/2,(height-h)/2,(1080+w)/2,(height+h)/2),paint)
                paint.color=Color.argb(210,12,14,18);canvas.drawRect(0f,0f,1080f,height.toFloat(),paint)
            }
            val face=androidx.core.content.res.ResourcesCompat.getFont(context,id.iswara.music.R.font.plus_jakarta_sans)
            fun layout(value:String,width:Int,size:Float,bold:Boolean):android.text.StaticLayout {
                val textPaint=android.text.TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    color=ink;textSize=size;typeface=Typeface.create(face,if(bold)Typeface.BOLD else Typeface.NORMAL)
                }
                return android.text.StaticLayout.Builder.obtain(value,0,value.length,textPaint,width).setIncludePad(false).setLineSpacing(8f,1.05f).build()
            }
            fun draw(value:String,x:Float,y:Float,width:Int,size:Float,bold:Boolean,maxHeight:Float) {
                var font=size;var text=layout(value,width,font,bold)
                while(text.height>maxHeight && font>26f) {font-=2;text=layout(value,width,font,bold)}
                require(text.height<=maxHeight) {"Baris terlalu panjang untuk kartu. Pilih bagian lirik yang lebih singkat."}
                canvas.save();canvas.translate(x,y);text.draw(canvas);canvas.restore()
            }
            paint.color=accent;paint.typeface=Typeface.create(face,Typeface.BOLD);paint.textSize=28f
            canvas.drawText("iSwara Music",72f,100f,paint)
            val top=if(story)250f else 155f
            val artRect=RectF(72f,top,300f,top+228f)
            if(image!=null) {
                val size=minOf(image.width,image.height);val left=(image.width-size)/2;val y=(image.height-size)/2
                canvas.drawBitmap(image,Rect(left,y,left+size,y+size),artRect,Paint(Paint.ANTI_ALIAS_FLAG))
            } else {paint.color=accent;canvas.drawRoundRect(artRect,20f,20f,paint);paint.color=if(paper)Color.WHITE else Color.BLACK;paint.textSize=100f;canvas.drawText("S",153f,top+150f,paint)}
            draw(track.title,340f,top+8f,668,42f,true,135f)
            draw(track.artist,340f,top+155f,668,28f,false,72f)
            val lyricTop=top+310f
            draw(lines.joinToString("\n\n"),72f,lyricTop,936,if(story)64f else 54f,true,height-lyricTop-210f)
            paint.color=accent;paint.strokeWidth=3f;canvas.drawLine(72f,height-150f,144f,height-150f,paint)
            paint.textSize=24f;canvas.drawText("POTONGAN LIRIK",72f,height-100f,paint)
            val folder=File(context.cacheDir,"lyric-cards").apply {mkdirs()}
            folder.listFiles()?.filter {System.currentTimeMillis()-it.lastModified()>24*60*60*1000L}?.forEach {it.delete()}
            val file=File(folder,"iswara-lyrics-${java.util.UUID.randomUUID()}.png")
            file.outputStream().use {check(output.compress(Bitmap.CompressFormat.PNG,100,it))}
            file
        } finally {output.recycle()}
    }
    fun shareCard(file: File): Intent {
        val uri=FileProvider.getUriForFile(context,context.packageName+".files",file)
        return Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM,uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION).apply {clipData=android.content.ClipData.newRawUri("Kartu lirik",uri)}
    }
    suspend fun lyricCard(track: Track, lines: List<String>): Intent = shareCard(lyricCardFile(track,lines))
}
