package com.example.relojcontrolandroid.totem

/** Lo que recibe la pantalla al terminar un marcaje. */
sealed class ResultadoMarcaje {
    data class Exitoso(val idEmpleado: String, val tipo: TipoMarcaje, val hora: String) : ResultadoMarcaje()
    data class Fallido(val codigo: CodigoError, val mensaje: String) : ResultadoMarcaje()
}

/**
 * Punto de entrada para la pantalla: [marcar] envía el comando al tótem, espera la
 * respuesta como máximo [timeoutMs] (3 s) y, solo si el tótem responde OK, guarda el
 * marcaje en el [repositorio].
 *
 * [validarEnApp] = true se usa con el Arduino real, que no tiene la base de datos:
 * la app revisa DUPLICADO y SECUENCIA antes de enviar. Con el tótem virtual va en false
 * porque él ya lo revisa.
 *
 * Los callbacks del canal deben llegar en el hilo principal (igual que el planificador).
 */
class ServicioMarcaje(
    private val canal: CanalTotem,
    private val repositorio: RepositorioMarcajes,
    private val planificador: Planificador,
    private val reloj: () -> Long = { System.currentTimeMillis() },
    private val timeoutMs: Long = 3_000L,
    private val validarEnApp: Boolean = false
) {

    private var ultimoNumero = 0

    fun marcar(idEmpleado: String, tipo: TipoMarcaje, alTerminar: (ResultadoMarcaje) -> Unit) {
        if (!ProtocoloTotem.FORMATO_ID_EMPLEADO.matches(idEmpleado)) {
            alTerminar(fallido(CodigoError.EMP_INVALIDO, tipo))
            return
        }

        if (!validarEnApp) {
            enviarAlTotem(idEmpleado, tipo, alTerminar)
            return
        }

        repositorio.ultimoMarcaje(
            idEmpleado,
            alObtener = { ultimo ->
                val error = ReglasMarcaje.validar(ultimo, tipo, reloj())
                if (error != null) alTerminar(fallido(error, tipo))
                else enviarAlTotem(idEmpleado, tipo, alTerminar)
            },
            alFallar = { alTerminar(fallido(CodigoError.GUARDADO, tipo)) }
        )
    }

    private fun enviarAlTotem(idEmpleado: String, tipo: TipoMarcaje, alTerminar: (ResultadoMarcaje) -> Unit) {
        ultimoNumero = ProtocoloTotem.siguienteNumero(ultimoNumero)
        val numero = ultimoNumero
        var terminado = false

        val timeout = planificador.despuesDe(timeoutMs) {
            if (!terminado) {
                terminado = true
                alTerminar(fallido(CodigoError.TIMEOUT, tipo))
            }
        }

        val linea = ProtocoloTotem.comandoALinea(Comando.Marca(numero, idEmpleado, tipo))

        canal.enviar(linea) { lineaRespuesta ->
            if (terminado) return@enviar

            val respuesta = ProtocoloTotem.leerRespuesta(lineaRespuesta)

            // Respuesta atrasada de un comando anterior: se ignora y se sigue esperando.
            if (respuesta != null && respuesta.numero != numero) return@enviar

            terminado = true
            timeout.cancelar()

            when (respuesta) {
                is Respuesta.MarcaAceptada -> guardar(respuesta, alTerminar)
                is Respuesta.Error -> alTerminar(fallido(respuesta.codigo, tipo))
                else -> alTerminar(fallido(CodigoError.FORMATO, tipo))
            }
        }
    }

    private fun guardar(respuesta: Respuesta.MarcaAceptada, alTerminar: (ResultadoMarcaje) -> Unit) {
        val marcaje = Marcaje(respuesta.idEmpleado, respuesta.tipo, reloj())
        repositorio.guardar(
            marcaje,
            alGuardar = {
                alTerminar(ResultadoMarcaje.Exitoso(respuesta.idEmpleado, respuesta.tipo, respuesta.hora))
            },
            alFallar = { alTerminar(fallido(CodigoError.GUARDADO, respuesta.tipo)) }
        )
    }

    private fun fallido(codigo: CodigoError, tipo: TipoMarcaje) =
        ResultadoMarcaje.Fallido(codigo, ReglasMarcaje.mensajePara(codigo, tipo))
}
