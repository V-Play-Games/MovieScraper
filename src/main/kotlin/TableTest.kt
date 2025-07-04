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
    val headers = mutableListOf<String>()
    val data = mutableListOf<Map<String, String>>()

    // Parse headers
    val headerCells = rows.first()!!.select("th, td")
    headerCells.forEach { headers.add(it.text()) }

    // Matrix tracker for col/row spans
    val cellMatrix = mutableListOf<MutableList<String?>>()

    // Parse rows
    // Track rowspans: for each column, how many more rows it should fill
    val rowspanTrack = MutableList(headers.size) { 0 }
    val rowspanValues = MutableList<String?>(headers.size) { null }
    for (i in 1 until rows.size) {
        val row = rows[i]
        val cells = row.select("td")

        // Prepare rowList, pre-fill with values from rowspans
        val rowList = MutableList<String?>(headers.size) { null }
        for (col in headers.indices) {
            if (rowspanTrack[col] > 0) {
                rowList[col] = rowspanValues[col]
                rowspanTrack[col]--
            }
        }

        var colIndex = 0
        for (cell in cells) {
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
        cellMatrix.add(rowList)
    }

    // Convert matrix to list of maps
    for (row in cellMatrix) {
        val rowMap = mutableMapOf<String, String>()
        for (j in headers.indices) {
            rowMap[headers[j]] = row[j] ?: ""
        }
        data.add(rowMap)
    }
    data.toJSON().toPrettyString().also { println(it) }
}