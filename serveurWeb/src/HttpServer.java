import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.TreeMap;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

public class HttpServer {
    public static void main(String[] args) throws IOException {

        // gestion message serveur
        String Erreur404 = "HTTP/1.1 404 Not Found\nContent-Type: text/plain; charset=UTF-8\n\nErreur 404 Not Found\nFichier non trouvé\n"; // Message d'erreur 404 à envoyer au client lorsque le fichier demandé n'est pas trouvé

      

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
        
        // 3. Récupération de tous les éléments <utilisateur>
        NodeList siteNodeList = document.getElementsByTagName("site");

        // on créer les liste pour stocker les différente éléments du fichier config
        TreeMap<String, String> gestionSite = new TreeMap<String, String>();
        
       for (int i = 0; i < siteNodeList.getLength(); i++) {
            Element site = (Element) siteNodeList.item(i); // on prend un site
            gestionSite.put(site.getElementsByTagName("port").item(0).getTextContent(), site.getElementsByTagName("DocumentRoot").item(0).getTextContent());
  
       }

       // fin lecture du fichier config //

       // gestion des variables du serveur
        ArrayList<String> ports = new ArrayList<String>(gestionSite.keySet());
       
        Thread thread = new Thread(() -> {
            try {
                int port = Integer.parseInt(ports.get(0));
                ServerSocket serverSocket = new ServerSocket(port);
                System.out.println("Serveur démarré sur le port " + port);

                Socket socket = serverSocket.accept();
                // Afficher un message indiquant qu'une connexion a été acceptée
                System.out.println("Connexion acceptée de " + socket.getInetAddress());


                

            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        thread.start();

        Thread thread2 = new Thread(() -> {
            try {
                int port = Integer.parseInt(ports.get(1));
                ServerSocket serverSocket = new ServerSocket(port);
                System.out.println("Serveur démarré sur le port " + port);

                Socket socket = serverSocket.accept();
                // Afficher un message indiquant qu'une connexion a été acceptée
                System.out.println("Connexion acceptée de " + socket.getInetAddress());


                

            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        thread.start();
    }
}

