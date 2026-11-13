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

        tvMensajeResultado = findViewById(R.id.tvMensajeResultado)
        btnVolver = findViewById(R.id.btnVolver)

        val idEmpleado =
            intent.getStringExtra("ID_EMPLEADO") ?: "Desconocido"

        val horaMarcaje =
            intent.getStringExtra("HORA_MARCAJE") ?: "--:--"

        tvMensajeResultado.text =
            "¡SOLICITUD ENVIADA!\n\n" +
                    "Empleado: $idEmpleado\n" +
                    "Hora de envío: $horaMarcaje\n\n" +
                    "Solicitud transmitida al tótem."

        btnVolver.setOnClickListener {
            finish()
        }
    }
}
