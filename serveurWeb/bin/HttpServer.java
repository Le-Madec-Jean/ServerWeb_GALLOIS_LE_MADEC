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

    public static synchronized void logToFile(String logFilePath, String message) {
        if (logFilePath == null || logFilePath.isEmpty()) return;
        try {
            File file = new File(logFilePath);
            if (file.getParentFile() != null) file.getParentFile().mkdirs();
            java.io.PrintWriter out = new java.io.PrintWriter(new java.io.FileWriter(file, true));
            out.println(new java.util.Date().toString() + " - " + message);
            out.close();
        } catch (IOException e) {
            System.out.println("Erreur d'écriture dans le log : " + e.getMessage());
        }
    }

    public static void chargerPage(String pathRoot, int port, String pathDefault, String accessLogPath, String errorLogPath) throws IOException {

        // gestion message serveur
        String Erreur404 = "HTTP/1.1 404 Not Found\nContent-Type: text/plain; charset=UTF-8\n\nErreur 404 Not Found\nFichier non trouvé\n";

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
                    processCount--; // Enlever l'en-tête de `ps`
                } catch (Exception e) {}

                String html = "<html><head><meta charset=\"UTF-8\"><title>Etat de la machine</title></head><body>"
                            + "<h1>Etat de la machine</h1><ul>"
                            + "<li>Mémoire JVM disponible : " + (freeMem / 1024 / 1024) + " Mo</li>"
                            + "<li>Espace disque disponible (/) : " + (freeDisk / 1024 / 1024 / 1024) + " Go</li>"
                            + "<li>Nombre de processus Linux : " + processCount + "</li>"
                            + "</ul></body></html>";

                byte[] bytes = html.getBytes("UTF-8");
                sortie.println("HTTP/1.1 200 OK\nContent-Type: text/html; charset=UTF-8\nContent-Length: " + bytes.length + "\n");
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

            System.out.println(
                    cheminFichierFinal + "iciiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiii");

            // Lire les lignes d'en-tête de la requête HTTP
            String headerLine;
            while ((headerLine = clientInput.readLine()) != null && !headerLine.isEmpty()) {
                System.out.println(headerLine);
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
                        String parentPath = path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
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
                            String hrefPath = path.endsWith("/") ? path + name : path + "/" + name;
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
                        FileInputStream fi = new FileInputStream(file); // crée un flux d'entrée pour lire le contenu

                        sortie.println("HTTP/1.1 200 OK\n Content-Length: " + file.length() + "\n");

                        byte[] buffer = new byte[1024];
                        int bytesRead;
                        // Lire le contenu du fichier dans le tampon
                        while ((bytesRead = fi.read(buffer)) != -1) {
                            outputStream.write(buffer, 0, bytesRead);
                        }

                        // Vider le tampon du flux de sortie
                        outputStream.flush();
                        fi.close();

                    } catch (FileNotFoundException e) {
                        logToFile(errorLogPath, Erreur404 + path + " (IP: " + socket.getInetAddress() + ")");
                        sortie.println(Erreur404 + path);
                    }
                }
            } else {
                logToFile(errorLogPath, Erreur404 + " (IP: " + socket.getInetAddress() + ")");

                sortie.println(Erreur404);
            }
            socket.close();
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

            gestionSite.put(site.getElementsByTagName("port").item(0).getTextContent(),
                    config);

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
                    }

                    String pathAcces = gestionSite.get(clefPort).get(2).getTextContent();
                    String pathError = gestionSite.get(clefPort).get(3).getTextContent();

                    System.err.println(gestionSite.get(clefPort).get(1) + " " + port);
                    System.out.println(pathDelfaut);

                    chargerPage(pathRoot, port, pathDelfaut);


                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
            thread.start();
        }
    }
}