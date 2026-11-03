package id.iswara.music

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.work.Configuration
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import id.iswara.music.cloud.SyncCoordinator
import id.iswara.music.cloud.SyncWorker
import id.iswara.music.cloud.AuthRepository
import id.iswara.music.data.*
import id.iswara.music.source.*
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

@HiltAndroidApp
class IswaraApp : Application(), Configuration.Provider {
    @Volatile var visible=false
        private set
    override val workManagerConfiguration: Configuration get() = Configuration.Builder().build()
    @Inject lateinit var sync: SyncCoordinator
    @Inject lateinit var auth: AuthRepository
    @Inject lateinit var library: LibraryRepository
    @Inject lateinit var dao: DocumentDao
    @Inject lateinit var preferences: Preferences
    @Inject lateinit var offline: OfflineStore
    @Inject lateinit var stems: StemStore
    @Inject lateinit var lyricMedia: LyricMediaStore
    @Inject lateinit var updates: AppUpdates
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(object:ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity:android.app.Activity) {visible=true}
            override fun onActivityPaused(activity:android.app.Activity) {visible=false}
            override fun onActivityCreated(activity:android.app.Activity,state:android.os.Bundle?) {}
            override fun onActivityStarted(activity:android.app.Activity) {}
            override fun onActivityStopped(activity:android.app.Activity) {}
            override fun onActivitySaveInstanceState(activity:android.app.Activity,state:android.os.Bundle) {}
            override fun onActivityDestroyed(activity:android.app.Activity) {}
        })
        library.scope.value = auth.account.value?.id ?: "guest"
        SyncWorker.schedule(this)
        scope.launch {preferences.settings.map {it.updateAuto}.distinctUntilChanged().collect {updates.schedule(it)}}
        scope.launch { auth.account.collect { account -> library.scope.value = account?.id ?: "guest"; sync.status.value = ""; if (account != null) SyncWorker.now(this@IswaraApp) } }
        scope.launch { library.scope.flatMapLatest { dao.observe(it) }.debounce(1500).collect { docs -> if (auth.account.value != null && docs.any { it.dirty }) SyncWorker.now(this@IswaraApp) } }
    }
}

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides @Singleton fun database(@ApplicationContext context: Context): IswaraDatabase = Room.databaseBuilder(context, IswaraDatabase::class.java, "iswara.db").addMigrations(MIGRATION_1_2, MIGRATION_2_3).build()
    @Provides fun dao(database: IswaraDatabase): DocumentDao = database.documents()
    @Provides @Singleton fun source(source: ResilientMusicSource): MusicSource = source
}
