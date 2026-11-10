package com.ejemplo.relojcontroliot

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

        // Captura de datos transmitidos por el Intent desde MainActivity [8, 22]
        val idEmpleado = intent.getStringExtra("ID_EMPLEADO") ?: "Desconocido"
        val horaMarcaje = intent.getStringExtra("HORA_MARCAJE") ?: "--:--"

        tvMensajeResultado.text = "¡MARCAJE REGISTRADO!\n\nEmpleado: $idEmpleado\nHora: $horaMarcaje"

        btnVolver.setOnClickListener {
            finish() // Libera la Activity y retorna a la pantalla anterior [14, 23]
        }
    }
}