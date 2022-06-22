package comp313.sec001.group1.tripez.lib.extensions

import android.content.res.Resources
import android.util.TypedValue
import com.google.android.material.textfield.TextInputLayout
import java.text.SimpleDateFormat
import java.util.*


val defaultFormatter = SimpleDateFormat("yyyy-MM-dd")

fun Date.format(formatter: SimpleDateFormat = defaultFormatter): String =
    formatter.format(this)

fun String.parseAsDate(formatter: SimpleDateFormat = defaultFormatter): Date =
    formatter.parse(this)!!

fun Float.dpToPixel(r: Resources): Int = TypedValue.applyDimension(
    TypedValue.COMPLEX_UNIT_DIP,
    this,
    r.displayMetrics
).toInt()

fun Int.dpToPixel(r: Resources) : Int = this.toFloat().dpToPixel(r)