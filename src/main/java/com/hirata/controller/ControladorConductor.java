/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.hirata.controller;

import com.hirata.dao.TelemetriaDAO;
import com.hirata.model.Camion;
import com.hirata.model.MySQLConexion;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import javax.swing.table.DefaultTableModel;
import org.jxmapviewer.viewer.GeoPosition;

/**
 *
 * @author wadev
 */
public class ControladorConductor extends ControladorBase {
    
    // Método que requiere tu vista IngresoKM para rellenar los JLabels de datos técnicos
    public Camion obtenerDatosCamionAdmin(String patente) {
        return obtenerDatosCamion(patente); // Reutiliza el método de la clase padre
    }

    // RF-10 / RF-11: Obtener patentes de camiones asignados al conductor logueado
    public List<String> obtenerCamionesAsignados(int idPersonal) {
        List<String> lista = new ArrayList<>();
        // Query de ejemplo adaptada a tu regla de negocio
        String sql = "SELECT patente FROM camiones WHERE estado = 1 AND id_personal_fk = ?"; 
        try (Connection con = MySQLConexion.getConexion();
             PreparedStatement ps = con.prepareStatement(sql)) {
            
            ps.setInt(1, idPersonal);
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lista.add(rs.getString("patente"));
                }
            }
            
        } catch (SQLException e) { e.printStackTrace(); }
        
        return lista;
    }

    public boolean registrarKilometraje(String patente, int km) {
        Camion camion = obtenerDatosCamionAdmin(patente);
        
        if (camion == null || camion.getEstado() == null || "0".equals(camion.getEstado())) { return false; }
        // Invoca a la lógica de transacciones común heredada de ControladorBase
        // Nota: Ajusta los parámetros fijos (ID camión, ID personal) según tu captura de sesión
        return ejecutarRegistroKM(camion.getId(), com.hirata.model.Sesion.idPersonal, camion.getKilometrajeAcumulado(), km);
    }
    
    public GeoPosition obtenerUltimaPosicionCamion(String patente) {
        // Instanciamos el DAO encargado de la telemetría IoT
        TelemetriaDAO tDao = new TelemetriaDAO();

        // Llamamos a un método del DAO que busque las coordenadas en la BD
        return tDao.obtenerUltimaCoordenadaPorPatente(patente);
    }
    
    // Devuelve el ID del GPS asociado al camión
    public int obtenerIdGpsPorCamion(int idCamion) {
        String sql = "SELECT id FROM dispositivos_gps WHERE id_camion_fk = ? AND estado = 1";
        try (java.sql.Connection con = MySQLConexion.getConexion();
             java.sql.PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, idCamion);
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt("id");
            }
        } catch (java.sql.SQLException e) {
            System.err.println("Error al obtener ID del hardware GPS: " + e.getMessage());
        }
        return -1; // Retorna -1 si no tiene hardware vinculado
    }
    
    // Recupera el último nivel de combustible registrado en la base de datos para este GPS
    public double obtenerUltimoCombustible(int idGps) {
        String sql = "SELECT consumo_combustible FROM telemetria_ruta WHERE id_gps_fk = ? ORDER BY id DESC LIMIT 1";
        try (Connection con = MySQLConexion.getConexion();
             PreparedStatement ps = con.prepareStatement(sql)) {
            
            ps.setInt(1, idGps);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getDouble("consumo_combustible");
                }
            }
        } catch (SQLException e) {
            System.err.println("Error en ControladorConductor -> obtenerUltimoCombustible: " + e.getMessage());
        }
        return 100.0; // Si el camión es nuevo y no tiene registros, inicializa con estanque lleno (100%)
    }

    // ---------- CONTRATO DE HERENCIA OBLIGATORIO (ControladorBase) ----------
    @Override
    public DefaultTableModel verHistorial(int idPersonal) {
        String[] col = {"ID Registro", "Creador (Rut)", "Vehículo (Patente)", "KM Anterior", "KM Ingresado", "Fecha"};
        
        DefaultTableModel model = new DefaultTableModel(col, 0);
        
        String sql = "SELECT rk.id, p.rut, c.patente, rk.kmAnterior, rk.kmIngresado, rk.fecha " +
                "FROM registro_km AS rk " +
                "INNER JOIN personal AS p ON rk.id_personal_fk = p.id " +
                "INNER JOIN camiones AS c ON rk.id_camion_fk = c.id " +
                "WHERE rk.id_personal_fk = ?";
        
        try (Connection con = MySQLConexion.getConexion();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, idPersonal);
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    model.addRow(new Object[]{rs.getInt("id"), rs.getString("rut"), rs.getString("patente"), rs.getInt("kmAnterior"), rs.getInt("kmIngresado"), rs.getTimestamp("fecha")});
                    
                }
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return model;
    }

    @Override
    public DefaultTableModel verAlertas(int idPersonal) {
        String[] col = {"ID", "Mensaje de Alerta", "Estado"};
        DefaultTableModel model = new DefaultTableModel(col, 0);
        return model;
    }
}
