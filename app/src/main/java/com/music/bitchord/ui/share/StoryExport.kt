package com.music.bitchord.ui.share

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.music.bitchord.ui.replay.cacheForSharing
import java.util.UUID

/** Each hand-off has its own file while another app may still be reading the previous one. */
internal suspend fun cacheStoryForSharing(context: Context, bitmap: Bitmap): Uri? =
    cacheForSharing(context, bitmap, "story-${UUID.randomUUID()}.png")
