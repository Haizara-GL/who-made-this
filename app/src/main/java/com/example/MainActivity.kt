package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.LocalParking
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.LocalParking
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.auth.authStateFlow
import com.example.auth.signOut
import com.example.data.UiState
import com.example.data.VestnikRepository
import com.example.ui.screens.ActiveCallOverlay
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.CallsScreen
import com.example.ui.screens.ChatDetailScreen
import com.example.ui.screens.ChatsListScreen
import com.example.ui.screens.ParkingScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.theme.VestnikTheme
import com.example.viewmodel.VestnikViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

const val ROUTE_CHATS = "chats"
const val ROUTE_CHAT_DETAIL = "chat_detail"
const val ROUTE_CALLS = "calls"
const val ROUTE_PARKING = "parking"
const val ROUTE_PROFILE = "profile"

private data class NavTab(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

private val TopTabs = listOf(
    NavTab(ROUTE_CHATS, R.string.nav_chats, Icons.Rounded.ChatBubble, Icons.Outlined.ChatBubbleOutline),
    NavTab(ROUTE_CALLS, R.string.nav_calls, Icons.Rounded.Call, Icons.Outlined.Call),
    NavTab(ROUTE_PARKING, R.string.nav_parking, Icons.Rounded.LocalParking, Icons.Outlined.LocalParking),
    NavTab(ROUTE_PROFILE, R.string.nav_profile, Icons.Rounded.Person, Icons.Outlined.PersonOutline)
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VestnikTheme {
                VestnikRoot()
            }
        }
    }
}

@Composable
private fun VestnikRoot() {
    val auth = Firebase.auth
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val user = currentUser

    if (user == null) {
        AuthScreen(onAuthSuccess = { /* AuthStateListener updates currentUser reactively */ })
    } else {
        key(user.uid) {
            AuthenticatedVestnikShell(
                currentUserId = user.uid,
                initialDisplayName = user.displayName,
                initialEmail = user.email
            )
        }
    }
}

@Composable
private fun AuthenticatedVestnikShell(
    currentUserId: String,
    initialDisplayName: String?,
    initialEmail: String?
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val viewModel: VestnikViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                VestnikViewModel(
                    repository = VestnikRepository(db),
                    currentUserId = currentUserId,
                    initialDisplayName = initialDisplayName,
                    initialEmail = initialEmail
                )
            }
        }
    )

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: ROUTE_CHATS

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) {
        viewModel.snackbarMessages.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    val ownProfileState by viewModel.ownProfile.collectAsStateWithLifecycle()
    val directoryState by viewModel.directoryProfiles.collectAsStateWithLifecycle()
    val chatsState by viewModel.chats.collectAsStateWithLifecycle()
    val selectedChat by viewModel.selectedChat.collectAsStateWithLifecycle()
    val messagesState by viewModel.messages.collectAsStateWithLifecycle()
    val callsState by viewModel.calls.collectAsStateWithLifecycle()
    val liveCall by viewModel.liveCallSession.collectAsStateWithLifecycle()
    val callElapsed by viewModel.activeCallElapsedSeconds.collectAsStateWithLifecycle()
    val callMuted by viewModel.callMuted.collectAsStateWithLifecycle()
    val callSpeaker by viewModel.callSpeaker.collectAsStateWithLifecycle()
    val parkingState by viewModel.parkingSpots.collectAsStateWithLifecycle()
    val parkingFilter by viewModel.parkingFilter.collectAsStateWithLifecycle()
    val userLocation by viewModel.userLocation.collectAsStateWithLifecycle()
    val actionInProgress by viewModel.actionInProgress.collectAsStateWithLifecycle()

    fun navigateToTab(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    fun openChatDetail(chatId: String) {
        viewModel.selectChat(chatId)
        navController.navigate(ROUTE_CHAT_DETAIL) {
            launchSingleTop = true
        }
    }

    val showMainNav = currentRoute != ROUTE_CHAT_DETAIL

    Box(modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val useNavigationRail = maxWidth >= 600.dp

            Row(modifier = Modifier.fillMaxSize()) {
                if (useNavigationRail && showMainNav) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        TopTabs.forEach { tab ->
                            val selected = currentRoute == tab.route
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateToTab(tab.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = stringResource(tab.labelRes)
                                    )
                                },
                                label = { Text(stringResource(tab.labelRes)) },
                                modifier = Modifier.testTag("nav_rail_${tab.route}")
                            )
                        }
                    }
                }

                Scaffold(
                    snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                    bottomBar = {
                        if (!useNavigationRail && showMainNav) {
                            NavigationBar(
                                containerColor = MaterialTheme.colorScheme.surface
                            ) {
                                TopTabs.forEach { tab ->
                                    val selected = currentRoute == tab.route
                                    NavigationBarItem(
                                        selected = selected,
                                        onClick = { navigateToTab(tab.route) },
                                        icon = {
                                            Icon(
                                                imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                                contentDescription = stringResource(tab.labelRes)
                                            )
                                        },
                                        label = { Text(stringResource(tab.labelRes)) },
                                        modifier = Modifier.testTag("nav_bar_${tab.route}")
                                    )
                                }
                            }
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = ROUTE_CHATS,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        composable(ROUTE_CHATS) {
                            ChatsListScreen(
                                chatsState = chatsState,
                                directoryState = directoryState,
                                currentUserId = currentUserId,
                                onOpenChat = { openChatDetail(it) },
                                onCreateGroupChat = { title, type, joinable ->
                                    viewModel.createGroupChat(title, type, joinable) { chatId ->
                                        openChatDetail(chatId)
                                    }
                                },
                                onStartDirectChat = { peerId, peerName ->
                                    viewModel.openDirectChat(peerId, peerName) { chatId ->
                                        openChatDetail(chatId)
                                    }
                                },
                                onJoinByCode = { code ->
                                    viewModel.joinChatByCode(code) { chatId ->
                                        openChatDetail(chatId)
                                    }
                                }
                            )
                        }

                        composable(ROUTE_CHAT_DETAIL) {
                            val activeSpots = (parkingState as? UiState.Success)?.data
                                ?.filter { it.status == "free" || it.status == "leaving_soon" }
                                .orEmpty()
                            ChatDetailScreen(
                                chat = selectedChat,
                                messagesState = messagesState,
                                activeSpots = activeSpots,
                                currentUserId = currentUserId,
                                onBack = {
                                    viewModel.selectChat(null)
                                    navController.popBackStack()
                                },
                                onSendMessage = { viewModel.sendTextMessage(it) },
                                onShareSpot = { viewModel.shareSpotToSelectedChat(it) },
                                onStartAudioCall = { peerId, peerName ->
                                    viewModel.startCall(peerId, peerName, "audio")
                                },
                                onStartVideoCall = { peerId, peerName ->
                                    viewModel.startCall(peerId, peerName, "video")
                                },
                                onOpenParkingTab = {
                                    viewModel.selectChat(null)
                                    navController.popBackStack()
                                    navigateToTab(ROUTE_PARKING)
                                }
                            )
                        }

                        composable(ROUTE_CALLS) {
                            CallsScreen(
                                callsState = callsState,
                                directoryState = directoryState,
                                currentUserId = currentUserId,
                                onStartCall = { peerId, peerName, callType ->
                                    viewModel.startCall(peerId, peerName, callType)
                                },
                                onStartEchoTest = { viewModel.startEchoTestCall("audio") }
                            )
                        }

                        composable(ROUTE_PARKING) {
                            ParkingScreen(
                                spotsState = parkingState,
                                selectedFilter = parkingFilter,
                                userLocation = userLocation,
                                currentUserId = currentUserId,
                                actionInProgress = actionInProgress,
                                onFilterSelected = { viewModel.setParkingFilter(it) },
                                onLocationUpdated = { viewModel.updateUserLocation(it) },
                                onReportSpot = { addr, dist, kind, note, mins, attachGps, onDone ->
                                    viewModel.reportParkingSpot(addr, dist, kind, note, mins, attachGps, onDone)
                                },
                                onCatchSpot = { viewModel.catchSpot(it) },
                                onReleaseSpot = { viewModel.releaseSpot(it) },
                                onCompleteSpot = { viewModel.completeSpot(it) },
                                onDeleteSpot = { viewModel.deleteSpot(it) },
                                onMessageDriver = { driverId, driverName ->
                                    viewModel.openDirectChat(driverId, driverName) { chatId ->
                                        openChatDetail(chatId)
                                    }
                                },
                                onCallDriver = { driverId, driverName ->
                                    viewModel.startCall(driverId, driverName, "audio")
                                }
                            )
                        }

                        composable(ROUTE_PROFILE) {
                            ProfileScreen(
                                ownProfileState = ownProfileState,
                                directoryState = directoryState,
                                currentUserId = currentUserId,
                                actionInProgress = actionInProgress,
                                onSaveProfile = { name, handle, car, city, disc ->
                                    viewModel.saveProfile(name, handle, car, city, disc)
                                },
                                onMessageUser = { userId, userName ->
                                    viewModel.openDirectChat(userId, userName) { chatId ->
                                        openChatDetail(chatId)
                                    }
                                },
                                onCallUser = { userId, userName ->
                                    viewModel.startCall(userId, userName, "audio")
                                },
                                onSignOut = {
                                    signOut(credentialManager = credentialManager, scope = scope)
                                }
                            )
                        }
                    }
                }
            }
        }

        // Global Live Call Overlay (renders across any tab when ringing or in call)
        val activeSession = liveCall
        if (activeSession != null) {
            ActiveCallOverlay(
                call = activeSession,
                currentUserId = currentUserId,
                elapsedSeconds = callElapsed,
                muted = callMuted,
                speakerOn = callSpeaker,
                onToggleMute = { viewModel.toggleCallMute() },
                onToggleSpeaker = { viewModel.toggleCallSpeaker() },
                onAnswer = { viewModel.answerCall(activeSession.id) },
                onDecline = { viewModel.declineCall(activeSession.id) },
                onEnd = { viewModel.endOrCancelCall(activeSession) }
            )
        }
    }
}
