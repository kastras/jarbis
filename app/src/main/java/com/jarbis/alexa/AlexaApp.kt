package com.jarbis.alexa

import android.app.Application
import android.content.Context
import com.jarbis.alexa.voice.AssistantEngine

class AlexaApp : Application() {

    private val engineLazy = lazy { AssistantEngine(this) }

    val engine: AssistantEngine by engineLazy

    override fun onTerminate() {
        if (engineLazy.isInitialized()) engine.destroy()
        super.onTerminate()
    }

    companion object {
        fun engine(context: Context): AssistantEngine =
            (context.applicationContext as AlexaApp).engine
    }
}