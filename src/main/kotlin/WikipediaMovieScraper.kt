import kotlinx.coroutines.runBlocking
import org.jsoup.Jsoup
import java.io.File
import kotlin.text.substringAfterLast

const val BASE_URL = "https://en.wikipedia.org/wiki"
val moveListLinkRegex = "List_of_([A-Za-z_]+)_films_of_\\d+".toRegex()

fun main(): Unit = runBlocking {
    (1870..2027)
        .toList()
        .executeScrapeTask("Scrape Film Years") { year ->
            "$BASE_URL/${year}_in_film" to "$year/$year.html"
        }
        .executeTask("Process Film Years") { _, yearFile -> getFilmLists(yearFile) }
        .flattenTaskResults()
        .executeTask("Filter Film List Links") { _, anchor ->
            moveListLinkRegex.matchEntire(anchor.attr("href").substringAfterLast("/"))?.groupValues?.get(1)
        }
        .filterSuccessful()
        .filter { it.result != null }
        .mapResults { it!! }
        .executeScrapeTask("Scrape Film Lists") { year, category ->
            "$BASE_URL/List_of_${category}_films_of_$year" to "$year/$category.html"
        }
        .mapToResult()
        .distinct()
        .executeTask("Extract Tables") { file ->
            Jsoup.parse(file).body().getElementsByClass("wikitable")
        }
        .filterSuccessful()
        .filter { it.result.isNotEmpty() }
        .forEach { (file, tables) -> println("${file.parentFile.name}/${file.nameWithoutExtension}: ${tables.size}") }
}

fun getFilmLists(yearFile: File) = Jsoup.parse(yearFile)
    .body()
    .getElementsByTag("a")
    .filter { it.attr("href").startsWith("/wiki/List_of_") }

