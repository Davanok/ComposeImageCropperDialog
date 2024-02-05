@Preview(showBackground = true)
@Composable
private fun Test(){
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    val picker =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()){
            imageUri = it
        }

    var imageResult by remember { mutableStateOf<Bitmap?>(null) }
    imageUri?.let { uri ->
        ImageCropDialog(uri) {
            imageResult = it
            imageUri = null
        }
    }
    imageResult?.let {
        Dialog(onDismissRequest = { imageResult = null }) {
            AsyncImage(
                modifier = Modifier.fillMaxWidth(),
                model = it,
                contentDescription = "Current image"
            )
        }
    }
    Button(
        modifier = Modifier.fillMaxWidth(),
        onClick = {
            picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
    ) {
        Text(text = "pick")
    }
}
