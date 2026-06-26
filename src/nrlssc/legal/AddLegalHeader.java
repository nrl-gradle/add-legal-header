package nrlssc.legal;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.stream.Collectors;

import com.google.common.io.Resources;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Adds an initial bit of text (the NRL source code disclosure text) to all Java source files in the given folder(s). The copyright text is versioned and marked by a special
 * sequence of characters in the first line. If the version number is updated, this code will replace the existing text with the new text. If the first lines are the same, then no
 * action is taken.
 *
 * The use of the word copyright is a misnomer, as Scott Bell informed me (Richard Owens). Use of this term in this project is not meant to imply an actual copyright.
 *
 */
public class AddLegalHeader {

    private static final Logger logger = LoggerFactory.getLogger(AddLegalHeader.class);

    private static final class MySimpleFileVisitor extends SimpleFileVisitor<Path> {

        private int filesModified = 0;

        private int filesWithCorrectCopyrightVersion = 0;

        private int filesWithIncorrectCopyrightVersion = 0;

        private final List<String> copyrightLines;

        private MySimpleFileVisitor(String legalVersion, String poc, String sectionCode) throws IOException {
            InputStream stream = Resources.getResource("nrlssc/copyright/nrl-disclaimer-java.txt").openStream();
            copyrightLines =
                    new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).lines().collect(Collectors.toList());
            stream.close();

            for(int i = 0; i < copyrightLines.size(); i++) {
                copyrightLines.set(i, copyrightLines.get(i).replaceAll("\\[LEGAL_VERSION\\]", legalVersion).replaceAll("\\[NRL_SECTION_CODE\\]", sectionCode).replaceAll("\\[NRL_POC\\]", poc));
            }
        }

        @Override
        public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) throws IOException {
            String extension = com.google.common.io.Files.getFileExtension(path.toString());
            if ("java".equals(extension) && path.toFile().isFile()) {
                logger.debug("Considering file {}", path);
                FileTime lastModifiedTime = Files.getLastModifiedTime(path);

                List<String> javaFilesLines = Files.readAllLines(path);
                if (copyrightLines.get(0).equals(javaFilesLines.get(0))) {
                    // We already have the correct copyright text in the file -- do nothing.
                    logger.info("Copyright was present in file {}", path);
                    filesWithCorrectCopyrightVersion++;
                } else if (javaFilesLines.get(0).startsWith(copyrightLines.get(0))) {
                    // Have a copyright header, but wrong verion. Replace.
                    String tempFilename = path.toString() + ".zzz";
                    Path tempFile = Paths.get(tempFilename);

                    Files.write(tempFile, copyrightLines, StandardOpenOption.CREATE);
                    int indexOfEndComment = -1;
                    for (int i = 0; i < javaFilesLines.size(); i++) {
                        if (javaFilesLines.get(i).contains("*/")) {
                            indexOfEndComment = i;
                            break;
                        }
                    }
                    Files.write(tempFile, javaFilesLines.subList(indexOfEndComment + 1, javaFilesLines.size()), Charset.defaultCharset(), StandardOpenOption.APPEND);

                    Files.move(tempFile, path, StandardCopyOption.REPLACE_EXISTING);
                    Files.setLastModifiedTime(path, lastModifiedTime);
                    logger.info("Incorrect version of copyright was present in file {}", path);
                    filesWithIncorrectCopyrightVersion++;
                } else {
                    String tempFilename = path.toString() + ".zzz";
                    Path tempFile = Paths.get(tempFilename);

                    Files.write(tempFile, copyrightLines, StandardOpenOption.CREATE);
                    Files.write(tempFile, javaFilesLines, Charset.defaultCharset(), StandardOpenOption.APPEND);

                    Files.move(tempFile, path, StandardCopyOption.REPLACE_EXISTING);
                    Files.setLastModifiedTime(path, lastModifiedTime);
                    filesModified++;
                    logger.info("File {} updated", path);
                }
            }

            return super.visitFile(path, attrs);
        }

        public int getFilesModified() {
            return filesModified;
        }

        public int getFilesWithCorrectCopyrightVersion() {
            return filesWithCorrectCopyrightVersion;
        }

        public int getFilesWithIncorrectCopyrightVersion() {
            return filesWithIncorrectCopyrightVersion;
        }
    }

    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.out.printf("Usage: sourceDir1 [sourceDir2 ...]");
            System.exit(1);
        }

        AddLegalHeader al = new AddLegalHeader();
        for (String dirName : args) {
            if((new File(dirName)).exists()) al.addCopyright(dirName);
        }

    }

    private String legalVersion = "1.1";
    private String poc = "Naval Research Laboratory";
    private String branchCode = "7340";
    public AddLegalHeader(String legalVersion, String POC, String branchCode){
        this.legalVersion = legalVersion;
        this.poc = POC;
        this.branchCode = branchCode;
    }
    public AddLegalHeader(){}

    public void addCopyright(String[] dirs) throws IOException {
        for (String dirName : dirs) {
            if((new File(dirName)).exists()) addCopyright(dirName);
        }
    }

    private void addCopyright(String dirName) throws IOException {
        MySimpleFileVisitor fileVisitor = new MySimpleFileVisitor(legalVersion, poc, branchCode);

        Files.walkFileTree(Paths.get(dirName), fileVisitor);
        logger.info("Files updated: {}", fileVisitor.getFilesModified());
        logger.info("Files with correct copyright: {}", fileVisitor.getFilesWithCorrectCopyrightVersion());
        logger.info("Files with incorrect copyright: {}", fileVisitor.getFilesWithIncorrectCopyrightVersion());
    }

}
