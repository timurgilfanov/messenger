package timur.gilfanov.messenger.entity.delegate

import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty
import timur.gilfanov.messenger.entity.model.message.DeliveryStatus
import timur.gilfanov.messenger.entity.validation.DeliveryStatusValidator

class DeliveryStatusDelegate(initialValue: DeliveryStatus) :
    ReadWriteProperty<Any?, DeliveryStatus> {
    private var value: DeliveryStatus = initialValue
    private val validator = DeliveryStatusValidator()

    override fun getValue(thisRef: Any?, property: KProperty<*>): DeliveryStatus = value

    override fun setValue(thisRef: Any?, property: KProperty<*>, value: DeliveryStatus) {
        validator.validate(this.value, value).getOrThrow()
        this.value = value
    }
}
