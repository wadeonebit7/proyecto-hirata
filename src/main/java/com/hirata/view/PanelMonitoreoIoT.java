/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/GUIForms/JFrame.java to edit this template
 */
package com.hirata.view;

import com.hirata.controller.ControladorAdmin;
import com.hirata.dao.TelemetriaDAO;
import com.hirata.model.TelemetriaRuta;
import java.awt.BorderLayout;
import java.awt.Color;
import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.swing.JOptionPane;
import javax.swing.Timer;
import org.jxmapviewer.JXMapViewer;
import org.jxmapviewer.OSMTileFactoryInfo;
import org.jxmapviewer.input.PanMouseInputListener;
import org.jxmapviewer.input.ZoomMouseWheelListenerCenter;
import org.jxmapviewer.painter.Painter;
import org.jxmapviewer.viewer.DefaultTileFactory;
import org.jxmapviewer.viewer.DefaultWaypoint;
import org.jxmapviewer.viewer.GeoPosition;
import org.jxmapviewer.viewer.TileFactoryInfo;
import org.jxmapviewer.viewer.Waypoint;
import org.jxmapviewer.viewer.WaypointPainter;

/**
 *
 * @author wadev
 */
public class PanelMonitoreoIoT extends javax.swing.JFrame {
    
    private static final java.util.logging.Logger logger = java.util.logging.Logger.getLogger(PanelMonitoreoIoT.class.getName());

    private javax.swing.Timer timerMonitoreo;
    private int idCamionSeleccionadoAdmin = -1; // Cambiará según el camión que elija monitorear el admin
    private CamionWaypoint waypointCamionMonitoreo = null;
    // Base operativa de Hirata para enfocar si no hay viaje activo
    private final GeoPosition origenBase = new GeoPosition(-20.23950916, -70.14480829);
    
    private JXMapViewer mapaViewer;
    private final TelemetriaDAO telemetriaDAO;
    private Timer relojMonitoreo;
    private int idGpsSeleccionado = -1;
    
    /**
     * Creates new form PanelMonitoreoIoT
     */
    public PanelMonitoreoIoT() {
        initComponents();
        this.setLocationRelativeTo(null);
        this.telemetriaDAO = new TelemetriaDAO();
        inicializarMapaNativo();
        configurarTemporizadorLectura();
    }
    
    private void inicializarMapaNativo() {
        
        mapaViewer = new JXMapViewer();

        // 1. REPARADO: Usamos la clase nativa de OpenStreetMap que calcula las URLs perfectas
        // Solo modificamos el constructor para forzar que sea HTTPS
        TileFactoryInfo info = new OSMTileFactoryInfo("OpenStreetMap", "https://tile.openstreetmap.org");
        
        // Configurar la propiedad indispensable para que el servidor de OpenStreetMap no rechace a Java
        System.setProperty("http.agent", "JXMapViewer2-TransporteHirata-v1.0");

        DefaultTileFactory tileFactory = new DefaultTileFactory(info);
        mapaViewer.setTileFactory(tileFactory);

        // 2. Centrar en Iquique (RF-10)
        // En la escala nativa de JXMapViewer2, los números van al revés: 0 es el mapa completo del mundo y 15 es muy cerca.
        // El nivel 3 o 4 es el zoom perfecto para ver el radio urbano de la ciudad.
        GeoPosition posIquique = new GeoPosition(-20.2496396, -70.1284423);
        mapaViewer.setZoom(6); 
        mapaViewer.setAddressLocation(posIquique);

        // 3. Controles de interacción de arrastrar nativo
        PanMouseInputListener pamil = new PanMouseInputListener(mapaViewer);
        mapaViewer.addMouseListener(pamil);
        mapaViewer.addMouseMotionListener(pamil);
        
        mapaViewer.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getButton() == java.awt.event.MouseEvent.BUTTON1) { // Click izquierdo
                    // Convertir el click del mouse a coordenadas de pixeles del mapa mundial
                    java.awt.Point clickPoint = e.getPoint();
                    
                    // Buscar en nuestra lista de camiones activos si alguno está en ese píxel
                    for (org.jxmapviewer.viewer.Waypoint wp : listaWaypoints) {
                        if (wp instanceof CamionWaypoint) {
                            CamionWaypoint camion = (CamionWaypoint) wp;
                            
                            // Obtener posición en píxeles del camión en la pantalla actual
                            java.awt.geom.Point2D camionPoint = mapaViewer.getTileFactory().geoToPixel(camion.getPosition(), mapaViewer.getZoom());
                            
                            // Traducir a coordenadas locales del panel visual
                            java.awt.Rectangle rectMapa = mapaViewer.getViewportBounds();
                            int localX = (int) camionPoint.getX() - rectMapa.x;
                            int localY = (int) camionPoint.getY() - rectMapa.y;
                            
                            // Dentro del mouseClicked, cuando detecta el click en el radio de tolerancia:
                            if (clickPoint.distance(localX, localY) <= 15) {
                                // Guardamos el ID seleccionado de forma global
                                idGpsSeleccionado = camion.getIdGps();

                                // Inyectamos también en los campos fijos por si acaso
                                txtGpsVinculado.setText(String.valueOf(camion.getIdGps()));
                                break;
                            } else {
                                // Si hace click en el vacío del mapa, se deselecciona el camión y se oculta el panel
                                idGpsSeleccionado = -1;
                            }
                        }
                    }
                }
            }
        });
        
        // Candado de Seguridad nativo para la rueda del mouse sin desbordar índices
        mapaViewer.addMouseWheelListener(new java.awt.event.MouseWheelListener() {
            @Override
            public void mouseWheelMoved(java.awt.event.MouseWheelEvent e) {
                int zoomActual = mapaViewer.getZoom();
                int rotacion = e.getWheelRotation(); 
                int siguienteZoom = zoomActual + rotacion;
                
                // Limitar el zoom entre nivel 1 (Muy cerca) y nivel 6 (Vista regional de Tarapacá)
                if (siguienteZoom >= 0 && siguienteZoom <= 15) {
                    mapaViewer.setZoom(siguienteZoom);
                }
            }
        });

        // 4. Forzar tamaño preferido del lienzo para evitar que Swing lo colapse
        mapaViewer.setPreferredSize(new java.awt.Dimension(800, 600)); 

        jfxContainer.setLayout(new BorderLayout());
        jfxContainer.add(mapaViewer, BorderLayout.CENTER);
        
        this.pack(); 
        
        jfxContainer.revalidate();
        jfxContainer.repaint();
    }

    
    
    
    
    
    
    
    
    private void configurarTemporizadorLectura() {
        // Temporizador de 3 segundos para el Personal de Logística (PC 1) 
        relojMonitoreo = new javax.swing.Timer(3000, e -> {
            List<TelemetriaRuta> flotaActiva = telemetriaDAO.listarUltimasPosicionesFlota();
            
            // 1. Limpiar y rellenar los marcadores de la flota activa en el mapa
            listaWaypoints.clear();
            for (TelemetriaRuta t : flotaActiva) {
                listaWaypoints.add(new CamionWaypoint(
                        t.getIdGpsFk(), 
                        t.getPatente(), 
                        t.getLatitud(), 
                        t.getLongitud(), 
                        t.getConsumoCombustible(), 
                        t.getTemperaturaCelsius()
                ));
            }
            
            // 2. Pintor de los iconos de los camiones (Mantenemos tu lógica previa de figuras nativas)
            WaypointPainter<org.jxmapviewer.viewer.Waypoint> waypointPainter = new WaypointPainter<>();
            waypointPainter.setRenderer(new org.jxmapviewer.viewer.WaypointRenderer<org.jxmapviewer.viewer.Waypoint>() {
                @Override
                public void paintWaypoint(java.awt.Graphics2D g, JXMapViewer map, org.jxmapviewer.viewer.Waypoint wp) {
                    if (wp instanceof CamionWaypoint) {
                        CamionWaypoint c = (CamionWaypoint) wp;
                        java.awt.geom.Point2D p = map.getTileFactory().geoToPixel(c.getPosition(), map.getZoom());
                        int x = (int) p.getX();
                        int y = (int) p.getY();

                        g.setColor(c.getColorEstado());
                        g.fillRect(x - 12, y - 6, 22, 11); // Cuerpo
                        g.fillRect(x + 10, y - 3, 6, 8);   // Cabina
                        g.setColor(Color.BLACK);
                        g.fillOval(x - 9, y + 5, 4, 4);    // Ruedas
                        g.fillOval(x + 2, y + 5, 4, 4);
                        g.fillOval(x + 10, y + 5, 4, 4);
                        
                        g.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 10));
                        g.drawString("S/N: " + c.getIdGps(), x - 15, y - 10);

                        // Renderizar el panel flotante si el camión es el seleccionado
                        if (c.getIdGps() == idGpsSeleccionado) {
                            int anchoPanel = 150; int altoPanel = 75;
                            int panelX = x - (anchoPanel / 2); int panelY = y - altoPanel - 20;

                            g.setColor(new Color(255, 255, 255, 230));
                            g.fillRoundRect(panelX, panelY, anchoPanel, altoPanel, 8, 8);
                            g.setColor((c.getTemperatura() > 5.0) ? Color.RED : Color.DARK_GRAY);
                            g.setStroke(new java.awt.BasicStroke(1.5f));
                            g.drawRoundRect(panelX, panelY, anchoPanel, altoPanel, 8, 8);
                            
                            g.setColor(Color.BLACK);
                            g.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 11));
                            g.drawString("TELEMETRÍA HIRATA", panelX + 8, panelY + 15);
                            g.setFont(new java.awt.Font("Segoe UI", java.awt.Font.PLAIN, 10));
                            g.drawString("S/N GPS: " + c.getIdGps(), panelX + 8, panelY + 30);
                            g.drawString("Estanque: " + String.format("%.1f%%", c.getCombustible()), panelX + 8, panelY + 44);
                            g.drawString("Temp. Carga: " + String.format("%.1f°C", c.getTemperatura()), panelX + 8, panelY + 58);
                        }
                    }
                }
            });
            waypointPainter.setWaypoints(listaWaypoints);

            // =========================================================================
            // NUEVO: PINTOR GEOMÉTRICO DE TRAZADO DE RUTA (SATISFACE RF-10) 
            // Si hay un camión seleccionado, dibuja una línea uniendo todas sus coordenadas previas
            // =========================================================================
            org.jxmapviewer.painter.Painter<JXMapViewer> lineaRutaPainter = new org.jxmapviewer.painter.Painter<JXMapViewer>() {
                @Override
                public void paint(java.awt.Graphics2D g, JXMapViewer map, int w, int h) {
                    if (idGpsSeleccionado == -1) return;

                    // Ir a MySQL a buscar el historial cronológico de posiciones de este vehículo [cite: 11]
                    List<GeoPosition> puntosHistorial = telemetriaDAO.obtenerHistorialRutaCamion(idGpsSeleccionado);
                    if (puntosHistorial.size() < 2) return;

                    g.setColor(new Color(0, 153, 76, 180)); // Verde esmeralda semitransparente para la ruta
                    g.setStroke(new java.awt.BasicStroke(3.0f, java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND));

                    int[] puntosX = new int[puntosHistorial.size()];
                    int[] puntosY = new int[puntosHistorial.size()];

                    // Convertir el historial geográfico a pixeles de pantalla
                    for (int i = 0; i < puntosHistorial.size(); i++) {
                        java.awt.geom.Point2D pixel = map.getTileFactory().geoToPixel(puntosHistorial.get(i), map.getZoom());
                        java.awt.Rectangle rect = map.getViewportBounds();
                        puntosX[i] = (int) pixel.getX() - rect.x;
                        puntosY[i] = (int) pixel.getY() - rect.y;
                    }

                    // Dibujar la línea poligonal continua a lo largo de las calles
                    g.drawPolyline(puntosX, puntosY, puntosHistorial.size());
                    
                    // CÁLCULO DEL TIEMPO DE RECORRIDO SIMULADO (RF-10) 
                    // Cada nodo del historial representa una transmisión cada 3 segundos en nuestra simulación
                    int segundosTotales = puntosHistorial.size() * 3;
                    int minutos = segundosTotales / 60;
                    int segundos = segundosTotales % 60;
                    
                    // Inyectar el indicador de tiempo en una etiqueta de tu JFrame lateral
                    txtTiempoRecorrido.setText(String.format("%02d min %02d seg", minutos, segundos));
                }
            };

            // 3. Unir ambos pintores en un CompoundPainter (Capa combinada)
            List<org.jxmapviewer.painter.Painter<JXMapViewer>> listaPainters = new java.util.ArrayList<>();
            listaPainters.add(lineaRutaPainter); // Primero se dibuja la línea de la calle por debajo
            listaPainters.add(waypointPainter);  // Luego se dibujan los camiones por encima
            
            org.jxmapviewer.painter.CompoundPainter<JXMapViewer> compoundPainter = new org.jxmapviewer.painter.CompoundPainter<>(listaPainters);
            mapaViewer.setOverlayPainter(compoundPainter);
            
            mapaViewer.repaint();
        });
        relojMonitoreo.start();
    }

    /**
     * CLASE PUENTE (INNER CLASS): Es el intermediario directo.
     * JavaScript invocará este método al hacer click en un camión dentro de Leaflet.
     */
    public class BridgeJavaJS {
        public void seleccionarCamionDesdeMapa(int idGps) {
            // Este bloque se ejecuta cuando haces click sobre el ícono del camión en las calles de Iquique
            // Buscamos los datos en la base de datos y rellenamos los campos de texto laterales
            System.out.println("[Puente IoT] El Administrador seleccionó el Dispositivo GPS ID: " + idGps);
            
            // Aquí puedes colocar consultas adicionales usando tu DAO para pintar etiquetas:
            txtGpsVinculado.setText("S/N: " + idGps);
            
            // Alerta visual de confirmación en la UI
            JOptionPane.showMessageDialog(null, "Abriendo bitácora de telemetría en tiempo real para el GPS ID: " + idGps);
        }
    }
    
    private void activarEscuchaMonitoreo(int idCamion, String patente) {
        // Si ya había un reloj corriendo, lo apagamos para no duplicar procesos
        if (timerMonitoreo != null && timerMonitoreo.isRunning()) {
            timerMonitoreo.stop();
        }

        idCamionSeleccionadoAdmin = idCamion;

        // Configurar reloj para ir a MySQL cada 3 segundos (3000 ms)
        timerMonitoreo = new javax.swing.Timer(3000, e -> {
            ControladorAdmin ctrl = new ControladorAdmin();
            com.hirata.model.TelemetriaRuta dataLive = ctrl.obtenerTelemetriaEnVivo(idCamionSeleccionadoAdmin);

            if (dataLive != null) {
                org.jxmapviewer.viewer.GeoPosition posActual = new org.jxmapviewer.viewer.GeoPosition(dataLive.getLatitud(), dataLive.getLongitud());

                // Requerimiento Crítico RF-13: Monitoreo térmico automatizado
                // Instanciamos el waypoint pasando la temperatura real leída del sensor IoT
                waypointCamionMonitoreo = new CamionWaypoint(
                    idCamionSeleccionadoAdmin, 
                    patente, 
                    posActual.getLatitude(), 
                    posActual.getLongitude(), 
                    dataLive.getConsumoCombustible(), 
                    dataLive.getTemperaturaCelsius()
                );

                // Colocar los indicadores en etiquetas de la interfaz (Labels del Admin)
                lblLiveCombustible.setText("Combustible: " + String.format("%.1f", dataLive.getConsumoCombustible()) + "%");
                lblLiveTemperatura.setText("Temperatura Carga: " + dataLive.getTemperaturaCelsius() + "°C");

                // Regla de Negocio RF-13 (Alerta visual en los Labels del admin)
                if (dataLive.getTemperaturaCelsius() > 5.0) {
                    lblLiveTemperatura.setForeground(java.awt.Color.RED);
                    lblLiveEstadoCritico.setText("¡ALERTA TÉRMICA: CADENA DE FRÍO COMPROMETIDA!");
                } else {
                    lblLiveTemperatura.setForeground(new java.awt.Color(0, 102, 0));
                    lblLiveEstadoCritico.setText("Estado: Operación Normal");
                }

                // Mover la cámara del administrador para seguir al camión y redibujar el mapa
                mapaViewer.setAddressLocation(posActual);
                actualizarCapasMapaMonitoreo();

            } else {
                lblLiveEstadoCritico.setText("El vehículo no registra un viaje activo en este momento.");
            }
        });

        timerMonitoreo.start(); // Encender el motor de escucha
    }
    
    private void actualizarCapasMapaMonitoreo() {
        List<Painter<JXMapViewer>> listaPainters = new ArrayList<>();

        if (waypointCamionMonitoreo != null) {
            org.jxmapviewer.viewer.WaypointPainter<org.jxmapviewer.viewer.Waypoint> waypointPainter = new org.jxmapviewer.viewer.WaypointPainter<>();
            waypointPainter.setRenderer(new org.jxmapviewer.viewer.WaypointRenderer<org.jxmapviewer.viewer.Waypoint>() {
                @Override
                public void paintWaypoint(java.awt.Graphics2D g, JXMapViewer map, org.jxmapviewer.viewer.Waypoint wp) {
                    if (wp instanceof CamionWaypoint) {
                        CamionWaypoint c = (CamionWaypoint) wp;
                        java.awt.geom.Point2D p = map.getTileFactory().geoToPixel(c.getPosition(), map.getZoom());
                        int x = (int) p.getX();
                        int y = (int) p.getY();

                        // PINTADO INTELIGENTE (RF-13): Conmuta color según la telemetría térmica
                        g.setColor(c.getColorEstado()); 
                        g.fillRect(x - 12, y - 6, 22, 11); // Remolque frigorífico
                        g.fillRect(x + 10, y - 3, 6, 8);   // Cabina

                        g.setColor(java.awt.Color.BLACK);
                        g.fillOval(x - 9, y + 5, 4, 4);   // Ruedas
                        g.fillOval(x + 2, y + 5, 4, 4);
                        g.fillOval(x + 10, y + 5, 4, 4);

                        g.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 10));
                        g.drawString(c.getPatente() + " (" + c.getTemperatura() + "°C)", x - 20, y - 10);
                    }
                }
            });

            java.util.Set<org.jxmapviewer.viewer.Waypoint> conjunto = new java.util.HashSet<>();
            conjunto.add(waypointCamionMonitoreo);
            waypointPainter.setWaypoints(conjunto);
            listaPainters.add(waypointPainter);
        }

        org.jxmapviewer.painter.CompoundPainter<JXMapViewer> compuesto = new org.jxmapviewer.painter.CompoundPainter<>(listaPainters);
        mapaViewer.setOverlayPainter(compuesto);
        mapaViewer.repaint();
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jTabbedPane1 = new javax.swing.JTabbedPane();
        jPanel1 = new javax.swing.JPanel();
        panelLateral = new javax.swing.JPanel();
        txtGpsVinculado = new javax.swing.JTextField();
        jLabel1 = new javax.swing.JLabel();
        lblAlertaTermica = new javax.swing.JLabel();
        txtTiempoRecorrido = new javax.swing.JLabel();
        lblLiveCombustible = new javax.swing.JLabel();
        lblLiveEstadoCritico = new javax.swing.JLabel();
        lblLiveTemperatura = new javax.swing.JLabel();
        txtTiempoRecorrido4 = new javax.swing.JLabel();
        jfxContainer = new javax.swing.JPanel();
        jPanel2 = new javax.swing.JPanel();

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);

        panelLateral.setBorder(javax.swing.BorderFactory.createEtchedBorder());

        txtGpsVinculado.setEditable(false);
        txtGpsVinculado.setText("------");
        txtGpsVinculado.addActionListener(this::txtGpsVinculadoActionPerformed);

        jLabel1.setText("Dispositivo GPS:");

        lblAlertaTermica.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        lblAlertaTermica.setText("Estado de Red: Normal");

        txtTiempoRecorrido.setText("Recorrido");

        lblLiveCombustible.setText("Recorrido");

        lblLiveEstadoCritico.setText("Recorrido");

        lblLiveTemperatura.setText("Recorrido");

        txtTiempoRecorrido4.setText("Recorrido");

        javax.swing.GroupLayout panelLateralLayout = new javax.swing.GroupLayout(panelLateral);
        panelLateral.setLayout(panelLateralLayout);
        panelLateralLayout.setHorizontalGroup(
            panelLateralLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, panelLateralLayout.createSequentialGroup()
                .addGroup(panelLateralLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addGroup(panelLateralLayout.createSequentialGroup()
                        .addContainerGap()
                        .addComponent(lblAlertaTermica, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                    .addGroup(panelLateralLayout.createSequentialGroup()
                        .addGap(19, 19, 19)
                        .addGroup(panelLateralLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(txtTiempoRecorrido, javax.swing.GroupLayout.PREFERRED_SIZE, 192, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addGroup(panelLateralLayout.createSequentialGroup()
                                .addComponent(jLabel1, javax.swing.GroupLayout.PREFERRED_SIZE, 124, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(txtGpsVinculado, javax.swing.GroupLayout.PREFERRED_SIZE, 98, javax.swing.GroupLayout.PREFERRED_SIZE))
                            .addComponent(lblLiveCombustible, javax.swing.GroupLayout.PREFERRED_SIZE, 192, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(lblLiveTemperatura, javax.swing.GroupLayout.PREFERRED_SIZE, 192, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(lblLiveEstadoCritico, javax.swing.GroupLayout.PREFERRED_SIZE, 192, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(txtTiempoRecorrido4, javax.swing.GroupLayout.PREFERRED_SIZE, 192, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addGap(0, 9, Short.MAX_VALUE)))
                .addContainerGap())
        );
        panelLateralLayout.setVerticalGroup(
            panelLateralLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(panelLateralLayout.createSequentialGroup()
                .addGap(115, 115, 115)
                .addGroup(panelLateralLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(txtGpsVinculado, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel1))
                .addGap(18, 18, 18)
                .addComponent(txtTiempoRecorrido)
                .addGap(21, 21, 21)
                .addComponent(lblLiveEstadoCritico)
                .addGap(18, 18, 18)
                .addComponent(lblLiveCombustible)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(lblLiveTemperatura)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(txtTiempoRecorrido4)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 176, Short.MAX_VALUE)
                .addComponent(lblAlertaTermica)
                .addContainerGap())
        );

        jfxContainer.setBorder(javax.swing.BorderFactory.createEtchedBorder());
        jfxContainer.setMinimumSize(new java.awt.Dimension(600, 500));
        jfxContainer.setOpaque(false);
        jfxContainer.setPreferredSize(new java.awt.Dimension(800, 600));
        jfxContainer.setRequestFocusEnabled(false);

        javax.swing.GroupLayout jfxContainerLayout = new javax.swing.GroupLayout(jfxContainer);
        jfxContainer.setLayout(jfxContainerLayout);
        jfxContainerLayout.setHorizontalGroup(
            jfxContainerLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 608, Short.MAX_VALUE)
        );
        jfxContainerLayout.setVerticalGroup(
            jfxContainerLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 0, Short.MAX_VALUE)
        );

        javax.swing.GroupLayout jPanel1Layout = new javax.swing.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(panelLateral, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jfxContainer, javax.swing.GroupLayout.PREFERRED_SIZE, 612, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap())
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(jfxContainer, javax.swing.GroupLayout.DEFAULT_SIZE, 500, Short.MAX_VALUE)
                    .addComponent(panelLateral, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        jTabbedPane1.addTab("tab1", jPanel1);

        javax.swing.GroupLayout jPanel2Layout = new javax.swing.GroupLayout(jPanel2);
        jPanel2.setLayout(jPanel2Layout);
        jPanel2Layout.setHorizontalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 896, Short.MAX_VALUE)
        );
        jPanel2Layout.setVerticalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 511, Short.MAX_VALUE)
        );

        jTabbedPane1.addTab("tab2", jPanel2);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jTabbedPane1)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jTabbedPane1, javax.swing.GroupLayout.PREFERRED_SIZE, 546, javax.swing.GroupLayout.PREFERRED_SIZE)
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void txtGpsVinculadoActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_txtGpsVinculadoActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_txtGpsVinculadoActionPerformed

    /**
     * @param args the command line arguments
     */
    public static void main(String args[]) {
        /* Set the Nimbus look and feel */
        //<editor-fold defaultstate="collapsed" desc=" Look and feel setting code (optional) ">
        /* If Nimbus (introduced in Java SE 6) is not available, stay with the default look and feel.
         * For details see http://download.oracle.com/javase/tutorial/uiswing/lookandfeel/plaf.html 
         */
        try {
            for (javax.swing.UIManager.LookAndFeelInfo info : javax.swing.UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    javax.swing.UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (ReflectiveOperationException | javax.swing.UnsupportedLookAndFeelException ex) {
            logger.log(java.util.logging.Level.SEVERE, null, ex);
        }
        //</editor-fold>

        /* Create and display the form */
        java.awt.EventQueue.invokeLater(() -> new PanelMonitoreoIoT().setVisible(true));
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JLabel jLabel1;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JPanel jPanel2;
    private javax.swing.JTabbedPane jTabbedPane1;
    private javax.swing.JPanel jfxContainer;
    private javax.swing.JLabel lblAlertaTermica;
    private javax.swing.JLabel lblLiveCombustible;
    private javax.swing.JLabel lblLiveEstadoCritico;
    private javax.swing.JLabel lblLiveTemperatura;
    private javax.swing.JPanel panelLateral;
    private javax.swing.JTextField txtGpsVinculado;
    private javax.swing.JLabel txtTiempoRecorrido;
    private javax.swing.JLabel txtTiempoRecorrido4;
    // End of variables declaration//GEN-END:variables
    private final java.util.Set<org.jxmapviewer.viewer.Waypoint> listaWaypoints = new java.util.HashSet<>();
}
