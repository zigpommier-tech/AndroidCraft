package com.androidcraft.studio

import android.app.Application
import android.content.Context
import com.androidcraft.studio.data.ModelRepository
import com.androidcraft.studio.engine.AudioEngine
import com.androidcraft.studio.engine.ImageEngine
import com.androidcraft.studio.engine.TextEngine
import com.androidcraft.studio.engine.VideoEngine

/** Application-scoped singletons so heavy engines and downloads outlive individual screens. */
class AppContainer(context: Context) {
    val modelRepository = ModelRepository.get(context)
    val textEngine = TextEngine(context.applicationContext)
    val audioEngine = AudioEngine(context.applicationContext)
    val imageEngine = ImageEngine()
    val videoEngine = VideoEngine()
}

class AndroidCraftApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/** Convenience accessor from any Context. */
val Context.appContainer: AppContainer
    get() = (applicationContext as AndroidCraftApp).container
