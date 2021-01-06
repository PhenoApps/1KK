package org.wheatgenetics.utils

import android.app.Activity
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import androidx.appcompat.app.AlertDialog
import androidx.databinding.DataBindingUtil
import com.google.zxing.common.DetectorResult
import org.wheatgenetics.imageprocess.DetectWithReferences
import org.wheatgenetics.onekk.R
import org.wheatgenetics.onekk.databinding.DialogCoinRecognitionBinding
import org.wheatgenetics.onekk.databinding.DialogUpdateAnalysisBinding
import org.wheatgenetics.onekk.interfaces.AnalysisUpdateListener
import org.wheatgenetics.onekk.interfaces.BleNotificationListener

class Dialogs {

    companion object {

        fun chooseBleDevice(builder: AlertDialog.Builder, title: String, devices: Array<BluetoothDevice>, function: (BluetoothDevice?) -> Unit) {

            if (devices.isNotEmpty()) {

                builder.setTitle(title)

                //bluetooth devices might have a null name
                builder.setSingleChoiceItems(devices.map { it.name }.toTypedArray() + "None", -1) { dialog, choice ->

                    if (choice > -1 && choice < devices.size) {

                        function(devices[choice])

                    } else if (choice >= devices.size) {

                        function(null)
                    }

                    dialog.dismiss()

                }

                builder.create()

                builder.show()
            }

        }

        /***
         * Generic dialog to run a function if the OK/Neutral button are pressed.
         * If the ok button is pressed the boolean parameter to the function is set to true, false otherwise.
         */
        fun booleanOption(builder: AlertDialog.Builder, title: String,
                          positiveText: String, negativeText: String,
                          neutralText: String, function: (Boolean) -> Unit) {

            builder.apply {

                setTitle(title)

                setPositiveButton(positiveText) { _, _ ->

                    function(true)

                }

                setNeutralButton(neutralText) { _, _ ->
                    
                }

                setNegativeButton(negativeText) { _, _ ->

                    function(false)
                }

                show()
            }
        }

        /**
         * Simple alert dialog to notify the user of a message.
         */
        fun notify(builder: AlertDialog.Builder, title: String) {

            builder.apply {

                setPositiveButton("OK") { _, _ ->

                }
            }

            builder.setTitle(title)
            builder.show()
        }

        /**
         * Simple alert dialog to notify the user of a message.
         */
        fun largeNotify(builder: AlertDialog.Builder, title: String) {

            builder.apply {

                setPositiveButton("OK") { _, _ ->

                }
            }

            builder.setMessage(title)
            builder.show()
        }

        fun onOk(builder: AlertDialog.Builder, title: String, cancel: String, ok: String, function: (Boolean) -> Unit) {

            builder.apply {

                setCancelable(false)

                setNegativeButton(cancel) { _, _ ->

                    function(false)

                }

                setPositiveButton(ok) { _, _ ->

                    function(true)

                }

                setTitle(title)

                create()

                show()
            }
        }

        fun askForExportType(builder: AlertDialog.Builder, title: String, options: Array<String>, function: (String) -> Unit) {

            builder.setTitle(title)

            builder.setSingleChoiceItems(options, 0) { dialog, choice ->

                function(options[choice])

                dialog.dismiss()

            }

            builder.create()

            builder.show()
        }

        fun askAcceptableImage(activity: Activity, builder: AlertDialog.Builder, title: String, result: DetectWithReferences.Result, function: ((Bitmap?) -> Unit)? = null, onDecline: (() -> Unit)? = null) {

            val binding = DataBindingUtil.inflate<DialogCoinRecognitionBinding>(activity.layoutInflater, R.layout.dialog_coin_recognition, null, false)

            binding.visible = true

            binding.resultView.rotation = 90f

            binding.resultView.setImageBitmap(result.dst)

            builder.setTitle(title)

            builder.setPositiveButton(R.string.accept) { dialog, which ->

                result.src?.let { src ->

                    if (function != null) {
                        function(src)
                    }

                    dialog.dismiss()
                }

            }

            builder.setNegativeButton(R.string.decline) { dialog, which ->

                if (onDecline != null) {
                    onDecline()
                }

                dialog.dismiss()

            }

            builder.setView(binding.root)

            builder.setCancelable(false)

            builder.show()

        }

        fun updateAnalysisDialog(aid: Int, weight: String, activity: Activity, builder: AlertDialog.Builder, title: String, srcBitmap: Bitmap?, dstBitmap: Bitmap?, function: ((Int, Double) -> Unit)? = null) {

            val binding = DataBindingUtil.inflate<DialogUpdateAnalysisBinding>(activity.layoutInflater, R.layout.dialog_update_analysis, null, false)

            binding.resultView.rotation = 90f

            binding.resultView.setImageBitmap(dstBitmap)

            builder.setTitle(title)

            binding.dialogWeightEditText.setText(weight)

            builder.setPositiveButton(R.string.accept) { dialog, which ->

                val weight = binding.dialogWeightEditText.text.toString().toDoubleOrNull() ?: 0.0

                if (function != null) {

                    function(aid, weight)

                }

                dialog.dismiss()

            }

            builder.setNegativeButton(R.string.decline) { dialog, which ->

                dialog.dismiss()

            }

            builder.setView(binding.root)

            builder.setCancelable(false)

            builder.show()

        }
    }
}