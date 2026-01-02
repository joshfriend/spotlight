package com.fueledbycaffeine.spotlight.tasks


/** Matches and extracts project paths from include statements in settings.gradle(.kts) */
internal val INCLUDE_PROJECT_PATH = Regex("""include\s*[(\s]+?["']([^"']+)["']""")
