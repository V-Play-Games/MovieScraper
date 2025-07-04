import net.vpg.vjson.value.JSONArray.Companion.toJSON
import org.jsoup.Jsoup

fun main() {
    val html = """
        <table>
            <tr><th>Name</th><th>Age</th><th>City</th></tr>
            <tr><td rowspan="2">John</td><td rowspan="3">25</td><td>New York</td></tr>
            <tr><td>Data Merged</td></tr>
            <tr><td>Jane</td><td>Los Angeles</td></tr>
        </table>
    """.trimIndent()

    val document = Jsoup.parse(html)
    val table = document.selectFirst("table")!!

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

    // Parse rows
    // Track rowspans: for each column, how many more rows it should fill
    val rowspanTrack = MutableList(headers.size) { 0 }
    val rowspanValues = MutableList<String?>(headers.size) { null }
    rows.drop(1).map { row ->
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
    }.toJSON().toPrettyString().also { println(it) }
}