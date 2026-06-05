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

public class HttpServer {
    public static void main(String[] args) throws IOException {

        String Erreur404 = "HTTP/1.1 404 Not Found\nContent-Type: text/plain; charset=UTF-8\n\nErreur 404 Not Found\nFichier non trouvé\n"; // Message d'erreur 404 à envoyer au client lorsque le fichier demandé n'est pas trouvé

        // Afficher le nom de la machine et son adresse IP
        InetAddress ip = InetAddress.getLocalHost();
        System.out.println("Nom de la machine : " + ip.getHostName() + "\nAdresse IP : " + ip.getHostAddress());

        // Définir le port d'écoute (par défaut 8080)
        int port = (args.length > 0) ? Integer.parseInt(args[0]) : 8080;

        // Créer un ServerSocket pour écouter les connexions entrantes
        ServerSocket serverSocket = new ServerSocket(port);
        // Afficher un message indiquant que le serveur est démarré
        System.out.println("Serveur démarré sur le port " + port);

        // Boucle infinie pour accepter les connexions entrantes
        while (true) {
            // Accepter une connexion entrante
            Socket socket = serverSocket.accept();
            // Afficher un message indiquant qu'une connexion a été acceptée
            System.out.println("Connexion acceptée de " + socket.getInetAddress());

            // Créer des flux pour lire la requête du client et envoyer la réponse
            BufferedReader clientInput = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            
            OutputStream outputStream = socket.getOutputStream(); // obtenir le flux de sortie pour envoyer la réponse au client
            PrintWriter sortie = new PrintWriter(outputStream, true); // sert à envoyer des données textuelles au client de manière efficace en utilisant un tampon et en assurant que les données sont envoyées immédiatement grâce au second argument "true"

            // Lire la première ligne de la requête HTTP (la ligne de tête)
            String head = clientInput.readLine();
            //  Si la ligne de tête est nulle, fermer la connexion et continuer à attendre les prochaines connexions
            if (head == null) {
                socket.close();
                continue; // Passer à la prochaine itération de la boucle pour attendre une nouvelle connexion
            }

            // Afficher la ligne de tête de la requête HTTP
            System.out.println(head);
            // Extraire le chemin du fichier demandé à partir de la ligne de tête de la requête HTTP
            String[] tabString = head.split(" "); 
            String path = tabString[1];

            // Si le chemin est "/", servir le fichier "index.html"
            if (path.equals("/")) {
                path = "index.html";
            } else if (path.startsWith("/")) { // Si le chemin commence par "/", supprimer le "/" initial pour obtenir le nom du fichier demandé
                path = path.substring(1);
            }

            // Lire les lignes d'en-tête de la requête HTTP jusqu'à ce qu'une ligne vide soit rencontrée, indiquant la fin des en-têtes
            String headerLine;
            while ((headerLine = clientInput.readLine()) != null && !headerLine.isEmpty()) {
                // Afficher chaque ligne d'en-tête de la requête HTTP
                System.out.println(headerLine);
            }

            // Vérifier si le fichier demandé existe dans le système de fichiers
            File file = new File(path);
            if (file.exists()) {
                
                try { // Si le fichier existe :
                    FileInputStream fi = new FileInputStream(file); // crée un flux d'entrée pour lire le contenu du fichier demandé

                    sortie.println("HTTP/1.1 200 OK\n Content-Length: " + file.length()+"\n"); // Envoyer la longueur du contenu du fichier au client pour qu'il puisse gérer correctement la réception des données
                    byte[] buffer = new byte[1024]; // tampon de 1024 octets pour lire le contenu du fichier en morceaux
                    int bytesRead;
                    // Lire le contenu du fichier dans le tampon jusqu'à la fin du fichier (indiquée par -1)
                    while ((bytesRead = fi.read(buffer)) != -1) { 
                        outputStream.write(buffer, 0, bytesRead); // écrire les données lues du fichier dans le flux de sortie pour les envoyer au client
                    }
                    
                    // Vider le tampon du flux de sortie pour s'assurer que toutes les données sont envoyées au client
                    outputStream.flush();
                    fi.close();

                } catch (FileNotFoundException e) { // Si le fichier n'est pas trouvé (ce qui est peu probable car nous avons déjà vérifié son existence), envoyer un message d'erreur 404 au client
                    sortie.println(Erreur404 + path); // Envoyer un message d'erreur indiquant que le fichier demandé n'a pas été trouvé
                }
            } else {
                sortie.println(Erreur404); // Envoyer une réponse HTTP 404 Not Found au client pour indiquer que le fichier demandé n'a pas été trouvé
            }
            socket.close(); // Fermer la connexion avec le client après avoir traité la requête
        }
    }
}