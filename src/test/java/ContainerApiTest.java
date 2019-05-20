import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.guardtime.assignment.ContainerApi;
import com.guardtime.ksi.Reader;
import com.guardtime.ksi.SignatureReader;
import com.guardtime.ksi.exceptions.KSIException;
import com.guardtime.ksi.unisignature.Identity;
import com.guardtime.ksi.unisignature.KSISignature;
import org.junit.Before;
import org.junit.Test;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class ContainerApiTest {

  private ContainerApi containerApi;
  final String CONTAINER = "konteiner";
  final String USER = "john.smith";

  @Before
  public void setContainerApi() {
    containerApi = new ContainerApi();
  }

  public ContainerApi getContainerApi() {
    return containerApi;
  }

  /** Checks if container is being created. */
  @Test
  public void createContainerTest() throws IOException {
    String pathToContainer = "src/main/resources/containers/konteiner.zip";
    containerApi.createContainer(CONTAINER);
    File container = new File(pathToContainer);
    assert (container.exists());
    Files.delete(Paths.get(pathToContainer));
  }

  /** Checks if container's name is being returned in the json. */
  @Test
  public void readContainerTest() throws IOException {
    String pathToContainer = "src/main/resources/containers/konteiner.zip";
    containerApi.createContainer(CONTAINER);
    String response = containerApi.readContainers();
    JsonParser parser = new JsonParser();
    JsonObject json = (JsonObject) parser.parse(response);
    assert (json.get("containers").getAsJsonArray().contains(new JsonPrimitive(CONTAINER)));
    Files.delete(Paths.get(pathToContainer));
  }

  /** Checks if container was signed */
  @Test
  public void signContainerTest() throws IOException, KSIException {

    containerApi.createContainer(CONTAINER);
    containerApi.sign(CONTAINER, USER);
    String pathToContainer = "src/main/resources/containers/konteiner.zip";
    String destDirectory = "src/main/resources/containers/temp";
    // unpack .zip container
    unzip(pathToContainer, destDirectory);

    // check that signature exists
    File signatureFile = new File("src/main/resources/containers/temp/META-INF/signature1.ksi");
    assert (signatureFile.exists());

    // check that signature was signed by user
    Reader reader = new SignatureReader();
    KSISignature ksiSignature = reader.read(signatureFile);
    Identity[] identity = ksiSignature.getAggregationHashChainIdentity();

    assert (identity[identity.length - 1].getDecodedClientId().equals(USER));

    // deletes unzipped files
    Files.delete(Paths.get(pathToContainer));
    File temp = new File("src/main/resources/containers/temp/META-INF");
    String[]entries = temp.list();
    for(String s: entries){
      File currentFile = new File(temp.getPath(),s);
      currentFile.delete();
    }
    temp.delete();
    temp = new File("src/main/resources/containers/temp");
    entries = temp.list();
    for(String s: entries){
      File currentFile = new File(temp.getPath(),s);
      currentFile.delete();
    }
    temp.delete();

  }

  /**
   * Creates a container, signs it twice, deletes one of those signatures.
   */
  @Test
  public void deleteContainerSignatureTest() throws IOException, KSIException {

    String secondUser = "Jane.Smith";

    containerApi.createContainer(CONTAINER);
    containerApi.sign(CONTAINER, secondUser);
    containerApi.sign(CONTAINER, USER);
    containerApi.delete(CONTAINER, USER);

    String pathToContainer = "src/main/resources/containers/konteiner.zip";
    String destDirectory = "src/main/resources/containers/temp";
    // unpack .zip container
    unzip(pathToContainer, destDirectory);

    // check that signature was signed by jane.smith
    File signatureFile = new File("src/main/resources/containers/temp/META-INF/signature1.ksi");

    Reader reader = new SignatureReader();
    KSISignature ksiSignature = reader.read(signatureFile);
    Identity[] identity = ksiSignature.getAggregationHashChainIdentity();

    assert (identity[identity.length - 1].getDecodedClientId().equals(secondUser));

    // check for other files in the directory. For every signature there should be two files (manifest and signature files)
    File files = new File("src/main/resources/containers/temp/META-INF");
    String[] fileNames = files.list();
    assert (fileNames.length == 2);

    // deletes unzipped files
    Files.delete(Paths.get(pathToContainer));
    File temp = new File("src/main/resources/containers/temp/META-INF");
    String[]entries = temp.list();
    for(String s: entries){
      File currentFile = new File(temp.getPath(),s);
      currentFile.delete();
    }
    temp.delete();
    temp = new File("src/main/resources/containers/temp");
    entries = temp.list();
    for(String s: entries){
      File currentFile = new File(temp.getPath(),s);
      currentFile.delete();
    }
    temp.delete();

  }


  /**
   * Extracts a zip file specified by the zipFilePath to a directory specified by destDirectory
   * (will be created if does not exists)
   *
   * @param zipFilePath
   * @param destDirectory
   * @throws IOException
   */
  public void unzip(String zipFilePath, String destDirectory) throws IOException {
    File destDir = new File(destDirectory);
    if (!destDir.exists()) {
      destDir.mkdir();
    }
    ZipInputStream zipIn = new ZipInputStream(new FileInputStream(zipFilePath));
    ZipEntry entry = zipIn.getNextEntry();
    // iterates over entries in the zip file
    while (entry != null) {
      String filePath = destDirectory + File.separator + entry.getName();
      if (!entry.isDirectory()) {
        // if the entry is a file, extracts it
        extractFile(zipIn, filePath);
      } else {
        // if the entry is a directory, make the directory
        File dir = new File(filePath);
        dir.mkdir();
      }
      zipIn.closeEntry();
      entry = zipIn.getNextEntry();
    }
    zipIn.close();
  }

  /**
   * Extracts a zip entry (file entry)
   *
   * @param zipIn
   * @param filePath
   * @throws IOException
   */
  private void extractFile(ZipInputStream zipIn, String filePath) throws IOException {
    BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(filePath));
    byte[] bytesIn = new byte[4096];
    int read = 0;
    while ((read = zipIn.read(bytesIn)) != -1) {
      bos.write(bytesIn, 0, read);
    }
    bos.close();
  }
}
