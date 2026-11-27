package com.example.relojcontrolandroid.totem

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.TimeZone

/** Prueba el tótem virtual y el servicio de marcaje juntos, sin hardware ni internet. */
class TotemVirtualTest {

    private lateinit var planificador: PlanificadorManual
    private lateinit var repositorio: RepositorioMarcajesEnMemoria
    private lateinit var totem: TotemVirtual
    private lateinit var servicio: ServicioMarcaje
    private val utc = TimeZone.getTimeZone("UTC")
    private val unDia = 24 * 60 * 60 * 1000L

    @Before
    fun preparar() {
        planificador = PlanificadorManual()
        repositorio = RepositorioMarcajesEnMemoria()
        val reloj = { planificador.ahora }
        totem = TotemVirtual(repositorio, planificador, reloj, zonaHoraria = utc)
        servicio = ServicioMarcaje(totem, repositorio, planificador, reloj, zonaHoraria = utc)
    }

    /** Marca y avanza el tiempo hasta tener resultado. */
    private fun marcar(tipo: TipoMarcaje, id: String = "EMP-1042"): ResultadoMarcaje {
        var resultado: ResultadoMarcaje? = null
        servicio.marcar(id, tipo) { resultado = it }
        planificador.avanzar(5_000)
        return resultado ?: error("sin resultado")
    }

    private fun codigoDe(resultado: ResultadoMarcaje) = (resultado as ResultadoMarcaje.Fallido).codigo

    @Test
    fun pingRespondePong() {
        var respuesta = ""
        totem.enviar("PING|1\n") { respuesta = it }
        planificador.avanzar(999)
        assertEquals("", respuesta) // todavía no pasa el segundo de espera
        planificador.avanzar(1)
        assertEquals("OK|1|PONG\n", respuesta)
    }

    @Test
    fun jornadaNormalEntradaYSalida() {
        val entrada = marcar(TipoMarcaje.ENTRADA)
        assertTrue(entrada is ResultadoMarcaje.Exitoso)

        planificador.avanzar(8 * 60 * 60 * 1000L) // 8 horas después
        val salida = marcar(TipoMarcaje.SALIDA)
        assertTrue(salida is ResultadoMarcaje.Exitoso)

        assertEquals(listOf(TipoMarcaje.ENTRADA, TipoMarcaje.SALIDA), repositorio.todos().map { it.tipo })
    }

    @Test
    fun dobleToqueEsDuplicadoYNoSeGuarda() {
        marcar(TipoMarcaje.ENTRADA)
        val segunda = marcar(TipoMarcaje.ENTRADA)
        assertEquals(CodigoError.DUPLICADO, codigoDe(segunda))
        assertEquals("Ya registraste este marcaje", (segunda as ResultadoMarcaje.Fallido).mensaje)
        assertEquals(1, repositorio.todos().size)
    }

    private fun exitoso(resultado: ResultadoMarcaje) = resultado as ResultadoMarcaje.Exitoso

    @Test
    fun jornadaNormalNoDejaObservaciones() {
        assertTrue(exitoso(marcar(TipoMarcaje.ENTRADA)).observaciones.isEmpty())
        planificador.avanzar(8 * 60 * 60 * 1000L)
        assertTrue(exitoso(marcar(TipoMarcaje.SALIDA)).observaciones.isEmpty())
    }

    @Test
    fun salidaSinEntradaSePermiteYDejaConstancia() {
        val r = exitoso(marcar(TipoMarcaje.SALIDA))
        assertEquals(listOf(Observacion(TipoObservacion.ENTRADA_NO_REGISTRADA, "27-11-2026")), r.observaciones)
        assertEquals(
            "No se registró la marcación de entrada del día 27-11-2026. Contáctese con jefatura para su justificación.",
            r.observaciones[0].mensaje
        )
        // La constancia queda guardada junto al marcaje.
        assertEquals(r.observaciones, repositorio.todos().single().observaciones)
    }

    @Test
    fun olvidoLaSalidaAyerYMarcaEntradaHoy() {
        marcar(TipoMarcaje.ENTRADA)            // 27-11 sin salida
        planificador.avanzar(unDia)
        val r = exitoso(marcar(TipoMarcaje.ENTRADA))  // 28-11
        assertEquals(listOf(Observacion(TipoObservacion.SALIDA_NO_REGISTRADA, "27-11-2026")), r.observaciones)
        assertEquals(
            "Se esperaba la marcación de salida del día 27-11-2026 y no se registró. Contáctese con jefatura para su justificación.",
            r.observaciones[0].mensaje
        )
    }

    @Test
    fun olvidoSalidaAyerYEntradaHoyDejaDosObservaciones() {
        marcar(TipoMarcaje.ENTRADA)            // 27-11 sin salida
        planificador.avanzar(unDia)
        val r = exitoso(marcar(TipoMarcaje.SALIDA))   // 28-11 sin entrada
        assertEquals(
            listOf(
                Observacion(TipoObservacion.SALIDA_NO_REGISTRADA, "27-11-2026"),
                Observacion(TipoObservacion.ENTRADA_NO_REGISTRADA, "28-11-2026")
            ),
            r.observaciones
        )
    }

    @Test
    fun dosSalidasElMismoDiaEsSecuencia() {
        marcar(TipoMarcaje.ENTRADA)
        planificador.avanzar(60 * 60 * 1000L)
        marcar(TipoMarcaje.SALIDA)
        planificador.avanzar(10 * 60 * 1000L)
        val r = marcar(TipoMarcaje.SALIDA)
        assertEquals(CodigoError.SECUENCIA, codigoDe(r))
        assertEquals("Ya registraste tu salida hoy", (r as ResultadoMarcaje.Fallido).mensaje)
    }

    @Test
    fun dosEntradasSeparadasEsSecuencia() {
        marcar(TipoMarcaje.ENTRADA)
        planificador.avanzar(10 * 60 * 1000L) // 10 minutos: ya no es duplicado
        val r = marcar(TipoMarcaje.ENTRADA)
        assertEquals(CodigoError.SECUENCIA, codigoDe(r))
        assertEquals("Ya registraste tu entrada hoy", (r as ResultadoMarcaje.Fallido).mensaje)
    }

    @Test
    fun cadaEmpleadoTieneSuPropiaSecuencia() {
        marcar(TipoMarcaje.ENTRADA, "EMP-1042")
        assertTrue(marcar(TipoMarcaje.ENTRADA, "EMP-2001") is ResultadoMarcaje.Exitoso)
    }

    @Test
    fun idInvalidoNoSeEnvia() {
        assertEquals(CodigoError.EMP_INVALIDO, codigoDe(marcar(TipoMarcaje.ENTRADA, "juan")))
    }

    @Test
    fun totemOcupadoConDosMarcajesALaVez() {
        var primero: ResultadoMarcaje? = null
        var segundo: ResultadoMarcaje? = null
        servicio.marcar("EMP-1042", TipoMarcaje.ENTRADA) { primero = it }
        servicio.marcar("EMP-2001", TipoMarcaje.ENTRADA) { segundo = it }
        planificador.avanzar(5_000)
        assertTrue(primero is ResultadoMarcaje.Exitoso)
        assertEquals(CodigoError.OCUPADO, codigoDe(segundo!!))
    }

    @Test
    fun totemDesconectadoTerminaEnTimeout() {
        totem.simularDesconexion = true
        var resultado: ResultadoMarcaje? = null
        servicio.marcar("EMP-1042", TipoMarcaje.ENTRADA) { resultado = it }
        planificador.avanzar(2_999)
        assertEquals(null, resultado)
        planificador.avanzar(1)
        assertEquals(CodigoError.TIMEOUT, codigoDe(resultado!!))
        assertTrue(repositorio.todos().isEmpty())
    }

    @Test
    fun siFallaElGuardadoNoEsExito() {
        val repoQueFalla = object : RepositorioMarcajes {
            override fun ultimoMarcaje(idEmpleado: String, alObtener: (Marcaje?) -> Unit, alFallar: (Exception) -> Unit) =
                alObtener(null)

            override fun guardar(marcaje: Marcaje, alGuardar: () -> Unit, alFallar: (Exception) -> Unit) =
                alFallar(Exception("sin internet"))
        }
        val reloj = { planificador.ahora }
        servicio = ServicioMarcaje(TotemVirtual(repoQueFalla, planificador, reloj), repoQueFalla, planificador, reloj)
        assertEquals(CodigoError.GUARDADO, codigoDe(marcar(TipoMarcaje.ENTRADA)))
    }

    @Test
    fun conArduinoRealLaAppValidaAntesDeEnviar() {
        // Canal falso que acepta todo, como un Arduino sin base de datos.
        val lineasEnviadas = mutableListOf<String>()
        val arduino = object : CanalTotem {
            override fun enviar(linea: String, alResponder: (String) -> Unit) {
                lineasEnviadas.add(linea)
                val c = (ProtocoloTotem.leerComando(linea) as LecturaComando.Valido).comando as Comando.Marca
                alResponder("OK|${c.numero}|${c.idEmpleado}|${c.tipo.codigo}|08:00:00\n")
            }
        }
        servicio = ServicioMarcaje(
            arduino, repositorio, planificador, { planificador.ahora }, validarEnApp = true, zonaHoraria = utc
        )

        assertTrue(marcar(TipoMarcaje.ENTRADA) is ResultadoMarcaje.Exitoso)
        assertEquals(listOf("MARCA|1|EMP-1042|E\n"), lineasEnviadas)

        planificador.avanzar(10 * 60 * 1000L)
        assertEquals(CodigoError.SECUENCIA, codigoDe(marcar(TipoMarcaje.ENTRADA)))
        assertEquals(1, lineasEnviadas.size) // la segunda entrada no llegó a enviarse
    }

    @Test
    fun respuestaAtrasadaDeOtroComandoSeIgnora() {
        val canal = object : CanalTotem {
            override fun enviar(linea: String, alResponder: (String) -> Unit) {
                alResponder("OK|77|EMP-1042|E|08:00:00\n") // de un comando viejo
                alResponder("ERR|1|OCUPADO\n")             // la de este comando
            }
        }
        servicio = ServicioMarcaje(canal, repositorio, planificador, { planificador.ahora })
        assertEquals(CodigoError.OCUPADO, codigoDe(marcar(TipoMarcaje.ENTRADA)))
    }
}
