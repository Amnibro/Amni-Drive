package com.amni.drive.launcher
import android.content.pm.ApplicationInfo
import com.amni.drive.gate.AppCategory
import org.junit.Assert.assertEquals
import org.junit.Test
class AppClassifierTest {
    @Test fun netflixIsVideo() = assertEquals(AppCategory.VIDEO, AppClassifier.classify("com.netflix.mediaclient", -1))
    @Test fun youtubeIsVideo() = assertEquals(AppCategory.VIDEO, AppClassifier.classify("com.google.android.youtube", -1))
    @Test fun mapsIsNav() = assertEquals(AppCategory.NAV, AppClassifier.classify("com.google.android.apps.maps", -1))
    @Test fun spotifyIsMedia() = assertEquals(AppCategory.MEDIA, AppClassifier.classify("com.spotify.music", -1))
    @Test fun chromeIsInteractive() = assertEquals(AppCategory.INTERACTIVE, AppClassifier.classify("com.android.chrome", -1))
    @Test fun systemGameCategoryClassifiesGame() = assertEquals(AppCategory.GAME, AppClassifier.classify("com.vendor.puzzler", ApplicationInfo.CATEGORY_GAME))
    @Test fun unknownIsSafe() = assertEquals(AppCategory.SAFE, AppClassifier.classify("com.vendor.utility", -1))
}
