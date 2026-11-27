package com.example.relojcontrolandroid.totem

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Tótem simulado dentro de la app. Recibe las mismas líneas que recibiría el Arduino
 * y contesta con el mismo formato, después de [demoraMs] (1 segundo por defecto).
 *
 * Revisa DUPLICADO y SECUENCIA (misma entrada o salida dos veces el mismo día) consultando el último marcaje del trabajador en el
 * [repositorio]. No guarda nada: guardar es trabajo de la app después de un OK.
 */
class TotemVirtual(
    private val repositorio: RepositorioMarcajes,
    private val planificador: Planificador,
    private val reloj: () -> Long = { System.currentTimeMillis() },
    private val demoraMs: Long = 1_000L,
    private val zonaHoraria: TimeZone = TimeZone.getDefault()
) : CanalTotem {

    /** true mientras procesa un MARCA; otro MARCA en ese momento recibe OCUPADO. */
    var ocupado = false
        private set

    /** Para la demo: si es true el tótem no contesta nada y la app termina en TIMEOUT. */
    var simularDesconexion = false

    private val formatoHora = SimpleDateFormat("HH:mm:ss", Locale.US).apply {
        timeZone = zonaHoraria
    }

    override fun enviar(linea: String, alResponder: (String) -> Unit) {
        if (simularDesconexion) return

        when (val lectura = ProtocoloTotem.leerComando(linea)) {
            is LecturaComando.Invalido ->
                responderDespues(Respuesta.Error(lectura.numero, lectura.codigo), alResponder)

            is LecturaComando.Valido -> when (val comando = lectura.comando) {
                is Comando.Ping ->
                    responderDespues(Respuesta.Pong(comando.numero), alResponder)

                is Comando.Estado ->
                    responderDespues(
                        Respuesta.EstadoTotem(comando.numero, ocupado, horaActual()),
                        alResponder
                    )

                is Comando.Marca -> procesarMarca(comando, alResponder)
            }
        }
    }

    private fun procesarMarca(comando: Comando.Marca, alResponder: (String) -> Unit) {
        if (ocupado) {
            // Contesta de inmediato: no hay que esperar para saber que está ocupado.
            alResponder(ProtocoloTotem.respuestaALinea(Respuesta.Error(comando.numero, CodigoError.OCUPADO)))
            return
        }

        ocupado = true

        planificador.despuesDe(demoraMs) {
            repositorio.ultimoMarcaje(
                comando.idEmpleado,
                alObtener = { ultimo ->
                    val error = ReglasMarcaje.evaluar(ultimo, comando.tipo, reloj(), zonaHoraria).error
                    val respuesta = if (error != null) {
                        Respuesta.Error(comando.numero, error)
                    } else {
                        Respuesta.MarcaAceptada(comando.numero, comando.idEmpleado, comando.tipo, horaActual())
                    }
                    ocupado = false
                    alResponder(ProtocoloTotem.respuestaALinea(respuesta))
                },
                alFallar = {
                    // No pudo consultar la base de datos: se pide reintentar.
                    ocupado = false
                    alResponder(
                        ProtocoloTotem.respuestaALinea(Respuesta.Error(comando.numero, CodigoError.OCUPADO))
                    )
                }
            )
        }
    }

    private fun responderDespues(respuesta: Respuesta, alResponder: (String) -> Unit) {
        planificador.despuesDe(demoraMs) {
            alResponder(ProtocoloTotem.respuestaALinea(respuesta))
        }
    }

    private fun horaActual(): String = formatoHora.format(Date(reloj()))
}
