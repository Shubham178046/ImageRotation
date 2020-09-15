package com.example.imagerotation

import android.Manifest
import android.app.Activity
import android.content.Context
import android.graphics.*
import android.media.ExifInterface
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import com.bumptech.glide.Glide
import com.gun0912.tedpermission.PermissionListener
import com.gun0912.tedpermission.TedPermission
import com.miguelbcr.ui.rx_paparazzo2.RxPaparazzo
import com.miguelbcr.ui.rx_paparazzo2.entities.FileData
import com.miguelbcr.ui.rx_paparazzo2.entities.Response
import com.miguelbcr.ui.rx_paparazzo2.entities.size.CustomMaxSize
import com.miguelbcr.ui.rx_paparazzo2.entities.size.Size
import com.yalantis.ucrop.UCrop
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.activity_main.*
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        btnCaptureImage.setOnClickListener {
            TedPermission.with(this).setPermissions(Manifest.permission.CAMERA)
                    .setPermissionListener(object : PermissionListener {
                        override fun onPermissionGranted() {
                            val size = CustomMaxSize(512)
                            val takeOnePhoto: Observable<Response<MainActivity, FileData>> =
                                    pickSingle(null, size)!!.usingCamera()
                            processSingle(takeOnePhoto)
                        }

                        override fun onPermissionDenied(deniedPermissions: List<String>) {
                            println("error" + deniedPermissions.toString())
                        }
                    }).check()
        }
    }

    private fun pickSingle(
            options: UCrop.Options?,
            size: Size
    ): RxPaparazzo.SingleSelectionBuilder<MainActivity>? {
        var size = size
        val resized: RxPaparazzo.SingleSelectionBuilder<MainActivity> =
                RxPaparazzo.single(this)
                        .setMaximumFileSizeInBytes(1000000)
                        .size(size)
                        .sendToMediaScanner()
        if (options != null) {
            resized.crop(options)
        }
        return resized
    }

    fun checkResultCode(context: Context, code: Int): Boolean {
        if (code == RxPaparazzo.RESULT_DENIED_PERMISSION) {
            showUserDidNotGrantPermissions(context)
        } else if (code == RxPaparazzo.RESULT_DENIED_PERMISSION_NEVER_ASK) {
            showUserDidNotGrantPermissionsNeverAsk(context)
        } else if (code != Activity.RESULT_OK) {
            showUserCanceled(context)
        }
        return code == Activity.RESULT_OK
    }

    private fun showUserCanceled(context: Context) {
        Toast.makeText(context, context.getString(R.string.user_canceled), Toast.LENGTH_SHORT)
                .show()
    }

    private fun showUserDidNotGrantPermissions(context: Context) {
        Toast.makeText(
                context,
                context.getString(R.string.user_did_not_grant_permissions),
                Toast.LENGTH_SHORT
        ).show()
    }

    private fun showUserDidNotGrantPermissionsNeverAsk(context: Context) {
        Toast.makeText(
                context,
                context.getString(R.string.user_did_not_grant_permissions_never_ask),
                Toast.LENGTH_SHORT
        ).show()
    }

    private fun processSingle(
            pickUsingGallery: Observable<Response<MainActivity, FileData>>
    ) {
        val point = Point()
        point.set(200, 200)
        pickUsingGallery
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        { response: com.miguelbcr.ui.rx_paparazzo2.entities.Response<MainActivity, FileData> ->
                            if (checkResultCode(
                                            this,
                                            response.resultCode()
                                    )
                            ) {

                                try {
                                    var mResponseFile = response.data()!!.getFile()
                                    if (mResponseFile != null && mResponseFile!!.exists()) {
                                        val rotation: Int =
                                                getImageOrientation(mResponseFile.path)
                                        var outStream: OutputStream? = null
                                        var bitmap = BitmapFactory.decodeFile(mResponseFile.path)
                                        bitmap = checkRotationFromCamera(bitmap!!, rotation)
                                        bitmap = Bitmap.createScaledBitmap(
                                                bitmap,
                                                (bitmap!!.width.toFloat() * 1f).toInt(),
                                                (bitmap.height.toFloat() * 1f).toInt(),
                                                false
                                        )
                                        Log.d("TAG", "processSingle: '" + mResponseFile.path)
                                        outStream = FileOutputStream(mResponseFile.path)
                                        val c = Calendar.getInstance()
                                        val curFormater = SimpleDateFormat("dd/MM/yyyy HH:mm:ss")
                                        val dateTime: String =
                                                curFormater.format(c.time) + "\n" + "22.5682" + "-" + "78.9565"
                                        bitmap = mark(bitmap, dateTime)
                                        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outStream)
                                        outStream.flush()
                                        outStream.close()
                                        Log.d("mResponse", "processSingle: "+mResponseFile)
                                        Glide.with(this).asBitmap()
                                                .load(mResponseFile)
                                                .into(imgRotate)
                                        val mBitmap = BitmapFactory.decodeFile(mResponseFile.path)

                                    }
                                } catch (e: Exception) {
                                    println("image picker crash" + e.localizedMessage)
                                }
                            }
                        },
                        { throwable: Throwable ->
                            throwable.printStackTrace()
                        }
                )
    }

    fun mark(src: Bitmap, watermark: String?): Bitmap? {
        val w = src.width
        val h = src.height
        val bounds = Rect()
        var noOfLines = 0
        for (line in watermark!!.split("\n")) {
            noOfLines++
        }

        val result = Bitmap.createBitmap(w, h, src.config)
        val canvas = Canvas(result)
        canvas.drawBitmap(src, 0f, 0f, null)
        val paint = Paint()
        paint.setColor(Color.WHITE)
        paint.textSize = 12F
        paint.alpha = 80
        paint.isAntiAlias = true
        paint.setUnderlineText(false)
        paint.getTextBounds(watermark, 0, watermark.length, bounds)
        var y: Float = (src.height - bounds.height() * noOfLines).toFloat()
        for (line in watermark.split("\n")) {
            canvas.drawText(
                    line!!,
                    ((src.width * 55) / 100).toFloat(),
                    y,
                    paint
            )
            y += paint.descent() - paint.ascent()
        }
        return result
    }

    fun checkRotationFromCamera(bitmap: Bitmap, rotate: Int): Bitmap? {
        val matrix = Matrix()
        matrix.postRotate(rotate.toFloat())
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    fun getImageOrientation(imagePath: String?): Int {
        var rotate = 0
        try {
            val exif = imagePath?.let { ExifInterface(it) }
            val orientation: Int = exif!!.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_270 -> rotate = 270
                ExifInterface.ORIENTATION_ROTATE_180 -> rotate = 180
                ExifInterface.ORIENTATION_ROTATE_90 -> rotate = 90
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return rotate
    }
}