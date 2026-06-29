/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.hirata.dao;

import com.hirata.model.MySQLConexion;
import com.hirata.model.ResumenViaje;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author wadev
 */
public class InformesDAO {

    /**
     * RF-12: Obtiene el historial completo de viajes para analizar el rendimiento general.
     */
    public List<ResumenViaje> obtenerHistorialRendimiento() {
        List<ResumenViaje> lista = new ArrayList<>();
        String sql = "SELECT patente, origen, destino, combustible_gastado, alertas_termicas, fecha_viaje "
                   + "FROM registro_viajes ORDER BY fecha_viaje DESC";
        
        try (Connection con = MySQLConexion.getConexion();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            
            while (rs.next()) {
                ResumenViaje v = new ResumenViaje();
                v.setPatente(rs.getString("patente"));
                v.setOrigen(rs.getString("origen"));
                v.setDestino(rs.getString("destino"));
                v.setCombustibleGastado(rs.getDouble("combustible_gastado"));
                v.setAlertasTermicas(rs.getInt("alertas_termicas"));
                v.setFechaViaje(rs.getTimestamp("fecha_viaje"));
                lista.add(v);
            }
        } catch (SQLException e) {
            System.err.println("Error al obtener historial de rendimiento: " + e.getMessage());
        }
        return lista;
    }

    /**
     * RF-12: Analítica de datos para agrupar y determinar las rutas más transitadas.
     */
    public List<ResumenViaje> obtenerRutasMasFrecuentes() {
        List<ResumenViaje> lista = new ArrayList<>();
        String sql = "SELECT origen, destino, COUNT(*) as frecuencia "
                   + "FROM registro_viajes "
                   + "GROUP BY origen, destino "
                   + "ORDER BY frecuencia DESC LIMIT 5";
        
        try (Connection con = MySQLConexion.getConexion();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            
            while (rs.next()) {
                ResumenViaje v = new ResumenViaje();
                v.setOrigen(rs.getString("origen"));
                v.setDestino(rs.getString("destino"));
                v.setFrecuencia(rs.getInt("frecuencia"));
                lista.add(v);
            }
        } catch (SQLException e) {
            System.err.println("Error al obtener rutas frecuentes: " + e.getMessage());
        }
        return lista;
    }

    /**
     * KPI Gerencial: Sumatoria de todas las rupturas de cadena de frío para justificar mantenimiento de hardware de refrigeración.
     */
    public int obtenerTotalAlertasTermicas() {
        int total = 0;
        String sql = "SELECT SUM(alertas_termicas) AS total_alertas FROM registro_viajes";
        
        try (Connection con = MySQLConexion.getConexion();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            
            if (rs.next()) {
                total = rs.getInt("total_alertas");
            }
        } catch (SQLException e) {
            System.err.println("Error al contabilizar alertas térmicas: " + e.getMessage());
        }
        return total;
    }
}
