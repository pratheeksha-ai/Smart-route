import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class SmartRouteServer {

    static final String DB_URL = "jdbc:postgresql://localhost:5432/smart_route_db";
    static final String DB_USER = "postgres";
    static final String DB_PASSWORD = "1234";

    public static void main(String[] args) {
        testDatabaseConnection();
        startServer();
    }

    public static void testDatabaseConnection() {
        try {
            Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);

            System.out.println("Connected to the database successfully!");

            conn.close();

        } catch (Exception e) {
            System.out.println("Error connecting to the database: " + e.getMessage());
        }
    }

    public static void addPlace(String placeName) {
        try {
            Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);

            String sql = "INSERT INTO places (name) VALUES (?) ON CONFLICT (name) DO NOTHING";

            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setString(1, placeName);
            ps.executeUpdate();

            System.out.println("Place checked/added successfully: " + placeName);

            conn.close();

        } catch (Exception e) {
            System.out.println("Error adding place: " + e.getMessage());
        }
    }

    public static void startServer() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);

            server.createContext("/", SmartRouteServer::serveFile);
            server.createContext("/api/add-place", SmartRouteServer::addPlaceApi);
            server.createContext("/api/places", SmartRouteServer::getPlacesApi);

            server.setExecutor(null);
            server.start();

            System.out.println("Server started successfully.");
            System.out.println("Open: http://localhost:8080");

        } catch (Exception e) {
            System.err.println("Error starting server: " + e.getMessage());
        }
    }

    public static void serveFile(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();

        if (path.equals("/")) {
            path = "/index.html";
        }

        File file = new File("public" + path);

        if (!file.exists()) {
            String response = "404 File Not Found";

            exchange.sendResponseHeaders(404, response.getBytes().length);
            exchange.getResponseBody().write(response.getBytes());
            exchange.close();

            return;
        }

        byte[] data = Files.readAllBytes(file.toPath());

        exchange.getResponseHeaders().set("Content-Type", "text/html");
        exchange.sendResponseHeaders(200, data.length);
        exchange.getResponseBody().write(data);
        exchange.close();
    }

    public static void addPlaceApi(HttpExchange exchange) throws IOException {
        String query = exchange.getRequestURI().getQuery();

        String placeName = "";

        if (query != null && query.startsWith("name=")) {
            placeName = query.substring(5);
            placeName = URLDecoder.decode(placeName, "UTF-8");
        }

        if (placeName.trim().isEmpty()) {
            String response = "Place name cannot be empty.";

            exchange.sendResponseHeaders(200, response.getBytes().length);
            exchange.getResponseBody().write(response.getBytes());
            exchange.close();

            return;
        }

        addPlace(placeName.trim());

        String response = "Place added successfully: " + placeName;

        exchange.sendResponseHeaders(200, response.getBytes().length);
        exchange.getResponseBody().write(response.getBytes());
        exchange.close();
    }

    public static void getPlacesApi(HttpExchange exchange) throws IOException {
        StringBuilder json = new StringBuilder();
        json.append("[");

        try {
            Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);

            String sql = "SELECT id, name FROM places ORDER BY id";

            PreparedStatement ps = conn.prepareStatement(sql);

            ResultSet rs = ps.executeQuery();

            int count = 0;

            while (rs.next()) {
                if (count > 0) {
                    json.append(",");
                }

                int id = rs.getInt("id");
                String name = rs.getString("name");

                json.append("{");
                json.append("\"id\":").append(id).append(",");
                json.append("\"name\":\"").append(name).append("\"");
                json.append("}");

                count++;
            }
            json.append("]");
            

            conn.close();

        } catch (Exception e) {
            json = new StringBuilder();
            json.append("{\"error\":\"").append(e.getMessage()).append("\"}");
        }

        String response = json.toString();

        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, response.getBytes().length);
        exchange.getResponseBody().write(response.getBytes());
        exchange.close();
    }
}