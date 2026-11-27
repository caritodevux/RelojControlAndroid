package com.example.relojcontrolandroid.totem

import android.os.Handler
import android.os.Looper

/** Planificador que ejecuta las acciones en el hilo principal (seguro para tocar la pantalla). */
class PlanificadorAndroid(
    private val handler: Handler = Handler(Looper.getMainLooper())
) : Planificador {

    override fun despuesDe(milisegundos: Long, accion: () -> Unit): Cancelable {
        val tarea = Runnable { accion() }
        handler.postDelayed(tarea, milisegundos)
        return Cancelable { handler.removeCallbacks(tarea) }
    }
}
