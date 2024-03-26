package com.example.ar_module

import android.content.Context
import android.content.Intent

object ARSDK {
    fun testAR(context: Context) {
        val intent = Intent(context, ARActivity::class.java)
        context.startActivity(intent)
    }
}