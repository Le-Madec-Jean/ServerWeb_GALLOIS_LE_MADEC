import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;
import java.util.ArrayList;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPOutputStream;
import java.lang.management.ManagementFactory;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Serveur Web configurable - SAE 2.03
 * Auteurs : GALLOIS Pierre, LE MADEC Jean
 */
public class HttpServer {

    //  MÉTHODES UTILITAIRES 

    // Formate une date en format HTTP (RFC-1123)
    // Ex : "Tue, 10 Jun 2025 12:00:00 GMT"
    private static String formaterDate(long timestamp) {
        SimpleDateFormat sdf = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z");
        sdf.setTimeZone(TimeZone.getTimeZone("GMT"));
        return sdf.format(new Date(timestamp));
    }

    // Écrit une ligne dans un fichier de log
    public static void logToFile(String cheminLog, String message) {
        if (cheminLog == null || cheminLog.isEmpty()) return;
        try {
            File f = new File(cheminLog);
            if (f.getParentFile() != null) f.getParentFile().mkdirs();
            PrintWriter out = new PrintWriter(new FileWriter(f, true));
            out.println(new java.util.Date() + " - " + message);
            out.close();
        } catch (IOException e) {
            System.out.println("Erreur log : " + e.getMessage());
        }
    }

    // Calcule le MD5 d'un fichier (utilisé pour l'ETag)
    private static String calculerMD5(File fichier) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            FileInputStream fis = new FileInputStream(fichier);
            byte[] buffer = new byte[1024];
            int nbLus;
            while ((nbLus = fis.read(buffer)) != -1) {
                digest.update(buffer, 0, nbLus);
            }
            fis.close();
            byte[] bytes = digest.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return "\"" + sb.toString() + "\""; // L'ETag doit être entre guillemets
        } catch (Exception e) {
            return null;
        }
    }

    // Retourne le Content-Type selon l'extension du fichier
    private static String getContentType(String nomFichier) {
        String nom = nomFichier.toLowerCase();
        if (nom.endsWith(".html") || nom.endsWith(".htm")) return "text/html; charset=UTF-8";
        if (nom.endsWith(".css"))   return "text/css; charset=UTF-8";
        if (nom.endsWith(".js"))    return "application/javascript";
        if (nom.endsWith(".png"))   return "image/png";
        if (nom.endsWith(".jpg") || nom.endsWith(".jpeg")) return "image/jpeg";
        if (nom.endsWith(".gif"))   return "image/gif";
        if (nom.endsWith(".pdf"))   return "application/pdf";
        if (nom.endsWith(".txt"))   return "text/plain; charset=UTF-8";
        return "application/octet-stream";
    }

    // Retourne true si le fichier doit être envoyé en gzip (images et PDF)
    private static boolean doitEtreGzippe(String nomFichier) {
        String nom = nomFichier.toLowerCase();
        return nom.endsWith(".png") || nom.endsWith(".jpg") || nom.endsWith(".jpeg")
            || nom.endsWith(".gif") || nom.endsWith(".pdf");
    }

    // Compresse des octets en gzip et retourne le résultat
    private static byte[] compresserGzip(byte[] donnees) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        GZIPOutputStream gzip = new GZIPOutputStream(baos);
        gzip.write(donnees);
        gzip.close();
        return baos.toByteArray();
    }

    // Exécute un script avec un interpréteur et retourne sa sortie
    private static String executerScript(String interpreteur, String code) {
        try {
            ProcessBuilder pb = new ProcessBuilder(interpreteur, "-c", code);
            Process processus = pb.start();
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(processus.getInputStream(), "UTF-8"));
            StringBuilder sortie = new StringBuilder();
            String ligne;
            while ((ligne = reader.readLine()) != null) {
                sortie.append(ligne).append("\n");
            }
            reader.close();
            processus.waitFor();
            return sortie.toString().trim();
        } catch (Exception e) {
            return "[Erreur : " + e.getMessage() + "]";
        }
    }

    // Lit un fichier HTML, exécute les balises <code interpreteur="...">...</code>
    // et retourne la page modifiée en octets
    private static byte[] traiterHtmlDynamique(File fichier) throws IOException {
        // On lit tout le fichier HTML en mémoire
        String texte = new String(Files.readAllBytes(fichier.toPath()), "UTF-8");

        // Regex pour trouver les balises <code interpreteur="...">...</code>
        // Gère les guillemets " ' « »
        Pattern pattern = Pattern.compile(
                "<code[\\s]+interpreteur=[«\"']?([^»\"'\\s>]+)[»\"']?[^>]*>(.*?)</code>",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
        Matcher matcher = pattern.matcher(texte);
        StringBuffer resultat = new StringBuffer();

        while (matcher.find()) {
            String interpreteur = matcher.group(1).trim(); // ex: /bin/bash
            String code = matcher.group(2).trim();         // le script à exécuter
            String sortie = executerScript(interpreteur, code);
            matcher.appendReplacement(resultat, Matcher.quoteReplacement(sortie));
        }
        matcher.appendTail(resultat);
        return resultat.toString().getBytes("UTF-8");
    }

    //  GESTION DES REQUÊTES

    // Lance le serveur sur un port et répond aux requêtes HTTP en boucle
    public static void chargerPage(String pathRoot, int port, String pathDefault,
                                   String accessLogPath, String errorLogPath) throws IOException {

        String erreur404 = "HTTP/1.1 404 Not Found\r\nContent-Type: text/plain; charset=UTF-8\r\n\r\n404 Not Found\n";
        String erreur403 = "HTTP/1.1 403 Forbidden\r\nContent-Type: text/plain; charset=UTF-8\r\n\r\n403 Forbidden\n";

        ServerSocket serverSocket = new ServerSocket(port);
        System.out.println("Serveur démarré sur le port " + port + " (chemin : " + pathRoot + ")");

        while (true) {
            Socket socket = serverSocket.accept();
            System.out.println("[Port " + port + "] Connexion de " + socket.getInetAddress());

            // Flux pour lire la requête et écrire la réponse
            BufferedReader entree = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            OutputStream sortieOctet = socket.getOutputStream();
            PrintWriter sortieTexte = new PrintWriter(sortieOctet, false);

            // Lire la première ligne de la requête (ex: "GET /index.html HTTP/1.1")
            String ligneRequete = entree.readLine();
            if (ligneRequete == null || ligneRequete.isEmpty()) {
                socket.close();
                continue;
            }

            System.out.println("[Port " + port + "] " + ligneRequete);
            logToFile(accessLogPath, "IP: " + socket.getInetAddress() + " | " + ligneRequete);

            // Extraire le chemin demandé (ex: /index.html)
            String[] parties = ligneRequete.split(" ");
            if (parties.length < 2) {
                socket.close();
                continue;
            }
            String cheminDemande = parties[1];

            // Lire les en-têtes HTTP envoyés par le navigateur
            String etagClient = null;
            String dateModifClient = null;
            boolean accepteGzip = false;
            String ligneEntete;
            while ((ligneEntete = entree.readLine()) != null && !ligneEntete.isEmpty()) {
                String lower = ligneEntete.toLowerCase();
                if (lower.startsWith("if-none-match:"))
                    etagClient = ligneEntete.substring(ligneEntete.indexOf(':') + 1).trim();
                else if (lower.startsWith("if-modified-since:"))
                    dateModifClient = ligneEntete.substring(ligneEntete.indexOf(':') + 1).trim();
                else if (lower.startsWith("accept-encoding:") && lower.contains("gzip"))
                    accepteGzip = true;
            }

            // --- Page /status : état de la machine ---
            if (cheminDemande.equals("/status")) {
                long memLibre   = Runtime.getRuntime().freeMemory();
                long disqueLibre = new File("/").getFreeSpace();
                int nbProcessus = 0;
                try {
                    Process p = Runtime.getRuntime().exec("ps -e");
                    BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
                    while (br.readLine() != null) nbProcessus++;
                    nbProcessus--; // enlever la ligne d'en-tête de ps
                    br.close();
                } catch (Exception e) {}

                String html = "<html><head><meta charset=\"UTF-8\"><title>Etat serveur</title></head><body>"
                        + "<h1>Etat de la machine</h1><ul>"
                        + "<li>Memoire JVM disponible : " + (memLibre / 1024 / 1024) + " Mo</li>"
                        + "<li>Espace disque disponible (/) : " + (disqueLibre / 1024 / 1024 / 1024) + " Go</li>"
                        + "<li>Nombre de processus Linux : " + nbProcessus + "</li>"
                        + "</ul></body></html>";
                byte[] corps = html.getBytes("UTF-8");
                sortieTexte.print("HTTP/1.1 200 OK\r\n");
                sortieTexte.print("Date: " + formaterDate(System.currentTimeMillis()) + "\r\n");
                sortieTexte.print("Server: MyWeb/1.0\r\n");
                sortieTexte.print("Content-Type: text/html; charset=UTF-8\r\n");
                sortieTexte.print("Content-Length: " + corps.length + "\r\n\r\n");
                sortieTexte.flush();
                sortieOctet.write(corps);
                sortieOctet.flush();
                socket.close();
                continue;
            }

            // --- Sécurité : bloquer les chemins avec ".." ---
            if (cheminDemande.contains("..")) {
                logToFile(errorLogPath, "403 (chemin interdit) : " + cheminDemande + " (IP: " + socket.getInetAddress() + ")");
                sortieTexte.print(erreur403);
                sortieTexte.flush();
                socket.close();
                continue;
            }

            // Supprimer le query string (?param=val) si présent
            if (cheminDemande.contains("?"))
                cheminDemande = cheminDemande.substring(0, cheminDemande.indexOf('?'));

            // Construire le chemin complet du fichier
            File fichier = new File(pathRoot + cheminDemande);

            // Si c'est un dossier, chercher le fichier par défaut (ex: index.html)
            if (fichier.exists() && fichier.isDirectory() && pathDefault != null && !pathDefault.isEmpty()) {
                File fichierDefaut = new File(fichier, pathDefault);
                if (fichierDefaut.exists() && fichierDefaut.isFile()) {
                    fichier = fichierDefaut;
                }
            }

            // --- Fichier introuvable → 404 ---
            if (!fichier.exists()) {
                logToFile(errorLogPath, "404 : " + cheminDemande + " (IP: " + socket.getInetAddress() + ")");
                sortieTexte.print(erreur404);
                sortieTexte.flush();
                socket.close();
                continue;
            }

            // --- C'est un dossier sans fichier par défaut → listing ---
            if (fichier.isDirectory()) {
                StringBuilder html = new StringBuilder();
                html.append("<html><head><meta charset=\"UTF-8\"><title>Index de ").append(cheminDemande)
                        .append("</title></head><body>\n");
                html.append("<h1>Index de ").append(cheminDemande).append("</h1><hr><pre>\n");

                // Lien vers le dossier parent
                if (!cheminDemande.equals("/")) {
                    String parent;
                    if (cheminDemande.endsWith("/")) {
                        parent = cheminDemande.substring(0, cheminDemande.length() - 1);
                    } else {
                        parent = cheminDemande;
                    }
                    int dernierSlash = parent.lastIndexOf('/');
                    if (dernierSlash >= 0) {
                        parent = parent.substring(0, dernierSlash + 1);
                        html.append("<a href=\"").append(parent).append("\">../</a>\n");
                    }
                }

                // Liste des fichiers et dossiers
                File[] liste = fichier.listFiles();
                if (liste != null) {
                    for (File f : liste) {
                        String nom;
                        if (f.isDirectory()) {
                            nom = f.getName() + "/";
                        } else {
                            nom = f.getName();
                        }
                        String href;
                        if (cheminDemande.endsWith("/")) {
                            href = cheminDemande + nom;
                        } else {
                            href = cheminDemande + "/" + nom;
                        }
                        html.append("<a href=\"").append(href).append("\">").append(nom).append("</a>\n");
                    }
                }
                html.append("</pre><hr></body></html>\n");

                byte[] corps = html.toString().getBytes("UTF-8");
                sortieTexte.print("HTTP/1.1 200 OK\r\n");
                sortieTexte.print("Date: " + formaterDate(System.currentTimeMillis()) + "\r\n");
                sortieTexte.print("Server: MyWeb/1.0\r\n");
                sortieTexte.print("Content-Type: text/html; charset=UTF-8\r\n");
                sortieTexte.print("Content-Length: " + corps.length + "\r\n");
                sortieTexte.print("\r\n");
                sortieTexte.flush();
                sortieOctet.write(corps);
                sortieOctet.flush();
                socket.close();
                continue;
            }

            // --- C'est un fichier normal ---
            try {
                String etagServeur     = calculerMD5(fichier);
                String dateModifServeur = formaterDate(fichier.lastModified());
                String dateActuelle    = formaterDate(System.currentTimeMillis());
                String contentType     = getContentType(fichier.getName());
                boolean faireGzip = doitEtreGzippe(fichier.getName()) && accepteGzip;

                // Vérification du cache : si le client a déjà la bonne version → 304
                boolean clientAJour = (etagClient != null && etagClient.equals(etagServeur))
                                   || (dateModifClient != null && dateModifClient.equals(dateModifServeur));

                if (clientAJour) {
                    sortieTexte.print("HTTP/1.1 304 Not Modified\r\n");
                    sortieTexte.print("Date: " + dateActuelle + "\r\n");
                    sortieTexte.print("Server: MyWeb/1.0\r\n");
                    sortieTexte.print("ETag: " + etagServeur + "\r\n");
                    sortieTexte.print("Last-Modified: " + dateModifServeur + "\r\n");
                    sortieTexte.print("\r\n");
                    sortieTexte.flush();
                    logToFile(accessLogPath, "304 Not Modified : " + cheminDemande);
                    socket.close();
                    continue;
                }

                // Préparer le contenu à envoyer
                byte[] corps;
                String nomFichier = fichier.getName().toLowerCase();
                if (nomFichier.endsWith(".html") || nomFichier.endsWith(".htm")) {
                    // Fichier HTML : on cherche et exécute les balises <code interpreteur=...>
                    corps = traiterHtmlDynamique(fichier);
                } else {
                    // Autre fichier : on lit les octets directement
                    corps = Files.readAllBytes(fichier.toPath());
                }

                // Compression gzip si le client l'accepte et que c'est une image/PDF
                if (faireGzip) {
                    corps = compresserGzip(corps);
                }

                // Envoi de la réponse HTTP
                sortieTexte.print("HTTP/1.1 200 OK\r\n");
                sortieTexte.print("Date: " + dateActuelle + "\r\n");
                sortieTexte.print("Server: MyWeb/1.0\r\n");
                sortieTexte.print("Content-Type: " + contentType + "\r\n");
                sortieTexte.print("Content-Length: " + corps.length + "\r\n");
                sortieTexte.print("ETag: " + etagServeur + "\r\n");
                sortieTexte.print("Last-Modified: " + dateModifServeur + "\r\n");
                if (faireGzip) sortieTexte.print("Content-Encoding: gzip\r\n");
                sortieTexte.print("\r\n");
                sortieTexte.flush();

                sortieOctet.write(corps);
                sortieOctet.flush();

                logToFile(accessLogPath, "200 OK : " + cheminDemande + " (IP: " + socket.getInetAddress() + ")");

            } catch (FileNotFoundException e) {
                logToFile(errorLogPath, "404 : " + cheminDemande + " (IP: " + socket.getInetAddress() + ")");
                sortieTexte.print(erreur404);
                sortieTexte.flush();
            } catch (IOException e) {
                sortieTexte.print("HTTP/1.1 500 Internal Server Error\r\n\r\nErreur interne.\n");
                sortieTexte.flush();
            }

            socket.close();
        }
    }

    //  MÉTHODE PRINCIPALE 

    public static void main(String[] args) throws Exception {

        // Écriture du fichier PID (numéro du processus Java)
        // ManagementFactory retourne quelque chose comme "1234@nomMachine", on prend juste le chiffre
        String nomJvm = ManagementFactory.getRuntimeMXBean().getName();
        String pidStr = nomJvm.split("@")[0];
        File repRun = new File("../run");
        repRun.mkdirs();
        PrintWriter pidWriter = new PrintWriter(new FileWriter(new File(repRun, "myweb.pid")));
        pidWriter.println(pidStr);
        pidWriter.close();
        System.out.println("PID : " + pidStr);

        // Lecture du fichier de configuration XML (../conf.d/*.conf)
        File repConf = new File("../conf.d");
        File[] tousLesConf = repConf.listFiles();
        if (tousLesConf == null || tousLesConf.length == 0) {
            System.err.println("Aucun fichier de configuration trouvé dans " + repConf.getPath());
            System.exit(1);
        }

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
<<<<<<< HEAD
        DocumentBuilder builder = null;
        try {
            builder = factory.newDocumentBuilder();
        } catch (ParserConfigurationException e) {
            e.printStackTrace();
        }

        // 2. Chargement du document en mémoire
        Document document = null;
        try {
            document = builder.parse(new File("ServerWeb_GALLOIS_LE_MADEC/serveurWeb/conf.d/serverWeb.conf"));
        } catch (SAXException | IOException e) {
            e.printStackTrace();
        }

        // 3. Récupération de tous les éléments <site>
        NodeList siteNodeList = document.getElementsByTagName("site");

        // on crée la liste pour stocker les différents éléments du fichier config
=======
        DocumentBuilder builder = factory.newDocumentBuilder();
>>>>>>> 9567894807e1e2dc210789f9646d022635569335

        // Map : port (String) → liste [DocumentRoot, DefaultIndex, AccesLog, ErrorLog]
        TreeMap<String, ArrayList<Node>> gestionSite = new TreeMap<String, ArrayList<Node>>();

        for (int c = 0; c < tousLesConf.length; c++) {
            File confFile = tousLesConf[c];
            if (!confFile.getName().endsWith(".conf")) continue;

            System.out.println("Chargement : " + confFile.getName());
            Document document = builder.parse(confFile);

            NodeList sites = document.getElementsByTagName("site");
            for (int i = 0; i < sites.getLength(); i++) {
                ArrayList<Node> config = new ArrayList<Node>();
                Element site = (Element) sites.item(i);
                config.add(site.getElementsByTagName("DocumentRoot").item(0)); // 0
                config.add(site.getElementsByTagName("DefaultIndex").item(0)); // 1
                config.add(site.getElementsByTagName("Acceslog").item(0));     // 2
                config.add(site.getElementsByTagName("Errorlog").item(0));     // 3
                String port = site.getElementsByTagName("port").item(0).getTextContent().trim();
                gestionSite.put(port, config);
                System.out.println("Site sur le port " + port);
            }
        }

        // Démarrage d'un Thread par site
        ArrayList<String> ports = new ArrayList<String>(gestionSite.keySet());
        for (int i = 0; i < ports.size(); i++) {
            final String clefPort = ports.get(i);
            Thread thread = new Thread(new Runnable() {
                public void run() {
                    try {
                        int port = Integer.parseInt(clefPort);

                        Node nodeRoot  = gestionSite.get(clefPort).get(0);
                        Node nodeIndex = gestionSite.get(clefPort).get(1);
                        Node nodeAcces = gestionSite.get(clefPort).get(2);
                        Node nodeError = gestionSite.get(clefPort).get(3);

                        String pathRoot;
                        if (nodeRoot != null) {
                            pathRoot = nodeRoot.getTextContent().trim();
                        } else {
                            pathRoot = "../var/www";
                        }
                        String pathDefault;
                        if (nodeIndex != null) {
                            pathDefault = nodeIndex.getTextContent().trim();
                        } else {
                            pathDefault = "index.html";
                        }
                        String pathAcces;
                        if (nodeAcces != null) {
                            pathAcces = nodeAcces.getTextContent().trim();
                        } else {
                            pathAcces = "";
                        }
                        String pathError;
                        if (nodeError != null) {
                            pathError = nodeError.getTextContent().trim();
                        } else {
                            pathError = "";
                        }

                        chargerPage(pathRoot, port, pathDefault, pathAcces, pathError);
                    } catch (IOException e) {
                        System.err.println("Erreur sur le port " + clefPort + " : " + e.getMessage());
                    }
                }
            });
            thread.start();
        }
    }
}