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

    public static void chargerPage(String pathRoot, int port, String pathDefault) throws IOException {

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

            String cheminFichierFinal;

            // Afficher la ligne de tête de la requête HTTP
            System.out.println(head);
            // Extraire le chemin du fichier demandé à partir de la ligne de tête de la
            // requête HTTP
            String[] tabString = head.split(" ");
            String path = tabString[1];

            System.out.println(path + "  iciiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiii");
            System.out.println(pathRoot);

            // Si le chemin est "/", servir le fichier "index.html"
            if (path.equals("/") && pathDefault != null) {
                cheminFichierFinal = pathRoot + path + "html/" + pathDefault;
            } else {
                cheminFichierFinal = pathRoot + path + "html/index.html";
            }

            System.out.println(
                    cheminFichierFinal + "iciiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiii");

            // Lire les lignes d'en-tête de la requête HTTP
            String headerLine;
            while ((headerLine = clientInput.readLine()) != null && !headerLine.isEmpty()) {
                System.out.println(headerLine);
            }

            // Vérifier si le fichier demandé existe dans le système de fichiers
            File file = new File(cheminFichierFinal);
            if (file.exists()) {
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
                    sortie.println(Erreur404 + path);
                }
            } else {
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