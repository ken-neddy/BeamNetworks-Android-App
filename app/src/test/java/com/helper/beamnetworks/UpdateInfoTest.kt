package com.helper.beamnetworks

import com.helper.beamnetworks.data.update.ReleaseChange
import com.helper.beamnetworks.data.update.ReleaseNotes
import com.helper.beamnetworks.data.update.UpdateInfo
import com.helper.beamnetworks.data.update.compareVersions
import org.junit.Assert.assertEquals
import org.junit.Test

class UpdateInfoTest {
    @Test
    fun comparesDottedVersionsNumerically() {
        assertEquals(1, compareVersions("1.10", "1.9"))
        assertEquals(0, compareVersions("1.2", "1.2.0"))
        assertEquals(-1, compareVersions("1.2", "1.3"))
    }

    @Test
    fun listsOnlyReleasesNewerThanInstalled() {
        val info = UpdateInfo(
            versionCode = 4, versionName = "1.3", apk = "a.apk", sha256 = "x",
            history = listOf("1.3", "1.2", "1.1").map { ReleaseNotes(it, listOf(ReleaseChange("Change in $it"))) },
        )
        assertEquals(listOf("1.3", "1.2"), info.changesSince("1.1").map { it.version })
        assertEquals(emptyList<String>(), info.changesSince("1.3").map { it.version })
    }
}
