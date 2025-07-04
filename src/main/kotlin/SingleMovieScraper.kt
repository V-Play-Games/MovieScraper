import net.vpg.vjson.value.JSONArray
import net.vpg.vjson.value.JSONArray.Companion.toJSON
import net.vpg.vjson.value.JSONObject.Companion.toJSON
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

// for debugging purposes

fun main() {
    val year = 1992
    val category = "Hindi"
    val link = "$BASE_URL/List_of_${category}_films_of_$year"
    println(link)
    Jsoup.connect(link)
        .get()
        .body()
        .getElementsByClass("wikitable")
        .let { tables -> mergeCompatibleTables2(tables) }
}
fun mergeCompatibleTables2(tables: List<Element>): List<JSONArray> {
    return tables.groupBy { table -> table.getElementsByTag("th").joinToString { it.text() } }
        .values
        .map { similarTables ->
            similarTables.mapNotNull { parseTableToJson2(it) }.flatten().toJSON()
        }.filter { !it.isEmpty() }
}

fun parseTableToJson2(table: Element): JSONArray? {
    val headers = table.getElementsByTag("th")
        .map { header ->
            header.attr("colspan")
                .takeIf { it.isNotEmpty() }
                ?.toInt()
                ?.let {
                    List(it) { i -> "${header.text()}$i" }
                } ?: listOf(header.text())
        }
        .flatten()
    if (headers.any {
            it.lowercase().contains("awards") || it.lowercase().contains("box office") ||
                    it.lowercase().contains("rank")
        }) {
        return null
    }
    if (headers.isEmpty()) return null
    println("\n\n=== Headers ===")
    println(headers)
    return table.getElementsByTag("tr")
        .drop(1)
        .map { row ->
            println(row.getElementsByTag("td").map { it.text() })
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
