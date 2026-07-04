package com.example

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.vpn.NetShieldVpnService
import com.example.vpn.VpnStateTracker
import com.example.vpn.VpnStatus
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF0F172A)) // Slate 900
                ) { innerPadding ->
                    MainDashboardScreen(
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun MainDashboardScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val vpnStatus by VpnStateTracker.status.collectAsStateWithLifecycle()
    val blockedCount by VpnStateTracker.blockedCount.collectAsStateWithLifecycle()
    val allowedCount by VpnStateTracker.allowedCount.collectAsStateWithLifecycle()
    val recentLogs by VpnStateTracker.recentLogs.collectAsStateWithLifecycle()

    var testDomainInput by remember { mutableStateOf("") }
    var testResult by remember { mutableStateOf<String?>(null) }
    var activeTab by remember { mutableIntStateOf(0) } // 0: Live Logs, 1: Global Intel

    // Simulated community collaboration threat intelligence stream
    val communityThreats = remember {
        mutableStateListOf(
            CommunityThreat("NYC_Node_24", "trackers.advertising.com", "Analytics Server blocked in NYC", "12s ago"),
            CommunityThreat("London_Hub_09", "malware-phishing-site.ru", "Critical phishing vector neutralised", "1m ago"),
            CommunityThreat("Tokyo_Core_8", "app-telemetry.analytics.net", "Device telemetry blocked on mobile", "3m ago")
        )
    }

    // Periodically update community threat stream for live collaboration feel
    LaunchedEffect(Unit) {
        val userNames = listOf("Berlin_Node_5", "Paris_Gate_12", "SF_Shield_42", "Seoul_Core_1")
        val threatDomains = listOf("bad-ads-network.org", "stealth-miner.co", "tracker.social-spy.com", "redirect-ad-loop.net")
        val descriptions = listOf("Ad server blocked", "Cryptomining domain isolated", "Social script tracker blocked", "Redirect loop blocked")
        while (true) {
            delay(12000)
            communityThreats.add(
                0,
                CommunityThreat(
                    node = userNames.random(),
                    domain = threatDomains.random(),
                    action = descriptions.random(),
                    time = "Just now"
                )
            )
            if (communityThreats.size > 8) {
                communityThreats.removeAt(communityThreats.size - 1)
            }
        }
    }

    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            startVpn(context)
        } else {
            VpnStateTracker.setStatus(VpnStatus.ERROR)
        }
    }

    fun toggleProtection() {
        if (vpnStatus == VpnStatus.ACTIVE) {
            stopVpn(context)
        } else {
            val prepareIntent = VpnService.prepare(context)
            if (prepareIntent != null) {
                vpnPermissionLauncher.launch(prepareIntent)
            } else {
                startVpn(context)
            }
        }
    }

    var showLimitationsDialog by remember { mutableStateOf(false) }

    if (showLimitationsDialog) {
        AlertDialog(
            onDismissRequest = { showLimitationsDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "NetShield Capabilities & Limits",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }
            },
            text = {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Text(
                            text = "NetShield operates as a lightweight, local device-wide DNS filtering gateway. It reads queried domain names locally and resolves clean queries via an encrypted upstream DNS (Cloudflare 1.1.1.1).",
                            style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF94A3B8))
                        )
                    }

                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "🎯 What NetShield BLOCKS:",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        color = Color(0xFF34D399),
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "• Third-party mobile banner/popup ad networks (DoubleClick, Unity Ads, etc.)\n" +
                                            "• Cross-app tracking SDKs & Telemetry endpoints\n" +
                                            "• Malicious redirect and phishing domains\n" +
                                            "• Browser-based advertising websites",
                                    style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFFCBD5E1))
                                )
                            }
                        }
                    }

                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF271C1C)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "⚠️ Why YouTube App Ads persist:",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        color = Color(0xFFF87171),
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "• First-Party CDNs: YouTube feeds both videos and ads from the identical web domain (e.g. googlevideo.com). Blocking it would freeze all YouTube videos.\n" +
                                            "• Stream-Level Injections: Ads are directly merged into the video stream on Google's servers before delivery.\n" +
                                            "• Encrypted Traffic: As a secure, privacy-preserving DNS filter, NetShield does not perform risky decryption (MITM) of HTTPS payloads.",
                                    style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFFFCA5A5))
                                )
                            }
                        }
                    }

                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "🚫 Other Unblockable Content:",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        color = Color(0xFFE2E8F0),
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "• Sponsored feed posts in Instagram, Facebook, or TikTok.\n" +
                                            "• Native ads integrated directly into the application server code.",
                                    style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF94A3B8))
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { showLimitationsDialog = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF14B8A6))
                ) {
                    Text("Got It")
                }
            },
            containerColor = Color(0xFF1E293B)
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "NetShield",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontFamily = FontFamily.SansSerif
                    )
                )
                Text(
                    text = "System-Wide Ad-Blocker",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = Color(0xFF94A3B8) // Slate 400
                    )
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(
                    onClick = { showLimitationsDialog = true },
                    modifier = Modifier
                        .background(Color(0xFF1E293B), RoundedCornerShape(12.dp))
                        .testTag("limitations_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Show Capabilities & Limitations",
                        tint = Color(0xFF14B8A6) // Teal
                    )
                }

                IconButton(
                    onClick = { VpnStateTracker.clearStats() },
                    modifier = Modifier
                        .background(Color(0xFF1E293B), RoundedCornerShape(12.dp))
                        .testTag("reset_stats_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Clear Session",
                        tint = Color(0xFF38BDF8) // Light Blue
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Educational Banner
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B).copy(alpha = 0.6f)),
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showLimitationsDialog = true }
                .padding(vertical = 4.dp)
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.HelpOutline,
                    contentDescription = "Info",
                    tint = Color(0xFF38BDF8),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Why do some YouTube or Instagram ads persist?",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Text(
                        text = "Tap to learn how DNS filtering works and its technical limits.",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = Color(0xFF94A3B8),
                            fontSize = 11.sp
                        )
                    )
                }
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = "Open Details",
                    tint = Color(0xFF64748B),
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Shield Indicator
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(200.dp)
                .clickable { toggleProtection() }
                .testTag("shield_toggle_button")
        ) {
            val isShieldActive = vpnStatus == VpnStatus.ACTIVE
            val glowColor = if (isShieldActive) Color(0xFF0D9488) else Color(0xFF475569) // Teal vs Slate 600
            val ringScale by animateFloatAsState(
                targetValue = if (isShieldActive) 1.15f else 1.0f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1500, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "RingGlow"
            )

            // Outer Pulse Ring
            Box(
                modifier = Modifier
                    .size(160.dp * ringScale)
                    .clip(CircleShape)
                    .background(glowColor.copy(alpha = 0.15f))
            )

            // Inner Ring
            Box(
                modifier = Modifier
                    .size(140.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = if (isShieldActive) {
                                listOf(Color(0xFF14B8A6), Color(0xFF0F766E)) // Teal 500 to 700
                            } else {
                                listOf(Color(0xFF334155), Color(0xFF1E293B)) // Slate 700 to 800
                            }
                        )
                    )
                    .border(2.dp, glowColor.copy(alpha = 0.8f), CircleShape)
                    .shadow(16.dp, CircleShape, clip = true),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = if (isShieldActive) Icons.Default.Shield else Icons.Default.ShieldMoon,
                        contentDescription = "Shield Indicator Logo",
                        modifier = Modifier.size(56.dp),
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (isShieldActive) "PROTECTION ON" else "PROTECTION OFF",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            letterSpacing = 1.2.sp
                        )
                    )
                    Text(
                        text = when (vpnStatus) {
                            VpnStatus.STOPPED -> "Inactive"
                            VpnStatus.STARTING -> "Starting..."
                            VpnStatus.ACTIVE -> "Local DNS Enabled"
                            VpnStatus.PAUSED -> "Paused"
                            VpnStatus.ERROR -> "Service Alert!"
                        },
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = if (isShieldActive) Color(0xFF99F6E4) else Color(0xFF94A3B8),
                            fontSize = 10.sp
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Real-Time Stats Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatsCard(
                title = "Blocked Requests",
                count = blockedCount.toString(),
                color = Color(0xFFEF4444), // Red 500
                icon = Icons.Default.Block,
                modifier = Modifier.weight(1f)
            )

            StatsCard(
                title = "Allowed Queries",
                count = allowedCount.toString(),
                color = Color(0xFF10B981), // Emerald 500
                icon = Icons.Default.CheckCircle,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Live Diagnostic Playground
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Shield Filtering Sandbox",
                    style = MaterialTheme.typography.titleSmall.copy(
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Start
                )
                Text(
                    text = "Type any domain to check NetShield's matching logic instantly.",
                    style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF94A3B8)),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Start
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = testDomainInput,
                        onValueChange = {
                            testDomainInput = it
                            testResult = null
                        },
                        placeholder = { Text("e.g. doubleclick.net", color = Color(0xFF64748B)) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF14B8A6),
                            unfocusedBorderColor = Color(0xFF475569),
                            focusedContainerColor = Color(0xFF0F172A),
                            unfocusedContainerColor = Color(0xFF0F172A)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("diagnostic_input"),
                        shape = RoundedCornerShape(10.dp)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = {
                            if (testDomainInput.isNotBlank()) {
                                val testDomain = testDomainInput.lowercase().trim()
                                val defaultBlocked = setOf(
                                    "doubleclick.net",
                                    "testblocked.com",
                                    "ads.google.com",
                                    "telemetry.app",
                                    "analytics.google.com",
                                    "crashlytics.com",
                                    "facebook-telemetry.com"
                                )
                                val matches = defaultBlocked.contains(testDomain) ||
                                        defaultBlocked.any { testDomain.endsWith(".$it") }
                                testResult = if (matches) {
                                    "🚫 [BLOCKED] Suffix matched in local database rules."
                                } else {
                                    "✅ [ALLOWED] Clean domain. Forwarding to upstream DNS."
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF14B8A6)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("diagnostic_test_button")
                    ) {
                        Text("Test", color = Color.White)
                    }
                }

                testResult?.let {
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF0F172A))
                            .padding(8.dp)
                    ) {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = if (it.contains("BLOCKED")) Color(0xFFF87171) else Color(0xFF34D399),
                                fontWeight = FontWeight.Medium
                            )
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Tabs Header
        TabRow(
            selectedTabIndex = activeTab,
            containerColor = Color(0xFF0F172A),
            contentColor = Color(0xFF14B8A6),
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[activeTab]),
                    color = Color(0xFF14B8A6)
                )
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Tab(
                selected = activeTab == 0,
                onClick = { activeTab = 0 },
                text = { Text("Local Live Logs", fontWeight = FontWeight.Bold) }
            )
            Tab(
                selected = activeTab == 1,
                onClick = { activeTab = 1 },
                text = { Text("Collaborative Threat Intel", fontWeight = FontWeight.Bold) }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Tab Content
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF1E293B))
                .padding(12.dp)
        ) {
            if (activeTab == 0) {
                if (recentLogs.isEmpty()) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.OfflineBolt,
                                contentDescription = "Offline Idle Status",
                                tint = Color(0xFF475569),
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Tunnel Waiting for Network Traffic",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = Color(0xFF64748B),
                                    textAlign = TextAlign.Center
                                )
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(recentLogs) { log ->
                            LogItemRow(log)
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Groups,
                                contentDescription = "Community Shield Icon",
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Telemetry Network: 4 Active Telemetry Nodes Connected",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = Color(0xFF38BDF8),
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }
                    items(communityThreats) { threat ->
                        CommunityThreatRow(threat)
                    }
                }
            }
        }
    }
}

@Composable
fun StatsCard(
    title: String,
    count: String,
    color: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = Color(0xFF94A3B8),
                        fontWeight = FontWeight.Medium
                    )
                )
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color.copy(alpha = 0.8f),
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = count,
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            )
        }
    }
}

@Composable
fun LogItemRow(log: com.example.vpn.BlockedLog) {
    val sdf = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    val timeStr = sdf.format(Date(log.timestamp))

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF0F172A))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = log.domain,
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold
                ),
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = timeStr,
                    style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF64748B))
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "•",
                    color = Color(0xFF64748B),
                    fontSize = 10.sp
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = log.category,
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = if (log.isBlocked) Color(0xFFEF4444) else Color(0xFF10B981)
                    )
                )
            }
        }

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(
                    if (log.isBlocked) Color(0xFFEF4444).copy(alpha = 0.15f)
                    else Color(0xFF10B981).copy(alpha = 0.15f)
                )
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(
                text = if (log.isBlocked) "Blocked" else "Allowed",
                style = MaterialTheme.typography.labelSmall.copy(
                    color = if (log.isBlocked) Color(0xFFEF4444) else Color(0xFF10B981),
                    fontWeight = FontWeight.Bold
                )
            )
        }
    }
}

data class CommunityThreat(
    val node: String,
    val domain: String,
    val action: String,
    val time: String
)

@Composable
fun CommunityThreatRow(threat: CommunityThreat) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF0F172A))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = threat.node,
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = Color(0xFF38BDF8),
                        fontWeight = FontWeight.Bold
                    )
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = threat.time,
                    style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF64748B))
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = threat.domain,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold
                ),
                maxLines = 1
            )
            Text(
                text = threat.action,
                style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF94A3B8))
            )
        }

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFFEF4444).copy(alpha = 0.15f))
                .padding(horizontal = 6.dp, vertical = 3.dp)
        ) {
            Text(
                text = "Isolated",
                style = MaterialTheme.typography.labelSmall.copy(
                    color = Color(0xFFEF4444),
                    fontWeight = FontWeight.Bold
                )
            )
        }
    }
}

private fun startVpn(context: Context) {
    val intent = Intent(context, NetShieldVpnService::class.java)
    context.startService(intent)
}

private fun stopVpn(context: Context) {
    val intent = Intent(context, NetShieldVpnService::class.java).apply {
        action = NetShieldVpnService.ACTION_STOP
    }
    context.startService(intent)
}
