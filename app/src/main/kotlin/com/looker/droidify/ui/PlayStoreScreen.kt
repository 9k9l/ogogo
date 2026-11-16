package com.looker.droidify.ui

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.looker.droidify.play.PlayApp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayStoreScreen(vm: PlayViewModel) {
    val app = vm.selected
    BackHandler(enabled = app != null) { vm.close() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(app?.name ?: "Google Play") },
                navigationIcon = {
                    if (app != null) TextButton(onClick = vm::close) { Text("Back") }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (app == null) SearchPage(vm) else DetailPage(vm, app)
        }
    }
}

@Composable
private fun SearchPage(vm: PlayViewModel) {
    OutlinedTextField(
        value = vm.query,
        onValueChange = { vm.query = it },
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        singleLine = true,
        label = { Text("Search Google Play") },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { vm.search() }),
        trailingIcon = {
            if (vm.searching) {
                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            } else {
                TextButton(onClick = vm::search) { Text("Go") }
            }
        },
    )
    vm.error?.let {
        Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp))
    }
    LazyColumn(Modifier.fillMaxSize()) {
        items(vm.results, key = { it.packageName }) { app ->
            ListItem(
                modifier = Modifier.clickable { vm.open(app) },
                leadingContent = { AppIcon(app.iconUrl, 48) },
                headlineContent = { Text(app.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                supportingContent = {
                    Text(
                        app.developer.ifEmpty { app.packageName },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
            )
        }
    }
}

@Composable
private fun DetailPage(vm: PlayViewModel, app: PlayApp) {
    val context = LocalContext.current
    val progress = vm.installProgress
    val installedVersion = installedVersionName(context.packageManager, app.packageName)

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIcon(app.iconUrl, 72)
            Column(Modifier.padding(start = 16.dp)) {
                Text(app.name, style = MaterialTheme.typography.titleLarge)
                Text(app.developer, style = MaterialTheme.typography.bodyMedium)
                Text(
                    "${app.versionName} · ${formatSize(app.sizeBytes)}",
                    style = MaterialTheme.typography.bodySmall,
                )
                if (installedVersion != null) {
                    Text("Installed: $installedVersion", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        if (progress == null) {
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    if (Build.VERSION.SDK_INT >= 26 && !context.packageManager.canRequestPackageInstalls()) {
                        // Let the user allow "install unknown apps" for this app first.
                        context.startActivity(
                            Intent(
                                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                Uri.parse("package:${context.packageName}"),
                            ),
                        )
                    } else {
                        vm.install()
                    }
                },
            ) { Text(if (installedVersion == null) "Install" else "Update / Reinstall") }
        } else {
            if (progress < 0f) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text("Getting download links…")
            } else {
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                Text("Downloading ${(progress * 100).toInt()}%")
            }
        }
        vm.installMessage?.let { Text(it) }

        if (app.changes.isNotBlank()) {
            Text("What's new", style = MaterialTheme.typography.titleMedium)
            Text(stripHtml(app.changes))
        }
        Spacer(Modifier.height(4.dp))
        Text(stripHtml(app.description.ifBlank { app.summary }))
    }
}

@Composable
private fun AppIcon(url: String?, size: Int) {
    AsyncImage(model = url, contentDescription = null, modifier = Modifier.size(size.dp))
}

private fun installedVersionName(pm: PackageManager, packageName: String): String? = try {
    pm.getPackageInfo(packageName, 0).versionName
} catch (_: PackageManager.NameNotFoundException) {
    null
}

private fun formatSize(bytes: Long): String = when {
    bytes <= 0 -> "?"
    bytes >= 1L shl 20 -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
    else -> "${bytes / 1024} KB"
}

/** Play descriptions contain simple HTML tags; show them as plain text. */
private fun stripHtml(text: String): String =
    androidx.core.text.HtmlCompat.fromHtml(text, androidx.core.text.HtmlCompat.FROM_HTML_MODE_COMPACT)
        .toString()
        .trim()
