package com.shelfio.app

import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import com.shelfio.app.data.CoverStorage
import com.shelfio.app.data.SettingsRepository
import com.shelfio.app.data.ShelfioRepository
import com.shelfio.app.data.backup.BackupManager
import com.shelfio.app.data.books.BookLookup
import com.shelfio.app.data.db.ShelfioDatabase
import com.shelfio.app.data.report.PdfReport
import java.security.MessageDigest

/** Crea una sola vez la base de datos y los repositorios que comparte toda la app. */
class ShelfioApplication : Application() {
    private val database by lazy { ShelfioDatabase.create(this) }
    val covers by lazy { CoverStorage(this) }
    val repository by lazy { ShelfioRepository(database.dao(), covers) }
    val backup by lazy { BackupManager(this, database.dao(), covers) }
    val pdfReport by lazy { PdfReport(this) }
    val settings by lazy { SettingsRepository(this) }
    val bookLookup by lazy {
        BookLookup(
            userApiKey = settings::getGoogleBooksKey,
            appApiKey = BuildConfig.GOOGLE_BOOKS_API_KEY,
            androidPackage = packageName,
            androidCertSha1 = signingCertSha1(),
        )
    }

    /** Huella SHA-1 (en hexadecimal, sin dos puntos) del certificado con el que está firmada la app. */
    @Suppress("DEPRECATION") // la API antigua es la única en Android 8.x (minSdk 26)
    private fun signingCertSha1(): String {
        val signature = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES).signingInfo?.apkContentsSigners?.firstOrNull()
        } else {
            packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNATURES).signatures?.firstOrNull()
        } ?: return ""
        return MessageDigest.getInstance("SHA-1").digest(signature.toByteArray()).joinToString("") { "%02X".format(it) }
    }
}
