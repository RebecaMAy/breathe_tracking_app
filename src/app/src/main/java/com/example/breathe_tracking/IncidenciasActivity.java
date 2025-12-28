/**
 * @file IncidenciasActivity.java
 * @brief Actividad para la visualización del historial de notificaciones y gestión de incidencias.
 * @package com.example.breathe_tracking
 * @copyright Copyright © 2025
 */

package com.example.breathe_tracking;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/**
 * @class IncidenciasActivity
 * @brief Centro de notificaciones de la aplicación.
 * @extends AppCompatActivity
 *
 * @details
 * Esta actividad cumple dos funciones principales:
 * 1. **Historial de Alertas:** Muestra una lista filtrada de las últimas mediciones peligrosas detectadas por el sensor.
 * 2. **Gestión de Incidencias:** Muestra el estado de los reportes enviados y permite la navegación para reportar nuevas incidencias manualmente.
 *
 * Utiliza un algoritmo de filtrado mediante `LinkedHashSet` para garantizar que el usuario vea siempre
 * las alertas más recientes y únicas, evitando la saturación de información en la pantalla.
 */

public class IncidenciasActivity extends AppCompatActivity {

    private TextView ultimasAlertasTextView;
    private TextView incidenciasEnviadasTextView;
    private ImageView backArrow;
    private Button reportarIncidenciaButton;
    private TrackingDataHolder dataHolder;
    private String sensorId;
    private String ubicacion;

    private ListenerRegistration firestoreListener;

    /** @brief Lista que almacena las últimas 6 alertas de mediciones para mostrar en la UI. */
    private final List<String> ultimasSeisAlertas = new ArrayList<>();
    /** @brief Constante que define el número máximo de alertas a mostrar en la UI. */
    private static final int MAX_ALERTS_IN_UI = 6;

    /**
     * @brief Inicialización de la actividad.
     * Recupera el contexto (Sensor y Ubicación) del Intent y configura la navegación.
     * (savedInstanceState:Bundle) -> onCreate() -> ()
     * @param savedInstanceState Estado guardado.
     */

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.incidencias);

        Intent intent = getIntent();
        if (intent != null) {
            sensorId = intent.getStringExtra("SENSOR_NAME");
            ubicacion = intent.getStringExtra("UBICACION"); // Recibimos la ubicación
        }

        ultimasAlertasTextView = findViewById(R.id.textView_ultimasAlertas);
        incidenciasEnviadasTextView = findViewById(R.id.textView_incidenciasEnviadas);
        backArrow = findViewById(R.id.img_back_arrow);
        reportarIncidenciaButton = findViewById(R.id.button_incidenciasManuales);

        dataHolder = TrackingDataHolder.getInstance();

        backArrow.setOnClickListener(v -> finish());

        reportarIncidenciaButton.setOnClickListener(v -> {
            Intent newintent = new Intent(IncidenciasActivity.this, EnvioIncidenciasActivity.class);
            newintent.putExtra("SENSOR_ID", sensorId);
            newintent.putExtra("UBICACION", ubicacion);
            startActivity(newintent);
        });

        setupObserversLocales();
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Iniciamos la escucha cuando la pantalla se hace visible
        setupFirestoreRealtimeListener();
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Detenemos la escucha cuando la pantalla ya no se ve para ahorrar datos y batería
        if (firestoreListener != null) {
            firestoreListener.remove();
            firestoreListener = null;
        }
    }

    /**
     * @brief Conecta con Firestore para escuchar cambios en tiempo real.
     * @details Filtra por:
     * 1. sensor_id actual.
     * 2. resuelta == false (Solo muestra las pendientes).
     * Si una incidencia pasa a 'resuelta=true', desaparece sola de la lista.
     */

    private void setupFirestoreRealtimeListener() {
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        // CONSULTA:
        // 1. Colección incidencias
        // 2. Que pertenezcan a este sensor (o quita esta línea si quieres ver todas las del usuario)
        // 3. Que NO estén resueltas
        // 4. Ordenadas por fecha (más reciente primero)
        Query query = db.collection("incidencias")
                .whereEqualTo("sensor_id", sensorId)
                .whereEqualTo("resuelta", false)
                .orderBy("fecha", Query.Direction.DESCENDING)
                .limit(4); // Solo traemos las últimas para no llenar la pantalla

        firestoreListener = query.addSnapshotListener((snapshots, e) -> {
            if (e != null) {
                // Manejo de error (puede pasar si falta el índice en Firebase)
                incidenciasEnviadasTextView.setText("Error cargando incidencias.");
                return;
            }

            if (snapshots != null && !snapshots.isEmpty()) {
                List<String> listaFormateada = new ArrayList<>();
                SimpleDateFormat sdf = new SimpleDateFormat("dd/MM HH:mm", Locale.getDefault());

                for (QueryDocumentSnapshot doc : snapshots) {
                    String titulo = doc.getString("titulo");
                    // Obtenemos el timestamp de Firebase
                    Timestamp fechaTimestamp = doc.getTimestamp("fecha");

                    String fechaStr = "";
                    if (fechaTimestamp != null) {
                        fechaStr = sdf.format(fechaTimestamp.toDate());
                    }

                    // Formato: "12/05 14:30 - Sensor Caído"
                    listaFormateada.add(fechaStr + " - " + titulo);
                }

                // Actualizamos el TextView
                incidenciasEnviadasTextView.setText(TextUtils.join("\n\n", listaFormateada));
            } else {
                incidenciasEnviadasTextView.setText("No hay incidencias pendientes.");
            }
        });
    }

    private void setupObserversLocales() {

        dataHolder.alertData.observe(this, newAlerts -> {
            if (newAlerts != null && !newAlerts.isEmpty()) {
                // Usamos LinkedHashSet para evitar duplicados y mantener orden
                LinkedHashSet<String> uniqueAlerts = new LinkedHashSet<>(newAlerts);
                uniqueAlerts.addAll(ultimasSeisAlertas);

                ultimasSeisAlertas.clear();
                ultimasSeisAlertas.addAll(uniqueAlerts);

                // Limitar a 6 alertas en pantalla
                while (ultimasSeisAlertas.size() > MAX_ALERTS_IN_UI) {
                    ultimasSeisAlertas.remove(ultimasSeisAlertas.size() - 1);
                }

                actualizarTextoAlertas();
            }
        });

        // Inicializar texto si ya había alertas
        actualizarTextoAlertas();

    }


    /**
     * @brief Renderiza la lista de alertas en el TextView.
     * Utiliza `TextUtils.join` para separar cada alerta con un doble salto de línea.
     */
    private void actualizarTextoAlertas() {
        if (ultimasSeisAlertas.isEmpty()) {
            ultimasAlertasTextView.setText("No hay alertas de sensores");
        } else {
            ultimasAlertasTextView.setText(TextUtils.join("\n\n", ultimasSeisAlertas));
        }
    }
}
