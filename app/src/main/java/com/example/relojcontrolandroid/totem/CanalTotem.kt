package com.example.relojcontrolandroid.totem

/**
 * Por dónde viajan las líneas del protocolo. El tótem virtual y el HC-05 (más adelante)
 * implementan esta interfaz, así la app no sabe con cuál está hablando.
 */
interface CanalTotem {

    /** Envía una línea (comando) y avisa cuando llega la línea de respuesta. */
    fun enviar(linea: String, alResponder: (String) -> Unit)
}

/** Algo que se puede cancelar, por ejemplo un timeout que ya no hace falta. */
fun interface Cancelable {
    fun cancelar()
}

/**
 * Ejecuta acciones después de un tiempo. En la app usa un Handler de Android
 * (PlanificadorAndroid); en las pruebas se usa uno manual para no esperar de verdad.
 */
interface Planificador {
    fun despuesDe(milisegundos: Long, accion: () -> Unit): Cancelable
}
