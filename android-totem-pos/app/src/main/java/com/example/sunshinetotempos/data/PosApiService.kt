package com.example.sunshinetotempos.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

data class TicketItem(
    val id: String,
    val displayCode: String,
    val category: String,
    val subService: String,
    val subServiceLabel: String,
    val status: String,
    val isReturnFromExam: Boolean,
    val reuseCount: Int,
    val queuedAt: String
)

data class ReuseResponse(
    val ok: Boolean,
    val message: String,
    val ticket: TicketItem?
)

object PosApiService {
    // 10.0.2.2 is the Android Emulator loopback to host PC localhost:5173
    var baseUrl: String = "http://10.0.2.2:5173"

    suspend fun fetchExamHoldTickets(): List<TicketItem> = withContext(Dispatchers.IO) {
        try {
            val url = URL("${baseUrl.trimEnd('/')}/api/state")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 4000
                readTimeout = 4000
            }
            if (conn.responseCode in 200..299) {
                val raw = conn.inputStream.bufferedReader().use { it.readText() }
                val root = JSONObject(raw)
                val cycles = root.optJSONArray("cycles") ?: JSONArray()
                var currentCycleId = ""
                for (i in 0 until cycles.length()) {
                    val c = cycles.getJSONObject(i)
                    if (c.optBoolean("isCurrent", false)) {
                        currentCycleId = c.optString("id")
                    }
                }
                val ticketsArr = root.optJSONArray("tickets") ?: JSONArray()
                val result = mutableListOf<TicketItem>()
                for (i in 0 until ticketsArr.length()) {
                    val obj = ticketsArr.getJSONObject(i)
                    val status = obj.optString("status")
                    val cycleId = obj.optString("cycleId")
                    if ((currentCycleId.isEmpty() || cycleId == currentCycleId) &&
                        (status == "AWAITING_EXAM" || status == "COMPLETED")
                    ) {
                        result.add(parseTicket(obj))
                    }
                }
                return@withContext result
            }
        } catch (_: Exception) {
        }
        emptyList()
    }

    suspend fun createTicket(
        category: String,
        subService: String,
        subServiceLabel: String
    ): TicketItem? = withContext(Dispatchers.IO) {
        try {
            val url = URL("${baseUrl.trimEnd('/')}/api/tickets")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                connectTimeout = 5000
                readTimeout = 5000
            }
            val payload = JSONObject().apply {
                put("category", category)
                put("subService", subService)
                put("subServiceLabel", subServiceLabel)
            }
            OutputStreamWriter(conn.outputStream).use { it.write(payload.toString()) }

            if (conn.responseCode in 200..299) {
                val raw = conn.inputStream.bufferedReader().use { it.readText() }
                return@withContext parseTicket(JSONObject(raw))
            }
        } catch (_: Exception) {
        }
        null
    }

    suspend fun reuseTicket(displayCode: String): ReuseResponse = withContext(Dispatchers.IO) {
        try {
            val url = URL("${baseUrl.trimEnd('/')}/api/tickets/reuse")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                connectTimeout = 5000
                readTimeout = 5000
            }
            val payload = JSONObject().apply {
                put("displayCode", displayCode)
            }
            OutputStreamWriter(conn.outputStream).use { it.write(payload.toString()) }

            val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
            val raw = stream?.bufferedReader()?.use { it.readText() } ?: "{}"
            val json = JSONObject(raw)
            val ok = json.optBoolean("ok", conn.responseCode in 200..299)
            val msg = json.optString("message", "Operação processada.")
            val ticketObj = json.optJSONObject("ticket")
            return@withContext ReuseResponse(
                ok = ok,
                message = msg,
                ticket = ticketObj?.let { parseTicket(it) }
            )
        } catch (e: Exception) {
            ReuseResponse(
                ok = false,
                message = "Erro de ligação ao servidor (${baseUrl}): verifique se o servidor está ativo.",
                ticket = null
            )
        }
    }

    private fun parseTicket(obj: JSONObject): TicketItem {
        return TicketItem(
            id = obj.optString("id"),
            displayCode = obj.optString("displayCode", "P-001"),
            category = obj.optString("category", "PARTICULAR"),
            subService = obj.optString("subService", ""),
            subServiceLabel = obj.optString("subServiceLabel", "Atendimento"),
            status = obj.optString("status", "WAITING"),
            isReturnFromExam = obj.optBoolean("isReturnFromExam", false),
            reuseCount = obj.optInt("reuseCount", 0),
            queuedAt = obj.optString("queuedAt", "")
        )
    }

    // Generates standard ESC/POS 80mm thermal printer byte commands
    fun buildEscPosReceiptBytes(ticket: TicketItem): ByteArray {
        val esc = 0x1B.toByte()
        val gs = 0x1D.toByte()
        val init = byteArrayOf(esc, '@'.code.toByte())
        val center = byteArrayOf(esc, 'a'.code.toByte(), 1)
        val boldOn = byteArrayOf(esc, 'E'.code.toByte(), 1)
        val boldOff = byteArrayOf(esc, 'E'.code.toByte(), 0)
        val doubleSize = byteArrayOf(gs, '!'.code.toByte(), 0x11)
        val normalSize = byteArrayOf(gs, '!'.code.toByte(), 0x00)
        val cutPaper = byteArrayOf(gs, 'V'.code.toByte(), 66, 0)

        val text = buildString {
            append("SUNSHINE HOSPITALS\n")
            append("Recepcao Central\n")
            append("--------------------------------\n")
            append("${ticket.subServiceLabel}\n\n")
        }
        val codeText = "${ticket.displayCode}\n\n"
        val footer = buildString {
            if (ticket.isReturnFromExam) {
                append("PRIORIDADE: RETORNO DE EXAME\n")
            }
            append("--------------------------------\n")
            append("Conserve este talao para retorno\n")
            append("de exames sem perder a fila.\n\n\n")
        }

        return init + center + boldOn + text.toByteArray() +
            doubleSize + codeText.toByteArray() +
            normalSize + boldOff + footer.toByteArray() + cutPaper
    }
}
