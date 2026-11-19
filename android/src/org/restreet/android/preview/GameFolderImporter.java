package org.restreet.android.preview;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.webkit.MimeTypeMap;
import java.io.*;
import java.security.MessageDigest;
import java.util.*;

/** Copies and validates a user-selected game directory through Android's Storage Access Framework. */
public final class GameFolderImporter {
    public interface Progress { void update(String message) throws IOException; }
    private static final String[][] REQUIRED = {
        {"default.xex", "f4902ab0a2515ca64675544852a0263ba429863dad13dc19b7c2d989c97aa2b1"},
        {"fifadllzf.xex.dll", "9d2fb47c3caf279d55962f671afc5b8eb7a597717d44ccb5995dd002a3c26201"},
        {"dlc/dlc_FootballCompEng/dlc/FootballCompEng/FootballCompEngzf.xex.dll", ""}
    };
    private static final String[] REQUIRED_DATA = {"data0.big", "data0.bh"};
    private static final int MAX_ENTRIES = 100000;

    private final Context context;
    private final ContentResolver resolver;
    private final File destination;
    private final Progress progress;
    private final Set<String> paths = new HashSet<>();
    private final Map<String, File> files = new HashMap<>();
    private long copied;
    private int count;
    private volatile boolean cancelled;

    private GameFolderImporter(Context context, File destination, Progress progress) {
        this.context = context.getApplicationContext();
        this.resolver = context.getContentResolver();
        this.destination = destination;
        this.progress = progress;
    }

    public static void importFolder(Context context, Uri tree, File destination, Progress progress) throws IOException {
        GameFolderImporter importer = new GameFolderImporter(context, destination, progress);
        try {
            if (destination.exists() || !destination.mkdirs()) throw new IOException("La carpeta temporária já existe.");
            String rootId = DocumentsContract.getTreeDocumentId(tree);
            Uri root = DocumentsContract.buildDocumentUriUsingTree(tree, rootId);
            importer.copyDirectory(tree, root, "");
            importer.validate();
            try (FileOutputStream marker = new FileOutputStream(new File(destination, ".restreet-import-complete"))) {
                marker.write("4541097B/78AA9072".getBytes("US-ASCII"));
            }
        } catch (Exception error) {
            deleteTree(destination);
            if (error instanceof IOException) throw (IOException) error;
            throw new IOException("Não foi possível ler a pasta selecionada.", error);
        }
    }

    private void checkCancelled() throws IOException {
        if (Thread.currentThread().isInterrupted()) throw new IOException("Importação cancelada.");
    }

    private void copyDirectory(Uri tree, Uri directory, String parent) throws IOException {
        checkCancelled();
        Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(tree,
                DocumentsContract.getDocumentId(directory));
        String[] columns = {DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE};
        try (Cursor cursor = resolver.query(children, columns, null, null, null)) {
            if (cursor == null) throw new IOException("O armazenamento não permitiu listar a pasta.");
            int idColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID);
            int nameColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME);
            int mimeColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE);
            while (cursor.moveToNext()) {
                checkCancelled();
                if (++count > MAX_ENTRIES) throw new IOException("A pasta contém itens demais.");
                String name = cursor.getString(nameColumn);
                validateName(name);
                String relative = parent.isEmpty() ? name : parent + "/" + name;
                String key = relative.toLowerCase(Locale.ROOT);
                if (!paths.add(key)) throw new IOException("Há nomes de arquivos duplicados: " + relative);
                Uri child = DocumentsContract.buildDocumentUriUsingTree(tree, cursor.getString(idColumn));
                File target = new File(destination, relative);
                String prefix = destination.getCanonicalPath() + File.separator;
                if (!target.getCanonicalPath().startsWith(prefix)) throw new IOException("Caminho inseguro.");
                if (DocumentsContract.Document.MIME_TYPE_DIR.equals(cursor.getString(mimeColumn))) {
                    if (!target.mkdirs() && !target.isDirectory()) throw new IOException("Não foi possível criar " + relative);
                    copyDirectory(tree, child, relative);
                } else {
                    File parentFile = target.getParentFile();
                    if (!parentFile.isDirectory() && !parentFile.mkdirs()) throw new IOException("Não foi possível criar a pasta de destino.");
                    copyFile(child, target);
                    files.put(key, target);
                }
            }
        } catch (SecurityException error) {
            throw new IOException("Sem permissão para ler a pasta selecionada.", error);
        }
    }

    private void validateName(String name) throws IOException {
        if (name == null || name.isEmpty() || name.equals(".") || name.equals("..")
                || name.endsWith(".") || name.endsWith(" ") || name.indexOf('/') >= 0
                || name.indexOf('\\') >= 0 || name.indexOf(':') >= 0)
            throw new IOException("A pasta contém um nome de arquivo inválido.");
    }

    private void copyFile(Uri source, File target) throws IOException {
        try (InputStream in = resolver.openInputStream(source)) {
            if (in == null) throw new IOException("Não foi possível abrir um arquivo do jogo.");
            try (OutputStream out = new BufferedOutputStream(new FileOutputStream(target))) {
                byte[] buffer = new byte[1024 * 1024];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    checkCancelled();
                    out.write(buffer, 0, read);
                    copied += read;
                }
            }
        }
    }

    private void validate() throws IOException {
        for (String[] required : REQUIRED) {
            File file = files.get(required[0].toLowerCase(Locale.ROOT));
            if (file == null || !file.isFile() || file.length() < 4)
                throw new IOException("Falta o arquivo obrigatório: " + required[0]);
            try (InputStream in = new FileInputStream(file)) {
                byte[] magic = new byte[4];
                if (in.read(magic) != 4 || magic[0] != 'X' || magic[1] != 'E' || magic[2] != 'X' || magic[3] != '2')
                    throw new IOException("Executável XEX2 inválido: " + required[0]);
            }
            if (!required[1].isEmpty() && !required[1].equals(sha256(file)))
                throw new IOException("Edição incompatível. Necessário Title ID 4541097B / Media ID 78AA9072.");
        }
        for (String required : REQUIRED_DATA) {
            File file = files.get(required.toLowerCase(Locale.ROOT));
            if (file == null || !file.isFile() || file.length() == 0)
                throw new IOException("Pasta incompleta: falta " + required + ".");
        }
    }

    private static String sha256(File file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream in = new FileInputStream(file)) {
                byte[] buffer = new byte[1024 * 1024];
                int read;
                while ((read = in.read(buffer)) != -1) digest.update(buffer, 0, read);
            }
            StringBuilder result = new StringBuilder();
            for (byte value : digest.digest()) result.append(String.format(Locale.ROOT, "%02x", value & 255));
            return result.toString();
        } catch (java.security.NoSuchAlgorithmException error) {
            throw new IOException(error);
        }
    }

    private static void deleteTree(File file) throws IOException {
        if (!file.exists()) return;
        File[] children = file.listFiles();
        if (children != null) for (File child : children) deleteTree(child);
        if (!file.delete()) throw new IOException("Não foi possível remover os dados temporários.");
    }
}
