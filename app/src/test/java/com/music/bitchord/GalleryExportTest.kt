package com.music.bitchord

import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import com.music.bitchord.ui.replay.saveToGallery
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowContentResolver
import java.io.File
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GalleryExportTest {
    @Test @Config(sdk = [34]) fun scopedGalleryPublishesOnlyAfterWritingThePng() = runBlocking {
        val provider = mediaProvider()
        val image = bitmap()
        try {
            assertTrue(saveToGallery(RuntimeEnvironment.getApplication(), image, "test", "story-card"))
            assertEquals(1, provider.inserted!!.getAsInteger(MediaStore.Images.Media.IS_PENDING))
            assertEquals(0, provider.updated!!.getAsInteger(MediaStore.Images.Media.IS_PENDING))
            assertEquals("Pictures/BitChord", provider.inserted!!.getAsString(MediaStore.Images.Media.RELATIVE_PATH))
            assertEquals(listOf("insert", "write", "publish"), provider.events)
            assertTrue(provider.file.length() > 0)
        } finally { image.recycle() }
    }

    @Test @Config(sdk = [34]) fun failedWriteRemovesTheUnfinishedGalleryEntry() = runBlocking {
        val provider = mediaProvider()
        // The default resolver shadow substitutes a writable stream after permission
        // and file-not-found errors; a disk I/O failure must propagate to the exporter.
        shadowOf(RuntimeEnvironment.getApplication().contentResolver).registerOutputStreamSupplier(
            Uri.parse("content://media/external/images/media/1"),
        ) {
            provider.events += "write"
            throw IOException("Test disk write failure")
        }
        val image = bitmap()
        try {
            assertFalse(saveToGallery(RuntimeEnvironment.getApplication(), image, "test", "story-card"))
            assertEquals(listOf("insert", "write", "delete"), provider.events)
            assertNull(provider.updated)
        } finally { image.recycle() }
    }

    @Test fun legacyGalleryDoesNotUseScopedStorageColumns() = runBlocking {
        val provider = mediaProvider()
        val image = bitmap()
        try {
            assertTrue(saveToGallery(RuntimeEnvironment.getApplication(), image, "test", "story-card"))
            assertFalse(provider.inserted!!.containsKey(MediaStore.Images.Media.IS_PENDING))
            assertFalse(provider.inserted!!.containsKey(MediaStore.Images.Media.RELATIVE_PATH))
            assertEquals(listOf("insert", "write"), provider.events)
        } finally { image.recycle() }
    }

    private fun bitmap() = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }

    private fun mediaProvider(): TestMediaProvider {
        val context = RuntimeEnvironment.getApplication()
        return TestMediaProvider(File.createTempFile("gallery-export", ".png", context.cacheDir)).apply {
            attachInfo(context, ProviderInfo().apply { authority = "media" })
            ShadowContentResolver.registerProviderInternal("media", this)
        }
    }

    private class TestMediaProvider(val file: File) : ContentProvider() {
        val events = mutableListOf<String>()
        var inserted: ContentValues? = null
        var updated: ContentValues? = null
        override fun onCreate() = true
        override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
        override fun getType(uri: Uri) = "image/png"
        override fun insert(uri: Uri, values: ContentValues?): Uri {
            events += "insert"
            inserted = ContentValues(values)
            return Uri.parse("content://media/external/images/media/1")
        }
        override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
            events += "write"
            return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_WRITE or ParcelFileDescriptor.MODE_TRUNCATE)
        }
        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int {
            events += "publish"
            updated = ContentValues(values)
            return 1
        }
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
            events += "delete"
            return 1
        }
    }
}
