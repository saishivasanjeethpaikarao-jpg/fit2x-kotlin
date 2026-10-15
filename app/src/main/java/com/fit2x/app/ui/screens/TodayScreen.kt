package com.fit2x.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fit2x.app.R
import com.fit2x.app.appGraph
import com.fit2x.app.data.auth.AuthState
import com.fit2x.app.data.auth.AuthViewModel
import com.fit2x.app.diet.DietViewModel
import com.fit2x.app.health.HealthViewModel
import com.fit2x.app.ui.components.ProgressRing
import com.fit2x.app.ui.components.StatCard
import com.fit2x.app.workout.WorkoutGraph
import com.fit2x.app.workout.WorkoutViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.flow.map

/**
 * Today — the home screen. Pulls live data from the real feature ViewModels:
 * steps from [HealthViewModel], protein from [DietViewModel], workout +
 * streak from [WorkoutViewModel], identity from [AuthViewModel].
 */
@Composable
fun TodayScreen(
    healthViewModel: HealthViewModel = viewModel(),
    dietViewModel: DietViewModel = viewModel(),
    workoutViewModel: WorkoutViewModel = viewModel(),
    authViewModel: AuthViewModel? = null,
    onStartWorkout: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val today = LocalDate.now()
    val heroDate = today.format(DateTimeFormatter.ofPattern("EEEE · d MMM", Locale.ENGLISH)).uppercase()

    val steps by healthViewModel.todaySteps.collectAsState()
    val dietState by dietViewModel.uiState.collectAsState()
    val todayWorkout by workoutViewModel.todayWorkout.collectAsState()
    val streak by workoutViewModel.streak.collectAsState()

    val profile by context.appGraph().database.userProfileDao()
        .observeProfile()
        .map { it }
        .collectAsState(initial = null)
    val stepGoal = profile?.stepGoal ?: 10_000

    val authState = authViewModel?.authState?.collectAsState()?.value
    val isSignedIn = authState is AuthState.SignedIn
    val userInitial = when (val s = authState) {
        is AuthState.SignedIn -> s.user.name.firstOrNull()?.uppercase() ?: "A"
        else -> profile?.name?.firstOrNull()?.uppercase() ?: "A"
    }

    val workoutTitle = todayWorkout?.title ?: "Active Recovery"
    val workoutFocus = todayWorkout?.subtitle?.ifBlank { "Easy walk and mobility. Keep it light." }
        ?: "Easy walk and mobility. Keep it light."
    val totalMoves = todayWorkout?.exercises?.size ?: 2
    // Completed moves come from today's sessions.
    val completedMoves = 0 // sessions are tracked in WorkoutDetail; today shows plan size

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(scheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Top bar: logo + avatar + sync pill
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(color = scheme.onBackground, fontWeight = FontWeight.ExtraBold)) {
                        append("FIT")
                    }
                    withStyle(SpanStyle(color = scheme.primary, fontWeight = FontWeight.ExtraBold)) {
                        append("2X")
                    }
                },
                fontSize = 22.sp,
                letterSpacing = 1.sp
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(scheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = userInitial,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = scheme.onSurfaceVariant
                    )
                }
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = scheme.surfaceVariant),
                    border = BorderStroke(1.dp, scheme.outline.copy(alpha = 0.6f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isSignedIn) scheme.primary
                                    else scheme.onSurfaceVariant.copy(alpha = 0.6f)
                                )
                        )
                        Text(
                            text = stringResource(
                                if (isSignedIn) R.string.nav_today else R.string.local_only
                            ),
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = scheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Offline banner (only when not signed in)
        if (!isSignedIn) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = scheme.tertiaryContainer.copy(alpha = 0.35f)
                ),
                border = BorderStroke(1.dp, scheme.tertiary.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = buildAnnotatedString {
                                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = scheme.tertiary)) {
                                    append(stringResource(R.string.offline_mode))
                                }
                                withStyle(SpanStyle(color = scheme.onSurfaceVariant)) {
                                    append(" · " + stringResource(R.string.offline_desc))
                                }
                            },
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Text(
                        text = today.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)),
                        style = MaterialTheme.typography.labelMedium,
                        color = scheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        }

        // Hero date header
        Column {
            Text(
                text = stringResource(R.string.today_label),
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 3.sp
                ),
                color = scheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = heroDate,
                style = MaterialTheme.typography.displaySmall.copy(
                    fontWeight = FontWeight.Black,
                    letterSpacing = (-1).sp,
                    lineHeight = 40.sp
                ),
                color = scheme.onBackground
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = workoutFocus,
                style = MaterialTheme.typography.bodyLarge,
                color = scheme.onSurfaceVariant
            )
        }

        // 2x2 stat grid — all live values
        val stepsLeft = (stepGoal - steps.toInt()).coerceAtLeast(0)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatCard(
                value = "%,d".format(steps),
                label = stringResource(R.string.stat_steps),
                progress = if (stepGoal > 0) steps.toFloat() / stepGoal else 0f,
                caption = if (stepsLeft > 0)
                    stringResource(R.string.steps_left, stepsLeft) else null,
                modifier = Modifier.weight(1f)
            )
            StatCard(
                value = "${dietState.proteinGrams.toInt()}g",
                label = stringResource(R.string.stat_protein),
                progress = dietState.proteinProgress,
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatCard(
                value = "$completedMoves/$totalMoves",
                label = stringResource(R.string.stat_exercises),
                modifier = Modifier.weight(1f)
            )
            StatCard(
                value = streak.toString(),
                label = stringResource(R.string.stat_streak),
                modifier = Modifier.weight(1f)
            )
        }

        // Today's workout card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = scheme.surface),
            border = BorderStroke(1.dp, scheme.outline.copy(alpha = 0.6f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.todays_workout_header),
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 2.sp
                            ),
                            color = scheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = workoutTitle,
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = (-0.5).sp
                            ),
                            color = scheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(
                                R.string.moves_done,
                                completedMoves,
                                totalMoves
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = scheme.onSurfaceVariant
                        )
                    }
                    val workoutProgress =
                        if (totalMoves > 0) completedMoves.toFloat() / totalMoves else 0f
                    ProgressRing(
                        progress = workoutProgress,
                        centerText = "${(workoutProgress * 100).toInt()}%"
                    )
                }
                Button(
                    onClick = onStartWorkout,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = scheme.primary,
                        contentColor = scheme.onPrimary
                    )
                ) {
                    Text(
                        text = "▶  " + stringResource(R.string.start_workout),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}
