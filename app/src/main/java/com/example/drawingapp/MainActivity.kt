package com.example.drawingapp

import android.Manifest
import android.app.Dialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.*
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import vadiole.colorpicker.ColorModel
import vadiole.colorpicker.ColorPickerDialog
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream


class MainActivity : AppCompatActivity(), View.OnClickListener {

    private var drawingView: DrawingView? = null
    private var selectedColor: ImageButton? = null
    private var brushIconBtn: ImageButton? = null
    private var colorRandom: ImageButton? = null
    private var savingDialog: Dialog? = null
    private var savedPreviewDialog : Dialog? = null
    private var resultPreviewFilePath : String? = null


    private val openGalleryLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()){
            result ->
            if(result.resultCode == RESULT_OK && result.data!=null){
                val imageBackGround : ImageView = findViewById(R.id.image_view_bg)

                imageBackGround.setImageURI(result.data?.data)
            }
        }

    private val storageRequestPermissionLauncher : ActivityResultLauncher<Array<String>> =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()){}


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        drawingView = findViewById(R.id.drawing_view)
        drawingView?.setBrushSize(10F)

        setPaintColors()

        brushIconBtn = findViewById(R.id.image_button_brush)
        brushIconBtn!!.setOnClickListener() {
            showBrushSizeDialog()
        }

        val selectImageBtn : ImageButton = findViewById(R.id.select_image_button)
        selectImageBtn.setOnClickListener{

            if(isReadStorageAllowed()){
                requestStoragePermission()
                val pickIntent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                openGalleryLauncher.launch(pickIntent)
            }

        }

        //Undo Functionality
        val undoButton : ImageButton = findViewById(R.id.undo_btn)
        undoButton.setOnClickListener{
            drawingView?.onUndo()
        }

        //Redo Functionality
        val redoButton : ImageButton = findViewById(R.id.redo_btn)
        redoButton.setOnClickListener{
            drawingView?.onRedo()
        }

        val saveButton : ImageButton = findViewById(R.id.save_button)
        saveButton.setOnClickListener{

            if (isReadStorageAllowed()){
                lifecycleScope.launch{
                    //Shows Saving Dialog
                    savingDialog()

                    saveBitmapFile(getBitmapFromView(findViewById(R.id.frame_layout_view)))
                }
            }

        }


    }

    //Paint Colors
    private fun setPaintColors() {
        val color1: ImageButton = findViewById(R.id.color_1)
        val color2: ImageButton = findViewById(R.id.color_2)
        val color3: ImageButton = findViewById(R.id.color_3)
        val color4: ImageButton = findViewById(R.id.color_4)
        val color5: ImageButton = findViewById(R.id.color_5)
        val color6: ImageButton = findViewById(R.id.color_6)
        val color7: ImageButton = findViewById(R.id.color_7)
        val color8: ImageButton = findViewById(R.id.color_8)
        val color9: ImageButton = findViewById(R.id.color_9)
        colorRandom = findViewById(R.id.color_random)

        color1.setOnClickListener(this)
        color2.setOnClickListener(this)
        color3.setOnClickListener(this)
        color4.setOnClickListener(this)
        color5.setOnClickListener(this)
        color6.setOnClickListener(this)
        color7.setOnClickListener(this)
        color8.setOnClickListener(this)
        color9.setOnClickListener(this)

        colorRandom!!.setOnClickListener(){

            colorPickerDialog()

            selectedColor?.setImageDrawable(
                ContextCompat.getDrawable(this, R.drawable.pallet)
            )

        }
    }

    //Request for Storage Permission
    private fun requestStoragePermission(){
        if(ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.READ_EXTERNAL_STORAGE)){
            deniedPermissionDialog("Storage Permission Required", "Drawing App need storage permission to enable this feature.")
        } else {
            storageRequestPermissionLauncher.launch(arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE))
        }
    }

    private fun isReadStorageAllowed(): Boolean{
        if(ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED){
            return true
        } else {
            requestStoragePermission()
        }
        return false
    }

    //Show brush sizes
    private fun showBrushSizeDialog() {
        val brushDialog = Dialog(this)
        brushDialog.setContentView(R.layout.dialog_brush_size)

        val smallBtn: ImageButton = brushDialog.findViewById(R.id.image_button_small_brush)
        smallBtn.setOnClickListener() {
            drawingView?.setBrushSize(10F)
            brushDialog.dismiss()
        }

        val mediumBtn: ImageButton = brushDialog.findViewById(R.id.image_button_medium_brush)
        mediumBtn.setOnClickListener() {
            drawingView?.setBrushSize(15F)
            brushDialog.dismiss()
        }

        val largeBtn: ImageButton = brushDialog.findViewById(R.id.image_button_large_brush)
        largeBtn.setOnClickListener() {
            drawingView?.setBrushSize(30F)
            brushDialog.dismiss()
        }

        brushDialog.show()
    }

    //Change paint on pallets
    private fun changePaintColor(view: View) {
        if (view != selectedColor) {
            val imageButton = view as ImageButton
            val imageButtonColor = imageButton.tag.toString()
            drawingView?.setColor(Color.parseColor(imageButtonColor))

            imageButton.setImageDrawable(
                ContextCompat.getDrawable(this, R.drawable.pallet_selected)
            )

            selectedColor?.setImageDrawable(
                ContextCompat.getDrawable(this, R.drawable.pallet)
            )

            selectedColor = view

        }
    }

    override fun onClick(view: View?) {
        changePaintColor(view!!)
    }

    //Color picker
    private fun colorPickerDialog(){
        var dialogSelectedColor = Color.BLACK
        val colorPicker: ColorPickerDialog = ColorPickerDialog.Builder()
            .setInitialColor(dialogSelectedColor)
            .setColorModel(ColorModel.ARGB)
            .setColorModelSwitchEnabled(true)
            .setButtonOkText(R.string.Select)
            .setButtonCancelText(R.string.Close)
            .onColorSelected { color: Int ->
                drawingView?.setColor(color)
                dialogSelectedColor = color
                colorRandom!!.setColorFilter(color)
            }
            .create()
        colorPicker.show(supportFragmentManager, "color_picker")

    }

    //Dialog for denied permission
    private fun deniedPermissionDialog(title: String, message: String){

        val builder: AlertDialog.Builder = AlertDialog.Builder(this)
        builder.setTitle(title)
            .setMessage(message)
            .setPositiveButton("Ok"){dialog, _-> dialog.dismiss()}

        builder.create().show()

    }

    //Gets and create the bitmap from the FrameLayout View
    private fun getBitmapFromView(view: View): Bitmap {
        val returnedBitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(returnedBitmap)
        val bgDrawable = view.background

        if (bgDrawable != null){
            bgDrawable.draw(canvas)
        } else {
            canvas.drawColor(Color.WHITE)
        }

        view.draw(canvas)

        return returnedBitmap
    }

    private var fileName : File? = null
    private suspend fun saveBitmapFile(mBitmap: Bitmap?): String {
        var result = ""

        withContext(Dispatchers.IO){
            if(mBitmap != null){
                try {
                    val bytes = ByteArrayOutputStream()
                    mBitmap.compress(Bitmap.CompressFormat.PNG, 90, bytes)

                    val file = File(externalCacheDir?.absoluteFile.toString()
                            + File.separator + "DrawingApp_" + System.currentTimeMillis() / 1000 + ".png")

                    fileName = file

                    val fileOutPut = FileOutputStream(file)
                    fileOutPut.write(bytes.toByteArray())
                    fileOutPut.close()

                    result = file.absolutePath
                    resultPreviewFilePath = result

                    runOnUiThread{
                        if(result.isNotEmpty()){
                            Toast.makeText(this@MainActivity, "Image Saved: $result", Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(this@MainActivity, "Image not saved due to error.", Toast.LENGTH_LONG).show()
                        }

                        //Dismiss saving dialog
                        savingDialog?.dismiss()

                        Log.e("PathFile", "$result")

                        savedPreviewDialog()
                    }
                } catch (e: Exception){
                    result = ""
                    e.printStackTrace()
                }
            }
        }

        return result
    }

    //Create the saving dialog
    private fun savingDialog(){
        savingDialog = Dialog(this@MainActivity)
        savingDialog?.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        savingDialog?.setContentView(R.layout.save_image_loading_dialog)
        savingDialog?.show()
    }

    //Saved Dialog

    private fun savedPreviewDialog(){
        savedPreviewDialog = Dialog(this@MainActivity)
        savedPreviewDialog?.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        savedPreviewDialog?.setContentView(R.layout.saved_dialog)

        val backBtn : TextView = savedPreviewDialog!!.findViewById(R.id.dialog_back_btn)
        backBtn.setOnClickListener{
            savedPreviewDialog?.dismiss()
        }

        val previewBg : ImageView = savedPreviewDialog!!.findViewById(R.id.saved_image)

        val previewImage : Bitmap = BitmapFactory.decodeFile("$resultPreviewFilePath")
        previewBg.setImageBitmap(previewImage)

        val newDrawingBtn : TextView = savedPreviewDialog!!.findViewById(R.id.dialog_new_drawing_btn)
        newDrawingBtn.setOnClickListener {
            drawingView?.reset()

            val imageBackGround : ImageView = findViewById(R.id.image_view_bg)
            imageBackGround.setImageBitmap(null)
            savedPreviewDialog?.dismiss()
        }

        val shareBtn : TextView = savedPreviewDialog!!.findViewById(R.id.dialog_share_btn)
        shareBtn.setOnClickListener{
            shareImage(FileProvider.getUriForFile(baseContext, "com.example.drawingapp.fileprovider", fileName!!))
        }




        savedPreviewDialog?.show()
    }

    private fun shareImage(uri: Uri){

        val shareIntent = Intent()
        shareIntent.action = Intent.ACTION_SEND
        shareIntent.putExtra(Intent.EXTRA_STREAM, uri)
        shareIntent.type = "image/jpeg"
        startActivity(Intent.createChooser(shareIntent,"Share"))
    }





}