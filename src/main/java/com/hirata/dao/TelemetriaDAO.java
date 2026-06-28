/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.hirata.dao;

import com.hirata.model.MySQLConexion;
import com.hirata.model.TelemetriaRuta;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.jxmapviewer.viewer.GeoPosition;

/**
 *
 * @author wadev
 */
public class TelemetriaDAO {
    /**
     * RF-11: Guarda las lecturas de posición y estado de motor enviadas por el sensor GPS.
     */
    public boolean insertarLecturaRuta(TelemetriaRuta t) {
        String sql = "INSERT INTO telemetria_ruta (id_gps_fk, latitud, longitud, consumo_combustible, temperatura_motor) "
                   + "VALUES (?, ?, ?, ?, ?)";
        
        try (Connection con = MySQLConexion.getConexion();
             PreparedStatement ps = con.prepareStatement(sql)) {
            
            ps.setInt(1, t.getIdGpsFk());
            ps.setDouble(2, t.getLatitud());
            ps.setDouble(3, t.getLongitud());
            ps.setDouble(4, t.getConsumoCombustible());
            ps.setInt(5, t.getTemperaturaMotor());
            
            return ps.executeUpdate() > 0;
            
        } catch (SQLException e) {
            System.err.println("Error en TelemetriaDAO -> insertarLecturaRuta: " + e.getMessage());
            return false;
        }
    }

    /**
     * RF-13: Registra las condiciones térmicas de la carga frigorífica en la base de datos.
     */
    public boolean insertarLecturaTemperatura(int idGps, double temperaturaCelsius) {
        String sql = "INSERT INTO control_temperatura_carga (id_gps_fk, temperatura_celsius) VALUES (?, ?)";
        
        try (Connection con = MySQLConexion.getConexion();
             PreparedStatement ps = con.prepareStatement(sql)) {
            
            ps.setInt(1, idGps);
            ps.setDouble(2, temperaturaCelsius);
            
            return ps.executeUpdate() > 0;
            
        } catch (SQLException e) {
            System.err.println("Error en TelemetriaDAO -> insertarLecturaTemperatura: " + e.getMessage());
            return false;
        }
    }

    /**
     * RF-13 (Excepción): Registra de forma automática fallas críticas de cadena de frío en la bitácora de auditoría.
     */
    public void registrarAlertaCadenaFrio(int idCamion, double tempErronea) {
        String sql = "INSERT INTO registro_alertas (id_camion_fk, tipo_alerta, descripcion, fecha) "
                   + "VALUES (?, 'CADENA_FRIO', ?, CURRENT_TIMESTAMP)";
        
        try (Connection con = MySQLConexion.getConexion();
             PreparedStatement ps = con.prepareStatement(sql)) {
            
            ps.setInt(1, idCamion);
            ps.setString(2, "FALLA CRÍTICA DE TELEMETRÍA: Contenedor refrigerado superó el límite seguro alcanzando los " + tempErronea + "°C.");
            ps.executeUpdate();
            
        } catch (SQLException e) {
            System.err.println("Error en TelemetriaDAO -> registrarAlertaCadenaFrio: " + e.getMessage());
        }
    }

    /**
     * RF-10 / RF-11: Recupera el último punto geográfico reportado por cada GPS activo en el sistema.
     * Es la función clave que utilizará el temporizador del mapa para renderizar la flota en Iquique.
     */
    public List<TelemetriaRuta> listarUltimasPosicionesFlota() {
        List<TelemetriaRuta> lista = new ArrayList<>();
        
        // Query optimizada: Agregamos el cálculo de segundos de antigüedad individual directamente en MySQL
    String sql = "SELECT tr.*, c.patente, c.marca, c.modelo, "
               + "       TIMESTAMPDIFF(SECOND, tr.fecha_hora, NOW()) AS segundos_atras, " // <-- Clave para independizar camiones
               + "       (SELECT tc.temperatura_celsius "
               + "        FROM control_temperatura_carga tc "
               + "        WHERE tc.id_gps_fk = tr.id_gps_fk "
               + "        ORDER BY tc.id DESC LIMIT 1) AS ultima_temp "
               + "FROM telemetria_ruta tr "
               + "INNER JOIN camiones AS c ON c.id = (SELECT id_camion_fk FROM dispositivos_gps WHERE id = tr.id_gps_fk) "
               + "INNER JOIN ( "
               + "    SELECT id_gps_fk, MAX(id) AS max_id "
               + "    FROM telemetria_ruta "
               + "    GROUP BY id_gps_fk "
               + ") sub ON tr.id = sub.max_id "
               + "INNER JOIN dispositivos_gps g ON tr.id_gps_fk = g.id "
               + "WHERE g.estado = 1";
        
        try (Connection con = MySQLConexion.getConexion();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            
            while (rs.next()) {
                TelemetriaRuta t = new TelemetriaRuta();
                t.setId(rs.getInt("id"));
                t.setIdGpsFk(rs.getInt("id_gps_fk"));
                t.setPatente(rs.getString("patente"));
                // Mapeamos los nuevos campos del camión al DTO
                t.setMarcaCamion(rs.getString("marca"));
                t.setModeloCamion(rs.getString("modelo"));
                t.setFechaHora(rs.getTimestamp("fecha_hora"));
                t.setLatitud(rs.getDouble("latitud"));
                t.setLongitud(rs.getDouble("longitud"));
                t.setConsumoCombustible(rs.getDouble("consumo_combustible"));
                t.setTemperaturaMotor(rs.getInt("temperatura_motor"));
                t.setTemperaturaCelsius(rs.getDouble("ultima_temp")); // Cadena de frío
                
                // =========================================================================
                // Asignación semántica de la antigüedad del reporte
                // =========================================================================
                t.setSegundosAtras(rs.getInt("segundos_atras"));
                
                lista.add(t);
            }
            
        } catch (SQLException e) {
            System.err.println("Error en TelemetriaDAO -> listarUltimasPosicionesFlota: " + e.getMessage());
        }
        
        return lista;
    }

    /**
     * Auxiliar de Selección: Busca el ID interno del dispositivo GPS asignado a un camión específico.
     */
    public int obtenerIdGpsPorCamion(int idCamion) {
        String sql = "SELECT id FROM dispositivos_gps WHERE id_camion_fk = ? AND estado = 1 LIMIT 1";
        try (Connection con = MySQLConexion.getConexion();
             PreparedStatement ps = con.prepareStatement(sql)) {
            
            ps.setInt(1, idCamion);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getInt("id");
            }
        } catch (SQLException e) {
            System.err.println("Error en TelemetriaDAO -> obtenerIdGpsPorCamion: " + e.getMessage());
        }
        return -1; // Retorna -1 si el camión no tiene un hardware GPS configurado
    }
    
    public List<GeoPosition> obtenerHistorialRutaCamion(int idGps) {
        List<GeoPosition> historial = new ArrayList<>();
        // Modifica los nombres de tus tablas y columnas según tu base de datos relacional MySQL
        String sql = "SELECT latitud, longitud FROM telemetria_ruta WHERE id_gps_fk = ? ORDER BY fecha_hora ASC";

        try (Connection con = MySQLConexion.getConexion(); // Reemplaza por tu método de conexión
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setInt(1, idGps);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    double lat = rs.getDouble("latitud");
                    double lon = rs.getDouble("longitud");
                    historial.add(new GeoPosition(lat, lon));
                }
            }
        } catch (Exception e) {
            System.err.println("Error al recuperar historial de rutas para el RF-10: " + e.getMessage());
        }
        return historial;
    }
    
    public GeoPosition obtenerUltimaCoordenadaPorPatente(String patente) {
        String sql = "SELECT t.latitud, t.longitud FROM telemetria_ruta t " +
                     "JOIN dispositivos_gps g ON t.id_gps_fk = g.id " +
                     "JOIN camiones c ON g.id_camion_fk = c.id " +
                     "WHERE c.patente = ? " +
                     "ORDER BY t.id DESC LIMIT 1"; // O usa tu columna de marca de tiempo (timestamp)
        
        try (java.sql.Connection con = MySQLConexion.getConexion();
             java.sql.PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setString(1, patente);
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    double lat = rs.getDouble("latitud");
                    double lon = rs.getDouble("longitud");

                    // Si las coordenadas son válidas y no son 0, devolvemos la posición
                    if (lat != 0 && lon != 0) {
                        return new org.jxmapviewer.viewer.GeoPosition(lat, lon);
                    }
                }
            }
        } catch (java.sql.SQLException e) {
            System.err.println("Error al recuperar la última posición GPS de la flota: " + e.getMessage());
        }

        // Si no hay viajes previos registrados en MySQL para este camión, retorna null
        return null; 
    }
    
    public TelemetriaRuta obtenerUltimoPuntoIoT(int idCamion) {
        // Consulta exacta uniendo tu tabla con dispositivos_gps y camiones
        String sql = "SELECT t.latitud, t.longitud, t.consumo_combustible, t.temperatura_celsius " +
                     "FROM telemetria_ruta t " +
                     "JOIN dispositivos_gps g ON t.id_gps_fk = g.id " +
                     "WHERE g.id_camion_fk = ? " +
                     "ORDER BY t.id DESC LIMIT 1"; // Ajusta 'id' por el nombre de tu llave primaria en telemetria_ruta

        try (java.sql.Connection con = MySQLConexion.getConexion();
             java.sql.PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setInt(1, idCamion);
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    com.hirata.model.TelemetriaRuta t = new TelemetriaRuta();
                    t.setLatitud(rs.getDouble("latitud"));
                    t.setLongitud(rs.getDouble("longitud"));
                    t.setConsumoCombustible(rs.getDouble("consumo_combustible"));
                    t.setTemperaturaCelsius(rs.getDouble("temperatura_celsius"));
                    return t;
                }
            }
        } catch (java.sql.SQLException e) {
            System.err.println("Error en el monitoreo administrativo IoT: " + e.getMessage());
        }
        return null; // Si el camión no ha iniciado ningún viaje o no tiene registros
    }
}

