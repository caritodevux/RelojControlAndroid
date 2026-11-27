package com.example.relojcontrolandroid.totem

/**
 * Reglas de validación de un marcaje nuevo comparado con el último del trabajador.
 * Las usa el tótem virtual y, con un Arduino real (que no tiene la base de datos),
 * la app antes de enviar el comando.
 */
object ReglasMarcaje {

    /** Mismo empleado y mismo tipo dentro de este tiempo = marcaje duplicado. */
    const val VENTANA_DUPLICADO_MS = 60_000L

    /** Devuelve el error que corresponde, o null si el marcaje es válido. */
    fun validar(ultimo: Marcaje?, tipoNuevo: TipoMarcaje, ahora: Long): CodigoError? {
        if (ultimo != null &&
            ultimo.tipo == tipoNuevo &&
            ahora - ultimo.instante < VENTANA_DUPLICADO_MS
        ) {
            return CodigoError.DUPLICADO
        }

        return when (tipoNuevo) {
            // Entrada con otra entrada todavía abierta.
            TipoMarcaje.ENTRADA ->
                if (ultimo?.tipo == TipoMarcaje.ENTRADA) CodigoError.SECUENCIA else null
            // Salida sin una entrada antes.
            TipoMarcaje.SALIDA ->
                if (ultimo == null || ultimo.tipo == TipoMarcaje.SALIDA) CodigoError.SECUENCIA else null
        }
    }

    /** Mensaje para el trabajador; SECUENCIA se explica según lo que intentó marcar. */
    fun mensajePara(codigo: CodigoError, tipo: TipoMarcaje): String =
        if (codigo == CodigoError.SECUENCIA) {
            when (tipo) {
                TipoMarcaje.ENTRADA -> "Ya tienes una entrada registrada"
                TipoMarcaje.SALIDA -> "Primero debes marcar entrada"
            }
        } else {
            codigo.mensajeUsuario
        }
}
