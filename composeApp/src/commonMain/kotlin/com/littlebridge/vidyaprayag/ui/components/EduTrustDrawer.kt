package com.littlebridge.vidyaprayag.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.littlebridge.vidyaprayag.ui.theme.AppTheme
import com.littlebridge.vidyaprayag.ui.theme.LocalAppTheme
import com.littlebridge.vidyaprayag.ui.theme.LocalThemeSwitcher

@Composable
fun EduTrustDrawerSheet() {
    ModalDrawerSheet(
        drawerContainerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.width(280.dp)
    ) {
        DrawerHeader()
        DrawerContent()
        Spacer(modifier = Modifier.weight(1f))
        DrawerFooter()
    }
}

@Composable
private fun DrawerHeader() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .padding(32.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.AccountCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp))
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text("Guest User", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Text("Welcome back!", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        EduTrustPrimaryButton(
            text = "Get Started",
            onClick = {},
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun DrawerContent() {
    val currentTheme = LocalAppTheme.current
    val themeSwitcher = LocalThemeSwitcher.current

    Column(modifier = Modifier.padding(24.dp)) {
        DrawerItem(Icons.Default.Home, "Home", isSelected = true)
        DrawerItem(Icons.Default.Search, "Discovery")
        DrawerItem(Icons.Default.Dashboard, "Management")
        DrawerItem(Icons.Default.Hub, "Tech Ecosystem")
        
        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.1f))
        
        Text("THEME", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.outline, letterSpacing = 2.sp)
        Spacer(modifier = Modifier.height(12.dp))
        
        ThemeToggleItem("Day Mode", Icons.Default.LightMode, isSelected = currentTheme == AppTheme.LIGHT) { themeSwitcher(AppTheme.LIGHT) }
        ThemeToggleItem("Night Mode", Icons.Default.DarkMode, isSelected = currentTheme == AppTheme.DARK) { themeSwitcher(AppTheme.DARK) }
        ThemeToggleItem("Midnight", Icons.Default.AutoAwesome, isSelected = currentTheme == AppTheme.MIDNIGHT) { themeSwitcher(AppTheme.MIDNIGHT) }

        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.1f))
        
        DrawerItem(Icons.Default.Login, "Sign In")
        DrawerItem(Icons.Default.Help, "Support")
    }
}

@Composable
private fun DrawerItem(icon: ImageVector, label: String, isSelected: Boolean = false) {
    val backgroundColor = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
    val contentColor = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(backgroundColor)
            .clickable { }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = contentColor)
        Spacer(modifier = Modifier.width(16.dp))
        Text(label, color = contentColor, fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal)
    }
}

@Composable
private fun ThemeToggleItem(label: String, icon: ImageVector, isSelected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f) else Color.Transparent)
            .clickable { onClick() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = if (isSelected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outline, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Text(label, color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, fontSize = 14.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium)
    }
}

@Composable
private fun DrawerFooter() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text("EDUTRUST V2.4", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.outline)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(6.dp).clip(RoundedCornerShape(3.dp)).background(MaterialTheme.colorScheme.secondary))
            Spacer(modifier = Modifier.width(4.dp))
            Text("LIVE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.outline)
        }
    }
}
