package com.ejemplo.relojcontroliot

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class MainActivity : AppCompatActivity() {

    // UUID estándar para perfil serie SPP en módulo HC-05 [15, 16]
    private val hc05Uuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothSocket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null

    private lateinit var tvEstadoConexion: TextView
    private lateinit var etIdEmpleado: EditText
    private lateinit var btnConectarBT: Button
    private lateinit var btnMarcarAsistencia: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main) // Vincula layout XML [17]

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()

        solicitarPermisosBluetooth()
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
        // Uso de setOnClickListener según instrucciones de clase [18]
        btnConectarBT.setOnClickListener {
            conectarHC05()
        }

        btnMarcarAsistencia.setOnClickListener {
            val idEmpleado = etIdEmpleado.text.toString().trim()
            if (idEmpleado.isEmpty()) {
                etIdEmpleado.error = "Ingrese un ID válido" // Validación de campo [12]
            } else {
                confirmarYEnviarMarcaje(idEmpleado)
            }
        }
    }

    private fun conectarHC05() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            solicitarPermisosBluetooth()
            return
        }

        val dispositivos: Set<BluetoothDevice>? = bluetoothAdapter?.bondedDevices
        val hc05Device = dispositivos?.find { it.name == "HC-05" || it.name == "HC-06" }

        if (hc05Device != null) {
            try {
                bluetoothSocket = hc05Device.createRfcommSocketToServiceRecord(hc05Uuid)
                bluetoothSocket?.connect()
                outputStream = bluetoothSocket?.outputStream

                tvEstadoConexion.text = "Estado: Conectado a ${hc05Device.name}"
                Toast.makeText(this, "Conectado al Tótem", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                // ISO/IEC 27400: Control de timeouts e interfaces visibles [11]
                tvEstadoConexion.text = "Estado: Error de Conexión"
                Toast.makeText(this, "Falla de conexión: ${e.message}", Toast.LENGTH_LONG).show()
            }
        } else {
            Toast.makeText(this, "Módulo HC-05 no encontrado en vinculados", Toast.LENGTH_SHORT).show()
        }
    }

    private fun confirmarYEnviarMarcaje(idEmpleado: String) {
        // Confirmación previa de seguridad antes de transmitir datos [11]
        AlertDialog.Builder(this)
            .setTitle("Confirmar Asistencia")
            .setMessage("¿Desea registrar asistencia para el ID: $idEmpleado?")
            .setPositiveButton("MARCAR") { _, _ ->
                val tramaSerial = "MARCA:$idEmpleado\n"
                val enviado = transmitirPorBluetooth(tramaSerial)

                if (enviado) {
                    val horaActual = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())

                    // Creación e invocación del Intent para cambiar de Activity [3, 19]
                    val intent = Intent(this, ResultadoMarcajeActivity::class.java)
                    intent.putExtra("ID_EMPLEADO", idEmpleado)   // Adjunta alias y valor [8]
                    intent.putExtra("HORA_MARCAJE", horaActual)
                    startActivity(intent) // Dispara el salto de pantalla [20]
                }
            }
            .setNegativeButton("CANCELAR", null)
            .show()
    }

    private fun transmitirPorBluetooth(trama: String): Boolean {
        return try {
            if (outputStream != null) {
                outputStream?.write(trama.toByteArray())
                true
            } else {
                Toast.makeText(this, "Debe conectar el Bluetooth primero", Toast.LENGTH_SHORT).show()
                false
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error de transmisión serial: ${e.message}", Toast.LENGTH_SHORT).show()
            false
        }
    }

    private fun solicitarPermisosBluetooth() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN),
                101
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            outputStream?.close()
            bluetoothSocket?.close() // Cierre seguro de sockets al destruir [14, 21]
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}