package org.fengshen.dev

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Keeps only the session token, encrypted with a key held by Android Keystore. Passwords are never saved. */
class CloudTokenStore(context:Context,namespace:String="cloud-session") {
    private val prefs=context.getSharedPreferences(namespace,0)
    private val alias="fengshen-$namespace-v1"
    private fun key():SecretKey {
        val store=KeyStore.getInstance("AndroidKeyStore").apply{load(null)}
        (store.getKey(alias,null) as? SecretKey)?.let{return it}
        val generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256).build())
        return generator.generateKey()
    }
    fun save(session:CloudSession){
        val cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key())
        val plain=JSONObject().put("username",session.username).put("token",session.token).toString().toByteArray(Charsets.UTF_8)
        val encoded=Base64.encodeToString(cipher.doFinal(plain),Base64.NO_WRAP)
        val iv=Base64.encodeToString(cipher.iv,Base64.NO_WRAP)
        prefs.edit().putString("ciphertext",encoded).putString("iv",iv).commit()
    }
    fun load():CloudSession? {
        val value=prefs.getString("ciphertext",null)?:return null
        return try {
            val iv=Base64.decode(prefs.getString("iv","")!!,Base64.NO_WRAP)
            val cipher=Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,iv))
            val o=JSONObject(String(cipher.doFinal(Base64.decode(value,Base64.NO_WRAP)),Charsets.UTF_8))
            CloudSession(o.getString("username"),o.getString("token"))
        } catch(_:Exception){clear();null}
    }
    fun clear(){prefs.edit().clear().commit()}
}
