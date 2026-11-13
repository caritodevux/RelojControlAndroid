package com.example.relojcontrolandroid

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class ResultadoMarcajeActivity : AppCompatActivity() {

    private lateinit var tvMensajeResultado: TextView
    private lateinit var btnVolver: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_resultado_marcaje)

        tvMensajeResultado = findViewById(
            R.id.tvMensajeResultado
        )

        btnVolver = findViewById(
            R.id.btnVolver
        )

        val idEmpleado =
            intent.getStringExtra("ID_EMPLEADO") ?: "Desconocido"

        val horaMarcaje =
            intent.getStringExtra("HORA_MARCAJE") ?: "--:--"

        val esDemo =
            intent.getBooleanExtra("MODO_DEMO", false)

        if (esDemo) {

            tvMensajeResultado.text =
                "¡MARCACIÓN SIMULADA!\n\n" +
                        "Empleado: $idEmpleado\n" +
                        "Hora: $horaMarcaje\n\n" +
                        "Modo: DEMOSTRACIÓN\n\n" +
                        "Resultado generado localmente.\n" +
                        "No se utilizó Arduino ni Bluetooth físico."

        } else {

            tvMensajeResultado.text =
                "¡SOLICITUD ENVIADA!\n\n" +
                        "Empleado: $idEmpleado\n" +
                        "Hora de envío: $horaMarcaje\n\n" +
                        "Modo: BLUETOOTH REAL\n\n" +
                        "Solicitud transmitida al tótem.\n" +
                        "Pendiente de confirmación del Arduino."
        }

        btnVolver.setOnClickListener {
            finish()
        }
    }
}