/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.hirata.controller;

import com.hirata.dao.TelemetriaDAO;
import com.hirata.model.Camion;
import com.hirata.model.MySQLConexion;
import com.hirata.model.TelemetriaRuta;
import java.sql.*;
import javax.swing.table.DefaultTableModel;

/**
 *
 * @author wadev
 */
public class ControladorAdmin extends ControladorBase {

    // Método que requiere tu vista IngresoKM para rellenar los JLabels de datos técnicos
    public Camion obtenerDatosCamionAdmin(String patente) {
        return obtenerDatosCamion(patente); // Reutiliza el método de la clase padre
    }

    public boolean registrarKmAdmin(String patente, int km) {
        Camion camion = obtenerDatosCamionAdmin(patente);
        // Verifica que el camion esta activo en el sistema
        if (camion.getEstado() == "0") { return false; }
        return ejecutarRegistroKM(camion.getId(), com.hirata.model.Sesion.idPersonal, camion.getKilometrajeAcumulado(), km);
    }

    public DefaultTableModel listarUsuarios() {
        
        String[] columnas = {"ID", "Estado","Personal (Rut)", "Credencial", "Rol"};
        DefaultTableModel m = new DefaultTableModel(columnas, 0);

        // 2. Query SQL adaptada a tus tablas (ajusta los nombres de las columnas si difieren en tu BD)
        // Usamos un CASE o IF para mostrar de manera legible "Activo" o "Inactivo" en la JTable
        String sql = "SELECT u.id, "
                + "CASE WHEN u.estado = 1 THEN 'Activo' ELSE 'Inactivo' END as estado_txt, "
                + "p.rut, "
                + "u.username, "
                + "r.rol "
                + "FROM usuarios AS u "
                + "INNER JOIN personal AS p ON u.id_personal_fk = p.id "
                + "INNER JOIN roles AS r ON u.id_rol_fk = r.id "
                + "ORDER BY u.id ASC";
                
        try (Connection con = com.hirata.model.MySQLConexion.getConexion();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                m.addRow(new Object[] {rs.getInt("id"), rs.getString("estado_txt"), rs.getString("rut"), rs.getString("username"), rs.getString("rol")});
            }

        } catch (SQLException e) {
            System.err.println("Error crítico al listar usuarios en ControladorAdmin: " + e.getMessage());
            e.printStackTrace();
        }
        return m;
    }

    public DefaultTableModel listarCamiones() {
        
        String[] columnas = {"ID", "Estado", "Conductor", "Patente", "Marca", "Modelo", "Anio", "KM Actual"};
        DefaultTableModel m = new DefaultTableModel(columnas, 0);
        
        String sql = "SELECT c.id, "
                   + "CASE WHEN c.estado = 1 THEN 'Activo' ELSE 'Inactivo' END as estado_txt, "
                   + "COALESCE(p.nombre, 'SIN CONDUCTOR') as conductor_nombre, "
                   + "c.patente, "
                   + "c.marca, "
                   + "c.modelo, "
                   + "c.anio, "
                   + "c.km_actual "
                   + "FROM camiones AS c "
                   + "LEFT JOIN personal AS p ON c.id_personal_fk = p.id "
                   + "ORDER BY u.id ASC";;

        try (Connection con = com.hirata.model.MySQLConexion.getConexion();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            // 4. Recorremos el ResultSet fila por fila
            while (rs.next()) {
                Object[] fila = new Object[8];
                fila[0] = rs.getInt("id");
                fila[1] = rs.getString("estado_txt");
                fila[2] = rs.getString("conductor_nombre");
                fila[3] = rs.getString("patente");
                fila[4] = rs.getString("marca");
                fila[5] = rs.getString("modelo");
                fila[6] = rs.getInt("anio");
                fila[7] = rs.getInt("km_actual");
                
                m.addRow(fila);
            }
        } catch (SQLException e) {
            System.err.println("Error crítico al listar camiones en ControladorAdmin: " + e.getMessage());
            e.printStackTrace();
        }

        return m;
    }

    public boolean reasignarConductor(int idCamion, String rut) {
        return true;
    }

    public boolean actualizarEstadoEntidad(String tabla, int id, boolean nuevoEstado) {
        if (tabla.equalsIgnoreCase("usuarios") && !nuevoEstado && id == com.hirata.model.Sesion.idPersonal) {
            System.err.println("Bloqueo de seguridad: Intento de auto-desactivación del ID: " + id);
            return false;
        }
        
        int estadoNumerico = nuevoEstado ? 1 : 0;

        String sql = "UPDATE " + tabla + " SET estado = ? WHERE id = ?";

        try (Connection con = com.hirata.model.MySQLConexion.getConexion();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setInt(1, estadoNumerico);
            ps.setInt(2, id);

            return ps.executeUpdate() > 0;

        } catch (SQLException e) {
            System.err.println("Error crítico al actualizar estado en la tabla " + tabla + ": " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    public TelemetriaRuta obtenerTelemetriaEnVivo(int idCamion) {
        TelemetriaDAO dao = new TelemetriaDAO();
        return dao.obtenerUltimoPuntoIoT(idCamion);
    }

    // ---------- CONTRATO DE HERENCIA OBLIGATORIO (ControladorBase) ----------
    @Override
    public DefaultTableModel verHistorial(int idFiltro) {
        String[] col = {"ID Registro", "Estado", "Creador (Rut)", "Vehículo (Patente)", "KM Anterior", "KM Ingresado", "Fecha"};
        
        DefaultTableModel model = new DefaultTableModel(col, 0);
        
        String sql = "SELECT rk.id, rk.estado, p.rut, c.patente, rk.kmAnterior, rk.kmIngresado, rk.fecha " +
                "FROM registro_km AS rk " +
                "INNER JOIN personal AS p ON rk.id_personal_fk = p.id " +
                "INNER JOIN camiones AS c ON rk.id_camion_fk = c.id";
        
        try (Connection con = MySQLConexion.getConexion();
             PreparedStatement ps = con.prepareStatement(sql)) {
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    model.addRow(new Object[]{rs.getInt("id"), rs.getInt("estado"), rs.getString("rut"), rs.getString("patente"), rs.getInt("kmAnterior"), rs.getInt("kmIngresado"), rs.getTimestamp("fecha")});
                    
                }
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return model;
    }

    @Override
    public DefaultTableModel verAlertas(int idFiltro) {
        return new DefaultTableModel(new String[]{"Alertas Globales"}, 0);
    }
}
