package com.littlebridge.vidyaprayag.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.littlebridge.vidyaprayag.ui.components.*

enum class AuthRole {
    ADMIN, PARENT
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthBottomSheet(
    onDismissRequest: () -> Unit,
    sheetState: SheetState = rememberModalBottomSheetState()
) {
    var role by remember { mutableStateOf(AuthRole.ADMIN) }
    var isOtpSent by remember { mutableStateOf(false) }
    var contactInfo by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .width(48.dp)
                    .height(6.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.outlineVariant)
            )
        },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(32.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "Access EduTrust",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            
            Text(
                text = if (isOtpSent) "Verify your identity" else "Enter your credentials to continue",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(modifier = Modifier.height(32.dp))

            if (!isOtpSent) {
                EntrySection(
                    role = role,
                    onRoleChange = { role = it },
                    contactInfo = contactInfo,
                    onContactInfoChange = { contactInfo = it },
                    onContinue = { isOtpSent = true }
                )
            } else {
                OtpSection(
                    contactInfo = contactInfo,
                    onVerify = { /* Handle login */ },
                    onBack = { isOtpSent = false }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Footer Terms
            Text(
                text = "By continuing, you agree to EduTrust's Terms of Service and Privacy Policy. Protected by reCAPTCHA.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                textAlign = TextAlign.Center,
                lineHeight = 16.sp
            )
        }
    }
}

@Composable
private fun EntrySection(
    role: AuthRole,
    onRoleChange: (AuthRole) -> Unit,
    contactInfo: String,
    onContactInfoChange: (String) -> Unit,
    onContinue: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // Role Toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .padding(4.dp)
        ) {
            RoleToggleButton(
                text = "Administrator",
                icon = Icons.Default.SupervisorAccount,
                isSelected = role == AuthRole.ADMIN,
                onClick = { onRoleChange(AuthRole.ADMIN) },
                modifier = Modifier.weight(1f)
            )
            RoleToggleButton(
                text = "Parent",
                icon = Icons.Default.FamilyRestroom,
                isSelected = role == AuthRole.PARENT,
                onClick = { onRoleChange(AuthRole.PARENT) },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Email or Mobile Number",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )

        OutlinedTextField(
            value = contactInfo,
            onValueChange = onContactInfoChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("admin@academy.edu", color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)) },
            leadingIcon = { Icon(Icons.Default.Mail, contentDescription = null, tint = MaterialTheme.colorScheme.outline) },
            shape = RoundedCornerShape(16.dp),
            colors = TextFieldDefaults.colors(
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                unfocusedIndicatorColor = Color.Transparent,
                focusedIndicatorColor = MaterialTheme.colorScheme.primary
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        EduTrustPrimaryButton(
            text = "Continue",
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Divider
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
            Text(
                text = "TRUSTED SECURITY",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
                letterSpacing = 1.sp
            )
            HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Social Logins
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SocialButton(
                text = "Google",
                icon = Icons.Default.AccountCircle, // Placeholder
                modifier = Modifier.weight(1f)
            )
            SocialButton(
                text = "Apple ID",
                icon = Icons.Default.Smartphone, // Using smartphone as placeholder for Apple ID
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun RoleToggleButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent)
            .clickable { onClick() }
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline
        )
    }
}

@Composable
private fun SocialButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = {},
        modifier = modifier.height(56.dp),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(text, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun OtpSection(
    contactInfo: String,
    onVerify: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "We've sent a 6-digit code to $contactInfo",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        // OTP Fields placeholder
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            repeat(6) {
                OtpField()
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        EduTrustPrimaryButton(
            text = "Verify & Secure Access",
            onClick = onVerify,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        TextButton(onClick = {}) {
            Text("Resend code", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(8.dp))

        TextButton(onClick = onBack) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Back to entry", color = MaterialTheme.colorScheme.outline)
            }
        }
    }
}

@Composable
private fun OtpField() {
    Box(
        modifier = Modifier
            .size(width = 44.dp, height = 56.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center
    ) {
        // Just a placeholder for visual design
        Text("-", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.outlineVariant)
    }
}
