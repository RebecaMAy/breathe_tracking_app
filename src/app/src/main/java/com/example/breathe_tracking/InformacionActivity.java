package com.example.breathe_tracking;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.LimitLine;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * @class GasData
 * @brief Almacena los valores históricos de los gases y su timestamp.
 */
class GasData {
    float ozono;
    int co2;
    long timestamp;

    public GasData(float ozono, int co2, long timestamp) {
        this.ozono = ozono;
        this.co2 = co2;
        this.timestamp = timestamp;
    }
}

/**
 * @class InformacionActivity
 * @brief Muestra el gráfico de datos históricos (CO2 y O3) vinculados al sensor
 * que inició la sesión, leídos de Firebase Firestore.
 */
public class InformacionActivity extends AppCompatActivity {

    // --- VARIABLES DE FIREBASE ---
    private FirebaseFirestore db;
    private String sensorId;
    private List<GasData> historicalData = new ArrayList<>();
    // -----------------------------

    private LineChart chart;
    private Spinner spinner;
    private ImageView imgCarita;
    private ImageView imgBackArrow;
    private TextView txtExplicacion;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.informacion);

        // 1. Inicialización de Firebase y obtención del ID
        db = FirebaseFirestore.getInstance();

        // Obtención del ID del sensor pasado desde SesionSensorActivity
        Intent intent = getIntent();
        if (intent.hasExtra("SENSOR_ID")) {
            sensorId = intent.getStringExtra("SENSOR_ID");
        } else {
            // ID por defecto o manejo de error si es nulo
            sensorId = "12345";
            Toast.makeText(this, "Advertencia: ID de sensor no encontrado. Usando: " + sensorId, Toast.LENGTH_SHORT).show();
        }


        chart = findViewById(R.id.chart_gases);
        spinner = findViewById(R.id.spinner_gases);
        imgCarita = findViewById(R.id.img_carita);
        imgBackArrow = findViewById(R.id.img_back_arrow);
        txtExplicacion = findViewById(R.id.txt_explicacion);

        imgBackArrow.setOnClickListener(v -> finish());

        // 2. Configurar Spinner
        String[] gases = {
                "Ozono (O3)",
                "Monóxido de Carbono (CO)",
                "Dióxido de Nitrógeno (NO2)",
                "Dióxido de Azufre (SO2)",
                "Dióxido de Carbono (CO2)"
        };

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, gases);
        spinner.setAdapter(adapter);

        // 3. Listener del Spinner
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                // Llama a cargarDatosGas, que maneja datos reales o simulados
                cargarDatosGas(position);
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        // 4. Configuración base del gráfico
        configurarEstiloGrafico();

        // 5. Cargar los datos de Firebase y luego cargar el gráfico por defecto (Ozono)
        cargarDatosDeFirebase();
    }

    /**
     * @brief Consulta Firestore para obtener los datos históricos del sensor actual.
     */
    private void cargarDatosDeFirebase() {
        if (sensorId == null) return;

        Toast.makeText(this, "Cargando datos históricos...", Toast.LENGTH_SHORT).show();

        // Consulta la subcolección 'mediciones'
        db.collection("sensores").document(sensorId).collection("mediciones")
                // Se asume que el campo 'fecha' o 'timestamp' se usa para ordenar
                .orderBy("fecha", Query.Direction.ASCENDING)
                .limit(50)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    historicalData.clear();

                    if (queryDocumentSnapshots.isEmpty()) {
                        Toast.makeText(this, "No se encontraron datos históricos para este sensor.", Toast.LENGTH_LONG).show();
                        // Forzamos a dibujar la gráfica con valores simulados
                        cargarDatosGas(0);
                        return;
                    }

                    for (QueryDocumentSnapshot document : queryDocumentSnapshots) {

                        // Extracción segura de datos
                        Float ozono = document.get("ozono", Float.class);
                        Long co2Long = document.getLong("co2");

                        // Obtener el timestamp (asumiendo que se guardó como FieldValue.serverTimestamp() o similar)
                        Long timestampLong = document.getTimestamp("fecha") != null ? document.getTimestamp("fecha").getSeconds() * 1000 : 0L;

                        if (ozono != null && co2Long != null) {
                            historicalData.add(new GasData(ozono, co2Long.intValue(), timestampLong));
                        }
                    }

                    // Después de cargar, cargar el gráfico por defecto (Ozono) con los datos reales
                    cargarDatosGas(0);
                    Toast.makeText(this, "Datos históricos cargados con éxito.", Toast.LENGTH_SHORT).show();

                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Error al cargar datos: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    // En caso de fallo en la BD, se intenta cargar la simulación
                    cargarDatosGas(0);
                });
    }


    private void configurarEstiloGrafico() {
        chart.getDescription().setEnabled(false);
        chart.setTouchEnabled(true);
        chart.setDragEnabled(true);
        chart.setScaleEnabled(true);
        chart.setPinchZoom(true);

        XAxis xAxis = chart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false);

        chart.getAxisRight().setEnabled(false);
    }

    /**
     * @brief Carga y dibuja los datos del gas seleccionado, usando datos reales si existen
     * para O3 y CO2, y datos simulados para los otros.
     * @param position Índice del gas seleccionado en el Spinner.
     */
    private void cargarDatosGas(int position) {

        float limiteSeguro = 0f;
        float limitePeligro = 0f;
        String unidad = "";
        String nombreGas = "";

        List<Entry> entries = new ArrayList<>();
        boolean usandoDatosReales = false;

        switch (position) {
            case 0: // Ozono (O3)
                nombreGas = "Ozono (O3)";
                unidad = "ppm";
                limiteSeguro = 0.6f;
                limitePeligro = 0.9f;
                // --- DATOS REALES ---
                if (!historicalData.isEmpty()) {
                    usandoDatosReales = true;
                    for (int i = 0; i < historicalData.size(); i++) {
                        entries.add(new Entry(i, historicalData.get(i).ozono));
                    }
                }
                break;

            case 4: // Dióxido de Carbono (CO2)
                nombreGas = "Dióxido de Carbono (CO2)";
                unidad = "ppm";
                limiteSeguro = 800f;
                limitePeligro = 1200f;
                // --- DATOS REALES ---
                if (!historicalData.isEmpty()) {
                    usandoDatosReales = true;
                    for (int i = 0; i < historicalData.size(); i++) {
                        entries.add(new Entry(i, historicalData.get(i).co2));
                    }
                }
                break;

            case 1: // Monóxido de Carbono (CO) (SIMULADO)
            case 2: // Dióxido de Nitrógeno (NO2) (SIMULADO)
            case 3: // Dióxido de Azufre (SO2) (SIMULADO)
                // Lógica de límites para gases simulados
                nombreGas = (position == 1) ? "Monóxido de Carbono (CO)" : (position == 2) ? "Dióxido de Nitrógeno (NO2)" : "Dióxido de Azufre (SO2)";
                unidad = (position == 1) ? "mg/m³" : "µg/m³";
                limiteSeguro = (position == 1) ? 10f : (position == 2) ? 200f : 350f;
                limitePeligro = (position == 1) ? 30f : (position == 2) ? 400f : 500f;

                // Generación de datos simulados
                float valorMinSimulado = (position == 1) ? 5f : (position == 2) ? 100f : 200f;
                float valorMaxSimulado = (position == 1) ? 40f : (position == 2) ? 500f : 600f;
                float rango = valorMaxSimulado - valorMinSimulado;
                for (int i = 0; i < 24; i++) {
                    float valor = (float) (valorMinSimulado + Math.random() * rango);
                    entries.add(new Entry(i, valor));
                }
                break;
        }

        // Si se seleccionó O3 o CO2 y no hay datos reales, se muestra vacío o un mensaje.
        if (!usandoDatosReales && (position == 0 || position == 4)) {
            Toast.makeText(this, "No hay datos reales para graficar. Intente más tarde.", Toast.LENGTH_LONG).show();
            chart.setData(null);
            chart.invalidate();
            return;
        }


        // Configuración de Eje Y y Limit Lines
        YAxis leftAxis = chart.getAxisLeft();
        leftAxis.removeAllLimitLines();
        leftAxis.setAxisMinimum(0f);

        LimitLine llSeguro = new LimitLine(limiteSeguro, "Seguro (" + limiteSeguro + " " + unidad + ")");
        llSeguro.setLineColor(Color.GREEN);
        llSeguro.setLineWidth(2f);
        llSeguro.enableDashedLine(10f, 10f, 0f);
        llSeguro.setLabelPosition(LimitLine.LimitLabelPosition.RIGHT_BOTTOM);
        llSeguro.setTextSize(10f);

        LimitLine llPeligro = new LimitLine(limitePeligro, "Peligroso (" + limitePeligro + " " + unidad + ")");
        llPeligro.setLineColor(Color.RED);
        llPeligro.setLineWidth(2f);
        llPeligro.enableDashedLine(10f, 10f, 0f);
        llPeligro.setLabelPosition(LimitLine.LimitLabelPosition.RIGHT_TOP);
        llPeligro.setTextSize(10f);

        leftAxis.addLimitLine(llSeguro);
        leftAxis.addLimitLine(llPeligro);


        // Creación del Dataset y estilización
        LineDataSet dataSet = new LineDataSet(entries, "Nivel de " + nombreGas);
        dataSet.setColor(ContextCompat.getColor(this, R.color.colorPrimary));
        dataSet.setLineWidth(1f);
        dataSet.setCircleRadius(2f);
        dataSet.setDrawCircleHole(false);
        dataSet.setDrawValues(false);
        dataSet.setDrawFilled(true);
        dataSet.setFillDrawable(ContextCompat.getDrawable(this, R.drawable.gradiente_semaforo));
        dataSet.setFillAlpha(255);

        // Aplicar y animar el gráfico
        LineData lineData = new LineData(dataSet);
        chart.setData(lineData);
        chart.animateX(1000);

        chart.invalidate();

        // Evaluar exposición con los nuevos límites
        evaluarExposicion(entries, limiteSeguro, limitePeligro);
    }

    private void evaluarExposicion(List<Entry> datos, float limiteSeguro, float limitePeligro) {
        int contadorPeligroso = 0;
        int contadorRiesgo = 0;

        for (Entry e : datos) {
            float val = e.getY();
            if (val > limitePeligro) {
                contadorPeligroso++;
            } else if (val > limiteSeguro) {
                contadorRiesgo++;
            }
        }

        if (contadorPeligroso > 0) {
            imgCarita.setImageResource(R.drawable.ic_face_sad);
            txtExplicacion.setText("¡Alerta! Se han detectado niveles PELIGROSOS. Evita la zona.");
        } else if (contadorRiesgo > 3) {
            imgCarita.setImageResource(R.drawable.ic_face_neutral);
            txtExplicacion.setText("Precaución: Niveles de riesgo detectados. La calidad del aire no es óptima.");
        } else {
            imgCarita.setImageResource(R.drawable.ic_face_happy);
            txtExplicacion.setText("¡Excelente! Calidad del aire segura.");
        }
    }
}