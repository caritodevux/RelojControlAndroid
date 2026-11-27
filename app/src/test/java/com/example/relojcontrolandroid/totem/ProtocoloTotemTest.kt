package com.example.relojcontrolandroid.totem

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProtocoloTotemTest {

    @Test
    fun armaLosComandosConSeparadorYFinDeLinea() {
        assertEquals("PING|1\n", ProtocoloTotem.comandoALinea(Comando.Ping(1)))
        assertEquals("ESTADO|2\n", ProtocoloTotem.comandoALinea(Comando.Estado(2)))
        assertEquals(
            "MARCA|3|EMP-1042|E\n",
            ProtocoloTotem.comandoALinea(Comando.Marca(3, "EMP-1042", TipoMarcaje.ENTRADA))
        )
    }

    @Test
    fun leeUnComandoMarcaValido() {
        assertEquals(
            LecturaComando.Valido(Comando.Marca(4, "EMP-1042", TipoMarcaje.SALIDA)),
            ProtocoloTotem.leerComando("MARCA|4|EMP-1042|S\n")
        )
    }

    @Test
    fun aceptaFinDeLineaWindows() {
        assertEquals(LecturaComando.Valido(Comando.Ping(7)), ProtocoloTotem.leerComando("PING|7\r\n"))
    }

    @Test
    fun comandoDesconocidoOIncompletoEsFormato() {
        assertEquals(LecturaComando.Invalido(5, CodigoError.FORMATO), ProtocoloTotem.leerComando("HOLA|5\n"))
        assertEquals(LecturaComando.Invalido(6, CodigoError.FORMATO), ProtocoloTotem.leerComando("MARCA|6|EMP-1\n"))
        assertEquals(LecturaComando.Invalido(8, CodigoError.FORMATO), ProtocoloTotem.leerComando("MARCA|8|EMP-1|X\n"))
    }

    @Test
    fun numeroInvalidoSeRespondeConCero() {
        assertEquals(LecturaComando.Invalido(0, CodigoError.FORMATO), ProtocoloTotem.leerComando("PING|abc\n"))
        assertEquals(LecturaComando.Invalido(0, CodigoError.FORMATO), ProtocoloTotem.leerComando("PING|1000\n"))
        assertEquals(LecturaComando.Invalido(0, CodigoError.FORMATO), ProtocoloTotem.leerComando("\n"))
    }

    @Test
    fun lineaDemasiadoLargaEsFormato() {
        val larga = "MARCA|9|EMP-" + "1".repeat(60) + "|E\n"
        assertEquals(LecturaComando.Invalido(9, CodigoError.FORMATO), ProtocoloTotem.leerComando(larga))
    }

    @Test
    fun idConFormatoIncorrectoEsEmpInvalido() {
        assertEquals(
            LecturaComando.Invalido(10, CodigoError.EMP_INVALIDO),
            ProtocoloTotem.leerComando("MARCA|10|1042|E\n")
        )
    }

    @Test
    fun respuestasIdaYVuelta() {
        val respuestas = listOf(
            Respuesta.Pong(1),
            Respuesta.EstadoTotem(2, ocupado = false, hora = "08:01:05"),
            Respuesta.EstadoTotem(3, ocupado = true, hora = "08:01:06"),
            Respuesta.MarcaAceptada(4, "EMP-1042", TipoMarcaje.ENTRADA, "08:01:12"),
            Respuesta.Error(5, CodigoError.DUPLICADO)
        )
        for (r in respuestas) {
            assertEquals(r, ProtocoloTotem.leerRespuesta(ProtocoloTotem.respuestaALinea(r)))
        }
        assertEquals("OK|4|EMP-1042|E|08:01:12\n", ProtocoloTotem.respuestaALinea(respuestas[3]))
        assertEquals("ERR|5|DUPLICADO\n", ProtocoloTotem.respuestaALinea(respuestas[4]))
    }

    @Test
    fun respuestasMalFormadasSonNull() {
        assertNull(ProtocoloTotem.leerRespuesta("OK|1\n"))
        assertNull(ProtocoloTotem.leerRespuesta("ERR|1|TIMEOUT\n")) // TIMEOUT no lo envía el tótem
        assertNull(ProtocoloTotem.leerRespuesta("OK|x|PONG\n"))
        assertNull(ProtocoloTotem.leerRespuesta("basura"))
    }

    @Test
    fun numeroDeMensajeVuelveAUnoDespuesDe999() {
        assertEquals(2, ProtocoloTotem.siguienteNumero(1))
        assertEquals(1, ProtocoloTotem.siguienteNumero(999))
    }
}
