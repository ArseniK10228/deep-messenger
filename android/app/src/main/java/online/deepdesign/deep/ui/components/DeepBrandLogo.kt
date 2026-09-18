package online.deepdesign.deep.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import online.deepdesign.deep.R

@Composable
fun DeepBrandLogo(
    size: Dp,
    modifier: Modifier = Modifier,
    cornerFraction: Float = 0.22f
) {
    val corner = (size.value * cornerFraction).dp
    Image(
        painter = painterResource(R.drawable.deep_logo),
        contentDescription = "Deep",
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(corner))
    )
}
