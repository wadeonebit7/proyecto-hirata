/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.hirata.dao;

import com.hirata.model.Camion;
import com.hirata.model.MySQLConexion;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author wadev
 */
public class CamionDAO {

    // RF-01 / RF-02: Registrar un nuevo camión en la flota
    public boolean insertar(Camion camion) {
        String sql = "INSERT INTO camiones (estado, id_personal_fk, patente, marca, modelo, anio, km_actual) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection con = MySQLConexion.getConexion();
             PreparedStatement ps = con.prepareStatement(sql)) {
            
            ps.setString(1, camion.getEstado());
            ps.setInt(2, camion.getIdPersonalFk());
            ps.setString(3, camion.getPatente());
            ps.setString(4, camion.getMarca());
            ps.setString(5, camion.getModelo());
            ps.setInt(6, camion.getAnio());
            ps.setInt(7, camion.getKilometrajeAcumulado());
            
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    // Listar actualizando de forma exacta las columnas de la BD
    public List<Camion> listarTodos() {
        List<Camion> lista = new ArrayList<>();
        String sql = "SELECT id, estado, id_personal_fk, patente, marca, modelo, anio, km_actual FROM camiones";
        try (Connection con = MySQLConexion.getConexion();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            
            while (rs.next()) {
                // REPARADO: Llama al nuevo constructor con los campos exactos de tu imagen
                Camion c = new Camion(
                    rs.getInt("id"),
                    rs.getString("estado"),
                    rs.getInt("id_personal_fk"),
                    rs.getString("patente"),
                    rs.getString("marca"),
                    rs.getString("modelo"),
                    rs.getInt("anio"),
                    rs.getInt("km_actual")
                );
                lista.add(c);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return lista;
    }

    public boolean actualizarKilometraje(int idCamion, int kilometrosRecorridos) {
        // Actualiza incrementando la columna km_actual real
        String sqlUpdate = "UPDATE camiones SET km_actual = km_actual + ? WHERE id = ?";
        try (Connection con = MySQLConexion.getConexion()) {
            con.setAutoCommit(false);
            try (PreparedStatement psUp = con.prepareStatement(sqlUpdate)) {
                psUp.setInt(1, kilometrosRecorridos);
                psUp.setInt(2, idCamion);
                psUp.executeUpdate();
            }
            con.commit();
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }
}
