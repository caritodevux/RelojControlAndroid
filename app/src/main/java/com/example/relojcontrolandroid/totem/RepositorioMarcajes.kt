package com.example.relojcontrolandroid.totem

/**
 * Un marcaje ya registrado. [instante] en milisegundos (System.currentTimeMillis()).
 * [observaciones] deja constancia de marcaciones faltantes (por ejemplo, la salida de ayer).
 */
data class Marcaje(
    val idEmpleado: String,
    val tipo: TipoMarcaje,
    val instante: Long,
    val observaciones: List<Observacion> = emptyList()
)

/**
 * Dónde se guardan y consultan los marcajes.
 *
 * Usa callbacks porque Firestore responde de forma asíncrona. Hoy existe la versión
 * en memoria; al migrar a Firestore se agrega otra implementación de esta interfaz
 * y el tótem virtual y el servicio de marcaje no cambian.
 */
interface RepositorioMarcajes {

    /** Entrega el último marcaje del empleado, o null si no tiene. */
    fun ultimoMarcaje(
        idEmpleado: String,
        alObtener: (Marcaje?) -> Unit,
        alFallar: (Exception) -> Unit
    )

    fun guardar(
        marcaje: Marcaje,
        alGuardar: () -> Unit,
        alFallar: (Exception) -> Unit
    )
}

/** Versión en memoria: sirve para la demo sin internet y para las pruebas. Se borra al cerrar la app. */
class RepositorioMarcajesEnMemoria : RepositorioMarcajes {

    private val marcajes = mutableListOf<Marcaje>()

    @Synchronized
    override fun ultimoMarcaje(
        idEmpleado: String,
        alObtener: (Marcaje?) -> Unit,
        alFallar: (Exception) -> Unit
    ) {
        alObtener(marcajes.filter { it.idEmpleado == idEmpleado }.maxByOrNull { it.instante })
    }

    @Synchronized
    override fun guardar(
        marcaje: Marcaje,
        alGuardar: () -> Unit,
        alFallar: (Exception) -> Unit
    ) {
        marcajes.add(marcaje)
        alGuardar()
    }

    @Synchronized
    fun todos(): List<Marcaje> = marcajes.toList()
}
