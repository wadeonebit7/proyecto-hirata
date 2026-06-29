/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.hirata.controller;

import com.hirata.dao.TelemetriaDAO;
import com.hirata.model.TelemetriaRuta;
import java.util.List;
import java.util.Random;
import org.jxmapviewer.viewer.GeoPosition;

/**
 *
 * @author wadev
 */
public class ControladorTelemetria implements Runnable {

    private final int idCamion;
    private final int idGps;
    private final TelemetriaDAO dao;
    private final Random random;
    
    // VARIABLES DE CONTROL DINÁMICO
    private final List<GeoPosition> rutaRealCalles; 
    private boolean enRuta;
    private double combustibleActual;
    private double temperaturaCarga;
    private String patente;
    
    private boolean alertaActiva = false;
    
    private double latActual = -20.23126000;
    private double lonActual = -70.15053000;
    private double latDestino = -20.20521000;
    private double lonDestino = -70.13425000;

    public ControladorTelemetria(int idCamion, List<GeoPosition> rutaCalculada) {
        this.idCamion = idCamion;
        this.dao = new TelemetriaDAO();
        this.idGps = this.dao.obtenerIdGpsPorCamion(idCamion); 
        this.rutaRealCalles = rutaCalculada;
        this.random = new Random();
        this.enRuta = false;
        this.combustibleActual = 100.0; 
        this.temperaturaCarga = 3.5; // Ajustado a valor óptimo de carga inicial
        
        if (rutaCalculada != null && !rutaCalculada.isEmpty()) {
            GeoPosition puntoInicio = rutaCalculada.get(0);
            this.latActual = puntoInicio.getLatitude();
            this.lonActual = puntoInicio.getLongitude();

            GeoPosition puntoFinal = rutaCalculada.get(rutaCalculada.size() - 1);
            this.latDestino = puntoFinal.getLatitude();
            this.lonDestino = puntoFinal.getLongitude();
        }
    }

    public void detenerSimulacion() {
        this.enRuta = false;
    }

    @Override
    public void run() {
        if (idGps == -1 || rutaRealCalles == null || rutaRealCalles.isEmpty()) {
            this.enRuta = false;
            return;
        }

        this.enRuta = true;
        int pasoActual = 0;

        while (enRuta && pasoActual < rutaRealCalles.size()) {
            try {
                // 1. Conseguir el nodo de la calle actual
                GeoPosition coordenadaCalle = rutaRealCalles.get(pasoActual);
                
                // Actualización de coordenadas globales para el mapa
                this.latActual = coordenadaCalle.getLatitude();
                this.lonActual = coordenadaCalle.getLongitude();
                
                // El combustible SIEMPRE disminuye un poco en cada nodo (gasto en carretera)
                if (this.combustibleActual > 0.0) {
                    this.combustibleActual -= 0.05 + (random.nextDouble() * 0.05); // Gasto real continuo
                    if (this.combustibleActual < 0) this.combustibleActual = 0.0;
                }
                
                // La temperatura SIEMPRE fluctúa con un pequeño ruido matemático (+/- 0.2°C)
                // Esto hace que si inyectas 8.0°C con el slider, en el próximo segundo varíe a 8.1°C, 7.9°C, etc.
                double ruidoTermico = (random.nextDouble() - 0.5) * 0.4; 
                this.temperaturaCarga = this.temperaturaCarga + ruidoTermico;
                
                // Opcional: Mantener la temperatura en un rango lógico si no ha sido forzada por el slider
                // (Por ejemplo, si baja demasiado o sube demasiado sola, la estabilizamos un poco)
                if (this.temperaturaCarga < -5.0) this.temperaturaCarga = -5.0;
                if (this.temperaturaCarga > 25.0) this.temperaturaCarga = 25.0;

                // =========================================================================
                // EMPAQUETADO EXACTO HACIA LA BASE DE DATOS (REPARADO)
                // =========================================================================
                TelemetriaRuta telemetria = new TelemetriaRuta();
                telemetria.setIdGpsFk(idGps);
                telemetria.setLatitud(this.latActual); 
                telemetria.setLongitud(this.lonActual);
                telemetria.setConsumoCombustible(this.combustibleActual);
                telemetria.setTemperaturaMotor(this.temperaturaCarga);
                
                // Insertar en la Base de Datos de Hirata
                dao.insertarLecturaRuta(telemetria);
                
                // Si la temperatura supera los 5.0°C, se crea un registro de incidente en la tabla de control
                if (this.temperaturaCarga > 5.0) {
                    if (!alertaActiva) {
                        // CASO A: Cruza el límite por primera vez. Se gatilla el registro en MySQL.
                        dao.insertarAlertaTemperatura(idGps, this.temperaturaCarga);
                        System.out.println("🚨 [ALERTA IOT] ¡Cadena de frío rota! Registro único insertado en control_temperatura_carga: " + String.format("%.1f", this.temperaturaCarga) + "°C.");
                        
                        alertaActiva = true; // Se enciende el candado para bloquear las ráfagas siguientes
                    }
                    // Si 'alertaActiva' ya es true, entra aquí pero no hace nada, ignorando los duplicados
                } else {
                    if (alertaActiva) {
                        // CASO B: La temperatura regresó a niveles seguros. Se libera el candado.
                        System.out.println("✅ [IoT-Restablecido] Temperatura normalizada. Sensor rearmado para futuras alertas.");
                        
                        alertaActiva = false; // Permite que un próximo exceso vuelva a generar una inserción
                    }
                }
                
                System.out.println("[Sensor-IoT] Guardando en MySQL -> Nodo " + (pasoActual + 1) + " | Gas: " + String.format("%.1f%%", combustibleActual) + " | Temp: " + String.format("%.1f°C", temperaturaCarga));

                // Avanzar con el salto de nodos veloz optimizado
                pasoActual += 4;
                
                Thread.sleep(3000); 

            } catch (InterruptedException e) {
                System.err.println("Simulación interrumpida en carretera: " + e.getMessage());
                this.enRuta = false;
            }
        }
        
        System.out.println(">>> [IoT] Destino alcanzado de principio a fin. <<<");
        this.enRuta = false;
    }

    public boolean isEnRuta() { return enRuta; }
    public void setPatente (String patente) { this.patente = patente; }
    public double getLatActual() { return this.latActual; }
    public double getLonActual() { return this.lonActual; }
    public double getCombustibleActual() { return this.combustibleActual; }
    public double getTemperaturaCarga() { return this.temperaturaCarga; }
    
    // Los Setters ahora solo inyectan el dato y NO congelan la simulación
    public void setCombustibleActual(double combustibleActual) { this.combustibleActual = combustibleActual; }
    public void setTemperaturaCarga(double temperaturaCarga) { this.temperaturaCarga = temperaturaCarga; }
    
    
    public void iniciarCombustibleSimulacion(double combustible) {
        this.combustibleActual = combustible;
    }

    public void iniciarTemperaturaSimulacion(double temperatura) {
        this.temperaturaCarga = temperatura;
    }
}
