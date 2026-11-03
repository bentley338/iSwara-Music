package id.iswara.music.playback

import ai.onnxruntime.*
import java.io.Closeable
import java.io.File
import java.nio.FloatBuffer

class StemModel(vocals:File,accompaniment:File):Closeable {
    private val environment=OrtEnvironment.getEnvironment()
    private val vocalSession:OrtSession
    private val instrumentSession:OrtSession
    init {
        OrtSession.SessionOptions().use {options ->
            options.setIntraOpNumThreads(1);options.setInterOpNumThreads(1);options.setCPUArenaAllocator(false)
            vocalSession=environment.createSession(vocals.absolutePath,options)
            try {instrumentSession=environment.createSession(accompaniment.absolutePath,options)}catch(e:Throwable){vocalSession.close();throw e}
        }
    }
    fun separate(audio:Array<FloatArray>,checkpoint:()->Unit={}):Array<FloatArray> {
        checkpoint();val magnitude=StemDsp.magnitudes(audio,checkpoint)
        OnnxTensor.createTensor(environment,FloatBuffer.wrap(magnitude),longArrayOf(2,1,StemDsp.FRAMES.toLong(),StemDsp.BINS.toLong())).use {input ->
            fun run(session:OrtSession):FloatArray {checkpoint();return session.run(mapOf("x" to input)).use {output ->
                val tensor=output[0] as OnnxTensor
                require(tensor.info.shape.contentEquals(longArrayOf(2,1,128,1024))) {"Bentuk model stem tidak sesuai."}
                FloatArray(magnitude.size).also {tensor.floatBuffer.get(it)}
            }}
            val vocal=run(vocalSession);val instrumental=run(instrumentSession)
            checkpoint();return StemDsp.reconstructVocals(audio,vocal,instrumental,checkpoint)
        }
    }
    override fun close(){try {vocalSession.close()}finally {instrumentSession.close()}}
}
