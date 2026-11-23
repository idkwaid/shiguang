package dev.lumo.gallery.feature.private

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import dev.lumo.gallery.core.security.BiometricHelper
import dev.lumo.gallery.core.security.SecureScreen
import dev.lumo.gallery.core.ui.LocalAppContainer
import dev.lumo.gallery.core.ui.components.EmptyState

/**
 * 私密空间：首次设置 PIN / PIN+生物识别解锁 / 解锁后列出私密图库。
 * 进入即开启 FLAG_SECURE；App 退后台自动上锁（见 LumoApp）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivateGateScreen(
    onBack: () -> Unit,
    onOpenLibrary: (String) -> Unit
) {
    val container = LocalAppContainer.current
    val vm: PrivateGateViewModel = viewModel(factory = PrivateGateViewModel.factory(container))
    val unlocked by vm.unlocked.collectAsStateWithLifecycle()
    val libraries by vm.privateLibraries.collectAsStateWithLifecycle()
    val hasPin by vm.hasPin.collectAsStateWithLifecycle()
    val biometricEnabled by vm.biometricEnabled.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val scope = rememberCoroutineScope()

    SecureScreen(true)

    var pin by remember { mutableStateOf("") }
    var pinConfirm by remember { mutableStateOf("") }
    var setupMode by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }

    LaunchedEffect(unlocked, hasPin) {
        if (!unlocked && hasPin && biometricEnabled && activity != null &&
            BiometricHelper.isAvailable(activity)
        ) {
            runCatching { vm.unlockWithBiometric(activity) }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("私密空间") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (unlocked) {
                        IconButton(onClick = { vm.lockNow() }) {
                            Icon(Icons.Outlined.Lock, contentDescription = "立即上锁")
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when {
                !hasPin || setupMode -> {
                    Spacer(Modifier.size(36.dp))
                    Icon(
                        imageVector = Icons.Outlined.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(Modifier.size(12.dp))
                    Text("设置私密密码", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "至少 4 位。忘记密码无法找回，只能重置并清空私密索引。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.size(20.dp))
                    OutlinedTextField(
                        value = pin,
                        onValueChange = { pin = it },
                        label = { Text("私密密码") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.size(10.dp))
                    OutlinedTextField(
                        value = pinConfirm,
                        onValueChange = { pinConfirm = it },
                        label = { Text("再输一次") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.size(16.dp))
                    Button(
                        onClick = {
                            if (pin != pinConfirm) {
                                scope.launch { snackbar.showSnackbar("两次输入不一致") }
                            } else {
                                vm.setPin(pin)
                                pin = ""
                                pinConfirm = ""
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("设置并进入") }
                }

                !unlocked -> {
                    Spacer(Modifier.size(48.dp))
                    Icon(
                        imageVector = Icons.Outlined.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(Modifier.size(14.dp))
                    Text("私密空间已锁定", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.size(18.dp))
                    OutlinedTextField(
                        value = pin,
                        onValueChange = { pin = it },
                        label = { Text("输入密码") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.size(12.dp))
                    Button(
                        onClick = { vm.unlockWithPin(pin) },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("解锁") }
                    if (activity != null && biometricEnabled && BiometricHelper.isAvailable(activity)) {
                        Spacer(Modifier.size(8.dp))
                        IconButton(onClick = {
                            scope.launch { vm.unlockWithBiometric(activity) }
                        }) {
                            Icon(Icons.Outlined.Fingerprint, contentDescription = "指纹/面容解锁")
                        }
                        Text(
                            "使用指纹、面容或锁屏密码",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                libraries.isEmpty() -> EmptyState(
                    icon = Icons.Outlined.LockOpen,
                    title = "还没有私密图库",
                    description = "回到首页长按一个图库，选择「设为私密」，它就会从首页隐藏并出现在这里。"
                )

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(libraries, key = { it.id }) { library ->
                        ListItem(
                            headlineContent = { Text(library.name) },
                            supportingContent = { Text("${library.itemCount} 张 · 已隐藏") },
                            leadingContent = { Icon(Icons.Outlined.Lock, contentDescription = null) },
                            modifier = Modifier.fillMaxWidth().clickable { onOpenLibrary(library.id) }
                        )
                    }
                }
            }
        }
    }
}
