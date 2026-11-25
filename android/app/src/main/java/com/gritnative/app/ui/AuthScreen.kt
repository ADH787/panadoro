package com.gritnative.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.gritnative.app.ui.components.GritButton
import com.gritnative.app.ui.components.GritButtonStyle
import com.gritnative.app.ui.components.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gritnative.app.data.AuthService
import com.gritnative.app.data.FirestoreSync
import com.gritnative.app.data.LeaderboardService
import com.gritnative.app.data.StoreRepository
import com.gritnative.app.ui.components.AmbientBackdrop
import com.gritnative.app.ui.components.EntranceWindow
import com.gritnative.app.ui.components.GritField
import com.gritnative.app.ui.components.LocalEntranceWindow
import com.gritnative.app.ui.components.RoundIconButton
import com.gritnative.app.ui.components.ShapeBadge
import com.gritnative.app.ui.components.StaggeredEntry
import com.gritnative.app.ui.theme.Grit
import com.gritnative.app.ui.theme.Symbol
import kotlinx.coroutines.launch

/**
 * The first thing a signed-out account sees. It can create an account, sign in,
 * or continue with the local-only store. The account is what turns on sync and
 * the leaderboard; nothing is uploaded before a sign-in.
 */
@Composable
fun AuthScreen(
    onSignedIn: () -> Unit,
    onSkip: () -> Unit
) {
    val auth by AuthService.state.collectAsState()
    val scope = rememberCoroutineScope()
    var creating by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }

    val transition = rememberInfiniteTransition(label = "auth-logo")
    val float by transition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3_200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "auth-float"
    )
    val breathe by transition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(2_300, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "auth-breathe"
    )

    fun submit() {
        if (auth.busy) return
        scope.launch {
            val ok = if (creating) {
                AuthService.createAccount(email, password)
            } else {
                AuthService.signIn(email, password)
            }
            if (ok) {
                if (creating && name.isNotBlank()) {
                    // Saved before the first sync and publish, so the board's
                    // first glimpse of this account already carries the
                    // chosen name.
                    StoreRepository.setDisplayName(name.trim().take(32))
                }
                password = ""
                FirestoreSync.syncNow(silent = true)
                LeaderboardService.publish()
                onSignedIn()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        AmbientBackdrop(
            modifier = Modifier.fillMaxSize(),
            first = MaterialTheme.colorScheme.primary,
            second = MaterialTheme.colorScheme.tertiary,
            third = MaterialTheme.colorScheme.secondary
        )

        CompositionLocalProvider(
            LocalEntranceWindow provides remember { EntranceWindow() }
        ) {
            Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 22.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(18.dp))
            StaggeredEntry(index = 0) {
                Box(
                    modifier = Modifier
                        .scale(breathe)
                        .rotate(float * 3.5f)
                ) {
                    ShapeBadge(
                        symbol = "local_fire_department",
                        shapeName = "Cookie12Sided",
                        container = MaterialTheme.colorScheme.primary,
                        content = MaterialTheme.colorScheme.onPrimary,
                        size = 96.dp,
                        iconSize = 50.dp,
                        filled = true
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            StaggeredEntry(index = 1) {
                Text(
                    text = "Panadoro",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 36.sp,
                    lineHeight = 41.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            StaggeredEntry(index = 2) {
                Text(
                    text = "Habits, tasks and focus — in one quiet place",
                    color = Grit.colors.subtext,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    textAlign = TextAlign.Center
                )
            }
            Spacer(Modifier.height(26.dp))

            StaggeredEntry(index = 3, modifier = Modifier.fillMaxWidth()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(32.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.94f),
                    shadowElevation = 18.dp
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(13.dp)
                    ) {
                        AnimatedContent(
                            targetState = creating,
                            transitionSpec = {
                                (fadeIn(tween(280)) + slideInVertically(tween(280)) { it / 5 })
                                    .togetherWith(fadeOut(tween(140)) + slideOutVertically(tween(140)) { -it / 6 })
                            },
                            label = "auth-mode"
                        ) { isCreating ->
                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(
                                    text = if (isCreating) "Make it yours" else "Welcome back",
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 24.sp,
                                    lineHeight = 28.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (isCreating) {
                                        "Create an account and your focus follows you to every device."
                                    } else {
                                        "Sign in to pick up your habits, tasks and focus exactly where you left them."
                                    },
                                    color = Grit.colors.subtext,
                                    fontSize = 11.5.sp,
                                    lineHeight = 16.sp
                                )
                            }
                        }

                        AnimatedVisibility(
                            visible = creating,
                            enter = fadeIn(tween(200)) +
                                expandVertically(tween(240, easing = FastOutSlowInEasing)),
                            exit = fadeOut(tween(140)) +
                                shrinkVertically(tween(180, easing = FastOutSlowInEasing))
                        ) {
                            GritField(
                                value = name,
                                onValueChange = { name = it.take(32) },
                                caption = "Display name",
                                placeholder = "Shown on the focus ranks",
                                symbol = "person",
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Text,
                                    imeAction = ImeAction.Next
                                )
                            )
                        }

                        GritField(
                            value = email,
                            onValueChange = {
                                email = it
                                AuthService.clearError()
                            },
                            caption = "Email",
                            placeholder = "you@example.com",
                            symbol = "inbox",
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Email,
                                imeAction = ImeAction.Next
                            )
                        )
                        GritField(
                            value = password,
                            onValueChange = {
                                password = it
                                AuthService.clearError()
                            },
                            caption = "Password",
                            placeholder = "At least 6 characters",
                            symbol = "key",
                            visualTransformation = if (showPassword) VisualTransformation.None
                            else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(onDone = { submit() }),
                            trailing = {
                                RoundIconButton(
                                    symbol = "visibility",
                                    onClick = { showPassword = !showPassword },
                                    size = 30.dp,
                                    iconSize = 16.dp,
                                    container = Color.Transparent,
                                    content = if (showPassword) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                    filled = showPassword
                                )
                            }
                        )

                        AnimatedVisibility(
                            visible = auth.error.isNotBlank(),
                            enter = fadeIn(tween(180)) + slideInVertically(tween(220)) { -it / 3 },
                            exit = fadeOut(tween(120))
                        ) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 9.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Symbol("error", size = 17.dp)
                                    Text(
                                        text = auth.error,
                                        fontSize = 11.sp,
                                        lineHeight = 14.sp,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }

                        Button(
                            onClick = { submit() },
                            enabled = !auth.busy,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors()
                        ) {
                            AnimatedContent(
                                targetState = auth.busy to creating,
                                transitionSpec = {
                                    (fadeIn(tween(180)) + scaleIn(initialScale = 0.9f))
                                        .togetherWith(fadeOut(tween(120)) + scaleOut(targetScale = 0.9f))
                                },
                                label = "auth-submit"
                            ) { (busy, isCreating) ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    if (busy) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            strokeWidth = 2.4.dp,
                                            color = MaterialTheme.colorScheme.onPrimary
                                        )
                                        Text("Talking to Firebase…", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                    } else {
                                        Symbol(if (isCreating) "person" else "arrow_forward", size = 19.dp)
                                        Text(
                                            if (isCreating) "Create account" else "Sign in",
                                            fontSize = 13.5.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (creating) "Already have an account?" else "New to Panadoro?",
                                color = Grit.colors.subtext,
                                fontSize = 11.sp
                            )
                            TextButton(
                                onClick = {
                                    creating = !creating
                                    AuthService.clearError()
                                }
                            ) {
                                Text(
                                    text = if (creating) "Sign in" else "Create one",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            // A first-class choice, but compact enough to stay above the
            // navigation bar on a 9:16 phone without scrolling.
            StaggeredEntry(index = 4) {
                GritButton(
                    label = "Continue without an account",
                    onClick = onSkip,
                    symbol = "arrow_forward",
                    style = GritButtonStyle.OUTLINED,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            StaggeredEntry(index = 5) {
                Text(
                    text = "Your habits, tasks and notes stay private. The leaderboard only " +
                        "sees the tags you choose to publish.",
                    color = Grit.colors.subtext.copy(alpha = 0.8f),
                    fontSize = 9.5.sp,
                    lineHeight = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }
            Spacer(Modifier.height(18.dp))
        }
        }
    }
}
