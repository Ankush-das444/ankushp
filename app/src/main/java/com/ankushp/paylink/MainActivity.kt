package com.ankushp.paylink // TODO: Replace with your actual package name

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.WriterException
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

// ==========================================
// 1. NATIVE SQLITE SETUP (NO ROOM REQUIRED)
// ==========================================

data class PaymentHistory(
    val id: Int = 0,
    val upiId: String,
    val payeeName: String,
    val amount: String,
    val note: String,
    val timestamp: Long = System.currentTimeMillis()
)

class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, "upi_database.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        val createTable = """
            CREATE TABLE payment_history (
                id INTEGER PRIMARY KEY AUTOINCREMENT, 
                upiId TEXT, 
                payeeName TEXT, 
                amount TEXT, 
                note TEXT, 
                timestamp INTEGER
            )
        """.trimIndent()
        db.execSQL(createTable)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS payment_history")
        onCreate(db)
    }

    fun insert(history: PaymentHistory) {
        val values = ContentValues().apply {
            put("upiId", history.upiId)
            put("payeeName", history.payeeName)
            put("amount", history.amount)
            put("note", history.note)
            put("timestamp", history.timestamp)
        }
        writableDatabase.insert("payment_history", null, values)
    }

    fun getRecentHistory(): List<PaymentHistory> {
        val list = mutableListOf<PaymentHistory>()
        val cursor = readableDatabase.rawQuery("SELECT * FROM payment_history ORDER BY timestamp DESC LIMIT 50", null)
        
        if (cursor.moveToFirst()) {
            do {
                list.add(
                    PaymentHistory(
                        id = cursor.getInt(cursor.getColumnIndexOrThrow("id")),
                        upiId = cursor.getString(cursor.getColumnIndexOrThrow("upiId")),
                        payeeName = cursor.getString(cursor.getColumnIndexOrThrow("payeeName")),
                        amount = cursor.getString(cursor.getColumnIndexOrThrow("amount")),
                        note = cursor.getString(cursor.getColumnIndexOrThrow("note")),
                        timestamp = cursor.getLong(cursor.getColumnIndexOrThrow("timestamp"))
                    )
                )
            } while (cursor.moveToNext())
        }
        cursor.close()
        return list
    }
}

// ==========================================
// 2. VIEWMODEL (PROFILES & HISTORY MANAGMENT)
// ==========================================

class UpiViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("UpiSettings", Context.MODE_PRIVATE)
    private val dbHelper = DatabaseHelper(application)

    var savedUpiId = mutableStateOf(prefs.getString("upiId", "") ?: "")
    var savedName = mutableStateOf(prefs.getString("payeeName", "") ?: "")

    private val _historyFlow = MutableStateFlow<List<PaymentHistory>>(emptyList())
    val historyFlow: StateFlow<List<PaymentHistory>> = _historyFlow

    init {
        loadHistory()
    }

    private fun loadHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            _historyFlow.value = dbHelper.getRecentHistory()
        }
    }

    fun saveProfile(upiId: String, name: String) {
        prefs.edit().putString("upiId", upiId).putString("payeeName", name).apply()
        savedUpiId.value = upiId
        savedName.value = name
    }

    fun saveToHistory(upiId: String, name: String, amount: String, note: String) {
        viewModelScope.launch(Dispatchers.IO) {
            dbHelper.insert(
                PaymentHistory(upiId = upiId, payeeName = name, amount = amount, note = note)
            )
            loadHistory()
        }
    }
}

// ==========================================
// 3. MAIN ACTIVITY & COMPOSE UI
// ==========================================

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    background = Color(0xFFF5F6FA),
                    surface = Color(0xFFFFFFFF),
                    primary = Color(0xFF4F46E5),
                    secondary = Color(0xFF818CF8),
                    onPrimary = Color.White,
                    onBackground = Color(0xFF111827),
                    onSurface = Color(0xFF111827)
                )
            ) {
                Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFFF5F6FA)) {
                    var showAbout by remember { mutableStateOf(false) }
                    if (showAbout) {
                        AboutScreen(onBack = { showAbout = false })
                    } else {
                        UpiPaymentGeneratorScreen(onNavigateToAbout = { showAbout = true })
                    }
                }
            }
        }
    }
}

// ==========================================
// PREMIUM LIGHT MODE PALETTE
// ==========================================
private val PageBg        = Color(0xFFF5F6FA)
private val CardWhite     = Color(0xFFFFFFFF)
private val Indigo        = Color(0xFF4F46E5)
private val IndigoLight   = Color(0xFF818CF8)
private val Emerald       = Color(0xFF10B981)
private val Amber         = Color(0xFFF59E0B)
private val TextPrimary   = Color(0xFF111827)
private val TextSecondary = Color(0xFF6B7280)
private val StrokeLine    = Color(0xFFE5E7EB)
private val IndigoTint    = Color(0xFFEEF2FF)
private val ShadowColor   = Color(0x14000000)

// Subtle shimmer sweep for the QR result card
@Composable
fun ShimmerBorder(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val sweep by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2800, easing = LinearEasing)),
        label = "sweep"
    )
    Box(
        modifier = modifier.drawBehind {
            val angle = sweep * 360.0
            val rad = Math.toRadians(angle)
            val r = size.minDimension * 0.55f
            val cx = size.width / 2f + (r * cos(rad)).toFloat()
            val cy = size.height / 2f + (r * sin(rad)).toFloat()
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0x28818CF8), Color.Transparent),
                    center = Offset(cx, cy),
                    radius = r * 0.9f
                ),
                radius = r * 0.9f,
                center = Offset(cx, cy)
            )
        }
    ) { content() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpiPaymentGeneratorScreen(viewModel: UpiViewModel = viewModel(), onNavigateToAbout: () -> Unit = {}) {
    val context = LocalContext.current
    val historyList by viewModel.historyFlow.collectAsState(initial = emptyList())

    var upiId by remember { mutableStateOf(viewModel.savedUpiId.value) }
    var payeeName by remember { mutableStateOf(viewModel.savedName.value) }
    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var generatedUpiLink by remember { mutableStateOf("") }
    var showHistorySheet by remember { mutableStateOf(false) }

    // Soft ambient light drifting across the page background
    val infiniteTransition = rememberInfiniteTransition(label = "ambient")
    val lightX by infiniteTransition.animateFloat(
        initialValue = 0.1f, targetValue = 0.9f,
        animationSpec = infiniteRepeatable(tween(7000, easing = LinearEasing), RepeatMode.Reverse),
        label = "lightX"
    )
    val lightY by infiniteTransition.animateFloat(
        initialValue = 0.1f, targetValue = 0.6f,
        animationSpec = infiniteRepeatable(tween(5000, easing = LinearEasing), RepeatMode.Reverse),
        label = "lightY"
    )
    val buttonPulse by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 1.025f,
        animationSpec = infiniteRepeatable(tween(1000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "btnPulse"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PageBg)
            .drawBehind {
                // Soft violet wash top-left
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0x1A818CF8), Color.Transparent),
                        center = Offset(lightX * size.width, lightY * size.height),
                        radius = size.minDimension * 0.85f
                    ),
                    radius = size.minDimension * 0.85f,
                    center = Offset(lightX * size.width, lightY * size.height)
                )
                // Soft emerald wash bottom-right (counter-drift)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0x1210B981), Color.Transparent),
                        center = Offset((1f - lightX) * size.width, (1f - lightY + 0.3f) * size.height),
                        radius = size.minDimension * 0.65f
                    ),
                    radius = size.minDimension * 0.65f,
                    center = Offset((1f - lightX) * size.width, (1f - lightY + 0.3f) * size.height)
                )
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(16.dp))

            // ── Header ──────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "PayLink",
                        fontSize = 30.sp,
                        fontWeight = FontWeight.ExtraBold,
                        style = MaterialTheme.typography.headlineMedium.copy(
                            brush = Brush.linearGradient(listOf(Indigo, IndigoLight))
                        )
                    )
                    Text(
                        text = "Instant UPI QR Generator",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        letterSpacing = 0.4.sp
                    )
                }
                // Header action buttons
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HeaderIconButton(
                        icon = { Icon(Icons.Outlined.Info, contentDescription = "About", tint = Indigo, modifier = Modifier.size(22.dp)) },
                        onClick = onNavigateToAbout
                    )
                    HeaderIconButton(
                        icon = { Icon(Icons.Default.History, contentDescription = "History", tint = Indigo, modifier = Modifier.size(22.dp)) },
                        onClick = { showHistorySheet = true }
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // ── Profile Card ─────────────────────────────────────────────
            PremiumCard {
                Column(modifier = Modifier.padding(20.dp)) {
                    SectionLabel(text = "Profile")
                    Spacer(Modifier.height(12.dp))
                    PremiumTextField(value = upiId, onValueChange = { upiId = it }, label = "Your UPI ID")
                    Spacer(Modifier.height(12.dp))
                    PremiumTextField(value = payeeName, onValueChange = { payeeName = it }, label = "Payee Name")
                }
            }

            Spacer(Modifier.height(14.dp))

            // ── Amount Card ───────────────────────────────────────────────
            PremiumCard {
                Column(modifier = Modifier.padding(20.dp)) {
                    SectionLabel(text = "Payment")
                    Spacer(Modifier.height(12.dp))
                    PremiumTextField(
                        value = amount,
                        onValueChange = { amount = it },
                        label = "Amount (₹)",
                        keyboardType = KeyboardType.Number
                    )
                    Spacer(Modifier.height(12.dp))
                    // Quick preset chips
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val presets = listOf("100", "500", "1000", "2000", "5000")
                        items(presets) { preset ->
                            val isSelected = amount == preset
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(22.dp))
                                    .background(if (isSelected) Indigo else IndigoTint)
                                    .clickable { amount = preset }
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    "₹$preset",
                                    color = if (isSelected) Color.White else Indigo,
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    PremiumTextField(value = note, onValueChange = { note = it }, label = "Note / Description (Optional)")
                }
            }

            Spacer(Modifier.height(24.dp))

            // ── Generate CTA ──────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .scale(buttonPulse)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Brush.linearGradient(listOf(Indigo, Color(0xFF7C3AED))))
                    // soft elevation illusion via bottom shadow stripe
                    .drawBehind {
                        drawRect(
                            color = Color(0x254F46E5),
                            topLeft = Offset(6f, size.height + 2f),
                            size = androidx.compose.ui.geometry.Size(size.width - 12f, 6f)
                        )
                    }
                    .clickable {
                        if (upiId.isNotBlank() && payeeName.isNotBlank() && amount.isNotBlank()) {
                            viewModel.saveProfile(upiId, payeeName)
                            viewModel.saveToHistory(upiId, payeeName, amount, note)
                            generatedUpiLink = generateUpiString(upiId, payeeName, amount, note)
                            qrBitmap = generateCustomQrCode(generatedUpiLink)
                        } else {
                            Toast.makeText(context, "UPI ID, Name, and Amount are required", Toast.LENGTH_SHORT).show()
                        }
                    }
                    .padding(vertical = 17.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("⚡", fontSize = 16.sp)
                    Text(
                        "Generate QR & Link",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color.White,
                        letterSpacing = 0.3.sp
                    )
                }
            }

            Spacer(Modifier.height(28.dp))

            // ── QR Result ────────────────────────────────────────────────
            qrBitmap?.let { bitmap ->
                ShimmerBorder(modifier = Modifier.fillMaxWidth()) {
                    PremiumCard {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(28.dp)
                        ) {
                            // QR image with a crisp indigo ring — centered fix
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .align(Alignment.CenterHorizontally)
                                    .size(234.dp)
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(Color.White)
                                    .border(2.dp, Brush.linearGradient(listOf(Indigo, Emerald)), RoundedCornerShape(20.dp))
                            ) {
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = "UPI QR Code",
                                    modifier = Modifier.size(214.dp)
                                )
                            }
                            Spacer(Modifier.height(20.dp))
                            // Amount display
                            Text(
                                text = "₹$amount",
                                fontSize = 36.sp,
                                fontWeight = FontWeight.ExtraBold,
                                style = MaterialTheme.typography.headlineLarge.copy(
                                    brush = Brush.linearGradient(listOf(Amber, Color(0xFFEF4444)))
                                )
                            )
                            if (note.isNotBlank()) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "For: $note",
                                    color = TextSecondary,
                                    fontSize = 13.sp
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            // Scan badge
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(IndigoTint)
                                    .padding(horizontal = 14.dp, vertical = 5.dp)
                            ) {
                                Text("Scan to Pay", color = Indigo, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))

                // Share section header
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.height(1.dp).weight(1f).background(StrokeLine))
                    Text("  Share to WhatsApp  ", color = TextSecondary, fontWeight = FontWeight.Medium, fontSize = 12.sp)
                    Box(Modifier.height(1.dp).weight(1f).background(StrokeLine))
                }
                Spacer(Modifier.height(12.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    LightOutlineButton(
                        label = "Link Only",
                        modifier = Modifier.weight(1f),
                        onClick = { shareToWhatsApp(context, generatedUpiLink, null) }
                    )
                    LightOutlineButton(
                        label = "QR Only",
                        modifier = Modifier.weight(1f),
                        onClick = {
                            val uri = saveBitmapToCacheAndGetUri(context, bitmap)
                            shareToWhatsApp(context, null, uri)
                        }
                    )
                }
                Spacer(Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Brush.linearGradient(listOf(Color(0xFF059669), Emerald)))
                        .clickable {
                            val uri = saveBitmapToCacheAndGetUri(context, bitmap)
                            shareToWhatsApp(context, generatedUpiLink, uri)
                        }
                        .padding(15.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Share, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Text("Share QR + Link", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
                Spacer(Modifier.height(12.dp))

                // Generic Share Button for Telegram, Signal, Instagram, etc.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White)
                        .border(1.5.dp, StrokeLine, RoundedCornerShape(14.dp))
                        .clickable {
                            val uri = saveBitmapToCacheAndGetUri(context, bitmap)
                            shareToOtherApps(context, generatedUpiLink, uri)
                        }
                        .padding(15.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Share, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(18.dp))
                        Text("Share via Other Apps", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    }
                }
                Spacer(Modifier.height(28.dp))
            }
        }
    }

    if (showHistorySheet) {
        ModalBottomSheet(
            onDismissRequest = { showHistorySheet = false },
            containerColor = Color.White,
            tonalElevation = 0.dp
        ) {
            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(IndigoTint),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.History, contentDescription = null, tint = Indigo, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "Recent Payments",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }
                Spacer(Modifier.height(16.dp))
                if (historyList.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No payment history yet", color = TextSecondary, fontSize = 14.sp)
                    }
                } else {
                    LazyColumn {
                        items(historyList) { item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(PageBg)
                                    .clickable {
                                        upiId = item.upiId
                                        payeeName = item.payeeName
                                        amount = item.amount
                                        note = item.note
                                        showHistorySheet = false
                                    }
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    // Amount pill
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(IndigoTint)
                                            .padding(horizontal = 10.dp, vertical = 5.dp)
                                    ) {
                                        Text(
                                            "₹${item.amount}",
                                            color = Indigo,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp
                                        )
                                    }
                                    Spacer(Modifier.width(10.dp))
                                    Column {
                                        Text(item.payeeName, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                        Text(item.note.ifBlank { "No note" }, color = TextSecondary, fontSize = 12.sp)
                                    }
                                }
                                Text(
                                    SimpleDateFormat("MMM dd", Locale.getDefault()).format(Date(item.timestamp)),
                                    color = TextSecondary,
                                    fontSize = 11.sp
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                        }
                    }
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

// ── Reusable premium light-mode components ─────────────────────────────────────

@Composable
fun PremiumCard(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(CardWhite)
            .border(1.dp, StrokeLine, RoundedCornerShape(20.dp))
            .drawBehind {
                // Soft indigo tint in top-left corner for depth
                drawCircle(
                    color = Color(0x0A4F46E5),
                    radius = size.minDimension * 0.6f,
                    center = Offset(0f, 0f)
                )
            }
    ) { content() }
}

@Composable
fun SectionLabel(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(14.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Indigo)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = text.uppercase(),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.8.sp,
            color = Indigo
        )
    }
}

@Composable
fun PremiumTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontSize = 13.sp) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Indigo,
            unfocusedBorderColor = StrokeLine,
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary,
            cursorColor = Indigo,
            focusedLabelColor = Indigo,
            unfocusedLabelColor = TextSecondary,
            focusedContainerColor = Color.White,
            unfocusedContainerColor = Color.White
        )
    )
}

@Composable
fun LightOutlineButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White)
            .border(1.5.dp, Indigo.copy(0.35f), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 13.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = Indigo, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
    }
}

// ==========================================
// 5. ABOUT SCREEN
// ==========================================

@Composable
fun HeaderIconButton(icon: @Composable () -> Unit, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(CardWhite)
            .border(1.dp, StrokeLine, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { icon() }
}

// ==========================================
// UPDATE CHECKER
// ==========================================

private const val CURRENT_VERSION = "v1.0.0"
private const val GITHUB_RELEASES_API = "https://api.github.com/repos/Ankush-das444/paylink/releases/latest"
private const val GITHUB_RELEASES_PAGE = "https://github.com/Ankush-das444/paylink/releases/latest"

sealed class UpdateState {
    object Idle : UpdateState()
    object Checking : UpdateState()
    object UpToDate : UpdateState()
    data class UpdateAvailable(val latestVersion: String) : UpdateState()
    object Error : UpdateState()
}

suspend fun checkForUpdate(): UpdateState = withContext(Dispatchers.IO) {
    return@withContext try {
        val url = URL(GITHUB_RELEASES_API)
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("Accept", "application/vnd.github.v3+json")
            connectTimeout = 8000
            readTimeout = 8000
        }
        if (connection.responseCode == HttpURLConnection.HTTP_OK) {
            val response = BufferedReader(InputStreamReader(connection.inputStream)).readText()
            val tag = JSONObject(response).getString("tag_name")
            connection.disconnect()
            if (tag != CURRENT_VERSION) UpdateState.UpdateAvailable(tag) else UpdateState.UpToDate
        } else {
            UpdateState.Error
        }
    } catch (e: Exception) {
        UpdateState.Error
    }
}

@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var updateState by remember { mutableStateOf<UpdateState>(UpdateState.Idle) }

    val infiniteTransition = rememberInfiniteTransition(label = "about_ambient")
    val lightX by infiniteTransition.animateFloat(
        initialValue = 0.2f, targetValue = 0.8f,
        animationSpec = infiniteRepeatable(tween(8000, easing = LinearEasing), RepeatMode.Reverse),
        label = "aX"
    )
    val lightY by infiniteTransition.animateFloat(
        initialValue = 0.1f, targetValue = 0.5f,
        animationSpec = infiniteRepeatable(tween(6000, easing = LinearEasing), RepeatMode.Reverse),
        label = "aY"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PageBg)
            .drawBehind {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0x1A818CF8), Color.Transparent),
                        center = Offset(lightX * size.width, lightY * size.height),
                        radius = size.minDimension * 0.85f
                    ),
                    radius = size.minDimension * 0.85f,
                    center = Offset(lightX * size.width, lightY * size.height)
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0x1210B981), Color.Transparent),
                        center = Offset((1f - lightX) * size.width, (1f - lightY + 0.4f) * size.height),
                        radius = size.minDimension * 0.6f
                    ),
                    radius = size.minDimension * 0.6f,
                    center = Offset((1f - lightX) * size.width, (1f - lightY + 0.4f) * size.height)
                )
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(16.dp))

            // Top bar with back button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HeaderIconButton(
                    icon = { Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Indigo, modifier = Modifier.size(22.dp)) },
                    onClick = onBack
                )
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(
                        text = "About PayLink",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold,
                        style = MaterialTheme.typography.titleLarge.copy(
                            brush = Brush.linearGradient(listOf(Indigo, IndigoLight))
                        )
                    )
                    Text(
                        text = "Open-source \u00b7 Privacy-first",
                        fontSize = 11.sp,
                        color = TextSecondary,
                        letterSpacing = 0.3.sp
                    )
                }
            }

            Spacer(Modifier.height(28.dp))

            // App branding
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(24.dp)),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_paylink),
                    contentDescription = "PayLink Icon",
                    modifier = Modifier.size(80.dp)
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "PayLink",
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                style = MaterialTheme.typography.headlineSmall.copy(
                    brush = Brush.linearGradient(listOf(Indigo, IndigoLight))
                )
            )
            Text("v1.0.0", fontSize = 12.sp, color = TextSecondary)

            Spacer(Modifier.height(24.dp))

            // Description card
            PremiumCard {
                Column(modifier = Modifier.padding(20.dp)) {
                    SectionLabel(text = "About")
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "PayLink is a privacy-first, open-source utility to instantly generate and share UPI payment requests.",
                        color = TextPrimary,
                        fontSize = 15.sp,
                        lineHeight = 23.sp
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "No data is sent to any server. Everything stays on your device. Simply enter your UPI ID, set an amount, generate a QR code, and share it instantly via WhatsApp or any app.",
                        color = TextSecondary,
                        fontSize = 13.sp,
                        lineHeight = 20.sp
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // Features card
            PremiumCard {
                Column(modifier = Modifier.padding(20.dp)) {
                    SectionLabel(text = "Features")
                    Spacer(Modifier.height(12.dp))
                    val features = listOf(
                        Icons.Filled.Lock     to "100% offline \u2014 no account or internet needed",
                        Icons.Filled.QrCode   to "Instant QR generation with custom amount & note",
                        Icons.Filled.Share    to "Share QR image or UPI link via WhatsApp",
                        Icons.Filled.History  to "Local payment history with quick re-fill",
                        Icons.Filled.Palette  to "Beautiful, distraction-free UI"
                    )
                    features.forEach { (icon, desc) ->
                        Row(
                            modifier = Modifier.padding(vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(IndigoTint),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = Indigo,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Text(desc, color = TextPrimary, fontSize = 13.sp, lineHeight = 19.sp, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // Developer card
            PremiumCard {
                Column(modifier = Modifier.padding(20.dp)) {
                    SectionLabel(text = "Developer")
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Brush.linearGradient(listOf(Indigo, Color(0xFF7C3AED)))),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Person,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Ankush", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 15.sp)
                            Text("Android Developer", color = TextSecondary, fontSize = 12.sp)
                        }
                    }
                }
            }

            Spacer(Modifier.height(28.dp))

            // Support Development button — primary gradient
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Brush.linearGradient(listOf(Indigo, Color(0xFF7C3AED))))
                    .clickable {
                        val upiUri = Uri.parse("upi://pay?pa=ankushdas44@upi&pn=Ankush+Das&am=49&cu=INR&tn=PayLink+Support")
                        val intent = Intent(Intent.ACTION_VIEW, upiUri)
                        try {
                            context.startActivity(intent)
                        } catch (e: ActivityNotFoundException) {
                            Toast.makeText(context, "No UPI app found", Toast.LENGTH_SHORT).show()
                        }
                    }
                    .padding(vertical = 17.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        imageVector = Icons.Filled.Favorite,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Text("Support Development", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White, letterSpacing = 0.3.sp)
                }
            }

            Spacer(Modifier.height(12.dp))

            // GitHub outline button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(CardWhite)
                    .border(1.5.dp, Indigo.copy(0.35f), RoundedCornerShape(16.dp))
                    .clickable {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Ankush-das444/paylink"))
                        try {
                            context.startActivity(intent)
                        } catch (e: ActivityNotFoundException) {
                            Toast.makeText(context, "No browser found", Toast.LENGTH_SHORT).show()
                        }
                    }
                    .padding(vertical = 17.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        imageVector = Icons.Filled.Code,
                        contentDescription = null,
                        tint = Indigo,
                        modifier = Modifier.size(18.dp)
                    )
                    Text("View on GitHub", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Indigo)
                }
            }

            Spacer(Modifier.height(12.dp))

            // Check for Updates button
            val isChecking = updateState is UpdateState.Checking
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(CardWhite)
                    .border(
                        1.5.dp,
                        when (updateState) {
                            is UpdateState.UpdateAvailable -> Color(0xFF10B981).copy(0.6f)
                            is UpdateState.Error           -> Color(0xFFEF4444).copy(0.5f)
                            else                           -> StrokeLine
                        },
                        RoundedCornerShape(16.dp)
                    )
                    .clickable(enabled = !isChecking) {
                        scope.launch {
                            updateState = UpdateState.Checking
                            updateState = checkForUpdate()
                        }
                    }
                    .padding(vertical = 17.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (isChecking) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = Indigo
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.SystemUpdate,
                            contentDescription = null,
                            tint = when (updateState) {
                                is UpdateState.UpdateAvailable -> Color(0xFF10B981)
                                is UpdateState.Error           -> Color(0xFFEF4444)
                                else                           -> TextSecondary
                            },
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Text(
                        text = when (updateState) {
                            is UpdateState.Idle            -> "Check for Updates"
                            is UpdateState.Checking        -> "Checking…"
                            is UpdateState.UpToDate        -> "You're up to date!"
                            is UpdateState.UpdateAvailable -> "Update available — tap to download"
                            is UpdateState.Error           -> "Couldn't check — tap to retry"
                        },
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        color = when (updateState) {
                            is UpdateState.UpdateAvailable -> Color(0xFF10B981)
                            is UpdateState.Error           -> Color(0xFFEF4444)
                            else                           -> TextSecondary
                        }
                    )
                }
            }

            // Update available — show green download CTA
            if (updateState is UpdateState.UpdateAvailable) {
                val latest = (updateState as UpdateState.UpdateAvailable).latestVersion
                Spacer(Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Brush.linearGradient(listOf(Color(0xFF059669), Color(0xFF10B981))))
                        .clickable {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_RELEASES_PAGE))
                            try { context.startActivity(intent) }
                            catch (e: ActivityNotFoundException) {
                                Toast.makeText(context, "No browser found", Toast.LENGTH_SHORT).show()
                            }
                        }
                        .padding(vertical = 17.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.SystemUpdate,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            "Download $latest",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color.White,
                            letterSpacing = 0.3.sp
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            Text(
                text = "Released under the MIT License",
                color = TextSecondary,
                fontSize = 11.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(Modifier.height(32.dp))
        }
    }
}

// ==========================================
// 4. HELPER FUNCTIONS
// ==========================================

fun generateUpiString(upiId: String, name: String, amount: String, note: String): String {
    val baseUrl = "upi://pay?pa=$upiId&pn=$name&am=$amount&cu=INR"
    return if (note.isNotBlank()) "$baseUrl&tn=$note" else baseUrl
}

fun generateCustomQrCode(content: String, size: Int = 512): Bitmap? {
    if (content.isEmpty()) return null
    return try {
        val hints = mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.H)
        val barcodeEncoder = MultiFormatWriter()
        val bitMatrix = barcodeEncoder.encode(content, BarcodeFormat.QR_CODE, size, size, hints)
        
        val width = bitMatrix.width
        val height = bitMatrix.height
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        
        val qrColor = AndroidColor.rgb(21, 101, 192) 
        val bgColor = AndroidColor.WHITE
        
        for (x in 0 until width) {
            for (y in 0 until height) {
                bitmap.setPixel(x, y, if (bitMatrix[x, y]) qrColor else bgColor)
            }
        }
        
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        
        paint.color = AndroidColor.WHITE
        val radius = size / 8f
        canvas.drawCircle(width / 2f, height / 2f, radius, paint)
        
        paint.color = qrColor
        paint.textSize = radius * 1.2f
        paint.textAlign = Paint.Align.CENTER
        paint.isFakeBoldText = true
        val textY = (height / 2f) - ((paint.descent() + paint.ascent()) / 2f)
        canvas.drawText("₹", width / 2f, textY, paint)
        
        bitmap
    } catch (e: WriterException) {
        e.printStackTrace()
        null
    }
}

fun saveBitmapToCacheAndGetUri(context: Context, bitmap: Bitmap): Uri? {
    return try {
        val imagesFolder = File(context.cacheDir, "images")
        imagesFolder.mkdirs()
        val file = File(imagesFolder, "shared_qr.png")
        val stream = FileOutputStream(file)
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        stream.flush()
        stream.close()
        
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

fun shareToWhatsApp(context: Context, link: String?, imageUri: Uri?) {
    val messageText = link?.let { "Hello! Please pay using this UPI link:\n\n$it" }

    val intent = Intent(Intent.ACTION_SEND).apply {
        setPackage("com.whatsapp")
        if (imageUri != null && messageText != null) {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, imageUri)
            putExtra(Intent.EXTRA_TEXT, messageText)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } else if (imageUri != null) {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, imageUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } else if (messageText != null) {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, messageText)
        }
    }
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "WhatsApp is not installed", Toast.LENGTH_SHORT).show()
    }
}

fun shareToOtherApps(context: Context, link: String?, imageUri: Uri?) {
    val messageText = link?.let { "Hello! Please pay using this UPI link:\n\n$it" }

    val intent = Intent(Intent.ACTION_SEND).apply {
        if (imageUri != null && messageText != null) {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, imageUri)
            putExtra(Intent.EXTRA_TEXT, messageText)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } else if (imageUri != null) {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, imageUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } else if (messageText != null) {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, messageText)
        }
    }

    // This pops up the native Android bottom sheet with all installed apps
    val chooser = Intent.createChooser(intent, "Share Payment Request")
    context.startActivity(chooser)
}
