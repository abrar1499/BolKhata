package com.bolkhata.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** One stock item with what it last cost and last sold for. */
data class StockItem(
    val line: StockLine,
    val buyRate: Double,
    val sellRate: Double,
    val boughtTotal: Double
)

private fun unitRate(e: Entry): Double = when {
    e.rate > 0 -> e.rate
    e.qty > 0 && e.amount > 0 -> e.amount / e.qty
    else -> 0.0
}

fun stockItems(entries: List<Entry>): List<StockItem> {
    val sorted = entries.sortedBy { it.date }
    return stock(entries)
        .filter { kotlin.math.abs(it.qty) > 0.0001 }
        .map { line ->
            val key = line.name.trim().lowercase()
            val mine = sorted.filter { it.item.trim().lowercase() == key && it.qty > 0 }
            val buys = mine.filter { it.type == Type.PURCHASE || it.type == Type.LIYA }
            val sells = mine.filter { it.type == Type.SALE }
            StockItem(
                line = line,
                buyRate = buys.lastOrNull()?.let { unitRate(it) } ?: 0.0,
                sellRate = sells.lastOrNull()?.let { unitRate(it) } ?: 0.0,
                boughtTotal = buys.sumOf { it.qty }
            )
        }
        .sortedBy { it.line.name.lowercase() }
}

/** A ready-to-fill sale or purchase for one stock item, opened from the Sold / Bought buttons. */
fun tradeTemplate(item: StockItem, sale: Boolean): Entry {
    val now = System.currentTimeMillis()
    val rate = if (sale) item.sellRate.takeIf { it > 0 } ?: item.buyRate else item.buyRate
    return Entry(
        id = "e" + System.nanoTime(),
        type = if (sale) Type.SALE else Type.PURCHASE,
        amount = rate,
        category = if (sale) Cat.SALES else Cat.STOCK,
        item = item.line.name,
        person = "",
        note = (if (sale) "Sold " else "Bought ") + item.line.name,
        date = now,
        createdAt = now,
        qty = 1.0,
        unit = item.line.unit,
        rate = rate
    )
}

@Composable
fun StockScreen(
    entries: List<Entry>,
    currency: String,
    chatBadge: Int,
    onTab: (Tab) -> Unit,
    onMenu: () -> Unit,
    onTrade: (Entry) -> Unit,
    onReports: () -> Unit
) {
    val nowMs = System.currentTimeMillis()
    val today = entries.filter { sameDay(it.date, nowMs) }
    val sales = today.filter { it.type == Type.SALE }.sumOf { it.amount }
    val bought = today.filter { it.type == Type.PURCHASE }.sumOf { it.amount }
    val profit = profitBreakdown(entries, today).let { it.costedSalesAmount - it.costOfGoods }
    val stockList = remember(entries) { stockItems(entries) }
    val value = stockList.sumOf { if (it.line.qty > 0) it.line.qty * it.buyRate else 0.0 }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Box(Modifier.fillMaxWidth()) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
                ) { HeroBackground(Modifier.matchParentSize()) }
                Column(Modifier.padding(horizontal = 16.dp).padding(top = 14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onMenu) { Icon(Icons.Filled.Menu, contentDescription = "Menu", tint = LocalHeader.current.on) }
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Stock", color = LocalHeader.current.on, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Text(if (stockList.size == 1) "1 item in stock" else "${stockList.size} items in stock", color = LocalHeader.current.on.copy(alpha = 0.8f), fontSize = 13.sp)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    TabSwitcher(Tab.STOCK, true, chatBadge, onTab)
                    Spacer(Modifier.height(14.dp))
                    Panel(Modifier.fillMaxWidth(), onClick = onReports, padding = 14.dp) {
                        Text("Today in business", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StockFigure("Sales", fmt(sales), GreenIn, Modifier.weight(1f), icon = Icons.Filled.ArrowDownward)
                            StockFigure("Bought", fmt(bought), RedSpend, Modifier.weight(1f), icon = Icons.Filled.ArrowUpward)
                            StockFigure(
                                "Profit", (if (profit > 0) "+" else "") + fmt(profit),
                                MaterialTheme.colorScheme.onSurfaceVariant, Modifier.weight(1f),
                                valueColor = if (profit >= 0) GreenIn else RedSpend
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "Stock value: " + money(currency, value),
                            fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        if (stockList.isEmpty()) {
            item {
                Text(
                    "No stock yet. Say: \"bought 10 rice bags at 75\"",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
                )
            }
        } else {
            item {
                Text(
                    "Stock", fontWeight = FontWeight.Bold, fontSize = 17.sp,
                    modifier = Modifier.padding(horizontal = 20.dp).padding(top = 16.dp, bottom = 4.dp)
                )
            }
        }

        items(stockList, key = { "stock-" + it.line.name.lowercase() }) { s ->
            StockCard(s, currency, onSold = { onTrade(tradeTemplate(s, sale = true)) }, onBought = { onTrade(tradeTemplate(s, sale = false)) })
        }
    }
}

@Composable
private fun StockCard(s: StockItem, currency: String, onSold: () -> Unit, onBought: () -> Unit) {
    val qty = s.line.qty
    val low = qty > 0 && (qty <= 3 || (s.boughtTotal > 0 && qty / s.boughtTotal <= 0.2))
    val share = if (s.boughtTotal > 0) (qty / s.boughtTotal).coerceIn(0.0, 1.0).toFloat() else 0f
    Panel(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp), padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(s.line.name, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val prices = listOfNotNull(
                    s.buyRate.takeIf { it > 0 }?.let { "Buy " + fmt(it) },
                    s.sellRate.takeIf { it > 0 }?.let { "Sell " + fmt(it) }
                ).joinToString(" · ")
                if (prices.isNotEmpty()) {
                    Text(prices + " " + currency, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (low) {
                Box(
                    Modifier.clip(CircleShape).background(OrangeOwe.copy(alpha = 0.16f)).padding(horizontal = 8.dp, vertical = 3.dp)
                ) { Text("Low", color = OrangeOwe, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                Spacer(Modifier.width(8.dp))
            }
            Text(
                fmt(qty) + (if (s.line.unit.isBlank()) "" else " " + s.line.unit),
                fontWeight = FontWeight.ExtraBold, fontSize = 17.sp,
                color = if (qty < 0) RedSpend else MaterialTheme.colorScheme.onSurface
            )
        }
        Spacer(Modifier.height(10.dp))
        Box(
            Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(
                Modifier.fillMaxWidth(share).fillMaxHeight().clip(CircleShape)
                    .background(if (low) OrangeOwe else GreenIn)
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(
                onClick = onSold, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.filledTonalButtonColors(containerColor = GreenIn.copy(alpha = 0.14f), contentColor = GreenIn)
            ) {
                Icon(Icons.Filled.ArrowDownward, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Sold", fontWeight = FontWeight.Bold)
            }
            FilledTonalButton(
                onClick = onBought, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.filledTonalButtonColors(containerColor = RedSpend.copy(alpha = 0.12f), contentColor = RedSpend)
            ) {
                Icon(Icons.Filled.ArrowUpward, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Bought", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun StockFigure(
    label: String,
    value: String,
    labelColor: Color,
    modifier: Modifier,
    valueColor: Color = Color.Unspecified,
    icon: ImageVector? = null
) {
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(labelColor.copy(alpha = 0.08f))
            .padding(horizontal = 10.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = labelColor, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
            }
            Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = labelColor, maxLines = 1)
        }
        Spacer(Modifier.height(4.dp))
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = valueColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
