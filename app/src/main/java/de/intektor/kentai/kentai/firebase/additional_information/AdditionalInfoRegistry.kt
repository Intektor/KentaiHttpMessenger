package de.intektor.kentai.kentai.firebase.additional_information

import com.google.common.collect.BiMap
import com.google.common.collect.HashBiMap
import de.intektor.kentai.kentai.firebase.additional_information.info.*

/**
 * @author Intektor
 */
object AdditionalInfoRegistry {

    private val registry: BiMap<Int, Class<out IAdditionalInfo>> = HashBiMap.create()

    init {
        register(AdditionalInfoEmpty::class.java)
        register(AdditionalInfoGroupInviteMessage::class.java)
        register(AdditionalInfoGroupModification::class.java)
        register(AdditionalInfoVoiceMessage::class.java)
        register(AdditionalInfoVideoMessage::class.java)
    }

    private fun register(clazz: Class<out IAdditionalInfo>) {
        registry.put(registry.size, clazz)
    }

    fun getID(clazz: Class<out IAdditionalInfo>): Int = registry.inverse()[clazz]!!

    fun create(id: Int): IAdditionalInfo = registry[id]!!.newInstance()
}