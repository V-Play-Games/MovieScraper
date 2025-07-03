import kotlinx.coroutines.runBlocking

fun main(): Unit = runBlocking {
    val startYear = 1870
    val endYear = 2027
    val years = startYear..endYear

    years.toList()
        .executeScrapeTask("Scrape Years", "years") { year ->
            "https://en.wikipedia.org/wiki/${year}_in_film" to "$year.html"
        }.filterSuccessful()

}
