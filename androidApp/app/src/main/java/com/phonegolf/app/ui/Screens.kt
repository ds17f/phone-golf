package com.phonegolf.app.ui

import android.content.Intent
import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.ButtonDefaults
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.phonegolf.app.Api
import com.phonegolf.app.Course
import com.phonegolf.app.DayScore
import com.phonegolf.app.Group
import com.phonegolf.app.HandicapInfo
import com.phonegolf.app.Leaderboard
import com.phonegolf.app.Prefs
import com.phonegolf.app.Sync
import com.phonegolf.app.UnlockCounter
import com.phonegolf.app.formatToPar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material.icons.Icons
import com.phonegolf.app.ApiException
import com.phonegolf.app.LocalBackend
import com.phonegolf.app.Scoring
import com.phonegolf.app.featuredIn
import com.phonegolf.app.holePlaque
import com.phonegolf.app.roundPlaque
import com.phonegolf.app.target
import com.phonegolf.app.todayHole
import com.phonegolf.app.toParOf
private val dayFmt = DateTimeFormatter.ofPattern("EEE MMM d")
private fun fmt(date: String) = LocalDate.parse(date).format(dayFmt)
private val num = KeyboardOptions(keyboardType = KeyboardType.Number)

private fun lastSaveText(prefs: Prefs): String {
    val last = prefs.lastSyncMillis
    val sent = if (prefs.isLocal) "Saved" else "Sent to leaderboard"
    return prefs.lastSyncError?.let { "Last send failed: $it" }
        ?: when {
            last == 0L -> if (prefs.isLocal) "Not saved yet" else "Not sent yet"
            System.currentTimeMillis() - last < 60_000 -> "$sent just now"
            else -> "$sent " + DateUtils.getRelativeTimeSpanString(last)
        }
}

@Composable
private fun UsageAccessCard(context: android.content.Context) {
    SectionCard("Allow usage access") {
        Text(
            "Phone Golf counts how many times you unlock your phone. Android needs your permission first. " +
                "In the list, choose Phone Golf and turn on \"Permit usage access\".",
        )
        Text(
            "The app reads only the unlock count. It does not read which apps you use or what you do in them.",
            style = MaterialTheme.typography.bodySmall,
        )
        Button(onClick = { context.startActivity(UnlockCounter.usageAccessSettingsIntent()) }) { Text("Allow usage access") }
    }
}

// ---- welcome -----------------------------------------------------------------

@Composable
fun WelcomeScreen(prefs: Prefs, onGroup: () -> Unit, onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var hasAccess by remember { mutableStateOf(UnlockCounter.hasUsageAccess(context)) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var handicap by remember { mutableStateOf("60") }
    var edited by remember { mutableStateOf(false) }
    var suggestedDays by remember { mutableStateOf<Int?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LifecycleResumeEffect(Unit) {
        hasAccess = UnlockCounter.hasUsageAccess(context)
        refreshKey++
        onPauseOrDispose { }
    }
    // Suggest a handicap from the last 6 full days, when Android has the data.
    LaunchedEffect(refreshKey) {
        if (!hasAccess) return@LaunchedEffect
        val counts = withContext(Dispatchers.Default) {
            (1..6).mapNotNull { UnlockCounter.countUnlocks(context, LocalDate.now().minusDays(it.toLong())) }
        }
        val sug = if (counts.size >= 3) Scoring.suggest(counts) else null
        if (sug != null && sug.first > 0 && !edited) { handicap = sug.first.toString(); suggestedDays = counts.size }
    }

    // The first screen is the green board, the same as on iPhone.
    val gold = ButtonDefaults.buttonColors(containerColor = Golf.Gold, contentColor = Golf.PlaqueInk)
    Scaffold(containerColor = Golf.Board, contentColor = Golf.BoardText) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Phone Golf", fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 40.sp, color = Color.White)
            Text(
                "Each day is a hole. Your score is how many times you unlock your phone. Low score wins.",
                textAlign = TextAlign.Center,
            )
            if (!hasAccess) {
                Button(onClick = { context.startActivity(UnlockCounter.usageAccessSettingsIntent()) }, colors = gold, modifier = Modifier.fillMaxWidth()) {
                    Text("Allow usage access")
                }
                Text(
                    "Android needs your permission to count unlocks. In the list, choose Phone Golf and turn on \"Permit usage access\". " +
                        "The app reads only the unlock count. It does not read which apps you use.",
                    style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text("HANDICAP", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.5.sp)
            BasicTextField(
                handicap, { handicap = it.filter(Char::isDigit).take(4); edited = true }, singleLine = true, keyboardOptions = num,
                textStyle = TextStyle(color = Golf.PlaqueInk, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 40.sp, textAlign = TextAlign.Center),
                modifier = Modifier.width(160.dp).background(Golf.Plaque, RoundedCornerShape(6.dp)).padding(vertical = 8.dp),
            )
            Text(
                "Your normal day, in unlocks. You can change it later." + (suggestedDays?.let { " Suggested from your last $it days." } ?: ""),
                style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center,
            )
            if (error != null) Text(error!!, color = Golf.Gold)
            Button(
                enabled = !busy && handicap.isNotBlank(),
                colors = gold,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    busy = true
                    scope.launch {
                        try {
                            prefs.startLocal("Me", handicap.toInt())
                            prefs.startSoloRound()
                            onDone()
                        } catch (e: Exception) { error = e.message } finally { busy = false }
                    }
                },
            ) { Text("Tee off", fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 18.sp) }
            TextButton(onClick = onGroup) { Text("Play with a group", color = Color.White) }
        }
    }
}

// ---- round (home) -----------------------------------------------------------------

@Composable
fun RoundScreen(prefs: Prefs, nav: Nav) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var hasAccess by remember { mutableStateOf(UnlockCounter.hasUsageAccess(context)) }
    var live by remember { mutableStateOf<Int?>(null) }
    var groups by remember { mutableStateOf<List<Group>?>(null) }
    var board by remember { mutableStateOf<Leaderboard?>(null) }
    var meId by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var refreshKey by remember { mutableIntStateOf(0) }
    val local = prefs.isLocal
    val todayStr = LocalDate.now().toString()

    // Re-check permission and recount every time the app comes to the front.
    LifecycleResumeEffect(Unit) {
        hasAccess = UnlockCounter.hasUsageAccess(context)
        refreshKey++
        onPauseOrDispose { }
    }

    LaunchedEffect(refreshKey) {
        if (hasAccess) {
            live = withContext(Dispatchers.Default) { UnlockCounter.countUnlocks(context, LocalDate.now()) }
            runCatching { Sync.run(context) }
        }
        try {
            val be = prefs.backend()
            meId = runCatching { be.me().id }.getOrNull()
            val gs = be.groups()
            board = featuredIn(gs)?.let { be.leaderboard(it.second.id) }
            groups = gs
            error = null
        } catch (e: Exception) {
            error = if (local) e.message else "Could not reach the server: ${e.message}"
        }
    }

    val b = board
    val me = b?.cards?.let { cards -> if (local) cards.firstOrNull() else cards.firstOrNull { it.playerId == meId } }
    AppScaffold(b?.course?.name ?: "Phone Golf", nav, showSettings = true) {
        item { ErrorText(error) }
        if (groups == null && error == null) { item { Loading() }; return@AppScaffold }

        if (b != null) {
            val hole = holePlaque(b.course)
            val hh = todayHole(me, todayStr)
            val count = if (hasAccess) (live ?: hh?.gross) else null
            val todayToPar = if (count != null && hh != null) toParOf(count, hh) else null
            val round = roundPlaque(me, b.course)
            item {
                ScoreStrip(
                    listOf(
                        Plaque("HOLE", hole.first, Golf.PlaqueInk, hole.second),
                        Plaque("TODAY", if (count != null && hh != null) "$count" else "–", toParInk(todayToPar), hh?.let { "target ${target(it)}" } ?: ""),
                        Plaque("ROUND", round.first, if (me != null && me.thru > 0 && me.totalToPar < 0) Golf.Red else Golf.PlaqueInk, round.second),
                    ),
                )
            }
            if (local && b.course.status == "upcoming") {
                // A solo round can start today. The player does not wait for the first day.
                item {
                    CenterButton("Tee off today") {
                        scope.launch {
                            runCatching { LocalBackend(prefs.localFile).moveCourse(b.course.id, LocalDate.now()) }.onFailure { error = it.message }
                            refreshKey++
                        }
                    }
                }
            }
            if (local && b.course.status == "finished") {
                item { CenterButton("Play another round") { scope.launch { runCatching { prefs.startSoloRound() }.onFailure { error = it.message }; refreshKey++ } } }
            }
            item {
                if (!hasAccess) UsageAccessCard(context)
                else Text(
                    "Counted automatically. ${lastSaveText(prefs)}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item { PaperScorecard(b, meId ?: me?.playerId, todayStr) }
            if (!local && b.cards.size >= 2) item { TournamentBoard(b, meId, todayStr) }
        } else if (groups != null) {
            if (!hasAccess) item { UsageAccessCard(context) }
            val gs = groups.orEmpty()
            if (local) {
                item { CenterButton("Tee off") { scope.launch { runCatching { prefs.startSoloRound() }.onFailure { error = it.message }; refreshKey++ } } }
            } else if (gs.isNotEmpty()) {
                item {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("No round is on.")
                        Button(onClick = {
                            scope.launch {
                                runCatching { prefs.backend().createCourse(gs.first().id, "The ${gs.first().name} Open", LocalDate.now(), 0, 0) }
                                    .onFailure { error = it.message }
                                refreshKey++
                            }
                        }) { Text("Start a round") }
                    }
                }
            } else {
                item {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("You are not in a group yet.")
                        Button(onClick = { nav.push(Screen.GroupFlow(true)) }) { Text("Join with a code") }
                        OutlinedButton(onClick = { nav.push(Screen.GroupFlow(false)) }) { Text("Make a group") }
                    }
                }
            }
        }
    }
}

@Composable
private fun CenterButton(text: String, onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Button(onClick = onClick) { Text(text) } }
}

// ---- group flow -----------------------------------------------------------------

/** Join or make a group on a server. Makes the player first, and keeps the key in memory for a retry. */
@Composable
fun GroupFlowScreen(prefs: Prefs, startJoin: Boolean, onBack: () -> Unit, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    val hasToken = remember { prefs.token != null }
    var joining by remember { mutableStateOf(startJoin) }
    var name by remember { mutableStateOf("") }
    var text by remember { mutableStateOf("") }
    var handicap by remember { mutableStateOf("60") }
    var server by remember { mutableStateOf(prefs.serverUrl) }
    var key by remember { mutableStateOf("") }
    var advanced by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var createdToken by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        if (prefs.hasSolo) runCatching {
            val b = LocalBackend(prefs.localFile)
            name = b.me().name.takeIf { it != "Me" }.orEmpty()
            handicap = b.handicap(LocalBackend.GROUP_ID).current.toString()
        }
    }

    val useKey = !hasToken && key.isNotBlank()
    val ready = server.isNotBlank() && (useKey || (text.isNotBlank() && handicap.isNotBlank() && (hasToken || name.isNotBlank())))

    AppScaffoldPlain("Play with a group", onBack) {
        Text("Groups need a Phone Golf server. Your solo card stays on this phone.", style = MaterialTheme.typography.bodySmall)
        if (!hasToken) {
            OutlinedTextField(name, { name = it }, label = { Text("Your name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (joining) Button(onClick = {}) { Text("Join with a code") } else OutlinedButton(onClick = { joining = true; text = "" }) { Text("Join with a code") }
            if (!joining) Button(onClick = {}) { Text("Make a group") } else OutlinedButton(onClick = { joining = false; text = "" }) { Text("Make a group") }
        }
        if (!useKey) {
            OutlinedTextField(
                text, { text = if (joining) it.uppercase() else it },
                label = { Text(if (joining) "Invite code" else "Group name") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                handicap, { handicap = it.filter(Char::isDigit) }, label = { Text("Handicap") }, singleLine = true,
                keyboardOptions = num, modifier = Modifier.fillMaxWidth(),
            )
        }
        TextButton(onClick = { advanced = !advanced }) { Text(if (advanced) "Hide advanced" else "Advanced") }
        if (advanced) {
            OutlinedTextField(server, { server = it }, label = { Text("Server URL") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            if (!hasToken) {
                OutlinedTextField(key, { key = it }, label = { Text("I have a player key") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text("With a key, you sign in as that player. No name or code is needed.", style = MaterialTheme.typography.bodySmall)
            }
        }
        ErrorText(error)
        Button(
            enabled = !busy && ready,
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                busy = true; error = null
                scope.launch {
                    try {
                        val url = server.trim().trimEnd('/')
                        val token = when {
                            hasToken -> prefs.token!!
                            useKey -> key.trim().also { Api(url, it).me() }
                            else -> createdToken ?: Api(url, null).createPlayer(name.trim()).second.also { createdToken = it }
                        }
                        if (!useKey) {
                            val api = Api(url, token)
                            if (joining) api.joinGroup(text.trim(), handicap.toInt()) else api.createGroup(text.trim(), handicap.toInt())
                        }
                        prefs.serverUrl = url
                        prefs.signInToServer(token)
                        onDone()
                    } catch (e: Exception) {
                        error = if (e is ApiException) e.message else "Could not reach the server: ${e.message}"
                    } finally { busy = false }
                }
            },
        ) { Text(if (useKey) "Sign in" else if (joining) "Join" else "Make group") }
        TextButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Back") }
    }
}

@Composable
private fun AppScaffoldPlain(title: String, onBack: () -> Unit, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Scaffold { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 28.sp, color = MaterialTheme.colorScheme.primary)
            content()
        }
    }
}

// ---- group -----------------------------------------------------------------

@Composable
fun GroupScreen(prefs: Prefs, nav: Nav, groupId: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var group by remember { mutableStateOf<Group?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var showCourse by remember { mutableStateOf(false) }
    var confirmLeave by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        try { group = prefs.backend().group(groupId) } catch (e: Exception) { error = e.message }
    }

    val g = group
    AppScaffold(g?.name ?: "Group", nav) {
        item { ErrorText(error) }
        if (g == null) { item { Loading() }; return@AppScaffold }

        if (!prefs.isLocal) item {
            SectionCard("Invite code") {
                Text(g.inviteCode, fontFamily = FontFamily.Monospace, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                OutlinedButton(onClick = {
                    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(
                        Intent.EXTRA_TEXT,
                        "Play Phone Golf with me. Fewest phone unlocks wins. Join \"${g.name}\" with code ${g.inviteCode}",
                    )
                    context.startActivity(Intent.createChooser(send, "Share invite"))
                }) { Text("Share invite") }
            }
        }

        if (!prefs.isLocal) item {
            SectionCard("Players") {
                g.members.forEach { m ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(m.name + if (m.id == g.ownerId) " (owner)" else "")
                        Text("handicap ${m.handicap} · ${m.platform}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        item {
            SectionCard("Courses") {
                if (g.courses.isEmpty()) Text("No courses yet. A course is 18 days in a row.")
                g.courses.forEach { c ->
                    Row(
                        Modifier.fillMaxWidth().clickable { nav.push(Screen.Course(c.id)) }.padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column {
                            Text(c.name, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                            Text("${fmt(c.startDate)} – ${fmt(c.endDate)}", style = MaterialTheme.typography.bodySmall)
                        }
                        Text(if (c.status == "active") "hole ${c.currentHole}" else c.status, style = MaterialTheme.typography.labelMedium)
                    }
                }
                OutlinedButton(onClick = { showCourse = true }) { Text("New course") }
            }
        }

        if (!prefs.isLocal) item { TextButton(onClick = { confirmLeave = true }) { Text("Leave group") } }
    }

    if (showCourse) {
        NewCourseDialog(
            groupName = g?.name.orEmpty(),
            onDismiss = { showCourse = false },
            onSubmit = { name, start, weekday, weekend ->
                scope.launch {
                    try {
                        val c = prefs.backend().createCourse(groupId, name, start, weekday, weekend)
                        showCourse = false
                        nav.push(Screen.Course(c.id))
                    } catch (e: Exception) { error = e.message; showCourse = false }
                }
            },
        )
    }
    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text("Leave ${g?.name}?") },
            text = { Text("Your scores stay, but you leave the leaderboard.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        runCatching { prefs.backend().leaveGroup(groupId) }
                        confirmLeave = false
                        nav.reset()
                    }
                }) { Text("Leave") }
            },
            dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun NewCourseDialog(groupName: String, onDismiss: () -> Unit, onSubmit: (String, LocalDate, Int, Int) -> Unit) {
    var name by remember { mutableStateOf("The $groupName Open") }
    var start by remember { mutableStateOf(LocalDate.now().toString()) }
    var weekday by remember { mutableStateOf("0") }
    var weekend by remember { mutableStateOf("0") }
    val startDate = runCatching { LocalDate.parse(start) }.getOrNull()
    val num = KeyboardOptions(keyboardType = KeyboardType.Number)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New course") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true)
                OutlinedTextField(
                    start, { start = it }, label = { Text("First day (YYYY-MM-DD)") }, singleLine = true,
                    isError = startDate == null,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(weekday, { weekday = it }, label = { Text("Weekday par") }, singleLine = true, keyboardOptions = num, modifier = Modifier.weight(1f))
                    OutlinedTextField(weekend, { weekend = it }, label = { Text("Weekend par") }, singleLine = true, keyboardOptions = num, modifier = Modifier.weight(1f))
                }
                Text(
                    "A course is 18 days in a row. Par is how many unlocks above your handicap count as even. Par 0 means: stay at your baseline.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && startDate != null && weekday.toIntOrNull() != null && weekend.toIntOrNull() != null,
                onClick = { onSubmit(name.trim(), startDate!!, weekday.toInt(), weekend.toInt()) },
            ) { Text("Make course") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

// ---- course -----------------------------------------------------------------

@Composable
private fun ToPar(n: Int?, modifier: Modifier = Modifier) {
    val color = when {
        n == null || n == 0 -> MaterialTheme.colorScheme.onSurfaceVariant
        n < 0 -> UnderPar
        else -> OverPar
    }
    Text(formatToPar(n), color = color, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, modifier = modifier)
}

@Composable
fun CourseScreen(prefs: Prefs, nav: Nav, courseId: String) {
    var board by remember { mutableStateOf<Leaderboard?>(null) }
    var meId by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(courseId) {
        try {
            val backend = prefs.backend()
            board = backend.leaderboard(courseId)
            meId = runCatching { backend.me().id }.getOrNull()
        } catch (e: Exception) { error = e.message }
    }
    val b = board
    val today = LocalDate.now().toString()
    AppScaffold(b?.course?.name ?: "Course", nav) {
        item { ErrorText(error) }
        if (b == null) { item { Loading() }; return@AppScaffold }
        item { TournamentBoard(b, meId, today) }
        item { PaperScorecard(b, meId, today) }
    }
}


// ---- settings -----------------------------------------------------------------

@Composable
fun SettingsScreen(prefs: Prefs, nav: Nav, onChanged: () -> Unit) {
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val local = prefs.isLocal
    var name by remember { mutableStateOf("") }
    var server by remember { mutableStateOf(prefs.serverUrl) }
    var message by remember { mutableStateOf<String?>(null) }
    var confirmOut by remember { mutableStateOf(false) }
    var groups by remember { mutableStateOf<List<Group>>(emptyList()) }
    var hcpGroup by remember { mutableStateOf<String?>(null) }
    var hcp by remember { mutableStateOf<HandicapInfo?>(null) }
    var showHcp by remember { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { runCatching { name = prefs.backend().me().name } }
    LaunchedEffect(reload) {
        try {
            val be = prefs.backend()
            val gs = be.groups()
            groups = gs
            val gid = if (local) LocalBackend.GROUP_ID else featuredIn(gs)?.first?.id ?: gs.firstOrNull()?.id
            hcpGroup = gid
            hcp = gid?.let { be.handicap(it) }
        } catch (e: Exception) { message = e.message }
    }

    AppScaffold("Settings", nav) {
        val h = hcp
        val gid = hcpGroup
        if (h != null && gid != null) item {
            SectionCard("Your handicap: ${h.current}") {
                if (h.suggestion != null) {
                    Text("Suggested: ${h.suggestion.value}. ${h.suggestion.explanation}")
                    if (h.suggestion.value != h.current) {
                        Button(onClick = {
                            scope.launch {
                                runCatching { prefs.backend().setHandicap(gid, h.suggestion.value, "suggestion") }
                                    .onFailure { message = it.message }
                                reload++
                            }
                        }) { Text("Use ${h.suggestion.value}") }
                    }
                } else {
                    Text("Score ${h.needsDays} more day(s) to get a suggested handicap.")
                }
                TextButton(onClick = { showHcp = true }) { Text("Set a different number") }
                Text("Handicap changes start tomorrow.", style = MaterialTheme.typography.bodySmall)
            }
        }
        item {
            SectionCard("Name") {
                OutlinedTextField(name, { name = it }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Button(enabled = name.isNotBlank(), onClick = {
                    scope.launch {
                        message = runCatching { prefs.backend().rename(name.trim()) }.fold({ "Saved." }, { it.message })
                    }
                }) { Text("Save") }
            }
        }
        item {
            SectionCard("Play with a group") {
                if (local) {
                    if (prefs.token != null) {
                        Button(onClick = { prefs.mode = "server"; onChanged() }) { Text("Back to group play") }
                    } else {
                        Button(onClick = { nav.push(Screen.GroupFlow(true)) }) { Text("Play with a group") }
                        Text("Groups need a Phone Golf server. Your solo card stays on this phone.", style = MaterialTheme.typography.bodySmall)
                    }
                } else {
                    groups.forEach { g ->
                        TextButton(onClick = { nav.push(Screen.Group(g.id)) }) { Text("Group: ${g.name}") }
                    }
                    OutlinedButton(onClick = { nav.push(Screen.GroupFlow(true)) }) { Text("Join another group") }
                    OutlinedButton(onClick = { nav.push(Screen.GroupFlow(false)) }) { Text("Make a group") }
                    OutlinedButton(onClick = {
                        clipboard.setText(AnnotatedString(prefs.token.orEmpty()))
                        message = "Player key copied."
                    }) { Text("Copy player key") }
                    Text("This key is your login. Paste it on another device to play as the same person. Keep it private.", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(server, { server = it }, label = { Text("Server URL") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedButton(onClick = { prefs.serverUrl = server; message = "Server saved." }) { Text("Save server") }
                    Button(onClick = {
                        scope.launch {
                            runCatching {
                                if (prefs.hasSolo) prefs.mode = "local"
                                else {
                                    prefs.startLocal(name.trim().ifBlank { "Me" }, hcp?.current ?: 60)
                                    prefs.startSoloRound()
                                }
                            }.onSuccess { onChanged() }.onFailure { message = it.message }
                        }
                    }) { Text("Play solo again") }
                }
            }
        }
        val past = groups.flatMap { it.courses }.filter { it.status == "finished" }.sortedByDescending { it.startDate }
        if (past.isNotEmpty()) item {
            SectionCard("Past rounds") {
                past.forEach { c ->
                    Column(Modifier.fillMaxWidth().clickable { nav.push(Screen.Course(c.id)) }.padding(vertical = 6.dp)) {
                        Text(c.name, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                        Text("${fmt(c.startDate)} – ${fmt(c.endDate)}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        item { message?.let { Text(it) } }
        item { TextButton(onClick = { confirmOut = true }) { Text(if (local) "Start over" else "Sign out") } }
    }

    if (showHcp) {
        var value by remember { mutableStateOf(hcp?.current?.toString().orEmpty()) }
        AlertDialog(
            onDismissRequest = { showHcp = false },
            title = { Text("Set handicap") },
            text = {
                OutlinedTextField(value, { value = it.filter(Char::isDigit) }, label = { Text("Handicap") }, singleLine = true, keyboardOptions = num)
            },
            confirmButton = {
                TextButton(enabled = value.isNotBlank() && hcpGroup != null, onClick = {
                    scope.launch {
                        runCatching { prefs.backend().setHandicap(hcpGroup!!, value.toInt(), "manual") }.onFailure { message = it.message }
                        showHcp = false
                        reload++
                    }
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showHcp = false }) { Text("Cancel") } },
        )
    }
    if (confirmOut) {
        AlertDialog(
            onDismissRequest = { confirmOut = false },
            title = { Text(if (local) "Start over?" else "Sign out?") },
            text = {
                Text(
                    if (local) "This deletes your solo scores, handicap and rounds from this phone. It also signs you out of any group."
                    else "Copy your player key first. Without it, you cannot get this player back.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (local) prefs.startOver() else prefs.signOut()
                    confirmOut = false
                    onChanged()
                }) { Text(if (local) "Delete and start over" else "Sign out") }
            },
            dismissButton = { TextButton(onClick = { confirmOut = false }) { Text("Cancel") } },
        )
    }
}
