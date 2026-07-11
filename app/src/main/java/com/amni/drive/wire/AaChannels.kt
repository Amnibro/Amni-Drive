package com.amni.drive.wire
object AaChannels {
const val CONTROL = 0
const val INPUT_SOURCE = 1
const val SENSOR_SOURCE = 2
const val VIDEO_SINK = 3
const val MEDIA_SINK = 4
const val SPEECH_SINK = 5
const val BLUETOOTH = 6
const val MICROPHONE = 7
const val NAVIGATION = 8
const val MEDIA_STATUS = 9
const val NOTIFICATION = 10
const val VENDOR = 255
fun name(id: Int): String = when (id) {
CONTROL -> "CONTROL"
INPUT_SOURCE -> "INPUT"
SENSOR_SOURCE -> "SENSOR"
VIDEO_SINK -> "VIDEO"
MEDIA_SINK -> "MEDIA_AUDIO"
SPEECH_SINK -> "SPEECH_AUDIO"
BLUETOOTH -> "BLUETOOTH"
MICROPHONE -> "MIC"
NAVIGATION -> "NAV"
MEDIA_STATUS -> "MEDIA_STATUS"
NOTIFICATION -> "NOTIFICATION"
else -> "CH_$id"
}
}
object AaFrameFlags {
const val NONE = 0
const val FIRST = 1
const val LAST = 2
const val CONTROL = 4
const val ENCRYPTED = 8
}
object AaTouchAction {
const val DOWN = 0
const val UP = 1
const val MOVE = 2
const val CANCEL = 3
const val POINTER_DOWN = 5
const val POINTER_UP = 6
}
