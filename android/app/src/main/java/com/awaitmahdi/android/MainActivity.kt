package com.awaitmahdi.android

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import java.text.NumberFormat
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val Application.dataStore by preferencesDataStore(name = "await_mahdi")

private object Prefs {
    val GuestUuid = stringPreferencesKey("guest_uuid")
    val Token = stringPreferencesKey("token")
    val Username = stringPreferencesKey("username")
    val UserId = stringPreferencesKey("user_id")
    val ApiBaseUrl = stringPreferencesKey("api_base_url")
    val TodayTotal = intPreferencesKey("today_total")
    val AllTimeTotal = intPreferencesKey("all_time_total")
    val UserToday = intPreferencesKey("user_today")
    val UserTotal = intPreferencesKey("user_total")
}

@Serializable
private data class Credentials(
    val username: String,
    val password: String,
)

@Serializable
private data class TokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("token_type") val tokenType: String,
    @SerialName("user_id") val userId: String,
    val username: String,
)

@Serializable
private data class HeartbeatRequest(
    val count: Int,
    @SerialName("guest_uuid") val guestUuid: String,
)

@Serializable
private data class Stats(
    @SerialName("today_total") val todayTotal: Int = 0,
    @SerialName("all_time_total") val allTimeTotal: Int = 0,
    @SerialName("user_today") val userToday: Int = 0,
    @SerialName("user_total") val userTotal: Int = 0,
)

private data class UserInfo(
    val username: String,
    val id: String,
)

private data class AwaitMahdiState(
    val guestUuid: String = "",
    val token: String? = null,
    val user: UserInfo? = null,
    val apiBaseUrl: String = BuildConfig.DEFAULT_API_BASE_URL,
    val stats: Stats = Stats(),
    val localCount: Int = 0,
    val isSyncing: Boolean = false,
    val errorMessage: String? = null,
) {
    val displayToday: Int get() = stats.todayTotal + localCount
    val displayTotal: Int get() = stats.allTimeTotal + localCount
    val userShare: Int get() = stats.userTotal + localCount
}

private class AwaitMahdiApi(
    private val client: HttpClient,
) {
    suspend fun heartbeat(baseUrl: String, token: String?, count: Int, guestUuid: String): Stats {
        return try {
            client.post("$baseUrl/heartbeat") {
                contentType(ContentType.Application.Json)
                token?.let { bearerAuth(it) }
                setBody(HeartbeatRequest(count = count, guestUuid = guestUuid))
            }.body()
        } catch (err: ClientRequestException) {
            if (err.response.status.value == 404) {
                fallbackSyncAndStats(baseUrl, token, count, guestUuid)
            } else {
                throw err
            }
        }
    }

    private suspend fun fallbackSyncAndStats(
        baseUrl: String,
        token: String?,
        count: Int,
        guestUuid: String,
    ): Stats {
        if (count > 0) {
            client.post("$baseUrl/sync") {
                contentType(ContentType.Application.Json)
                token?.let { bearerAuth(it) }
                setBody(HeartbeatRequest(count = count, guestUuid = guestUuid))
            }
        }

        return client.get("$baseUrl/stats") {
            url {
                parameters.append("guest_uuid", guestUuid)
            }
            token?.let { bearerAuth(it) }
        }.body()
    }

    suspend fun login(baseUrl: String, username: String, password: String): TokenResponse {
        return auth("$baseUrl/login", username, password)
    }

    suspend fun register(baseUrl: String, username: String, password: String): TokenResponse {
        return auth("$baseUrl/register", username, password)
    }

    private suspend fun auth(url: String, username: String, password: String): TokenResponse {
        return client.post(url) {
            contentType(ContentType.Application.Json)
            setBody(Credentials(username = username, password = password))
        }.body()
    }
}

private class AwaitMahdiViewModel(application: Application) : AndroidViewModel(application) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val api = AwaitMahdiApi(
        HttpClient(Android) {
            install(ContentNegotiation) {
                json(json)
            }
        }
    )

    private val _state = MutableStateFlow(AwaitMahdiState())
    val state: StateFlow<AwaitMahdiState> = _state

    private var heartbeatJob: Job? = null

    init {
        viewModelScope.launch {
            application.dataStore.data.collect { prefs ->
                val guestUuid = prefs[Prefs.GuestUuid] ?: UUID.randomUUID().toString()
                if (prefs[Prefs.GuestUuid] == null) {
                    application.dataStore.edit { it[Prefs.GuestUuid] = guestUuid }
                }

                val username = prefs[Prefs.Username]
                val userId = prefs[Prefs.UserId]
                val current = _state.value
                _state.value = current.copy(
                    guestUuid = guestUuid,
                    token = prefs[Prefs.Token],
                    user = if (username != null && userId != null) UserInfo(username, userId) else null,
                    apiBaseUrl = prefs[Prefs.ApiBaseUrl] ?: BuildConfig.DEFAULT_API_BASE_URL,
                    stats = Stats(
                        todayTotal = prefs[Prefs.TodayTotal] ?: 0,
                        allTimeTotal = prefs[Prefs.AllTimeTotal] ?: 0,
                        userToday = prefs[Prefs.UserToday] ?: 0,
                        userTotal = prefs[Prefs.UserTotal] ?: 0,
                    )
                )
            }
        }
        viewModelScope.launch {
            delay(150)
            syncHeartbeat(0)
        }
        heartbeatJob = viewModelScope.launch {
            while (true) {
                delay(12_000)
                syncHeartbeat(state.value.localCount)
            }
        }
    }

    fun onClickSalavat() {
        setLocalCount(state.value.localCount + 1)
    }

    fun clearError() {
        updateVolatile(errorMessage = null)
    }

    fun saveApiBaseUrl(rawUrl: String) {
        val normalized = rawUrl.trim().removeSuffix("/")
        viewModelScope.launch {
            getApplication<Application>().dataStore.edit {
                it[Prefs.ApiBaseUrl] = normalized.ifBlank { BuildConfig.DEFAULT_API_BASE_URL }
            }
            syncHeartbeat(0)
        }
    }

    fun login(username: String, password: String, register: Boolean) {
        viewModelScope.launch {
            updateVolatile(isSyncing = true, errorMessage = null)
            try {
                val snapshot = state.value
                val token = if (register) {
                    api.register(snapshot.apiBaseUrl, username.trim(), password)
                } else {
                    api.login(snapshot.apiBaseUrl, username.trim(), password)
                }
                getApplication<Application>().dataStore.edit {
                    it[Prefs.Token] = token.accessToken
                    it[Prefs.Username] = token.username
                    it[Prefs.UserId] = token.userId
                }
                syncHeartbeat(snapshot.localCount)
            } catch (err: Throwable) {
                updateVolatile(isSyncing = false, errorMessage = friendlyError(err))
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            val unsynced = state.value.localCount
            if (unsynced > 0) {
                syncHeartbeat(unsynced)
            }
            getApplication<Application>().dataStore.edit {
                it.remove(Prefs.Token)
                it.remove(Prefs.Username)
                it.remove(Prefs.UserId)
            }
            syncHeartbeat(0)
        }
    }

    fun flushNow() {
        viewModelScope.launch {
            syncHeartbeat(state.value.localCount)
        }
    }

    private suspend fun syncHeartbeat(countToSync: Int) {
        val snapshot = state.value
        if (snapshot.guestUuid.isBlank()) return
        updateVolatile(isSyncing = true, errorMessage = null)
        try {
            val stats = api.heartbeat(
                baseUrl = snapshot.apiBaseUrl,
                token = snapshot.token,
                count = countToSync,
                guestUuid = snapshot.guestUuid,
            )
            val newLocalCount = if (countToSync > 0) {
                (snapshot.localCount - countToSync).coerceAtLeast(0)
            } else {
                snapshot.localCount
            }
            persistStatsIfMonotonic(stats)
            setLocalCount(newLocalCount)
            updateVolatile(isSyncing = false, errorMessage = null)
        } catch (err: Throwable) {
            updateVolatile(isSyncing = false, errorMessage = friendlyError(err))
        }
    }

    private suspend fun persistStatsIfMonotonic(newStats: Stats) {
        val current = state.value.stats
        if (newStats.allTimeTotal < current.allTimeTotal) return
        getApplication<Application>().dataStore.edit {
            it[Prefs.TodayTotal] = newStats.todayTotal
            it[Prefs.AllTimeTotal] = newStats.allTimeTotal
            it[Prefs.UserToday] = newStats.userToday
            it[Prefs.UserTotal] = newStats.userTotal
        }
    }

    private fun setLocalCount(value: Int) {
        updateVolatile(localCount = value)
    }

    private fun updateVolatile(
        localCount: Int = state.value.localCount,
        isSyncing: Boolean = state.value.isSyncing,
        errorMessage: String? = state.value.errorMessage,
    ) {
        val current = _state.value
        _state.value = current.copy(
            localCount = localCount,
            isSyncing = isSyncing,
            errorMessage = errorMessage,
        )
    }

    private fun friendlyError(err: Throwable): String {
        return when (err) {
            is HttpRequestTimeoutException -> "ارتباط با سرور زمان‌بر شد. آدرس API را بررسی کنید."
            is ClientRequestException -> when (err.response.status.value) {
                400 -> "اطلاعات وارد شده معتبر نیست یا کاربر قبلاً ثبت شده است."
                401 -> "نام کاربری یا رمز عبور اشتباه است."
                else -> "خطای سرور: ${err.response.status.value}"
            }
            else -> err.message?.takeIf { it.isNotBlank() } ?: "ارتباط با سرور برقرار نشد."
        }
    }

    override fun onCleared() {
        heartbeatJob?.cancel()
        super.onCleared()
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            CompositionLocalProviderRtl {
                AwaitMahdiTheme {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = Color(0xFF0F0F0F),
                    ) {
                        AwaitMahdiApp()
                    }
                }
            }
        }
    }
}

@Composable
private fun CompositionLocalProviderRtl(content: @Composable () -> Unit) {
    androidx.compose.runtime.CompositionLocalProvider(
        LocalLayoutDirection provides LayoutDirection.Rtl,
        content = content,
    )
}

@Composable
private fun AwaitMahdiTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = androidx.compose.material3.darkColorScheme(
            background = Color(0xFF0F0F0F),
            surface = Color(0xFF1E1E1E),
            primary = Color(0xFF80CBC4),
            secondary = Color(0xFFA7FFEB),
            onBackground = Color(0xFFE0E0E0),
            onSurface = Color.White,
        ),
        typography = MaterialTheme.typography.copy(
            bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif),
            bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif),
            titleLarge = TextStyle(fontFamily = FontFamily.SansSerif),
        ),
        content = content,
    )
}

@Composable
private fun AwaitMahdiApp(viewModel: AwaitMahdiViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()
    var showInfo by remember { mutableStateOf(false) }
    var showAuth by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.flushNow()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F0F))
            .padding(16.dp),
    ) {
        AuthButton(
            user = state.user,
            onLoginClick = { showAuth = true },
            onLogoutClick = viewModel::logout,
            modifier = Modifier.align(Alignment.TopStart),
        )

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            val isLandscape = maxHeight < 520.dp || maxWidth > maxHeight
            if (isLandscape) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    SalavatButton(
                        onClick = viewModel::onClickSalavat,
                        size = (maxHeight * 0.64f).coerceIn(120.dp, 240.dp),
                    )
                    Spacer(Modifier.width(26.dp))
                    CounterPanel(state = state, compact = true)
                }
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    SalavatButton(
                        onClick = viewModel::onClickSalavat,
                        size = 280.dp,
                    )
                    Spacer(Modifier.height(30.dp))
                    CounterPanel(state = state, compact = false)
                }
            }
        }

        if (state.user != null) {
            Text(
                text = "سهم شما: ${formatFaNumber(state.userShare)}",
                color = Color(0xFF424242),
                fontSize = 13.sp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 38.dp),
            )
        }

        Row(
            modifier = Modifier.align(Alignment.BottomStart),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RoundIconButton(text = "i", italic = true, onClick = { showInfo = true })
            RoundIconButton(text = "⚙", italic = false, onClick = { showSettings = true })
        }

        if (state.isSyncing) {
            CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(22.dp),
                color = Color(0xFF424242),
                strokeWidth = 2.dp,
            )
        }
    }

    state.errorMessage?.let {
        AlertDialog(
            onDismissRequest = viewModel::clearError,
            confirmButton = {
                TextButton(onClick = viewModel::clearError) {
                    Text("باشه")
                }
            },
            title = { Text("خطا", textAlign = TextAlign.Right, modifier = Modifier.fillMaxWidth()) },
            text = { Text(it, textAlign = TextAlign.Right, modifier = Modifier.fillMaxWidth()) },
        )
    }

    if (showInfo) {
        InfoDialog(onDismiss = { showInfo = false })
    }

    if (showAuth) {
        AuthDialog(
            isSyncing = state.isSyncing,
            onDismiss = { showAuth = false },
            onSubmit = { username, password, register ->
                viewModel.login(username, password, register)
                showAuth = false
            },
        )
    }

    if (showSettings) {
        SettingsDialog(
            currentUrl = state.apiBaseUrl,
            onDismiss = { showSettings = false },
            onSave = {
                viewModel.saveApiBaseUrl(it)
                showSettings = false
            },
        )
    }
}

@Composable
private fun AuthButton(
    user: UserInfo?,
    onLoginClick: () -> Unit,
    onLogoutClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val text = user?.let { "خروج (${it.username})" } ?: "ورود / ثبت نام"
    Text(
        text = text,
        color = Color(0xFF9E9E9E),
        fontSize = 13.sp,
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .border(1.dp, Color(0xFF424242), RoundedCornerShape(20.dp))
            .clickable { if (user == null) onLoginClick() else onLogoutClick() }
            .padding(horizontal = 15.dp, vertical = 6.dp),
    )
}

@Composable
private fun SalavatButton(
    onClick: () -> Unit,
    size: androidx.compose.ui.unit.Dp,
) {
    val scope = rememberCoroutineScope()
    val brightness = remember { Animatable(0.6f) }
    val scale = remember { Animatable(1f) }

    Box(
        modifier = Modifier
            .size(size)
            .scale(scale.value)
            .clip(CircleShape)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
            ) {
                onClick()
                scope.launch {
                    scale.snapTo(0.98f)
                    brightness.animateTo(1.2f, animationSpec = tween(100))
                    scale.animateTo(1f, animationSpec = tween(140))
                    brightness.animateTo(0.6f, animationSpec = tween(700))
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val center = Offset(this.size.width / 2f, this.size.height / 2f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF1B5E20).copy(alpha = 0.95f * brightness.value),
                        Color(0xFF004D40).copy(alpha = 0.85f),
                        Color.Black.copy(alpha = 0.95f),
                    ),
                    center = center,
                    radius = this.size.minDimension / 1.55f,
                )
            )
            drawCircle(
                color = Color(0xFFA7FFEB).copy(alpha = 0.28f * brightness.value),
                style = Stroke(width = 5.dp.toPx()),
            )
            drawArc(
                color = Color(0xFFFFD54F).copy(alpha = 0.55f * brightness.value),
                startAngle = -40f,
                sweepAngle = 260f,
                useCenter = false,
                style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "اللهم صل علی",
                color = Color(0xFFE8F5E9),
                fontSize = (size.value / 12).sp,
                fontWeight = FontWeight.Light,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "محمد و آل محمد",
                color = Color(0xFFFFF8E1),
                fontSize = (size.value / 10).sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "و عجل فرجهم",
                color = Color(0xFFA7FFEB),
                fontSize = (size.value / 13).sp,
                fontWeight = FontWeight.Normal,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun CounterPanel(state: AwaitMahdiState, compact: Boolean) {
    val valueSize = if (compact) 32.sp else 48.sp
    val labelSize = if (compact) 13.sp else 16.sp

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.width(if (compact) 160.dp else 280.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            StatItem(label = "تعداد امروز", value = state.displayToday, valueSize = valueSize, labelSize = labelSize)
            AnimatedVisibility(
                visible = state.localCount > 0,
                enter = fadeIn(tween(700)),
                exit = fadeOut(tween(700)),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 6.dp),
            ) {
                Text(
                    text = "(+${formatFaNumber(state.localCount)})",
                    color = Color(0xFF7A7A7A),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Light,
                )
            }
        }
        Spacer(Modifier.height(if (compact) 8.dp else 15.dp))
        Box(
            Modifier
                .fillMaxWidth(0.82f)
                .height(1.dp)
                .background(Color(0xFF424242))
        )
        Spacer(Modifier.height(if (compact) 8.dp else 15.dp))
        StatItem(label = "تعداد کل", value = state.displayTotal, valueSize = valueSize, labelSize = labelSize)
    }
}

@Composable
private fun StatItem(label: String, value: Int, valueSize: androidx.compose.ui.unit.TextUnit, labelSize: androidx.compose.ui.unit.TextUnit) {
    val animatedValue = remember { Animatable(value.toFloat()) }
    LaunchedEffect(value) {
        val diff = value - animatedValue.value
        val duration = if (diff > 0f && diff <= 5f) 100 else 5_000
        animatedValue.animateTo(value.toFloat(), animationSpec = tween(durationMillis = duration))
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, color = Color(0xFF9E9E9E), fontSize = labelSize)
        Text(
            text = formatFaNumber(animatedValue.value.toInt()),
            color = Color.White,
            fontSize = valueSize,
            fontWeight = FontWeight.Light,
            lineHeight = valueSize,
        )
    }
}

@Composable
private fun RoundIconButton(text: String, italic: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .border(1.dp, Color(0xFF424242), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = Color(0xFF9E9E9E),
            fontSize = 15.sp,
            fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun InfoDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF004D40)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("باشه")
            }
        },
        title = { Text("درباره برنامه", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Right) },
        text = {
            Column(horizontalAlignment = Alignment.End, modifier = Modifier.fillMaxWidth()) {
                Text("این برنامه با هدف تعجیل در فرج ، منجی عالم امام زمان علیه السلام .", textAlign = TextAlign.Right)
                Spacer(Modifier.height(10.dp))
                Text("در این برنامه دوستداران و منتظران امام زمان برای سلامتی و تعجیل در فرج ، با هم صلوات می فرستند .", textAlign = TextAlign.Right)
                Spacer(Modifier.height(10.dp))
                Text("در برنامه تعداد صلوات های تمامی منتظران در همان روز و در کل ، نمایش داده می شود .", textAlign = TextAlign.Right)
                Spacer(Modifier.height(18.dp))
                Text(
                    "اللهم صل على محمد و آل محمد و عجل فرجهم",
                    color = Color(0xFFA7FFEB),
                    fontSize = 20.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        containerColor = Color(0xFF1E1E1E),
        textContentColor = Color(0xFFCFCFCF),
        titleContentColor = Color(0xFFE0E0E0),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AuthDialog(
    isSyncing: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (String, String, Boolean) -> Unit,
) {
    var registerMode by remember { mutableStateOf(false) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val colors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = Color.White,
        unfocusedTextColor = Color.White,
        focusedBorderColor = Color(0xFF80CBC4),
        unfocusedBorderColor = Color(0xFF333333),
        cursorColor = Color(0xFF80CBC4),
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                enabled = !isSyncing && username.isNotBlank() && password.isNotBlank(),
                onClick = { onSubmit(username, password, registerMode) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF004D40)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (registerMode) "ثبت نام و ورود" else "ورود")
            }
        },
        dismissButton = {
            TextButton(onClick = { registerMode = !registerMode }, modifier = Modifier.fillMaxWidth()) {
                Text(if (registerMode) "حساب دارید؟ وارد شوید" else "حساب ندارید؟ ثبت نام کنید", color = Color(0xFF80CBC4))
            }
        },
        title = {
            Text(
                if (registerMode) "ثبت نام" else "ورود به حساب",
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Right,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("نام کاربری (موبایل/ایمیل)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = colors,
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("رمز عبور") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    colors = colors,
                )
            }
        },
        containerColor = Color(0xFF1E1E1E),
        textContentColor = Color(0xFFCFCFCF),
        titleContentColor = Color(0xFFE0E0E0),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsDialog(currentUrl: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var url by remember(currentUrl) { mutableStateOf(currentUrl) }
    val configuration = LocalConfiguration.current
    val hint = if (configuration.screenWidthDp > 600) {
        "برای emulator معمولاً http://10.0.2.2:8000 و برای گوشی واقعی آدرس IP سرور روی شبکه را وارد کنید."
    } else {
        "emulator: http://10.0.2.2:8000\nگوشی واقعی: IP سرور روی شبکه"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = { onSave(url) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF004D40)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("ذخیره")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("انصراف", color = Color(0xFF80CBC4))
            }
        },
        title = { Text("تنظیم آدرس بک‌اند", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Right) },
        text = {
            Column {
                Text(hint, color = Color(0xFFBDBDBD), textAlign = TextAlign.Right)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("API URL") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF80CBC4),
                        unfocusedBorderColor = Color(0xFF333333),
                        cursorColor = Color(0xFF80CBC4),
                    ),
                )
            }
        },
        containerColor = Color(0xFF1E1E1E),
        textContentColor = Color(0xFFCFCFCF),
        titleContentColor = Color(0xFFE0E0E0),
    )
}

private fun formatFaNumber(value: Int): String {
    return NumberFormat.getInstance(Locale.forLanguageTag("fa-IR")).format(value)
}
