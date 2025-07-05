import kotlinx.coroutines.runBlocking
import net.vpg.vjson.value.JSONArray
import net.vpg.vjson.value.JSONArray.Companion.toJSON
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
        .flattenTaskResults()
        .executeTask("Parse Tables") { file, tables ->
            parseTableToJson(tables)
        }
        .filterSuccessful()
        .filter { (_, array) -> array != null }
        .mapResults { it!! }
        .flattenTaskResults()
        .mapResults { it.toObject() }
        .also { rawData ->
            rawData.also { println("Writing Raw JSON to file...") }
                .mapToResult()
                .toJSON()
                .toPrettyString()
                .also { File("rawData.json").writeText(it) }
        }
        .executeTask("Clean Keys") { _, obj ->
            obj.toMap()
                .keys
                .map { it to cleanKey(it) }
                .filter { it.second != null }
                .map { it.second to obj.getString(it.first) }
                .filter { it.second.isNotEmpty() }
                .toMap()
                .toJSON()
        }
        .filterSuccessful()
        .filter { (_, obj) -> !obj.isEmpty() }
        .executeTask("Inject Year and Category") { file, obj ->
            obj.put("year", file.parentFile.name.toInt())
            obj.put("category", file.nameWithoutExtension)
        }
        .also { println("Writing Cleaned JSON to file...") }
        .mapToResult()
        .toJSON()
        .also { File("cleanedData.json").writeText(it.toPrettyString()) }
        .map { it.toObject() }
        .executeTask("Convert to CSV") {
            """
            ${it.getString("title", "")},
            ${it.getString("director", "")},
            ${it.getString("cast", "")},
            ${it.getString("genre", "")},
            ${it.getString("year", "")},
            ${it.getString("category", "")}
            """.trimIndent().replace("\n", "")
        }
        .also { println("Writing CSV to file...") }
        .mapToResult()
        .joinToString("\n")
        .also { File("cleanedData.csv").writeText("title,director,cast,genre,year,category\n") }
        .also { File("cleanedData.csv").appendText(it) }
}

fun cleanKey(key: String) = key.lowercase().replace(" ", "_").let {
    when (it) {
        "title",
        "title[1]",
        "titles",
        "english/korean_title",
        "english_title",
        "f_title",
        "title_(latin)_english",
        "title_(native_title)",
        "director0",
        "movie_name",
        "punjabi",
        "lollywood",
        "film",
        "films",
        "name",
        "movies",
        "movie",
        "pashto"
            -> "title"

        "director(s)",
        "director",
        "director1",
        "'director",
        "director/",
        "direction",
        "directed_by",
        "actor(s)"
            -> "director"

        "cast",
        "featured_cast",
        "cast_and_crew",
        "cast_(subject_of_documentary)",
        "cast_cast",
        "cast_&_crew",
        "british_cast_and_crew",
        "british/uk_cast_and_crew",
        "casta",
        "main_cast",
        "main_actors",
        "actors",
        "actro"
            -> "cast"

        "genre",
        "genre/note",
        "genre(s)",
        "subgenre/notes",
        "subgenre"
            -> "genre"

        else -> null
    }
}

fun parseTableToJson(table: Element): JSONArray? {
    val rows = table.select("tr")
    val headers = rows.first()!!
        .select("th, td")
        .map { header ->
            header.attr("colspan")
                .takeIf { it.isNotEmpty() }
                ?.toInt()
                ?.takeIf { it < 4 }
                ?.let { List(it) { i -> "${header.text()}$i" } }
                ?: listOf(header.text())
        }
        .flatten()
    if (headers.isEmpty() || headers.any {
            it.lowercase().contains("awards") ||
                    it.lowercase().contains("box office") ||
                    it.lowercase().contains("rank")
        }) return null

    // Parse rows
    // Track rowspans: for each column, how many more rows it should fill
    val rowspanTrack = MutableList(headers.size) { 0 }
    val rowspanValues = MutableList<String?>(headers.size) { null }
    return rows.drop(1).map { row ->
        // Prepare rowList, pre-fill with values from rowspans
        val rowList = MutableList<String?>(headers.size) { null }
        headers.indices.forEach { col ->
            if (rowspanTrack[col] > 0) {
                rowList[col] = rowspanValues[col]
                rowspanTrack[col]--
            }
        }

        var colIndex = 0
        row.select("td, th").forEach { cell ->
            // Find next available colIndex
            while (colIndex < headers.size && rowList[colIndex] != null) colIndex++
            val value = cell.text()
            val colspan = cell.attr("colspan").toIntOrNull() ?: 1
            val rowspan = cell.attr("rowspan").toIntOrNull() ?: 1
            for (c in 0 until colspan) {
                val targetCol = colIndex + c
                if (targetCol < headers.size) {
                    rowList[targetCol] = value
                    if (rowspan > 1) {
                        rowspanTrack[targetCol] = rowspan - 1
                        rowspanValues[targetCol] = value
                    }
                }
            }
            colIndex += colspan
        }
        return@map headers.indices.associate { j ->
            headers[j] to (rowList[j] ?: "")
        }
    }.toJSON()
}

fun getFilmLists(yearFile: File) = Jsoup.parse(yearFile)
    .body()
    .getElementsByTag("a")
    .filter { it.attr("href").startsWith("/wiki/List_of_") }
