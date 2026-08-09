package com.jarbis.alexa

import android.app.Application
import android.content.Context
import com.jarbis.alexa.music.OkHttpNewPipeDownloader
import com.jarbis.alexa.voice.AssistantEngine
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.localization.Localization
import java.util.Locale

class AlexaApp : Application() {

    private val engineLazy = lazy { AssistantEngine(this) }

    val engine: AssistantEngine by engineLazy

    override fun onCreate() {
        super.onCreate()
        // Inicializa NewPipeExtractor de una vez al arrancar la app.
        NewPipe.init(
            OkHttpNewPipeDownloader(),
            Localization.fromLocale(Locale.getDefault()),
        )
    }

    override fun onTerminate() {
        if (engineLazy.isInitialized()) engine.destroy()
        super.onTerminate()
    }

    companion object {
        fun engine(context: Context): AssistantEngine =
            (context.applicationContext as AlexaApp).engine
    }
}