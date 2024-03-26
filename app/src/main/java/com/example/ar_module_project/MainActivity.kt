package com.example.ar_module_project

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.example.ar_module.ARSDK

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        ARSDK.testAR(this)
    }
}