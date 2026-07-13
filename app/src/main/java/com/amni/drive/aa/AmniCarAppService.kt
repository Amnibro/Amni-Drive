package com.amni.drive.aa
import androidx.car.app.CarAppService
import androidx.car.app.Session
import androidx.car.app.SessionInfo
import androidx.car.app.validation.HostValidator
class AmniCarAppService : CarAppService() {
override fun createHostValidator(): HostValidator = HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
override fun onCreateSession(): Session = AmniCarSession()
override fun onCreateSession(sessionInfo: SessionInfo): Session = AmniCarSession()
}
