package comp313.sec001.group1.tripez.data.form

data class FormField<A> (val value: A, val touched : Boolean = false) {
    fun update(newVal: A) =
        if (newVal == value) this else FormField(newVal, true)

    fun touch() = FormField(value,true)
}