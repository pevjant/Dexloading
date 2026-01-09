package com.example.dexloadingtest

import android.app.Application

class App : Application() {
    // GloballyDynamic handles resource loading automatically
    // No SplitCompat.install() needed
}
