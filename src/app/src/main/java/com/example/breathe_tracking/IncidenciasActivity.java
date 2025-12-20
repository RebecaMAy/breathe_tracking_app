package com.example.breathe_tracking;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.ImageView;
import android.widget.TextView;
import android.Manifest;


import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import java.util.ArrayList;
import java.util.List;

public class IncidenciasActivity extends AppCompatActivity {

    private TextView ultimasAlertasTextView;
    private TextView incidenciasEnviadasTextView;
    private ImageView backArrow;
    private TrackingDataHolder dataHolder;

    /** @brief Lista que almacena las últimas 4 alertas de mediciones recibidas. */
    private final List<String> ultimasCuatroAlertas = new ArrayList<>();
    /** @brief Constante que define el número máximo de alertas a mostrar. */
    private static final int MAX_ALERTS = 4;
    /** @brief ID para el canal de notificaciones de alertas. */
    private static final String CHANNEL_ID = "alert_channel";
    /** @brief Contador para asegurar que cada notificación tenga un ID único. */
    private int notificationIdCounter = 0;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.incidencias);

        ultimasAlertasTextView = findViewById(R.id.textView_ultimasAlertas);
        incidenciasEnviadasTextView = findViewById(R.id.textView_incidenciasEnviadas);
        backArrow = findViewById(R.id.img_back_arrow);

        dataHolder = TrackingDataHolder.getInstance();

        // Botón para volver a la pantalla anterior
        backArrow.setOnClickListener(v -> finish());

        // Crea el canal de notificaciones al iniciar la actividad
        createNotificationChannel();
        // Observadores para actualizar los datos en tiempo real
        setupObservers();
    }

    /**
     * @brief Configura los observadores para los datos de alertas e incidencias.
     *        La lógica de alertas ahora gestiona una lista persistente de hasta 4 mensajes,
     *        lanza una notificación por cada nueva alerta y reemplaza las más antiguas.
     */
    private void setupObservers() {
        // Observa las nuevas alertas de mediciones que llegan desde el servicio
        dataHolder.alertData.observe(this, newAlerts -> {
            if (newAlerts != null && !newAlerts.isEmpty()) {
                boolean listChanged = false;
                // Itera sobre las nuevas alertas recibidas
                for (String alert : newAlerts) {
                    // Si la alerta no está ya en nuestra lista, la procesa como nueva
                    if (!ultimasCuatroAlertas.contains(alert)) {
                        // Lanza una notificación para la nueva alerta
                        sendNotification("Nueva Alerta de Medición", alert);

                        // Añade la nueva alerta al principio de la lista
                        ultimasCuatroAlertas.add(0, alert);
                        listChanged = true;
                        // Si la lista supera el tamaño máximo, elimina la alerta más antigua (la última de la lista)
                        if (ultimasCuatroAlertas.size() > MAX_ALERTS) {
                            ultimasCuatroAlertas.remove(MAX_ALERTS);
                        }
                    }
                }
                // Si la lista ha cambiado, actualiza la interfaz
                if (listChanged) {
                    actualizarTextoAlertas();
                }
            }
            // Importante: Si newAlerts es null o está vacía, no hacemos nada.
            // Esto preserva las alertas antiguas en pantalla aunque la condición que las generó ya no se cumpla.
        });

        // Observa y muestra las incidencias de conexión
        dataHolder.incidenciaData.observe(this, incidencia -> {
            if (incidencia != null) {
                incidenciasEnviadasTextView.setText(incidencia);
            }
        });

        // Carga inicial del texto de alertas al crear la actividad
        actualizarTextoAlertas();
    }

    /**
     * @brief Actualiza el TextView de alertas con el contenido de la lista.
     *        Muestra "No hay alertas" si la lista está vacía, o formatea
     *        las alertas con saltos de línea si hay contenido.
     */
    private void actualizarTextoAlertas() {
        if (ultimasCuatroAlertas.isEmpty()) {
            ultimasAlertasTextView.setText("No hay alertas");
        } else {
            // Une las alertas de la lista con dobles saltos de línea para mejorar la legibilidad
            String textoAlertas = TextUtils.join("\n\n", ultimasCuatroAlertas);
            ultimasAlertasTextView.setText(textoAlertas);
        }
    }

    /**
     * @brief Crea el canal de notificaciones necesario para Android 8.0 (API 26) y superior.
     *        Si el canal ya existe, la operación no hace nada, por lo que es seguro llamarlo múltiples veces.
     */
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "Alertas de Medición";
            String description = "Canal para notificaciones de alertas de mediciones del sensor.";
            int importance = NotificationManager.IMPORTANCE_HIGH;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);

            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }

    /**
     * @brief Crea y muestra una notificación en el dispositivo para una nueva alerta.
     * @param title El título de la notificación.
     * @param message El cuerpo del mensaje de la notificación.
     *
     * La notificación, al ser pulsada, abrirá esta misma actividad (IncidenciasActivity).
     * NOTA: Es necesario añadir y solicitar el permiso POST_NOTIFICATIONS en Android 13+.
     */
    private void sendNotification(String title, String message) {
        // Intent para abrir la app al pulsar la notificación
        Intent intent = new Intent(this, IncidenciasActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notifications) // Icono de la notificación
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent) // Acción al pulsar
                .setAutoCancel(true); // La notificación se cierra al pulsarla

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);

        // Comprobación de permiso (necesario para Android 13+)
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            // Si no se tiene el permiso, no se puede enviar la notificación.
            // Aquí se debería incluir la lógica para solicitar el permiso al usuario.
            // Por ejemplo: ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_CODE);
            return;
        }
        // Se usa un ID único para que cada notificación nueva aparezca por separado.
        notificationManager.notify(notificationIdCounter++, builder.build());
    }
}