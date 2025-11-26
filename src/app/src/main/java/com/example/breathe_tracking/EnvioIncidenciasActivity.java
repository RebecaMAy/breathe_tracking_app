package com.example.breathe_tracking;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class EnvioIncidenciasActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.envio_incidencias);

        // Encontrar las vistas
        EditText tituloEditText = findViewById(R.id.editText_tituloIncidencia);
        EditText mensajeEditText = findViewById(R.id.editText_mensaje);
        Button acceptButton = findViewById(R.id.button_aceptar);
        Button cancelButton = findViewById(R.id.button_cancelar);

        // --- Lógica para rellenar los datos automáticamente ---
        Intent intent = getIntent();
        String sensorName = intent.getStringExtra("SENSOR_NAME");
        String ubicacion = intent.getStringExtra("UBICACION");
        String ultimaConexion = intent.getStringExtra("ULTIMA_CONEXION");

        if (ultimaConexion != null) {
            ultimaConexion = ultimaConexion.replace("Última conex. ", "");
        }

        // --- Lógica para rellenar los campos de texto ---
        // rellenamos automaticamente el asunto y el mensaje
        String titulo = String.format("AVISO:'%s' Desconectado", sensorName);
        String mensaje = String.format(Locale.getDefault(),
                "El '%s' de la zona '%s' ha dejado de funcionar. La última lectura se recibió a las %s. Por favor, compruebe la conexión o si existe algún problema con el sensor.",
                sensorName, ubicacion, ultimaConexion);

        tituloEditText.setText(titulo);
        mensajeEditText.setText(mensaje);

        // --- Lógica de los botones ---
        cancelButton.setOnClickListener(v -> {
            finish();
        });

        acceptButton.setOnClickListener(v -> {
            String tituloIncidencia = tituloEditText.getText().toString();
            String mensajeIncidencia = mensajeEditText.getText().toString();

            // ------ Implemetnacion Firebase ---------------------------
            FirebaseFirestore db = FirebaseFirestore.getInstance();
            Map<String, Object> incidencia = new HashMap<>();
            incidencia.put("sensor_id", sensorName);           // Codigo de sensor
            incidencia.put("titulo", tituloIncidencia);
            incidencia.put("mensaje", mensajeIncidencia);        // Contenido manual del usuario
            incidencia.put("ubicacion", ubicacion);
            incidencia.put("estado", "PENDIENTE");
            incidencia.put("resuelta", false);
            incidencia.put("fecha", FieldValue.serverTimestamp());
            // ------------------------------------------

            db.collection("incidencias").add(incidencia)
                    .addOnSuccessListener(documentReference -> {
                        // ... (Manejo del éxito)
                        new AlertDialog.Builder(this)
                                .setTitle("Enviado")
                                .setMessage("Mensaje enviado correctamente al administrador")
                                .setPositiveButton("Aceptar", (dialog, which) -> {
                                    finish();
                                })
                                .show();
                    })
                    .addOnFailureListener(e -> {
                        // ... (Manejo del fallo)
                        new AlertDialog.Builder(this)
                                .setTitle("Error de Envío")
                                .setMessage("No se pudo enviar la incidencia. Compruebe su conexión a Internet. Error: " + e.getMessage())
                                .setPositiveButton("Aceptar", null)
                                .show();
                    });
        });
    }
}
