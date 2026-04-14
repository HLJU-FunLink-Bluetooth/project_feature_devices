package com.hlju.funlinkbluetooth.feature.devices

import android.Manifest
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.hlju.funlinkbluetooth.core.designsystem.navigation.PageScaffold
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
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.FloatingActionButton
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.icon.extended.Replace
import top.yukonga.miuix.kmp.icon.extended.SearchDevice
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

@Composable
fun DevicesPage(
    controller: DevicesController,
    bottomInset: Dp
) {
    val context = LocalContext.current
    val state by controller.state.collectAsState()
    val isAdvertising = state.isAdvertising
    val isHost = state.role == ConnectionRole.HOST
    val isRefreshing = state.status == ConnectionStatus.DISCOVERING
    val connectedList = state.connectedEndpoints.toList().sortedBy { it.endpointName.lowercase() }
    val connectedById = connectedList.associateBy { it.endpointId }
    val discoveredList = state.discoveredEndpoints
        .filterNot { connectedById.containsKey(it.endpointId) }
        .sortedBy { it.endpointName.lowercase() }
    val connectedRoomName = connectedList.firstOrNull()?.endpointName.orEmpty()
    val bestLinkQuality = state.endpointBandwidth.values.minOfOrNull { it } ?: -1
    val discoveredCount = discoveredList.size
    val pendingCount = state.pendingConnections.size

    val statusAccent by animateColorAsState(
        targetValue = statusColor(state.status),
        animationSpec = spring(),
        label = "statusAccent"
    )
    val qualityAccent by animateColorAsState(
        targetValue = qualityColor(bestLinkQuality),
        animationSpec = spring(),
        label = "qualityAccent"
    )

    var lastHostToggleAtMs by remember { mutableLongStateOf(0L) }
    val hostToggleDebounceMs = 900L
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
    val primaryActionText = when {
        isHost && isAdvertising -> "停止广播"
        isHost -> "开始广播"
        isRefreshing -> "停止扫描"
        else -> "开始扫描"
    }
    val switchRoleActionText = if (isHost) "Host" else "Client"

    PageScaffold(
        title = "趣连蓝牙",
        scrollBehavior = scrollBehavior,
    ) { innerPadding, contentModifier ->
        val fabBottomPadding = innerPadding.calculateBottomPadding() + bottomInset + Spacing.FloatingInset
        val fabEndPadding = pageHorizontalPadding + Spacing.FloatingInset
        val fabClearance = Spacing.IconExtraLarge + Spacing.ExtraExtraLarge + Spacing.PageSectionGap + Spacing.FloatingInset

        Box(modifier = contentModifier) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .scrollEndHaptic()
                    .overScrollVertical()
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .padding(horizontal = pageHorizontalPadding),
                contentPadding = PaddingValues(
                    top = innerPadding.calculateTopPadding() + Spacing.PageSectionGap,
                    bottom = innerPadding.calculateBottomPadding() + bottomInset + Spacing.PageSectionGap + fabClearance
                ),
                verticalArrangement = Arrangement.spacedBy(Spacing.PageSectionGap),
                overscrollEffect = null
            ) {
                item {
                    ConnectionHeroCard(
                        isHost = isHost,
                        status = state.status,
                        summary = statusSummary(
                            status = state.status,
                            isHost = isHost,
                            isAdvert = isAdvertising,
                            roomName = controller.roomNameInput,
                            connectedRoomName = connectedRoomName
                        ),
                        primaryActionText = primaryActionText,
                        connectedCount = connectedList.size,
                        discoveredCount = discoveredCount,
                        pendingCount = pendingCount,
                        bestLinkQuality = bestLinkQuality,
                        lastError = state.lastError,
                        identifierValue = if (isHost) controller.roomNameInput else controller.clientNameInput,
                        statusAccent = statusAccent,
                        onIdentifierChange = {
                            if (isHost) controller.updateRoomName(it) else controller.updateClientName(it)
                        },
                        onPrimaryAction = {
                            if (isHost) {
                                val now = SystemClock.elapsedRealtime()
                                if (now - lastHostToggleAtMs < hostToggleDebounceMs) return@ConnectionHeroCard
                                lastHostToggleAtMs = now
                                if (isAdvertising) {
                                    controller.stopHostBroadcast()
                                } else {
                                    controller.startHostBroadcast()
                                    if (controller.pendingStartHost) permissionLauncher.launch(requiredPermissions)
                                }
                            } else {
                                if (isRefreshing) {
                                    controller.stopClientScan()
                                } else {
                                    controller.startClientScan()
                                    if (controller.pendingStartScan) permissionLauncher.launch(requiredPermissions)
                                }
                            }
                        }
                    )
                }

                item {
                    MetricsRow(
                        isHost = isHost,
                        connectedCount = connectedList.size,
                        discoveredCount = discoveredCount,
                        pendingCount = pendingCount,
                        connectedRoomName = connectedRoomName,
                        qualityAccent = qualityAccent,
                        bestLinkQuality = bestLinkQuality
                    )
                }

                item {
                    if (isHost) {
                        SectionTitle(
                            title = "已连接设备",
                            summary = if (connectedList.isEmpty()) {
                                "开始广播后，等待其他设备加入你的房间。"
                            } else {
                                "已建立连接的设备会在这里持续更新链路质量。"
                            }
                        )
                    } else {
                        SectionTitle(
                            title = "附近房间",
                            summary = if (isRefreshing) {
                                "正在搜索附近可加入的房间。"
                            } else {
                                "开始扫描后，可从这里发起连接。"
                            }
                        )
                    }
                }

                item {
                    if (isHost) {
                        ConnectedEndpointsCard(
                            connectedList = connectedList,
                            endpointBandwidth = state.endpointBandwidth
                        )
                    } else {
                        DiscoveredEndpointsCard(
                            discoveredList = discoveredList,
                            isRefreshing = isRefreshing,
                            onConnect = controller::requestConnectionToEndpoint
                        )
                    }
                }
            }

            FloatingActionButton(
                onClick = {
                    val target = if (isHost) ConnectionRole.CLIENT else ConnectionRole.HOST
                    controller.requestRoleSwitch(target)
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = fabEndPadding, bottom = fabBottomPadding),
                shape = Corners.PageShape
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = Spacing.ExtraLarge),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = MiuixIcons.Replace,
                        contentDescription = switchRoleActionText,
                        tint = Color.White,
                        modifier = Modifier.size(Spacing.IconMedium)
                    )
                    Box(modifier = Modifier.width(Spacing.PageBase10))
                    Text(
                        text = switchRoleActionText,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

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

        val switchTarget = controller.pendingRoleSwitchTarget
        val switchSummary = when (state.role) {
            ConnectionRole.HOST if switchTarget == ConnectionRole.CLIENT ->
                "当前已连接设备，切换到 Client 会停止广播。是否继续切换？"

            ConnectionRole.CLIENT if switchTarget == ConnectionRole.HOST ->
                "当前已连接 Host，切换到 Host 前建议确认连接状态。是否继续切换？"

            else -> "当前已有连接设备，切换模式可能影响当前连接。是否继续切换？"
        }

        ConfirmationDialog(
            show = controller.showRoleSwitchConfirmDialog && switchTarget != null,
            title = "确认切换模式",
            summary = switchSummary,
            onDismissRequest = {
                controller.cancelRoleSwitch()
            },
        ) {
            DialogActionRow(
                cancelText = "取消",
                confirmText = "确定切换",
                destructiveConfirm = true,
                onCancel = {
                    controller.cancelRoleSwitch()
                },
                onConfirm = {
                    controller.confirmRoleSwitch()
                }
            )
        }
    }
}

@Composable
private fun ConnectionHeroCard(
    isHost: Boolean,
    status: ConnectionStatus,
    summary: String,
    primaryActionText: String,
    connectedCount: Int,
    discoveredCount: Int,
    pendingCount: Int,
    bestLinkQuality: Int,
    lastError: String?,
    identifierValue: String,
    statusAccent: Color,
    onIdentifierChange: (String) -> Unit,
    onPrimaryAction: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(Corners.PageShape)
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = Spacing.PageCardPaddingLarge,
                vertical = Spacing.PageCardPadding
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.Medium)
            ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall)) {
                    Text(
                        text = if (isHost) "主机模式" else "客户端模式",
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onBackgroundVariant
                    )
                    Text(
                        text = statusHeadline(status),
                        style = MiuixTheme.textStyles.title2,
                        color = statusAccent,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Text(
                text = summary,
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurface
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.SmallPlus)
            ) {
                StatusBadge(
                    text = "质量 ${qualityLabel(bestLinkQuality)}",
                    tone = if (bestLinkQuality > 0) SurfaceTone.Primary else SurfaceTone.Neutral
                )
                StatusBadge(
                    text = if (isHost) "已连 $connectedCount" else "房间 $discoveredCount",
                    tone = SurfaceTone.Neutral
                )
                if (pendingCount > 0) {
                    StatusBadge(
                        text = "待处理 $pendingCount",
                        tone = SurfaceTone.Warning
                    )
                }
            }

            TextField(
                value = identifierValue,
                onValueChange = onIdentifierChange,
                modifier = Modifier.fillMaxWidth(),
                label = if (isHost) "房间标识" else "连接标识",
                singleLine = true
            )
            Text(
                text = if (isHost) {
                    "房间名会自动记忆，下次可以直接广播。"
                } else {
                    "连接名会自动记忆，用于被其他设备识别。"
                },
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onBackgroundVariant
            )

            AnimatedVisibility(
                visible = !lastError.isNullOrBlank(),
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Text(
                    text = lastError.orEmpty(),
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.error
                )
            }

            Button(
                onClick = onPrimaryAction,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColorsPrimary()
            ) {
                Text(text = primaryActionText)
            }
        }
    }
}

@Composable
private fun MetricsRow(
    isHost: Boolean,
    connectedCount: Int,
    discoveredCount: Int,
    pendingCount: Int,
    connectedRoomName: String,
    qualityAccent: Color,
    bestLinkQuality: Int
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.Medium)
    ) {
        MetricCard(
            modifier = Modifier.weight(1f),
            title = if (isHost) "已连接设备" else "附近房间",
            value = if (isHost) connectedCount.toString() else discoveredCount.toString(),
            summary = if (isHost) "实时连接数量" else "可发现房间数",
            accent = if (isHost && connectedCount > 0) qualityAccent else MiuixTheme.colorScheme.onSurface
        )
        MetricCard(
            modifier = Modifier.weight(1f),
            title = if (isHost) "待处理请求" else "当前链路",
            value = if (isHost) pendingCount.toString() else connectedRoomName.ifBlank { "未连接" },
            summary = if (isHost) {
                "等待确认的连接"
            } else if (connectedRoomName.isBlank()) {
                "扫描后可发起连接"
            } else {
                "质量 ${qualityLabel(bestLinkQuality)}"
            },
            accent = if (isHost && pendingCount > 0) {
                MiuixTheme.colorScheme.error
            } else if (!isHost && connectedRoomName.isNotBlank()) {
                qualityAccent
            } else {
                MiuixTheme.colorScheme.onSurface
            }
        )
    }
}

@Composable
private fun MetricCard(
    title: String,
    value: String,
    summary: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .clip(Corners.PageShape),
        pressFeedbackType = PressFeedbackType.Tilt
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = Spacing.PageCardPadding,
                vertical = Spacing.PageBase10
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall)
        ) {
            Text(
                text = title,
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onBackgroundVariant
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
                color = MiuixTheme.colorScheme.onBackgroundVariant
            )
        }
    }
}

@Composable
private fun ConnectedEndpointsCard(
    connectedList: List<NearbyEndpointInfo>,
    endpointBandwidth: Map<String, Int>
) {
    if (connectedList.isEmpty()) {
        StateMessageCard(
            title = "暂无已连接设备",
            summary = "开始广播并等待其他设备加入，连接成功后会在这里显示。",
            tone = SurfaceTone.Neutral
        )
        return
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .clip(Corners.PageShape)
    ) {
        Column {
            connectedList.forEachIndexed { index, endpoint ->
                EndpointRow(
                    title = endpoint.endpointName,
                    subtitle = "设备已建立连接",
                    trailingLabel = endpointBandwidth[endpoint.endpointId]?.let { qualityLabel(it) },
                    trailingTone = endpointBandwidth[endpoint.endpointId]?.let { qualityColor(it) }
                        ?: MiuixTheme.colorScheme.onBackgroundVariant,
                    onClick = null
                )
                if (index < connectedList.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = Spacing.DividerInsetStart)
                    )
                }
            }
        }
    }
}

@Composable
private fun DiscoveredEndpointsCard(
    discoveredList: List<NearbyEndpointInfo>,
    isRefreshing: Boolean,
    onConnect: (String) -> Unit
) {
    if (discoveredList.isEmpty()) {
        StateMessageCard(
            title = if (isRefreshing) "正在搜索附近房间" else "暂无附近房间",
            summary = if (isRefreshing) {
                "继续保持扫描，附近房间被发现后会出现在这里。"
            } else {
                "点击开始扫描，或检查另一台设备是否已经开始广播。"
            },
            tone = if (isRefreshing) SurfaceTone.Primary else SurfaceTone.Neutral
        )
        return
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .clip(Corners.PageShape)
    ) {
        Column {
            discoveredList.forEachIndexed { index, endpoint ->
                EndpointRow(
                    title = endpoint.endpointName,
                    subtitle = "点击发起连接",
                    trailingLabel = "连接",
                    trailingTone = MiuixTheme.colorScheme.primary,
                    onClick = { onConnect(endpoint.endpointId) }
                )
                if (index < discoveredList.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = Spacing.DividerInsetStart)
                    )
                }
            }
        }
    }
}

@Composable
private fun EndpointRow(
    title: String,
    subtitle: String,
    trailingLabel: String?,
    trailingTone: Color,
    onClick: (() -> Unit)?
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(Corners.nestedShape(Spacing.Zero)),
        showIndication = onClick != null,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.PageCardPaddingLarge, vertical = Spacing.PageCardPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.PageBase10)
        ) {
            Icon(
                imageVector = if (onClick == null) MiuixIcons.Ok else MiuixIcons.SearchDevice,
                contentDescription = title,
                tint = trailingTone,
                modifier = Modifier.size(Spacing.IconMedium)
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall)
            ) {
                Text(
                    text = title,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onBackgroundVariant
                )
            }
            if (!trailingLabel.isNullOrBlank()) {
                StatusBadge(
                    text = trailingLabel,
                    tone = if (onClick == null) SurfaceTone.Primary else SurfaceTone.Primary
                )
            }
        }
    }
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
    isHost -> "尚未广播，可以先填写房间名再开始。"
    connectedRoomName.isNotBlank() -> "已连接到房间 $connectedRoomName，可直接进入插件联调。"
    status == ConnectionStatus.DISCOVERING -> "正在搜索附近房间，发现后即可点击加入。"
    else -> "尚未连接，可填写连接名后开始扫描。"
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
