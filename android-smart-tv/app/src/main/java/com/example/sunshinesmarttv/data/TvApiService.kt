package com.example.sunshinesmarttv.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class TvCounterItem(
    val id: String,
    val code: String,
    val name: String,
    val subtitle: String,
    val isActive: Boolean
)

data class TvTicketItem(
    val id: String,
    val displayCode: String,
    val category: String,
    val subServiceLabel: String,
    val status: String,
    val isReturnFromExam: Boolean,
    val assignedCounterId: String,
    val assignedCounterName: String,
    val callCount: Int,
    val calledAt: String,
    val queuedAt: String
)

data class TvDashboardState(
    val counters: List<TvCounterItem>,
    val activeCall: TvTicketItem?,
    val recentCalls: List<TvTicketItem>,
    val waitingTickets: List<TvTicketItem>,
    val connected: Boolean
)

object TvApiService {
    var baseUrl: String = "http://10.0.2.2:5173"

    suspend fun fetchDashboardState(): TvDashboardState = withContext(Dispatchers.IO) {
        try {
            val url = URL("${baseUrl.trimEnd('/')}/api/state")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 3500
                readTimeout = 3500
            }
            if (conn.responseCode in 200..299) {
                val raw = conn.inputStream.bufferedReader().use { it.readText() }
                val root = JSONObject(raw)

                // Find active cycle
                val cyclesArr = root.optJSONArray("cycles") ?: JSONArray()
                var currentCycleId = ""
                for (i in 0 until cyclesArr.length()) {
                    val c = cyclesArr.getJSONObject(i)
                    if (c.optBoolean("isCurrent", false)) {
                        currentCycleId = c.optString("id")
                    }
                }

                // Parse counters
                val countersArr = root.optJSONArray("counters") ?: JSONArray()
                val counters = mutableListOf<TvCounterItem>()
                for (i in 0 until countersArr.length()) {
                    val obj = countersArr.getJSONObject(i)
                    if (obj.optBoolean("isActive", true)) {
                        counters.add(
                            TvCounterItem(
                                id = obj.optString("id"),
                                code = obj.optString("code"),
                                name = obj.optString("name"),
                                subtitle = obj.optString("subtitle"),
                                isActive = true
                            )
                        )
                    }
                }

                // Parse tickets in active cycle
                val ticketsArr = root.optJSONArray("tickets") ?: JSONArray()
                val currentTickets = mutableListOf<TvTicketItem>()
                for (i in 0 until ticketsArr.length()) {
                    val obj = ticketsArr.getJSONObject(i)
                    if (currentCycleId.isEmpty() || obj.optString("cycleId") == currentCycleId) {
                        currentTickets.add(
                            TvTicketItem(
                                id = obj.optString("id"),
                                displayCode = obj.optString("displayCode"),
                                category = obj.optString("category", "PARTICULAR"),
                                subServiceLabel = obj.optString("subServiceLabel", "Atendimento"),
                                status = obj.optString("status", "WAITING"),
                                isReturnFromExam = obj.optBoolean("isReturnFromExam", false),
                                assignedCounterId = obj.optString("assignedCounterId", ""),
                                assignedCounterName = obj.optString("assignedCounterName", "Balcão"),
                                callCount = obj.optInt("callCount", 0),
                                calledAt = obj.optString("calledAt", ""),
                                queuedAt = obj.optString("queuedAt", "")
                            )
                        )
                    }
                }

                val calledOrServing = currentTickets
                    .filter { (it.status == "CALLED" || it.status == "IN_SERVICE") && it.calledAt.isNotEmpty() }
                    .sortedByDescending { it.calledAt }

                val recentCalls = currentTickets
                    .filter { it.calledAt.isNotEmpty() && it.status != "WAITING" }
                    .sortedByDescending { it.calledAt }
                    .take(4)

                val waiting = currentTickets
                    .filter { it.status == "WAITING" }
                    .sortedWith(
                        compareByDescending<TvTicketItem> { it.isReturnFromExam }
                            .thenBy { it.queuedAt }
                    )
                    .take(6)

                return@withContext TvDashboardState(
                    counters = counters,
                    activeCall = calledOrServing.firstOrNull(),
                    recentCalls = recentCalls,
                    waitingTickets = waiting,
                    connected = true
                )
            }
        } catch (_: Exception) {
        }
        TvDashboardState(
            counters = emptyList(),
            activeCall = null,
            recentCalls = emptyList(),
            waitingTickets = emptyList(),
            connected = false
        )
    }
}
