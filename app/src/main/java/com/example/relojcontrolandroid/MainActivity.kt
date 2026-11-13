package com.example.relojcontrolandroid

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
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

    private val hc05Uuid: UUID = UUID.fromString(
        "00001101-0000-1000-8000-00805F9B34FB"
    )

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothSocket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null

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
    }

    private fun vincularVistasUI() {
        tvEstadoConexion = findViewById(R.id.tvEstadoConexion)
        etIdEmpleado = findViewById(R.id.etIdEmpleado)
        btnConectarBT = findViewById(R.id.btnConectarBT)
        btnMarcarAsistencia = findViewById(R.id.btnMarcarAsistencia)
    }

    private fun configurarListeners() {
        btnConectarBT.setOnClickListener {
            conectarHC05()
        }

        btnMarcarAsistencia.setOnClickListener {
            val idEmpleado = etIdEmpleado.text.toString().trim()

            if (idEmpleado.isEmpty()) {
                etIdEmpleado.error = "Ingrese un ID válido"
            } else {
                confirmarYEnviarMarcaje(idEmpleado)
            }
        }
    }

    private fun conectarHC05() {

        // Android 12 o superior requiere permiso BLUETOOTH_CONNECT.
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.BLUETOOTH_CONNECT),
                REQUEST_BT_PERMISSION
            )
            return
        }

        val adapter = bluetoothAdapter

        if (adapter == null) {
            Toast.makeText(
                this,
                "Este dispositivo no tiene Bluetooth",
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
                    "Primero vincule el HC-05 desde los ajustes Bluetooth del teléfono",
                    Toast.LENGTH_LONG
                ).show()
                return
            }

            tvEstadoConexion.text = "Estado: Conectando..."
            btnConectarBT.isEnabled = false

            // La conexión se realiza fuera del hilo principal.
            Thread {
                var socketTemporal: BluetoothSocket? = null

                try {
                    val socket =
                        dispositivo.createRfcommSocketToServiceRecord(hc05Uuid)

                    socketTemporal = socket
                    socket.connect()

                    val nuevoOutputStream = socket.outputStream

                    runOnUiThread {
                        bluetoothSocket = socket
                        outputStream = nuevoOutputStream

                        tvEstadoConexion.text =
                            "Estado: Conectado a ${dispositivo.name}"

                        btnConectarBT.isEnabled = true

                        Toast.makeText(
                            this,
                            "Conectado al tótem correctamente",
                            Toast.LENGTH_SHORT
                        ).show()
                    }

                } catch (e: Exception) {

                    try {
                        socketTemporal?.close()
                    } catch (_: IOException) {
                    }

                    runOnUiThread {
                        tvEstadoConexion.text =
                            "Estado: Error de conexión"

                        btnConectarBT.isEnabled = true

                        Toast.makeText(
                            this,
                            "Falla de conexión: ${e.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }.start()

        } catch (e: SecurityException) {
            Toast.makeText(
                this,
                "Falta autorización para utilizar Bluetooth",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun confirmarYEnviarMarcaje(idEmpleado: String) {

        if (bluetoothSocket?.isConnected != true || outputStream == null) {
            Toast.makeText(
                this,
                "Debe conectar el Bluetooth primero",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        AlertDialog.Builder(this)
            .setTitle("Confirmar asistencia")
            .setMessage(
                "¿Desea registrar asistencia para el ID: $idEmpleado?"
            )
            .setPositiveButton("MARCAR") { _, _ ->
                transmitirPorBluetooth(idEmpleado)
            }
            .setNegativeButton("CANCELAR", null)
            .show()
    }

    private fun transmitirPorBluetooth(idEmpleado: String) {

        val socket = bluetoothSocket
        val stream = outputStream

        if (socket == null || !socket.isConnected || stream == null) {
            Toast.makeText(
                this,
                "La conexión Bluetooth no está disponible",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        // Esta es la trama que espera Arduino:
        // MARCA:EMP-1042\n
        val tramaSerial = "MARCA:$idEmpleado\n"

        btnMarcarAsistencia.isEnabled = false

        Thread {
            try {
                stream.write(tramaSerial.toByteArray(Charsets.UTF_8))
                stream.flush()

                val horaActual = SimpleDateFormat(
                    "HH:mm:ss",
                    Locale.getDefault()
                ).format(Date())

                runOnUiThread {
                    btnMarcarAsistencia.isEnabled = true

                    val intent = Intent(
                        this,
                        ResultadoMarcajeActivity::class.java
                    )

                    intent.putExtra("ID_EMPLEADO", idEmpleado)
                    intent.putExtra("HORA_MARCAJE", horaActual)

                    startActivity(intent)
                }

            } catch (e: Exception) {
                runOnUiThread {
                    btnMarcarAsistencia.isEnabled = true

                    Toast.makeText(
                        this,
                        "Error al enviar marcaje: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }.start()
    }

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
                    "Se necesita permiso Bluetooth para conectar el tótem",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    override fun onDestroy() {
        try {
            outputStream?.close()
            bluetoothSocket?.close()
        } catch (_: IOException) {
        }

        super.onDestroy()
    }
}
