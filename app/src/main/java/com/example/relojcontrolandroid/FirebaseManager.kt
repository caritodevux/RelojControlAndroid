package com.example.relojcontrolandroid

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class FirebaseManager {

    private val database = FirebaseDatabase.getInstance()
    private val auth = FirebaseAuth.getInstance()

    fun guardarMensaje(mensaje: String) {

        // Comprobar si existe un usuario autenticado
        val usuario = auth.currentUser

        if (usuario == null) {
            Log.e("FirebaseManager", "No hay ningún usuario autenticado")
            return
        }

        // Guardar dentro del espacio del usuario
        val referencia = database
            .getReference("usuarios")
            .child(usuario.uid)
            .child("mensajes")

        referencia.push().setValue(mensaje)
            .addOnSuccessListener {
                Log.d("FirebaseManager", "Mensaje guardado correctamente")
            }
            .addOnFailureListener { error ->
                Log.e("FirebaseManager", "Error al guardar: ${error.message}")
            }
    }
}