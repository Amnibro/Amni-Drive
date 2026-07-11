package com.amni.drive.aa
import android.content.Intent
import androidx.car.app.Screen
import androidx.car.app.Session
class AmniCarSession : Session() {
override fun onCreateScreen(intent: Intent): Screen = AaHomeScreen(carContext)
}
