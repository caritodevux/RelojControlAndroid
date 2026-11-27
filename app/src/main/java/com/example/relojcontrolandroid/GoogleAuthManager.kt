package com.example.relojcontrolandroid

import android.app.Activity
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.credentials.CredentialManager
import androidx.credentials.CredentialManagerCallback
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider

class GoogleAuthManager(private val activity: Activity) {

    private val credentialManager = CredentialManager.create(activity)
    private val auth = FirebaseAuth.getInstance()

    fun iniciarSesion(
        onExito: (FirebaseUser) -> Unit,
        onError: (String) -> Unit
    ) {

        // default_web_client_id lo genera el plugin google-services
        // a partir de google-services.json
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(
                activity.getString(R.string.default_web_client_id)
            )
            .setAutoSelectEnabled(false)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        credentialManager.getCredentialAsync(
            activity,
            request,
            null,
            ContextCompat.getMainExecutor(activity),
            object : CredentialManagerCallback<
                    GetCredentialResponse,
                    GetCredentialException> {

                override fun onResult(result: GetCredentialResponse) {
                    manejarCredencial(result, onExito, onError)
                }

                override fun onError(e: GetCredentialException) {
                    Log.e("GoogleAuth", "Credential Manager: ${e.type} - ${e.message}")
                    onError("No se pudo obtener la cuenta: ${e.message}")
                }
            }
        )
    }

    private fun manejarCredencial(
        result: GetCredentialResponse,
        onExito: (FirebaseUser) -> Unit,
        onError: (String) -> Unit
    ) {

        val credencial = result.credential

        if (
            credencial is CustomCredential &&
            credencial.type ==
            GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {

            try {

                val googleCredential =
                    GoogleIdTokenCredential.createFrom(credencial.data)

                autenticarEnFirebase(
                    googleCredential.idToken,
                    onExito,
                    onError
                )

            } catch (e: GoogleIdTokenParsingException) {
                Log.e("GoogleAuth", "Token inválido: ${e.message}")
                onError("Token de Google inválido")
            }

        } else {
            Log.e("GoogleAuth", "Tipo de credencial inesperado: ${credencial.type}")
            onError("Tipo de credencial no soportado")
        }
    }

    private fun autenticarEnFirebase(
        idToken: String,
        onExito: (FirebaseUser) -> Unit,
        onError: (String) -> Unit
    ) {

        val credencial = GoogleAuthProvider.getCredential(idToken, null)

        auth.signInWithCredential(credencial)
            .addOnCompleteListener(activity) { tarea ->

                val usuario = auth.currentUser

                if (tarea.isSuccessful && usuario != null) {
                    Log.d("GoogleAuth", "Login OK: ${usuario.uid}")
                    onExito(usuario)
                } else {
                    Log.e("GoogleAuth", "Firebase: ${tarea.exception?.message}")
                    onError("Error de Firebase: ${tarea.exception?.message}")
                }
            }
    }
}