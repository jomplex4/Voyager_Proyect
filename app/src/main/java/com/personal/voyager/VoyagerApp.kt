package com.personal.voyager

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.personal.voyager.core.NewPipeDownloader
import com.personal.voyager.core.Prefs
import com.personal.voyager.dl.Downloads
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization

class VoyagerApp : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        NewPipe.init(NewPipeDownloader(), Localization("en", "US"), ContentCountry("US"))
        Downloads.init(this)
    }

    /** Small caches: the target phone has about 2 GB of RAM. */
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.08).build() }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("img"))
                    .maxSizeBytes(16L * 1024 * 1024)
                    .build()
            }
            .allowRgb565(true)
            .crossfade(false)
            .respectCacheHeaders(false)
            .build()
}
