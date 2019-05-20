package com.guardtime.assignment;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

/**
 * The main class that deals with all the web logic.
 */
public class Main {

  public static void main(String[] args) throws IOException {
    ContainerApi containerAPI = new ContainerApi();

    int serverPort = 1234;
    HttpServer server = HttpServer.create(new InetSocketAddress(serverPort), 0);
    server.createContext(
        "/create",
        (exchange -> {
          if ("PUT".equals(exchange.getRequestMethod())) {

            InputStream inputStream = exchange.getRequestBody();
            JsonParser jsonParser = new JsonParser();
            JsonObject jsonObject =
                (JsonObject)
                    jsonParser.parse(new InputStreamReader(inputStream, StandardCharsets.UTF_8));

            String fileName = jsonObject.get("name").getAsString();
            String response = containerAPI.createContainer(fileName);
            int statusCode = response.length() < 1 ? 201 : 409;
            exchange.sendResponseHeaders(statusCode, response.getBytes().length);
            OutputStream output = exchange.getResponseBody();
            output.write(response.getBytes());
            output.flush();
          } else {
            exchange.sendResponseHeaders(405, -1); // 405 Method Not Allowed
          }
          exchange.close();
        }));

    server.createContext(
        "/read",
        (exchange -> {
          if ("GET".equals(exchange.getRequestMethod())) {
            String response = containerAPI.readContainers();
            int statusCode = 200;
            exchange.sendResponseHeaders(statusCode, response.getBytes().length);
            OutputStream output = exchange.getResponseBody();
            output.write(response.getBytes());
            output.flush();
          } else {
            exchange.sendResponseHeaders(405, -1); // 405 Method Not Allowed
          }
          exchange.close();
        }));

    server.createContext(
        "/sign",
        (exchange -> {
          if ("POST".equals(exchange.getRequestMethod())) {

            InputStream inputStream = exchange.getRequestBody();
            JsonParser jsonParser = new JsonParser();
            JsonObject jsonObject =
                (JsonObject)
                    jsonParser.parse(new InputStreamReader(inputStream, StandardCharsets.UTF_8));

            String fileName = jsonObject.get("name").getAsString();
            String userId = jsonObject.get("userId").getAsString();
            String response = containerAPI.sign(fileName, userId);
            int statusCode = response.length() < 1 ? 201 : 409;
            exchange.sendResponseHeaders(statusCode, response.getBytes().length);
            OutputStream output = exchange.getResponseBody();
            output.write(response.getBytes());
            output.flush();
          } else {
            exchange.sendResponseHeaders(405, -1); // 405 Method Not Allowed
          }
          exchange.close();
        }));

    server.createContext(
        "/delete",
        (exchange -> {
          if ("DELETE".equals(exchange.getRequestMethod())) {

            String query = exchange.getRequestURI().getQuery();

            String name = getQueryParameterValue(query, "name");
            String userId = getQueryParameterValue(query, "userId");

            String response = containerAPI.delete(name, userId);

            int statusCode = response.length() < 1 ? 201 : 409;
            exchange.sendResponseHeaders(statusCode, response.getBytes().length);
            OutputStream output = exchange.getResponseBody();
            output.write(response.getBytes());
            output.flush();
          } else {
            exchange.sendResponseHeaders(405, -1); // 405 Method Not Allowed
          }
          exchange.close();
        }));

    server.setExecutor(null); // creates a default executor
    server.start();
  }

  /**
   * Helper method to find parameter values from the query
   *
   * @param queryString from the requed URI
   * @param param the parameter key
   * @return the value of the corresponding parameter key
   */
  private static String getQueryParameterValue(String queryString, String param) {
    int startIdx = queryString.indexOf(param) + param.length() + 1;
    int endIdx = queryString.length();
    for (int i = startIdx; i < queryString.length(); i++) {
      if (queryString.charAt(i) == '&') {
        endIdx = i;
      }
    }
    return queryString.substring(startIdx, endIdx);
  }

}
