package timur.gilfanov.messenger.entity.delegate

import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty
import timur.gilfanov.messenger.entity.validation.TextValidator

class TextDelegate(initialValue: String, maxLength: Int) : ReadWriteProperty<Any?, String> {
    private var value: String = initialValue
    private val validator = TextValidator(maxLength)

    init {
        validator.validate(value).getOrThrow()
    }

    override fun getValue(thisRef: Any?, property: KProperty<*>): String = value

    override fun setValue(thisRef: Any?, property: KProperty<*>, value: String) {
        validator.validate(value).getOrThrow()
        this.value = value
    }
}
