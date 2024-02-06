package //TODO

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.bobrarium_v2.R
import java.io.FileNotFoundException
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sign

private const val TAG = "ImageCropDialog"

private val boxSize = 300.dp

private fun Uri.getBitmap(context: Context): Bitmap?{
    try {
        context.contentResolver.openInputStream(this).use {
            it?: return null
            return BitmapFactory.decodeStream(it)
        }
    }
    catch (e: FileNotFoundException){
        e.printStackTrace()
    }
    return null
}
private fun Bitmap.getResizedBitmap(maxSize: Int): Bitmap {
    var newWidth = width
    var newHeight = height
    val bitmapRatio = newWidth.toFloat() / newHeight.toFloat()
    if (bitmapRatio > 1) {
        newWidth = maxSize
        newHeight = (newWidth / bitmapRatio).toInt()
    } else {
        newHeight = maxSize
        newWidth = (newHeight * bitmapRatio).toInt()
    }
    return Bitmap.createScaledBitmap(this, newWidth, newHeight, true)
}
private val Bitmap.minSize get() = min(width, height)
private operator fun Size.minus(other: Float) = Size(width - other, height - other)
private fun Size.swap() = Size(height, width)
private fun IntSize.swap() = IntSize(height, width)
@Stable private fun Modifier.mirrorHorizontal() = this.scale(scaleX = -1f, scaleY = 1f)
private fun Bitmap.getIntSize() = IntSize(width, height)
private fun Offset.toIntOffset() = IntOffset(x.toInt(), y.toInt())

private fun checkImageOffset(currentOffset: Offset, offsetChange: Offset, imageMinusBox: Size): Offset{
    val futureOffset = currentOffset + offsetChange
    val futureOffsetX2 = futureOffset * 2F
    return Offset(
        if (abs(futureOffsetX2.x) <= imageMinusBox.width) futureOffset.x else currentOffset.x,
        if (abs(futureOffsetX2.y) <= imageMinusBox.height) futureOffset.y else currentOffset.y
    )
}
private fun globalOffsetChange(offset: Offset, rotation: Float): Offset{
    return when(rotation){
        0f -> offset
        90f -> Offset(-offset.y, offset.x)
        180f -> offset * -1f
        270f -> Offset(offset.y, -offset.x)
        else -> offset
    }
}
fun changeOffsetWhenScale(zoomChange: Float, imageRealSize: Size, boxSizePx: Float, offset: Offset): Offset {
    val oneMinusSizeChange = (1 - zoomChange)
    val offsetX2 = offset * 2f

    if (zoomChange >= 1) return offset * -oneMinusSizeChange
    return Offset(
        if (imageRealSize.width * zoomChange - abs(offsetX2.x) < boxSizePx)
            imageRealSize.width * oneMinusSizeChange / 2 * (-offset.x.sign)
        else -offset.x * oneMinusSizeChange,

        if (imageRealSize.height * zoomChange - abs(offsetX2.y) < boxSizePx)
            imageRealSize.height * oneMinusSizeChange / 2 * (-offset.y.sign)
        else -offset.y * oneMinusSizeChange,
    )
}

@Composable
fun ImageCropDialog(uri: Uri, onResult: (result: Bitmap?) -> Unit) {
    Dialog(
        onDismissRequest = { onResult(null) },
        properties = DialogProperties(
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        BoxWithConstraints (
            modifier = Modifier
                .fillMaxSize()
        ) {
            val context = LocalContext.current
            val density = LocalDensity.current
            val boxSizePx = remember(density){
                with(density) { boxSize.toPx() }
            }
            val imageBitmap = remember(uri) {
                val bitmap = uri.getBitmap(context)?.getResizedBitmap(boxSizePx.toInt())
                if (bitmap == null) onResult(null)
                bitmap!!
            }

            var scale by remember { mutableFloatStateOf(boxSizePx / imageBitmap.minSize) }
            var rotation by remember { mutableFloatStateOf(0f) }
            var offset by remember { mutableStateOf(Offset.Zero) }

            var imageRealSize by remember {
                mutableStateOf(Size(imageBitmap.width * scale, imageBitmap.height * scale) )
            }
            val imageMinusBox by remember (imageRealSize, boxSizePx) {
                derivedStateOf { imageRealSize - boxSizePx }
            }

            val state = rememberTransformableState { zoomChange, offsetChange, _ ->
                offset = checkImageOffset(
                    offset,
                    globalOffsetChange(offsetChange * scale, rotation),
                    imageMinusBox
                )
                if (imageBitmap.minSize * scale * zoomChange >= boxSizePx) {
                    offset += changeOffsetWhenScale(
                        zoomChange,
                        imageRealSize,
                        boxSizePx,
                        offset
                    )
                    imageRealSize *= zoomChange
                    scale *= zoomChange
                }
            }

            AsyncImage(
                modifier = Modifier
                    .align(Alignment.Center)
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        rotationZ = rotation,
                        translationX = offset.x,
                        translationY = offset.y
                    )
                    .transformable(state = state),
                model = imageBitmap,
                contentDescription = stringResource(id = R.string.image),
                contentScale = ContentScale.Fit
            )

            Box(
                modifier = Modifier
                    .background(
                        Color.Gray.copy(alpha = .5f),
                        RoundedCornerShape(16.dp)
                    )
                    .size(boxSize)
                    .fillMaxWidth()
                    .align(Alignment.Center)
            )

            Card (
                modifier = Modifier
                    .align(Alignment.TopCenter)
            ){
                Row (
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    IconButton(onClick = {
                        rotation = (rotation + 270) % 360
                        offset = Offset.Zero
                        imageRealSize = imageRealSize.swap()
                    }) {
                        Icon (
                            imageVector = ImageVector.vectorResource(id = R.drawable.baseline_rotate_90_degrees_ccw_24),
                            contentDescription = stringResource(id = R.string.rotateLeft)
                        )
                    }
                    IconButton(onClick = {
                        rotation = (rotation + 90) % 360
                        offset = Offset.Zero
                        imageRealSize = imageRealSize.swap()
                    }) {
                        Icon (
                            modifier = Modifier.mirrorHorizontal(),
                            imageVector = ImageVector.vectorResource(id = R.drawable.baseline_rotate_90_degrees_ccw_24),
                            contentDescription = stringResource(id = R.string.rotateRight)
                        )
                    }
                }
            }
            Card (
                modifier = Modifier
                    .align(Alignment.BottomCenter)
            ) {
                Row (
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    IconButton(
                        onClick = { onResult(null) }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = stringResource(id = R.string.cancel)
                        )
                    }
                    IconButton(
                        onClick = {
                            scale = boxSizePx / imageBitmap.minSize
                            imageRealSize = Size(imageBitmap.width * scale, imageBitmap.height * scale)
                            rotation = 0f
                            offset = Offset.Zero
                        }
                    ) {
                        Icon(
                            imageVector = ImageVector.vectorResource(id = R.drawable.baseline_restart_alt_24),
                            contentDescription = stringResource(id = R.string.cancel)
                        )
                    }
                    IconButton(
                        onClick = {
                            onResult(cropImage(imageBitmap, scale, offset, rotation, boxSizePx))
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = stringResource(id = R.string.finish)
                        )
                    }
                }
            }
        }
    }
}
private operator fun IntSize.minus(other: Int) = IntOffset(width - other, height - other)
private operator fun IntOffset.minus(other: Int) = IntOffset(x - other, y - other)
private fun IntSize.asGlobal(rotation: Float) = if (rotation % 180 == 0f) this else swap()


private fun cropImage(source: Bitmap, scale: Float, offset: Offset, rotation: Float, boxSize: Float): Bitmap {
    val matrix = Matrix().apply {
        setRotate(rotation)
    }
    val tempBitmap = Bitmap.createBitmap(
        source,
        0,
        0,
        source.width,
        source.height,
        matrix,
        false
    )


    val imageSize = source.getIntSize().asGlobal(rotation)

    val scaledBoxSize = (boxSize / scale).toInt()
    val scaledOffset = (offset / scale).toIntOffset()

    val cords = (imageSize - scaledBoxSize) * .5f - scaledOffset

    return Bitmap.createBitmap(
        tempBitmap,
        cords.x,
        cords.y,
        scaledBoxSize,
        scaledBoxSize
    )
}
