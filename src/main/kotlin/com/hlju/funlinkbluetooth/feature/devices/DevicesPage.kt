package com.hlju.funlinkbluetooth.feature.devices

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.hlju.funlinkbluetooth.core.designsystem.navigation.PageScaffold
import com.hlju.funlinkbluetooth.core.designsystem.theme.miSansTextStyles
import com.hlju.funlinkbluetooth.core.designsystem.token.Corners
import com.hlju.funlinkbluetooth.core.designsystem.token.Spacing
import com.hlju.funlinkbluetooth.core.designsystem.token.adaptivePageHorizontalPadding
import com.hlju.funlinkbluetooth.core.designsystem.widget.SectionTitle
import com.hlju.funlinkbluetooth.core.designsystem.widget.StateMessageCard
import com.hlju.funlinkbluetooth.core.designsystem.widget.StatusBadge
import com.hlju.funlinkbluetooth.core.designsystem.widget.SurfaceTone
import com.hlju.funlinkbluetooth.core.designsystem.widget.qualityColor
import com.hlju.funlinkbluetooth.core.designsystem.widget.qualityLabel
import com.hlju.funlinkbluetooth.core.model.ConnectionRole
import com.hlju.funlinkbluetooth.core.model.ConnectionStatus
import com.hlju.funlinkbluetooth.core.model.NearbyEndpointInfo
import com.hlju.funlinkbluetooth.core.nearby.runtime.PendingConnectionInfo
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.ProgressIndicatorDefaults
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Link
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.icon.extended.Scan
import top.yukonga.miuix.kmp.icon.extended.SearchDevice
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

private const val RoleSwitchDurationMillis = 320
private const val RoleSwitchSwapProgress = 0.5f

private data class DevicesContentSnapshot(
    val role: ConnectionRole,
    val status: ConnectionStatus,
    val isAdvertising: Boolean,
    val isDiscovering: Boolean,
    val connectedList: List<NearbyEndpointInfo>,
    val discoveredList: List<NearbyEndpointInfo>,
    val pendingList: List<PendingConnectionInfo>,
    val endpointBandwidth: Map<String, Int>,
    val connectedRoomName: String,
    val connectedCount: Int,
    val discoveredCount: Int,
    val pendingCount: Int,
    val bestLinkQuality: Int,
    val lastError: String?,
    val roomNameValue: String,
    val clientNameValue: String,
    val summary: String,
    val primaryActionText: String,
    val primaryActionEnabled: Boolean
) {
    val isHost: Boolean
        get() = role == ConnectionRole.HOST

    val isBusy: Boolean
        get() = status == ConnectionStatus.ADVERTISING ||
            status == ConnectionStatus.DISCOVERING ||
            status == ConnectionStatus.CONNECTING
}

@Composable
fun DevicesPage(
    controller: DevicesController,
    bottomInset: Dp,
    topBarActions: @Composable RowScope.() -> Unit = {}
) {
    val context = LocalContext.current
    val state by controller.state.collectAsState()
    val isAdvertising = state.isAdvertising
    val isHost = state.role == ConnectionRole.HOST
    val isDiscovering = state.isDiscovering
    val connectedList = state.connectedEndpoints.toList().sortedBy { it.endpointName.lowercase() }
    val connectedById = connectedList.associateBy { it.endpointId }
    val discoveredList = state.discoveredEndpoints
        .filterNot { connectedById.containsKey(it.endpointId) }
        .sortedBy { it.endpointName.lowercase() }
    val connectedRoomName = connectedList.firstOrNull()?.endpointName.orEmpty()
    val bestLinkQuality = state.endpointBandwidth.values.minOfOrNull { it } ?: -1
    val discoveredCount = discoveredList.size
    val pendingCount = state.pendingConnections.size

    val currentPrimaryActionText = primaryActionTextForRole(
        role = state.role,
        isAdvertising = isAdvertising,
        isDiscovering = isDiscovering
    )
    val currentPrimaryActionEnabled = primaryActionEnabledForRole(
        role = state.role,
        isAdvertising = isAdvertising,
        isDiscovering = isDiscovering,
        isStartingAdvertising = state.isStartingAdvertising,
        isStartingDiscovery = state.isStartingDiscovery,
        pendingStartHost = controller.pendingStartHost,
        pendingStartScan = controller.pendingStartScan
    )
    val contentSnapshot = DevicesContentSnapshot(
        role = state.role,
        status = state.status,
        isAdvertising = isAdvertising,
        isDiscovering = isDiscovering,
        connectedList = connectedList,
        discoveredList = discoveredList,
        pendingList = state.pendingConnections,
        endpointBandwidth = state.endpointBandwidth,
        connectedRoomName = connectedRoomName,
        connectedCount = connectedList.size,
        discoveredCount = discoveredCount,
        pendingCount = pendingCount,
        bestLinkQuality = bestLinkQuality,
        lastError = state.lastError,
        roomNameValue = controller.roomNameInput,
        clientNameValue = controller.clientNameInput,
        summary = statusSummary(
            status = state.status,
            isHost = isHost,
            isAdvert = isAdvertising,
            roomName = controller.roomNameInput,
            connectedRoomName = connectedRoomName
        ),
        primaryActionText = currentPrimaryActionText,
        primaryActionEnabled = currentPrimaryActionEnabled
    )

    val statusAccent by animateColorAsState(
        targetValue = statusColor(contentSnapshot.status),
        animationSpec = spring(),
        label = "statusAccent"
    )
    val qualityAccent by animateColorAsState(
        targetValue = qualityColor(contentSnapshot.bestLinkQuality),
        animationSpec = spring(),
        label = "qualityAccent"
    )

    val requiredPermissions = remember {
        arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.NEARBY_WIFI_DEVICES
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        controller.onPermissionsResult(grants.all { it.value })
    }

    LaunchedEffect(requiredPermissions) {
        if (!controller.checkPermissions(context, requiredPermissions)) {
            permissionLauncher.launch(requiredPermissions)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            controller.stopDiscovery()
        }
    }

    val scrollBehavior = MiuixScrollBehavior()
    val pageHorizontalPadding = adaptivePageHorizontalPadding(Spacing.PageOuterInset)

    PageScaffold(
        title = "趣连蓝牙",
        scrollBehavior = scrollBehavior,
        actions = topBarActions,
    ) { innerPadding, contentModifier ->
        DevicesPageContent(
            content = contentSnapshot,
            contentModifier = contentModifier,
            contentPadding = PaddingValues(
                start = pageHorizontalPadding,
                top = innerPadding.calculateTopPadding() + Spacing.PageSectionGap,
                end = pageHorizontalPadding,
                bottom = innerPadding.calculateBottomPadding() + bottomInset + Spacing.PageSectionGap
            ),
            scrollBehavior = scrollBehavior,
            statusAccent = statusAccent,
            qualityAccent = qualityAccent,
            actionsEnabled = contentSnapshot.role == state.role,
            onRoomNameChange = controller::updateRoomName,
            onClientNameChange = controller::updateClientName,
            onPrimaryAction = {
                if (state.role == ConnectionRole.HOST) {
                    if (isAdvertising) {
                        controller.stopHostBroadcast()
                    } else {
                        controller.startHostBroadcast()
                        if (controller.pendingStartHost) permissionLauncher.launch(requiredPermissions)
                    }
                } else {
                    if (isDiscovering) {
                        controller.stopClientScan()
                    } else {
                        controller.startClientScan()
                        if (controller.pendingStartScan) permissionLauncher.launch(requiredPermissions)
                    }
                }
            },
            onConnect = controller::requestConnectionToEndpoint
        )

        val acceptTarget = state.pendingConnections.firstOrNull()
        ConfirmationDialog(
            show = acceptTarget != null,
            title = "确认接受连接",
            summary = if (acceptTarget != null) {
                "是否接受来自 ${acceptTarget.endpointName} 的连接请求？"
            } else {
                "是否接受此连接请求？"
            },
            onDismissRequest = {
                val endpointId = acceptTarget?.endpointId ?: return@ConfirmationDialog
                controller.rejectIncomingConnection(endpointId)
            },
        ) {
            DialogActionRow(
                cancelText = "取消",
                confirmText = "接受连接",
                onCancel = {
                    val endpointId = acceptTarget?.endpointId ?: return@DialogActionRow
                    controller.rejectIncomingConnection(endpointId)
                },
                onConfirm = {
                    val endpointId = acceptTarget?.endpointId ?: return@DialogActionRow
                    controller.acceptIncomingConnection(endpointId)
                }
            )
        }

    }
}

@Composable
private fun DevicesPageContent(
    content: DevicesContentSnapshot,
    contentModifier: Modifier,
    contentPadding: PaddingValues,
    scrollBehavior: ScrollBehavior?,
    statusAccent: Color,
    qualityAccent: Color,
    actionsEnabled: Boolean,
    onRoomNameChange: (String) -> Unit,
    onClientNameChange: (String) -> Unit,
    onPrimaryAction: () -> Unit,
    onConnect: (String) -> Unit
) {
    LazyColumn(
        modifier = contentModifier
            .fillMaxWidth()
            .fillMaxHeight()
            .scrollEndHaptic()
            .overScrollVertical()
            .then(
                if (scrollBehavior != null) {
                    Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
                } else {
                    Modifier
                }
            ),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(Spacing.PageSectionGap),
        overscrollEffect = null
    ) {
        item {
            DevicesModeTransition(
                content = content,
                actionsEnabled = actionsEnabled,
                statusAccent = statusAccent,
                qualityAccent = qualityAccent,
                onRoomNameChange = onRoomNameChange,
                onClientNameChange = onClientNameChange,
                onPrimaryAction = onPrimaryAction,
                onConnect = onConnect
            )
        }

    }
}

@Composable
private fun DevicesModeTransition(
    content: DevicesContentSnapshot,
    actionsEnabled: Boolean,
    statusAccent: Color,
    qualityAccent: Color,
    onRoomNameChange: (String) -> Unit,
    onClientNameChange: (String) -> Unit,
    onPrimaryAction: () -> Unit,
    onConnect: (String) -> Unit
) {
    var activeContent by remember { mutableStateOf(content) }
    var isSwitchingRole by remember { mutableStateOf(false) }
    var isErasingOldRole by remember { mutableStateOf(false) }
    val latestContent by rememberUpdatedState(content)
    val switchProgress = remember { Animatable(1f) }

    LaunchedEffect(content.role) {
        if (content.role != activeContent.role) {
            isSwitchingRole = true
            isErasingOldRole = true
            switchProgress.snapTo(0f)
            switchProgress.animateTo(
                targetValue = RoleSwitchSwapProgress,
                animationSpec = tween(
                    durationMillis = (RoleSwitchDurationMillis * RoleSwitchSwapProgress).toInt(),
                    easing = FastOutSlowInEasing
                )
            )
            activeContent = latestContent
            isErasingOldRole = false
            switchProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = (RoleSwitchDurationMillis * (1f - RoleSwitchSwapProgress)).toInt(),
                    easing = FastOutSlowInEasing
                )
            )
            activeContent = latestContent
            isSwitchingRole = false
        }
    }

    LaunchedEffect(content) {
        if (!isSwitchingRole && content.role == activeContent.role) {
            activeContent = content
        }
    }

    val contentAlpha = roleSwitchContentAlpha(
        progress = switchProgress.value,
        isErasingOldRole = isErasingOldRole,
        isSwitchingRole = isSwitchingRole
    )

    Box(
        modifier = Modifier.fillMaxWidth()
    ) {
        DevicesModeContent(
            content = activeContent,
            actionsEnabled = actionsEnabled && !isSwitchingRole,
            statusAccent = statusAccent,
            qualityAccent = qualityAccent,
            onRoomNameChange = onRoomNameChange,
            onClientNameChange = onClientNameChange,
            onPrimaryAction = onPrimaryAction,
            onConnect = onConnect,
            modifier = Modifier.graphicsLayer { alpha = contentAlpha }
        )
    }
}

private fun roleSwitchContentAlpha(
    progress: Float,
    isErasingOldRole: Boolean,
    isSwitchingRole: Boolean
): Float {
    if (!isSwitchingRole) return 1f
    return if (isErasingOldRole) {
        1f - (progress / RoleSwitchSwapProgress).coerceIn(0f, 1f)
    } else {
        ((progress - RoleSwitchSwapProgress) / (1f - RoleSwitchSwapProgress)).coerceIn(0f, 1f)
    }
}

@Composable
private fun DevicesModeContent(
    content: DevicesContentSnapshot,
    actionsEnabled: Boolean,
    statusAccent: Color,
    qualityAccent: Color,
    onRoomNameChange: (String) -> Unit,
    onClientNameChange: (String) -> Unit,
    onPrimaryAction: () -> Unit,
    onConnect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.PageSectionGap)
    ) {
        ConnectionModeCard(
            content = content,
            actionsEnabled = actionsEnabled,
            statusAccent = statusAccent,
            onRoomNameChange = onRoomNameChange,
            onClientNameChange = onClientNameChange,
            onPrimaryAction = onPrimaryAction
        )

        ConnectionStatusStrip(
            content = content,
            statusAccent = statusAccent,
            qualityAccent = qualityAccent
        )

        SectionTitle(
            title = if (content.isHost) "连接请求" else "附近房间",
            summary = if (content.isHost) {
                "已连接设备和待确认请求会显示在这里。"
            } else if (content.isDiscovering) {
                "正在查找可加入的房间。"
            } else {
                "开始扫描后即可选择房间连接。"
            }
        )

        EndpointGroupCard(
            content = content,
            actionsEnabled = actionsEnabled,
            onConnect = onConnect
        )
    }
}

@Composable
private fun ConnectionModeCard(
    content: DevicesContentSnapshot,
    actionsEnabled: Boolean,
    statusAccent: Color,
    onRoomNameChange: (String) -> Unit,
    onClientNameChange: (String) -> Unit,
    onPrimaryAction: () -> Unit
) {
    val modeIcon = if (content.isHost) MiuixIcons.Link else MiuixIcons.SearchDevice
    val configTitle = if (content.isHost) "广播设置" else "加入设置"

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(Corners.PageShape),
        insideMargin = PaddingValues(Spacing.PageCardPaddingLarge)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(Spacing.PageBase12)
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall)
            ) {
                Text(
                    text = configTitle,
                    style = MiuixTheme.textStyles.title3,
                    color = MiuixTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = content.summary,
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurface
                )
            }

            TextField(
                value = if (content.isHost) content.roomNameValue else content.clientNameValue,
                onValueChange = if (content.isHost) onRoomNameChange else onClientNameChange,
                modifier = Modifier.fillMaxWidth(),
                label = if (content.isHost) "房间标识" else "连接标识",
                singleLine = true
            )

            Text(
                text = if (content.isHost) {
                    "作为 Host 时，其他设备会通过这个房间标识找到你。"
                } else {
                    "作为 Client 时，这个连接标识会展示给 Host。"
                },
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onBackgroundVariant
            )

            AnimatedVisibility(
                visible = !content.lastError.isNullOrBlank(),
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Text(
                    text = content.lastError.orEmpty(),
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.error
                )
            }

            Button(
                onClick = onPrimaryAction,
                enabled = content.primaryActionEnabled && actionsEnabled,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColorsPrimary()
            ) {
                Icon(
                    imageVector = if (content.isBusy) MiuixIcons.Close else modeIcon,
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(Spacing.IconMedium)
                )
                Spacer(modifier = Modifier.width(Spacing.Medium))
                Text(text = content.primaryActionText)
            }
        }
    }
}

@Composable
private fun ModeGlyph(
    imageVector: ImageVector,
    tint: Color
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(Corners.nestedShape())
            .background(tint.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(Spacing.IconMedium)
        )
    }
}

@Composable
private fun ConnectionStatusStrip(
    content: DevicesContentSnapshot,
    statusAccent: Color,
    qualityAccent: Color
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(Corners.PageShape),
        insideMargin = PaddingValues(Spacing.PageCardPaddingLarge)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(Spacing.PageBase12)
        ) {
            if (content.isBusy) {
                LinearProgressIndicator(
                    progress = null,
                    colors = ProgressIndicatorDefaults.progressIndicatorColors(
                        foregroundColor = statusAccent,
                        backgroundColor = MiuixTheme.colorScheme.secondaryContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.PageBase12)
            ) {
                MetricColumn(
                    title = "状态",
                    value = statusHeadline(content.status),
                    summary = if (content.isHost) "Host" else "Client",
                    accent = statusAccent,
                    modifier = Modifier.weight(1f)
                )
                MetricColumn(
                    title = if (content.isHost) "设备" else "房间",
                    value = if (content.isHost) {
                        content.connectedCount.toString()
                    } else {
                        content.discoveredCount.toString()
                    },
                    summary = if (content.isHost) {
                        "待处理 ${content.pendingCount}"
                    } else if (content.connectedRoomName.isBlank()) {
                        "未连接"
                    } else {
                        "已连接"
                    },
                    accent = if (content.isHost && content.pendingCount > 0) {
                        MiuixTheme.colorScheme.error
                    } else {
                        MiuixTheme.colorScheme.onSurface
                    },
                    modifier = Modifier.weight(1f)
                )
                MetricColumn(
                    title = "质量",
                    value = qualityLabel(content.bestLinkQuality),
                    summary = if (content.bestLinkQuality > 0) "当前链路" else "等待评估",
                    accent = qualityAccent,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun MetricColumn(
    title: String,
    value: String,
    summary: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall)
    ) {
        Text(
            text = title,
            style = MiuixTheme.textStyles.footnote2,
            color = MiuixTheme.colorScheme.onBackgroundVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = value,
            style = MiuixTheme.textStyles.title3,
            color = accent,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = summary,
            style = MiuixTheme.textStyles.footnote2,
            color = MiuixTheme.colorScheme.onBackgroundVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun EndpointGroupCard(
    content: DevicesContentSnapshot,
    actionsEnabled: Boolean,
    onConnect: (String) -> Unit
) {
    if (content.isHost) {
        HostEndpointGroupCard(content = content)
    } else {
        ClientEndpointGroupCard(
            content = content,
            actionsEnabled = actionsEnabled,
            onConnect = onConnect
        )
    }
}

@Composable
private fun HostEndpointGroupCard(
    content: DevicesContentSnapshot
) {
    if (content.connectedList.isEmpty() && content.pendingList.isEmpty()) {
        StateMessageCard(
            title = "暂无连接",
            summary = "开始广播后，连接请求会在这里出现。",
            tone = SurfaceTone.Neutral
        )
        return
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(Corners.PageShape)
    ) {
        Column {
            content.pendingList.forEachIndexed { index, pending ->
                EndpointRow(
                    title = pending.endpointName,
                    subtitle = "等待确认连接",
                    imageVector = MiuixIcons.Scan,
                    iconTint = MiuixTheme.colorScheme.error,
                    trailingLabel = "待确认",
                    trailingTone = SurfaceTone.Warning,
                    onClick = null
                )
                if (index < content.pendingList.lastIndex || content.connectedList.isNotEmpty()) {
                    EndpointDivider()
                }
            }

            content.connectedList.forEachIndexed { index, endpoint ->
                val quality = content.endpointBandwidth[endpoint.endpointId] ?: endpoint.bandwidthQuality
                EndpointRow(
                    title = endpoint.endpointName,
                    subtitle = "设备已连接",
                    imageVector = MiuixIcons.Ok,
                    iconTint = qualityColor(quality),
                    trailingLabel = qualityLabel(quality),
                    trailingTone = if (quality > 0) SurfaceTone.Primary else SurfaceTone.Neutral,
                    onClick = null
                )
                if (index < content.connectedList.lastIndex) {
                    EndpointDivider()
                }
            }
        }
    }
}

@Composable
private fun ClientEndpointGroupCard(
    content: DevicesContentSnapshot,
    actionsEnabled: Boolean,
    onConnect: (String) -> Unit
) {
    if (content.discoveredList.isEmpty()) {
        StateMessageCard(
            title = if (content.isDiscovering) "正在扫描" else "暂无房间",
            summary = if (content.isDiscovering) {
                "保持当前页面，发现房间后会自动显示。"
            } else {
                "点击开始扫描，或确认另一台设备已经开始广播。"
            },
            tone = if (content.isDiscovering) SurfaceTone.Primary else SurfaceTone.Neutral
        )
        return
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(Corners.PageShape)
    ) {
        Column {
            content.discoveredList.forEachIndexed { index, endpoint ->
                EndpointRow(
                    title = endpoint.endpointName,
                    subtitle = "点击发起连接",
                    imageVector = MiuixIcons.SearchDevice,
                    iconTint = MiuixTheme.colorScheme.primary,
                    trailingLabel = "连接",
                    trailingTone = SurfaceTone.Primary,
                    onClick = if (actionsEnabled) {
                        { onConnect(endpoint.endpointId) }
                    } else {
                        null
                    }
                )
                if (index < content.discoveredList.lastIndex) {
                    EndpointDivider()
                }
            }
        }
    }
}

@Composable
private fun EndpointRow(
    title: String,
    subtitle: String,
    imageVector: ImageVector,
    iconTint: Color,
    trailingLabel: String,
    trailingTone: SurfaceTone,
    onClick: (() -> Unit)?
) {
    val clickModifier = if (onClick != null) {
        Modifier.clickable(onClick = onClick)
    } else {
        Modifier
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(clickModifier)
            .padding(horizontal = Spacing.PageCardPaddingLarge, vertical = Spacing.PageCardPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.PageBase10)
    ) {
        ModeGlyph(
            imageVector = imageVector,
            tint = iconTint
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall)
        ) {
            Text(
                text = title,
                fontWeight = FontWeight.Medium,
                color = MiuixTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onBackgroundVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        StatusBadge(
            text = trailingLabel,
            tone = trailingTone
        )
    }
}

@Composable
private fun EndpointDivider() {
    HorizontalDivider(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = Spacing.DividerInsetStart)
    )
}

@Composable
private fun ConfirmationDialog(
    show: Boolean,
    title: String,
    summary: String,
    onDismissRequest: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (!show) return

    Dialog(onDismissRequest = onDismissRequest) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(Corners.PageShape)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = Spacing.ExtraLarge, vertical = Spacing.LargePlus),
                verticalArrangement = Arrangement.spacedBy(Spacing.PageBase10),
            ) {
                Text(
                    text = title,
                    style = MiuixTheme.textStyles.title3,
                    color = MiuixTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = summary,
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                )
                content()
            }
        }
    }
}

@Composable
private fun DialogActionRow(
    cancelText: String,
    confirmText: String,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
    destructiveConfirm: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Spacing.Small),
        horizontalArrangement = Arrangement.spacedBy(Spacing.Medium),
    ) {
        TextButton(
            text = cancelText,
            modifier = Modifier.weight(1f),
            onClick = onCancel,
        )
        TextButton(
            text = confirmText,
            modifier = Modifier.weight(1f),
            colors = if (destructiveConfirm) {
                ButtonDefaults.textButtonColors(
                    color = MiuixTheme.colorScheme.errorContainer,
                    textColor = MiuixTheme.colorScheme.error,
                )
            } else {
                ButtonDefaults.textButtonColors()
            },
            onClick = onConfirm,
        )
    }
}

@Composable
private fun statusColor(status: ConnectionStatus): Color {
    val primary = MiuixTheme.colorScheme.primary
    return when (status) {
        ConnectionStatus.ACTIVE -> primary
        ConnectionStatus.ERROR -> MiuixTheme.colorScheme.error
        ConnectionStatus.ADVERTISING -> primary.copy(alpha = 0.92f)
        ConnectionStatus.DISCOVERING -> primary.copy(alpha = 0.75f)
        ConnectionStatus.CONNECTING -> primary.copy(alpha = 0.86f)
        ConnectionStatus.IDLE -> MiuixTheme.colorScheme.onBackgroundVariant
    }
}

private fun statusHeadline(status: ConnectionStatus): String = when (status) {
    ConnectionStatus.IDLE -> "待机"
    ConnectionStatus.ADVERTISING -> "广播中"
    ConnectionStatus.DISCOVERING -> "扫描中"
    ConnectionStatus.CONNECTING -> "连接中"
    ConnectionStatus.ACTIVE -> "连接稳定"
    ConnectionStatus.ERROR -> "连接异常"
}

private fun primaryActionTextForRole(
    role: ConnectionRole,
    isAdvertising: Boolean,
    isDiscovering: Boolean
): String = when {
    role == ConnectionRole.HOST && isAdvertising -> "停止广播"
    role == ConnectionRole.HOST -> "开始广播"
    isDiscovering -> "停止扫描"
    else -> "开始扫描"
}

private fun primaryActionEnabledForRole(
    role: ConnectionRole,
    isAdvertising: Boolean,
    isDiscovering: Boolean,
    isStartingAdvertising: Boolean,
    isStartingDiscovery: Boolean,
    pendingStartHost: Boolean,
    pendingStartScan: Boolean
): Boolean = when {
    role == ConnectionRole.HOST && isAdvertising -> true
    role == ConnectionRole.HOST -> !isStartingAdvertising && !pendingStartHost
    isDiscovering -> true
    else -> !isStartingDiscovery && !pendingStartScan
}

private fun statusSummary(
    status: ConnectionStatus,
    isHost: Boolean,
    isAdvert: Boolean,
    roomName: String,
    connectedRoomName: String
): String = when {
    status == ConnectionStatus.ERROR -> "连接异常，请检查权限、蓝牙和附近设备状态。"
    status == ConnectionStatus.CONNECTING -> "正在建立连接，请保持设备靠近并稍候。"
    isHost && isAdvert -> "房间 ${roomName.ifBlank { "未命名房间" }} 正在广播，等待其他设备加入。"
    isHost -> "填写房间标识后即可开始广播。"
    connectedRoomName.isNotBlank() -> "已连接到房间 $connectedRoomName，可直接进入插件联调。"
    status == ConnectionStatus.DISCOVERING -> "正在搜索附近房间，发现后即可点击加入。"
    else -> "填写连接标识后即可开始扫描。"
}

private fun previewSnapshot(
    role: ConnectionRole,
    status: ConnectionStatus,
    connectedList: List<NearbyEndpointInfo> = emptyList(),
    discoveredList: List<NearbyEndpointInfo> = emptyList(),
    pendingList: List<PendingConnectionInfo> = emptyList(),
    endpointBandwidth: Map<String, Int> = emptyMap(),
    roomName: String = "HLJU Lab",
    clientName: String = "cyzi7-phone",
    lastError: String? = null
): DevicesContentSnapshot {
    val connectedRoomName = connectedList.firstOrNull()?.endpointName.orEmpty()
    val bestLinkQuality = endpointBandwidth.values.minOfOrNull { it } ?: -1
    val isHost = role == ConnectionRole.HOST
    return DevicesContentSnapshot(
        role = role,
        status = status,
        isAdvertising = status == ConnectionStatus.ADVERTISING,
        isDiscovering = status == ConnectionStatus.DISCOVERING,
        connectedList = connectedList,
        discoveredList = discoveredList,
        pendingList = pendingList,
        endpointBandwidth = endpointBandwidth,
        connectedRoomName = connectedRoomName,
        connectedCount = connectedList.size,
        discoveredCount = discoveredList.size,
        pendingCount = pendingList.size,
        bestLinkQuality = bestLinkQuality,
        lastError = lastError,
        roomNameValue = roomName,
        clientNameValue = clientName,
        summary = statusSummary(
            status = status,
            isHost = isHost,
            isAdvert = status == ConnectionStatus.ADVERTISING,
            roomName = roomName,
            connectedRoomName = connectedRoomName
        ),
        primaryActionText = primaryActionTextForRole(
            role = role,
            isAdvertising = status == ConnectionStatus.ADVERTISING,
            isDiscovering = status == ConnectionStatus.DISCOVERING
        ),
        primaryActionEnabled = true
    )
}

@Composable
private fun DevicesPreviewSurface(
    content: DevicesContentSnapshot,
    dark: Boolean = false
) {
    MiuixTheme(
        colors = if (dark) darkColorScheme() else lightColorScheme(),
        textStyles = miSansTextStyles()
    ) {
        DevicesPageContent(
            content = content,
            contentModifier = Modifier
                .fillMaxSize()
                .background(MiuixTheme.colorScheme.background),
            contentPadding = PaddingValues(Spacing.PageOuterInset),
            scrollBehavior = null,
            statusAccent = statusColor(content.status),
            qualityAccent = qualityColor(content.bestLinkQuality),
            actionsEnabled = true,
            onRoomNameChange = {},
            onClientNameChange = {},
            onPrimaryAction = {},
            onConnect = {}
        )
    }
}
