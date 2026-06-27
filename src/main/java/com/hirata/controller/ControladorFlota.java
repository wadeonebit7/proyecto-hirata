/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.hirata.controller;

import com.hirata.dao.CamionDAO; // Tu DAO de camiones
import com.hirata.model.MySQLConexion;
import javax.swing.table.DefaultTableModel;
import java.sql.*;

/**
 *
 * @author wadev
 */
public class ControladorFlota extends ControladorBase {
    
    private final CamionDAO camionDAO = new CamionDAO();

    // Implementación obligatoria de ControladorBase
    @Override
    public DefaultTableModel verHistorial(int idCamion) {
        String[] columnas = {"ID", "Patente", "Modelo", "KM Acumulado", "Estado", "Alerta Preventiva"};
        DefaultTableModel modelo = new DefaultTableModel(columnas, 0);
        
        // Aquí puedes usar la lógica que lee de tu DAO o tabla camiones
        // Regla Etapa 1: Si kilometraje >= 5000 -> "REQUIERE MANTENCIÓN"
        return modelo;
    }
    
    @Override
    public DefaultTableModel verAlertas(int idFiltro) {
        return new DefaultTableModel();
    }

    // =================================================================
    // ETAPA 3: MÉTODOS DE TELEMETRÍA Y LOGÍSTICA DE SENSORES (RF-10 al RF-13)[cite: 4]
    // =================================================================

    // RF-11 / RF-13: Insertar lecturas directas de telemetría en ruta[cite: 4]
    public boolean registrarTelemetriaSensores(int idCamion, double lat, double lon, int velocidad, double temp) {
        String sqlGps = "INSERT INTO historial_gps (id_camion_fk, latitud, longitud, velocidad_kmh) VALUES (?, ?, ?, ?)";
        String sqlTemp = "INSERT INTO control_temperatura (id_camion_fk, temperatura_celsius, limite_critico_superado) VALUES (?, ?, ?)";
        
        // Alerta crítica si la temperatura de la carga supera los 7.5°C (Flujo alterno RF-13)[cite: 4]
        boolean limiteSuperado = (temp > 7.5); 

        try (Connection con = MySQLConexion.getConexion()) {
            con.setAutoCommit(false); // Transacción segura como en la Etapa II

            try (PreparedStatement psG = con.prepareStatement(sqlGps);
                 PreparedStatement psT = con.prepareStatement(sqlTemp)) {
                
                // 1. Guardar GPS (RF-11)[cite: 4]
                psG.setInt(1, idCamion);
                psG.setDouble(2, lat);
                psG.setDouble(3, lon);
                psG.setInt(4, velocidad);
                psG.executeUpdate();

                // 2. Guardar Temperatura de Carga (RF-13)[cite: 4]
                psT.setInt(1, idCamion);
                psT.setDouble(2, temp);
                psT.setBoolean(3, limiteSuperado);
                psT.executeUpdate();

                con.commit(); // Todo o nada
                return true;
            } catch (SQLException e) {
                con.rollback();
                e.printStackTrace();
                return false;
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }
}
