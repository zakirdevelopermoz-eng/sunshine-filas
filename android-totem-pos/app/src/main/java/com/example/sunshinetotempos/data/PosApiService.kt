package com.example.sunshinetotempos.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

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
    var baseUrl: String = "https://xpysyyfogouxapchvkwb.supabase.co"
    private const val SUPABASE_KEY = "sb_publishable_RJFeXPv9PuWg3YMgcajwaQ_bkDmfQ63"

    private val sslSocketFactory by lazy {
        val trustAllCerts = arrayOf<TrustManager>(
            object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            }
        )
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, trustAllCerts, SecureRandom())
        sslContext.socketFactory
    }

    private fun nowIso(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date())
    }

    private fun openSupabaseConnection(path: String, method: String): HttpURLConnection {
        val url = URL("https://xpysyyfogouxapchvkwb.supabase.co/rest/v1/$path")
        return (url.openConnection() as HttpsURLConnection).apply {
            sslSocketFactory = this@PosApiService.sslSocketFactory
            requestMethod = method
            setRequestProperty("apikey", SUPABASE_KEY)
            setRequestProperty("Authorization", "Bearer $SUPABASE_KEY")
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Cache-Control", "no-cache")
            if (method == "POST" || method == "PATCH") {
                setRequestProperty("Prefer", "return=representation")
                doOutput = true
            }
            connectTimeout = 5000
            readTimeout = 5000
        }
    }

    private fun getActiveCycleIdFromSupabase(): String? {
        return try {
            val conn = openSupabaseConnection("queue_cycles?is_current=eq.true&select=id&limit=1", "GET")
            if (conn.responseCode in 200..299) {
                val arr = JSONArray(conn.inputStream.bufferedReader().use { it.readText() })
                if (arr.length() > 0) arr.getJSONObject(0).optString("id") else null
            } else null
        } catch (_: Exception) {
            null
        }
    }

    suspend fun fetchExamHoldTickets(): List<TicketItem> = withContext(Dispatchers.IO) {
        try {
            val cycleId = getActiveCycleIdFromSupabase()
            if (!cycleId.isNullOrEmpty()) {
                val conn = openSupabaseConnection(
                    "tickets?cycle_id=eq.$cycleId&status=in.(AWAITING_EXAM,COMPLETED)&order=created_at.desc",
                    "GET"
                )
                if (conn.responseCode in 200..299) {
                    val arr = JSONArray(conn.inputStream.bufferedReader().use { it.readText() })
                    val list = mutableListOf<TicketItem>()
                    for (i in 0 until arr.length()) {
                        list.add(parseSupabaseTicket(arr.getJSONObject(i)))
                    }
                    return@withContext list
                }
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
            val cycleId = getActiveCycleIdFromSupabase()
            if (!cycleId.isNullOrEmpty()) {
                val seqConn = openSupabaseConnection(
                    "tickets?cycle_id=eq.$cycleId&category=eq.$category&select=sequence_number&order=sequence_number.desc&limit=1",
                    "GET"
                )
                var nextSeq = 1
                if (seqConn.responseCode in 200..299) {
                    val seqArr = JSONArray(seqConn.inputStream.bufferedReader().use { it.readText() })
                    if (seqArr.length() > 0) {
                        nextSeq = seqArr.getJSONObject(0).optInt("sequence_number", 0) + 1
                    }
                }
                val prefix = when (category) {
                    "SEGURO" -> "S"
                    "OUTROS" -> "G"
                    else -> "P"
                }
                val displayCode = "$prefix-${nextSeq.toString().padStart(3, '0')}"
                val now = nowIso()

                val insertConn = openSupabaseConnection("tickets", "POST")
                val payload = JSONObject().apply {
                    put("cycle_id", cycleId)
                    put("service_unit_id", "recepcao")
                    put("category", category)
                    put("sub_service", subService)
                    put("sub_service_label", subServiceLabel)
                    put("sequence_number", nextSeq)
                    put("display_code", displayCode)
                    put("status", "WAITING")
                    put("is_return_from_exam", false)
                    put("reuse_count", 0)
                    put("call_count", 0)
                    put("created_at", now)
                    put("queued_at", now)
                }
                OutputStreamWriter(insertConn.outputStream).use { it.write(payload.toString()) }
                if (insertConn.responseCode in 200..299) {
                    val insertedArr = JSONArray(insertConn.inputStream.bufferedReader().use { it.readText() })
                    if (insertedArr.length() > 0) {
                        return@withContext parseSupabaseTicket(insertedArr.getJSONObject(0))
                    }
                }
            }
        } catch (_: Exception) {
        }
        null
    }

    suspend fun reuseTicket(displayCode: String): ReuseResponse = withContext(Dispatchers.IO) {
        val rawCode = displayCode.trim().uppercase().replace("\\s+".toRegex(), "")
        val formatted = if (rawCode.contains("-")) {
            rawCode
        } else {
            rawCode.replace("^([A-Z]+)(\\d+)$".toRegex()) { m ->
                "${m.groupValues[1]}-${m.groupValues[2].padStart(3, '0')}"
            }
        }

        try {
            val findConn = openSupabaseConnection(
                "tickets?display_code=ilike.$formatted&order=created_at.desc&limit=1",
                "GET"
            )
            if (findConn.responseCode in 200..299) {
                val arr = JSONArray(findConn.inputStream.bufferedReader().use { it.readText() })
                if (arr.length() > 0) {
                    val target = arr.getJSONObject(0)
                    val id = target.optString("id")
                    val nextReuse = target.optInt("reuse_count", 0) + 1
                    val now = nowIso()

                    val patchConn = openSupabaseConnection("tickets?id=eq.$id", "PATCH")
                    val patchObj = JSONObject().apply {
                        put("status", "WAITING")
                        put("is_return_from_exam", true)
                        put("reuse_count", nextReuse)
                        put("returned_from_exam_at", now)
                        put("queued_at", now)
                    }
                    OutputStreamWriter(patchConn.outputStream).use { it.write(patchObj.toString()) }
                    if (patchConn.responseCode in 200..299) {
                        val updatedArr = JSONArray(patchConn.inputStream.bufferedReader().use { it.readText() })
                        if (updatedArr.length() > 0) {
                            val updatedTicket = parseSupabaseTicket(updatedArr.getJSONObject(0))
                            return@withContext ReuseResponse(
                                ok = true,
                                message = "Senha ${updatedTicket.displayCode} reativada com prioridade.",
                                ticket = updatedTicket
                            )
                        }
                    }
                }
            }
        } catch (_: Exception) {
        }

        ReuseResponse(
            ok = false,
            message = "Não foi possível localizar a senha $formatted.",
            ticket = null
        )
    }

    private fun parseSupabaseTicket(obj: JSONObject): TicketItem {
        return TicketItem(
            id = obj.optString("id"),
            displayCode = obj.optString("display_code", "P-001"),
            category = obj.optString("category", "PARTICULAR"),
            subService = obj.optString("sub_service", ""),
            subServiceLabel = obj.optString("sub_service_label", "Atendimento"),
            status = obj.optString("status", "WAITING"),
            isReturnFromExam = obj.optBoolean("is_return_from_exam", false),
            reuseCount = obj.optInt("reuse_count", 0),
            queuedAt = obj.optString("queued_at", "")
        )
    }

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
