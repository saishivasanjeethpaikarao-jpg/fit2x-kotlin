package com.fit2x.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Card shown when Health Connect step permission is not granted.
 *
 * Three states:
 * 1. [HealthPermissionState.NotInstalled] — Health Connect app missing → Play Store button
 * 2. [HealthPermissionState.Denied]       — permission not granted → "Grant access" button
 * 3. (granted → this card is not shown at all)
 */
enum class HealthPermissionState {
    NotInstalled,
    Denied
}

@Composable
fun HealthPermissionCard(
    state: HealthPermissionState,
    onGrantAccess: () -> Unit,
    onInstall: () -> Unit,
    onEnterManually: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.DirectionsWalk,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = "Automatic step tracking",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Text(
                text = when (state) {
                    HealthPermissionState.NotInstalled ->
                        "FIT2X reads your steps from Health Connect, which isn't " +
                            "installed on this phone yet. Install it once — steps " +
                            "then sync automatically, even when the app is closed."
                    HealthPermissionState.Denied ->
                        "FIT2X reads your steps from Health Connect so you never " +
                            "have to count manually. Grant access once — syncing " +
                            "happens in the background every 15 minutes."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(4.dp))

            when (state) {
                HealthPermissionState.NotInstalled -> {
                    Button(
                        onClick = onInstall,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Filled.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Install Health Connect")
                    }
                }
                HealthPermissionState.Denied -> {
                    Button(
                        onClick = onGrantAccess,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Filled.HealthAndSafety,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Grant step access")
                    }
                }
            }

            // Always offer the manual fallback — never trap the user.
            TextButton(
                onClick = onEnterManually,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text(
                    "Enter steps manually instead",
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

/**
 * Compact banner variant for inline use (e.g. top of the Steps screen).
 */
@Composable
fun HealthPermissionBanner(
    state: HealthPermissionState,
    onAction: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = when (state) {
                    HealthPermissionState.NotInstalled -> "Install Health Connect for auto step sync"
                    HealthPermissionState.Denied -> "Grant Health Connect access for auto step sync"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(12.dp))
            OutlinedButton(onClick = onAction) {
                Text(
                    when (state) {
                        HealthPermissionState.NotInstalled -> "Install"
                        HealthPermissionState.Denied -> "Grant"
                    }
                )
            }
        }
    }
}
