/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.hirata.dao;

import com.hirata.model.MySQLConexion;
import com.hirata.model.EquipoOficina;
import java.sql.*;

/**
 *
 * @author wadev
 */
public class EquipoDAO {
    // Busca un equipo por su SN (Número de serie natural)
    public EquipoOficina buscarPorSN(String sn) {
        String sql = "SELECT * FROM equipos WHERE sn = ?";
        try (Connection con = MySQLConexion.getConexion();
             PreparedStatement ps = con.prepareStatement(sql)) {
            
            ps.setString(1, sn);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    EquipoOficina eq = new EquipoOficina();
                    eq.setId(rs.getInt("id"));
                    eq.setSn(rs.getString("sn"));
                    eq.setEstado(rs.getInt("estado"));
                    eq.setMarca(rs.getString("marca"));
                    eq.setModelo(rs.getString("modelo"));
                    eq.setFechaRegistro(rs.getDate("fecha_registro"));
                    eq.setIdPersonal(rs.getInt("id_personal"));
                    eq.setIdTipoEquipo(rs.getInt("id_tipo_equipo"));
                    return eq;
                }
            }
        } catch (SQLException e) {
            System.err.println("Error EquipoDAO.buscarPorSN: " + e.getMessage());
        }
        return null; // Si no existe, lanza la excepción en la vista
    }
}
