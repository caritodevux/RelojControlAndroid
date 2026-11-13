package com.example.relojcontrolandroid

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import java.io.IOException
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class MainActivity : AppCompatActivity() {

    companion object {
        private const val REQUEST_BT_PERMISSION = 101
    }

    private val hc05Uuid = UUID.fromString(
        "00001101-0000-1000-8000-00805F9B34FB"
    )

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothSocket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null

    // Por defecto, trabajamos sin hardware.
    private var modoDemo = true
    private var demoConectado = false

    private lateinit var rgModo: RadioGroup
    private lateinit var tvEstadoConexion: TextView
    private lateinit var etIdEmpleado: EditText
    private lateinit var btnConectarBT: Button
    private lateinit var btnMarcarAsistencia: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()

        vincularVistasUI()
        configurarListeners()
        actualizarInterfazModo()
    }

    private fun vincularVistasUI() {
        rgModo = findViewById(R.id.rgModo)
        tvEstadoConexion = findViewById(R.id.tvEstadoConexion)
        etIdEmpleado = findViewById(R.id.etIdEmpleado)
        btnConectarBT = findViewById(R.id.btnConectarBT)
        btnMarcarAsistencia =
            findViewById(R.id.btnMarcarAsistencia)
    }

    private fun configurarListeners() {

        rgModo.setOnCheckedChangeListener { _, checkedId ->

            modoDemo = checkedId == R.id.rbModoDemo

            demoConectado = false

            cerrarConexionBluetooth()

            actualizarInterfazModo()
        }

        btnConectarBT.setOnClickListener {

            if (modoDemo) {
                conectarDemo()
            } else {
                conectarHC05()
            }
        }

        btnMarcarAsistencia.setOnClickListener {

            val idEmpleado =
                etIdEmpleado.text.toString().trim()

            if (idEmpleado.isEmpty()) {
                etIdEmpleado.error = "Ingrese un ID válido"
            } else if (!idEmpleado.matches(
                    Regex("EMP-[A-Z0-9-]+")
                )
            ) {
                etIdEmpleado.error =
                    "Formato esperado: EMP-1042"
            } else {
                confirmarYEnviarMarcaje(idEmpleado)
            }
        }
    }

    private fun actualizarInterfazModo() {

        if (modoDemo) {

            tvEstadoConexion.text =
                if (demoConectado) {
                    "DEMO: Conexión simulada activa"
                } else {
                    "DEMO: Desconectado"
                }

            btnConectarBT.text =
                if (demoConectado) {
                    "Demostración conectada"
                } else {
                    "Iniciar demostración"
                }

        } else {

            tvEstadoConexion.text =
                if (bluetoothSocket?.isConnected == true) {
                    "BLUETOOTH: Conectado"
                } else {
                    "BLUETOOTH: Desconectado"
                }

            btnConectarBT.text = "Conectar a Tótem (HC-05)"
        }

        btnMarcarAsistencia.isEnabled =
            if (modoDemo) demoConectado
            else bluetoothSocket?.isConnected == true
    }

    // -------------------------------------------------
    // MODO DEMOSTRACIÓN
    // -------------------------------------------------

    private fun conectarDemo() {

        demoConectado = true

        actualizarInterfazModo()

        Toast.makeText(
            this,
            "Modo demostración activado. No se utiliza Bluetooth real.",
            Toast.LENGTH_LONG
        ).show()
    }

    private fun simularMarcaje(idEmpleado: String) {

        btnMarcarAsistencia.isEnabled = false

        Toast.makeText(
            this,
            "Procesando marcaje simulado...",
            Toast.LENGTH_SHORT
        ).show()

        // Simula el tiempo de respuesta del sistema.
        Handler(Looper.getMainLooper()).postDelayed({

            if (isFinishing || isDestroyed) return@postDelayed

            abrirPantallaResultado(
                idEmpleado = idEmpleado,
                esDemo = true
            )

        }, 1000)
    }

    // -------------------------------------------------
    // MODO BLUETOOTH REAL
    // -------------------------------------------------

    private fun conectarHC05() {

        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {

            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.BLUETOOTH_CONNECT
                ),
                REQUEST_BT_PERMISSION
            )

            return
        }

        val adapter = bluetoothAdapter

        if (adapter == null) {
            Toast.makeText(
                this,
                "El dispositivo no dispone de Bluetooth",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        try {

            if (!adapter.isEnabled) {

                startActivity(
                    Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                )

                return
            }

            val dispositivo: BluetoothDevice? =
                adapter.bondedDevices.firstOrNull {
                    it.name == "HC-05" || it.name == "HC-06"
                }

            if (dispositivo == null) {

                Toast.makeText(
                    this,
                    "Vincule primero el HC-05 desde los ajustes Bluetooth",
                    Toast.LENGTH_LONG
                ).show()

                return
            }

            tvEstadoConexion.text = "BLUETOOTH: Conectando..."
            btnConectarBT.isEnabled = false

            Thread {

                var socketTemporal: BluetoothSocket? = null

                try {

                    val socket =
                        dispositivo.createRfcommSocketToServiceRecord(
                            hc05Uuid
                        )

                    socketTemporal = socket

                    socket.connect()

                    val nuevoOutputStream = socket.outputStream

                    runOnUiThread {

                        if (isFinishing || isDestroyed) {
                            socket.close()
                            return@runOnUiThread
                        }

                        bluetoothSocket = socket
                        outputStream = nuevoOutputStream

                        btnConectarBT.isEnabled = true

                        tvEstadoConexion.text =
                            "BLUETOOTH: Conectado a ${dispositivo.name}"

                        actualizarInterfazModo()

                        Toast.makeText(
                            this,
                            "Conexión Bluetooth exitosa",
                            Toast.LENGTH_SHORT
                        ).show()
                    }

                } catch (e: Exception) {

                    try {
                        socketTemporal?.close()
                    } catch (_: IOException) {
                    }

                    runOnUiThread {

                        btnConectarBT.isEnabled = true

                        tvEstadoConexion.text =
                            "BLUETOOTH: Error de conexión"

                        Toast.makeText(
                            this,
                            "Error Bluetooth: ${e.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }

            }.start()

        } catch (e: SecurityException) {

            Toast.makeText(
                this,
                "Falta autorización Bluetooth",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    // -------------------------------------------------
    // CONFIRMACIÓN DE ASISTENCIA
    // -------------------------------------------------

    private fun confirmarYEnviarMarcaje(idEmpleado: String) {

        val conectado = if (modoDemo) {
            demoConectado
        } else {
            bluetoothSocket?.isConnected == true &&
                    outputStream != null
        }

        if (!conectado) {

            Toast.makeText(
                this,
                "Debe conectar el sistema primero",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        val titulo = if (modoDemo) {
            "Confirmar marcaje (DEMO)"
        } else {
            "Confirmar asistencia"
        }

        val mensaje = if (modoDemo) {
            "Se simulará el registro de asistencia para $idEmpleado."
        } else {
            "¿Desea enviar el registro de asistencia para $idEmpleado?"
        }

        AlertDialog.Builder(this)
            .setTitle(titulo)
            .setMessage(mensaje)
            .setPositiveButton("MARCAR") { _, _ ->

                if (modoDemo) {
                    simularMarcaje(idEmpleado)
                } else {
                    transmitirPorBluetooth(idEmpleado)
                }
            }
            .setNegativeButton("CANCELAR", null)
            .show()
    }

    // -------------------------------------------------
    // TRANSMISIÓN BLUETOOTH REAL
    // -------------------------------------------------

    private fun transmitirPorBluetooth(idEmpleado: String) {

        val socket = bluetoothSocket
        val stream = outputStream

        if (
            socket == null ||
            !socket.isConnected ||
            stream == null
        ) {
            Toast.makeText(
                this,
                "Conexión Bluetooth no disponible",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        val tramaSerial = "MARCA:$idEmpleado\n"

        btnMarcarAsistencia.isEnabled = false

        Thread {

            try {

                stream.write(
                    tramaSerial.toByteArray(Charsets.UTF_8)
                )

                stream.flush()

                runOnUiThread {

                    btnMarcarAsistencia.isEnabled = true

                    abrirPantallaResultado(
                        idEmpleado = idEmpleado,
                        esDemo = false
                    )
                }

            } catch (e: Exception) {

                runOnUiThread {

                    btnMarcarAsistencia.isEnabled = true

                    Toast.makeText(
                        this,
                        "Error al transmitir: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }

        }.start()
    }

    // -------------------------------------------------
    // RESULTADO
    // -------------------------------------------------

    private fun abrirPantallaResultado(
        idEmpleado: String,
        esDemo: Boolean
    ) {

        val horaActual = SimpleDateFormat(
            "HH:mm:ss",
            Locale.getDefault()
        ).format(Date())

        val intent = Intent(
            this,
            ResultadoMarcajeActivity::class.java
        )

        intent.putExtra("ID_EMPLEADO", idEmpleado)
        intent.putExtra("HORA_MARCAJE", horaActual)
        intent.putExtra("MODO_DEMO", esDemo)

        startActivity(intent)

        btnMarcarAsistencia.isEnabled = true
    }

    // -------------------------------------------------
    // PERMISOS
    // -------------------------------------------------

    @Suppress("DEPRECATION")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {

        super.onRequestPermissionsResult(
            requestCode,
            permissions,
            grantResults
        )

        if (requestCode == REQUEST_BT_PERMISSION) {

            if (
                grantResults.isNotEmpty() &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED
            ) {
                conectarHC05()
            } else {
                Toast.makeText(
                    this,
                    "Permiso Bluetooth denegado",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    // -------------------------------------------------
    // CIERRE DE CONEXIÓN
    // -------------------------------------------------

    private fun cerrarConexionBluetooth() {

        try {
            outputStream?.close()
            bluetoothSocket?.close()
        } catch (_: IOException) {
        }

        outputStream = null
        bluetoothSocket = null
    }

    override fun onDestroy() {

        cerrarConexionBluetooth()

        super.onDestroy()
    }
}
