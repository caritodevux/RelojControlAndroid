package com.example.relojcontrolandroid.totem

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Marcación que faltó y queda registrada para que jefatura la justifique. */
enum class TipoObservacion {
    SALIDA_NO_REGISTRADA,
    ENTRADA_NO_REGISTRADA
}

/** Constancia guardada junto al marcaje. [fecha] es el día de la marcación que faltó (dd-MM-yyyy). */
data class Observacion(
    val tipo: TipoObservacion,
    val fecha: String
) {
    val mensaje: String
        get() = when (tipo) {
            TipoObservacion.SALIDA_NO_REGISTRADA ->
                "Se esperaba la marcación de salida del día $fecha y no se registró. " +
                    "Contáctese con jefatura para su justificación."
            TipoObservacion.ENTRADA_NO_REGISTRADA ->
                "No se registró la marcación de entrada del día $fecha. " +
                    "Contáctese con jefatura para su justificación."
        }
}

/** Resultado de revisar un marcaje nuevo: un error que lo bloquea, o las observaciones con que se acepta. */
data class Evaluacion(
    val error: CodigoError?,
    val observaciones: List<Observacion> = emptyList()
)

/**
 * Reglas de validación de un marcaje nuevo comparado con el último del trabajador.
 * Las usa el tótem virtual (para responder DUPLICADO o SECUENCIA) y la app
 * (para dejar constancia de marcaciones faltantes, y para validar con el Arduino real).
 *
 * La secuencia se reinicia cada día: lo que faltó un día anterior no bloquea,
 * solo queda como observación.
 */
object ReglasMarcaje {

    /** Mismo empleado y mismo tipo dentro de este tiempo = marcaje duplicado. */
    const val VENTANA_DUPLICADO_MS = 60_000L

    fun evaluar(
        ultimo: Marcaje?,
        tipoNuevo: TipoMarcaje,
        ahora: Long,
        zonaHoraria: TimeZone = TimeZone.getDefault()
    ): Evaluacion {
        if (ultimo != null &&
            ultimo.tipo == tipoNuevo &&
            ahora - ultimo.instante < VENTANA_DUPLICADO_MS
        ) {
            return Evaluacion(CodigoError.DUPLICADO)
        }

        val hoy = dia(ahora, zonaHoraria)
        val diaUltimo = ultimo?.let { dia(it.instante, zonaHoraria) }
        val ultimoEsDeHoy = diaUltimo == hoy

        // Dos entradas o dos salidas el mismo día: se bloquea.
        if (ultimo != null && ultimoEsDeHoy && ultimo.tipo == tipoNuevo) {
            return Evaluacion(CodigoError.SECUENCIA)
        }

        val observaciones = mutableListOf<Observacion>()

        // Quedó una entrada abierta de un día anterior: faltó esa salida.
        if (ultimo?.tipo == TipoMarcaje.ENTRADA && !ultimoEsDeHoy && diaUltimo != null) {
            observaciones.add(Observacion(TipoObservacion.SALIDA_NO_REGISTRADA, diaUltimo))
        }

        // Salida sin una entrada hoy: faltó la entrada de hoy.
        if (tipoNuevo == TipoMarcaje.SALIDA && !(ultimo?.tipo == TipoMarcaje.ENTRADA && ultimoEsDeHoy)) {
            observaciones.add(Observacion(TipoObservacion.ENTRADA_NO_REGISTRADA, hoy))
        }

        return Evaluacion(null, observaciones)
    }

    /** Mensaje para el trabajador; SECUENCIA se explica según lo que intentó marcar. */
    fun mensajePara(codigo: CodigoError, tipo: TipoMarcaje): String =
        if (codigo == CodigoError.SECUENCIA) {
            when (tipo) {
                TipoMarcaje.ENTRADA -> "Ya registraste tu entrada hoy"
                TipoMarcaje.SALIDA -> "Ya registraste tu salida hoy"
            }
        } else {
            codigo.mensajeUsuario
        }

    private fun dia(instante: Long, zonaHoraria: TimeZone): String =
        SimpleDateFormat("dd-MM-yyyy", Locale.US)
            .apply { timeZone = zonaHoraria }
            .format(Date(instante))
}
