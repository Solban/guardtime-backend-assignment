package com.guardtime.assignment;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.guardtime.ksi.Reader;
import com.guardtime.ksi.SignatureReader;
import com.guardtime.ksi.blocksigner.IdentityMetadata;
import com.guardtime.ksi.blocksigner.KsiBlockSigner;
import com.guardtime.ksi.exceptions.KSIException;
import com.guardtime.ksi.hashing.DataHasher;
import com.guardtime.ksi.hashing.HashAlgorithm;
import com.guardtime.ksi.service.client.KSIServiceCredentials;
import com.guardtime.ksi.service.client.KSISigningClient;
import com.guardtime.ksi.service.client.ServiceCredentials;
import com.guardtime.ksi.service.client.http.CredentialsAwareHttpSettings;
import com.guardtime.ksi.service.http.simple.SimpleHttpSigningClient;
import com.guardtime.ksi.unisignature.Identity;
import com.guardtime.ksi.unisignature.KSISignature;
import com.guardtime.ksi.util.Base16;
import org.apache.commons.io.IOUtils;

import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class ContainerApi {

  private static KSISigningClient ksiSigningClient;
  private static Reader reader;

  // Specifies where containers are stored
  private static final Path CONTAINERS_PATH = Paths.get("src", "main", "resources", "containers");
  // The source directory of the files to be compressed
  private static final Path SOURCE_DIR = Paths.get("src", "main", "resources", "files");

  public ContainerApi() {
    setUpKsi();
  }

  void setUpKsi() {

    String aggregatorUrl = System.getProperty("aggregator.url");
    String loginId = System.getProperty("ksi.login.id");
    String loginKey = System.getProperty("ksi.login.key");

    ServiceCredentials credentials = new KSIServiceCredentials(loginId, loginKey);

    ksiSigningClient =
        new SimpleHttpSigningClient(new CredentialsAwareHttpSettings(aggregatorUrl, credentials));

    reader = new SignatureReader();
  }

  /**
   * First part of the assignment - Creating a container. All the files in the files directory will
   * get compressed in to a .zip file, the name will be provided by the user. Since a container
   * might need to be fetched later, it needs to be identifiable. That is achieved by each container
   * having a distinct name. After checking the name, it proceeds to compress all the files.
   *
   * @param fileName of the .zip container to be created.
   * @return an error message in the form of Json if the name is not satisfactory or will return and
   *     empty string if the container was created successfully.
   */
  public String createContainer(String fileName) {

    if (fileName == null || fileName.length() < 1) {
      return errorMessageAsJson("Provide a name for the container.");
    }


    if (isContainerNameDistinct(fileName)) {
      return errorMessageAsJson("Container with that name already exists!");
    }

    // Define the input and output points for the compression
    String fileUri = ("src/main/resources/containers/" + fileName + ".zip");
    // Goes through all the content in directory, reads it to a byte array, which can contain up to
    // 2GB and writes to the .zip container
    try {
      final ZipOutputStream outputStream = new ZipOutputStream(new FileOutputStream(fileUri));
      Files.walkFileTree(
          SOURCE_DIR,
          new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
              try {
                Path targetFile = SOURCE_DIR.relativize(file);
                outputStream.putNextEntry(new ZipEntry(targetFile.toString()));
                byte[] bytes = Files.readAllBytes(file);
                outputStream.write(bytes, 0, bytes.length);
                outputStream.closeEntry();
              } catch (IOException e) {
                e.printStackTrace();
              }
              return FileVisitResult.CONTINUE;
            }
          });
      outputStream.close();
    } catch (IOException e) {
      e.printStackTrace();
    }

    return "";
  }

  /**
   * Second part of the assignment. Reads the existing containers.
   *
   * @return a json with the names of all the containers and how many there are.
   */
  public String readContainers() {
    List<String> filesInFolder = getAllContainerNamesInDir();

    JsonObject jsonObject = new JsonObject();
    jsonObject.add("numberOfContainers", new JsonPrimitive(filesInFolder.size()));
    JsonArray data = new JsonArray();
    filesInFolder.forEach(data::add);
    jsonObject.add("containers", data);

    return jsonObject.toString();
  }

  /**
   * Third part of the assignment. Creates a manifest file from the contents of the .zip container.
   * Signs the manifest file. Persists the signature to the .zip container.
   *
   * @param fileName of the .zip container wished to be signed.
   * @param userId of the user whose name on it will be signed. Used later to know which signature
   *     to delete.
   * @return a json with the appropriate error message or empty string if signing was successful.
   */
  public String sign(String fileName, String userId) {

    if (fileName == null || fileName.length() < 1) {
      return errorMessageAsJson("Provide the name of a container you wish to sign.");
    }

    if (userId == null || userId.length() < 1) {
      return errorMessageAsJson("Provide your name to sign the content.");
    }

    Map<String, String> env = new HashMap<>();
    env.put("create", "true");

    URI uri = URI.create("jar:" + CONTAINERS_PATH.toUri() + "/" + fileName + ".zip");
    // List of POJO to store each file's uri, hash-algorithm and hash.
    List<MetaData> metaData;

    try (FileSystem fs = FileSystems.newFileSystem(uri, env)) {
      metaData = getMetaDataList(fs);

      // Creates "META-INF" directory
      Path newDir = fs.getPath("META-INF");
      if (Files.notExists(newDir)) {
        Files.createDirectory(newDir);
      }

      int signatureNumber = getSignatureNumber(fs);

      // Creates a new manifest file
      Path manifestFile = fs.getPath("META-INF", "manifest" + signatureNumber + ".tlv");
      try (Writer writer =
          Files.newBufferedWriter(
              manifestFile, StandardCharsets.UTF_8, StandardOpenOption.CREATE)) {
        for (MetaData df : metaData) {
          writer.write("Datafile\n");
          writer.write("\turi=" + df.getUri() + "\n");
          writer.write("\thash-algorithm=" + df.getHashAlgorithm() + "\n");
          writer.write("\thash=" + df.getHash() + "\n");
        }
        writer.write("signature-uri=META-INF/signature" + signatureNumber + ".ksi");
      }

      signTheManifest(manifestFile, fs, signatureNumber, userId);

    } catch (Exception e) {
      e.printStackTrace();
    }

    return "";
  }

  /**
   * Fourth part of the assignment - Deleting a signature from the container.
   *
   * @param fileName of the container to delete the signature from.
   * @param userId of the user whose signature is to be deleted from the container.
   * @return status code 409 with error message if something went wrong, otherwise no message with
   *     status code 200.
   */
  public String delete(String fileName, String userId) {
    if (fileName == null || fileName.length() < 1) {
      return errorMessageAsJson(
          "Provide the name of a container you wish to delete the signature from.");
    }

    if (userId == null || userId.length() < 1) {
      return errorMessageAsJson("Provide your name to delete the signature from the container.");
    }

    URI uri = URI.create("jar:" + CONTAINERS_PATH.toUri() + "/" + fileName + ".zip");

    Map<String, String> env = new HashMap<>();
    env.put("create", "false");

    /* Create ZIP file System */
    try (FileSystem fs = FileSystems.newFileSystem(uri, env)) {

      Path root = fs.getRootDirectories().iterator().next();
      try {
        Files.walk(root)
            .filter(path -> path.toString().contains("/META-INF/signature"))
            .filter(path -> path.toString().contains(".ksi"))
            .filter(path -> Files.isRegularFile(path))
            .forEach(
                path -> {
                  try {
                    KSISignature signature = reader.read(Files.newInputStream(path));
                    if (isItToBeDeleted(signature.getAggregationHashChainIdentity(), userId)) {
                      // find the corresponding manifest file
                      String manifest = path.toString().replace("signature", "manifest");
                      manifest = manifest.replace("ksi", "tlv");
                      Path manifestPath = fs.getPath(manifest);
                      // delete tha manifest and then the signature file
                      Files.delete(manifestPath);
                      Files.delete(path);
                    }
                  } catch (Exception e) {
                    e.printStackTrace();
                  }
                });
      } catch (Exception e) {
        // will receive NoSuchFileExceptions, due to the files being deleted. That is to be expected
        e.printStackTrace();
      }

    } catch (IOException e) {
      e.printStackTrace();
    }

    return "";
  }

  /** @return all container names. */
  private List<String> getAllContainerNamesInDir() {
    List<String> filesInFolder = new ArrayList<>();
    try {
      filesInFolder =
          Files.walk(CONTAINERS_PATH)
              .filter(Files::isRegularFile)
              .filter(path -> !path.toString().contains(".DS_S"))
              .map(Path::toString)
              .map(s -> s.substring(30, s.length() - 4))
              .collect(Collectors.toList());
    } catch (IOException e) {
      e.printStackTrace();
    }

    return filesInFolder;
  }

  /**
   * Since a container might need to be fetched later, it needs to be identifiable. That is achieved
   * by each container having a distinct name.
   *
   * @param name for the .zip file container.
   * @return whether the container with the same name already exists in the directory where all
   *     containers are kept.
   */
  private boolean isContainerNameDistinct(String name) {
    List<String> filesInFolder = getAllContainerNamesInDir();
    return filesInFolder.contains(name);
  }

  /**
   * @param message that gives more info on why the request created an error.
   * @return a string in json format.
   */
  private String errorMessageAsJson(String message) {
    JsonObject obj = new JsonObject();
    obj.add("Error", new JsonPrimitive(message));
    return obj.toString();
  }

  /**
   * Determines the next signature number. Finds the manifest file with the biggest number and adds
   * one.
   *
   * @param fs of the zip file.
   * @return next signature number.
   */
  private int getSignatureNumber(FileSystem fs) {
    Path root = fs.getRootDirectories().iterator().next();
    try {
      return Files.walk(root)
              .filter(path -> path.toString().contains("/META-INF"))
              .filter(path -> path.toString().contains("/manifest"))
              .map(
                  path ->
                      Integer.parseInt(path.toString().substring(18, path.toString().indexOf("."))))
              .reduce(0, Integer::max)
          + 1;
    } catch (IOException e) {
      e.printStackTrace();
    }
    return 1;
  }

  /**
   * Walking through all the files in .zip container and generating the content for each to add to
   * the manifest file.
   *
   * @param fs for the .zip container.
   * @return a list containing metadata about the containers content files.
   */
  private List<MetaData> getMetaDataList(FileSystem fs) {
    List<MetaData> metaData = new ArrayList<>();
    Path root = fs.getRootDirectories().iterator().next();
    try {
      Files.walk(root)
          .forEach(
              path -> {
                try {
                  if (Files.isRegularFile(path)) {
                    if (!path.toString().contains("/META-INF")
                        && !path.toString().contains("/.DS_S")) {
                      // Removing the slash ("/") from the front  of the name
                      String fileUri = path.toString().substring(1, path.toString().length());
                      // Generating the hash
                      DataHasher dh = new DataHasher(HashAlgorithm.SHA2_256);
                      String hashAlgorithm = HashAlgorithm.SHA2_256.getName();
                      dh.addData(Files.newInputStream(path));
                      String hash = Base16.encode(dh.getHash().getValue());
                      metaData.add(new MetaData(fileUri, hashAlgorithm, hash));
                    }
                  }
                } catch (Exception e) {
                  e.printStackTrace();
                }
              });
    } catch (Exception e) {
      e.printStackTrace();
    }
    return metaData;
  }

  /**
   * Signs the file and persists the signature to the container. Does not extend the signature.
   * Although found the following statement: "A general recommendation is to extend the signature to
   * the closest publication as soon as possible. This is what the Catena-DB also does with all
   * signatures it persists.", but was not sure where to save the extended signature and the
   * assignment did not specify. UserId is used to later know which signature to delete.
   *
   * @param manifestFile to be signed using the KSI.
   * @param fs for the .zip container.
   * @param signatureNumber for this signature.
   * @param userId to know which user signed it.
   */
  private void signTheManifest(Path manifestFile, FileSystem fs, int signatureNumber, String userId)
      throws IOException, KSIException {
    // Makes a temporary identical copy of the manifest file
    InputStream in = Files.newInputStream(manifestFile);
    final File maniFestFile = File.createTempFile("prefix", "sufix");
    maniFestFile.deleteOnExit();
    try (FileOutputStream out = new FileOutputStream(maniFestFile)) {
      IOUtils.copy(in, out);
    }

    KsiBlockSigner ksiBlockSigner = new KsiBlockSigner(ksiSigningClient);
    DataHasher dh = new DataHasher(HashAlgorithm.SHA2_256);
    dh.addData(maniFestFile);

    ksiBlockSigner.add(dh.getHash(), new IdentityMetadata(userId));
    List<KSISignature> signatures = ksiBlockSigner.sign();

    // Persists signature to file
    Path signaturePath = fs.getPath("META-INF", "signature" + signatureNumber + ".ksi");
    try (OutputStream outputStream =
        Files.newOutputStream(signaturePath, StandardOpenOption.CREATE)) {
      signatures.get(0).writeTo(outputStream);
    }
  }

  /**
   * Checks for userId in the identity metadata.
   *
   * @return whether the manifest was signed by the given user.
   */
  private boolean isItToBeDeleted(Identity[] identity, String userId) {
    StringBuilder stringBuilder = new StringBuilder();
    stringBuilder.append(identity[identity.length - 1].getDecodedClientId());
    return stringBuilder.toString().equals(userId);
  }
}
