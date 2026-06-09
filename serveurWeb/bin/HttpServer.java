import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.TreeMap;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

public class HttpServer {

    private static String formaterDate(long timestamp) {
        return DateTimeFormatter.RFC_1123_DATE_TIME
                .withZone(ZoneId.of("GMT"))
                .format(Instant.ofEpochMilli(timestamp));
    }

    public static synchronized void logToFile(String logFilePath, String message) {
        if (logFilePath == null || logFilePath.isEmpty())
            return;
        try {
            File file = new File(logFilePath);
            if (file.getParentFile() != null)
                file.getParentFile().mkdirs();
            java.io.PrintWriter out = new java.io.PrintWriter(new java.io.FileWriter(file, true));
            out.println(new java.util.Date().toString() + " - " + message);
            out.close();
        } catch (IOException e) {
            System.out.println("Erreur d'écriture dans le log : " + e.getMessage());
        }
    }

    public static void chargerPage(String pathRoot, int port, String pathDefault, String accessLogPath,
            String errorLogPath) throws IOException {

        // gestion message serveur
        String html404 = "<html><head><title>404 Not Found</title><meta charset=\"UTF-8\"><style>body{font-family: sans-serif; text-align: center; margin-top: 50px;}</style></head><body><h1>404 - Fichier non trouvé</h1><p>La ressource demandée n'existe pas sur ce serveur.</p><hr><p><i>Serveur Web Java SAE</i></p></body></html>";
        String reponse404 = "HTTP/1.1 404 Not Found\r\nContent-Type: text/html; charset=UTF-8\r\nContent-Length: " + html404.getBytes("UTF-8").length + "\r\n\r\n" + html404;

        String html403 = "<html><head><title>403 Forbidden</title><meta charset=\"UTF-8\"><style>body{font-family: sans-serif; text-align: center; margin-top: 50px;}</style></head><body><h1>403 - Accès Interdit</h1><p>Vous n'avez pas l'autorisation d'accéder à cette ressource pour des raisons de sécurité.</p><hr><p><i>Serveur Web Java SAE</i></p></body></html>";
        String reponse403 = "HTTP/1.1 403 Forbidden\r\nContent-Type: text/html; charset=UTF-8\r\nContent-Length: " + html403.getBytes("UTF-8").length + "\r\n\r\n" + html403;

        ServerSocket serverSocket = new ServerSocket(port);
        System.out.println("Serveur démarré sur le port " + port);

        // Boucle infinie pour accepter les connexions entrantes
        while (true) {
            // Accepter une connexion entrante
            Socket socket = serverSocket.accept();
            // Afficher un message indiquant qu'une connexion a été acceptée
            System.out.println("Connexion acceptée de " + socket.getInetAddress());

            // Créer des flux pour lire la requête du client et envoyer la réponse
            BufferedReader clientInput = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            OutputStream outputStream = socket.getOutputStream(); // obtenir le flux de sortie pour envoyer la réponse
                                                                  // au client
            PrintWriter sortie = new PrintWriter(outputStream, true); // sert à envoyer des données textuelles au client

            // Lire la première ligne de la rsequête HTTP (la ligne de tête)
            String head = clientInput.readLine();
            // Si la ligne de tête est nulle, fermer la connexion et continuer à attendre
            if (head == null) {
                socket.close();
                continue;
            }

            // Afficher la ligne de tête de la requête HTTP
            System.out.println(head);
            // Extraire le chemin du fichier demandé à partir de la ligne de tête de la
            // requête HTTP
            String[] tabString = head.split(" ");
            String path = tabString[1];

            // Sécurité : on empêche de remonter dans les dossiers avec ".." (Faille de Path Traversal)
            if (path.contains("..")) {
                logToFile(errorLogPath, "403 Forbidden (Securité) - IP: " + socket.getInetAddress() + " | Path: " + path);
                sortie.print(reponse403);
                sortie.flush();
                socket.close();
                continue;
            }

            // log
            logToFile(accessLogPath, "IP: " + socket.getInetAddress() + " | Requete: " + head);

            // etat de la machine
            if (path.equals("/status")) {
                long freeMem = Runtime.getRuntime().freeMemory();
                long freeDisk = new File("/").getFreeSpace();
                int processCount = 0;
                try {
                    Process p = Runtime.getRuntime().exec("ps -e");
                    BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
                    while (br.readLine() != null) processCount++;
                    processCount--; // Enlever l'en-tête de 'ps'
                } catch (Exception e) {}


                String html = "<html><head><meta charset=\"UTF-8\"><title>Etat de la machine</title></head><body>"
                        + "<h1>Etat de la machine</h1><ul>"
                        + "<li>Mémoire JVM disponible : " + (freeMem / 1024 / 1024) + " Mo</li>"
                        + "<li>Espace disque disponible (/) : " + (freeDisk / 1024 / 1024 / 1024) + " Go</li>"
                        + "<li>Nombre de processus Linux : " + processCount + "</li>"
                        + "</ul></body></html>";

                byte[] bytes = html.getBytes("UTF-8");
                sortie.println("HTTP/1.1 200 OK\nContent-Type: text/html; charset=UTF-8\nContent-Length: "
                        + bytes.length + "\n");
                outputStream.write(bytes);
                outputStream.flush();
                socket.close();
                continue;
            }

            String cheminFichierFinal = pathRoot + path;
            File file = new File(cheminFichierFinal);

            // Si c'est un dossier, on essaie d'abord d'y trouver le fichier par défaut
            if (file.exists() && file.isDirectory() && !pathDefault.isEmpty()) {
                File defaultFile = new File(file, pathDefault);
                if (defaultFile.exists()) {
                    file = defaultFile;
                }

            }

           
            // Lire les lignes d'en-tête de la requête HTTP
            String headerLine;
            String etagClient = null; // Contiendra le If-None-Match
            String dateClient = null; // Contiendra le If-Modified-Since
            while ((headerLine = clientInput.readLine()) != null && !headerLine.isEmpty()) {
                System.out.println(headerLine);

                

                // On passe tout en minuscules pour éviter les problèmes de casse
                String ligneMinuscule = headerLine.toLowerCase();

                // Vraie syntaxe Java : startsWith et substring
                if (ligneMinuscule.startsWith("if-none-match:")) {
                    etagClient = headerLine.substring(headerLine.indexOf(":") + 1).trim();
                } else if (ligneMinuscule.startsWith("if-modified-since:")) {
                    dateClient = headerLine.substring(headerLine.indexOf(":") + 1).trim();
                }
            }

            // Vérifier si le fichier demandé existe dans le système de fichiers
            if (file.exists()) {
                if (file.isDirectory()) {
                    // Afficher la liste des fichiers et dossiers
                    StringBuilder html = new StringBuilder();
                    html.append("<html><head><title>Index of ").append(path)
                            .append("</title><meta charset=\"UTF-8\"></head><body>\n");
                    html.append("<h1>Index of ").append(path).append("</h1><hr><pre>\n");

                    // Lien parent si on n'est pas à la racine
                    if (!path.equals("/")) {
                        String parentPath;
                        if (path.endsWith("/")) {
                            parentPath = path.substring(0, path.length() - 1);
                        } else {
                            parentPath = path;
                        }

                        int lastSlash = parentPath.lastIndexOf("/");
                        if (lastSlash >= 0) {
                            parentPath = parentPath.substring(0, lastSlash + 1);
                            html.append("<a href=\"").append(parentPath).append("\">../</a>\n");
                        }
                    }

                    File[] list = file.listFiles();
                    if (list != null) {
                        for (File f : list) {
                            String name = f.getName();
                            if (f.isDirectory()) {
                                name += "/";
                            }
                            String hrefPath;
                            if (path.endsWith("/")) {
                                hrefPath = path + name;
                            } else {
                                hrefPath = path + "/" + name;
                            }
                            html.append("<a href=\"").append(hrefPath).append("\">").append(name).append("</a>\n");
                        }
                    }
                    html.append("</pre><hr></body></html>\n");

                    String reponse = html.toString();
                    byte[] reponseBytes = reponse.getBytes("UTF-8");
                    sortie.println("HTTP/1.1 200 OK\nContent-Type: text/html; charset=UTF-8\nContent-Length: "
                            + reponseBytes.length + "\n");
                    outputStream.write(reponseBytes);
                    outputStream.flush();
                } else {
                    try { // Si le fichier existe :
                        String fileName = file.getName().toLowerCase();
                        String contentType = "application/octet-stream";
                        
                        // Détermination du Content-Type basique
                        if (fileName.endsWith(".html") || fileName.endsWith(".htm")) contentType = "text/html; charset=UTF-8";
                        else if (fileName.endsWith(".css")) contentType = "text/css";
                        else if (fileName.endsWith(".js")) contentType = "application/javascript";
                        else if (fileName.endsWith(".png")) contentType = "image/png";
                        else if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg")) contentType = "image/jpeg";
                        else if (fileName.endsWith(".pdf")) contentType = "application/pdf";
                        else if (fileName.endsWith(".gif")) contentType = "image/gif";
                        // On calcule le MD5 du fichier tel qu'il est sur le disque MAINTENANT
                        String etagServeur = calculerMD5(file);

                        // On récupère la date de modification du fichier MAINTENANT
                        String dateServeur = formaterDate(file.lastModified());
                        boolean leClientEstAJour = false;

                        if (etagClient != null && etagClient.equals(etagServeur)) {
                            leClientEstAJour = true;
                        } else if (dateClient != null && dateClient.equals(dateServeur)) {
                            leClientEstAJour = true;
                        }

                        if (leClientEstAJour) {
                            String dateActuelle = formaterDate(System.currentTimeMillis()); // Heure de la réponse
                            
                            // Attention : on utilise print() et \r\n pour la conformité HTTP
                            sortie.print("HTTP/1.1 304 Not Modified\r\n");
                            sortie.print("Date: " + dateActuelle + "\r\n");
                            sortie.print("Server: Gallois /1.0\r\n");
                            sortie.print("ETag: " + etagServeur + "\r\n");
                            sortie.print("Last-Modified: " + dateServeur + "\r\n");
                            sortie.print("\r\n"); // Ligne vide cruciale
                            sortie.flush();

                            logToFile(accessLogPath, "IP: " + socket.getInetAddress() + " | Cache validé (304) pour " + path);
                        } else {
                            String dateActuelle = formaterDate(System.currentTimeMillis()); // Heure de la réponse
                            
                            // On compresse TOUT en gzip maintenant, comme demandé !
                            boolean compressGzip = true;

                            FileInputStream fi = new FileInputStream(file); // crée un flux d'entrée pour lire le contenu

                            if (compressGzip) {
                                // Compresser le fichier en gzip en mémoire
                                java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                                java.util.zip.GZIPOutputStream gzipOut = new java.util.zip.GZIPOutputStream(baos);
                                
                                byte[] buffer = new byte[1024];
                                int bytesRead;
                                while ((bytesRead = fi.read(buffer)) != -1) {
                                    gzipOut.write(buffer, 0, bytesRead);
                                }
                                gzipOut.close();
                                
                                byte[] gzipData = baos.toByteArray();
                                
                                // Envoyer les bons en-têtes (Headers)
                                sortie.print("HTTP/1.1 200 OK\r\n");
                                sortie.print("Date: " + dateActuelle + "\r\n");
                                sortie.print("Server: Gallois /1.0\r\n");
                                sortie.print("Content-Type: " + contentType + "\r\n");
                                sortie.print("Content-Encoding: gzip\r\n");
                                sortie.print("ETag: " + etagServeur + "\r\n");
                                sortie.print("Last-Modified: " + dateServeur + "\r\n");
                                sortie.print("Content-Length: " + gzipData.length + "\r\n\r\n");// \r\n\"\r\n" sert à indiquer la fin des en-têtes HTTP
                                sortie.flush();
                                
                                // Envoyer le contenu compressé
                                outputStream.write(gzipData);
                            } else {
                                // Envoi normal sans compression
                                sortie.print("HTTP/1.1 200 OK\r\n");
                                sortie.print("Date: " + dateActuelle + "\r\n");
                                sortie.print("Server: Gallois /1.0\r\n");
                                sortie.print("Content-Type: " + contentType + "\r\n");
                                sortie.print("ETag: " + etagServeur + "\r\n");
                                sortie.print("Last-Modified: " + dateServeur + "\r\n");
                                sortie.print("Content-Length: " + file.length() + "\r\n");
                                sortie.print("\r\n");
                                sortie.flush();

                                byte[] buffer = new byte[1024];
                                int bytesRead;
                                while ((bytesRead = fi.read(buffer)) != -1) {
                                    outputStream.write(buffer, 0, bytesRead);
                                }
                            }

                            // Vider le tampon du flux de sortie
                            outputStream.flush();
                            fi.close();
                        }
                    } catch (FileNotFoundException e) {
                        logToFile(errorLogPath, "404 Not Found - Path: " + path + " (IP: " + socket.getInetAddress() + ")");
                        sortie.print(reponse404);
                        sortie.flush();
                    }
                }
            } else {
                logToFile(errorLogPath, "404 Not Found - Path: " + path + " (IP: " + socket.getInetAddress() + ")");
                sortie.print(reponse404);
                sortie.flush();
            }
            socket.close();
        }
    }

    private static String calculerMD5(File fichier) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            FileInputStream fis = new FileInputStream(fichier);
            byte[] buffer = new byte[1024];
            int nbLectures;

            while ((nbLectures = fis.read(buffer)) != -1) {
                digest.update(buffer, 0, nbLectures);
            }
            fis.close();

            // Conversion des octets en chaîne hexadécimale
            byte[] bytes = digest.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return "\"" + sb.toString() + "\""; // L'ETag doit être entouré de guillemets
        } catch (Exception e) {
            return null;
        }
    }

    public static void main(String[] args) throws IOException {

        // lecture du fichier config //
        // 1. Préparation du parseur
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = null;
        try {
            builder = factory.newDocumentBuilder();
        } catch (ParserConfigurationException e) {
            e.printStackTrace();
        }

        // 2. Chargement du document en mémoire
        Document document = null;
        try {
            document = builder.parse(new File("../conf.d/serverWeb.conf"));
        } catch (SAXException | IOException e) {
            e.printStackTrace();
        }

        // 3. Récupération de tous les éléments <site>
        NodeList siteNodeList = document.getElementsByTagName("site");

        // on crée la liste pour stocker les différents éléments du fichier config

        TreeMap<String, ArrayList<Node>> gestionSite = new TreeMap<String, ArrayList<Node>>();

        for (int i = 0; i < siteNodeList.getLength(); i++) {
            ArrayList<Node> config = new ArrayList<Node>();
            Element site = (Element) siteNodeList.item(i);// on prend un site
            config.add(site.getElementsByTagName("DocumentRoot").item(0));
            config.add(site.getElementsByTagName("DefaultIndex").item(0));
            config.add(site.getElementsByTagName("Acceslog").item(0));
            config.add(site.getElementsByTagName("Errorlog").item(0));

            gestionSite.put(site.getElementsByTagName("port").item(0).getTextContent(), config);

        }
        // fin lecture du fichier config //

        // gestion des variables du serveur
        // On crée une liste "ports" à partir des clés de la map "gestionSite"
        ArrayList<String> ports = new ArrayList<String>(gestionSite.keySet());

        // On parcourt chaque élément de la liste "ports"
        for (String clefPort : ports) {

            // On crée un nouveau Thread à chaque tour de boucle
            Thread thread = new Thread(() -> {
                try {
                    int port = Integer.parseInt(clefPort);

                    String pathRoot = gestionSite.get(clefPort).get(0).getTextContent();
                    String pathDelfaut = null;
                    if (gestionSite.get(clefPort).get(1) != null) {
                        pathDelfaut = gestionSite.get(clefPort).get(1).getTextContent();
                    } else {
                        pathDelfaut = "index.html";
                    }

                    String pathAcces = gestionSite.get(clefPort).get(2).getTextContent();
                    String pathError = gestionSite.get(clefPort).get(3).getTextContent();

                    System.err.println(gestionSite.get(clefPort).get(1) + " " + port);
                    System.out.println(pathDelfaut);

                    chargerPage(pathRoot, port, pathDelfaut, pathAcces, pathError);

                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
            thread.start();
        }
    }
}