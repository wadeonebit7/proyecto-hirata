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
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.geom.Point2D;
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
import org.jxmapviewer.painter.CompoundPainter;
import org.jxmapviewer.painter.Painter;
import org.jxmapviewer.viewer.DefaultTileFactory;
import org.jxmapviewer.viewer.DefaultWaypoint;
import org.jxmapviewer.viewer.GeoPosition;
import org.jxmapviewer.viewer.TileFactoryInfo;
import org.jxmapviewer.viewer.Waypoint;
import org.jxmapviewer.viewer.WaypointPainter;
import org.jxmapviewer.viewer.WaypointRenderer;

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
                    boolean camionDetectado = false;
                    
                    // Buscar en nuestra lista de camiones activos si alguno está en ese píxel
                    for (Waypoint wp : listaWaypoints) {
                        if (wp instanceof CamionWaypoint) {
                            CamionWaypoint camion = (CamionWaypoint) wp;
                            
                            // Obtener posición en píxeles del camión en la pantalla actual
                            Point2D camionPoint = mapaViewer.getTileFactory().geoToPixel(camion.getPosition(), mapaViewer.getZoom());
                            
                            // Traducir a coordenadas locales del panel visual
                            Rectangle rectMapa = mapaViewer.getViewportBounds();
                            int localX = (int) camionPoint.getX() - rectMapa.x;
                            int localY = (int) camionPoint.getY() - rectMapa.y;
                            
                            // Dentro del mouseClicked, cuando detecta el click en el radio de tolerancia:
                            if (clickPoint.distance(localX, localY) <= 15) {
                                // Guardamos el ID seleccionado de forma global
                                idGpsSeleccionado = camion.getIdGps();

                                // Inyectamos también en los campos fijos por si acaso
                                txtGpsVinculado.setText(String.valueOf(camion.getIdGps()));
                                camionDetectado = true;
                                break;
                            }
                        }
                    }
                    
                    // Si el clic terminó y no se tocó ningún camión en el radio, ocultamos el panel
                    if (!camionDetectado) {
                        idGpsSeleccionado = -1;
                        txtGpsVinculado.setText("");
                        mapaViewer.repaint(); // Redibujar inmediatamente para borrar el panel de la pantalla
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

            // 2. Pintor de los iconos de los camiones y panel flotante de datos expandido
            WaypointPainter<Waypoint> waypointPainter = new WaypointPainter<>();
            waypointPainter.setRenderer(new WaypointRenderer<Waypoint>() {
                @Override
                public void paintWaypoint(Graphics2D g, JXMapViewer map, Waypoint wp) {
                    if (wp instanceof CamionWaypoint) {
                        CamionWaypoint c = (CamionWaypoint) wp;
                        java.awt.geom.Point2D p = map.getTileFactory().geoToPixel(c.getPosition(), map.getZoom());
                        int x = (int) p.getX();
                        int y = (int) p.getY();

                        // Dibujar el icono nativo del camión
                        g.setColor(c.getColorEstado());
                        g.fillRect(x - 12, y - 6, 22, 11); // Cuerpo
                        g.fillRect(x + 10, y - 3, 6, 8);   // Cabina
                        g.setColor(Color.BLACK);
                        g.fillOval(x - 9, y + 5, 4, 4);    // Ruedas
                        g.fillOval(x + 2, y + 5, 4, 4);
                        g.fillOval(x + 10, y + 5, 4, 4);

                        g.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 10));
                        g.drawString("S/N: " + c.getIdGps(), x - 15, y - 10);

                        // =========================================================================
                        // RENDERIZADO DEL PANEL FLOTANTE EN TIEMPO REAL (REDISEÑADO Y CORREGIDO)
                        // =========================================================================
                        if (c.getIdGps() == idGpsSeleccionado) {
                            TelemetriaRuta infoCamion = null;
                            for (TelemetriaRuta t : flotaActiva) {
                                if (t.getIdGpsFk() == c.getIdGps()) {
                                    infoCamion = t;
                                    break;
                                }
                            }

                            if (infoCamion != null) {
                                // Aumentamos levemente el alto a 130 para dar espacio a la fila de la patente sin colapsar textos
                                int anchoPanel = 220; 
                                int altoPanel = 130;
                                int panelX = x - (anchoPanel / 2); 
                                int panelY = y - altoPanel - 25;

                                g.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING, java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

                                // Fondo blanco semitransparente
                                g.setColor(new Color(255, 255, 255, 240));
                                g.fillRoundRect(panelX, panelY, anchoPanel, altoPanel, 10, 10);

                                // Borde de alerta térmica dinámico
                                g.setColor((c.getTemperatura() > 5.0) ? Color.RED : new Color(50, 50, 50));
                                g.setStroke(new java.awt.BasicStroke(1.8f));
                                g.drawRoundRect(panelX, panelY, anchoPanel, altoPanel, 10, 10);

                                int posXText = panelX + 12;
                                int posYText = panelY + 16;

                                // Encabezado principal
                                g.setColor(Color.BLACK);
                                g.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 11));
                                g.drawString("TELEMETRÍA EN VIVO", posXText, posYText);

                                // Línea separadora
                                g.setColor(new Color(200, 200, 200));
                                g.drawLine(panelX + 10, panelY + 22, panelX + anchoPanel - 10, panelY + 22);

                                g.setFont(new java.awt.Font("Segoe UI", java.awt.Font.PLAIN, 10));
                                g.setColor(new Color(30, 30, 30));

                                // 1. Datos de Identificación (CORREGIDO: Coordenadas Y secuenciales)
                                String vehiculoInfo = (infoCamion.getMarcaCamion() != null) ? (infoCamion.getMarcaCamion() + " " + infoCamion.getModeloCamion()) : "Vehículo Desconocido";
                                g.drawString("Vehículo: " + vehiculoInfo, posXText, posYText + 16);
                                g.drawString("Patente: " + c.getPatente(), posXText, posYText + 28);

                                // 2. Coordenadas Geográficas
                                g.drawString("Lat: " + String.format("%.6f", c.getPosition().getLatitude()) + " | Lon: " + String.format("%.6f", c.getPosition().getLongitude()), posXText, posYText + 41);

                                // ===================== 3. Estado del GPS con Lógica Temporal Global =========================
                                g.drawString("Estado GPS: ", posXText, posYText + 54);
                                g.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 10));

                                boolean estaActivoAhora = false;

                                if (infoCamion != null) {
                                    // Leemos de forma directa y explícita el retraso real de este vehículo
                                    int segundosDesdeUltimoInforme = infoCamion.getSegundosAtras();
                                    
                                    // Ventana elástica segura: si transmitió en los últimos 15 segundos, está ONLINE
                                    if (segundosDesdeUltimoInforme >= 0 && segundosDesdeUltimoInforme <= 15) {
                                        estaActivoAhora = true;
                                    }
                                }

                                if (estaActivoAhora) {
                                    g.setColor(new Color(0, 153, 51)); // Verde
                                    g.drawString("ONLINE", posXText + 58, posYText + 54);
                                } else {
                                    g.setColor(new Color(230, 126, 34)); // Gris
                                    g.drawString("Ahorro de Energía", posXText + 58, posYText + 54);
                                }

                                g.setFont(new java.awt.Font("Segoe UI", java.awt.Font.PLAIN, 10));
                                g.setColor(new Color(30, 30, 30));
                                // ===========================================================================================
                                
                                // 4. Variables de los Sensores IoT
                                g.drawString("Combustible: " + String.format("%.1f%%", c.getCombustible()), posXText, posYText + 67);
                                
                                if (c.getTemperatura() > 5.0) {
                                    g.setColor(Color.RED);
                                    g.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 10));
                                }
                                g.drawString("Temp. Carga: " + String.format("%.1f°C", c.getTemperatura()), posXText, posYText + 80);
                                
                                g.setFont(new java.awt.Font("Segoe UI", java.awt.Font.PLAIN, 10));
                                g.setColor(new Color(30, 30, 30));

                                // 5. Fecha y Hora de la última ráfaga procesada
                                String textoFechaHora = "Buscando satélite...";
                                if (infoCamion.getFechaHora() != null) {
                                    java.text.SimpleDateFormat sdfVisual = new java.text.SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
                                    textoFechaHora = sdfVisual.format(infoCamion.getFechaHora());
                                }
                                g.setColor(Color.DARK_GRAY);
                                g.drawString("Informe: " + textoFechaHora, posXText, posYText + 95);
                            }
                        }
                    }
                }
            });
            waypointPainter.setWaypoints(listaWaypoints);

            // 3. Unir pintores en el CompoundPainter
            List<Painter<JXMapViewer>> listaPainters = new ArrayList<>();
            listaPainters.add(waypointPainter); 

            CompoundPainter<JXMapViewer> compoundPainter = new CompoundPainter<>(listaPainters);
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
        
        mapaViewer.setOverlayPainter(null); // Limpia por completo cualquier pintor remanente en el lienzo
        
        List<Painter<JXMapViewer>> listaPainters = new ArrayList<>();

        if (waypointCamionMonitoreo != null) {
            WaypointPainter<Waypoint> waypointPainter = new WaypointPainter<>();
            waypointPainter.setRenderer(new WaypointRenderer<Waypoint>() {
                @Override
                public void paintWaypoint(Graphics2D g, JXMapViewer map, Waypoint wp) {
                    if (wp instanceof CamionWaypoint) {
                        CamionWaypoint c = (CamionWaypoint) wp;
                        Point2D p = map.getTileFactory().geoToPixel(c.getPosition(), map.getZoom());
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

            Set<Waypoint> conjunto = new HashSet<>();
            conjunto.add(waypointCamionMonitoreo);
            waypointPainter.setWaypoints(conjunto);
            listaPainters.add(waypointPainter);
        }

        CompoundPainter<JXMapViewer> compuesto = new CompoundPainter<>(listaPainters);
        mapaViewer.setOverlayPainter(compuesto);
        
        mapaViewer.revalidate();
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
        jLabel2 = new javax.swing.JLabel();
        txtGpsVinculado1 = new javax.swing.JTextField();
        jfxContainer = new javax.swing.JPanel();
        jPanel2 = new javax.swing.JPanel();
        jScrollPane1 = new javax.swing.JScrollPane();
        jTable1 = new javax.swing.JTable();

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);

        panelLateral.setBorder(javax.swing.BorderFactory.createEtchedBorder());

        txtGpsVinculado.setEditable(false);
        txtGpsVinculado.setHorizontalAlignment(javax.swing.JTextField.CENTER);
        txtGpsVinculado.setText("------");
        txtGpsVinculado.addActionListener(this::txtGpsVinculadoActionPerformed);

        jLabel1.setText("Dispositivo GPS:");

        lblAlertaTermica.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        lblAlertaTermica.setText("Estado de Red: Normal");

        txtTiempoRecorrido.setText("TiempoRecorrido");

        lblLiveCombustible.setText("LiveCombustible");

        lblLiveEstadoCritico.setText("LiveEstadoCritico");

        lblLiveTemperatura.setText("LiveTemperatura");

        txtGpsVinculado1.setEditable(false);
        txtGpsVinculado1.setHorizontalAlignment(javax.swing.JTextField.CENTER);
        txtGpsVinculado1.setText("------");
        txtGpsVinculado1.addActionListener(this::txtGpsVinculado1ActionPerformed);

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
                            .addComponent(lblLiveCombustible, javax.swing.GroupLayout.PREFERRED_SIZE, 192, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(lblLiveTemperatura, javax.swing.GroupLayout.PREFERRED_SIZE, 192, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(lblLiveEstadoCritico, javax.swing.GroupLayout.PREFERRED_SIZE, 192, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addGroup(panelLateralLayout.createSequentialGroup()
                                .addComponent(jLabel1, javax.swing.GroupLayout.PREFERRED_SIZE, 124, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(txtGpsVinculado, javax.swing.GroupLayout.PREFERRED_SIZE, 98, javax.swing.GroupLayout.PREFERRED_SIZE))
                            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, panelLateralLayout.createSequentialGroup()
                                .addComponent(jLabel2, javax.swing.GroupLayout.PREFERRED_SIZE, 124, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(txtGpsVinculado1, javax.swing.GroupLayout.PREFERRED_SIZE, 98, javax.swing.GroupLayout.PREFERRED_SIZE)))
                        .addGap(0, 9, Short.MAX_VALUE)))
                .addContainerGap())
        );
        panelLateralLayout.setVerticalGroup(
            panelLateralLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(panelLateralLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(panelLateralLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel1)
                    .addComponent(txtGpsVinculado, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(18, 18, 18)
                .addGroup(panelLateralLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel2)
                    .addComponent(txtGpsVinculado1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(87, 87, 87)
                .addComponent(txtTiempoRecorrido)
                .addGap(21, 21, 21)
                .addComponent(lblLiveEstadoCritico)
                .addGap(18, 18, 18)
                .addComponent(lblLiveCombustible)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(lblLiveTemperatura)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 204, Short.MAX_VALUE)
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

        jTable1.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {
                {null, null, null, null},
                {null, null, null, null},
                {null, null, null, null},
                {null, null, null, null}
            },
            new String [] {
                "Title 1", "Title 2", "Title 3", "Title 4"
            }
        ));
        jScrollPane1.setViewportView(jTable1);

        javax.swing.GroupLayout jPanel2Layout = new javax.swing.GroupLayout(jPanel2);
        jPanel2.setLayout(jPanel2Layout);
        jPanel2Layout.setHorizontalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel2Layout.createSequentialGroup()
                .addGap(20, 20, 20)
                .addComponent(jScrollPane1, javax.swing.GroupLayout.PREFERRED_SIZE, 856, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(20, Short.MAX_VALUE))
        );
        jPanel2Layout.setVerticalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel2Layout.createSequentialGroup()
                .addContainerGap(78, Short.MAX_VALUE)
                .addComponent(jScrollPane1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap())
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

    private void txtGpsVinculado1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_txtGpsVinculado1ActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_txtGpsVinculado1ActionPerformed

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
    private javax.swing.JLabel jLabel2;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JPanel jPanel2;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JTabbedPane jTabbedPane1;
    private javax.swing.JTable jTable1;
    private javax.swing.JPanel jfxContainer;
    private javax.swing.JLabel lblAlertaTermica;
    private javax.swing.JLabel lblLiveCombustible;
    private javax.swing.JLabel lblLiveEstadoCritico;
    private javax.swing.JLabel lblLiveTemperatura;
    private javax.swing.JPanel panelLateral;
    private javax.swing.JTextField txtGpsVinculado;
    private javax.swing.JTextField txtGpsVinculado1;
    private javax.swing.JLabel txtTiempoRecorrido;
    // End of variables declaration//GEN-END:variables
    private final java.util.Set<org.jxmapviewer.viewer.Waypoint> listaWaypoints = new java.util.HashSet<>();
}
