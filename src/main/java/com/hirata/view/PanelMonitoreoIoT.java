/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/GUIForms/JFrame.java to edit this template
 */
package com.hirata.view;

import com.hirata.controller.ControladorAdmin;
import com.hirata.dao.InformesDAO;
import com.hirata.dao.TelemetriaDAO;
import com.hirata.model.ResumenViaje;
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
import javax.swing.table.DefaultTableModel;
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
    
    private JXMapViewer mapaViewer;
    private final TelemetriaDAO telemetriaDAO;
    private Timer relojMonitoreo;
    private int idGpsSeleccionado = -1;
    
    private java.awt.Image imgCamionAzul = null;
    private java.awt.Image imgCamionRojo = null;
    
    /**
     * Creates new form PanelMonitoreoIoT
     */
    public PanelMonitoreoIoT() {
        initComponents();
        
        cargarInformesGerenciales();
        
        // Cargar las imágenes PNG en memoria al iniciar la ventana
        try {
            // El "/com/hirata/view/" debe coincidir exactamente con la ruta de carpetas donde guardes tus PNGs
            imgCamionAzul = new javax.swing.ImageIcon(getClass().getResource("/com/hirata/view/camion_azul.png")).getImage();
            imgCamionRojo = new javax.swing.ImageIcon(getClass().getResource("/com/hirata/view/camion_rojo.png")).getImage();
        } catch (Exception ex) {
            System.err.println("Advertencia: No se pudieron cargar los archivos PNG de los camiones. " + ex.getMessage());
        }
        
        this.setLocationRelativeTo(null);
        this.telemetriaDAO = new TelemetriaDAO();
        inicializarMapaNativo();
        configurarTemporizadorLectura();
        
        // RENDERIZADOR PERSONALIZADO PARA MARCAR ALERTAS TÉRMICAS EN LA TABLA
        // RENDERIZADOR PERSONALIZADO PARA MARCAR ALERTAS TÉRMICAS EN LA TABLA
        tblFlota.setDefaultRenderer(Object.class, new javax.swing.table.DefaultTableCellRenderer() {
            @Override
            public java.awt.Component getTableCellRendererComponent(javax.swing.JTable table, Object value,
                    boolean isSelected, boolean hasFocus, int row, int column) {
                
                java.awt.Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                
                // 1. Escudo contra celdas vacías (Evita el NullPointerException inicial de NetBeans)
                Object valorPatente = table.getValueAt(row, 0);
                if (valorPatente == null) {
                    return c; 
                }
                
                String patenteFila = valorPatente.toString();
                boolean excedeTemperatura = false;
                
                if (listaWaypoints != null) {
                    for (org.jxmapviewer.viewer.Waypoint wp : listaWaypoints) {
                        if (wp instanceof CamionWaypoint) {
                            CamionWaypoint camion = (CamionWaypoint) wp;
                            if (camion.getPatente().equalsIgnoreCase(patenteFila) && camion.getTemperatura() > 5.0) {
                                excedeTemperatura = true;
                                break;
                            }
                        }
                    }
                }
                
                // 2. Aplicar colores dinámicos
                if (excedeTemperatura) {
                    c.setBackground(new Color(255, 204, 204)); // Fondo Rojo de Alerta
                    c.setForeground(Color.RED);               // Texto Rojo
                    setFont(getFont().deriveFont(java.awt.Font.BOLD));
                } else {
                    if (isSelected) {
                        c.setBackground(table.getSelectionBackground());
                        c.setForeground(table.getSelectionForeground());
                    } else {
                        c.setBackground(Color.WHITE); // Fondo Normal
                        Object valorEstado = table.getValueAt(row, 2);
                        String estadoVehiculo = (valorEstado != null) ? valorEstado.toString() : "";
                        
                        if (column == 2) { // Si está pintando la columna de "Estado"
                            if (estadoVehiculo.equals("Online")) {
                                c.setForeground(new Color(0, 153, 51)); // Verde
                                setFont(getFont().deriveFont(java.awt.Font.BOLD));
                            } else if (estadoVehiculo.equals("Offline")) {
                                c.setForeground(Color.GRAY); // Gris
                                setFont(getFont().deriveFont(java.awt.Font.ITALIC));
                            } else {
                                c.setForeground(Color.BLACK); // Por seguridad
                                setFont(getFont().deriveFont(java.awt.Font.PLAIN));
                            }
                        } else {
                            // Columnas normales (Patente y N° Serie)
                            c.setForeground(Color.BLACK);
                            setFont(getFont().deriveFont(java.awt.Font.PLAIN));
                        }
                    }
                }
                return c;
            }
        });
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
                                txtDispositivoGps.setText(String.valueOf(camion.getIdGps()));
                                camionDetectado = true;
                                break;
                            }
                        }
                    }
                    
                    // Si el clic terminó y no se tocó ningún camión en el radio, ocultamos el panel
                    if (!camionDetectado) {
                        idGpsSeleccionado = -1;
                        txtDispositivoGps.setText("");
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
            
            DefaultTableModel modeloTabla = (DefaultTableModel) tblFlota.getModel();
            int filaSeleccionada = tblFlota.getSelectedRow();
            modeloTabla.setRowCount(0);
            
            int contadorAlertasTermicas = 0;
            
            for (TelemetriaRuta t : flotaActiva) {
                
                // 1. Determinar el estado lógico en tiempo real (margen de 15 segundos)
                boolean estaActivoAhora = (t.getSegundosAtras() >= 0 && t.getSegundosAtras() <= 15);
                String estadoTexto = estaActivoAhora ? "Online" : "Offline";
                
                // 2. Llenar las 3 columnas exactas de tu JTable
                modeloTabla.addRow(new Object[]{
                    t.getPatente(),
                    t.getNumeroSerieGps(), 
                    estadoTexto 
                });
                
                // EVALUACIÓN EN TIEMPO REAL: Si el camión actual excede los 5.0°C, sumamos una incidencia
                if (t.getTemperaturaMotor() > 5.0) {
                    contadorAlertasTermicas++;
                }
            }
            
            lblTotalFlota.setText("Flota Activa: " + flotaActiva.size() + " vehículos.");
            lblTotalIncidencias.setText("Incidencias: " + contadorAlertasTermicas + " alertas térmicas.");
            
            // Gestión visual inteligente: Si hay alertas se tiñe de rojo, si está todo limpio en verde
            if (contadorAlertasTermicas > 0) {
                lblTotalIncidencias.setForeground(Color.RED);
            } else {
                lblTotalIncidencias.setForeground(new Color(0, 153, 51)); // Color Verde estable
            }
            // =========================================================================
            
            // Restaurar la selección del usuario
            if (filaSeleccionada != -1 && filaSeleccionada < tblFlota.getRowCount()) {
                tblFlota.setRowSelectionInterval(filaSeleccionada, filaSeleccionada);
            }
            
            listaWaypoints.clear();
            for (TelemetriaRuta t : flotaActiva) {
                listaWaypoints.add(new CamionWaypoint(
                        t.getIdGpsFk(), 
                        t.getPatente(), 
                        t.getLatitud(), 
                        t.getLongitud(), 
                        t.getConsumoCombustible(), 
                        t.getTemperaturaMotor() 
                ));
            }

            // Pintor de los iconos de los camiones y panel flotante de datos expandido
            WaypointPainter<Waypoint> waypointPainter = new WaypointPainter<>();
            waypointPainter.setRenderer(new WaypointRenderer<Waypoint>() {
                @Override
                public void paintWaypoint(Graphics2D g, JXMapViewer map, Waypoint wp) {
                    if (wp instanceof CamionWaypoint) {
                        CamionWaypoint c = (CamionWaypoint) wp;
                        java.awt.geom.Point2D p = map.getTileFactory().geoToPixel(c.getPosition(), map.getZoom());
                        int x = (int) p.getX();
                        int y = (int) p.getY();

                        // CONMUTACIÓN DE IMÁGENES EN TIEMPO REAL
                        if (imgCamionAzul != null && imgCamionRojo != null) {
                            java.awt.Image imagenAQuemar = (c.getTemperatura() > 5.0) ? imgCamionRojo : imgCamionAzul;
                            g.drawImage(imagenAQuemar, x - 16, y - 8, 48, 24, null);
                        } else {
                            // Fallback (Rectángulos)
                            g.setColor(c.getColorEstado()); 
                            g.fillRect(x - 12, y - 6, 22, 11); 
                            g.fillRect(x + 10, y - 3, 6, 8);   
                            g.setColor(Color.BLACK);
                            g.fillOval(x - 9, y + 5, 4, 4);    
                            g.fillOval(x + 2, y + 5, 4, 4);
                            g.fillOval(x + 10, y + 5, 4, 4);
                        }

                        // Imprimir el S/N del GPS flotando arriba del camión en el mapa
                        g.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 10));
                        g.setColor(Color.BLACK);
                        g.drawString("S/N: " + c.getIdGps(), x - 15, y - 12);

                        // RENDERIZADO DEL PANEL FLOTANTE EN TIEMPO REAL
                        if (c.getIdGps() == idGpsSeleccionado) {
                            TelemetriaRuta infoCamion = null;
                            for (TelemetriaRuta t : flotaActiva) {
                                if (t.getIdGpsFk() == c.getIdGps()) {
                                    infoCamion = t;
                                    break;
                                }
                            }

                            if (infoCamion != null) {
                                int anchoPanel = 220; 
                                int altoPanel = 145;
                                int panelX = x - (anchoPanel / 2); 
                                int panelY = y - altoPanel - 25;

                                g.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING, java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

                                // Fondo blanco y borde
                                g.setColor(new Color(255, 255, 255, 240));
                                g.fillRoundRect(panelX, panelY, anchoPanel, altoPanel, 10, 10);
                                g.setColor((c.getTemperatura() > 5.0) ? Color.RED : new Color(50, 50, 50));
                                g.setStroke(new java.awt.BasicStroke(1.8f));
                                g.drawRoundRect(panelX, panelY, anchoPanel, altoPanel, 10, 10);

                                int posXText = panelX + 12;
                                int posYText = panelY + 16;

                                // Textos
                                g.setColor(Color.BLACK);
                                g.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 11));
                                g.drawString("TELEMETRÍA EN VIVO", posXText, posYText);

                                g.setColor(new Color(200, 200, 200));
                                g.drawLine(panelX + 10, panelY + 22, panelX + anchoPanel - 10, panelY + 22);

                                g.setFont(new java.awt.Font("Segoe UI", java.awt.Font.PLAIN, 10));
                                g.setColor(new Color(30, 30, 30));

                                String vehiculoInfo = (infoCamion.getMarcaCamion() != null) ? (infoCamion.getMarcaCamion() + " " + infoCamion.getModeloCamion()) : "Vehículo Desconocido";
                                g.drawString("Vehículo: " + vehiculoInfo, posXText, posYText + 16);
                                g.drawString("Patente: " + c.getPatente(), posXText, posYText + 28);
                                
                                String choferAsignado = (infoCamion.getNombreChofer() != null) ? infoCamion.getNombreChofer() : "Sin Chofer";
                                g.drawString("Chofer: " + choferAsignado, posXText, posYText + 40);
                                g.drawString("Lat: " + String.format("%.6f", c.getPosition().getLatitude()) + " | Lon: " + String.format("%.6f", c.getPosition().getLongitude()), posXText, posYText + 53);

                                g.drawString("Estado GPS: ", posXText, posYText + 66);
                                g.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 10));

                                boolean estaActivoAhora = (infoCamion.getSegundosAtras() >= 0 && infoCamion.getSegundosAtras() <= 15);

                                if (estaActivoAhora) {
                                    g.setColor(new Color(0, 153, 51)); 
                                    g.drawString("ONLINE", posXText + 58, posYText + 66);
                                } else {
                                    g.setColor(new Color(230, 126, 34)); 
                                    g.drawString("Ahorro de Energía", posXText + 58, posYText + 66);
                                }

                                g.setFont(new java.awt.Font("Segoe UI", java.awt.Font.PLAIN, 10));
                                g.setColor(new Color(30, 30, 30));
                                
                                g.drawString("Combustible: " + String.format("%.1f%%", infoCamion.getConsumoCombustible()), posXText, posYText + 79);

                                double tempReal = infoCamion.getTemperaturaMotor();
                                if (tempReal > 5.0) {
                                    g.setColor(Color.RED);
                                    g.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 10));
                                }
                                g.drawString("Temp. Carga: " + String.format("%.1f°C", tempReal), posXText, posYText + 92);

                                g.setFont(new java.awt.Font("Segoe UI", java.awt.Font.PLAIN, 10));
                                g.setColor(new Color(30, 30, 30));

                                String textoFechaHora = "Buscando satélite...";
                                if (infoCamion.getFechaHora() != null) {
                                    java.text.SimpleDateFormat sdfVisual = new java.text.SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
                                    textoFechaHora = sdfVisual.format(infoCamion.getFechaHora());
                                }
                                g.setColor(Color.DARK_GRAY);
                                g.drawString("Informe: " + textoFechaHora, posXText, posYText + 107);
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
            txtDispositivoGps.setText("S/N: " + idGps);
            
            // Alerta visual de confirmación en la UI
            JOptionPane.showMessageDialog(null, "Abriendo bitácora de telemetría en tiempo real para el GPS ID: " + idGps);
        }
    }
    
    private void cargarInformesGerenciales() {
        InformesDAO dao = new InformesDAO();
        
        // 1. CARGAR HISTORIAL DE RENDIMIENTO
        List<ResumenViaje> historial = dao.obtenerHistorialRendimiento();
        DefaultTableModel modeloRendimiento = (DefaultTableModel) tblRendimiento.getModel();
        modeloRendimiento.setRowCount(0); // Limpiar tabla
        
        for (ResumenViaje v : historial) {
            modeloRendimiento.addRow(new Object[]{
                v.getPatente(),
                v.getOrigen(),
                v.getDestino(),
                String.format("%.1f%%", v.getCombustibleGastado()),
                v.getAlertasTermicas(),
                v.getFechaViaje() != null ? new java.text.SimpleDateFormat("dd/MM/yyyy HH:mm").format(v.getFechaViaje()) : "N/A"
            });
        }
        
        // 2. CARGAR RUTAS MÁS FRECUENTES
        List<ResumenViaje> rutasFrecuentes = dao.obtenerRutasMasFrecuentes();
        DefaultTableModel modeloRutas = (DefaultTableModel) tblRutas.getModel();
        modeloRutas.setRowCount(0); // Limpiar tabla
        
        for (ResumenViaje v : rutasFrecuentes) {
            modeloRutas.addRow(new Object[]{
                v.getOrigen(),
                v.getDestino(),
                v.getFrecuencia() + " viajes"
            });
        }
        
        // 3. CARGAR KPI DE ALERTAS
        int totalAlertas = dao.obtenerTotalAlertasTermicas();
        lblTotalAlertas.setText(String.valueOf(totalAlertas));
        
        if (totalAlertas > 0) {
            lblTotalAlertas.setForeground(java.awt.Color.RED);
        } else {
            lblTotalAlertas.setForeground(new java.awt.Color(0, 153, 51)); // Verde
        }
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
        txtDispositivoGps = new javax.swing.JTextField();
        jLabel1 = new javax.swing.JLabel();
        jLabel2 = new javax.swing.JLabel();
        jScrollPane2 = new javax.swing.JScrollPane();
        tblFlota = new javax.swing.JTable();
        lblTotalFlota = new javax.swing.JLabel();
        lblTotalFlota1 = new javax.swing.JLabel();
        lblTotalFlota2 = new javax.swing.JLabel();
        lblTotalIncidencias = new javax.swing.JLabel();
        jfxContainer = new javax.swing.JPanel();
        jLabel3 = new javax.swing.JLabel();
        PanelInformes = new javax.swing.JPanel();
        jScrollPane1 = new javax.swing.JScrollPane();
        tblRendimiento = new javax.swing.JTable();
        jLabel4 = new javax.swing.JLabel();
        jScrollPane3 = new javax.swing.JScrollPane();
        tblRutas = new javax.swing.JTable();
        jLabel5 = new javax.swing.JLabel();
        lblTotalAlertas = new javax.swing.JLabel();
        btnActualizar = new javax.swing.JButton();
        btnExportarPDF = new javax.swing.JButton();
        jPanel2 = new javax.swing.JPanel();

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);

        panelLateral.setBorder(javax.swing.BorderFactory.createEtchedBorder());

        txtDispositivoGps.setEditable(false);
        txtDispositivoGps.setHorizontalAlignment(javax.swing.JTextField.CENTER);
        txtDispositivoGps.setText("------");
        txtDispositivoGps.addActionListener(this::txtDispositivoGpsActionPerformed);

        jLabel1.setFont(new java.awt.Font("Segoe UI", 1, 12)); // NOI18N
        jLabel1.setText("Dispositivo GPS:");

        tblFlota.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {
                {null, null, null},
                {null, null, null},
                {null, null, null},
                {null, null, null}
            },
            new String [] {
                "Patente", "N° Serie GPS", "Estado"
            }
        ));
        tblFlota.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                tblFlotaMouseClicked(evt);
            }
        });
        jScrollPane2.setViewportView(tblFlota);

        lblTotalFlota.setFont(new java.awt.Font("Segoe UI", 1, 12)); // NOI18N
        lblTotalFlota.setHorizontalAlignment(javax.swing.SwingConstants.LEFT);
        lblTotalFlota.setText("Flota Activa: 0 vehículos");

        lblTotalFlota1.setFont(new java.awt.Font("Segoe UI", 1, 12)); // NOI18N
        lblTotalFlota1.setText("<html><font size='4' color='#0066CC'>■</font> Operación Normal (Cadena de frío estable)</html>");

        lblTotalFlota2.setFont(new java.awt.Font("Segoe UI", 1, 12)); // NOI18N
        lblTotalFlota2.setText("<html><font size='4' color='#FF0000'>■</font> Alerta Crítica (Temperatura &gt; 5°C)</html>");

        lblTotalIncidencias.setFont(new java.awt.Font("Segoe UI", 1, 12)); // NOI18N
        lblTotalIncidencias.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        lblTotalIncidencias.setText("Incidencias: 0 alertas termicas");

        javax.swing.GroupLayout panelLateralLayout = new javax.swing.GroupLayout(panelLateral);
        panelLateral.setLayout(panelLateralLayout);
        panelLateralLayout.setHorizontalGroup(
            panelLateralLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, panelLateralLayout.createSequentialGroup()
                .addGap(0, 0, Short.MAX_VALUE)
                .addComponent(jLabel1, javax.swing.GroupLayout.PREFERRED_SIZE, 124, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(18, 18, 18)
                .addComponent(txtDispositivoGps, javax.swing.GroupLayout.PREFERRED_SIZE, 98, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(53, 53, 53))
            .addGroup(panelLateralLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(panelLateralLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(panelLateralLayout.createSequentialGroup()
                        .addGap(0, 15, Short.MAX_VALUE)
                        .addComponent(jLabel2, javax.swing.GroupLayout.PREFERRED_SIZE, 124, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(192, 192, 192))
                    .addComponent(lblTotalFlota1)
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, panelLateralLayout.createSequentialGroup()
                        .addGroup(panelLateralLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                            .addComponent(lblTotalIncidencias, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(jScrollPane2, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.PREFERRED_SIZE, 0, Short.MAX_VALUE)
                            .addComponent(lblTotalFlota, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                        .addContainerGap())
                    .addComponent(lblTotalFlota2)))
        );
        panelLateralLayout.setVerticalGroup(
            panelLateralLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(panelLateralLayout.createSequentialGroup()
                .addGap(10, 10, 10)
                .addComponent(lblTotalFlota1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(lblTotalFlota2, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jLabel2)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(panelLateralLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel1)
                    .addComponent(txtDispositivoGps, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(18, 18, 18)
                .addComponent(lblTotalFlota, javax.swing.GroupLayout.PREFERRED_SIZE, 16, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jScrollPane2, javax.swing.GroupLayout.PREFERRED_SIZE, 196, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(lblTotalIncidencias)
                .addContainerGap(169, Short.MAX_VALUE))
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
            .addGap(0, 529, Short.MAX_VALUE)
        );

        jLabel3.setFont(new java.awt.Font("Segoe UI", 1, 24)); // NOI18N
        jLabel3.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        jLabel3.setText("PANEL DE LOGÍSTICA EN TIEMPO REAL");

        javax.swing.GroupLayout jPanel1Layout = new javax.swing.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel1Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(jLabel3, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addGroup(jPanel1Layout.createSequentialGroup()
                        .addComponent(panelLateral, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jfxContainer, javax.swing.GroupLayout.PREFERRED_SIZE, 612, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addContainerGap())
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addGap(17, 17, 17)
                .addComponent(jLabel3)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(jfxContainer, javax.swing.GroupLayout.DEFAULT_SIZE, 533, Short.MAX_VALUE)
                    .addComponent(panelLateral, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addGap(15, 15, 15))
        );

        jTabbedPane1.addTab("Logística", jPanel1);

        tblRendimiento.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {
                {null, null, null, null, null, null},
                {null, null, null, null, null, null},
                {null, null, null, null, null, null},
                {null, null, null, null, null, null}
            },
            new String [] {
                "Patente", "Origen", "Destino", "Gasto Combustible (%)", "Alertas Térmicas", "Fecha"
            }
        ));
        jScrollPane1.setViewportView(tblRendimiento);

        jLabel4.setFont(new java.awt.Font("Segoe UI", 1, 18)); // NOI18N
        jLabel4.setText("RENDIMIENTO DE FLOTA");

        tblRutas.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {
                {null, null, null},
                {null, null, null},
                {null, null, null},
                {null, null, null}
            },
            new String [] {
                "Origen", "Destino", "Frecuencia"
            }
        ));
        jScrollPane3.setViewportView(tblRutas);

        jLabel5.setText("Total Alertas Térmicas (Histórico):");

        lblTotalAlertas.setText("0");

        btnActualizar.setText("Actualizar Informes");
        btnActualizar.addActionListener(this::btnActualizarActionPerformed);

        btnExportarPDF.setText("Exportar Reporte a PDF");
        btnExportarPDF.addActionListener(this::btnExportarPDFActionPerformed);

        javax.swing.GroupLayout PanelInformesLayout = new javax.swing.GroupLayout(PanelInformes);
        PanelInformes.setLayout(PanelInformesLayout);
        PanelInformesLayout.setHorizontalGroup(
            PanelInformesLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(PanelInformesLayout.createSequentialGroup()
                .addGroup(PanelInformesLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(PanelInformesLayout.createSequentialGroup()
                        .addContainerGap(632, Short.MAX_VALUE)
                        .addComponent(jScrollPane3, javax.swing.GroupLayout.PREFERRED_SIZE, 333, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(PanelInformesLayout.createSequentialGroup()
                        .addGap(26, 26, 26)
                        .addComponent(jScrollPane1, javax.swing.GroupLayout.PREFERRED_SIZE, 574, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(0, 0, Short.MAX_VALUE)))
                .addContainerGap())
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, PanelInformesLayout.createSequentialGroup()
                .addGap(0, 0, Short.MAX_VALUE)
                .addComponent(jLabel5)
                .addGap(35, 35, 35)
                .addComponent(lblTotalAlertas, javax.swing.GroupLayout.PREFERRED_SIZE, 89, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(26, 26, 26))
            .addGroup(PanelInformesLayout.createSequentialGroup()
                .addGap(20, 20, 20)
                .addComponent(jLabel4)
                .addGap(51, 51, 51)
                .addComponent(btnExportarPDF, javax.swing.GroupLayout.PREFERRED_SIZE, 203, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(btnActualizar, javax.swing.GroupLayout.PREFERRED_SIZE, 227, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(53, 53, 53))
        );
        PanelInformesLayout.setVerticalGroup(
            PanelInformesLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(PanelInformesLayout.createSequentialGroup()
                .addGroup(PanelInformesLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(PanelInformesLayout.createSequentialGroup()
                        .addGap(15, 15, 15)
                        .addComponent(jLabel4))
                    .addGroup(PanelInformesLayout.createSequentialGroup()
                        .addGap(37, 37, 37)
                        .addGroup(PanelInformesLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(btnActualizar)
                            .addComponent(btnExportarPDF))))
                .addGap(87, 87, 87)
                .addGroup(PanelInformesLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel5)
                    .addComponent(lblTotalAlertas))
                .addGap(49, 49, 49)
                .addGroup(PanelInformesLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(jScrollPane3, javax.swing.GroupLayout.DEFAULT_SIZE, 261, Short.MAX_VALUE)
                    .addComponent(jScrollPane1, javax.swing.GroupLayout.PREFERRED_SIZE, 0, Short.MAX_VALUE))
                .addContainerGap(136, Short.MAX_VALUE))
        );

        jTabbedPane1.addTab("Registros", PanelInformes);

        javax.swing.GroupLayout jPanel2Layout = new javax.swing.GroupLayout(jPanel2);
        jPanel2.setLayout(jPanel2Layout);
        jPanel2Layout.setHorizontalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 971, Short.MAX_VALUE)
        );
        jPanel2Layout.setVerticalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 609, Short.MAX_VALUE)
        );

        jTabbedPane1.addTab("tab3", jPanel2);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jTabbedPane1)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addComponent(jTabbedPane1, javax.swing.GroupLayout.PREFERRED_SIZE, 592, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(0, 0, Short.MAX_VALUE))
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void txtDispositivoGpsActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_txtDispositivoGpsActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_txtDispositivoGpsActionPerformed

    private void tblFlotaMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_tblFlotaMouseClicked
        // TODO add your handling code here:
        int fila = tblFlota.getSelectedRow();
        if (fila == -1) return;

        // 1. Extraer la patente (Columna 0) para mover el mapa
        String patenteSeleccionada = tblFlota.getValueAt(fila, 0).toString();

        // 2. Extraer el Número de Serie Real (Columna 1) para el JTextField
        String snSeleccionado = tblFlota.getValueAt(fila, 1).toString();

        // Asignar el S/N verdadero al cajón de texto (Asegúrate de poner el nombre de tu variable JTextField)
        txtDispositivoGps.setText(snSeleccionado);

        // 2. Buscar en la lista de marcadores activos del mapa las coordenadas de esa patente
        for (Waypoint wp : listaWaypoints) {
            if (wp instanceof CamionWaypoint) {
                CamionWaypoint camion = (CamionWaypoint) wp;

                if (camion.getPatente().equalsIgnoreCase(patenteSeleccionada)) {

                    // ASIGNACIÓN CLAVE: Activamos su ID de forma global para que el paintWaypoint dibuje su panel
                    idGpsSeleccionado = camion.getIdGps();

                    // Actualizar tu JTextField lateral si lo sigues ocupando
                    txtDispositivoGps.setText(String.valueOf(camion.getIdGps()));

                    // ENFOQUE NATIVO: Movemos de inmediato la cámara del mapa sobre la posición en vivo del camión
                    mapaViewer.setAddressLocation(camion.getPosition());

                    // Forzar el redibujado para que aparezca la ventanita blanca al instante
                    mapaViewer.repaint();
                    break;
                }
            }
        }
    }//GEN-LAST:event_tblFlotaMouseClicked

    private void btnActualizarActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnActualizarActionPerformed
        // TODO add your handling code here:
        cargarInformesGerenciales();
        javax.swing.JOptionPane.showMessageDialog(this, "Informes actualizados con éxito desde la base de datos.");
    }//GEN-LAST:event_btnActualizarActionPerformed

    private void btnExportarPDFActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnExportarPDFActionPerformed
        // TODO add your handling code here:
        generarReportePDF();
    }//GEN-LAST:event_btnExportarPDFActionPerformed
    
    private void generarReportePDF() {
        javax.swing.JFileChooser fileChooser = new javax.swing.JFileChooser();
        fileChooser.setDialogTitle("Guardar Dashboard Gerencial PDF");
        fileChooser.setSelectedFile(new java.io.File("Dashboard_Logistica_Hirata.pdf")); 
        
        if (fileChooser.showSaveDialog(this) == javax.swing.JFileChooser.APPROVE_OPTION) {
            String rutaFichero = fileChooser.getSelectedFile().getAbsolutePath();
            if (!rutaFichero.toLowerCase().endsWith(".pdf")) rutaFichero += ".pdf";
            
            try {
                // 1. Configurar documento A4 Vertical
                com.itextpdf.text.Document documento = new com.itextpdf.text.Document(com.itextpdf.text.PageSize.A4); 
                com.itextpdf.text.pdf.PdfWriter.getInstance(documento, new java.io.FileOutputStream(rutaFichero));
                documento.open();
                
                // 2. Fuentes Profesionales
                com.itextpdf.text.Font fTitulo = new com.itextpdf.text.Font(com.itextpdf.text.Font.FontFamily.HELVETICA, 20, com.itextpdf.text.Font.BOLD, new com.itextpdf.text.BaseColor(0, 51, 102));
                com.itextpdf.text.Font fSubtitulo = new com.itextpdf.text.Font(com.itextpdf.text.Font.FontFamily.HELVETICA, 13, com.itextpdf.text.Font.BOLD, new com.itextpdf.text.BaseColor(50, 50, 50));
                com.itextpdf.text.Font fKpiVal = new com.itextpdf.text.Font(com.itextpdf.text.Font.FontFamily.HELVETICA, 16, com.itextpdf.text.Font.BOLD, new com.itextpdf.text.BaseColor(0, 102, 204));
                com.itextpdf.text.Font fKpiTit = new com.itextpdf.text.Font(com.itextpdf.text.Font.FontFamily.HELVETICA, 9, com.itextpdf.text.Font.NORMAL, com.itextpdf.text.BaseColor.DARK_GRAY);
                com.itextpdf.text.Font fTablaCabecera = new com.itextpdf.text.Font(com.itextpdf.text.Font.FontFamily.HELVETICA, 10, com.itextpdf.text.Font.BOLD, com.itextpdf.text.BaseColor.WHITE);
                com.itextpdf.text.Font fTablaDato = new com.itextpdf.text.Font(com.itextpdf.text.Font.FontFamily.HELVETICA, 9, com.itextpdf.text.Font.NORMAL);
                
                // 3. Encabezado Oficial
                documento.add(new com.itextpdf.text.Paragraph("DASHBOARD GERENCIAL DE FLOTA", fTitulo));
                documento.add(new com.itextpdf.text.Paragraph("Transportes Hirata S.A. - Análisis de Telemetría IoT", fSubtitulo));
                documento.add(new com.itextpdf.text.Paragraph("Generado el: " + new java.text.SimpleDateFormat("dd/MM/yyyy HH:mm").format(new java.util.Date())));
                documento.add(new com.itextpdf.text.Paragraph("\n"));
                
                // ==========================================================
                // MOTOR DE CÁLCULO DE KPIs (INTELIGENCIA DE NEGOCIOS)
                // ==========================================================
                int totalViajes = tblRendimiento.getRowCount();
                double combustibleGlobal = 0.0;
                int alertasGlobales = 0;
                int vehiculosEnRiesgo = 0;
                
                // Mapa para agrupar estadísticas por patente: [0]Viajes, [1]SumaGas, [2]SumaAlertas
                java.util.Map<String, double[]> statsVehiculos = new java.util.HashMap<>();
                
                for (int i = 0; i < totalViajes; i++) {
                    String patente = tblRendimiento.getValueAt(i, 0).toString();
                    String gasStr = tblRendimiento.getValueAt(i, 3).toString().replace("%", "").replace(",", ".");
                    double gas = Double.parseDouble(gasStr);
                    int alertas = Integer.parseInt(tblRendimiento.getValueAt(i, 4).toString());
                    
                    combustibleGlobal += gas;
                    alertasGlobales += alertas;
                    
                    if (!statsVehiculos.containsKey(patente)) {
                        statsVehiculos.put(patente, new double[]{0, 0, 0});
                    }
                    double[] stats = statsVehiculos.get(patente);
                    stats[0]++;        // +1 Viaje
                    stats[1] += gas;   // Suma Combustible
                    stats[2] += alertas; // Suma Alertas
                }
                
                double promedioGasGlobal = (totalViajes > 0) ? (combustibleGlobal / totalViajes) : 0.0;
                
                // ==========================================================
                // TARJETAS SUPERIORES (KPIs GLOBALES)
                // ==========================================================
                com.itextpdf.text.pdf.PdfPTable tablaKpis = new com.itextpdf.text.pdf.PdfPTable(4);
                tablaKpis.setWidthPercentage(100);
                
                String[] titulosKpi = {"VIAJES TOTALES", "CONSUMO PROM.", "ALERTAS TOTALES", "ÍNDICE DE RIESGO"};
                String[] valoresKpi = {
                    String.valueOf(totalViajes), 
                    String.format("%.1f%%", promedioGasGlobal), 
                    String.valueOf(alertasGlobales),
                    (alertasGlobales > 0) ? "MODERADO" : "ÓPTIMO"
                };
                
                for (int i = 0; i < 4; i++) {
                    com.itextpdf.text.pdf.PdfPCell celdaTit = new com.itextpdf.text.pdf.PdfPCell(new com.itextpdf.text.Phrase(titulosKpi[i], fKpiTit));
                    celdaTit.setBorder(com.itextpdf.text.Rectangle.NO_BORDER);
                    celdaTit.setHorizontalAlignment(com.itextpdf.text.Element.ALIGN_CENTER);
                    tablaKpis.addCell(celdaTit);
                }
                for (int i = 0; i < 4; i++) {
                    com.itextpdf.text.pdf.PdfPCell celdaVal = new com.itextpdf.text.pdf.PdfPCell(new com.itextpdf.text.Phrase(valoresKpi[i], fKpiVal));
                    celdaVal.setBorder(com.itextpdf.text.Rectangle.BOTTOM);
                    celdaVal.setBorderColorBottom(new com.itextpdf.text.BaseColor(200, 200, 200));
                    celdaVal.setPaddingBottom(10f);
                    celdaVal.setHorizontalAlignment(com.itextpdf.text.Element.ALIGN_CENTER);
                    
                    // Colorear rojo si el índice de riesgo es moderado/alto
                    if (i == 3 && alertasGlobales > 0) celdaVal.setPhrase(new com.itextpdf.text.Phrase(valoresKpi[i], new com.itextpdf.text.Font(com.itextpdf.text.Font.FontFamily.HELVETICA, 16, com.itextpdf.text.Font.BOLD, com.itextpdf.text.BaseColor.RED)));
                    
                    tablaKpis.addCell(celdaVal);
                }
                documento.add(tablaKpis);
                documento.add(new com.itextpdf.text.Paragraph("\n\n"));
                
                // ==========================================================
                // SECCIÓN 1: PREDICCIÓN DE MANTENIMIENTO E ÍNDICE DE RIESGO
                // ==========================================================
                documento.add(new com.itextpdf.text.Paragraph("1. Eficiencia y Diagnóstico Predictivo por Vehículo", fSubtitulo));
                documento.add(new com.itextpdf.text.Paragraph("Evaluación cruzada de rendimiento de motor y estabilidad de refrigeración térmica.\n\n", fTablaDato));
                
                com.itextpdf.text.pdf.PdfPTable tablaPredictiva = new com.itextpdf.text.pdf.PdfPTable(5);
                tablaPredictiva.setWidthPercentage(100);
                tablaPredictiva.setWidths(new float[]{1.5f, 1.5f, 1.5f, 1.5f, 2f});
                
                String[] cabecerasDiag = {"Patente", "Consumo Prom.", "Total Alertas", "Índice Riesgo", "Diagnóstico del Sistema"};
                for (String c : cabecerasDiag) {
                    com.itextpdf.text.pdf.PdfPCell celda = new com.itextpdf.text.pdf.PdfPCell(new com.itextpdf.text.Phrase(c, fTablaCabecera));
                    celda.setBackgroundColor(new com.itextpdf.text.BaseColor(0, 102, 204));
                    celda.setHorizontalAlignment(com.itextpdf.text.Element.ALIGN_CENTER);
                    celda.setPadding(6);
                    tablaPredictiva.addCell(celda);
                }
                
                for (java.util.Map.Entry<String, double[]> entry : statsVehiculos.entrySet()) {
                    String patente = entry.getKey();
                    double[] stats = entry.getValue(); // [0]Viajes, [1]Gas, [2]Alertas
                    
                    double promGas = stats[1] / stats[0];
                    double indiceRiesgo = stats[2] / stats[0]; // Alertas por viaje
                    
                    String textoRiesgo = (indiceRiesgo > 0.5) ? "ALTO" : (indiceRiesgo > 0 ? "MEDIO" : "BAJO");
                    String diagnostico = "Operativo";
                    
                    if (stats[2] > 0 || promGas > (promedioGasGlobal + 5.0)) {
                        diagnostico = "Revisión Recomendada";
                        vehiculosEnRiesgo++;
                    }
                    
                    tablaPredictiva.addCell(new com.itextpdf.text.Phrase(patente, fTablaDato));
                    tablaPredictiva.addCell(new com.itextpdf.text.Phrase(String.format("%.1f%%", promGas), fTablaDato));
                    tablaPredictiva.addCell(new com.itextpdf.text.Phrase(String.valueOf((int)stats[2]), fTablaDato));
                    
                    // Colorear índice de riesgo
                    com.itextpdf.text.Font fRiesgo = fTablaDato;
                    if (textoRiesgo.equals("ALTO")) fRiesgo = new com.itextpdf.text.Font(com.itextpdf.text.Font.FontFamily.HELVETICA, 9, com.itextpdf.text.Font.BOLD, com.itextpdf.text.BaseColor.RED);
                    tablaPredictiva.addCell(new com.itextpdf.text.Phrase(textoRiesgo, fRiesgo));
                    
                    // Colorear diagnóstico
                    com.itextpdf.text.Font fDiag = fTablaDato;
                    if (diagnostico.contains("Revisión")) fDiag = new com.itextpdf.text.Font(com.itextpdf.text.Font.FontFamily.HELVETICA, 9, com.itextpdf.text.Font.BOLD, new com.itextpdf.text.BaseColor(204, 102, 0));
                    tablaPredictiva.addCell(new com.itextpdf.text.Phrase(diagnostico, fDiag));
                }
                documento.add(tablaPredictiva);
                documento.add(new com.itextpdf.text.Paragraph("\n\n"));
                
                // ==========================================================
                // SECCIÓN 2: DISTRIBUCIÓN DE RUTAS (PORCENTAJE DE USO)
                // ==========================================================
                documento.add(new com.itextpdf.text.Paragraph("2. Densidad Logística de Rutas", fSubtitulo));
                documento.add(new com.itextpdf.text.Paragraph("Distribución porcentual de los corredores viales más utilizados.\n\n", fTablaDato));
                
                // Calcular total de frecuencias para sacar el porcentaje
                int sumaFrecuencias = 0;
                for (int i = 0; i < tblRutas.getRowCount(); i++) {
                    sumaFrecuencias += Integer.parseInt(tblRutas.getValueAt(i, 2).toString().replace(" viajes", "").trim());
                }
                
                com.itextpdf.text.pdf.PdfPTable tablaDistribucion = new com.itextpdf.text.pdf.PdfPTable(4);
                tablaDistribucion.setWidthPercentage(90);
                tablaDistribucion.setWidths(new float[]{2f, 2f, 1f, 1f});
                
                String[] cabecerasRutas = {"Origen", "Destino", "Viajes", "Porcentaje"};
                for (String c : cabecerasRutas) {
                    com.itextpdf.text.pdf.PdfPCell celda = new com.itextpdf.text.pdf.PdfPCell(new com.itextpdf.text.Phrase(c, fTablaCabecera));
                    celda.setBackgroundColor(new com.itextpdf.text.BaseColor(0, 102, 204));
                    celda.setPadding(6);
                    tablaDistribucion.addCell(celda);
                }
                
                for (int i = 0; i < tblRutas.getRowCount(); i++) {
                    String origen = tblRutas.getValueAt(i, 0).toString();
                    String destino = tblRutas.getValueAt(i, 1).toString();
                    String frecuenciaStr = tblRutas.getValueAt(i, 2).toString().replace(" viajes", "").trim();
                    int frec = Integer.parseInt(frecuenciaStr);
                    double porcentaje = (sumaFrecuencias > 0) ? ((double)frec / sumaFrecuencias) * 100 : 0.0;
                    
                    tablaDistribucion.addCell(new com.itextpdf.text.Phrase(origen, fTablaDato));
                    tablaDistribucion.addCell(new com.itextpdf.text.Phrase(destino, fTablaDato));
                    tablaDistribucion.addCell(new com.itextpdf.text.Phrase(frecuenciaStr, fTablaDato));
                    tablaDistribucion.addCell(new com.itextpdf.text.Phrase(String.format("%.1f%%", porcentaje), new com.itextpdf.text.Font(com.itextpdf.text.Font.FontFamily.HELVETICA, 9, com.itextpdf.text.Font.BOLD)));
                }
                documento.add(tablaDistribucion);
                documento.add(new com.itextpdf.text.Paragraph("\n\n"));
                
                // ==========================================================
                // SECCIÓN 3: BITÁCORA DETALLADA
                // ==========================================================
                documento.add(new com.itextpdf.text.Paragraph("3. Bitácora Cruda de Viajes Consolidada", fSubtitulo));
                documento.add(new com.itextpdf.text.Paragraph(" ", fTablaDato));
                
                com.itextpdf.text.pdf.PdfPTable pdfTablaRendimiento = new com.itextpdf.text.pdf.PdfPTable(tblRendimiento.getColumnCount());
                pdfTablaRendimiento.setWidthPercentage(100);
                pdfTablaRendimiento.setWidths(new float[]{1.5f, 2f, 2f, 1.4f, 1.2f, 1.8f}); 
                
                for (int i = 0; i < tblRendimiento.getColumnCount(); i++) {
                    com.itextpdf.text.pdf.PdfPCell celda = new com.itextpdf.text.pdf.PdfPCell(new com.itextpdf.text.Phrase(tblRendimiento.getColumnName(i), fTablaCabecera));
                    celda.setBackgroundColor(new com.itextpdf.text.BaseColor(102, 102, 102)); 
                    celda.setHorizontalAlignment(com.itextpdf.text.Element.ALIGN_CENTER);
                    celda.setPadding(4);
                    pdfTablaRendimiento.addCell(celda);
                }
                
                for (int i = 0; i < tblRendimiento.getRowCount(); i++) {
                    for (int j = 0; j < tblRendimiento.getColumnCount(); j++) {
                        Object valor = tblRendimiento.getValueAt(i, j);
                        com.itextpdf.text.pdf.PdfPCell c = new com.itextpdf.text.pdf.PdfPCell(new com.itextpdf.text.Phrase(valor != null ? valor.toString() : "", fTablaDato));
                        c.setHorizontalAlignment(com.itextpdf.text.Element.ALIGN_CENTER);
                        pdfTablaRendimiento.addCell(c);
                    }
                }
                documento.add(pdfTablaRendimiento);
                
                // Pie de página
                documento.add(new com.itextpdf.text.Paragraph("\n\n-- Fin del Documento --", fTablaDato));
                documento.close();
                
                javax.swing.JOptionPane.showMessageDialog(this, 
                        "El Dashboard Gerencial en formato A4 se exportó exitosamente.\n" + rutaFichero, 
                        "Reporte Generado", javax.swing.JOptionPane.INFORMATION_MESSAGE);
                
            } catch (Exception e) {
                javax.swing.JOptionPane.showMessageDialog(this, 
                        "Error al compilar la estructura del PDF: " + e.getMessage(), 
                        "Fallo de Escritura", javax.swing.JOptionPane.ERROR_MESSAGE);
            }
        }
    }
    
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
    private javax.swing.JPanel PanelInformes;
    private javax.swing.JButton btnActualizar;
    private javax.swing.JButton btnExportarPDF;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JLabel jLabel5;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JPanel jPanel2;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JScrollPane jScrollPane2;
    private javax.swing.JScrollPane jScrollPane3;
    private javax.swing.JTabbedPane jTabbedPane1;
    private javax.swing.JPanel jfxContainer;
    private javax.swing.JLabel lblTotalAlertas;
    private javax.swing.JLabel lblTotalFlota;
    private javax.swing.JLabel lblTotalFlota1;
    private javax.swing.JLabel lblTotalFlota2;
    private javax.swing.JLabel lblTotalIncidencias;
    private javax.swing.JPanel panelLateral;
    private javax.swing.JTable tblFlota;
    private javax.swing.JTable tblRendimiento;
    private javax.swing.JTable tblRutas;
    private javax.swing.JTextField txtDispositivoGps;
    // End of variables declaration//GEN-END:variables
    private final java.util.Set<org.jxmapviewer.viewer.Waypoint> listaWaypoints = new java.util.HashSet<>();
}
