package com.example.relojcontrolandroid.totem

/** Planificador para pruebas: el tiempo avanza solo cuando la prueba llama a [avanzar]. */
class PlanificadorManual : Planificador {

    var ahora = 1_000_000L
        private set

    private data class Tarea(val cuando: Long, val accion: () -> Unit, var cancelada: Boolean = false)

    private val tareas = mutableListOf<Tarea>()

    override fun despuesDe(milisegundos: Long, accion: () -> Unit): Cancelable {
        val tarea = Tarea(ahora + milisegundos, accion)
        tareas.add(tarea)
        return Cancelable { tarea.cancelada = true }
    }

    fun avanzar(milisegundos: Long) {
        val fin = ahora + milisegundos
        while (true) {
            val siguiente = tareas
                .filter { !it.cancelada && it.cuando <= fin }
                .minByOrNull { it.cuando } ?: break
            tareas.remove(siguiente)
            ahora = siguiente.cuando
            siguiente.accion()
        }
        ahora = fin
    }
}
