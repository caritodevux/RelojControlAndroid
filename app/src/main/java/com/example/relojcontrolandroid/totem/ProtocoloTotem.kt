package com.example.relojcontrolandroid.totem

/**
 * Protocolo de texto entre la app y el tótem (virtual o Arduino + HC-05).
 *
 * Cada mensaje es una línea: campos separados por "|" y terminada en "\n".
 *   App -> tótem:  PING|N   ESTADO|N   MARCA|N|EMP-1042|E
 *   Tótem -> app:  OK|N|PONG   OK|N|LISTO|08:01:05   OK|N|EMP-1042|E|08:01:12   ERR|N|DUPLICADO
 *
 * N es el número de mensaje (1 a 999); la respuesta repite el N del comando.
 * Esta clase no usa nada de Android, para poder probarla con JUnit.
 */

/** Tipo de marcaje: E = entrada, S = salida. */
enum class TipoMarcaje(val codigo: String) {
    ENTRADA("E"),
    SALIDA("S");

    companion object {
        fun desdeCodigo(codigo: String): TipoMarcaje? =
            values().firstOrNull { it.codigo == codigo }
    }
}

/**
 * Códigos de error. Los primeros los responde el tótem; TIMEOUT y GUARDADO
 * los genera la app (no viajan por Bluetooth).
 */
enum class CodigoError(val mensajeUsuario: String, val loEnviaElTotem: Boolean = true) {
    FORMATO("Error de comunicación, intenta de nuevo"),
    EMP_INVALIDO("ID de empleado no válido"),
    DUPLICADO("Ya registraste este marcaje"),
    SECUENCIA("La secuencia de marcaje no es válida"),
    OCUPADO("Tótem ocupado, intenta en unos segundos"),
    TIMEOUT("Sin respuesta del tótem, revisa la conexión", loEnviaElTotem = false),
    GUARDADO("No se pudo guardar el marcaje, intenta de nuevo", loEnviaElTotem = false);

    companion object {
        fun desdeTexto(texto: String): CodigoError? =
            values().firstOrNull { it.name == texto && it.loEnviaElTotem }
    }
}

sealed class Comando {
    abstract val numero: Int

    data class Ping(override val numero: Int) : Comando()
    data class Estado(override val numero: Int) : Comando()
    data class Marca(
        override val numero: Int,
        val idEmpleado: String,
        val tipo: TipoMarcaje
    ) : Comando()
}

sealed class Respuesta {
    abstract val numero: Int

    data class Pong(override val numero: Int) : Respuesta()
    data class EstadoTotem(override val numero: Int, val ocupado: Boolean, val hora: String) : Respuesta()
    data class MarcaAceptada(
        override val numero: Int,
        val idEmpleado: String,
        val tipo: TipoMarcaje,
        val hora: String
    ) : Respuesta()
    data class Error(override val numero: Int, val codigo: CodigoError) : Respuesta()
}

/** Resultado de leer un comando: o el comando, o el error que hay que responder. */
sealed class LecturaComando {
    data class Valido(val comando: Comando) : LecturaComando()
    data class Invalido(val numero: Int, val codigo: CodigoError) : LecturaComando()
}

object ProtocoloTotem {

    const val SEPARADOR = "|"
    const val FIN_DE_LINEA = "\n"
    const val LARGO_MAXIMO = 64
    const val NUMERO_MAXIMO = 999

    /** Mismo formato que valida hoy la pantalla principal (EMP-1042). */
    val FORMATO_ID_EMPLEADO = Regex("EMP-[A-Z0-9-]+")

    private val FORMATO_HORA = Regex("\\d{2}:\\d{2}:\\d{2}")

    // ---------------- Comandos (app -> tótem) ----------------

    fun comandoALinea(comando: Comando): String {
        val campos = when (comando) {
            is Comando.Ping -> listOf("PING", comando.numero.toString())
            is Comando.Estado -> listOf("ESTADO", comando.numero.toString())
            is Comando.Marca -> listOf(
                "MARCA", comando.numero.toString(), comando.idEmpleado, comando.tipo.codigo
            )
        }
        return campos.joinToString(SEPARADOR) + FIN_DE_LINEA
    }

    fun leerComando(linea: String): LecturaComando {
        val texto = limpiar(linea)
        val campos = texto.split(SEPARADOR)
        // Si no se puede leer el número, se responde con N = 0.
        val numero = campos.getOrNull(1)?.let { leerNumero(it) } ?: 0

        if (texto.isEmpty() || texto.length > LARGO_MAXIMO || numero == 0) {
            return LecturaComando.Invalido(numero, CodigoError.FORMATO)
        }

        return when (campos[0]) {
            "PING" ->
                if (campos.size == 2) LecturaComando.Valido(Comando.Ping(numero))
                else LecturaComando.Invalido(numero, CodigoError.FORMATO)

            "ESTADO" ->
                if (campos.size == 2) LecturaComando.Valido(Comando.Estado(numero))
                else LecturaComando.Invalido(numero, CodigoError.FORMATO)

            "MARCA" -> {
                if (campos.size != 4) return LecturaComando.Invalido(numero, CodigoError.FORMATO)
                val tipo = TipoMarcaje.desdeCodigo(campos[3])
                    ?: return LecturaComando.Invalido(numero, CodigoError.FORMATO)
                if (!FORMATO_ID_EMPLEADO.matches(campos[2])) {
                    return LecturaComando.Invalido(numero, CodigoError.EMP_INVALIDO)
                }
                LecturaComando.Valido(Comando.Marca(numero, campos[2], tipo))
            }

            else -> LecturaComando.Invalido(numero, CodigoError.FORMATO)
        }
    }

    // ---------------- Respuestas (tótem -> app) ----------------

    fun respuestaALinea(respuesta: Respuesta): String {
        val n = respuesta.numero.toString()
        val campos = when (respuesta) {
            is Respuesta.Pong -> listOf("OK", n, "PONG")
            is Respuesta.EstadoTotem ->
                listOf("OK", n, if (respuesta.ocupado) "OCUPADO" else "LISTO", respuesta.hora)
            is Respuesta.MarcaAceptada ->
                listOf("OK", n, respuesta.idEmpleado, respuesta.tipo.codigo, respuesta.hora)
            is Respuesta.Error -> listOf("ERR", n, respuesta.codigo.name)
        }
        return campos.joinToString(SEPARADOR) + FIN_DE_LINEA
    }

    /** Devuelve null si la línea no es una respuesta válida del protocolo. */
    fun leerRespuesta(linea: String): Respuesta? {
        val campos = limpiar(linea).split(SEPARADOR)
        val numero = campos.getOrNull(1)?.toIntOrNull() ?: return null
        if (numero !in 0..NUMERO_MAXIMO) return null

        return when (campos[0]) {
            "ERR" -> {
                if (campos.size != 3) return null
                val codigo = CodigoError.desdeTexto(campos[2]) ?: return null
                Respuesta.Error(numero, codigo)
            }

            "OK" -> when {
                campos.size == 3 && campos[2] == "PONG" -> Respuesta.Pong(numero)

                campos.size == 4 && campos[2] in setOf("LISTO", "OCUPADO") &&
                    FORMATO_HORA.matches(campos[3]) ->
                    Respuesta.EstadoTotem(numero, campos[2] == "OCUPADO", campos[3])

                campos.size == 5 -> {
                    val tipo = TipoMarcaje.desdeCodigo(campos[3]) ?: return null
                    if (!FORMATO_HORA.matches(campos[4])) return null
                    Respuesta.MarcaAceptada(numero, campos[2], tipo, campos[4])
                }

                else -> null
            }

            else -> null
        }
    }

    /** Siguiente número de mensaje: 1, 2, ... 999 y vuelve a 1. */
    fun siguienteNumero(actual: Int): Int = if (actual >= NUMERO_MAXIMO) 1 else actual + 1

    private fun limpiar(linea: String): String = linea.trimEnd('\n', '\r')

    private fun leerNumero(texto: String): Int? =
        texto.toIntOrNull()?.takeIf { it in 1..NUMERO_MAXIMO }
}
