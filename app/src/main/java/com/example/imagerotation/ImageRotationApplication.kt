package com.example.imagerotation

import android.app.Application
import com.miguelbcr.ui.rx_paparazzo2.RxPaparazzo

class ImageRotationApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        RxPaparazzo.register(this)
    }
}