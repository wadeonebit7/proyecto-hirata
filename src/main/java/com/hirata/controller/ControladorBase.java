/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.hirata.controller;

import com.hirata.model.Camion;
import com.hirata.model.MySQLConexion;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import javax.swing.JOptionPane;
import javax.swing.table.DefaultTableModel;

/**
 *
 * @author wadev
 */
public abstract class ControladorBase {
    
    // Método base para obtener un historial (será sobrescrito)
    public abstract DefaultTableModel verHistorial(int idFiltro);
    // Método base para ver alertas (será sobrescrito)
    public abstract DefaultTableModel verAlertas(int idFiltro);
    
    
    public Camion obtenerDatosCamion(String patente) {
        String sql = "SELECT * FROM camiones WHERE patente = ? AND estado = 1";
        try (Connection con = MySQLConexion.getConexion();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, patente);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                Camion c = new Camion();
                c.setId(rs.getInt("id"));
                c.setEstado(rs.getString("estado"));
                c.setPatente(rs.getString("patente"));
                c.setMarca(rs.getString("marca"));
                c.setModelo(rs.getString("modelo"));
                c.setAnio(rs.getInt("anio"));
                c.setKilometrajeAcumulado(rs.getInt("km_actual"));
                return c;
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return null;
    }
    
    // Lógica común para registrar un KM (usada por ambos)
    protected boolean ejecutarRegistroKM(int idCamion, int idPersonal, int kmAnterior, int kmIngresado) {
        // Verifica que el km ingresado no sea menor o igual al actual kilometraje del camion
        if (kmIngresado <= kmAnterior) { return false; }
        
        Connection con = MySQLConexion.getConexion();
        try {
            // 1. Insertar el registro de historial
            String sqlReg = "INSERT INTO registro_km (id_camion_fk, id_personal_fk, kmAnterior, kmIngresado) VALUES (?, ?, ?, ?)";
            PreparedStatement psReg = con.prepareStatement(sqlReg);
            psReg.setInt(1, idCamion);
            psReg.setInt(2, idPersonal);
            psReg.setInt(3, kmAnterior);
            psReg.setInt(4, kmIngresado);
            psReg.executeUpdate();

            // 2. ACTUALIZAR EL KM_ACTUAL DEL CAMIÓN
            String sqlUpdateCamion = "UPDATE camiones SET km_actual = ? WHERE id = ?";
            PreparedStatement psUpC = con.prepareStatement(sqlUpdateCamion);
            psUpC.setInt(1, kmIngresado);
            psUpC.setInt(2, idCamion);
            psUpC.executeUpdate();

            // 3. Calcular acumulado desde la última alerta
            String sqlSuma = "SELECT SUM(kmIngresado - kmAnterior) as acumulado " +
                             "FROM registro_km " +
                             "WHERE id_camion_fk = ? AND fecha > " +
                             "COALESCE((SELECT MAX(fecha) FROM registro_alertas WHERE id_camion_fk = ?), '1900-01-01')";

            PreparedStatement psSuma = con.prepareStatement(sqlSuma);
            psSuma.setInt(1, idCamion);
            psSuma.setInt(2, idCamion);
            ResultSet rsSuma = psSuma.executeQuery();

            if (rsSuma.next()) {
                int totalAcumulado = rsSuma.getInt("acumulado");
                if (totalAcumulado >= 5000) {
                    generarYMostrarAlerta(idCamion, totalAcumulado);
                }
            }
            return true; // Si llegó aquí, todo se guardó correctamente
        } catch (SQLException e) {
            System.err.println("Error en ejecución base: " + e.getMessage());
            return false;
        }
    }
    
    private void generarYMostrarAlerta(int idCamion, int km) {
        Connection con = MySQLConexion.getConexion();
        try {
            String msg = "Mantenimiento Preventivo: El camión ha acumulado " + km + " km desde su último control.";
            String sql = "INSERT INTO registro_alertas (id_camion_fk, mensaje, leido) VALUES (?, ?, 0)";
            
            PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setInt(1, idCamion);
            ps.setString(2, msg);
            ps.executeUpdate();
            
            ResultSet rs = ps.getGeneratedKeys();
            if (rs.next()) {
                int idAlerta = rs.getInt(1);
                
                // Mostrar cuadro de diálogo al usuario
                JOptionPane.showMessageDialog(null, msg, "ALERTA DE MANTENIMIENTO", JOptionPane.WARNING_MESSAGE);
                
                // Al cerrar el mensaje, marcamos como leída (leido = 1)
                String sqlUpdate = "UPDATE registro_alertas SET leido = 1 WHERE id = ?";
                PreparedStatement psUp = con.prepareStatement(sqlUpdate);
                psUp.setInt(1, idAlerta);
                psUp.executeUpdate();
            }
        } catch (SQLException e) { e.printStackTrace(); }
    }
}
