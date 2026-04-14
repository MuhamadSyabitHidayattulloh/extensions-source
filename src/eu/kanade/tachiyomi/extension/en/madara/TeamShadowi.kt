package eu.kanade.tachiyomi.extension.en.madara

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class TeamShadowi :
    Madara(
        "Team Shadowi",
        "https://www.team-shadowi.com",
        "en",
        dateFormat = SimpleDateFormat("MMMM d, yyyy", Locale.US),
    )
    
