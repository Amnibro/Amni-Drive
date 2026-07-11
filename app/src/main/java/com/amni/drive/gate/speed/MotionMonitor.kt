package com.amni.drive.gate.speed
import com.amni.drive.gate.DriveState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
class MotionMonitor(private val sources: List<MotionSource>, cfg: MotionConfig = MotionConfig()) {
    private val classifier = MotionClassifier(cfg)
    fun state(): Flow<DriveState> = sources.map { it.samples() }.merge().map { classifier.update(it) }.distinctUntilChanged()
}
