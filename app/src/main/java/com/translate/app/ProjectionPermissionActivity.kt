package com.translate.app

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle

class ProjectionPermissionActivity : Activity() {

    companion object {
        private const val REQUEST_CODE = 1001

        fun start(context: Context, frameX: Int, frameY: Int, frameW: Int, frameH: Int, density: Int) {
            val intent = Intent(context, ProjectionPermissionActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra("frame_x", frameX)
                putExtra("frame_y", frameY)
                putExtra("frame_w", frameW)
                putExtra("frame_h", frameH)
                putExtra("screen_density", density)
            }
            context.startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(projectionManager.createScreenCaptureIntent(), REQUEST_CODE)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_CODE && resultCode == RESULT_OK && data != null) {
            val prefs = getSharedPreferences("translate_prefs", MODE_PRIVATE)
            val projectionData = data.getStringExtra("projection_data")
                ?: data.toUri(0)
            prefs.edit().putString("projection_data", projectionData).apply()

            val serviceIntent = Intent(this, SquareFloatService::class.java)
            startService(serviceIntent)
        }

        finish()
    }
}
