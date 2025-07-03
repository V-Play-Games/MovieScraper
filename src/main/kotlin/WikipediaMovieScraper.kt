import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URI

const val baseUrl = "https://en.wikipedia.org/wiki/%d_in_film"

fun main() = runBlocking {
    val startYear = 1870
    val endYear = 2027
    val years = startYear..endYear

    val results = executeAll("Download Years", years.toList()) { scrapeYear(it) }
}

suspend fun scrapeYear(year: Int): String = withContext(Dispatchers.IO) {
    URI(baseUrl.format(year))
        .toURL()
        .readText()
        .also { File("years/$year.html").writeText(it) }
}
