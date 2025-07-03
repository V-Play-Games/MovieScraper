import kotlinx.coroutines.runBlocking
import net.vpg.vjson.value.JSONArray
import net.vpg.vjson.value.JSONArray.Companion.toJSON
import net.vpg.vjson.value.JSONObject
import net.vpg.vjson.value.JSONObject.Companion.toJSON
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.io.File

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
        .executeTask("Merge Tables") { file, tables ->
            mergeCompatibleTables(tables)
        }
        .flattenTaskResults()
        .flattenTaskResults()
        .mapResults { it.toObject() }
        .executeTask("Inject Year and Category") { file, obj ->
            obj.put("year", file.parentFile.name.toInt())
            obj.put("category", file.nameWithoutExtension)
        }
        .executeTask("Clean Keys") { _, obj ->
            val cleanedData = JSONObject()
            obj.toMap().forEach { (key, value) ->
                cleanedData.put(key.lowercase().replace(" ", "_"), value)
            }
            return@executeTask cleanedData
        }
        .also { println("Writing to file...") }
        .mapToResult()
        .also { list -> list.map { it.toMap().entries }.flatten().map { it.key }.distinct().forEach(::println) }
        .toJSON()
        .toPrettyString()
        .also { File("cleanedData.json").writeText(it) }
}

fun mergeCompatibleTables(tables: List<Element>): List<JSONArray> {
    return tables.groupBy { table -> table.getElementsByTag("th").joinToString { it.text() } }
        .values
        .map { similarTables ->
            similarTables.mapNotNull { parseTableToJson(it) }.flatten().toJSON()
        }.filter { !it.isEmpty() }
}

fun parseTableToJson(table: Element): JSONArray? {
    val headers = table.getElementsByTag("th")
        .also { header ->
            header.attr("colspan")
                .takeIf { it.isNotEmpty() }
                ?.toInt()
                ?.let {
                    List(it) { i -> "$header$i" }
                }
        }
        .map { it.text() }
    if (headers.any {
            it.lowercase().contains("awards") || it.lowercase().contains("box office") ||
                    it.lowercase().contains("rank")
        }) {
        return null
    }
    return table.getElementsByTag("tr")
        .drop(1)
        .map { row ->
            row.getElementsByTag("td")
                .mapIndexedNotNull { index, cell ->
                    if (cell.text().isNotEmpty()) {
                        headers[index] to cell.text()
                    } else null
                }
                .toMap()
                .toJSON()
        }.toJSON()
}

fun getFilmLists(yearFile: File) = Jsoup.parse(yearFile)
    .body()
    .getElementsByTag("a")
    .filter { it.attr("href").startsWith("/wiki/List_of_") }

