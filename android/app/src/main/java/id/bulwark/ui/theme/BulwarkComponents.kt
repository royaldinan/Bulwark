package id.bulwark.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val BulwarkPillShape = RoundedCornerShape(percent = 50)

// VERIFY: Icons.AutoMirrored.Filled.ArrowBack tersedia di material-icons-core >= 1.6;
// bila versi Compose lebih lama, ganti ke Icons.Default.ArrowBack.
@Composable
fun BulwarkTopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    onOverflow: (() -> Unit)? = null,
) {
    Box(
        modifier = modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = title,
            color = BulwarkNavy,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        if (onBack != null) {
            IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart)) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Kembali",
                    tint = BulwarkNavy,
                )
            }
        }
        if (onOverflow != null) {
            IconButton(onClick = onOverflow, modifier = Modifier.align(Alignment.CenterEnd)) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Lainnya",
                    tint = BulwarkNavy,
                )
            }
        }
    }
}

@Composable
fun BulwarkPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val brush = if (enabled) {
        Brush.horizontalGradient(listOf(BulwarkGradientStart, BulwarkGradientEnd))
    } else {
        SolidColor(BulwarkDisabledFill)
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(BulwarkPillShape)
            .background(brush)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (enabled) BulwarkWhite else BulwarkTextDisabled,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
fun BulwarkSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(BulwarkPillShape)
            .background(if (enabled) BulwarkSurface else BulwarkDisabledFill)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (enabled) BulwarkNavy else BulwarkTextDisabled,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
fun <T> BulwarkSegmentedToggle(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: (T) -> String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(BulwarkPillShape)
            .background(BulwarkSurface)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clip(BulwarkPillShape)
                    .background(if (isSelected) BulwarkNavy else Color.Transparent)
                    .clickable { onSelect(option) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label(option),
                    color = if (isSelected) BulwarkWhite else BulwarkNavy,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
fun BulwarkInfoCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(24.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(BulwarkWhite)
            .border(1.dp, BulwarkLine, shape)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = BulwarkNavy)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = BulwarkNavy, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(
                text = subtitle,
                color = BulwarkTextSecondary,
                fontSize = 13.sp,
                textDecoration = TextDecoration.Underline,
            )
        }
        Spacer(Modifier.width(12.dp))
        Box(
            modifier = Modifier
                .clip(BulwarkPillShape)
                .background(BulwarkSurface)
                .clickable(onClick = onAction)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text(actionLabel, color = BulwarkTextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
fun BulwarkOptionCard(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    value: String? = null,
    badge: String? = null,
) {
    val shape = RoundedCornerShape(24.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) BulwarkSelectedCard else BulwarkWhite)
            .border(1.dp, if (selected) BulwarkSelectedCard else BulwarkLine, shape)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
            colors = RadioButtonDefaults.colors(
                selectedColor = BulwarkGradientEnd,
                unselectedColor = BulwarkLine,
            ),
        )
        Spacer(Modifier.width(4.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, color = BulwarkNavy, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                if (selected && badge != null) {
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(BulwarkPillShape)
                            .background(BulwarkBadge)
                            .padding(horizontal = 10.dp, vertical = 3.dp),
                    ) {
                        Text(badge, color = BulwarkWhite, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            if (subtitle.isNotEmpty()) {
                Text(subtitle, color = BulwarkTextSecondary, fontSize = 13.sp)
            }
        }
        if (value != null) {
            Spacer(Modifier.width(8.dp))
            Text(value, color = BulwarkNavy, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun BulwarkStatusHero(
    title: String,
    subtitle: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.linearGradient(listOf(BulwarkNavy, BulwarkHeroEnd)))
            .padding(20.dp),
    ) {
        Text(title, color = BulwarkWhite, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(subtitle, color = BulwarkWhite.copy(alpha = 0.75f), fontSize = 14.sp)
        Spacer(Modifier.height(16.dp))
        Box(
            modifier = Modifier
                .clip(BulwarkPillShape)
                .border(1.dp, BulwarkWhite.copy(alpha = 0.6f), BulwarkPillShape)
                .clickable(onClick = onAction)
                .padding(horizontal = 18.dp, vertical = 10.dp),
        ) {
            Text(actionLabel, color = BulwarkWhite, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun BulwarkStatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(BulwarkSurface)
            .padding(16.dp),
    ) {
        Text(label, color = BulwarkTextSecondary, fontSize = 13.sp)
        Spacer(Modifier.height(8.dp))
        Text(value, color = BulwarkNavy, fontSize = 26.sp, fontWeight = FontWeight.Bold)
    }
}

data class BulwarkNavItem(
    val label: String,
    val icon: ImageVector,
)

@Composable
fun BulwarkBottomBar(
    items: List<BulwarkNavItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(BulwarkWhite)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEachIndexed { index, item ->
            val selected = index == selectedIndex
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onSelect(index) }
                    .padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = item.label,
                    tint = if (selected) BulwarkNavy else BulwarkTextDisabled,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = item.label,
                    color = if (selected) BulwarkNavy else BulwarkTextDisabled,
                    fontSize = 11.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
    }
}
