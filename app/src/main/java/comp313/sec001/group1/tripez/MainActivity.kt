package comp313.sec001.group1.tripez

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import comp313.sec001.group1.tripez.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
    }
}