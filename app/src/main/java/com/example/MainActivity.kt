package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.data.repo.ChatRepository
import com.example.ui.AmmarChatViewModel
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.BannedScreen
import com.example.ui.screens.ChatDetailScreen
import com.example.ui.screens.ChatsHomeScreen
import com.example.ui.screens.CreateGroupDialog
import com.example.ui.screens.GroupInfoScreen
import com.example.ui.screens.JoinInviteLinkDialog
import com.example.ui.screens.SettingsAndOwnerScreen
import com.example.ui.screens.SplashCreditsScreen
import com.example.ui.theme.AmmarChatTheme

class MainActivity : ComponentActivity() {
    private val viewModel: AmmarChatViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AmmarChatRoot(viewModel = viewModel)
        }
    }
}

@Composable
fun AmmarChatRoot(
    viewModel: AmmarChatViewModel,
    splashDurationSeconds: Int = 15
) {
    val activeTheme by viewModel.activeTheme.collectAsStateWithLifecycle()
    val customThemeImagePath by viewModel.customThemeImagePath.collectAsStateWithLifecycle()
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val allUsers by viewModel.allUsers.collectAsStateWithLifecycle()
    val allGroups by viewModel.allGroups.collectAsStateWithLifecycle()
    val threads by viewModel.chatThreads.collectAsStateWithLifecycle()
    val presenceMap by viewModel.presenceMap.collectAsStateWithLifecycle()
    val recordingState by viewModel.recordingState.collectAsStateWithLifecycle()
    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
    val authError by viewModel.authError.collectAsStateWithLifecycle()
    val toastMessage by viewModel.toastMessage.collectAsStateWithLifecycle()

    var showSplash by rememberSaveable { mutableStateOf(splashDurationSeconds > 0) }
    var showCreateGroupDialog by remember { mutableStateOf(false) }
    var showJoinLinkDialog by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(toastMessage) {
        val msg = toastMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearToast()
        }
    }

    AmmarChatTheme(accentColor = activeTheme.accent) {
        Scaffold(
            snackbarHost = {
                Box(modifier = Modifier.navigationBarsPadding()) {
                    SnackbarHost(hostState = snackbarHostState)
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = innerPadding.calculateBottomPadding() * 0f)
            ) {
                when {
                    showSplash -> {
                        SplashCreditsScreen(
                            totalDurationSeconds = splashDurationSeconds,
                            onFinished = { showSplash = false }
                        )
                    }

                    currentUser == null -> {
                        AuthScreen(
                            connectionState = connectionState,
                            authError = authError,
                            onClearError = { viewModel.clearAuthError() },
                            onSignUp = { email, name, username, uri ->
                                viewModel.signUp(email, name, username, uri)
                            },
                            onSignIn = { email, name, username ->
                                viewModel.signIn(email, name, username)
                            }
                        )
                    }

                    currentUser?.isCurrentlyBanned() == true -> {
                        BannedScreen(
                            username = currentUser?.username ?: "user",
                            banExpiresAt = currentUser?.banExpiresAt ?: -1L,
                            onSignOut = { viewModel.signOut() }
                        )
                    }

                    else -> {
                        val me = currentUser!!
                        val navController = rememberNavController()

                        NavHost(
                            navController = navController,
                            startDestination = "home"
                        ) {
                            composable("home") {
                                ChatsHomeScreen(
                                    currentUser = me,
                                    connectionState = connectionState,
                                    theme = activeTheme,
                                    threads = threads,
                                    allUsers = allUsers,
                                    allGroups = allGroups,
                                    onOpenDm = { peer ->
                                        navController.navigate("chat/false/${peer.userId}")
                                    },
                                    onOpenGroup = { groupId ->
                                        navController.navigate("chat/true/$groupId")
                                    },
                                    onJoinPublicGroup = { groupId ->
                                        viewModel.joinPublicGroup(groupId) { joined ->
                                            navController.navigate("chat/true/${joined.groupId}")
                                        }
                                    },
                                    onOpenSettings = {
                                        navController.navigate("settings")
                                    },
                                    onOpenCreateGroup = { showCreateGroupDialog = true },
                                    onOpenJoinLink = { showJoinLinkDialog = true },
                                    onOpenHotspotNode = { viewModel.openHotspotNodeApp() }
                                )
                            }

                            composable(
                                route = "chat/{isGroup}/{recipientId}",
                                arguments = listOf(
                                    navArgument("isGroup") { type = NavType.BoolType },
                                    navArgument("recipientId") { type = NavType.StringType }
                                )
                            ) { backStackEntry ->
                                val isGroup = backStackEntry.arguments?.getBoolean("isGroup") ?: false
                                val recipientId = backStackEntry.arguments?.getString("recipientId") ?: ""
                                val chatId = if (isGroup) {
                                    ChatRepository.groupChatId(recipientId)
                                } else {
                                    ChatRepository.dmChatId(me.userId, recipientId)
                                }

                                val messages by viewModel.observeMessages(chatId)
                                    .collectAsStateWithLifecycle(initialValue = emptyList())
                                val group by if (isGroup) {
                                    viewModel.observeGroup(recipientId)
                                        .collectAsStateWithLifecycle(initialValue = null)
                                } else {
                                    remember { mutableStateOf(null) }
                                }
                                val peerUser = if (!isGroup) {
                                    allUsers.firstOrNull { it.userId == recipientId }
                                } else null
                                val myGroups = allGroups.filter { me.userId in it.memberIds() }

                                ChatDetailScreen(
                                    chatId = chatId,
                                    isGroup = isGroup,
                                    recipientId = recipientId,
                                    currentUser = me,
                                    peerUser = peerUser,
                                    group = group,
                                    allUsers = allUsers,
                                    myGroups = myGroups,
                                    messages = messages,
                                    presence = presenceMap[chatId],
                                    connectionState = connectionState,
                                    theme = activeTheme,
                                    customThemeImagePath = customThemeImagePath,
                                    recordingState = recordingState,
                                    playbackState = playbackState,
                                    onBack = { navController.popBackStack() },
                                    onSetOpenChat = { viewModel.setOpenChat(it) },
                                    onTextChanged = { text ->
                                        viewModel.onComposerTextChanged(chatId, isGroup, recipientId, text)
                                    },
                                    onSendText = { text, onSent ->
                                        viewModel.sendText(chatId, isGroup, recipientId, text, onSent)
                                    },
                                    onSendVideoEmoji = { uri ->
                                        viewModel.sendVideoEmoji(chatId, isGroup, recipientId, uri)
                                    },
                                    onStartVoice = {
                                        viewModel.startVoiceRecording(chatId, isGroup, recipientId)
                                    },
                                    onStopAndSendVoice = {
                                        viewModel.stopAndSendVoice(chatId, isGroup, recipientId)
                                    },
                                    onCancelVoice = {
                                        viewModel.cancelVoiceRecording(chatId, isGroup, recipientId)
                                    },
                                    onTogglePlayVoice = { msg -> viewModel.togglePlayVoice(msg) },
                                    onOpenGroupInfo = { gid -> navController.navigate("group_info/$gid") },
                                    onShareGroupInvite = { grp ->
                                        viewModel.sendGroupInvite(chatId, isGroup, recipientId, grp)
                                    },
                                    onJoinGroupInvite = { gid, tok, snap ->
                                        viewModel.joinGroupByInvite(gid, tok, snap) { joined ->
                                            navController.navigate("chat/true/${joined.groupId}")
                                        }
                                    },
                                    onOwnerBanWithDuration = { uid, durationMs ->
                                        viewModel.banUserWithDuration(uid, durationMs)
                                    },
                                    onOwnerGrantBadge = { uid, badge ->
                                        viewModel.setUserBadgeByOwner(uid, badge)
                                    },
                                    onOpenHotspotNode = { viewModel.openHotspotNodeApp() }
                                )
                            }

                            composable(
                                route = "group_info/{groupId}",
                                arguments = listOf(navArgument("groupId") { type = NavType.StringType })
                            ) { backStackEntry ->
                                val groupId = backStackEntry.arguments?.getString("groupId") ?: ""
                                val group by viewModel.observeGroup(groupId)
                                    .collectAsStateWithLifecycle(initialValue = null)

                                group?.let { grp ->
                                    GroupInfoScreen(
                                        group = grp,
                                        currentUser = me,
                                        allUsers = allUsers,
                                        inviteLink = viewModel.buildInviteLink(grp),
                                        canManage = viewModel.canManageGroup(me, grp),
                                        theme = activeTheme,
                                        onBack = { navController.popBackStack() },
                                        onUpdateGroupDp = { uri -> viewModel.updateGroupDp(grp.groupId, uri) },
                                        onTogglePrivacy = { viewModel.toggleGroupPrivacy(grp.groupId) },
                                        onRegenerateInviteLink = { viewModel.regenerateGroupInviteLink(grp.groupId) },
                                        onMuteMember = { uid, mins -> viewModel.muteGroupMember(grp.groupId, uid, mins) },
                                        onChangeGroupOwner = { newOwnerId -> viewModel.changeGroupOwner(grp.groupId, newOwnerId) },
                                        onKickMember = { targetUid -> viewModel.kickMember(grp.groupId, targetUid) },
                                        onToggleAdmin = { targetUid -> viewModel.toggleGroupAdmin(grp.groupId, targetUid) },
                                        onCopiedLink = { viewModel.showToast("Invite link copied to clipboard") }
                                    )
                                }
                            }

                            composable("settings") {
                                SettingsAndOwnerScreen(
                                    currentUser = me,
                                    allUsers = allUsers,
                                    activeTheme = activeTheme,
                                    customThemeImagePath = customThemeImagePath,
                                    onBack = { navController.popBackStack() },
                                    onUpdateAvatar = { uri -> viewModel.updateProfile(me.displayName, uri) },
                                    onSetVideoAvatar = { uri -> viewModel.setMyVideoAvatar(uri) },
                                    onSetOwnerBadgeAndSticker = { badge, uri ->
                                        viewModel.setOwnerBadgeAndSticker(badge, uri)
                                    },
                                    onSetUserBadge = { uid, badge -> viewModel.setUserBadgeByOwner(uid, badge) },
                                    onSelectTheme = { t -> viewModel.setThemeByOwner(t) },
                                    onRandomTheme = { viewModel.setRandomThemeByOwner() },
                                    onSelectCustomGalleryTheme = { uri -> viewModel.setCustomGalleryThemeByOwner(uri) },
                                    onBanWithDuration = { uid, durationMs -> viewModel.banUserWithDuration(uid, durationMs) },
                                    onOpenHotspotNode = { viewModel.openHotspotNodeApp() },
                                    onSignOut = { viewModel.signOut() }
                                )
                            }
                        }

                        if (showCreateGroupDialog) {
                            CreateGroupDialog(
                                theme = activeTheme,
                                onDismiss = { showCreateGroupDialog = false },
                                onCreate = { name, uri, isPublic ->
                                    viewModel.createGroup(name, uri, isPublic) { grp ->
                                        showCreateGroupDialog = false
                                        navController.navigate("chat/true/${grp.groupId}")
                                    }
                                }
                            )
                        }

                        if (showJoinLinkDialog) {
                            JoinInviteLinkDialog(
                                theme = activeTheme,
                                onDismiss = { showJoinLinkDialog = false },
                                onJoin = { rawLink ->
                                    viewModel.joinGroupByLinkText(rawLink) { grp ->
                                        showJoinLinkDialog = false
                                        navController.navigate("chat/true/${grp.groupId}")
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
