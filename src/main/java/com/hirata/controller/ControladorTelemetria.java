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
    private final List<GeoPosition> rutaRealCalles; // Aquí se guardan las calles de OpenStreetMap
    private boolean enRuta;
    private double combustibleActual;
    private double temperaturaCarga;
    private String patente;
    
    // Coordenadas geográficas reales de Iquique para la simulación
    // Origen: Cavancha (-20.23126, -70.15053) -> Destino: ZOFRI (-20.20521, -70.13425)
    private double latActual = -20.23126000;
    private double lonActual = -70.15053000;
    private double latDestino = -20.20521000;
    private double lonDestino = -70.13425000;

    /**
     * Constructor del emulador de telemetría.
     */
    public ControladorTelemetria(int idCamion, List<GeoPosition> rutaCalculada) {
        this.idCamion = idCamion;
        this.dao = new TelemetriaDAO();
        this.idGps = this.dao.obtenerIdGpsPorCamion(idCamion); // Buscamos el GPS asociado al camión en la base de datos
        this.rutaRealCalles = rutaCalculada;
        this.random = new Random();
        this.enRuta = false;
        this.combustibleActual = 100.0; // Parte con estanque lleno
        this.temperaturaCarga = 2.5;    // Temperatura inicial segura (RF-13)
        
        // Inicializar las coordenadas con el punto de partida exacto
        if (rutaCalculada != null && !rutaCalculada.isEmpty()) {
            // El punto inicial donde aparece el camión
            GeoPosition puntoInicio = rutaCalculada.get(0);
            this.latActual = puntoInicio.getLatitude();
            this.lonActual = puntoInicio.getLongitude();

            // El punto final a donde se dirige
            GeoPosition puntoFinal = rutaCalculada.get(rutaCalculada.size() - 1);
            this.latDestino = puntoFinal.getLatitude();
            this.lonDestino = puntoFinal.getLongitude();
        }
    }

    /**
     * Detiene el hilo de simulación de forma segura.
     */
    public void detenerSimulacion() {
        this.enRuta = false;
    }

    /**
     * Ciclo de vida asíncrono del hilo en segundo plano (Thread).
     */
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
                
                // =========================================================================
                // CRÍTICO: ASIGNAR DIRECTO A LAS VARIABLES DE LA CLASE (SIN EL "double")
                // Si pones "double latActual", estás creando una variable local y el mapa no se entera.
                // =========================================================================
                // ACTUALIZACIÓN DE NUESTROS ATRIBUTOS DINÁMICOS
                this.latActual = coordenadaCalle.getLatitude();
                this.lonActual = coordenadaCalle.getLongitude();
                
                // Simulación de desgaste de combustible y temperatura (RF-11, RF-13)
                combustibleActual -= 0.1 + (random.nextDouble() * 0.05);
                if (combustibleActual < 0) combustibleActual = 0;
                
                if (pasoActual >= 15) {
                    this.temperaturaCarga = 6.4; // Simula alerta térmica para la defensa
                } else {
                    this.temperaturaCarga = 2.5 + (random.nextDouble() * 0.4); 
                }

                // Empaquetar la telemetría para MySQL
                TelemetriaRuta telemetria = new TelemetriaRuta();
                telemetria.setIdGpsFk(idGps);
                telemetria.setLatitud(this.latActual);   // Enviamos el valor global
                telemetria.setLongitud(this.lonActual);
                telemetria.setConsumoCombustible(combustibleActual);
                telemetria.setTemperaturaCelsius(this.temperaturaCarga);

                // Insertar en la Base de Datos de Hirata
                dao.insertarLecturaRuta(telemetria);
                
                System.out.println("[Sensor-IoT] Avanzando por calle. Nodo " + (pasoActual + 1) + " de " + rutaRealCalles.size());

                // Avanzar al siguiente nodo vial
                pasoActual++;
                
                // Esperar 3 segundos para sincronizar con el Timer de la vista
                Thread.sleep(3000); 

            } catch (InterruptedException e) {
                System.err.println("Simulación interrumpida en carretera: " + e.getMessage());
                this.enRuta = false;
            }
        }
        
        System.out.println(">>> [IoT] Destino alcanzado de principio a fin. <<<");
        this.enRuta = false;
    }

    // Getters auxiliares para verificar estados desde las vistas
    public boolean isEnRuta() { return enRuta; }
    public void setPatente (String patente) { this.patente = patente; }
    public double getLatActual() { return this.latActual; }
    public double getLonActual() { return this.lonActual; }
    public double getCombustibleActual() { return this.combustibleActual; }
    public double getTemperaturaCarga() { return this.temperaturaCarga; }
}
