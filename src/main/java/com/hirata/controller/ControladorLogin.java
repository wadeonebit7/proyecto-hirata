/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.hirata.controller;

import com.hirata.model.MySQLConexion;
import com.hirata.model.Hash;
import com.hirata.model.Sesion;
import java.sql.*;
/**
 *
 * @author wadev
 */
public class ControladorLogin {
    public String acceder(String user, String pass) {
        String passHash = Hash.sha256(pass);
        Connection con = MySQLConexion.getConexion();
        
        // Relacionamos Usuarios con Personal y Roles según tu MER
        String sql = "SELECT p.id, p.rut, p.nombre, r.rol, u.estado " +
                     "FROM usuarios u " +
                     "JOIN personal p ON u.id_personal_fk = p.id " +
                     "JOIN roles r ON u.id_rol_fk = r.id " +
                     "WHERE u.username = ? AND u.password = ?";
        
        try {
            PreparedStatement ps = con.prepareStatement(sql);
            ps.setString(1, user);
            ps.setString(2, passHash);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                if (!rs.getBoolean("estado")) return "INACTIVO";
                
                // Guardamos los IDs en la sesión para trazabilidad
                Sesion.idPersonal = rs.getInt("id");
                Sesion.rut = rs.getString("rut");
                Sesion.nombre = rs.getString("nombre");
                Sesion.rol = rs.getString("rol");
                
                return Sesion.rol;
            }
        } catch (SQLException e) {
            System.err.println("Error Login: " + e.getMessage());
        }
        return "ERROR";
    }
}
