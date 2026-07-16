package com.amigos.kinbridge

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.amigos.kinbridge.databinding.ActivityMainBinding
import com.google.firebase.analytics.FirebaseAnalytics

class MainActivity : AppCompatActivity() {

    private lateinit var analytics: FirebaseAnalytics
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Obtain the FirebaseAnalytics instance
        analytics = FirebaseAnalytics.getInstance(this)

        binding.logEventButton.setOnClickListener {
            val bundle = Bundle().apply {
                putString(FirebaseAnalytics.Param.ITEM_ID, "test_id")
                putString(FirebaseAnalytics.Param.ITEM_NAME, "test_name")
                putString(FirebaseAnalytics.Param.CONTENT_TYPE, "button")
            }
            analytics.logEvent(FirebaseAnalytics.Event.SELECT_CONTENT, bundle)
            Toast.makeText(this, "Event logged to Firebase Analytics", Toast.LENGTH_SHORT).show()
        }
    }
}
