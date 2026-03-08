package eu.kanade.tachiyomi.extension.id.komiknextgonline

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.util.Log
import kotlin.system.exitProcess

class KomikNextGOnlineUrlActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pathSegments = intent?.data?.pathSegments
        if (pathSegments != null && pathSegments.size >= 2 && "comic".equals(pathSegments[0])) {
            val slug = pathSegments[1]
            try {
                startActivity(
                    Intent().apply {
                        action = "eu.kanade.tachiyomi.SEARCH"
                        putExtra("query", "id:$slug")
                        putExtra("filter", packageName)
                    },
                )
            } catch (e: ActivityNotFoundException) {
                Log.e("KomikNextGOnlineUrlActivity", e.toString())
            }
        } else {
            Log.e("KomikNextGOnlineUrlActivity", "Could not parse URI from intent " + intent.toString())
        }

        finish()
        exitProcess(0)
    }
}
