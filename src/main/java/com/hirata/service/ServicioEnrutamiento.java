/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.hirata.service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;
import org.jxmapviewer.viewer.GeoPosition;

/**
 *
 * @author wadev
 */
public class ServicioEnrutamiento {
    /**
     * Calcula la ruta real por calles utilizando el servidor público de OSRM.
     */
    public static List<GeoPosition> calcularRutaCalles(GeoPosition origen, GeoPosition destino) {
        List<GeoPosition> puntosRuta = new ArrayList<>();
        try {
            // Construir la URL de petición al motor de enrutamiento OSRM (Perfil de conducción 'driving')
            String urlStr = String.format(java.util.Locale.US,
                "https://router.project-osrm.org/route/v1/driving/%f,%f;%f,%f?overview=full&geometries=geojson",
                origen.getLongitude(), origen.getLatitude(), destino.getLongitude(), destino.getLatitude());

            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "JXMapViewer2-TransporteHirata-v1.0");

            if (conn.getResponseCode() != 200) {
                System.err.println("Error de conexión con el servidor de rutas.");
                return puntosRuta;
            }

            // Leer la respuesta JSON del servidor
            BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String linea;
            while ((linea = br.readLine()) != null) {
                sb.append(linea);
            }
            br.close();

            // Parsear el camino geométrico devuelto por OSRM
            JSONObject jsonResponse = new JSONObject(sb.toString());
            JSONArray routes = jsonResponse.getJSONArray("routes");
            if (routes.length() > 0) {
                JSONObject geometry = routes.getJSONObject(0).getJSONObject("geometry");
                JSONArray coordinates = geometry.getJSONArray("coordinates");

                // Convertir cada coordenada vial calculada en un GeoPosition para el mapa
                for (int i = 0; i < coordinates.length(); i++) {
                    JSONArray coord = coordinates.getJSONArray(i);
                    double lon = coord.getDouble(0);
                    double lat = coord.getDouble(1);
                    puntosRuta.add(new GeoPosition(lat, lon));
                }
            }
        } catch (Exception e) {
            System.err.println("Error al calcular ruta dinámica: " + e.getMessage());
        }
        return puntosRuta;
    }
}
