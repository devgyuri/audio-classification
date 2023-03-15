/*
 * Copyright 2022 The TensorFlow Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.tensorflow.lite.examples.audio.fragments

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Context.*
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.CompoundButton
import android.widget.RadioGroup
import android.widget.Toast
import android.widget.Toast.*
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.fragment.app.Fragment
import androidx.navigation.Navigation
import com.github.angads25.toggle.interfaces.OnToggledListener
import com.github.angads25.toggle.model.ToggleableView
import com.github.angads25.toggle.widget.LabeledSwitch
import org.tensorflow.lite.examples.audio.AudioClassificationHelper
import org.tensorflow.lite.examples.audio.R
import org.tensorflow.lite.examples.audio.databinding.FragmentAudioBinding
import org.tensorflow.lite.examples.audio.ui.ProbabilitiesAdapter
import org.tensorflow.lite.support.label.Category

interface AudioClassificationListener {
    fun onError(error: String)
    fun onResult(results: List<Category>, inferenceTime: Long)
}

const val LABEL_BABY = 1
const val LABEL_GLASS = 2
const val LABEL_GUN = 3

const val INDEX_BABY = 20
const val INDEX_CRYING = 19
const val INDEX_GLASS = 435
const val INDEX_GUN = 421

class AudioFragment : Fragment() {
    private var _fragmentBinding: FragmentAudioBinding? = null
    private val fragmentAudioBinding get() = _fragmentBinding!!
    private val adapter by lazy { ProbabilitiesAdapter() }
    private var notificationCnt = 0
    private val maxNotificationNum = 10

    private lateinit var audioHelper: AudioClassificationHelper

    private fun displayNotification(id: Int, label: Int) {
        var channel_id = "test"
        var channel_name = "My notification"
        var descriptionText = "Hello world"
        val importance = NotificationManager.IMPORTANCE_HIGH

        if(label == LABEL_BABY) {
            channel_id = "baby"
            channel_name = "Event Occurs"
            descriptionText = "Baby is crying"
        } else if(label == LABEL_GLASS) {
            channel_id = "glass"
            channel_name = "Event Occurs"
            descriptionText = "Glass was broken"
        } else if(label == LABEL_GUN) {
            channel_id = "gun"
            channel_name = "Event Occurs"
            descriptionText = "Gun was fired"
        }

        val builder = Notification.Builder(requireActivity(), channel_id)
            .setSmallIcon(R.drawable.alarm)
            .setContentTitle(channel_name)
            .setContentText(descriptionText)
            .setChannelId(channel_id)
            .setWhen(System.currentTimeMillis())
            .setAutoCancel(true)
            .setShowWhen(true)
        val channel = NotificationChannel(channel_id, channel_name, importance).apply {
            description = descriptionText
        }
        // Register the channel with the system
        val notificationManager: NotificationManager =
            requireActivity().getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)

        notificationManager?.notify(id,  builder.build())
    }

    private val audioClassificationListener = object : AudioClassificationListener {
        override fun onResult(results: List<Category>, inferenceTime: Long) {
            requireActivity().runOnUiThread {
                adapter.categoryList = results
                adapter.notifyDataSetChanged()
                fragmentAudioBinding.bottomSheetLayout.inferenceTimeVal.text =
                    String.format("%d ms", inferenceTime)
                if(audioHelper.checkFlag1) {
                    for(i in results) {
                        if(i.index == INDEX_BABY || i.index == INDEX_CRYING) {
                            displayNotification(notificationCnt%maxNotificationNum, LABEL_BABY)
                            notificationCnt += 1
                        }
                    }
                }
                if(audioHelper.checkFlag2) {
                    for(i in results) {
                        if(i.index == INDEX_GLASS) {
                            displayNotification(notificationCnt%maxNotificationNum, LABEL_GLASS)
                            notificationCnt += 1
                        }
                    }
                }
                if(audioHelper.checkFlag3) {
                    for(i in results) {
                        if(i.index == INDEX_GUN) {
                            displayNotification(notificationCnt%maxNotificationNum, LABEL_GUN)
                            notificationCnt += 1
                        }
                    }
                }
            }
        }

        override fun onError(error: String) {
            requireActivity().runOnUiThread {
                makeText(requireContext(), error, LENGTH_SHORT).show()
                adapter.categoryList = emptyList()
                adapter.notifyDataSetChanged()
            }
        }
    }

    override fun onCreateView(
      inflater: LayoutInflater,
      container: ViewGroup?,
      savedInstanceState: Bundle?
    ): View {
        _fragmentBinding = FragmentAudioBinding.inflate(inflater, container, false)
        return fragmentAudioBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        fragmentAudioBinding.recyclerView.adapter = adapter
        audioHelper = AudioClassificationHelper(
            requireContext(),
            audioClassificationListener
        )

        // play switch
        fragmentAudioBinding.bottomSheetLayout.playSwitch.setOnToggledListener(
            object : OnToggledListener {
                override fun onSwitched(toggleableView: ToggleableView?, isOn: Boolean) {
                    if(isOn) {
                        audioHelper.startAudioClassification()
                        Toast.makeText(getActivity(), "turn on", Toast.LENGTH_LONG).show()
                    } else {
                        audioHelper.stopAudioClassification()
                        Toast.makeText(getActivity(), "trun off", Toast.LENGTH_LONG).show()
                    }
                }
            })

        // Allow the user to change the amount of overlap used in classification. More overlap
        // can lead to more accurate resolves in classification.
        fragmentAudioBinding.bottomSheetLayout.spinnerOverlap.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                  parent: AdapterView<*>?,
                  view: View?,
                  position: Int,
                  id: Long
                ) {
                    audioHelper.stopAudioClassification()
                    audioHelper.overlap = 0.25f * position
                    audioHelper.startAudioClassification()
                }

                override fun onNothingSelected(p0: AdapterView<*>?) {
                    // no op
                }
            }

        // Allow the user to change the max number of results returned by the audio classifier.
        // Currently allows between 1 and 5 results, but can be edited here.
        fragmentAudioBinding.bottomSheetLayout.resultsMinus.setOnClickListener {
            if (audioHelper.numOfResults > 1) {
                audioHelper.numOfResults--
                audioHelper.stopAudioClassification()
                audioHelper.initClassifier()
                fragmentAudioBinding.bottomSheetLayout.resultsValue.text =
                    audioHelper.numOfResults.toString()
            }
        }

        fragmentAudioBinding.bottomSheetLayout.resultsPlus.setOnClickListener {
            if (audioHelper.numOfResults < 5) {
                audioHelper.numOfResults++
                audioHelper.stopAudioClassification()
                audioHelper.initClassifier()
                fragmentAudioBinding.bottomSheetLayout.resultsValue.text =
                    audioHelper.numOfResults.toString()
            }
        }

        // Allow the user to change the confidence threshold required for the classifier to return
        // a result. Increments in steps of 10%.
        fragmentAudioBinding.bottomSheetLayout.thresholdMinus.setOnClickListener {
            if (audioHelper.classificationThreshold >= 0.2) {
                audioHelper.stopAudioClassification()
                audioHelper.classificationThreshold -= 0.1f
                audioHelper.initClassifier()
                fragmentAudioBinding.bottomSheetLayout.thresholdValue.text =
                    String.format("%.2f", audioHelper.classificationThreshold)
            }
        }

        fragmentAudioBinding.bottomSheetLayout.thresholdPlus.setOnClickListener {
            if (audioHelper.classificationThreshold <= 0.8) {
                audioHelper.stopAudioClassification()
                audioHelper.classificationThreshold += 0.1f
                audioHelper.initClassifier()
                fragmentAudioBinding.bottomSheetLayout.thresholdValue.text =
                    String.format("%.2f", audioHelper.classificationThreshold)
            }
        }

        // Allow the user to change the number of threads used for classification
        fragmentAudioBinding.bottomSheetLayout.threadsMinus.setOnClickListener {
            if (audioHelper.numThreads > 1) {
                audioHelper.stopAudioClassification()
                audioHelper.numThreads--
                fragmentAudioBinding.bottomSheetLayout.threadsValue.text = audioHelper
                    .numThreads
                    .toString()
                audioHelper.initClassifier()
            }
        }

        fragmentAudioBinding.bottomSheetLayout.threadsPlus.setOnClickListener {
            if (audioHelper.numThreads < 4) {
                audioHelper.stopAudioClassification()
                audioHelper.numThreads++
                fragmentAudioBinding.bottomSheetLayout.threadsValue.text = audioHelper
                    .numThreads
                    .toString()
                audioHelper.initClassifier()
            }
        }

        fragmentAudioBinding.bottomSheetLayout.spinnerOverlap.setSelection(
            2,
            false
        )

        fragmentAudioBinding.bottomSheetLayout.checkBox1.setOnCheckedChangeListener(
            object : CompoundButton.OnCheckedChangeListener {
                override fun onCheckedChanged(p0: CompoundButton?, p1: Boolean) {
                    audioHelper.checkFlag1 = p1
                }
            }
        )

        fragmentAudioBinding.bottomSheetLayout.checkBox2.setOnCheckedChangeListener(
            object : CompoundButton.OnCheckedChangeListener {
                override fun onCheckedChanged(p0: CompoundButton?, p1: Boolean) {
                    audioHelper.checkFlag2 = p1
                }
            }
        )

        fragmentAudioBinding.bottomSheetLayout.checkBox3.setOnCheckedChangeListener(
            object : CompoundButton.OnCheckedChangeListener {
                override fun onCheckedChanged(p0: CompoundButton?, p1: Boolean) {
                    audioHelper.checkFlag3 = p1
                }
            }
        )
    }

    override fun onResume() {
        super.onResume()
        // Make sure that all permissions are still present, since the
        // user could have removed them while the app was in paused state.
        if (!PermissionsFragment.hasPermissions(requireContext())) {
            Navigation.findNavController(requireActivity(), R.id.fragment_container)
                .navigate(AudioFragmentDirections.actionAudioToPermissions())
        }

        if (::audioHelper.isInitialized ) {
            audioHelper.startAudioClassification()
        }
    }

    override fun onPause() {
        super.onPause()
        if (::audioHelper.isInitialized ) {
            audioHelper.stopAudioClassification()
        }
    }

    override fun onDestroyView() {
        _fragmentBinding = null
        super.onDestroyView()
    }
}
