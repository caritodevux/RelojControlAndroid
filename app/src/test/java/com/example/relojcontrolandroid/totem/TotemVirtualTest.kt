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

    @Before
    fun preparar() {
        planificador = PlanificadorManual()
        repositorio = RepositorioMarcajesEnMemoria()
        val reloj = { planificador.ahora }
        totem = TotemVirtual(repositorio, planificador, reloj, zonaHoraria = TimeZone.getTimeZone("UTC"))
        servicio = ServicioMarcaje(totem, repositorio, planificador, reloj)
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

    @Test
    fun salidaSinEntradaEsSecuencia() {
        val r = marcar(TipoMarcaje.SALIDA)
        assertEquals(CodigoError.SECUENCIA, codigoDe(r))
        assertEquals("Primero debes marcar entrada", (r as ResultadoMarcaje.Fallido).mensaje)
    }

    @Test
    fun dosEntradasSeparadasEsSecuencia() {
        marcar(TipoMarcaje.ENTRADA)
        planificador.avanzar(10 * 60 * 1000L) // 10 minutos: ya no es duplicado
        val r = marcar(TipoMarcaje.ENTRADA)
        assertEquals(CodigoError.SECUENCIA, codigoDe(r))
        assertEquals("Ya tienes una entrada registrada", (r as ResultadoMarcaje.Fallido).mensaje)
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
        servicio = ServicioMarcaje(arduino, repositorio, planificador, { planificador.ahora }, validarEnApp = true)

        assertEquals(CodigoError.SECUENCIA, codigoDe(marcar(TipoMarcaje.SALIDA)))
        assertTrue(lineasEnviadas.isEmpty()) // no llegó a enviarse

        assertTrue(marcar(TipoMarcaje.ENTRADA) is ResultadoMarcaje.Exitoso)
        assertEquals(listOf("MARCA|1|EMP-1042|E\n"), lineasEnviadas)
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
