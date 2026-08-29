package com.teledrive.app.telegram

import android.app.Application
import com.google.android.recaptcha.Recaptcha
import com.google.android.recaptcha.RecaptchaAction
import com.google.android.recaptcha.RecaptchaClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object RecaptchaHelper {

    private var client: RecaptchaClient? = null
    private var currentKeyId: String? = null

    suspend fun getVerificationToken(
        application: Application,
        keyId: String,
        action: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val recaptchaClient = if (client != null && currentKeyId == keyId) {
                client!!
            } else {
                val fetched = Recaptcha.getClient(application, keyId).getOrThrow()
                client = fetched
                currentKeyId = keyId
                fetched
            }

            val token = recaptchaClient.execute(RecaptchaAction.custom(action)).getOrThrow()
            Result.success(token)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
